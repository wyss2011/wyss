package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityJumpMixin {
   @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
   private void afw$blockJumpWhileAnimating(CallbackInfo ci) {
      LivingEntity self = (LivingEntity)(Object)this;
      if (self instanceof ClientPlayerEntity player && ClientAnimationRuntime.isActorPendingOrActive(player.getUuid())) {
         ci.cancel();
      }
   }
}
