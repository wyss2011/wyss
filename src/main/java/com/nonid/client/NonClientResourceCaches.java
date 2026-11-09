package com.nonid.client;

import com.nonid.NeedsOfNatureClient;
import com.nonid.internal.animation.client.render.gecko.AnimationActorAnimatable;
import com.nonid.internal.animation.client.render.gecko.GeckoResourceResolver;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;

public final class NonClientResourceCaches {
   private static final Identifier RELOAD_LISTENER_ID = Identifier.of("needsofnature", "client_resource_caches");

   private NonClientResourceCaches() {
   }

   public static void registerReloadListener() {
      ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(RELOAD_LISTENER_ID, (SynchronousResourceReloader)manager -> clear());
   }

   private static void clear() {
      NeedsOfNatureClient.clearGeneratedTintTextures();
      NonDestroyedSkinClient.clearResourceCaches();
      NonAnimationPreviewRenderer.clearCache();
      PregnancyEggGeoModel.clearTextureCache();
      NonStatLabelResolver.clearCache();
      AnimationActorAnimatable.clearResourceCaches();
      GeckoResourceResolver.clearResourceCaches();
   }
}
