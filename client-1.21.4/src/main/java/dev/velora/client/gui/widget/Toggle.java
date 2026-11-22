package dev.velora.client.gui.widget;

import dev.velora.client.render.Render2D;
import dev.velora.client.render.Theme;
import dev.velora.core.util.Animation;
import dev.velora.core.util.ColorUtil;
import dev.velora.core.util.Easing;
import net.minecraft.client.gui.DrawContext;

/** Animated toggle switch (track + knob), reused by setting rows and mod cards. */
public final class Toggle {
    public static final int WIDTH = 22;
    public static final int HEIGHT = 12;

    private final Animation anim;

    public Toggle(boolean initial) {
        anim = new Animation(Theme.duration(160), Easing.EASE_OUT_CUBIC, initial ? 1 : 0);
    }

    public void render(DrawContext ctx, float x, float y, boolean on) {
        anim.animateTo(on ? 1 : 0);
        float t = anim.getFloat();
        int track = ColorUtil.lerp(Theme.SURFACE_ACTIVE, Theme.accent(), t);
        Render2D.roundedRect(ctx, x, y, WIDTH, HEIGHT, HEIGHT / 2f, track);
        float knob = HEIGHT - 4;
        float kx = x + 2 + (WIDTH - 4 - knob) * t;
        Render2D.circle(ctx, kx + knob / 2f, y + HEIGHT / 2f, knob / 2f, ColorUtil.lerp(0xFFB8BDCC, 0xFFFFFFFF, t));
    }
}
