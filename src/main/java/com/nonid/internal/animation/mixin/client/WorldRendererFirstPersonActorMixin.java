package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldRenderer.class)
public class WorldRendererFirstPersonActorMixin {
   @Redirect(method = "fillEntityRenderStates", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;isThirdPerson()Z"))
   private boolean afw$keepFocusedActorRenderableInFirstPerson(Camera camera) {
      boolean thirdPerson = camera.isThirdPerson();
      if (thirdPerson) {
         return true;
      }

      if (camera.getFocusedEntity() instanceof PlayerEntity player) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null) {
            return !player.getUuid().equals(client.player.getUuid()) ? false : ClientAnimationRuntime.isActorPendingOrActive(player.getUuid());
         } else {
            return false;
         }
      } else {
         return false;
      }
   }
}
