package com.nonid.internal.animation.util;

public final class StageTimeWarp {
   private static final double EPSILON_TICKS = 1.0E-4;

   private StageTimeWarp() {
   }

   public static double offsetSecondsToTicks(double offsetSeconds) {
      return !Double.isFinite(offsetSeconds) ? 0.0 : offsetSeconds * 20.0;
   }

   public static double clampOffsetSeconds(double cycleTicks, double offsetSeconds) {
      return clampOffsetTicks(cycleTicks, offsetSecondsToTicks(offsetSeconds)) / 20.0;
   }

   public static double clampOffsetTicks(double cycleTicks, double offsetTicks) {
      if (!Double.isFinite(cycleTicks) || cycleTicks <= 0.0) {
         return 0.0;
      }

      if (!Double.isFinite(offsetTicks)) {
         return 0.0;
      }

      double midpoint = cycleTicks * 0.5;
      double minOffset = -midpoint + 1.0E-4;
      double maxOffset = midpoint - 1.0E-4;
      return Math.max(minOffset, Math.min(maxOffset, offsetTicks));
   }

   public static boolean hasWarp(double cycleTicks, double offsetTicks) {
      return Double.isFinite(cycleTicks) && !(cycleTicks <= 0.0) ? Math.abs(clampOffsetTicks(cycleTicks, offsetTicks)) > 1.0E-4 : false;
   }

   public static double authoredToWarpedCycleTicks(double authoredTicks, double cycleTicks, double offsetTicks) {
      if (!hasWarp(cycleTicks, offsetTicks)) {
         return clampCyclePosition(authoredTicks, cycleTicks);
      }

      double position = clampCyclePosition(authoredTicks, cycleTicks);
      double midpoint = cycleTicks * 0.5;
      double shiftedMidpoint = midpoint + clampOffsetTicks(cycleTicks, offsetTicks);
      return position <= midpoint
         ? position * (shiftedMidpoint / midpoint)
         : shiftedMidpoint + (position - midpoint) * ((cycleTicks - shiftedMidpoint) / (cycleTicks - midpoint));
   }

   public static double warpedToAuthoredCycleTicks(double warpedTicks, double cycleTicks, double offsetTicks) {
      if (!hasWarp(cycleTicks, offsetTicks)) {
         return clampCyclePosition(warpedTicks, cycleTicks);
      }

      double position = clampCyclePosition(warpedTicks, cycleTicks);
      double midpoint = cycleTicks * 0.5;
      double shiftedMidpoint = midpoint + clampOffsetTicks(cycleTicks, offsetTicks);
      return position <= shiftedMidpoint
         ? position * (midpoint / shiftedMidpoint)
         : midpoint + (position - shiftedMidpoint) * ((cycleTicks - midpoint) / (cycleTicks - shiftedMidpoint));
   }

   private static double clampCyclePosition(double ticks, double cycleTicks) {
      if (!Double.isFinite(cycleTicks) || cycleTicks <= 0.0) {
         return 0.0;
      }

      if (!Double.isFinite(ticks)) {
         return 0.0;
      }

      double position = ticks % cycleTicks;
      if (position < 0.0) {
         position += cycleTicks;
      }

      return position > cycleTicks ? cycleTicks : position;
   }
}
