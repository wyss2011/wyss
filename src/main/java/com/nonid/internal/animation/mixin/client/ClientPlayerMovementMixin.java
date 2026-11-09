package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerMovementMixin {
   @Inject(method = "tickMovementInput", at = @At("HEAD"), cancellable = true)
   private void afw$lockMovementWhileAnimating(CallbackInfo ci) {
      ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
      if (ClientAnimationRuntime.isActorPendingOrActive(self.getUuid())) {
         self.sidewaysSpeed = 0.0F;
         self.forwardSpeed = 0.0F;
         self.setJumping(false);
         self.setSprinting(false);
         Input input = self.input;
         if (input != null) {
            input.playerInput = new PlayerInput(false, false, false, false, false, false, false);
            if (input instanceof InputAccessor accessor) {
               accessor.afw$setMovementVector(Vec2f.ZERO);
            }
         }

         ci.cancel();
      }
   }
}
