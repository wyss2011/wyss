package com.nonid.internal.animation.client.camera;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public final class AnimatedCameraPoseTracker {
   private static final long HOLD_TICKS = 20L;
   private static final Map<UUID, AnimatedCameraPoseTracker.PoseSample> SAMPLES = new ConcurrentHashMap<>();

   private AnimatedCameraPoseTracker() {
   }

   public static void capture(
      UUID actorUuid,
      UUID instanceId,
      Identifier animationId,
      Vec3d worldPos,
      Vec3d bodyWorldPos,
      Vec3d headForward,
      Vec3d headUp,
      Vec3d headRight,
      Vec3d bodyForward,
      Vec3d bodyUp,
      Vec3d bodyRight,
      long tick
   ) {
      if (actorUuid != null && worldPos != null) {
         SAMPLES.put(
            actorUuid,
            new AnimatedCameraPoseTracker.PoseSample(
               instanceId, animationId, worldPos, bodyWorldPos, headForward, headUp, headRight, bodyForward, bodyUp, bodyRight, tick
            )
         );
      }
   }

   @Nullable
   public static AnimatedCameraPoseTracker.PoseSample resolve(UUID actorUuid, long nowTick) {
      AnimatedCameraPoseTracker.PoseSample sample = SAMPLES.get(actorUuid);
      if (sample == null) {
         return null;
      } else if (nowTick - sample.tick() > 20L) {
         SAMPLES.remove(actorUuid, sample);
         return null;
      } else {
         return sample;
      }
   }

   public static void prune(long nowTick) {
      SAMPLES.entrySet().removeIf(entry -> nowTick - entry.getValue().tick() > 20L);
   }

   public static void clear() {
      SAMPLES.clear();
   }

   public record PoseSample(
      UUID instanceId,
      Identifier animationId,
      Vec3d worldPos,
      Vec3d bodyWorldPos,
      Vec3d headForward,
      Vec3d headUp,
      Vec3d headRight,
      Vec3d bodyForward,
      Vec3d bodyUp,
      Vec3d bodyRight,
      long tick
   ) {
   }
}
