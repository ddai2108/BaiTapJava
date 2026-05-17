package aijudge.core;

import aijudge.api.GeminiClient;
import aijudge.util.StringUtils;

public class CheckerGenerator {

    public static final String NO_CHECKER = "NO_CHECKER_NEEDED";

    private final GeminiClient gemini;

    public CheckerGenerator(GeminiClient gemini) {
        this.gemini = gemini;
    }

    public boolean needsChecker(String problem) {
        String p = problem.toLowerCase();
        // Heuristic: bài có nhiều đáp án, số thực, thứ tự tùy ý
        return p.contains("any") || p.contains("bất kỳ") || p.contains("một trong")
            || p.contains("có thể in") || p.contains("số thực") || p.contains("float")
            || p.contains("double") || p.contains("real") || p.contains("thứ tự bất kỳ")
            || p.contains("permutation") || p.contains("hoán vị");
    }

    public String generate(String problem, String lang) {
        if (!needsChecker(problem)) return NO_CHECKER;

        String langNote = lang.equals("Java")
            ? "Viết checker bằng Java. Class tên Checker, main nhận args: inputFile expectedFile actualFile."
            : "Viết checker bằng Java (luôn dùng Java cho checker dù bài dùng ngôn ngữ khác).";

        String prompt =
            "Bạn là chuyên gia thi lập trình IOI/ICPC.\n" +
            "Đề bài sau CÓ NHIỀU ĐÁP ÁN ĐÚNG hoặc output linh hoạt.\n\n" +
            "ĐỀ BÀI:\n" + problem + "\n\n" +
            "Hãy viết CHECKER JAVA để kiểm tra output của thí sinh:\n" +
            langNote + "\n\n" +
            "YÊU CẦU CHECKER:\n" +
            "- Đọc 3 file: args[0]=input, args[1]=expected_output, args[2]=actual_output\n" +
            "- Kiểm tra actual_output có hợp lệ theo đề bài không\n" +
            "- In 'AC' nếu đúng, 'WA: <lý do>' nếu sai\n" +
            "- Exit code: 0 nếu AC, 1 nếu WA\n" +
            "- Xử lý số thực với epsilon=1e-9 nếu cần\n" +
            "- Xử lý trailing whitespace/newline\n\n" +
            "Chỉ trả về code Java thuần, không markdown, không giải thích.\n";

        String result = gemini.send(prompt, false);
        return StringUtils.stripFence(result).trim();
    }

    public static boolean isValid(String checker) {
        return checker != null && !checker.isBlank() && !checker.equals(NO_CHECKER);
    }
}
