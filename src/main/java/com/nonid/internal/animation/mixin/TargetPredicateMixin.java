package com.nonid.internal.animation.mixin;

import com.nonid.internal.animation.server.ServerAnimationController;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TargetPredicate.class)
public abstract class TargetPredicateMixin {
   @Inject(method = "test", at = @At("HEAD"), cancellable = true)
   private void afw$ignoreAnimationActors(ServerWorld world, LivingEntity tester, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
      if (ServerAnimationController.shouldIgnoreAttackers(world, target)) {
         cir.setReturnValue(false);
      }
   }
}
