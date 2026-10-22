package ru.orion.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;

@Mixin(EntityRenderer.class)
public class EntityRendererFireMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void orion$noEntityFire(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        OrionConfig config = OrionClient.getConfigOrNull();
        if (config != null && config.isNoFireEnabled()) {
            state.displayFireAnimation = false;
        }
    }
}
