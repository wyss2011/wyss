package com.nonid.mixin;

import com.nonid.LiquidHolder;
import com.nonid.NeedsOfNature;
import com.nonid.NonTrinketsIntegration;
import com.nonid.effect.NonStatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityLiquidJumpMixin {
   @Inject(method = "jump", at = @At("TAIL"))
   private void needsOfNature$applyLiquidJumpSlowdown(CallbackInfo ci) {
      LivingEntity self = (LivingEntity)(Object)this;
      if (self instanceof PlayerEntity) {
         if (self instanceof LiquidHolder) {
            float multiplier = 1.0F;
            StatusEffectInstance effect = self.getStatusEffect(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.FILLED));
            if (effect != null) {
               int amplifier = effect.getAmplifier();
               float liquidMultiplier = amplifier >= 2
                  ? NeedsOfNature.getConfig().getFilledStageThreeJumpMult()
                  : (amplifier >= 1 ? NeedsOfNature.getConfig().getFilledStageTwoJumpMult() : NeedsOfNature.getConfig().getFilledStageOneJumpMult());
               liquidMultiplier = non$applyFilledAccessoryMultiplier(self, liquidMultiplier);
               multiplier *= liquidMultiplier;
            }

            StatusEffectInstance pregnancy = self.getStatusEffect(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.PREGNANT));
            if (pregnancy != null) {
               multiplier *= NeedsOfNature.getPregnancyMovementMultiplier(pregnancy.getDuration());
            }

            if (!(Math.abs(multiplier - 1.0F) < 1.0E-4F)) {
               Vec3d velocity = self.getVelocity();
               self.setVelocity(velocity.x * multiplier, velocity.y * multiplier, velocity.z * multiplier);
            }
         }
      }
   }

   private static float non$applyFilledAccessoryMultiplier(LivingEntity entity, float multiplier) {
      double effectMultiplier = NonTrinketsIntegration.getActiveTankAccessoryEffects(entity).filledEffectMultiplier();
      if (!Double.isFinite(effectMultiplier)) {
         return multiplier;
      }

      double penalty = 1.0 - multiplier;
      return (float)Math.max(0.0, Math.min(2.0, 1.0 - penalty * effectMultiplier));
   }
}
