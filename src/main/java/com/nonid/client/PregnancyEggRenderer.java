package com.nonid.client;

import com.nonid.entity.PregnancyEggEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

public final class PregnancyEggRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<PregnancyEggEntity, R> {
   public PregnancyEggRenderer(Context context) {
      super(context, new PregnancyEggGeoModel());
   }

   public void addRenderData(PregnancyEggEntity animatable, Void relatedObject, R renderState, float partialTick) {
      super.addRenderData(animatable, relatedObject, renderState, partialTick);
      Identifier sourceEntityTypeId = animatable.getSourceEntityTypeId();
      if (sourceEntityTypeId != null) {
         renderState.addGeckolibData(PregnancyEggGeoModel.SOURCE_ENTITY_TYPE_TICKET, sourceEntityTypeId);
      }

      renderState.addGeckolibData(PregnancyEggGeoModel.GROWTH_SCALE_TICKET, animatable.getGrowthScale(partialTick));
      renderState.addGeckolibData(PregnancyEggGeoModel.HURT_SHAKE_TICKET, Math.max(0.0F, animatable.hurtTime - partialTick));
      Identifier eggTextureId = animatable.getEggTextureId();
      renderState.addGeckolibData(PregnancyEggGeoModel.EGG_TEXTURE_TICKET, eggTextureId == null ? PregnancyEggGeoModel.DEFAULT_TEXTURE_ID : eggTextureId);
   }

   public void scaleModelForRender(RenderPassInfo<@NotNull R> renderPassInfo, float widthScale, float heightScale) {
      float growth = (Float)renderPassInfo.getOrDefaultGeckolibData(PregnancyEggGeoModel.GROWTH_SCALE_TICKET, 1.0F);
      float clamped = MathHelper.clamp(growth, 0.05F, 4.0F);
      super.scaleModelForRender(renderPassInfo, widthScale * clamped, heightScale * clamped);
   }

   public void preRenderPass(RenderPassInfo<@NotNull R> renderPassInfo, OrderedRenderCommandQueue renderTasks) {
      super.preRenderPass(renderPassInfo, renderTasks);
      float hurtShake = (Float)renderPassInfo.getOrDefaultGeckolibData(PregnancyEggGeoModel.HURT_SHAKE_TICKET, 0.0F);
      if (!(hurtShake <= 0.0F)) {
         float intensity = MathHelper.clamp((hurtShake - 7.0F) / 3.0F, 0.0F, 1.0F);
         if (!(intensity <= 0.0F)) {
            double wobble = 0.004 * intensity;
            float roll = 2.0F * intensity;
            renderPassInfo.poseStack().translate(wobble, 0.0, 0.0);
            renderPassInfo.poseStack().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
         }
      }
   }

   public int getPackedOverlay(PregnancyEggEntity animatable, Void relatedObject, float u, float partialTick) {
      return OverlayTexture.DEFAULT_UV;
   }

   protected float getDeathMaxRotation(@NotNull GeoRenderState renderState) {
      return 0.0F;
   }
}
