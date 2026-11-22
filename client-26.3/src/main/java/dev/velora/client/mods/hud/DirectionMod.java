package dev.velora.client.mods.hud;

import dev.velora.client.hud.HudMod;
import dev.velora.client.render.Render2D;
import dev.velora.core.hud.Anchor;
import dev.velora.core.module.Category;
import dev.velora.core.setting.ColorSetting;
import dev.velora.core.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/** Compass strip that scrolls with the player's yaw. */
public final class DirectionMod extends HudMod {
    private static final String[] LABELS = {"S", "SW", "W", "NW", "N", "NO", "O", "SO"};

    private final NumberSetting width = add(new NumberSetting("width", "Breite", "", 140, 80, 260, 2, "px"));
    private final NumberSetting range = add(new NumberSetting("range", "Sichtbereich", "Gradzahl, die die Leiste zeigt", 180, 90, 360, 10, "°"));
    private final ColorSetting markerColor = add(new ColorSetting("marker_color", "Markierung", "", 0xFF7C5CFF));

    public DirectionMod() {
        super("direction", "Direction HUD", "Kompassleiste mit Himmelsrichtungen", Category.HUD, false, Anchor.TOP_CENTER, 0, 4);
    }

    static String name(Direction d) {
        return switch (d) {
            case NORTH -> "Norden";
            case SOUTH -> "Süden";
            case EAST -> "Osten";
            case WEST -> "Westen";
            case UP -> "Oben";
            case DOWN -> "Unten";
        };
    }

    @Override public int getWidth(boolean preview) { return width.getInt(); }

    @Override public int getHeight(boolean preview) { return 22; }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, float tickDelta, boolean preview) {
        float yaw = MC.player != null ? Mth.wrapDegrees(MC.player.getViewYRot(tickDelta)) : 0f;
        int w = width.getInt();
        float pxPerDeg = w / range.getFloat();
        float center = w / 2f;
        ctx.enableScissor(0, 0, w, 22);
        // yaw 0 = south in Minecraft; labels every 45°
        for (int i = 0; i < 8; i++) {
            float deg = i * 45f;
            float diff = Mth.wrapDegrees(deg - yaw);
            float x = center + diff * pxPerDeg;
            if (x < -10 || x > w + 10) continue;
            String label = LABELS[i];
            boolean cardinal = label.length() == 1;
            int color = cardinal ? text() : faded(0xFF9AA0B4);
            int tw = textWidth(label);
            drawString(ctx, label, Math.round(x - tw / 2f), 3, color);
        }
        // minor ticks every 15°
        for (int i = 0; i < 24; i++) {
            float diff = Mth.wrapDegrees(i * 15f - yaw);
            float x = center + diff * pxPerDeg;
            if (x < 0 || x > w) continue;
            Render2D.rect(ctx, x - 0.25f, 15, 0.5f, i % 3 == 0 ? 4 : 2, faded(0x80FFFFFF));
        }
        ctx.disableScissor();
        Render2D.rect(ctx, center - 0.5f, 13, 1, 8, faded(markerColor.getRenderColor()));
        // compass heading (north = 0°) on the right
        String deg = Math.floorMod(Math.round(yaw + 180f), 360) + "°";
        drawString(ctx, deg, w - textWidth(deg) - 3, 13, faded(0xFF9AA0B4));
    }
}
