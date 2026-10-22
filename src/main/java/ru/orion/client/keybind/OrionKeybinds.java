package ru.orion.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.gui.OrionClickGuiScreen;

public final class OrionKeybinds {
    private static final String OPEN_CLICK_GUI_TRANSLATION_KEY = "key.orion-client.open_click_gui";
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "orion_client")
    );

    private final OrionConfigManager configManager;
    private KeyMapping openClickGuiKey;

    public OrionKeybinds(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        openClickGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                OPEN_CLICK_GUI_TRANSLATION_KEY,
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_RSHIFT,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::handleClientTick);
    }

    private void handleClientTick(Minecraft client) {
        while (openClickGuiKey.consumeClick()) {
            client.gui.setScreen(new OrionClickGuiScreen(configManager));
        }
    }
}
