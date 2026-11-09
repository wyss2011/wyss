package com.nonid.mixin;

import com.nonid.LiquidHolder;
import com.nonid.NeedsOfNature;
import com.nonid.NonTrinketsIntegration;
import com.nonid.effect.NonStatusEffects;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerLiquidMovementMixin {
   @Unique
   private static final Identifier LIQUID_SPEED_MOD = NeedsOfNature.id("liquid_speed");
   @Unique
   private static final Identifier PREGNANCY_SPEED_MOD = NeedsOfNature.id("pregnancy_speed");

   @Inject(method = "tickMovement", at = @At("TAIL"))
   private void needsOfNature$applyLiquidMovementSlowdown(CallbackInfo ci) {
      PlayerEntity player = (PlayerEntity)(Object)this;
      if (player instanceof LiquidHolder) {
         EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
         if (attribute != null) {
            StatusEffectInstance effect = player.getStatusEffect(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.FILLED));
            if (effect == null) {
               attribute.removeModifier(LIQUID_SPEED_MOD);
            } else {
               int amplifier = effect.getAmplifier();
               float multiplier = amplifier >= 2
                  ? NeedsOfNature.getConfig().getFilledStageThreeSpeedMult()
                  : (amplifier >= 1 ? NeedsOfNature.getConfig().getFilledStageTwoSpeedMult() : NeedsOfNature.getConfig().getFilledStageOneSpeedMult());
               multiplier = non$applyFilledAccessoryMultiplier(player, multiplier);
               double value = multiplier - 1.0;
               attribute.updateModifier(new EntityAttributeModifier(LIQUID_SPEED_MOD, value, Operation.ADD_MULTIPLIED_TOTAL));
            }

            if (!player.getEntityWorld().isClient()) {
               StatusEffectInstance pregnancy = player.getStatusEffect(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.PREGNANT));
               if (pregnancy == null) {
                  attribute.removeModifier(PREGNANCY_SPEED_MOD);
               } else {
                  float pregnancyMultiplier = NeedsOfNature.getPregnancyMovementMultiplier(pregnancy.getDuration());
                  attribute.updateModifier(new EntityAttributeModifier(PREGNANCY_SPEED_MOD, pregnancyMultiplier - 1.0, Operation.ADD_MULTIPLIED_TOTAL));
               }
            }
         }
      }
   }

   @Unique
   private static float non$applyFilledAccessoryMultiplier(PlayerEntity player, float multiplier) {
      double effectMultiplier = NonTrinketsIntegration.getActiveTankAccessoryEffects(player).filledEffectMultiplier();
      if (!Double.isFinite(effectMultiplier)) {
         return multiplier;
      }

      double penalty = 1.0 - multiplier;
      return (float)Math.max(0.0, Math.min(2.0, 1.0 - penalty * effectMultiplier));
   }
}
