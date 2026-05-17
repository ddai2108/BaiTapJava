package aijudge.ui;

import aijudge.api.GeminiClient;
import aijudge.core.JudgeEngine;
import aijudge.core.TestcaseGenerator;
import aijudge.model.SubmissionRecord;
import aijudge.model.TestCase;

import javax.swing.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;


public class SubmitHandler {

    private static final DateTimeFormatter DT_FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final MainFrame frame;

    public SubmitHandler(MainFrame frame) {
        this.frame = frame;
    }

    public void handle() {
        frame.startProcessing(this::doJudge);
    }

    private void doJudge() {
        String code    = frame.codeArea.getText().trim();
        String lang    = Objects.toString(frame.languageBox.getSelectedItem());
        String problem = frame.problemArea.getText().trim();

        if (code.isBlank()) {
            frame.setResult("❌ Chưa có code. Dán code vào ô \"Code của bạn\".");
            return;
        }
        if (problem.isBlank()) {
            frame.setResult("❌ Chưa có đề bài. Nhập đề hoặc tải ảnh đề lên.");
            return;
        }

        String existingTests = frame.testcaseArea.getText().trim();
        boolean hasTests     = TestcaseGenerator.isValidFormat(existingTests)
                            && !existingTests.startsWith("⏳");

        String testsText;
        if (hasTests) {
            frame.setResult("⏳ Đang biên dịch...");
            testsText = existingTests;
        } else {
            frame.setResult("⏳ Đang sinh testcase song song với biên dịch...");
            testsText = generateTestcasesSync(problem);
            if (testsText == null) return; // lỗi đã set
        }

        List<TestCase> tests = TestcaseGenerator.parse(testsText);
        if (tests.isEmpty()) {
            frame.setResult("❌ Không parse được testcase từ dữ liệu hiện tại.");
            return;
        }

        final String finalTests = testsText;
        SwingUtilities.invokeLater(() -> {
            frame.testcaseArea.setText(finalTests);
            frame.testcaseStatusLabel.setText("✅ " + tests.size() + " testcase");
        });
        frame.lastGenProblem = problem;

        frame.setResult("⏳ Đang biên dịch và chấm...");
        try {
            JudgeEngine.JudgeResult result = frame.judge.judge(lang, code, tests, verdicts ->
                SwingUtilities.invokeLater(() -> frame.resultArea.setText(liveText(verdicts, tests.size())))
            );

            String finalText = buildFinalText(result);
            frame.setResult(finalText);

            String verdict = result.summaryVerdict();
            frame.historyPanel.addRecord(new SubmissionRecord(
                LocalDateTime.now().format(DT_FMT),
                lang, verdict, result.score(),
                problem, code, finalText
            ));

        } catch (Exception ex) {
            frame.setResult("❌ Lỗi hệ thống:\n" + ex.getMessage());
        }
    }

    private String generateTestcasesSync(String problem) {
        try {
            String tests = frame.tcGen.generate(problem);
            if (GeminiClient.isApiError(tests) || !TestcaseGenerator.isValidFormat(tests)) {
                frame.setResult("❌ Không sinh được testcase. Phản hồi API:\n\n" + tests);
                return null;
            }
            return tests;
        } catch (Exception ex) {
            frame.setResult("❌ Lỗi sinh testcase: " + ex.getMessage());
            return null;
        }
    }

    private String liveText(String[] verdicts, int total) {
        StringBuilder sb = new StringBuilder("⏳ ĐANG CHẤM...\n─────────────────\n\n");
        int done = 0, ac = 0;
        for (int i = 0; i < verdicts.length; i++) {
            sb.append(String.format("  Testcase %2d :  %s%n", i + 1,
                verdicts[i] == null ? "..." : verdicts[i]));
            if (verdicts[i] != null && !verdicts[i].equals("...")) done++;
            if ("AC".equals(verdicts[i])) ac++;
        }
        sb.append(String.format("%n  Đã chấm: %d/%d  |  AC: %d%n", done, total, ac));
        return sb.toString();
    }

    private String buildFinalText(JudgeEngine.JudgeResult r) {
        if (!r.compileOk()) {
            return "❌ COMPILE ERROR:\n─────────────────\n" + r.compileMsg();
        }
        StringBuilder sb = new StringBuilder("KẾT QUẢ CHẤM BÀI\n─────────────────\n\n");
        for (int i = 0; i < r.verdicts().length; i++) {
            String v = r.verdicts()[i];
            String icon = switch (v) {
                case "AC"  -> "✅";
                case "WA"  -> "❌";
                case "TLE" -> "⏱";
                case "RE"  -> "💥";
                default    -> "?";
            };
            sb.append(String.format("  Testcase %2d :  %s %s%n", i + 1, icon, v));
        }
        sb.append("\n─────────────────\n");
        sb.append(String.format("  AC   : %d / %d%n", r.acCount(), r.total()));
        sb.append(String.format("  Điểm : %.2f / 10%n", r.score()));
        sb.append("\n  Kết luận: ").append(r.summaryVerdict()).append("\n");
        return sb.toString();
    }
}
