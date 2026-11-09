package com.nonid.mixin;

import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnimalEntity.class)
public abstract class AnimalEntityBreedingMixin {
   @Inject(method = "canBreedWith", at = @At("HEAD"), cancellable = true)
   private void non$skipInvalidGenderPairWhenSelectingMate(AnimalEntity other, CallbackInfoReturnable<Boolean> cir) {
      if (other != null) {
         NonConfig config = NeedsOfNature.getConfig();
         if (config != null && config.useAnimationBreeding() && config.requireMaleFemaleForBreeding()) {
            AnimalEntity self = (AnimalEntity)(Object)this;
            boolean selfMale = self.getCommandTags().contains("gender.male");
            boolean selfFemale = self.getCommandTags().contains("gender.female");
            boolean otherMale = other.getCommandTags().contains("gender.male");
            boolean otherFemale = other.getCommandTags().contains("gender.female");
            boolean validPair = selfMale && otherFemale || selfFemale && otherMale;
            if (!validPair) {
               cir.setReturnValue(false);
            }
         }
      }
   }

   @Inject(method = "breed(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;)V", at = @At("HEAD"), cancellable = true)
   private void non$overrideVanillaBreeding(ServerWorld world, AnimalEntity other, CallbackInfo ci) {
      AnimalEntity self = (AnimalEntity)(Object)this;
      if (NeedsOfNature.handleAnimationBreedingAttempt(world, self, other)) {
         ci.cancel();
      }
   }
}
