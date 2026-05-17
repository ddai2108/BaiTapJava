package aijudge.core;

import aijudge.api.GeminiClient;
import aijudge.model.TestCase;
import aijudge.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class TestcaseVerifier {

    private final GeminiClient        gemini;
    private final SampleCodeGenerator codeGen;
    private final CodeCompiler        compiler;
    private final ProcessRunner       runner;

    public TestcaseVerifier(GeminiClient gemini, CodeCompiler compiler, ProcessRunner runner) {
        this.gemini   = gemini;
        this.codeGen  = new SampleCodeGenerator(gemini);
        this.compiler = compiler;
        this.runner   = runner;
    }

    public record VerifyResult(
        int    index,      
        String input,
        String expectedOut,  
        String actualOut,    
        boolean match,       
        String note          
    ) {}

    public record VerifyReport(
        List<VerifyResult> results,
        int    totalTests,
        int    correctTests,
        int    wrongTests,
        boolean compileOk,
        String  compileSummary,
        String  acCode          
    ) {
        public boolean allCorrect() { return compileOk && wrongTests == 0; }

        public String summary() {
            if (!compileOk) return "❌ Không thể verify: Code AC không biên dịch được.\n" + compileSummary;
            StringBuilder sb = new StringBuilder();
            sb.append("🔍 KẾT QUẢ XÁC MINH TESTCASE\n");
            sb.append("══════════════════════════════════\n\n");
            sb.append(String.format("  Tổng testcase : %d%n", totalTests));
            sb.append(String.format("  Output đúng  : %d ✅%n", correctTests));
            sb.append(String.format("  Output SAI   : %d ❌%n", wrongTests));
            sb.append("\n");
            for (VerifyResult r : results) {
                String icon = r.match() ? "✅" : "❌";
                sb.append(String.format("  Test %2d: %s%n", r.index(), icon));
                if (!r.match()) {
                    sb.append(String.format("    Input   : %s%n", r.input().replace("\n", "↵")));
                    sb.append(String.format("    Kỳ vọng : %s%n", r.expectedOut().trim()));
                    sb.append(String.format("    Thực tế : %s%n", r.actualOut().trim()));
                    sb.append(String.format("    → %s%n", r.note()));
                }
            }
            sb.append("\n══════════════════════════════════\n");
            if (allCorrect())
                sb.append("✅ Tất cả testcase output đều CHÍNH XÁC!\n");
            else
                sb.append("⚠️ Có " + wrongTests + " testcase output SAI! Xem chi tiết bên trên.\n");
            return sb.toString();
        }
    }

    /**
     * Verify toàn bộ testcase:
     * sinh code AC → biên dịch → chạy từng test → so sánh output.
     *
     * @param problem  Nội dung đề bài
     * @param lang     Ngôn ngữ
     * @param tests    Danh sách testcase cần verify
     * @param progress Callback cập nhật tiến độ (nullable)
     */
    public VerifyReport verify(String problem, String lang, List<TestCase> tests,
                               java.util.function.Consumer<String> progress) throws Exception {
        if (progress != null) progress.accept("⏳ Đang sinh code AC để xác minh testcase...");

        String acCode = StringUtils.stripFence(codeGen.generate(problem, lang, SampleCodeGenerator.CodeType.AC)).trim();
        acCode = StringUtils.sanitizeCodeForCompile(acCode);
        if (acCode.isBlank() || GeminiClient.isApiError(acCode)) {
            return new VerifyReport(List.of(), tests.size(), 0, tests.size(),
                false, "Không sinh được code AC: " + acCode, acCode);
        }

        if (progress != null) progress.accept("⏳ Đang biên dịch code AC...");
        Path dir = Files.createTempDirectory("verify-");
        try {
            var cr = compiler.compile(dir, lang, acCode);
            if (!cr.success()) {
                return new VerifyReport(List.of(), tests.size(), 0, tests.size(),
                    false, cr.output(), acCode);
            }

            if (progress != null) progress.accept("⏳ Đang chạy " + tests.size() + " testcase để verify...");
            int N = tests.size();
            VerifyResult[] results = new VerifyResult[N];

            ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(4, Runtime.getRuntime().availableProcessors()));
            CountDownLatch latch = new CountDownLatch(N);

            for (int i = 0; i < N; i++) {
                final int idx = i;
                final TestCase tc = tests.get(i);
                pool.submit(() -> {
                    try {
                        var run = runner.run(cr.runCmd(), dir, tc.input(), 10);
                        String actual   = run.timeout() ? "[TLE]" :
                                          run.exitCode() != 0 ? "[RE exit=" + run.exitCode() + "]" :
                                          StringUtils.normalize(run.stdout());
                        String expected = StringUtils.normalize(tc.output());
                        boolean match   = actual.equals(expected);
                        String note     = match ? "" :
                            run.timeout()    ? "Code AC bị TLE — bài có thể cần thuật toán khác." :
                            run.exitCode()!=0? "Code AC bị Runtime Error." :
                            "Output khác nhau. Có thể testcase output sai hoặc code AC chưa đúng.";
                        results[idx] = new VerifyResult(
                            idx + 1, tc.input().trim(), tc.output().trim(),
                            run.timeout() ? "[TLE]" :
                            run.exitCode()!=0 ? "[RE]" : run.stdout().trim(),
                            match, note);
                    } catch (Exception ex) {
                        results[idx] = new VerifyResult(idx + 1, tc.input().trim(),
                            tc.output().trim(), "[ERROR: " + ex.getMessage() + "]",
                            false, "Lỗi hệ thống khi chạy.");
                    } finally {
                        latch.countDown();
                    }
                });
            }
            pool.shutdown();
            latch.await(120, TimeUnit.SECONDS);

            List<VerifyResult> list = new ArrayList<>();
            int correct = 0, wrong = 0;
            for (VerifyResult r : results) {
                if (r == null) continue;
                list.add(r);
                if (r.match()) correct++; else wrong++;
            }
            return new VerifyReport(list, N, correct, wrong, true, "", acCode);

        } finally {
            ProcessRunner.deleteDir(dir);
        }
    }

    public List<TestCase> fixWrongOutputs(List<TestCase> original, VerifyReport report) {
        List<TestCase> fixed = new ArrayList<>(original);
        for (VerifyResult r : report.results()) {
            if (!r.match() && !r.actualOut().startsWith("[")) {
                int idx = r.index() - 1;
                if (idx >= 0 && idx < fixed.size()) {
                    fixed.set(idx, new TestCase(fixed.get(idx).input(), r.actualOut() + "\n"));
                }
            }
        }
        return fixed;
    }
}
