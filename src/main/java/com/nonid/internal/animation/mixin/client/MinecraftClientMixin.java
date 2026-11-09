package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
   @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
   private void afw$blockHandledScreens(Screen screen, CallbackInfo ci) {
      if (screen instanceof HandledScreen) {
         MinecraftClient client = (MinecraftClient)(Object)this;
         if (client.player != null) {
            if (ClientAnimationRuntime.isActorPendingOrActive(client.player.getUuid())) {
               ci.cancel();
            }
         }
      }
   }
}
