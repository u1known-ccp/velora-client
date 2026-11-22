package dev.velora.client.gui.widget;

import dev.velora.client.render.Render2D;
import dev.velora.client.render.Theme;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ButtonSetting;
import dev.velora.core.setting.ModeSetting;
import dev.velora.core.setting.NumberSetting;
import dev.velora.core.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/** Boolean, number, mode and button rows. */
public final class BasicWidgets {
    private BasicWidgets() { }

    public static final class BooleanWidget extends SettingWidget {
        private final BooleanSetting s;
        private final Toggle toggle;

        public BooleanWidget(BooleanSetting s) {
            super(s);
            this.s = s;
            this.toggle = new Toggle(s.isOn());
        }

        @Override protected int controlWidth() { return Toggle.WIDTH; }

        @Override
        protected void renderControl(DrawContext ctx, int mx, int my, float delta) {
            toggle.render(ctx, x + width - Toggle.WIDTH - 8, controlCenterY() - Toggle.HEIGHT / 2f, s.isOn());
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && isHovered(mx, my)) {
                s.toggle();
                return true;
            }
            return false;
        }
    }

    public static final class NumberWidget extends SettingWidget {
        private static final int TRACK = 110;
        private final NumberSetting s;
        private boolean dragging;

        public NumberWidget(NumberSetting s) {
            super(s);
            this.s = s;
        }

        @Override protected int controlWidth() { return TRACK + 40; }

        private int trackX() { return x + width - TRACK - 10; }

        @Override
        protected void renderControl(DrawContext ctx, int mx, int my, float delta) {
            int tx = trackX();
            float cy = controlCenterY();
            float p = (float) s.getProgress();
            Render2D.roundedRect(ctx, tx, cy - 2, TRACK, 4, 2, Theme.SURFACE_ACTIVE);
            Render2D.roundedRect(ctx, tx, cy - 2, Math.max(4, TRACK * p), 4, 2, Theme.accent());
            float knobX = tx + TRACK * p;
            Render2D.circle(ctx, knobX, cy, dragging ? 5 : 4, 0xFFFFFFFF);
            String v = s.format();
            Render2D.text(ctx, v, tx - Render2D.width(v) - 8, cy - 4, Theme.TEXT_DIM, false);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !isHovered(mx, my)) return false;
            if (mx >= trackX() - 6 && mx <= trackX() + TRACK + 6) {
                dragging = true;
                update(mx);
            }
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button) {
            if (!dragging) return false;
            update(mx);
            return true;
        }

        @Override
        public void mouseReleased(double mx, double my, int button) { dragging = false; }

        private void update(double mx) { s.setFromProgress((mx - trackX()) / TRACK); }
    }

    public static final class ModeWidget extends SettingWidget {
        private final ModeSetting s;

        public ModeWidget(ModeSetting s) {
            super(s);
            this.s = s;
        }

        private int boxWidth() {
            int w = 0;
            for (String m : s.getModes()) w = Math.max(w, Render2D.width(m));
            return Math.min(150, w + 30);
        }

        @Override protected int controlWidth() { return boxWidth(); }

        @Override
        protected void renderControl(DrawContext ctx, int mx, int my, float delta) {
            int bw = boxWidth();
            int bx = x + width - bw - 8;
            int by = controlCenterY() - 8;
            boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + 16;
            Render2D.roundedRect(ctx, bx, by, bw, 16, Theme.RADIUS_SMALL, hover ? Theme.SURFACE_ACTIVE : Theme.SIDEBAR);
            Render2D.text(ctx, "‹", bx + 5, by + 4, Theme.TEXT_DIM, false);
            Render2D.text(ctx, "›", bx + bw - 9, by + 4, Theme.TEXT_DIM, false);
            Render2D.centeredText(ctx, Render2D.ellipsize(s.get(), bw - 24), bx + bw / 2f, by + 4, Theme.TEXT, 1f, false);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!isHovered(mx, my)) return false;
            int bw = boxWidth();
            int bx = x + width - bw - 8;
            boolean left = button == 1 || (button == 0 && mx < bx + bw / 2.0 && mx >= bx);
            s.cycle(left ? -1 : 1);
            return true;
        }
    }

    public static final class ButtonWidget extends SettingWidget {
        private final ButtonSetting s;

        public ButtonWidget(ButtonSetting s) {
            super(s);
            this.s = s;
        }

        private int bw() { return Render2D.width(s.getButtonLabel()) + 18; }

        @Override protected int controlWidth() { return bw(); }

        @Override
        protected void renderControl(DrawContext ctx, int mx, int my, float delta) {
            int bw = bw();
            int bx = x + width - bw - 8;
            int by = controlCenterY() - 8;
            boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + 16;
            int color = hover ? ColorUtil.brighten(Theme.accent(), 0.15f) : Theme.accent();
            Render2D.roundedRect(ctx, bx, by, bw, 16, Theme.RADIUS_SMALL, color);
            Render2D.centeredText(ctx, s.getButtonLabel(), bx + bw / 2f, by + 4, 0xFFFFFFFF, 1f, false);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !isHovered(mx, my)) return false;
            s.press();
            return true;
        }
    }
}
