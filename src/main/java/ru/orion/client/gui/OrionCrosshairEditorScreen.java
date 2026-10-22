package ru.orion.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Редактор прицела: сетка 15x15, клик/протяжка мышью рисует пиксели, справа
 * живое превью в натуральную величину. Стиль повторяет ClickGui (Java2D в текстуру).
 */
public final class OrionCrosshairEditorScreen extends Screen {
    private static final Identifier EDITOR_TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "crosshair_editor");
    private static final int MAX_TEXTURE_SCALE = 6;

    private static final int GRID = OrionConfig.CROSSHAIR_GRID;
    private static final int CELL = 16;
    private static final int GRID_X = 24;
    private static final int GRID_Y = 58;
    private static final int GRID_SIZE = GRID * CELL; // 240
    private static final int PANEL_X = GRID_X + GRID_SIZE + 16; // правая колонка: превью и кнопки
    private static final int PANEL_W = 96;
    private static final int W = PANEL_X + PANEL_W + 24; // 400
    private static final int H = GRID_Y + GRID_SIZE + 24; // 322
    private static final int BUTTON_H = 26;
    private static final float DISPLAY_SCALE = 1.35F;
    private static final int SCREEN_FIT_MARGIN = 12;

    private static final Color BG = new Color(0x1E, 0x1F, 0x24);
    private static final Color SURFACE = new Color(0x23, 0x26, 0x2D);
    private static final Color INSET = new Color(0x15, 0x16, 0x1B);
    private static final Color BORDER = new Color(0x3A, 0x40, 0x49);
    private static final Color TEXT = new Color(0xE5, 0xE7, 0xEB);
    private static final Color MUTED = new Color(0x9C, 0xA3, 0xAF);

    private final OrionConfigManager configManager;
    private final Screen parent;
    private final StringBuilder pixels;

    private DynamicTexture dynamicTexture;
    private int textureWidth;
    private int textureHeight;
    private List<Integer> lastFingerprint;
    private int hoveredCell = -1;
    private boolean painting;
    private char paintValue = '1';

    public OrionCrosshairEditorScreen(OrionConfigManager configManager, Screen parent) {
        super(Component.literal("Crosshair Editor"));
        this.configManager = configManager;
        this.parent = parent;

        String stored = configManager.getConfig().getCrosshairPixels();
        if (stored == null || stored.length() != GRID * GRID) {
            stored = OrionConfig.defaultCrosshairPixels();
        }
        this.pixels = new StringBuilder(stored);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        saveAndExit();
    }

    @Override
    public void removed() {
        Minecraft.getInstance().getTextureManager().release(EDITOR_TEXTURE);
        dynamicTexture = null;
        textureWidth = 0;
        textureHeight = 0;
        lastFingerprint = null;
        super.removed();
    }

    private void saveAndExit() {
        configManager.getConfig().setCrosshairPixels(pixels.toString());
        configManager.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0x66000000);
        minecraft.gui.hud.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);

        hoveredCell = cellAt(toLocalX(mouseX), toLocalY(mouseY));
        updateTextureIfNeeded();

        int x = guiX();
        int y = guiY();
        context.blit(EDITOR_TEXTURE, x, y, x + displayWidth(), y + displayHeight(), 0.0F, 1.0F, 0.0F, 1.0F);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }

        int localX = toLocalX(event.x());
        int localY = toLocalY(event.y());

        int cell = cellAt(localX, localY);
        if (cell >= 0) {
            paintValue = pixels.charAt(cell) == '1' ? '0' : '1';
            pixels.setCharAt(cell, paintValue);
            painting = true;
            lastFingerprint = null;
            return true;
        }

        if (isInside(localX, localY, PANEL_X, clearButtonY(), PANEL_W, BUTTON_H)) {
            for (int i = 0; i < pixels.length(); i++) {
                pixels.setCharAt(i, '0');
            }
            lastFingerprint = null;
            return true;
        }

        if (isInside(localX, localY, PANEL_X, resetButtonY(), PANEL_W, BUTTON_H)) {
            pixels.setLength(0);
            pixels.append(OrionConfig.defaultCrosshairPixels());
            lastFingerprint = null;
            return true;
        }

        if (isInside(localX, localY, PANEL_X, doneButtonY(), PANEL_W, BUTTON_H)) {
            saveAndExit();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(event, dragX, dragY);
        }
        if (painting) {
            int cell = cellAt(toLocalX(event.x()), toLocalY(event.y()));
            if (cell >= 0 && pixels.charAt(cell) != paintValue) {
                pixels.setCharAt(cell, paintValue);
                lastFingerprint = null;
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseReleased(event);
        }
        if (painting) {
            painting = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            saveAndExit();
            return true;
        }
        return super.keyPressed(event);
    }

    private int clearButtonY() {
        return GRID_Y + GRID_SIZE - BUTTON_H * 3 - 16;
    }

    private int resetButtonY() {
        return GRID_Y + GRID_SIZE - BUTTON_H * 2 - 8;
    }

    private int doneButtonY() {
        return GRID_Y + GRID_SIZE - BUTTON_H;
    }

    private int cellAt(int localX, int localY) {
        if (localX < GRID_X || localY < GRID_Y || localX >= GRID_X + GRID_SIZE || localY >= GRID_Y + GRID_SIZE) {
            return -1;
        }
        int cx = (localX - GRID_X) / CELL;
        int cy = (localY - GRID_Y) / CELL;
        return cy * GRID + cx;
    }

    private void updateTextureIfNeeded() {
        int scale = Math.max(1, Math.min(MAX_TEXTURE_SCALE, Minecraft.getInstance().getWindow().getGuiScale()));
        List<Integer> fingerprint = new ArrayList<>(4);
        fingerprint.add(scale);
        fingerprint.add(pixels.toString().hashCode());
        fingerprint.add(hoveredCell);
        fingerprint.add(configManager.getConfig().getCrosshairColor().hashCode());
        if (fingerprint.equals(lastFingerprint)) {
            return;
        }
        lastFingerprint = fingerprint;

        int imageWidth = W * scale;
        int imageHeight = H * scale;
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(scale, scale);

        draw(g);
        g.dispose();
        uploadTexture(image);
    }

    private void draw(Graphics2D g) {
        Color accent = parseColor(configManager.getConfig().getAccentColor());
        Color crosshairColor = parseColor(configManager.getConfig().getCrosshairColor());

        g.setColor(BG);
        g.fill(new RoundRectangle2D.Float(0, 0, W, H, 11, 11));
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(0.5F, 0.5F, W - 1, H - 1, 11, 11));

        drawText(g, "Crosshair Editor", 24, 26, titleFont(12.5F), accent);
        drawText(g, "Кликайте по клеткам, чтобы нарисовать прицел", 24, 40, bodyFont(7.0F), MUTED);

        // Сетка.
        g.setColor(INSET);
        g.fill(new Rectangle2D.Float(GRID_X, GRID_Y, GRID_SIZE, GRID_SIZE));
        for (int cy = 0; cy < GRID; cy++) {
            for (int cx = 0; cx < GRID; cx++) {
                int index = cy * GRID + cx;
                float x = GRID_X + cx * CELL;
                float y = GRID_Y + cy * CELL;
                if (pixels.charAt(index) == '1') {
                    g.setColor(crosshairColor);
                    g.fill(new Rectangle2D.Float(x + 1, y + 1, CELL - 2, CELL - 2));
                } else if (index == hoveredCell) {
                    g.setColor(new Color(255, 255, 255, 26));
                    g.fill(new Rectangle2D.Float(x + 1, y + 1, CELL - 2, CELL - 2));
                }
            }
        }

        // Линии сетки и оси центра.
        g.setColor(new Color(255, 255, 255, 14));
        g.setStroke(new BasicStroke(1.0F));
        for (int i = 1; i < GRID; i++) {
            g.draw(new Line2D.Float(GRID_X + i * CELL, GRID_Y, GRID_X + i * CELL, GRID_Y + GRID_SIZE));
            g.draw(new Line2D.Float(GRID_X, GRID_Y + i * CELL, GRID_X + GRID_SIZE, GRID_Y + i * CELL));
        }
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70));
        g.draw(new Line2D.Float(GRID_X + GRID / 2 * CELL, GRID_Y, GRID_X + GRID / 2 * CELL, GRID_Y + GRID_SIZE));
        g.draw(new Line2D.Float(GRID_X + (GRID / 2 + 1) * CELL, GRID_Y, GRID_X + (GRID / 2 + 1) * CELL, GRID_Y + GRID_SIZE));
        g.draw(new Line2D.Float(GRID_X, GRID_Y + GRID / 2 * CELL, GRID_X + GRID_SIZE, GRID_Y + GRID / 2 * CELL));
        g.draw(new Line2D.Float(GRID_X, GRID_Y + (GRID / 2 + 1) * CELL, GRID_X + GRID_SIZE, GRID_Y + (GRID / 2 + 1) * CELL));
        g.setColor(BORDER);
        g.draw(new Rectangle2D.Float(GRID_X - 0.5F, GRID_Y - 0.5F, GRID_SIZE + 1, GRID_SIZE + 1));

        // Превью в натуральную величину на тёмном фоне.
        drawText(g, "Превью", PANEL_X, GRID_Y + 10, bodyFont(7.5F), MUTED);
        float previewSize = 47;
        float previewX = PANEL_X + (PANEL_W - previewSize) / 2.0F;
        float previewY = GRID_Y + 16;
        g.setColor(INSET);
        g.fill(new RoundRectangle2D.Float(previewX, previewY, previewSize, previewSize, 6, 6));
        g.setColor(BORDER);
        g.draw(new RoundRectangle2D.Float(previewX + 0.5F, previewY + 0.5F, previewSize - 1, previewSize - 1, 6, 6));
        float px0 = previewX + (previewSize - GRID) / 2.0F;
        float py0 = previewY + (previewSize - GRID) / 2.0F;
        g.setColor(crosshairColor);
        for (int cy = 0; cy < GRID; cy++) {
            for (int cx = 0; cx < GRID; cx++) {
                if (pixels.charAt(cy * GRID + cx) == '1') {
                    g.fill(new Rectangle2D.Float(px0 + cx, py0 + cy, 1, 1));
                }
            }
        }

        drawButton(g, PANEL_X, clearButtonY(), "Очистить", SURFACE, TEXT);
        drawButton(g, PANEL_X, resetButtonY(), "Сбросить", SURFACE, TEXT);
        drawButton(g, PANEL_X, doneButtonY(), "Готово", accent, Color.BLACK);
    }

    private void drawButton(Graphics2D g, int x, int y, String label, Color background, Color textColor) {
        g.setColor(background);
        g.fill(new RoundRectangle2D.Float(x, y, PANEL_W, BUTTON_H, 6, 6));
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, PANEL_W - 1, BUTTON_H - 1, 6, 6));
        Font font = bodyFont(8.0F);
        float textWidth = (float) g.getFontMetrics(font).getStringBounds(label, g).getWidth();
        drawText(g, label, x + (PANEL_W - textWidth) / 2.0F, y + BUTTON_H / 2.0F + 3.0F, font, textColor);
    }

    private static void drawText(Graphics2D g, String text, float x, float baseline, Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, x, baseline);
    }

    private void uploadTexture(BufferedImage image) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        Minecraft client = Minecraft.getInstance();

        if (dynamicTexture == null || textureWidth != imageWidth || textureHeight != imageHeight) {
            client.getTextureManager().release(EDITOR_TEXTURE);
            NativeImage nativeImage = new NativeImage(imageWidth, imageHeight, true);
            copyPixels(image, nativeImage);
            dynamicTexture = new DynamicTexture(() -> "orion_crosshair_editor", nativeImage);
            client.getTextureManager().register(EDITOR_TEXTURE, dynamicTexture);
            textureWidth = imageWidth;
            textureHeight = imageHeight;
        } else {
            NativeImage nativePixels = dynamicTexture.getPixels();
            if (nativePixels == null || nativePixels.isClosed()) {
                lastFingerprint = null;
                return;
            }
            copyPixels(image, nativePixels);
            dynamicTexture.upload();
        }
    }

    private static void copyPixels(BufferedImage source, NativeImage target) {
        int w = source.getWidth();
        int h = source.getHeight();
        int[] argb = source.getRGB(0, 0, w, h, null, 0, w);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                target.setPixel(x, y, argb[row + x]);
            }
        }
    }

    private float displayFactor() {
        float factor = DISPLAY_SCALE;
        if (width > 0 && height > 0) {
            factor = Math.min(factor, (width - SCREEN_FIT_MARGIN) / (float) W);
            factor = Math.min(factor, (height - SCREEN_FIT_MARGIN) / (float) H);
        }
        return Math.max(0.4F, factor);
    }

    private int displayWidth() {
        return Math.max(1, Math.round(W * displayFactor()));
    }

    private int displayHeight() {
        return Math.max(1, Math.round(H * displayFactor()));
    }

    private int guiX() {
        return Math.max(0, (width - displayWidth()) / 2);
    }

    private int guiY() {
        return Math.max(0, (height - displayHeight()) / 2);
    }

    private int toLocalX(double screenX) {
        return (int) Math.floor((screenX - guiX()) * W / (double) displayWidth());
    }

    private int toLocalY(double screenY) {
        return (int) Math.floor((screenY - guiY()) * H / (double) displayHeight());
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static Font titleFont(float size) {
        Font font = pickFont(size, Font.PLAIN, "Inter SemiBold", "Inter Semi Bold", "Segoe UI Semibold");
        if (font != null) {
            return font;
        }
        font = pickFont(size, Font.BOLD, "Inter", "Segoe UI");
        return font != null ? font : new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size)).deriveFont(Font.BOLD, size);
    }

    private static Font bodyFont(float size) {
        Font font = pickFont(size, Font.PLAIN, "Inter", "Segoe UI");
        return font != null ? font : new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size)).deriveFont(Font.PLAIN, size);
    }

    private static Font pickFont(float size, int style, String... families) {
        for (String family : families) {
            Font font = new Font(family, style, Math.round(size));
            if (font.getFamily().equalsIgnoreCase(family) && font.canDisplay('Я') && font.canDisplay('ё')) {
                return font.deriveFont(style, size);
            }
        }
        return null;
    }

    private static Color parseColor(String hex) {
        try {
            return new Color(Integer.parseInt(hex.substring(1), 16));
        } catch (RuntimeException exception) {
            return new Color(0xF5, 0x9E, 0x0B);
        }
    }
}
