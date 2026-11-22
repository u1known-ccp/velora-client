package dev.velora.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** All launcher directories. Root: %APPDATA%\.velora on Windows, ~/.velora elsewhere. */
public final class LauncherPaths {
    private static final Path ROOT = resolveRoot();

    private LauncherPaths() { }

    private static Path resolveRoot() {
        String override = System.getProperty("velora.home");
        if (override != null && !override.isBlank()) return Path.of(override);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String appData = System.getenv("APPDATA");
        if (os.contains("win") && appData != null) return Path.of(appData, ".velora");
        return Path.of(System.getProperty("user.home"), ".velora");
    }

    public static Path root() { return ROOT; }
    public static Path launcherDir() { return ROOT.resolve("launcher"); }
    public static Path settingsFile() { return launcherDir().resolve("settings.json"); }
    public static Path accountsFile() { return launcherDir().resolve("accounts.json"); }
    public static Path keyFile() { return launcherDir().resolve("secret.key"); }
    public static Path versions() { return ROOT.resolve("versions"); }
    public static Path libraries() { return ROOT.resolve("libraries"); }
    public static Path assets() { return ROOT.resolve("assets"); }
    public static Path runtimes() { return ROOT.resolve("runtime"); }
    public static Path instances() { return ROOT.resolve("instances"); }
    public static Path updates() { return ROOT.resolve("updates"); }
    public static Path logs() { return launcherDir().resolve("logs"); }

    public static Path instance(String profileName) {
        String safe = profileName.replaceAll("[^A-Za-z0-9 _\\-]", "_").trim();
        return instances().resolve(safe.isEmpty() ? "default" : safe);
    }

    public static void ensure() throws IOException {
        for (Path p : new Path[]{launcherDir(), versions(), libraries(), assets(), runtimes(), instances(), updates(), logs()}) {
            Files.createDirectories(p);
        }
    }
}
