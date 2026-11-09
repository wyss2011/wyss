package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.render.gecko.ParticleKeyframes;
import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityAnimationParticlePositionMixin {
   @Inject(method = "getBodyX", at = @At("RETURN"), cancellable = true)
   private void afw$moveBodyParticleX(double widthScale, CallbackInfoReturnable<Double> cir) {
      Vec3d offset = this.afw$getAnimatedParticleOffset();
      if (offset != null) {
         cir.setReturnValue((Double)cir.getReturnValue() + offset.x);
      }
   }

   @Inject(method = "getBodyY", at = @At("RETURN"), cancellable = true)
   private void afw$moveBodyParticleY(double heightScale, CallbackInfoReturnable<Double> cir) {
      Vec3d offset = this.afw$getAnimatedParticleOffset();
      if (offset != null) {
         cir.setReturnValue((Double)cir.getReturnValue() + offset.y);
      }
   }

   @Inject(method = "getBodyZ", at = @At("RETURN"), cancellable = true)
   private void afw$moveBodyParticleZ(double widthScale, CallbackInfoReturnable<Double> cir) {
      Vec3d offset = this.afw$getAnimatedParticleOffset();
      if (offset != null) {
         cir.setReturnValue((Double)cir.getReturnValue() + offset.z);
      }
   }

   private Vec3d afw$getAnimatedParticleOffset() {
      Entity entity = (Entity)(Object)this;
      UUID actorUuid = entity.getUuid();
      if (!ClientAnimationRuntime.isActorActive(actorUuid)) {
         return null;
      }

      Vec3d animatedOrigin = ParticleKeyframes.findActorOrigin(actorUuid);
      return animatedOrigin == null ? null : animatedOrigin.subtract(entity.getEntityPos());
   }
}
