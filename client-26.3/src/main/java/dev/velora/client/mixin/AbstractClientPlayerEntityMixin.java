package dev.velora.client.mixin;

import dev.velora.client.cosmetics.ClientCosmetics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cosmetics: replace cape (and elytra) texture with the player's Velora cape. 26.3 uses ClientAsset textures. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void velora$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        Identifier cape = ClientCosmetics.capeFor((AbstractClientPlayer) (Object) this);
        if (cape == null) return;
        PlayerSkin s = cir.getReturnValue();
        ClientAsset.Texture texture = new ClientAsset.ResourceTexture(cape, cape);
        cir.setReturnValue(new PlayerSkin(s.body(), texture, texture, s.model(), s.secure()));
    }
}
