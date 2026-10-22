package ru.orion.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.effect.OrionFakePrefix;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Закрепляет локального игрока и дополнительные ники в начале TAB. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Shadow
    @Final
    private static Comparator<PlayerInfo> PLAYER_COMPARATOR;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getPlayerInfos", at = @At("HEAD"), cancellable = true)
    private void orion$prioritizeConfiguredPlayers(CallbackInfoReturnable<List<PlayerInfo>> callback) {
        List<String> prioritizedNames = OrionFakePrefix.prioritizedPlayerNames();
        if (prioritizedNames.isEmpty() || minecraft.player == null) {
            return;
        }

        Map<String, Integer> priorities = new HashMap<>();
        for (int index = 0; index < prioritizedNames.size(); index++) {
            priorities.putIfAbsent(prioritizedNames.get(index).toLowerCase(Locale.ROOT), index);
        }

        Comparator<PlayerInfo> comparator = Comparator
                .comparingInt((PlayerInfo info) -> priorities.getOrDefault(
                        info.getProfile().name().toLowerCase(Locale.ROOT), Integer.MAX_VALUE))
                .thenComparing(PLAYER_COMPARATOR);

        List<PlayerInfo> sorted = minecraft.player.connection.getListedOnlinePlayers().stream()
                .sorted(comparator)
                .limit(80L)
                .toList();
        callback.setReturnValue(sorted);
    }
}
