package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.util.ARGB;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;

/**
 * Crosshair: подменяет ванильный прицел на нарисованный пользователем в редакторе
 * (сетка 15x15, см. OrionCrosshairEditorScreen). Пока модуль выключен — рисуется ванильный.
 */
public final class OrionCustomCrosshair {
    private final OrionConfigManager configManager;

    public OrionCustomCrosshair(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (context, tickCounter) -> {
            OrionConfig config = configManager.getConfig();
            if (!config.isCrosshairEnabled()) {
                original.extractRenderState(context, tickCounter);
                return;
            }

            String pixels = config.getCrosshairPixels();
            int grid = OrionConfig.CROSSHAIR_GRID;
            if (pixels.length() != grid * grid) {
                original.extractRenderState(context, tickCounter);
                return;
            }

            int color = ru.orion.client.effect.OrionRainbow.orElse(parseColor(config.getCrosshairColor()));
            int x0 = (context.guiWidth() - grid) / 2;
            int y0 = (context.guiHeight() - grid) / 2;
            for (int y = 0; y < grid; y++) {
                for (int x = 0; x < grid; x++) {
                    if (pixels.charAt(y * grid + x) == '1') {
                        context.fill(x0 + x, y0 + y, x0 + x + 1, y0 + y + 1, color);
                    }
                }
            }
        });
    }

    private static int parseColor(String hex) {
        try {
            return ARGB.opaque(Integer.parseInt(hex.substring(1), 16));
        } catch (RuntimeException ignored) {
            return ARGB.color(255, 245, 245, 255);
        }
    }
}
