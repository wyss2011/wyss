package com.nonid.internal.animation.mixin;

import com.nonid.internal.animation.server.ServerAnimationController;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class MobEntityIgnoreAttackersMixin {
   @Inject(method = "tryAttack", at = @At("HEAD"), cancellable = true)
   private void afw$blockDirectAttackOnIgnoredActors(ServerWorld world, Entity target, CallbackInfoReturnable<Boolean> cir) {
      if (target instanceof LivingEntity livingTarget) {
         if (ServerAnimationController.shouldIgnoreAttackers(world, livingTarget)) {
            cir.setReturnValue(false);
         }
      }
   }
}
