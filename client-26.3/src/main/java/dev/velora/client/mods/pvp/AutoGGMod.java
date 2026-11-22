package dev.velora.client.mods.pvp;

import dev.velora.client.api.TickListener;
import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.setting.NumberSetting;
import dev.velora.core.setting.TextSetting;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Sends a message after a game ends. Only server/system messages are inspected (never player chat),
 * a cooldown prevents spam if several end-of-game lines arrive at once.
 */
public final class AutoGGMod extends Module implements TickListener {
    private final TextSetting message = add(new TextSetting("message", "Nachricht", "Text oder /befehl", "gg", 100));
    private final TextSetting triggers = add(new TextSetting("triggers", "Auslöser", "Komma-getrennt, Groß/Klein egal",
            "1st Killer -,Winner:,Winners:,Victory!,You won,hat das Spiel gewonnen,Sieger:", 400));
    private final NumberSetting delay = add(new NumberSetting("delay", "Verzögerung", "", 1.0, 0, 5, 0.5, "s"));
    private final NumberSetting cooldown = add(new NumberSetting("cooldown", "Abklingzeit", "", 10, 3, 60, 1, "s"));

    private long lastSent;
    private int pendingTicks = -1;

    public AutoGGMod() {
        super("auto_gg", "AutoGG", "Schreibt automatisch \"gg\" am Spielende", Category.PVP, false);
    }

    /** Called for every game (system) message. */
    public void onGameMessage(Component text) {
        if (!isEnabled() || pendingTicks >= 0) return;
        long now = System.currentTimeMillis();
        if (now - lastSent < cooldown.getInt() * 1000L) return;
        String content = text.getString().toLowerCase(Locale.ROOT);
        List<String> list = Arrays.stream(triggers.get().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        for (String trigger : list) {
            if (content.contains(trigger.toLowerCase(Locale.ROOT))) {
                pendingTicks = (int) Math.round(delay.getDouble() * 20);
                return;
            }
        }
    }

    @Override
    public void onTick(Minecraft mc) {
        if (pendingTicks < 0) return;
        if (pendingTicks-- > 0) return;
        pendingTicks = -1;
        if (mc.player == null || mc.getConnection() == null) return;
        String msg = message.get().trim();
        if (msg.isEmpty()) return;
        if (msg.startsWith("/")) mc.getConnection().sendCommand(msg.substring(1));
        else mc.getConnection().sendChat(msg);
        lastSent = System.currentTimeMillis();
    }
}
