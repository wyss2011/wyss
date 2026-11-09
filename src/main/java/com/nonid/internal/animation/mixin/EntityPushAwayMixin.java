package com.nonid.internal.animation.mixin;

import com.nonid.internal.animation.server.ServerAnimationController;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
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
         if (self.getEntityWorld() instanceof ServerWorld world) {
            if (ServerAnimationController.isActorPendingOrActive(world, self.getUuid())
               || ServerAnimationController.isActorPendingOrActive(world, other.getUuid())) {
               ci.cancel();
            }
         }
      }
   }
}
