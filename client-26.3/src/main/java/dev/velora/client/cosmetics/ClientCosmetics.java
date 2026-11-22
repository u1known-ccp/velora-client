package dev.velora.client.cosmetics;

import dev.velora.core.VeloraCore;
import dev.velora.core.config.ClientSettings;
import dev.velora.core.cosmetics.CosmeticsManager;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;

/** Decides which cape (if any) a rendered player wears. Called from AbstractClientPlayerEntityMixin. */
public final class ClientCosmetics {
    private ClientCosmetics() { }

    public static Identifier capeFor(AbstractClientPlayer player) {
        if (!VeloraCore.isReady()) return null;
        ClientSettings settings = VeloraCore.get().config().client();
        if (!settings.cosmeticsEnabled.isOn()) return null;
        CosmeticsManager cosmetics = VeloraCore.get().cosmetics();
        Minecraft mc = Minecraft.getInstance();
        String id;
        if (mc.player != null && player.getUUID().equals(mc.player.getUUID())) {
            id = settings.equippedCape.get();
            // permission check: locked cosmetics are never rendered, even if the config was edited by hand
            if (id.isEmpty() || !cosmetics.isUnlocked(id)) return null;
        } else {
            if (!settings.showOthersCosmetics.isOn()) return null;
            Optional<String> remote = cosmetics.getRemoteCape(player.getUUID());
            if (remote.isEmpty()) return null;
            id = remote.get();
        }
        return CapeTextures.get(id);
    }
}
