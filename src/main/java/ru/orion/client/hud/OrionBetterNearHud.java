package ru.orion.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionNameProtect;
import ru.orion.client.effect.OrionRainbow;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * BetterNear: панель в стиле клиента со списком игроков в радиусе — дистанция в блоках,
 * ник и стрелка направления относительно взгляда. Появляется только когда рядом кто-то есть.
 * Рисуется Java2D в текстуру, как остальные HUD-панели клиента.
 */
public final class OrionBetterNearHud {
    private static final Identifier HUD_TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "hud_better_near");
    private static final int MAX_TEXTURE_SCALE = 6;
    private static final int MAX_ROWS = 5;
    private static final int ARROW_SECTORS = 24; // квантование угла стрелки, чтобы не перерисовывать текстуру каждый кадр

    // Design units (1 unit = 1 gui px).
    private static final float PANEL_WIDTH = 118.0F;
    private static final float HEADER_HEIGHT = 18.0F;
    private static final float ROW_HEIGHT = 15.0F;
    private static final float PAD_X = 9.0F;
    private static final float BOTTOM_PAD = 6.0F;
    private static final int SCREEN_MARGIN = 8;
    private static final float PANEL_ARC = 8.0F;

    private static final Color TEXT_COLOR = new Color(0xF4, 0xF4, 0xF6);
    private static final Color MUTED_COLOR = new Color(0xD4, 0xDA, 0xE8);

    private final OrionConfigManager configManager;

    private DynamicTexture dynamicTexture;
    private int textureWidth;
    private int textureHeight;
    private int guiWidth;
    private int guiHeight;
    private List<Object> lastFingerprint;
    // Перетаскивание при открытом чате.
    private boolean wasMouseDown;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    private record NearPlayer(String name, int distance, int sector) {
    }

    public OrionBetterNearHud(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        Identifier hudId = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "better_near");
        HudElementRegistry.addLast(hudId, this::render);
    }

    private void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        OrionConfig config = configManager.getConfig();
        if (!config.isBetterNearEnabled()) {
            releaseTextures();
            return;
        }

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            releaseTextures();
            return;
        }

        boolean chatOpen = client.gui.screen() instanceof ChatScreen;
        List<NearPlayer> nearPlayers = collectNearPlayers(client, player, config.getBetterNearRadius());
        // При открытом чате панель видна даже пустой — чтобы её можно было перетащить.
        if (nearPlayers.isEmpty() && !chatOpen) {
            releaseTextures();
            wasMouseDown = false;
            dragging = false;
            return;
        }

        int scale = Math.max(1, Math.min(MAX_TEXTURE_SCALE, client.getWindow().getGuiScale()));

        List<Object> fingerprint = new ArrayList<>(nearPlayers.size() * 3 + 3);
        fingerprint.add(scale);
        fingerprint.add(config.getAccentColor());
        fingerprint.add(OrionRainbow.fingerprintStep());
        for (NearPlayer near : nearPlayers) {
            fingerprint.add(near.name);
            fingerprint.add(near.distance);
            fingerprint.add(near.sector);
        }
        if (!fingerprint.equals(lastFingerprint)) {
            updateTexture(config, nearPlayers, scale);
            lastFingerprint = fingerprint;
        }

        if (dynamicTexture == null) {
            return;
        }

        handleDrag(client, context, config, chatOpen);

        int x = effectiveX(context, config);
        int y = effectiveY(context, config);
        if (!OrionGlassCompositor.INSTANCE.submit(HUD_TEXTURE, context, dynamicTexture, x, y, guiWidth, guiHeight)) {
            context.blit(HUD_TEXTURE, x, y, x + guiWidth, y + guiHeight, 0.0F, 1.0F, 0.0F, 1.0F);
        }
    }

    private int effectiveX(GuiGraphicsExtractor context, OrionConfig config) {
        int x = config.getBetterNearX() < 0
                ? context.guiWidth() - guiWidth - SCREEN_MARGIN
                : config.getBetterNearX();
        return Mth.clamp(x, 0, Math.max(0, context.guiWidth() - guiWidth));
    }

    private int effectiveY(GuiGraphicsExtractor context, OrionConfig config) {
        int y = config.getBetterNearY() < 0 ? SCREEN_MARGIN : config.getBetterNearY();
        return Mth.clamp(y, 0, Math.max(0, context.guiHeight() - guiHeight));
    }

    /** Перетаскивание мышью, пока открыт чат (курсор свободен только там). */
    private void handleDrag(Minecraft client, GuiGraphicsExtractor context, OrionConfig config, boolean chatOpen) {
        if (!chatOpen) {
            wasMouseDown = false;
            dragging = false;
            return;
        }

        boolean down = client.mouseHandler.isLeftPressed();
        double mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
        double mouseY = client.mouseHandler.getScaledYPos(client.getWindow());

        if (down && !wasMouseDown) {
            int x = effectiveX(context, config);
            int y = effectiveY(context, config);
            if (mouseX >= x && mouseX <= x + guiWidth && mouseY >= y && mouseY <= y + guiHeight) {
                dragging = true;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - y;
            }
        } else if (down && dragging) {
            int newX = Mth.clamp((int) Math.round(mouseX - dragOffsetX), 0, Math.max(0, context.guiWidth() - guiWidth));
            int newY = Mth.clamp((int) Math.round(mouseY - dragOffsetY), 0, Math.max(0, context.guiHeight() - guiHeight));
            config.setBetterNearX(newX);
            config.setBetterNearY(newY);
        } else if (!down && dragging) {
            dragging = false;
            configManager.save();
        }

        wasMouseDown = down;
    }

    private List<NearPlayer> collectNearPlayers(Minecraft client, LocalPlayer player, int radius) {
        List<AbstractClientPlayer> candidates = new ArrayList<>();
        for (AbstractClientPlayer other : client.level.players()) {
            if (other == player) {
                continue;
            }
            if (player.distanceToSqr(other) <= (double) radius * radius) {
                candidates.add(other);
            }
        }
        candidates.sort(Comparator.comparingDouble(player::distanceToSqr));

        List<NearPlayer> result = new ArrayList<>(Math.min(candidates.size(), MAX_ROWS));
        for (int i = 0; i < candidates.size() && i < MAX_ROWS; i++) {
            AbstractClientPlayer other = candidates.get(i);
            int distance = Math.max(1, (int) Math.round(Math.sqrt(player.distanceToSqr(other))));

            // Куда указывает стрелка: азимут на цель минус направление взгляда (0 = прямо перед собой).
            double bearing = Math.toDegrees(Math.atan2(other.getZ() - player.getZ(), other.getX() - player.getX())) - 90.0;
            float relative = (float) Mth.wrapDegrees(bearing - player.getYRot());
            int sector = Math.floorMod(Math.round(relative / (360.0F / ARROW_SECTORS)), ARROW_SECTORS);

            result.add(new NearPlayer(OrionNameProtect.replace(other.getGameProfile().name()), distance, sector));
        }
        return result;
    }

    private void updateTexture(OrionConfig config, List<NearPlayer> nearPlayers, int scale) {
        float designWidth = PANEL_WIDTH;
        float designHeight = HEADER_HEIGHT + Math.max(1, nearPlayers.size()) * ROW_HEIGHT + BOTTOM_PAD;

        guiWidth = Math.max(1, Math.round(designWidth));
        guiHeight = Math.max(1, Math.round(designHeight));
        int imageWidth = guiWidth * scale;
        int imageHeight = guiHeight * scale;

        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(imageWidth / designWidth, imageHeight / designHeight);

        Color accent = OrionRainbow.orElse(parseColor(config.getAccentColor()));

        OrionGlassStyle.drawPanel(g, 0, 0, designWidth, designHeight, PANEL_ARC);

        Font titleFont = titleFont(7.5F);
        Font nameFont = bodyFont(7.0F);
        Font distFont = titleFont(7.0F);

        OrionGlassStyle.drawText(g, titleFont, "Рядом", PAD_X, 12.0F, accent);
        g.setStroke(new BasicStroke(1.0F));
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70));
        g.draw(new RoundRectangle2D.Float(PAD_X, HEADER_HEIGHT - 2.5F, designWidth - PAD_X * 2, 0.6F, 0.6F, 0.6F));

        if (nearPlayers.isEmpty()) {
            // Плейсхолдер при открытом чате: панель видна, чтобы её можно было перетащить.
            OrionGlassStyle.drawText(g, nameFont, "Никого рядом", PAD_X,
                    HEADER_HEIGHT + ROW_HEIGHT / 2.0F + 2.6F, MUTED_COLOR);
        }

        for (int i = 0; i < nearPlayers.size(); i++) {
            NearPlayer near = nearPlayers.get(i);
            float rowCenter = HEADER_HEIGHT + i * ROW_HEIGHT + ROW_HEIGHT / 2.0F;

            drawArrow(g, PAD_X + 4.5F, rowCenter, 4.2F, near.sector * (360.0F / ARROW_SECTORS), accent);

            OrionGlassStyle.drawText(g, nameFont,
                    clip(g, nameFont, near.name, designWidth - PAD_X * 2 - 42),
                    PAD_X + 12.0F, rowCenter + 2.6F, TEXT_COLOR);

            String distance = near.distance + "м";
            g.setFont(distFont);
            float distWidth = (float) g.getFontMetrics(distFont).getStringBounds(distance, g).getWidth();
            OrionGlassStyle.drawText(g, distFont, distance, designWidth - PAD_X - distWidth,
                    rowCenter + 2.6F, MUTED_COLOR);
        }

        g.dispose();
        uploadTexture(image);
        OrionGlassCompositor.INSTANCE.updateMask(HUD_TEXTURE, new OrionGlassCompositor.MaskLayout(
                imageWidth, imageHeight, designWidth, designHeight,
                List.of(new OrionGlassCompositor.Panel(0, 0, designWidth, designHeight, PANEL_ARC))));
    }

    private void releaseTextures() {
        if (dynamicTexture != null) {
            Minecraft.getInstance().getTextureManager().release(HUD_TEXTURE);
            dynamicTexture = null;
            lastFingerprint = null;
        }
        OrionGlassCompositor.INSTANCE.releaseMask(HUD_TEXTURE);
        wasMouseDown = false;
        dragging = false;
    }

    /** Стрелка-указатель, повёрнутая на angleDeg (0 — вверх, по часовой). */
    private static void drawArrow(Graphics2D g, float cx, float cy, float r, float angleDeg, Color accent) {
        Path2D arrow = new Path2D.Float();
        arrow.moveTo(0, -r);
        arrow.lineTo(r * 0.72F, r);
        arrow.lineTo(0, r * 0.45F);
        arrow.lineTo(-r * 0.72F, r);
        arrow.closePath();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(cx, cy);
        g2.rotate(Math.toRadians(angleDeg));
        g2.setColor(accent);
        g2.fill(arrow);
        g2.dispose();
    }

    private static String clip(Graphics2D g, Font font, String text, float maxWidth) {
        if ((float) g.getFontMetrics(font).getStringBounds(text, g).getWidth() <= maxWidth) {
            return text;
        }
        String clipped = text;
        while (clipped.length() > 1
                && (float) g.getFontMetrics(font).getStringBounds(clipped + "…", g).getWidth() > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped + "…";
    }

    private void uploadTexture(BufferedImage image) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        Minecraft client = Minecraft.getInstance();

        if (dynamicTexture == null || textureWidth != imageWidth || textureHeight != imageHeight) {
            client.getTextureManager().release(HUD_TEXTURE);
            NativeImage nativeImage = new NativeImage(imageWidth, imageHeight, true);
            copyPixels(image, nativeImage);
            dynamicTexture = new DynamicTexture(() -> "orion_better_near", nativeImage);
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
