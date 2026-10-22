package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfigManager;

public final class OrionChinaHeadRenderer {
    private static final int SEGMENTS = 64;
    private static final float RADIUS = 0.55F;
    private static final float HEIGHT = 0.42F;
    private static final float HEAD_OFFSET = 0.28F;
    private static final int FILL_ALPHA = 185;

    private final OrionConfigManager configManager;

    public OrionChinaHeadRenderer(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    private void render(LevelRenderContext context) {
        if (!configManager.getConfig().isChinaHeadEnabled()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.options.getCameraType().isFirstPerson() || !player.isAlive() || player.isInvisible()) {
            return;
        }

        Camera camera = client.gameRenderer.mainCamera();
        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 playerPosition = interpolatedPosition(player, tickDelta);
        Vec3 cameraPosition = camera.position();

        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(
                playerPosition.x - cameraPosition.x,
                playerPosition.y - cameraPosition.y + player.getEyeHeight() + HEAD_OFFSET,
                playerPosition.z - cameraPosition.z
        );

        int accent = parseColor(configManager.getConfig().getAccentColor());
        RenderType renderType = RenderTypes.debugTriangleFan();
        context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                renderType,
                (pose, vertexConsumer) -> buildHat(pose, vertexConsumer, accent)
        );
        poseStack.popPose();
    }

    private static void buildHat(PoseStack.Pose pose, VertexConsumer vc, int accent) {
        int centerColor = withAlpha(lighten(accent, 1.25F), FILL_ALPHA);
        int edgeColor = withAlpha(accent, FILL_ALPHA);
        vc.addVertex(pose, 0.0F, HEIGHT, 0.0F).setColor(centerColor);

        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = (float) (Math.PI * 2.0D * i / SEGMENTS);
            float x = -Mth.sin(angle) * RADIUS;
            float z = Mth.cos(angle) * RADIUS;
            vc.addVertex(pose, x, 0.0F, z).setColor(edgeColor);
        }
    }

    private static Vec3 interpolatedPosition(Entity entity, float tickDelta) {
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        return new Vec3(x, y, z);
    }

    private static int parseColor(String hex) {
        try {
            return ARGB.opaque(Integer.parseInt(hex.substring(1), 16));
        } catch (RuntimeException ignored) {
            return ARGB.color(255, 255, 124, 0);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return ARGB.color(Mth.clamp(alpha, 0, 255), ARGB.red(color), ARGB.green(color), ARGB.blue(color));
    }

    private static int lighten(int color, float multiplier) {
        return ARGB.color(
                ARGB.alpha(color),
                Mth.clamp(Math.round(ARGB.red(color) * multiplier), 0, 255),
                Mth.clamp(Math.round(ARGB.green(color) * multiplier), 0, 255),
                Mth.clamp(Math.round(ARGB.blue(color) * multiplier), 0, 255)
        );
    }
}
