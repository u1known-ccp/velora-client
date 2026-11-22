package dev.velora.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.velora.core.util.ColorUtil;

/** ARGB color with optional animated "chroma" (rainbow) mode. */
public class ColorSetting extends Setting<Integer> {
    private final boolean allowAlpha;
    private boolean chroma;

    public ColorSetting(String id, String name, String description, int defaultArgb) {
        this(id, name, description, defaultArgb, true);
    }

    public ColorSetting(String id, String name, String description, int defaultArgb, boolean allowAlpha) {
        super(id, name, description, defaultArgb);
        this.allowAlpha = allowAlpha;
    }

    public boolean allowsAlpha() { return allowAlpha; }

    public boolean isChroma() { return chroma; }

    public void setChroma(boolean chroma) {
        if (this.chroma == chroma) return;
        this.chroma = chroma;
        fireChanged();
    }

    /** The raw stored color. */
    public int getArgb() { return value; }

    /** The color to render right now (applies chroma animation, keeps the stored alpha). */
    public int getRenderColor() {
        if (!chroma) return value;
        int rainbow = ColorUtil.rainbow(4000L, 0L, 0.75f, 1f);
        return ColorUtil.withAlpha(rainbow, ColorUtil.alpha(value));
    }

    @Override
    protected Integer sanitize(Integer input) {
        if (input == null) return getDefault();
        return allowAlpha ? input : (input | 0xFF000000);
    }

    @Override
    public JsonElement toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("argb", String.format("#%08X", value));
        o.addProperty("chroma", chroma);
        return o;
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonObject()) {
            JsonObject o = element.getAsJsonObject();
            try {
                String hex = o.has("argb") ? o.get("argb").getAsString() : null;
                if (hex != null && hex.startsWith("#")) {
                    set((int) Long.parseLong(hex.substring(1), 16));
                } else {
                    reset();
                }
            } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException e) {
                reset();
            }
            chroma = o.has("chroma") && o.get("chroma").isJsonPrimitive() && o.get("chroma").getAsBoolean();
        } else {
            reset();
            chroma = false;
        }
    }
}
