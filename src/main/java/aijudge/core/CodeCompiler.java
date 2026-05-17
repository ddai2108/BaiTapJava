package aijudge.core;

import aijudge.model.CompileResult;
import aijudge.model.RunResult;
import aijudge.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class CodeCompiler {

    private final ProcessRunner runner;

    public CodeCompiler(ProcessRunner runner) {
        this.runner = runner;
    }

    /**
     * Biên dịch code vào thư mục {@code dir}.
     *
     * @param dir  Thư mục làm việc tạm
     * @param lang Ngôn ngữ: "Java", "C++", "Python"
     * @param code Nội dung code nguồn (có thể còn fence markdown)
     */
    public CompileResult compile(Path dir, String lang, String code) throws Exception {
        // Bóc fence và làm sạch trước khi biên dịch
        code = StringUtils.stripFence(code);
        code = StringUtils.sanitizeCodeForCompile(code);

        String normalizedLang = normalizeLang(lang);

        return switch (normalizedLang) {
            case "Java"   -> compileJava(dir, code);
            case "C++"    -> compileCpp(dir, code);
            case "Python" -> compilePython(dir, code);
            default       -> new CompileResult(false, "Ngôn ngữ không hỗ trợ: " + lang, List.of());
        };
    }

    private String normalizeLang(String lang) {
        if (lang == null) return "";
        String s = lang.trim().toLowerCase();

        if (s.contains("java")) return "Java";
        if (s.contains("c++") || s.contains("cpp")) return "C++";
        if (s.contains("python") || s.equals("py")) return "Python";

        return lang.trim();
    }

    private CompileResult compileJava(Path dir, String code) throws Exception {
        // Đảm bảo class tên là Main
        code = ensureMainClass(code);

        Path src = dir.resolve("Main.java");
        Files.writeString(src, code, StandardCharsets.UTF_8);

        String javac = ExeFinder.find("javac");
        String java  = ExeFinder.find("java");
        if (javac == null || java == null)
            return new CompileResult(false, "Không tìm thấy javac/java. Kiểm tra JDK.", List.of());

        RunResult r = runner.run(
            List.of(javac, "-encoding", "UTF-8", "Main.java"),
            dir, "", 20
        );

        if (r.exitCode() != 0) {
            String errMsg = r.stdout() + r.stderr();
            errMsg += "\n\n── Snippet (10 dòng đầu) ──\n" +
                      code.lines().limit(10).reduce("", (a, b) -> a + b + "\n");
            return new CompileResult(false, errMsg, List.of());
        }

        return new CompileResult(true, "",
            List.of(java, "-cp", dir.toString(), "Main"));
    }

    private String ensureMainClass(String code) {
        // Tìm tên class public
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("public\\s+class\\s+(\\w+)")
            .matcher(code);
        if (m.find()) {
            String className = m.group(1);
            if (!className.equals("Main")) {
                // Thay tất cả tên class đó bằng Main
                code = code.replaceAll("\\b" + className + "\\b", "Main");
            }
        }
        return code;
    }


    private CompileResult compileCpp(Path dir, String code) throws Exception {
        Files.writeString(dir.resolve("main.cpp"), code, StandardCharsets.UTF_8);
        String gpp = ExeFinder.findCpp();
        if (gpp == null)
            return new CompileResult(false,
                "Không tìm thấy g++. Cài MinGW (Windows) hoặc build-essential (Linux).", List.of());

        String exe = ExeFinder.isWindows() ? "main.exe" : "main";
        RunResult r = runner.run(
            List.of(gpp, "-std=c++17", "-O2", "-finput-charset=UTF-8",
                    "main.cpp", "-o", exe),
            dir, "", 30
        );
        if (r.exitCode() != 0) {
            return new CompileResult(false, r.stdout() + r.stderr(), List.of());
        }
        return new CompileResult(true, "",
            List.of(dir.resolve(exe).toString()));
    }

    private CompileResult compilePython(Path dir, String code) throws Exception {
        // Thêm encoding declaration để tránh lỗi trên Python 2 (nếu có)
        if (!code.startsWith("# -*- coding") && !code.startsWith("# coding")) {
            code = "# -*- coding: utf-8 -*-\n" + code;
        }
        Files.writeString(dir.resolve("main.py"), code, StandardCharsets.UTF_8);
        String py = ExeFinder.findPython();
        if (py == null)
            return new CompileResult(false, "Không tìm thấy python/python3.", List.of());

        RunResult r = runner.run(List.of(py, "-m", "py_compile", "main.py"), dir, "", 20);
        if (r.exitCode() != 0) {
            return new CompileResult(false, r.stdout() + r.stderr(), List.of());
        }
        return new CompileResult(true, "", List.of(py, "main.py"));
    }
}
