package com.nonid.mixin.client;

import com.nonid.client.NonDebugNameHelper;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererDebugLabelMixin {
   @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
   private void needsOfNature$forceDebugLabel(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
      if (NonDebugNameHelper.shouldShowDebugLabel(entity)) {
         cir.setReturnValue(true);
      }
   }
}
