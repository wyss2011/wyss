package com.nonid.internal.animation.client.render.gecko;

import com.nonid.internal.animation.AnimationEngine;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class AnimationActorGeoModel extends GeoModel<AnimationActorAnimatable> {
   private static final ThreadLocal<Identifier> CURRENT_ANIMATION_RESOURCE = new ThreadLocal<>();
   private static final Identifier DEFAULT_MODEL = AnimationEngine.packId("entity/missingmodel");
   private static final Identifier DEFAULT_TEXTURE = AnimationEngine.packId("textures/missingmodel.png");
   private static final Identifier PLACEHOLDER_ANIMATIONS = AnimationEngine.packId("afw/placeholder");

   public static void setCurrentAnimationResource(Identifier resource) {
      CURRENT_ANIMATION_RESOURCE.set(resource);
   }

   public static void clearCurrentAnimationResource() {
      CURRENT_ANIMATION_RESOURCE.remove();
   }

   @NotNull
   public Identifier getModelResource(GeoRenderState renderState) {
      return (Identifier)renderState.getOrDefaultGeckolibData(GeckoRenderTickets.MODEL_ID, DEFAULT_MODEL);
   }

   @NotNull
   public Identifier getTextureResource(GeoRenderState renderState) {
      return (Identifier)renderState.getOrDefaultGeckolibData(GeckoRenderTickets.TEXTURE_ID, DEFAULT_TEXTURE);
   }

   @NotNull
   public Identifier getAnimationResource(AnimationActorAnimatable animatable) {
      Identifier resource = CURRENT_ANIMATION_RESOURCE.get();
      return resource != null ? resource : PLACEHOLDER_ANIMATIONS;
   }
}
