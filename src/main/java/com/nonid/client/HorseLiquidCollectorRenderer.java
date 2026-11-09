package com.nonid.client;

import com.nonid.entity.HorseLiquidCollectorEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class HorseLiquidCollectorRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<HorseLiquidCollectorEntity, R> {
   public HorseLiquidCollectorRenderer(Context context) {
      super(context, new HorseLiquidCollectorGeoModel());
   }

   public void addRenderData(HorseLiquidCollectorEntity animatable, Void relatedObject, R renderState, float partialTick) {
      super.addRenderData(animatable, relatedObject, renderState, partialTick);
      renderState.addGeckolibData(HorseLiquidCollectorGeoModel.COLLECTOR_FULL_TICKET, animatable.isCollectorFull());
   }

   public int getPackedOverlay(HorseLiquidCollectorEntity animatable, Void relatedObject, float u, float partialTick) {
      return OverlayTexture.DEFAULT_UV;
   }

   protected float getDeathMaxRotation(@NotNull GeoRenderState renderState) {
      return 0.0F;
   }
}
