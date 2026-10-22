package ru.orion.client.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("GUI_SNIPPET")
    static RenderPipeline.Snippet orion$getGuiSnippet() {
        throw new AssertionError();
    }

    @Invoker("register")
    static RenderPipeline orion$register(RenderPipeline pipeline) {
        throw new AssertionError();
    }

    @Invoker("registerOptional")
    static RenderPipeline orion$registerOptional(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}
