package dev.velora.core.cosmetics;

import dev.velora.core.backend.BackendClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Decides which cosmetics a player may use and which cosmetics other players wear.
 * <ul>
 *   <li>Without backend: only {@link Cosmetic#free() free} cosmetics are unlocked, only the local player is decorated.</li>
 *   <li>With backend: owned cosmetics and other players' equipped cosmetics are fetched asynchronously and cached.</li>
 * </ul>
 * Ownership is never decided by the client alone for non-free items – the backend is the authority.
 */
public final class CosmeticsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Cosmetics");
    private static final long REMOTE_CACHE_MS = 5 * 60_000L;

    private final Supplier<BackendClient> backend;
    private final Set<String> owned = ConcurrentHashMap.newKeySet();
    private final Map<UUID, RemoteEntry> remote = new ConcurrentHashMap<>();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public CosmeticsManager(Supplier<BackendClient> backend) {
        this.backend = backend;
        resetOwnedToFree();
    }

    private void resetOwnedToFree() {
        owned.clear();
        for (Cosmetic c : CosmeticCatalog.all()) if (c.free()) owned.add(c.id());
    }

    public boolean isUnlocked(String id) { return owned.contains(id); }

    public Set<String> getOwned() { return Collections.unmodifiableSet(owned); }

    /** Refreshes ownership for the local player from the backend (no-op without backend). */
    public void refreshOwned(UUID self) {
        BackendClient client = backend.get();
        if (client == null || !client.isEnabled() || self == null) {
            resetOwnedToFree();
            return;
        }
        client.fetchOwnedCosmetics(self).whenComplete((ids, err) -> {
            if (err != null) {
                LOGGER.warn("Could not fetch owned cosmetics: {}", err.getMessage());
                return;
            }
            Set<String> fresh = new HashSet<>();
            for (Cosmetic c : CosmeticCatalog.all()) if (c.free()) fresh.add(c.id());
            fresh.addAll(ids);
            owned.clear();
            owned.addAll(fresh);
        });
    }

    /** Equipped cape of another player, if known. Triggers an async fetch when the cache is stale. */
    public Optional<String> getRemoteCape(UUID player) {
        RemoteEntry entry = remote.get(player);
        long now = System.currentTimeMillis();
        if (entry == null || now - entry.fetchedAt > REMOTE_CACHE_MS) requestRemote(Set.of(player));
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.equipped.get(CosmeticType.CAPE.name()));
    }

    public void requestRemote(Collection<UUID> players) {
        BackendClient client = backend.get();
        if (client == null || !client.isEnabled()) return;
        Set<UUID> toFetch = new HashSet<>();
        for (UUID id : players) if (inFlight.add(id)) toFetch.add(id);
        if (toFetch.isEmpty()) return;
        client.fetchEquipped(toFetch).whenComplete((result, err) -> {
            inFlight.removeAll(toFetch);
            long now = System.currentTimeMillis();
            if (err != null) {
                // cache the failure too, so we do not hammer the backend
                for (UUID id : toFetch) remote.put(id, new RemoteEntry(Map.of(), now));
                return;
            }
            for (UUID id : toFetch) remote.put(id, new RemoteEntry(result.getOrDefault(id, Map.of()), now));
        });
    }

    public void clearRemoteCache() { remote.clear(); }

    private record RemoteEntry(Map<String, String> equipped, long fetchedAt) { }
}
