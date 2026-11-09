package com.nonid.internal.animation.client.render.gecko.layer;

import com.nonid.internal.animation.client.camera.AnimatedCameraPoseTracker;
import com.nonid.internal.animation.client.camera.CameraPositionAccess;
import com.nonid.internal.animation.client.render.AnimationRenderStateAccess;
import com.nonid.internal.animation.client.render.gecko.GeckoRenderTickets;
import java.util.UUID;
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

public final class CameraPoseGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState> extends GeoRenderLayer<T, O, R> {
   private static final Identifier PLAYER_TYPE_ID = Identifier.ofVanilla("player");
   private static final String HEAD_BONE = "head";
   private static final String BODY_BONE = "body";
   private static final float HEAD_CENTER_Y_OFFSET = 0.3F;
   private static final float BODY_CENTER_Y_OFFSET = -0.375F;

   public CameraPoseGeoLayer(GeoRenderer<@NotNull T, @NotNull O, @NotNull R> renderer) {
      super(renderer);
   }

   public void submitRenderTask(RenderPassInfo<@NotNull R> renderPassInfo, @NotNull OrderedRenderCommandQueue renderTasks) {
      if (renderPassInfo.willRender()) {
         if (renderPassInfo.renderState() instanceof AnimationRenderStateAccess access) {
            if (PLAYER_TYPE_ID.equals(access.afw$getEntityTypeId())) {
               UUID actorUuid = access.afw$getEntityUuid();
               if (actorUuid != null) {
                  renderPassInfo.renderPosed(
                     () -> {
                        MatrixStack modelStack = new MatrixStack();
                        modelStack.peek().copy(renderPassInfo.poseStack().peek());
                        CameraPoseGeoLayer.HeadSample headSample = this.findHeadSample(modelStack, renderPassInfo.model().topLevelBones());
                        if (headSample != null) {
                           CameraPoseGeoLayer.BodySample bodySample = this.findBodySample(modelStack, renderPassInfo.model().topLevelBones());
                           MinecraftClient client = MinecraftClient.getInstance();
                           long nowTick = client != null && client.world != null ? client.world.getTime() : 0L;
                           UUID instanceId = (UUID)renderPassInfo.renderState().getGeckolibData(GeckoRenderTickets.ANIMATION_INSTANCE_ID);
                           Identifier animationId = (Identifier)renderPassInfo.renderState().getGeckolibData(GeckoRenderTickets.ANIMATION_ID);
                           AnimatedCameraPoseTracker.capture(
                              actorUuid,
                              instanceId,
                              animationId,
                              headSample.worldPos(),
                              bodySample == null ? null : bodySample.worldPos(),
                              headSample.forward(),
                              headSample.up(),
                              headSample.right(),
                              bodySample == null ? null : bodySample.forward(),
                              bodySample == null ? null : bodySample.up(),
                              bodySample == null ? null : bodySample.right(),
                              nowTick
                           );
                        }
                     }
                  );
               }
            }
         }
      }
   }

   private CameraPoseGeoLayer.HeadSample findHeadSample(MatrixStack poseStack, GeoBone[] bones) {
      for (GeoBone bone : bones) {
         CameraPoseGeoLayer.HeadSample headSample = this.findHeadSample(poseStack, bone);
         if (headSample != null) {
            return headSample;
         }
      }

      return null;
   }

   private CameraPoseGeoLayer.HeadSample findHeadSample(MatrixStack poseStack, GeoBone bone) {
      poseStack.push();
      RenderUtil.prepMatrixForBone(poseStack, bone);
      CameraPoseGeoLayer.HeadSample headSample = null;
      if ("head".equalsIgnoreCase(bone.name())) {
         poseStack.push();
         bone.translateToPivotPoint(poseStack);
         Vec3d headPos = this.matrixToWorldPos(poseStack, 0.3F);
         CameraPoseGeoLayer.Basis headBasis = this.matrixToBasis(poseStack);
         if (headPos != null) {
            headSample = new CameraPoseGeoLayer.HeadSample(
               headPos, headBasis == null ? null : headBasis.forward(), headBasis == null ? null : headBasis.up(), headBasis == null ? null : headBasis.right()
            );
         }

         poseStack.pop();
      }

      if (headSample == null) {
         for (GeoBone child : bone.children()) {
            headSample = this.findHeadSample(poseStack, child);
            if (headSample != null) {
               break;
            }
         }
      }

      poseStack.pop();
      return headSample;
   }

   private CameraPoseGeoLayer.BodySample findBodySample(MatrixStack poseStack, GeoBone[] bones) {
      for (GeoBone bone : bones) {
         CameraPoseGeoLayer.BodySample sample = this.findBodySample(poseStack, bone);
         if (sample != null) {
            return sample;
         }
      }

      return null;
   }

   private CameraPoseGeoLayer.BodySample findBodySample(MatrixStack poseStack, GeoBone bone) {
      poseStack.push();
      RenderUtil.prepMatrixForBone(poseStack, bone);
      CameraPoseGeoLayer.BodySample bodySample = null;
      if ("body".equalsIgnoreCase(bone.name())) {
         poseStack.push();
         bone.translateToPivotPoint(poseStack);
         Vec3d bodyPos = this.matrixToWorldPos(poseStack, -0.375F);
         CameraPoseGeoLayer.Basis bodyBasis = this.matrixToBasis(poseStack);
         if (bodyPos != null) {
            bodySample = new CameraPoseGeoLayer.BodySample(
               bodyPos, bodyBasis == null ? null : bodyBasis.forward(), bodyBasis == null ? null : bodyBasis.up(), bodyBasis == null ? null : bodyBasis.right()
            );
         }

         poseStack.pop();
      }

      if (bodySample == null) {
         for (GeoBone child : bone.children()) {
            bodySample = this.findBodySample(poseStack, child);
            if (bodySample != null) {
               break;
            }
         }
      }

      poseStack.pop();
      return bodySample;
   }

   private Vec3d matrixToWorldPos(MatrixStack poseStack, float localYOffset) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
         Vector3f relative = poseStack.peek().getPositionMatrix().transformPosition(0.0F, localYOffset, 0.0F, new Vector3f());
         Vec3d cameraWorldPos = ((CameraPositionAccess)client.gameRenderer.getCamera()).afw$getPos();
         return new Vec3d(cameraWorldPos.x + relative.x(), cameraWorldPos.y + relative.y(), cameraWorldPos.z + relative.z());
      } else {
         return null;
      }
   }

   private CameraPoseGeoLayer.Basis matrixToBasis(MatrixStack poseStack) {
      Vector3f origin = poseStack.peek().getPositionMatrix().transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
      Vec3d forwardHint = this.vectorBetween(origin, poseStack.peek().getPositionMatrix().transformPosition(0.0F, 0.0F, -1.0F, new Vector3f()));
      Vec3d upHint = this.vectorBetween(origin, poseStack.peek().getPositionMatrix().transformPosition(0.0F, 1.0F, 0.0F, new Vector3f()));
      if (forwardHint != null && upHint != null) {
         Vec3d forward = this.normalize(forwardHint);
         Vec3d right = this.normalize(forward.crossProduct(upHint));
         if (forward != null && right != null) {
            Vec3d up = this.normalize(right.crossProduct(forward));
            return up == null ? null : new CameraPoseGeoLayer.Basis(forward, up, right);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private Vec3d vectorBetween(Vector3f origin, Vector3f target) {
      return origin != null && target != null ? new Vec3d(target.x() - origin.x(), target.y() - origin.y(), target.z() - origin.z()) : null;
   }

   private Vec3d normalize(Vec3d vector) {
      if (vector != null && !(vector.lengthSquared() < 1.0E-8)) {
         Vec3d normalized = vector.normalize();
         return normalized.isFinite() ? normalized : null;
      } else {
         return null;
      }
   }

   private record Basis(Vec3d forward, Vec3d up, Vec3d right) {
   }

   private record BodySample(Vec3d worldPos, Vec3d forward, Vec3d up, Vec3d right) {
   }

   private record HeadSample(Vec3d worldPos, Vec3d forward, Vec3d up, Vec3d right) {
   }
}
