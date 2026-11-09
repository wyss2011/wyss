package com.nonid.internal.animation.client.camera;

import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

public final class AnimationCameraZoom {
   private static final float DEFAULT_DISTANCE = 4.0F;
   private static final float MIN_DISTANCE = 0.5F;
   private static final float MAX_DISTANCE = 8.0F;
   private static final float SCROLL_STEP = 0.25F;
   private static final float SMOOTHING_PER_TICK = 0.35F;
   private static final float SNAP_EPSILON = 0.005F;
   private static float previousDistance = 4.0F;
   private static float currentDistance = 4.0F;
   private static float targetDistance = 4.0F;

   private AnimationCameraZoom() {
   }

   public static boolean handleScroll(MinecraftClient client, double verticalScroll) {
      if (verticalScroll != 0.0 && isThirdPersonZoomAvailable(client)) {
         float direction = verticalScroll > 0.0 ? -1.0F : 1.0F;
         targetDistance = MathHelper.clamp(targetDistance + direction * 0.25F, 0.5F, 8.0F);
         return true;
      } else {
         return false;
      }
   }

   public static void clientTick(MinecraftClient client) {
      if (!isLocalPlayerAnimating(client)) {
         reset();
      } else {
         previousDistance = currentDistance;
         float delta = targetDistance - currentDistance;
         if (Math.abs(delta) <= 0.005F) {
            currentDistance = targetDistance;
         } else {
            currentDistance = MathHelper.lerp(0.35F, currentDistance, targetDistance);
         }
      }
   }

   public static float distance(float tickDelta) {
      float progress = MathHelper.clamp(tickDelta, 0.0F, 1.0F);
      return MathHelper.lerp(progress, previousDistance, currentDistance);
   }

   public static void reset() {
      previousDistance = 4.0F;
      currentDistance = 4.0F;
      targetDistance = 4.0F;
   }

   public static boolean isLocalPlayerAnimating(MinecraftClient client) {
      return client != null && client.player != null && ClientAnimationRuntime.isActorPendingOrActive(client.player.getUuid());
   }

   private static boolean isThirdPersonZoomAvailable(MinecraftClient client) {
      return isLocalPlayerAnimating(client) && client.currentScreen == null && client.options.getPerspective() != Perspective.FIRST_PERSON;
   }
}
