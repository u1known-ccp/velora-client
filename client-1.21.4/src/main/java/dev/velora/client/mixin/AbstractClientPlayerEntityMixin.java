package dev.velora.client.mixin;

import dev.velora.client.cosmetics.ClientCosmetics;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cosmetics: replace cape (and elytra) texture with the player's Velora cape. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void velora$cape(CallbackInfoReturnable<SkinTextures> cir) {
        Identifier cape = ClientCosmetics.capeFor((AbstractClientPlayerEntity) (Object) this);
        if (cape == null) return;
        SkinTextures t = cir.getReturnValue();
        cir.setReturnValue(new SkinTextures(t.texture(), t.textureUrl(), cape, cape, t.model(), t.secure()));
    }
}
