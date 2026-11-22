package dev.velora.launcher.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.velora.core.backend.BackendClient;
import dev.velora.core.backend.NewsItem;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** News from the backend, falling back to the news bundled with the launcher. */
public final class NewsService {
    private NewsService() { }

    public static List<NewsItem> load(BackendClient backend) {
        if (backend != null && backend.isEnabled()) {
            try {
                List<NewsItem> remote = backend.fetchNews().get(6, TimeUnit.SECONDS);
                if (!remote.isEmpty()) return remote;
            } catch (Exception ignored) {
                // offline → bundled news
            }
        }
        return bundled();
    }

    private static List<NewsItem> bundled() {
        List<NewsItem> list = new ArrayList<>();
        try (InputStream in = NewsService.class.getResourceAsStream("/velora/news.json")) {
            if (in == null) return list;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray items = root.getAsJsonArray("items");
            for (JsonElement e : items) {
                JsonObject o = e.getAsJsonObject();
                list.add(new NewsItem(o.get("title").getAsString(), o.get("body").getAsString(),
                        o.get("date").getAsString(), o.has("url") ? o.get("url").getAsString() : ""));
            }
        } catch (Exception ignored) {
            // no news
        }
        return list;
    }
}
