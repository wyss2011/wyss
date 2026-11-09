package com.nonid.internal.animation.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import org.jetbrains.annotations.Nullable;

public final class WeightedPicker {
   private WeightedPicker() {
   }

   @Nullable
   public static <T> T pick(List<T> candidates, ToDoubleFunction<T> weight, double randomUnit) {
      if (candidates != null && !candidates.isEmpty()) {
         List<T> valid = new ArrayList<>(candidates.size());
         double total = 0.0;

         for (T candidate : candidates) {
            if (candidate != null) {
               valid.add(candidate);
               total += sanitizeWeight(weight.applyAsDouble(candidate));
            }
         }

         if (valid.isEmpty()) {
            return null;
         }

         double unit = Double.isFinite(randomUnit) ? Math.max(0.0, Math.min(Math.nextDown(1.0), randomUnit)) : 0.0;
         if (Double.isFinite(total) && !(total <= 0.0)) {
            double roll = unit * total;
            double running = 0.0;

            for (T candidate : valid) {
               running += sanitizeWeight(weight.applyAsDouble(candidate));
               if (roll < running) {
                  return candidate;
               }
            }

            return valid.getLast();
         } else {
            return valid.get(Math.min(valid.size() - 1, (int)Math.floor(unit * valid.size())));
         }
      } else {
         return null;
      }
   }

   public static double sanitizeWeight(double value) {
      return Double.isFinite(value) && value > 0.0 ? value : 1.0;
   }
}
