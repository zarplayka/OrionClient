package ru.orion.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.effect.OrionWorldChanger;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void orion$applyWorldChangerFog(Camera camera, int renderDistance, DeltaTracker tickCounter, float darkenWorldAmount,
                                            ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        OrionConfig config = OrionClient.getConfigOrNull();
        if (config == null || !config.isWorldChangerEnabled()) {
            return;
        }

        cir.getReturnValue().color.set(OrionWorldChanger.fogVector(config.getWorldChangerFogColor()));
    }
}
