package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.camera.AnimationCameraZoom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseScrollAnimationZoomMixin {
   @Shadow
   @Final
   private MinecraftClient client;

   @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
   private void afw$zoomAnimationCamera(long window, double horizontal, double vertical, CallbackInfo ci) {
      if (AnimationCameraZoom.handleScroll(this.client, vertical)) {
         ci.cancel();
      }
   }
}
