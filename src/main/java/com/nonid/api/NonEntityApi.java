package com.nonid.api;

import com.nonid.EnergyHolder;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class NonEntityApi {
   private NonEntityApi() {
   }

   public static boolean hasEnergy(LivingEntity entity) {
      return entity instanceof EnergyHolder;
   }

   @Nullable
   public static NonEnergySnapshot getEnergy(LivingEntity entity) {
      return entity instanceof EnergyHolder holder
         ? new NonEnergySnapshot(
            holder.getEnergy(), holder.getMaxEnergy(), holder.getEnergyGainMultiplier(), holder.getEnergyGainDrift(), holder.isEnergyInitialized()
         )
         : null;
   }

   public static boolean setEnergy(LivingEntity entity, int energy) {
      if (entity instanceof EnergyHolder holder && !entity.getEntityWorld().isClient()) {
         int before = holder.getEnergy();
         holder.setEnergy(energy);
         return holder.getEnergy() != before;
      } else {
         return false;
      }
   }

   public static int addEnergy(LivingEntity entity, int amount) {
      if (entity instanceof EnergyHolder holder && !entity.getEntityWorld().isClient() && amount != 0) {
         int before = holder.getEnergy();
         long requested = (long)before + amount;
         int safeRequested = requested > 2147483647L ? Integer.MAX_VALUE : (requested < -2147483648L ? Integer.MIN_VALUE : (int)requested);
         holder.setEnergy(safeRequested);
         return holder.getEnergy() - before;
      } else {
         return 0;
      }
   }
}
