package com.nonid.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.nonid.client.NonEnergizedEffectTextures;
import com.nonid.client.NonFilledEffectTextures;
import com.nonid.client.NonPregnantEffectTextures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.StatusEffectsDisplay;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(StatusEffectsDisplay.class)
public class StatusEffectsDisplayMixin {
   @Redirect(
      method = "drawStatusEffects",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"
      )
   )
   private void non$drawFilledEffectSprite(DrawContext context, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
      if (NonFilledEffectTextures.isFilledSprite(sprite)) {
         Identifier texture = NonFilledEffectTextures.resolveTexture(MinecraftClient.getInstance().player);
         context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, width, height, width, height);
      } else if (NonEnergizedEffectTextures.isEnergizedSprite(sprite)) {
         Identifier texture = NonEnergizedEffectTextures.resolveTexture(MinecraftClient.getInstance().player);
         context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, width, height, width, height);
      } else {
         if (NonPregnantEffectTextures.shouldDrawSourceEgg(sprite)) {
            ItemStack eggStack = NonPregnantEffectTextures.resolveSourceEgg(MinecraftClient.getInstance().player);
            if (!eggStack.isEmpty()) {
               NonPregnantEffectTextures.drawSourceEgg(context, eggStack, x, y, width, height);
               return;
            }
         }

         context.drawGuiTexture(pipeline, sprite, x, y, width, height);
      }
   }
}
