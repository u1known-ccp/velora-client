package dev.velora.client.mods.hud;

import dev.velora.client.hud.HudMod;
import dev.velora.core.hud.Anchor;
import dev.velora.core.module.Category;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ModeSetting;
import dev.velora.core.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Armor pieces (and optionally held items) with durability. Also serves as "Armor HUD" for PvP. */
public final class ArmorStatusMod extends HudMod {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int ROW = 18;

    private final ModeSetting layout = add(new ModeSetting("layout", "Ausrichtung", "", "Vertikal", "Vertikal", "Horizontal"));
    private final ModeSetting durability = add(new ModeSetting("durability", "Haltbarkeit", "", "Prozent", "Prozent", "Wert", "Aus"));
    private final BooleanSetting showHeld = add(new BooleanSetting("show_held", "Gehaltene Items", "Haupt- und Nebenhand anzeigen", true));
    private final BooleanSetting colorDurability = add(new BooleanSetting("color_durability", "Haltbarkeit einfärben", "", true));

    public ArmorStatusMod() {
        super("armor_status", "Armor Status", "Rüstung und Haltbarkeit (auch als Armor HUD)", Category.HUD, false, Anchor.MIDDLE_RIGHT, -4, 0);
    }

    private List<ItemStack> stacks(boolean preview) {
        List<ItemStack> list = new ArrayList<>(6);
        if (MC.player != null) {
            if (showHeld.isOn()) {
                ItemStack main = MC.player.getMainHandStack();
                if (!main.isEmpty()) list.add(main);
            }
            for (EquipmentSlot slot : ARMOR) {
                ItemStack s = MC.player.getEquippedStack(slot);
                if (!s.isEmpty()) list.add(s);
            }
            if (showHeld.isOn()) {
                ItemStack off = MC.player.getOffHandStack();
                if (!off.isEmpty()) list.add(off);
            }
        }
        if (list.isEmpty() && preview) {
            list.add(new ItemStack(Items.DIAMOND_HELMET));
            list.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
            list.add(new ItemStack(Items.DIAMOND_LEGGINGS));
            list.add(new ItemStack(Items.DIAMOND_BOOTS));
        }
        return list;
    }

    private String durabilityText(ItemStack s) {
        if (durability.is("Aus")) return "";
        if (s.isDamageable()) {
            int max = s.getMaxDamage();
            int left = max - s.getDamage();
            return durability.is("Prozent") ? Math.round(left * 100f / max) + "%" : Integer.toString(left);
        }
        return s.getCount() > 1 ? Integer.toString(s.getCount()) : "";
    }

    private int durabilityColor(ItemStack s) {
        if (!colorDurability.isOn() || !s.isDamageable()) return text();
        float frac = 1f - s.getDamage() / (float) s.getMaxDamage();
        return faded(ColorUtil.healthColor(frac));
    }

    private int textColumnWidth(List<ItemStack> list) {
        int w = 0;
        for (ItemStack s : list) w = Math.max(w, textWidth(durabilityText(s)));
        return w;
    }

    @Override
    public boolean hasContent() { return !stacks(false).isEmpty(); }

    @Override
    public int getWidth(boolean preview) {
        List<ItemStack> list = stacks(preview);
        int tw = textColumnWidth(list);
        if (layout.is("Vertikal")) return 4 + 16 + (tw > 0 ? 3 + tw : 0) + 4;
        int cell = 16 + (tw > 0 ? 2 + tw : 0) + 4;
        return 4 + Math.max(1, list.size()) * cell;
    }

    @Override
    public int getHeight(boolean preview) {
        int n = Math.max(1, stacks(preview).size());
        return layout.is("Vertikal") ? 2 + n * ROW : ROW + 4;
    }

    @Override
    protected void renderContent(DrawContext ctx, float tickDelta, boolean preview) {
        List<ItemStack> list = stacks(preview);
        int tw = textColumnWidth(list);
        boolean vertical = layout.is("Vertikal");
        int x = 4;
        int y = vertical ? 2 : 2;
        for (ItemStack s : list) {
            ctx.drawItem(s, x, y + 1);
            String d = durabilityText(s);
            if (!d.isEmpty()) drawString(ctx, d, x + 19, y + 5, durabilityColor(s));
            if (vertical) {
                y += ROW;
            } else {
                x += 16 + (tw > 0 ? 2 + tw : 0) + 4;
            }
        }
    }
}
