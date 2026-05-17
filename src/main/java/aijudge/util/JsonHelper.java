package aijudge.util;

import aijudge.model.SubmissionRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonHelper {

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 32) sb.append(String.format("\\u%04x", (int) c));
                    else        sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    public static String unescape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    case 'u'  -> {
                        if (i + 4 < s.length()) {
                            String h = s.substring(i + 1, i + 5);
                            try { sb.append((char) Integer.parseInt(h, 16)); i += 4; }
                            catch (Exception ignored) { sb.append("\\u").append(h); i += 4; }
                        }
                    }
                    default -> sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static void save(Path file, List<SubmissionRecord> records) throws IOException {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < records.size(); i++) {
            SubmissionRecord r = records.get(i);
            sb.append("  {\n");
            sb.append("    \"time\": \"")    .append(escape(r.time))    .append("\",\n");
            sb.append("    \"lang\": \"")    .append(escape(r.language)).append("\",\n");
            sb.append("    \"verdict\": \"").append(escape(r.verdict))  .append("\",\n");
            sb.append("    \"score\": \"")  .append(String.format("%.2f", r.score)).append("\",\n");
            sb.append("    \"problem\": \"").append(escape(r.problem))  .append("\",\n");
            sb.append("    \"code\": \"")   .append(escape(r.code))     .append("\",\n");
            sb.append("    \"detail\": \"") .append(escape(r.detail))   .append("\"\n");
            sb.append("  }");
            if (i + 1 < records.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("]\n");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    public static List<SubmissionRecord> load(Path file) {
        List<SubmissionRecord> list = new ArrayList<>();
        if (!Files.exists(file)) return list;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            int pos = 0;
            while (pos < json.length()) {
                int start = json.indexOf('{', pos);
                if (start < 0) break;
                int depth = 0, end = -1;
                for (int i = start; i < json.length(); i++) {
                    if (json.charAt(i) == '{') depth++;
                    else if (json.charAt(i) == '}') {
                        depth--;
                        if (depth == 0) { end = i; break; }
                    }
                }
                if (end < 0) break;
                String block = json.substring(start + 1, end);
                Map<String, String> kv = parseStringFields(block);
                String time    = kv.getOrDefault("time", "");
                String lang    = kv.getOrDefault("lang", "Java");
                String verdict = kv.getOrDefault("verdict", "");
                String problem = kv.getOrDefault("problem", "");
                String code    = kv.getOrDefault("code", "");
                String detail  = kv.getOrDefault("detail", "");
                double score   = 0;
                try { score = Double.parseDouble(kv.getOrDefault("score", "0")); }
                catch (Exception ignored) {}
                if (!time.isBlank())
                    list.add(new SubmissionRecord(time, lang, verdict, score, problem, code, detail));
                pos = end + 1;
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static Map<String, String> parseStringFields(String block) {
        Map<String, String> map = new LinkedHashMap<>();
        int pos = 0;
        while (pos < block.length()) {
            int ks = block.indexOf('"', pos);
            if (ks < 0) break;
            int ke = block.indexOf('"', ks + 1);
            if (ke < 0) break;
            String key = block.substring(ks + 1, ke);
            int colon = block.indexOf(':', ke + 1);
            if (colon < 0) break;
            int vs = block.indexOf('"', colon + 1);
            if (vs < 0) { pos = colon + 1; continue; }
            StringBuilder val = new StringBuilder();
            int i = vs + 1;
            while (i < block.length()) {
                char c = block.charAt(i);
                if (c == '\\' && i + 1 < block.length()) {
                    char n = block.charAt(i + 1);
                    switch (n) {
                        case '"'  -> val.append('"');
                        case '\\' -> val.append('\\');
                        case 'n'  -> val.append('\n');
                        case 'r'  -> val.append('\r');
                        case 't'  -> val.append('\t');
                        default   -> val.append(n);
                    }
                    i += 2;
                } else if (c == '"') {
                    break;
                } else {
                    val.append(c);
                    i++;
                }
            }
            map.put(key, val.toString());
            pos = i + 1;
        }
        return map;
    }
}
