package com.nonid.data;

import com.nonid.internal.animation.data.AnimationGameplayMetadata;
import com.nonid.internal.animation.util.AnimationIds;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.Identifier;

public final class NonPeakStages {
   private static volatile Map<Identifier, Integer> syncedPeakStages = Map.of();

   private NonPeakStages() {
   }

   public static Integer getPeakStage(Identifier animationId) {
      if (animationId == null) {
         return null;
      }

      Identifier base = baseAnimationId(animationId);
      Integer synced = syncedPeakStages.get(base);
      return synced != null ? synced : AnimationGameplayMetadata.peakStage(base);
   }

   public static Map<String, Integer> snapshotPeakStagesForSync() {
      Map<String, Integer> out = new LinkedHashMap<>();
      AnimationGameplayMetadata.peakStages().forEach((id, stage) -> {
         if (id != null && stage != null && stage > 0) {
            out.put(id.toString(), stage);
         }
      });
      return out;
   }

   public static void applySyncedPeakStages(Map<String, Integer> synced) {
      if (synced != null && !synced.isEmpty()) {
         Map<Identifier, Integer> parsed = new HashMap<>();
         synced.forEach((rawId, stage) -> {
            if (rawId != null && stage != null && stage > 0) {
               Identifier id = Identifier.tryParse(rawId);
               if (id != null) {
                  parsed.put(baseAnimationId(id), stage);
               }
            }
         });
         syncedPeakStages = Map.copyOf(parsed);
      } else {
         syncedPeakStages = Map.of();
      }
   }

   public static void clearSyncedPeakStages() {
      syncedPeakStages = Map.of();
   }

   public static Identifier baseAnimationId(Identifier id) {
      return AnimationIds.baseId(id);
   }

   public static Integer stageNumberFromId(Identifier stageId) {
      return stageId == null ? null : AnimationIds.stageNumberOrDefault(stageId, 1);
   }
}
