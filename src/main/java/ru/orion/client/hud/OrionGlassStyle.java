package ru.orion.client.hud;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.geom.RoundRectangle2D;

/** Общая Java2D-поверхность стеклянных HUD; размытие выполняет GPU-композитор. */
public final class OrionGlassStyle {
    private static final Color TOP = new Color(255, 255, 255, 50);
    private static final Color MIDDLE = new Color(225, 228, 231, 12);
    private static final Color BOTTOM = new Color(255, 255, 255, 24);
    private static final Color EDGE_LIGHT = new Color(255, 255, 255, 150);
    private static final Color EDGE_CLEAR = new Color(255, 255, 255, 12);
    private static final Color EDGE_BOTTOM = new Color(255, 255, 255, 65);
    private static final Color TEXT_SHADOW = new Color(0, 0, 0, 145);
    private static final float EDGE_INSET = 0.5F;
    private static final float EDGE_WIDTH = 0.65F;

    private OrionGlassStyle() {
    }

    public static void drawPanel(Graphics2D graphics, float x, float y, float width, float height, float arc) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setPaint(new LinearGradientPaint(x, y, x, y + height,
                    new float[]{0.0F, 0.48F, 1.0F}, new Color[]{TOP, MIDDLE, BOTTOM}));
            g.fill(new RoundRectangle2D.Float(x, y, width, height, arc, arc));
            g.setStroke(new BasicStroke(EDGE_WIDTH));
            g.setPaint(new LinearGradientPaint(x, y, x + height, y + height,
                    new float[]{0.0F, 0.42F, 0.70F, 1.0F},
                    new Color[]{EDGE_LIGHT, EDGE_CLEAR, EDGE_CLEAR, EDGE_BOTTOM}));
            float innerArc = Math.max(0.0F, arc - EDGE_INSET * 2.0F);
            g.draw(new RoundRectangle2D.Float(x + EDGE_INSET, y + EDGE_INSET,
                    width - EDGE_INSET * 2.0F, height - EDGE_INSET * 2.0F, innerArc, innerArc));
        } finally {
            g.dispose();
        }
    }

    public static void drawText(Graphics2D g, Font font, String text, float x, float baseline, Color color) {
        g.setFont(font);
        g.setColor(TEXT_SHADOW);
        g.drawString(text, x + 0.45F, baseline + 0.65F);
        g.setColor(color);
        g.drawString(text, x, baseline);
    }
}
