package dev.velora.client.mods.hud;

import dev.velora.client.hud.TextHudMod;
import dev.velora.core.hud.Anchor;
import dev.velora.core.module.Category;
import dev.velora.core.setting.BooleanSetting;

public final class FpsMod extends TextHudMod {
    private final BooleanSetting colorize = add(new BooleanSetting("colorize", "Farbig nach Wert", "Rot unter 30, Gelb unter 60", false));

    public FpsMod() {
        super("fps", "FPS Counter", "Zeigt die aktuellen Bilder pro Sekunde", Category.HUD, true, Anchor.TOP_LEFT, 4, 4);
    }

    @Override protected String label() { return "FPS"; }

    @Override protected boolean labelAfterValue() { return true; }

    @Override
    protected String value(boolean preview) {
        int fps = MC.getCurrentFps();
        if (colorize.isOn()) {
            String code = fps < 30 ? "§c" : fps < 60 ? "§e" : "§a";
            return code + fps + "§r";
        }
        return Integer.toString(fps);
    }

    @Override public boolean requiresWorld() { return false; }

    @Override protected long refreshIntervalMs() { return 250; }
}
