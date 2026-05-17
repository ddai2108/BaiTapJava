package aijudge.ui;

import aijudge.api.GeminiClient;
import aijudge.core.TestcaseGenerator;

import javax.swing.*;
import java.awt.FileDialog;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.*;

public class ImageUploadHandler {

    private final MainFrame frame;

    public ImageUploadHandler(MainFrame frame) {
        this.frame = frame;
    }

    public void handle() {
        FileDialog dlg = new FileDialog(frame, "Chọn ảnh đề bài (PNG/JPG/WEBP)", FileDialog.LOAD);
        dlg.setFilenameFilter((dir, name) -> {
            String l = name.toLowerCase();
            return l.endsWith(".png") || l.endsWith(".jpg")
                || l.endsWith(".jpeg") || l.endsWith(".webp");
        });
        dlg.setVisible(true);

        if (dlg.getDirectory() == null || dlg.getFile() == null) return;
        File file = new File(dlg.getDirectory(), dlg.getFile());

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String name  = file.getName().toLowerCase();
            String mime  = name.endsWith(".png")  ? "image/png"
                         : name.endsWith(".webp") ? "image/webp"
                         : "image/jpeg";

            frame.gemini.setImage(bytes, mime);
            frame.imageInfoLabel.setText("Đã chọn: " + file.getName());
            frame.uploadImageBtn.setEnabled(false);
            frame.setResult("⏳ Đang đọc đề từ ảnh...");

            frame.runAsync(() -> processImage(file.getName()));

        } catch (Exception ex) {
            frame.setResult("❌ Lỗi đọc file: " + ex.getMessage());
        }
    }

    private void processImage(String fileName) {
        try {
            String extracted = frame.gemini.send(
                "Đây là ảnh đề bài lập trình thi đấu (IOI/ICPC). " +
                "Hãy đọc và ghi lại TOÀN BỘ nội dung thành văn bản thuần, bao gồm: " +
                "tên bài, mô tả, Input, Output, ràng buộc, ví dụ mẫu.\n" +
                "KHÔNG thêm giải thích. Nếu không thấy phần nào thì bỏ qua.", true);

            frame.gemini.clearImage(); 

            if (GeminiClient.isApiError(extracted) || extracted.isBlank()) {
                frame.setResult("❌ Không đọc được đề từ ảnh.\nPhản hồi: " + extracted);
                return;
            }

            final String prob = extracted.trim();

            SwingUtilities.invokeLater(() -> {
                frame.problemArea.setText(prob);
                frame.testcaseArea.setText("⏳ Đang sinh testcase...");
                frame.testcaseStatusLabel.setText("⏳ Đang sinh testcase...");
            });
            frame.setResult("⏳ Đang sinh ví dụ mẫu và testcase...");

            ExecutorService pool = Executors.newFixedThreadPool(2);
            Future<String> exFut = pool.submit(() -> frame.gemini.send(
                "Cho đề bài:\n" + prob + "\n\n" +
                "Sinh ĐÚNG 1 ví dụ Input/Output đơn giản nhất.\n" +
                "Chỉ trả về 2 dòng:\nInput: <giá trị>\nOutput: <giá trị>", false));
            Future<String> tcFut = pool.submit(() -> frame.tcGen.generate(prob));
            pool.shutdown();
            pool.awaitTermination(90, TimeUnit.SECONDS);

            try {
                String ex = exFut.get();
                if (!GeminiClient.isApiError(ex) && !ex.isBlank()) {
                    String full = prob + "\n\n── Ví dụ mẫu ──\n" + ex.trim();
                    frame.lastGenProblem = full.trim();
                    SwingUtilities.invokeLater(() -> frame.problemArea.setText(full));
                } else {
                    frame.lastGenProblem = prob;
                }
            } catch (Exception ignored) {
                frame.lastGenProblem = prob;
            }

            try {
                String tests = tcFut.get();
                boolean valid = TestcaseGenerator.isValidFormat(tests)
                             && !GeminiClient.isApiError(tests);
                if (valid) {
                    final String ft = tests;
                    int cnt = TestcaseGenerator.parse(tests).size();
                    SwingUtilities.invokeLater(() -> {
                        frame.testcaseArea.setText(ft);
                        frame.testcaseStatusLabel.setText("✅ " + cnt + " testcase sẵn sàng");
                    });
                    frame.setResult("✅ Đã đọc đề và sinh " + cnt +
                                    " testcase.\nDán code vào ô bên phải rồi bấm \"Nộp bài / Chấm\".");
                } else {
                    SwingUtilities.invokeLater(() -> {
                        frame.testcaseArea.setText("");
                        frame.testcaseStatusLabel.setText("⚠ Chưa sinh được testcase");
                    });
                    frame.setResult("✅ Đã đọc đề. Dán code rồi bấm \"Nộp bài / Chấm\".");
                }
            } catch (Exception ignored) {
                frame.setResult("✅ Đã đọc đề. Dán code rồi bấm \"Nộp bài / Chấm\".");
            }

        } catch (Exception ex) {
            frame.setResult("❌ Lỗi xử lý ảnh: " + ex.getMessage());
        } finally {
            SwingUtilities.invokeLater(() -> frame.uploadImageBtn.setEnabled(true));
        }
    }
}
