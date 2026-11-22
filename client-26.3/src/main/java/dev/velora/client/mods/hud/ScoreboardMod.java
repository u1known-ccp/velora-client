package dev.velora.client.mods.hud;

import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ColorSetting;
import dev.velora.core.setting.NumberSetting;

/** Customises the vanilla sidebar scoreboard (hooks in InGameHudMixin). */
public final class ScoreboardMod extends Module {
    public final BooleanSetting hide = add(new BooleanSetting("hide", "Scoreboard ausblenden", "", false));
    public final BooleanSetting hideNumbers = add(new BooleanSetting("hide_numbers", "Rote Zahlen ausblenden", "", true));
    public final NumberSetting scale = add(new NumberSetting("scale", "Größe", "", 1.0, 0.5, 1.5, 0.05, "x"));
    public final NumberSetting offsetY = add(new NumberSetting("offset_y", "Vertikaler Versatz", "", 0, -150, 150, 1, "px"));
    public final ColorSetting headerColor = add(new ColorSetting("header_color", "Titel-Hintergrund", "", 0x66000000));
    public final ColorSetting bodyColor = add(new ColorSetting("body_color", "Hintergrund", "", 0x4D000000));
    public final BooleanSetting textShadow = add(new BooleanSetting("text_shadow", "Textschatten", "", false));

    public ScoreboardMod() {
        super("scoreboard", "Scoreboard Customizer", "Scoreboard ausblenden, skalieren, Zahlen entfernen", Category.HUD, false);
    }
}
