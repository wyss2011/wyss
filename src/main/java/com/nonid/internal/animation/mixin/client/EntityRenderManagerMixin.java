package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.render.AnimationRenderStateAccess;
import com.nonid.internal.animation.client.render.gecko.GeckoReplacedRender;
import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import java.util.UUID;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public class EntityRenderManagerMixin {
   @Inject(method = "getAndUpdateRenderState", at = @At("RETURN"))
   private void afw$attachIdentity(Entity entity, float tickDelta, CallbackInfoReturnable<EntityRenderState> cir) {
      EntityRenderState state = (EntityRenderState)cir.getReturnValue();
      if (state instanceof AnimationRenderStateAccess access) {
         access.afw$setEntityUuid(entity.getUuid());
         access.afw$setEntityTypeId(Registries.ENTITY_TYPE.getId(entity.getType()));
      }

      if (state instanceof LivingEntityRenderState livingState) {
         if (entity instanceof LivingEntity living) {
            if (ClientAnimationRuntime.hasActiveInstances()) {
               if (ClientAnimationRuntime.isActorActive(entity.getUuid())) {
                  UUID actorUuid = entity.getUuid();
                  UUID anchorUuid = ClientAnimationRuntime.findAnchorUuidForActor(actorUuid);
                  boolean keepShadow = anchorUuid == null || anchorUuid.equals(actorUuid);
                  if (!keepShadow) {
                     state.shadowRadius = 0.0F;
                     state.shadowPieces.clear();
                  }

                  GeckoReplacedRender.prepare(living, livingState, tickDelta);
               }
            }
         }
      }
   }

   @Redirect(
      method = "render",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"
      )
   )
   private void afw$swapRenderer(
      EntityRenderer<?, ?> vanillaRenderer, EntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera
   ) {
      if (state instanceof LivingEntityRenderState livingState && ClientAnimationRuntime.hasActiveInstances()) {
         AnimationRenderStateAccess access = (AnimationRenderStateAccess)state;
         UUID uuid = access.afw$getEntityUuid();
         if (uuid != null && ClientAnimationRuntime.isActorActive(uuid) && GeckoReplacedRender.render(livingState, matrices, queue, camera)) {
            state.onFire = false;
            return;
         }
      }

      ((EntityRendererInvoker)vanillaRenderer).afw$render(state, matrices, queue, camera);
   }
}
