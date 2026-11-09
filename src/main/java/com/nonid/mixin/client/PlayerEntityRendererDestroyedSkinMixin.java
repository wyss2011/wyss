package com.nonid.mixin.client;

import com.nonid.client.NonDebugSpinMode;
import com.nonid.client.NonDestroyedSkinClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererDestroyedSkinMixin {
   @ModifyArg(
      method = "renderRightArm",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/entity/PlayerEntityRenderer;renderArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;Lnet/minecraft/client/model/ModelPart;Z)V"
      ),
      index = 3
   )
   private Identifier non$applyDestroyedSkinToRightArm(Identifier skinTexture) {
      return non$resolveLocalHandTexture(skinTexture);
   }

   @ModifyArg(
      method = "renderLeftArm",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/entity/PlayerEntityRenderer;renderArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;Lnet/minecraft/client/model/ModelPart;Z)V"
      ),
      index = 3
   )
   private Identifier non$applyDestroyedSkinToLeftArm(Identifier skinTexture) {
      return non$resolveLocalHandTexture(skinTexture);
   }

   private static Identifier non$resolveLocalHandTexture(Identifier skinTexture) {
      if (NonDebugSpinMode.isEnabled()) {
         return skinTexture;
      } else {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null) {
            Identifier replacement = NonDestroyedSkinClient.resolveBaseTexture(client.player, skinTexture);
            return replacement != null ? replacement : skinTexture;
         } else {
            return skinTexture;
         }
      }
   }
}
