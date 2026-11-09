package com.nonid.internal.animation.util;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class AnimationIds {
   private AnimationIds() {
   }

   @Nullable
   public static Identifier baseId(@Nullable Identifier animationId) {
      if (animationId == null) {
         return null;
      }

      Integer stage = stageNumber(animationId);
      if (stage == null) {
         return animationId;
      }

      String path = animationId.getPath();
      return Identifier.of(animationId.getNamespace(), path.substring(0, path.lastIndexOf(".p")));
   }

   @Nullable
   public static Integer stageNumber(@Nullable Identifier animationId) {
      if (animationId == null) {
         return null;
      }

      String path = animationId.getPath();
      int marker = path.lastIndexOf(".p");
      if (marker > 0 && marker + 2 < path.length()) {
         String raw = path.substring(marker + 2);
         int value = 0;

         for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < '0' || c > '9') {
               return null;
            }

            int digit = c - '0';
            if (value > (Integer.MAX_VALUE - digit) / 10) {
               return null;
            }

            value = value * 10 + digit;
         }

         return value > 0 ? value : null;
      } else {
         return null;
      }
   }

   public static int stageNumberOrDefault(@Nullable Identifier animationId, int fallback) {
      Integer stage = stageNumber(animationId);
      return stage == null ? fallback : stage;
   }

   public static Identifier stageId(Identifier animationId, int stage) {
      if (animationId == null) {
         throw new IllegalArgumentException("animationId cannot be null");
      }

      if (stage <= 0) {
         throw new IllegalArgumentException("stage must be positive");
      }

      Identifier base = baseId(animationId);
      return Identifier.of(base.getNamespace(), base.getPath() + ".p" + stage);
   }
}
