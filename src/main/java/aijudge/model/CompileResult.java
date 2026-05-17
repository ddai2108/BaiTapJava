package aijudge.model;

import java.util.List;

/**
 * Kết quả biên dịch code.
 *
 * @param success  {@code true} nếu biên dịch thành công
 * @param output   Thông báo lỗi (nếu có)
 * @param runCmd   Lệnh để chạy chương trình sau khi biên dịch
 */
public record CompileResult(boolean success, String output, List<String> runCmd) {}
