package com.nonid.client;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.nonid.internal.animation.AnimationEngine;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class NonAnimationPreviewRenderer {
   private static final Map<String, Optional<NonAnimationPreviewRenderer.PreviewTexture>> TEXTURE_CACHE = new HashMap<>();

   private NonAnimationPreviewRenderer() {
   }

   public static void draw(DrawContext context, TextRenderer textRenderer, String animationId, int x, int y, int size) {
      Optional<NonAnimationPreviewRenderer.PreviewTexture> preview = previewTexture(animationId);
      if (preview.isPresent()) {
         NonAnimationPreviewRenderer.PreviewTexture texture = preview.get();
         int textureWidth = texture.width();
         int textureHeight = texture.height();
         int drawWidth = size;
         int drawHeight = size;
         if (textureWidth > textureHeight) {
            drawHeight = Math.max(1, Math.round(size * ((float)textureHeight / textureWidth)));
         } else if (textureHeight > textureWidth) {
            drawWidth = Math.max(1, Math.round(size * ((float)textureWidth / textureHeight)));
         }

         int drawX = x + (size - drawWidth) / 2;
         int drawY = y + (size - drawHeight) / 2;
         context.fill(x, y, x + size, y + size, -13948117);
         context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            texture.id(),
            drawX,
            drawY,
            0.0F,
            0.0F,
            drawWidth,
            drawHeight,
            textureWidth,
            textureHeight,
            textureWidth,
            textureHeight
         );
      } else {
         context.fill(x, y, x + size, y + size, -13948117);
         context.drawCenteredTextWithShadow(textRenderer, "?", x + size / 2, y + size / 2 - 4, -5592406);
      }
   }

   public static void clearCache() {
      TEXTURE_CACHE.clear();
   }

   private static Optional<NonAnimationPreviewRenderer.PreviewTexture> previewTexture(String animationId) {
      return TEXTURE_CACHE.computeIfAbsent(animationId, NonAnimationPreviewRenderer::resolvePreviewTexture);
   }

   private static Optional<NonAnimationPreviewRenderer.PreviewTexture> resolvePreviewTexture(String animationId) {
      Identifier id = Identifier.tryParse(animationId);
      if (id == null) {
         return Optional.empty();
      }

      String path = "textures/afw/previews/" + id.getPath() + ".png";
      Identifier namespaced = Identifier.of(id.getNamespace(), path);
      MinecraftClient client = MinecraftClient.getInstance();
      Identifier preview = null;
      if (client.getResourceManager().getResource(namespaced).isPresent()) {
         preview = namespaced;
      } else {
         Identifier fallback = AnimationEngine.packId(path);
         if (client.getResourceManager().getResource(fallback).isPresent()) {
            preview = fallback;
         }
      }

      if (preview == null) {
         return Optional.empty();
      }

      GpuTextureView textureView = client.getTextureManager().getTexture(preview).getGlTextureView();
      return Optional.of(new NonAnimationPreviewRenderer.PreviewTexture(preview, Math.max(1, textureView.getWidth(0)), Math.max(1, textureView.getHeight(0))));
   }

   private record PreviewTexture(Identifier id, int width, int height) {
   }
}
