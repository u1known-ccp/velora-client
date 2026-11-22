package dev.velora.client.gui;

import dev.velora.client.render.Render2D;
import dev.velora.client.render.Theme;
import dev.velora.core.util.Animation;
import dev.velora.core.util.ColorUtil;
import dev.velora.core.util.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

/** Small reusable UI pieces for Velora screens. */
public final class UiParts {
    private UiParts() { }

    /** Self drawn button with hover animation. */
    public static final class Button {
        public int x, y, w, h;
        public String label;
        public boolean primary;
        public boolean enabled = true;
        private final Runnable action;
        private final Animation hover = new Animation(Theme.duration(120), Easing.EASE_OUT_CUBIC, 0);

        public Button(String label, boolean primary, Runnable action) {
            this.label = label;
            this.primary = primary;
            this.action = action;
        }

        public Button at(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            return this;
        }

        public boolean hovered(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        public void render(DrawContext ctx, int mx, int my) {
            hover.animateTo(enabled && hovered(mx, my) ? 1 : 0);
            float t = hover.getFloat();
            int bg;
            int fg;
            if (!enabled) {
                bg = Theme.SURFACE;
                fg = Theme.TEXT_MUTED;
            } else if (primary) {
                bg = ColorUtil.lerp(Theme.accent(), ColorUtil.brighten(Theme.accent(), 0.18f), t);
                fg = 0xFFFFFFFF;
            } else {
                bg = ColorUtil.lerp(Theme.SURFACE, Theme.SURFACE_ACTIVE, t);
                fg = Theme.TEXT;
            }
            Render2D.roundedRect(ctx, x, y, w, h, Theme.RADIUS, bg);
            Render2D.centeredText(ctx, Render2D.ellipsize(label, w - 8), x + w / 2f, y + (h - 8) / 2f, fg, 1f, false);
        }

        public boolean click(double mx, double my, int button) {
            if (button == 0 && enabled && hovered(mx, my)) {
                MinecraftClient.getInstance().getSoundManager().play(
                        net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                action.run();
                return true;
            }
            return false;
        }
    }

    /** Single line text input (search field, profile names). */
    public static final class TextField {
        public int x, y, w, h;
        public String placeholder;
        private final StringBuilder text = new StringBuilder();
        private final int maxLength;
        public boolean focused;

        public TextField(String placeholder, int maxLength) {
            this.placeholder = placeholder;
            this.maxLength = maxLength;
        }

        public String get() { return text.toString(); }

        public void set(String value) {
            text.setLength(0);
            text.append(value == null ? "" : value);
        }

        public TextField at(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            return this;
        }

        public void render(DrawContext ctx, String icon) {
            Render2D.roundedRectBordered(ctx, x, y, w, h, Theme.RADIUS, Theme.SURFACE, focused ? Theme.accent() : Theme.BORDER, 1);
            int tx = x + 6;
            if (icon != null) {
                Render2D.text(ctx, icon, tx, y + (h - 8) / 2f, Theme.TEXT_MUTED, false);
                tx += Render2D.width(icon) + 4;
            }
            String visible = get();
            int max = x + w - 6 - tx;
            while (Render2D.width(visible) > max && !visible.isEmpty()) visible = visible.substring(1);
            if (visible.isEmpty() && !focused) {
                Render2D.text(ctx, placeholder, tx, y + (h - 8) / 2f, Theme.TEXT_MUTED, false);
            } else {
                Render2D.text(ctx, visible, tx, y + (h - 8) / 2f, Theme.TEXT, false);
            }
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                Render2D.rect(ctx, tx + Render2D.width(visible), y + (h - 10) / 2f, 1, 10, Theme.accent());
            }
        }

        public boolean click(double mx, double my) {
            focused = mx >= x && mx < x + w && my >= y && my < y + h;
            return focused;
        }

        /** @return true if the key was consumed */
        public boolean key(int key) {
            if (!focused) return false;
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!text.isEmpty()) {
                    if (Screen.hasControlDown()) text.setLength(0);
                    else text.setLength(text.length() - 1);
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                focused = false;
                return true;
            }
            if (Screen.isPaste(key)) {
                type(MinecraftClient.getInstance().keyboard.getClipboard());
                return true;
            }
            return true;
        }

        public boolean type(String s) {
            if (!focused) return false;
            for (char c : s.toCharArray()) {
                if (text.length() >= maxLength) break;
                if (c >= 0x20 && c != 0x7F && c != '§') text.append(c);
            }
            return true;
        }
    }

    /** Transient message at the bottom of a screen. */
    public static final class Toast {
        private String message;
        private int color;
        private long shownAt;

        public void show(String message, int color) {
            this.message = message;
            this.color = color;
            this.shownAt = System.currentTimeMillis();
        }

        public void render(DrawContext ctx, int centerX, int y) {
            if (message == null) return;
            long age = System.currentTimeMillis() - shownAt;
            if (age > 2800) {
                message = null;
                return;
            }
            float alpha = age < 150 ? age / 150f : age > 2400 ? (2800 - age) / 400f : 1f;
            int w = Render2D.width(message) + 20;
            Render2D.roundedRect(ctx, centerX - w / 2f, y, w, 18, 9, ColorUtil.fade(0xF0181B22, alpha));
            Render2D.rect(ctx, centerX - w / 2f + 8, y + 7, 4, 4, ColorUtil.fade(color, alpha));
            Render2D.text(ctx, message, centerX - w / 2f + 16, y + 5, ColorUtil.fade(Theme.TEXT, Math.max(0.05f, alpha)), false);
        }
    }
}
