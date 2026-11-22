package dev.velora.client;

import com.google.gson.JsonObject;
import dev.velora.core.VeloraCore;
import dev.velora.core.backend.BackendClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Optional backend features that need a logged-in player (equipping cosmetics, cloud config sync).
 * Authentication uses the Mojang session server join/hasJoined handshake – the Minecraft access token
 * is only ever sent to Mojang (inside authlib), never to the Velora backend.
 */
public final class BackendSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Backend");
    private static volatile String token;
    private static volatile String tokenBackend = "";

    private BackendSync() { }

    private static BackendClient backend() {
        BackendClient b = VeloraCore.get().backend();
        if (b == null || !b.isEnabled()) throw new IllegalStateException("Backend ist deaktiviert (Velora-Menü → Einstellungen)");
        return b;
    }

    /** Session token for the current backend; authenticates on first use. */
    public static CompletableFuture<String> token() {
        BackendClient b;
        try {
            b = backend();
        } catch (IllegalStateException e) {
            return CompletableFuture.failedFuture(e);
        }
        if (token != null && tokenBackend.equals(b.getBaseUrl())) return CompletableFuture.completedFuture(token);
        MinecraftClient mc = MinecraftClient.getInstance();
        Session session = mc.getSession();
        UUID uuid = session.getUuidOrNull();
        if (session.getAccountType() != Session.AccountType.MSA || uuid == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Backend-Funktionen benötigen ein Microsoft-Konto"));
        }
        String username = session.getUsername();
        return b.requestChallenge(username).thenApply(serverId -> {
            try {
                mc.getSessionService().joinServer(uuid, session.getAccessToken(), serverId);
            } catch (Exception e) {
                throw new CompletionException(new IllegalStateException("Mojang-Sitzung konnte nicht bestätigt werden: " + e.getMessage()));
            }
            return serverId;
        }).thenCompose(serverId -> b.verify(username, serverId)).thenApply(t -> {
            token = t;
            tokenBackend = b.getBaseUrl();
            LOGGER.info("Authenticated with Velora backend {}", b.getBaseUrl());
            return t;
        });
    }

    public static void pushEquippedCape(String cosmeticId) {
        if (VeloraCore.get().backend() == null || !VeloraCore.get().backend().isEnabled()) return;
        token().thenCompose(t -> backend().equip(t, "CAPE", cosmeticId))
                .exceptionally(e -> {
                    LOGGER.warn("Could not sync equipped cape: {}", rootMessage(e));
                    return null;
                });
    }

    public static CompletableFuture<Void> uploadActiveProfile() {
        JsonObject snapshot = VeloraCore.get().config().profiles().snapshot();
        return token().thenCompose(t -> backend().uploadConfig(t, snapshot));
    }

    /** Downloads the cloud profile and stores it as a new local profile "Cloud". Returns the profile name. */
    public static CompletableFuture<String> downloadToProfile() {
        return token().thenCompose(t -> backend().downloadConfig(t)).thenApply((Optional<JsonObject> cfg) -> {
            JsonObject json = cfg.orElseThrow(() -> new CompletionException(new IllegalStateException("Keine Cloud-Konfiguration gespeichert")));
            try {
                var profiles = VeloraCore.get().config().profiles();
                String name = profiles.exists("Cloud") ? profiles.uniqueName("Cloud") : "Cloud";
                if (name.equals(profiles.getActive())) name = profiles.uniqueName("Cloud");
                profiles.writeProfile(name, json);
                return name;
            } catch (java.io.IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    public static String rootMessage(Throwable t) {
        while (t.getCause() != null && (t instanceof CompletionException || t instanceof java.util.concurrent.ExecutionException)) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
