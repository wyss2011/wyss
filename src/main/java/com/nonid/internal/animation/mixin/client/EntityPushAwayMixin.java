package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityPushAwayMixin {
   @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
   private void afw$disablePushAwayDuringAnimation(Entity other, CallbackInfo ci) {
      if (other != null) {
         Entity self = (Entity)(Object)this;
         if (self.getEntityWorld().isClient()) {
            if (ClientAnimationRuntime.isActorPendingOrActive(self.getUuid()) || ClientAnimationRuntime.isActorPendingOrActive(other.getUuid())) {
               ci.cancel();
            }
         }
      }
   }
}
