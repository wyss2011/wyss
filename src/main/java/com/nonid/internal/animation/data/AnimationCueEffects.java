package com.nonid.internal.animation.data;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

public final class AnimationCueEffects {
   public static final String REACTIVE_IMPACT = "reactiveimpact";
   public static final String REACTIVE_IMPACT_SILENT = "reactiveimpact_silent";

   private AnimationCueEffects() {
   }

   public static boolean isReactiveImpact(@Nullable String effect) {
      String normalized = normalize(effect);
      return "reactiveimpact".equals(normalized) || "reactiveimpact_silent".equals(normalized);
   }

   public static boolean isSilentReactiveImpact(@Nullable String effect) {
      return "reactiveimpact_silent".equals(normalize(effect));
   }

   @Nullable
   public static String timingFamily(@Nullable String effect) {
      String normalized = normalize(effect);
      return "reactiveimpact_silent".equals(normalized) ? "reactiveimpact" : normalized;
   }

   @Nullable
   private static String normalize(@Nullable String effect) {
      return effect != null && !effect.isBlank() ? effect.trim().toLowerCase(Locale.ROOT) : null;
   }
}
