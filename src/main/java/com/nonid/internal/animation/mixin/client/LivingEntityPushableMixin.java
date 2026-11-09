package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityPushableMixin {
   @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
   private void afw$disablePushDuringAnimation(CallbackInfoReturnable<Boolean> cir) {
      LivingEntity self = (LivingEntity)(Object)this;
      if (self.getEntityWorld().isClient()) {
         if (ClientAnimationRuntime.isActorPendingOrActive(self.getUuid())) {
            cir.setReturnValue(false);
         }
      }
   }
}
