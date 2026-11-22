package dev.velora.client.mods.hud;

import dev.velora.client.hud.HudMod;
import dev.velora.core.hud.Anchor;
import dev.velora.core.module.Category;
import dev.velora.core.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Active status effects with icon, level and remaining time. Also serves as "Potion HUD" for PvP. */
public final class PotionEffectsMod extends HudMod {
    private static final int ROW = 20;
    private static final String[] ROMAN = {"", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private final BooleanSetting showIcons = add(new BooleanSetting("show_icons", "Symbole", "", true));
    private final BooleanSetting blink = add(new BooleanSetting("blink", "Blinken bei Ablauf", "Blinkt in den letzten 10 Sekunden", true));
    private final BooleanSetting hideVanilla = add(new BooleanSetting("hide_vanilla", "Vanilla-Anzeige ausblenden", "Blendet die Effekt-Symbole oben rechts aus", true));

    public PotionEffectsMod() {
        super("potion_effects", "Potion Effects", "Aktive Effekte mit Restzeit (auch als Potion HUD)", Category.HUD, false, Anchor.MIDDLE_LEFT, 4, 0);
    }

    public boolean hidesVanilla() { return isEnabled() && hideVanilla.isOn(); }

    private record Entry(RegistryEntry<StatusEffect> type, String name, String time, boolean expiring) { }

    private List<Entry> entries(boolean preview) {
        List<Entry> list = new ArrayList<>();
        if (MC.player != null) {
            Collection<StatusEffectInstance> effects = MC.player.getStatusEffects();
            float tickRate = MC.world != null ? MC.world.getTickManager().getTickRate() : 20f;
            for (StatusEffectInstance e : effects) {
                if (!e.shouldShowIcon()) continue;
                String name = e.getEffectType().value().getName().getString();
                int amp = e.getAmplifier();
                if (amp > 0) name += " " + (amp < ROMAN.length ? ROMAN[amp] : Integer.toString(amp + 1));
                String time = StatusEffectUtil.getDurationText(e, 1.0f, tickRate).getString();
                list.add(new Entry(e.getEffectType(), name, time, !e.isInfinite() && e.getDuration() < 200));
            }
        }
        if (list.isEmpty() && preview) {
            list.add(new Entry(StatusEffects.SPEED, "Schnelligkeit II", "1:30", false));
            list.add(new Entry(StatusEffects.STRENGTH, "Stärke", "0:08", true));
        }
        return list;
    }

    @Override
    public boolean hasContent() { return MC.player != null && !MC.player.getStatusEffects().isEmpty(); }

    @Override
    public int getWidth(boolean preview) {
        int w = 0;
        for (Entry e : entries(preview)) w = Math.max(w, Math.max(textWidth(e.name), textWidth(e.time)));
        return 4 + (showIcons.isOn() ? 22 : 0) + w + 4;
    }

    @Override
    public int getHeight(boolean preview) {
        return 2 + Math.max(1, entries(preview).size()) * ROW;
    }

    @Override
    protected void renderContent(DrawContext ctx, float tickDelta, boolean preview) {
        int y = 2;
        boolean blinkOff = blink.isOn() && (System.currentTimeMillis() / 400) % 2 == 0;
        for (Entry e : entries(preview)) {
            int x = 4;
            if (showIcons.isOn()) {
                Sprite sprite = MC.getStatusEffectSpriteManager().getSprite(e.type);
                ctx.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, x, y + 1, 18, 18);
                x += 22;
            }
            drawString(ctx, e.name, x, y + 1, text());
            int timeColor = e.expiring && blinkOff ? faded(0xFFFF5C7A) : faded(0xFFB0B6C8);
            drawString(ctx, e.time, x, y + 10, timeColor);
            y += ROW;
        }
    }
}
