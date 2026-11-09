package com.nonid.api.animation;

import com.nonid.internal.animation.api.AnimationService;
import com.nonid.internal.animation.api.NonAnimationViews;
import com.nonid.internal.animation.data.AnimationDefinitions;
import com.nonid.internal.animation.server.ServerAnimationController;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonAnimationApi {
   private NonAnimationApi() {
   }

   public static List<NonAnimationDefinition> getLoadedDefinitions() {
      return AnimationDefinitions.getLoadedDefinitionsSnapshot().stream().map(NonAnimationApi::definitionView).toList();
   }

   @Nullable
   public static NonAnimationDefinition getDefinition(Identifier animationId) {
      return definitionView(AnimationDefinitions.getDefinition(animationId));
   }

   public static boolean isEnabled(Identifier animationId) {
      return AnimationDefinitions.isAnimationEnabled(animationId);
   }

   public static boolean hasAnimationForEntityType(@Nullable EntityType<?> type, boolean baby) {
      return AnimationService.hasEnabledGenericAnimationForEntityType(type, baby);
   }

   public static List<NonAnimationCandidate> findCandidates(ServerWorld world, List<? extends Entity> actors, @Nullable NonAnimationApi.MatchOptions options) {
      if (world != null && actors != null && !actors.isEmpty()) {
         NonAnimationApi.MatchOptions safe = options == null ? NonAnimationApi.MatchOptions.defaults() : options;
         AnimationService.MatchOptions internalOptions = internalOptions(safe);
         List<NonAnimationCandidate> result = new ArrayList<>();

         for (AnimationService.EligibleAnimation candidate : AnimationService.findEligibleAnimationsForActors(world, actors, internalOptions)) {
            AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(candidate.animationId());
            if (definition != null) {
               boolean eligible = AnimationService.isAnimationStartEligible(world, definition, actors, candidate.actorKeys(), safe.positionAnchor());
               result.add(new NonAnimationCandidate(definitionView(definition), candidate.actorKeys(), eligible));
            }
         }

         result.sort(Comparator.comparing(candidatex -> candidatex.definition().id().toString()));
         return List.copyOf(result);
      } else {
         return List.of();
      }
   }

   @Nullable
   public static NonAnimationCandidate findMatch(ServerWorld world, List<? extends Entity> actors, @Nullable NonAnimationApi.MatchOptions options) {
      if (world != null && actors != null && !actors.isEmpty()) {
         NonAnimationApi.MatchOptions safe = options == null ? NonAnimationApi.MatchOptions.defaults() : options;
         AnimationService.MatchedAnimation match = AnimationService.findAnimationForActors(world, actors, internalOptions(safe));
         if (match == null) {
            return null;
         }

         AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(match.animationId());
         if (definition == null) {
            return null;
         }

         boolean eligible = AnimationService.isAnimationStartEligible(world, definition, actors, match.actorKeys(), safe.positionAnchor());
         return new NonAnimationCandidate(definitionView(definition), match.actorKeys(), eligible);
      } else {
         return null;
      }
   }

   public static boolean canStart(
      ServerWorld world, Identifier animationId, List<? extends Entity> actors, @Nullable List<String> actorKeys, @Nullable Entity positionAnchor
   ) {
      AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(animationId);
      if (definition == null) {
         return false;
      } else {
         return actorKeys == null
            ? AnimationService.isAnimationStartEligible(world, definition, actors, positionAnchor)
            : AnimationService.isAnimationStartEligible(world, definition, actors, actorKeys, positionAnchor);
      }
   }

   @Nullable
   public static UUID start(
      ServerWorld world,
      Identifier animationId,
      List<? extends Entity> actors,
      @Nullable List<String> actorKeys,
      @Nullable NonAnimationApi.StartOptions options
   ) {
      AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(animationId);
      if (definition == null) {
         return null;
      }

      NonAnimationApi.StartOptions safe = options == null ? NonAnimationApi.StartOptions.defaults() : options;
      return AnimationService.startAnimationNow(
         world,
         animationId,
         actors,
         actorKeys,
         definition.stages(),
         new AnimationService.StartOptions(
            safe.damageBehavior(),
            safe.ignoreAttackers(),
            safe.positionAnchor(),
            safe.requester(),
            safe.metadata(),
            safe.forceDebugChat(),
            safe.allowMultiplePlayers()
         )
      );
   }

   @Nullable
   public static UUID startMatched(
      ServerWorld world,
      List<? extends Entity> actors,
      @Nullable NonAnimationApi.MatchOptions matchOptions,
      @Nullable NonAnimationApi.StartOptions startOptions
   ) {
      NonAnimationCandidate candidate = findMatch(world, actors, matchOptions);
      return candidate != null && candidate.startEligible() ? start(world, candidate.definition().id(), actors, candidate.actorKeys(), startOptions) : null;
   }

   @Nullable
   public static NonAnimationSession getSession(ServerWorld world, UUID instanceId) {
      if (world != null && instanceId != null) {
         ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
         return snapshot == null ? null : NonAnimationViews.session(snapshot);
      } else {
         return null;
      }
   }

   @Nullable
   public static NonAnimationStage getCurrentStage(ServerWorld world, UUID instanceId) {
      return NonAnimationViews.stage(AnimationService.getCurrentStage(world, instanceId));
   }

   public static boolean advanceStage(ServerWorld world, UUID instanceId) {
      return AnimationService.advanceStage(world, instanceId);
   }

   public static boolean advanceToStage(ServerWorld world, UUID instanceId, int stageNumber) {
      return AnimationService.advanceToStage(world, instanceId, stageNumber);
   }

   public static void multiplySpeed(ServerWorld world, UUID instanceId, double multiplier) {
      AnimationService.adjustSpeedSilently(world, instanceId, multiplier);
   }

   public static boolean stop(ServerWorld world, UUID instanceId) {
      return AnimationService.stopAnimation(world, instanceId);
   }

   public static void stopAll(ServerWorld world) {
      AnimationService.stopAll(world);
   }

   public static boolean enqueue(
      ServerWorld world, Identifier animationId, List<? extends Entity> actors, int insertIndex, @Nullable NonAnimationApi.StartOptions options
   ) {
      NonAnimationApi.StartOptions safe = options == null ? NonAnimationApi.StartOptions.defaults() : options;
      return AnimationService.enqueueAnimation(
         world, animationId, actors, insertIndex, safe.damageBehavior(), safe.ignoreAttackers(), safe.metadata(), safe.requester()
      );
   }

   public static void clearQueue(ServerWorld world, ServerPlayerEntity player) {
      AnimationService.clearQueuedAnimations(world, player);
   }

   public static boolean isActorPendingOrActive(ServerWorld world, UUID actorUuid) {
      return AnimationService.isActorPendingOrActive(world, actorUuid);
   }

   public static boolean shouldIgnoreAttackers(ServerWorld world, LivingEntity actor) {
      return AnimationService.shouldIgnoreAttackers(world, actor);
   }

   public static void setStartProtection(ServerPlayerEntity player, int ticks) {
      AnimationService.setAnimationStartProtection(player, ticks);
   }

   public static void clearStartProtection(ServerPlayerEntity player) {
      AnimationService.clearAnimationStartProtection(player);
   }

   public static boolean hasStartProtection(ServerWorld world, ServerPlayerEntity player) {
      return AnimationService.hasAnimationStartProtection(world, player);
   }

   private static AnimationService.MatchOptions internalOptions(NonAnimationApi.MatchOptions options) {
      AnimationService.AnimationCandidateFilter filter = options.candidateFilter() == null
         ? null
         : (world, definition, actors) -> options.candidateFilter().test(world, definitionView(definition), actors);
      return new AnimationService.MatchOptions(
         options.requiredAnimationTags(),
         options.positionAnchor(),
         options.requireStartEligibility(),
         options.requiredActiveActor(),
         options.excludedAnimationIds(),
         filter,
         options.joinPredecessorAnimationId()
      );
   }

   @Nullable
   private static NonAnimationDefinition definitionView(@Nullable AnimationDefinitions.Definition definition) {
      return definition == null
         ? null
         : new NonAnimationDefinition(
            definition.id(),
            definition.displayName(),
            definition.actors().stream().map(NonAnimationApi::actorView).toList(),
            definition.requiredUnionTags(),
            definition.animationTags(),
            definition.contentTags(),
            NonAnimationViews.stages(definition.stages()),
            definition.weight(),
            definition.attackEligible(),
            blockView(definition.blockRequirements()),
            definition.waterRequirement().name().toLowerCase(Locale.ROOT),
            definition.positionAnchorActor(),
            packView(definition.packInfo()),
            definition.joinAfter(),
            AnimationDefinitions.isDefinitionEnabled(definition)
         );
   }

   private static NonAnimationActor actorView(AnimationDefinitions.ActorConstraint actor) {
      return new NonAnimationActor(
         actor.entityTypes(),
         actor.entityVariant(),
         actor.requiredTags(),
         actor.requiredTagsAny(),
         actor.label(),
         actor.ageRequirement() == AnimationDefinitions.AgeRequirement.BABY,
         actor.activity() == AnimationDefinitions.ActorActivity.PASSIVE ? NonAnimationActor.Activity.PASSIVE : NonAnimationActor.Activity.ACTIVE,
         actor.propLeftItemId(),
         actor.propRightItemId(),
         actor.propFloorItemId()
      );
   }

   @Nullable
   private static NonAnimationPack packView(@Nullable AnimationDefinitions.AnimationPackInfo pack) {
      return pack == null
         ? null
         : new NonAnimationPack(
            pack.id(),
            pack.resourcePackId(),
            pack.name(),
            pack.author(),
            pack.version(),
            pack.description(),
            pack.localModelRoot(),
            pack.modelImports()
               .stream()
               .map(modelImport -> new NonAnimationPack.ModelImport(modelImport.packId(), modelImport.modelRoot(), modelImport.entityTypes()))
               .toList()
         );
   }

   @Nullable
   private static NonAnimationBlockRequirement blockView(@Nullable AnimationDefinitions.BlockRequirements requirement) {
      if (requirement == null) {
         return null;
      }

      AnimationDefinitions.BlockPredicate blocks = requirement.blocks();
      return new NonAnimationBlockRequirement(
         requirement.type(),
         requirement.height() == null ? null : new NonAnimationBlockRequirement.WallHeight(requirement.height().min(), requirement.height().max()),
         requirement.width() == null ? null : new NonAnimationBlockRequirement.WallWidth(requirement.width().min(), requirement.width().max()),
         requirement.facing() == null ? null : requirement.facing().id(),
         requirement.clearance() == null
            ? null
            : new NonAnimationBlockRequirement.Dimensions(requirement.clearance().width(), requirement.clearance().height(), requirement.clearance().depth()),
         requirement.support() == null ? null : requirement.support().id(),
         requirement.placement() == null ? null : requirement.placement().id(),
         requirement.surfaceRadius(),
         requirement.surfaceFootprint() == null
            ? null
            : new NonAnimationBlockRequirement.Footprint(
               requirement.surfaceFootprint().width(),
               requirement.surfaceFootprint().depth(),
               requirement.surfaceFootprint().height(),
               requirement.surfaceFootprint().margin()
            ),
         blocks == null ? Set.of() : blocks.blockIds(),
         blocks == null ? Set.of() : blocks.tagIds()
      );
   }

   @FunctionalInterface
   public interface CandidateFilter {
      boolean test(ServerWorld var1, NonAnimationDefinition var2, List<Entity> var3);
   }

   public record MatchOptions(
      Set<String> requiredAnimationTags,
      @Nullable Entity positionAnchor,
      boolean requireStartEligibility,
      @Nullable Entity requiredActiveActor,
      Set<Identifier> excludedAnimationIds,
      @Nullable NonAnimationApi.CandidateFilter candidateFilter,
      @Nullable Identifier joinPredecessorAnimationId
   ) {
      public MatchOptions {
         requiredAnimationTags = requiredAnimationTags == null ? Set.of() : Set.copyOf(requiredAnimationTags);
         excludedAnimationIds = excludedAnimationIds == null ? Set.of() : Set.copyOf(excludedAnimationIds);
      }

      public static NonAnimationApi.MatchOptions defaults() {
         return new NonAnimationApi.MatchOptions(Set.of(), null, false, null, Set.of(), null, null);
      }

      public static NonAnimationApi.MatchOptions startEligible(@Nullable Entity positionAnchor) {
         return new NonAnimationApi.MatchOptions(Set.of(), positionAnchor, true, null, Set.of(), null, null);
      }
   }

   public record StartOptions(
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      @Nullable Entity positionAnchor,
      @Nullable ServerPlayerEntity requester,
      Map<String, String> metadata,
      boolean forceDebugChat,
      boolean allowMultiplePlayers
   ) {
      public StartOptions {
         damageBehavior = damageBehavior == null ? NonAnimationDamageBehavior.IGNORE_DAMAGE : damageBehavior;
         metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
      }

      public static NonAnimationApi.StartOptions defaults() {
         return new NonAnimationApi.StartOptions(NonAnimationDamageBehavior.IGNORE_DAMAGE, false, null, null, Map.of(), false, false);
      }
   }
}
