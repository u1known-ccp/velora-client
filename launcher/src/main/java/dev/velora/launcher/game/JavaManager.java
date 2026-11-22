package dev.velora.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.velora.launcher.LauncherPaths;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/**
 * Finds Java installations (reads the JDK's {@code release} file – no process spawning) and can install
 * the official Mojang Java runtime from launchermeta.mojang.com.
 */
public final class JavaManager {
    private static final String RUNTIME_INDEX =
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
    private static final boolean WINDOWS = Rules.osName().equals("windows");

    public record JavaInstall(Path home, int majorVersion, String version, String source) {
        public Path executable() {
            Path bin = home.resolve("bin");
            if (WINDOWS) {
                Path javaw = bin.resolve("javaw.exe");
                return Files.isRegularFile(javaw) ? javaw : bin.resolve("java.exe");
            }
            return bin.resolve("java");
        }

        @Override
        public String toString() { return "Java " + version + " – " + source + " (" + home + ")"; }
    }

    public List<JavaInstall> detect() {
        Map<Path, JavaInstall> found = new LinkedHashMap<>();
        add(found, Path.of(System.getProperty("java.home")), "Launcher-Runtime");
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) add(found, Path.of(javaHome), "JAVA_HOME");
        scanRuntimesDir(found);
        if (WINDOWS) {
            for (String base : new String[]{System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)")}) {
                if (base == null) continue;
                for (String vendor : new String[]{"Java", "Eclipse Adoptium", "Microsoft", "Zulu", "BellSoft", "Amazon Corretto", "Semeru"}) {
                    scanParent(found, Path.of(base, vendor), vendor);
                }
            }
        } else {
            scanParent(found, Path.of("/usr/lib/jvm"), "System");
            scanParent(found, Path.of("/Library/Java/JavaVirtualMachines"), "System");
        }
        return new ArrayList<>(found.values());
    }

    /** Best installation satisfying the required major version (prefers the exact major version). */
    public Optional<JavaInstall> best(int requiredMajor, String customPath) {
        if (customPath != null && !customPath.isBlank()) {
            Path p = Path.of(customPath);
            Path home = Files.isRegularFile(p) ? p.getParent().getParent() : p;
            Optional<JavaInstall> custom = inspect(home, "Benutzerdefiniert");
            if (custom.isPresent()) return custom;
        }
        List<JavaInstall> all = detect();
        return all.stream().filter(j -> j.majorVersion() == requiredMajor).findFirst()
                .or(() -> all.stream().filter(j -> j.majorVersion() > requiredMajor).findFirst());
    }

    private void scanRuntimesDir(Map<Path, JavaInstall> found) {
        Path dir = LauncherPaths.runtimes();
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> s = Files.newDirectoryStream(dir)) {
            for (Path p : s) add(found, p, "Mojang-Runtime");
        } catch (IOException ignored) {
            // ignore unreadable directories
        }
    }

    private void scanParent(Map<Path, JavaInstall> found, Path parent, String source) {
        if (!Files.isDirectory(parent)) return;
        try (DirectoryStream<Path> s = Files.newDirectoryStream(parent)) {
            for (Path p : s) {
                add(found, p, source);
                add(found, p.resolve("Contents/Home"), source); // macOS bundles
            }
        } catch (IOException ignored) {
            // ignore unreadable directories
        }
    }

    private void add(Map<Path, JavaInstall> found, Path home, String source) {
        try {
            Path real = home.toRealPath();
            if (found.containsKey(real)) return;
            inspect(real, source).ifPresent(j -> found.put(real, j));
        } catch (IOException ignored) {
            // not a java home
        }
    }

    public static Optional<JavaInstall> inspect(Path home, String source) {
        Path release = home.resolve("release");
        Path exe = home.resolve("bin").resolve(WINDOWS ? "java.exe" : "java");
        if (!Files.isRegularFile(release) || !Files.isRegularFile(exe)) return Optional.empty();
        try (var in = Files.newInputStream(release)) {
            Properties p = new Properties();
            p.load(in);
            String v = p.getProperty("JAVA_VERSION", "").replace("\"", "");
            if (v.isEmpty()) return Optional.empty();
            String[] parts = v.split("[._+-]");
            int major = Integer.parseInt(parts[0].equals("1") && parts.length > 1 ? parts[1] : parts[0]);
            return Optional.of(new JavaInstall(home, major, v, source));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Downloads the official Mojang runtime for the given component (e.g. "java-runtime-delta" = Java 21). */
    public JavaInstall installMojangRuntime(String component, DoubleConsumer progress, BooleanSupplier cancelled) throws IOException {
        String platform = platformKey();
        JsonObject index = Http.getJson(RUNTIME_INDEX);
        if (!index.has(platform) || !index.getAsJsonObject(platform).has(component)) {
            throw new IOException("Keine Mojang-Runtime '" + component + "' für " + platform);
        }
        JsonArray entries = index.getAsJsonObject(platform).getAsJsonArray(component);
        if (entries.isEmpty()) throw new IOException("Runtime '" + component + "' ist für diese Plattform nicht verfügbar");
        String manifestUrl = entries.get(0).getAsJsonObject().getAsJsonObject("manifest").get("url").getAsString();
        JsonObject files = Http.getJson(manifestUrl).getAsJsonObject("files");
        Path home = LauncherPaths.runtimes().resolve(component);
        List<Downloader.Task> tasks = new ArrayList<>();
        List<Path> executables = new ArrayList<>();
        for (String rel : files.keySet()) {
            JsonObject f = files.getAsJsonObject(rel);
            Path target = home.resolve(rel);
            switch (f.get("type").getAsString()) {
                case "directory" -> Files.createDirectories(target);
                case "file" -> {
                    JsonObject raw = f.getAsJsonObject("downloads").getAsJsonObject("raw");
                    tasks.add(new Downloader.Task(raw.get("url").getAsString(), target, raw.get("sha1").getAsString(), raw.get("size").getAsLong()));
                    if (f.has("executable") && f.get("executable").getAsBoolean()) executables.add(target);
                }
                default -> { } // links are not needed on Windows; on Unix the runtime works without them
            }
        }
        new Downloader().downloadAll(tasks, (done, total, n, totalN) -> progress.accept(done / (double) total), cancelled);
        for (Path exe : executables) exe.toFile().setExecutable(true);
        return inspect(home, "Mojang-Runtime").orElseThrow(() -> new IOException("Runtime-Installation unvollständig"));
    }

    private static String platformKey() {
        return switch (Rules.osName()) {
            case "windows" -> Rules.isArm() ? "windows-arm64" : (Rules.is64Bit() ? "windows-x64" : "windows-x86");
            case "osx" -> Rules.isArm() ? "mac-os-arm64" : "mac-os";
            default -> Rules.is64Bit() ? "linux" : "linux-i386";
        };
    }
}
