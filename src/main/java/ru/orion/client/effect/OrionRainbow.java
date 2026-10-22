package ru.orion.client.effect;

import net.minecraft.util.ARGB;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;

/**
 * RainbowColor: когда модуль включён, все настраиваемые цвета клиента заменяются
 * текущим цветом радуги, плавно пробегающей весь спектр за PERIOD_MS.
 */
public final class OrionRainbow {
    private static final long BASE_PERIOD_MS = 12_000L; // период при скорости 1
    private static final int FINGERPRINT_STEPS = 64; // для Java2D-панелей: квантуем, чтобы не перерисовывать каждый кадр

    private OrionRainbow() {
    }

    public static boolean isActive() {
        OrionConfig config = OrionClient.getConfigOrNull();
        return config != null && config.isRainbowEnabled();
    }

    private static long periodMs() {
        OrionConfig config = OrionClient.getConfigOrNull();
        int speed = config == null ? 3 : Math.max(1, Math.min(10, config.getRainbowSpeed()));
        return BASE_PERIOD_MS / speed;
    }

    public static float hue() {
        long period = periodMs();
        return (System.currentTimeMillis() % period) / (float) period;
    }

    /** Непрозрачный ARGB текущего цвета радуги. */
    public static int argb() {
        return ARGB.opaque(java.awt.Color.HSBtoRGB(hue(), 1.0F, 1.0F) & 0xFFFFFF);
    }

    /** Радуга, если модуль включён, иначе исходный цвет. */
    public static int orElse(int argb) {
        return isActive() ? argb() : argb;
    }

    /** То же для Java2D-цветов (HUD-панели). */
    public static java.awt.Color orElse(java.awt.Color color) {
        if (!isActive()) {
            return color;
        }
        int rgb = argb();
        return new java.awt.Color(ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
    }

    /**
     * Дискретный шаг радуги для fingerprint'ов текстурных панелей:
     * -1 когда радуга выключена, иначе 0..FINGERPRINT_STEPS-1.
     */
    public static int fingerprintStep() {
        if (!isActive()) {
            return -1;
        }
        long period = periodMs();
        return (int) ((System.currentTimeMillis() % period) * FINGERPRINT_STEPS / period);
    }
}
