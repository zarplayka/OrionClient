package ru.orion.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.hud.OrionScoreboardGlass;

@Mixin(Hud.class)
public abstract class HudScoreboardGlassMixin {
    @Unique
    private final OrionScoreboardGlass orion$scoreboardGlass = new OrionScoreboardGlass();

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"))
    private void orion$beginHud(GuiGraphicsExtractor context, DeltaTracker delta, CallbackInfo callback) {
        orion$scoreboardGlass.beginFrame();
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("RETURN"))
    private void orion$endHud(GuiGraphicsExtractor context, DeltaTracker delta, CallbackInfo callback) {
        orion$scoreboardGlass.endFrame();
    }

    @Inject(method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("HEAD"))
    private void orion$beginSidebar(GuiGraphicsExtractor context, Objective objective, CallbackInfo callback) {
        orion$scoreboardGlass.beginSidebar(context);
    }

    @Inject(method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("RETURN"))
    private void orion$endSidebar(GuiGraphicsExtractor context, Objective objective, CallbackInfo callback) {
        orion$scoreboardGlass.endSidebar(context);
    }

    @Redirect(method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0))
    private void orion$header(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
        orion$scoreboardGlass.header(context, x0, y0, x1, y1, color);
    }

    @Redirect(method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1))
    private void orion$body(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
        orion$scoreboardGlass.body(context, x0, y0, x1, y1, color);
    }
}
