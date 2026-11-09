package com.nonid.internal.animation.server;

import com.nonid.NeedsOfNature;
import com.nonid.internal.animation.data.AnimationGameplayMetadata;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.util.AnimationIds;
import java.util.Objects;
import net.minecraft.util.Identifier;

final class AnimationStageTiming {
   static final long NO_AUTOMATIC_END = -1L;

   private AnimationStageTiming() {
   }

   static long resolveDurationTicks(Identifier animationId, AnimationStageInfo stage, double speed) {
      if (stage == null) {
         return -1L;
      } else {
         double safeSpeed = Double.isFinite(speed) && speed > 0.0 ? speed : 1.0;
         if (!stage.loop()) {
            double authoredTicks = exactCycleTicks(stage);
            return authoredTicks <= 0.0 ? -1L : Math.max(1L, (long)Math.ceil(authoredTicks / safeSpeed));
         } else {
            long targetTicks = resolveLoopTargetTicks(animationId, stage.animationId());
            if (targetTicks < 0L) {
               return -1L;
            } else {
               double cycleTicks = exactCycleTicks(stage);
               if (cycleTicks <= 0.0) {
                  return targetTicks;
               } else {
                  double worldCycleTicks = cycleTicks / safeSpeed;
                  if (Double.isFinite(worldCycleTicks) && !(worldCycleTicks <= 0.0)) {
                     long cycles = Math.max(1L, Math.round(targetTicks / worldCycleTicks));
                     return Math.max(1L, Math.round(cycles * worldCycleTicks));
                  } else {
                     return targetTicks;
                  }
               }
            }
         }
      }
   }

   private static long resolveLoopTargetTicks(Identifier animationId, Identifier stageId) {
      Identifier baseId = AnimationIds.baseId(animationId == null ? stageId : animationId);
      Integer seconds = AnimationGameplayMetadata.stageSeconds(stageId);
      if (seconds == null) {
         seconds = AnimationGameplayMetadata.stageSeconds(baseId);
      }

      if (seconds != null && seconds < 0) {
         return -1L;
      }

      if (seconds == null) {
         Integer peakStage = AnimationGameplayMetadata.peakStage(baseId);
         seconds = Objects.equals(AnimationIds.stageNumber(stageId), peakStage)
            ? NeedsOfNature.getConfig().getPeakLoopProgressSeconds()
            : NeedsOfNature.getConfig().getLoopProgressSeconds();
      }

      double multiplier = AnimationGameplayMetadata.stageDurationMultiplier(stageId);
      return Math.max(1L, Math.round(seconds.intValue() * 20.0 * multiplier));
   }

   private static double exactCycleTicks(AnimationStageInfo stage) {
      double metadataTicks = AnimationGameplayMetadata.cycleTicks(stage.animationId());
      if (metadataTicks > 0.0) {
         return metadataTicks;
      } else {
         return Double.isFinite(stage.cycleTicks()) && stage.cycleTicks() > 0.0 ? stage.cycleTicks() : Math.max(0L, stage.lengthTicks());
      }
   }
}
