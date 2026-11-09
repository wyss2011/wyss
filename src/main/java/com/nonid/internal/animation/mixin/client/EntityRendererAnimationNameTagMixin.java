package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityRenderer.class, priority = 1500)
public abstract class EntityRendererAnimationNameTagMixin {
   @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
   private void afw$hideSameAnimationNameLabel(Entity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
      if (entity != null) {
         UUID viewerUuid = afw$getLocalPlayerUuid();
         if (ClientAnimationRuntime.shouldHideNameLabelForViewer(viewerUuid, entity.getUuid())) {
            cir.setReturnValue(false);
         }
      }
   }

   @Inject(method = "updateRenderState", at = @At("TAIL"))
   private void afw$offsetOutsideViewerAnimationNameLabel(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
      if (entity != null && state != null && state.nameLabelPos != null) {
         UUID viewerUuid = afw$getLocalPlayerUuid();
         double yOffset = ClientAnimationRuntime.findOutsideViewerNameLabelYOffset(viewerUuid, entity.getUuid());
         if (!(yOffset <= 0.0)) {
            state.nameLabelPos = state.nameLabelPos.add(0.0, yOffset, 0.0);
         }
      }
   }

   private static UUID afw$getLocalPlayerUuid() {
      MinecraftClient client = MinecraftClient.getInstance();
      return client != null && client.player != null ? client.player.getUuid() : null;
   }
}
