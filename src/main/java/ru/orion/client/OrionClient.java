package ru.orion.client;

import net.fabricmc.api.ClientModInitializer;
import ru.orion.client.command.OrionFakeBanCommand;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.discord.OrionDiscordPresenceService;
import ru.orion.client.effect.OrionChinaHeadRenderer;
import ru.orion.client.effect.OrionFullBrightModule;
import ru.orion.client.effect.OrionHealthTags;
import ru.orion.client.effect.OrionHitColorFlash;
import ru.orion.client.effect.OrionHitSound;
import ru.orion.client.effect.OrionJumpCircleEffect;
import ru.orion.client.effect.OrionNoWeatherModule;
import ru.orion.client.effect.OrionTargetEsp;
import ru.orion.client.effect.OrionWorldParticles;
import ru.orion.client.hud.OrionBetterNearHud;
import ru.orion.client.hud.OrionCustomCrosshair;
import ru.orion.client.hud.OrionHudRenderer;
import ru.orion.client.keybind.OrionKeybinds;
import ru.orion.client.media.OrionMediaController;
import ru.orion.client.media.OrionMediaPlayerHud;

public final class OrionClient implements ClientModInitializer {
    public static final String MOD_ID = "orion-client";
    public static final String VERSION_TEXT = "Orion Client v0.1";

    private static OrionClient instance;

    private final OrionConfigManager configManager = new OrionConfigManager();
    private final OrionHudRenderer hudRenderer = new OrionHudRenderer(configManager);
    private final OrionKeybinds keybinds = new OrionKeybinds(configManager);
    private final OrionDiscordPresenceService discordPresenceService = new OrionDiscordPresenceService(configManager);
    private final OrionJumpCircleEffect jumpCircleEffect = new OrionJumpCircleEffect(configManager);
    private final OrionTargetEsp targetEsp = new OrionTargetEsp(configManager);
    private final OrionChinaHeadRenderer chinaHeadRenderer = new OrionChinaHeadRenderer(configManager);
    private final OrionHealthTags healthTags = new OrionHealthTags(configManager);
    private final OrionWorldParticles worldParticles = new OrionWorldParticles(configManager);
    private final OrionNoWeatherModule noWeatherModule = new OrionNoWeatherModule(configManager);
    private final OrionFullBrightModule fullBrightModule = new OrionFullBrightModule(configManager);
    private final OrionMediaController mediaController = new OrionMediaController(configManager);
    private final OrionMediaPlayerHud mediaPlayerHud = new OrionMediaPlayerHud(configManager, mediaController);
    private final OrionHitColorFlash hitColorFlash = new OrionHitColorFlash(configManager);
    private final OrionHitSound hitSound = new OrionHitSound(configManager);
    private final OrionCustomCrosshair customCrosshair = new OrionCustomCrosshair(configManager);
    private final OrionBetterNearHud betterNearHud = new OrionBetterNearHud(configManager);
    private final OrionFakeBanCommand fakeBanCommand = new OrionFakeBanCommand();

    @Override
    public void onInitializeClient() {
        instance = this;

        configManager.load();
        hudRenderer.register();
        keybinds.register();
        discordPresenceService.register();
        jumpCircleEffect.register();
        targetEsp.register();
        chinaHeadRenderer.register();
        healthTags.register();
        worldParticles.register();
        noWeatherModule.register();
        fullBrightModule.register();
        mediaController.register();
        mediaPlayerHud.register();
        hitColorFlash.register();
        hitSound.register();
        customCrosshair.register();
        betterNearHud.register();
        fakeBanCommand.register();
    }

    public static OrionClient getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Orion Client is not initialized yet.");
        }

        return instance;
    }

    /** Safe accessor for mixins/early callers; returns null before init instead of throwing. */
    public static OrionConfig getConfigOrNull() {
        OrionClient current = instance;
        return current == null ? null : current.configManager.getConfig();
    }

    public OrionConfigManager getConfigManager() {
        return configManager;
    }

    public OrionDiscordPresenceService getDiscordPresenceService() {
        return discordPresenceService;
    }

    public OrionTargetEsp getTargetEsp() {
        return targetEsp;
    }

    public static OrionTargetEsp getTargetEspOrNull() {
        OrionClient current = instance;
        return current == null ? null : current.targetEsp;
    }

    public static OrionHitColorFlash getHitColorFlashOrNull() {
        OrionClient current = instance;
        return current == null ? null : current.hitColorFlash;
    }

    public OrionHitSound getHitSound() {
        return hitSound;
    }

    public static OrionHitSound getHitSoundOrNull() {
        OrionClient current = instance;
        return current == null ? null : current.hitSound;
    }

    public OrionMediaController getMediaController() {
        return mediaController;
    }
}
