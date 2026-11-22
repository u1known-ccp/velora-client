package dev.velora.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.velora.launcher.LauncherPaths;
import dev.velora.launcher.auth.Account;
import dev.velora.launcher.config.LauncherSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds the command line from the version JSON (rules, placeholders) and starts the game process. */
public final class GameLauncher {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z_]+)}");

    public Process launch(LaunchSpec spec, Account account, LauncherSettings.GameProfile profile, JavaManager.JavaInstall java,
                          String clientId, String launcherVersion, String quickPlayServer, Consumer<String> log) throws IOException {
        Files.createDirectories(spec.gameDir());
        String sep = System.getProperty("path.separator");
        StringBuilder cp = new StringBuilder();
        for (Path p : spec.classpath()) {
            if (!cp.isEmpty()) cp.append(sep);
            cp.append(realPath(p));
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("natives_directory", spec.nativesDir().toAbsolutePath().toString());
        vars.put("launcher_name", "Velora");
        vars.put("launcher_version", launcherVersion);
        vars.put("classpath", cp.toString());
        vars.put("classpath_separator", sep);
        vars.put("library_directory", LauncherPaths.libraries().toAbsolutePath().toString());
        vars.put("version_name", spec.versionId());
        vars.put("auth_player_name", account.username);
        vars.put("game_directory", spec.gameDir().toAbsolutePath().toString());
        vars.put("assets_root", LauncherPaths.assets().toAbsolutePath().toString());
        vars.put("assets_index_name", spec.assetIndex());
        vars.put("auth_uuid", account.uuid);
        vars.put("auth_access_token", account.accessToken);
        vars.put("clientid", clientId == null ? "" : clientId);
        vars.put("auth_xuid", account.xuid == null || account.xuid.isEmpty() ? "0" : account.xuid);
        vars.put("user_type", account.type == Account.Type.MICROSOFT ? "msa" : "legacy");
        vars.put("version_type", "release");
        vars.put("resolution_width", Integer.toString(profile.width > 0 ? profile.width : 1280));
        vars.put("resolution_height", Integer.toString(profile.height > 0 ? profile.height : 720));
        vars.put("quickPlayMultiplayer", quickPlayServer == null ? "" : quickPlayServer);

        Map<String, Boolean> features = new HashMap<>();
        features.put("has_custom_resolution", true);
        features.put("is_quick_play_multiplayer", quickPlayServer != null && !quickPlayServer.isBlank());

        List<String> cmd = new ArrayList<>();
        cmd.add(java.executable().toString());
        cmd.add("-Xmx" + profile.ramMb + "M");
        cmd.add("-Xms" + Math.min(profile.ramMb, 1024) + "M");
        cmd.add("-XX:+UseG1GC");
        cmd.add("-XX:MaxGCPauseMillis=50");
        cmd.add("-Dvelora.launcher=" + launcherVersion);
        // Note: Mojang's log4j config (spec.loggingArgument()) emits XML events meant for the official launcher.
        // We keep the game's built-in plain-text console format so the Logs page stays readable.
        if (profile.jvmArgs != null && !profile.jvmArgs.isBlank()) {
            for (String a : profile.jvmArgs.trim().split("\\s+")) cmd.add(a);
        }
        appendArgs(cmd, spec.jvmArguments(), vars, features);
        cmd.add(spec.mainClass());
        appendArgs(cmd, spec.gameArguments(), vars, features);

        // log the command line without the access token and without the (very long) classpath
        String printable = String.join(" ", cmd).replace(cp.toString(), "<classpath>");
        if (account.accessToken.length() > 8) printable = printable.replace(account.accessToken, "<token>");
        log.accept("[Velora] Starte: " + printable);

        ProcessBuilder pb = new ProcessBuilder(cmd).directory(spec.gameDir().toFile()).redirectErrorStream(true);
        Process process = pb.start();
        pump(process.getInputStream(), log);
        return process;
    }

    /**
     * Fabric Loader ≥ 0.20 identifies its own libraries by their real (canonical) path. If the classpath used a
     * different spelling of the same file (symlinks, redirected/virtualised AppData folders), Mixin would be loaded
     * twice and the game crashes – so we always pass the real path.
     */
    private static String realPath(Path p) {
        try {
            return p.toRealPath().toString();
        } catch (IOException e) {
            return p.toAbsolutePath().toString();
        }
    }

    private static void appendArgs(List<String> cmd, JsonArray args, Map<String, String> vars, Map<String, Boolean> features) {
        for (JsonElement e : args) {
            if (e.isJsonPrimitive()) {
                cmd.add(replace(e.getAsString(), vars));
            } else if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                if (!Rules.allowed(o.has("rules") ? o.getAsJsonArray("rules") : null, features)) continue;
                JsonElement value = o.get("value");
                if (value.isJsonArray()) {
                    for (JsonElement v : value.getAsJsonArray()) cmd.add(replace(v.getAsString(), vars));
                } else {
                    cmd.add(replace(value.getAsString(), vars));
                }
            }
        }
    }

    private static String replace(String arg, Map<String, String> vars) {
        Matcher m = PLACEHOLDER.matcher(arg);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = vars.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static void pump(InputStream in, Consumer<String> log) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) log.accept(line);
            } catch (IOException ignored) {
                // process ended
            }
        }, "Velora-GameLog");
        t.setDaemon(true);
        t.start();
    }
}
