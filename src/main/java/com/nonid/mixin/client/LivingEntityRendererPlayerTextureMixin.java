package com.nonid.mixin.client;

import com.nonid.client.NonDebugSpinMode;
import com.nonid.client.NonDestroyedSkinClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererPlayerTextureMixin {
   @ModifyVariable(method = "getRenderLayer", at = @At("STORE"), ordinal = 0)
   private Identifier non$applyPlayerSkinEffectsToFinalTexture(
      Identifier texture, LivingEntityRenderState state, boolean showBody, boolean translucent, boolean showOutline
   ) {
      if (!NonDebugSpinMode.isEnabled() && state instanceof PlayerEntityRenderState playerState) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.world != null) {
            return client.world.getEntityById(playerState.id) instanceof LivingEntity living
               ? NonDestroyedSkinClient.resolveRenderedTexture(living, texture)
               : texture;
         } else {
            return texture;
         }
      } else {
         return texture;
      }
   }
}
