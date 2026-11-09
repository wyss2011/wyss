package com.nonid;

import java.util.List;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public interface EnergyHolder {
   int getEnergy();

   int getMaxEnergy();

   void setEnergy(int var1);

   default float getEnergyGainMultiplier() {
      return 1.0F;
   }

   default void setEnergyGainMultiplier(float value) {
   }

   default float getEnergyGainDrift() {
      return 1.0F;
   }

   default void setEnergyGainDrift(float value) {
   }

   default boolean isEnergyInitialized() {
      return false;
   }

   default void markEnergyInitialized() {
   }

   default void ensureEnergyInitialized(ServerWorld world) {
   }

   default void onNonAnimationStarted() {
   }

   default void onNonAttackEscaped(LivingEntity player, int retryDelayTicks) {
   }

   default void requestAttackOnPlayer(ServerWorld world, ServerPlayerEntity player) {
   }

   default void applyPartnerCooldown(UUID partnerId, long nowTick, int cooldownTicks) {
   }

   default List<String> getActiveCooldownDebugLines(ServerWorld world) {
      return List.of();
   }

   default double getEnergyAuraCarry() {
      return 0.0;
   }

   default void setEnergyAuraCarry(double value) {
   }
}
