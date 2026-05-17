package aijudge.core;

import java.io.File;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class ExeFinder {

    private ExeFinder() {}

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /** Tìm {@code javac}, {@code java}, {@code python}, … trong PATH. */
    public static String find(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        List<String> exts = new ArrayList<>();
        if (isWindows()) {
            String pex = System.getenv("PATHEXT");
            if (pex != null)
                for (String x : pex.split(";"))
                    if (!x.isBlank()) exts.add(x.toLowerCase());
            if (!exts.contains(".exe")) exts.add(".exe");
        } else {
            exts.add("");
        }

        for (String dir : pathEnv.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path folder = Paths.get(dir.trim());
            if (!Files.isDirectory(folder)) continue;
            for (String ext : exts) {
                Path candidate = folder.resolve(name + ext);
                if (Files.exists(candidate) &&
                    (isWindows() ? Files.isRegularFile(candidate) : Files.isExecutable(candidate)))
                    return candidate.toString();
            }
        }
        return null;
    }

    public static String findCpp() {
        String env = System.getenv("CXX");
        if (env != null && !env.isBlank() && Files.exists(Paths.get(env))) return env;

        String fromPath = find("g++");
        if (fromPath != null) return fromPath;

        if (isWindows()) {
            for (String p : new String[]{
                "C:\\msys64\\mingw64\\bin\\g++.exe",
                "C:\\msys64\\ucrt64\\bin\\g++.exe",
                "C:\\MinGW\\bin\\g++.exe",
                "C:\\mingw64\\bin\\g++.exe",
                "D:\\msys64\\mingw64\\bin\\g++.exe",
                "D:\\mingw64\\bin\\g++.exe"
            }) {
                if (Files.exists(Paths.get(p))) return p;
            }
        }
        return null;
    }

    public static String findPython() {
        String py = find("python");
        if (py != null) return py;
        py = find("python3");
        if (py != null) return py;

        if (isWindows()) {
            String user = System.getProperty("user.name");
            for (String p : new String[]{
                "C:\\Python312\\python.exe",
                "C:\\Python311\\python.exe",
                "C:\\Python310\\python.exe",
                "C:\\Users\\" + user + "\\AppData\\Local\\Programs\\Python\\Python312\\python.exe",
                "C:\\Users\\" + user + "\\AppData\\Local\\Programs\\Python\\Python311\\python.exe"
            }) {
                if (Files.exists(Paths.get(p))) return p;
            }
        }
        return null;
    }
}
