package dev.velora.client.mixin;

import dev.velora.client.gui.VeloraTitleScreen;
import dev.velora.core.VeloraCore;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Replaces the vanilla title screen with the Velora main menu (26.3: screens are managed by {@link Gui}). */
@Mixin(Gui.class)
public abstract class MinecraftClientMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen velora$titleScreen(Screen screen) {
        if (screen instanceof TitleScreen && VeloraCore.isReady() && VeloraCore.get().config().client().customTitleScreen.isOn()) {
            return new VeloraTitleScreen();
        }
        return screen;
    }
}
