package com.nonid.internal.animation.client.render.gecko.layer;

import com.nonid.internal.animation.client.camera.CameraPositionAccess;
import com.nonid.internal.animation.client.render.AnimationRenderStateAccess;
import com.nonid.internal.animation.client.render.gecko.BoneTextureOverrides;
import com.nonid.internal.animation.client.render.gecko.GeckoRenderTickets;
import com.nonid.internal.animation.client.render.gecko.ParticleKeyframes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

public final class ParticleKeyframeGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState> extends GeoRenderLayer<T, O, R> {
   public ParticleKeyframeGeoLayer(GeoRenderer<@NotNull T, @NotNull O, @NotNull R> renderer) {
      super(renderer);
   }

   public void submitRenderTask(RenderPassInfo<@NotNull R> renderPassInfo, @NotNull OrderedRenderCommandQueue renderTasks) {
      if (renderPassInfo.willRender()) {
         UUID actorUuid = (UUID)renderPassInfo.renderState().getGeckolibData(GeckoRenderTickets.ACTOR_UUID);
         UUID instanceId = (UUID)renderPassInfo.renderState().getGeckolibData(GeckoRenderTickets.ANIMATION_INSTANCE_ID);
         MinecraftClient client = MinecraftClient.getInstance();
         if (actorUuid != null && instanceId != null && client != null && client.world != null) {
            long worldTick = client.world.getTime();
            Identifier modelId = (Identifier)renderPassInfo.renderState().getGeckolibData(GeckoRenderTickets.MODEL_ID);
            Map<String, BoneTextureOverrides.ModelLocator> modelLocators = BoneTextureOverrides.getLocators(modelId);
            if (modelLocators == null) {
               modelLocators = Map.of();
            }

            String actorOriginBone = this.resolveActorOriginBone((R)renderPassInfo.renderState());
            if (modelLocators.isEmpty() && actorOriginBone == null) {
               ParticleKeyframes.updateLocatorPositions(instanceId, actorUuid, worldTick, Map.of());
            } else {
               ParticleKeyframeGeoLayer.PoseCapture capture = new ParticleKeyframeGeoLayer.PoseCapture();
               Map<String, BoneTextureOverrides.ModelLocator> locators = modelLocators;
               renderPassInfo.renderPosed(
                  () -> {
                     MatrixStack modelStack = new MatrixStack();
                     modelStack.peek().copy(renderPassInfo.poseStack().peek());
                     if (locators.isEmpty()) {
                        capture.actorOrigin = renderPassInfo.model()
                           .getBone(actorOriginBone)
                           .map(bonex -> this.captureBonePosition(modelStack, bonex))
                           .orElse(null);
                     } else {
                        for (GeoBone bone : renderPassInfo.model().topLevelBones()) {
                           this.capturePositions(modelStack, bone, locators, actorOriginBone, capture);
                        }
                     }
                  }
               );
               ParticleKeyframes.updateLocatorData(instanceId, actorUuid, worldTick, capture.locators, capture.directions);
               ParticleKeyframes.updateActorOrigin(actorUuid, capture.actorOrigin);
            }
         }
      }
   }

   private void capturePositions(
      MatrixStack poseStack,
      GeoBone bone,
      Map<String, BoneTextureOverrides.ModelLocator> modelLocators,
      String actorOriginBone,
      ParticleKeyframeGeoLayer.PoseCapture capture
   ) {
      poseStack.push();
      RenderUtil.prepMatrixForBone(poseStack, bone);
      if (capture.actorOrigin == null && bone.name().equals(actorOriginBone)) {
         poseStack.push();
         bone.translateToPivotPoint(poseStack);
         capture.actorOrigin = this.matrixToWorldPos(poseStack);
         poseStack.pop();
      }

      for (Entry<String, BoneTextureOverrides.ModelLocator> entry : modelLocators.entrySet()) {
         BoneTextureOverrides.ModelLocator locator = entry.getValue();
         if (locator != null && bone.name().equals(locator.parentBone())) {
            poseStack.push();
            bone.translateToPivotPoint(poseStack);
            Vec3d pivotPos = this.matrixToWorldPos(poseStack);
            poseStack.translate(
               (float)((locator.x() - locator.parentPivotX()) / 16.0),
               (float)((locator.y() - locator.parentPivotY()) / 16.0),
               (float)((locator.z() - locator.parentPivotZ()) / 16.0)
            );
            Vec3d worldPos = this.matrixToWorldPos(poseStack);
            if (worldPos != null && worldPos.isFinite()) {
               capture.locators.put(entry.getKey(), worldPos);
            }

            if (worldPos != null && pivotPos != null && worldPos.isFinite() && pivotPos.isFinite()) {
               Vec3d direction = worldPos.subtract(pivotPos);
               if (direction.lengthSquared() > 1.0E-4) {
                  capture.directions.put(entry.getKey(), direction.normalize());
               }
            }

            poseStack.pop();
         }
      }

      for (GeoBone child : bone.children()) {
         this.capturePositions(poseStack, child, modelLocators, actorOriginBone, capture);
      }

      poseStack.pop();
   }

   private Vec3d captureBonePosition(MatrixStack poseStack, GeoBone target) {
      Deque<GeoBone> ancestry = new ArrayDeque<>();

      for (GeoBone bone = target; bone != null; bone = bone.parent()) {
         ancestry.addFirst(bone);
      }

      int pushed = 0;

      try {
         for (GeoBone bone : ancestry) {
            poseStack.push();
            pushed++;
            RenderUtil.prepMatrixForBone(poseStack, bone);
         }

         poseStack.push();
         pushed++;
         target.translateToPivotPoint(poseStack);
         return this.matrixToWorldPos(poseStack);
      } finally {
         while (pushed-- > 0) {
            poseStack.pop();
         }
      }
   }

   private String resolveActorOriginBone(R renderState) {
      if (renderState instanceof AnimationRenderStateAccess access) {
         Identifier entityTypeId = access.afw$getEntityTypeId();
         return entityTypeId != null && !entityTypeId.getPath().isBlank() ? entityTypeId.getPath() : null;
      } else {
         return null;
      }
   }

   private Vec3d matrixToWorldPos(MatrixStack poseStack) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
         if (client.gameRenderer.getCamera() instanceof CameraPositionAccess access) {
            Vector3f var6 = poseStack.peek().getPositionMatrix().transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
            Vec3d cameraWorldPos = access.afw$getPos();
            return new Vec3d(cameraWorldPos.x + var6.x(), cameraWorldPos.y + var6.y(), cameraWorldPos.z + var6.z());
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static final class PoseCapture {
      private final Map<String, Vec3d> locators = new LinkedHashMap<>();
      private final Map<String, Vec3d> directions = new LinkedHashMap<>();
      private Vec3d actorOrigin;
   }
}
