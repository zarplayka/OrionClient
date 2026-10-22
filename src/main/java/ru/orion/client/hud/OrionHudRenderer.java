package ru.orion.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Watermark HUD в стиле Liquid Glass: сверху оранжевая звезда Ориона, заголовок
 * «Orion Client» и версия, снизу отдельные стеклянные панели с ником и FPS.
 * Рисуется Java2D в текстуру с нативным разрешением, чтобы текст оставался чётким
 * на любом gui scale.
 */
public final class OrionHudRenderer {
    private static final Identifier HUD_TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "hud_watermark");
    private static final int MAX_TEXTURE_SCALE = 6;

    // Design units (1 unit = 1 gui px at hudScale 1).
    private static final float TOP_PILL_HEIGHT = 17.0F;
    private static final float BOTTOM_PILL_HEIGHT = 15.0F;
    private static final float ROW_GAP = 4.0F;
    private static final float PILL_PAD_X = 8.0F;
    private static final float PILL_GAP = 5.0F;
    private static final float STAR_RADIUS = 4.8F;

    private static final Color TEXT_COLOR = new Color(0xF7, 0xF9, 0xFF);
    private static final Color VERSION_COLOR = new Color(0xD4, 0xDA, 0xE8);
    private static final Color LABEL_COLOR = new Color(0xB6, 0xC0, 0xD2);

    private static final String NAME_TEXT = "Orion Client";
    private static final String VERSION_LABEL = "v0.1";

    private final OrionConfigManager configManager;

    private DynamicTexture dynamicTexture;
    private int textureWidth;
    private int textureHeight;
    private int guiWidth;
    private int guiHeight;
    private List<Object> lastFingerprint;

    public OrionHudRenderer(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        Identifier hudId = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "hud");
        HudElementRegistry.addLast(hudId, this::render);
    }

    private void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        OrionConfig config = configManager.getConfig();
        if (!config.isHudEnabled()) {
            releaseTextures();
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            releaseTextures();
            return;
        }

        String username = ru.orion.client.effect.OrionNameProtect.replace(client.getUser().getName());
        String fpsValue = Integer.toString(client.getFps());
        int scale = Math.max(1, Math.min(MAX_TEXTURE_SCALE, client.getWindow().getGuiScale()));
        float hudScale = config.getHudScale();

        List<Object> fingerprint = new ArrayList<>(6);
        fingerprint.add(scale);
        fingerprint.add(Math.round(hudScale * 100.0F));
        fingerprint.add(username);
        fingerprint.add(fpsValue);
        fingerprint.add(config.getAccentColor());
        fingerprint.add(ru.orion.client.effect.OrionRainbow.fingerprintStep());
        if (!fingerprint.equals(lastFingerprint)) {
            updateTexture(config, username, fpsValue, scale, hudScale);
            lastFingerprint = fingerprint;
        }

        if (dynamicTexture == null) {
            return;
        }

        int x = config.getHudX();
        int y = config.getHudY();
        if (!OrionGlassCompositor.INSTANCE.submit(HUD_TEXTURE, context, dynamicTexture, x, y, guiWidth, guiHeight)) {
            context.blit(HUD_TEXTURE, x, y, x + guiWidth, y + guiHeight, 0.0F, 1.0F, 0.0F, 1.0F);
        }
    }

    private void updateTexture(OrionConfig config, String username, String fpsValue, int scale, float hudScale) {
        // Measure the layout first with a throwaway context.
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        Font brandFont = titleFont(9.0F);
        Font versionFont = bodyFont(6.6F);
        Font statFont = titleFont(8.4F);
        Font labelFont = bodyFont(6.8F);

        float nameWidth = width(pg, brandFont, NAME_TEXT);
        float versionWidth = width(pg, versionFont, VERSION_LABEL);
        float usernameWidth = width(pg, statFont, username);
        float fpsValueWidth = width(pg, statFont, fpsValue);
        float fpsLabelWidth = width(pg, labelFont, "FPS");

        // Слот под число фиксируем по самой широкой цифре, минимум на 3 разряда:
        // иначе пилюля дёргается на каждом изменении FPS из-за разной ширины цифр.
        float digitWidth = 0.0F;
        for (char digit = '0'; digit <= '9'; digit++) {
            digitWidth = Math.max(digitWidth, width(pg, statFont, String.valueOf(digit)));
        }
        float fpsSlotWidth = digitWidth * Math.max(3, fpsValue.length());
        pg.dispose();

        // Верхняя пилюля: [звезда] Orion Client v0.1.
        float starCx = PILL_PAD_X + STAR_RADIUS;
        float nameX = starCx + STAR_RADIUS + 4.5F;
        float versionX = nameX + nameWidth + 4.5F;
        float topPillWidth = versionX + versionWidth + PILL_PAD_X;

        // Нижний ряд: пилюля с ником и пилюля с FPS.
        float userPillWidth = PILL_PAD_X + usernameWidth + PILL_PAD_X;
        // Запас справа: скругление пилюли съедает часть отступа, «FPS» не должен вылезать за край.
        float fpsPillWidth = PILL_PAD_X + fpsSlotWidth + 3.5F + fpsLabelWidth + PILL_PAD_X + 2.5F;

        // Шапка всегда не уже нижнего ряда — FPS-пилюля не должна торчать правее неё.
        float rowWidth = userPillWidth + PILL_GAP + fpsPillWidth;
        topPillWidth = Math.max(topPillWidth, rowWidth);
        float designWidth = topPillWidth;
        float designHeight = TOP_PILL_HEIGHT + ROW_GAP + BOTTOM_PILL_HEIGHT;

        guiWidth = Math.max(1, Math.round(designWidth * hudScale));
        guiHeight = Math.max(1, Math.round(designHeight * hudScale));
        int imageWidth = guiWidth * scale;
        int imageHeight = guiHeight * scale;

        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(imageWidth / designWidth, imageHeight / designHeight);

        Color accent = ru.orion.client.effect.OrionRainbow.orElse(parseColor(config.getAccentColor()));

        // Верхняя стеклянная пилюля.
        drawGlassPill(g, 0.0F, 0.0F, topPillWidth, TOP_PILL_HEIGHT);
        drawStar(g, starCx, TOP_PILL_HEIGHT / 2.0F, STAR_RADIUS, accent);

        float brandBaseline = centeredBaseline(g, brandFont, TOP_PILL_HEIGHT / 2.0F);
        drawText(g, brandFont, NAME_TEXT, nameX, brandBaseline, accent);
        drawText(g, versionFont, VERSION_LABEL, versionX, brandBaseline, VERSION_COLOR);

        // Нижний ряд: ник и FPS в отдельных пилюлях.
        float rowY = TOP_PILL_HEIGHT + ROW_GAP;
        float rowCenter = rowY + BOTTOM_PILL_HEIGHT / 2.0F;
        float statBaseline = centeredBaseline(g, statFont, rowCenter);

        drawGlassPill(g, 0.0F, rowY, userPillWidth, BOTTOM_PILL_HEIGHT);
        drawText(g, statFont, username, PILL_PAD_X, statBaseline, TEXT_COLOR);

        float fpsPillX = userPillWidth + PILL_GAP;
        drawGlassPill(g, fpsPillX, rowY, fpsPillWidth, BOTTOM_PILL_HEIGHT);
        // Число прижато к правому краю слота, чтобы подпись «FPS» не смещалась.
        drawText(g, statFont, fpsValue, fpsPillX + PILL_PAD_X + (fpsSlotWidth - fpsValueWidth),
                statBaseline, TEXT_COLOR);
        drawText(g, labelFont, "FPS", fpsPillX + PILL_PAD_X + fpsSlotWidth + 3.5F,
                statBaseline, LABEL_COLOR);

        g.dispose();
        uploadTexture(image);
        OrionGlassCompositor.INSTANCE.updateMask(HUD_TEXTURE, new OrionGlassCompositor.MaskLayout(
                imageWidth, imageHeight, designWidth, designHeight, List.of(
                new OrionGlassCompositor.Panel(0, 0, designWidth, TOP_PILL_HEIGHT, TOP_PILL_HEIGHT),
                new OrionGlassCompositor.Panel(0, rowY, userPillWidth, BOTTOM_PILL_HEIGHT, BOTTOM_PILL_HEIGHT),
                new OrionGlassCompositor.Panel(fpsPillX, rowY, fpsPillWidth, BOTTOM_PILL_HEIGHT, BOTTOM_PILL_HEIGHT))));
    }

    private static void drawGlassPill(Graphics2D g, float x, float y, float width, float height) {
        OrionGlassStyle.drawPanel(g, x, y, width, height, height);
    }

    private static void drawText(Graphics2D g, Font font, String text, float x, float baseline, Color color) {
        OrionGlassStyle.drawText(g, font, text, x, baseline, color);
    }

    /** Четырёхлучевая звезда Ориона с прозрачным ромбом в центре. */
    private static void drawStar(Graphics2D g, float cx, float cy, float r, Color accent) {
        float pinch = r * 0.30F;
        Path2D star = new Path2D.Float();
        star.moveTo(cx, cy - r);
        star.quadTo(cx + pinch, cy - pinch, cx + r, cy);
        star.quadTo(cx + pinch, cy + pinch, cx, cy + r);
        star.quadTo(cx - pinch, cy + pinch, cx - r, cy);
        star.quadTo(cx - pinch, cy - pinch, cx, cy - r);
        star.closePath();

        float core = r * 0.26F;
        Path2D hole = new Path2D.Float();
        hole.moveTo(cx, cy - core);
        hole.lineTo(cx + core, cy);
        hole.lineTo(cx, cy + core);
        hole.lineTo(cx - core, cy);
        hole.closePath();
        Area silhouette = new Area(star);
        silhouette.subtract(new Area(hole));
        g.setColor(accent);
        g.fill(silhouette);
    }

    /** Базовая линия, при которой текст данного шрифта визуально центрирован по centerY. */
    private static float centeredBaseline(Graphics2D g, Font font, float centerY) {
        FontMetrics metrics = g.getFontMetrics(font);
        return centerY + (metrics.getAscent() - metrics.getDescent()) / 2.0F;
    }

    private void uploadTexture(BufferedImage image) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        Minecraft client = Minecraft.getInstance();

        if (dynamicTexture == null || textureWidth != imageWidth || textureHeight != imageHeight) {
            client.getTextureManager().release(HUD_TEXTURE);
            NativeImage nativeImage = new NativeImage(imageWidth, imageHeight, true);
            copyPixels(image, nativeImage);
            dynamicTexture = new DynamicTexture(() -> "orion_hud_watermark", nativeImage);
            client.getTextureManager().register(HUD_TEXTURE, dynamicTexture);
            textureWidth = imageWidth;
            textureHeight = imageHeight;
        } else {
            NativeImage pixels = dynamicTexture.getPixels();
            if (pixels == null || pixels.isClosed()) {
                lastFingerprint = null;
                return;
            }
            copyPixels(image, pixels);
            dynamicTexture.upload();
        }
    }

    static void copyPixels(BufferedImage source, NativeImage target) {
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

    private void releaseTextures() {
        if (dynamicTexture != null) {
            Minecraft.getInstance().getTextureManager().release(HUD_TEXTURE);
            dynamicTexture = null;
            lastFingerprint = null;
        }
        OrionGlassCompositor.INSTANCE.releaseMask(HUD_TEXTURE);
    }

    private static float width(Graphics2D g, Font font, String text) {
        // Меряем на 16-кратном кегле: на мелком размере хинтинг занижает ширину,
        // а рисуется текст в увеличенной текстуре — из-за этого он вылезал за пилюлю.
        Font measuring = font.deriveFont(font.getSize2D() * 16.0F);
        FontMetrics metrics = g.getFontMetrics(measuring);
        return (float) metrics.getStringBounds(text, g).getWidth() / 16.0F;
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

    private static Color parseColor(String color) {
        try {
            int rgb = Integer.parseUnsignedInt(color.substring(1), 16);
            return new Color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255);
        } catch (RuntimeException exception) {
            return new Color(0xFF, 0x7C, 0x00);
        }
    }
}
