package dev.velora.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.velora.client.Mods;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fullbright (26.3): replaces the gamma option read in {@code LightmapRenderStateExtractor#extract}
 * (first Double#floatValue call = gamma, second = darkness effect scale – verified against 26.3 sources).
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapTextureManagerMixin {
    @ModifyExpressionValue(method = "extract", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0))
    private float velora$fullbright(float gamma) {
        if (Mods.ready() && Mods.fullbright.isEnabled()) return Mods.fullbright.gamma();
        return gamma;
    }
}
