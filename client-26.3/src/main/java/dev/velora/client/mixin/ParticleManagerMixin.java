package dev.velora.client.mixin;

import dev.velora.client.Mods;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Queue;

/** Particle Optimizer: drop new particles according to density and a hard cap. */
@Mixin(ParticleEngine.class)
public abstract class ParticleManagerMixin {
    @Shadow @Final private Map<ParticleRenderType, Queue<Particle>> particles;
    @Shadow @Final private Queue<Particle> particlesToAdd;

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void velora$limit(Particle particle, CallbackInfo ci) {
        if (!Mods.ready() || !Mods.particleOptimizer.isEnabled()) return;
        int count = particlesToAdd.size();
        for (Queue<Particle> q : particles.values()) count += q.size();
        if (Mods.particleOptimizer.shouldDrop(count)) ci.cancel();
    }
}
