package dev.velora.launcher.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Data baked into the launcher at build time: launcher version, default Microsoft client ID and the list of
 * bundled game versions (velora/versions.properties, newest first). Each game version has its own client jar
 * ({@code velora/bundled/velora-client-<mc>.bin}), Fabric Loader and Fabric API version.
 */
public record BuildInfo(String version, String defaultClientId, List<GameVersion> versions) {

    public record GameVersion(String minecraft, String loader, String fabricApi) {
        public String bundledResource() { return "/velora/bundled/velora-client-" + minecraft + ".bin"; }
    }

    public static BuildInfo load() {
        Properties p = read("/velora/launcher.properties");
        Properties v = read("/velora/versions.properties");
        List<GameVersion> versions = new ArrayList<>();
        for (String mc : v.getProperty("versions", "").split(",")) {
            String id = mc.trim();
            if (id.isEmpty()) continue;
            versions.add(new GameVersion(id, v.getProperty(id + ".loader", ""), v.getProperty(id + ".fabric_api", "")));
        }
        if (versions.isEmpty()) versions.add(new GameVersion("1.21.4", "0.16.14", "0.119.4+1.21.4")); // dev fallback
        return new BuildInfo(p.getProperty("version", "dev"), p.getProperty("microsoft_client_id", ""), List.copyOf(versions));
    }

    private static Properties read(String resource) {
        Properties p = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream(resource)) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {
            // defaults
        }
        return p;
    }

    /** The newest bundled game version (default for new profiles). */
    public GameVersion latest() { return versions.get(0); }

    public Optional<GameVersion> version(String minecraft) {
        return versions.stream().filter(g -> g.minecraft().equals(minecraft)).findFirst();
    }

    /** Convenience for update checks and display (newest version). */
    public String minecraftVersion() { return latest().minecraft(); }
}
