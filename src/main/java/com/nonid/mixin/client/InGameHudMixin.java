package com.nonid.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.nonid.client.NonEnergizedEffectTextures;
import com.nonid.client.NonFilledEffectTextures;
import com.nonid.client.NonHudOverlay;
import com.nonid.client.NonPregnantEffectTextures;
import com.nonid.effect.NonStatusEffects;
import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
   @Inject(method = "render", at = @At("TAIL"))
   private void non$renderLiquidOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NonHudOverlay.isUiPreviewEnabled()) {
         NonHudOverlay.renderUiPreview(context);
      } else if (NonHudOverlay.shouldHideVanillaHud()) {
         NonHudOverlay.renderOverlay(context);
      } else {
         NonHudOverlay.renderLiquidOverlay(context);
      }
   }

   @Inject(method = "renderMiscOverlays", at = @At("HEAD"), cancellable = true)
   private void non$hideMiscOverlaysDuringAnimation(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NonHudOverlay.shouldHideVanillaHud()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
   private void non$hideCrosshairDuringAnimation(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NonHudOverlay.shouldHideVanillaHud()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderMainHud", at = @At("HEAD"), cancellable = true)
   private void non$hideMainHudDuringAnimation(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NonHudOverlay.shouldHideVanillaHud()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
   private void non$hideStatusEffectsDuringAnimation(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NonHudOverlay.shouldHideVanillaHud()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderBossBarHud", at = @At("HEAD"), cancellable = true)
   private void non$hideBossBarsDuringAnimation(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NonHudOverlay.shouldHideVanillaHud()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderSleepOverlay", at = @At("HEAD"), cancellable = true)
   private void non$hideSleepOverlayDuringAnimation(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NonHudOverlay.shouldHideVanillaHud()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderDemoTimer", at = @At("HEAD"), cancellable = true)
   private void non$hideDemoTimerDuringAnimation(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NonHudOverlay.shouldHideVanillaHud()) {
         ci.cancel();
      }
   }

   @Inject(
      method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void non$hideScoreboardDuringAnimation(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NonHudOverlay.shouldHideVanillaHud()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
   private void non$hideOverlayMessageDuringAnimation(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NonHudOverlay.shouldHideVanillaHud()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
   private void non$hideTitleDuringAnimation(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NonHudOverlay.shouldHideVanillaHud()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderChat", at = @At("HEAD"), cancellable = true)
   private void non$hideChatDuringAnimation(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NonHudOverlay.shouldHideVanillaHud()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderPlayerList", at = @At("HEAD"), cancellable = true)
   private void non$hidePlayerListDuringAnimation(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NonHudOverlay.shouldHideVanillaHud()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderSubtitlesHud", at = @At("HEAD"), cancellable = true)
   private void non$hideSubtitlesDuringAnimation(DrawContext context, boolean defer, CallbackInfo ci) {
      if (NonHudOverlay.shouldHideVanillaHud()) {
         ci.cancel();
      }
   }

   @Redirect(
      method = "renderStatusEffectOverlay",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getStatusEffects()Ljava/util/Collection;")
   )
   private Collection<StatusEffectInstance> non$hideEnergizedFromHudOverlay(ClientPlayerEntity player) {
      Collection<StatusEffectInstance> effects = player.getStatusEffects();
      if (effects.isEmpty()) {
         return effects;
      }

      ArrayList<StatusEffectInstance> filtered = new ArrayList<>(effects.size());

      for (StatusEffectInstance effect : effects) {
         if (effect.getEffectType().value() != NonStatusEffects.ENERGIZED) {
            filtered.add(effect);
         }
      }

      return filtered;
   }

   @Redirect(
      method = "renderStatusEffectOverlay",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V"
      )
   )
   private void non$drawFilledStatusEffectIcon(DrawContext context, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, int color) {
      if (NonFilledEffectTextures.isFilledSprite(sprite)) {
         Identifier texture = NonFilledEffectTextures.resolveTexture(MinecraftClient.getInstance().player);
         context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, width, height, width, height, color);
      } else if (NonEnergizedEffectTextures.isEnergizedSprite(sprite)) {
         Identifier texture = NonEnergizedEffectTextures.resolveTexture(MinecraftClient.getInstance().player);
         context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, width, height, width, height, color);
      } else {
         if (NonPregnantEffectTextures.shouldDrawSourceEgg(sprite)) {
            ItemStack eggStack = NonPregnantEffectTextures.resolveSourceEgg(MinecraftClient.getInstance().player);
            if (!eggStack.isEmpty()) {
               NonPregnantEffectTextures.drawSourceEgg(context, eggStack, x, y, width, height);
               return;
            }
         }

         context.drawGuiTexture(pipeline, sprite, x, y, width, height, color);
      }
   }
}
