package dev.velora.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Map;

/** Evaluates Mojang "rules" arrays (os name/arch, feature flags). */
public final class Rules {
    private Rules() { }

    public static String osName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }

    public static boolean is64Bit() {
        String arch = System.getProperty("os.arch", "");
        return arch.contains("64");
    }

    public static boolean isArm() {
        return System.getProperty("os.arch", "").toLowerCase(Locale.ROOT).contains("aarch64");
    }

    public static boolean allowed(JsonArray rules, Map<String, Boolean> features) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allowed = false;
        for (JsonElement e : rules) {
            JsonObject rule = e.getAsJsonObject();
            boolean matches = true;
            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name") && !os.get("name").getAsString().equals(osName())) matches = false;
                if (os.has("arch")) {
                    String arch = os.get("arch").getAsString();
                    if (arch.equals("x86") && is64Bit()) matches = false;
                }
            }
            if (rule.has("features")) {
                JsonObject f = rule.getAsJsonObject("features");
                for (String key : f.keySet()) {
                    if (features.getOrDefault(key, false) != f.get(key).getAsBoolean()) matches = false;
                }
            }
            if (matches) allowed = rule.get("action").getAsString().equals("allow");
        }
        return allowed;
    }
}
