package dev.velora.client.mixin;

import dev.velora.client.Mods;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat Customizer: timestamps and configurable history length (26.3: private addMessage with a message source). */
@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {
    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
    private Component velora$timestamp(Component message) {
        return Mods.ready() ? Mods.chatCustomizer.decorate(message) : message;
    }

    @ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
    private int velora$history(int original) {
        return Mods.ready() ? Mods.chatCustomizer.historyLimit() : original;
    }
}
