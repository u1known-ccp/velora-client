package dev.velora.launcher.game;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Parallel downloader with hash verification, retries and atomic file replacement.
 * Existing files with matching hash (or size, when no hash is known) are skipped.
 */
public final class Downloader {
    public record Task(String url, Path target, String sha1, long size) { }

    public interface Progress {
        void update(long doneBytes, long totalBytes, int doneFiles, int totalFiles);
    }

    private static final int THREADS = 12;
    private static final int RETRIES = 3;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public void downloadAll(List<Task> tasks, Progress progress, BooleanSupplier cancelled) throws IOException {
        List<Task> todo = new ArrayList<>();
        long total = 0;
        for (Task t : tasks) {
            if (!isValid(t)) {
                todo.add(t);
                total += Math.max(0, t.size());
            }
        }
        if (todo.isEmpty()) {
            progress.update(1, 1, tasks.size(), tasks.size());
            return;
        }
        AtomicLong doneBytes = new AtomicLong();
        AtomicLong doneFiles = new AtomicLong();
        final long totalBytes = Math.max(1, total);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS, r -> {
            Thread th = new Thread(r, "Velora-Download");
            th.setDaemon(true);
            return th;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Task t : todo) {
                futures.add(pool.submit(() -> {
                    if (cancelled.getAsBoolean()) return null;
                    download(t);
                    doneBytes.addAndGet(Math.max(0, t.size()));
                    progress.update(doneBytes.get(), totalBytes, (int) doneFiles.incrementAndGet(), todo.size());
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    throw cause instanceof IOException io ? io : new IOException(cause);
                }
                if (cancelled.getAsBoolean()) throw new IOException("Abgebrochen");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Abgebrochen", e);
        } finally {
            pool.shutdownNow();
        }
    }

    public static boolean isValid(Task t) {
        try {
            if (!Files.isRegularFile(t.target())) return false;
            if (t.size() > 0 && Files.size(t.target()) != t.size()) return false;
            if (t.sha1() != null && !t.sha1().isEmpty()) return t.sha1().equalsIgnoreCase(sha1(t.target()));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void download(Task t) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= RETRIES; attempt++) {
            try {
                downloadOnce(t);
                return;
            } catch (IOException e) {
                last = e;
            }
        }
        throw new IOException("Download fehlgeschlagen: " + t.url() + " (" + (last == null ? "?" : last.getMessage()) + ")", last);
    }

    private void downloadOnce(Task t) throws IOException {
        Files.createDirectories(t.target().toAbsolutePath().getParent());
        Path tmp = t.target().resolveSibling(t.target().getFileName() + ".part");
        HttpRequest req = HttpRequest.newBuilder(URI.create(t.url())).timeout(Duration.ofMinutes(5))
                .header("User-Agent", "VeloraLauncher").GET().build();
        try {
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                resp.body().close();
                throw new IOException("HTTP " + resp.statusCode());
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    digest.update(buf, 0, n);
                }
            }
            if (t.sha1() != null && !t.sha1().isEmpty()) {
                String actual = HexFormat.of().formatHex(digest.digest());
                if (!actual.equalsIgnoreCase(t.sha1())) {
                    Files.deleteIfExists(tmp);
                    throw new IOException("Prüfsumme stimmt nicht für " + t.target().getFileName());
                }
            }
            Files.move(tmp, t.target(), StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Abgebrochen", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public static String sha1(Path file) throws IOException {
        return hash(file, "SHA-1");
    }

    public static String sha256(Path file) throws IOException {
        return hash(file, "SHA-256");
    }

    private static String hash(Path file, String algorithm) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
