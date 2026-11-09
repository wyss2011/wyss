package com.nonid.internal.animation.client.sound;

import com.nonid.api.client.NonAnimationSoundEvents;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class AnimationSoundAnticipation {
   private static final int MAX_LOOKAHEAD_MILLIS = 1000;
   private static final AnimationSoundAnticipation.Listener NO_OP = new AnimationSoundAnticipation.Listener() {
      @Override
      public int lookaheadMillis() {
         return 0;
      }

      @Override
      public void anticipate(NonAnimationSoundEvents.SoundContext context) {
      }

      @Override
      public boolean allowNextStageLookahead(Identifier currentStageId, int stageIndex) {
         return true;
      }
   };
   private static volatile AnimationSoundAnticipation.Listener listener = NO_OP;

   private AnimationSoundAnticipation() {
   }

   public static void setListener(@Nullable AnimationSoundAnticipation.Listener nextListener) {
      listener = nextListener == null ? NO_OP : nextListener;
   }

   public static int lookaheadMillis() {
      return Math.max(0, Math.min(1000, listener.lookaheadMillis()));
   }

   public static void anticipate(NonAnimationSoundEvents.SoundContext context) {
      if (context != null) {
         listener.anticipate(context);
      }
   }

   public static boolean allowNextStageLookahead(Identifier currentStageId, int stageIndex) {
      return listener.allowNextStageLookahead(currentStageId, stageIndex);
   }

   public interface Listener {
      int lookaheadMillis();

      void anticipate(NonAnimationSoundEvents.SoundContext var1);

      default boolean allowNextStageLookahead(Identifier currentStageId, int stageIndex) {
         return true;
      }
   }
}
