package aijudge.core;

import aijudge.api.GeminiClient;
import aijudge.util.StringUtils;

import java.util.concurrent.*;
import java.text.Normalizer;

public class SampleCodeGenerator {

    public enum CodeType { AC, WA, TLE }

    private static final int AI_TIMEOUT_SEC = 25;

    private final GeminiClient gemini;

    public SampleCodeGenerator(GeminiClient gemini) {
        this.gemini = gemini;
    }

    public String generate(String problem, String lang, CodeType type) {
        String normalizedLang = normalizeLang(lang);
        String prompt = buildPrompt(problem, normalizedLang, type);
        String result = safeSend(prompt);

        String code = extractCode(result);
        code = StringUtils.stripFence(code).trim();

        if (isBadAiResult(code)) {
            code = fallbackCode(problem, normalizedLang, type);
        }

        return StringUtils.sanitizeCodeForCompile(code);
    }

    private String buildPrompt(String problem, String lang, CodeType type) {
        String typeText = switch (type) {
            case AC -> "Viết lời giải đúng hoàn toàn, tối ưu và có thể Accepted.";
            case WA -> "Viết lời giải có lỗi logic tinh vi, chạy được nhưng sai ở một số testcase.";
            case TLE -> "Viết lời giải đúng với test nhỏ nhưng thuật toán chậm, dễ bị TLE với test lớn.";
        };

        return "Bạn là lập trình viên thi đấu ICPC/IOI.\n" +
               "Ngôn ngữ cần viết: " + lang + "\n" +
               "Yêu cầu: " + typeText + "\n\n" +
               "Quy tắc:\n" +
               "- Chỉ trả về code nằm giữa thẻ [CODE] và [/CODE].\n" +
               "- Không markdown, không dùng ```.\n" +
               "- Code đọc stdin, ghi stdout.\n" +
               "- Nếu Java, class chính phải tên Main.\n" +
               "- Nếu C++, dùng C++17.\n\n" +
               "[CODE]\n<mã nguồn>\n[/CODE]\n\n" +
               "ĐỀ BÀI:\n" + problem;
    }

    private String safeSend(String prompt) {
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            Future<String> f = ex.submit(() -> gemini.send(prompt, false));
            String result = f.get(AI_TIMEOUT_SEC, TimeUnit.SECONDS);
            return result == null ? "" : result;
        } catch (Exception e) {
            return "";
        } finally {
            ex.shutdownNow();
        }
    }

    private String normalizeLang(String lang) {
        if (lang == null) return "Java";
        String s = lang.trim().toLowerCase();

        if (s.contains("java")) return "Java";
        if (s.contains("c++") || s.contains("cpp")) return "C++";
        if (s.contains("python") || s.equals("py")) return "Python";

        return lang.trim();
    }

    private String extractCode(String text) {
        if (text == null) return "";

        String lower = text.toLowerCase();
        int start = lower.indexOf("[code]");
        int end = lower.indexOf("[/code]");

        if (start >= 0 && end > start) {
            return text.substring(start + "[CODE]".length(), end).trim();
        }

        return text.trim();
    }

    private boolean isBadAiResult(String code) {
        if (code == null || code.isBlank()) return true;

        String s = code.toLowerCase();

        return s.contains("lỗi gemini")
            || s.contains("loi gemini")
            || s.contains("quota")
            || s.contains("resource_exhausted")
            || s.contains("permission_denied")
            || s.contains("api_key")
            || s.contains("chưa thiết lập")
            || s.startsWith("raw_response")
            || s.startsWith("lỗi:");
    }

    private String fallbackCode(String problem, String lang, CodeType type) {
        String p = normalizeVietnamese(problem);

        if (isProductProblem(p)) return fallbackProduct(lang, type);
        if (isSumProblem(p)) return fallbackSum(lang, type);
        if (isEvenOddProblem(p)) return fallbackEvenOdd(lang, type);
        if (isPrimeProblem(p)) return fallbackPrime(lang, type);

        return fallbackProduct(lang, type);
    }

    private String normalizeVietnamese(String s) {
        if (s == null) return "";
        String x = s.toLowerCase();
        x = Normalizer.normalize(x, Normalizer.Form.NFD);
        x = x.replaceAll("\\p{M}", "");
        x = x.replace('đ', 'd');
        return x;
    }

    private boolean isProductProblem(String p) {
        return p.contains("tich")
            || p.contains("nhan")
            || p.contains("product")
            || p.contains("a * b")
            || p.contains("a*b")
            || p.contains("phep tinh a")
            || (p.contains("hai so") && p.contains("*"));
    }

    private boolean isSumProblem(String p) {
        return p.contains("tong")
            || p.contains("sum")
            || p.contains("a + b")
            || p.contains("a+b")
            || (p.contains("hai so") && p.contains("+"));
    }

    private boolean isEvenOddProblem(String p) {
        return p.contains("chan le")
            || p.contains("chan, le")
            || p.contains("so chan")
            || p.contains("so le")
            || p.contains("even")
            || p.contains("odd");
    }

    private boolean isPrimeProblem(String p) {
        return p.contains("nguyen to")
            || p.contains("prime");
    }

    private String fallbackProduct(String lang, CodeType type) {
        boolean wrong = type == CodeType.WA;

        if ("Java".equals(lang)) {
            return "import java.util.*;\n\npublic class Main {\n" +
                   "    public static void main(String[] args) {\n" +
                   "        Scanner sc = new Scanner(System.in);\n" +
                   "        long a = sc.nextLong();\n" +
                   "        long b = sc.nextLong();\n" +
                   "        System.out.print(" + (wrong ? "a + b" : "a * b") + ");\n" +
                   "    }\n}";
        }

        if ("Python".equals(lang)) {
            return "import sys\n\ndata = list(map(int, sys.stdin.read().split()))\na, b = data[0], data[1]\nprint(" +
                   (wrong ? "a + b" : "a * b") + ")\n";
        }

        return "#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n" +
               "    ios::sync_with_stdio(false);\n" +
               "    cin.tie(nullptr);\n" +
               "    long long A, B;\n" +
               "    cin >> A >> B;\n" +
               "    cout << " + (wrong ? "A + B" : "A * B") + ";\n" +
               "    return 0;\n}";
    }

    private String fallbackSum(String lang, CodeType type) {
        boolean wrong = type == CodeType.WA;

        if ("Java".equals(lang)) {
            return "import java.util.*;\npublic class Main {\n" +
                   "    public static void main(String[] args) {\n" +
                   "        Scanner sc = new Scanner(System.in);\n" +
                   "        long a = sc.nextLong(), b = sc.nextLong();\n" +
                   "        System.out.print(" + (wrong ? "a * b" : "a + b") + ");\n" +
                   "    }\n}";
        }

        if ("Python".equals(lang)) {
            return "import sys\n\na, b = map(int, sys.stdin.read().split()[:2])\nprint(" +
                   (wrong ? "a * b" : "a + b") + ")\n";
        }

        return "#include <bits/stdc++.h>\nusing namespace std;\nint main(){ long long a,b; cin>>a>>b; cout<<" +
               (wrong ? "a*b" : "a+b") + "; }";
    }

    private String fallbackEvenOdd(String lang, CodeType type) {
        boolean wrong = type == CodeType.WA;

        if ("Java".equals(lang)) {
            return "import java.util.*;\npublic class Main {\n" +
                   "    public static void main(String[] args) {\n" +
                   "        Scanner sc = new Scanner(System.in);\n" +
                   "        long n = sc.nextLong();\n" +
                   "        System.out.print((n % 2 == 0) ? \"" + (wrong ? "ODD" : "EVEN") + "\" : \"" + (wrong ? "EVEN" : "ODD") + "\");\n" +
                   "    }\n}";
        }

        if ("Python".equals(lang)) {
            return "import sys\n\nn = int(sys.stdin.read().strip())\nprint('" + (wrong ? "ODD" : "EVEN") + "' if n % 2 == 0 else '" + (wrong ? "EVEN" : "ODD") + "')\n";
        }

        return "#include <bits/stdc++.h>\nusing namespace std;\nint main(){ long long n; cin>>n; cout<<(n%2==0?\"" +
               (wrong ? "ODD" : "EVEN") + "\":\"" + (wrong ? "EVEN" : "ODD") + "\"); }";
    }

    private String fallbackPrime(String lang, CodeType type) {
        boolean wrong = type == CodeType.WA;
        boolean slow = type == CodeType.TLE;

        if ("Java".equals(lang)) {
            return "import java.util.*;\npublic class Main {\n" +
                   "    static boolean prime(long n){\n" +
                   "        if(n < 2) return false;\n" +
                   (slow ? "        for(long i=2;i<n;i++) if(n%i==0) return false;\n" :
                           "        for(long i=2;i*i<=n;i++) if(n%i==0) return false;\n") +
                   "        return true;\n" +
                   "    }\n" +
                   "    public static void main(String[] args){ Scanner sc=new Scanner(System.in); long n=sc.nextLong(); boolean ok=prime(n); System.out.print(ok?\"" +
                   (wrong ? "NO" : "YES") + "\":\"" + (wrong ? "YES" : "NO") + "\"); }\n}";
        }

        if ("Python".equals(lang)) {
            String loop = slow ? "range(2, n)" : "range(2, int(n**0.5)+1)";
            return "import sys\n\nn = int(sys.stdin.read().strip())\ndef prime(n):\n    if n < 2: return False\n    for i in " + loop + ":\n        if n % i == 0: return False\n    return True\nok = prime(n)\nprint('" +
                   (wrong ? "NO" : "YES") + "' if ok else '" + (wrong ? "YES" : "NO") + "')\n";
        }

        return "#include <bits/stdc++.h>\nusing namespace std;\nbool prime(long long n){ if(n<2) return false; for(long long i=2;" +
               (slow ? "i<n" : "i*i<=n") + ";i++) if(n%i==0) return false; return true; }\nint main(){ long long n; cin>>n; bool ok=prime(n); cout<<(ok?\"" +
               (wrong ? "NO" : "YES") + "\":\"" + (wrong ? "YES" : "NO") + "\"); }";
    }
}
