package dev.velora.client.mods.utility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.velora.client.api.KeyListener;
import dev.velora.client.hud.HudMod;
import dev.velora.core.VeloraCore;
import dev.velora.core.config.JsonFiles;
import dev.velora.core.hud.Anchor;
import dev.velora.core.module.Category;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ButtonSetting;
import dev.velora.core.setting.KeybindSetting;
import dev.velora.core.setting.NumberSetting;
import dev.velora.core.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Waypoints per server / singleplayer world and dimension. Stored in {@code config/velora/waypoints.json}
 * (independent of profiles). Managed by hotkey and the client command {@code /waypoint}.
 * The HUD lists the nearest waypoints with distance and a direction arrow.
 */
public final class WaypointsMod extends HudMod implements KeyListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Waypoints");
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int[] COLORS = {0xFF7C5CFF, 0xFF3DDC97, 0xFFFFC857, 0xFFFF5C7A, 0xFF4FC3F7, 0xFFFF8A3D};

    public record Waypoint(String name, int x, int y, int z, String dimension, int color) { }

    private final KeybindSetting addKey = add(new KeybindSetting("add_key", "Wegpunkt setzen (Taste)", "", KeybindSetting.NONE));
    private final NumberSetting maxShown = add(new NumberSetting("max_shown", "Max. angezeigt", "", 5, 1, 15, 1));
    private final BooleanSetting showCoords = add(new BooleanSetting("show_coords", "Koordinaten anzeigen", "", false));

    private final Map<String, List<Waypoint>> byWorld = new LinkedHashMap<>();
    private boolean loaded;

    public WaypointsMod() {
        super("waypoints", "Waypoints", "Wegpunkte pro Welt mit Distanz und Richtung (/waypoint)", Category.UTILITY, false, Anchor.TOP_RIGHT, -4, 40);
        add(new ButtonSetting("clear", "Wegpunkte dieser Welt", "Löscht alle Wegpunkte der aktuellen Welt", "Löschen", () -> {
            String key = worldKey();
            if (key != null && byWorld.remove(key) != null) {
                saveWaypoints();
                UtilityMods.feedback("Alle Wegpunkte dieser Welt gelöscht.");
            }
        }));
    }

    // ------------------------------------------------------------------ storage

    private Path file() { return VeloraCore.get().config().getRoot().resolve("waypoints.json"); }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        JsonFiles.read(file()).ifPresent(root -> {
            for (String world : root.keySet()) {
                List<Waypoint> list = new ArrayList<>();
                for (JsonElement e : root.getAsJsonArray(world)) {
                    try {
                        JsonObject o = e.getAsJsonObject();
                        list.add(new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(),
                                o.get("z").getAsInt(), o.get("dimension").getAsString(), o.get("color").getAsInt()));
                    } catch (RuntimeException ex) {
                        LOGGER.warn("Skipping invalid waypoint in {}", world);
                    }
                }
                byWorld.put(world, list);
            }
        });
    }

    private void saveWaypoints() {
        JsonObject root = new JsonObject();
        byWorld.forEach((world, list) -> {
            JsonArray arr = new JsonArray();
            for (Waypoint w : list) {
                JsonObject o = new JsonObject();
                o.addProperty("name", w.name());
                o.addProperty("x", w.x());
                o.addProperty("y", w.y());
                o.addProperty("z", w.z());
                o.addProperty("dimension", w.dimension());
                o.addProperty("color", w.color());
                arr.add(o);
            }
            root.add(world, arr);
        });
        try {
            JsonFiles.write(file(), root);
        } catch (IOException e) {
            LOGGER.error("Could not save waypoints", e);
        }
    }

    /** Identifier of the current world: server address or singleplayer level name. */
    public static String worldKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;
        if (mc.isInSingleplayer() && mc.getServer() != null) return "sp:" + mc.getServer().getSaveProperties().getLevelName();
        ServerInfo info = mc.getCurrentServerEntry();
        return "mp:" + (info == null ? "unknown" : info.address.toLowerCase(Locale.ROOT));
    }

    private static String dimension() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
    }

    public List<Waypoint> current() {
        ensureLoaded();
        String key = worldKey();
        return key == null ? List.of() : byWorld.getOrDefault(key, List.of());
    }

    // ------------------------------------------------------------------ API (commands / hotkey)

    public Optional<Waypoint> addHere(String name) {
        ensureLoaded();
        MinecraftClient mc = MinecraftClient.getInstance();
        String key = worldKey();
        if (mc.player == null || key == null) return Optional.empty();
        List<Waypoint> list = byWorld.computeIfAbsent(key, k -> new ArrayList<>());
        String finalName = name == null || name.isBlank() ? "Wegpunkt " + (list.size() + 1) : name.trim();
        list.removeIf(w -> w.name().equalsIgnoreCase(finalName));
        Waypoint wp = new Waypoint(finalName, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(),
                dimension(), COLORS[list.size() % COLORS.length]);
        list.add(wp);
        saveWaypoints();
        return Optional.of(wp);
    }

    public boolean remove(String name) {
        ensureLoaded();
        String key = worldKey();
        if (key == null) return false;
        List<Waypoint> list = byWorld.get(key);
        if (list == null || !list.removeIf(w -> w.name().equalsIgnoreCase(name))) return false;
        saveWaypoints();
        return true;
    }

    @Override
    public void onKey(int code, boolean pressed) {
        if (pressed && code == addKey.getKey()) {
            addHere(null).ifPresent(w -> UtilityMods.feedback("Wegpunkt \"" + w.name() + "\" gesetzt (" + w.x() + " " + w.y() + " " + w.z() + ")"));
        }
    }

    // ------------------------------------------------------------------ HUD

    private record Line(String text, int color) { }

    private List<Line> lines(boolean preview) {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<Line> lines = new ArrayList<>();
        if (mc.player != null) {
            String dim = dimension();
            double px = mc.player.getX(), pz = mc.player.getZ();
            float yaw = mc.player.getYaw();
            List<Waypoint> list = new ArrayList<>(current().stream().filter(w -> w.dimension().equals(dim)).toList());
            list.sort(Comparator.comparingDouble(w -> sq(w.x() + 0.5 - px) + sq(w.z() + 0.5 - pz)));
            for (Waypoint w : list.subList(0, Math.min(list.size(), maxShown.getInt()))) {
                double dx = w.x() + 0.5 - px, dz = w.z() + 0.5 - pz;
                int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz + sq(w.y() - mc.player.getY())));
                float target = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float rel = MathHelper.wrapDegrees(target - yaw);
                String arrow = ARROWS[Math.floorMod(Math.round(rel / 45f), 8)];
                String coords = showCoords.isOn() ? " (" + w.x() + " " + w.y() + " " + w.z() + ")" : "";
                lines.add(new Line(arrow + " " + w.name() + " §7" + dist + "m" + coords, w.color()));
            }
        }
        if (lines.isEmpty() && preview) {
            lines.add(new Line("↑ Basis §7128m", COLORS[0]));
            lines.add(new Line("↗ Mine §742m", COLORS[1]));
        }
        return lines;
    }

    private static double sq(double v) { return v * v; }

    @Override
    public boolean hasContent() { return !lines(false).isEmpty(); }

    @Override
    public int getWidth(boolean preview) {
        int w = 0;
        for (Line l : lines(preview)) w = Math.max(w, textWidth(l.text()));
        return w + 10;
    }

    @Override
    public int getHeight(boolean preview) { return Math.max(1, lines(preview).size()) * 10 + 6; }

    @Override
    protected void renderContent(DrawContext ctx, float tickDelta, boolean preview) {
        int y = 4;
        for (Line l : lines(preview)) {
            drawString(ctx, l.text(), 5, y, faded(ColorUtil.withAlpha(l.color(), 255)));
            y += 10;
        }
    }
}
