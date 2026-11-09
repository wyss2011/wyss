package com.nonid.internal.animation.client.camera;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public final class FirstPersonLookConstraint {
   private static final double BACKWARD_HARD_DEGREES = 160.0;
   private static final double DOWNWARD_HARD_DEGREES = 60.0;
   private static final double BACKWARD_FULL_LIMIT_UPWARD_PITCH_DEGREES = 20.0;
   private static final double BACKWARD_DISABLE_UPWARD_PITCH_DEGREES = 40.0;
   private static final double MIN_HORIZONTAL_LENGTH_FOR_YAW = 0.001;

   private FirstPersonLookConstraint() {
   }

   public static Vec3d constrain(Vec3d desiredLook, @Nullable AnimatedCameraPoseTracker.PoseSample pose, @Nullable Vec3d previousConstrainedLook) {
      if (desiredLook == null) {
         return Vec3d.ZERO;
      }

      Vec3d normalizedLook = desiredLook.normalize();
      if (pose == null) {
         return normalizedLook;
      }

      Vec3d bodyForward = normalize(pose.bodyForward());
      Vec3d bodyUp = normalize(pose.bodyUp());
      Vec3d bodyRight = normalize(pose.bodyRight());
      if (bodyForward != null && bodyUp != null && bodyRight != null) {
         FirstPersonLookConstraint.LocalAngles desiredAngles = toLocalAngles(normalizedLook, bodyForward, bodyUp, bodyRight);
         if (desiredAngles == null) {
            return normalizedLook;
         }

         double localYaw = desiredAngles.yawDegrees();
         double localPitch = desiredAngles.pitchDegrees();
         FirstPersonLookConstraint.LocalAngles previousAngles = toLocalAngles(previousConstrainedLook, bodyForward, bodyUp, bodyRight);
         if (previousAngles != null) {
            localYaw = unwrapNear(localYaw, previousAngles.yawDegrees());
         }

         double constrainedPitch = constrainDownward(localPitch, 60.0);
         double effectiveBackwardLimit = computeEffectiveBackwardLimit(localPitch);
         double constrainedYaw = constrainSymmetric(localYaw, effectiveBackwardLimit);
         if (Math.abs(constrainedYaw - localYaw) < 1.0E-4 && Math.abs(constrainedPitch - localPitch) < 1.0E-4) {
            return normalizedLook;
         }

         double yawRad = Math.toRadians(constrainedYaw);
         double pitchRad = Math.toRadians(constrainedPitch);
         double correctedHorizontal = Math.cos(pitchRad);
         Vec3d correctedLocal = new Vec3d(Math.sin(yawRad) * correctedHorizontal, Math.sin(pitchRad), Math.cos(yawRad) * correctedHorizontal);
         Vec3d correctedWorld = bodyRight.multiply(correctedLocal.x).add(bodyUp.multiply(correctedLocal.y)).add(bodyForward.multiply(correctedLocal.z));
         return correctedWorld.normalize();
      } else {
         return normalizedLook;
      }
   }

   public static float toMinecraftYaw(Vec3d lookVector, float fallbackYaw) {
      Vec3d normalized = lookVector == null ? Vec3d.ZERO : lookVector.normalize();
      return normalized.horizontalLengthSquared() <= 0.001 ? fallbackYaw : (float)Math.toDegrees(Math.atan2(-normalized.x, normalized.z));
   }

   public static float toMinecraftPitch(Vec3d lookVector) {
      Vec3d normalized = lookVector == null ? Vec3d.ZERO : lookVector.normalize();
      double clampedY = MathHelper.clamp(normalized.y, -1.0, 1.0);
      return (float)(-Math.toDegrees(Math.asin(clampedY)));
   }

   private static double constrainSymmetric(double angleDegrees, double hardLimit) {
      double sign = Math.signum(angleDegrees);
      double absolute = Math.abs(angleDegrees);
      return absolute <= hardLimit ? angleDegrees : sign * hardLimit;
   }

   private static double constrainDownward(double pitchDegrees, double hardLimit) {
      double downward = Math.max(0.0, -pitchDegrees);
      return downward <= hardLimit ? pitchDegrees : -hardLimit;
   }

   private static double computeEffectiveBackwardLimit(double localPitchDegrees) {
      if (localPitchDegrees <= 20.0) {
         return 160.0;
      }

      if (localPitchDegrees >= 40.0) {
         return 180.0;
      }

      double t = (localPitchDegrees - 20.0) / Math.max(1.0E-6, 20.0);
      return MathHelper.lerp(t, 160.0, 180.0);
   }

   @Nullable
   private static Vec3d normalize(@Nullable Vec3d vector) {
      if (vector == null) {
         return null;
      }

      if (vector.lengthSquared() < 1.0E-8) {
         return null;
      }

      Vec3d normalized = vector.normalize();
      return !normalized.isFinite() ? null : normalized;
   }

   @Nullable
   private static FirstPersonLookConstraint.LocalAngles toLocalAngles(@Nullable Vec3d look, Vec3d bodyForward, Vec3d bodyUp, Vec3d bodyRight) {
      Vec3d normalizedLook = normalize(look);
      if (normalizedLook == null) {
         return null;
      }

      double localX = normalizedLook.dotProduct(bodyRight);
      double localY = normalizedLook.dotProduct(bodyUp);
      double localZ = normalizedLook.dotProduct(bodyForward);
      double horizontalLength = Math.sqrt(localX * localX + localZ * localZ);
      double localYaw = Math.toDegrees(Math.atan2(localX, localZ));
      double localPitch = Math.toDegrees(Math.atan2(localY, Math.max(1.0E-6, horizontalLength)));
      return new FirstPersonLookConstraint.LocalAngles(localYaw, localPitch);
   }

   private static double unwrapNear(double angleDegrees, double referenceDegrees) {
      double unwrapped = angleDegrees;

      while (unwrapped - referenceDegrees > 180.0) {
         unwrapped -= 360.0;
      }

      while (unwrapped - referenceDegrees < -180.0) {
         unwrapped += 360.0;
      }

      return unwrapped;
   }

   private record LocalAngles(double yawDegrees, double pitchDegrees) {
   }
}
