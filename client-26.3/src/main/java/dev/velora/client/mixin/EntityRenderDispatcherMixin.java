package dev.velora.client.mixin;

import dev.velora.client.Mods;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity Culling: skip rendering entities beyond the configured distances. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void velora$cull(E entity, Frustum culler, double camX, double camY, double camZ, float partialTicks,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (Mods.ready() && Mods.entityCulling.isEnabled() && Mods.entityCulling.shouldCull(entity, camX, camY, camZ)) {
            cir.setReturnValue(false);
        }
    }
}
