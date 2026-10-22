package ru.orion.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.orion.client.effect.OrionFakePrefix;
import ru.orion.client.effect.OrionNameProtect;

/** Подмена до передачи GUI-текста в отложенный сборщик рендера. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
    @ModifyVariable(
            method = {
                    "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
                    "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
            },
            at = @At("HEAD"),
            argsOnly = true
    )
    private Component orion$replaceComponent(Component component) {
        return OrionNameProtect.replace(OrionFakePrefix.replace(component));
    }

    @ModifyVariable(
            method = {
                    "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V",
                    "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"
            },
            at = @At("HEAD"),
            argsOnly = true
    )
    private FormattedCharSequence orion$replaceSequence(FormattedCharSequence sequence) {
        return OrionNameProtect.replace(OrionFakePrefix.replace(sequence));
    }
}
