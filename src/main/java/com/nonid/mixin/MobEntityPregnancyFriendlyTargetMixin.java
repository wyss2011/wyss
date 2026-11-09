package com.nonid.mixin;

import com.nonid.NeedsOfNature;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntityPregnancyFriendlyTargetMixin {
   @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
   private void non$blockPlayerTargetingForPregnancyFriendly(LivingEntity target, CallbackInfo ci) {
      if (target instanceof PlayerEntity) {
         MobEntity mob = (MobEntity)(Object)this;
         if (NeedsOfNature.isPregnancyFriendlyMob(mob)) {
            ci.cancel();
         }
      }
   }

   @Inject(method = "mobTick", at = @At("HEAD"))
   private void non$clearPlayerTargetForPregnancyFriendly(CallbackInfo ci) {
      MobEntity mob = (MobEntity)(Object)this;
      if (NeedsOfNature.isPregnancyFriendlyMob(mob)) {
         if (mob.getTarget() instanceof PlayerEntity) {
            mob.setTarget(null);
            if (mob instanceof Angerable angerable) {
               angerable.stopAnger();
            }
         }
      }
   }
}
