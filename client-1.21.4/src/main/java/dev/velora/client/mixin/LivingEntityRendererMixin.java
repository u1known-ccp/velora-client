package dev.velora.client.mixin;

import dev.velora.client.Mods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nametags: render the local player's own nametag in third person. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true)
    private void velora$ownNametag(LivingEntity entity, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !Mods.ready() || !Mods.nametags.isEnabled() || !Mods.nametags.showOwn.isOn()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == mc.player && !mc.options.getPerspective().isFirstPerson() && MinecraftClient.isHudEnabled()
                && !entity.isInvisibleTo(mc.player)) {
            cir.setReturnValue(true);
        }
    }
}
