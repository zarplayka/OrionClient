package ru.orion.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.hud.OrionGlassCompositor;

import java.util.function.Supplier;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererGlassMixin {
    @Inject(method = "executeDrawRange(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;II)V",
            at = @At("HEAD"))
    private void orion$captureGlassBackground(Supplier<String> label, RenderTarget target,
                                            GpuBufferSlice transforms, int from, int to, CallbackInfo callback) {
        OrionGlassCompositor.INSTANCE.captureBeforeGui(target.getColorTexture());
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void orion$releaseGlass(CallbackInfo callback) {
        OrionGlassCompositor.INSTANCE.close();
    }
}
