package com.nonid.effect;

import com.nonid.EnergyHolder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.world.ServerWorld;

public class EnergyStatusEffect extends StatusEffect {
   private static final double TOTAL_RATIO = 0.5;
   private final Map<UUID, EnergyStatusEffect.ApplicationState> applications = new HashMap<>();

   public EnergyStatusEffect() {
      super(StatusEffectCategory.HARMFUL, 10494192);
   }

   public boolean canApplyUpdateEffect(int duration, int amplifier) {
      return true;
   }

   public boolean applyUpdateEffect(ServerWorld world, LivingEntity entity, int amplifier) {
      if (!(entity instanceof EnergyHolder holder)) {
         return true;
      } else {
         StatusEffectInstance instance = null;

         for (StatusEffectInstance effect : entity.getStatusEffects()) {
            if (effect.getEffectType().value() == this) {
               instance = effect;
               break;
            }
         }

         if (instance == null) {
            return true;
         }

         UUID id = entity.getUuid();
         int duration = instance.getDuration();
         EnergyStatusEffect.ApplicationState state = this.applications.get(id);
         if (state == null || state.instance != instance) {
            state = new EnergyStatusEffect.ApplicationState(instance, duration);
            this.applications.put(id, state);
         } else if (duration > state.initialDuration) {
            state.initialDuration = duration;
         }

         int max = holder.getMaxEnergy();
         if (max > 0) {
            double total = max * 0.5 * (amplifier + 1);
            double perTick = total / Math.max(1, state.initialDuration);
            double accum = state.remainder + perTick;
            int add = (int)Math.floor(accum);
            state.remainder = accum - add;
            if (add > 0) {
               holder.setEnergy(Math.min(max, holder.getEnergy() + add));
            }
         }

         if (duration <= 1) {
            this.applications.remove(id);
         }

         return true;
      }
   }

   public void clearRuntimeState() {
      this.applications.clear();
   }

   private static final class ApplicationState {
      private final StatusEffectInstance instance;
      private int initialDuration;
      private double remainder;

      private ApplicationState(StatusEffectInstance instance, int initialDuration) {
         this.instance = instance;
         this.initialDuration = initialDuration;
      }
   }
}
