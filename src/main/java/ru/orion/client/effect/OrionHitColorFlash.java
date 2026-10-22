package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfigManager;

/**
 * HitColor: при ударе по сущности подсвечивается именно её хитбокс — рамка по рёбрам
 * плюс полупрозрачная заливка граней. Цвет и прозрачность заливки настраиваются;
 * вспышка затухает за FLASH_MS.
 */
public final class OrionHitColorFlash {
    private static final long FLASH_MS = 350L;
    private static final float LINE_WIDTH = 2.5F;
    private static final float EDGE_INFLATE = 0.004F; // рамка чуть шире заливки, чтобы не мерцали друг об друга

    private final OrionConfigManager configManager;
    private final Int2LongOpenHashMap hitTimes = new Int2LongOpenHashMap();

    public OrionHitColorFlash(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    /** Вызывается из MultiPlayerGameModeMixin при атаке. */
    public void recordHit(Entity target) {
        if (configManager.getConfig().isHitColorEnabled()) {
            hitTimes.put(target.getId(), System.currentTimeMillis());
        }
    }

    private void render(LevelRenderContext context) {
        if (hitTimes.isEmpty() || !configManager.getConfig().isHitColorEnabled()) {
            hitTimes.clear();
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            hitTimes.clear();
            return;
        }

        long now = System.currentTimeMillis();
        hitTimes.values().removeIf((long time) -> now - time > FLASH_MS);
        if (hitTimes.isEmpty()) {
            return;
        }

        Vec3 cam = client.gameRenderer.mainCamera().position();
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        int rgb = OrionRainbow.orElse(parseColor(configManager.getConfig().getHitColorColor()));
        float opacity = configManager.getConfig().getHitColorOpacity() / 100.0F;
        PoseStack poseStack = context.poseStack();

        for (Entity entity : client.level.entitiesForRendering()) {
            long hitAt = hitTimes.getOrDefault(entity.getId(), -1L);
            if (hitAt < 0) {
                continue;
            }

            float fade = 1.0F - (now - hitAt) / (float) FLASH_MS;
            int fillAlpha = Math.round(255.0F * opacity * fade);
            if (fillAlpha <= 2) {
                continue;
            }
            int fillColor = ARGB.color(fillAlpha, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
            int edgeColor = ARGB.color(Math.round(255.0F * fade), ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));

            Vec3 lerped = entity.getPosition(partialTick);
            AABB box = entity.getBoundingBox().move(lerped.subtract(entity.position()));

            poseStack.pushPose();
            poseStack.translate(box.minX - cam.x, box.minY - cam.y, box.minZ - cam.z);
            float sx = (float) (box.maxX - box.minX);
            float sy = (float) (box.maxY - box.minY);
            float sz = (float) (box.maxZ - box.minZ);
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    RenderTypes.debugQuads(),
                    (pose, vertexConsumer) -> buildFilledBox(pose, vertexConsumer, sx, sy, sz, fillColor)
            );
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    RenderTypes.lines(),
                    (pose, vertexConsumer) -> buildWireBox(pose, vertexConsumer, sx, sy, sz, edgeColor)
            );
            poseStack.popPose();
        }
    }

    /** 12 рёбер бокса (0,0,0)..(sx,sy,sz), чуть шире заливки. */
    private static void buildWireBox(PoseStack.Pose pose, VertexConsumer vc, float sx, float sy, float sz, int color) {
        float e = EDGE_INFLATE;
        // Нижняя грань.
        line(pose, vc, -e, -e, -e, sx + e, -e, -e, color);
        line(pose, vc, sx + e, -e, -e, sx + e, -e, sz + e, color);
        line(pose, vc, sx + e, -e, sz + e, -e, -e, sz + e, color);
        line(pose, vc, -e, -e, sz + e, -e, -e, -e, color);
        // Верхняя грань.
        line(pose, vc, -e, sy + e, -e, sx + e, sy + e, -e, color);
        line(pose, vc, sx + e, sy + e, -e, sx + e, sy + e, sz + e, color);
        line(pose, vc, sx + e, sy + e, sz + e, -e, sy + e, sz + e, color);
        line(pose, vc, -e, sy + e, sz + e, -e, sy + e, -e, color);
        // Вертикальные рёбра.
        line(pose, vc, -e, -e, -e, -e, sy + e, -e, color);
        line(pose, vc, sx + e, -e, -e, sx + e, sy + e, -e, color);
        line(pose, vc, sx + e, -e, sz + e, sx + e, sy + e, sz + e, color);
        line(pose, vc, -e, -e, sz + e, -e, sy + e, sz + e, color);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer vc,
                             float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4F) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        // Формат lines() в 26.x требует LineWidth в каждой вершине.
        vc.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(LINE_WIDTH);
        vc.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(LINE_WIDTH);
    }

    /** 6 граней бокса (0,0,0)..(sx,sy,sz). */
    private static void buildFilledBox(PoseStack.Pose pose, VertexConsumer vc, float sx, float sy, float sz, int color) {
        // Низ и верх.
        quad(pose, vc, color, 0, 0, 0, sx, 0, 0, sx, 0, sz, 0, 0, sz);
        quad(pose, vc, color, 0, sy, 0, 0, sy, sz, sx, sy, sz, sx, sy, 0);
        // Стороны по Z.
        quad(pose, vc, color, 0, 0, 0, 0, sy, 0, sx, sy, 0, sx, 0, 0);
        quad(pose, vc, color, 0, 0, sz, sx, 0, sz, sx, sy, sz, 0, sy, sz);
        // Стороны по X.
        quad(pose, vc, color, 0, 0, 0, 0, 0, sz, 0, sy, sz, 0, sy, 0);
        quad(pose, vc, color, sx, 0, 0, sx, sy, 0, sx, sy, sz, sx, 0, sz);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int color,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vc.addVertex(pose, x1, y1, z1).setColor(color);
        vc.addVertex(pose, x2, y2, z2).setColor(color);
        vc.addVertex(pose, x3, y3, z3).setColor(color);
        vc.addVertex(pose, x4, y4, z4).setColor(color);
    }

    private static int parseColor(String hex) {
        try {
            return ARGB.opaque(Integer.parseInt(hex.substring(1), 16));
        } catch (RuntimeException ignored) {
            return ARGB.color(255, 239, 68, 68);
        }
    }
}
