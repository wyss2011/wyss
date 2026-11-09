package com.nonid.internal.animation.mixin;

import com.nonid.NonAnimationRescueSystem;
import com.nonid.internal.animation.server.ServerAnimationController;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
   @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
   private void afw$handleDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
      LivingEntity actor = (LivingEntity)(Object)this;
      MobEntity redirectTarget = NonAnimationRescueSystem.findDirectDamageRedirectTarget(world, actor, source);
      if (redirectTarget != null) {
         cir.setReturnValue(redirectTarget.damage(world, source, amount));
      } else {
         if (!ServerAnimationController.shouldAllowActorDamage(world, actor, source)) {
            cir.setReturnValue(false);
         }
      }
   }
}
