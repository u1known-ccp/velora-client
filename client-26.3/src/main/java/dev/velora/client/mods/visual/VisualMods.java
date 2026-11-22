package dev.velora.client.mods.visual;

import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ModeSetting;
import dev.velora.core.setting.NumberSetting;

/** Visual-only modules whose logic lives in mixins (see dev.velora.client.mixin). */
public final class VisualMods {
    private VisualMods() { }

    /** Overrides the gamma used to build the lightmap. The options file is never touched. */
    public static final class FullbrightMod extends Module {
        public final NumberSetting gamma = add(new NumberSetting("gamma", "Helligkeit", "100% = Vanilla \"Hell\"", 1000, 100, 1500, 50, "%"));

        public FullbrightMod() {
            super("fullbright", "Fullbright", "Alles hell – auch in Höhlen und nachts", Category.VISUAL, false);
        }

        public float gamma() { return gamma.getFloat() / 100f; }
    }

    /** Client-side visual time of day (the server time is not changed). */
    public static final class TimeChangerMod extends Module {
        public final ModeSetting preset = add(new ModeSetting("preset", "Tageszeit", "", "Mittag", "Morgen", "Mittag", "Abend", "Mitternacht", "Eigene"));
        public final NumberSetting custom = add(new NumberSetting("custom", "Eigene Zeit", "0 = 6 Uhr morgens", 6000, 0, 23999, 100, " t"));
        public final BooleanSetting cycle = add(new BooleanSetting("cycle", "Zeit läuft weiter", "Ab der gewählten Zeit normal weiterlaufen", false));
        private long enabledAtNanos;

        public TimeChangerMod() {
            super("time_changer", "Time Changer", "Eigene Tageszeit (nur für dich sichtbar)", Category.VISUAL, false);
            custom.visibleWhen(() -> preset.is("Eigene"));
        }

        @Override
        protected void onEnable() { enabledAtNanos = System.nanoTime(); }

        public long timeOfDay(long realTime) {
            long base = switch (preset.get()) {
                case "Morgen" -> 0;
                case "Abend" -> 12000;
                case "Mitternacht" -> 18000;
                case "Eigene" -> custom.getInt();
                default -> 6000;
            };
            if (cycle.isOn()) base += (System.nanoTime() - enabledAtNanos) / 50_000_000L;
            // keep the moon phase of the real world time
            return (realTime / 24000L) * 24000L + Math.floorMod(base, 24000L);
        }
    }

    /** Client-side visual weather. */
    public static final class WeatherChangerMod extends Module {
        public final ModeSetting weather = add(new ModeSetting("weather", "Wetter", "", "Klar", "Klar", "Regen", "Gewitter"));

        public WeatherChangerMod() {
            super("weather_changer", "Weather Changer", "Regen ausblenden oder erzwingen (nur visuell)", Category.VISUAL, false);
        }

        public float rain() { return weather.is("Klar") ? 0f : 1f; }

        public float thunder() { return weather.is("Gewitter") ? 1f : 0f; }
    }

    /** Shows your own nametag in third person. */
    public static final class NametagsMod extends Module {
        public final BooleanSetting showOwn = add(new BooleanSetting("show_own", "Eigenen Nametag zeigen", "In der 3rd-Person-Ansicht", true));

        public NametagsMod() {
            super("nametags", "Nametags", "Eigener Nametag in der 3rd Person", Category.VISUAL, false);
        }
    }
}
