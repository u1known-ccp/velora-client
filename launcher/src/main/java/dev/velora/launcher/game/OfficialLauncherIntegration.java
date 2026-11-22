package dev.velora.launcher.game;

import com.google.gson.JsonObject;
import dev.velora.core.config.JsonFiles;
import dev.velora.launcher.LauncherPaths;
import dev.velora.launcher.config.LauncherSettings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

/**
 * Registers Velora as a profile in the official Minecraft Launcher – the same mechanism the official
 * Fabric installer uses (a Fabric version JSON in {@code .minecraft/versions} + a launcher profile).
 * The official launcher then handles the Microsoft login, so online/multiplayer servers work with the
 * player's real account. Velora gets its own game directory, so mods of other clients are not touched.
 */
public final class OfficialLauncherIntegration {
    private static final String FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader/%s/%s/profile/json";

    public record Result(Path minecraftDir, Path profilesFile, Path gameDir, String versionId, boolean createdProfilesFile, boolean launcherInstalled) { }

    private final VersionInstaller installer;

    public OfficialLauncherIntegration(VersionInstaller installer) {
        this.installer = installer;
    }

    public static Path minecraftDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String appData = System.getenv("APPDATA");
        if (os.contains("win") && appData != null) return Path.of(appData, ".minecraft");
        if (os.contains("mac")) return Path.of(System.getProperty("user.home"), "Library", "Application Support", "minecraft");
        return Path.of(System.getProperty("user.home"), ".minecraft");
    }

    /**
     * True if the official launcher is installed or has been used. A launcher_profiles.json alone is not enough –
     * Velora may have created it itself – so we look for files only the official launcher writes and its install locations.
     */
    public static boolean officialLauncherDetected() {
        Path mc = minecraftDir();
        for (String marker : new String[]{"launcher_accounts.json", "launcher_accounts_microsoft_store.json",
                "launcher_ui_state.json", "launcher_ui_state_microsoft_store.json", "launcher_log.txt",
                "launcher_profiles_microsoft_store.json"}) {
            if (Files.isRegularFile(mc.resolve(marker))) return true;
        }
        String pf86 = System.getenv("ProgramFiles(x86)");
        if (pf86 != null && Files.isRegularFile(Path.of(pf86, "Minecraft Launcher", "MinecraftLauncher.exe"))) return true;
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            try (var s = Files.newDirectoryStream(Path.of(localAppData, "Packages"), "Microsoft.4297127D64EC6*")) {
                if (s.iterator().hasNext()) return true; // Microsoft Store / Xbox app launcher
            } catch (IOException ignored) {
                // no packages directory
            }
        }
        return false;
    }

    public Result install(LauncherSettings.GameProfile profile) throws IOException {
        Path mc = minecraftDir();
        Files.createDirectories(mc);
        String versionId = "fabric-loader-" + profile.loaderVersion + "-" + profile.minecraftVersion;

        // 1. Fabric version JSON (libraries carry their maven URLs; the official launcher downloads them itself)
        JsonObject fabric = Http.getJson(String.format(FABRIC_META, profile.minecraftVersion, profile.loaderVersion));
        Path versionDir = mc.resolve("versions").resolve(versionId);
        Files.createDirectories(versionDir);
        JsonFiles.write(versionDir.resolve(versionId + ".json"), fabric);
        Path dummyJar = versionDir.resolve(versionId + ".jar");
        if (!Files.exists(dummyJar)) Files.createFile(dummyJar); // same placeholder the Fabric installer creates

        // 2. Velora + Fabric API into Velora's own game directory
        Path gameDir = LauncherPaths.instance(profile.name);
        installer.installMods(gameDir.resolve("mods"), profile.minecraftVersion, () -> false);

        // 3. launcher profile (both files if the Store launcher is used as well)
        boolean launcherInstalled = officialLauncherDetected();
        Path classic = mc.resolve("launcher_profiles.json");
        Path store = mc.resolve("launcher_profiles_microsoft_store.json");
        boolean created = false;
        if (!Files.exists(classic) && !Files.exists(store)) {
            created = true;
            JsonObject root = new JsonObject();
            root.add("profiles", new JsonObject());
            root.add("settings", new JsonObject());
            root.addProperty("version", 3);
            JsonFiles.write(classic, root);
        }
        for (Path file : new Path[]{classic, store}) {
            if (Files.isRegularFile(file)) addProfile(file, profile, versionId, gameDir);
        }
        return new Result(mc, Files.isRegularFile(classic) ? classic : store, gameDir, versionId, created, launcherInstalled);
    }

    private static void addProfile(Path file, LauncherSettings.GameProfile profile, String versionId, Path gameDir) throws IOException {
        Path backup = file.resolveSibling(file.getFileName() + ".velora-backup");
        if (!Files.exists(backup)) Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
        JsonObject root = JsonFiles.read(file).orElseThrow(() -> new IOException(file.getFileName() + " ist beschädigt – bitte den Minecraft Launcher einmal starten"));
        JsonObject profiles = root.has("profiles") && root.get("profiles").isJsonObject() ? root.getAsJsonObject("profiles") : new JsonObject();
        String key = "velora-" + profile.minecraftVersion;
        JsonObject p = profiles.has(key) && profiles.get(key).isJsonObject() ? profiles.getAsJsonObject(key) : new JsonObject();
        String now = Instant.now().toString();
        p.addProperty("name", profile.name);
        p.addProperty("type", "custom");
        if (!p.has("created")) p.addProperty("created", now);
        p.addProperty("lastUsed", now);
        p.addProperty("lastVersionId", versionId);
        p.addProperty("gameDir", gameDir.toAbsolutePath().toString());
        p.addProperty("javaArgs", "-Xmx" + profile.ramMb + "M -XX:+UseG1GC -XX:MaxGCPauseMillis=50");
        String icon = iconDataUri();
        if (icon != null) p.addProperty("icon", icon);
        profiles.add(key, p);
        root.add("profiles", profiles);
        JsonFiles.write(file, root);
    }

    private static String iconDataUri() {
        try (InputStream in = OfficialLauncherIntegration.class.getResourceAsStream("/velora/icon.png")) {
            return in == null ? null : "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (IOException e) {
            return null;
        }
    }
}
