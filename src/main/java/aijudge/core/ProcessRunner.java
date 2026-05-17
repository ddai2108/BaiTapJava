package aijudge.core;

import aijudge.model.RunResult;
import aijudge.util.StringUtils;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

public class ProcessRunner {

    /**
     * Chạy lệnh {@code cmd} trong thư mục {@code dir}, truyền {@code input} vào stdin.
     *
     * @param cmd     Danh sách tham số lệnh (ví dụ: ["java", "-cp", ".", "Main"])
     * @param dir     Thư mục làm việc
     * @param input   Nội dung stdin (rỗng nếu không cần)
     * @param timeout Giới hạn thời gian (giây)
     * @return Kết quả bao gồm exit code, stdout, stderr và trạng thái timeout
     */
    public RunResult run(List<String> cmd, Path dir, String input, int timeout) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir.toFile());
        Process proc = pb.start();

        try (OutputStream os = proc.getOutputStream()) {
            if (input != null && !input.isBlank())
                os.write(input.getBytes(StandardCharsets.UTF_8));
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<String> outFut = pool.submit(() -> StringUtils.readAll(proc.getInputStream()));
        Future<String> errFut = pool.submit(() -> StringUtils.readAll(proc.getErrorStream()));

        boolean finished = proc.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            pool.shutdownNow();
            return new RunResult(-1, "", "Time Limit Exceeded", true);
        }

        String stdout = outFut.get(3, TimeUnit.SECONDS);
        String stderr = errFut.get(3, TimeUnit.SECONDS);
        pool.shutdown();

        return new RunResult(proc.exitValue(), stdout, stderr, false);
    }

    public static void deleteDir(Path dir) {
        try (var walk = java.nio.file.Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .map(java.nio.file.Path::toFile)
                .forEach(java.io.File::delete);
        } catch (Exception ignored) {}
    }
}
