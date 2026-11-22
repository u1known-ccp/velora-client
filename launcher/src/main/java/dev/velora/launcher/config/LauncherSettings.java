package dev.velora.launcher.config;

import com.google.gson.JsonObject;
import dev.velora.core.config.JsonFiles;
import dev.velora.launcher.LauncherPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persistent launcher settings (no secrets – those live encrypted in accounts.json). */
public final class LauncherSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/LauncherSettings");

    /** Azure AD application (client) ID used for the Microsoft device code login. Must be registered by the distributor. */
    public String microsoftClientId = "";
    public String javaPath = "";
    public boolean closeOnLaunch = false;
    public boolean autoUpdate = true;
    public boolean backendEnabled = false;
    public String backendUrl = "http://localhost:8787";
    public String selectedProfile = "Velora 1.21.4";
    public String selectedAccount = "";
    public List<GameProfile> profiles = new ArrayList<>();
    public List<ServerShortcut> servers = new ArrayList<>();

    public static final class GameProfile {
        public String name;
        public String minecraftVersion;
        public String loaderVersion;
        public int ramMb;
        public String jvmArgs;
        public int width;
        public int height;

        public GameProfile() { }

        public GameProfile(String name, String minecraftVersion, String loaderVersion, int ramMb) {
            this.name = name;
            this.minecraftVersion = minecraftVersion;
            this.loaderVersion = loaderVersion;
            this.ramMb = ramMb;
            this.jvmArgs = "";
            this.width = 1280;
            this.height = 720;
        }
    }

    public static final class ServerShortcut {
        public String name;
        public String address;

        public ServerShortcut() { }

        public ServerShortcut(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }

    public Optional<GameProfile> profile(String name) {
        return profiles.stream().filter(p -> p.name.equals(name)).findFirst();
    }

    public GameProfile selectedProfile() {
        return profile(selectedProfile).orElseGet(() -> profiles.get(0));
    }

    public static LauncherSettings load(BuildInfo build) {
        LauncherSettings s = JsonFiles.read(LauncherPaths.settingsFile())
                .map(o -> JsonFiles.GSON.fromJson(o, LauncherSettings.class))
                .orElseGet(LauncherSettings::new);
        if (s.profiles == null) s.profiles = new ArrayList<>();
        if (s.servers == null) s.servers = new ArrayList<>();
        if (s.profiles.isEmpty()) {
            long maxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
            int ram = (int) Math.max(2048, Math.min(4096, totalSystemMemoryMb() / 4));
            s.profiles.add(new GameProfile("Velora " + build.minecraftVersion(), build.minecraftVersion(), build.loaderVersion(), ram));
            s.selectedProfile = s.profiles.get(0).name;
            LOGGER.debug("Created default profile (launcher heap {} MB)", maxMb);
        }
        if (s.servers.isEmpty()) s.servers.add(new ServerShortcut("Lokaler Server", "localhost"));
        if (s.microsoftClientId == null || s.microsoftClientId.isBlank()) s.microsoftClientId = build.defaultClientId();
        return s;
    }

    public void save() {
        try {
            JsonObject json = JsonFiles.GSON.toJsonTree(this).getAsJsonObject();
            JsonFiles.write(LauncherPaths.settingsFile(), json);
        } catch (IOException e) {
            LOGGER.error("Could not save launcher settings", e);
        }
    }

    public static long totalSystemMemoryMb() {
        try {
            var bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean os) return os.getTotalMemorySize() / (1024 * 1024);
        } catch (RuntimeException | LinkageError ignored) {
            // fall through
        }
        return 8192;
    }
}
