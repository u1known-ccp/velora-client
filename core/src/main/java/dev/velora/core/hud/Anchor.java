package dev.velora.core.hud;

/**
 * Screen anchor of a HUD element. The element's point with the same relative coordinates
 * is placed at the anchor, so a TOP_RIGHT element grows to the left, a BOTTOM element grows upwards, etc.
 */
public enum Anchor {
    TOP_LEFT(0f, 0f), TOP_CENTER(0.5f, 0f), TOP_RIGHT(1f, 0f),
    MIDDLE_LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), MIDDLE_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f), BOTTOM_CENTER(0.5f, 1f), BOTTOM_RIGHT(1f, 1f);

    public final float fx;
    public final float fy;

    Anchor(float fx, float fy) {
        this.fx = fx;
        this.fy = fy;
    }

    /** Picks the anchor for an element whose center lies at the given relative screen position. */
    public static Anchor nearest(float relX, float relY) {
        int col = relX < 1f / 3f ? 0 : relX > 2f / 3f ? 2 : 1;
        int row = relY < 1f / 3f ? 0 : relY > 2f / 3f ? 2 : 1;
        return values()[row * 3 + col];
    }
}
