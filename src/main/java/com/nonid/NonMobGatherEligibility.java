package com.nonid;

import com.nonid.entity.HorseLiquidCollectorEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.CamelEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.TameableEntity;

public final class NonMobGatherEligibility {
   private NonMobGatherEligibility() {
   }

   public static boolean canInitiateAutoGather(MobEntity mob) {
      if (mob == null || !mob.isAlive() || mob.isRemoved()) {
         return false;
      }

      if (!mob.isAiDisabled() && !mob.isSleeping()) {
         if (mob instanceof TameableEntity tameable && (tameable.isSitting() || tameable.isInSittingPose())) {
            return false;
         } else {
            return mob instanceof FoxEntity fox && (fox.isSitting() || fox.isSleeping()) ? false : !(mob instanceof CamelEntity camel && camel.isSitting());
         }
      } else {
         return false;
      }
   }

   public static boolean canBeAutoGatherTarget(MobEntity mob) {
      if (mob == null || !mob.isAlive() || mob.isRemoved()) {
         return false;
      } else {
         return mob instanceof HorseLiquidCollectorEntity ? true : canInitiateAutoGather(mob);
      }
   }
}
