package ru.orion.client.config;

public final class OrionConfig {
    private static final String DEFAULT_DISCORD_APPLICATION_ID = "1520703983163932832";

    private boolean hudEnabled = true;
    private boolean scoreboardGlassEnabled = true;
    private int hudX = 8;
    private int hudY = 8;
    private float hudScale = 1.0F;
    private String accentColor = "#FF7C00";
    private boolean discordRpcEnabled = true;
    private String discordApplicationId = DEFAULT_DISCORD_APPLICATION_ID;
    private String discordLargeImageKey = "orion_icon";
    private String discordLargeImageText = "Orion Client";
    private boolean jumpCircleEnabled = true;
    private String jumpCircleColor = "#FF7C00";
    private float jumpCircleSize = 1.0F;
    private boolean mediaPlayerEnabled = false;
    private int mediaPlayerX = 8;
    private int mediaPlayerY = 40;
    private String mediaPlayerCorner = "TOP_LEFT";
    private boolean mediaPlayerAnimated = true;
    private boolean aspectEnabled = false;
    private float aspectStretch = 1.25F;
    private boolean targetEspEnabled = false;
    private int targetEspTexture = 0;
    private String targetEspChainColor = "#F5F5FF";
    private boolean chinaHeadEnabled = false;
    private boolean worldParticlesEnabled = false;
    private String worldParticlesColor = "#FF7C00";
    private int worldParticlesCount = 180;
    private boolean noWeatherEnabled = false;
    private boolean noFireEnabled = false;
    private boolean fullBrightEnabled = false;
    private boolean worldChangerEnabled = false;
    private String worldChangerSkyColor = "#64D9FF";
    private String worldChangerFogColor = "#14111F";
    private int clickGuiTheme = 0;
    private boolean healthTagsEnabled = true;
    private boolean protectNameEnabled = false;
    private String protectName = "Player";
    private boolean fakePrefixEnabled = false;
    private String fakePrefix = "{\"text\":\"ORION \",\"color\":\"#FF7C00\",\"bold\":true}";
    private String fakePrefixAdditionalNames = "";
    private boolean hitBoxEnabled = false;
    private String hitBoxColor = "#FF7C00";
    private boolean hitColorEnabled = false;
    private String hitColorColor = "#EF4444";
    private int hitColorOpacity = 45;
    private boolean crosshairEnabled = false;
    private String crosshairColor = "#F5F5FF";
    private String crosshairPixels = defaultCrosshairPixels();
    private boolean betterNearEnabled = false;
    private int betterNearRadius = 50;
    private int betterNearX = -1; // -1 — позиция по умолчанию (правый верхний угол)
    private int betterNearY = -1;
    private boolean rainbowEnabled = false;
    private int rainbowSpeed = 3; // 1..10, период цикла = 12с / скорость
    private boolean hitSoundEnabled = false;
    private int hitSoundIndex = 0; // 0..5 — встроенные, 6 — свои из папки
    private int hitSoundVolume = 70; // 0..100%

    public static final int CROSSHAIR_GRID = 15;

    /** Классический плюс 9x9 по центру сетки 15x15. */
    public static String defaultCrosshairPixels() {
        StringBuilder pixels = new StringBuilder(CROSSHAIR_GRID * CROSSHAIR_GRID);
        for (int y = 0; y < CROSSHAIR_GRID; y++) {
            for (int x = 0; x < CROSSHAIR_GRID; x++) {
                boolean on = (x == 7 && y >= 3 && y <= 11) || (y == 7 && x >= 3 && x <= 11);
                pixels.append(on ? '1' : '0');
            }
        }
        return pixels.toString();
    }

    public static OrionConfig createDefault() {
        return new OrionConfig();
    }

    public boolean isHudEnabled() {
        return hudEnabled;
    }

    public boolean isScoreboardGlassEnabled() {
        return scoreboardGlassEnabled;
    }

    public void setScoreboardGlassEnabled(boolean enabled) {
        scoreboardGlassEnabled = enabled;
    }

    public void setHudEnabled(boolean hudEnabled) {
        this.hudEnabled = hudEnabled;
    }

    public int getHudX() {
        return hudX;
    }

    public void setHudX(int hudX) {
        this.hudX = hudX;
    }

    public int getHudY() {
        return hudY;
    }

    public void setHudY(int hudY) {
        this.hudY = hudY;
    }

    public float getHudScale() {
        return hudScale;
    }

    public void setHudScale(float hudScale) {
        this.hudScale = hudScale;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }

    public boolean isDiscordRpcEnabled() {
        return discordRpcEnabled;
    }

    public void setDiscordRpcEnabled(boolean discordRpcEnabled) {
        this.discordRpcEnabled = discordRpcEnabled;
    }

    public String getDiscordApplicationId() {
        return discordApplicationId;
    }

    public void setDiscordApplicationId(String discordApplicationId) {
        this.discordApplicationId = discordApplicationId;
    }

    public String getDiscordLargeImageKey() {
        return discordLargeImageKey;
    }

    public void setDiscordLargeImageKey(String discordLargeImageKey) {
        this.discordLargeImageKey = discordLargeImageKey;
    }

    public String getDiscordLargeImageText() {
        return discordLargeImageText;
    }

    public void setDiscordLargeImageText(String discordLargeImageText) {
        this.discordLargeImageText = discordLargeImageText;
    }

    public boolean isJumpCircleEnabled() {
        return jumpCircleEnabled;
    }

    public void setJumpCircleEnabled(boolean jumpCircleEnabled) {
        this.jumpCircleEnabled = jumpCircleEnabled;
    }

    public String getJumpCircleColor() {
        return jumpCircleColor;
    }

    public void setJumpCircleColor(String jumpCircleColor) {
        this.jumpCircleColor = jumpCircleColor;
    }

    public float getJumpCircleSize() {
        return jumpCircleSize;
    }

    public void setJumpCircleSize(float jumpCircleSize) {
        this.jumpCircleSize = jumpCircleSize;
    }

    public boolean isMediaPlayerEnabled() {
        return mediaPlayerEnabled;
    }

    public void setMediaPlayerEnabled(boolean mediaPlayerEnabled) {
        this.mediaPlayerEnabled = mediaPlayerEnabled;
    }

    public int getMediaPlayerX() {
        return mediaPlayerX;
    }

    public void setMediaPlayerX(int mediaPlayerX) {
        this.mediaPlayerX = mediaPlayerX;
    }

    public int getMediaPlayerY() {
        return mediaPlayerY;
    }

    public void setMediaPlayerY(int mediaPlayerY) {
        this.mediaPlayerY = mediaPlayerY;
    }

    public String getMediaPlayerCorner() {
        return mediaPlayerCorner;
    }

    public void setMediaPlayerCorner(String mediaPlayerCorner) {
        this.mediaPlayerCorner = mediaPlayerCorner;
    }

    public boolean isMediaPlayerAnimated() {
        return mediaPlayerAnimated;
    }

    public void setMediaPlayerAnimated(boolean mediaPlayerAnimated) {
        this.mediaPlayerAnimated = mediaPlayerAnimated;
    }

    public boolean isAspectEnabled() {
        return aspectEnabled;
    }

    public void setAspectEnabled(boolean aspectEnabled) {
        this.aspectEnabled = aspectEnabled;
    }

    public float getAspectStretch() {
        return aspectStretch;
    }

    public void setAspectStretch(float aspectStretch) {
        this.aspectStretch = aspectStretch;
    }

    public boolean isTargetEspEnabled() {
        return targetEspEnabled;
    }

    public void setTargetEspEnabled(boolean targetEspEnabled) {
        this.targetEspEnabled = targetEspEnabled;
    }

    public int getTargetEspTexture() {
        return targetEspTexture;
    }

    public void setTargetEspTexture(int targetEspTexture) {
        this.targetEspTexture = targetEspTexture;
    }

    public String getTargetEspChainColor() {
        return targetEspChainColor;
    }

    public void setTargetEspChainColor(String targetEspChainColor) {
        this.targetEspChainColor = targetEspChainColor;
    }

    public boolean isChinaHeadEnabled() {
        return chinaHeadEnabled;
    }

    public void setChinaHeadEnabled(boolean chinaHeadEnabled) {
        this.chinaHeadEnabled = chinaHeadEnabled;
    }

    public boolean isWorldParticlesEnabled() {
        return worldParticlesEnabled;
    }

    public void setWorldParticlesEnabled(boolean worldParticlesEnabled) {
        this.worldParticlesEnabled = worldParticlesEnabled;
    }

    public String getWorldParticlesColor() {
        return worldParticlesColor;
    }

    public void setWorldParticlesColor(String worldParticlesColor) {
        this.worldParticlesColor = worldParticlesColor;
    }

    public int getWorldParticlesCount() {
        return worldParticlesCount;
    }

    public void setWorldParticlesCount(int worldParticlesCount) {
        this.worldParticlesCount = worldParticlesCount;
    }

    public boolean isNoWeatherEnabled() {
        return noWeatherEnabled;
    }

    public void setNoWeatherEnabled(boolean noWeatherEnabled) {
        this.noWeatherEnabled = noWeatherEnabled;
    }

    public boolean isNoFireEnabled() {
        return noFireEnabled;
    }

    public void setNoFireEnabled(boolean noFireEnabled) {
        this.noFireEnabled = noFireEnabled;
    }

    public boolean isFullBrightEnabled() {
        return fullBrightEnabled;
    }

    public void setFullBrightEnabled(boolean fullBrightEnabled) {
        this.fullBrightEnabled = fullBrightEnabled;
    }

    public boolean isWorldChangerEnabled() {
        return worldChangerEnabled;
    }

    public void setWorldChangerEnabled(boolean worldChangerEnabled) {
        this.worldChangerEnabled = worldChangerEnabled;
    }

    public String getWorldChangerSkyColor() {
        return worldChangerSkyColor;
    }

    public void setWorldChangerSkyColor(String worldChangerSkyColor) {
        this.worldChangerSkyColor = worldChangerSkyColor;
    }

    public String getWorldChangerFogColor() {
        return worldChangerFogColor;
    }

    public void setWorldChangerFogColor(String worldChangerFogColor) {
        this.worldChangerFogColor = worldChangerFogColor;
    }

    public int getClickGuiTheme() {
        return clickGuiTheme;
    }

    public void setClickGuiTheme(int clickGuiTheme) {
        this.clickGuiTheme = clickGuiTheme;
    }

    public boolean isHealthTagsEnabled() {
        return healthTagsEnabled;
    }

    public void setHealthTagsEnabled(boolean healthTagsEnabled) {
        this.healthTagsEnabled = healthTagsEnabled;
    }

    public boolean isProtectNameEnabled() {
        return protectNameEnabled;
    }

    public void setProtectNameEnabled(boolean protectNameEnabled) {
        this.protectNameEnabled = protectNameEnabled;
    }

    public String getProtectName() {
        return protectName;
    }

    public void setProtectName(String protectName) {
        this.protectName = protectName;
    }

    public boolean isFakePrefixEnabled() {
        return fakePrefixEnabled;
    }

    public void setFakePrefixEnabled(boolean fakePrefixEnabled) {
        this.fakePrefixEnabled = fakePrefixEnabled;
    }

    public String getFakePrefix() {
        return fakePrefix;
    }

    public void setFakePrefix(String fakePrefix) {
        this.fakePrefix = fakePrefix;
    }

    public String getFakePrefixAdditionalNames() {
        return fakePrefixAdditionalNames;
    }

    public void setFakePrefixAdditionalNames(String fakePrefixAdditionalNames) {
        this.fakePrefixAdditionalNames = fakePrefixAdditionalNames;
    }

    public boolean isHitBoxEnabled() {
        return hitBoxEnabled;
    }

    public void setHitBoxEnabled(boolean hitBoxEnabled) {
        this.hitBoxEnabled = hitBoxEnabled;
    }

    public String getHitBoxColor() {
        return hitBoxColor;
    }

    public void setHitBoxColor(String hitBoxColor) {
        this.hitBoxColor = hitBoxColor;
    }

    public boolean isHitColorEnabled() {
        return hitColorEnabled;
    }

    public void setHitColorEnabled(boolean hitColorEnabled) {
        this.hitColorEnabled = hitColorEnabled;
    }

    public String getHitColorColor() {
        return hitColorColor;
    }

    public void setHitColorColor(String hitColorColor) {
        this.hitColorColor = hitColorColor;
    }

    public int getHitColorOpacity() {
        return hitColorOpacity;
    }

    public void setHitColorOpacity(int hitColorOpacity) {
        this.hitColorOpacity = hitColorOpacity;
    }

    public boolean isCrosshairEnabled() {
        return crosshairEnabled;
    }

    public void setCrosshairEnabled(boolean crosshairEnabled) {
        this.crosshairEnabled = crosshairEnabled;
    }

    public String getCrosshairColor() {
        return crosshairColor;
    }

    public void setCrosshairColor(String crosshairColor) {
        this.crosshairColor = crosshairColor;
    }

    public String getCrosshairPixels() {
        return crosshairPixels;
    }

    public void setCrosshairPixels(String crosshairPixels) {
        this.crosshairPixels = crosshairPixels;
    }

    public boolean isBetterNearEnabled() {
        return betterNearEnabled;
    }

    public void setBetterNearEnabled(boolean betterNearEnabled) {
        this.betterNearEnabled = betterNearEnabled;
    }

    public int getBetterNearRadius() {
        return betterNearRadius;
    }

    public void setBetterNearRadius(int betterNearRadius) {
        this.betterNearRadius = betterNearRadius;
    }

    public int getBetterNearX() {
        return betterNearX;
    }

    public void setBetterNearX(int betterNearX) {
        this.betterNearX = betterNearX;
    }

    public int getBetterNearY() {
        return betterNearY;
    }

    public void setBetterNearY(int betterNearY) {
        this.betterNearY = betterNearY;
    }

    public boolean isRainbowEnabled() {
        return rainbowEnabled;
    }

    public void setRainbowEnabled(boolean rainbowEnabled) {
        this.rainbowEnabled = rainbowEnabled;
    }

    public int getRainbowSpeed() {
        return rainbowSpeed;
    }

    public void setRainbowSpeed(int rainbowSpeed) {
        this.rainbowSpeed = rainbowSpeed;
    }

    public boolean isHitSoundEnabled() {
        return hitSoundEnabled;
    }

    public void setHitSoundEnabled(boolean hitSoundEnabled) {
        this.hitSoundEnabled = hitSoundEnabled;
    }

    public int getHitSoundIndex() {
        return hitSoundIndex;
    }

    public void setHitSoundIndex(int hitSoundIndex) {
        this.hitSoundIndex = hitSoundIndex;
    }

    public int getHitSoundVolume() {
        return hitSoundVolume;
    }

    public void setHitSoundVolume(int hitSoundVolume) {
        this.hitSoundVolume = hitSoundVolume;
    }

    public void sanitize() {
        hudScale = clamp(hudScale, 0.5F, 4.0F);
        clickGuiTheme = Math.max(0, Math.min(2, clickGuiTheme));

        if (accentColor == null || !accentColor.matches("^#[0-9a-fA-F]{6}$")) {
            accentColor = "#FF7C00";
        }

        if (discordApplicationId == null || discordApplicationId.isBlank()) {
            discordApplicationId = DEFAULT_DISCORD_APPLICATION_ID;
        }

        if (discordLargeImageKey == null || discordLargeImageKey.isBlank()) {
            discordLargeImageKey = "orion_icon";
        }

        if (discordLargeImageText == null || discordLargeImageText.isBlank()) {
            discordLargeImageText = "Orion Client";
        }

        if (jumpCircleColor == null || !jumpCircleColor.matches("^#[0-9a-fA-F]{6}$")) {
            jumpCircleColor = "#FF7C00";
        }

        jumpCircleSize = clamp(jumpCircleSize, 0.25F, 3.0F);

        mediaPlayerX = Math.max(0, mediaPlayerX);
        mediaPlayerY = Math.max(0, mediaPlayerY);
        if (!"TOP_LEFT".equals(mediaPlayerCorner) && !"TOP_RIGHT".equals(mediaPlayerCorner)) {
            mediaPlayerCorner = "TOP_LEFT";
        }
        aspectStretch = clamp(aspectStretch, 0.05F, 2.0F);
        targetEspTexture = Math.max(0, targetEspTexture);
        if (targetEspChainColor == null || !targetEspChainColor.matches("^#[0-9a-fA-F]{6}$")) {
            targetEspChainColor = "#F5F5FF";
        }

        if (worldParticlesColor == null || !worldParticlesColor.matches("^#[0-9a-fA-F]{6}$")) {
            worldParticlesColor = "#FF7C00";
        }

        worldParticlesCount = Math.max(1, Math.min(2500, worldParticlesCount));

        if (worldChangerSkyColor == null || !worldChangerSkyColor.matches("^#[0-9a-fA-F]{6}$")) {
            worldChangerSkyColor = "#64D9FF";
        }

        if (worldChangerFogColor == null || !worldChangerFogColor.matches("^#[0-9a-fA-F]{6}$")) {
            worldChangerFogColor = "#14111F";
        }

        if (hitBoxColor == null || !hitBoxColor.matches("^#[0-9a-fA-F]{6}$")) {
            hitBoxColor = "#FF7C00";
        }

        if (hitColorColor == null || !hitColorColor.matches("^#[0-9a-fA-F]{6}$")) {
            hitColorColor = "#EF4444";
        }
        hitColorOpacity = Math.max(5, Math.min(100, hitColorOpacity));

        if (crosshairColor == null || !crosshairColor.matches("^#[0-9a-fA-F]{6}$")) {
            crosshairColor = "#F5F5FF";
        }

        if (crosshairPixels == null || crosshairPixels.length() != CROSSHAIR_GRID * CROSSHAIR_GRID
                || !crosshairPixels.matches("^[01]+$")) {
            crosshairPixels = defaultCrosshairPixels();
        }

        betterNearRadius = Math.max(10, Math.min(200, betterNearRadius));
        betterNearX = Math.max(-1, betterNearX);
        betterNearY = Math.max(-1, betterNearY);
        rainbowSpeed = Math.max(1, Math.min(10, rainbowSpeed));
        hitSoundIndex = Math.max(0, Math.min(6, hitSoundIndex));
        hitSoundVolume = Math.max(0, Math.min(100, hitSoundVolume));

        if (protectName == null) {
            protectName = "Player";
        }
        protectName = protectName.trim();
        if (protectName.length() > 16) {
            protectName = protectName.substring(0, 16);
        }
        if (protectName.isEmpty()) {
            protectName = "Player";
        }

        if (fakePrefix == null || fakePrefix.isBlank()) {
            fakePrefix = "{\"text\":\"ORION \",\"color\":\"#FF7C00\",\"bold\":true}";
        } else if (fakePrefix.length() > 2048) {
            fakePrefix = fakePrefix.substring(0, 2048);
        }

        if (fakePrefixAdditionalNames == null) {
            fakePrefixAdditionalNames = "";
        } else if (fakePrefixAdditionalNames.length() > 512) {
            fakePrefixAdditionalNames = fakePrefixAdditionalNames.substring(0, 512);
        }
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return min;
        }

        return Math.max(min, Math.min(max, value));
    }
}
