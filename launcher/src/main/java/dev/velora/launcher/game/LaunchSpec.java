package dev.velora.launcher.game;

import com.google.gson.JsonArray;

import java.nio.file.Path;
import java.util.List;

/** Everything needed to start the game after installation. */
public record LaunchSpec(
        String versionId,
        String mainClass,
        List<Path> classpath,
        JsonArray jvmArguments,
        JsonArray gameArguments,
        String loggingArgument,
        String assetIndex,
        Path gameDir,
        Path nativesDir,
        int requiredJava) {
}
