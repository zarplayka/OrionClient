package ru.orion.client.effect;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import ru.orion.client.config.OrionConfigManager;

public final class OrionNoWeatherModule {
    private final OrionConfigManager configManager;

    public OrionNoWeatherModule(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (!configManager.getConfig().isNoWeatherEnabled() || client.level == null) {
            return;
        }

        client.level.setRainLevel(0.0F);
        client.level.setThunderLevel(0.0F);
    }
}
