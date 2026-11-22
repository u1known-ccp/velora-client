package dev.velora.client.gui;

import dev.velora.client.Mods;
import dev.velora.client.render.Render2D;
import dev.velora.client.render.Theme;
import dev.velora.core.VeloraCore;
import dev.velora.core.util.Animation;
import dev.velora.core.util.ColorUtil;
import dev.velora.core.util.Easing;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Velora main menu: panorama background, animated logo, large buttons, quick join. */
public final class VeloraTitleScreen extends Screen {
    private static final int BUTTON_W = 200;
    private static final int BUTTON_H = 22;

    private final List<UiParts.Button> buttons = new ArrayList<>();
    private final Animation intro = new Animation(Theme.duration(600), Easing.EASE_OUT_CUBIC, 0);
    private final float[][] stars = new float[40][3];

    public VeloraTitleScreen() {
        super(Component.literal("Velora"));
        Random r = new Random(42);
        for (float[] s : stars) {
            s[0] = r.nextFloat();
            s[1] = r.nextFloat();
            s[2] = 0.3f + r.nextFloat() * 0.7f;
        }
    }

    @Override
    protected void init() {
        buttons.clear();
        int x = width / 2 - BUTTON_W / 2;
        int y = height / 2 - 18;
        buttons.add(new UiParts.Button("Einzelspieler", true, () -> minecraft.gui.setScreen(new SelectWorldScreen(this))).at(x, y, BUTTON_W, BUTTON_H));
        buttons.add(new UiParts.Button("Mehrspieler", false, () -> minecraft.gui.setScreen(minecraft.options.skipMultiplayerWarning
                ? new JoinMultiplayerScreen(this) : new SafetyScreen(this))).at(x, y + 26, BUTTON_W, BUTTON_H));
        String quick = Mods.quickJoin != null ? Mods.quickJoin.address.get() : "";
        UiParts.Button quickJoin = new UiParts.Button("Quick Join: " + (quick.isEmpty() ? "–" : quick), false,
                () -> Mods.quickJoin.join(minecraft)).at(x, y + 52, BUTTON_W, BUTTON_H);
        quickJoin.enabled = Mods.quickJoin != null && Mods.quickJoin.isEnabled() && !quick.isEmpty();
        buttons.add(quickJoin);
        int half = (BUTTON_W - 4) / 2;
        buttons.add(new UiParts.Button("Velora-Menü", false, () -> minecraft.gui.setScreen(new ModMenuScreen(this))).at(x, y + 78, half, BUTTON_H));
        buttons.add(new UiParts.Button("Optionen", false, () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options))).at(x + half + 4, y + 78, half, BUTTON_H));
        buttons.add(new UiParts.Button("Sprache", false, () -> minecraft.gui.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager())))
                .at(x, y + 104, half, BUTTON_H));
        buttons.add(new UiParts.Button("Beenden", false, minecraft::stop).at(x + half + 4, y + 104, half, BUTTON_H));
        intro.animateTo(1);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        extractPanorama(ctx, delta);
        ctx.fillGradient(0, 0, width, height, 0xB0090A10, 0xE0050608);
        // subtle drifting particles
        long t = System.currentTimeMillis();
        for (float[] s : stars) {
            float x = (float) ((s[0] * width + t * 0.004 * s[2]) % width);
            float y = s[1] * height + (float) Math.sin(t / 2400.0 + s[0] * 10) * 4;
            Render2D.rect(ctx, x, y, s[2], s[2], ColorUtil.withAlpha(Theme.accent(), (int) (60 + 90 * s[2])));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        extractBackground(ctx, mouseX, mouseY, delta);
        float t = intro.getFloat();
        float logoY = height / 2f - 86 + (1 - t) * -12;

        // logo
        float glow = (float) (Math.sin(System.currentTimeMillis() / 900.0) * 0.5 + 0.5);
        Render2D.circle(ctx, width / 2f, logoY + 18, 34 + glow * 3, ColorUtil.withAlpha(Theme.accent(), (int) (18 + glow * 14)));
        Render2D.centeredText(ctx, "VELORA", width / 2f, logoY, ColorUtil.fade(0xFFFFFFFF, t), 3.2f, true);
        Render2D.centeredText(ctx, "CLIENT", width / 2f, logoY + 30, ColorUtil.fade(Theme.accent(), t), 1.1f, false);
        Render2D.rect(ctx, width / 2f - 20 * t, logoY + 44, 40 * t, 1, ColorUtil.withAlpha(Theme.accent(), 0xA0));

        for (UiParts.Button b : buttons) b.render(ctx, mouseX, mouseY);

        String version = "Velora " + VeloraCore.get().version() + " · Minecraft " + SharedConstants.getCurrentVersion().name();
        Render2D.text(ctx, version, 4, height - 12, Theme.TEXT_MUTED, false);
        String legal = "Nicht offiziell von Mojang oder Microsoft";
        Render2D.text(ctx, legal, width - Render2D.width(legal) - 4, height - 12, Theme.TEXT_MUTED, false);
        if (minecraft.getUser() != null) {
            String user = "Angemeldet als " + minecraft.getUser().getName();
            Render2D.text(ctx, user, width - Render2D.width(user) - 4, 4, Theme.TEXT_DIM, false);
        }
    }

    private boolean onClick(double mouseX, double mouseY, int button) {
        for (UiParts.Button b : buttons) if (b.click(mouseX, mouseY, button)) return true;
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ 26.3 input adapters (SDL event records -> classic coordinates/buttons)

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return onClick(event.x(), event.y(), UiParts.legacyButton(event.button())) || super.mouseClicked(event, doubleClick);
    }
}
