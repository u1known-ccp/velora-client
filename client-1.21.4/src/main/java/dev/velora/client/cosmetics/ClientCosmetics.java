package dev.velora.client.cosmetics;

import dev.velora.core.VeloraCore;
import dev.velora.core.config.ClientSettings;
import dev.velora.core.cosmetics.CosmeticsManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Optional;

/** Decides which cape (if any) a rendered player wears. Called from AbstractClientPlayerEntityMixin. */
public final class ClientCosmetics {
    private ClientCosmetics() { }

    public static Identifier capeFor(AbstractClientPlayerEntity player) {
        if (!VeloraCore.isReady()) return null;
        ClientSettings settings = VeloraCore.get().config().client();
        if (!settings.cosmeticsEnabled.isOn()) return null;
        CosmeticsManager cosmetics = VeloraCore.get().cosmetics();
        MinecraftClient mc = MinecraftClient.getInstance();
        String id;
        if (mc.player != null && player.getUuid().equals(mc.player.getUuid())) {
            id = settings.equippedCape.get();
            // permission check: locked cosmetics are never rendered, even if the config was edited by hand
            if (id.isEmpty() || !cosmetics.isUnlocked(id)) return null;
        } else {
            if (!settings.showOthersCosmetics.isOn()) return null;
            Optional<String> remote = cosmetics.getRemoteCape(player.getUuid());
            if (remote.isEmpty()) return null;
            id = remote.get();
        }
        return CapeTextures.get(id);
    }
}
