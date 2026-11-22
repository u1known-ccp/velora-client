package dev.velora.core.hud;

import com.google.gson.JsonObject;

/**
 * Resolution independent position + scale of a HUD element.
 * Stored as anchor + pixel offset in GUI coordinates so elements stay in place when the window or GUI scale changes.
 */
public final class HudPosition {
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 3.0f;

    private Anchor anchor;
    private float offsetX;
    private float offsetY;
    private float scale;

    private final Anchor defaultAnchor;
    private final float defaultOffsetX;
    private final float defaultOffsetY;

    public HudPosition(Anchor anchor, float offsetX, float offsetY) {
        this.defaultAnchor = anchor;
        this.defaultOffsetX = offsetX;
        this.defaultOffsetY = offsetY;
        reset();
    }

    public Anchor getAnchor() { return anchor; }
    public float getScale() { return scale; }

    public void setScale(float scale) {
        this.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Float.isFinite(scale) ? scale : 1f));
    }

    /** Top-left x of the element in GUI pixels, given the scaled element width. */
    public float resolveX(float screenWidth, float scaledWidth) {
        return anchor.fx * screenWidth - anchor.fx * scaledWidth + offsetX;
    }

    public float resolveY(float screenHeight, float scaledHeight) {
        return anchor.fy * screenHeight - anchor.fy * scaledHeight + offsetY;
    }

    /**
     * Moves the element so that its top-left corner is at (x, y). The best anchor is chosen
     * automatically from the element center, the offset is recomputed accordingly.
     */
    public void setAbsolute(float x, float y, float scaledWidth, float scaledHeight, float screenWidth, float screenHeight) {
        float maxX = Math.max(0, screenWidth - scaledWidth);
        float maxY = Math.max(0, screenHeight - scaledHeight);
        x = Math.max(0, Math.min(maxX, x));
        y = Math.max(0, Math.min(maxY, y));
        float centerX = (x + scaledWidth / 2f) / Math.max(1f, screenWidth);
        float centerY = (y + scaledHeight / 2f) / Math.max(1f, screenHeight);
        this.anchor = Anchor.nearest(centerX, centerY);
        this.offsetX = x - (anchor.fx * screenWidth - anchor.fx * scaledWidth);
        this.offsetY = y - (anchor.fy * screenHeight - anchor.fy * scaledHeight);
    }

    public void reset() {
        this.anchor = defaultAnchor;
        this.offsetX = defaultOffsetX;
        this.offsetY = defaultOffsetY;
        this.scale = 1f;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("anchor", anchor.name());
        o.addProperty("x", offsetX);
        o.addProperty("y", offsetY);
        o.addProperty("scale", scale);
        return o;
    }

    public void fromJson(JsonObject o) {
        if (o == null) {
            reset();
            return;
        }
        try {
            anchor = Anchor.valueOf(o.get("anchor").getAsString());
            offsetX = o.get("x").getAsFloat();
            offsetY = o.get("y").getAsFloat();
            setScale(o.has("scale") ? o.get("scale").getAsFloat() : 1f);
        } catch (RuntimeException e) {
            reset();
        }
    }
}
