package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntityRenderer.class, priority = 1500)
public abstract class LivingEntityRendererAnimationNameTagMixin {
   @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
   private void afw$hideSameAnimationNameLabel(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
      if (entity != null) {
         UUID viewerUuid = afw$getLocalPlayerUuid();
         if (ClientAnimationRuntime.shouldHideNameLabelForViewer(viewerUuid, entity.getUuid())) {
            cir.setReturnValue(false);
         }
      }
   }

   private static UUID afw$getLocalPlayerUuid() {
      MinecraftClient client = MinecraftClient.getInstance();
      return client != null && client.player != null ? client.player.getUuid() : null;
   }
}
