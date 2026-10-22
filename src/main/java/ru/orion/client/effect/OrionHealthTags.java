package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import ru.orion.client.config.OrionConfigManager;

/**
 * Vanilla-style pixel hearts above players and mobs, rendered the same way as the
 * HealthIndicators mod (https://github.com/AdyTech99/HealthIndicators): text render type
 * (crisp vanilla colors, depth-tested), 0.025 world-per-pixel name-tag scale, 8px heart step,
 * container under every slot with the fill pushed toward the camera by a distance-scaled
 * z offset, rows of ten hearts stacking upwards, absorption hearts appended in gold.
 */
public final class OrionHealthTags {
    private static final double MAX_DISTANCE = 64.0D;
    private static final double MAX_DISTANCE_SQ = MAX_DISTANCE * MAX_DISTANCE;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float SCALE = 0.025F;
    private static final float HEART_SIZE = 9.0F;
    private static final int HEART_STEP = 8;
    private static final int HEARTS_PER_ROW = 10;
    // Extra world-space lift when a name tag line sits under the hearts (players/named mobs).
    private static final float NAME_LINE_LIFT = 9.0F * 1.15F * SCALE;

    private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/container.png");
    private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/full.png");
    private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/half.png");
    private static final Identifier HEART_ABSORBING_FULL = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/absorbing_full.png");
    private static final Identifier HEART_ABSORBING_HALF = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/absorbing_half.png");

    private final OrionConfigManager configManager;

    public OrionHealthTags(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    private void render(LevelRenderContext context) {
        if (!configManager.getConfig().isHealthTagsEnabled()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        CameraRenderState cameraState = context.levelState().cameraRenderState;
        if (cameraState == null || !cameraState.initialized || cameraState.orientation == null) {
            return;
        }

        Vec3 cameraPos = cameraState.pos;
        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == client.player
                    || living instanceof ArmorStand || !living.isAlive()
                    || living.isInvisibleTo(client.player)) {
                continue;
            }

            double x = Mth.lerp(tickDelta, living.xOld, living.getX());
            double y = Mth.lerp(tickDelta, living.yOld, living.getY());
            double z = Mth.lerp(tickDelta, living.zOld, living.getZ());
            double distanceSq = cameraPos.distanceToSqr(x, y, z);
            if (distanceSq > MAX_DISTANCE_SQ) {
                continue;
            }

            submitHearts(context, living, x - cameraPos.x, y - cameraPos.y, z - cameraPos.z, cameraState, distanceSq);
        }
    }

    private void submitHearts(LevelRenderContext context, LivingEntity living,
                              double dx, double dy, double dz, CameraRenderState cameraState, double distanceSq) {
        int healthRed = Mth.ceil(living.getHealth());
        int maxHealth = Mth.ceil(living.getMaxHealth());
        int healthYellow = Mth.ceil(living.getAbsorptionAmount());

        int heartsRed = Mth.ceil(healthRed / 2.0F);
        boolean lastRedHalf = (healthRed & 1) == 1;
        int heartsNormal = Math.max(1, Mth.ceil(maxHealth / 2.0F));
        int heartsYellow = Mth.ceil(healthYellow / 2.0F);
        boolean lastYellowHalf = (healthYellow & 1) == 1;
        int heartsTotal = heartsNormal + heartsYellow;

        int pixelsTotal = Math.min(heartsTotal, HEARTS_PER_ROW) * HEART_STEP + 1;
        float maxX = pixelsTotal / 2.0F;
        double heartDensity = 50.0D - Math.max(4.0D - Math.ceil((double) heartsTotal / HEARTS_PER_ROW), -3.0D) * 5.0D;

        boolean hasNameLine = living instanceof Player || living.hasCustomName();
        float baseLift = living.getBbHeight() + 0.5F + (hasNameLine ? NAME_LINE_LIFT : 0.0F);

        // Same trick as HealthIndicators: the fill goes toward the camera and the container away
        // from it, with the gap growing with distance so depth precision never lets the dark
        // container swallow the red fill (containers and fills are separate RenderType batches
        // whose draw order is not the submit order).
        float zOffset = 0.01F * (1.0F + (float) Math.sqrt(distanceSq));

        PoseStack poseStack = context.poseStack();
        double rowLift = 0.0D;
        for (int heart = 0; heart < heartsTotal; heart++) {
            if (heart % HEARTS_PER_ROW == 0) {
                rowLift = heart / heartDensity;
            }

            Identifier fill;
            if (heart < heartsRed) {
                fill = (heart == heartsRed - 1 && lastRedHalf) ? HEART_HALF : HEART_FULL;
            } else if (heart < heartsNormal) {
                fill = null;
            } else {
                fill = (heart == heartsTotal - 1 && lastYellowHalf) ? HEART_ABSORBING_HALF : HEART_ABSORBING_FULL;
            }

            float x = maxX - (heart % HEARTS_PER_ROW) * HEART_STEP;

            poseStack.pushPose();
            poseStack.translate(dx, dy + baseLift + rowLift, dz);
            poseStack.rotate(cameraState.orientation);
            poseStack.scale(-SCALE, SCALE, SCALE);

            submitHeart(context, poseStack, HEART_CONTAINER, x, -zOffset);
            if (fill != null) {
                submitHeart(context, poseStack, fill, x, zOffset);
            }
            poseStack.popPose();
        }
    }

    private static void submitHeart(LevelRenderContext context, PoseStack poseStack, Identifier texture, float x, float z) {
        context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                RenderTypes.text(texture),
                (pose, vertexConsumer) -> drawHeart(pose.pose(), vertexConsumer, x, z)
        );
    }

    private static void drawHeart(Matrix4f model, VertexConsumer vc, float x, float z) {
        vc.addVertex(model, x, -HEART_SIZE, z).setUv(0.0F, 1.0F).setLight(FULL_BRIGHT).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        vc.addVertex(model, x - HEART_SIZE, -HEART_SIZE, z).setUv(1.0F, 1.0F).setLight(FULL_BRIGHT).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        vc.addVertex(model, x - HEART_SIZE, 0.0F, z).setUv(1.0F, 0.0F).setLight(FULL_BRIGHT).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        vc.addVertex(model, x, 0.0F, z).setUv(0.0F, 0.0F).setLight(FULL_BRIGHT).setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
