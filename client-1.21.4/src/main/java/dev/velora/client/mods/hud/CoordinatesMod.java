package dev.velora.client.mods.hud;

import dev.velora.client.hud.HudMod;
import dev.velora.core.hud.Anchor;
import dev.velora.core.module.Category;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ColorSetting;
import dev.velora.core.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CoordinatesMod extends HudMod {
    private final NumberSetting decimals = add(new NumberSetting("decimals", "Nachkommastellen", "", 0, 0, 3, 1));
    private final BooleanSetting showFacing = add(new BooleanSetting("show_facing", "Blickrichtung", "", true));
    private final BooleanSetting showOther = add(new BooleanSetting("show_other_dimension", "Nether/Oberwelt umrechnen", "", false));
    private final ColorSetting axisColor = add(new ColorSetting("axis_color", "Achsenfarbe", "", 0xFFB9A8FF));

    public CoordinatesMod() {
        super("coordinates", "Coordinates", "Zeigt die aktuelle Position", Category.HUD, false, Anchor.TOP_LEFT, 4, 40);
    }

    private String fmt(double v) {
        return String.format(Locale.ROOT, "%." + decimals.getInt() + "f", v);
    }

    private List<String[]> lines(boolean preview) {
        List<String[]> l = new ArrayList<>(5);
        double x = 0, y = 64, z = 0;
        Direction facing = Direction.NORTH;
        boolean nether = false;
        if (MC.player != null) {
            x = MC.player.getX();
            y = MC.player.getY();
            z = MC.player.getZ();
            facing = MC.player.getHorizontalFacing();
            nether = MC.player.getWorld().getRegistryKey() == World.NETHER;
        } else if (!preview) {
            return l;
        }
        l.add(new String[]{"X", fmt(x)});
        l.add(new String[]{"Y", fmt(y)});
        l.add(new String[]{"Z", fmt(z)});
        if (showFacing.isOn()) {
            String axis = switch (facing) {
                case NORTH -> "-Z";
                case SOUTH -> "+Z";
                case EAST -> "+X";
                case WEST -> "-X";
                default -> "";
            };
            l.add(new String[]{"F", DirectionMod.name(facing) + " (" + axis + ")"});
        }
        if (showOther.isOn()) {
            double f = nether ? 8 : 1 / 8d;
            l.add(new String[]{nether ? "OW" : "N", fmt(x * f) + ", " + fmt(z * f)});
        }
        return l;
    }

    @Override
    public int getWidth(boolean preview) {
        int w = 0;
        for (String[] line : lines(preview)) w = Math.max(w, textWidth(line[0] + ": ") + textWidth(line[1]));
        return w + 10;
    }

    @Override
    public int getHeight(boolean preview) {
        return lines(preview).size() * 10 + 6;
    }

    @Override
    protected void renderContent(DrawContext ctx, float tickDelta, boolean preview) {
        int y = 4;
        for (String[] line : lines(preview)) {
            String key = line[0] + ": ";
            drawString(ctx, key, 5, y, faded(axisColor.getRenderColor()));
            drawString(ctx, line[1], 5 + textWidth(key), y, text());
            y += 10;
        }
    }
}
