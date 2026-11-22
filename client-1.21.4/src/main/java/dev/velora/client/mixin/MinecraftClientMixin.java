package dev.velora.client.mixin;

import dev.velora.client.gui.VeloraTitleScreen;
import dev.velora.core.VeloraCore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Replaces the vanilla title screen with the Velora main menu (if enabled). */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen velora$titleScreen(Screen screen) {
        if (screen instanceof TitleScreen && VeloraCore.isReady() && VeloraCore.get().config().client().customTitleScreen.isOn()) {
            return new VeloraTitleScreen();
        }
        return screen;
    }
}
