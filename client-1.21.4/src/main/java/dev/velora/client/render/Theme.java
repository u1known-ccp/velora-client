package dev.velora.client.render;

import dev.velora.core.VeloraCore;
import dev.velora.core.util.ColorUtil;

/** Velora dark theme. The accent color comes from the client settings and can be changed live. */
public final class Theme {
    public static final int BACKDROP = 0xB0050608;
    public static final int PANEL = 0xF20E0F13;
    public static final int SIDEBAR = 0xFF111319;
    public static final int SURFACE = 0xFF171A21;
    public static final int SURFACE_HOVER = 0xFF1E222B;
    public static final int SURFACE_ACTIVE = 0xFF252A35;
    public static final int BORDER = 0xFF262A35;
    public static final int TEXT = 0xFFEDEFF5;
    public static final int TEXT_DIM = 0xFF9AA0B4;
    public static final int TEXT_MUTED = 0xFF5F6578;
    public static final int SUCCESS = 0xFF3DDC97;
    public static final int WARNING = 0xFFFFC857;
    public static final int DANGER = 0xFFFF5C7A;
    public static final int SHADOW = 0xFF000000;

    public static final float RADIUS_LARGE = 8f;
    public static final float RADIUS = 5f;
    public static final float RADIUS_SMALL = 3f;

    private Theme() { }

    public static int accent() {
        return VeloraCore.isReady() ? VeloraCore.get().config().client().accentColor.getRenderColor() | 0xFF000000 : 0xFF7C5CFF;
    }

    public static int accentDim() { return ColorUtil.darken(accent(), 0.45f); }

    public static int accentSoft() { return ColorUtil.withAlpha(accent(), 0x40); }

    public static boolean animations() {
        return !VeloraCore.isReady() || VeloraCore.get().config().client().animations.isOn();
    }

    /** Animation duration scaled by the user's speed setting (0 when animations are disabled). */
    public static long duration(long baseMs) {
        if (!VeloraCore.isReady()) return baseMs;
        var c = VeloraCore.get().config().client();
        if (!c.animations.isOn()) return 1;
        return Math.max(1, Math.round(baseMs / c.animationSpeed.getDouble()));
    }
}
