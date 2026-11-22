package dev.velora.launcher.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Versions baked into the launcher at build time (launcher.properties). */
public record BuildInfo(String version, String minecraftVersion, String loaderVersion, String fabricApiVersion, String defaultClientId) {

    public static BuildInfo load() {
        Properties p = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream("/velora/launcher.properties")) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {
            // defaults below
        }
        return new BuildInfo(
                p.getProperty("version", "dev"),
                p.getProperty("minecraft_version", "1.21.4"),
                p.getProperty("loader_version", "0.16.14"),
                p.getProperty("fabric_version", "0.119.4+1.21.4"),
                p.getProperty("microsoft_client_id", ""));
    }
}
