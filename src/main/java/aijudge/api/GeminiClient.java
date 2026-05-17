package aijudge.api;

import aijudge.util.JsonHelper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.*;

public class GeminiClient {

    // Thứ tự ưu tiên model: thử lần lượt nếu model trước bị quota
    private static final String[] MODELS = {
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash",
    };

    private static final String BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final Semaphore RATE_LIMITER = new Semaphore(2);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private byte[] imageBytes;
    private String imageMime;

    public void setImage(byte[] bytes, String mime) { this.imageBytes = bytes; this.imageMime = mime; }
    public void clearImage() { this.imageBytes = null; this.imageMime = null; }
    public boolean hasImage() { return imageBytes != null && imageMime != null; }

    public String send(String prompt, boolean withImage) {
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank())
            return "Lỗi: Chưa thiết lập GEMINI_API_KEY.\n" +
                   "Chạy: setx GEMINI_API_KEY \"your_key\" rồi restart terminal.";

        for (String model : MODELS) {
            String result = sendWithModel(prompt, withImage, model, key);
            if (!is429(result)) return result;
        }
        return "Lỗi: Tất cả model đều hết quota hôm nay.\n" +
               "Giải pháp:\n" +
               "1. Tạo API key mới tại https://aistudio.google.com/apikey\n" +
               "2. Chạy: setx GEMINI_API_KEY \"key_moi\" rồi restart app\n" +
               "3. Hoặc chờ đến 7h sáng mai (quota reset)";
    }

    public String[] sendParallel(String[] prompts, int timeoutS) {
        String[] results = new String[prompts.length];
        // Dùng pool nhỏ + semaphore để không gửi quá 2 request cùng lúc
        ExecutorService pool = Executors.newFixedThreadPool(2);
        @SuppressWarnings("unchecked")
        Future<String>[] futures = new Future[prompts.length];
        for (int i = 0; i < prompts.length; i++) {
            final String p = prompts[i];
            futures[i] = pool.submit(() -> send(p, false));
        }
        pool.shutdown();
        for (int i = 0; i < prompts.length; i++) {
            try { results[i] = futures[i].get(timeoutS, TimeUnit.SECONDS); }
            catch (Exception e) { results[i] = "Lỗi: " + e.getMessage(); }
        }
        return results;
    }

    private String sendWithModel(String prompt, boolean withImage, String model, String key) {
        int maxRetry = 1;
        for (int attempt = 0; attempt < maxRetry; attempt++) {
            try {
                RATE_LIMITER.acquire();
                try {
                    String url = BASE_URL + model + ":generateContent";
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(35))
                            .header("Content-Type", "application/json")
                            .header("x-goog-api-key", key)
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    buildJson(prompt, withImage, 8192), StandardCharsets.UTF_8))
                            .build();

                    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

                    if (resp.statusCode() == 429) {
                        return resp.body(); // trả về ngay để module sinh testcase/code dùng fallback, không bị treo giao diện
                    }

                    if (resp.statusCode() < 200 || resp.statusCode() >= 300)
                        return "Loi Gemini HTTP " + resp.statusCode() + ":\n" + resp.body();

                    String text = extractText(resp.body());
                    if (text.isBlank())
                        return "RAW_RESPONSE:\n" + resp.body().substring(0, Math.min(2000, resp.body().length()));
                    return text;

                } finally {
                    RATE_LIMITER.release();
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Lỗi: Bị interrupt";
            } catch (Exception e) {
                if (attempt == maxRetry - 1)
                    return "Lỗi Gemini: " + e.getMessage();
            }
        }
        return "Lỗi: Hết lần retry";
    }

    private int parseRetryDelay(String body) {
        try {
            int idx = body.indexOf("retryDelay");
            if (idx < 0) return 15;
            int q1 = body.indexOf('"', idx + 12);
            int q2 = body.indexOf('"', q1 + 1);
            if (q1 < 0 || q2 < 0) return 15;
            String val = body.substring(q1 + 1, q2).replace("s", "").trim();
            return (int) Math.ceil(Double.parseDouble(val));
        } catch (Exception e) {
            return 15;
        }
    }

    private boolean is429(String response) {
        return response != null && (
            response.contains("\"code\": 429") ||
            response.contains("\"code\":429") ||
            response.contains("RESOURCE_EXHAUSTED") ||
            response.contains("quota"));
    }

    private String buildJson(String prompt, boolean withImage, int maxTokens) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"contents\":[{\"parts\":[{\"text\":\"")
          .append(JsonHelper.escape(prompt))
          .append("\"}");
        if (withImage && hasImage()) {
            sb.append(",{\"inline_data\":{\"mime_type\":\"")
              .append(imageMime).append("\",\"data\":\"")
              .append(Base64.getEncoder().encodeToString(imageBytes))
              .append("\"}}");
        }
        sb.append("]}],")
          .append("\"generationConfig\":{\"temperature\":0.1,\"maxOutputTokens\":").append(maxTokens).append("}}");
        return sb.toString();
    }

    private String extractText(String json) {
        StringBuilder result = new StringBuilder();
        int pos = 0;
        while (pos < json.length()) {
            int textKey = json.indexOf("\"text\"", pos);
            if (textKey < 0) break;
            boolean isThought = false;
            int blockStart = json.lastIndexOf("{", textKey);
            if (blockStart >= 0) {
                String block = json.substring(blockStart, Math.min(blockStart + 200, json.length()));
                isThought = block.contains("\"thought\"") && block.contains("true");
            }
            int colon = json.indexOf(":", textKey + 6);
            if (colon < 0) break;
            int strStart = colon + 1;
            while (strStart < json.length() && json.charAt(strStart) == ' ') strStart++;
            if (strStart >= json.length() || json.charAt(strStart) != '"') { pos = colon + 1; continue; }
            strStart++;
            StringBuilder sb = new StringBuilder();
            int i = strStart;
            while (i < json.length()) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    switch (next) {
                        case '"'  -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case 'n'  -> sb.append('\n');
                        case 'r'  -> sb.append('\r');
                        case 't'  -> sb.append('\t');
                        case 'u'  -> {
                            if (i + 5 < json.length()) {
                                try {
                                    int cp = Integer.parseInt(json.substring(i + 2, i + 6), 16);
                                    sb.append((char) cp); i += 4;
                                } catch (Exception ignored) {}
                            }
                        }
                        default -> sb.append(next);
                    }
                    i += 2;
                } else if (c == '"') { break; }
                else { sb.append(c); i++; }
            }
            if (!isThought) result.append(sb);
            pos = i + 1;
        }
        return result.toString();
    }

    public static boolean isApiError(String response) {
        if (response == null || response.isBlank()) return true;
        String s = response.toLowerCase();
        return s.startsWith("raw_response") || s.contains("loi gemini http")
            || s.contains("lỗi gemini") || s.contains("api_key")
            || s.contains("quota") || s.contains("permission_denied")
            || s.contains("resource_exhausted");
    }
}
