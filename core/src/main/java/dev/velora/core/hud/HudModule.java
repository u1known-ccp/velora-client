package dev.velora.core.hud;

import com.google.gson.JsonObject;
import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ColorSetting;
import dev.velora.core.setting.NumberSetting;

/**
 * A module that owns a HUD element. Holds the platform independent layout and appearance;
 * the client implementation draws it.
 */
public abstract class HudModule extends Module {
    private final HudPosition position;

    protected final BooleanSetting background;
    protected final ColorSetting backgroundColor;
    protected final ColorSetting textColor;
    protected final BooleanSetting textShadow;
    protected final NumberSetting opacity;

    protected HudModule(String id, String name, String description, Category category, boolean enabledByDefault,
                        Anchor anchor, float offsetX, float offsetY) {
        super(id, name, description, category, enabledByDefault);
        this.position = new HudPosition(anchor, offsetX, offsetY);
        this.background = add(new BooleanSetting("background", "Hintergrund", "Halbtransparenten Hintergrund zeichnen", true));
        this.backgroundColor = add(new ColorSetting("background_color", "Hintergrundfarbe", "", 0x6E0E0F13));
        this.backgroundColor.visibleWhen(background::isOn);
        this.textColor = add(new ColorSetting("text_color", "Textfarbe", "", 0xFFFFFFFF));
        this.textShadow = add(new BooleanSetting("text_shadow", "Textschatten", "", true));
        this.opacity = add(new NumberSetting("opacity", "Deckkraft", "Gesamte Transparenz des Elements", 100, 10, 100, 5, "%"));
    }

    public HudPosition getPosition() { return position; }

    public BooleanSetting background() { return background; }
    public ColorSetting backgroundColor() { return backgroundColor; }
    public ColorSetting textColor() { return textColor; }
    public BooleanSetting textShadow() { return textShadow; }

    /** 0..1 overall opacity multiplier. */
    public float getOpacity() { return opacity.getFloat() / 100f; }

    /** Notify the config system that the layout changed (called by the HUD editor). */
    public void markLayoutChanged() {
        layoutDirty.run();
    }

    private Runnable layoutDirty = () -> { };

    public void setLayoutDirtyHook(Runnable hook) { this.layoutDirty = hook == null ? () -> { } : hook; }

    @Override
    public void resetSettings() {
        super.resetSettings();
        position.reset();
        layoutDirty.run();
    }

    @Override
    protected void saveExtra(JsonObject o) {
        o.add("hud", position.toJson());
    }

    @Override
    protected void loadExtra(JsonObject o) {
        position.fromJson(o != null && o.has("hud") && o.get("hud").isJsonObject() ? o.getAsJsonObject("hud") : null);
    }
}
