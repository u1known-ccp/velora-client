package dev.velora.launcher.service;

import dev.velora.core.backend.BackendClient;
import dev.velora.core.backend.UpdateInfo;
import dev.velora.launcher.LauncherPaths;
import dev.velora.launcher.game.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Optional update channel via the Velora backend. Updates are downloaded into {@code updates/}
 * and only used after their SHA-256 matches the value announced by the backend.
 * Client updates are applied automatically on the next launch; launcher updates are offered to the user.
 */
public final class UpdateService {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Updates");

    public record Result(String component, String version, Path file, String changelog) { }

    private final BackendClient backend;

    public UpdateService(BackendClient backend) {
        this.backend = backend;
    }

    public Optional<Result> check(String component, String currentVersion, String filePrefix) {
        if (backend == null || !backend.isEnabled()) return Optional.empty();
        try {
            Optional<UpdateInfo> info = backend.fetchLatestUpdate(component).get();
            if (info.isEmpty() || UpdateInfo.compareVersions(info.get().version(), currentVersion) <= 0) return Optional.empty();
            UpdateInfo u = info.get();
            if (u.url().isEmpty() || u.sha256().isEmpty()) {
                LOGGER.warn("Update {} {} has no URL or checksum – ignored", component, u.version());
                return Optional.empty();
            }
            Path target = LauncherPaths.updates().resolve(filePrefix + u.version() + ".jar");
            if (!Files.isRegularFile(target) || !Downloader.sha256(target).equalsIgnoreCase(u.sha256())) {
                Path tmp = target.resolveSibling(target.getFileName() + ".download");
                new Downloader().download(new Downloader.Task(u.url(), tmp, "", -1));
                String actual = Downloader.sha256(tmp);
                if (!actual.equalsIgnoreCase(u.sha256())) {
                    Files.deleteIfExists(tmp);
                    throw new IOException("SHA-256 des Updates stimmt nicht – verworfen");
                }
                Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return Optional.of(new Result(component, u.version(), target, u.changelog()));
        } catch (Exception e) {
            LOGGER.warn("Update check for {} failed: {}", component, e.getMessage());
            return Optional.empty();
        }
    }
}
