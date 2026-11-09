package com.nonid.internal.animation.client.render.gecko;

import com.nonid.NeedsOfNature;
import com.nonid.internal.animation.AnimationEngine;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;

public final class VanillaTextureResolver {
   private static final Map<Identifier, Identifier> REDIRECT_CACHE = new ConcurrentHashMap<>();
   private static final List<Identifier> UPLOADED_TEXTURES = new ArrayList<>();
   private static final Identifier RELOAD_LISTENER_ID = AnimationEngine.runtimeId("vanilla_textures");

   private VanillaTextureResolver() {
   }

   public static void registerReloadListener() {
      ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(RELOAD_LISTENER_ID, (SynchronousResourceReloader)manager -> clearCache());
   }

   public static Identifier resolveTexture(Identifier textureId) {
      if (textureId == null) {
         return null;
      } else {
         return !NeedsOfNature.getConfig().forceVanillaEntityTextures()
            ? textureId
            : REDIRECT_CACHE.computeIfAbsent(textureId, VanillaTextureResolver::copyVanillaTexture);
      }
   }

   private static Identifier copyVanillaTexture(Identifier textureId) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return textureId;
      }

      ResourceManager resourceManager = client.getResourceManager();

      try {
         List<Resource> resources = resourceManager.getAllResources(textureId);
         if (resources.isEmpty()) {
            return textureId;
         }

         Resource vanillaResource = null;

         for (Resource res : resources) {
            String packId = res.getPackId();
            if ("vanilla".equals(packId) || "minecraft".equals(packId) || "default".equalsIgnoreCase(packId)) {
               vanillaResource = res;
               break;
            }
         }

         if (vanillaResource == null) {
            vanillaResource = resources.getLast();
         }

         try (InputStream stream = vanillaResource.getInputStream()) {
            NativeImage image = NativeImage.read(stream);
            Identifier redirected = AnimationEngine.runtimeId("vanilla_textures/" + textureId.getNamespace() + "/" + textureId.getPath());
            client.getTextureManager().registerTexture(redirected, new NativeImageBackedTexture(() -> "non_animation_vanilla_texture", image));
            synchronized (UPLOADED_TEXTURES) {
               UPLOADED_TEXTURES.add(redirected);
            }

            return redirected;
         }
      } catch (IOException | RuntimeException e) {
         AnimationEngine.LOGGER.warn("[NoN Animation] Failed to pull vanilla texture {}, using active resource stack", textureId, e);
         return textureId;
      }
   }

   public static void clearCache() {
      REDIRECT_CACHE.clear();
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null) {
         synchronized (UPLOADED_TEXTURES) {
            for (Identifier id : UPLOADED_TEXTURES) {
               client.getTextureManager().destroyTexture(id);
            }

            UPLOADED_TEXTURES.clear();
         }
      }
   }
}
