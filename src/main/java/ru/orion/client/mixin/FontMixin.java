package ru.orion.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.orion.client.effect.OrionFakePrefix;
import ru.orion.client.effect.OrionNameProtect;

/**
 * ProtectName: основные пути отрисовки текста (drawInBatch и GUI) проходят через
 * prepareText, а контурный текст — через prepare8xTextOutline.
 */
@Mixin(Font.class)
public abstract class FontMixin {
    @ModifyVariable(
            method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private String orion$protectPlainText(String text) {
        return OrionNameProtect.replace(OrionFakePrefix.replace(text));
    }

    @ModifyVariable(
            method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private FormattedCharSequence orion$protectFormattedText(FormattedCharSequence text) {
        return OrionNameProtect.replace(OrionFakePrefix.replace(text));
    }

    @ModifyVariable(method = "prepare8xTextOutline", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence orion$protectOutlineText(FormattedCharSequence text) {
        return OrionNameProtect.replace(OrionFakePrefix.replace(text));
    }

    // Ширина меряется до подмены в prepareText, поэтому скорборд/таб верстались под
    // реальный ник, и псевдоним вылезал за границы. Подменяем и в width(...).
    @ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
    private String orion$protectStringWidth(String text) {
        return OrionNameProtect.replace(OrionFakePrefix.replace(text));
    }

    @ModifyVariable(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedText orion$protectTextWidth(FormattedText text) {
        return OrionNameProtect.replace(OrionFakePrefix.replace(text));
    }

    @ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence orion$protectSequenceWidth(FormattedCharSequence text) {
        return OrionNameProtect.replace(OrionFakePrefix.replace(text));
    }
}
