package ru.orion.client.mixin;

import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.effect.OrionRainbow;

/**
 * HitBox: когда модуль включён, заменяет ванильный хитбокс (F3+B) на чистый цветной бокс —
 * без красной планки глаз, синего вектора взгляда и серверного дубля.
 */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {
    @Inject(
            method = "showHitboxes(Lnet/minecraft/world/entity/Entity;FZ)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void orion$cleanColoredHitbox(Entity entity, float partialTick, boolean serverBox, CallbackInfo ci) {
        OrionConfig config = OrionClient.getConfigOrNull();
        if (config == null || !config.isHitBoxEnabled()) {
            return;
        }

        ci.cancel();
        if (serverBox) {
            return; // серверный дубль из дебага не рисуем вовсе
        }

        int color = OrionRainbow.orElse(orion$parseColor(config.getHitBoxColor()));
        Vec3 offset = entity.getPosition(partialTick).subtract(entity.position());
        Gizmos.cuboid(entity.getBoundingBox().move(offset), GizmoStyle.stroke(color));
    }

    private static int orion$parseColor(String hex) {
        try {
            return ARGB.opaque(Integer.parseInt(hex.substring(1), 16));
        } catch (RuntimeException ignored) {
            return ARGB.color(255, 255, 124, 0);
        }
    }
}
