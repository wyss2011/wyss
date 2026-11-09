package com.nonid.internal.animation.api;

import com.nonid.api.animation.NonAnimationSession;
import com.nonid.api.animation.NonAnimationStage;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.server.ServerAnimationController;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class NonAnimationViews {
   private NonAnimationViews() {
   }

   @Nullable
   public static NonAnimationStage stage(@Nullable AnimationStageInfo stage) {
      return stage == null
         ? null
         : new NonAnimationStage(
            stage.animationId(),
            stage.loop(),
            stage.lengthTicks(),
            stage.allowJoin(),
            stage.speed(),
            stage.playbackAnimationId(),
            stage.actorProps()
               .stream()
               .map(prop -> new NonAnimationStage.ActorProp(prop.actorKey(), prop.propLeftItemId(), prop.propRightItemId(), prop.propFloorItemId()))
               .toList(),
            stage.cycleMidpointOffsetSeconds(),
            stage.cycleTicks()
         );
   }

   public static List<NonAnimationStage> stages(@Nullable List<AnimationStageInfo> stages) {
      return stages != null && !stages.isEmpty() ? stages.stream().map(NonAnimationViews::stage).toList() : List.of();
   }

   public static NonAnimationSession session(ServerAnimationController.ActiveInstanceSnapshot snapshot) {
      List<AnimationStageInfo> stages = snapshot.stages();
      NonAnimationStage currentStage = null;
      if (stages != null && !stages.isEmpty()) {
         int index = Math.max(0, Math.min(snapshot.stageIndex(), stages.size() - 1));
         currentStage = stage(stages.get(index));
      }

      return new NonAnimationSession(
         snapshot.instanceId(),
         snapshot.animationId(),
         snapshot.actorUuids(),
         currentStage,
         snapshot.damageBehavior(),
         snapshot.ignoreAttackers(),
         snapshot.metadata()
      );
   }
}
