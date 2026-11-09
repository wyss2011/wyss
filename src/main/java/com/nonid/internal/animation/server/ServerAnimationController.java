package com.nonid.internal.animation.server;

import com.nonid.NeedsOfNature;
import com.nonid.NonDebugChatCategory;
import com.nonid.api.animation.NonAnimationDamageBehavior;
import com.nonid.internal.animation.AnimationEngine;
import com.nonid.internal.animation.api.AnimationLifecycleEvents;
import com.nonid.internal.animation.data.AnimationDefinitions;
import com.nonid.internal.animation.network.AdvanceAnimationStageS2CPayload;
import com.nonid.internal.animation.network.AnimationSpeedUpdateS2CPayload;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.network.StartAnimationS2CPayload;
import com.nonid.internal.animation.network.StopAnimationS2CPayload;
import com.nonid.internal.animation.util.WeightedPicker;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDamage;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDeath;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.jetbrains.annotations.Nullable;

public final class ServerAnimationController {
   private static final double DEBUG_MULTI_PLAYER_REQUESTER_MAX_DIST = 5.0;
   private static final int BLOCK_SCAN_VERTICAL_RANGE = 1;
   private static final double HALF_HEIGHT_SUPPORT_TOP_OFFSET = 0.5625;
   private static final boolean BLOCK_REQUIRE_SOLID_BELOW = true;
   private static final double FLOOR_MIN_HEIGHT = 0.875;
   private static final double FLOOR_SUPPORT_INSET = 0.0625;
   private static final double FLOOR_SUPPORT_THICKNESS = 0.0625;
   private static final double WALL_CORE_INSET = 0.0625;
   private static final double WALL_CORE_MIN_HEIGHT = 0.875;
   private static final double WALL_EDGE_EPSILON = 1.0E-6;
   private static final double WALL_ANCHOR_DISTANCE = 0.5;
   private static final double WALL_BROAD_FACE_MIN_SPAN = 0.5;
   private static final double GROUND_PLACEMENT_SEARCH_RADIUS = 2.0;
   private static final double GROUND_PLACEMENT_SEARCH_STEP = 0.5;
   private static final double GROUND_PLACEMENT_CLEARANCE_HALF_EXTENT = 0.75;
   private static final double GROUND_PLACEMENT_CLEARANCE_HEIGHT = 2.0;
   private static final double GROUND_PLACEMENT_FLOOR_EPSILON = 0.01;
   private static final double PLAYER_LOCK_EPSILON_SQ = 4.0E-4;
   private static final int IN_WALL_GRACE_TICKS = 5;
   private static final int PLAYER_NUDGE_INTERVAL_TICKS = 4;
   private static final int PLAYER_NUDGE_MAX_MOBS_PER_PLAYER = 6;
   private static final double PLAYER_NUDGE_SCAN_RADIUS = 1.0;
   private static final double PLAYER_NUDGE_MIN_DISTANCE = 0.85;
   private static final double PLAYER_NUDGE_STRENGTH = 0.08;
   private static final double PLAYER_NUDGE_EPSILON = 1.0E-6;
   private static final int RECENT_NO_RESTORE_TRANSITION_TICKS = 20;
   private static final Identifier BEDS_BLOCK_TAG_ID = Identifier.of("minecraft", "beds");
   private static final TagKey<Block> WALL_SURFACES_BLOCK_TAG = TagKey.of(RegistryKeys.BLOCK, AnimationEngine.packId("wall_surfaces"));
   private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
   private static final Map<RegistryKey<World>, Map<UUID, Long>> IN_WALL_GRACE_BY_WORLD = new HashMap<>();
   private static final Map<RegistryKey<World>, Map<UUID, ServerAnimationController.RecentNoRestoreTransition>> RECENT_NO_RESTORE_TRANSITIONS_BY_WORLD = new HashMap<>();
   private static final String ANIMATION_NOAI_TAG = "afw_noai";
   private static final String ANIMATION_NOAI_ORIG_TAG = "afw_noai_orig";
   private static final int NOAI_CLEANUP_INTERVAL_TICKS = 6000;
   private static final int STARTUP_NOAI_CLEANUP_TICKS = 20;
   private static final int STARTUP_START_BLOCK_TICKS = 40;
   private static final List<ServerAnimationController.GroundPlacementOffset> GROUND_PLACEMENT_OFFSETS = createGroundPlacementOffsets();
   private static boolean initialized = false;
   private static int STARTUP_NOAI_CLEANUP_UNTIL_TICK = Integer.MIN_VALUE;
   private static int STARTUP_START_BLOCK_UNTIL_TICK = Integer.MIN_VALUE;
   private static final Map<UUID, ServerAnimationController.ActiveInstance> ACTIVE_INSTANCES = new ConcurrentHashMap<>();
   private static final Map<RegistryKey<World>, Map<UUID, UUID>> ACTIVE_INSTANCE_BY_ACTOR = new ConcurrentHashMap<>();
   private static final Map<UUID, ServerAnimationController.AnimationStartProtection> ANIMATION_START_PROTECTION = new ConcurrentHashMap<>();
   private static final int PLAYER_QUEUE_CAP = 8;
   private static final Map<RegistryKey<World>, Map<UUID, ServerAnimationController.PlayerQueueState>> PLAYER_QUEUES_BY_WORLD = new HashMap<>();
   private static final Map<RegistryKey<World>, Map<UUID, UUID>> QUEUED_MOB_TO_PLAYER_BY_WORLD = new HashMap<>();
   private static final Map<RegistryKey<World>, Map<UUID, ServerAnimationController.DeferredQueueContext>> DEFERRED_QUEUE_CONTEXT_BY_WORLD = new HashMap<>();
   private static final Map<RegistryKey<World>, Map<UUID, ServerAnimationController.AiDisableState>> AI_DISABLE_STATE_BY_WORLD = new HashMap<>();
   private static final Map<RegistryKey<World>, Integer> NOAI_CLEANUP_TICKS_BY_WORLD = new HashMap<>();
   private static final Map<RegistryKey<World>, Map<UUID, ServerAnimationController.PlayerLockState>> PLAYER_LOCK_STATE_BY_WORLD = new HashMap<>();
   private static final int MAX_FLOOR_SNAP_DROP = 2;

   private ServerAnimationController() {
   }

   public static void init() {
      if (!initialized) {
         initialized = true;
         ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)server -> {
            AI_DISABLE_STATE_BY_WORLD.clear();
            NOAI_CLEANUP_TICKS_BY_WORLD.clear();
            STARTUP_NOAI_CLEANUP_UNTIL_TICK = server.getTicks() + 20;
            STARTUP_START_BLOCK_UNTIL_TICK = server.getTicks() + 40;
            PLAYER_LOCK_STATE_BY_WORLD.clear();
            ACTIVE_INSTANCES.clear();
            ACTIVE_INSTANCE_BY_ACTOR.clear();
            PLAYER_QUEUES_BY_WORLD.clear();
            QUEUED_MOB_TO_PLAYER_BY_WORLD.clear();
            DEFERRED_QUEUE_CONTEXT_BY_WORLD.clear();
            ANIMATION_START_PROTECTION.clear();
            RECENT_NO_RESTORE_TRANSITIONS_BY_WORLD.clear();
         });
         ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> server.execute(() -> {
            ServerPlayerEntity player = handler.player;
            if (player != null) {
               if (player.getEntityWorld() instanceof ServerWorld world) {
                  stopActiveInstancesForDisconnectingPlayer(world, player.getUuid());
                  releasePlayerLocks(world, List.of(player.getUuid()));
                  clearPlayerQueue(world, player);
                  clearDeferredQueueOriginal(world, player.getUuid());
                  ANIMATION_START_PROTECTION.remove(player.getUuid());
                  removePlayerFromInstanceSubscribers(player.getUuid());
               }
            }
         }));
         ServerLivingEntityEvents.AFTER_DEATH.register((AfterDeath)(entity, damageSource) -> {
            if (entity.getEntityWorld() instanceof ServerWorld world) {
               stopActiveInstancesForDeadActor(world, entity);
            }
         });
         ServerLivingEntityEvents.AFTER_DAMAGE.register((AfterDamage)(entity, damageSource, baseDamageTaken, damageTaken, blocked) -> {
            if (!blocked && !(damageTaken <= 0.0F)) {
               if (entity.getEntityWorld() instanceof ServerWorld world) {
                  stopDamageSensitiveInstances(world, entity);
               }
            }
         });
         ServerTickEvents.END_WORLD_TICK.register(ServerAnimationController::tickWorld);
      }
   }

   @Nullable
   public static UUID startNowWithContext(
      ServerWorld world,
      @Nullable ServerPlayerEntity requester,
      Identifier animationId,
      List<Entity> actorsSorted,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      @Nullable UUID positionAnchorUuid,
      @Nullable AnimationStartContext startContext,
      boolean forceChat,
      boolean allowMultiplePlayers
   ) {
      if (world == null || animationId == null || actorsSorted == null || actorsSorted.isEmpty()) {
         return null;
      }

      if (isWithinStartupStartBlockWindow(world)) {
         return null;
      }

      if (!AnimationDefinitions.isAnimationEnabled(animationId)) {
         return null;
      }

      List<Entity> actors = new ArrayList<>(actorsSorted.size());
      actors.addAll(actorsSorted);
      actors.sort(Comparator.comparingInt(Entity::getId));
      int players = 0;
      List<UUID> actorUuids = new ArrayList<>(actors.size());

      for (Entity e : actors) {
         if (e == null || e.getEntityWorld() != world) {
            return null;
         }

         if (e instanceof PlayerEntity) {
            players++;
         } else if (!(e instanceof MobEntity)) {
            sendOrLogDebug(world, requester, NonDebugChatCategory.WARNING, dbg("matched_unpathable_actor", animationId), forceChat);
            return null;
         }

         actorUuids.add(e.getUuid());
      }

      boolean authorizedMultiPlayerRequest = players > 1 && requester != null && (allowMultiplePlayers || forceChat);
      if (players > 1 && !authorizedMultiPlayerRequest) {
         sendOrLogDebug(world, requester, NonDebugChatCategory.SETUP, dbg("matched_multiple_players_not_supported", animationId), forceChat);
         return null;
      }

      if (authorizedMultiPlayerRequest && !allSelectedPlayersNearRequester(actors, requester, 5.0)) {
         sendOrLogDebug(
            world,
            requester,
            NonDebugChatCategory.SETUP,
            dbg("start_cancelled_players_too_far_from_requester", String.format(Locale.ROOT, "%.1f", 5.0)),
            forceChat
         );
         return null;
      }

      if (shouldBlockStart(world, actorUuids)) {
         return null;
      }

      List<String> safeActorKeys = actorKeys != null && actorKeys.size() == actorUuids.size() ? List.copyOf(actorKeys) : List.of();
      List<AnimationStageInfo> safeStages = stages == null ? List.of() : List.copyOf(stages);
      NonAnimationDamageBehavior safeBehavior = damageBehavior == null ? NonAnimationDamageBehavior.IGNORE_DAMAGE : damageBehavior;
      return startAndBroadcast(
         world,
         requester,
         animationId,
         actorUuids,
         safeActorKeys,
         safeStages,
         safeBehavior,
         ignoreAttackers,
         sanitizeStartContext(startContext),
         forceChat,
         positionAnchorUuid,
         null,
         null
      );
   }

   @Nullable
   public static ServerAnimationController.MatchedStartRequest startEligibleMatchedNow(
      ServerWorld world,
      @Nullable ServerPlayerEntity requester,
      List<? extends Entity> actorsSorted,
      @Nullable Set<String> requiredAnimationTags,
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      @Nullable UUID positionAnchorUuid,
      @Nullable AnimationStartContext startContext,
      boolean forceChat
   ) {
      if (world != null && actorsSorted != null && !actorsSorted.isEmpty()) {
         List<Entity> actors = new ArrayList<>(actorsSorted.size());
         actors.addAll(actorsSorted);
         actors.sort(Comparator.comparingInt(Entity::getId));
         ServerAnimationController.EligibleDefinition eligible = selectEligibleDefinition(
            world, actors, requiredAnimationTags == null ? Set.of() : requiredAnimationTags, positionAnchorUuid
         );
         if (eligible == null) {
            return null;
         }

         UUID instanceId = startNowWithContext(
            world,
            requester,
            eligible.definition().id(),
            actors,
            eligible.actorKeys(),
            eligible.definition().stages(),
            damageBehavior,
            ignoreAttackers,
            positionAnchorUuid,
            startContext,
            forceChat,
            false
         );
         return new ServerAnimationController.MatchedStartRequest(instanceId, eligible.definition().id(), eligible.actorKeys(), eligible.definition().stages());
      } else {
         return null;
      }
   }

   public static boolean isDefinitionStartEligible(
      ServerWorld world, @Nullable AnimationDefinitions.Definition definition, List<? extends Entity> actorsSorted, @Nullable UUID positionAnchorUuid
   ) {
      if (world != null && definition != null && actorsSorted != null && !actorsSorted.isEmpty()) {
         List<Entity> actors = new ArrayList<>(actorsSorted.size());
         actors.addAll(actorsSorted);
         actors.sort(Comparator.comparingInt(Entity::getId));

         for (Entity actor : actors) {
            if (actor == null || actor.getEntityWorld() != world) {
               return false;
            }
         }

         List<String> actorKeys = AnimationDefinitions.resolveActorKeys(definition, actors, world.getRandom());
         return isDefinitionStartEligible(world, definition, actors, actorKeys, positionAnchorUuid);
      } else {
         return false;
      }
   }

   public static boolean isDefinitionStartEligible(
      ServerWorld world,
      @Nullable AnimationDefinitions.Definition definition,
      List<? extends Entity> actorsSorted,
      List<String> actorKeys,
      @Nullable UUID positionAnchorUuid
   ) {
      if (world != null && definition != null && actorsSorted != null && !actorsSorted.isEmpty()) {
         List<Entity> actors = new ArrayList<>(actorsSorted.size());
         actors.addAll(actorsSorted);
         actors.sort(Comparator.comparingInt(Entity::getId));

         for (Entity actor : actors) {
            if (actor == null || actor.getEntityWorld() != world) {
               return false;
            }
         }

         if (actorKeys == null || actorKeys.size() != actors.size()) {
            return false;
         }

         if (!AnimationDefinitions.resolveAllActorKeyAssignments(definition, actors).contains(List.copyOf(actorKeys))) {
            return false;
         }

         List<UUID> actorUuids = actors.stream().<UUID>map(Entity::getUuid).toList();
         UUID effectiveAnchorUuid = resolveEffectivePositionAnchorUuid(world, definition.id(), actorUuids, actorKeys, positionAnchorUuid);
         return checkStartRequirements(world, definition, actorUuids, effectiveAnchorUuid).valid();
      } else {
         return false;
      }
   }

   public static void setAnimationStartProtection(@Nullable ServerPlayerEntity player, int ticks) {
      if (player != null) {
         if (player.getEntityWorld() instanceof ServerWorld world) {
            if (ticks <= 0) {
               ANIMATION_START_PROTECTION.remove(player.getUuid());
            } else {
               long until = world.getTime() + ticks;
               ANIMATION_START_PROTECTION.put(player.getUuid(), new ServerAnimationController.AnimationStartProtection(world.getRegistryKey(), until));
            }
         }
      }
   }

   public static void clearAnimationStartProtection(@Nullable ServerPlayerEntity player) {
      if (player != null) {
         ANIMATION_START_PROTECTION.remove(player.getUuid());
      }
   }

   public static boolean hasAnimationStartProtection(@Nullable ServerWorld world, @Nullable ServerPlayerEntity player) {
      if (world != null && player != null) {
         ServerAnimationController.AnimationStartProtection protection = ANIMATION_START_PROTECTION.get(player.getUuid());
         if (protection == null) {
            return false;
         } else if (!protection.worldKey.equals(world.getRegistryKey())) {
            ANIMATION_START_PROTECTION.remove(player.getUuid());
            return false;
         } else if (world.getTime() >= protection.untilTick) {
            ANIMATION_START_PROTECTION.remove(player.getUuid());
            return false;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public static boolean enqueueForPlayerWithContext(
      ServerWorld world,
      @Nullable ServerPlayerEntity requester,
      Identifier animationId,
      List<? extends Entity> actors,
      int insertIndex,
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      @Nullable AnimationStartContext startContext,
      boolean forceChat
   ) {
      if (world != null && animationId != null && actors != null && !actors.isEmpty()) {
         UUID playerUuid = inferSinglePlayerUuid(world, actors);
         if (playerUuid == null) {
            sendOrLogDebug(world, requester, NonDebugChatCategory.WARNING, dbg("queue_enqueue_failed_exactly_one_player"), forceChat);
            return false;
         }

         ServerAnimationController.PlayerQueueState queue = PLAYER_QUEUES_BY_WORLD.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>())
            .computeIfAbsent(playerUuid, ServerAnimationController.PlayerQueueState::new);
         if (queue.entries.size() >= 8) {
            sendOrLogDebug(world, requester, NonDebugChatCategory.WARNING, dbg("queue_enqueue_failed_full", 8), forceChat);
            return false;
         }

         List<UUID> actorUuids = new ArrayList<>();
         List<UUID> nonPlayers = new ArrayList<>();

         for (Entity actor : actors) {
            if (actor != null && actor.getEntityWorld() == world) {
               UUID uuid = actor.getUuid();
               if (actor instanceof ServerPlayerEntity) {
                  if (!uuid.equals(playerUuid)) {
                     continue;
                  }
               } else {
                  nonPlayers.add(uuid);
               }

               actorUuids.add(uuid);
            }
         }

         if (!actorUuids.contains(playerUuid)) {
            sendOrLogDebug(world, requester, NonDebugChatCategory.WARNING, dbg("queue_enqueue_failed_player_missing"), forceChat);
            return false;
         }

         Map<UUID, UUID> mobOwner = QUEUED_MOB_TO_PLAYER_BY_WORLD.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>());

         for (UUID mobUuid : nonPlayers) {
            UUID existingOwner = mobOwner.get(mobUuid);
            if (existingOwner != null) {
               sendOrLogDebug(
                  world,
                  requester,
                  NonDebugChatCategory.WARNING,
                  dbg("queue_enqueue_denied_mob_already_queued", describeActorForDebug(world, mobUuid)),
                  forceChat
               );
               return false;
            }
         }

         UUID entryId = UUID.randomUUID();
         ServerAnimationController.QueuedEntry entry = new ServerAnimationController.QueuedEntry(
            entryId,
            animationId,
            List.copyOf(actorUuids),
            damageBehavior == null ? NonAnimationDamageBehavior.IGNORE_DAMAGE : damageBehavior,
            ignoreAttackers,
            sanitizeStartContext(startContext)
         );
         insertQueueEntry(queue.entries, entry, insertIndex);
         captureDeferredQueueContextFromActiveInstance(world, playerUuid);

         for (UUID mobUuid : nonPlayers) {
            mobOwner.put(mobUuid, playerUuid);
         }

         sendOrLogDebug(
            world, requester, NonDebugChatCategory.INFO, dbg("queued_at_position", animationId, describeQueuePosition(queue.entries, entryId)), forceChat
         );
         return true;
      } else {
         return false;
      }
   }

   public static void clearPlayerQueue(ServerWorld world, @Nullable ServerPlayerEntity player) {
      if (world != null && player != null) {
         clearPlayerQueueByUuid(world, player.getUuid(), NonDebugChatCategory.INFO, dbg("queue_cleared_for_player"));
      }
   }

   private static void stopActiveInstancesForDisconnectingPlayer(ServerWorld world, UUID playerUuid) {
      if (world != null && playerUuid != null && !ACTIVE_INSTANCES.isEmpty()) {
         List<UUID> instanceIds = new ArrayList<>();

         for (Entry<UUID, ServerAnimationController.ActiveInstance> entry : ACTIVE_INSTANCES.entrySet()) {
            ServerAnimationController.ActiveInstance inst = entry.getValue();
            if (inst.world == world && inst.actorUuidSet.contains(playerUuid)) {
               instanceIds.add(entry.getKey());
            }
         }

         for (UUID instanceId : instanceIds) {
            stopInstance(world, instanceId, true, ServerAnimationController.StopReason.EXTERNAL_STOP);
         }
      }
   }

   @Nullable
   private static ServerAnimationController.EligibleDefinition selectEligibleDefinition(
      ServerWorld world, List<Entity> actorsSorted, Set<String> requiredAnimationTags, @Nullable UUID positionAnchorUuid
   ) {
      if (world != null && actorsSorted != null && !actorsSorted.isEmpty()) {
         AnimationDefinitions.MatchResult match = AnimationDefinitions.match(actorsSorted, requiredAnimationTags);
         List<AnimationDefinitions.Definition> candidates = match.candidatesSorted();
         if (candidates != null && !candidates.isEmpty()) {
            List<ServerAnimationController.EligibleDefinition> eligible = new ArrayList<>();
            List<UUID> actorUuids = actorsSorted.stream().<UUID>map(Entity::getUuid).toList();

            for (AnimationDefinitions.Definition definition : candidates) {
               List<String> actorKeys = AnimationDefinitions.resolveActorKeys(definition, actorsSorted, world.getRandom());
               UUID effectiveAnchorUuid = resolveEffectivePositionAnchorUuid(world, definition.id(), actorUuids, actorKeys, positionAnchorUuid);
               ServerAnimationController.RequirementCheck check = checkStartRequirements(world, definition, actorUuids, effectiveAnchorUuid);
               if (check.valid()) {
                  eligible.add(new ServerAnimationController.EligibleDefinition(definition, actorKeys, check.blockPlacement()));
               }
            }

            return eligible.isEmpty() ? null : pickWeightedEligible(eligible, world.getRandom());
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static ServerAnimationController.RequirementCheck checkStartRequirements(
      ServerWorld world, @Nullable AnimationDefinitions.Definition definition, List<UUID> actorUuids, @Nullable UUID effectiveAnchorUuid
   ) {
      if (definition == null) {
         return new ServerAnimationController.RequirementCheck(false, null);
      } else {
         AnimationDefinitions.BlockRequirements blockRequirements = definition.blockRequirements();
         AnimationDefinitions.WaterRequirement waterRequirement = definition.waterRequirement();
         if (blockRequirements != null && waterRequirement != AnimationDefinitions.WaterRequirement.NONE) {
            return new ServerAnimationController.RequirementCheck(false, null);
         } else if (blockRequirements != null) {
            Entity anchor = pickAnchorEntity(world, actorUuids, effectiveAnchorUuid);
            ServerAnimationController.BlockPlacement placement = anchor == null ? null : findBlockPlacement(world, anchor, blockRequirements);
            return new ServerAnimationController.RequirementCheck(placement != null, placement);
         } else {
            return new ServerAnimationController.RequirementCheck(waterRequirementMet(world, definition, actorUuids), null);
         }
      }
   }

   private static ServerAnimationController.EligibleDefinition pickWeightedEligible(
      List<ServerAnimationController.EligibleDefinition> definitions, Random random
   ) {
      return WeightedPicker.pick(definitions, eligible -> eligible.definition().weight(), random.nextDouble());
   }

   public static boolean advanceStage(ServerWorld world, UUID instanceId) {
      ServerAnimationController.ActiveInstance inst = ACTIVE_INSTANCES.get(instanceId);
      if (inst != null && inst.world == world) {
         int currentIndex = inst.stageTracker == null ? 0 : inst.stageTracker.stageIndex;
         if (inst.stages != null && !inst.stages.isEmpty()) {
            if (currentIndex + 1 >= inst.stages.size()) {
               return stopInstance(world, instanceId, true, ServerAnimationController.StopReason.NATURAL_END);
            }

            int targetIndex = currentIndex + 1;
            return advanceToStageIndex(world, instanceId, inst, targetIndex);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean advanceToStage(ServerWorld world, UUID instanceId, int stageNumber) {
      ServerAnimationController.ActiveInstance inst = ACTIVE_INSTANCES.get(instanceId);
      if (inst == null || inst.world != world) {
         return false;
      } else {
         return stageNumber <= 0 ? false : advanceToStageIndex(world, instanceId, inst, stageNumber - 1);
      }
   }

   private static boolean advanceToStageIndex(ServerWorld world, UUID instanceId, ServerAnimationController.ActiveInstance inst, int targetIndex) {
      if (inst == null || inst.world != world || inst.stages == null || inst.stages.isEmpty()) {
         return false;
      }

      if (targetIndex >= 0 && targetIndex < inst.stages.size()) {
         long advanceTick = world.getTime();
         if (inst.stageTracker != null) {
            inst.stageTracker.stageIndex = targetIndex;
            inst.stageTracker.stageStartTick = advanceTick;
            inst.stageTracker.progressEndTickOverride = -1L;
         }

         inst.speed = clampSpeed(resolveStageSpeed(inst.stages, inst.stageTracker == null ? 0 : inst.stageTracker.stageIndex));
         long stageEndTick = resolveStageEndTick(inst);
         AdvanceAnimationStageS2CPayload payload = new AdvanceAnimationStageS2CPayload(instanceId, advanceTick, stageEndTick, targetIndex);
         Set<ServerPlayerEntity> targets = computeInstanceBroadcastTargets(world, null, inst);
         rememberInstanceSubscribers(inst, targets);

         for (ServerPlayerEntity p : targets) {
            ServerPlayNetworking.send(p, payload);
            ServerPlayNetworking.send(p, new AnimationSpeedUpdateS2CPayload(instanceId, inst.speed, advanceTick, stageEndTick));
         }

         ((AnimationLifecycleEvents.StageAdvance)AnimationLifecycleEvents.STAGE_ADVANCE.invoker())
            .onStageAdvance(new AnimationLifecycleEvents.StageChangedContext(world, snapshot(instanceId, inst), resolveCurrentStage(inst), advanceTick));
         return true;
      } else {
         return false;
      }
   }

   public static boolean stopInstance(ServerWorld world, UUID instanceId) {
      return stopInstance(world, instanceId, true, ServerAnimationController.StopReason.EXTERNAL_STOP);
   }

   public static void adjustSpeed(ServerWorld world, UUID instanceId, double multiplier, @Nullable ServerPlayerEntity requester) {
      adjustSpeed(world, instanceId, multiplier, requester, requester != null);
   }

   public static void adjustSpeedSilently(ServerWorld world, UUID instanceId, double multiplier) {
      adjustSpeed(world, instanceId, multiplier, null, false);
   }

   private static void adjustSpeed(ServerWorld world, UUID instanceId, double multiplier, @Nullable ServerPlayerEntity requester, boolean notify) {
      ServerAnimationController.ActiveInstance inst = ACTIVE_INSTANCES.get(instanceId);
      if (inst != null && inst.world == world) {
         double clampedMultiplier = sanitizeMultiplier(multiplier);
         double newSpeed = clampSpeed(inst.speed * clampedMultiplier);
         double oldSpeed = inst.speed;
         if (Math.abs(newSpeed - oldSpeed) < 1.0E-4) {
            if (notify) {
               sendOrLogDebug(world, requester, NonDebugChatCategory.ALWAYS, dbg("speed_unchanged"), true);
            }
         } else {
            long now = world.getTime();
            AnimationStageInfo currentStage = resolveCurrentStage(inst);
            long duration = Math.max(0L, currentStage.lengthTicks());
            if (inst.stageTracker != null) {
               if (duration > 0L) {
                  long elapsed = Math.max(0L, now - inst.stageTracker.stageStartTick);
                  double normalizedProgress = elapsed * oldSpeed;
                  long adjustedStart = now - Math.round(normalizedProgress / newSpeed);
                  inst.stageTracker.stageStartTick = Math.max(inst.startTick, adjustedStart);
               }

               inst.stageTracker.progressEndTickOverride = -1L;
            }

            inst.speed = newSpeed;
            long stageStartTick = inst.stageTracker == null ? inst.startTick : inst.stageTracker.stageStartTick;
            long stageEndTick = resolveStageEndTick(inst);
            AnimationSpeedUpdateS2CPayload payload = new AnimationSpeedUpdateS2CPayload(instanceId, newSpeed, stageStartTick, stageEndTick);
            Set<ServerPlayerEntity> targets = computeInstanceBroadcastTargets(world, requester, inst);
            rememberInstanceSubscribers(inst, targets);

            for (ServerPlayerEntity p : targets) {
               ServerPlayNetworking.send(p, payload);
            }

            if (notify) {
               sendOrLogDebug(world, requester, NonDebugChatCategory.ALWAYS, dbg("speed_set", String.format(Locale.ROOT, "%.2fx", newSpeed)), true);
            }
         }
      } else {
         if (notify) {
            sendOrLogDebug(world, requester, NonDebugChatCategory.ALWAYS, dbg("no_active_instance_to_adjust"), true);
         }
      }
   }

   public static boolean setCurrentStageProgressDuration(ServerWorld world, UUID instanceId, long durationTicks) {
      ServerAnimationController.ActiveInstance inst = ACTIVE_INSTANCES.get(instanceId);
      if (inst != null && inst.world == world && inst.stageTracker != null && durationTicks > 0L) {
         long stageStartTick = inst.stageTracker.stageStartTick;
         inst.stageTracker.progressEndTickOverride = stageStartTick + Math.max(1L, durationTicks);
         AnimationSpeedUpdateS2CPayload payload = new AnimationSpeedUpdateS2CPayload(
            instanceId, inst.speed, stageStartTick, inst.stageTracker.progressEndTickOverride
         );
         Set<ServerPlayerEntity> targets = computeInstanceBroadcastTargets(world, null, inst);
         rememberInstanceSubscribers(inst, targets);

         for (ServerPlayerEntity player : targets) {
            ServerPlayNetworking.send(player, payload);
         }

         return true;
      } else {
         return false;
      }
   }

   private static void stopActiveInstancesForDeadActor(ServerWorld world, LivingEntity actor) {
      if (world != null && actor != null) {
         UUID actorUuid = actor.getUuid();
         stopActiveInstancesForDeadActor(world, actorUuid);
         clearRecentNoRestoreTransition(world, actorUuid);
         ANIMATION_START_PROTECTION.remove(actorUuid);
         if (actor instanceof ServerPlayerEntity) {
            clearPlayerQueueByUuid(world, actorUuid, null, null);
         }
      }
   }

   private static void stopActiveInstancesForDeadActor(ServerWorld world, UUID actorUuid) {
      if (world != null && actorUuid != null && !ACTIVE_INSTANCES.isEmpty()) {
         List<UUID> instanceIds = new ArrayList<>();

         for (Entry<UUID, ServerAnimationController.ActiveInstance> entry : ACTIVE_INSTANCES.entrySet()) {
            ServerAnimationController.ActiveInstance inst = entry.getValue();
            if (inst.world == world && inst.actorUuidSet.contains(actorUuid)) {
               instanceIds.add(entry.getKey());
            }
         }

         for (UUID instanceId : instanceIds) {
            stopInstance(world, instanceId, true, ServerAnimationController.StopReason.ACTOR_DEATH, Set.of(actorUuid));
         }
      }
   }

   public static boolean stopInstance(ServerWorld world, UUID instanceId, boolean restoreTransforms) {
      return stopInstance(world, instanceId, restoreTransforms, ServerAnimationController.StopReason.EXTERNAL_STOP);
   }

   public static boolean stopInstance(ServerWorld world, UUID instanceId, boolean restoreTransforms, ServerAnimationController.StopReason reason) {
      return stopInstance(world, instanceId, restoreTransforms, reason, null);
   }

   private static boolean stopInstance(
      ServerWorld world, UUID instanceId, boolean restoreTransforms, ServerAnimationController.StopReason reason, @Nullable Set<UUID> skipRestoreActors
   ) {
      ServerAnimationController.ActiveInstance inst = ACTIVE_INSTANCES.get(instanceId);
      if (inst != null && inst.world == world) {
         long stopTick = world.getTime() + 1L;
         boolean chained = false;
         if (reason == ServerAnimationController.StopReason.NATURAL_END && inst.playerUuid != null) {
            chained = startNextQueuedForPlayerDeferringRestore(world, inst, instanceId);
         }

         StopAnimationS2CPayload payload = new StopAnimationS2CPayload(instanceId, stopTick);

         for (ServerPlayerEntity p : computeInstanceBroadcastTargets(world, null, inst)) {
            ServerPlayNetworking.send(p, payload);
         }

         fireStopEvent(instanceId, inst);
         if (restoreTransforms) {
            Set<UUID> effectiveSkipActors = skipRestoreActors;
            if (chained && inst.playerUuid != null) {
               if (effectiveSkipActors != null && !effectiveSkipActors.isEmpty()) {
                  Set<UUID> combined = new HashSet<>(effectiveSkipActors);
                  combined.add(inst.playerUuid);
                  effectiveSkipActors = combined;
               } else {
                  effectiveSkipActors = Set.of(inst.playerUuid);
               }
            }

            restoreTransforms(inst, effectiveSkipActors);
         } else {
            cacheNoRestoreTransition(world, inst);
         }

         restoreAiForActors(inst.world, inst.actorUuids);
         releasePlayerLocks(inst.world, inst.actorUuids);
         processPendingAiRestores(inst.world);
         removeActiveInstance(instanceId);
         if (reason == ServerAnimationController.StopReason.NATURAL_END) {
            if (chained) {
               sendOrLogDebug(world, null, NonDebugChatCategory.INFO, dbg("queue_continued_for_player"), false);
            }
         } else {
            handleQueueOnStop(world, inst, reason);
         }

         return true;
      } else {
         return false;
      }
   }

   public static boolean shouldAllowActorDamage(ServerWorld world, LivingEntity actor, DamageSource source) {
      UUID actorUuid = actor.getUuid();
      long now = world.getTime();
      if (source != null && source.isOf(DamageTypes.IN_WALL)) {
         Map<UUID, Long> grace = IN_WALL_GRACE_BY_WORLD.get(world.getRegistryKey());
         if (grace != null) {
            Long until = grace.get(actorUuid);
            if (until != null) {
               if (now <= until) {
                  return false;
               }

               grace.remove(actorUuid);
            }
         }

         if (ACTIVE_INSTANCES.isEmpty()) {
            return true;
         }

         for (ServerAnimationController.ActiveInstance inst : ACTIVE_INSTANCES.values()) {
            if (inst.world == world && now >= inst.startTick && inst.actorUuidSet.contains(actorUuid)) {
               return false;
            }
         }
      }

      if (source != null && source.isOf(DamageTypes.DROWN) && isUnderwaterBreathingProtected(world, actorUuid)) {
         return false;
      }

      if (ACTIVE_INSTANCES.isEmpty()) {
         return true;
      }

      for (Entry<UUID, ServerAnimationController.ActiveInstance> entry : List.copyOf(ACTIVE_INSTANCES.entrySet())) {
         ServerAnimationController.ActiveInstance inst = entry.getValue();
         if (inst.world == world && now >= inst.startTick && inst.actorUuidSet.contains(actorUuid)) {
            if (inst.ignoreAttackers && source != null && source.getAttacker() instanceof MobEntity) {
               return false;
            }

            if (inst.damageBehavior == NonAnimationDamageBehavior.BLOCK_DAMAGE) {
               return false;
            }
         }
      }

      return true;
   }

   private static void stopDamageSensitiveInstances(ServerWorld world, LivingEntity actor) {
      if (!ACTIVE_INSTANCES.isEmpty()) {
         UUID actorUuid = actor.getUuid();
         long now = world.getTime();
         List<UUID> stopInstances = null;

         for (Entry<UUID, ServerAnimationController.ActiveInstance> entry : List.copyOf(ACTIVE_INSTANCES.entrySet())) {
            ServerAnimationController.ActiveInstance inst = entry.getValue();
            if (inst.world == world
               && now >= inst.startTick
               && inst.actorUuidSet.contains(actorUuid)
               && inst.damageBehavior == NonAnimationDamageBehavior.STOP_ON_DAMAGE) {
               if (stopInstances == null) {
                  stopInstances = new ArrayList<>();
               }

               stopInstances.add(entry.getKey());
            }
         }

         if (stopInstances != null) {
            for (UUID id : stopInstances) {
               stopInstance(world, id, true, ServerAnimationController.StopReason.DAMAGE_STOP);
            }
         }
      }
   }

   public static boolean shouldIgnoreAttackers(ServerWorld world, LivingEntity actor) {
      if (ACTIVE_INSTANCES.isEmpty()) {
         return false;
      }

      UUID actorUuid = actor.getUuid();

      for (ServerAnimationController.ActiveInstance inst : ACTIVE_INSTANCES.values()) {
         if (inst.world == world && inst.ignoreAttackers && inst.actorUuidSet.contains(actorUuid)) {
            return true;
         }
      }

      return false;
   }

   private static boolean isUnderwaterBreathingProtected(ServerWorld world, UUID actorUuid) {
      if (world != null && actorUuid != null && !ACTIVE_INSTANCES.isEmpty()) {
         for (ServerAnimationController.ActiveInstance inst : ACTIVE_INSTANCES.values()) {
            if (inst.world == world && inst.underwaterBreathing && inst.actorUuidSet.contains(actorUuid)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static ServerAnimationController.ActiveInstance findActiveInstanceForActors(ServerWorld world, List<UUID> actorUuids) {
      if (actorUuids != null && !actorUuids.isEmpty()) {
         if (ACTIVE_INSTANCES.isEmpty()) {
            return null;
         }

         for (ServerAnimationController.ActiveInstance inst : ACTIVE_INSTANCES.values()) {
            if (inst.world == world) {
               for (UUID uuid : actorUuids) {
                  if (inst.actorUuidSet.contains(uuid)) {
                     return inst;
                  }
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static void stopInstanceAndBroadcast(ServerPlayerEntity requester, UUID instanceId) {
      if (requester.getEntityWorld() instanceof ServerWorld requesterWorld) {
         ServerAnimationController.ActiveInstance inst = ACTIVE_INSTANCES.get(instanceId);
         if (inst == null) {
            sendOrLogDebug(requesterWorld, requester, NonDebugChatCategory.ALWAYS, dbg("no_such_active_instance"), true);
         } else if (inst.world != requesterWorld) {
            sendOrLogDebug(requesterWorld, requester, NonDebugChatCategory.ALWAYS, dbg("instance_in_different_world"), true);
         } else {
            long now = requesterWorld.getTime();
            long stopTick = now + 1L;
            StopAnimationS2CPayload payload = new StopAnimationS2CPayload(instanceId, stopTick);

            for (ServerPlayerEntity p : computeInstanceBroadcastTargets(requesterWorld, requester, inst)) {
               ServerPlayNetworking.send(p, payload);
            }

            boolean chained = false;
            if (inst.playerUuid != null) {
               chained = startNextQueuedForPlayer(requesterWorld, inst.playerUuid, instanceId, resolveTransitionYaw(inst));
            }

            fireStopEvent(instanceId, inst);
            restoreTransforms(inst);
            restoreAiForActors(inst.world, inst.actorUuids);
            releasePlayerLocks(inst.world, inst.actorUuids);
            processPendingAiRestores(inst.world);
            removeActiveInstance(instanceId);
            sendOrLogDebug(requesterWorld, requester, NonDebugChatCategory.INFO, dbg("broadcasting_stop"), false);
            if (chained) {
               sendOrLogDebug(requesterWorld, requester, NonDebugChatCategory.INFO, dbg("queue_continued_for_player"), false);
            }

            AnimationEngine.logRuntime(
               "Broadcast STOP: instance={} stopTick={} actors={} requester={}", instanceId, stopTick, inst.actorUuids.size(), requester.getName().getString()
            );
         }
      }
   }

   public static void clearAllInstancesInWorld(ServerWorld world) {
      List<UUID> toRemove = new ArrayList<>();

      for (Entry<UUID, ServerAnimationController.ActiveInstance> entry : ACTIVE_INSTANCES.entrySet()) {
         ServerAnimationController.ActiveInstance inst = entry.getValue();
         if (inst.world == world) {
            fireStopEvent(entry.getKey(), inst);
            restoreTransforms(inst);
            restoreAiForActors(world, inst.actorUuids);
            releasePlayerLocks(world, inst.actorUuids);
            toRemove.add(entry.getKey());
            handleQueueOnStop(world, inst, ServerAnimationController.StopReason.STOP_ALL);
         }
      }

      for (UUID id : toRemove) {
         removeActiveInstance(id);
      }

      Map<UUID, ServerAnimationController.PlayerQueueState> queues = PLAYER_QUEUES_BY_WORLD.remove(world.getRegistryKey());
      if (queues != null) {
         Map<UUID, UUID> byMob = QUEUED_MOB_TO_PLAYER_BY_WORLD.get(world.getRegistryKey());
         if (byMob != null) {
            byMob.clear();
         }

         DEFERRED_QUEUE_CONTEXT_BY_WORLD.remove(world.getRegistryKey());

         for (UUID playerUuid : queues.keySet()) {
            ServerPlayerEntity player = Objects.requireNonNull(world.getServer()).getPlayerManager().getPlayer(playerUuid);
            sendOrLogDebug(world, player, NonDebugChatCategory.INFO, dbg("queue_cleared"), false);
         }
      }

      Map<UUID, Long> grace = IN_WALL_GRACE_BY_WORLD.get(world.getRegistryKey());
      if (grace != null) {
         grace.clear();
      }

      processPendingAiRestores(world);
      clearAllPlayerLocksInWorld(world);
      RECENT_NO_RESTORE_TRANSITIONS_BY_WORLD.remove(world.getRegistryKey());
   }

   private static void tickWorld(ServerWorld world) {
      cleanupInWallGrace(world, world.getTime());
      cleanupNoRestoreTransitions(world, world.getTime());
      tickActiveInstances(world);
      tickNoAiCleanup(world);
      tickPlayerQueues(world);
   }

   private static void cacheNoRestoreTransition(ServerWorld world, ServerAnimationController.ActiveInstance inst) {
      if (world != null && inst != null && inst.sharedTransform != null && inst.actorUuidSet != null && !inst.actorUuidSet.isEmpty()) {
         long expiresTick = world.getTime() + 20L;
         ServerAnimationController.RecentNoRestoreTransition transition = new ServerAnimationController.RecentNoRestoreTransition(
            expiresTick, inst.sharedTransform, inst.blockPlacement
         );
         Map<UUID, ServerAnimationController.RecentNoRestoreTransition> byActor = RECENT_NO_RESTORE_TRANSITIONS_BY_WORLD.computeIfAbsent(
            world.getRegistryKey(), ignored -> new HashMap<>()
         );

         for (UUID actorUuid : inst.actorUuidSet) {
            if (actorUuid != null) {
               byActor.put(actorUuid, transition);
            }
         }
      }
   }

   @Nullable
   private static ServerAnimationController.RecentNoRestoreTransition consumeNoRestoreTransition(
      ServerWorld world, List<UUID> actorUuids, AnimationStartContext startContext
   ) {
      if (world == null || actorUuids == null || actorUuids.isEmpty() || startContext == null) {
         return null;
      }

      if (!startContext.isJoinReplacement()) {
         return null;
      }

      Map<UUID, ServerAnimationController.RecentNoRestoreTransition> byActor = RECENT_NO_RESTORE_TRANSITIONS_BY_WORLD.get(world.getRegistryKey());
      if (byActor != null && !byActor.isEmpty()) {
         long now = world.getTime();
         ServerAnimationController.RecentNoRestoreTransition selected = null;

         for (UUID actorUuid : actorUuids) {
            ServerAnimationController.RecentNoRestoreTransition candidate = byActor.get(actorUuid);
            if (candidate != null) {
               if (candidate.expiresTick() >= now) {
                  selected = candidate;
                  break;
               }

               byActor.remove(actorUuid);
            }
         }

         for (UUID actorUuid : actorUuids) {
            byActor.remove(actorUuid);
         }

         if (byActor.isEmpty()) {
            RECENT_NO_RESTORE_TRANSITIONS_BY_WORLD.remove(world.getRegistryKey());
         }

         return selected;
      } else {
         return null;
      }
   }

   private static void cleanupNoRestoreTransitions(ServerWorld world, long now) {
      Map<UUID, ServerAnimationController.RecentNoRestoreTransition> byActor = RECENT_NO_RESTORE_TRANSITIONS_BY_WORLD.get(world.getRegistryKey());
      if (byActor != null && !byActor.isEmpty()) {
         byActor.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresTick() < now);
         if (byActor.isEmpty()) {
            RECENT_NO_RESTORE_TRANSITIONS_BY_WORLD.remove(world.getRegistryKey());
         }
      }
   }

   private static void clearRecentNoRestoreTransition(ServerWorld world, UUID actorUuid) {
      if (world != null && actorUuid != null) {
         Map<UUID, ServerAnimationController.RecentNoRestoreTransition> byActor = RECENT_NO_RESTORE_TRANSITIONS_BY_WORLD.get(world.getRegistryKey());
         if (byActor != null) {
            byActor.remove(actorUuid);
            if (byActor.isEmpty()) {
               RECENT_NO_RESTORE_TRANSITIONS_BY_WORLD.remove(world.getRegistryKey());
            }
         }
      }
   }

   private static boolean shouldBlockStart(ServerWorld world, List<UUID> actorUuids) {
      if (actorUuids != null && !actorUuids.isEmpty()) {
         for (UUID uuid : actorUuids) {
            ServerPlayerEntity player = Objects.requireNonNull(world.getServer()).getPlayerManager().getPlayer(uuid);
            if (player != null && player.getEntityWorld() == world && hasAnimationStartProtection(world, player)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static Text dbg(String key, Object... args) {
      return Text.translatable("debug.needsofnature.animation." + key, sanitizeTranslatableArgs(args));
   }

   private static Object[] sanitizeTranslatableArgs(Object... args) {
      if (args != null && args.length != 0) {
         Object[] safe = new Object[args.length];

         for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof Identifier id) {
               safe[i] = id.toString();
            } else {
               safe[i] = !(arg instanceof Text) && arg != null ? String.valueOf(arg) : arg;
            }
         }

         return safe;
      } else {
         return new Object[0];
      }
   }

   private static void sendOrLogDebug(
      ServerWorld world, @Nullable ServerPlayerEntity requester, NonDebugChatCategory category, String message, boolean forceChat
   ) {
      sendOrLogDebug(world, requester, category, Text.literal(message), forceChat);
   }

   private static void sendOrLogDebug(ServerWorld world, @Nullable ServerPlayerEntity requester, NonDebugChatCategory category, Text message, boolean forceChat) {
      AnimationEngine.logRuntime("{}", message.getString());
      if (requester != null) {
         if (AnimationEngine.shouldSendDebugChat(requester, category, forceChat)) {
            AnimationEngine.sendDebugChat(requester, category, message, forceChat);
         }
      } else if (world != null) {
         for (ServerPlayerEntity player : world.getPlayers()) {
            if (AnimationEngine.shouldSendDebugChat(player, category, forceChat)) {
               AnimationEngine.sendDebugChat(player, category, message, forceChat);
            }
         }
      }
   }

   private static void tickActiveInstances(ServerWorld world) {
      long now = world.getTime();
      List<UUID> toRemove = new ArrayList<>();
      Set<UUID> actorsToFreeze = new HashSet<>();
      Set<UUID> activePlayerUuids = new HashSet<>();
      Map<UUID, ServerAnimationController.SharedTransform> playersToLock = new HashMap<>();
      Set<UUID> underwaterPlayers = new HashSet<>();
      List<ServerAnimationController.StageAdvance> stageAdvances = new ArrayList<>();
      Map<UUID, LivingEntity> deadActors = new LinkedHashMap<>();

      for (ServerAnimationController.ActiveInstance inst : ACTIVE_INSTANCES.values()) {
         if (inst.world == world) {
            LivingEntity deadActor = findDeadLoadedActor(inst);
            if (deadActor != null) {
               deadActors.putIfAbsent(deadActor.getUuid(), deadActor);
            }
         }
      }

      for (LivingEntity deadActor : deadActors.values()) {
         stopActiveInstancesForDeadActor(world, deadActor);
      }

      for (Entry<UUID, ServerAnimationController.ActiveInstance> entry : ACTIVE_INSTANCES.entrySet()) {
         ServerAnimationController.ActiveInstance inst = entry.getValue();
         if (inst.world == world && !maybeAdvanceStages(world, entry.getKey(), inst, now, stageAdvances, toRemove)) {
            actorsToFreeze.addAll(inst.actorUuidSet);
            if (inst.playerUuid != null) {
               activePlayerUuids.add(inst.playerUuid);
            }

            if (inst.sharedTransform != null) {
               for (UUID uuid : inst.actorUuidSet) {
                  Entity e = world.getEntity(uuid);
                  if (e instanceof ServerPlayerEntity) {
                     playersToLock.put(uuid, inst.sharedTransform);
                     if (inst.underwaterBreathing) {
                        underwaterPlayers.add(uuid);
                     }
                  }
               }
            }
         }
      }

      for (ServerAnimationController.StageAdvance adv : stageAdvances) {
         ServerAnimationController.ActiveInstance inst = ACTIVE_INSTANCES.get(adv.instanceId());
         if (inst != null && inst.world == world) {
            AdvanceAnimationStageS2CPayload payload = new AdvanceAnimationStageS2CPayload(
               adv.instanceId(), adv.advanceTick(), adv.stageEndTick(), adv.stageIndex()
            );
            Set<ServerPlayerEntity> targets = computeInstanceBroadcastTargets(world, null, inst);
            rememberInstanceSubscribers(inst, targets);

            for (ServerPlayerEntity p : targets) {
               ServerPlayNetworking.send(p, payload);
               ServerPlayNetworking.send(p, new AnimationSpeedUpdateS2CPayload(adv.instanceId(), inst.speed, adv.advanceTick(), adv.stageEndTick()));
            }

            ((AnimationLifecycleEvents.StageAdvance)AnimationLifecycleEvents.STAGE_ADVANCE.invoker())
               .onStageAdvance(
                  new AnimationLifecycleEvents.StageChangedContext(world, snapshot(adv.instanceId(), inst), resolveCurrentStage(inst), adv.advanceTick())
               );
         }
      }

      freezeActiveActors(world, actorsToFreeze);
      lockActivePlayers(world, playersToLock, underwaterPlayers);
      nudgeNonActiveMobsAwayFromAnimatedPlayers(world, activePlayerUuids, now);
      cleanupStalePlayerLocks(world, playersToLock.keySet());

      for (UUID id : toRemove) {
         removeActiveInstance(id);
      }

      processPendingAiRestores(world);
   }

   @Nullable
   private static LivingEntity findDeadLoadedActor(ServerAnimationController.ActiveInstance inst) {
      for (UUID actorUuid : inst.actorUuidSet) {
         if (inst.world.getEntity(actorUuid) instanceof LivingEntity living && (!living.isAlive() || living.isRemoved())) {
            return living;
         }
      }

      return null;
   }

   @Nullable
   private static UUID startAndBroadcast(
      ServerWorld world,
      @Nullable ServerPlayerEntity requester,
      Identifier animationId,
      List<UUID> actorUuids,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      AnimationStartContext startContext,
      boolean forceChat,
      @Nullable Float transitionYaw
   ) {
      return startAndBroadcast(
         world, requester, animationId, actorUuids, actorKeys, stages, damageBehavior, ignoreAttackers, startContext, forceChat, null, null, transitionYaw
      );
   }

   @Nullable
   private static UUID startAndBroadcast(
      ServerWorld world,
      @Nullable ServerPlayerEntity requester,
      Identifier animationId,
      List<UUID> actorUuids,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      AnimationStartContext startContext,
      boolean forceChat,
      @Nullable UUID preferredAnchorUuid,
      @Nullable Float transitionYaw
   ) {
      return startAndBroadcast(
         world,
         requester,
         animationId,
         actorUuids,
         actorKeys,
         stages,
         damageBehavior,
         ignoreAttackers,
         startContext,
         forceChat,
         preferredAnchorUuid,
         null,
         transitionYaw
      );
   }

   @Nullable
   private static UUID startAndBroadcast(
      ServerWorld world,
      @Nullable ServerPlayerEntity requester,
      Identifier animationId,
      List<UUID> actorUuids,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      AnimationStartContext startContext,
      boolean forceChat,
      @Nullable UUID preferredAnchorUuid,
      @Nullable ServerAnimationController.BlockPlacement cachedBlockPlacement,
      @Nullable Float transitionYaw
   ) {
      long startTick = world.getTime() + 1L;
      UUID instanceId = UUID.randomUUID();
      Map<UUID, ServerAnimationController.OriginalTransform> originalTransforms = captureOriginalTransforms(world, actorUuids);
      AnimationStartContext safeContext = sanitizeStartContext(startContext);
      UUID effectivePositionAnchorUuid = resolveEffectivePositionAnchorUuid(world, animationId, actorUuids, actorKeys, preferredAnchorUuid);
      ServerAnimationController.RecentNoRestoreTransition joinTransition = consumeNoRestoreTransition(world, actorUuids, safeContext);
      Float effectiveTransitionYaw = transitionYaw;
      if (effectiveTransitionYaw == null && joinTransition != null) {
         effectiveTransitionYaw = joinTransition.sharedTransform().yaw();
      }

      ServerAnimationController.SharedTransform sharedTransform = joinTransition != null
         ? joinTransition.sharedTransform()
         : pickSharedTransform(actorUuids, originalTransforms, effectivePositionAnchorUuid);
      AnimationDefinitions.Definition def = AnimationDefinitions.getDefinition(animationId);
      AnimationDefinitions.AnimationPackInfo packInfo = def == null ? null : def.packInfo();
      List<List<Identifier>> modelRootsByActor = resolveModelRootsByActor(world, actorUuids, packInfo);
      AnimationDefinitions.BlockRequirements blockRequirements = def == null ? null : def.blockRequirements();
      AnimationDefinitions.WaterRequirement waterRequirement = def == null ? AnimationDefinitions.WaterRequirement.NONE : def.waterRequirement();
      ServerAnimationController.BlockPlacement placement = cachedBlockPlacement != null
         ? cachedBlockPlacement
         : (joinTransition == null ? null : joinTransition.blockPlacement());
      boolean lockOrientation = false;
      float lockedYaw = 0.0F;
      float lockedHeadYaw = 0.0F;
      float lockedPitch = 0.0F;
      if (blockRequirements != null && waterRequirement != AnimationDefinitions.WaterRequirement.NONE) {
         sendOrLogDebug(world, requester, NonDebugChatCategory.SETUP, dbg("start_cancelled_block_and_water_requirement"), forceChat);
         return null;
      }

      if (blockRequirements != null) {
         Entity anchor = pickAnchorEntity(world, actorUuids, effectivePositionAnchorUuid);
         if (placement == null) {
            placement = anchor == null ? null : findBlockPlacement(world, anchor, blockRequirements);
         }

         if (placement == null) {
            sendOrLogDebug(
               world, requester, NonDebugChatCategory.WARNING, blockRequirementFailureMessage(blockRequirements, animationId, world, actorUuids), forceChat
            );
            return null;
         }

         sharedTransform = placement.sharedTransform();
         lockOrientation = true;
         lockedYaw = sharedTransform.yaw();
         lockedHeadYaw = sharedTransform.headYaw();
         lockedPitch = sharedTransform.pitch();
      } else if (waterRequirement != AnimationDefinitions.WaterRequirement.NONE) {
         Entity waterAnchor = findWaterAnchor(world, actorUuids, waterRequirement);
         if (waterAnchor == null) {
            sendOrLogDebug(world, requester, NonDebugChatCategory.WARNING, dbg("start_cancelled_water_requirement_not_met"), forceChat);
            return null;
         }

         ServerAnimationController.SharedTransform base = sharedTransform;
         sharedTransform = new ServerAnimationController.SharedTransform(waterAnchor.getEntityPos(), base.yaw(), base.pitch(), base.headYaw(), base.bodyYaw());
      } else {
         if (anyActorOnWaterFooting(world, actorUuids)) {
            sendOrLogDebug(world, requester, NonDebugChatCategory.WARNING, dbg("start_cancelled_not_allowed_in_water"), forceChat);
            return null;
         }

         UUID queuedPlayerUuid = resolvePlayerUuid(world, actorUuids);
         ServerAnimationController.SharedTransform groundTransform = resolveQueuedPlacementTransform(world, queuedPlayerUuid, sharedTransform, safeContext);
         if (groundTransform == null) {
            groundTransform = findSafeGroundPlacement(world, sharedTransform);
         }

         if (groundTransform == null) {
            ServerAnimationController.SharedTransform dismountTransform = findMountedPlayerDismountTransform(world, actorUuids, sharedTransform);
            if (dismountTransform != null) {
               groundTransform = findSafeGroundPlacement(world, dismountTransform);
            }
         }

         if (groundTransform == null) {
            sendOrLogDebug(
               world,
               requester,
               NonDebugChatCategory.WARNING,
               dbg("start_cancelled_not_enough_space", describeAnimationIdForDebug(animationId), describeActorsForDebug(world, actorUuids)),
               forceChat
            );
            return null;
         }

         sharedTransform = groundTransform;
      }

      if (effectivePositionAnchorUuid != null && !lockOrientation) {
         lockOrientation = true;
         lockedYaw = sharedTransform.yaw();
         lockedHeadYaw = sharedTransform.headYaw();
         lockedPitch = sharedTransform.pitch();
      }

      boolean preservePlayerLook = false;
      if (effectiveTransitionYaw != null && blockRequirements == null) {
         float yaw = MathHelper.wrapDegrees(effectiveTransitionYaw);
         sharedTransform = new ServerAnimationController.SharedTransform(sharedTransform.pos(), yaw, 0.0F, yaw, yaw);
         lockOrientation = true;
         lockedYaw = yaw;
         lockedHeadYaw = yaw;
         lockedPitch = 0.0F;
         preservePlayerLook = true;
      }

      Set<UUID> actorUuidSet = Set.copyOf(new HashSet<>(actorUuids));
      List<String> safeActorKeys = actorKeys != null && actorKeys.size() == actorUuids.size() ? List.copyOf(actorKeys) : List.of();
      List<AnimationStageInfo> safeStages = stages == null ? List.of() : List.copyOf(stages);
      if (!((AnimationLifecycleEvents.AllowStart)AnimationLifecycleEvents.ALLOW_START.invoker())
         .allowStart(
            new AnimationLifecycleEvents.AllowStartContext(
               world, animationId, actorUuids, safeActorKeys, safeStages, requester, damageBehavior, ignoreAttackers, safeContext
            )
         )) {
         return null;
      }

      double initialSpeed = clampSpeed(resolveStageSpeed(stages, 0));
      AnimationStageInfo initialStage = safeStages.isEmpty() ? new AnimationStageInfo(animationId, true, 0L, true, initialSpeed) : safeStages.getFirst();
      long initialStageEndTick = resolveStageEndTick(animationId, initialStage, startTick, initialSpeed);
      Vec3d cameraOrbitTarget = placement == null ? null : placement.cameraOrbitTarget();
      StartAnimationS2CPayload payload = new StartAnimationS2CPayload(
         animationId,
         modelRootsByActor,
         instanceId,
         actorUuids,
         safeActorKeys,
         safeStages,
         startTick,
         0,
         startTick,
         initialStageEndTick,
         initialSpeed,
         lockOrientation,
         lockedYaw,
         lockedHeadYaw,
         lockedPitch,
         cameraOrbitTarget
      );
      disableAiForActors(world, actorUuids);
      if (placement != null) {
         markInWallGrace(world, actorUuids);
      }

      applySharedTransform(world, actorUuids, sharedTransform, preservePlayerLook);
      if (ignoreAttackers) {
         clearAttackTargets(world, actorUuidSet);
      }

      Set<ServerPlayerEntity> targets = computeBroadcastTargets(world, requester, actorUuids);

      for (ServerPlayerEntity p : targets) {
         ServerPlayNetworking.send(p, payload);
      }

      UUID playerUuid = resolvePlayerUuid(world, actorUuids);
      originalTransforms = applyDeferredQueueOriginal(world, playerUuid, originalTransforms, safeContext);
      boolean underwaterBreathing = waterRequirement == AnimationDefinitions.WaterRequirement.UNDERWATER;
      ServerAnimationController.ActiveInstance active = new ServerAnimationController.ActiveInstance(
         world,
         animationId,
         modelRootsByActor,
         List.copyOf(actorUuids),
         safeActorKeys,
         safeStages,
         startTick,
         originalTransforms,
         sharedTransform,
         lockOrientation,
         lockedYaw,
         lockedHeadYaw,
         lockedPitch,
         damageBehavior,
         ignoreAttackers,
         safeContext,
         underwaterBreathing,
         playerUuid,
         actorUuidSet,
         new ServerAnimationController.StageTracker(startTick),
         placement,
         cameraOrbitTarget,
         initialSpeed
      );
      registerActiveInstance(instanceId, active);
      rememberInstanceSubscribers(active, targets);
      acquirePlayerLocks(world, actorUuids);
      ((AnimationLifecycleEvents.Start)AnimationLifecycleEvents.START.invoker())
         .onStart(new AnimationLifecycleEvents.StartedContext(world, snapshot(instanceId, active), requester));
      sendOrLogDebug(world, requester, NonDebugChatCategory.INFO, dbg("actors_immediate_broadcast_start"), false);
      String requesterName = requester == null ? "api" : requester.getName().getString();
      AnimationEngine.logRuntime(
         "Broadcast START: animation={} instance={} startTick={} actors={} requester={}",
         describeAnimationIdForDebug(animationId),
         instanceId,
         startTick,
         actorUuids.size(),
         requesterName
      );
      return instanceId;
   }

   private static Set<ServerPlayerEntity> computeBroadcastTargets(ServerWorld world, @Nullable ServerPlayerEntity requester, List<UUID> actorUuids) {
      Set<ServerPlayerEntity> targets = new HashSet<>();
      if (requester != null) {
         targets.add(requester);
      }

      for (UUID uuid : actorUuids) {
         Entity e = world.getEntity(uuid);
         if (e != null) {
            targets.addAll(PlayerLookup.tracking(e));
            if (e instanceof ServerPlayerEntity sp) {
               targets.add(sp);
            }
         } else {
            ServerPlayerEntity p = Objects.requireNonNull(world.getServer()).getPlayerManager().getPlayer(uuid);
            if (p != null && p.getEntityWorld() == world) {
               targets.add(p);
               targets.addAll(PlayerLookup.tracking(p));
            }
         }
      }

      return targets;
   }

   private static Set<ServerPlayerEntity> computeInstanceBroadcastTargets(
      ServerWorld world, @Nullable ServerPlayerEntity requester, ServerAnimationController.ActiveInstance inst
   ) {
      Set<ServerPlayerEntity> targets = computeBroadcastTargets(world, requester, inst == null ? List.of() : inst.actorUuids);
      if (world != null && inst != null && !inst.subscribedPlayerUuids.isEmpty()) {
         for (UUID playerUuid : inst.subscribedPlayerUuids) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerUuid);
            if (player != null && player.getEntityWorld() == world) {
               targets.add(player);
            }
         }

         return targets;
      } else {
         return targets;
      }
   }

   private static void rememberInstanceSubscribers(ServerAnimationController.ActiveInstance inst, Set<ServerPlayerEntity> targets) {
      if (inst != null && targets != null && !targets.isEmpty()) {
         for (ServerPlayerEntity player : targets) {
            if (player != null && player.getEntityWorld() == inst.world) {
               inst.subscribedPlayerUuids.add(player.getUuid());
            }
         }
      }
   }

   private static void removePlayerFromInstanceSubscribers(UUID playerUuid) {
      if (playerUuid != null && !ACTIVE_INSTANCES.isEmpty()) {
         for (ServerAnimationController.ActiveInstance inst : ACTIVE_INSTANCES.values()) {
            if (inst != null) {
               inst.subscribedPlayerUuids.remove(playerUuid);
            }
         }
      }
   }

   private static void fireStopEvent(UUID instanceId, ServerAnimationController.ActiveInstance inst) {
      if (inst != null) {
         ((AnimationLifecycleEvents.Stop)AnimationLifecycleEvents.STOP.invoker())
            .onStop(new AnimationLifecycleEvents.StoppedContext(inst.world, snapshot(instanceId, inst)));
      }
   }

   private static boolean allSelectedPlayersNearRequester(List<Entity> actors, ServerPlayerEntity requester, double maxDistance) {
      if (actors != null && requester != null) {
         double maxDistanceSq = maxDistance * maxDistance;

         for (Entity actor : actors) {
            if (actor instanceof PlayerEntity playerActor && playerActor.squaredDistanceTo(requester) > maxDistanceSq) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static Vec3d computeCentroid(List<? extends Entity> entities) {
      double x = 0.0;
      double y = 0.0;
      double z = 0.0;

      for (Entity e : entities) {
         Vec3d p = e.getEntityPos();
         x += p.x;
         y += p.y;
         z += p.z;
      }

      double n = entities.size();
      return new Vec3d(x / n, y / n, z / n);
   }

   private static Map<UUID, ServerAnimationController.OriginalTransform> captureOriginalTransforms(ServerWorld world, List<UUID> actorUuids) {
      Map<UUID, ServerAnimationController.OriginalTransform> originals = new HashMap<>();

      for (UUID uuid : actorUuids) {
         Entity e = world.getEntity(uuid);
         if (e instanceof LivingEntity living) {
            Vec3d restorePos = living instanceof ServerPlayerEntity player ? resolveMountedPlayerRestorePosition(player) : null;
            originals.put(
               uuid,
               new ServerAnimationController.OriginalTransform(
                  world.getRegistryKey(), living.getEntityPos(), living.getYaw(), living.getPitch(), living.getHeadYaw(), living.getBodyYaw(), restorePos
               )
            );
         } else if (e != null) {
            originals.put(
               uuid,
               new ServerAnimationController.OriginalTransform(world.getRegistryKey(), e.getEntityPos(), e.getYaw(), e.getPitch(), e.getYaw(), e.getYaw())
            );
         }
      }

      return originals;
   }

   @Nullable
   private static UUID resolveEffectivePositionAnchorUuid(
      ServerWorld world, Identifier animationId, List<UUID> actorUuids, List<String> actorKeys, @Nullable UUID preferredAnchorUuid
   ) {
      Entity explicitAnchor = resolveAnchorEntity(world, actorUuids, preferredAnchorUuid);
      if (explicitAnchor != null) {
         return explicitAnchor.getUuid();
      }

      AnimationDefinitions.Definition def = AnimationDefinitions.getDefinition(animationId);
      String anchorActorKey = def == null ? null : normalizeActorKey(def.positionAnchorActor());
      if (anchorActorKey == null) {
         return null;
      }

      if (actorKeys != null && actorKeys.size() == actorUuids.size()) {
         for (int i = 0; i < actorUuids.size(); i++) {
            if (anchorActorKey.equals(normalizeActorKey(actorKeys.get(i)))) {
               Entity anchor = resolveAnchorEntity(world, actorUuids, actorUuids.get(i));
               return anchor == null ? null : anchor.getUuid();
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @Nullable
   private static String normalizeActorKey(@Nullable String actorKey) {
      if (actorKey == null) {
         return null;
      }

      String value = actorKey.trim();
      return value.isEmpty() ? null : value.toLowerCase(Locale.ROOT);
   }

   @Nullable
   private static Entity resolveAnchorEntity(ServerWorld world, List<UUID> actorUuids, @Nullable UUID anchorUuid) {
      if (world == null || actorUuids == null || actorUuids.isEmpty() || anchorUuid == null) {
         return null;
      }

      if (!actorUuids.contains(anchorUuid)) {
         return null;
      }

      Entity entity = world.getEntity(anchorUuid);
      if (entity != null) {
         return entity;
      }

      ServerPlayerEntity player = Objects.requireNonNull(world.getServer()).getPlayerManager().getPlayer(anchorUuid);
      return player != null && player.getEntityWorld() == world ? player : null;
   }

   private static ServerAnimationController.SharedTransform pickSharedTransform(
      List<UUID> actorUuids, Map<UUID, ServerAnimationController.OriginalTransform> originals, @Nullable UUID anchorUuid
   ) {
      if (anchorUuid != null) {
         ServerAnimationController.OriginalTransform anchored = originals.get(anchorUuid);
         if (anchored != null) {
            return new ServerAnimationController.SharedTransform(anchored.pos(), anchored.yaw(), anchored.pitch(), anchored.headYaw(), anchored.bodyYaw());
         }
      }

      for (UUID uuid : actorUuids) {
         ServerAnimationController.OriginalTransform ot = originals.get(uuid);
         if (ot != null) {
            return new ServerAnimationController.SharedTransform(ot.pos(), ot.yaw(), ot.pitch(), ot.headYaw(), ot.bodyYaw());
         }
      }

      return new ServerAnimationController.SharedTransform(Vec3d.ZERO, 0.0F, 0.0F, 0.0F, 0.0F);
   }

   private static Entity pickAnchorEntity(ServerWorld world, List<UUID> actorUuids, @Nullable UUID preferredAnchorUuid) {
      Entity anchor = resolveAnchorEntity(world, actorUuids, preferredAnchorUuid);
      return anchor != null ? anchor : pickAnchorEntity(world, actorUuids);
   }

   private static Entity pickAnchorEntity(ServerWorld world, List<UUID> actorUuids) {
      for (UUID uuid : actorUuids) {
         Entity e = world.getEntity(uuid);
         if (e instanceof PlayerEntity) {
            return e;
         }
      }

      for (UUID uuid : actorUuids) {
         Entity e = world.getEntity(uuid);
         if (e != null) {
            return e;
         }
      }

      return null;
   }

   private static boolean waterRequirementMet(ServerWorld world, @Nullable AnimationDefinitions.Definition def, List<UUID> actorUuids) {
      AnimationDefinitions.WaterRequirement req = def == null ? AnimationDefinitions.WaterRequirement.NONE : def.waterRequirement();
      return req == AnimationDefinitions.WaterRequirement.NONE ? !anyActorOnWaterFooting(world, actorUuids) : findWaterAnchor(world, actorUuids, req) != null;
   }

   private static boolean anyActorOnWaterFooting(ServerWorld world, List<UUID> actorUuids) {
      for (UUID uuid : actorUuids) {
         Entity e = world.getEntity(uuid);
         if (e != null) {
            BlockPos underFeet = e.getBlockPos().down();
            if (world.getFluidState(underFeet).isIn(FluidTags.WATER)) {
               return true;
            }
         }
      }

      return false;
   }

   @Nullable
   private static Entity findWaterAnchor(ServerWorld world, List<UUID> actorUuids, AnimationDefinitions.WaterRequirement requirement) {
      for (UUID uuid : actorUuids) {
         Entity e = world.getEntity(uuid);
         if (e != null && meetsWaterRequirement(e, requirement)) {
            return e;
         }
      }

      return null;
   }

   private static boolean meetsWaterRequirement(Entity e, AnimationDefinitions.WaterRequirement requirement) {
      return switch (requirement) {
         case SURFACE -> e.isTouchingWater() && !e.isSubmergedInWater();
         case UNDERWATER -> e.isSubmergedInWater();
         case NONE -> true;
      };
   }

   @Nullable
   private static ServerAnimationController.BlockPlacement findWallPlacement(
      ServerWorld world, Entity anchor, AnimationDefinitions.BlockRequirements requirements
   ) {
      Vec3d anchorPos = anchor.getEntityPos();
      BlockPos anchorBlock = BlockPos.ofFloored(anchorPos);
      int radius = getBlockScanRadius();
      double bestDistSq = Double.MAX_VALUE;
      ServerAnimationController.BlockPlacement best = null;
      int minY = anchorBlock.getY() - 1;
      int maxY = anchorBlock.getY() + 1;

      for (int y = minY; y <= maxY; y++) {
         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               BlockPos pos = new BlockPos(anchorBlock.getX() + dx, y, anchorBlock.getZ() + dz);

               for (Direction facing : HORIZONTAL_DIRECTIONS) {
                  ServerAnimationController.WallSegment segment = resolveWallSegment(world, pos, facing, requirements);
                  if (segment != null && checkWallClearance(world, segment, facing, requirements.clearance())) {
                     Vec3d sharedPos = segment.surfaceCenter();
                     double distSq = sharedPos.squaredDistanceTo(anchorPos);
                     if (!(distSq >= bestDistSq) && hasBlockRequirementLineOfSight(world, anchor, sharedPos)) {
                        float yaw = directionToYaw(facing.getOpposite());
                        bestDistSq = distSq;
                        ServerAnimationController.SharedTransform transform = new ServerAnimationController.SharedTransform(sharedPos, yaw, 0.0F, yaw, yaw);
                        best = new ServerAnimationController.BlockPlacement(segment.columns().getFirst().pos(), facing, transform, null, false);
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   @Nullable
   private static ServerAnimationController.BlockPlacement findBlockPlacement(
      ServerWorld world, Entity anchor, AnimationDefinitions.BlockRequirements requirements
   ) {
      if (requirements == null) {
         return null;
      } else if (requirements.isWall()) {
         return findWallPlacement(world, anchor, requirements);
      } else {
         return requirements.isCenterSupport() ? findCenterSupportPlacement(world, anchor, requirements) : null;
      }
   }

   @Nullable
   private static ServerAnimationController.BlockPlacement findCenterSupportPlacement(
      ServerWorld world, Entity anchor, AnimationDefinitions.BlockRequirements requirements
   ) {
      Vec3d anchorPos = anchor.getEntityPos();
      BlockPos anchorBlock = BlockPos.ofFloored(anchorPos);
      int radius = getBlockScanRadius();
      double bestDistSq = Double.MAX_VALUE;
      ServerAnimationController.BlockPlacement best = null;
      int minY = anchorBlock.getY() - 1;
      int maxY = anchorBlock.getY() + 1;

      for (int y = minY; y <= maxY; y++) {
         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               BlockPos supportPos = new BlockPos(anchorBlock.getX() + dx, y, anchorBlock.getZ() + dz);
               Double supportTopOffset = supportTopOffset(world, supportPos, requirements.support(), requirements.blocks());
               if (supportTopOffset != null) {
                  Direction[] directions = directionsByDistanceToAnchor(anchorPos, supportPos, supportTopOffset);
                  if (requirements.placement() != AnimationDefinitions.CenterPlacement.SURFACE_FOOTPRINT) {
                     if (checkCenterSupportSurfaceRadius(world, supportPos, supportTopOffset, requirements.surfaceRadius(), requirements.clearance())) {
                        for (Direction direction : directions) {
                           if (checkCenterDirectionalClearance(world, supportPos, direction, supportTopOffset, requirements.clearance())) {
                              BlockPos sidePos = supportPos.offset(direction);
                              Vec3d sideCenter = new Vec3d(sidePos.getX() + 0.5, supportPos.getY() + supportTopOffset, sidePos.getZ() + 0.5);
                              double distSq = sideCenter.squaredDistanceTo(anchorPos);
                              if (distSq < bestDistSq) {
                                 float yaw = directionToYaw(direction.getOpposite());
                                 Vec3d sharedPos = new Vec3d(supportPos.getX() + 0.5, supportPos.getY(), supportPos.getZ() + 0.5);
                                 if (hasBlockRequirementLineOfSight(world, anchor, sideCenter)) {
                                    bestDistSq = distSq;
                                    ServerAnimationController.SharedTransform transform = new ServerAnimationController.SharedTransform(
                                       sharedPos, yaw, 0.0F, yaw, yaw
                                    );
                                    best = new ServerAnimationController.BlockPlacement(supportPos, direction, transform, null, true);
                                 }
                              }
                           }
                        }
                     }
                  } else {
                     for (Direction direction : directions) {
                        for (int lateralSign : new int[]{1, -1}) {
                           if (checkCenterSurfaceFootprint(world, supportPos, direction, lateralSign, supportTopOffset, requirements)) {
                              ServerAnimationController.BedFootprintFacing bedFacing = resolveBedFootprintFacing(
                                 world, supportPos, direction, lateralSign, requirements
                              );
                              if (!bedFacing.bedFootprint() || bedFacing.facing() != null) {
                                 Vec3d footprintCenter = centerSurfaceFootprint(
                                    supportPos, direction, lateralSign, supportTopOffset, requirements.surfaceFootprint()
                                 );
                                 double distSq = footprintCenter.squaredDistanceTo(anchorPos);
                                 if (distSq < bestDistSq) {
                                    Direction placementFacing = bedFacing.facing() == null ? direction : bedFacing.facing().getOpposite();
                                    BlockPos placementPos = supportPos;
                                    if (bedFacing.facing() != null) {
                                       AnimationDefinitions.SurfaceFootprint footprint = requirements.surfaceFootprint();
                                       Direction side = lateralSign >= 0 ? direction.rotateYCounterclockwise() : direction.rotateYClockwise();
                                       placementPos = supportPos.offset(bedFacing.facing(), Math.max(0, footprint.depth() - 1))
                                          .offset(side, Math.max(0, footprint.width() - 1));
                                    }

                                    float yaw = directionToYaw(placementFacing.getOpposite());
                                    Vec3d sharedPos = new Vec3d(placementPos.getX() + 0.5, placementPos.getY(), placementPos.getZ() + 0.5);
                                    if (hasBlockRequirementLineOfSight(world, anchor, footprintCenter)) {
                                       bestDistSq = distSq;
                                       ServerAnimationController.SharedTransform transform = new ServerAnimationController.SharedTransform(
                                          sharedPos, yaw, 0.0F, yaw, yaw
                                       );
                                       best = new ServerAnimationController.BlockPlacement(placementPos, placementFacing, transform, null, true);
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private static Direction[] directionsByDistanceToAnchor(Vec3d anchorPos, BlockPos supportPos, double supportTopOffset) {
      Direction[] directions = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
      Arrays.sort(directions, Comparator.comparingDouble(direction -> {
         BlockPos side = supportPos.offset(direction);
         Vec3d center = new Vec3d(side.getX() + 0.5, supportPos.getY() + supportTopOffset, side.getZ() + 0.5);
         return center.squaredDistanceTo(anchorPos);
      }));
      return directions;
   }

   @Nullable
   private static Double supportTopOffset(
      ServerWorld world, BlockPos pos, AnimationDefinitions.CenterSupport support, @Nullable AnimationDefinitions.BlockPredicate blocks
   ) {
      if (support == null) {
         return null;
      }

      BlockState state = world.getBlockState(pos);
      if (!state.getFluidState().isEmpty()) {
         return null;
      }

      if (!matchesBlockPredicate(state, blocks)) {
         return null;
      }

      if (support == AnimationDefinitions.CenterSupport.SURFACE) {
         if (blocks != null && !blocks.isEmpty()) {
            VoxelShape shape = state.getCollisionShape(world, pos);
            return shape.isEmpty() ? null : shape.getMax(Axis.Y);
         } else {
            return null;
         }
      } else {
         if (support == AnimationDefinitions.CenterSupport.HALF_HEIGHT) {
            return isHalfHeightSupport(state) ? 0.5625 : null;
         }

         if (state.getBlock() instanceof SlabBlock) {
            SlabType type = (SlabType)state.get(SlabBlock.TYPE);
            if (support == AnimationDefinitions.CenterSupport.SLAB) {
               return type == SlabType.BOTTOM ? 0.5 : null;
            }

            if (support == AnimationDefinitions.CenterSupport.FULL_BLOCK) {
               return type != SlabType.TOP && type != SlabType.DOUBLE ? null : 1.0;
            }
         }

         return support == AnimationDefinitions.CenterSupport.FULL_BLOCK && isFullHeightSupport(world, pos, state) ? 1.0 : null;
      }
   }

   private static List<List<Identifier>> resolveModelRootsByActor(
      ServerWorld world, List<UUID> actorUuids, @Nullable AnimationDefinitions.AnimationPackInfo packInfo
   ) {
      if (actorUuids != null && !actorUuids.isEmpty()) {
         if (packInfo == null) {
            return Collections.nCopies(actorUuids.size(), List.of());
         }

         List<List<Identifier>> rootsByActor = new ArrayList<>(actorUuids.size());

         for (UUID actorUuid : actorUuids) {
            Entity actor = resolveAnchorEntity(world, actorUuids, actorUuid);
            Identifier entityTypeId = actor == null ? null : Registries.ENTITY_TYPE.getId(actor.getType());
            rootsByActor.add(packInfo.modelRootsFor(entityTypeId));
         }

         return List.copyOf(rootsByActor);
      } else {
         return List.of();
      }
   }

   private static boolean isHalfHeightSupport(BlockState state) {
      return state.getBlock() instanceof BedBlock ? true : state.getBlock() instanceof SlabBlock && state.get(SlabBlock.TYPE) == SlabType.BOTTOM;
   }

   private static boolean isFullHeightSupport(ServerWorld world, BlockPos pos, BlockState state) {
      if (state.isAir() || state.isReplaceable()) {
         return false;
      }

      if (state.isSolidBlock(world, pos)) {
         return true;
      }

      VoxelShape shape = state.getCollisionShape(world, pos);
      return !shape.isEmpty() && shape.getMax(Axis.Y) >= 0.999;
   }

   private static boolean checkCenterDirectionalClearance(
      ServerWorld world, BlockPos supportPos, Direction facing, double supportTopOffset, AnimationDefinitions.Clearance clearance
   ) {
      if (clearance == null) {
         return true;
      }

      int lateralRadius = Math.max(1, clearance.width()) - 1;
      int height = Math.max(1, clearance.height());
      int depth = Math.max(1, clearance.depth());
      Direction left = facing.rotateYCounterclockwise();

      for (int d = 1; d <= depth; d++) {
         for (int w = -lateralRadius; w <= lateralRadius; w++) {
            BlockPos check = supportPos.offset(facing, d).offset(left, w);
            if (!isDirectionalClearanceFree(world, check, supportPos.getY(), height)) {
               return false;
            }

            if (!isSolidFloorBlock(world, check.down())) {
               return false;
            }
         }
      }

      return true;
   }

   private static boolean checkCenterSurfaceFootprint(
      ServerWorld world, BlockPos supportPos, Direction facing, int lateralSign, double supportTopOffset, AnimationDefinitions.BlockRequirements requirements
   ) {
      AnimationDefinitions.SurfaceFootprint footprint = requirements.surfaceFootprint();
      if (footprint == null) {
         return false;
      }

      int width = Math.max(1, footprint.width());
      int depth = Math.max(1, footprint.depth());
      int height = Math.max(1, footprint.height());
      int margin = Math.max(0, footprint.margin());
      Direction side = lateralSign >= 0 ? facing.rotateYCounterclockwise() : facing.rotateYClockwise();
      double supportTopY = supportPos.getY() + supportTopOffset;

      for (int d = -margin; d < depth + margin; d++) {
         for (int w = -margin; w < width + margin; w++) {
            BlockPos check = supportPos.offset(facing, d).offset(side, w);
            boolean insideFootprint = d >= 0 && d < depth && w >= 0 && w < width;
            if (insideFootprint) {
               Double checkTopOffset = supportTopOffset(world, check, requirements.support(), requirements.blocks());
               if (checkTopOffset == null || Math.abs(checkTopOffset - supportTopOffset) > 1.0E-4) {
                  return false;
               }

               if (!isSpaceClearAboveSupport(world, check, supportTopY, height)) {
                  return false;
               }
            } else if (!isSurfaceFootprintMarginClear(world, check, supportTopY, height)) {
               return false;
            }
         }
      }

      return true;
   }

   private static ServerAnimationController.BedFootprintFacing resolveBedFootprintFacing(
      ServerWorld world, BlockPos supportPos, Direction facing, int lateralSign, AnimationDefinitions.BlockRequirements requirements
   ) {
      AnimationDefinitions.SurfaceFootprint footprint = requirements.surfaceFootprint();
      if (footprint == null) {
         return ServerAnimationController.BedFootprintFacing.none();
      }

      List<BlockPos> positions = centerSurfaceFootprintPositions(supportPos, facing, lateralSign, footprint);
      if (positions.isEmpty()) {
         return ServerAnimationController.BedFootprintFacing.none();
      }

      int bedCount = 0;

      for (BlockPos pos : positions) {
         if (world.getBlockState(pos).getBlock() instanceof BedBlock) {
            bedCount++;
         }
      }

      if (bedCount == 0) {
         return ServerAnimationController.BedFootprintFacing.none();
      }

      boolean bedOnlyPredicate = isBedOnlyBlockPredicate(requirements.blocks());
      if (bedCount != positions.size()) {
         return bedOnlyPredicate ? ServerAnimationController.BedFootprintFacing.invalid() : ServerAnimationController.BedFootprintFacing.none();
      }

      Set<BlockPos> footprintPositions = new HashSet<>(positions);
      Set<BlockPos> pairedPositions = new HashSet<>();
      Direction expectedFacing = null;

      for (BlockPos pos : positions) {
         BlockState state = world.getBlockState(pos);
         Direction bedFacing = (Direction)state.get(BedBlock.FACING);
         BedPart part = (BedPart)state.get(BedBlock.PART);
         if (expectedFacing == null) {
            expectedFacing = bedFacing;
         } else if (expectedFacing != bedFacing) {
            return ServerAnimationController.BedFootprintFacing.invalid();
         }

         BlockPos otherPos = part == BedPart.FOOT ? pos.offset(bedFacing) : pos.offset(bedFacing.getOpposite());
         if (!footprintPositions.contains(otherPos)) {
            return ServerAnimationController.BedFootprintFacing.invalid();
         }

         BlockState otherState = world.getBlockState(otherPos);
         if (!(otherState.getBlock() instanceof BedBlock)) {
            return ServerAnimationController.BedFootprintFacing.invalid();
         }

         if (otherState.get(BedBlock.FACING) != bedFacing) {
            return ServerAnimationController.BedFootprintFacing.invalid();
         }

         if (otherState.get(BedBlock.PART) == part) {
            return ServerAnimationController.BedFootprintFacing.invalid();
         }

         pairedPositions.add(pos);
         pairedPositions.add(otherPos);
      }

      if (!pairedPositions.containsAll(footprintPositions) || expectedFacing == null) {
         return ServerAnimationController.BedFootprintFacing.invalid();
      } else {
         return facing == expectedFacing && lateralSign >= 0
            ? ServerAnimationController.BedFootprintFacing.valid(expectedFacing)
            : ServerAnimationController.BedFootprintFacing.invalid();
      }
   }

   private static List<BlockPos> centerSurfaceFootprintPositions(
      BlockPos supportPos, Direction facing, int lateralSign, AnimationDefinitions.SurfaceFootprint footprint
   ) {
      int width = Math.max(1, footprint == null ? 1 : footprint.width());
      int depth = Math.max(1, footprint == null ? 1 : footprint.depth());
      Direction side = lateralSign >= 0 ? facing.rotateYCounterclockwise() : facing.rotateYClockwise();
      List<BlockPos> positions = new ArrayList<>(width * depth);

      for (int d = 0; d < depth; d++) {
         for (int w = 0; w < width; w++) {
            positions.add(supportPos.offset(facing, d).offset(side, w));
         }
      }

      return positions;
   }

   private static boolean isBedOnlyBlockPredicate(@Nullable AnimationDefinitions.BlockPredicate blocks) {
      if (blocks != null && !blocks.isEmpty()) {
         for (Identifier tagId : blocks.tagIds()) {
            if (!BEDS_BLOCK_TAG_ID.equals(tagId)) {
               return false;
            }
         }

         for (Identifier blockId : blocks.blockIds()) {
            if (!(Registries.BLOCK.get(blockId) instanceof BedBlock)) {
               return false;
            }
         }

         return !blocks.tagIds().isEmpty() || !blocks.blockIds().isEmpty();
      } else {
         return false;
      }
   }

   private static boolean isSurfaceFootprintMarginClear(ServerWorld world, BlockPos pos, double supportTopY, int height) {
      Box clearanceBox = new Box(pos.getX(), supportTopY + 1.0E-4, pos.getZ(), pos.getX() + 1.0, supportTopY + Math.max(1, height), pos.getZ() + 1.0);
      return world.isSpaceEmpty(clearanceBox) && !containsFluid(world, clearanceBox);
   }

   private static Vec3d centerSurfaceFootprint(
      BlockPos supportPos, Direction facing, int lateralSign, double supportTopOffset, AnimationDefinitions.SurfaceFootprint footprint
   ) {
      int width = Math.max(1, footprint == null ? 1 : footprint.width());
      int depth = Math.max(1, footprint == null ? 1 : footprint.depth());
      Direction side = lateralSign >= 0 ? facing.rotateYCounterclockwise() : facing.rotateYClockwise();
      double forwardOffset = (depth - 1) * 0.5;
      double sideOffset = (width - 1) * 0.5;
      return new Vec3d(
         supportPos.getX() + 0.5 + facing.getOffsetX() * forwardOffset + side.getOffsetX() * sideOffset,
         supportPos.getY() + supportTopOffset,
         supportPos.getZ() + 0.5 + facing.getOffsetZ() * forwardOffset + side.getOffsetZ() * sideOffset
      );
   }

   private static boolean checkCenterSupportSurfaceRadius(
      ServerWorld world, BlockPos supportPos, double supportTopOffset, int surfaceRadius, AnimationDefinitions.Clearance clearance
   ) {
      int radius = Math.max(0, surfaceRadius);
      int height = Math.max(1, clearance == null ? 2 : clearance.height());
      double supportTopY = supportPos.getY() + supportTopOffset;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            BlockPos check = supportPos.add(dx, 0, dz);
            if (!isSameOrLowerSurface(world, check, supportTopOffset)) {
               return false;
            }

            if (!isSpaceClearAboveSupport(world, check, supportTopY, height)) {
               return false;
            }
         }
      }

      return true;
   }

   private static boolean isSameOrLowerSurface(ServerWorld world, BlockPos pos, double supportTopOffset) {
      BlockState state = world.getBlockState(pos);
      if (!state.getFluidState().isEmpty()) {
         return false;
      } else if (!state.isAir() && !state.isReplaceable()) {
         VoxelShape shape = state.getCollisionShape(world, pos);
         return shape.isEmpty() ? true : shape.getMax(Axis.Y) <= supportTopOffset + 1.0E-4;
      } else {
         return true;
      }
   }

   private static boolean isDirectionalClearanceFree(ServerWorld world, BlockPos pos, int supportY, int height) {
      Box clearanceBox = new Box(pos.getX(), supportY + 1.0E-4, pos.getZ(), pos.getX() + 1.0, supportY + Math.max(1, height), pos.getZ() + 1.0);
      return world.isSpaceEmpty(clearanceBox) && !containsFluid(world, clearanceBox);
   }

   private static boolean isSpaceClearAboveSupport(ServerWorld world, BlockPos pos, double supportTopY, int height) {
      Box clearanceBox = new Box(pos.getX(), supportTopY + 1.0E-4, pos.getZ(), pos.getX() + 1.0, supportTopY + Math.max(1, height), pos.getZ() + 1.0);
      return world.isSpaceEmpty(clearanceBox) && !containsFluid(world, clearanceBox);
   }

   private static Text blockRequirementFailureMessage(
      AnimationDefinitions.BlockRequirements requirements, Identifier animationId, ServerWorld world, List<UUID> actorUuids
   ) {
      String animation = describeAnimationIdForDebug(animationId);
      String actors = describeActorsForDebug(world, actorUuids);
      return requirements != null && requirements.isCenterSupport()
         ? dbg("start_cancelled_no_valid_center_support", animation, actors)
         : dbg("start_cancelled_no_valid_wall", animation, actors);
   }

   private static boolean checkWallClearance(
      ServerWorld world, ServerAnimationController.WallSegment segment, Direction facing, AnimationDefinitions.Clearance clearance
   ) {
      if (clearance == null) {
         return true;
      }

      if (segment != null && !segment.columns().isEmpty()) {
         int sidePadding = Math.max(1, clearance.width()) - 1;
         int height = Math.max(1, clearance.height());
         int depth = Math.max(1, clearance.depth());
         Direction left = facing.rotateYCounterclockwise();
         BlockPos firstWallPos = segment.columns().getFirst().pos();
         int wallWidth = segment.columns().size();

         for (int dy = 0; dy < height; dy++) {
            for (int d = 1; d <= depth; d++) {
               for (int w = -sidePadding; w < wallWidth + sidePadding; w++) {
                  BlockPos check = firstWallPos.offset(facing, d).offset(left, w).up(dy);
                  if (!isFreeSpace(world, check)) {
                     return false;
                  }

                  if (dy == 0 && !isSolidFloorBlock(world, check.down())) {
                     return false;
                  }
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   @Nullable
   private static ServerAnimationController.WallSegment resolveWallSegment(
      ServerWorld world, BlockPos firstPos, Direction facing, AnimationDefinitions.BlockRequirements requirements
   ) {
      if (world != null && firstPos != null && facing != null && requirements != null) {
         AnimationDefinitions.WallWidth width = requirements.width();
         int minimum = Math.max(1, width == null ? 1 : width.min());
         Direction lateral = facing.rotateYCounterclockwise();
         List<ServerAnimationController.WallColumn> columns = new ArrayList<>(minimum);

         for (int index = 0; index < minimum; index++) {
            ServerAnimationController.WallColumn column = resolveWallColumn(world, firstPos.offset(lateral, index), facing, requirements);
            if (column == null) {
               return null;
            }

            columns.add(column);
         }

         Integer maximum = width == null ? null : width.max();
         if (maximum != null && exceedsMaximumWallRun(world, firstPos, facing, requirements, maximum)) {
            return null;
         }

         double x = 0.0;
         double y = 0.0;
         double z = 0.0;

         for (ServerAnimationController.WallColumn column : columns) {
            x += column.surfaceAnchor().x;
            y += column.surfaceAnchor().y;
            z += column.surfaceAnchor().z;
         }

         double divisor = columns.size();
         return new ServerAnimationController.WallSegment(List.copyOf(columns), new Vec3d(x / divisor, y / divisor, z / divisor));
      } else {
         return null;
      }
   }

   @Nullable
   private static ServerAnimationController.WallColumn resolveWallColumn(
      ServerWorld world, BlockPos pos, Direction facing, AnimationDefinitions.BlockRequirements requirements
   ) {
      if (!isSolidFloorBlock(world, pos.down())) {
         return null;
      }

      if (!isWallRequirementBlock(world, pos, facing, requirements)) {
         return null;
      }

      int wallHeight = countWallHeight(world, pos, facing, requirements.height(), requirements);
      if (matchesWallHeight(wallHeight, requirements.height()) && hasWallHeightCapClearance(world, pos, wallHeight, requirements.height())) {
         VoxelShape wallShape = world.getBlockState(pos).getCollisionShape(world, pos);
         if (wallShape.isEmpty()) {
            return null;
         }

         EnumMap<Direction, Double> broadSurfaces = findBroadWallSurfaces(wallShape.getBoundingBoxes());
         Double broadSurface = broadSurfaces.get(facing);
         return !broadSurfaces.isEmpty() && broadSurface == null
            ? null
            : new ServerAnimationController.WallColumn(pos, wallSurfaceAnchor(pos, wallShape, facing, broadSurface));
      } else {
         return null;
      }
   }

   private static boolean exceedsMaximumWallRun(
      ServerWorld world, BlockPos origin, Direction facing, AnimationDefinitions.BlockRequirements requirements, int maximum
   ) {
      int count = 1;
      Direction lateral = facing.rotateYCounterclockwise();

      for (Direction scanDirection : new Direction[]{lateral, lateral.getOpposite()}) {
         for (BlockPos cursor = origin.offset(scanDirection);
            count <= maximum && resolveWallColumn(world, cursor, facing, requirements) != null;
            cursor = cursor.offset(scanDirection)
         ) {
            if (++count > maximum) {
               return true;
            }
         }
      }

      return false;
   }

   private static int getBlockScanRadius() {
      int radius = NeedsOfNature.getConfig().blockSearchRadius();
      return Math.max(1, radius);
   }

   private static boolean hasBlockRequirementLineOfSight(ServerWorld world, Entity anchor, Vec3d target) {
      if (world != null && anchor != null && target != null) {
         Vec3d start = anchor.getEyePos();
         Vec3d end = target.add(0.0, 0.75, 0.0);
         if (start.squaredDistanceTo(end) < 1.0E-6) {
            return true;
         }

         BlockHitResult result = world.raycast(new RaycastContext(start, end, ShapeType.COLLIDER, FluidHandling.NONE, anchor));
         return result == null || result.getType() == Type.MISS;
      } else {
         return false;
      }
   }

   private static void markInWallGrace(ServerWorld world, List<UUID> actorUuids) {
      if (actorUuids != null && !actorUuids.isEmpty()) {
         long until = world.getTime() + 5L;
         Map<UUID, Long> grace = IN_WALL_GRACE_BY_WORLD.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>());

         for (UUID uuid : actorUuids) {
            Long existing = grace.get(uuid);
            if (existing == null || existing < until) {
               grace.put(uuid, until);
            }
         }
      }
   }

   private static void cleanupInWallGrace(ServerWorld world, long now) {
      Map<UUID, Long> grace = IN_WALL_GRACE_BY_WORLD.get(world.getRegistryKey());
      if (grace != null && !grace.isEmpty()) {
         grace.entrySet().removeIf(entry -> entry.getValue() < now);
      }
   }

   private static EnumMap<Direction, Double> findBroadWallSurfaces(List<Box> boxes) {
      EnumMap<Direction, Double> surfaces = new EnumMap<>(Direction.class);

      for (Direction direction : HORIZONTAL_DIRECTIONS) {
         Double surface = findBroadWallSurface(boxes, direction);
         if (surface != null) {
            surfaces.put(direction, surface);
         }
      }

      return surfaces;
   }

   @Nullable
   private static Double findBroadWallSurface(List<Box> boxes, Direction facing) {
      Axis lateralAxis = facing.getAxis() == Axis.X ? Axis.Z : Axis.X;
      boolean positive = facing == Direction.EAST || facing == Direction.SOUTH;
      Double bestSurface = null;

      for (Box candidate : boxes) {
         double surface = boxBoundary(candidate, facing);
         if (!(coveredSpanAtSurface(boxes, facing, lateralAxis, surface) + 1.0E-6 < 0.5)
            && (bestSurface == null || (positive ? surface > bestSurface : surface < bestSurface))) {
            bestSurface = surface;
         }
      }

      return bestSurface;
   }

   private static double coveredSpanAtSurface(List<Box> boxes, Direction facing, Axis lateralAxis, double surface) {
      List<Box> surfaceBoxes = new ArrayList<>();

      for (Box box : boxes) {
         if (Math.abs(boxBoundary(box, facing) - surface) <= 1.0E-6) {
            surfaceBoxes.add(box);
         }
      }

      surfaceBoxes.sort(Comparator.comparingDouble(boxx -> boxMin(boxx, lateralAxis)));
      double covered = 0.0;
      double intervalEnd = Double.NEGATIVE_INFINITY;

      for (Box box : surfaceBoxes) {
         double min = boxMin(box, lateralAxis);
         double max = boxMax(box, lateralAxis);
         if (min > intervalEnd) {
            covered += max - min;
            intervalEnd = max;
         } else if (max > intervalEnd) {
            covered += max - intervalEnd;
            intervalEnd = max;
         }
      }

      return covered;
   }

   private static Vec3d wallSurfaceAnchor(BlockPos pos, VoxelShape shape, Direction facing, @Nullable Double broadSurface) {
      double centerX = pos.getX() + 0.5;
      double centerZ = pos.getZ() + 0.5;
      double surface = broadSurface != null ? broadSurface : wallShapeBoundary(shape, facing);

      return switch (facing) {
         case EAST -> new Vec3d(pos.getX() + surface + 0.5, pos.getY(), centerZ);
         case WEST -> new Vec3d(pos.getX() + surface - 0.5, pos.getY(), centerZ);
         case SOUTH -> new Vec3d(centerX, pos.getY(), pos.getZ() + surface + 0.5);
         case NORTH -> new Vec3d(centerX, pos.getY(), pos.getZ() + surface - 0.5);
         default -> throw new IllegalArgumentException("Wall facing must be horizontal: " + facing);
      };
   }

   private static double wallShapeBoundary(VoxelShape shape, Direction facing) {
      return switch (facing) {
         case EAST -> shape.getMax(Axis.X);
         case WEST -> shape.getMin(Axis.X);
         case SOUTH -> shape.getMax(Axis.Z);
         case NORTH -> shape.getMin(Axis.Z);
         default -> throw new IllegalArgumentException("Wall facing must be horizontal: " + facing);
      };
   }

   private static double boxBoundary(Box box, Direction facing) {
      return switch (facing) {
         case EAST -> box.maxX;
         case WEST -> box.minX;
         case SOUTH -> box.maxZ;
         case NORTH -> box.minZ;
         default -> throw new IllegalArgumentException("Wall facing must be horizontal: " + facing);
      };
   }

   private static double boxMin(Box box, Axis axis) {
      return switch (axis) {
         case X -> box.minX;
         case Y -> box.minY;
         case Z -> box.minZ;
         default -> throw new MatchException(null, null);
      };
   }

   private static double boxMax(Box box, Axis axis) {
      return switch (axis) {
         case X -> box.maxX;
         case Y -> box.maxY;
         case Z -> box.maxZ;
         default -> throw new MatchException(null, null);
      };
   }

   private static float directionToYaw(Direction dir) {
      return switch (dir) {
         case EAST -> -90.0F;
         case WEST -> 90.0F;
         default -> 0.0F;
         case NORTH -> 180.0F;
      };
   }

   private static int countWallHeight(
      ServerWorld world, BlockPos pos, Direction facing, AnimationDefinitions.WallHeight height, AnimationDefinitions.BlockRequirements requirements
   ) {
      if (height == null) {
         return 0;
      }

      int min = Math.max(1, height.min());
      Integer max = height.max();
      int limit = max != null ? max + 1 : min;
      int actual = 0;

      for (BlockPos cursor = pos; actual < limit && isWallRequirementBlock(world, cursor, facing, requirements); cursor = cursor.up()) {
         actual++;
      }

      return actual;
   }

   private static boolean matchesWallHeight(int actual, AnimationDefinitions.WallHeight height) {
      if (height == null) {
         return false;
      }

      int min = Math.max(1, height.min());
      Integer max = height.max();
      return actual < min ? false : max == null || actual <= max;
   }

   private static boolean hasWallHeightCapClearance(ServerWorld world, BlockPos pos, int actual, AnimationDefinitions.WallHeight height) {
      if (height == null || height.max() == null) {
         return true;
      } else {
         return actual != height.max() ? true : isFreeSpace(world, pos.up(actual));
      }
   }

   private static boolean isSolidWallBlock(ServerWorld world, BlockPos pos) {
      BlockState state = world.getBlockState(pos);
      if (!state.getFluidState().isEmpty()) {
         return false;
      }

      if (state.isAir() || state.isReplaceable()) {
         return false;
      }

      if (!state.isIn(BlockTags.FENCES) && !state.isIn(BlockTags.WALLS) && !state.isIn(WALL_SURFACES_BLOCK_TAG)) {
         VoxelShape shape = state.getCollisionShape(world, pos);
         if (shape.isEmpty()) {
            return false;
         } else if (!(shape.getMin(Axis.X) > 1.0E-6)
            && !(shape.getMax(Axis.X) < 0.999999)
            && !(shape.getMin(Axis.Z) > 1.0E-6)
            && !(shape.getMax(Axis.Z) < 0.999999)) {
            VoxelShape requiredCore = VoxelShapes.cuboid(0.0625, 0.0, 0.0625, 0.9375, 0.875, 0.9375);
            return !VoxelShapes.matchesAnywhere(requiredCore, shape, BooleanBiFunction.ONLY_FIRST);
         } else {
            return false;
         }
      } else {
         return true;
      }
   }

   private static boolean isWallRequirementBlock(
      ServerWorld world, BlockPos pos, Direction wallSurfaceFacing, AnimationDefinitions.BlockRequirements requirements
   ) {
      AnimationDefinitions.BlockPredicate blocks = requirements == null ? null : requirements.blocks();
      if (blocks != null && !blocks.isEmpty()) {
         BlockState state = world.getBlockState(pos);
         if (!state.getFluidState().isEmpty()) {
            return false;
         } else if (!matchesBlockPredicate(state, blocks)) {
            return false;
         } else {
            return !matchesWallFacing(state, wallSurfaceFacing, requirements.facing()) ? false : !state.getCollisionShape(world, pos).isEmpty();
         }
      } else {
         return isSolidWallBlock(world, pos);
      }
   }

   private static boolean matchesWallFacing(BlockState state, Direction wallSurfaceFacing, @Nullable AnimationDefinitions.WallFacing requiredFacing) {
      if (requiredFacing == null) {
         return true;
      }

      Direction blockFacing = null;

      for (Entry<Property<?>, Comparable<?>> entry : state.getEntries().entrySet()) {
         if ("facing".equals(entry.getKey().getName()) && entry.getValue() instanceof Direction direction) {
            blockFacing = direction;
            break;
         }
      }

      return requiredFacing.matches(blockFacing, wallSurfaceFacing);
   }

   private static boolean matchesBlockPredicate(BlockState state, @Nullable AnimationDefinitions.BlockPredicate blocks) {
      if (blocks != null && !blocks.isEmpty()) {
         Identifier blockId = Registries.BLOCK.getId(state.getBlock());
         if (blocks.blockIds().contains(blockId)) {
            return true;
         }

         for (Identifier tagId : blocks.tagIds()) {
            if (state.isIn(TagKey.of(RegistryKeys.BLOCK, tagId))) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private static boolean isSolidFloorBlock(ServerWorld world, BlockPos pos) {
      BlockState state = world.getBlockState(pos);
      if (!state.getFluidState().isEmpty()) {
         return false;
      }

      if (state.isAir() || state.isReplaceable()) {
         return false;
      }

      if (state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.WALLS)) {
         return false;
      }

      if (state.isSolidBlock(world, pos)) {
         return true;
      }

      VoxelShape shape = state.getCollisionShape(world, pos);
      if (shape.isEmpty()) {
         return false;
      }

      double top = shape.getMax(Axis.Y);
      if (top < 0.875) {
         return false;
      }

      VoxelShape requiredSupport = VoxelShapes.cuboid(0.0625, top - 0.0625, 0.0625, 0.9375, top, 0.9375);
      return !VoxelShapes.matchesAnywhere(requiredSupport, shape, BooleanBiFunction.ONLY_FIRST);
   }

   private static boolean isFreeSpace(ServerWorld world, BlockPos pos) {
      BlockState state = world.getBlockState(pos);
      return !state.getFluidState().isEmpty() ? false : state.isAir() || state.isReplaceable();
   }

   private static void applySharedTransform(
      ServerWorld world, List<UUID> actorUuids, ServerAnimationController.SharedTransform sharedTransform, boolean preservePlayerLook
   ) {
      Map<UUID, ServerAnimationController.AiDisableState> states = AI_DISABLE_STATE_BY_WORLD.get(world.getRegistryKey());

      for (UUID uuid : actorUuids) {
         Entity e = world.getEntity(uuid);
         if (e != null) {
            switch (e) {
               case ServerPlayerEntity player:
                  preparePlayerForAnimationTeleport(player);
                  float teleportYaw = preservePlayerLook ? player.getYaw() : sharedTransform.yaw();
                  float teleportPitch = preservePlayerLook ? player.getPitch() : sharedTransform.pitch();
                  player.networkHandler.requestTeleport(sharedTransform.pos().x, sharedTransform.pos().y, sharedTransform.pos().z, teleportYaw, teleportPitch);
                  player.setHeadYaw(sharedTransform.headYaw());
                  player.setBodyYaw(sharedTransform.bodyYaw());
                  break;
               case LivingEntity living:
                  living.refreshPositionAndAngles(
                     sharedTransform.pos().x, sharedTransform.pos().y, sharedTransform.pos().z, sharedTransform.yaw(), sharedTransform.pitch()
                  );
                  living.setHeadYaw(sharedTransform.headYaw());
                  living.setBodyYaw(sharedTransform.bodyYaw());
                  break;
               default:
                  e.refreshPositionAndAngles(
                     sharedTransform.pos().x, sharedTransform.pos().y, sharedTransform.pos().z, sharedTransform.yaw(), sharedTransform.pitch()
                  );
            }

            e.setVelocity(Vec3d.ZERO);
            e.fallDistance = 0.0;
            if (e instanceof MobEntity mob && states != null) {
               ServerAnimationController.AiDisableState state = states.get(uuid);
               if (state != null) {
                  state.lockedYaw = sharedTransform.yaw();
                  state.lockedHeadYaw = sharedTransform.headYaw();
                  state.lockedBodyYaw = sharedTransform.bodyYaw();
                  state.lockedPitch = sharedTransform.pitch();
               }
            }
         }
      }
   }

   private static void preparePlayerForAnimationTeleport(ServerPlayerEntity player) {
      if (player != null) {
         if (player.hasVehicle()) {
            player.stopRiding();
         }

         if (player.isSleeping()) {
            player.wakeUp(true, true);
         }

         player.setVelocity(Vec3d.ZERO);
         player.fallDistance = 0.0;
      }
   }

   @Nullable
   private static ServerAnimationController.SharedTransform findSafeGroundPlacement(
      ServerWorld world, ServerAnimationController.SharedTransform sharedTransform
   ) {
      Vec3d startPos = sharedTransform.pos();
      ServerAnimationController.FloorHit baseFloor = findNearestFloor(world, startPos, 2);
      if (!baseFloor.found) {
         return null;
      }

      ServerAnimationController.SharedTransform base = withSharedPosition(sharedTransform, baseFloor.pos);
      if (isGroundPlacementClear(world, baseFloor.pos)) {
         return base;
      }

      for (ServerAnimationController.GroundPlacementOffset offset : GROUND_PLACEMENT_OFFSETS) {
         Vec3d candidateStart = startPos.add(offset.dx(), 0.0, offset.dz());
         ServerAnimationController.FloorHit candidateFloor = findNearestFloor(world, candidateStart, 2);
         if (candidateFloor.found && isGroundPlacementClear(world, candidateFloor.pos)) {
            return withSharedPosition(sharedTransform, candidateFloor.pos);
         }
      }

      return null;
   }

   private static ServerAnimationController.SharedTransform withSharedPosition(ServerAnimationController.SharedTransform sharedTransform, Vec3d pos) {
      return new ServerAnimationController.SharedTransform(
         pos, sharedTransform.yaw(), sharedTransform.pitch(), sharedTransform.headYaw(), sharedTransform.bodyYaw()
      );
   }

   @Nullable
   private static ServerAnimationController.SharedTransform findMountedPlayerDismountTransform(
      ServerWorld world, List<UUID> actorUuids, ServerAnimationController.SharedTransform base
   ) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty() && base != null) {
         for (UUID actorUuid : actorUuids) {
            if (world.getEntity(actorUuid) instanceof ServerPlayerEntity player && player.hasVehicle()) {
               Entity vehicle = player.getVehicle();
               if (vehicle != null && !vehicle.isRemoved()) {
                  Vec3d dismountPos = vehicle.updatePassengerForDismount(player);
                  if (dismountPos == null) {
                     dismountPos = vehicle.getEntityPos();
                  }

                  return withSharedPosition(base, dismountPos);
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @Nullable
   private static Vec3d resolveMountedPlayerRestorePosition(ServerPlayerEntity player) {
      if (player != null && player.hasVehicle()) {
         Entity vehicle = player.getVehicle();
         if (vehicle != null && !vehicle.isRemoved()) {
            Vec3d dismountPos = vehicle.updatePassengerForDismount(player);
            if (dismountPos == null || vehicle.getBoundingBox().expand(0.05).contains(dismountPos)) {
               Box box = vehicle.getBoundingBox();
               dismountPos = new Vec3d(vehicle.getX(), box.maxY + 0.05, vehicle.getZ());
            }

            return dismountPos;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static ServerAnimationController.SharedTransform resolveQueuedPlacementTransform(
      ServerWorld world, @Nullable UUID playerUuid, ServerAnimationController.SharedTransform base, AnimationStartContext startContext
   ) {
      if (world == null || playerUuid == null || base == null) {
         return null;
      }

      if (startContext != null && startContext.queueContinuation()) {
         ServerAnimationController.DeferredQueueContext context = getDeferredQueueContext(world, playerUuid);
         if (context != null && context.previousPlacement() != null) {
            ServerAnimationController.BlockPlacement previousPlacement = context.previousPlacement();
            if (!isCenterSupportPlacement(previousPlacement)) {
               ServerAnimationController.SharedTransform previousTransform = previousPlacement.sharedTransform();
               return isGroundPlacementClear(world, previousTransform.pos()) ? withSharedPosition(base, previousTransform.pos()) : null;
            }

            double supportTopOffset = supportSurfaceTopOffset(world, previousPlacement.wallPos());
            if (supportTopOffset <= 0.0) {
               return null;
            }

            Vec3d topPos = new Vec3d(
               previousPlacement.wallPos().getX() + 0.5, previousPlacement.wallPos().getY() + supportTopOffset, previousPlacement.wallPos().getZ() + 0.5
            );
            return !isGroundPlacementClear(world, topPos) ? null : withSharedPosition(base, topPos);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static double supportSurfaceTopOffset(ServerWorld world, BlockPos pos) {
      BlockState state = world.getBlockState(pos);
      if (!state.getFluidState().isEmpty()) {
         return -1.0;
      }

      VoxelShape shape = state.getCollisionShape(world, pos);
      return shape.isEmpty() ? -1.0 : shape.getMax(Axis.Y);
   }

   private static boolean isGroundPlacementClear(ServerWorld world, Vec3d pos) {
      Box clearanceBox = new Box(pos.x - 0.75, pos.y + 0.01, pos.z - 0.75, pos.x + 0.75, pos.y + 2.0, pos.z + 0.75);
      return world.isSpaceEmpty(clearanceBox) && !containsFluid(world, clearanceBox);
   }

   private static boolean containsFluid(ServerWorld world, Box box) {
      int minX = MathHelper.floor(box.minX);
      int maxX = MathHelper.floor(box.maxX - 1.0E-7);
      int minY = MathHelper.floor(box.minY);
      int maxY = MathHelper.floor(box.maxY - 1.0E-7);
      int minZ = MathHelper.floor(box.minZ);
      int maxZ = MathHelper.floor(box.maxZ - 1.0E-7);
      Mutable mutable = new Mutable();

      for (int y = minY; y <= maxY; y++) {
         for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
               mutable.set(x, y, z);
               if (!world.getFluidState(mutable).isEmpty()) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static ServerAnimationController.FloorHit findNearestFloor(ServerWorld world, Vec3d startPos, int maxDropBlocks) {
      BlockPos startBlock = BlockPos.ofFloored(startPos);
      Mutable cursor = new Mutable(startBlock.getX(), startBlock.getY(), startBlock.getZ());
      int minY = Math.max(world.getBottomY(), startBlock.getY() - maxDropBlocks);
      double maxFloorY = startPos.y + 0.001;

      for (int y = startBlock.getY(); y >= minY; y--) {
         cursor.setY(y);
         BlockState state = world.getBlockState(cursor);
         if (!state.isAir()) {
            VoxelShape shape = state.getCollisionShape(world, cursor);
            if (!shape.isEmpty()) {
               double top = shape.getMax(Axis.Y);
               if (!(top <= 0.0)) {
                  double floorY = y + top;
                  if (floorY <= maxFloorY) {
                     return new ServerAnimationController.FloorHit(true, new Vec3d(startPos.x, floorY, startPos.z));
                  }
               }
            }
         }
      }

      return new ServerAnimationController.FloorHit(false, startPos);
   }

   private static List<ServerAnimationController.GroundPlacementOffset> createGroundPlacementOffsets() {
      int steps = Math.max(1, MathHelper.ceil(4.0));
      double maxDistSq = 4.0;
      List<ServerAnimationController.GroundPlacementOffset> offsets = new ArrayList<>();

      for (int ix = -steps; ix <= steps; ix++) {
         for (int iz = -steps; iz <= steps; iz++) {
            if (ix != 0 || iz != 0) {
               double dx = ix * 0.5;
               double dz = iz * 0.5;
               double distSq = dx * dx + dz * dz;
               if (!(distSq > maxDistSq)) {
                  offsets.add(new ServerAnimationController.GroundPlacementOffset(dx, dz, distSq));
               }
            }
         }
      }

      offsets.sort(Comparator.comparingDouble(ServerAnimationController.GroundPlacementOffset::distSq));
      return List.copyOf(offsets);
   }

   private static void restoreTransforms(ServerAnimationController.ActiveInstance inst) {
      restoreTransforms(inst, null);
   }

   private static void restoreTransforms(ServerAnimationController.ActiveInstance inst, @Nullable Set<UUID> skipActors) {
      Map<UUID, ServerAnimationController.OriginalTransform> originals = inst.originalTransforms;
      if (originals != null && !originals.isEmpty()) {
         Map<UUID, ServerAnimationController.AiDisableState> states = AI_DISABLE_STATE_BY_WORLD.get(inst.world.getRegistryKey());

         for (Entry<UUID, ServerAnimationController.OriginalTransform> entry : originals.entrySet()) {
            if (skipActors == null || !skipActors.contains(entry.getKey())) {
               ServerAnimationController.OriginalTransform ot = entry.getValue();
               if (ot.worldKey() == inst.world.getRegistryKey()) {
                  Entity e = inst.world.getEntity(entry.getKey());
                  if (e != null) {
                     switch (e) {
                        case ServerPlayerEntity player:
                           preparePlayerForAnimationTeleport(player);
                           Vec3d restorePos = ot.restorePosOrPos();
                           player.networkHandler.requestTeleport(restorePos.x, restorePos.y, restorePos.z, player.getYaw(), player.getPitch());
                           clearDeferredQueueOriginal(inst.world, entry.getKey());
                           break;
                        case LivingEntity living:
                           living.refreshPositionAndAngles(ot.pos().x, ot.pos().y, ot.pos().z, ot.yaw(), ot.pitch());
                           living.setHeadYaw(ot.headYaw());
                           living.setBodyYaw(ot.bodyYaw());
                           break;
                        default:
                           e.refreshPositionAndAngles(ot.pos().x, ot.pos().y, ot.pos().z, ot.yaw(), ot.pitch());
                     }

                     e.setVelocity(Vec3d.ZERO);
                     e.fallDistance = 0.0;
                     if (e instanceof MobEntity && states != null) {
                        ServerAnimationController.AiDisableState state = states.get(entry.getKey());
                        if (state != null) {
                           state.lockedYaw = ot.yaw();
                           state.lockedHeadYaw = ot.headYaw();
                           state.lockedBodyYaw = ot.bodyYaw();
                           state.lockedPitch = ot.pitch();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Nullable
   private static ServerAnimationController.OriginalTransform getOriginalTransformForActor(ServerAnimationController.ActiveInstance inst, UUID actorUuid) {
      return inst != null && actorUuid != null && inst.originalTransforms != null ? inst.originalTransforms.get(actorUuid) : null;
   }

   private static boolean startNextQueuedForPlayerDeferringRestore(
      ServerWorld world, ServerAnimationController.ActiveInstance inst, @Nullable UUID ignoreInstanceId
   ) {
      if (world != null && inst != null && inst.playerUuid != null) {
         boolean inserted = captureDeferredQueueContext(world, inst.playerUuid, inst);
         boolean started = startNextQueuedForPlayer(world, inst.playerUuid, ignoreInstanceId, resolveTransitionYaw(inst));
         if (!started && inserted) {
            clearDeferredQueueOriginal(world, inst.playerUuid);
         }

         return started;
      } else {
         return false;
      }
   }

   private static boolean captureDeferredQueueContextFromActiveInstance(ServerWorld world, UUID playerUuid) {
      ServerAnimationController.ActiveInstance active = findActiveInstanceForActors(world, List.of(playerUuid));
      return captureDeferredQueueContext(world, playerUuid, active);
   }

   private static boolean captureDeferredQueueContext(ServerWorld world, UUID playerUuid, @Nullable ServerAnimationController.ActiveInstance inst) {
      if (world != null && playerUuid != null && inst != null) {
         ServerAnimationController.OriginalTransform original = getOriginalTransformForActor(inst, playerUuid);
         if (original == null) {
            return false;
         }

         Map<UUID, ServerAnimationController.DeferredQueueContext> byPlayer = DEFERRED_QUEUE_CONTEXT_BY_WORLD.computeIfAbsent(
            world.getRegistryKey(), ignored -> new HashMap<>()
         );
         ServerAnimationController.DeferredQueueContext context = new ServerAnimationController.DeferredQueueContext(
            original, queueContinuationPlacementForInstance(inst)
         );
         return byPlayer.putIfAbsent(playerUuid, context) == null;
      } else {
         return false;
      }
   }

   private static Map<UUID, ServerAnimationController.OriginalTransform> applyDeferredQueueOriginal(
      ServerWorld world, @Nullable UUID playerUuid, Map<UUID, ServerAnimationController.OriginalTransform> originals, AnimationStartContext startContext
   ) {
      if (world == null || playerUuid == null || originals == null || originals.isEmpty()) {
         return originals;
      }

      if (startContext != null && startContext.queueContinuation()) {
         ServerAnimationController.OriginalTransform deferred = getDeferredQueueOriginal(world, playerUuid);
         if (deferred == null) {
            return originals;
         }

         Map<UUID, ServerAnimationController.OriginalTransform> carried = new HashMap<>(originals);
         carried.put(playerUuid, deferred);
         return carried;
      } else {
         return originals;
      }
   }

   @Nullable
   private static ServerAnimationController.OriginalTransform getDeferredQueueOriginal(ServerWorld world, UUID playerUuid) {
      ServerAnimationController.DeferredQueueContext context = getDeferredQueueContext(world, playerUuid);
      return context == null ? null : context.originalTransform();
   }

   private static void clearDeferredQueueOriginal(ServerWorld world, UUID playerUuid) {
      Map<UUID, ServerAnimationController.DeferredQueueContext> byPlayer = DEFERRED_QUEUE_CONTEXT_BY_WORLD.get(world.getRegistryKey());
      if (byPlayer != null) {
         byPlayer.remove(playerUuid);
         if (byPlayer.isEmpty()) {
            DEFERRED_QUEUE_CONTEXT_BY_WORLD.remove(world.getRegistryKey());
         }
      }
   }

   @Nullable
   private static ServerAnimationController.DeferredQueueContext getDeferredQueueContext(ServerWorld world, UUID playerUuid) {
      Map<UUID, ServerAnimationController.DeferredQueueContext> byPlayer = DEFERRED_QUEUE_CONTEXT_BY_WORLD.get(world.getRegistryKey());
      return byPlayer == null ? null : byPlayer.get(playerUuid);
   }

   @Nullable
   private static ServerAnimationController.BlockPlacement queueContinuationPlacementForInstance(ServerAnimationController.ActiveInstance inst) {
      return inst != null && inst.blockPlacement != null ? inst.blockPlacement : null;
   }

   private static boolean isCenterSupportPlacement(ServerAnimationController.BlockPlacement placement) {
      return placement != null && placement.centerSupport();
   }

   private static void disableAiForActors(ServerWorld world, List<UUID> actorUuids) {
      if (!actorUuids.isEmpty()) {
         Map<UUID, ServerAnimationController.AiDisableState> states = AI_DISABLE_STATE_BY_WORLD.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>());

         for (UUID uuid : actorUuids) {
            if (world.getEntity(uuid) instanceof MobEntity mob) {
               ServerAnimationController.AiDisableState state = states.get(uuid);
               if (state == null) {
                  boolean originalAiDisabled = mob.isAiDisabled();
                  state = new ServerAnimationController.AiDisableState(originalAiDisabled);
                  states.put(uuid, state);
               } else {
                  state.locks++;
                  state.needsRestore = false;
               }

               captureOrientation(mob, state);
               applyNoAiTags(mob, state);
               mob.setAiDisabled(true);
               mob.getNavigation().stop();
               mob.setVelocity(Vec3d.ZERO);
               mob.fallDistance = 0.0;
               mob.setYaw(state.lockedYaw);
               mob.setPitch(state.lockedPitch);
               mob.setHeadYaw(state.lockedHeadYaw);
               mob.setBodyYaw(state.lockedBodyYaw);
            }
         }
      }
   }

   private static void restoreAiForActors(ServerWorld world, List<UUID> actorUuids) {
      if (!actorUuids.isEmpty()) {
         Map<UUID, ServerAnimationController.AiDisableState> states = AI_DISABLE_STATE_BY_WORLD.get(world.getRegistryKey());
         if (states != null) {
            for (UUID uuid : actorUuids) {
               ServerAnimationController.AiDisableState state = states.get(uuid);
               if (state != null) {
                  if (state.locks > 1) {
                     state.locks--;
                  } else {
                     state.locks = 0;
                     state.needsRestore = true;
                     tryRestoreAi(world, uuid, state, states);
                  }
               }
            }

            if (states.isEmpty()) {
               AI_DISABLE_STATE_BY_WORLD.remove(world.getRegistryKey());
            }
         }
      }
   }

   private static void tryRestoreAi(
      ServerWorld world, UUID actorUuid, ServerAnimationController.AiDisableState state, Map<UUID, ServerAnimationController.AiDisableState> states
   ) {
      Entity e = world.getEntity(actorUuid);
      if (e instanceof MobEntity mob) {
         restoreAiState(mob, state);
         states.remove(actorUuid);
      } else if (e != null) {
         states.remove(actorUuid);
      }
   }

   private static void processPendingAiRestores(ServerWorld world) {
      Map<UUID, ServerAnimationController.AiDisableState> states = AI_DISABLE_STATE_BY_WORLD.get(world.getRegistryKey());
      if (states != null && !states.isEmpty()) {
         Iterator<Entry<UUID, ServerAnimationController.AiDisableState>> it = states.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, ServerAnimationController.AiDisableState> entry = it.next();
            ServerAnimationController.AiDisableState state = entry.getValue();
            if (state.locks <= 0 && state.needsRestore) {
               Entity e = world.getEntity(entry.getKey());
               if (e instanceof MobEntity mob) {
                  restoreAiState(mob, state);
                  it.remove();
               } else if (e != null) {
                  it.remove();
               }
            }
         }

         if (states.isEmpty()) {
            AI_DISABLE_STATE_BY_WORLD.remove(world.getRegistryKey());
         }
      }
   }

   private static void freezeActiveActors(ServerWorld world, Set<UUID> actorUuids) {
      if (!actorUuids.isEmpty()) {
         Map<UUID, ServerAnimationController.AiDisableState> states = AI_DISABLE_STATE_BY_WORLD.get(world.getRegistryKey());
         if (states != null && !states.isEmpty()) {
            for (UUID uuid : actorUuids) {
               ServerAnimationController.AiDisableState state = states.get(uuid);
               if (state != null && world.getEntity(uuid) instanceof MobEntity mob) {
                  freezeMobDuringAnimation(mob, state);
               }
            }
         }
      }
   }

   private static void captureOrientation(MobEntity mob, ServerAnimationController.AiDisableState state) {
      state.lockedYaw = mob.getYaw();
      state.lockedHeadYaw = mob.getHeadYaw();
      state.lockedBodyYaw = mob.getBodyYaw();
      state.lockedPitch = mob.getPitch();
   }

   private static void freezeMobDuringAnimation(MobEntity mob, ServerAnimationController.AiDisableState state) {
      mob.setVelocity(Vec3d.ZERO);
      mob.fallDistance = 0.0;
      mob.setYaw(state.lockedYaw);
      mob.setPitch(state.lockedPitch);
      mob.setHeadYaw(state.lockedHeadYaw);
      mob.setBodyYaw(state.lockedBodyYaw);
   }

   private static void applyNoAiTags(MobEntity mob, ServerAnimationController.AiDisableState state) {
      mob.addCommandTag("afw_noai");
      if (state.originalAiDisabled) {
         mob.addCommandTag("afw_noai_orig");
      } else {
         mob.removeCommandTag("afw_noai_orig");
      }
   }

   public static void sendActiveInstancesToPlayer(ServerPlayerEntity player) {
      if (player.getEntityWorld() instanceof ServerWorld world) {
         for (Entry<UUID, ServerAnimationController.ActiveInstance> entry : ACTIVE_INSTANCES.entrySet()) {
            ServerAnimationController.ActiveInstance inst = entry.getValue();
            if (inst.world == world) {
               List<String> safeKeys = sanitizeActorKeys(inst.actorKeys, inst.actorUuids.size());
               List<AnimationStageInfo> safeStages = inst.stages == null ? List.of() : List.copyOf(inst.stages);
               int stageIndex = inst.stageTracker == null ? 0 : inst.stageTracker.stageIndex;
               long stageStartTick = inst.stageTracker == null ? inst.startTick : inst.stageTracker.stageStartTick;
               StartAnimationS2CPayload payload = new StartAnimationS2CPayload(
                  inst.animationId,
                  inst.modelRootsByActor,
                  entry.getKey(),
                  inst.actorUuids,
                  safeKeys,
                  safeStages,
                  inst.startTick,
                  stageIndex,
                  stageStartTick,
                  resolvePublishedStageEndTick(inst),
                  inst.speed,
                  inst.lockOrientation,
                  inst.lockedYaw,
                  inst.lockedHeadYaw,
                  inst.lockedPitch,
                  inst.cameraOrbitTarget
               );
               ServerPlayNetworking.send(player, payload);
               inst.subscribedPlayerUuids.add(player.getUuid());
            }
         }
      }
   }

   private static void tickPlayerQueues(ServerWorld world) {
      Map<UUID, ServerAnimationController.PlayerQueueState> queues = PLAYER_QUEUES_BY_WORLD.get(world.getRegistryKey());
      if (queues != null && !queues.isEmpty()) {
         List<UUID> removePlayers = new ArrayList<>();

         for (UUID playerUuid : List.copyOf(queues.keySet())) {
            ServerPlayerEntity player = Objects.requireNonNull(world.getServer()).getPlayerManager().getPlayer(playerUuid);
            if (player == null || player.getEntityWorld() != world || !player.isAlive() || player.isRemoved()) {
               removePlayers.add(playerUuid);
            } else if (!isActorActiveIgnoringInstance(world, playerUuid, null)) {
               startNextQueuedForPlayer(world, playerUuid, null, null);
               ServerAnimationController.PlayerQueueState state = queues.get(playerUuid);
               if (state == null || state.entries.isEmpty()) {
                  removePlayers.add(playerUuid);
               }
            }
         }

         for (UUID playerUuid : removePlayers) {
            clearPlayerQueueByUuid(world, playerUuid, null, null);
         }

         if (queues.isEmpty()) {
            PLAYER_QUEUES_BY_WORLD.remove(world.getRegistryKey(), queues);
         }
      }
   }

   @Nullable
   private static UUID inferSinglePlayerUuid(ServerWorld world, List<? extends Entity> actors) {
      UUID found = null;

      for (Entity actor : actors) {
         if (actor instanceof ServerPlayerEntity player && player.getEntityWorld() == world) {
            if (found != null && !found.equals(player.getUuid())) {
               return null;
            }

            found = player.getUuid();
         }
      }

      return found;
   }

   @Nullable
   private static UUID resolvePlayerUuid(ServerWorld world, List<UUID> actorUuids) {
      if (actorUuids != null && !actorUuids.isEmpty()) {
         UUID found = null;

         for (UUID uuid : actorUuids) {
            Entity entity = world.getEntity(uuid);
            if (entity instanceof ServerPlayerEntity) {
               if (found != null && !found.equals(uuid)) {
                  return null;
               }

               found = uuid;
            }
         }

         return found;
      } else {
         return null;
      }
   }

   private static void insertQueueEntry(Deque<ServerAnimationController.QueuedEntry> entries, ServerAnimationController.QueuedEntry entry, int insertIndex) {
      List<ServerAnimationController.QueuedEntry> list = new ArrayList<>(entries);
      int pos = resolveInsertPosition(list.size(), insertIndex);
      list.add(pos, entry);
      entries.clear();
      entries.addAll(list);
   }

   private static int resolveInsertPosition(int size, int insertIndex) {
      if (insertIndex == 0) {
         return size;
      }

      if (insertIndex > 0) {
         return Math.max(0, Math.min(size, insertIndex - 1));
      }

      int fromEnd = size + insertIndex + 1;
      return Math.max(0, Math.min(size, fromEnd));
   }

   private static int describeQueuePosition(Deque<ServerAnimationController.QueuedEntry> entries, UUID entryId) {
      int idx = 1;

      for (ServerAnimationController.QueuedEntry entry : entries) {
         if (entry.entryId.equals(entryId)) {
            return idx;
         }

         idx++;
      }

      return Math.max(1, entries.size());
   }

   private static String describeActorForDebug(@Nullable ServerWorld world, @Nullable UUID actorUuid) {
      if (actorUuid != null && world != null) {
         Entity entity = world.getEntity(actorUuid);
         if (entity == null && world.getServer() != null) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(actorUuid);
            if (player != null && player.getEntityWorld() == world) {
               entity = player;
            }
         }

         if (entity == null) {
            String uuid = actorUuid.toString();
            return "missing:" + uuid.substring(0, 8);
         } else {
            String display = entity.getDisplayName().getString();
            Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());
            return display != null && !display.isBlank() ? display : typeId.getPath();
         }
      } else {
         return "(unknown)";
      }
   }

   private static String describeActorsForDebug(@Nullable ServerWorld world, @Nullable List<UUID> actorUuids) {
      if (actorUuids != null && !actorUuids.isEmpty()) {
         List<String> labels = new ArrayList<>();

         for (UUID actorUuid : actorUuids) {
            if (actorUuid != null) {
               labels.add(describeActorForDebug(world, actorUuid));
            }
         }

         if (labels.isEmpty()) {
            return "(none)";
         }

         int cap = 4;
         return labels.size() <= cap ? String.join(", ", labels) : String.join(", ", labels.subList(0, cap)) + " +" + (labels.size() - cap) + " more";
      } else {
         return "(none)";
      }
   }

   private static String describeAnimationIdForDebug(@Nullable Identifier animationId) {
      return animationId == null ? "(unknown)" : animationId.toString();
   }

   private static boolean isActorActiveIgnoringInstance(ServerWorld world, UUID actorUuid, @Nullable UUID ignoreInstanceId) {
      for (Entry<UUID, ServerAnimationController.ActiveInstance> entry : ACTIVE_INSTANCES.entrySet()) {
         if (ignoreInstanceId == null || !ignoreInstanceId.equals(entry.getKey())) {
            ServerAnimationController.ActiveInstance inst = entry.getValue();
            if (inst.world == world && inst.actorUuidSet.contains(actorUuid)) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean startNextQueuedForPlayer(ServerWorld world, UUID playerUuid, @Nullable UUID ignoreInstanceId, @Nullable Float transitionYaw) {
      Map<UUID, ServerAnimationController.PlayerQueueState> queues = PLAYER_QUEUES_BY_WORLD.get(world.getRegistryKey());
      if (queues == null) {
         return false;
      }

      ServerAnimationController.PlayerQueueState queue = queues.get(playerUuid);
      if (queue != null && !queue.entries.isEmpty()) {
         while (!queue.entries.isEmpty()) {
            ServerAnimationController.QueuedEntry entry = queue.entries.peekFirst();
            if (entry == null) {
               queue.entries.pollFirst();
            } else {
               if (isActorActiveIgnoringInstance(world, playerUuid, ignoreInstanceId)) {
                  return false;
               }

               ServerAnimationController.QueueStartResult result = tryStartQueuedEntry(world, queue, entry, transitionYaw);
               if (result == ServerAnimationController.QueueStartResult.RETRY) {
                  return false;
               }

               queue.entries.pollFirst();
               clearQueuedMobMappings(world, queue.playerUuid, entry);
               if (result == ServerAnimationController.QueueStartResult.STARTED) {
                  if (queue.entries.isEmpty()) {
                     queues.remove(queue.playerUuid);
                  }

                  return true;
               }
            }
         }

         queues.remove(queue.playerUuid);
         return false;
      } else {
         return false;
      }
   }

   private static ServerAnimationController.QueueStartResult tryStartQueuedEntry(
      ServerWorld world, ServerAnimationController.PlayerQueueState queue, ServerAnimationController.QueuedEntry entry, @Nullable Float transitionYaw
   ) {
      ServerPlayerEntity player = Objects.requireNonNull(world.getServer()).getPlayerManager().getPlayer(queue.playerUuid);
      if (player != null && player.getEntityWorld() == world && player.isAlive() && !player.isRemoved()) {
         List<Entity> resolvedActors = new ArrayList<>();
         boolean missingNonPlayer = false;

         for (UUID actorUuid : entry.actorUuids) {
            if (actorUuid.equals(queue.playerUuid)) {
               resolvedActors.add(player);
            } else {
               Entity entity = world.getEntity(actorUuid);
               if (!(entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved())) {
                  missingNonPlayer = true;
                  break;
               }

               resolvedActors.add(entity);
            }
         }

         if (missingNonPlayer) {
            sendOrLogDebug(
               world,
               player,
               NonDebugChatCategory.WARNING,
               dbg("queue_skipped_missing_non_player_actor", describeAnimationIdForDebug(entry.animationId), describeActorsForDebug(world, entry.actorUuids)),
               false
            );
            return ServerAnimationController.QueueStartResult.SKIP;
         } else if (resolvedActors.isEmpty()) {
            sendOrLogDebug(
               world,
               player,
               NonDebugChatCategory.WARNING,
               dbg("queue_skipped_no_actors_resolved", describeAnimationIdForDebug(entry.animationId), describeActorsForDebug(world, entry.actorUuids)),
               false
            );
            return ServerAnimationController.QueueStartResult.SKIP;
         } else if (hasAnimationStartProtection(world, player)) {
            return ServerAnimationController.QueueStartResult.RETRY;
         } else {
            AnimationDefinitions.Definition def = AnimationDefinitions.getDefinition(entry.animationId);
            if (def == null) {
               sendOrLogDebug(
                  world,
                  player,
                  NonDebugChatCategory.WARNING,
                  dbg("queue_skipped_missing_definition", describeAnimationIdForDebug(entry.animationId), describeActorsForDebug(world, entry.actorUuids)),
                  false
               );
               return ServerAnimationController.QueueStartResult.SKIP;
            } else {
               List<Entity> sorted = new ArrayList<>(resolvedActors);
               sorted.sort(Comparator.comparingInt(Entity::getId));
               List<UUID> uuids = sorted.stream().<UUID>map(Entity::getUuid).toList();
               if (!waterRequirementMet(world, def, uuids)) {
                  sendOrLogDebug(
                     world,
                     player,
                     NonDebugChatCategory.WARNING,
                     dbg("queue_skipped_requirements_not_met", describeAnimationIdForDebug(entry.animationId), describeActorsForDebug(world, entry.actorUuids)),
                     false
                  );
                  return ServerAnimationController.QueueStartResult.SKIP;
               } else {
                  List<String> actorKeys = AnimationDefinitions.resolveActorKeys(def, sorted, world.getRandom());
                  AnimationStartContext nextContext = sanitizeStartContext(entry.startContext()).asQueueContinuation();
                  UUID instanceId = startAndBroadcast(
                     world, player, def.id(), uuids, actorKeys, def.stages(), entry.damageBehavior, entry.ignoreAttackers, nextContext, false, transitionYaw
                  );
                  if (instanceId == null) {
                     sendOrLogDebug(
                        world,
                        player,
                        NonDebugChatCategory.WARNING,
                        dbg("queue_skipped_start_failed", describeAnimationIdForDebug(entry.animationId), describeActorsForDebug(world, entry.actorUuids)),
                        false
                     );
                     return ServerAnimationController.QueueStartResult.SKIP;
                  } else {
                     sendOrLogDebug(
                        world,
                        player,
                        NonDebugChatCategory.INFO,
                        dbg("queued_animation_started", describeAnimationIdForDebug(def.id()), describeActorsForDebug(world, uuids)),
                        false
                     );
                     return ServerAnimationController.QueueStartResult.STARTED;
                  }
               }
            }
         }
      } else {
         sendOrLogDebug(
            world,
            null,
            NonDebugChatCategory.WARNING,
            dbg("queue_skipped_player_unavailable", describeAnimationIdForDebug(entry.animationId), describeActorsForDebug(world, entry.actorUuids)),
            false
         );
         return ServerAnimationController.QueueStartResult.SKIP;
      }
   }

   private static void clearQueuedMobMappings(ServerWorld world, UUID playerUuid, ServerAnimationController.QueuedEntry entry) {
      Map<UUID, UUID> byMob = QUEUED_MOB_TO_PLAYER_BY_WORLD.get(world.getRegistryKey());
      if (byMob != null && !byMob.isEmpty() && entry != null) {
         for (UUID actorUuid : entry.actorUuids) {
            if (actorUuid != null && !actorUuid.equals(playerUuid)) {
               UUID owner = byMob.get(actorUuid);
               if (playerUuid.equals(owner)) {
                  byMob.remove(actorUuid);
               }
            }
         }
      }
   }

   private static void clearPlayerQueueByUuid(ServerWorld world, UUID playerUuid, @Nullable NonDebugChatCategory category, @Nullable Text debugMessage) {
      if (world != null && playerUuid != null) {
         clearDeferredQueueOriginal(world, playerUuid);
         Map<UUID, ServerAnimationController.PlayerQueueState> queues = PLAYER_QUEUES_BY_WORLD.get(world.getRegistryKey());
         if (queues != null) {
            ServerAnimationController.PlayerQueueState queue = queues.remove(playerUuid);
            if (queue != null) {
               for (ServerAnimationController.QueuedEntry entry : queue.entries) {
                  clearQueuedMobMappings(world, playerUuid, entry);
               }

               if (queues.isEmpty()) {
                  PLAYER_QUEUES_BY_WORLD.remove(world.getRegistryKey());
               }

               if (category != null && debugMessage != null) {
                  ServerPlayerEntity player = Objects.requireNonNull(world.getServer()).getPlayerManager().getPlayer(playerUuid);
                  sendOrLogDebug(world, player, category, debugMessage, false);
               }
            }
         }
      }
   }

   private static void handleQueueOnStop(ServerWorld world, ServerAnimationController.ActiveInstance inst, ServerAnimationController.StopReason reason) {
      if (world != null && inst != null && inst.playerUuid != null) {
         switch (reason) {
            case NATURAL_END:
               boolean started = startNextQueuedForPlayerDeferringRestore(world, inst, null);
               if (started) {
                  sendOrLogDebug(world, null, NonDebugChatCategory.INFO, dbg("queue_continued_for_player"), false);
               }
               break;
            case EXTERNAL_STOP:
            case DAMAGE_STOP:
            case ACTOR_DEATH:
            case STOP_ALL:
               clearPlayerQueueByUuid(world, inst.playerUuid, NonDebugChatCategory.INFO, dbg("queue_cleared"));
            case JOIN_REPLACE:
         }
      }
   }

   public static boolean isActorPendingOrActive(ServerWorld world, UUID actorUuid) {
      if (isActorActiveIgnoringInstance(world, actorUuid, null)) {
         return true;
      }

      Map<UUID, ServerAnimationController.PlayerQueueState> queues = PLAYER_QUEUES_BY_WORLD.get(world.getRegistryKey());
      if (queues != null && !queues.isEmpty()) {
         if (queues.containsKey(actorUuid)) {
            return true;
         }

         for (ServerAnimationController.PlayerQueueState queue : queues.values()) {
            for (ServerAnimationController.QueuedEntry entry : queue.entries) {
               if (entry.actorUuids.contains(actorUuid)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static void tickNoAiCleanup(ServerWorld world) {
      RegistryKey<World> key = world.getRegistryKey();
      boolean startupWindowCleanup = isWithinStartupNoAiCleanupWindow(world);
      if (startupWindowCleanup || !world.getPlayers().isEmpty()) {
         if (!startupWindowCleanup) {
            int ticksLeft = NOAI_CLEANUP_TICKS_BY_WORLD.getOrDefault(key, 0) - 1;
            if (ticksLeft > 0) {
               NOAI_CLEANUP_TICKS_BY_WORLD.put(key, ticksLeft);
               return;
            }

            NOAI_CLEANUP_TICKS_BY_WORLD.put(key, 6000);
         }

         Map<UUID, ServerAnimationController.AiDisableState> states = AI_DISABLE_STATE_BY_WORLD.get(key);

         for (Entity entity : world.iterateEntities()) {
            if (entity instanceof MobEntity mob && (mob.getCommandTags().contains("afw_noai") || mob.getCommandTags().contains("afw_noai_orig"))) {
               UUID uuid = mob.getUuid();
               if (startupWindowCleanup || !isActorActive(world, uuid)) {
                  ServerAnimationController.AiDisableState state = states == null ? null : states.get(uuid);
                  if (state != null) {
                     state.locks = 0;
                     state.needsRestore = true;
                     tryRestoreAi(world, uuid, state, states);
                  } else {
                     boolean originalDisabled = mob.getCommandTags().contains("afw_noai_orig");
                     mob.setAiDisabled(originalDisabled);
                     clearNoAiTags(mob);
                  }
               }
            }
         }

         if (states != null && states.isEmpty()) {
            AI_DISABLE_STATE_BY_WORLD.remove(key);
         }
      }
   }

   private static boolean isWithinStartupNoAiCleanupWindow(ServerWorld world) {
      if (world != null && world.getServer() != null) {
         int now = world.getServer().getTicks();
         return now <= STARTUP_NOAI_CLEANUP_UNTIL_TICK;
      } else {
         return false;
      }
   }

   private static boolean isWithinStartupStartBlockWindow(ServerWorld world) {
      if (world != null && world.getServer() != null) {
         int now = world.getServer().getTicks();
         return now <= STARTUP_START_BLOCK_UNTIL_TICK;
      } else {
         return false;
      }
   }

   private static List<String> sanitizeActorKeys(List<String> actorKeys, int actorCount) {
      if (actorKeys != null && !actorKeys.isEmpty()) {
         return actorKeys.size() != actorCount ? List.of() : List.copyOf(actorKeys);
      } else {
         return List.of();
      }
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

   private static AnimationStartContext sanitizeStartContext(@Nullable AnimationStartContext context) {
      return context == null
         ? AnimationStartContext.empty()
         : new AnimationStartContext(sanitizeMetadata(context.addonMetadata()), context.sessionData(), context.joinPredecessor(), context.queueContinuation());
   }

   private static void registerActiveInstance(UUID instanceId, ServerAnimationController.ActiveInstance instance) {
      ACTIVE_INSTANCES.put(instanceId, instance);
      Map<UUID, UUID> actorIndex = ACTIVE_INSTANCE_BY_ACTOR.computeIfAbsent(instance.world.getRegistryKey(), ignored -> new ConcurrentHashMap<>());

      for (UUID actorUuid : instance.actorUuidSet) {
         actorIndex.put(actorUuid, instanceId);
      }
   }

   @Nullable
   private static ServerAnimationController.ActiveInstance removeActiveInstance(UUID instanceId) {
      ServerAnimationController.ActiveInstance removed = ACTIVE_INSTANCES.remove(instanceId);
      if (removed == null) {
         return null;
      }

      Map<UUID, UUID> actorIndex = ACTIVE_INSTANCE_BY_ACTOR.get(removed.world.getRegistryKey());
      if (actorIndex != null) {
         for (UUID actorUuid : removed.actorUuidSet) {
            actorIndex.remove(actorUuid, instanceId);
         }

         if (actorIndex.isEmpty()) {
            ACTIVE_INSTANCE_BY_ACTOR.remove(removed.world.getRegistryKey(), actorIndex);
         }
      }

      return removed;
   }

   @Nullable
   public static UUID findActiveInstanceId(ServerWorld world, UUID actorUuid) {
      if (world != null && actorUuid != null) {
         Map<UUID, UUID> actorIndex = ACTIVE_INSTANCE_BY_ACTOR.get(world.getRegistryKey());
         if (actorIndex == null) {
            return null;
         }

         UUID instanceId = actorIndex.get(actorUuid);
         if (instanceId == null) {
            return null;
         }

         ServerAnimationController.ActiveInstance instance = ACTIVE_INSTANCES.get(instanceId);
         if (instance != null && instance.world == world && instance.actorUuidSet.contains(actorUuid)) {
            return instanceId;
         }

         actorIndex.remove(actorUuid, instanceId);
         return null;
      } else {
         return null;
      }
   }

   public static boolean isActorActive(ServerWorld world, UUID actorUuid) {
      return findActiveInstanceId(world, actorUuid) != null;
   }

   private static void clearNoAiTags(MobEntity mob) {
      mob.removeCommandTag("afw_noai");
      mob.removeCommandTag("afw_noai_orig");
   }

   private static void restoreAiState(MobEntity mob, ServerAnimationController.AiDisableState state) {
      mob.setVelocity(Vec3d.ZERO);
      mob.fallDistance = 0.0;
      mob.setAiDisabled(state.originalAiDisabled);
      clearNoAiTags(mob);
   }

   private static void acquirePlayerLocks(ServerWorld world, List<UUID> actorUuids) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         Map<UUID, ServerAnimationController.PlayerLockState> states = PLAYER_LOCK_STATE_BY_WORLD.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>());

         for (UUID uuid : actorUuids) {
            if (world.getEntity(uuid) instanceof ServerPlayerEntity player) {
               ServerAnimationController.PlayerLockState existing = states.get(uuid);
               if (existing == null) {
                  states.put(uuid, new ServerAnimationController.PlayerLockState(player.hasNoGravity()));
               } else {
                  existing.locks++;
               }

               player.setNoGravity(true);
               player.setVelocity(Vec3d.ZERO);
               player.fallDistance = 0.0;
            }
         }
      }
   }

   private static void releasePlayerLocks(ServerWorld world, List<UUID> actorUuids) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         Map<UUID, ServerAnimationController.PlayerLockState> states = PLAYER_LOCK_STATE_BY_WORLD.get(world.getRegistryKey());
         if (states != null && !states.isEmpty()) {
            for (UUID uuid : actorUuids) {
               ServerAnimationController.PlayerLockState state = states.get(uuid);
               if (state != null) {
                  if (state.locks > 1) {
                     state.locks--;
                  } else {
                     ServerPlayerEntity player = Objects.requireNonNull(world.getServer()).getPlayerManager().getPlayer(uuid);
                     if (player != null) {
                        player.setNoGravity(state.originalNoGravity);
                        player.fallDistance = 0.0;
                     }

                     states.remove(uuid);
                  }
               }
            }

            if (states.isEmpty()) {
               PLAYER_LOCK_STATE_BY_WORLD.remove(world.getRegistryKey());
            }
         }
      }
   }

   private static void clearAllPlayerLocksInWorld(ServerWorld world) {
      if (world != null) {
         Map<UUID, ServerAnimationController.PlayerLockState> states = PLAYER_LOCK_STATE_BY_WORLD.remove(world.getRegistryKey());
         if (states != null && !states.isEmpty()) {
            for (Entry<UUID, ServerAnimationController.PlayerLockState> entry : states.entrySet()) {
               ServerPlayerEntity player = Objects.requireNonNull(world.getServer()).getPlayerManager().getPlayer(entry.getKey());
               if (player != null) {
                  player.setNoGravity(entry.getValue().originalNoGravity);
                  player.fallDistance = 0.0;
               }
            }
         }
      }
   }

   private static void cleanupStalePlayerLocks(ServerWorld world, Set<UUID> currentlyLockedPlayers) {
      if (world != null) {
         Set<UUID> lockedNow = currentlyLockedPlayers == null ? Set.of() : currentlyLockedPlayers;
         Map<UUID, ServerAnimationController.PlayerLockState> states = PLAYER_LOCK_STATE_BY_WORLD.get(world.getRegistryKey());
         if (states != null && !states.isEmpty()) {
            Iterator<Entry<UUID, ServerAnimationController.PlayerLockState>> it = states.entrySet().iterator();

            while (it.hasNext()) {
               Entry<UUID, ServerAnimationController.PlayerLockState> entry = it.next();
               UUID uuid = entry.getKey();
               if (!lockedNow.contains(uuid) && !isActorActive(world, uuid)) {
                  ServerPlayerEntity player = Objects.requireNonNull(world.getServer()).getPlayerManager().getPlayer(uuid);
                  if (player != null) {
                     player.setNoGravity(entry.getValue().originalNoGravity);
                     player.fallDistance = 0.0;
                  }

                  it.remove();
               }
            }

            if (states.isEmpty()) {
               PLAYER_LOCK_STATE_BY_WORLD.remove(world.getRegistryKey());
            }
         }

         for (ServerPlayerEntity player : world.getPlayers()) {
            UUID uuid = player.getUuid();
            if (!lockedNow.contains(uuid) && !isActorActive(world, uuid) && player.hasNoGravity() && !player.isSpectator() && !player.getAbilities().flying) {
               player.setNoGravity(false);
               player.fallDistance = 0.0;
            }
         }
      }
   }

   private static void lockActivePlayers(ServerWorld world, Map<UUID, ServerAnimationController.SharedTransform> playersToLock, Set<UUID> underwaterPlayers) {
      if (!playersToLock.isEmpty()) {
         for (Entry<UUID, ServerAnimationController.SharedTransform> entry : playersToLock.entrySet()) {
            if (world.getEntity(entry.getKey()) instanceof ServerPlayerEntity player) {
               preparePlayerForAnimationTeleport(player);
               ServerAnimationController.SharedTransform sharedTransform = entry.getValue();
               Vec3d targetPos = sharedTransform.pos();
               double dx = player.getX() - targetPos.x;
               double dy = player.getY() - targetPos.y;
               double dz = player.getZ() - targetPos.z;
               if (dx * dx + dy * dy + dz * dz > 4.0E-4) {
                  player.setPosition(targetPos.x, targetPos.y, targetPos.z);
                  player.networkHandler.requestTeleport(targetPos.x, targetPos.y, targetPos.z, player.getYaw(), player.getPitch());
               }

               player.setNoGravity(true);
               if (underwaterPlayers.contains(player.getUuid())) {
                  player.setAir(player.getMaxAir());
               }

               player.setVelocity(Vec3d.ZERO);
               player.fallDistance = 0.0;
            }
         }
      }
   }

   private static void nudgeNonActiveMobsAwayFromAnimatedPlayers(ServerWorld world, Set<UUID> activePlayerUuids, long nowTick) {
      if (world != null && activePlayerUuids != null && !activePlayerUuids.isEmpty()) {
         if (nowTick % 4L == 0L) {
            for (UUID playerUuid : activePlayerUuids) {
               ServerPlayerEntity player = Objects.requireNonNull(world.getServer()).getPlayerManager().getPlayer(playerUuid);
               if (player != null && player.getEntityWorld() == world && player.isAlive() && !player.isRemoved()) {
                  Box searchBox = player.getBoundingBox().expand(1.0, 0.5, 1.0);
                  List<Entity> nearby = world.getOtherEntities(
                     player,
                     searchBox,
                     entityx -> entityx instanceof MobEntity mobx && mobx.isAlive() && !mobx.isRemoved() && !isActorPendingOrActive(world, mobx.getUuid())
                  );
                  if (!nearby.isEmpty()) {
                     int nudged = 0;

                     for (Entity entity : nearby) {
                        if (entity instanceof MobEntity mob && !isActorPendingOrActive(world, mob.getUuid())) {
                           if (nudged >= 6) {
                              break;
                           }

                           if (nudgeMobAwayFromPlayer(player, mob)) {
                              nudged++;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean nudgeMobAwayFromPlayer(ServerPlayerEntity player, MobEntity mob) {
      double dx = mob.getX() - player.getX();
      double dz = mob.getZ() - player.getZ();
      double horizontalDistSq = dx * dx + dz * dz;
      double minDistSq = 0.7224999999999999;
      if (horizontalDistSq >= minDistSq) {
         return false;
      }

      Vec3d dir = resolveHorizontalNudgeDirection(mob, dx, dz, horizontalDistSq);
      double dist = Math.sqrt(Math.max(horizontalDistSq, 1.0E-6));
      double overlap = Math.max(0.0, 0.85 - dist);
      double strength = 0.08 * (0.6 + overlap / 0.85);
      mob.addVelocity(dir.x * strength, 0.0, dir.z * strength);
      mob.fallDistance = 0.0;
      return true;
   }

   private static Vec3d resolveHorizontalNudgeDirection(MobEntity mob, double dx, double dz, double horizontalDistSq) {
      if (horizontalDistSq > 1.0E-6) {
         double invLen = 1.0 / Math.sqrt(horizontalDistSq);
         return new Vec3d(dx * invLen, 0.0, dz * invLen);
      } else {
         long bits = mob.getUuid().getLeastSignificantBits() ^ mob.getUuid().getMostSignificantBits();
         double angle = (bits & 65535L) * 9.587379924285257E-5;
         return new Vec3d(Math.cos(angle), 0.0, Math.sin(angle));
      }
   }

   private static void clearAttackTargets(ServerWorld world, Set<UUID> actorUuids) {
      if (!actorUuids.isEmpty()) {
         for (Entity entity : world.iterateEntities()) {
            if (entity instanceof MobEntity mob) {
               LivingEntity target = mob.getTarget();
               if (target != null && actorUuids.contains(target.getUuid())) {
                  mob.setTarget(null);
                  mob.getNavigation().stop();
               }
            }
         }
      }
   }

   private static double clampSpeed(double speed) {
      return !Double.isNaN(speed) && !Double.isInfinite(speed) && !(speed <= 0.0) ? Math.max(0.1, Math.min(speed, 4.0)) : 1.0;
   }

   private static double resolveStageSpeed(List<AnimationStageInfo> stages, int index) {
      if (stages != null && !stages.isEmpty()) {
         int clamped = Math.max(0, Math.min(index, stages.size() - 1));
         double speed = stages.get(clamped).speed();
         return !Double.isNaN(speed) && !Double.isInfinite(speed) && !(speed <= 0.0) ? speed : 1.0;
      } else {
         return 1.0;
      }
   }

   private static double sanitizeMultiplier(double multiplier) {
      return !Double.isNaN(multiplier) && !Double.isInfinite(multiplier) && !(multiplier <= 0.0) ? Math.max(0.1, Math.min(multiplier, 4.0)) : 1.0;
   }

   private static boolean maybeAdvanceStages(
      ServerWorld world,
      UUID instanceId,
      ServerAnimationController.ActiveInstance inst,
      long now,
      List<ServerAnimationController.StageAdvance> stageAdvances,
      List<UUID> toRemove
   ) {
      List<AnimationStageInfo> stages = inst.stages;
      ServerAnimationController.StageTracker tracker = inst.stageTracker;
      if (stages != null && !stages.isEmpty() && tracker != null) {
         int idx = Math.max(0, Math.min(tracker.stageIndex, stages.size() - 1));
         long stageStart = tracker.stageStartTick;
         double speed = clampSpeed(inst.speed);

         while (true) {
            AnimationStageInfo current = stages.get(idx);
            long duration = AnimationStageTiming.resolveDurationTicks(inst.animationId, current, speed);
            if (duration < 0L) {
               return false;
            }

            long targetEnd = stageStart + duration;
            if (now < targetEnd) {
               return false;
            }

            if (idx + 1 >= stages.size()) {
               long stopTick = now + 1L;
               boolean chained = false;
               if (inst.playerUuid != null) {
                  chained = startNextQueuedForPlayerDeferringRestore(world, inst, instanceId);
               }

               StopAnimationS2CPayload payload = new StopAnimationS2CPayload(instanceId, stopTick);

               for (ServerPlayerEntity p : computeInstanceBroadcastTargets(world, null, inst)) {
                  ServerPlayNetworking.send(p, payload);
               }

               fireStopEvent(instanceId, inst);
               restoreTransforms(inst, chained && inst.playerUuid != null ? Set.of(inst.playerUuid) : null);
               restoreAiForActors(world, inst.actorUuids);
               releasePlayerLocks(world, inst.actorUuids);
               toRemove.add(instanceId);
               if (chained) {
                  sendOrLogDebug(world, null, NonDebugChatCategory.INFO, dbg("queue_continued_for_player"), false);
               }

               return true;
            }

            idx++;
            stageStart = targetEnd;
            tracker.stageIndex = idx;
            tracker.stageStartTick = stageStart;
            tracker.progressEndTickOverride = -1L;
            inst.speed = clampSpeed(resolveStageSpeed(stages, idx));
            speed = clampSpeed(inst.speed);
            long nextStageEnd = resolveStageEndTick(inst.animationId, stages.get(idx), stageStart, speed);
            stageAdvances.add(new ServerAnimationController.StageAdvance(instanceId, stageStart, nextStageEnd, idx));
         }
      } else {
         return false;
      }
   }

   private static long resolveStageEndTick(ServerAnimationController.ActiveInstance instance) {
      return instance != null && instance.stageTracker != null
         ? resolveStageEndTick(instance.animationId, resolveCurrentStage(instance), instance.stageTracker.stageStartTick, instance.speed)
         : -1L;
   }

   private static long resolvePublishedStageEndTick(ServerAnimationController.ActiveInstance instance) {
      if (instance != null && instance.stageTracker != null) {
         long override = instance.stageTracker.progressEndTickOverride;
         return override > instance.stageTracker.stageStartTick ? override : resolveStageEndTick(instance);
      } else {
         return -1L;
      }
   }

   private static long resolveStageEndTick(Identifier animationId, AnimationStageInfo stage, long stageStartTick, double speed) {
      long duration = AnimationStageTiming.resolveDurationTicks(animationId, stage, speed);
      return duration < 0L ? -1L : stageStartTick + duration;
   }

   private static AnimationStageInfo resolveCurrentStage(ServerAnimationController.ActiveInstance inst) {
      if (inst.stages != null && !inst.stages.isEmpty()) {
         int idx = inst.stageTracker == null ? 0 : Math.max(0, Math.min(inst.stageTracker.stageIndex, inst.stages.size() - 1));
         return inst.stages.get(idx);
      } else {
         return new AnimationStageInfo(inst.animationId, true, 0L, true, 1.0);
      }
   }

   @Nullable
   public static AnimationStageInfo getCurrentStage(ServerWorld world, UUID instanceId) {
      ServerAnimationController.ActiveInstance inst = ACTIVE_INSTANCES.get(instanceId);
      return inst != null && inst.world == world ? resolveCurrentStage(inst) : null;
   }

   @Nullable
   public static ServerAnimationController.ActiveInstanceSnapshot getActiveInstanceSnapshot(ServerWorld world, UUID instanceId) {
      ServerAnimationController.ActiveInstance inst = ACTIVE_INSTANCES.get(instanceId);
      return inst != null && inst.world == world ? snapshot(instanceId, inst) : null;
   }

   public static List<ServerAnimationController.ActiveInstanceSnapshot> getActiveInstanceSnapshots(ServerWorld world) {
      if (world != null && !ACTIVE_INSTANCES.isEmpty()) {
         List<ServerAnimationController.ActiveInstanceSnapshot> snapshots = new ArrayList<>();

         for (Entry<UUID, ServerAnimationController.ActiveInstance> entry : ACTIVE_INSTANCES.entrySet()) {
            if (entry.getValue().world == world) {
               snapshots.add(snapshot(entry.getKey(), entry.getValue()));
            }
         }

         return List.copyOf(snapshots);
      } else {
         return List.of();
      }
   }

   private static ServerAnimationController.ActiveInstanceSnapshot snapshot(UUID instanceId, ServerAnimationController.ActiveInstance instance) {
      int stageIndex = instance.stageTracker == null ? 0 : instance.stageTracker.stageIndex;
      long stageStartTick = instance.stageTracker == null ? instance.startTick : instance.stageTracker.stageStartTick;
      return new ServerAnimationController.ActiveInstanceSnapshot(
         instanceId,
         instance.animationId,
         List.copyOf(instance.actorUuids),
         sanitizeActorKeys(instance.actorKeys, instance.actorUuids.size()),
         List.copyOf(instance.stages),
         instance.startTick,
         stageIndex,
         stageStartTick,
         resolvePublishedStageEndTick(instance),
         instance.speed,
         instance.damageBehavior,
         instance.ignoreAttackers,
         instance.startContext
      );
   }

   @Nullable
   private static Float resolveTransitionYaw(@Nullable ServerAnimationController.ActiveInstance inst) {
      return inst != null && inst.sharedTransform != null ? inst.sharedTransform.yaw() : null;
   }

   private static final class ActiveInstance {
      final ServerWorld world;
      final Identifier animationId;
      final List<List<Identifier>> modelRootsByActor;
      final List<UUID> actorUuids;
      final List<String> actorKeys;
      final List<AnimationStageInfo> stages;
      final long startTick;
      final Map<UUID, ServerAnimationController.OriginalTransform> originalTransforms;
      final ServerAnimationController.SharedTransform sharedTransform;
      final boolean lockOrientation;
      final float lockedYaw;
      final float lockedHeadYaw;
      final float lockedPitch;
      final NonAnimationDamageBehavior damageBehavior;
      final boolean ignoreAttackers;
      final AnimationStartContext startContext;
      final boolean underwaterBreathing;
      @Nullable
      final UUID playerUuid;
      final Set<UUID> actorUuidSet;
      final ServerAnimationController.StageTracker stageTracker;
      @Nullable
      final ServerAnimationController.BlockPlacement blockPlacement;
      @Nullable
      final Vec3d cameraOrbitTarget;
      final Set<UUID> subscribedPlayerUuids = ConcurrentHashMap.newKeySet();
      double speed;

      ActiveInstance(
         ServerWorld world,
         Identifier animationId,
         List<List<Identifier>> modelRootsByActor,
         List<UUID> actorUuids,
         List<String> actorKeys,
         List<AnimationStageInfo> stages,
         long startTick,
         Map<UUID, ServerAnimationController.OriginalTransform> originalTransforms,
         ServerAnimationController.SharedTransform sharedTransform,
         boolean lockOrientation,
         float lockedYaw,
         float lockedHeadYaw,
         float lockedPitch,
         NonAnimationDamageBehavior damageBehavior,
         boolean ignoreAttackers,
         AnimationStartContext startContext,
         boolean underwaterBreathing,
         @Nullable UUID playerUuid,
         Set<UUID> actorUuidSet,
         ServerAnimationController.StageTracker stageTracker,
         @Nullable ServerAnimationController.BlockPlacement blockPlacement,
         @Nullable Vec3d cameraOrbitTarget,
         double speed
      ) {
         this.world = world;
         this.animationId = animationId;
         this.modelRootsByActor = modelRootsByActor.stream().map(List::copyOf).toList();
         this.actorUuids = actorUuids;
         this.actorKeys = actorKeys;
         this.stages = stages;
         this.startTick = startTick;
         this.originalTransforms = originalTransforms;
         this.sharedTransform = sharedTransform;
         this.lockOrientation = lockOrientation;
         this.lockedYaw = lockedYaw;
         this.lockedHeadYaw = lockedHeadYaw;
         this.lockedPitch = lockedPitch;
         this.damageBehavior = damageBehavior;
         this.ignoreAttackers = ignoreAttackers;
         this.startContext = startContext;
         this.underwaterBreathing = underwaterBreathing;
         this.playerUuid = playerUuid;
         this.actorUuidSet = actorUuidSet;
         this.stageTracker = stageTracker;
         this.blockPlacement = blockPlacement;
         this.cameraOrbitTarget = cameraOrbitTarget;
         this.speed = speed;
      }
   }

   public record ActiveInstanceSnapshot(
      UUID instanceId,
      Identifier animationId,
      List<UUID> actorUuids,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      long startTick,
      int stageIndex,
      long stageStartTick,
      long stageEndTick,
      double speed,
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      AnimationStartContext startContext
   ) {
      public Map<String, String> metadata() {
         return this.startContext.addonMetadata();
      }

      @Nullable
      public AnimationSessionData sessionData() {
         return this.startContext.sessionData();
      }
   }

   private static final class AiDisableState {
      final boolean originalAiDisabled;
      int locks;
      boolean needsRestore;
      float lockedYaw;
      float lockedHeadYaw;
      float lockedBodyYaw;
      float lockedPitch;

      AiDisableState(boolean originalAiDisabled) {
         this.originalAiDisabled = originalAiDisabled;
         this.locks = 1;
         this.needsRestore = false;
         this.lockedYaw = 0.0F;
         this.lockedHeadYaw = 0.0F;
         this.lockedBodyYaw = 0.0F;
         this.lockedPitch = 0.0F;
      }
   }

   private record AnimationStartProtection(RegistryKey<World> worldKey, long untilTick) {
   }

   private record BedFootprintFacing(boolean bedFootprint, @Nullable Direction facing) {
      static ServerAnimationController.BedFootprintFacing none() {
         return new ServerAnimationController.BedFootprintFacing(false, null);
      }

      static ServerAnimationController.BedFootprintFacing invalid() {
         return new ServerAnimationController.BedFootprintFacing(true, null);
      }

      static ServerAnimationController.BedFootprintFacing valid(Direction facing) {
         return new ServerAnimationController.BedFootprintFacing(true, facing);
      }
   }

   private record BlockPlacement(
      BlockPos wallPos, Direction facing, ServerAnimationController.SharedTransform sharedTransform, @Nullable Vec3d cameraOrbitTarget, boolean centerSupport
   ) {
   }

   private record DeferredQueueContext(
      ServerAnimationController.OriginalTransform originalTransform, @Nullable ServerAnimationController.BlockPlacement previousPlacement
   ) {
   }

   private record EligibleDefinition(
      AnimationDefinitions.Definition definition, List<String> actorKeys, @Nullable ServerAnimationController.BlockPlacement blockPlacement
   ) {
   }

   private record FloorHit(boolean found, Vec3d pos) {
   }

   private record GroundPlacementOffset(double dx, double dz, double distSq) {
   }

   public record MatchedStartRequest(@Nullable UUID instanceId, Identifier animationId, List<String> actorKeys, List<AnimationStageInfo> stages) {
   }

   private record OriginalTransform(RegistryKey<World> worldKey, Vec3d pos, float yaw, float pitch, float headYaw, float bodyYaw, @Nullable Vec3d restorePos) {
      private OriginalTransform(RegistryKey<World> worldKey, Vec3d pos, float yaw, float pitch, float headYaw, float bodyYaw) {
         this(worldKey, pos, yaw, pitch, headYaw, bodyYaw, null);
      }

      private Vec3d restorePosOrPos() {
         return this.restorePos == null ? this.pos : this.restorePos;
      }
   }

   private static final class PlayerLockState {
      final boolean originalNoGravity;
      int locks;

      PlayerLockState(boolean originalNoGravity) {
         this.originalNoGravity = originalNoGravity;
         this.locks = 1;
      }
   }

   private static final class PlayerQueueState {
      final UUID playerUuid;
      final Deque<ServerAnimationController.QueuedEntry> entries = new ArrayDeque<>();

      PlayerQueueState(UUID playerUuid) {
         this.playerUuid = playerUuid;
      }
   }

   private enum QueueStartResult {
      STARTED,
      RETRY,
      SKIP;
   }

   private record QueuedEntry(
      UUID entryId,
      Identifier animationId,
      List<UUID> actorUuids,
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      AnimationStartContext startContext
   ) {
   }

   private record RecentNoRestoreTransition(
      long expiresTick, ServerAnimationController.SharedTransform sharedTransform, @Nullable ServerAnimationController.BlockPlacement blockPlacement
   ) {
   }

   private record RequirementCheck(boolean valid, @Nullable ServerAnimationController.BlockPlacement blockPlacement) {
   }

   private record SharedTransform(Vec3d pos, float yaw, float pitch, float headYaw, float bodyYaw) {
   }

   private record StageAdvance(UUID instanceId, long advanceTick, long stageEndTick, int stageIndex) {
   }

   private static final class StageTracker {
      int stageIndex = 0;
      long stageStartTick;
      long progressEndTickOverride;

      StageTracker(long stageStartTick) {
         this.stageStartTick = stageStartTick;
         this.progressEndTickOverride = -1L;
      }
   }

   private enum StopReason {
      NATURAL_END,
      EXTERNAL_STOP,
      DAMAGE_STOP,
      JOIN_REPLACE,
      ACTOR_DEATH,
      STOP_ALL;
   }

   private record WallColumn(BlockPos pos, Vec3d surfaceAnchor) {
   }

   private record WallSegment(List<ServerAnimationController.WallColumn> columns, Vec3d surfaceCenter) {
   }
}
