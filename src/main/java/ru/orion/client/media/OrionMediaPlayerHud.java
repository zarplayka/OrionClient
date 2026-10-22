package ru.orion.client.media;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.hud.OrionGlassCompositor;
import ru.orion.client.hud.OrionGlassStyle;

/**
 * "Now playing" widget pinned to a top corner, rendered off-screen with Java2D at native pixel
 * resolution (like the ClickGUI) so every rounded corner, ring and knob is perfectly smooth.
 * Collapsed it is a small capsule; hovering expands it Dynamic-Island-style. Layout follows the
 * reference mockup: cover, title/artist, progress with times, shuffle/prev/ring-play/next/repeat,
 * divider, volume section and a menu chip. Interactive while the chat screen is open.
 */
public final class OrionMediaPlayerHud {
    // Panel size in gui units; PAD leaves room for the soft drop shadow inside the texture.
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 66;
    private static final int PAD = 5;
    private static final int IMG_WIDTH = PANEL_WIDTH + PAD * 2;
    private static final int IMG_HEIGHT = PANEL_HEIGHT + PAD * 2;
    private static final int CAPSULE_WIDTH = 46;
    private static final int CAPSULE_HEIGHT = 16;
    private static final int MARGIN = 6;
    private static final int HOVER_PADDING = 4;
    private static final int EXPAND_ANIMATION_MS = 220;
    private static final int MAX_TEXTURE_SCALE = 6;

    private static final Color ART_BG = new Color(255, 255, 255, 22);
    private static final Color TITLE_COLOR = new Color(0xF2, 0xF2, 0xF5);
    private static final Color ARTIST_COLOR = new Color(0xD4, 0xDA, 0xE8);
    private static final Color TIME_COLOR = new Color(0xB9, 0xB9, 0xC0);
    private static final Color TIME_MUTED_COLOR = new Color(0xBD, 0xC6, 0xD3);
    private static final Color TRACK_COLOR = new Color(255, 255, 255, 38);
    private static final Color ICON_COLOR = new Color(0xF0, 0xEE, 0xF4);
    private static final Color ICON_MUTED_COLOR = new Color(0xBE, 0xC8, 0xD5);
    private static final Color DIVIDER_COLOR = new Color(255, 255, 255, 32);
    private static final Color CHIP_BG = new Color(255, 255, 255, 24);
    private static final Color PLAY_INNER_BG = new Color(255, 255, 255, 18);
    private static final Color CAPSULE_BAR_COLOR = new Color(255, 255, 255, 205);

    // Content layout (panel-local gui units), proportions transferred from the 1217x411 mockup.
    private static final int ART_X = 13;
    private static final int ART_Y = 14;
    private static final int ART_SIZE = 38;
    private static final int TEXT_X = 56;
    private static final float TITLE_BASELINE = 19.0F;
    private static final float ARTIST_BASELINE = 28.0F;
    private static final int BAR_LEFT = 56;
    private static final int BAR_RIGHT = 145;
    private static final float BAR_CY = 33.5F;
    private static final float TIMES_BASELINE = 41.0F;
    private static final float BUTTONS_CY = 48.5F;
    private static final float PLAY_CX = 100.5F;
    private static final float PLAY_RADIUS = 8.8F;
    private static final float PREV_CX = 79.0F;
    private static final float NEXT_CX = 122.0F;
    private static final float SHUFFLE_CX = 60.5F;
    private static final float REPEAT_CX = 140.5F;
    private static final float DIVIDER_X = 151.5F;
    private static final float VOLUME_ICON_CX = 159.5F;
    private static final float VOLUME_CY = 47.5F;
    private static final float VOLUME_LEFT = 167.0F;
    private static final float VOLUME_RIGHT = 189.0F;
    private static final float MENU_X = 178.5F;
    private static final float MENU_Y = 12.0F;
    private static final float MENU_SIZE = 9.5F;

    private static final Identifier HUD_TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "media_player_hud");

    private final OrionConfigManager configManager;
    private final OrionMediaController controller;

    private int appliedCoverGeneration = -1;
    private BufferedImage coverImage;

    private float expandAnim;
    private long lastFrameNanos;
    private float eased;
    private int panelGuiX;
    private int panelGuiY;
    private int panelGuiWidth;
    private int panelGuiHeight;

    private DynamicTexture dynamicTexture;
    private int textureWidth;
    private int textureHeight;
    private List<Object> lastFingerprint;

    private boolean wasMouseDown;
    private boolean pressedInside;
    private boolean pressedOnBar;
    private boolean pressedOnVolume;
    private double pressX;
    private double pressY;

    public OrionMediaPlayerHud(OrionConfigManager configManager, OrionMediaController controller) {
        this.configManager = configManager;
        this.controller = controller;
    }

    public void register() {
        Identifier id = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "media_player");
        HudElementRegistry.addLast(id, this::render);
    }

    // ---- Frame ----

    private void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        OrionConfig config = configManager.getConfig();
        Minecraft client = Minecraft.getInstance();
        if (!config.isMediaPlayerEnabled() || client == null || client.font == null) {
            lastFrameNanos = 0L;
            releaseTextures();
            return;
        }

        Window window = client.getWindow();
        boolean cursorFree = client.gui.screen() != null && !client.mouseHandler.isMouseGrabbed();
        double mouseX = cursorFree ? client.mouseHandler.getScaledXPos(window) : -1;
        double mouseY = cursorFree ? client.mouseHandler.getScaledYPos(window) : -1;

        updateGeometry(config, window, mouseX, mouseY);
        handleInput(client, config, mouseX, mouseY);
        refreshCover();
        updateTextureIfNeeded(config, window, mouseX, mouseY);

        boolean topRight = isTopRight(config);
        int blitX = topRight ? window.getGuiScaledWidth() - MARGIN - PANEL_WIDTH - PAD : MARGIN - PAD;
        int blitY = MARGIN - PAD;
        if (dynamicTexture != null && !OrionGlassCompositor.INSTANCE.submit(
                HUD_TEXTURE, context, dynamicTexture, blitX, blitY, IMG_WIDTH, IMG_HEIGHT)) {
            context.blit(HUD_TEXTURE, blitX, blitY, blitX + IMG_WIDTH, blitY + IMG_HEIGHT, 0.0F, 1.0F, 0.0F, 1.0F);
        }
    }

    private void updateGeometry(OrionConfig config, Window window, double mouseX, double mouseY) {
        boolean hovered = mouseX >= panelGuiX - HOVER_PADDING && mouseX <= panelGuiX + panelGuiWidth + HOVER_PADDING
                && mouseY >= panelGuiY - HOVER_PADDING && mouseY <= panelGuiY + panelGuiHeight + HOVER_PADDING;

        if (!config.isMediaPlayerAnimated()) {
            expandAnim = 1.0F;
        } else {
            long now = System.nanoTime();
            float dtMs = lastFrameNanos == 0L ? 0.0F : (now - lastFrameNanos) / 1_000_000.0F;
            lastFrameNanos = now;
            float step = Math.min(1.0F, dtMs / EXPAND_ANIMATION_MS);
            float target = hovered ? 1.0F : 0.0F;
            expandAnim = expandAnim < target
                    ? Math.min(target, expandAnim + step)
                    : Math.max(target, expandAnim - step);
        }

        eased = easeInOutCubic(expandAnim);
        panelGuiWidth = Math.round(lerp(CAPSULE_WIDTH, PANEL_WIDTH, eased));
        panelGuiHeight = Math.round(lerp(CAPSULE_HEIGHT, PANEL_HEIGHT, eased));
        panelGuiX = isTopRight(config) ? window.getGuiScaledWidth() - MARGIN - panelGuiWidth : MARGIN;
        panelGuiY = MARGIN;
    }

    private static boolean isTopRight(OrionConfig config) {
        return "TOP_RIGHT".equals(config.getMediaPlayerCorner());
    }

    // ---- Texture composition ----

    private void updateTextureIfNeeded(OrionConfig config, Window window, double mouseX, double mouseY) {
        int scale = Math.max(1, Math.min(MAX_TEXTURE_SCALE, window.getGuiScale()));
        int hoveredButton = hoveredButton(config, mouseX, mouseY);

        List<Object> fingerprint = new ArrayList<>(16);
        fingerprint.add(scale);
        fingerprint.add(Math.round(eased * 160.0F));
        fingerprint.add(isTopRight(config));
        fingerprint.add(controller.isActive());
        fingerprint.add(controller.isPlaying());
        fingerprint.add(controller.getTitle());
        fingerprint.add(controller.getArtist());
        fingerprint.add((int) Math.floor(controller.getCurrentSeconds()));
        fingerprint.add(barFillPixels(scale));
        fingerprint.add(appliedCoverGeneration);
        fingerprint.add(hoveredButton);
        fingerprint.add(config.getAccentColor());
        fingerprint.add(capsulePhase());
        fingerprint.add(controller.isShuffleActive());
        fingerprint.add(controller.getRepeatMode());
        fingerprint.add(Math.round(controller.getVolume() * 200.0F));
        if (fingerprint.equals(lastFingerprint)) {
            return;
        }

        BufferedImage image = compose(config, scale, hoveredButton);
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        Minecraft client = Minecraft.getInstance();
        if (dynamicTexture == null || textureWidth != imageWidth || textureHeight != imageHeight) {
            client.getTextureManager().release(HUD_TEXTURE);
            NativeImage nativeImage = new NativeImage(imageWidth, imageHeight, true);
            copyPixels(image, nativeImage);
            dynamicTexture = new DynamicTexture(() -> "orion_media_hud", nativeImage);
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
        lastFingerprint = fingerprint;
    }

    /** Animation phase for the capsule equalizer, quantized so the texture refreshes ~12 times/s. */
    private int capsulePhase() {
        if (eased > 0.35F || !controller.isActive() || !controller.isPlaying()) {
            return -1;
        }
        return (int) ((System.currentTimeMillis() / 85L) % 1024L);
    }

    private int barFillPixels(int scale) {
        double duration = controller.getDurationSeconds();
        if (duration <= 0) {
            return -1;
        }
        double fraction = Math.min(1.0, controller.getCurrentSeconds() / duration);
        return (int) Math.round((BAR_RIGHT - BAR_LEFT) * scale * fraction);
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

    private BufferedImage compose(OrionConfig config, int scale, int hoveredButton) {
        BufferedImage image = new BufferedImage(IMG_WIDTH * scale, IMG_HEIGHT * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.scale(scale, scale);

        float w = lerp(CAPSULE_WIDTH, PANEL_WIDTH, eased);
        float h = lerp(CAPSULE_HEIGHT, PANEL_HEIGHT, eased);
        float px = isTopRight(config) ? PAD + PANEL_WIDTH - w : PAD;
        float py = PAD;
        float radius = lerp(CAPSULE_HEIGHT / 2.0F, 8.0F, eased);

        OrionGlassStyle.drawPanel(g, px, py, w, h, radius * 2.0F);
        OrionGlassCompositor.INSTANCE.updateMask(HUD_TEXTURE, new OrionGlassCompositor.MaskLayout(
                IMG_WIDTH * scale, IMG_HEIGHT * scale, IMG_WIDTH, IMG_HEIGHT,
                List.of(new OrionGlassCompositor.Panel(px, py, w, h, radius * 2.0F))));

        Color accent = parseColor(config.getAccentColor());
        float capsuleAlpha = clamp01((0.35F - eased) / 0.35F);
        float contentAlpha = clamp01((eased - 0.6F) / 0.4F);

        if (capsuleAlpha > 0.03F) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, capsuleAlpha));
            drawCapsuleContent(g, px, py, h, accent);
        }

        if (contentAlpha > 0.03F) {
            Graphics2D content = (Graphics2D) g.create();
            content.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, contentAlpha));
            content.clip(new RoundRectangle2D.Float(px, py, w, h, radius * 2, radius * 2));
            float cx = isTopRight(config) ? px + w - PANEL_WIDTH : px;
            content.translate(cx, py);
            drawExpandedContent(content, accent, hoveredButton);
            content.dispose();
        }

        g.dispose();
        return image;
    }

    private void drawCapsuleContent(Graphics2D g, float px, float py, float h, Color accent) {
        float cy = py + h / 2.0F;
        boolean playingNow = controller.isActive() && controller.isPlaying();

        g.setColor(controller.isActive() ? accent : ICON_MUTED_COLOR);
        g.fill(new Ellipse2D.Float(px + 8, cy - 2.2F, 4.4F, 4.4F));

        long now = System.currentTimeMillis();
        g.setColor(CAPSULE_BAR_COLOR);
        for (int i = 0; i < 4; i++) {
            float height = playingNow
                    ? 2.4F + (float) ((Math.sin(now / 130.0D + i * 1.9D) + 1.0D) * 2.6D)
                    : 2.4F;
            float barX = px + 17 + i * 5;
            g.fill(new RoundRectangle2D.Float(barX, cy - height / 2.0F, 2.2F, height, 2.2F, 2.2F));
        }
    }

    /** The full mockup layout, drawn in panel-local coordinates (0,0 .. 200,66). */
    private void drawExpandedContent(Graphics2D g, Color accent, int hoveredButton) {
        // Cover.
        RoundRectangle2D artShape = new RoundRectangle2D.Float(ART_X, ART_Y, ART_SIZE, ART_SIZE, 13, 13);
        g.setColor(ART_BG);
        g.fill(artShape);
        if (coverImage != null && controller.isActive()) {
            Graphics2D art = (Graphics2D) g.create();
            art.clip(artShape);
            art.drawImage(coverImage, ART_X, ART_Y, ART_SIZE, ART_SIZE, null);
            art.dispose();
        } else {
            drawNote(g, ART_X, ART_Y, ART_SIZE, accent);
        }
        g.setColor(new Color(255, 255, 255, 20));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(artShape);

        // Title + artist.
        String title = controller.isActive() ? controller.getTitle() : "Not playing";
        if (title.isBlank()) {
            title = controller.isPlaying() ? "Playing" : "Media session";
        }
        String artist = controller.isActive() ? controller.getArtist() : "";
        int textMaxWidth = (int) (MENU_X - TEXT_X - 6);
        g.setFont(titleFont());
        OrionGlassStyle.drawText(g, g.getFont(), fit(g, title, textMaxWidth), TEXT_X, TITLE_BASELINE, TITLE_COLOR);
        if (!artist.isEmpty()) {
            g.setFont(bodyFont(6.6F));
            OrionGlassStyle.drawText(g, g.getFont(), fit(g, artist, textMaxWidth), TEXT_X, ARTIST_BASELINE, ARTIST_COLOR);
        }

        drawProgress(g, accent);
        drawTransport(g, accent, hoveredButton);
        drawRightSection(g, accent, hoveredButton);
    }

    private void drawProgress(Graphics2D g, Color accent) {
        double duration = controller.getDurationSeconds();
        double current = controller.getCurrentSeconds();
        float barWidth = BAR_RIGHT - BAR_LEFT;

        g.setColor(TRACK_COLOR);
        g.fill(new RoundRectangle2D.Float(BAR_LEFT, BAR_CY - 1.3F, barWidth, 2.6F, 2.6F, 2.6F));

        double fraction = duration > 0 ? Math.min(1.0, current / duration) : 0.0;
        float fill = (float) (barWidth * fraction);
        if (fill > 0.5F) {
            g.setPaint(new GradientPaint(BAR_LEFT, 0, lighten(accent, 25), BAR_LEFT + barWidth, 0, accent));
            g.fill(new RoundRectangle2D.Float(BAR_LEFT, BAR_CY - 1.3F, fill, 2.6F, 2.6F, 2.6F));
            g.setColor(accent);
            g.fill(new Ellipse2D.Float(BAR_LEFT + fill - 2.9F, BAR_CY - 2.9F, 5.8F, 5.8F));
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Float(BAR_LEFT + fill - 1.1F, BAR_CY - 1.1F, 2.2F, 2.2F));
        }

        g.setFont(bodyFont(6.0F));
        OrionGlassStyle.drawText(g, g.getFont(), formatTime(current), BAR_LEFT, TIMES_BASELINE, TIME_COLOR);
        String total = formatTime(Math.max(0.0, duration));
        float totalWidth = (float) g.getFontMetrics().getStringBounds(total, g).getWidth();
        OrionGlassStyle.drawText(g, g.getFont(), total, BAR_RIGHT - totalWidth, TIMES_BASELINE, TIME_MUTED_COLOR);
    }

    private void drawTransport(Graphics2D g, Color accent, int hoveredButton) {
        g.setStroke(new BasicStroke(1.4F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Color shuffleColor = controller.isShuffleActive() ? accent : ICON_MUTED_COLOR;
        if (hoveredButton == 3) {
            shuffleColor = lighten(shuffleColor, 45);
        }
        drawShuffle(g, SHUFFLE_CX, BUTTONS_CY, shuffleColor);

        int repeat = controller.getRepeatMode();
        Color repeatColor = repeat != 0 ? accent : ICON_MUTED_COLOR;
        if (hoveredButton == 4) {
            repeatColor = lighten(repeatColor, 45);
        }
        drawRepeat(g, REPEAT_CX, BUTTONS_CY, repeatColor);
        if (repeat == 2) {
            // "Repeat one" badge.
            g.setColor(repeatColor);
            g.fill(new Ellipse2D.Float(REPEAT_CX - 1.1F, BUTTONS_CY - 1.1F, 2.2F, 2.2F));
        }

        drawSkip(g, PREV_CX, BUTTONS_CY, true, hoveredButton == 0 ? Color.WHITE : ICON_COLOR);
        drawSkip(g, NEXT_CX, BUTTONS_CY, false, hoveredButton == 2 ? Color.WHITE : ICON_COLOR);

        // Ring play/pause.
        g.setColor(PLAY_INNER_BG);
        g.fill(new Ellipse2D.Float(PLAY_CX - PLAY_RADIUS, BUTTONS_CY - PLAY_RADIUS, PLAY_RADIUS * 2, PLAY_RADIUS * 2));
        g.setColor(hoveredButton == 1 ? lighten(accent, 40) : accent);
        g.setStroke(new BasicStroke(1.4F));
        g.draw(new Ellipse2D.Float(PLAY_CX - PLAY_RADIUS, BUTTONS_CY - PLAY_RADIUS, PLAY_RADIUS * 2, PLAY_RADIUS * 2));

        g.setColor(ICON_COLOR);
        if (controller.isPlaying()) {
            g.fill(new RoundRectangle2D.Float(PLAY_CX - 3.1F, BUTTONS_CY - 3.5F, 2.2F, 7.0F, 1.5F, 1.5F));
            g.fill(new RoundRectangle2D.Float(PLAY_CX + 0.9F, BUTTONS_CY - 3.5F, 2.2F, 7.0F, 1.5F, 1.5F));
        } else {
            Path2D play = new Path2D.Float();
            play.moveTo(PLAY_CX - 2.4F, BUTTONS_CY - 3.7F);
            play.lineTo(PLAY_CX + 3.9F, BUTTONS_CY);
            play.lineTo(PLAY_CX - 2.4F, BUTTONS_CY + 3.7F);
            play.closePath();
            g.fill(play);
        }
    }

    private static void drawSkip(Graphics2D g, float cx, float cy, boolean left, Color color) {
        g.setColor(color);
        float d = left ? -1.0F : 1.0F;
        Path2D triangle = new Path2D.Float();
        triangle.moveTo(cx - d * 3.6F, cy - 4.0F);
        triangle.lineTo(cx + d * 2.6F, cy);
        triangle.lineTo(cx - d * 3.6F, cy + 4.0F);
        triangle.closePath();
        g.fill(triangle);
        g.fill(new RoundRectangle2D.Float(cx + d * 3.0F - 0.9F, cy - 4.0F, 1.8F, 8.0F, 1.8F, 1.8F));
    }

    private static void drawShuffle(Graphics2D g, float cx, float cy, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1.3F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D top = new Path2D.Float();
        top.moveTo(cx - 4.5F, cy + 3.0F);
        top.curveTo(cx - 1.5F, cy + 3.0F, cx + 1.5F, cy - 3.0F, cx + 4.0F, cy - 3.0F);
        g.draw(top);
        Path2D bottom = new Path2D.Float();
        bottom.moveTo(cx - 4.5F, cy - 3.0F);
        bottom.curveTo(cx - 1.5F, cy - 3.0F, cx + 1.5F, cy + 3.0F, cx + 4.0F, cy + 3.0F);
        g.draw(bottom);
        drawArrowHead(g, cx + 4.0F, cy - 3.0F);
        drawArrowHead(g, cx + 4.0F, cy + 3.0F);
    }

    private static void drawRepeat(Graphics2D g, float cx, float cy, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1.3F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D top = new Path2D.Float();
        top.moveTo(cx - 4.2F, cy + 1.2F);
        top.lineTo(cx - 4.2F, cy - 1.4F);
        top.quadTo(cx - 4.2F, cy - 3.2F, cx - 2.2F, cy - 3.2F);
        top.lineTo(cx + 4.0F, cy - 3.2F);
        g.draw(top);
        Path2D bottom = new Path2D.Float();
        bottom.moveTo(cx + 4.2F, cy - 1.2F);
        bottom.lineTo(cx + 4.2F, cy + 1.4F);
        bottom.quadTo(cx + 4.2F, cy + 3.2F, cx + 2.2F, cy + 3.2F);
        bottom.lineTo(cx - 4.0F, cy + 3.2F);
        g.draw(bottom);
        drawArrowHead(g, cx + 4.0F, cy - 3.2F);
        drawArrowHead(g, cx - 4.0F, cy + 3.2F);
    }

    private static void drawArrowHead(Graphics2D g, float x, float y) {
        g.draw(new Line2D.Float(x - 1.6F, y - 1.6F, x, y));
        g.draw(new Line2D.Float(x - 1.6F, y + 1.6F, x, y));
    }

    private void drawRightSection(Graphics2D g, Color accent, int hoveredButton) {
        g.setColor(DIVIDER_COLOR);
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new Line2D.Float(DIVIDER_X, 29, DIVIDER_X, 55));

        // Menu chip (three dots) -> opens the MediaPlayer settings.
        g.setColor(hoveredButton == 5 ? lighten(CHIP_BG, 14) : CHIP_BG);
        g.fill(new RoundRectangle2D.Float(MENU_X, MENU_Y, MENU_SIZE, MENU_SIZE, 7, 7));
        g.setColor(new Color(255, 255, 255, hoveredButton == 5 ? 45 : 22));
        g.draw(new RoundRectangle2D.Float(MENU_X + 0.5F, MENU_Y + 0.5F, MENU_SIZE - 1, MENU_SIZE - 1, 6, 6));
        g.setColor(hoveredButton == 5 ? ICON_COLOR : ICON_MUTED_COLOR);
        float dotX = MENU_X + MENU_SIZE / 2.0F - 0.7F;
        for (int i = 0; i < 3; i++) {
            g.fill(new Ellipse2D.Float(dotX, MENU_Y + 2.0F + i * 2.6F, 1.4F, 1.4F));
        }

        // Speaker.
        g.setColor(ICON_MUTED_COLOR);
        Path2D speaker = new Path2D.Float();
        speaker.moveTo(VOLUME_ICON_CX - 4.2F, VOLUME_CY - 1.7F);
        speaker.lineTo(VOLUME_ICON_CX - 2.0F, VOLUME_CY - 1.7F);
        speaker.lineTo(VOLUME_ICON_CX + 0.6F, VOLUME_CY - 4.0F);
        speaker.lineTo(VOLUME_ICON_CX + 0.6F, VOLUME_CY + 4.0F);
        speaker.lineTo(VOLUME_ICON_CX - 2.0F, VOLUME_CY + 1.7F);
        speaker.lineTo(VOLUME_ICON_CX - 4.2F, VOLUME_CY + 1.7F);
        speaker.closePath();
        g.fill(speaker);
        g.setStroke(new BasicStroke(1.1F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Arc2D.Float(VOLUME_ICON_CX + 0.8F, VOLUME_CY - 2.6F, 4.4F, 5.2F, -55, 110, java.awt.geom.Arc2D.OPEN));

        // Volume slider (controls the Windows master volume).
        float volumeFraction = clamp01(controller.getVolume());
        float volumeWidth = VOLUME_RIGHT - VOLUME_LEFT;
        g.setColor(TRACK_COLOR);
        g.fill(new RoundRectangle2D.Float(VOLUME_LEFT, VOLUME_CY - 1.2F, volumeWidth, 2.4F, 2.4F, 2.4F));
        g.setColor(accent);
        if (volumeFraction > 0.01F) {
            g.fill(new RoundRectangle2D.Float(VOLUME_LEFT, VOLUME_CY - 1.2F, volumeWidth * volumeFraction, 2.4F, 2.4F, 2.4F));
        }
        g.fill(new Ellipse2D.Float(VOLUME_LEFT + volumeWidth * volumeFraction - 2.6F, VOLUME_CY - 2.6F, 5.2F, 5.2F));
    }

    private static void drawNote(Graphics2D g, float x, float y, float size, Color accent) {
        g.setColor(accent);
        g.setStroke(new BasicStroke(2.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float cx = x + size / 2.0F;
        float cy = y + size / 2.0F;
        // Eighth note: head, stem and a curved flag.
        g.fill(new Ellipse2D.Float(cx - 6.5F, cy + 2.5F, 7.5F, 6.0F));
        g.draw(new Line2D.Float(cx + 0.6F, cy + 5.0F, cx + 0.6F, cy - 8.5F));
        Path2D flag = new Path2D.Float();
        flag.moveTo(cx + 0.6F, cy - 8.5F);
        flag.quadTo(cx + 5.5F, cy - 7.0F, cx + 6.0F, cy - 2.0F);
        g.draw(flag);
    }

    // ---- Cover ----

    private void refreshCover() {
        int generation = controller.getCoverGeneration();
        if (generation == appliedCoverGeneration) {
            return;
        }
        appliedCoverGeneration = generation;
        try {
            coverImage = ImageIO.read(new File(controller.getCoverPath()));
        } catch (Exception exception) {
            coverImage = null;
        }
    }

    // ---- Interaction (chat screen only, fully expanded only) ----

    private int hoveredButton(OrionConfig config, double mouseX, double mouseY) {
        if (eased < 0.95F || mouseX < 0) {
            return -1;
        }
        double rx = mouseX - contentGuiX(config);
        double ry = mouseY - panelGuiY;

        if (rx >= MENU_X - 1.5F && rx <= MENU_X + MENU_SIZE + 1.5F
                && ry >= MENU_Y - 1.5F && ry <= MENU_Y + MENU_SIZE + 1.5F) {
            return 5;
        }

        if (Math.abs(ry - BUTTONS_CY) > 8) {
            return -1;
        }
        if (Math.abs(rx - PREV_CX) <= 6) {
            return 0;
        }
        if (Math.abs(rx - PLAY_CX) <= PLAY_RADIUS + 1) {
            return 1;
        }
        if (Math.abs(rx - NEXT_CX) <= 6) {
            return 2;
        }
        if (Math.abs(rx - SHUFFLE_CX) <= 6) {
            return 3;
        }
        if (Math.abs(rx - REPEAT_CX) <= 6) {
            return 4;
        }
        return -1;
    }

    private boolean isOnVolume(OrionConfig config, double mouseX, double mouseY) {
        double rx = mouseX - contentGuiX(config);
        double ry = mouseY - panelGuiY;
        return rx >= VOLUME_LEFT - 4 && rx <= VOLUME_RIGHT + 4 && Math.abs(ry - VOLUME_CY) <= 5;
    }

    private float volumeFractionAt(OrionConfig config, double mouseX) {
        double rx = mouseX - contentGuiX(config);
        return clamp01((float) ((rx - VOLUME_LEFT) / (VOLUME_RIGHT - VOLUME_LEFT)));
    }

    private int contentGuiX(OrionConfig config) {
        return isTopRight(config) ? panelGuiX + panelGuiWidth - PANEL_WIDTH : panelGuiX;
    }

    private void handleInput(Minecraft client, OrionConfig config, double mouseX, double mouseY) {
        if (!(client.gui.screen() instanceof ChatScreen) || eased < 0.95F) {
            wasMouseDown = false;
            pressedInside = false;
            pressedOnBar = false;
            pressedOnVolume = false;
            return;
        }

        boolean down = client.mouseHandler.isLeftPressed();
        int x = contentGuiX(config);
        int y = panelGuiY;

        if (down && !wasMouseDown) {
            pressedInside = false;
            pressedOnBar = false;
            pressedOnVolume = false;

            if (isInside(mouseX, mouseY, x, y, PANEL_WIDTH, PANEL_HEIGHT)) {
                boolean onBar = controller.getDurationSeconds() > 0
                        && mouseX >= x + BAR_LEFT && mouseX <= x + BAR_RIGHT
                        && mouseY >= y + BAR_CY - 4 && mouseY <= y + BAR_CY + 5;
                if (onBar) {
                    pressedOnBar = true;
                } else if (isOnVolume(config, mouseX, mouseY)) {
                    pressedOnVolume = true;
                    controller.setVolume(volumeFractionAt(config, mouseX), false);
                } else {
                    pressedInside = true;
                    pressX = mouseX;
                    pressY = mouseY;
                }
            }
        } else if (down && wasMouseDown && pressedOnVolume) {
            controller.setVolume(volumeFractionAt(config, mouseX), false);
        } else if (!down && wasMouseDown) {
            if (pressedOnVolume) {
                controller.setVolume(volumeFractionAt(config, mouseX), true);
            } else if (pressedOnBar) {
                double duration = controller.getDurationSeconds();
                if (duration > 0) {
                    double fraction = Math.max(0.0, Math.min(1.0, (mouseX - x - BAR_LEFT) / (double) (BAR_RIGHT - BAR_LEFT)));
                    controller.seek(fraction * duration);
                }
            } else if (pressedInside) {
                int button = hoveredButton(config, pressX, pressY);
                if (button == 0) {
                    controller.previous();
                } else if (button == 1) {
                    controller.playPause();
                } else if (button == 2) {
                    controller.next();
                } else if (button == 3) {
                    controller.toggleShuffle();
                } else if (button == 4) {
                    controller.cycleRepeat();
                } else if (button == 5) {
                    client.gui.setScreen(ru.orion.client.gui.OrionClickGuiScreen.withMediaPlayerSettings(configManager));
                }
            }
            pressedInside = false;
            pressedOnBar = false;
            pressedOnVolume = false;
        }

        wasMouseDown = down;
    }

    // ---- Helpers ----

    private void releaseTextures() {
        if (dynamicTexture != null) {
            Minecraft.getInstance().getTextureManager().release(HUD_TEXTURE);
            dynamicTexture = null;
            lastFingerprint = null;
        }
        OrionGlassCompositor.INSTANCE.releaseMask(HUD_TEXTURE);
        wasMouseDown = false;
        pressedInside = false;
        pressedOnBar = false;
        pressedOnVolume = false;
    }

    private static Font titleFont() {
        Font font = pickFont(8.4F, Font.PLAIN, "Inter SemiBold", "Inter Semi Bold", "Segoe UI Semibold");
        if (font != null) {
            return font;
        }
        font = pickFont(8.4F, Font.BOLD, "Inter", "Segoe UI");
        return font != null ? font : new Font(Font.SANS_SERIF, Font.BOLD, 8).deriveFont(Font.BOLD, 8.4F);
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

    private static String fit(Graphics2D g, String text, int maxWidth) {
        if (g.getFontMetrics().getStringBounds(text, g).getWidth() <= maxWidth) {
            return text;
        }

        String ellipsis = "…";
        while (!text.isEmpty() && g.getFontMetrics().getStringBounds(text + ellipsis, g).getWidth() > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }

    private static String formatTime(double seconds) {
        int total = (int) Math.floor(seconds);
        int minutes = total / 60;
        int secs = total % 60;
        return minutes + ":" + (secs < 10 ? "0" + secs : Integer.toString(secs));
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static Color lighten(Color color, int amount) {
        return new Color(Math.min(255, color.getRed() + amount), Math.min(255, color.getGreen() + amount),
                Math.min(255, color.getBlue() + amount), color.getAlpha());
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float easeInOutCubic(float value) {
        if (value < 0.5F) {
            return 4.0F * value * value * value;
        }
        float f = -2.0F * value + 2.0F;
        return 1.0F - f * f * f / 2.0F;
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
