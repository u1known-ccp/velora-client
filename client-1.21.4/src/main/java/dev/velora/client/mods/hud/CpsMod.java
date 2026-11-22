package dev.velora.client.mods.hud;

import dev.velora.client.hud.TextHudMod;
import dev.velora.client.input.ClickTracker;
import dev.velora.core.hud.Anchor;
import dev.velora.core.module.Category;
import dev.velora.core.setting.BooleanSetting;

public final class CpsMod extends TextHudMod {
    private final BooleanSetting showRight = add(new BooleanSetting("show_right", "Rechtsklicks anzeigen", "", true));

    public CpsMod() {
        super("cps", "CPS Counter", "Klicks pro Sekunde (links | rechts)", Category.HUD, false, Anchor.TOP_LEFT, 4, 22);
    }

    @Override protected String label() { return "CPS"; }

    @Override protected boolean labelAfterValue() { return true; }

    @Override
    protected String value(boolean preview) {
        int left = ClickTracker.leftCps();
        int right = ClickTracker.rightCps();
        if (preview && left == 0 && right == 0) {
            left = 12;
            right = 4;
        }
        return showRight.isOn() ? left + " | " + right : Integer.toString(left);
    }

    @Override protected long refreshIntervalMs() { return 50; }
}
