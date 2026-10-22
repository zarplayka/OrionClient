package ru.orion.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/** Меняет только фон sidebar: содержимое и шрифты остаются под управлением Minecraft. */
public final class OrionScoreboardGlass {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "scoreboard_glass");
    private static final int MAX_TEXTURE_SCALE = 6;
    private static final int PAD_X = 6;
    private static final int PAD_Y = 4;
    private static final float PANEL_ARC = 10.0F;

    private DynamicTexture texture;
    private int cachedWidth;
    private int cachedHeight;
    private int cachedScale;
    private boolean active;
    private boolean drawn;
    private int headerX0;
    private int headerY0;
    private int headerX1;
    private int headerY1;
    private int headerColor;

    public void beginFrame() {
        drawn = false;
    }

    public void endFrame() {
        if (!drawn) {
            release();
        }
    }

    public void beginSidebar(GuiGraphicsExtractor context) {
        OrionConfig config = OrionClient.getConfigOrNull();
        active = config != null && config.isScoreboardGlassEnabled();
        if (active) {
            // Отступ справа позволяет скруглить кромку, не накладывая её на числа.
            context.pose().pushMatrix();
            context.pose().translate(-PAD_X, 0);
        }
    }

    public void endSidebar(GuiGraphicsExtractor context) {
        if (active) {
            context.pose().popMatrix();
        }
        active = false;
    }

    public void header(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
        if (!active) {
            context.fill(x0, y0, x1, y1, color);
            return;
        }
        headerX0 = x0;
        headerY0 = y0;
        headerX1 = x1;
        headerY1 = y1;
        headerColor = color;
    }

    public void body(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
        if (!active) {
            context.fill(x0, y0, x1, y1, color);
            return;
        }
        int left = Math.max(PAD_X, Math.min(headerX0, x0) - PAD_X);
        int top = Math.max(0, Math.min(headerY0, y0) - PAD_Y);
        int right = Math.min(context.guiWidth() + PAD_X - 1, Math.max(headerX1, x1) + PAD_X);
        int bottom = Math.min(context.guiHeight(), Math.max(headerY1, y1) + PAD_Y);
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            context.fill(headerX0, headerY0, headerX1, headerY1, headerColor);
            context.fill(x0, y0, x1, y1, color);
            return;
        }
        int scale = Math.max(1, Math.min(MAX_TEXTURE_SCALE, Minecraft.getInstance().getWindow().getGuiScale()));
        updateTexture(width, height, scale);
        if (!OrionGlassCompositor.INSTANCE.submit(TEXTURE, context, texture, left, top, width, height)) {
            context.blit(TEXTURE, left, top, right, bottom, 0.0F, 1.0F, 0.0F, 1.0F);
        }
        drawn = true;
    }

    private void updateTexture(int width, int height, int scale) {
        if (texture != null && width == cachedWidth && height == cachedHeight && scale == cachedScale) {
            return;
        }
        int pixelWidth = width * scale;
        int pixelHeight = height * scale;
        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(scale, scale);
            OrionGlassStyle.drawPanel(g, 0, 0, width, height, PANEL_ARC);
        } finally {
            g.dispose();
        }
        Minecraft client = Minecraft.getInstance();
        if (texture == null || pixelWidth != cachedWidth * cachedScale || pixelHeight != cachedHeight * cachedScale) {
            client.getTextureManager().release(TEXTURE);
            NativeImage pixels = new NativeImage(pixelWidth, pixelHeight, true);
            OrionHudRenderer.copyPixels(image, pixels);
            texture = new DynamicTexture(() -> "orion_scoreboard_glass", pixels);
            client.getTextureManager().register(TEXTURE, texture);
        } else {
            OrionHudRenderer.copyPixels(image, texture.getPixels());
            texture.upload();
        }
        OrionGlassCompositor.INSTANCE.updateMask(TEXTURE, new OrionGlassCompositor.MaskLayout(
                pixelWidth, pixelHeight, width, height,
                List.of(new OrionGlassCompositor.Panel(0, 0, width, height, PANEL_ARC))));
        cachedWidth = width;
        cachedHeight = height;
        cachedScale = scale;
    }

    private void release() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            texture = null;
        }
        OrionGlassCompositor.INSTANCE.releaseMask(TEXTURE);
    }
}
