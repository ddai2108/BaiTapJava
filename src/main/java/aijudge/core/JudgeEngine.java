package aijudge.core;

import aijudge.model.CompileResult;
import aijudge.model.RunResult;
import aijudge.model.TestCase;
import aijudge.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class JudgeEngine {

    private static final int TIME_LIMIT_SEC = 5;
    private static final int MAX_THREADS    = 4;

    private final CodeCompiler  compiler;
    private final ProcessRunner runner;

    public JudgeEngine(CodeCompiler compiler, ProcessRunner runner) {
        this.compiler = compiler;
        this.runner   = runner;
    }

    /**
     * Kết quả chấm toàn bộ bài.
     *
     * @param verdicts  Mảng verdict từng testcase ("AC", "WA", "TLE", "RE")
     * @param acCount   Số testcase AC
     * @param total     Tổng số testcase
     * @param compileOk {@code true} nếu biên dịch thành công
     * @param compileMsg Thông báo lỗi biên dịch (nếu có)
     */
    public record JudgeResult(
        String[] verdicts,
        int acCount,
        int total,
        boolean compileOk,
        String compileMsg
    ) {
        public boolean isAccepted() { return compileOk && acCount == total; }
        public boolean hasTLE()     { return Arrays.asList(verdicts).contains("TLE"); }
        public double  score()      { return total == 0 ? 0 : 10.0 * acCount / total; }

        public String summaryVerdict() {
            if (!compileOk)       return "COMPILE ERROR";
            if (isAccepted())     return "✓ ACCEPTED";
            if (hasTLE())         return "TIME LIMIT EXCEEDED";
            return "WRONG ANSWER";
        }
    }

    /**
     * Chấm bài: biên dịch + chạy tất cả testcase.
     *
     * @param lang         Ngôn ngữ lập trình
     * @param code         Mã nguồn của thí sinh
     * @param tests        Danh sách testcase
     * @param liveCallback Gọi mỗi khi có testcase hoàn thành (truyền mảng verdicts hiện tại)
     * @return Kết quả chấm cuối cùng
     */
    public JudgeResult judge(String lang, String code, List<TestCase> tests,
                             Consumer<String[]> liveCallback) throws Exception {
        Path judgeDir = Files.createTempDirectory("aijudge-");
        try {
            // ── Biên dịch ────────────────────────────────────────────────
            CompileResult cr = compiler.compile(judgeDir, lang, code);
            if (!cr.success()) {
                return new JudgeResult(new String[0], 0, tests.size(), false, cr.output());
            }

            // ── Chấm song song ───────────────────────────────────────────
            int N = tests.size();
            String[] verdicts = new String[N];
            Arrays.fill(verdicts, "...");

            int threads = Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors());
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(N);

            for (int i = 0; i < N; i++) {
                final int   idx = i;
                final TestCase tc = tests.get(i);
                pool.submit(() -> {
                    try {
                        RunResult run = runner.run(cr.runCmd(), judgeDir, tc.input(), TIME_LIMIT_SEC);
                        if (run.timeout()) {
                            verdicts[idx] = "TLE";
                        } else if (run.exitCode() != 0) {
                            verdicts[idx] = "RE";
                        } else {
                            String expected = StringUtils.normalize(tc.output());
                            String actual   = StringUtils.normalize(run.stdout());
                            verdicts[idx]   = expected.equals(actual) ? "AC" : "WA";
                        }
                    } catch (Exception ex) {
                        verdicts[idx] = "RE";
                    } finally {
                        latch.countDown();
                        if (liveCallback != null)
                            liveCallback.accept(verdicts.clone());
                    }
                });
            }

            pool.shutdown();
            latch.await(120, TimeUnit.SECONDS);

            int ac = (int) Arrays.stream(verdicts).filter("AC"::equals).count();
            return new JudgeResult(verdicts, ac, N, true, "");

        } finally {
            ProcessRunner.deleteDir(judgeDir);
        }
    }
}
