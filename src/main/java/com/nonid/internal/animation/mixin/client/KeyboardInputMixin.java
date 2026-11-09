package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
   @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   private void afw$lockKeyboardInput(CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null) {
         if (ClientAnimationRuntime.isActorPendingOrActive(client.player.getUuid())) {
            Input self = (Input)(Object)this;
            self.playerInput = new PlayerInput(false, false, false, false, false, false, false);
            if (self instanceof InputAccessor accessor) {
               accessor.afw$setMovementVector(Vec2f.ZERO);
            }

            ci.cancel();
         }
      }
   }
}
