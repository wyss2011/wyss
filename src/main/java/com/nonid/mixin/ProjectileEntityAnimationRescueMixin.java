package com.nonid.mixin;

import com.nonid.NonAnimationRescueSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityAnimationRescueMixin {
   @ModifyVariable(method = "hitOrDeflect", at = @At("HEAD"), argsOnly = true)
   private HitResult non$redirectAnimatedPlayerHit(HitResult hitResult) {
      ProjectileEntity projectile = (ProjectileEntity)(Object)this;
      if (projectile.getEntityWorld() instanceof ServerWorld world && hitResult instanceof EntityHitResult entityHit) {
         Entity var7 = entityHit.getEntity();
         Entity redirectedTarget = NonAnimationRescueSystem.redirectProjectileTarget(world, projectile, var7);
         return (HitResult)(redirectedTarget == var7 ? hitResult : new EntityHitResult(redirectedTarget, entityHit.getPos()));
      } else {
         return hitResult;
      }
   }
}
