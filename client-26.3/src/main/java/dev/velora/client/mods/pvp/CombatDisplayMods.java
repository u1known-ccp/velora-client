package dev.velora.client.mods.pvp;

import dev.velora.client.hud.TextHudMod;
import dev.velora.core.hud.Anchor;
import dev.velora.core.module.Category;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.NumberSetting;

import java.util.Locale;

public final class CombatDisplayMods {
    private CombatDisplayMods() { }

    public static final class ComboCounterMod extends TextHudMod {
        private final BooleanSetting hideZero = add(new BooleanSetting("hide_zero", "Bei 0 ausblenden", "", false));

        public ComboCounterMod() {
            super("combo_counter", "Combo Counter", "Treffer in Folge ohne selbst getroffen zu werden", Category.PVP, false, Anchor.MIDDLE_RIGHT, -4, 56);
        }

        @Override protected String label() { return "Combo"; }

        @Override protected boolean labelAfterValue() { return true; }

        @Override
        protected String value(boolean preview) {
            int c = PvpTracker.combo();
            return Integer.toString(preview && c == 0 ? 3 : c);
        }

        @Override
        public boolean hasContent() { return !hideZero.isOn() || PvpTracker.combo() > 0; }

        @Override protected long refreshIntervalMs() { return 50; }
    }

    public static final class ReachDisplayMod extends TextHudMod {
        private final NumberSetting fadeAfter = add(new NumberSetting("fade_after", "Ausblenden nach", "0 = immer anzeigen", 3, 0, 10, 1, "s"));

        public ReachDisplayMod() {
            super("reach_display", "Reach Display", "Zeigt die Distanz deines letzten Schlags (nur Anzeige)", Category.PVP, false, Anchor.MIDDLE_RIGHT, -4, 74);
        }

        @Override protected String label() { return "Reach"; }

        @Override
        protected String value(boolean preview) {
            double r = PvpTracker.lastReach();
            if (r < 0) return preview ? "2.87 m" : "-";
            return String.format(Locale.ROOT, "%.2f m", r);
        }

        @Override
        public boolean hasContent() {
            if (fadeAfter.getInt() == 0) return true;
            return PvpTracker.lastReach() >= 0 && PvpTracker.lastReachAgeMs() < fadeAfter.getInt() * 1000L;
        }

        @Override protected long refreshIntervalMs() { return 50; }
    }
}
