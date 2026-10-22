package ru.orion.client.mixin;

import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.orion.client.effect.OrionFakePrefix;
import ru.orion.client.effect.OrionNameProtect;

/** Чат в 26.3 пишет строки напрямую в ActiveTextCollector, минуя Font. */
@Mixin(targets = {
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess"
})
public abstract class ChatGraphicsAccessMixin {
    @ModifyVariable(method = "handleMessage", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence orion$replaceChatLine(FormattedCharSequence sequence) {
        return OrionNameProtect.replace(OrionFakePrefix.replaceChat(sequence));
    }
}
