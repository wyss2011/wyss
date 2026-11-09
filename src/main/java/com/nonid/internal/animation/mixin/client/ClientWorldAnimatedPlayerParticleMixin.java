package com.nonid.internal.animation.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class ClientWorldAnimatedPlayerParticleMixin {
   @Unique
   private int afw$animatedPlayerParticleSuppressionDepth;

   @WrapOperation(method = "tickEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V"))
   private void afw$trackAnimatedPlayerTick(Entity entity, Operation<Void> original) {
      if (entity instanceof PlayerEntity && ClientAnimationRuntime.isActorActive(entity.getUuid())) {
         this.afw$animatedPlayerParticleSuppressionDepth++;

         try {
            original.call(new Object[]{entity});
         } finally {
            this.afw$animatedPlayerParticleSuppressionDepth--;
         }
      } else {
         original.call(new Object[]{entity});
      }
   }

   @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;ZZDDDDDD)V", at = @At("HEAD"), cancellable = true)
   private void afw$suppressAnimatedPlayerParticles(
      ParticleEffect particle,
      boolean force,
      boolean important,
      double x,
      double y,
      double z,
      double velocityX,
      double velocityY,
      double velocityZ,
      CallbackInfo ci
   ) {
      if (this.afw$animatedPlayerParticleSuppressionDepth > 0) {
         ci.cancel();
      }
   }
}
