package com.nonid.internal.animation.server;

import java.util.Map;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record AnimationStartContext(
   Map<String, String> addonMetadata, @Nullable AnimationSessionData sessionData, @Nullable Identifier joinPredecessor, boolean queueContinuation
) {
   private static final AnimationStartContext EMPTY = new AnimationStartContext(Map.of(), null, null, false);

   public AnimationStartContext {
      addonMetadata = addonMetadata != null && !addonMetadata.isEmpty() ? Map.copyOf(addonMetadata) : Map.of();
   }

   public static AnimationStartContext empty() {
      return EMPTY;
   }

   public static AnimationStartContext addonMetadata(@Nullable Map<String, String> metadata) {
      return metadata != null && !metadata.isEmpty() ? new AnimationStartContext(metadata, null, null, false) : EMPTY;
   }

   public AnimationStartContext withSessionData(@Nullable AnimationSessionData data) {
      return new AnimationStartContext(this.addonMetadata, data, this.joinPredecessor, this.queueContinuation);
   }

   public AnimationStartContext withJoinPredecessor(@Nullable Identifier predecessor) {
      return new AnimationStartContext(this.addonMetadata, this.sessionData, predecessor, this.queueContinuation);
   }

   public AnimationStartContext asQueueContinuation() {
      return this.queueContinuation ? this : new AnimationStartContext(this.addonMetadata, this.sessionData, this.joinPredecessor, true);
   }

   public boolean isJoinReplacement() {
      return this.joinPredecessor != null;
   }
}
