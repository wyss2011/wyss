package com.nonid.internal.animation.client.render.gecko.layer;

import com.nonid.internal.animation.AnimationEngine;
import com.nonid.internal.animation.client.compat.wildfire.WildfireGenderCompat;
import com.nonid.internal.animation.client.compat.wildfire.WildfireGenderState;
import com.nonid.internal.animation.client.render.AnimationRenderStateAccess;
import com.nonid.internal.animation.client.render.gecko.GeckoRenderTickets;
import java.util.UUID;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

public final class WildfireGenderGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState> extends GeoRenderLayer<T, O, R> {
   private static final String BODY_BONE = "body";
   private static boolean loggedMissingBodyBone;

   public WildfireGenderGeoLayer(GeoRenderer<@NotNull T, @NotNull O, @NotNull R> renderer) {
      super(renderer);
   }

   public void submitRenderTask(RenderPassInfo<@NotNull R> renderPassInfo, @NotNull OrderedRenderCommandQueue renderTasks) {
      if (renderPassInfo.willRender()) {
         WildfireGenderState state = (WildfireGenderState)renderPassInfo.renderState().getGeckolibData(GeckoRenderTickets.WILDFIRE_GENDER_STATE);
         if (state != null) {
            if (renderPassInfo.renderState() instanceof LivingEntityRenderState livingState) {
               if (renderPassInfo.renderState() instanceof AnimationRenderStateAccess access) {
                  UUID actorUuid = access.afw$getEntityUuid();
                  if (actorUuid != null) {
                     renderPassInfo.renderPosed(() -> {
                        MatrixStack modelStack = new MatrixStack();
                        modelStack.peek().copy(renderPassInfo.poseStack().peek());
                        MatrixStack rootStack = new MatrixStack();
                        rootStack.peek().copy(renderPassInfo.poseStack().peek());
                        MatrixStack bodyStack = this.findBonePose(rootStack, renderPassInfo, "body");
                        if (bodyStack == null) {
                           logMissingBodyBone();
                        } else {
                           this.submitAtBone(rootStack, bodyStack, modelStack, renderPassInfo, renderTasks, livingState, actorUuid, "body");
                        }
                     });
                  }
               }
            }
         }
      }
   }

   private boolean submitAtBone(
      MatrixStack rootPose,
      MatrixStack physicsPose,
      MatrixStack poseStack,
      RenderPassInfo<@NotNull R> renderPassInfo,
      OrderedRenderCommandQueue renderTasks,
      LivingEntityRenderState livingState,
      UUID actorUuid,
      String targetBone
   ) {
      for (GeoBone bone : renderPassInfo.model().topLevelBones()) {
         if (this.submitAtBoneRecursive(rootPose, physicsPose, poseStack, bone, renderTasks, livingState, actorUuid, renderPassInfo.packedLight(), targetBone)) {
            return true;
         }
      }

      return false;
   }

   private boolean submitAtBoneRecursive(
      MatrixStack rootPose,
      MatrixStack physicsPose,
      MatrixStack poseStack,
      GeoBone bone,
      OrderedRenderCommandQueue renderTasks,
      LivingEntityRenderState livingState,
      UUID actorUuid,
      int packedLight,
      String targetBone
   ) {
      poseStack.push();
      RenderUtil.prepMatrixForBone(poseStack, bone);
      boolean submitted = false;
      if (targetBone.equals(bone.name())) {
         WildfireGenderCompat.overrideRenderPhysics(actorUuid, livingState, rootPose, physicsPose);
         applyVanillaLayerCoordinateBridge(poseStack);
         submitted = WildfireGenderCompat.submitOriginalLayer(livingState, poseStack, renderTasks, packedLight);
      } else {
         for (GeoBone child : bone.children()) {
            if (this.submitAtBoneRecursive(rootPose, physicsPose, poseStack, child, renderTasks, livingState, actorUuid, packedLight, targetBone)) {
               submitted = true;
               break;
            }
         }
      }

      poseStack.pop();
      return submitted;
   }

   private MatrixStack findBonePose(MatrixStack rootPose, RenderPassInfo<@NotNull R> renderPassInfo, String targetBone) {
      MatrixStack searchStack = new MatrixStack();
      searchStack.peek().copy(rootPose.peek());

      for (GeoBone bone : renderPassInfo.model().topLevelBones()) {
         MatrixStack found = this.findBonePoseRecursive(searchStack, bone, targetBone);
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   private MatrixStack findBonePoseRecursive(MatrixStack poseStack, GeoBone bone, String targetBone) {
      poseStack.push();
      RenderUtil.prepMatrixForBone(poseStack, bone);
      MatrixStack found = null;
      if (targetBone.equals(bone.name())) {
         found = new MatrixStack();
         found.peek().copy(poseStack.peek());
      } else {
         for (GeoBone child : bone.children()) {
            found = this.findBonePoseRecursive(poseStack, child, targetBone);
            if (found != null) {
               break;
            }
         }
      }

      poseStack.pop();
      return found;
   }

   private static void logMissingBodyBone() {
      if (!loggedMissingBodyBone) {
         loggedMissingBodyBone = true;
         AnimationEngine.LOGGER.warn("[NoN Animation] Female Gender compatibility requires Gecko player bone '{}'; skipping layer.", "body");
      }
   }

   private static void applyVanillaLayerCoordinateBridge(MatrixStack poseStack) {
      poseStack.translate(0.0F, 1.5F, 0.0F);
      poseStack.multiply(new Quaternionf().rotationZ((float) Math.PI));
   }
}
