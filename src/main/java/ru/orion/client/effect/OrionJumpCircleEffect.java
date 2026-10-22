package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfigManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawns an expanding ring on the ground the moment the player jumps and renders it in world space.
 * Reimplemented for the MC 26.3 submit/extraction pipeline (no external code or assets).
 */
public final class OrionJumpCircleEffect {
    private static final double JUMP_VELOCITY_THRESHOLD = 0.08; // tells a jump apart from walking off a ledge
    private static final float ANIM_DURATION = 0.60F;           // seconds for the full ring animation
    private static final float MIN_RADIUS = 0.15F;
    private static final float MAX_RADIUS = 0.55F; // итоговый диаметр ~1.1 блока
    private static final float RING_THICKNESS = 0.18F;          // inner edge = outer * (1 - thickness)
    private static final int SEGMENTS = 48;
    private static final int BASE_ALPHA = 210;
    private static final int MAX_ACTIVE_CIRCLES = 16;
    private static final int FALLBACK_ACCENT = ARGB.color(255, 255, 124, 0); // #FF7C00

    private final OrionConfigManager configManager;
    private final List<Circle> circles = new ArrayList<>();
    private boolean wasOnGround = true;
    private double lastGroundY;

    public OrionJumpCircleEffect(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::collectSubmits);
    }

    private void tick(Minecraft client) {
        LocalPlayer player = client.player;

        if (player == null) {
            wasOnGround = true;
            circles.clear();
            return;
        }

        long now = System.nanoTime();
        circles.removeIf(circle -> circle.age(now) >= ANIM_DURATION);

        boolean onGround = player.onGround();

        if (onGround) {
            // Remember the real ground height: the jump is only detected one tick later,
            // by which point the player has already risen off the floor.
            lastGroundY = player.getY();
        }

        if (configManager.getConfig().isJumpCircleEnabled()
                && wasOnGround
                && !onGround
                && player.getDeltaMovement().y > JUMP_VELOCITY_THRESHOLD) {
            if (circles.size() >= MAX_ACTIVE_CIRCLES) {
                circles.remove(0);
            }
            circles.add(new Circle(player.getX(), lastGroundY + 0.02, player.getZ(), now));
        }

        wasOnGround = onGround;
    }

    private void collectSubmits(LevelRenderContext context) {
        if (circles.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Camera camera = client.gameRenderer.mainCamera();
        Vec3 cam = camera.position();
        PoseStack poseStack = context.poseStack();
        RenderType renderType = RenderTypes.debugQuads();
        int rgb = OrionRainbow.orElse(parseColor(configManager.getConfig().getJumpCircleColor()));
        float size = configManager.getConfig().getJumpCircleSize();

        long now = System.nanoTime();
        for (Circle circle : circles) {
            float progress = Math.min(1.0F, circle.age(now) / ANIM_DURATION);
            float radius = (MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * easeOutCubic(progress)) * size;
            int alpha = Math.round(BASE_ALPHA * fade(progress));
            int color = ARGB.color(alpha, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));

            poseStack.pushPose();
            poseStack.translate(circle.x - cam.x, circle.y - cam.y, circle.z - cam.z);

            float outer = radius;
            float inner = radius * (1.0F - RING_THICKNESS);
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    renderType,
                    (pose, vertexConsumer) -> buildRing(pose, vertexConsumer, inner, outer, color)
            );

            poseStack.popPose();
        }
    }

    private static void buildRing(PoseStack.Pose pose, VertexConsumer vc, float inner, float outer, int color) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = (Math.PI * 2.0 / SEGMENTS) * i;
            double a1 = (Math.PI * 2.0 / SEGMENTS) * (i + 1);
            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);

            // Flat quad on the XZ plane (local y = 0): innerA -> outerA -> outerB -> innerB.
            vc.addVertex(pose, inner * cos0, 0.0F, inner * sin0).setColor(color);
            vc.addVertex(pose, outer * cos0, 0.0F, outer * sin0).setColor(color);
            vc.addVertex(pose, outer * cos1, 0.0F, outer * sin1).setColor(color);
            vc.addVertex(pose, inner * cos1, 0.0F, inner * sin1).setColor(color);
        }
    }

    private static float easeOutCubic(float t) {
        float f = 1.0F - t;
        return 1.0F - f * f * f;
    }

    /** Quick fade-in, hold, then fade-out across the lifetime. */
    private static float fade(float progress) {
        if (progress < 0.12F) {
            return progress / 0.12F;
        }

        if (progress > 0.55F) {
            return Math.max(0.0F, 1.0F - (progress - 0.55F) / 0.45F);
        }

        return 1.0F;
    }

    private static int parseColor(String color) {
        try {
            int rgb = Integer.parseUnsignedInt(color.substring(1), 16);
            return ARGB.color(255, rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255);
        } catch (RuntimeException exception) {
            return FALLBACK_ACCENT;
        }
    }

    private static final class Circle {
        private final double x;
        private final double y;
        private final double z;
        private final long spawnNanos;

        private Circle(double x, double y, double z, long spawnNanos) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.spawnNanos = spawnNanos;
        }

        private float age(long now) {
            return (now - spawnNanos) / 1_000_000_000.0F;
        }
    }
}
