package com.nonid.internal.animation.client.camera;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.CollisionView;
import org.jetbrains.annotations.Nullable;

public final class FirstPersonCameraCollision {
   private static final double CAMERA_BOX_SIZE = 0.25;
   private static final double PUSH_EPSILON = 0.005;
   private static final int MAX_PUSH_ITERATIONS = 8;

   private FirstPersonCameraCollision() {
   }

   public static Vec3d resolve(CollisionView world, Vec3d desiredPos) {
      if (world != null && desiredPos != null) {
         Vec3d resolvedPos = desiredPos;

         for (int i = 0; i < 8; i++) {
            Box cameraBox = Box.of(resolvedPos, 0.25, 0.25, 0.25);
            if (world.isSpaceEmpty(cameraBox)) {
               return resolvedPos;
            }

            Vec3d push = computeMinimalPush(world, cameraBox);
            if (push == null || push.lengthSquared() < 1.0E-10) {
               return resolvedPos;
            }

            resolvedPos = resolvedPos.add(push);
         }

         return resolvedPos;
      } else {
         return desiredPos;
      }
   }

   @Nullable
   private static Vec3d computeMinimalPush(CollisionView world, Box cameraBox) {
      double bestDistanceSq = Double.POSITIVE_INFINITY;
      Vec3d bestPush = null;

      for (VoxelShape shape : world.getBlockCollisions(null, cameraBox.expand(0.005))) {
         for (Box blockBox : shape.getBoundingBoxes()) {
            if (cameraBox.intersects(blockBox)) {
               Vec3d candidate = smallestSeparatingPush(cameraBox, blockBox);
               if (candidate != null) {
                  double distanceSq = candidate.lengthSquared();
                  if (distanceSq < bestDistanceSq) {
                     bestDistanceSq = distanceSq;
                     bestPush = candidate;
                  }
               }
            }
         }
      }

      return bestPush;
   }

   @Nullable
   private static Vec3d smallestSeparatingPush(Box cameraBox, Box blockBox) {
      double pushNegX = blockBox.minX - cameraBox.maxX - 0.005;
      double pushPosX = blockBox.maxX - cameraBox.minX + 0.005;
      double pushNegY = blockBox.minY - cameraBox.maxY - 0.005;
      double pushPosY = blockBox.maxY - cameraBox.minY + 0.005;
      double pushNegZ = blockBox.minZ - cameraBox.maxZ - 0.005;
      double pushPosZ = blockBox.maxZ - cameraBox.minZ + 0.005;
      Vec3d[] candidates = new Vec3d[]{
         new Vec3d(pushNegX, 0.0, 0.0),
         new Vec3d(pushPosX, 0.0, 0.0),
         new Vec3d(0.0, pushNegY, 0.0),
         new Vec3d(0.0, pushPosY, 0.0),
         new Vec3d(0.0, 0.0, pushNegZ),
         new Vec3d(0.0, 0.0, pushPosZ)
      };
      Vec3d best = null;
      double bestDistanceSq = Double.POSITIVE_INFINITY;

      for (Vec3d candidate : candidates) {
         if (!(candidate.lengthSquared() <= 1.0E-10)) {
            double distanceSq = candidate.lengthSquared();
            if (distanceSq < bestDistanceSq) {
               bestDistanceSq = distanceSq;
               best = candidate;
            }
         }
      }

      return best;
   }
}
