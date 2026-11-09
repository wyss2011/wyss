package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.render.AnimationRenderStateAccess;
import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState> {
   @Inject(
      method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
      at = @At("TAIL")
   )
   private void afw$attachUuidAndType(T entity, S state, float tickDelta, CallbackInfo ci) {
      AnimationRenderStateAccess access = (AnimationRenderStateAccess)state;
      access.afw$setEntityUuid(entity.getUuid());
      access.afw$setEntityTypeId(Registries.ENTITY_TYPE.getId(entity.getType()));
      if (ClientAnimationRuntime.hasActiveInstances()) {
         ClientAnimationRuntime.LockedOrientation locked = ClientAnimationRuntime.getLockedOrientation(entity.getUuid());
         if (locked != null) {
            state.bodyYaw = locked.bodyYaw();
            state.relativeHeadYaw = MathHelper.wrapDegrees(locked.headYaw() - locked.bodyYaw());
            state.pitch = locked.pitch();
         }
      }
   }
}
