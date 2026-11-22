package dev.velora.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.velora.client.Mods;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fullbright: replaces the gamma value read inside {@code update} (second Double#floatValue call:
 * the first is the darkness effect scale, the second the gamma option – verified against 1.21.4 sources).
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
    private float velora$fullbright(float gamma) {
        if (Mods.ready() && Mods.fullbright.isEnabled()) return Mods.fullbright.gamma();
        return gamma;
    }
}
