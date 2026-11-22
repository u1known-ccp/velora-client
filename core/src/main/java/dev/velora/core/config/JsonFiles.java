package dev.velora.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Safe JSON file IO: atomic writes, corrupted files are backed up instead of crashing the client. */
public final class JsonFiles {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Config");
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonFiles() { }

    public static Optional<JsonObject> read(Path file) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement element = JsonParser.parseString(text);
            if (element.isJsonObject()) return Optional.of(element.getAsJsonObject());
            LOGGER.warn("Config {} is not a JSON object, ignoring", file);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.error("Failed to read {}, backing it up and using defaults", file, e);
            backupCorrupted(file);
        }
        return Optional.empty();
    }

    public static void write(Path file, JsonElement json) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void backupCorrupted(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            LOGGER.error("Could not back up corrupted config {}", file, ex);
        }
    }
}
