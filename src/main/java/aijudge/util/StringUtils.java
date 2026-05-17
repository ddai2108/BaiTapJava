package aijudge.util;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class StringUtils {

    private StringUtils() {}

    public static String normalize(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    public static String stripFence(String t) {
        if (t == null) return "";
        t = t.trim();

        int start = t.indexOf("```");
        if (start < 0) return t; 

        int lineEnd = t.indexOf('\n', start);
        if (lineEnd < 0) return t; 

        int end = t.lastIndexOf("```");
        if (end <= lineEnd) return t; 

        return t.substring(lineEnd + 1, end).trim();
    }

    public static String sanitizeCodeForCompile(String code) {
        if (code == null) return "";

        StringBuilder result = new StringBuilder(code.length());
        int i = 0, n = code.length();

        while (i < n) {
            char c = code.charAt(i);

            if (c == '/' && i + 1 < n && code.charAt(i + 1) == '/') {
                int eol = code.indexOf('\n', i);
                if (eol < 0) eol = n;
                appendAsciiOnly(result, code, i, eol);
                i = eol;
                continue;
            }

            if (c == '/' && i + 1 < n && code.charAt(i + 1) == '*') {
                int end = code.indexOf("*/", i + 2);
                if (end < 0) { end = n - 2; }
                appendAsciiOnly(result, code, i, end + 2);
                i = end + 2;
                continue;
            }

            result.append(c);
            i++;
        }

        return result.toString();
    }

    private static void appendAsciiOnly(StringBuilder sb, String s, int from, int to) {
        to = Math.min(to, s.length());
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            sb.append(c < 128 ? c : '?');
        }
    }

    public static String readAll(InputStream is) throws Exception {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    public static String stackTrace(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public static String truncate(String s, int maxLen) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }
}
