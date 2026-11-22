package dev.velora.client.api;

import net.minecraft.client.Minecraft;

/** Implemented by modules that need a client tick (20/s). Only enabled modules are ticked. */
public interface TickListener {
    void onTick(Minecraft mc);
}
