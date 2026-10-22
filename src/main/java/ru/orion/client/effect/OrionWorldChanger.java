package ru.orion.client.effect;

import net.minecraft.util.ARGB;
import org.joml.Vector4f;

public final class OrionWorldChanger {
    public static final String[] COLOR_PRESETS = {
            "#FF7C00", "#64D9FF", "#8A5CFF", "#FF5DAA", "#35F28A", "#F5F5FF",
            "#FF3030", "#FFD84D", "#1BFFEE", "#14111F"
    };

    private OrionWorldChanger() {
    }

    public static String colorName(String color) {
        return switch (normalize(color)) {
            case "#FF7C00" -> "Orion";
            case "#64D9FF" -> "Cyan";
            case "#8A5CFF" -> "Violet";
            case "#FF5DAA" -> "Pink";
            case "#35F28A" -> "Green";
            case "#F5F5FF" -> "White";
            case "#FF3030" -> "Red";
            case "#FFD84D" -> "Gold";
            case "#1BFFEE" -> "Aqua";
            case "#14111F" -> "Night";
            default -> normalize(color);
        };
    }

    public static String cycleColor(String current, int direction) {
        String normalized = normalize(current);
        int index = 0;
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            if (COLOR_PRESETS[i].equalsIgnoreCase(normalized)) {
                index = i;
                break;
            }
        }
        return COLOR_PRESETS[Math.floorMod(index + direction, COLOR_PRESETS.length)];
    }

    public static int parseOpaqueColor(String color) {
        String normalized = normalize(color);
        try {
            return ARGB.opaque(Integer.parseInt(normalized.substring(1), 16));
        } catch (RuntimeException ignored) {
            return ARGB.color(255, 255, 124, 0);
        }
    }

    public static Vector4f fogVector(String color) {
        int rgb = OrionRainbow.orElse(parseOpaqueColor(color));
        return new Vector4f(ARGB.red(rgb) / 255.0F, ARGB.green(rgb) / 255.0F, ARGB.blue(rgb) / 255.0F, 1.0F);
    }

    private static String normalize(String color) {
        if (color == null || !color.matches("^#[0-9a-fA-F]{6}$")) {
            return "#FF7C00";
        }
        return color.toUpperCase(java.util.Locale.ROOT);
    }
}
