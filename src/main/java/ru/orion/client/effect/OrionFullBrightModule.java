package ru.orion.client.effect;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import ru.orion.client.config.OrionConfigManager;

public final class OrionFullBrightModule {
    private static final int NIGHT_VISION_DURATION_TICKS = 1_728_000;
    private static final int REFRESH_THRESHOLD_TICKS = 40;

    private final OrionConfigManager configManager;
    private boolean appliedByModule;

    public OrionFullBrightModule(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }

        if (configManager.getConfig().isFullBrightEnabled()) {
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current == null || current.getDuration() <= REFRESH_THRESHOLD_TICKS) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_DURATION_TICKS, 0, false, false, false));
                appliedByModule = true;
            }
            return;
        }

        if (appliedByModule && player.hasEffect(MobEffects.NIGHT_VISION)) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        appliedByModule = false;
    }
}
