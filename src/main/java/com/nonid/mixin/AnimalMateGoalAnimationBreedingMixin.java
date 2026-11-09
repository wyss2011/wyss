package com.nonid.mixin;

import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import net.minecraft.entity.ai.goal.AnimalMateGoal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnimalMateGoal.class)
public abstract class AnimalMateGoalAnimationBreedingMixin {
   @Final
   @Shadow
   protected AnimalEntity animal;
   @Final
   @Shadow
   protected ServerWorld world;
   @Shadow
   @Nullable
   protected AnimalEntity mate;

   @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   private void non$startAnimationBreedingWhenClose(CallbackInfo ci) {
      NonConfig config = NeedsOfNature.getConfig();
      if (config != null && config.useAnimationBreeding()) {
         AnimalEntity other = this.mate;
         if (other != null && other.isAlive() && !other.isRemoved()) {
            if (this.animal.isInLove() && other.isInLove()) {
               if (!(this.animal.squaredDistanceTo(other) >= 9.0)) {
                  if (NeedsOfNature.handleAnimationBreedingAttempt(this.world, this.animal, other)) {
                     ci.cancel();
                  }
               }
            }
         }
      }
   }
}
