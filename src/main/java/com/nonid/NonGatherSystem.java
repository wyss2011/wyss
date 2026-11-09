package com.nonid;

import com.nonid.api.animation.NonAnimationDamageBehavior;
import com.nonid.internal.animation.api.AnimationService;
import com.nonid.internal.animation.data.AnimationDefinitions;
import com.nonid.internal.animation.network.AnimationStageInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class NonGatherSystem {
   private static final double MOVE_SPEED = 1.15;
   private static final int REPATH_EVERY_TICKS = 5;
   private static final int TIMEOUT_TICKS = 3600;
   private static final int NO_LOS_TIMEOUT_TICKS = 100;
   private static final int TOO_FAR_TIMEOUT_TICKS = 200;
   private static final double TIMEOUT_MAX_DIST_SQ = 100.0;
   private static final double GATHER_EXTRA_DIST = 0.25;
   private static final double GATHER_TARGET_OVERSHOOT = 1.0;
   private static final double START_MAX_VERTICAL_DELTA = 2.5;
   private static final float LOOK_YAW_SPEED = 30.0F;
   private static final float LOOK_PITCH_SPEED = 30.0F;
   private static final Map<UUID, NonGatherSystem.Session> SESSIONS_BY_INITIATOR = new ConcurrentHashMap<>();

   private NonGatherSystem() {
   }

   static void clearRuntimeState() {
      SESSIONS_BY_INITIATOR.clear();
   }

   @Nullable
   public static NonGatherSystem.GatherCandidate findGatherCandidate(
      ServerWorld world, List<Entity> actors, boolean isAttack, Entity attacker, @Nullable Entity gatherAnchor
   ) {
      if (world != null && actors != null && !actors.isEmpty() && attacker != null) {
         List<Entity> sortedActors = new ArrayList<>(actors);
         sortedActors.sort(Comparator.comparingInt(Entity::getId));
         AnimationService.MatchedAnimation matched = isAttack
            ? NonAnimationMatcher.findAutomaticAttack(world, sortedActors, Set.of(), gatherAnchor, true, attacker, Set.of())
            : NonAnimationMatcher.findAutomatic(world, sortedActors, Set.of(), gatherAnchor, true, null, Set.of());
         return toGatherCandidate(matched);
      } else {
         return null;
      }
   }

   @Nullable
   public static NonGatherSystem.GatherCandidate findUnprotectedGatherCandidate(
      ServerWorld world, List<Entity> actors, boolean isAttack, Entity attacker, @Nullable Entity gatherAnchor
   ) {
      NonGatherSystem.GatherCandidate candidate = findGatherCandidate(world, actors, isAttack, attacker, gatherAnchor);
      if (candidate == null) {
         return null;
      }

      AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(candidate.animationId());
      if (definition == null) {
         return null;
      }

      List<Entity> sortedActors = new ArrayList<>(actors);
      sortedActors.sort(Comparator.comparingInt(Entity::getId));
      return !NeedsOfNature.isProtectedByAccessory(world, definition, sortedActors)
         ? candidate
         : findUnprotectedFallbackCandidate(world, sortedActors, isAttack, attacker, gatherAnchor, true, candidate.animationId(), null);
   }

   @Nullable
   static NonGatherSystem.GatherCandidate findUnprotectedFallbackCandidate(
      ServerWorld world,
      List<Entity> actors,
      boolean isAttack,
      Entity attacker,
      @Nullable Entity gatherAnchor,
      boolean requireStartEligibility,
      Identifier blockedAnimationId,
      @Nullable Identifier joinPredecessorAnimationId
   ) {
      if (blockedAnimationId == null) {
         return null;
      }

      List<Entity> sortedActors = new ArrayList<>(actors);
      sortedActors.sort(Comparator.comparingInt(Entity::getId));
      Set<Identifier> excluded = new HashSet<>();
      excluded.add(blockedAnimationId);

      while (true) {
         AnimationService.MatchedAnimation matched = isAttack
            ? NonAnimationMatcher.findAutomaticAttack(
               world, sortedActors, Set.of(), gatherAnchor, requireStartEligibility, attacker, excluded, null, joinPredecessorAnimationId
            )
            : NonAnimationMatcher.findAutomatic(
               world, sortedActors, Set.of(), gatherAnchor, requireStartEligibility, null, excluded, null, joinPredecessorAnimationId
            );
         NonGatherSystem.GatherCandidate candidate = toGatherCandidate(matched);
         if (candidate == null) {
            return null;
         }

         AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(candidate.animationId());
         if (definition == null) {
            return null;
         }

         if (!NeedsOfNature.isProtectedByAccessory(world, definition, sortedActors)) {
            return candidate;
         }

         excluded.add(candidate.animationId());
      }
   }

   @Nullable
   private static NonGatherSystem.GatherCandidate toGatherCandidate(@Nullable AnimationService.MatchedAnimation matched) {
      return matched == null ? null : new NonGatherSystem.GatherCandidate(matched.animationId(), matched.actorKeys(), matched.stages());
   }

   public static boolean startMobPlayerGather(
      ServerWorld world, MobEntity mob, ServerPlayerEntity player, Identifier animationId, List<String> actorKeys, List<AnimationStageInfo> stages
   ) {
      if (world != null && mob != null && player != null && animationId != null) {
         if (mob.getEntityWorld() != world || player.getEntityWorld() != world) {
            return false;
         } else if (!NonMobGatherEligibility.canInitiateAutoGather(mob)) {
            return false;
         } else if (isAutomaticMobPlayerStartBlockedByMount(player)) {
            reportAutomaticMobPlayerMountBlock(world, mob, player, animationId, "gather_start");
            return false;
         } else {
            NonGatherSystem.Session session = new NonGatherSystem.Session(
               NonGatherSystem.SessionType.MOB_PLAYER,
               mob.getUuid(),
               player.getUuid(),
               animationId,
               List.copyOf(actorKeys),
               stages == null ? List.of() : List.copyOf(stages),
               true,
               null,
               world.getRegistryKey(),
               world.getTime(),
               0,
               0,
               0
            );
            SESSIONS_BY_INITIATOR.put(mob.getUuid(), session);
            NeedsOfNature.trackNoNGatherPair(world, List.of(mob, player));
            return true;
         }
      } else {
         return false;
      }
   }

   public static boolean isAutomaticMobPlayerStartBlockedByMount(ServerPlayerEntity player) {
      if (player != null && player.hasVehicle()) {
         Entity vehicle = player.getVehicle();
         if (vehicle != null && !vehicle.isRemoved()) {
            NonConfig config = NeedsOfNature.getConfig();
            if (config == null) {
               return false;
            } else {
               return vehicle instanceof LivingEntity ? config.blockAnimationsWhileRidingLivingEntities() : config.blockAnimationsWhileRidingVehicles();
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void reportAutomaticMobPlayerMountBlock(
      ServerWorld world, MobEntity mob, ServerPlayerEntity player, @Nullable Identifier animationId, String phase
   ) {
      if (world != null && mob != null && player != null) {
         Entity vehicle = player.getVehicle();
         Identifier mobType = Registries.ENTITY_TYPE.getId(mob.getType());
         Identifier vehicleType = vehicle == null ? null : Registries.ENTITY_TYPE.getId(vehicle.getType());
         NeedsOfNature.logRuntime(
            "NoN automatic mob-player start blocked by mounted-player protection: phase={} mobType={} player={} vehicle={} anim={}",
            phase,
            mobType,
            player.getName().getString(),
            vehicleType,
            animationId
         );
         Text vehicleName = (Text)(vehicle == null ? Text.literal("载具") : vehicle.getDisplayName());
         NeedsOfNature.sendDebugChat(
            player, NonDebugChatCategory.WARNING, Text.translatable("debug.needsofnature.mounted_animation_blocked", new Object[]{vehicleName})
         );
      }
   }

   public static void startMobMobGather(
      ServerWorld world,
      MobEntity initiator,
      MobEntity partner,
      Identifier animationId,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      boolean attackMode
   ) {
      if (world != null && initiator != null && partner != null && animationId != null) {
         if (initiator.getEntityWorld() == world && partner.getEntityWorld() == world) {
            if (NonMobGatherEligibility.canInitiateAutoGather(initiator) && NonMobGatherEligibility.canBeAutoGatherTarget(partner)) {
               NonGatherSystem.Session session = new NonGatherSystem.Session(
                  NonGatherSystem.SessionType.MOB_MOB,
                  initiator.getUuid(),
                  partner.getUuid(),
                  animationId,
                  List.copyOf(actorKeys),
                  stages == null ? List.of() : List.copyOf(stages),
                  attackMode,
                  null,
                  world.getRegistryKey(),
                  world.getTime(),
                  0,
                  0,
                  0
               );
               SESSIONS_BY_INITIATOR.put(initiator.getUuid(), session);
               NeedsOfNature.trackNoNGatherPair(world, List.of(initiator, partner));
            }
         }
      }
   }

   public static void startJoinGather(ServerWorld world, MobEntity joiningMob, ServerPlayerEntity requester, UUID activeInstanceId, Identifier animationId) {
      if (world != null && joiningMob != null && requester != null && activeInstanceId != null) {
         if (joiningMob.getEntityWorld() == world && requester.getEntityWorld() == world) {
            if (NonMobGatherEligibility.canInitiateAutoGather(joiningMob)) {
               NonGatherSystem.Session session = new NonGatherSystem.Session(
                  NonGatherSystem.SessionType.MOB_JOIN_ACTIVE_ANIMATION,
                  joiningMob.getUuid(),
                  requester.getUuid(),
                  animationId,
                  List.of(),
                  List.of(),
                  false,
                  activeInstanceId,
                  world.getRegistryKey(),
                  world.getTime(),
                  0,
                  0,
                  0
               );
               SESSIONS_BY_INITIATOR.put(joiningMob.getUuid(), session);
               NeedsOfNature.trackNoNGatherPair(world, List.of(joiningMob, requester));
            }
         }
      }
   }

   public static void tickServerWorld(ServerWorld world) {
      if (world != null && !SESSIONS_BY_INITIATOR.isEmpty()) {
         for (NonGatherSystem.Session session : List.copyOf(SESSIONS_BY_INITIATOR.values())) {
            if (session != null && world.getRegistryKey().equals(session.worldKey())) {
               if (!(world.getEntity(session.initiatorUuid()) instanceof MobEntity mob && mob.getEntityWorld() == world)) {
                  remove(session, world, false, null, "initiator_missing");
               } else if (session.type() == NonGatherSystem.SessionType.MOB_PLAYER) {
                  tickMobPlayer(world, session, mob);
               } else if (session.type() == NonGatherSystem.SessionType.MOB_MOB) {
                  tickMobMob(world, session, mob);
               } else {
                  tickJoin(world, session, mob);
               }
            }
         }
      }
   }

   public static boolean isActorGathering(UUID actorUuid) {
      if (actorUuid != null && !SESSIONS_BY_INITIATOR.isEmpty()) {
         for (NonGatherSystem.Session session : SESSIONS_BY_INITIATOR.values()) {
            if (session != null && (actorUuid.equals(session.initiatorUuid()) || actorUuid.equals(session.targetUuid()))) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean isMobGatheringWithPlayer(ServerWorld world, UUID mobUuid, UUID playerUuid) {
      if (world != null && mobUuid != null && playerUuid != null) {
         NonGatherSystem.Session session = SESSIONS_BY_INITIATOR.get(mobUuid);
         if (session == null || !world.getRegistryKey().equals(session.worldKey())) {
            return false;
         } else {
            return !playerUuid.equals(session.targetUuid())
               ? false
               : session.type() == NonGatherSystem.SessionType.MOB_PLAYER || session.type() == NonGatherSystem.SessionType.MOB_JOIN_ACTIVE_ANIMATION;
         }
      } else {
         return false;
      }
   }

   public static void cancelForActors(ServerWorld world, Collection<UUID> actorUuids) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty() && !SESSIONS_BY_INITIATOR.isEmpty()) {
         Set<UUID> actors = new HashSet<>(actorUuids);

         for (NonGatherSystem.Session session : List.copyOf(SESSIONS_BY_INITIATOR.values())) {
            if (session != null
               && world.getRegistryKey().equals(session.worldKey())
               && (actors.contains(session.initiatorUuid()) || actors.contains(session.targetUuid()))) {
               remove(session, world, false, null, "player_request_accepted");
            }
         }
      }
   }

   private static void tickMobPlayer(ServerWorld world, NonGatherSystem.Session session, MobEntity mob) {
      ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(session.targetUuid());
      if (player == null || player.getEntityWorld() != world || !player.isAlive() || player.isRemoved() || mob.isRemoved()) {
         remove(session, world, false, null, "invalid_actor");
      } else if (!NonMobGatherEligibility.canInitiateAutoGather(mob)) {
         remove(session, world, false, null, "mob_not_auto_gatherable");
      } else if (!NeedsOfNature.canMobAttackPlayer(player) || NeedsOfNature.isMobAttackFailsafeActive(world, mob.getUuid())) {
         remove(session, world, false, null, "player_not_attackable_or_failsafe");
      } else if (isAutomaticMobPlayerStartBlockedByMount(player)) {
         reportAutomaticMobPlayerMountBlock(world, mob, player, session.animationId(), "close_range_start");
         remove(session, world, false, null, "player_mounted_protected");
      } else if (AnimationService.isActorPendingOrActive(world, mob.getUuid()) || AnimationService.isActorPendingOrActive(world, player.getUuid())) {
         remove(session, world, false, null, "afw_actor_pending_or_active");
      } else if (timedOut(world, session)) {
         remove(session, world, true, NonGatherCancelReason.TIMEOUT, "timeout");
      } else {
         int noLosTicks = mob.canSee(player) ? 0 : session.noLosTicks() + 1;
         int tooFarTicks = mob.squaredDistanceTo(player) > 100.0 ? session.tooFarTicks() + 1 : 0;
         if (!cancelledForDistance(session, world, noLosTicks, tooFarTicks)) {
            mob.getLookControl().lookAt(player, 30.0F, 30.0F);
            if (NonAttackSystem.hasAnimationStartProtection(world, player)) {
               int repathCooldown = Math.max(0, session.repathCooldown() - 1);
               if (repathCooldown <= 0) {
                  startMovingIntoTarget(mob, player);
                  repathCooldown = 5;
               }

               update(session, repathCooldown, noLosTicks, tooFarTicks);
            } else if (closeEnoughToStart(mob, player)) {
               List<Entity> actors = sortedActors(mob, player);
               NonGatherSystem.StartCandidate startCandidate = resolveProtectedStartCandidate(world, session, actors, true, mob, player);
               if (startCandidate == null) {
                  NeedsOfNature.clearNoNGatherPair(world, List.of(mob.getUuid(), player.getUuid()));
                  SESSIONS_BY_INITIATOR.remove(session.initiatorUuid(), session);
               } else {
                  UUID instanceId = NeedsOfNature.startAnimationNowWithContext(
                     world,
                     startCandidate.animationId(),
                     actors,
                     startCandidate.actorKeys(),
                     startCandidate.stages(),
                     NonAnimationDamageBehavior.STOP_ON_DAMAGE,
                     false,
                     player,
                     NeedsOfNature.buildModeContext(true)
                  );
                  NeedsOfNature.clearNoNGatherPair(world, List.of(mob.getUuid(), player.getUuid()));
                  SESSIONS_BY_INITIATOR.remove(session.initiatorUuid(), session);
                  if (instanceId == null) {
                     NeedsOfNature.logRuntime(
                        "NoN gather start failed at close range: mobType={} player={} anim={}",
                        Registries.ENTITY_TYPE.getId(mob.getType()),
                        player.getName().getString(),
                        startCandidate.animationId()
                     );
                  }
               }
            } else {
               int repathCooldown = Math.max(0, session.repathCooldown() - 1);
               if (repathCooldown <= 0) {
                  startMovingIntoTarget(mob, player);
                  repathCooldown = 5;
               }

               update(session, repathCooldown, noLosTicks, tooFarTicks);
            }
         }
      }
   }

   private static void tickMobMob(ServerWorld world, NonGatherSystem.Session session, MobEntity initiator) {
      if (!(
         world.getEntity(session.targetUuid()) instanceof MobEntity partner
            && partner.getEntityWorld() == world
            && partner.isAlive()
            && !partner.isRemoved()
            && !initiator.isRemoved()
      )) {
         remove(session, world, false, null, "invalid_actor");
      } else if (!NonMobGatherEligibility.canInitiateAutoGather(initiator) || !NonMobGatherEligibility.canBeAutoGatherTarget(partner)) {
         remove(session, world, false, null, "mob_not_auto_gatherable");
      } else if (AnimationService.isActorPendingOrActive(world, initiator.getUuid()) || AnimationService.isActorPendingOrActive(world, partner.getUuid())) {
         remove(session, world, false, null, "afw_actor_pending_or_active");
      } else if (timedOut(world, session)) {
         remove(session, world, true, NonGatherCancelReason.TIMEOUT, "timeout");
      } else {
         boolean hasLos = initiator.canSee(partner) && partner.canSee(initiator);
         int noLosTicks = hasLos ? 0 : session.noLosTicks() + 1;
         int tooFarTicks = initiator.squaredDistanceTo(partner) > 100.0 ? session.tooFarTicks() + 1 : 0;
         if (!cancelledForDistance(session, world, noLosTicks, tooFarTicks)) {
            initiator.getLookControl().lookAt(partner, 30.0F, 30.0F);
            partner.getLookControl().lookAt(initiator, 30.0F, 30.0F);
            if (closeEnoughToStart(initiator, partner)) {
               List<Entity> actors = sortedActors(initiator, partner);
               NonGatherSystem.StartCandidate startCandidate = resolveProtectedStartCandidate(
                  world, session, actors, session.attackMode(), initiator, session.attackMode() ? partner : null
               );
               if (startCandidate == null) {
                  NeedsOfNature.clearNoNGatherPair(world, List.of(initiator.getUuid(), partner.getUuid()));
                  SESSIONS_BY_INITIATOR.remove(session.initiatorUuid(), session);
               } else {
                  UUID instanceId = NeedsOfNature.startAnimationNowWithContext(
                     world,
                     startCandidate.animationId(),
                     actors,
                     startCandidate.actorKeys(),
                     startCandidate.stages(),
                     NonAnimationDamageBehavior.STOP_ON_DAMAGE,
                     false,
                     session.attackMode() ? partner : null,
                     NeedsOfNature.buildModeContext(session.attackMode())
                  );
                  NeedsOfNature.clearNoNGatherPair(world, List.of(initiator.getUuid(), partner.getUuid()));
                  SESSIONS_BY_INITIATOR.remove(session.initiatorUuid(), session);
                  if (instanceId == null) {
                     NeedsOfNature.logRuntime(
                        "NoN mob gather start failed at close range: first={} second={} anim={}",
                        Registries.ENTITY_TYPE.getId(initiator.getType()),
                        Registries.ENTITY_TYPE.getId(partner.getType()),
                        startCandidate.animationId()
                     );
                  }
               }
            } else {
               int repathCooldown = Math.max(0, session.repathCooldown() - 1);
               if (repathCooldown <= 0) {
                  startMovingIntoTarget(initiator, partner);
                  startMovingIntoTarget(partner, initiator);
                  repathCooldown = 5;
               }

               update(session, repathCooldown, noLosTicks, tooFarTicks);
            }
         }
      }
   }

   private static void tickJoin(ServerWorld world, NonGatherSystem.Session session, MobEntity joiningMob) {
      ServerPlayerEntity requester = world.getServer().getPlayerManager().getPlayer(session.targetUuid());
      if (requester == null
         || requester.getEntityWorld() != world
         || !requester.isAlive()
         || requester.isRemoved()
         || joiningMob.isRemoved()
         || session.activeInstanceUuid() == null) {
         remove(session, world, false, null, "invalid_join_actor");
      } else if (!NonMobGatherEligibility.canInitiateAutoGather(joiningMob)) {
         remove(session, world, false, null, "mob_not_auto_gatherable");
      } else if (AnimationService.isActorPendingOrActive(world, joiningMob.getUuid())) {
         remove(session, world, false, null, "afw_actor_pending_or_active");
      } else {
         AnimationStageInfo stage = AnimationService.getCurrentStage(world, session.activeInstanceUuid());
         if (stage == null) {
            remove(session, world, false, null, "join_stage_unavailable");
         } else if (timedOut(world, session)) {
            remove(session, world, true, NonGatherCancelReason.TIMEOUT, "timeout");
         } else {
            int noLosTicks = joiningMob.canSee(requester) ? 0 : session.noLosTicks() + 1;
            int tooFarTicks = joiningMob.squaredDistanceTo(requester) > 100.0 ? session.tooFarTicks() + 1 : 0;
            if (!cancelledForDistance(session, world, noLosTicks, tooFarTicks)) {
               joiningMob.getLookControl().lookAt(requester, 30.0F, 30.0F);
               if (closeEnoughToJoin(joiningMob, requester)) {
                  Identifier joinAnimationId = resolveProtectedJoinAnimationId(world, session, joiningMob, requester);
                  if (joinAnimationId == null) {
                     NeedsOfNature.clearNoNGatherPair(world, List.of(joiningMob.getUuid(), requester.getUuid()));
                     SESSIONS_BY_INITIATOR.remove(session.initiatorUuid(), session);
                  } else {
                     boolean voluntaryManualPeakJoin = NeedsOfNature.isVoluntaryManualPeakInstance(world, session.activeInstanceUuid());
                     boolean accepted = NeedsOfNature.joinAnimationNow(world, session.activeInstanceUuid(), List.of(joiningMob), requester, joinAnimationId);
                     NeedsOfNature.clearNoNGatherPair(world, List.of(joiningMob.getUuid(), requester.getUuid()));
                     SESSIONS_BY_INITIATOR.remove(session.initiatorUuid(), session);
                     if (!accepted) {
                        NeedsOfNature.logRuntime(
                           "NoN join gather failed at close range: mobType={} player={} instance={} anim={}",
                           Registries.ENTITY_TYPE.getId(joiningMob.getType()),
                           requester.getName().getString(),
                           session.activeInstanceUuid(),
                           session.animationId()
                        );
                     } else if (voluntaryManualPeakJoin) {
                        NonDestroyedSkinSystem.applyDestroyedSkinVoluntaryJoinInitialDamage(world, requester);
                     }
                  }
               } else {
                  int repathCooldown = Math.max(0, session.repathCooldown() - 1);
                  if (repathCooldown <= 0) {
                     startMovingIntoTarget(joiningMob, requester);
                     repathCooldown = 5;
                  }

                  update(session, repathCooldown, noLosTicks, tooFarTicks);
               }
            }
         }
      }
   }

   private static void startMovingIntoTarget(MobEntity mob, Entity target) {
      if (mob != null && target != null) {
         double dx = target.getX() - mob.getX();
         double dz = target.getZ() - mob.getZ();
         double length = Math.sqrt(dx * dx + dz * dz);
         double targetX = target.getX();
         double targetZ = target.getZ();
         if (length > 1.0E-4) {
            targetX += dx / length * 1.0;
            targetZ += dz / length * 1.0;
         }

         mob.getNavigation().startMovingTo(targetX, target.getY(), targetZ, 1.15);
      }
   }

   @Nullable
   private static Identifier resolveProtectedJoinAnimationId(
      ServerWorld world, NonGatherSystem.Session session, MobEntity joiningMob, ServerPlayerEntity requester
   ) {
      List<Entity> combinedActors = resolveJoinActors(world, session.activeInstanceUuid(), joiningMob);
      if (combinedActors.isEmpty()) {
         return session.animationId();
      }

      Identifier predecessorAnimationId = AnimationService.getActiveAnimationId(world, session.activeInstanceUuid());
      if (predecessorAnimationId == null) {
         return null;
      }

      AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(session.animationId());
      if (definition == null) {
         return session.animationId();
      }

      if (!NonInjectorMatchPolicy.allowsAutomaticNonMatch(world, definition, combinedActors)) {
         boolean isAttack = NeedsOfNature.isInstanceAttack(world, session.activeInstanceUuid());
         NonGatherSystem.GatherCandidate fallback = findUnprotectedFallbackCandidate(
            world, combinedActors, isAttack, joiningMob, requester, true, session.animationId(), predecessorAnimationId
         );
         return fallback == null ? null : fallback.animationId();
      }

      if (!NeedsOfNature.isProtectedByAccessory(world, definition, combinedActors)) {
         return session.animationId();
      }

      if (!NeedsOfNature.consumeProtectorForAnimation(world, definition, combinedActors)) {
         return session.animationId();
      }

      boolean isAttack = NeedsOfNature.isInstanceAttack(world, session.activeInstanceUuid());
      NonGatherSystem.GatherCandidate fallback = findUnprotectedFallbackCandidate(
         world, combinedActors, isAttack, joiningMob, requester, true, session.animationId(), predecessorAnimationId
      );
      return fallback == null ? null : fallback.animationId();
   }

   private static List<Entity> resolveJoinActors(ServerWorld world, @Nullable UUID activeInstanceUuid, MobEntity joiningMob) {
      if (world != null && activeInstanceUuid != null && joiningMob != null) {
         List<Entity> actors = new ArrayList<>();

         for (UUID actorUuid : NeedsOfNature.getInstanceActors(world, activeInstanceUuid)) {
            Entity actor = world.getEntity(actorUuid);
            if (actor == null) {
               actor = world.getServer().getPlayerManager().getPlayer(actorUuid);
            }

            if (actor != null && actor.getEntityWorld() == world && !actor.isRemoved()) {
               actors.add(actor);
            }
         }

         if (joiningMob.getEntityWorld() == world && !joiningMob.isRemoved()) {
            actors.add(joiningMob);
         }

         actors.sort(Comparator.comparingInt(Entity::getId));
         return actors;
      } else {
         return List.of();
      }
   }

   private static boolean timedOut(ServerWorld world, NonGatherSystem.Session session) {
      return world.getTime() - session.startTick() > 3600L;
   }

   @Nullable
   private static NonGatherSystem.StartCandidate resolveProtectedStartCandidate(
      ServerWorld world, NonGatherSystem.Session session, List<Entity> actors, boolean isAttack, Entity attacker, @Nullable Entity gatherAnchor
   ) {
      AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(session.animationId());
      if (definition == null) {
         return new NonGatherSystem.StartCandidate(session.animationId(), session.actorKeys(), session.stages());
      }

      if (!NonInjectorMatchPolicy.allowsAutomaticNonMatch(world, definition, actors)) {
         NonGatherSystem.GatherCandidate fallback = findUnprotectedFallbackCandidate(
            world, actors, isAttack, attacker, gatherAnchor, true, session.animationId(), null
         );
         return fallback == null ? null : new NonGatherSystem.StartCandidate(fallback.animationId(), fallback.actorKeys(), fallback.stages());
      }

      if (!NeedsOfNature.isProtectedByAccessory(world, definition, actors)) {
         return new NonGatherSystem.StartCandidate(session.animationId(), session.actorKeys(), session.stages());
      }

      if (!NeedsOfNature.consumeProtectorForAnimation(world, definition, actors)) {
         return new NonGatherSystem.StartCandidate(session.animationId(), session.actorKeys(), session.stages());
      }

      NonGatherSystem.GatherCandidate fallback = findUnprotectedFallbackCandidate(
         world, actors, isAttack, attacker, gatherAnchor, true, session.animationId(), null
      );
      return fallback == null ? null : new NonGatherSystem.StartCandidate(fallback.animationId(), fallback.actorKeys(), fallback.stages());
   }

   private static boolean cancelledForDistance(NonGatherSystem.Session session, ServerWorld world, int noLosTicks, int tooFarTicks) {
      if (noLosTicks > 100) {
         remove(session, world, true, NonGatherCancelReason.NO_LOS, "no_los");
         return true;
      } else if (tooFarTicks > 200) {
         remove(session, world, true, NonGatherCancelReason.TOO_FAR, "too_far");
         return true;
      } else {
         return false;
      }
   }

   private static void update(NonGatherSystem.Session session, int repathCooldown, int noLosTicks, int tooFarTicks) {
      SESSIONS_BY_INITIATOR.put(
         session.initiatorUuid(),
         new NonGatherSystem.Session(
            session.type(),
            session.initiatorUuid(),
            session.targetUuid(),
            session.animationId(),
            session.actorKeys(),
            session.stages(),
            session.attackMode(),
            session.activeInstanceUuid(),
            session.worldKey(),
            session.startTick(),
            repathCooldown,
            noLosTicks,
            tooFarTicks
         )
      );
   }

   private static void remove(NonGatherSystem.Session session, ServerWorld world, boolean applyCooldown, @Nullable NonGatherCancelReason reason) {
      remove(session, world, applyCooldown, reason, "unspecified");
   }

   private static void remove(NonGatherSystem.Session session, ServerWorld world, boolean applyCooldown, @Nullable NonGatherCancelReason reason, String detail) {
      if (session != null && world != null) {
         Entity initiator = world.getEntity(session.initiatorUuid());
         Entity target = world.getEntity(session.targetUuid());
         if (initiator instanceof MobEntity mob) {
            mob.getNavigation().stop();
         }

         if (target instanceof MobEntity mob) {
            mob.getNavigation().stop();
         }

         List<UUID> actorUuids = List.of(session.initiatorUuid(), session.targetUuid());
         if (applyCooldown && reason != null) {
            NeedsOfNature.applyNoNGatherCancellation(world, session.animationId(), actorUuids, reason);
         } else {
            NeedsOfNature.clearNoNGatherPair(world, actorUuids);
         }

         SESSIONS_BY_INITIATOR.remove(session.initiatorUuid(), session);
      }
   }

   private static boolean closeEnoughToStart(Entity a, Entity b) {
      if (a != null && b != null) {
         return Math.abs(a.getY() - b.getY()) > 2.5 ? false : horizontalBoxDistanceSq(a.getBoundingBox(), b.getBoundingBox()) <= 0.0625;
      } else {
         return false;
      }
   }

   private static boolean closeEnoughToJoin(Entity a, Entity b) {
      return closeEnoughToStart(a, b);
   }

   private static double horizontalBoxDistanceSq(Box a, Box b) {
      double dx = axisGap(a.minX, a.maxX, b.minX, b.maxX);
      double dz = axisGap(a.minZ, a.maxZ, b.minZ, b.maxZ);
      return dx * dx + dz * dz;
   }

   private static double axisGap(double aMin, double aMax, double bMin, double bMax) {
      if (aMax < bMin) {
         return bMin - aMax;
      } else {
         return bMax < aMin ? aMin - bMax : 0.0;
      }
   }

   private static List<Entity> sortedActors(Entity first, Entity second) {
      List<Entity> actors = new ArrayList<>(2);
      actors.add(first);
      actors.add(second);
      actors.sort(Comparator.comparingInt(Entity::getId));
      return actors;
   }

   public record GatherCandidate(Identifier animationId, List<String> actorKeys, List<AnimationStageInfo> stages) {
   }

   private record Session(
      NonGatherSystem.SessionType type,
      UUID initiatorUuid,
      UUID targetUuid,
      Identifier animationId,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      boolean attackMode,
      @Nullable UUID activeInstanceUuid,
      RegistryKey<World> worldKey,
      long startTick,
      int repathCooldown,
      int noLosTicks,
      int tooFarTicks
   ) {
   }

   private enum SessionType {
      MOB_PLAYER,
      MOB_MOB,
      MOB_JOIN_ACTIVE_ANIMATION;
   }

   private record StartCandidate(Identifier animationId, List<String> actorKeys, List<AnimationStageInfo> stages) {
   }
}
