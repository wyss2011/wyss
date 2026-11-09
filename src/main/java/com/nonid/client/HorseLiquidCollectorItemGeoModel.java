package com.nonid.client;

import com.nonid.internal.animation.AnimationEngine;
import com.nonid.item.HorseLiquidCollectorItem;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class HorseLiquidCollectorItemGeoModel extends GeoModel<HorseLiquidCollectorItem> {
   private static final Identifier PLACEHOLDER_ANIMATION_ID = AnimationEngine.packId("afw/placeholder");

   @NotNull
   public Identifier getModelResource(@NotNull GeoRenderState renderState) {
      return HorseLiquidCollectorGeoModel.MODEL_ID;
   }

   @NotNull
   public Identifier getTextureResource(@NotNull GeoRenderState renderState) {
      return HorseLiquidCollectorGeoModel.EMPTY_TEXTURE_ID;
   }

   @NotNull
   public Identifier getAnimationResource(HorseLiquidCollectorItem animatable) {
      return PLACEHOLDER_ANIMATION_ID;
   }
}
