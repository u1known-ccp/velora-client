package dev.velora.client.mods.hud;

import dev.velora.client.hud.TextHudMod;
import dev.velora.core.hud.Anchor;
import dev.velora.core.module.Category;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ModeSetting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/** Small single-value HUD elements. Each is a full module with its own settings and position. */
public final class SimpleInfoMods {
    private SimpleInfoMods() { }

    public static final class PingMod extends TextHudMod {
        public PingMod() {
            super("ping", "Ping Display", "Latenz zum Server (laut Spielerliste)", Category.HUD, false, Anchor.TOP_LEFT, 4, 92);
        }

        @Override protected String label() { return "Ping"; }

        @Override
        protected String value(boolean preview) {
            if (MC.getConnection() == null || MC.player == null) return preview ? "42 ms" : "-";
            if (MC.isLocalServer()) return "0 ms";
            PlayerInfo entry = MC.getConnection().getPlayerInfo(MC.player.getUUID());
            return entry == null ? "-" : entry.getLatency() + " ms";
        }

        @Override protected long refreshIntervalMs() { return 1000; }
    }

    public static final class MemoryMod extends TextHudMod {
        private final ModeSetting mode = add(new ModeSetting("mode", "Anzeige", "", "Prozent", "Prozent", "MB", "Beides"));

        public MemoryMod() {
            super("memory", "Memory Usage", "Belegter Arbeitsspeicher der JVM", Category.HUD, false, Anchor.TOP_LEFT, 4, 110);
        }

        @Override protected String label() { return "RAM"; }

        @Override
        protected String value(boolean preview) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long max = rt.maxMemory() / (1024 * 1024);
            int pct = (int) (used * 100 / Math.max(1, max));
            return switch (mode.get()) {
                case "MB" -> used + "/" + max + " MB";
                case "Beides" -> pct + "% (" + used + " MB)";
                default -> pct + "%";
            };
        }

        @Override public boolean requiresWorld() { return false; }

        @Override protected long refreshIntervalMs() { return 500; }
    }

    public static final class SpeedometerMod extends TextHudMod {
        private final BooleanSetting includeVertical = add(new BooleanSetting("vertical", "Vertikal einbeziehen", "", false));
        private Vec3 last;
        private double smoothed;
        private long lastSample;

        public SpeedometerMod() {
            super("speedometer", "Speedometer", "Geschwindigkeit in Blöcken pro Sekunde", Category.HUD, false, Anchor.TOP_LEFT, 4, 128);
        }

        @Override protected String label() { return "Speed"; }

        @Override
        protected String value(boolean preview) {
            if (MC.player == null) return "0.00 m/s";
            Vec3 pos = MC.player.position();
            long now = System.nanoTime();
            if (last != null && lastSample != 0) {
                double dt = (now - lastSample) / 1e9;
                if (dt > 0) {
                    double dx = pos.x - last.x, dz = pos.z - last.z, dy = includeVertical.isOn() ? pos.y - last.y : 0;
                    double speed = Math.sqrt(dx * dx + dy * dy + dz * dz) / dt;
                    smoothed = smoothed * 0.6 + speed * 0.4;
                }
            }
            last = pos;
            lastSample = now;
            return String.format(Locale.ROOT, "%.2f m/s", smoothed);
        }

        @Override protected long refreshIntervalMs() { return 150; }
    }

    public static final class BiomeMod extends TextHudMod {
        public BiomeMod() {
            super("biome", "Biome Display", "Aktuelles Biom", Category.HUD, false, Anchor.TOP_LEFT, 4, 146);
        }

        @Override protected String label() { return "Biom"; }

        @Override
        protected String value(boolean preview) {
            if (MC.level == null || MC.player == null) return "Ebene";
            Optional<ResourceKey<Biome>> key = MC.level.getBiome(MC.player.blockPosition()).unwrapKey();
            if (key.isEmpty()) return "?";
            Identifier id = key.get().identifier();
            return Component.translatable("biome." + id.getNamespace() + "." + id.getPath()).getString();
        }

        @Override protected long refreshIntervalMs() { return 500; }
    }

    public static final class ClockMod extends TextHudMod {
        private final BooleanSetting twentyFour = add(new BooleanSetting("24h", "24-Stunden-Format", "", true));
        private final BooleanSetting seconds = add(new BooleanSetting("seconds", "Sekunden", "", false));

        public ClockMod() {
            super("clock", "Clock", "Echtzeit-Uhr", Category.HUD, false, Anchor.TOP_RIGHT, -4, 4);
            showLabel.set(false);
        }

        @Override protected String label() { return "Zeit"; }

        @Override
        protected String value(boolean preview) {
            String pattern = (twentyFour.isOn() ? "HH:mm" : "hh:mm") + (seconds.isOn() ? ":ss" : "") + (twentyFour.isOn() ? "" : " a");
            return LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.GERMANY));
        }

        @Override public boolean requiresWorld() { return false; }

        @Override protected long refreshIntervalMs() { return 500; }
    }

    public static final class ServerInfoMod extends TextHudMod {
        private final BooleanSetting showPlayers = add(new BooleanSetting("show_players", "Spieleranzahl", "", true));

        public ServerInfoMod() {
            super("server_info", "Server Information", "Server-Adresse und Spieleranzahl", Category.UTILITY, false, Anchor.TOP_RIGHT, -4, 22);
        }

        @Override protected String label() { return "Server"; }

        @Override
        protected String value(boolean preview) {
            if (MC.getConnection() == null) return preview ? "play.example.net (24)" : "-";
            String address;
            if (MC.isLocalServer()) {
                address = "Einzelspieler";
            } else {
                ServerData info = MC.getCurrentServer();
                address = info == null ? "Unbekannt" : info.ip;
            }
            if (!showPlayers.isOn() || MC.isLocalServer()) return address;
            return address + " (" + MC.getConnection().getOnlinePlayers().size() + ")";
        }

        @Override protected long refreshIntervalMs() { return 1000; }
    }
}
