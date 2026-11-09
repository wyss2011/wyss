package com.nonid;

import com.nonid.internal.animation.api.AnimationService;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonAnimationMatcher {
   private NonAnimationMatcher() {
   }

   @Nullable
   public static AnimationService.MatchedAnimation findAutomatic(
      ServerWorld world,
      List<? extends Entity> actors,
      @Nullable Set<String> requiredTags,
      @Nullable Entity positionAnchor,
      boolean requireStartEligibility,
      @Nullable Entity requiredActiveActor,
      @Nullable Set<Identifier> excludedAnimationIds
   ) {
      return findAutomatic(world, actors, requiredTags, positionAnchor, requireStartEligibility, requiredActiveActor, excludedAnimationIds, null, null, false);
   }

   @Nullable
   public static AnimationService.MatchedAnimation findAutomatic(
      ServerWorld world,
      List<? extends Entity> actors,
      @Nullable Set<String> requiredTags,
      @Nullable Entity positionAnchor,
      boolean requireStartEligibility,
      @Nullable Entity requiredActiveActor,
      @Nullable Set<Identifier> excludedAnimationIds,
      @Nullable AnimationService.AnimationCandidateFilter additionalFilter,
      @Nullable Identifier joinPredecessorAnimationId
   ) {
      return findAutomatic(
         world,
         actors,
         requiredTags,
         positionAnchor,
         requireStartEligibility,
         requiredActiveActor,
         excludedAnimationIds,
         additionalFilter,
         joinPredecessorAnimationId,
         false
      );
   }

   @Nullable
   public static AnimationService.MatchedAnimation findAutomaticAttack(
      ServerWorld world,
      List<? extends Entity> actors,
      @Nullable Set<String> requiredTags,
      @Nullable Entity positionAnchor,
      boolean requireStartEligibility,
      @Nullable Entity requiredActiveActor,
      @Nullable Set<Identifier> excludedAnimationIds
   ) {
      return findAutomatic(world, actors, requiredTags, positionAnchor, requireStartEligibility, requiredActiveActor, excludedAnimationIds, null, null, true);
   }

   @Nullable
   public static AnimationService.MatchedAnimation findAutomaticAttack(
      ServerWorld world,
      List<? extends Entity> actors,
      @Nullable Set<String> requiredTags,
      @Nullable Entity positionAnchor,
      boolean requireStartEligibility,
      @Nullable Entity requiredActiveActor,
      @Nullable Set<Identifier> excludedAnimationIds,
      @Nullable AnimationService.AnimationCandidateFilter additionalFilter,
      @Nullable Identifier joinPredecessorAnimationId
   ) {
      return findAutomatic(
         world,
         actors,
         requiredTags,
         positionAnchor,
         requireStartEligibility,
         requiredActiveActor,
         excludedAnimationIds,
         additionalFilter,
         joinPredecessorAnimationId,
         true
      );
   }

   @Nullable
   private static AnimationService.MatchedAnimation findAutomatic(
      ServerWorld world,
      List<? extends Entity> actors,
      @Nullable Set<String> requiredTags,
      @Nullable Entity positionAnchor,
      boolean requireStartEligibility,
      @Nullable Entity requiredActiveActor,
      @Nullable Set<Identifier> excludedAnimationIds,
      @Nullable AnimationService.AnimationCandidateFilter additionalFilter,
      @Nullable Identifier joinPredecessorAnimationId,
      boolean attackSelection
   ) {
      AnimationService.AnimationCandidateFilter filter = (candidateWorld, definition, candidateActors) -> (!attackSelection || definition.attackEligible())
         && NonInjectorMatchPolicy.allowsAutomaticNonMatch(candidateWorld, definition, candidateActors)
         && (additionalFilter == null || additionalFilter.test(candidateWorld, definition, candidateActors));
      return AnimationService.findAnimationForActors(
         world,
         actors,
         new AnimationService.MatchOptions(
            requiredTags == null ? Set.of() : requiredTags,
            positionAnchor,
            requireStartEligibility,
            requiredActiveActor,
            excludedAnimationIds == null ? Set.of() : excludedAnimationIds,
            filter,
            joinPredecessorAnimationId
         )
      );
   }

   public static List<AnimationService.EligibleAnimation> findEligibleAutomatic(
      ServerWorld world,
      List<? extends Entity> actors,
      @Nullable Set<String> requiredTags,
      @Nullable Entity positionAnchor,
      boolean requireStartEligibility,
      @Nullable Entity requiredActiveActor,
      @Nullable Set<Identifier> excludedAnimationIds,
      @Nullable Identifier joinPredecessorAnimationId
   ) {
      return findEligibleAutomatic(
         world, actors, requiredTags, positionAnchor, requireStartEligibility, requiredActiveActor, excludedAnimationIds, joinPredecessorAnimationId, false
      );
   }

   public static List<AnimationService.EligibleAnimation> findEligibleAutomaticAttack(
      ServerWorld world,
      List<? extends Entity> actors,
      @Nullable Set<String> requiredTags,
      @Nullable Entity positionAnchor,
      boolean requireStartEligibility,
      @Nullable Entity requiredActiveActor,
      @Nullable Set<Identifier> excludedAnimationIds,
      @Nullable Identifier joinPredecessorAnimationId
   ) {
      return findEligibleAutomatic(
         world, actors, requiredTags, positionAnchor, requireStartEligibility, requiredActiveActor, excludedAnimationIds, joinPredecessorAnimationId, true
      );
   }

   private static List<AnimationService.EligibleAnimation> findEligibleAutomatic(
      ServerWorld world,
      List<? extends Entity> actors,
      @Nullable Set<String> requiredTags,
      @Nullable Entity positionAnchor,
      boolean requireStartEligibility,
      @Nullable Entity requiredActiveActor,
      @Nullable Set<Identifier> excludedAnimationIds,
      @Nullable Identifier joinPredecessorAnimationId,
      boolean attackSelection
   ) {
      AnimationService.AnimationCandidateFilter filter = (candidateWorld, definition, candidateActors) -> (!attackSelection || definition.attackEligible())
         && NonInjectorMatchPolicy.allowsAutomaticNonMatch(candidateWorld, definition, candidateActors);
      return AnimationService.findEligibleAnimationsForActors(
         world,
         actors,
         new AnimationService.MatchOptions(
            requiredTags == null ? Set.of() : requiredTags,
            positionAnchor,
            requireStartEligibility,
            requiredActiveActor,
            excludedAnimationIds == null ? Set.of() : excludedAnimationIds,
            filter,
            joinPredecessorAnimationId
         )
      );
   }
}
