package dev.velora.client.mixin;

import dev.velora.client.Mods;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat Customizer: timestamps and configurable history length. */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true)
    private Text velora$timestamp(Text message) {
        return Mods.ready() ? Mods.chatCustomizer.decorate(message) : message;
    }

    @ModifyConstant(method = {"addVisibleMessage", "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V"}, constant = @Constant(intValue = 100))
    private int velora$history(int original) {
        return Mods.ready() ? Mods.chatCustomizer.historyLimit() : original;
    }
}
