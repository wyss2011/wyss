package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.camera.AnimatedCameraPoseTracker;
import com.nonid.internal.animation.client.camera.AnimationCameraZoom;
import com.nonid.internal.animation.client.camera.FirstPersonCameraCollision;
import com.nonid.internal.animation.client.camera.FirstPersonLookConstraint;
import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraAnimationMixin {
   private static final double CAMERA_FORWARD_OFFSET = 0.2;
   private static final double THIRD_PERSON_ORBIT_EPSILON_SQ = 1.0E-8;
   private static final long FALLBACK_POSE_HOLD_TICKS = 20L;
   private static final long START_ALIGNMENT_TICKS = 8L;
   private static final long THIRD_PERSON_ORBIT_SMOOTH_TICKS = 6L;
   private static final long NEW_INSTANCE_ORBIT_SAMPLE_DELAY_TICKS = 4L;
   private static final long BODY_ORBIT_VALIDATION_INTERVAL_TICKS = 20L;
   private static final double BODY_ORBIT_MISMATCH_DISTANCE_SQ = 0.25;
   private static final int BODY_ORBIT_MISMATCHES_BEFORE_CORRECTION = 2;
   private static final double THIRD_PERSON_FOCUS_BOX_SIZE = 0.25;
   private static final double THIRD_PERSON_FOCUS_UP_STEP = 0.1;
   private static final int THIRD_PERSON_FOCUS_UP_STEPS = 16;
   private static final double THIRD_PERSON_FOCUS_HORIZONTAL_STEP = 0.25;
   private static final int THIRD_PERSON_FOCUS_HORIZONTAL_RINGS = 4;
   private static AnimatedCameraPoseTracker.PoseSample afw$lastValidPosedPose = null;
   private static Vec3d afw$lastConstrainedLook = null;
   private static UUID afw$lastActiveInstanceId = null;
   private static UUID afw$startAlignInstanceId = null;
   private static UUID afw$bodyOrbitInstanceId = null;
   private static Identifier afw$bodyOrbitAnimationId = null;
   private static Vec3d afw$bodyOrbitTarget = null;
   private static boolean afw$delayBodyOrbitForCurrentInstance = false;
   private static long afw$nextBodyOrbitValidationTick = 0L;
   private static int afw$bodyOrbitMismatchCount = 0;
   private static UUID afw$smoothedOrbitInstanceId = null;
   private static Identifier afw$smoothedOrbitAnimationId = null;
   private static Vec3d afw$orbitSmoothStart = null;
   private static Vec3d afw$orbitSmoothTarget = null;
   private static Vec3d afw$currentSmoothedOrbitTarget = null;
   private static long afw$orbitSmoothStartTick = 0L;
   private static boolean afw$wasFirstPersonLastFrame = false;
   private static boolean afw$startAlignPendingTarget = false;
   private static boolean afw$startAlignActive = false;
   private static float afw$startAlignFromYaw = 0.0F;
   private static float afw$startAlignFromPitch = 0.0F;
   private static float afw$startAlignTargetYaw = 0.0F;
   private static float afw$startAlignTargetPitch = 0.0F;
   private static long afw$startAlignTick = 0L;

   @Shadow
   protected abstract void setPos(Vec3d var1);

   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Shadow
   protected abstract void moveBy(float var1, float var2, float var3);

   @Shadow
   private float clipToSpace(float startingDistance) {
      return startingDistance;
   }

   @Inject(method = "update", at = @At("TAIL"))
   private void afw$lockFirstPersonCameraToAnimatedPose(
      World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci
   ) {
      if (!(focusedEntity instanceof PlayerEntity focusedPlayer)) {
         afw$resetAnimationCameraState(false);
      } else {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.world != null && client.player != null) {
            if (!focusedPlayer.getUuid().equals(client.player.getUuid())) {
               afw$resetAnimationCameraState(!thirdPerson);
            } else {
               UUID actorUuid = focusedPlayer.getUuid();
               boolean pendingOrActive = ClientAnimationRuntime.isActorPendingOrActive(actorUuid);
               UUID activeInstanceId = ClientAnimationRuntime.findLatestActiveInstanceContaining(actorUuid);
               if (!pendingOrActive) {
                  afw$resetAnimationCameraState(!thirdPerson);
               } else if (thirdPerson) {
                  afw$lastActiveInstanceId = activeInstanceId;
                  afw$wasFirstPersonLastFrame = false;
                  Identifier activeAnimationId = ClientAnimationRuntime.findLatestActiveAnimationIdContaining(actorUuid);
                  this.afw$applyThirdPersonCameraAdjustments(actorUuid, activeInstanceId, activeAnimationId, focusedPlayer, tickDelta, client.world.getTime());
               } else {
                  boolean enteredFirstPerson = !afw$wasFirstPersonLastFrame;
                  boolean instanceChanged = !Objects.equals(activeInstanceId, afw$lastActiveInstanceId);
                  boolean replacingActiveInstance = instanceChanged && afw$lastActiveInstanceId != null;
                  if (activeInstanceId == null || !enteredFirstPerson && (!instanceChanged || replacingActiveInstance)) {
                     if (replacingActiveInstance) {
                        afw$clearStartAlignment();
                     }
                  } else {
                     afw$beginStartAlignment(activeInstanceId, focusedPlayer.getYaw(tickDelta), focusedPlayer.getPitch(tickDelta));
                  }

                  afw$lastActiveInstanceId = activeInstanceId;
                  afw$wasFirstPersonLastFrame = true;
                  long nowTick = client.world.getTime();
                  AnimatedCameraPoseTracker.PoseSample posedPose = AnimatedCameraPoseTracker.resolve(actorUuid, nowTick);
                  if (posedPose != null) {
                     afw$lastValidPosedPose = posedPose;
                  } else {
                     if (afw$lastValidPosedPose == null || nowTick - afw$lastValidPosedPose.tick() > 20L) {
                        return;
                     }

                     posedPose = afw$lastValidPosedPose;
                  }

                  if (afw$startAlignPendingTarget && Objects.equals(activeInstanceId, afw$startAlignInstanceId)) {
                     Vec3d targetLook = afw$resolveStartAlignmentTargetLook(posedPose);
                     if (targetLook != null) {
                        afw$startAlignTargetYaw = FirstPersonLookConstraint.toMinecraftYaw(targetLook, afw$startAlignFromYaw);
                        afw$startAlignTargetPitch = FirstPersonLookConstraint.toMinecraftPitch(targetLook);
                        afw$startAlignTick = nowTick;
                        afw$startAlignPendingTarget = false;
                        afw$startAlignActive = true;
                     }
                  }

                  float desiredYaw = focusedPlayer.getYaw(tickDelta);
                  float desiredPitch = focusedPlayer.getPitch(tickDelta);
                  if (afw$startAlignActive && Objects.equals(activeInstanceId, afw$startAlignInstanceId)) {
                     float progress = MathHelper.clamp(((float)(nowTick - afw$startAlignTick) + tickDelta) / 8.0F, 0.0F, 1.0F);
                     float eased = afw$smootherStep(progress);
                     desiredYaw = afw$lerpAngleDegrees(eased, afw$startAlignFromYaw, afw$startAlignTargetYaw);
                     desiredPitch = MathHelper.lerp(eased, afw$startAlignFromPitch, afw$startAlignTargetPitch);
                     if (progress >= 1.0F) {
                        afw$startAlignActive = false;
                     }
                  }

                  Vec3d desiredLook = Vec3d.fromPolar(desiredPitch, desiredYaw);
                  Vec3d constrainedLook = FirstPersonLookConstraint.constrain(desiredLook, posedPose, afw$lastConstrainedLook);
                  float constrainedYaw = FirstPersonLookConstraint.toMinecraftYaw(constrainedLook, desiredYaw);
                  float constrainedPitch = FirstPersonLookConstraint.toMinecraftPitch(constrainedLook);
                  focusedPlayer.lastYaw = constrainedYaw;
                  focusedPlayer.lastPitch = constrainedPitch;
                  focusedPlayer.setYaw(constrainedYaw);
                  focusedPlayer.setPitch(constrainedPitch);
                  focusedPlayer.lastHeadYaw = constrainedYaw;
                  focusedPlayer.headYaw = constrainedYaw;
                  focusedPlayer.setHeadYaw(constrainedYaw);
                  focusedPlayer.lastBodyYaw = constrainedYaw;
                  focusedPlayer.bodyYaw = constrainedYaw;
                  focusedPlayer.setBodyYaw(constrainedYaw);
                  this.setRotation(constrainedYaw, constrainedPitch);
                  Vec3d desiredCameraPos = posedPose.worldPos().add(constrainedLook.multiply(0.2));
                  this.setPos(FirstPersonCameraCollision.resolve(area, desiredCameraPos));
                  afw$lastConstrainedLook = constrainedLook;
               }
            }
         } else {
            afw$resetAnimationCameraState(!thirdPerson);
         }
      }
   }

   private static void afw$beginStartAlignment(UUID instanceId, float startYaw, float startPitch) {
      afw$startAlignInstanceId = instanceId;
      afw$startAlignPendingTarget = true;
      afw$startAlignActive = false;
      afw$startAlignFromYaw = startYaw;
      afw$startAlignFromPitch = startPitch;
      afw$startAlignTargetYaw = startYaw;
      afw$startAlignTargetPitch = startPitch;
      afw$startAlignTick = 0L;
   }

   private static void afw$clearStartAlignment() {
      afw$startAlignInstanceId = null;
      afw$startAlignPendingTarget = false;
      afw$startAlignActive = false;
   }

   private static Vec3d afw$resolveStartAlignmentTargetLook(AnimatedCameraPoseTracker.PoseSample pose) {
      Vec3d targetLook = pose.headForward();
      if (targetLook == null || targetLook.lengthSquared() < 1.0E-8 || !targetLook.isFinite()) {
         targetLook = pose.bodyForward();
      }

      return targetLook != null && !(targetLook.lengthSquared() < 1.0E-8) && targetLook.isFinite() ? targetLook.normalize() : null;
   }

   private void afw$applyThirdPersonCameraAdjustments(
      UUID actorUuid, UUID activeInstanceId, Identifier activeAnimationId, PlayerEntity focusedPlayer, float tickDelta, long nowTick
   ) {
      Vec3d focusPos = focusedPlayer.getCameraPosVec(tickDelta);
      if (focusPos != null) {
         Vec3d adjustedFocus = focusPos;
         Vec3d orbitTarget = ClientAnimationRuntime.findCameraOrbitTargetForActor(actorUuid);
         if (orbitTarget != null) {
            afw$clearBodyOrbitTarget();
            Vec3d horizontalDelta = new Vec3d(orbitTarget.x - focusPos.x, 0.0, orbitTarget.z - focusPos.z);
            if (horizontalDelta.isFinite() && horizontalDelta.lengthSquared() >= 1.0E-8) {
               adjustedFocus = focusPos.add(horizontalDelta);
            }
         } else {
            Vec3d bodyTarget = afw$resolveBodyOrbitTarget(actorUuid, activeInstanceId, activeAnimationId, nowTick);
            if (bodyTarget != null) {
               adjustedFocus = afw$resolveSmoothedOrbitTarget(activeInstanceId, activeAnimationId, focusPos, bodyTarget, nowTick, tickDelta);
            } else if (afw$currentSmoothedOrbitTarget != null && afw$currentSmoothedOrbitTarget.isFinite()) {
               adjustedFocus = afw$currentSmoothedOrbitTarget;
            } else if (activeInstanceId == null) {
               afw$clearOrbitSmoothing();
            }
         }

         adjustedFocus = afw$resolveClearThirdPersonFocus(focusedPlayer.getEntityWorld(), adjustedFocus);
         float zoomDistance = AnimationCameraZoom.distance(tickDelta);
         this.setPos(adjustedFocus);
         this.moveBy(-this.clipToSpace(zoomDistance), 0.0F, 0.0F);
      }
   }

   private static Vec3d afw$resolveClearThirdPersonFocus(World world, Vec3d desiredFocus) {
      if (world != null && desiredFocus != null && desiredFocus.isFinite()) {
         if (afw$isThirdPersonFocusClear(world, desiredFocus)) {
            return desiredFocus;
         }

         for (int step = 1; step <= 16; step++) {
            Vec3d candidate = desiredFocus.add(0.0, step * 0.1, 0.0);
            if (afw$isThirdPersonFocusClear(world, candidate)) {
               return candidate;
            }
         }

         for (int ring = 1; ring <= 4; ring++) {
            double distance = ring * 0.25;

            for (int i = 0; i < 8; i++) {
               double angle = (Math.PI * 2) * i / 8.0;
               Vec3d candidate = desiredFocus.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
               if (afw$isThirdPersonFocusClear(world, candidate)) {
                  return candidate;
               }
            }
         }

         return desiredFocus;
      } else {
         return desiredFocus;
      }
   }

   private static boolean afw$isThirdPersonFocusClear(World world, Vec3d focus) {
      if (world != null && focus != null && focus.isFinite()) {
         Box focusBox = Box.of(focus, 0.25, 0.25, 0.25);
         return world.isSpaceEmpty(focusBox) && !afw$intersectsCameraCollisionShape(world, focusBox);
      } else {
         return true;
      }
   }

   private static boolean afw$intersectsCameraCollisionShape(World world, Box focusBox) {
      int minX = MathHelper.floor(focusBox.minX);
      int maxX = MathHelper.floor(focusBox.maxX);
      int minY = MathHelper.floor(focusBox.minY);
      int maxY = MathHelper.floor(focusBox.maxY);
      int minZ = MathHelper.floor(focusBox.minZ);
      int maxZ = MathHelper.floor(focusBox.maxZ);
      ShapeContext shapeContext = ShapeContext.absent();

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               BlockPos pos = new BlockPos(x, y, z);
               BlockState state = world.getBlockState(pos);
               if (!state.isAir()) {
                  VoxelShape shape = state.getCameraCollisionShape(world, pos, shapeContext);
                  if (!shape.isEmpty()) {
                     for (Box shapeBox : shape.getBoundingBoxes()) {
                        if (focusBox.intersects(shapeBox.offset(pos))) {
                           return true;
                        }
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   private static Vec3d afw$resolveBodyOrbitTarget(UUID actorUuid, UUID activeInstanceId, Identifier activeAnimationId, long nowTick) {
      if (actorUuid != null && activeInstanceId != null && activeAnimationId != null) {
         boolean instanceChanged = !Objects.equals(activeInstanceId, afw$bodyOrbitInstanceId);
         boolean animationChanged = !Objects.equals(activeAnimationId, afw$bodyOrbitAnimationId);
         if (instanceChanged || animationChanged) {
            if (instanceChanged) {
               afw$delayBodyOrbitForCurrentInstance = afw$currentSmoothedOrbitTarget == null;
            }

            afw$bodyOrbitInstanceId = activeInstanceId;
            afw$bodyOrbitAnimationId = activeAnimationId;
            afw$bodyOrbitTarget = null;
            afw$resetBodyOrbitValidation();
         }

         if (afw$bodyOrbitTarget != null) {
            afw$validateBodyOrbitTarget(actorUuid, activeInstanceId, activeAnimationId, nowTick);
            return afw$bodyOrbitTarget;
         } else {
            Long startTick = ClientAnimationRuntime.findStartTickForActor(actorUuid);
            if (afw$delayBodyOrbitForCurrentInstance && startTick != null && nowTick - startTick < 4L) {
               return null;
            } else {
               AnimatedCameraPoseTracker.PoseSample sample = afw$resolveMatchingBodyPose(actorUuid, activeInstanceId, activeAnimationId, nowTick);
               if (sample == null) {
                  return null;
               } else if (afw$delayBodyOrbitForCurrentInstance && startTick != null && sample.tick() - startTick < 4L) {
                  return null;
               } else {
                  Vec3d bodyPos = sample.bodyWorldPos();
                  if (bodyPos != null && bodyPos.isFinite()) {
                     afw$bodyOrbitTarget = bodyPos;
                     afw$nextBodyOrbitValidationTick = nowTick + 20L;
                     afw$bodyOrbitMismatchCount = 0;
                     return afw$bodyOrbitTarget;
                  } else {
                     return null;
                  }
               }
            }
         }
      } else {
         afw$clearBodyOrbitTarget();
         return null;
      }
   }

   private static void afw$validateBodyOrbitTarget(UUID actorUuid, UUID activeInstanceId, Identifier activeAnimationId, long nowTick) {
      if (nowTick >= afw$nextBodyOrbitValidationTick) {
         afw$nextBodyOrbitValidationTick = nowTick + 20L;
         AnimatedCameraPoseTracker.PoseSample sample = afw$resolveMatchingBodyPose(actorUuid, activeInstanceId, activeAnimationId, nowTick);
         if (sample != null && sample.bodyWorldPos() != null && sample.bodyWorldPos().isFinite()) {
            Vec3d latestBodyPos = sample.bodyWorldPos();
            if (afw$bodyOrbitTarget.squaredDistanceTo(latestBodyPos) <= 0.25) {
               afw$bodyOrbitMismatchCount = 0;
            } else {
               afw$bodyOrbitMismatchCount++;
               if (afw$bodyOrbitMismatchCount >= 2) {
                  afw$bodyOrbitTarget = latestBodyPos;
                  afw$bodyOrbitMismatchCount = 0;
               }
            }
         }
      }
   }

   private static AnimatedCameraPoseTracker.PoseSample afw$resolveMatchingBodyPose(
      UUID actorUuid, UUID activeInstanceId, Identifier activeAnimationId, long nowTick
   ) {
      AnimatedCameraPoseTracker.PoseSample sample = AnimatedCameraPoseTracker.resolve(actorUuid, nowTick);
      return sample != null && Objects.equals(sample.instanceId(), activeInstanceId) && Objects.equals(sample.animationId(), activeAnimationId) ? sample : null;
   }

   private static Vec3d afw$resolveSmoothedOrbitTarget(
      UUID activeInstanceId, Identifier activeAnimationId, Vec3d fallbackStart, Vec3d desiredTarget, long nowTick, float tickDelta
   ) {
      if (desiredTarget != null && desiredTarget.isFinite()) {
         boolean targetChanged = afw$orbitSmoothTarget == null || afw$orbitSmoothTarget.squaredDistanceTo(desiredTarget) >= 1.0E-6;
         if (!Objects.equals(activeInstanceId, afw$smoothedOrbitInstanceId)
            || !Objects.equals(activeAnimationId, afw$smoothedOrbitAnimationId)
            || targetChanged) {
            afw$smoothedOrbitInstanceId = activeInstanceId;
            afw$smoothedOrbitAnimationId = activeAnimationId;
            afw$orbitSmoothStart = afw$currentSmoothedOrbitTarget != null ? afw$currentSmoothedOrbitTarget : fallbackStart;
            afw$orbitSmoothTarget = desiredTarget;
            afw$orbitSmoothStartTick = nowTick;
         }

         if (afw$orbitSmoothStart != null && afw$orbitSmoothTarget != null) {
            float progress = MathHelper.clamp(((float)(nowTick - afw$orbitSmoothStartTick) + tickDelta) / 6.0F, 0.0F, 1.0F);
            float eased = afw$smootherStep(progress);
            afw$currentSmoothedOrbitTarget = new Vec3d(
               MathHelper.lerp(eased, afw$orbitSmoothStart.x, afw$orbitSmoothTarget.x),
               MathHelper.lerp(eased, afw$orbitSmoothStart.y, afw$orbitSmoothTarget.y),
               MathHelper.lerp(eased, afw$orbitSmoothStart.z, afw$orbitSmoothTarget.z)
            );
            if (progress >= 1.0F) {
               afw$orbitSmoothStart = afw$orbitSmoothTarget;
            }

            return afw$currentSmoothedOrbitTarget;
         } else {
            afw$currentSmoothedOrbitTarget = desiredTarget;
            return desiredTarget;
         }
      } else {
         return fallbackStart;
      }
   }

   private static void afw$clearBodyOrbitTarget() {
      afw$bodyOrbitInstanceId = null;
      afw$bodyOrbitAnimationId = null;
      afw$bodyOrbitTarget = null;
      afw$delayBodyOrbitForCurrentInstance = false;
      afw$resetBodyOrbitValidation();
   }

   private static void afw$resetBodyOrbitValidation() {
      afw$nextBodyOrbitValidationTick = 0L;
      afw$bodyOrbitMismatchCount = 0;
   }

   private static void afw$clearOrbitSmoothing() {
      afw$smoothedOrbitInstanceId = null;
      afw$smoothedOrbitAnimationId = null;
      afw$orbitSmoothStart = null;
      afw$orbitSmoothTarget = null;
      afw$currentSmoothedOrbitTarget = null;
      afw$orbitSmoothStartTick = 0L;
   }

   private static float afw$lerpAngleDegrees(float delta, float start, float end) {
      return start + MathHelper.wrapDegrees(end - start) * delta;
   }

   private static float afw$smootherStep(float value) {
      float clamped = MathHelper.clamp(value, 0.0F, 1.0F);
      return clamped * clamped * clamped * (clamped * (clamped * 6.0F - 15.0F) + 10.0F);
   }

   private static void afw$resetAnimationCameraState(boolean firstPerson) {
      afw$lastValidPosedPose = null;
      afw$lastConstrainedLook = null;
      afw$lastActiveInstanceId = null;
      afw$startAlignInstanceId = null;
      afw$startAlignPendingTarget = false;
      afw$startAlignActive = false;
      afw$clearBodyOrbitTarget();
      afw$clearOrbitSmoothing();
      afw$wasFirstPersonLastFrame = firstPerson;
   }
}
