package com.nonid.mixin;

import com.nonid.NeedsOfNature;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityDamageMixin {
   @Redirect(
      method = "damage",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;shouldDamagePlayer(Lnet/minecraft/entity/player/PlayerEntity;)Z")
   )
   private boolean non$allowDefeatedFinishDamage(ServerPlayerEntity victim, PlayerEntity attacker) {
      return NeedsOfNature.shouldBypassVanillaPvpForDefeatedFinish(victim, attacker) ? true : victim.shouldDamagePlayer(attacker);
   }
}
