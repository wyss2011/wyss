package com.nonid.mixin;

import com.nonid.NonPlayerAvoidanceState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FleeEntityGoal.class)
public abstract class FleeEntityGoalMixin {
   @Shadow
   @Final
   protected PathAwareEntity mob;
   @Shadow
   protected LivingEntity targetEntity;

   @Inject(method = "canStart", at = @At("RETURN"), cancellable = true)
   private void non$suppressPlayerAvoidanceStart(CallbackInfoReturnable<Boolean> cir) {
      if (cir.getReturnValueZ() && this.non$shouldSuppressPlayerAvoidance()) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "shouldContinue", at = @At("RETURN"), cancellable = true)
   private void non$suppressPlayerAvoidanceContinuation(CallbackInfoReturnable<Boolean> cir) {
      if (cir.getReturnValueZ() && this.non$shouldSuppressPlayerAvoidance()) {
         this.mob.getNavigation().stop();
         cir.setReturnValue(false);
      }
   }

   @Unique
   private boolean non$shouldSuppressPlayerAvoidance() {
      return this.targetEntity instanceof ServerPlayerEntity player
         && this.mob instanceof NonPlayerAvoidanceState state
         && state.shouldSuppressPlayerAvoidance(player);
   }
}
