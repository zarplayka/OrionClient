package ru.orion.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.effect.OrionRainbow;
import ru.orion.client.effect.OrionWorldChanger;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void orion$applyWorldChangerSky(ClientLevel level, float tickDelta, Camera camera, SkyRenderState state, CallbackInfo ci) {
        OrionConfig config = OrionClient.getConfigOrNull();
        if (config == null || !config.isWorldChangerEnabled()) {
            return;
        }

        state.skyColor = ARGB.vector3fFromRGB24(OrionRainbow.orElse(
                OrionWorldChanger.parseOpaqueColor(config.getWorldChangerSkyColor())));
    }
}
