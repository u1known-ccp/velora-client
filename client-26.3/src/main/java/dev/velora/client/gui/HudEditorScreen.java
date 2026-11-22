package dev.velora.client.gui;

import dev.velora.client.hud.HudMod;
import dev.velora.client.render.Render2D;
import dev.velora.client.render.Theme;
import dev.velora.core.VeloraCore;
import dev.velora.core.config.ClientSettings;
import dev.velora.core.hud.HudPosition;
import dev.velora.core.module.Module;
import dev.velora.core.util.ColorUtil;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Drag & drop HUD editor with live preview, grid, snapping to grid / screen center / other elements,
 * resize handles, element list (show/hide), per-element settings and reset.
 */
public final class HudEditorScreen extends Screen {
    private static final int SNAP_DISTANCE = 4;
    private static final int HANDLE = 5;

    private final Screen parent;
    private final VeloraCore core = VeloraCore.get();
    private final List<UiParts.Button> buttons = new ArrayList<>();
    private final UiParts.Toast toast = new UiParts.Toast();

    private HudMod dragging;
    private HudMod resizing;
    private HudMod selected;
    private float dragOffsetX, dragOffsetY;
    private float resizeStartScale, resizeStartDiag, resizeAnchorX, resizeAnchorY;
    private Float guideX, guideY;
    private boolean showList;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD-Editor"));
        this.parent = parent;
    }

    private List<HudMod> allHud() { return core.modules().ofType(HudMod.class); }

    private List<HudMod> enabledHud() {
        List<HudMod> list = new ArrayList<>();
        for (HudMod m : allHud()) if (m.isEnabled()) list.add(m);
        return list;
    }

    private ClientSettings settings() { return core.config().client(); }

    @Override
    protected void init() {
        buttons.clear();
        // compact 3x2 toolbar just below the crosshair – the screen center is rarely used by HUD elements
        int bw = 92;
        int gap = 4;
        int x = width / 2 - (bw * 3 + gap * 2) / 2;
        int y = height / 2 + 18;
        buttons.add(new UiParts.Button("Elemente", false, () -> showList = !showList).at(x, y, bw, 18));
        buttons.add(new UiParts.Button(gridLabel(), false, this::toggleSnap).at(x + (bw + gap), y, bw, 18));
        buttons.add(new UiParts.Button(profileLabel(), false, this::nextProfile).at(x + (bw + gap) * 2, y, bw, 18));
        buttons.add(new UiParts.Button("Zurücksetzen", false, this::reset).at(x, y + 22, bw, 18));
        buttons.add(new UiParts.Button("Velora-Menü", false, () -> minecraft.gui.setScreen(new ModMenuScreen(this))).at(x + (bw + gap), y + 22, bw, 18));
        buttons.add(new UiParts.Button("Fertig", true, this::onClose).at(x + (bw + gap) * 2, y + 22, bw, 18));
    }

    private String profileLabel() { return "Profil: " + core.config().profiles().getActive(); }

    /** HUD profiles: cycles through the client profiles (each stores its own HUD layout). */
    private void nextProfile() {
        List<String> names = core.config().profiles().list();
        int idx = names.indexOf(core.config().profiles().getActive());
        String next = names.get((idx + 1) % names.size());
        if (names.size() == 1) {
            toast.show("Nur ein Profil vorhanden – neue Profile im Velora-Menü anlegen", Theme.WARNING);
            return;
        }
        core.config().switchProfile(next);
        buttons.get(2).label = profileLabel();
        selected = null;
        toast.show("HUD-Profil \"" + next + "\" geladen", Theme.SUCCESS);
    }

    private String gridLabel() { return settings().hudSnap.isOn() ? "Raster: An" : "Raster: Aus"; }

    private void toggleSnap() {
        settings().hudSnap.toggle();
        buttons.get(1).label = gridLabel();
    }

    private void reset() {
        if (selected != null) {
            selected.getPosition().reset();
            selected.markLayoutChanged();
            toast.show(selected.getName() + " zurückgesetzt", Theme.SUCCESS);
        } else {
            for (HudMod m : allHud()) m.getPosition().reset();
            if (!allHud().isEmpty()) allHud().get(0).markLayoutChanged();
            toast.show("Alle HUD-Positionen zurückgesetzt", Theme.SUCCESS);
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) {
            extractPanorama(ctx, delta);
            ctx.fill(0, 0, width, height, 0x90050608);
        } else {
            ctx.fill(0, 0, width, height, 0x40000000);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        extractBackground(ctx, mouseX, mouseY, delta);
        boolean active = dragging != null || resizing != null;
        if (settings().hudSnap.isOn() || active) renderGrid(ctx, active);
        // center lines
        int centerColor = ColorUtil.withAlpha(Theme.accent(), active ? 0x50 : 0x22);
        Render2D.rect(ctx, width / 2f - 0.5f, 0, 1, height, centerColor);
        Render2D.rect(ctx, 0, height / 2f - 0.5f, width, 1, centerColor);

        for (HudMod mod : enabledHud()) {
            try {
                mod.render(ctx, delta, true);
            } catch (RuntimeException ignored) {
                // preview errors must not break the editor
            }
            float[] b = mod.bounds(width, height, true);
            boolean hover = isInside(b, mouseX, mouseY);
            boolean isSelected = mod == selected;
            int outline = isSelected ? Theme.accent() : hover ? 0xC0FFFFFF : 0x50FFFFFF;
            Render2D.outline(ctx, b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, isSelected ? 1f : 0.5f, outline);
            if (hover || isSelected) {
                Render2D.rect(ctx, b[0] + b[2] - HANDLE + 1, b[1] + b[3] - HANDLE + 1, HANDLE, HANDLE, Theme.accent());
                String label = mod.getName() + "  " + Math.round(mod.getPosition().getScale() * 100) + "%";
                float ly = b[1] > 14 ? b[1] - 11 : b[1] + b[3] + 3;
                Render2D.text(ctx, label, b[0], ly, 0xFFFFFFFF, 0.75f, true);
            }
        }

        if (guideX != null) Render2D.rect(ctx, guideX - 0.5f, 0, 1, height, Theme.WARNING);
        if (guideY != null) Render2D.rect(ctx, 0, guideY - 0.5f, width, 1, Theme.WARNING);

        // toolbar
        UiParts.Button first = buttons.get(0);
        UiParts.Button last = buttons.get(buttons.size() - 1);
        Render2D.shadow(ctx, first.x - 6, first.y - 6, last.x + last.w - first.x + 12, last.y + last.h - first.y + 12, Theme.RADIUS, 6, Theme.SHADOW);
        Render2D.roundedRect(ctx, first.x - 6, first.y - 6, last.x + last.w - first.x + 12, last.y + last.h - first.y + 12, Theme.RADIUS, 0xE80E0F13);
        for (UiParts.Button b : buttons) b.render(ctx, mouseX, mouseY);

        if (showList) renderElementList(ctx, mouseX, mouseY);
        else if (enabledHud().isEmpty()) {
            Render2D.centeredText(ctx, "Keine HUD-Elemente aktiv – öffne \"Elemente\"", width / 2f, height / 2f + 8, Theme.TEXT_DIM, 1f, true);
        }

        Render2D.centeredText(ctx, "Ziehen: verschieben · Ecke/Mausrad: Größe · Rechtsklick: Einstellungen",
                width / 2f, height - 20, 0xB0FFFFFF, 0.75f, true);
        Render2D.centeredText(ctx, "Pfeiltasten: feinjustieren (Shift = 10px) · Entf: ausblenden · Alt: ohne Einrasten",
                width / 2f, height - 11, 0xB0FFFFFF, 0.75f, true);
        toast.render(ctx, width / 2, height - 36);
    }

    private void renderGrid(GuiGraphicsExtractor ctx, boolean strong) {
        int grid = settings().hudGridSize.getInt();
        int color = strong ? 0x18FFFFFF : 0x0CFFFFFF;
        for (int x = 0; x < width; x += grid) Render2D.rect(ctx, x, 0, 0.5f, height, color);
        for (int y = 0; y < height; y += grid) Render2D.rect(ctx, 0, y, width, 0.5f, color);
    }

    private int listX() { return 8; }

    private int listY() { return 32; }

    private void renderElementList(GuiGraphicsExtractor ctx, int mx, int my) {
        List<HudMod> all = allHud();
        int w = 150;
        int h = all.size() * 16 + 10;
        Render2D.shadow(ctx, listX(), listY(), w, h, Theme.RADIUS, 6, Theme.SHADOW);
        Render2D.roundedRect(ctx, listX(), listY(), w, h, Theme.RADIUS, Theme.PANEL);
        int y = listY() + 5;
        for (HudMod m : all) {
            boolean hover = mx >= listX() && mx < listX() + w && my >= y && my < y + 16;
            if (hover) Render2D.roundedRect(ctx, listX() + 3, y, w - 6, 16, Theme.RADIUS_SMALL, Theme.SURFACE_HOVER);
            Render2D.roundedRect(ctx, listX() + 8, y + 4, 8, 8, 2, m.isEnabled() ? Theme.accent() : Theme.SURFACE_ACTIVE);
            Render2D.text(ctx, m.getName(), listX() + 22, y + 4, m.isEnabled() ? Theme.TEXT : Theme.TEXT_MUTED, false);
            y += 16;
        }
    }

    private static boolean isInside(float[] b, double mx, double my) {
        return mx >= b[0] - 1 && mx <= b[0] + b[2] + 1 && my >= b[1] - 1 && my <= b[1] + b[3] + 1;
    }

    private HudMod elementAt(double mx, double my) {
        List<HudMod> list = enabledHud();
        // topmost = last rendered
        for (int i = list.size() - 1; i >= 0; i--) {
            if (isInside(list.get(i).bounds(width, height, true), mx, my)) return list.get(i);
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    private boolean onClick(double mx, double my, int button) {
        for (UiParts.Button b : buttons) if (b.click(mx, my, button)) return true;
        if (showList) {
            List<HudMod> all = allHud();
            int w = 150;
            int y = listY() + 5;
            if (mx >= listX() && mx < listX() + w && my >= listY() && my < listY() + all.size() * 16 + 10) {
                for (HudMod m : all) {
                    if (my >= y && my < y + 16) {
                        m.toggle();
                        return true;
                    }
                    y += 16;
                }
                return true;
            }
        }
        HudMod hit = elementAt(mx, my);
        if (hit == null) {
            selected = null;
            return false;
        }
        selected = hit;
        if (button == 1) {
            minecraft.gui.setScreen(ModMenuScreen.forModule(this, hit));
            return true;
        }
        if (button != 0) return true;
        float[] b = hit.bounds(width, height, true);
        boolean onHandle = mx >= b[0] + b[2] - HANDLE - 1 && my >= b[1] + b[3] - HANDLE - 1;
        if (onHandle) {
            resizing = hit;
            resizeStartScale = hit.getPosition().getScale();
            resizeAnchorX = b[0];
            resizeAnchorY = b[1];
            resizeStartDiag = (float) Math.max(1, Math.hypot(mx - b[0], my - b[1]));
        } else {
            dragging = hit;
            dragOffsetX = (float) (mx - b[0]);
            dragOffsetY = (float) (my - b[1]);
        }
        return true;
    }

    private boolean onDrag(double mx, double my, int button, double dx, double dy) {
        if (resizing != null) {
            float diag = (float) Math.hypot(mx - resizeAnchorX, my - resizeAnchorY);
            float scale = resizeStartScale * diag / resizeStartDiag;
            scale = Math.round(scale * 20f) / 20f; // 5% steps
            HudPosition p = resizing.getPosition();
            p.setScale(scale);
            float[] b = resizing.bounds(width, height, true);
            p.setAbsolute(resizeAnchorX, resizeAnchorY, b[2], b[3], width, height);
            return true;
        }
        if (dragging != null) {
            float[] b = dragging.bounds(width, height, true);
            float x = (float) mx - dragOffsetX;
            float y = (float) my - dragOffsetY;
            guideX = null;
            guideY = null;
            if (!(com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LALT) || com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RALT))) {
                float[] snapped = snap(dragging, x, y, b[2], b[3]);
                x = snapped[0];
                y = snapped[1];
            }
            dragging.getPosition().setAbsolute(x, y, b[2], b[3], width, height);
            return true;
        }
        return false;
    }

    /** Snaps to screen center, screen edges, other elements' edges, then the grid. Alt disables snapping. */
    private float[] snap(HudMod self, float x, float y, float w, float h) {
        boolean guides = settings().hudGuides.isOn();
        Float sx = null, sy = null;
        if (guides) {
            // candidate guide lines: screen center and the edges/centers of all other elements
            List<Float> xs = new ArrayList<>();
            List<Float> ys = new ArrayList<>();
            xs.add(width / 2f);
            ys.add(height / 2f);
            for (HudMod other : enabledHud()) {
                if (other == self) continue;
                float[] o = other.bounds(width, height, true);
                xs.add(o[0]);
                xs.add(o[0] + o[2] / 2f);
                xs.add(o[0] + o[2]);
                ys.add(o[1]);
                ys.add(o[1] + o[3] / 2f);
                ys.add(o[1] + o[3]);
            }
            float best = SNAP_DISTANCE + 1;
            for (float line : xs) {
                for (float edge : new float[]{0, w / 2f, w}) {
                    float d = Math.abs(x + edge - line);
                    if (d < best) {
                        best = d;
                        sx = line - edge;
                        guideX = line;
                    }
                }
            }
            best = SNAP_DISTANCE + 1;
            for (float line : ys) {
                for (float edge : new float[]{0, h / 2f, h}) {
                    float d = Math.abs(y + edge - line);
                    if (d < best) {
                        best = d;
                        sy = line - edge;
                        guideY = line;
                    }
                }
            }
        }
        if (settings().hudSnap.isOn()) {
            int grid = settings().hudGridSize.getInt();
            if (sx == null) x = Math.round(x / grid) * grid;
            if (sy == null) y = Math.round(y / grid) * grid;
        }
        return new float[]{sx != null ? sx : x, sy != null ? sy : y};
    }

    private boolean onRelease(double mx, double my, int button) {
        if (dragging != null || resizing != null) {
            HudMod changed = dragging != null ? dragging : resizing;
            changed.markLayoutChanged();
            dragging = null;
            resizing = null;
            guideX = null;
            guideY = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        HudMod hit = elementAt(mx, my);
        if (hit == null) return false;
        HudPosition p = hit.getPosition();
        float[] before = hit.bounds(width, height, true);
        p.setScale(Math.round((p.getScale() + (vertical > 0 ? 0.05f : -0.05f)) * 20f) / 20f);
        float[] after = hit.bounds(width, height, true);
        // keep the element centered while scaling
        p.setAbsolute(before[0] + (before[2] - after[2]) / 2, before[1] + (before[3] - after[3]) / 2, after[2], after[3], width, height);
        hit.markLayoutChanged();
        selected = hit;
        return true;
    }

    private boolean onKey(int keyCode, int scanCode, int modifiers) {
        if (selected != null) {
            int step = ((modifiers & 3) != 0) ? 10 : 1;
            float[] b = selected.bounds(width, height, true);
            float nx = b[0], ny = b[1];
            switch (keyCode) {
                case InputConstants.KEY_LEFT -> nx -= step;
                case InputConstants.KEY_RIGHT -> nx += step;
                case InputConstants.KEY_UP -> ny -= step;
                case InputConstants.KEY_DOWN -> ny += step;
                case InputConstants.KEY_DELETE, InputConstants.KEY_BACKSPACE -> {
                    Module m = selected;
                    m.setEnabled(false);
                    toast.show(m.getName() + " ausgeblendet", Theme.WARNING);
                    selected = null;
                    return true;
                }
                default -> {
                    return super.keyPressed(currentKeyEvent);
                }
            }
            selected.getPosition().setAbsolute(nx, ny, b[2], b[3], width, height);
            selected.markLayoutChanged();
            return true;
        }
        return super.keyPressed(currentKeyEvent);
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ 26.3 input adapters (SDL event records -> classic coordinates/buttons)

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return onClick(event.x(), event.y(), UiParts.legacyButton(event.button())) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        return onDrag(event.x(), event.y(), UiParts.legacyButton(event.button()), dx, dy) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        return onRelease(event.x(), event.y(), UiParts.legacyButton(event.button())) || super.mouseReleased(event);
    }

    private net.minecraft.client.input.KeyEvent currentKeyEvent;

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        currentKeyEvent = event;
        return onKey(event.key(), 0, event.modifiers());
    }
}
