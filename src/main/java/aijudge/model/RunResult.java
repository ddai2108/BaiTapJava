package aijudge.model;

/**
 * Kết quả sau khi chạy một lệnh (process).
 *
 * @param exitCode  Mã trả về của tiến trình (0 = thành công)
 * @param stdout    Đầu ra tiêu chuẩn
 * @param stderr    Đầu ra lỗi
 * @param timeout   {@code true} nếu bị kill vì quá thời gian
 */
public record RunResult(int exitCode, String stdout, String stderr, boolean timeout) {}
