package aijudge.core;

import aijudge.api.GeminiClient;
import aijudge.model.TestCase;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;


public class PipelineOrchestrator {

    public record PipelineResult(
        String        testcaseText,
        String        acCode,
        String        waCode,
        String        tleCode,
        String        checkerCode,
        String        analysisText,
        TestcaseVerifier.VerifyReport verifyReport
    ) {}

    private final GeminiClient        gemini;
    private final SampleCodeGenerator codeGen;
    private final CheckerGenerator    checkerGen;
    private final ProblemAnalyzer     analyzer;
    private final TestcaseGenerator   tcGen;
    private final TestcaseVerifier    verifier;

    public PipelineOrchestrator(GeminiClient gemini, CodeCompiler compiler, ProcessRunner runner) {
        this.gemini     = gemini;
        this.codeGen    = new SampleCodeGenerator(gemini);
        this.checkerGen = new CheckerGenerator(gemini);
        this.analyzer   = new ProblemAnalyzer(gemini);
        this.tcGen      = new TestcaseGenerator(gemini);
        this.verifier   = new TestcaseVerifier(gemini, compiler, runner);
    }

    /**
     * Chạy pipeline đầy đủ song song.
     *
     * @param problem  Nội dung đề bài
     * @param lang     Ngôn ngữ lập trình
     * @param progress Callback cập nhật UI theo thời gian thực
     * @return Kết quả đầy đủ của pipeline
     */
    public PipelineResult run(String problem, String lang,
                              Consumer<String> progress) throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();

        progress.accept("🚀 Bắt đầu pipeline AI song song...\n" +
                        "   Đang gửi tất cả request lên Gemini cùng lúc...");

        Future<String> tcFut       = pool.submit(() -> {
            progress.accept("  📋 Đang sinh testcase...");
            return tcGen.generate(problem);
        });
        Future<String> acFut       = pool.submit(() -> {
            progress.accept("  💻 Đang sinh code AC...");
            return codeGen.generate(problem, lang, SampleCodeGenerator.CodeType.AC);
        });
        Future<String> waFut       = pool.submit(() -> {
            progress.accept("  ❌ Đang sinh code WA...");
            return codeGen.generate(problem, lang, SampleCodeGenerator.CodeType.WA);
        });
        Future<String> tleFut      = pool.submit(() -> {
            progress.accept("  ⏱ Đang sinh code TLE...");
            return codeGen.generate(problem, lang, SampleCodeGenerator.CodeType.TLE);
        });
        Future<String> checkerFut  = pool.submit(() -> {
            progress.accept("  🔍 Đang sinh checker...");
            return checkerGen.generate(problem, lang);
        });
        Future<String> analysisFut = pool.submit(() -> {
            progress.accept("  📊 Đang phân tích đề...");
            return analyzer.analyze(problem);
        });

        pool.shutdown();

        String tcText      = safeGet(tcFut,       "");
        String acCode      = safeGet(acFut,        "");
        String waCode      = safeGet(waFut,        "");
        String tleCode     = safeGet(tleFut,       "");
        String checkerCode = safeGet(checkerFut,   CheckerGenerator.NO_CHECKER);
        String analysis    = safeGet(analysisFut,  "");

        progress.accept("✅ Nhận xong kết quả từ Gemini.\n" +
                        "  🔬 Đang verify testcase bằng code AC thực tế...");

        TestcaseVerifier.VerifyReport verifyReport = null;
        List<TestCase> tests = TestcaseGenerator.parse(tcText);

        if (!tests.isEmpty() && !GeminiClient.isApiError(acCode)) {
            try {
                verifyReport = verifier.verify(problem, lang, tests, progress);

                // Nếu có testcase sai output → tự động sửa
                if (verifyReport.compileOk() && verifyReport.wrongTests() > 0) {
                    progress.accept("🔧 Phát hiện " + verifyReport.wrongTests() +
                                    " testcase sai output. Đang tự sửa...");
                    List<TestCase> fixedTests = verifier.fixWrongOutputs(tests, verifyReport);
                    tcText = TestcaseGenerator.buildText(fixedTests);
                    progress.accept("✅ Đã sửa testcase output sai tự động.");
                }
            } catch (Exception ex) {
                progress.accept("⚠️ Verify thất bại: " + ex.getMessage());
            }
        }

        progress.accept("🎉 Pipeline hoàn thành!");
        return new PipelineResult(tcText, acCode, waCode, tleCode,
                                   checkerCode, analysis, verifyReport);
    }

    private static <T> T safeGet(Future<T> f, T fallback) {
        try { return f.get(120, TimeUnit.SECONDS); }
        catch (Exception e) { return fallback; }
    }
}
