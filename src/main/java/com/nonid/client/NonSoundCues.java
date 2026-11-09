package com.nonid.client;

import com.nonid.data.NonPeakStages;
import com.nonid.internal.animation.data.AnimationCueEffects;
import java.util.Locale;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

public final class NonSoundCues {
   private static final int DRY_IMPACT_COUNT = 11;
   private static final int WET_IMPACT_COUNT = 16;
   private static final String[] REACTIVE_DRY = new String[]{"impactdry01", "impactdry02", "impactdry03"};
   private static final String[] REACTIVE_WET_IMPACT = new String[]{"impactwet01", "impactwet02", "impactwet03"};
   private static final String[] REACTIVE_WET = new String[]{"wet04", "wet06", "wet07"};
   private static final String PEAK_REACTIVE_SOUND = "shot_in01";
   private static final String BIRTH_SOUND = "needsofnature:pop";

   private NonSoundCues() {
   }

   public static Identifier resolveSoundId(String effect, LivingEntity anchor, Identifier stageAnimationId, int stageIndex) {
      if (effect != null && !effect.isBlank()) {
         if (effect.indexOf(58) >= 0) {
            Identifier direct = Identifier.tryParse(effect);
            if (direct != null) {
               if ("minecraft".equals(direct.getNamespace()) && isLocalEffectPath(direct.getPath())) {
                  return Identifier.of("needsofnature", direct.getPath());
               }

               return direct;
            }
         }

         String resolved = mapEffect(effect, anchor, stageAnimationId, stageIndex);
         if (resolved == null) {
            return null;
         }

         if (resolved.indexOf(58) >= 0) {
            Identifier directResolved = Identifier.tryParse(resolved);
            if (directResolved != null) {
               if ("minecraft".equals(directResolved.getNamespace()) && isLocalEffectPath(directResolved.getPath())) {
                  return Identifier.of("needsofnature", directResolved.getPath());
               }

               return directResolved;
            }
         }

         return Identifier.of("needsofnature", resolved);
      } else {
         return null;
      }
   }

   public static boolean isPeakReactiveImpactCue(String effect, Identifier stageAnimationId, int stageIndex) {
      return !AnimationCueEffects.isReactiveImpact(effect) ? false : isPeakStage(stageAnimationId, stageIndex);
   }

   public static boolean isPeakAnimationStage(Identifier stageAnimationId, int stageIndex) {
      return isPeakStage(stageAnimationId, stageIndex);
   }

   public static boolean isActionSoundId(Identifier soundId) {
      return soundId != null && "needsofnature".equals(soundId.getNamespace()) && isLocalEffectPath(soundId.getPath());
   }

   private static String mapEffect(String effect, LivingEntity anchor, Identifier stageAnimationId, int stageIndex) {
      if (effect.equals("impactdry")) {
         return pickRandom("impactdry", 11, anchor);
      } else if (effect.equals("impactwet")) {
         return pickRandom("impactwet", 16, anchor);
      } else if ("reactiveimpact".equalsIgnoreCase(effect)) {
         return pickReactiveImpact(anchor, stageAnimationId, stageIndex);
      } else if (effect.equals("birth")) {
         return "needsofnature:pop";
      } else {
         return !effect.startsWith("impactdry")
               && !effect.startsWith("impactwet")
               && !effect.startsWith("wet")
               && !effect.startsWith("shot_in")
               && !effect.startsWith("shot_out")
               && !effect.startsWith("motion")
               && !effect.startsWith("retract")
            ? null
            : effect;
      }
   }

   private static String pickRandom(String prefix, int count, LivingEntity anchor) {
      Random random = anchor != null ? anchor.getEntityWorld().getRandom() : Random.create();
      int index = random.nextInt(count) + 1;
      return prefix + String.format(Locale.ROOT, "%02d", index);
   }

   private static String pickReactiveImpact(LivingEntity anchor, Identifier stageAnimationId, int stageIndex) {
      if (isPeakStage(stageAnimationId, stageIndex)) {
         return "shot_in01";
      } else {
         float ratio = NonHudOverlay.getLiquidFillRatio();
         if (ratio < 0.1F) {
            return pickFromList(REACTIVE_DRY, anchor);
         } else {
            return ratio > 0.8F ? pickFromList(REACTIVE_WET, anchor) : pickFromList(REACTIVE_WET_IMPACT, anchor);
         }
      }
   }

   private static boolean isPeakStage(Identifier stageAnimationId, int stageIndex) {
      if (stageAnimationId == null) {
         return false;
      }

      if (stageIndex < 0) {
         return false;
      }

      if (NonPeakStages.stageNumberFromId(stageAnimationId) == null) {
         return false;
      }

      Identifier baseAnimationId = NonPeakStages.baseAnimationId(stageAnimationId);
      Integer peakStage = NonPeakStages.getPeakStage(baseAnimationId);
      if (peakStage == null) {
         return false;
      }

      int logicalStage = stageIndex + 1;
      return logicalStage == peakStage;
   }

   private static String pickFromList(String[] options, LivingEntity anchor) {
      if (options != null && options.length != 0) {
         Random random = anchor != null ? anchor.getEntityWorld().getRandom() : Random.create();
         return options[random.nextInt(options.length)];
      } else {
         return null;
      }
   }

   private static boolean isLocalEffectPath(String path) {
      return path.startsWith("impactdry")
         || path.startsWith("impactwet")
         || path.startsWith("wet")
         || path.startsWith("shot_in")
         || path.startsWith("shot_out")
         || path.startsWith("motion")
         || path.startsWith("retract");
   }
}
