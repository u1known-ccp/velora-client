package dev.velora.launcher.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Tiny JSON GET helper for metadata endpoints (piston-meta, Fabric meta). */
public final class Http {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Http() { }

    public static JsonObject getJson(String url) throws IOException {
        return JsonParser.parseString(getString(url)).getAsJsonObject();
    }

    public static JsonElement getJsonElement(String url) throws IOException {
        return JsonParser.parseString(getString(url));
    }

    public static String getString(String url) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "VeloraLauncher").GET().build();
        try {
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode() + " für " + url);
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Abgebrochen", e);
        } catch (RuntimeException e) {
            throw new IOException("Ungültige Antwort von " + url, e);
        }
    }
}
