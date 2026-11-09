package com.nonid.mixin.client;

import com.nonid.EnergyHolder;
import com.nonid.client.NonDebugNameHelper;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererDebugNameMixin {
   @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
   private void needsOfNature$showDebugLabel(Entity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
      if (NonDebugNameHelper.shouldShowDebugLabel(entity)) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
   private void needsOfNature$debugDisplayName(Entity entity, CallbackInfoReturnable<Text> cir) {
      if (NonDebugNameHelper.shouldShowDebugLabel(entity)) {
         if (entity instanceof EnergyHolder holder) {
            String energy = holder.getEnergy() + "/" + holder.getMaxEnergy();
            cir.setReturnValue(Text.literal("T:" + energy + " " + NonDebugNameHelper.resolveGenderSymbol(entity)));
         }
      }
   }

   @Inject(method = "updateRenderState", at = @At("TAIL"))
   private void needsOfNature$debugLabelState(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
      if (NonDebugNameHelper.shouldShowDebugLabel(entity)) {
         if (entity instanceof EnergyHolder holder) {
            String energy = holder.getEnergy() + "/" + holder.getMaxEnergy();
            state.displayName = Text.literal("T:" + energy + " " + NonDebugNameHelper.resolveGenderSymbol(entity));
            Vec3d labelPos = entity.getAttachments().getPointNullable(EntityAttachmentType.NAME_TAG, 0, entity.getLerpedYaw(tickDelta));
            if (labelPos == null) {
               labelPos = new Vec3d(entity.getX(), entity.getY() + entity.getHeight() + 0.5, entity.getZ());
            }

            state.nameLabelPos = labelPos;
         }
      }
   }
}
