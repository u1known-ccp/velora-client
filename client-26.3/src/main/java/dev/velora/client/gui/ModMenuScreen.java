package dev.velora.client.gui;

import org.joml.Matrix3x2fStack;
import dev.velora.client.cosmetics.CapeTextures;
import dev.velora.client.gui.widget.SettingWidget;
import dev.velora.client.gui.widget.SettingWidgets;
import dev.velora.client.gui.widget.Toggle;
import dev.velora.client.render.Render2D;
import dev.velora.client.render.Theme;
import dev.velora.core.VeloraCore;
import dev.velora.core.config.ConfigManager;
import dev.velora.core.config.ProfileManager;
import dev.velora.core.cosmetics.Cosmetic;
import dev.velora.core.cosmetics.CosmeticCatalog;
import dev.velora.core.cosmetics.CosmeticType;
import dev.velora.core.hud.HudModule;
import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.setting.ButtonSetting;
import dev.velora.core.setting.Setting;
import dev.velora.core.util.Animation;
import dev.velora.core.util.ColorUtil;
import dev.velora.core.util.Easing;
import com.mojang.blaze3d.platform.InputConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The Velora in-game menu: mods (search, categories, favorites, cards, settings), cosmetics,
 * profiles and client settings. Everything is drawn with batched GUI quads; no per-frame allocations
 * besides small layout lists.
 */
public final class ModMenuScreen extends Screen {
    private enum Page { MODS, COSMETICS, PROFILES, SETTINGS }

    private static final int SIDEBAR_W = 116;
    private static final int CARD_MIN_W = 128;
    private static final int CARD_H = 66;
    private static final int GAP = 6;

    private static Page lastPage = Page.MODS;
    private static Category lastCategory;
    private static boolean lastFavorites;

    private final Screen parent;
    private final VeloraCore core = VeloraCore.get();
    private Page page = lastPage;
    private Category category = lastCategory;
    private boolean favoritesOnly = lastFavorites;
    private final UiParts.TextField search = new UiParts.TextField("Mods suchen…", 40);
    private final UiParts.TextField profileName = new UiParts.TextField("Name des neuen Profils", 32);
    private final UiParts.Toast toast = new UiParts.Toast();

    private Module selected;
    private List<SettingWidget> widgets = List.of();
    private SettingWidget focusedWidget;
    private final Map<Module, Toggle> toggles = new HashMap<>();
    private final Map<Object, Animation> hovers = new HashMap<>();
    private final Animation openAnim = new Animation(Theme.duration(220), Easing.EASE_OUT_CUBIC, 0);
    private final Animation scrollAnim = new Animation(Theme.duration(120), Easing.EASE_OUT_CUBIC, 0);
    private double scrollTarget;
    private int contentHeight;

    // layout
    private int px, py, pw, ph, cx, cy, cw, ch;
    private final List<UiParts.Button> buttons = new ArrayList<>();

    public ModMenuScreen(Screen parent) {
        super(Component.literal("Velora"));
        this.parent = parent;
    }

    /** Opens the menu directly on a module's settings page. */
    public static ModMenuScreen forModule(Screen parent, Module module) {
        ModMenuScreen screen = new ModMenuScreen(parent);
        screen.page = Page.MODS;
        screen.selected = module;
        return screen;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 24, 600);
        ph = Math.min(height - 24, 360);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + SIDEBAR_W + 12;
        cy = py + 12;
        cw = pw - SIDEBAR_W - 24;
        ch = ph - 24;
        search.at(cx, cy, Math.min(180, cw / 2), 18);
        openAnim.animateTo(1);
        if (selected != null) openSettings(selected);
        else if (page == Page.SETTINGS) buildClientSettingsWidgets();
        rebuildButtons();
    }

    /** Opens the menu on the cosmetics page. */
    public static ModMenuScreen cosmetics(Screen parent) {
        ModMenuScreen screen = new ModMenuScreen(parent);
        screen.page = Page.COSMETICS;
        screen.selected = null;
        return screen;
    }

    /** Opens the menu on the client settings page. */
    public static ModMenuScreen clientSettings(Screen parent) {
        ModMenuScreen screen = new ModMenuScreen(parent);
        screen.page = Page.SETTINGS;
        screen.selected = null;
        return screen;
    }

    // ------------------------------------------------------------------ navigation

    private void setPage(Page p) {
        page = p;
        lastPage = p;
        selected = null;
        widgets = List.of();
        resetScroll();
        if (p == Page.SETTINGS) buildClientSettingsWidgets();
        rebuildButtons();
    }

    private void openSettings(Module module) {
        selected = module;
        List<Setting<?>> list = new ArrayList<>();
        list.add(module.getToggleKey());
        list.addAll(module.getSettings());
        widgets = new ArrayList<>(SettingWidgets.createAll(list));
        if (module instanceof HudModule) {
            widgets.add(SettingWidgets.create(new ButtonSetting("edit_hud", "Position & Größe", "Im HUD-Editor verschieben und skalieren",
                    "HUD-Editor", () -> minecraft.gui.setScreen(new HudEditorScreen(this)))));
        }
        widgets.add(SettingWidgets.create(new ButtonSetting("reset", "Zurücksetzen", "Alle Einstellungen dieses Mods auf Standard",
                "Zurücksetzen", () -> {
                    module.resetSettings();
                    module.getToggleKey().reset();
                    toast.show(module.getName() + " zurückgesetzt", Theme.SUCCESS);
                    openSettings(module);
                })));
        resetScroll();
        rebuildButtons();
    }

    private void buildClientSettingsWidgets() {
        List<Setting<?>> list = new ArrayList<>(core.config().client().all());
        list.add(new ButtonSetting("video", "Grafik", "Minecraft-Grafikeinstellungen", "Öffnen",
                () -> minecraft.gui.setScreen(new VideoSettingsScreen(this, minecraft, minecraft.options))));
        list.add(new ButtonSetting("sound", "Audio", "Lautstärke und Geräusche", "Öffnen",
                () -> minecraft.gui.setScreen(new SoundOptionsScreen(this, minecraft.options))));
        list.add(new ButtonSetting("controls", "Steuerung", "Vanilla-Tastenbelegung", "Öffnen",
                () -> minecraft.gui.setScreen(new ControlsScreen(this, minecraft.options))));
        list.add(new ButtonSetting("all_options", "Alle Minecraft-Optionen", "", "Öffnen",
                () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options))));
        list.add(new ButtonSetting("config_folder", "Konfigurationsordner", "config/velora", "Öffnen",
                () -> com.mojang.blaze3d.Blaze3D.openPath(core.config().getRoot())));
        list.add(new ButtonSetting("cloud_upload", "Cloud-Sync: Hochladen", "Aktives Profil im Velora-Backend sichern", "Hochladen",
                () -> dev.velora.client.BackendSync.uploadActiveProfile().whenComplete((v, e) -> minecraft.execute(() ->
                        toast.show(e == null ? "Profil in der Cloud gespeichert" : "Fehler: " + dev.velora.client.BackendSync.rootMessage(e),
                                e == null ? Theme.SUCCESS : Theme.DANGER)))));
        list.add(new ButtonSetting("cloud_download", "Cloud-Sync: Herunterladen", "Cloud-Profil als neues Profil \"Cloud\" speichern", "Laden",
                () -> dev.velora.client.BackendSync.downloadToProfile().whenComplete((name, e) -> minecraft.execute(() ->
                        toast.show(e == null ? "Als Profil \"" + name + "\" gespeichert" : "Fehler: " + dev.velora.client.BackendSync.rootMessage(e),
                                e == null ? Theme.SUCCESS : Theme.DANGER)))));
        widgets = SettingWidgets.createAll(list);
    }

    private void resetScroll() {
        scrollTarget = 0;
        scrollAnim.set(0);
    }

    private void rebuildButtons() {
        buttons.clear();
        int bx = px + 10;
        int bw = SIDEBAR_W - 20;
        int by = py + 54;
        for (Page p : Page.values()) {
            String label = switch (p) {
                case MODS -> "Mods";
                case COSMETICS -> "Cosmetics";
                case PROFILES -> "Profile";
                case SETTINGS -> "Einstellungen";
            };
            UiParts.Button b = new UiParts.Button(label, false, () -> setPage(p)).at(bx, by, bw, 20);
            buttons.add(b);
            by += 24;
        }
        buttons.add(new UiParts.Button("HUD-Editor", true, () -> minecraft.gui.setScreen(new HudEditorScreen(this)))
                .at(bx, py + ph - 52, bw, 20));

        if (page == Page.PROFILES) {
            int y = cy + 26;
            profileName.at(cx, y, Math.min(170, cw - 190), 18);
            int x = profileName.x + profileName.w + 6;
            buttons.add(new UiParts.Button("Neu", true, () -> createProfile(false)).at(x, y, 50, 18));
            buttons.add(new UiParts.Button("Duplizieren", false, () -> createProfile(true)).at(x + 56, y, 76, 18));
            buttons.add(new UiParts.Button("Import-Ordner", false, this::openImportFolder).at(cx + cw - 90, cy, 90, 18));
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float t = openAnim.getFloat();
        if (minecraft.level == null) {
            extractPanorama(ctx, delta);
            ctx.fill(0, 0, width, height, ColorUtil.fade(0xC0050608, t));
        } else if (core.config().client().dimBackground.isOn()) {
            ctx.fillGradient(0, 0, width, height, ColorUtil.fade(0xA0050608, t), ColorUtil.fade(0xD0050608, t));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        extractBackground(ctx, mouseX, mouseY, delta);
        float t = openAnim.getFloat();
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        float scale = 0.96f + 0.04f * t;
        m.translate(width / 2f, height / 2f);
        m.scale(scale, scale);
        m.translate(-width / 2f, -height / 2f);

        Render2D.shadow(ctx, px, py, pw, ph, Theme.RADIUS_LARGE, 10, Theme.SHADOW);
        Render2D.roundedRect(ctx, px, py, pw, ph, Theme.RADIUS_LARGE, Theme.PANEL);
        Render2D.roundedRect(ctx, px, py, SIDEBAR_W, ph, Theme.RADIUS_LARGE, Theme.SIDEBAR);
        Render2D.rect(ctx, px + SIDEBAR_W - Theme.RADIUS_LARGE, py, Theme.RADIUS_LARGE, ph, Theme.SIDEBAR);
        Render2D.rect(ctx, px + SIDEBAR_W, py + 10, 1, ph - 20, Theme.BORDER);

        renderSidebar(ctx, mouseX, mouseY);

        ctx.enableScissor(cx - 2, cy - 2, cx + cw + 2, cy + ch + 2);
        switch (page) {
            case MODS -> {
                if (selected != null) renderModuleSettings(ctx, mouseX, mouseY, delta);
                else renderModGrid(ctx, mouseX, mouseY);
            }
            case COSMETICS -> renderCosmetics(ctx, mouseX, mouseY);
            case PROFILES -> renderProfiles(ctx, mouseX, mouseY);
            case SETTINGS -> renderWidgetPage(ctx, mouseX, mouseY, delta, "Einstellungen", "Globale Client-Einstellungen (gelten für alle Profile)");
        }
        ctx.disableScissor();
        m.popMatrix();
        toast.render(ctx, width / 2, py + ph - 26);
    }

    private void renderSidebar(GuiGraphicsExtractor ctx, int mx, int my) {
        int lx = px + 12;
        int ly = py + 14;
        Render2D.roundedRect(ctx, lx, ly, 24, 24, 6, Theme.accent());
        Render2D.text(ctx, "V", lx + 7, ly + 6, 0xFFFFFFFF, 1.5f, false);
        Render2D.text(ctx, "Velora", lx + 30, ly + 3, Theme.TEXT, 1.2f, false);
        Render2D.text(ctx, "v" + core.version(), lx + 30, ly + 15, Theme.TEXT_MUTED, 0.75f, false);

        for (int i = 0; i < Page.values().length; i++) {
            UiParts.Button b = buttons.get(i);
            boolean active = page == Page.values()[i];
            boolean hover = b.hovered(mx, my);
            Animation a = hovers.computeIfAbsent("nav" + i, k -> new Animation(Theme.duration(120), Easing.EASE_OUT_CUBIC, 0));
            a.animateTo(active ? 1 : hover ? 0.5 : 0);
            float t = a.getFloat();
            if (t > 0.01f) Render2D.roundedRect(ctx, b.x, b.y, b.w, b.h, Theme.RADIUS, ColorUtil.fade(active ? Theme.accentSoft() : Theme.SURFACE_HOVER, Math.min(1, t * (active ? 1 : 2))));
            if (active) Render2D.roundedRect(ctx, b.x, b.y + 5, 2, b.h - 10, 1, Theme.accent());
            Render2D.text(ctx, b.label, b.x + 10, b.y + 6, active ? Theme.TEXT : Theme.TEXT_DIM, false);
        }
        UiParts.Button hud = buttons.get(Page.values().length);
        hud.render(ctx, mx, my);
        String profile = "Profil: " + core.config().profiles().getActive();
        Render2D.text(ctx, Render2D.ellipsize(profile, SIDEBAR_W - 24), px + 12, py + ph - 24, Theme.TEXT_MUTED, false);
    }

    // ---------------------------------------------------------------- mods grid

    private List<Module> visibleModules() {
        return core.modules().search(search.get(), category, favoritesOnly);
    }

    private int chipsY() { return cy + 24; }

    /** Chip layout {x, y, w} per chip; wraps into further rows when the panel is narrow. Index 0 = all, 1 = favorites. */
    private List<int[]> chipLayout() {
        List<int[]> rects = new ArrayList<>();
        int x = cx;
        int y = chipsY();
        for (String label : chipLabels()) {
            int w = Render2D.width(label) + 14;
            if (x + w > cx + cw && x > cx) {
                x = cx;
                y += 20;
            }
            rects.add(new int[]{x, y, w});
            x += w + 4;
        }
        return rects;
    }

    private static List<String> chipLabels() {
        List<String> labels = new ArrayList<>();
        labels.add("Alle");
        labels.add("★ Favoriten");
        for (Category c : Category.values()) labels.add(c.getDisplayName());
        return labels;
    }

    private int gridTop() {
        List<int[]> chips = chipLayout();
        return chips.get(chips.size() - 1)[1] + 22;
    }

    private void renderModGrid(GuiGraphicsExtractor ctx, int mx, int my) {
        search.render(ctx, "⌕");
        String count = visibleModules().size() + " Mods";
        Render2D.text(ctx, count, cx + cw - Render2D.width(count), cy + 5, Theme.TEXT_MUTED, false);

        // category chips
        List<String> labels = chipLabels();
        List<int[]> chips = chipLayout();
        for (int i = 0; i < labels.size(); i++) {
            boolean active = i == 0 ? category == null && !favoritesOnly
                    : i == 1 ? favoritesOnly
                    : category == Category.values()[i - 2] && !favoritesOnly;
            int[] r = chips.get(i);
            chip(ctx, mx, my, r[0], r[1], labels.get(i), active);
        }

        List<Module> mods = visibleModules();
        int cols = Math.max(1, (cw + GAP) / (CARD_MIN_W + GAP));
        int cardW = (cw - (cols - 1) * GAP) / cols;
        int rows = (mods.size() + cols - 1) / cols;
        contentHeight = rows * (CARD_H + GAP);
        int top = gridTop();
        int viewH = cy + ch - top;
        double scroll = scrollAnim.get();

        ctx.enableScissor(cx - 2, top, cx + cw + 2, cy + ch);
        for (int i = 0; i < mods.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int cardX = cx + col * (cardW + GAP);
            int cardY = (int) (top + row * (CARD_H + GAP) - scroll);
            if (cardY + CARD_H < top || cardY > cy + ch) continue;
            renderCard(ctx, mods.get(i), cardX, cardY, cardW, mx, my, my >= top && my < cy + ch);
        }
        if (mods.isEmpty()) {
            Render2D.centeredText(ctx, "Keine Mods gefunden", cx + cw / 2f, top + 40, Theme.TEXT_MUTED, 1f, false);
        }
        ctx.disableScissor();
        renderScrollbar(ctx, top, viewH);
    }

    private int chip(GuiGraphicsExtractor ctx, int mx, int my, int x, int y, String label, boolean active) {
        int w = Render2D.width(label) + 14;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 16;
        int bg = active ? Theme.accent() : hover ? Theme.SURFACE_ACTIVE : Theme.SURFACE;
        Render2D.roundedRect(ctx, x, y, w, 16, 8, bg);
        Render2D.text(ctx, label, x + 7, y + 4, active ? 0xFFFFFFFF : Theme.TEXT_DIM, false);
        return x + w + 4;
    }

    private void renderCard(GuiGraphicsExtractor ctx, Module m, int x, int y, int w, int mx, int my, boolean mouseInView) {
        boolean hover = mouseInView && mx >= x && mx < x + w && my >= y && my < y + CARD_H;
        Animation a = hovers.computeIfAbsent(m, k -> new Animation(Theme.duration(140), Easing.EASE_OUT_CUBIC, 0));
        a.animateTo(hover ? 1 : 0);
        float t = a.getFloat();
        int bg = ColorUtil.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t);
        int border = m.isEnabled() ? ColorUtil.lerp(Theme.accentDim(), Theme.accent(), t) : ColorUtil.lerp(Theme.BORDER, Theme.SURFACE_ACTIVE, t);
        Render2D.roundedRectBordered(ctx, x, y - t * 1.5f, w, CARD_H, Theme.RADIUS, bg, border, 1);
        float yy = y - t * 1.5f;

        Render2D.text(ctx, Render2D.ellipsize(m.getName(), w - 26), x + 8, yy + 8, Theme.TEXT, false);
        Render2D.text(ctx, m.getCategory().getDisplayName().toUpperCase(), x + 8, yy + 19, ColorUtil.withAlpha(Theme.accent(), 0xC0), 0.7f, false);
        // description, up to two lines at 0.75 scale
        List<String> lines = wrap(m.getDescription(), (int) ((w - 16) / 0.75f), 2);
        for (int i = 0; i < lines.size(); i++) {
            Render2D.text(ctx, lines.get(i), x + 8, yy + 28 + i * 8, Theme.TEXT_DIM, 0.75f, false);
        }
        // favorite star
        boolean starHover = hover && mx >= x + w - 18 && my < y + 18;
        String star = m.isFavorite() ? "★" : "☆";
        Render2D.text(ctx, star, x + w - 14, yy + 7, m.isFavorite() ? Theme.WARNING : starHover ? Theme.TEXT : Theme.TEXT_MUTED, false);
        // settings gear
        boolean gearHover = hover && mx < x + 24 && my >= y + CARD_H - 18;
        Render2D.text(ctx, "⚙", x + 8, yy + CARD_H - 15, gearHover ? Theme.accent() : Theme.TEXT_MUTED, false);
        if (m.getToggleKey().isBound()) {
            String key = dev.velora.client.gui.widget.InputWidgets.keyName(m.getToggleKey().getKey());
            Render2D.text(ctx, Render2D.ellipsize(key, w - 70), x + 22, yy + CARD_H - 14, Theme.TEXT_MUTED, 0.75f, false);
        }
        toggles.computeIfAbsent(m, k -> new Toggle(k.isEnabled()))
                .render(ctx, x + w - Toggle.WIDTH - 8, yy + CARD_H - Toggle.HEIGHT - 7, m.isEnabled());
    }

    private static List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (Render2D.width(candidate) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
                if (lines.size() == maxLines) break;
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (lines.size() < maxLines && !line.isEmpty()) lines.add(line.toString());
        if (lines.size() == maxLines) {
            String last = lines.get(maxLines - 1);
            lines.set(maxLines - 1, Render2D.ellipsize(last, maxWidth));
        }
        return lines;
    }

    private void renderScrollbar(GuiGraphicsExtractor ctx, int top, int viewH) {
        if (contentHeight <= viewH) return;
        float ratio = viewH / (float) contentHeight;
        float barH = Math.max(20, viewH * ratio);
        float maxScroll = contentHeight - viewH;
        float barY = top + (float) (scrollAnim.get() / maxScroll) * (viewH - barH);
        Render2D.roundedRect(ctx, cx + cw + 3, barY, 3, barH, 1.5f, Theme.SURFACE_ACTIVE);
    }

    // ---------------------------------------------------------------- module settings

    private void renderModuleSettings(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        Module m = selected;
        boolean backHover = mx >= cx && mx < cx + 60 && my >= cy && my < cy + 16;
        Render2D.text(ctx, "‹ Zurück", cx, cy + 4, backHover ? Theme.accent() : Theme.TEXT_DIM, false);
        Render2D.text(ctx, m.getName(), cx, cy + 22, Theme.TEXT, 1.35f, false);
        Render2D.text(ctx, Render2D.ellipsize(m.getDescription(), cw - 50), cx, cy + 38, Theme.TEXT_DIM, false);
        toggles.computeIfAbsent(m, k -> new Toggle(k.isEnabled())).render(ctx, cx + cw - Toggle.WIDTH, cy + 24, m.isEnabled());
        layoutAndRenderWidgets(ctx, mx, my, delta, cy + 54);
    }

    private void renderWidgetPage(GuiGraphicsExtractor ctx, int mx, int my, float delta, String title, String subtitle) {
        Render2D.text(ctx, title, cx, cy + 2, Theme.TEXT, 1.35f, false);
        Render2D.text(ctx, subtitle, cx, cy + 18, Theme.TEXT_DIM, false);
        layoutAndRenderWidgets(ctx, mx, my, delta, cy + 34);
    }

    private int widgetsTop;

    private void layoutAndRenderWidgets(GuiGraphicsExtractor ctx, int mx, int my, float delta, int top) {
        widgetsTop = top;
        int viewH = cy + ch - top;
        double scroll = scrollAnim.get();
        int y = (int) (top - scroll);
        int total = 0;
        ctx.enableScissor(cx - 2, top, cx + cw + 2, cy + ch);
        for (SettingWidget w : widgets) {
            if (!w.getSetting().isVisible()) continue;
            w.x = cx;
            w.y = y;
            w.width = cw;
            int h = w.getHeight();
            if (y + h >= top && y <= cy + ch) w.render(ctx, mx, my, delta);
            y += h + 4;
            total += h + 4;
        }
        ctx.disableScissor();
        contentHeight = total;
        renderScrollbar(ctx, top, viewH);
    }

    // ---------------------------------------------------------------- cosmetics

    private void renderCosmetics(GuiGraphicsExtractor ctx, int mx, int my) {
        Render2D.text(ctx, "Cosmetics", cx, cy + 2, Theme.TEXT, 1.35f, false);
        String sub = core.backend() != null && core.backend().isEnabled()
                ? "Freischaltungen werden vom Backend synchronisiert" : "Offline-Modus: nur kostenlose Cosmetics verfügbar";
        Render2D.text(ctx, sub, cx, cy + 18, Theme.TEXT_DIM, false);

        int previewW = minecraft.player != null ? 110 : 0;
        int gridW = cw - previewW - (previewW > 0 ? 10 : 0);
        List<Cosmetic> capes = new ArrayList<>(CosmeticCatalog.ofType(CosmeticType.CAPE));
        int tileW = 70;
        int tileH = 92;
        int cols = Math.max(1, (gridW + GAP) / (tileW + GAP));
        int top = cy + 36;
        String equipped = core.config().client().equippedCape.get();
        // "none" tile first
        for (int i = 0; i <= capes.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = cx + col * (tileW + GAP);
            int y = (int) (top + row * (tileH + GAP) - scrollAnim.get());
            Cosmetic c = i == 0 ? null : capes.get(i - 1);
            String id = c == null ? "" : c.id();
            boolean unlocked = c == null || core.cosmetics().isUnlocked(id);
            boolean active = equipped.equals(id);
            boolean hover = mx >= x && mx < x + tileW && my >= y && my < y + tileH;
            int border = active ? Theme.accent() : hover ? Theme.SURFACE_ACTIVE : Theme.BORDER;
            Render2D.roundedRectBordered(ctx, x, y, tileW, tileH, Theme.RADIUS, hover ? Theme.SURFACE_HOVER : Theme.SURFACE, border, active ? 1.5f : 1);
            if (c == null) {
                Render2D.centeredText(ctx, "—", x + tileW / 2f, y + 32, Theme.TEXT_MUTED, 2f, false);
                Render2D.centeredText(ctx, "Kein Cape", x + tileW / 2f, y + tileH - 14, Theme.TEXT_DIM, 1f, false);
            } else {
                Identifier tex = CapeTextures.get(c.id());
                Matrix3x2fStack ms = ctx.pose();
                ms.pushMatrix();
                ms.translate(x + (tileW - 30) / 2f, y + 8);
                ms.scale(3f, 3f);
                ctx.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, CapeTextures.PREVIEW_U, CapeTextures.PREVIEW_V,
                        CapeTextures.PREVIEW_W, CapeTextures.PREVIEW_H, 64, 32, -1);
                ms.popMatrix();
                if (!unlocked) {
                    Render2D.roundedRect(ctx, x + 1, y + 1, tileW - 2, tileH - 2, Theme.RADIUS, 0x990E0F13);
                    Render2D.centeredText(ctx, "Gesperrt", x + tileW / 2f, y + 34, Theme.TEXT_DIM, 1f, false);
                }
                Render2D.centeredText(ctx, Render2D.ellipsize(c.name(), tileW - 6), x + tileW / 2f, y + tileH - 14, unlocked ? Theme.TEXT : Theme.TEXT_MUTED, 1f, false);
            }
        }
        if (previewW > 0) {
            int bx = cx + cw - previewW;
            Render2D.roundedRect(ctx, bx, top, previewW, Math.min(170, ch - 40), Theme.RADIUS, Theme.SURFACE);
            InventoryScreen.extractEntityInInventoryFollowsMouse(ctx, bx + 5, top + 5, bx + previewW - 5, top + Math.min(170, ch - 40) - 5, 50, 0.0625f, mx, my, minecraft.player);
            Render2D.centeredText(ctx, "Vorschau", bx + previewW / 2f, top + Math.min(170, ch - 40) + 4, Theme.TEXT_MUTED, 0.75f, false);
        }
        contentHeight = ((capes.size() + 1 + cols - 1) / cols) * (tileH + GAP);
        renderScrollbar(ctx, top, cy + ch - top);
    }

    private boolean clickCosmetics(double mx, double my) {
        int previewW = minecraft.player != null ? 110 : 0;
        int gridW = cw - previewW - (previewW > 0 ? 10 : 0);
        List<Cosmetic> capes = CosmeticCatalog.ofType(CosmeticType.CAPE);
        int tileW = 70, tileH = 92;
        int cols = Math.max(1, (gridW + GAP) / (tileW + GAP));
        int top = cy + 36;
        for (int i = 0; i <= capes.size(); i++) {
            int x = cx + (i % cols) * (tileW + GAP);
            int y = (int) (top + (i / cols) * (tileH + GAP) - scrollAnim.get());
            if (mx >= x && mx < x + tileW && my >= y && my < y + tileH && my >= top) {
                Cosmetic c = i == 0 ? null : capes.get(i - 1);
                if (c != null && !core.cosmetics().isUnlocked(c.id())) {
                    toast.show(c.name() + " ist gesperrt – Freischaltung über das Velora-Backend", Theme.WARNING);
                    return true;
                }
                core.config().client().equippedCape.set(c == null ? "" : c.id());
                toast.show(c == null ? "Cape abgelegt" : c.name() + " ausgerüstet", Theme.SUCCESS);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- profiles

    private void renderProfiles(GuiGraphicsExtractor ctx, int mx, int my) {
        Render2D.text(ctx, "Profile", cx, cy + 2, Theme.TEXT, 1.35f, false);
        Render2D.text(ctx, "Mods, Einstellungen & HUD-Layout pro Profil", cx, cy + 14, Theme.TEXT_DIM, 0.75f, false);
        profileName.render(ctx, null);
        for (int i = Page.values().length + 1; i < buttons.size(); i++) buttons.get(i).render(ctx, mx, my);

        ProfileManager profiles = core.config().profiles();
        int top = cy + 52;
        int y = (int) (top - scrollAnim.get());
        List<String> names = profiles.list();
        for (String name : names) {
            boolean active = name.equals(profiles.getActive());
            Render2D.roundedRectBordered(ctx, cx, y, cw, 24, Theme.RADIUS, Theme.SURFACE, active ? Theme.accent() : Theme.BORDER, 1);
            Render2D.text(ctx, name, cx + 8, y + 8, Theme.TEXT, false);
            if (active) {
                int bw = Render2D.width("AKTIV") + 10;
                Render2D.roundedRect(ctx, cx + 14 + Render2D.width(name), y + 6, bw, 12, 6, Theme.accentSoft());
                Render2D.text(ctx, "AKTIV", cx + 19 + Render2D.width(name), y + 8, Theme.accent(), 0.8f, false);
            }
            int bx = cx + cw - 8;
            for (String action : rowActions(active)) {
                int w = Render2D.width(action) + 12;
                bx -= w;
                boolean hover = mx >= bx && mx < bx + w && my >= y + 4 && my < y + 20;
                int color = action.equals("Löschen") ? (hover ? Theme.DANGER : ColorUtil.darken(Theme.DANGER, 0.3f)) : (hover ? Theme.SURFACE_ACTIVE : Theme.SIDEBAR);
                Render2D.roundedRect(ctx, bx, y + 4, w, 16, Theme.RADIUS_SMALL, color);
                Render2D.text(ctx, action, bx + 6, y + 8, Theme.TEXT, false);
                bx -= 4;
            }
            y += 28;
        }
        contentHeight = names.size() * 28;
        Render2D.text(ctx, "Tipp: Profil-Datei (.json) einfach in dieses Fenster ziehen, um sie zu importieren.",
                cx, cy + ch - 10, Theme.TEXT_MUTED, 0.75f, false);
    }

    private static List<String> rowActions(boolean active) {
        return active ? List.of("Exportieren") : List.of("Löschen", "Exportieren", "Laden");
    }

    private boolean clickProfiles(double mx, double my) {
        ProfileManager profiles = core.config().profiles();
        int top = cy + 52;
        int y = (int) (top - scrollAnim.get());
        for (String name : profiles.list()) {
            boolean active = name.equals(profiles.getActive());
            int bx = cx + cw - 8;
            for (String action : rowActions(active)) {
                int w = Render2D.width(action) + 12;
                bx -= w;
                if (mx >= bx && mx < bx + w && my >= y + 4 && my < y + 20 && my >= top) {
                    profileAction(action, name);
                    return true;
                }
                bx -= 4;
            }
            y += 28;
        }
        return false;
    }

    private void profileAction(String action, String name) {
        ConfigManager config = core.config();
        try {
            switch (action) {
                case "Laden" -> {
                    config.switchProfile(name);
                    toggles.clear();
                    toast.show("Profil \"" + name + "\" geladen", Theme.SUCCESS);
                }
                case "Löschen" -> {
                    config.profiles().delete(name);
                    toast.show("Profil \"" + name + "\" gelöscht", Theme.WARNING);
                }
                case "Exportieren" -> {
                    Path dir = config.getRoot().resolve("exports");
                    Files.createDirectories(dir);
                    Path target = dir.resolve(name + "-" + LocalDate.now() + ".json");
                    config.exportProfile(name, target);
                    toast.show("Exportiert nach config/velora/exports", Theme.SUCCESS);
                    com.mojang.blaze3d.Blaze3D.openPath(dir);
                }
                default -> { }
            }
        } catch (IOException | RuntimeException e) {
            toast.show("Fehler: " + e.getMessage(), Theme.DANGER);
        }
    }

    private void createProfile(boolean copy) {
        String name = profileName.get().trim();
        if (name.isEmpty()) name = copy ? core.config().profiles().getActive() + " Kopie" : "Profil";
        if (!ProfileManager.isValidName(name)) {
            toast.show("Nur Buchstaben, Zahlen, Leerzeichen, _ und - (max. 32)", Theme.DANGER);
            return;
        }
        try {
            String created = core.config().createProfile(name, copy);
            profileName.set("");
            toast.show("Profil \"" + created + "\" erstellt", Theme.SUCCESS);
        } catch (IOException | RuntimeException e) {
            toast.show("Fehler: " + e.getMessage(), Theme.DANGER);
        }
    }

    private void openImportFolder() {
        try {
            Path dir = core.config().getRoot().resolve("imports");
            Files.createDirectories(dir);
            com.mojang.blaze3d.Blaze3D.openPath(dir);
            toast.show("Ziehe .json-Dateien in das Minecraft-Fenster", Theme.accent());
        } catch (IOException e) {
            toast.show("Fehler: " + e.getMessage(), Theme.DANGER);
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        int imported = 0;
        for (Path p : paths) {
            if (!p.getFileName().toString().toLowerCase().endsWith(".json")) continue;
            try {
                core.config().importProfile(p);
                imported++;
            } catch (IOException | RuntimeException e) {
                toast.show("Import fehlgeschlagen: " + e.getMessage(), Theme.DANGER);
            }
        }
        if (imported > 0) {
            if (page != Page.PROFILES) setPage(Page.PROFILES);
            toast.show(imported + " Profil(e) importiert", Theme.SUCCESS);
        }
    }

    // ------------------------------------------------------------------ input

    private boolean onClick(double mx, double my, int button) {
        for (int i = 0; i <= Page.values().length; i++) {
            if (buttons.get(i).click(mx, my, button)) return true;
        }
        switch (page) {
            case MODS -> {
                if (selected != null) return clickSettings(mx, my, button);
                return clickGrid(mx, my, button);
            }
            case SETTINGS -> {
                return clickWidgets(mx, my, button);
            }
            case COSMETICS -> {
                return clickCosmetics(mx, my);
            }
            case PROFILES -> {
                profileName.click(mx, my);
                for (int i = Page.values().length + 1; i < buttons.size(); i++) {
                    if (buttons.get(i).click(mx, my, button)) return true;
                }
                return clickProfiles(mx, my);
            }
        }
        return false;
    }

    private boolean clickGrid(double mx, double my, int button) {
        if (search.click(mx, my)) return true;
        // chips
        List<int[]> chips = chipLayout();
        for (int i = 0; i < chips.size(); i++) {
            int[] r = chips.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + 16) {
                favoritesOnly = i == 1;
                category = i >= 2 ? Category.values()[i - 2] : null;
                lastCategory = category;
                lastFavorites = favoritesOnly;
                resetScroll();
                return true;
            }
        }
        int top = gridTop();
        if (my < top || my > cy + ch) return false;
        List<Module> mods = visibleModules();
        int cols = Math.max(1, (cw + GAP) / (CARD_MIN_W + GAP));
        int cardW = (cw - (cols - 1) * GAP) / cols;
        for (int i = 0; i < mods.size(); i++) {
            int cardX = cx + (i % cols) * (cardW + GAP);
            int cardY = (int) (top + (i / cols) * (CARD_H + GAP) - scrollAnim.get());
            if (mx < cardX || mx >= cardX + cardW || my < cardY || my >= cardY + CARD_H) continue;
            Module m = mods.get(i);
            if (button == 1 || (mx < cardX + 24 && my >= cardY + CARD_H - 18)) {
                openSettings(m);
            } else if (mx >= cardX + cardW - 18 && my < cardY + 18) {
                m.setFavorite(!m.isFavorite());
            } else if (button == 0) {
                m.toggle();
            }
            return true;
        }
        return false;
    }

    private boolean clickSettings(double mx, double my, int button) {
        if (mx >= cx && mx < cx + 60 && my >= cy && my < cy + 16) {
            selected = null;
            widgets = List.of();
            resetScroll();
            return true;
        }
        if (mx >= cx + cw - Toggle.WIDTH && mx < cx + cw && my >= cy + 22 && my < cy + 38) {
            selected.toggle();
            return true;
        }
        return clickWidgets(mx, my, button);
    }

    private boolean clickWidgets(double mx, double my, int button) {
        if (my < widgetsTop) return false;
        for (SettingWidget w : widgets) {
            if (!w.getSetting().isVisible()) continue;
            if (w.mouseClicked(mx, my, button)) {
                if (focusedWidget != null && focusedWidget != w) focusedWidget.unfocus();
                focusedWidget = w;
                return true;
            }
        }
        if (focusedWidget != null) {
            focusedWidget.unfocus();
            focusedWidget = null;
        }
        return false;
    }

    private boolean onDrag(double mx, double my, int button, double dx, double dy) {
        for (SettingWidget w : widgets) {
            if (w.mouseDragged(mx, my, button)) return true;
        }
        return false;
    }

    private boolean onRelease(double mx, double my, int button) {
        for (SettingWidget w : widgets) w.mouseReleased(mx, my, button);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int viewTop = page == Page.MODS && selected == null ? gridTop() : widgetsTop > 0 ? widgetsTop : cy + 40;
        int viewH = cy + ch - viewTop;
        double max = Math.max(0, contentHeight - viewH);
        scrollTarget = Math.max(0, Math.min(max, scrollTarget - vertical * 28));
        scrollAnim.animateTo(scrollTarget);
        return true;
    }

    private boolean widgetCapturing() {
        return focusedWidget != null && focusedWidget.isCapturingKeyboard();
    }

    private boolean onKey(int keyCode, int scanCode, int modifiers) {
        if (widgetCapturing()) {
            focusedWidget.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (page == Page.MODS && selected == null && search.key(keyCode, modifiers)) {
            resetScroll();
            return true;
        }
        if (page == Page.PROFILES && profileName.key(keyCode, modifiers)) {
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) createProfile(false);
            return true;
        }
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (selected != null) {
                selected = null;
                widgets = List.of();
                resetScroll();
                return true;
            }
            onClose();
            return true;
        }
        // typing anywhere on the mods page focuses the search
        if (page == Page.MODS && selected == null && keyCode == InputConstants.KEY_F && UiParts.ctrl(modifiers)) {
            search.focused = true;
            return true;
        }
        return super.keyPressed(currentKeyEvent);
    }

    private boolean onChar(char chr, int modifiers) {
        if (focusedWidget != null && focusedWidget.charTyped(chr, modifiers)) return true;
        if (page == Page.MODS && selected == null) {
            if (!search.focused && Character.isLetterOrDigit(chr)) search.focused = true;
            if (search.type(String.valueOf(chr))) {
                resetScroll();
                return true;
            }
        }
        if (page == Page.PROFILES && profileName.type(String.valueOf(chr))) return true;
        return false;
    }

    @Override
    public void onClose() {
        if (focusedWidget != null) focusedWidget.unfocus();
        minecraft.gui.setScreen(parent);
    }

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

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        boolean handled = false;
        for (char c : event.codepointAsString().toCharArray()) handled |= onChar(c, 0);
        return handled;
    }
}
