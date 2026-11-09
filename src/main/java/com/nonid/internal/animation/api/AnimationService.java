package com.nonid.internal.animation.api;

import com.nonid.api.animation.NonAnimationDamageBehavior;
import com.nonid.internal.animation.data.AnimationDefinitions;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.network.StopAllAnimationsS2CPayload;
import com.nonid.internal.animation.server.AnimationStartContext;
import com.nonid.internal.animation.server.ServerAnimationController;
import com.nonid.internal.animation.util.WeightedPicker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class AnimationService {
   private AnimationService() {
   }

   public static boolean hasEnabledGenericAnimationForEntityType(@Nullable EntityType<?> type, boolean baby) {
      return type == null ? false : hasEnabledGenericAnimationForEntityType(Registries.ENTITY_TYPE.getId(type), baby);
   }

   public static boolean hasEnabledGenericAnimationForEntityType(@Nullable Identifier entityTypeId, boolean baby) {
      return AnimationDefinitions.hasEnabledGenericAnimationForEntityType(entityTypeId, baby);
   }

   @Nullable
   public static UUID startAnimationNow(
      ServerWorld world, Identifier animationId, List<? extends Entity> actors, @Nullable List<String> actorKeys, @Nullable List<AnimationStageInfo> stages
   ) {
      return startAnimationNow(world, animationId, actors, actorKeys, stages, AnimationService.StartOptions.defaults());
   }

   @Nullable
   public static UUID startAnimationNow(
      ServerWorld world,
      Identifier animationId,
      List<? extends Entity> actors,
      @Nullable List<String> actorKeys,
      @Nullable List<AnimationStageInfo> stages,
      @Nullable AnimationService.StartOptions options
   ) {
      AnimationService.StartOptions safeOptions = options == null ? AnimationService.StartOptions.defaults() : options;
      return startAnimationNowWithContext(
         world, animationId, actors, actorKeys, stages, safeOptions, AnimationStartContext.addonMetadata(sanitizeMetadata(safeOptions.metadata()))
      );
   }

   @Nullable
   public static UUID startAnimationNowWithContext(
      ServerWorld world,
      Identifier animationId,
      List<? extends Entity> actors,
      @Nullable List<String> actorKeys,
      @Nullable List<AnimationStageInfo> stages,
      @Nullable AnimationService.StartOptions options,
      @Nullable AnimationStartContext startContext
   ) {
      if (world == null || animationId == null || actors == null || actors.isEmpty()) {
         return null;
      }

      if (!AnimationDefinitions.isAnimationEnabled(animationId)) {
         return null;
      }

      AnimationService.StartOptions safeOptions = options == null ? AnimationService.StartOptions.defaults() : options;
      List<Entity> actorList = new ArrayList<>(actors.size());
      actorList.addAll(actors);
      actorList.sort(Comparator.comparingInt(Entity::getId));
      UUID positionAnchorUuid = null;
      Entity positionAnchor = safeOptions.positionAnchor();
      if (positionAnchor != null && positionAnchor.getEntityWorld() == world) {
         UUID candidate = positionAnchor.getUuid();
         boolean anchorIsActor = actorList.stream().anyMatch(e -> e.getUuid().equals(candidate));
         if (anchorIsActor) {
            positionAnchorUuid = candidate;
         }
      }

      return ServerAnimationController.startNowWithContext(
         world,
         safeOptions.requester(),
         animationId,
         actorList,
         actorKeys,
         stages,
         safeOptions.damageBehavior() == null ? NonAnimationDamageBehavior.IGNORE_DAMAGE : safeOptions.damageBehavior(),
         safeOptions.ignoreAttackers(),
         positionAnchorUuid,
         startContext == null ? AnimationStartContext.empty() : startContext,
         safeOptions.forceChat(),
         safeOptions.allowMultiplePlayers()
      );
   }

   @Nullable
   public static Identifier findMatchedAnimationId(List<? extends Entity> actors, @Nullable Set<String> requiredAnimationTags) {
      if (actors != null && !actors.isEmpty()) {
         List<Entity> actorList = new ArrayList<>(actors.size());
         actorList.addAll(actors);
         actorList.sort(Comparator.comparingInt(Entity::getId));
         AnimationDefinitions.MatchResult match = AnimationDefinitions.match(actorList, requiredAnimationTags == null ? Set.of() : requiredAnimationTags);
         AnimationDefinitions.Definition chosen = match.chosen();
         return chosen == null ? null : chosen.id();
      } else {
         return null;
      }
   }

   @Nullable
   public static AnimationService.MatchedAnimation findAnimationForActors(
      ServerWorld world, List<? extends Entity> actors, @Nullable AnimationService.MatchOptions options
   ) {
      if (world != null && actors != null && !actors.isEmpty()) {
         AnimationService.MatchOptions safeOptions = options == null ? AnimationService.MatchOptions.defaults() : options;
         List<Entity> actorList = new ArrayList<>(actors.size());
         actorList.addAll(actors);
         actorList.sort(Comparator.comparingInt(Entity::getId));
         Set<String> safeTags = safeOptions.requiredAnimationTags() == null ? Set.of() : safeOptions.requiredAnimationTags();
         Set<Identifier> excluded = safeOptions.excludedAnimationIds() == null ? Set.of() : safeOptions.excludedAnimationIds();
         UUID positionAnchorUuid = null;
         Entity positionAnchor = safeOptions.positionAnchor();
         if (positionAnchor != null && positionAnchor.getEntityWorld() == world) {
            UUID candidate = positionAnchor.getUuid();
            boolean anchorIsActor = actors.stream().anyMatch(e -> e != null && e.getUuid().equals(candidate));
            if (anchorIsActor) {
               positionAnchorUuid = candidate;
            }
         }

         List<AnimationDefinitions.Definition> candidates = AnimationDefinitions.findMatchingCandidates(actorList, safeTags);
         if (candidates != null && !candidates.isEmpty()) {
            List<AnimationDefinitions.Definition> eligible = new ArrayList<>();

            for (AnimationDefinitions.Definition candidate : candidates) {
               if (candidate != null
                  && !excluded.contains(candidate.id())
                  && AnimationDefinitions.isJoinCandidateAllowed(safeOptions.joinPredecessorAnimationId(), candidate)) {
                  Entity activeActor = safeOptions.requiredActiveActor();
                  if ((
                        activeActor == null
                           || AnimationDefinitions.canResolveActorAsActivity(candidate, actorList, activeActor, AnimationDefinitions.ActorActivity.ACTIVE)
                     )
                     && (
                        !safeOptions.requireStartEligibility()
                           || ServerAnimationController.isDefinitionStartEligible(world, candidate, actorList, positionAnchorUuid)
                     )) {
                     AnimationService.AnimationCandidateFilter candidateFilter = safeOptions.candidateFilter();
                     if (candidateFilter == null || candidateFilter.test(world, candidate, List.copyOf(actorList))) {
                        eligible.add(candidate);
                     }
                  }
               }
            }

            return pickWeightedMatchedAnimation(world, actorList, eligible);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static List<AnimationService.EligibleAnimation> findEligibleAnimationsForActors(
      ServerWorld world, List<? extends Entity> actors, @Nullable AnimationService.MatchOptions options
   ) {
      if (world != null && actors != null && !actors.isEmpty()) {
         AnimationService.MatchOptions safeOptions = options == null ? AnimationService.MatchOptions.defaults() : options;
         List<Entity> actorList = new ArrayList<>(actors.size());
         actorList.addAll(actors);
         actorList.sort(Comparator.comparingInt(Entity::getId));
         Set<String> safeTags = safeOptions.requiredAnimationTags() == null ? Set.of() : safeOptions.requiredAnimationTags();
         Set<Identifier> excluded = safeOptions.excludedAnimationIds() == null ? Set.of() : safeOptions.excludedAnimationIds();
         Entity positionAnchor = safeOptions.positionAnchor();
         UUID positionAnchorUuid = positionAnchor != null
               && positionAnchor.getEntityWorld() == world
               && actorList.stream().anyMatch(entity -> entity.getUuid().equals(positionAnchor.getUuid()))
            ? positionAnchor.getUuid()
            : null;
         List<AnimationService.EligibleAnimation> result = new ArrayList<>();

         for (AnimationDefinitions.Definition candidate : AnimationDefinitions.findMatchingCandidates(actorList, safeTags)) {
            if (candidate != null
               && !excluded.contains(candidate.id())
               && AnimationDefinitions.isJoinCandidateAllowed(safeOptions.joinPredecessorAnimationId(), candidate)) {
               Entity activeActor = safeOptions.requiredActiveActor();
               if (activeActor == null
                  || AnimationDefinitions.canResolveActorAsActivity(candidate, actorList, activeActor, AnimationDefinitions.ActorActivity.ACTIVE)) {
                  AnimationService.AnimationCandidateFilter filter = safeOptions.candidateFilter();
                  if (filter == null || filter.test(world, candidate, List.copyOf(actorList))) {
                     for (List<String> actorKeys : AnimationDefinitions.resolveAllActorKeyAssignments(candidate, actorList)) {
                        if (!safeOptions.requireStartEligibility()
                           || ServerAnimationController.isDefinitionStartEligible(world, candidate, actorList, actorKeys, positionAnchorUuid)) {
                           AnimationDefinitions.AnimationPackInfo pack = candidate.packInfo();
                           result.add(
                              new AnimationService.EligibleAnimation(
                                 candidate.id(),
                                 List.copyOf(actorKeys),
                                 candidate.stages() == null ? List.of() : List.copyOf(candidate.stages()),
                                 candidate.contentTags() == null ? List.of() : List.copyOf(candidate.contentTags()),
                                 pack == null ? null : pack.id(),
                                 pack == null ? null : pack.name(),
                                 pack == null ? null : pack.author(),
                                 candidate.displayName()
                              )
                           );
                        }
                     }
                  }
               }
            }
         }

         result.sort(
            Comparator.<AnimationService.EligibleAnimation, String>comparing(candidatex -> candidatex.animationId().toString())
               .thenComparing(candidatex -> String.join("\u0000", candidatex.actorKeys()))
         );
         return List.copyOf(result);
      } else {
         return List.of();
      }
   }

   @Nullable
   private static AnimationService.MatchedAnimation pickWeightedMatchedAnimation(
      ServerWorld world, List<Entity> actors, List<AnimationDefinitions.Definition> definitions
   ) {
      if (world != null && actors != null && definitions != null && !definitions.isEmpty()) {
         AnimationDefinitions.Definition picked = WeightedPicker.pick(definitions, AnimationDefinitions.Definition::weight, world.getRandom().nextDouble());
         if (picked == null) {
            return null;
         }

         List<String> actorKeys = AnimationDefinitions.resolveActorKeys(picked, actors, world.getRandom());
         return actorKeys != null && actorKeys.size() == actors.size()
            ? new AnimationService.MatchedAnimation(picked.id(), List.copyOf(actorKeys), picked.stages() == null ? List.of() : List.copyOf(picked.stages()))
            : null;
      } else {
         return null;
      }
   }

   public static boolean isAnimationStartEligible(
      ServerWorld world, @Nullable AnimationDefinitions.Definition definition, List<? extends Entity> actors, @Nullable Entity positionAnchor
   ) {
      if (world != null && actors != null && !actors.isEmpty()) {
         UUID positionAnchorUuid = null;
         if (positionAnchor != null && positionAnchor.getEntityWorld() == world) {
            UUID candidate = positionAnchor.getUuid();
            boolean anchorIsActor = actors.stream().anyMatch(e -> e != null && e.getUuid().equals(candidate));
            if (anchorIsActor) {
               positionAnchorUuid = candidate;
            }
         }

         return ServerAnimationController.isDefinitionStartEligible(world, definition, actors, positionAnchorUuid);
      } else {
         return false;
      }
   }

   public static boolean isAnimationStartEligible(
      ServerWorld world,
      @Nullable AnimationDefinitions.Definition definition,
      List<? extends Entity> actors,
      List<String> actorKeys,
      @Nullable Entity positionAnchor
   ) {
      if (world != null && actors != null && !actors.isEmpty()) {
         UUID positionAnchorUuid = null;
         if (positionAnchor != null && positionAnchor.getEntityWorld() == world) {
            UUID candidate = positionAnchor.getUuid();
            boolean anchorIsActor = actors.stream().anyMatch(e -> e != null && e.getUuid().equals(candidate));
            if (anchorIsActor) {
               positionAnchorUuid = candidate;
            }
         }

         return ServerAnimationController.isDefinitionStartEligible(world, definition, actors, actorKeys, positionAnchorUuid);
      } else {
         return false;
      }
   }

   public static boolean advanceStage(ServerWorld world, UUID instanceId) {
      return ServerAnimationController.advanceStage(world, instanceId);
   }

   public static boolean advanceToStage(ServerWorld world, UUID instanceId, int stageNumber) {
      return ServerAnimationController.advanceToStage(world, instanceId, stageNumber);
   }

   public static void adjustSpeedSilently(ServerWorld world, UUID instanceId, double multiplier) {
      ServerAnimationController.adjustSpeedSilently(world, instanceId, multiplier);
   }

   @Nullable
   public static AnimationStageInfo getCurrentStage(ServerWorld world, UUID instanceId) {
      return ServerAnimationController.getCurrentStage(world, instanceId);
   }

   @Nullable
   public static Identifier getActiveAnimationId(ServerWorld world, UUID instanceId) {
      ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
      return snapshot == null ? null : snapshot.animationId();
   }

   public static boolean isJoinTransitionAllowed(@Nullable Identifier predecessorAnimationId, @Nullable Identifier successorAnimationId) {
      return predecessorAnimationId != null && successorAnimationId != null
         ? AnimationDefinitions.isJoinCandidateAllowed(predecessorAnimationId, AnimationDefinitions.getDefinition(successorAnimationId))
         : false;
   }

   public static boolean stopAnimation(ServerWorld world, UUID instanceId) {
      return ServerAnimationController.stopInstance(world, instanceId);
   }

   public static boolean enqueueAnimation(
      ServerWorld world,
      Identifier animationId,
      List<? extends Entity> actors,
      int insertIndex,
      @Nullable NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      @Nullable Map<String, String> metadata,
      @Nullable ServerPlayerEntity requester
   ) {
      return ServerAnimationController.enqueueForPlayerWithContext(
         world,
         requester,
         animationId,
         actors,
         insertIndex,
         damageBehavior == null ? NonAnimationDamageBehavior.IGNORE_DAMAGE : damageBehavior,
         ignoreAttackers,
         AnimationStartContext.addonMetadata(sanitizeMetadata(metadata)),
         false
      );
   }

   public static void clearQueuedAnimations(ServerWorld world, ServerPlayerEntity player) {
      ServerAnimationController.clearPlayerQueue(world, player);
   }

   public static boolean isActorPendingOrActive(ServerWorld world, UUID actorUuid) {
      return ServerAnimationController.isActorPendingOrActive(world, actorUuid);
   }

   public static boolean shouldIgnoreAttackers(ServerWorld world, LivingEntity actor) {
      return world != null && actor != null ? ServerAnimationController.shouldIgnoreAttackers(world, actor) : false;
   }

   public static void setAnimationStartProtection(ServerPlayerEntity player, int ticks) {
      ServerAnimationController.setAnimationStartProtection(player, ticks);
   }

   public static void clearAnimationStartProtection(ServerPlayerEntity player) {
      ServerAnimationController.clearAnimationStartProtection(player);
   }

   public static boolean hasAnimationStartProtection(ServerWorld world, ServerPlayerEntity player) {
      return ServerAnimationController.hasAnimationStartProtection(world, player);
   }

   public static void stopAll(ServerWorld world) {
      long stopTick = world.getTime() + 1L;
      StopAllAnimationsS2CPayload payload = new StopAllAnimationsS2CPayload(stopTick);

      for (ServerPlayerEntity player : Objects.requireNonNull(world.getServer()).getPlayerManager().getPlayerList()) {
         if (player.getEntityWorld() == world) {
            ServerPlayNetworking.send(player, payload);
         }
      }

      ServerAnimationController.clearAllInstancesInWorld(world);
   }

   private static Map<String, String> sanitizeMetadata(@Nullable Map<String, String> metadata) {
      if (metadata != null && !metadata.isEmpty()) {
         LinkedHashMap<String, String> out = new LinkedHashMap<>();

         for (Entry<String, String> entry : metadata.entrySet()) {
            if (entry != null) {
               String key = entry.getKey();
               String value = entry.getValue();
               if (key != null && !key.isBlank() && value != null) {
                  out.put(key, value);
               }
            }
         }

         return out.isEmpty() ? Map.of() : Map.copyOf(out);
      } else {
         return Map.of();
      }
   }

   @FunctionalInterface
   public interface AnimationCandidateFilter {
      boolean test(ServerWorld var1, AnimationDefinitions.Definition var2, List<Entity> var3);
   }

   public record EligibleAnimation(
      Identifier animationId,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      List<String> contentTags,
      @Nullable String packId,
      @Nullable String packName,
      @Nullable String packAuthor,
      @Nullable String displayName
   ) {
      public EligibleAnimation(
         Identifier animationId,
         List<String> actorKeys,
         List<AnimationStageInfo> stages,
         List<String> contentTags,
         @Nullable String packId,
         @Nullable String packName,
         @Nullable String packAuthor
      ) {
         this(animationId, actorKeys, stages, contentTags, packId, packName, packAuthor, null);
      }
   }

   public record MatchOptions(
      @Nullable Set<String> requiredAnimationTags,
      @Nullable Entity positionAnchor,
      boolean requireStartEligibility,
      @Nullable Entity requiredActiveActor,
      @Nullable Set<Identifier> excludedAnimationIds,
      @Nullable AnimationService.AnimationCandidateFilter candidateFilter,
      @Nullable Identifier joinPredecessorAnimationId
   ) {
      public MatchOptions(
         @Nullable Set<String> requiredAnimationTags,
         @Nullable Entity positionAnchor,
         boolean requireStartEligibility,
         @Nullable Entity requiredActiveActor,
         @Nullable Set<Identifier> excludedAnimationIds,
         @Nullable AnimationService.AnimationCandidateFilter candidateFilter
      ) {
         this(requiredAnimationTags, positionAnchor, requireStartEligibility, requiredActiveActor, excludedAnimationIds, candidateFilter, null);
      }

      public MatchOptions(
         @Nullable Set<String> requiredAnimationTags,
         @Nullable Entity positionAnchor,
         boolean requireStartEligibility,
         @Nullable Entity requiredActiveActor,
         @Nullable Set<Identifier> excludedAnimationIds
      ) {
         this(requiredAnimationTags, positionAnchor, requireStartEligibility, requiredActiveActor, excludedAnimationIds, null);
      }

      public static AnimationService.MatchOptions defaults() {
         return new AnimationService.MatchOptions(Set.of(), null, false, null, Set.of(), null);
      }

      public static AnimationService.MatchOptions startEligible(@Nullable Entity positionAnchor) {
         return new AnimationService.MatchOptions(Set.of(), positionAnchor, true, null, Set.of(), null);
      }
   }

   public record MatchedAnimation(Identifier animationId, List<String> actorKeys, List<AnimationStageInfo> stages) {
   }

   public record StartOptions(
      @Nullable NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      @Nullable Entity positionAnchor,
      @Nullable ServerPlayerEntity requester,
      @Nullable Map<String, String> metadata,
      boolean forceChat,
      boolean allowMultiplePlayers
   ) {
      public StartOptions(
         @Nullable NonAnimationDamageBehavior damageBehavior,
         boolean ignoreAttackers,
         @Nullable Entity positionAnchor,
         @Nullable ServerPlayerEntity requester,
         @Nullable Map<String, String> metadata,
         boolean forceChat
      ) {
         this(damageBehavior, ignoreAttackers, positionAnchor, requester, metadata, forceChat, false);
      }

      public static AnimationService.StartOptions defaults() {
         return new AnimationService.StartOptions(NonAnimationDamageBehavior.IGNORE_DAMAGE, false, null, null, Map.of(), false, false);
      }
   }
}
