package aijudge.core;

import aijudge.api.GeminiClient;


public class ProblemAnalyzer {

    private final GeminiClient gemini;

    public ProblemAnalyzer(GeminiClient gemini) {
        this.gemini = gemini;
    }

    /**
     * Phân tích toàn diện đề bài: thuật toán, edge case, hướng tiếp cận.
     *
     * @param problem Nội dung đề bài
     * @return Văn bản phân tích chi tiết
     */
    public String analyze(String problem) {
        String prompt =
            "Bạn là chuyên gia lập trình thi đấu (ICPC/IOI). Phân tích kỹ đề bài sau:\n\n" +
            "📌 ĐỀ BÀI:\n" + problem + "\n\n" +
            "Hãy phân tích theo các mục sau (trả lời bằng tiếng Việt, rõ ràng, súc tích):\n\n" +
            "## 1. Tóm tắt bài toán\n" +
            "   - Cho gì? Cần tìm/tính gì?\n\n" +
            "## 2. Phân tích ràng buộc\n" +
            "   - Giới hạn N, K và các giá trị khác\n" +
            "   - Thời gian cho phép ước tính bao nhiêu phép tính?\n\n" +
            "## 3. Các edge case cần chú ý\n" +
            "   - Liệt kê ít nhất 5 edge case quan trọng\n\n" +
            "## 4. Hướng tiếp cận thuật toán\n" +
            "   - Thuật toán gợi ý (DP, greedy, graph, math, binary search...)\n" +
            "   - Tại sao chọn thuật toán này?\n" +
            "   - Độ phức tạp: O(...) thời gian, O(...) không gian\n\n" +
            "## 5. Bẫy thường gặp\n" +
            "   - Những lỗi phổ biến khi làm bài này\n" +
            "   - Định dạng input/output cần chú ý\n\n" +
            "## 6. Gợi ý cài đặt\n" +
            "   - Cấu trúc dữ liệu nên dùng\n" +
            "   - Các bước cài đặt chính\n";

        return gemini.send(prompt, false);
    }

    /**
     * Đánh giá độ mạnh của bộ testcase hiện có.
     *
     * @param problem      Nội dung đề bài
     * @param testcaseText Văn bản các testcase theo định dạng chuẩn
     * @return Báo cáo đánh giá testcase
     */
    public String evaluateTestcases(String problem, String testcaseText) {
        String prompt =
            "Bạn là chuyên gia sinh testcase cho kỳ thi lập trình (IOI/ICPC).\n\n" +
            "Đề bài:\n" + problem + "\n\n" +
            "Bộ testcase hiện có:\n" + testcaseText + "\n\n" +
            "Hãy đánh giá độ mạnh của bộ testcase theo các tiêu chí:\n\n" +
            "## ✅ Điểm mạnh\n" +
            "   - Những trường hợp đã được phủ tốt\n\n" +
            "## ❌ Điểm yếu / Thiếu sót\n" +
            "   - Những edge case CHƯA được phủ\n" +
            "   - Những trường hợp đặc biệt còn thiếu\n\n" +
            "## ⚠️ Testcase có thể sai output\n" +
            "   - Liệt kê testcase nào nghi ngờ output tính sai (nếu có)\n" +
            "   - Giải thích tại sao\n\n" +
            "## 📊 Đánh giá tổng thể\n" +
            "   - Độ mạnh: Yếu / Trung bình / Mạnh / Rất mạnh\n" +
            "   - Lý do đánh giá\n\n" +
            "## 💡 Gợi ý bổ sung\n" +
            "   - Cần thêm những testcase gì để bộ test mạnh hơn?\n" +
            "   - Gợi ý input cụ thể cho 2-3 testcase còn thiếu\n";

        return gemini.send(prompt, false);
    }
}
