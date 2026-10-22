package ru.orion.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OrionConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "orion-client.json";

    private final Path configPath;
    private OrionConfig config = OrionConfig.createDefault();

    public OrionConfigManager() {
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
    }

    public void load() {
        if (!Files.exists(configPath)) {
            config = OrionConfig.createDefault();
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            OrionConfig loadedConfig = GSON.fromJson(reader, OrionConfig.class);

            if (loadedConfig == null) {
                throw new IllegalStateException("Config file is empty.");
            }

            loadedConfig.sanitize();
            config = loadedConfig;
            save();
        } catch (Exception exception) {
            LOGGER.warn("Failed to load Orion Client config. Restoring default config.", exception);
            config = OrionConfig.createDefault();
            save();
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());

            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            LOGGER.error("Failed to save Orion Client config.", exception);
        }
    }

    public OrionConfig getConfig() {
        return config;
    }

    public void setHudEnabled(boolean hudEnabled) {
        config.setHudEnabled(hudEnabled);
        save();
    }

    public void setScoreboardGlassEnabled(boolean enabled) {
        config.setScoreboardGlassEnabled(enabled);
        save();
    }

    public void setDiscordRpcEnabled(boolean discordRpcEnabled) {
        config.setDiscordRpcEnabled(discordRpcEnabled);
        save();
    }

    public void setJumpCircleEnabled(boolean jumpCircleEnabled) {
        config.setJumpCircleEnabled(jumpCircleEnabled);
        save();
    }

    public void setJumpCircleColor(String jumpCircleColor) {
        config.setJumpCircleColor(jumpCircleColor);
        save();
    }

    public void setMediaPlayerEnabled(boolean mediaPlayerEnabled) {
        config.setMediaPlayerEnabled(mediaPlayerEnabled);
        save();
    }

    public void setMediaPlayerPosition(int x, int y) {
        config.setMediaPlayerX(x);
        config.setMediaPlayerY(y);
        save();
    }

    public void setAspectEnabled(boolean aspectEnabled) {
        config.setAspectEnabled(aspectEnabled);
        save();
    }

    public void setTargetEspEnabled(boolean targetEspEnabled) {
        config.setTargetEspEnabled(targetEspEnabled);
        save();
    }

    public void setTargetEspTexture(int targetEspTexture) {
        config.setTargetEspTexture(targetEspTexture);
        save();
    }

    public void setTargetEspChainColor(String targetEspChainColor) {
        config.setTargetEspChainColor(targetEspChainColor);
        save();
    }

    public void setChinaHeadEnabled(boolean chinaHeadEnabled) {
        config.setChinaHeadEnabled(chinaHeadEnabled);
        save();
    }

    public void setWorldParticlesEnabled(boolean worldParticlesEnabled) {
        config.setWorldParticlesEnabled(worldParticlesEnabled);
        save();
    }

    public void setWorldParticlesColor(String worldParticlesColor) {
        config.setWorldParticlesColor(worldParticlesColor);
        save();
    }

    public void setNoWeatherEnabled(boolean noWeatherEnabled) {
        config.setNoWeatherEnabled(noWeatherEnabled);
        save();
    }

    public void setNoFireEnabled(boolean noFireEnabled) {
        config.setNoFireEnabled(noFireEnabled);
        save();
    }

    public void setFullBrightEnabled(boolean fullBrightEnabled) {
        config.setFullBrightEnabled(fullBrightEnabled);
        save();
    }

    public void setWorldChangerEnabled(boolean worldChangerEnabled) {
        config.setWorldChangerEnabled(worldChangerEnabled);
        save();
    }

    public void setWorldChangerSkyColor(String worldChangerSkyColor) {
        config.setWorldChangerSkyColor(worldChangerSkyColor);
        save();
    }

    public void setWorldChangerFogColor(String worldChangerFogColor) {
        config.setWorldChangerFogColor(worldChangerFogColor);
        save();
    }

    public void setMediaPlayerCorner(String mediaPlayerCorner) {
        config.setMediaPlayerCorner(mediaPlayerCorner);
        config.sanitize();
        save();
    }

    public void setMediaPlayerAnimated(boolean mediaPlayerAnimated) {
        config.setMediaPlayerAnimated(mediaPlayerAnimated);
        save();
    }

    public void setClickGuiTheme(int clickGuiTheme) {
        config.setClickGuiTheme(clickGuiTheme);
        config.sanitize();
        save();
    }

    public void setHealthTagsEnabled(boolean healthTagsEnabled) {
        config.setHealthTagsEnabled(healthTagsEnabled);
        save();
    }

    public void setProtectNameEnabled(boolean protectNameEnabled) {
        config.setProtectNameEnabled(protectNameEnabled);
        save();
    }

    public void setProtectName(String protectName) {
        config.setProtectName(protectName);
        save();
    }

    public void setFakePrefixEnabled(boolean fakePrefixEnabled) {
        config.setFakePrefixEnabled(fakePrefixEnabled);
        save();
    }

    public void setFakePrefix(String fakePrefix) {
        config.setFakePrefix(fakePrefix);
        save();
    }

    public void setFakePrefixAdditionalNames(String additionalNames) {
        config.setFakePrefixAdditionalNames(additionalNames);
        save();
    }

    public void setHitBoxEnabled(boolean hitBoxEnabled) {
        config.setHitBoxEnabled(hitBoxEnabled);
        save();
    }

    public void setHitColorEnabled(boolean hitColorEnabled) {
        config.setHitColorEnabled(hitColorEnabled);
        save();
    }

    public void setCrosshairEnabled(boolean crosshairEnabled) {
        config.setCrosshairEnabled(crosshairEnabled);
        save();
    }

    public void setBetterNearEnabled(boolean betterNearEnabled) {
        config.setBetterNearEnabled(betterNearEnabled);
        save();
    }

    public void setRainbowEnabled(boolean rainbowEnabled) {
        config.setRainbowEnabled(rainbowEnabled);
        save();
    }

    public void setHitSoundEnabled(boolean hitSoundEnabled) {
        config.setHitSoundEnabled(hitSoundEnabled);
        save();
    }

    public void setHitSoundIndex(int hitSoundIndex) {
        config.setHitSoundIndex(hitSoundIndex);
        config.sanitize();
        save();
    }
}
