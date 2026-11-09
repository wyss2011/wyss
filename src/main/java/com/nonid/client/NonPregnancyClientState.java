package com.nonid.client;

import com.nonid.PregnancyCyclePhase;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonPregnancyClientState {
   @Nullable
   private static Identifier pregnantEntityTypeId;
   private static boolean cycleEnabled;
   private static int cycleDay;
   private static PregnancyCyclePhase cyclePhase = PregnancyCyclePhase.NORMAL;

   private NonPregnancyClientState() {
   }

   public static void setPregnantEntityTypeId(@Nullable Identifier entityTypeId) {
      pregnantEntityTypeId = entityTypeId;
   }

   @Nullable
   public static Identifier getPregnantEntityTypeId() {
      return pregnantEntityTypeId;
   }

   public static void setCycleState(boolean enabled, int day, PregnancyCyclePhase phase) {
      cycleEnabled = enabled;
      cycleDay = enabled ? Math.max(1, Math.min(30, day)) : 0;
      cyclePhase = enabled && phase != null ? phase : PregnancyCyclePhase.NORMAL;
   }

   public static boolean isCycleEnabled() {
      return cycleEnabled;
   }

   public static int getCycleDay() {
      return cycleDay;
   }

   public static PregnancyCyclePhase getCyclePhase() {
      return cyclePhase;
   }

   public static void clear() {
      pregnantEntityTypeId = null;
      cycleEnabled = false;
      cycleDay = 0;
      cyclePhase = PregnancyCyclePhase.NORMAL;
   }
}
