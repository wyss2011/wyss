package com.nonid.mixin.client;

import com.nonid.NeedsOfNatureClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
public class GameRendererReactiveImpactFovMixin {
   @ModifyArg(
      method = "renderWorld",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;getBasicProjectionMatrix(F)Lorg/joml/Matrix4f;", ordinal = 0),
      index = 0
   )
   private float non$applyReactiveImpactWorldFov(float fov) {
      float offset = NeedsOfNatureClient.getReactiveImpactFovOffset(0.0F);
      return Math.abs(offset) <= 1.0E-4F ? fov : Math.max(5.0F, fov + offset);
   }
}
