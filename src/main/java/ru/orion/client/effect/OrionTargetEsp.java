package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;

import java.util.UUID;

/**
 * Target ESP rendered like the reference from zar.jar:
 * a smoothed, camera-facing world quad with fixed world scale and animated rotation.
 */
public final class OrionTargetEsp {
    public static final String[] TEXTURES = {
            "rhomb", "ghost", "chain", "crystals", "marker"
    };

    private static final float TARGET_FPS = 60.0F;
    private static final float TARGET_FRAME_TIME = 1000.0F / TARGET_FPS;
    private static final float POSITION_SMOOTHING = 1.5F;
    private static final float APPEAR_SPEED = 0.18F;
    private static final float DISAPPEAR_SPEED = 0.18F;
    private static final float BASE_SCALE = 0.50F;
    private static final float GLOW_SCALE = 0.72F;
    private static final double FRONT_OFFSET_MULTIPLIER = 0.72D;
    private static final long TARGET_LINGER_MS = 4_000L;
    private static final long HIT_HIGHLIGHT_MS = 350L;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int WHITE = ARGB.color(255, 255, 255, 255);
    private static final float CHAIN_SCALE = 0.5F;
    private static final float CHAIN_BAND_HEIGHT = 1.5F;
    private static final float CHAIN_BOTTOM_Y = -0.5F;
    private static final int CHAIN_PARTS = 18;
    private static final int CHAIN_POINTS = 720;
    private static final float CHAIN_U_REPEATS = 8.0F;
    private static final float CHAIN_TOP_COLOR_MULTIPLIER = 1.15F;
    private static final int CHAIN_MAX_ALPHA = 128;
    private static final int MARKER_SEGMENTS = 64;
    private static final float MARKER_STEP_SPEED = 0.06F;
    private static final float MARKER_RADIUS_PULSE = 0.18F;
    private static final float MARKER_OUTER_RADIUS = 1.35F;
    private static final double GHOST_OUTER_RADIUS = 0.7D;
    private static final double MARKER_OUTLINE_WIDTH = 0.035D;
    private static final double MARKER_SIDE_GLOW_WIDTH = 0.055D;
    private static final int RED_HIT_COLOR = 0xFFFF0000;
    private static final CrystalTemplate[] CRYSTALS = {
            new CrystalTemplate(0.0D, 0.85D, 0.8D, -49.0F, 0.0F, 40.0F),
            new CrystalTemplate(0.2D, 0.85D, -0.675D, 35.0F, 0.0F, -30.0F),
            new CrystalTemplate(0.6D, 1.35D, 0.6D, -30.0F, 0.0F, 35.0F),
            new CrystalTemplate(-0.74D, 1.05D, 0.4D, -25.0F, 0.0F, -30.0F),
            new CrystalTemplate(0.74D, 0.95D, -0.4D, 0.0F, 0.0F, 0.0F),
            new CrystalTemplate(-0.475D, 0.85D, -0.375D, 30.0F, 0.0F, -25.0F),
            new CrystalTemplate(0.0D, 1.35D, -0.6D, 45.0F, 0.0F, 0.0F),
            new CrystalTemplate(0.85D, 0.7D, 0.1D, -30.0F, 0.0F, 30.0F),
            new CrystalTemplate(-0.7D, 1.35D, -0.3D, 0.0F, 0.0F, 0.0F),
            new CrystalTemplate(-0.3D, 1.35D, 0.55D, 0.0F, 0.0F, 0.0F),
            new CrystalTemplate(-0.5D, 0.7D, 0.7D, 0.0F, 0.0F, 0.0F),
            new CrystalTemplate(0.5D, 0.7D, 0.7D, 0.0F, 0.0F, 0.0F),
            new CrystalTemplate(-0.7D, 0.75D, 0.0D, 0.0F, 0.0F, 0.0F),
            new CrystalTemplate(-0.2D, 0.65D, -0.7D, 0.0F, 0.0F, 0.0F)
    };

    private final OrionConfigManager configManager;

    private Vec3 smoothedPosition;
    private LivingEntity lastTarget;
    private LivingEntity lingeringTarget;
    private long lingeringUntilMs;
    private float anim;
    private float hitAnimSmoothed;
    private float circleStep;
    private double markerSmoothY;
    private double markerSmoothY2;
    private UUID lastHitPlayerId;
    private long lastHitUntilMs;
    private float crystalRotation;
    private long lastFrameTime = System.currentTimeMillis();

    public OrionTargetEsp(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public static String textureName(int index) {
        return TEXTURES[Math.floorMod(index, TEXTURES.length)];
    }

    public void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    public void recordHit(Entity target) {
        if (target instanceof LivingEntity living) {
            lastHitPlayerId = living.getUUID();
            lastHitUntilMs = System.currentTimeMillis() + HIT_HIGHLIGHT_MS;
        }
    }

    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        float deltaTime = getDeltaTime();
        LivingEntity target = resolveTarget(client);
        if (!configManager.getConfig().isTargetEspEnabled()) {
            clearTargetState();
            return;
        }

        long now = System.currentTimeMillis();
        if (target != null) {
            lingeringTarget = target;
            lingeringUntilMs = now + TARGET_LINGER_MS;
        } else if (lingeringTarget != null && lingeringTarget.isAlive() && now <= lingeringUntilMs) {
            target = lingeringTarget;
        }

        // Геометрия эффектов проходит depth-тест (не видна сквозь модельку и блоки),
        // а рейкаст дополнительно плавно гасит эффект, когда цель скрыта блоками.
        boolean hasTarget = target != null && isVisibleThroughBlocks(client, target);

        updateAnim(hasTarget, deltaTime);
        if (!hasTarget) {
            if (anim <= 0.0F) {
                clearTargetState();
            }
            return;
        }

        if (anim <= 0.01F) {
            return;
        }

        Camera camera = client.gameRenderer.mainCamera();
        Vec3 targetPosition = interpolatedPosition(target, client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        if (lastTarget != target || smoothedPosition == null) {
            smoothedPosition = targetPosition;
            lastTarget = target;
        } else {
            float smoothing = Math.min(1.0F, client.getDeltaTracker().getGameTimeDeltaTicks() * POSITION_SMOOTHING);
            smoothedPosition = smoothedPosition.lerp(targetPosition, smoothing);
        }

        PoseStack poseStack = context.poseStack();
        Vec3 cameraPosition = camera.position();
        String selected = textureName(configManager.getConfig().getTargetEspTexture());
        float hitProgress = getHitProgress(target);
        hitAnimSmoothed = Mth.lerp(Math.min(1.0F, 0.35F * deltaTime), hitAnimSmoothed, hitProgress);
        int baseColor = OrionRainbow.orElse(OrionWorldChanger.parseOpaqueColor(configManager.getConfig().getTargetEspChainColor()));
        int color1 = lerpColor(baseColor, RED_HIT_COLOR, hitProgress);
        int color2 = lerpColor(lighten(baseColor, CHAIN_TOP_COLOR_MULTIPLIER), RED_HIT_COLOR, hitProgress);

        if ("chain".equals(selected)) {
            poseStack.pushPose();
            poseStack.translate(
                    smoothedPosition.x - cameraPosition.x,
                    smoothedPosition.y - cameraPosition.y,
                    smoothedPosition.z - cameraPosition.z
            );
            submitChain(context, poseStack, target, anim, hitAnimSmoothed, color1, color2);
            poseStack.popPose();
            return;
        }

        if ("rhomb".equals(selected)) {
            poseStack.pushPose();
            poseStack.translate(
                    smoothedPosition.x - cameraPosition.x,
                    smoothedPosition.y - cameraPosition.y,
                    smoothedPosition.z - cameraPosition.z
            );
            submitRhomb(context, poseStack, camera, target, anim, hitAnimSmoothed, color1, color2);
            poseStack.popPose();
            return;
        }

        if ("ghost".equals(selected)) {
            poseStack.pushPose();
            poseStack.translate(
                    smoothedPosition.x - cameraPosition.x,
                    smoothedPosition.y - cameraPosition.y + target.getBbHeight() * 0.5F,
                    smoothedPosition.z - cameraPosition.z
            );
            submitGhost(context, poseStack, camera, anim, hitAnimSmoothed, color1, color2, darken(color1, 0.8F));
            poseStack.popPose();
            return;
        }

        if ("crystals".equals(selected)) {
            poseStack.pushPose();
            poseStack.translate(
                    smoothedPosition.x - cameraPosition.x,
                    smoothedPosition.y - cameraPosition.y,
                    smoothedPosition.z - cameraPosition.z
            );
            submitCrystals(context, poseStack, camera, anim, hitAnimSmoothed, color1, deltaTime);
            poseStack.popPose();
            return;
        }

        if ("marker".equals(selected)) {
            poseStack.pushPose();
            poseStack.translate(
                    smoothedPosition.x - cameraPosition.x,
                    smoothedPosition.y - cameraPosition.y + target.getBbHeight() * 0.5F,
                    smoothedPosition.z - cameraPosition.z
            );
            submitMarkerCircle(
                    context,
                    poseStack,
                    target,
                    anim,
                    hitAnimSmoothed,
                    hitProgress,
                    baseColor,
                    deltaTime
            );
            poseStack.popPose();
            return;
        }

        Vec3 renderPosition = frontFacingPosition(smoothedPosition, cameraPosition, target);
        Identifier texture = texture(selected);
        float scale = scale(selected) * anim;
        float rotation = rotation(selected);

        poseStack.pushPose();
        poseStack.translate(
                renderPosition.x - cameraPosition.x,
                renderPosition.y - cameraPosition.y + target.getBbHeight() * 0.5F,
                renderPosition.z - cameraPosition.z
        );
        poseStack.rotate(camera.rotation());

        if (shouldSpin(selected)) {
            poseStack.rotate(Axis.ZP.rotation(rotation));
        }

        poseStack.scale(scale, scale, 1.0F);
        if (drawReferenceGlow(selected)) {
            poseStack.pushPose();
            poseStack.scale(GLOW_SCALE / BASE_SCALE, GLOW_SCALE / BASE_SCALE, 1.0F);
            submitQuad(context, poseStack, texture("glow"), ARGB.color(90, 255, 255, 255));
            poseStack.popPose();
        }
        submitQuad(context, poseStack, texture, WHITE);

        poseStack.popPose();
    }

    private static void submitChain(LevelRenderContext context, PoseStack poseStack, LivingEntity target, float alpha, float hitAnim, int bottomBaseColor, int topBaseColor) {
        long now = System.currentTimeMillis();
        float timeDegrees = (now % 360_000L) / 1000.0F * 60.0F;
        float wave = (float) Math.sin(Math.toRadians(timeDegrees));
        float zTilt = 20.0F * Math.min(1.0F + wave, 1.0F);
        float xTilt = 20.0F * (Math.min(1.0F + wave, 2.0F) - 1.0F);
        float spin = (now % 720_000L) / 1000.0F * 30.0F;
        float radius = target.getBbWidth() * 3.0F;
        float hitScale = 1.0F - MARKER_RADIUS_PULSE * hitAnim;
        int bottomColor = chainColor(bottomBaseColor, alpha);
        int topColor = chainColor(topBaseColor, alpha);

        for (int ring = 0; ring < 2; ring++) {
            float radiusMultiplier = 1.2F - 0.5F * (ring == 0 ? 1.0F : 0.9F);
            float ringZTilt = ring == 0 ? zTilt : -zTilt;
            float ringXTilt = ring == 0 ? xTilt : -xTilt;

            poseStack.pushPose();
            poseStack.translate(0.0F, target.getBbHeight() * 0.5F, 0.0F);
            poseStack.scale(CHAIN_SCALE * hitScale, CHAIN_SCALE * hitScale, CHAIN_SCALE * hitScale);
            poseStack.rotate(Axis.ZP.rotationDegrees(ringZTilt));
            poseStack.rotate(Axis.XP.rotationDegrees(ringXTilt));

            RenderType renderType = OrionEspRenderTypes.texturedDepthTested(texture("chain"));
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    renderType,
                    (pose, vertexConsumer) -> buildChainBand(
                            pose,
                            vertexConsumer,
                            radius,
                            radiusMultiplier,
                            spin,
                            ringZTilt / 100.0F,
                            -ringXTilt / 100.0F,
                            bottomColor,
                            topColor
                    )
            );
            poseStack.popPose();
        }
    }

    private static void submitRhomb(LevelRenderContext context, PoseStack poseStack, Camera camera, LivingEntity target, float alpha, float hitAnim, int color1, int color2) {
        poseStack.translate(0.0F, target.getBbHeight() * 0.5F, 0.0F);
        poseStack.rotate(camera.rotation());
        float time = (System.currentTimeMillis() % 6283L) / 1000.0F;
        poseStack.rotate(Axis.ZP.rotationDegrees((float) Math.sin(time) * 360.0F));
        float scale = 0.5F * (1.0F - MARKER_RADIUS_PULSE * hitAnim);
        poseStack.scale(scale, scale, 1.0F);

        int bottom = withAlpha(color2, Math.round(255.0F * alpha));
        int top = withAlpha(color1, Math.round(255.0F * alpha));
        RenderType renderType = OrionEspRenderTypes.textured(texture("rhomb"));
        context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                renderType,
                (pose, vertexConsumer) -> buildGradientQuad(pose, vertexConsumer, bottom, top)
        );
    }

    private static void submitGhost(LevelRenderContext context, PoseStack poseStack, Camera camera, float alpha, float hitAnim, int color1, int color2, int color3) {
        RenderType renderType = OrionEspRenderTypes.texturedDepthTested(texture("ghost"));
        submitGhostRibbon(context, poseStack, camera, renderType, alpha, hitAnim, color1, color2, 0);
        submitGhostRibbon(context, poseStack, camera, renderType, alpha, hitAnim, color2, color3, 1);
        submitGhostRibbon(context, poseStack, camera, renderType, alpha, hitAnim, color3, color1, 2);
    }

    private static void submitGhostRibbon(LevelRenderContext context, PoseStack parent, Camera camera, RenderType renderType, float alpha, float hitAnim, int fromColor, int toColor, int lane) {
        double radius = GHOST_OUTER_RADIUS * (1.0D - 0.25D * hitAnim);
        double speed = 11.0D * (1.0D - 0.25D * hitAnim);
        long now = System.currentTimeMillis();

        for (int index = 0; index < 40.0F * alpha; index++) {
            parent.pushPose();
            double angle = 0.15D * (now * 0.5D - index * speed) / 30.0D;
            double x = Math.sin(angle) * radius;
            double y = Math.cos(angle) * radius;
            Vec3 offset = ghostOffset(lane, x, y);
            parent.translate(offset.x, offset.y, offset.z);
            parent.rotate(camera.rotation());
            parent.rotate(Axis.ZP.rotationDegrees(now * 0.1F - index * 10.0F));
            parent.translate(0.25F, 0.25F, 0.0F);

            float progress = index / 40.0F;
            int color = lerpColor(fromColor, toColor, progress);
            int alphaValue = Math.round(Math.max(0.0F, 255.0F - index * 15.0F) * alpha);
            int particleColor = withAlpha(color, alphaValue);

            context.submitNodeCollector().submitCustomGeometry(
                    parent,
                    renderType,
                    (pose, vertexConsumer) -> buildGhostQuad(pose, vertexConsumer, particleColor)
            );
            parent.popPose();
        }
    }

    private static Vec3 ghostOffset(int lane, double x, double y) {
        return switch (lane) {
            case 0 -> new Vec3(x, y, -y);
            case 1 -> new Vec3(-x, x, -y);
            default -> new Vec3(-x, -x, y);
        };
    }

    private void submitCrystals(LevelRenderContext context, PoseStack poseStack, Camera camera, float alpha, float hitAnim, int color, float deltaTime) {
        crystalRotation = (crystalRotation + 0.5F * deltaTime) % 360.0F;
        poseStack.rotate(Axis.YP.rotationDegrees(crystalRotation));
        int crystalColor = withAlpha(color, Math.round(65.0F * alpha));
        int glowColor = withAlpha(color, Math.round(19.0F * alpha));
        int outerGlowColor = withAlpha(color, Math.round(8.0F * alpha));
        RenderType crystalRenderType = OrionEspRenderTypes.colorQuadsDepthTested();

        for (CrystalTemplate crystal : CRYSTALS) {
            poseStack.pushPose();
            crystal.applyTransform(poseStack, hitAnim);
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    crystalRenderType,
                    (pose, vertexConsumer) -> buildCrystal(pose, vertexConsumer, crystalColor)
            );
            poseStack.pushPose();
            poseStack.scale(1.15F, 1.15F, 1.15F);
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    crystalRenderType,
                    (pose, vertexConsumer) -> buildCrystal(pose, vertexConsumer, glowColor)
            );
            poseStack.popPose();
            poseStack.pushPose();
            poseStack.scale(1.3F, 1.3F, 1.3F);
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    crystalRenderType,
                    (pose, vertexConsumer) -> buildCrystal(pose, vertexConsumer, outerGlowColor)
            );
            poseStack.popPose();
            submitCrystalBloom(context, poseStack, camera, color, alpha);
            poseStack.popPose();
        }
    }

    private static void submitCrystalBloom(LevelRenderContext context, PoseStack poseStack, Camera camera, int color, float alpha) {
        RenderType renderType = OrionEspRenderTypes.texturedDepthTested(texture("crystal-glow"));
        int bloomColor = withAlpha(color, Math.round(18.0F * alpha));

        for (int i = 0; i < 6; i++) {
            poseStack.pushPose();
            poseStack.rotate(Axis.YP.rotationDegrees(360.0F / 6.0F * i));
            poseStack.rotate(camera.rotation());
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    renderType,
                    (pose, vertexConsumer) -> buildTexturedQuad(pose, vertexConsumer, 0.75F, bloomColor)
            );
            poseStack.popPose();
        }

        for (int i = 0; i < 6; i++) {
            poseStack.pushPose();
            poseStack.rotate(Axis.XP.rotationDegrees(90.0F));
            poseStack.rotate(Axis.YP.rotationDegrees(360.0F / 6.0F * i));
            poseStack.rotate(camera.rotation());
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    renderType,
                    (pose, vertexConsumer) -> buildTexturedQuad(pose, vertexConsumer, 0.75F, bloomColor)
            );
            poseStack.popPose();
        }
    }

    private static void buildGradientQuad(PoseStack.Pose pose, VertexConsumer vc, int bottomColor, int topColor) {
        addVertex(pose, vc, -1.0F, -1.0F, 0.0F, 0.0F, bottomColor);
        addVertex(pose, vc, -1.0F, 1.0F, 0.0F, 1.0F, topColor);
        addVertex(pose, vc, 1.0F, 1.0F, 1.0F, 1.0F, topColor);
        addVertex(pose, vc, 1.0F, -1.0F, 1.0F, 0.0F, bottomColor);
    }

    private static void buildGhostQuad(PoseStack.Pose pose, VertexConsumer vc, int color) {
        addVertex(pose, vc, 0.0F, -0.5F, 0.0F, 0.0F, color);
        addVertex(pose, vc, -0.5F, -0.5F, 0.0F, 1.0F, color);
        addVertex(pose, vc, -0.5F, 0.0F, 1.0F, 1.0F, color);
        addVertex(pose, vc, 0.0F, 0.0F, 1.0F, 0.0F, color);
    }

    private static void buildTexturedQuad(PoseStack.Pose pose, VertexConsumer vc, float size, int color) {
        float half = size * 0.5F;
        addVertex(pose, vc, -half, -half, 0.0F, 1.0F, color);
        addVertex(pose, vc, half, -half, 1.0F, 1.0F, color);
        addVertex(pose, vc, half, half, 1.0F, 0.0F, color);
        addVertex(pose, vc, -half, half, 0.0F, 0.0F, color);
    }

    private static void buildCrystal(PoseStack.Pose pose, VertexConsumer vc, int color) {
        int sides = 8;
        float radius = 0.05F;
        float halfHeight = 0.025F;
        float tipOffset = 0.075F;
        int sideColor = color;
        int darkColor = darken(color, 0.7F);
        int lightColor = lighten(color, 1.2F);

        for (int i = 0; i < sides; i++) {
            double angle0 = Math.PI * 2.0D * i / sides;
            double angle1 = Math.PI * 2.0D * (i + 1) / sides;
            Vec3 bottom0 = new Vec3(Math.cos(angle0) * radius, -halfHeight, Math.sin(angle0) * radius);
            Vec3 bottom1 = new Vec3(Math.cos(angle1) * radius, -halfHeight, Math.sin(angle1) * radius);
            Vec3 top0 = new Vec3(Math.cos(angle0) * radius, halfHeight, Math.sin(angle0) * radius);
            Vec3 top1 = new Vec3(Math.cos(angle1) * radius, halfHeight, Math.sin(angle1) * radius);
            Vec3 topTip = new Vec3(0.0D, halfHeight + tipOffset, 0.0D);
            Vec3 bottomTip = new Vec3(0.0D, -halfHeight - tipOffset, 0.0D);
            int quadColor = i % 2 == 0 ? sideColor : darkColor;
            int capColor = i % 2 == 0 ? lightColor : sideColor;

            addColorVertex(pose, vc, bottom0, quadColor);
            addColorVertex(pose, vc, bottom1, quadColor);
            addColorVertex(pose, vc, top1, quadColor);
            addColorVertex(pose, vc, top0, quadColor);

            addColorVertex(pose, vc, topTip, capColor);
            addColorVertex(pose, vc, top1, capColor);
            addColorVertex(pose, vc, top0, capColor);
            addColorVertex(pose, vc, top0, capColor);

            addColorVertex(pose, vc, bottomTip, darkColor);
            addColorVertex(pose, vc, bottom0, darkColor);
            addColorVertex(pose, vc, bottom1, darkColor);
            addColorVertex(pose, vc, bottom1, darkColor);
        }
    }

    private static void buildChainBand(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float radius,
            float radiusMultiplier,
            float spinDegrees,
            float xOffset,
            float zOffset,
            int bottomColor,
            int topColor
    ) {
        float topY = CHAIN_BOTTOM_Y + CHAIN_BAND_HEIGHT;
        int step = CHAIN_PARTS / 2;
        for (int point = 0; point < CHAIN_POINTS; point += step) {
            float previousPoint = point - step;
            float previousAngle = (float) Math.toRadians(previousPoint + spinDegrees);
            float currentAngle = (float) Math.toRadians(point + spinDegrees);

            float x0 = (float) Math.sin(previousAngle) * radius * radiusMultiplier + xOffset;
            float z0 = (float) Math.cos(previousAngle) * radius * radiusMultiplier + zOffset;
            float x1 = (float) Math.sin(currentAngle) * radius * radiusMultiplier + xOffset;
            float z1 = (float) Math.cos(currentAngle) * radius * radiusMultiplier + zOffset;
            float u0 = previousPoint / 360.0F * CHAIN_U_REPEATS;
            float u1 = point / 360.0F * CHAIN_U_REPEATS;

            addChainVertex(pose, vc, x0, CHAIN_BOTTOM_Y, z0, u0, 0.0F, bottomColor);
            addChainVertex(pose, vc, x1, CHAIN_BOTTOM_Y, z1, u1, 0.0F, bottomColor);
            addChainVertex(pose, vc, x1, topY, z1, u1, 0.99F, topColor);
            addChainVertex(pose, vc, x0, topY, z0, u0, 0.99F, topColor);
        }
    }

    private void submitMarkerCircle(
            LevelRenderContext context,
            PoseStack poseStack,
            LivingEntity target,
            float alpha,
            float hitAnimProgress,
            float hitRedProgress,
            int baseColor,
            float deltaTime
    ) {
        circleStep += MARKER_STEP_SPEED * deltaTime;

        float radiusScale = 1.0F - (float) Math.sin(Math.min(hitAnimProgress * 2.0F, 1.0F) * Math.PI) * MARKER_RADIUS_PULSE;
        float radius = Math.max(target.getBbWidth() * MARKER_OUTER_RADIUS, target.getBbWidth() + 0.24F) * radiusScale;
        float height = target.getBbHeight();

        markerSmoothY = lerp(markerSmoothY, smoothSinAnimation(circleStep) * height, 0.12D);
        markerSmoothY2 = lerp(markerSmoothY2, smoothSinAnimation(circleStep - 0.35D) * height, 0.10D);

        int color1 = lerpColor(baseColor, RED_HIT_COLOR, hitRedProgress);
        int color2 = lerpColor(lighten(baseColor, CHAIN_TOP_COLOR_MULTIPLIER), RED_HIT_COLOR, hitRedProgress);
        RenderType renderType = OrionEspRenderTypes.colorQuadsDepthTested();

        context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                renderType,
                (pose, vertexConsumer) -> buildMarkerCircle(pose, vertexConsumer, radius, height, alpha, color1, color2)
        );
    }

    private void buildMarkerCircle(PoseStack.Pose pose, VertexConsumer vc, float radius, float height, float alpha, int color1, int color2) {
        for (int i = 0; i < MARKER_SEGMENTS; i++) {
            float progress0 = i / (float) MARKER_SEGMENTS;
            float progress1 = ((i + 1) % MARKER_SEGMENTS) / (float) MARKER_SEGMENTS;
            float mix0 = 0.5F - 0.5F * (float) Math.cos(progress0 * Math.PI * 2.0D);
            float mix1 = 0.5F - 0.5F * (float) Math.cos(progress1 * Math.PI * 2.0D);

            int segmentColor0 = lerpColor(color1, color2, mix0);
            int segmentColor1 = lerpColor(color1, color2, mix1);
            int solid0 = multAlpha(segmentColor0, 0.80F * alpha);
            int solid1 = multAlpha(segmentColor1, 0.80F * alpha);
            int clear0 = multAlpha(segmentColor0, 0.0F);
            int clear1 = multAlpha(segmentColor1, 0.0F);
            int line0 = multAlpha(segmentColor0, alpha);
            int line1 = multAlpha(segmentColor1, alpha);
            int sideGlow0 = multAlpha(segmentColor0, 0.15F * alpha);
            int sideGlow1 = multAlpha(segmentColor1, 0.15F * alpha);

            double lowerY = markerSmoothY - height * 0.5D;
            double upperY = markerSmoothY2 - height * 0.5D;
            Vec3 lower0 = circlePoint(i, MARKER_SEGMENTS, radius, lowerY);
            Vec3 lower1 = circlePoint(i + 1, MARKER_SEGMENTS, radius, lowerY);
            Vec3 upper0 = circlePoint(i, MARKER_SEGMENTS, radius, upperY);
            Vec3 upper1 = circlePoint(i + 1, MARKER_SEGMENTS, radius, upperY);

            addColorVertex(pose, vc, lower0, solid0);
            addColorVertex(pose, vc, lower1, solid1);
            addColorVertex(pose, vc, upper1, clear1);
            addColorVertex(pose, vc, upper0, clear0);

            addColorVertex(pose, vc, upper0, clear0);
            addColorVertex(pose, vc, upper1, clear1);
            addColorVertex(pose, vc, lower1, solid1);
            addColorVertex(pose, vc, lower0, solid0);

            addMarkerVerticalGlow(pose, vc, lower0, upper0, sideGlow0, clear0);
            addMarkerOutline(pose, vc, i, radius, lowerY, line0, line1);
        }
    }

    private static void addMarkerVerticalGlow(PoseStack.Pose pose, VertexConsumer vc, Vec3 lower, Vec3 upper, int lowerColor, int upperColor) {
        Vec3 normal = new Vec3(lower.x, 0.0D, lower.z);
        if (normal.lengthSqr() < 1.0E-6D) {
            return;
        }

        Vec3 side = normal.normalize().scale(MARKER_SIDE_GLOW_WIDTH);
        addColorVertex(pose, vc, lower.subtract(side), lowerColor);
        addColorVertex(pose, vc, lower.add(side), lowerColor);
        addColorVertex(pose, vc, upper.add(side), upperColor);
        addColorVertex(pose, vc, upper.subtract(side), upperColor);
    }

    private static void addMarkerOutline(PoseStack.Pose pose, VertexConsumer vc, int index, float radius, double y, int color0, int color1) {
        Vec3 inner0 = circlePoint(index, MARKER_SEGMENTS, Math.max(0.01D, radius - MARKER_OUTLINE_WIDTH), y);
        Vec3 outer0 = circlePoint(index, MARKER_SEGMENTS, radius + MARKER_OUTLINE_WIDTH, y);
        Vec3 outer1 = circlePoint(index + 1, MARKER_SEGMENTS, radius + MARKER_OUTLINE_WIDTH, y);
        Vec3 inner1 = circlePoint(index + 1, MARKER_SEGMENTS, Math.max(0.01D, radius - MARKER_OUTLINE_WIDTH), y);

        addColorVertex(pose, vc, inner0, color0);
        addColorVertex(pose, vc, outer0, color0);
        addColorVertex(pose, vc, outer1, color1);
        addColorVertex(pose, vc, inner1, color1);
    }

    private static Vec3 frontFacingPosition(Vec3 basePosition, Vec3 cameraPosition, LivingEntity target) {
        Vec3 cameraDirection = new Vec3(
                cameraPosition.x - basePosition.x,
                0.0D,
                cameraPosition.z - basePosition.z
        );
        if (cameraDirection.lengthSqr() < 1.0E-6D) {
            return basePosition;
        }

        double frontOffset = Math.max(0.18D, target.getBbWidth() * FRONT_OFFSET_MULTIPLIER);
        return basePosition.add(cameraDirection.normalize().scale(frontOffset));
    }

    /** Пропускает цель, если от камеры до её глаз или центра есть прямая, не перекрытая блоками. */
    private static boolean isVisibleThroughBlocks(Minecraft client, LivingEntity target) {
        if (client.level == null || client.gameRenderer == null) {
            return true;
        }

        Vec3 from = client.gameRenderer.mainCamera().position();
        Vec3 eye = target.getEyePosition();
        Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        return isClearPath(client, from, eye, target) || isClearPath(client, from, center, target);
    }

    private static boolean isClearPath(Minecraft client, Vec3 from, Vec3 to, LivingEntity target) {
        return client.level.clip(new net.minecraft.world.level.ClipContext(
                from, to,
                net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                target
        )).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private LivingEntity resolveTarget(Minecraft client) {
        if (client.player == null || client.level == null) {
            return null;
        }

        Entity picked = client.crosshairPickEntity;
        if (!(picked instanceof LivingEntity living) || living == client.player || !living.isAlive()) {
            return null;
        }

        return living;
    }

    private void clearTargetState() {
        smoothedPosition = null;
        lastTarget = null;
        lingeringTarget = null;
        lingeringUntilMs = 0L;
        anim = 0.0F;
        hitAnimSmoothed = 0.0F;
        markerSmoothY = 0.0D;
        markerSmoothY2 = 0.0D;
        lastHitPlayerId = null;
        lastHitUntilMs = 0L;
    }

    private float getDeltaTime() {
        long now = System.currentTimeMillis();
        float deltaMs = Mth.clamp(now - lastFrameTime, 1.0F, 100.0F);
        lastFrameTime = now;
        return deltaMs / TARGET_FRAME_TIME;
    }

    private void updateAnim(boolean visible, float deltaTime) {
        float step = (visible ? APPEAR_SPEED : DISAPPEAR_SPEED) * deltaTime;
        anim = Mth.clamp(anim + (visible ? step : -step), 0.0F, 1.0F);
    }

    private float getHitProgress(LivingEntity target) {
        if (lastHitPlayerId == null || target == null) {
            return 0.0F;
        }

        long now = System.currentTimeMillis();
        if (now > lastHitUntilMs) {
            lastHitPlayerId = null;
            lastHitUntilMs = 0L;
            return 0.0F;
        }

        if (!lastHitPlayerId.equals(target.getUUID())) {
            return 0.0F;
        }

        return Mth.clamp((lastHitUntilMs - now) / (float) HIT_HIGHLIGHT_MS, 0.0F, 1.0F);
    }

    private static Vec3 interpolatedPosition(Entity entity, float tickDelta) {
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        return new Vec3(x, y, z);
    }

    private static float scale(String textureName) {
        return switch (textureName) {
            case "chain", "kol" -> 0.42F;
            case "ghost-glow", "ghost-triangle" -> 0.36F;
            case "skull" -> 0.34F;
            case "glow", "bloom" -> 0.56F;
            default -> BASE_SCALE;
        };
    }

    private static float rotation(String textureName) {
        long now = System.currentTimeMillis();
        double time = (now % 6283L) / 1000.0D;
        float multiplier = switch (textureName) {
            case "target1", "marker" -> 420.0F;
            case "chain", "kol" -> 260.0F;
            default -> 360.0F;
        };
        return (float) Math.sin(time) * (float) Math.toRadians(multiplier);
    }

    private static boolean shouldSpin(String textureName) {
        return !"skull".equals(textureName);
    }

    private static boolean drawReferenceGlow(String textureName) {
        return "target".equals(textureName) || "target1".equals(textureName) || "marker".equals(textureName);
    }

    private static void submitQuad(LevelRenderContext context, PoseStack poseStack, Identifier texture, int color) {
        RenderType renderType = RenderTypes.entityTranslucentEmissive(texture);
        context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                renderType,
                (pose, vertexConsumer) -> buildQuad(pose, vertexConsumer, color)
        );
    }

    private static void buildQuad(PoseStack.Pose pose, VertexConsumer vc, int color) {
        addVertex(pose, vc, -1.0F, -1.0F, 0.0F, 1.0F, color);
        addVertex(pose, vc, 1.0F, -1.0F, 1.0F, 1.0F, color);
        addVertex(pose, vc, 1.0F, 1.0F, 1.0F, 0.0F, color);
        addVertex(pose, vc, -1.0F, 1.0F, 0.0F, 0.0F, color);
    }

    private static void addVertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float u, float v, int color) {
        vc.addVertex(pose, x, y, 0.0F)
                .setUv(u, v)
                .setColor(color)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    private static void addChainVertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v, int color) {
        vc.addVertex(pose, x, y, z)
                .setUv(u, v)
                .setColor(color)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void addColorVertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 point, int color) {
        vc.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(color);
    }

    private static Vec3 circlePoint(int index, int segments, double radius, double y) {
        int clamped = Math.min(index, segments);
        double angle = clamped * Math.PI * 2.0D / segments;
        return new Vec3(Math.cos(angle) * radius, y, -Math.sin(angle) * radius);
    }

    private static int chainColor(int baseColor, float alphaFactor) {
        int alpha = Math.round(Mth.clamp(alphaFactor, 0.0F, 1.0F) * CHAIN_MAX_ALPHA);
        return ARGB.color(alpha, ARGB.red(baseColor), ARGB.green(baseColor), ARGB.blue(baseColor));
    }

    private static int withAlpha(int color, int alpha) {
        return ARGB.color(Mth.clamp(alpha, 0, 255), ARGB.red(color), ARGB.green(color), ARGB.blue(color));
    }

    private static int multAlpha(int color, float multiplier) {
        int alpha = Math.round(ARGB.alpha(color) * multiplier);
        return ARGB.color(Mth.clamp(alpha, 0, 255), ARGB.red(color), ARGB.green(color), ARGB.blue(color));
    }

    private static int lerpColor(int first, int second, float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        int alpha = Math.round(Mth.lerp(clamped, ARGB.alpha(first), ARGB.alpha(second)));
        int red = Math.round(Mth.lerp(clamped, ARGB.red(first), ARGB.red(second)));
        int green = Math.round(Mth.lerp(clamped, ARGB.green(first), ARGB.green(second)));
        int blue = Math.round(Mth.lerp(clamped, ARGB.blue(first), ARGB.blue(second)));
        return ARGB.color(alpha, red, green, blue);
    }

    private static double smoothSinAnimation(double value) {
        double t = (Math.sin(value) + 1.0D) / 2.0D;
        return -(Math.cos(Math.PI * t) - 1.0D) / 2.0D;
    }

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private static int lighten(int color, float multiplier) {
        return ARGB.color(
                ARGB.alpha(color),
                Mth.clamp(Math.round(ARGB.red(color) * multiplier), 0, 255),
                Mth.clamp(Math.round(ARGB.green(color) * multiplier), 0, 255),
                Mth.clamp(Math.round(ARGB.blue(color) * multiplier), 0, 255)
        );
    }

    private static int darken(int color, float multiplier) {
        return ARGB.color(
                ARGB.alpha(color),
                Mth.clamp(Math.round(ARGB.red(color) * multiplier), 0, 255),
                Mth.clamp(Math.round(ARGB.green(color) * multiplier), 0, 255),
                Mth.clamp(Math.round(ARGB.blue(color) * multiplier), 0, 255)
        );
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/targetesp/" + name + ".png");
    }

    private static final class CrystalTemplate {
        private final Vec3 position;
        private final float rotationX;
        private final float rotationY;
        private final float rotationZ;
        private final float rotationSpeed;

        private CrystalTemplate(double x, double y, double z, float rotationX, float rotationY, float rotationZ) {
            this.position = new Vec3(x, y, z);
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            this.rotationZ = rotationZ;
            this.rotationSpeed = 0.5F + Math.floorMod(Double.doubleToLongBits(x * 31.0D + y * 17.0D + z * 13.0D), 150L) / 100.0F;
        }

        private void applyTransform(PoseStack poseStack, float hitAnim) {
            double scale = 1.0D - 0.25D * hitAnim;
            poseStack.translate(position.x * scale, position.y * scale, position.z * scale);
            float timeRotation = (System.currentTimeMillis() % 36_000L) / 100.0F * rotationSpeed;
            float pulsation = 1.0F + (float) Math.sin(System.currentTimeMillis() % 31_416L / 1000.0D * 2.0D) * 0.1F;
            poseStack.scale(pulsation, pulsation, pulsation);
            poseStack.rotate(Axis.XP.rotationDegrees(rotationX));
            poseStack.rotate(Axis.YP.rotationDegrees(rotationY + timeRotation));
            poseStack.rotate(Axis.ZP.rotationDegrees(rotationZ));
        }
    }
}
