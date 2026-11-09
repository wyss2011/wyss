package com.nonid.internal.animation.client.compat.wildfire;

public record WildfireGenderState(
   float bustSize,
   float xOffset,
   float yOffset,
   float zOffset,
   float cleavage,
   boolean uniboob,
   boolean hasJacketLayer,
   WildfireGenderState.Side left,
   WildfireGenderState.Side right
) {
   public record Side(
      float physicsX, float physicsY, float physicsSize, float bounceRotation, WildfireGenderState.UvRect baseUv, WildfireGenderState.UvRect overlayUv
   ) {
   }

   public record UvRect(float u1, float v1, float u2, float v2) {
      public static WildfireGenderState.UvRect pixels(int x1, int y1, int x2, int y2) {
         return new WildfireGenderState.UvRect(x1 / 64.0F, y1 / 64.0F, x2 / 64.0F, y2 / 64.0F);
      }
   }
}
