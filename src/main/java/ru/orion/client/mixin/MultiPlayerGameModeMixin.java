package ru.orion.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionHitColorFlash;
import ru.orion.client.effect.OrionHitSound;
import ru.orion.client.effect.OrionTargetEsp;

@Mixin(MultiPlayerGameMode.class)
public final class MultiPlayerGameModeMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void orion$recordTargetEspHit(Player player, Entity target, CallbackInfo ci) {
        if (target == null) {
            return;
        }

        OrionTargetEsp targetEsp = OrionClient.getTargetEspOrNull();
        if (targetEsp != null) {
            targetEsp.recordHit(target);
        }

        OrionHitColorFlash hitColorFlash = OrionClient.getHitColorFlashOrNull();
        if (hitColorFlash != null) {
            hitColorFlash.recordHit(target);
        }

        OrionHitSound hitSound = OrionClient.getHitSoundOrNull();
        if (hitSound != null) {
            hitSound.onHit(target);
        }
    }
}
