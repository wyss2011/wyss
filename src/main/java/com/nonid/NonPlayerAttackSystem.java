package com.nonid;

import com.nonid.api.animation.NonAnimationDamageBehavior;
import com.nonid.internal.animation.api.AnimationService;
import com.nonid.internal.animation.data.AnimationDefinitions;
import com.nonid.internal.animation.server.AnimationStartContext;
import com.nonid.internal.animation.server.ServerAnimationController;
import com.nonid.network.AttackStateS2CPayload;
import com.nonid.network.PlayerAttackInputC2SPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDamage;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

final class NonPlayerAttackSystem {
   private static final int INPUT_LEFT = 1;
   private static final int INPUT_RIGHT = 2;
   private static final long INPUT_MIN_INTERVAL_TICKS = 1L;
   private static final float PVP_MASH_RANDOM_MIN = 0.8F;
   private static final float PVP_MASH_RANDOM_MAX = 1.2F;
   private static final Map<UUID, NonPlayerAttackSystem.Contest> CONTESTS = new ConcurrentHashMap<>();
   private static final Map<UUID, NonPlayerAttackSystem.DefeatedOwnership> DEFEATED_OWNERSHIP = new ConcurrentHashMap<>();
   private static final Map<UUID, NonPlayerAttackSystem.FinishAttempt> FINISH_ATTEMPTS = new ConcurrentHashMap<>();
   private static final ThreadLocal<NonPlayerAttackSystem.DamageBypass> VANILLA_PVP_BYPASS = new ThreadLocal<>();

   private NonPlayerAttackSystem() {
   }

   static void register() {
      AttackEntityCallback.EVENT.register((AttackEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (!(world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity attacker && entity instanceof ServerPlayerEntity victim)) {
            return ActionResult.PASS;
         } else if (hand == Hand.MAIN_HAND && attacker.getMainHandStack().isEmpty()) {
            return (ActionResult)(tryStartPlayerAttack(serverWorld, attacker, victim) ? ActionResult.SUCCESS : ActionResult.PASS);
         } else {
            return ActionResult.PASS;
         }
      });
      ServerPlayNetworking.registerGlobalReceiver(
         PlayerAttackInputC2SPayload.ID, (payload, context) -> context.server().execute(() -> handleInput(context.player(), payload))
      );
      ServerLivingEntityEvents.ALLOW_DAMAGE
         .register(
            (AllowDamage)(entity, source, amount) -> {
               if (entity instanceof ServerPlayerEntity victim && victim.getEntityWorld() instanceof ServerWorld world) {
                  NonPlayerAttackSystem.DefeatedOwnership ownership = validOwnership(world, victim.getUuid());
                  if (ownership == null) {
                     return true;
                  } else {
                     ServerPlayerEntity attributed = resolveAttributedPlayer(source);
                     if (attributed != null && ownership.ownerUuid().equals(attributed.getUuid())) {
                        FINISH_ATTEMPTS.put(
                           victim.getUuid(),
                           new NonPlayerAttackSystem.FinishAttempt(
                              world.getRegistryKey(), attributed.getUuid(), ownership.instanceId(), countReadyTotems(victim), world.getTime()
                           )
                        );
                        return true;
                     } else {
                        return false;
                     }
                  }
               } else {
                  return true;
               }
            }
         );
      ServerLivingEntityEvents.AFTER_DAMAGE.register((AfterDamage)(entity, source, baseDamageTaken, damageTaken, blocked) -> {
         if (entity instanceof ServerPlayerEntity victim && victim.getEntityWorld() instanceof ServerWorld world) {
            NonPlayerAttackSystem.FinishAttempt attempt = FINISH_ATTEMPTS.remove(victim.getUuid());
            if (attempt != null && attempt.worldKey().equals(world.getRegistryKey()) && !blocked) {
               ServerPlayerEntity attributed = resolveAttributedPlayer(source);
               if (attributed != null && attempt.ownerUuid().equals(attributed.getUuid())) {
                  if (victim.isAlive() && countReadyTotems(victim) < attempt.totemsBefore()) {
                     AnimationService.stopAnimation(world, attempt.instanceId());
                  }
               }
            }
         }
      });
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> server.execute(() -> clearForPlayer(handler.player)));
   }

   private static boolean tryStartPlayerAttack(ServerWorld world, ServerPlayerEntity attacker, ServerPlayerEntity victim) {
      if (world != null && attacker != null && victim != null) {
         if (attacker == victim || attacker.getEntityWorld() != world || victim.getEntityWorld() != world) {
            return false;
         } else if (!attacker.isAlive() || attacker.isRemoved() || attacker.isSpectator()) {
            return false;
         } else if (!victim.isAlive() || victim.isRemoved() || victim.isSpectator()) {
            return false;
         } else if (isOwnerFinishingDamage(world, victim, attacker)) {
            return false;
         } else if (victim.isCreative() && !NeedsOfNature.getConfig().canAttackCreativePlayers()) {
            return false;
         } else if (!(attacker instanceof EnergyHolder holder && holder.getEnergy() >= 200)) {
            return false;
         } else {
            if (!NonPlayerInteractionSystem.areAttacksAllowed(victim, attacker)) {
               return false;
            }

            if (AnimationService.isActorPendingOrActive(world, attacker.getUuid()) || AnimationService.isActorPendingOrActive(world, victim.getUuid())) {
               return false;
            }

            if (!NonAttackSystem.hasAnimationStartProtection(world, attacker) && !NonAttackSystem.hasAnimationStartProtection(world, victim)) {
               NonPlayerAttackSystem.AttackSelection selection = selectAttack(world, attacker, victim, true, true);
               if (selection.match() == null) {
                  return selection.blockedByProtector();
               }

               AnimationService.MatchedAnimation match = selection.match();
               List<Entity> actors = sortedActors(attacker, victim);
               NonGatherSystem.cancelForActors(world, List.of(attacker.getUuid(), victim.getUuid()));
               UUID instanceId = NeedsOfNature.startAnimationNowWithContext(
                  world,
                  match.animationId(),
                  actors,
                  match.actorKeys(),
                  match.stages(),
                  NonAnimationDamageBehavior.STOP_ON_DAMAGE,
                  false,
                  victim,
                  buildPlayerAttackContext(victim.getUuid(), attacker.getUuid(), Set.of(attacker.getUuid())),
                  attacker,
                  false,
                  true
               );
               if (instanceId != null) {
                  NeedsOfNature.logRuntime(
                     "[NoN] Player attack started: attacker={} victim={} animation={} instance={}",
                     attacker.getName().getString(),
                     victim.getName().getString(),
                     match.animationId(),
                     instanceId
                  );
               }

               return true;
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   @Nullable
   static AnimationService.MatchedAnimation findViableAttack(ServerWorld world, ServerPlayerEntity attacker, ServerPlayerEntity victim) {
      NonPlayerAttackSystem.AttackSelection selection = selectAttack(world, attacker, victim, false, false);
      return selection.match();
   }

   private static NonPlayerAttackSystem.AttackSelection selectAttack(
      ServerWorld world, ServerPlayerEntity attacker, ServerPlayerEntity victim, boolean requireStartEligibility, boolean consumeProtector
   ) {
      List<Entity> actors = sortedActors(attacker, victim);
      Set<Identifier> excluded = new LinkedHashSet<>();
      boolean blockedByProtector = false;
      boolean protectorConsumed = false;

      while (true) {
         AnimationService.MatchedAnimation match = NonAnimationMatcher.findAutomaticAttack(
            world,
            actors,
            Set.of(),
            victim,
            requireStartEligibility,
            attacker,
            excluded,
            (candidateWorld, definitionx, candidateActors) -> NonPlayerInteractionSystem.isPlayerInteractionDefinition(definitionx),
            null
         );
         if (match == null) {
            return new NonPlayerAttackSystem.AttackSelection(null, blockedByProtector);
         }

         AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(match.animationId());
         if (definition == null) {
            excluded.add(match.animationId());
         } else {
            if (!NeedsOfNature.isProtectedByAccessory(world, definition, actors)) {
               return new NonPlayerAttackSystem.AttackSelection(match, blockedByProtector);
            }

            blockedByProtector = true;
            if (consumeProtector && !protectorConsumed) {
               protectorConsumed = NeedsOfNature.consumeProtectorForAnimation(world, definition, actors);
            }

            excluded.add(match.animationId());
         }
      }
   }

   static AnimationStartContext buildPlayerAttackContext(UUID victimUuid, UUID ownerUuid, Set<UUID> attackerUuids) {
      return AnimationStartContext.empty()
         .withSessionData(NonAnimationSessionData.playerAttack(new NonAnimationSessionData.PlayerAttack(victimUuid, ownerUuid, ownerUuid, attackerUuids)));
   }

   static AnimationStartContext prepareDefeatedJoinContext(
      ServerWorld world, List<UUID> existingActorUuids, ServerPlayerEntity requester, AnimationStartContext sourceContext
   ) {
      AnimationStartContext context = sourceContext == null ? AnimationStartContext.empty() : sourceContext;
      NonAnimationSessionData.PlayerAttack existing = playerAttack(context);
      UUID victimUuid = existing == null ? firstPlayerUuid(world, existingActorUuids) : existing.victimUuid();
      if (victimUuid == null) {
         return context;
      }

      UUID finishOwnerUuid = existing == null ? requester.getUuid() : existing.finishOwnerUuid();
      LinkedHashSet<UUID> attackers = new LinkedHashSet<>();
      if (existing != null) {
         attackers.addAll(existing.attackerUuids());
      }

      attackers.add(requester.getUuid());
      return context.withSessionData(
         NonAnimationSessionData.playerAttack(new NonAnimationSessionData.PlayerAttack(victimUuid, finishOwnerUuid, requester.getUuid(), attackers))
      );
   }

   static AnimationStartContext defeatedTailContext(AnimationStartContext sourceContext, UUID victimUuid) {
      AnimationStartContext context = sourceContext == null ? AnimationStartContext.empty() : sourceContext;
      NonAnimationSessionData.PlayerAttack attack = playerAttack(context);
      return attack == null
         ? context.withSessionData(NonAnimationSessionData.attack())
         : context.withSessionData(NonAnimationSessionData.playerAttack(attack.forDefeatedTail(victimUuid)));
   }

   static boolean isPlayerAttackContext(@Nullable AnimationStartContext context) {
      return playerAttack(context) != null;
   }

   @Nullable
   static ServerPlayerEntity resolveAttackVictim(ServerWorld world, UUID instanceId, List<UUID> actorUuids) {
      ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
      NonAnimationSessionData.PlayerAttack attack = snapshot == null ? null : playerAttack(snapshot.startContext());
      if (attack != null && actorUuids != null && actorUuids.contains(attack.victimUuid())) {
         ServerPlayerEntity victim = world.getServer().getPlayerManager().getPlayer(attack.victimUuid());
         if (victim != null && victim.getEntityWorld() == world) {
            return victim;
         }
      }

      if (actorUuids == null) {
         return null;
      }

      for (UUID actorUuid : actorUuids) {
         ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(actorUuid);
         if (player != null && player.getEntityWorld() == world) {
            return player;
         }
      }

      return null;
   }

   static void onAnimationStarted(
      ServerWorld world, UUID instanceId, List<UUID> actorUuids, @Nullable NonAnimationSessionData.PlayerAttack attack, long startTick, boolean defeated
   ) {
      if (attack != null && actorUuids != null && actorUuids.contains(attack.victimUuid())) {
         Set<UUID> actorSet = Set.copyOf(actorUuids);
         LinkedHashSet<UUID> activeAttackers = new LinkedHashSet<>();

         for (UUID attackerUuid : attack.attackerUuids()) {
            if (actorSet.contains(attackerUuid)) {
               ServerPlayerEntity attacker = world.getServer().getPlayerManager().getPlayer(attackerUuid);
               if (attacker != null && attacker.getEntityWorld() == world) {
                  activeAttackers.add(attackerUuid);
               }
            }
         }

         if (defeated) {
            DEFEATED_OWNERSHIP.put(
               attack.victimUuid(),
               new NonPlayerAttackSystem.DefeatedOwnership(world.getRegistryKey(), attack.victimUuid(), attack.finishOwnerUuid(), instanceId)
            );
         }

         if (!activeAttackers.isEmpty()) {
            NonPlayerAttackSystem.Contest contest = new NonPlayerAttackSystem.Contest(
               world.getRegistryKey(), instanceId, attack.victimUuid(), attack.finishOwnerUuid(), attack.initiatorUuid(), activeAttackers, startTick, defeated
            );
            CONTESTS.put(instanceId, contest);
         }
      }
   }

   static void onAnimationStopped(ServerWorld world, UUID instanceId) {
      NonPlayerAttackSystem.Contest contest = CONTESTS.remove(instanceId);
      if (contest != null) {
         FINISH_ATTEMPTS.remove(contest.victimUuid);
      }

      DEFEATED_OWNERSHIP.entrySet().removeIf(entry -> {
         NonPlayerAttackSystem.DefeatedOwnership ownership = entry.getValue();
         return ownership.instanceId().equals(instanceId) && ownership.worldKey().equals(world.getRegistryKey());
      });
   }

   static boolean syncEscapeControl(ServerWorld world, UUID instanceId, boolean escapable, boolean defeated) {
      NonPlayerAttackSystem.Contest contest = CONTESTS.get(instanceId);
      if (contest != null && contest.worldKey.equals(world.getRegistryKey())) {
         contest.escapable = escapable;
         contest.defeated = defeated;
         broadcastState(world, contest);
         return true;
      } else {
         return false;
      }
   }

   static boolean isContest(UUID instanceId) {
      return instanceId != null && CONTESTS.containsKey(instanceId);
   }

   static List<String> getContestDebugLines(ServerPlayerEntity target) {
      if (target != null && target.getEntityWorld() instanceof ServerWorld world) {
         NonPlayerAttackSystem.Contest contest = findContestForPlayer(world, target.getUuid());
         if (contest == null) {
            return List.of();
         } else {
            ServerPlayerEntity victim = world.getServer().getPlayerManager().getPlayer(contest.victimUuid);
            if (victim != null && victim.getEntityWorld() == world) {
               List<String> lines = new ArrayList<>();
               String role = contest.victimUuid.equals(target.getUuid()) ? "defender" : "attacker";
               lines.add(
                  String.format(
                     Locale.ROOT, "PvP contest %s | role %s | progress %.1f%%", contest.instanceId.toString().substring(0, 8), role, contest.progress * 100.0F
                  )
               );
               lines.add(formatContestParticipant(world, contest, victim, true));
               contest.activeAttackerUuids
                  .stream()
                  .sorted(Comparator.comparing(UUID::toString))
                  .map(uuid -> world.getServer().getPlayerManager().getPlayer(uuid))
                  .filter(player -> player != null && player.getEntityWorld() == world)
                  .map(player -> formatContestParticipant(world, contest, player, false))
                  .forEach(lines::add);
               return List.copyOf(lines);
            } else {
               return List.of();
            }
         }
      } else {
         return List.of();
      }
   }

   @Nullable
   private static NonPlayerAttackSystem.Contest findContestForPlayer(ServerWorld world, UUID playerUuid) {
      for (NonPlayerAttackSystem.Contest contest : CONTESTS.values()) {
         if (contest.worldKey.equals(world.getRegistryKey()) && (contest.victimUuid.equals(playerUuid) || contest.activeAttackerUuids.contains(playerUuid))) {
            return contest;
         }
      }

      return null;
   }

   private static String formatContestParticipant(ServerWorld world, NonPlayerAttackSystem.Contest contest, ServerPlayerEntity participant, boolean defender) {
      NonAttackSystem.PvpMashFactors factors = NonAttackSystem.resolveServerPlayerContestStep(world, participant, defender, contest.defeated, contest.startTick);
      int energy = 0;
      int maxEnergy = 0;
      if (participant instanceof EnergyHolder holder) {
         energy = holder.getEnergy();
         maxEnergy = holder.getMaxEnergy();
      }

      return String.format(
         Locale.ROOT,
         "%s %s: %s%.2f-%.2f%%/input | HP %.1f/%.1f | energy %d/%d | health %.2fx, heat %.2fx, healthy %.2fx, ease %.2fx",
         defender ? "Defender" : "Attacker",
         participant.getName().getString(),
         defender ? "+" : "-",
         factors.step() * 0.8F * 100.0F,
         factors.step() * 1.2F * 100.0F,
         participant.getHealth(),
         participant.getMaxHealth(),
         energy,
         maxEnergy,
         factors.healthMultiplier(),
         factors.heatMultiplier(),
         factors.healthyDefenderMultiplier(),
         factors.defeatedEaseMultiplier()
      );
   }

   static void tick(ServerWorld world) {
      long now = world.getTime();

      for (NonPlayerAttackSystem.Contest contest : List.copyOf(CONTESTS.values())) {
         if (contest.worldKey.equals(world.getRegistryKey())) {
            if (AnimationService.getCurrentStage(world, contest.instanceId) == null) {
               CONTESTS.remove(contest.instanceId, contest);
            } else {
               ServerPlayerEntity victim = world.getServer().getPlayerManager().getPlayer(contest.victimUuid);
               if (victim == null || victim.getEntityWorld() != world || !victim.isAlive() || victim.isRemoved()) {
                  CONTESTS.remove(contest.instanceId, contest);
               }
            }
         }
      }

      DEFEATED_OWNERSHIP.entrySet().removeIf(entry -> {
         NonPlayerAttackSystem.DefeatedOwnership ownership = entry.getValue();
         return ownership.worldKey().equals(world.getRegistryKey()) && AnimationService.getCurrentStage(world, ownership.instanceId()) == null;
      });
      FINISH_ATTEMPTS.entrySet().removeIf(entry -> {
         NonPlayerAttackSystem.FinishAttempt attempt = entry.getValue();
         return attempt.worldKey().equals(world.getRegistryKey()) && now - attempt.tick() > 1L;
      });
   }

   private static void handleInput(ServerPlayerEntity player, PlayerAttackInputC2SPayload payload) {
      if (player != null && payload != null && player.getEntityWorld() instanceof ServerWorld world) {
         NonPlayerAttackSystem.Contest contest = CONTESTS.get(payload.instanceId());
         if (contest != null && !contest.resolving && contest.escapable) {
            if (contest.worldKey.equals(world.getRegistryKey())) {
               boolean defender = contest.victimUuid.equals(player.getUuid());
               boolean attacker = contest.activeAttackerUuids.contains(player.getUuid());
               if (defender || attacker) {
                  int key = payload.keyId();
                  if (key == 1 || key == 2) {
                     long now = world.getTime();
                     long lastTick = contest.lastInputTicks.getOrDefault(player.getUuid(), Long.MIN_VALUE);
                     if (lastTick == Long.MIN_VALUE || now - lastTick >= 1L) {
                        int lastKey = contest.lastKeys.getOrDefault(player.getUuid(), 0);
                        if (lastKey != key) {
                           contest.lastInputTicks.put(player.getUuid(), now);
                           contest.lastKeys.put(player.getUuid(), key);
                           ServerPlayerEntity victim = world.getServer().getPlayerManager().getPlayer(contest.victimUuid);
                           if (victim != null && victim.getEntityWorld() == world && victim.isAlive()) {
                              NonAttackSystem.PvpMashFactors factors = NonAttackSystem.resolveServerPlayerContestStep(
                                 world, player, defender, contest.defeated, contest.startTick
                              );
                              float randomMultiplier = 0.8F + world.getRandom().nextFloat() * 0.40000004F;
                              float step = factors.step() * randomMultiplier;
                              contest.progress = clampProgress(contest.progress + (defender ? step : -step));
                              broadcastState(world, contest);
                              if (contest.progress >= 1.0F) {
                                 contest.resolving = true;
                                 NeedsOfNature.completeAttackEscape(world, victim, contest.instanceId, contest.initiatorUuid);
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

   private static void broadcastState(ServerWorld world, NonPlayerAttackSystem.Contest contest) {
      AttackStateS2CPayload.EscapeProfile profile = contest.defeated
         ? AttackStateS2CPayload.EscapeProfile.DEFEATED
         : AttackStateS2CPayload.EscapeProfile.NORMAL;
      ServerPlayerEntity victim = world.getServer().getPlayerManager().getPlayer(contest.victimUuid);
      if (victim != null && victim.getEntityWorld() == world) {
         ServerPlayNetworking.send(
            victim, new AttackStateS2CPayload(contest.instanceId, true, contest.escapable, profile, AttackStateS2CPayload.PvpRole.DEFENDER, contest.progress)
         );
      }

      for (UUID attackerUuid : contest.activeAttackerUuids) {
         ServerPlayerEntity attacker = world.getServer().getPlayerManager().getPlayer(attackerUuid);
         if (attacker != null && attacker.getEntityWorld() == world) {
            ServerPlayNetworking.send(
               attacker,
               new AttackStateS2CPayload(contest.instanceId, true, contest.escapable, profile, AttackStateS2CPayload.PvpRole.ATTACKER, contest.progress)
            );
         }
      }
   }

   static boolean isOwnerFinishingDamage(ServerWorld world, ServerPlayerEntity victim, @Nullable DamageSource source) {
      ServerPlayerEntity attacker = resolveAttributedPlayer(source);
      return attacker != null && isOwnerFinishingDamage(world, victim, attacker);
   }

   static boolean shouldBypassVanillaPvpCheck(ServerPlayerEntity victim, PlayerEntity attacker) {
      if (victim != null && attacker != null && victim.getEntityWorld() instanceof ServerWorld world) {
         NonPlayerAttackSystem.DamageBypass bypass = VANILLA_PVP_BYPASS.get();
         if (bypass != null && bypass.victimUuid().equals(victim.getUuid()) && bypass.attackerUuid().equals(attacker.getUuid())) {
            return true;
         }

         NonPlayerAttackSystem.DefeatedOwnership ownership = validOwnership(world, victim.getUuid());
         return ownership != null && ownership.ownerUuid().equals(attacker.getUuid());
      } else {
         return false;
      }
   }

   static void applyPlayerAttributedEscapeDamage(ServerWorld world, ServerPlayerEntity victim, ServerPlayerEntity attacker, float amount) {
      NonPlayerAttackSystem.DamageBypass previous = VANILLA_PVP_BYPASS.get();
      VANILLA_PVP_BYPASS.set(new NonPlayerAttackSystem.DamageBypass(victim.getUuid(), attacker.getUuid()));

      try {
         victim.damage(world, victim.getDamageSources().playerAttack(attacker), amount);
      } finally {
         if (previous == null) {
            VANILLA_PVP_BYPASS.remove();
         } else {
            VANILLA_PVP_BYPASS.set(previous);
         }
      }
   }

   private static boolean isOwnerFinishingDamage(ServerWorld world, ServerPlayerEntity victim, ServerPlayerEntity attacker) {
      NonPlayerAttackSystem.DefeatedOwnership ownership = validOwnership(world, victim.getUuid());
      return ownership != null && ownership.ownerUuid().equals(attacker.getUuid());
   }

   static void onOwnerFinishingDeath(ServerWorld world, ServerPlayerEntity victim) {
      if (world != null && victim != null) {
         NonPlayerAttackSystem.DefeatedOwnership ownership = DEFEATED_OWNERSHIP.get(victim.getUuid());
         if (ownership != null && ownership.worldKey().equals(world.getRegistryKey())) {
            FINISH_ATTEMPTS.remove(victim.getUuid());
         }
      }
   }

   @Nullable
   static ServerPlayerEntity resolveAttributedPlayer(@Nullable DamageSource source) {
      if (source == null) {
         return null;
      } else if (source.getAttacker() instanceof ServerPlayerEntity player) {
         return player;
      } else {
         return source.getSource() instanceof ServerPlayerEntity player ? player : null;
      }
   }

   static NonAnimationDamageBehavior defeatedDamageBehavior(AnimationStartContext context) {
      return isPlayerAttackContext(context) ? NonAnimationDamageBehavior.IGNORE_DAMAGE : NonAnimationDamageBehavior.STOP_ON_DAMAGE;
   }

   static void clearForRespawn(ServerPlayerEntity player) {
      clearForPlayer(player);
   }

   private static void clearForPlayer(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld() instanceof ServerWorld world) {
         UUID var6 = player.getUuid();
         LinkedHashSet<UUID> stopInstances = new LinkedHashSet<>();

         for (NonPlayerAttackSystem.Contest contest : List.copyOf(CONTESTS.values())) {
            if (contest.worldKey.equals(world.getRegistryKey())
               && (
                  contest.victimUuid.equals(var6)
                     || contest.finishOwnerUuid.equals(var6)
                     || contest.initiatorUuid.equals(var6)
                     || contest.activeAttackerUuids.contains(var6)
               )) {
               stopInstances.add(contest.instanceId);
            }
         }

         for (NonPlayerAttackSystem.DefeatedOwnership ownership : List.copyOf(DEFEATED_OWNERSHIP.values())) {
            if (ownership.worldKey().equals(world.getRegistryKey()) && (ownership.victimUuid().equals(var6) || ownership.ownerUuid().equals(var6))) {
               stopInstances.add(ownership.instanceId());
            }
         }

         for (UUID instanceId : stopInstances) {
            AnimationService.stopAnimation(world, instanceId);
         }

         CONTESTS.entrySet().removeIf(entry -> stopInstances.contains(entry.getKey()));
         DEFEATED_OWNERSHIP.entrySet().removeIf(entry -> entry.getKey().equals(var6) || entry.getValue().ownerUuid().equals(var6));
         FINISH_ATTEMPTS.remove(var6);
      }
   }

   static void clearRuntimeState() {
      CONTESTS.clear();
      DEFEATED_OWNERSHIP.clear();
      FINISH_ATTEMPTS.clear();
   }

   @Nullable
   private static NonPlayerAttackSystem.DefeatedOwnership validOwnership(ServerWorld world, UUID victimUuid) {
      NonPlayerAttackSystem.DefeatedOwnership ownership = DEFEATED_OWNERSHIP.get(victimUuid);
      if (ownership == null || !ownership.worldKey().equals(world.getRegistryKey())) {
         return null;
      } else if (AnimationService.getCurrentStage(world, ownership.instanceId()) == null) {
         DEFEATED_OWNERSHIP.remove(victimUuid, ownership);
         return null;
      } else {
         return ownership;
      }
   }

   @Nullable
   static NonAnimationSessionData.PlayerAttack playerAttack(@Nullable AnimationStartContext context) {
      return context != null && context.sessionData() instanceof NonAnimationSessionData data ? data.playerAttack() : null;
   }

   @Nullable
   private static UUID firstPlayerUuid(ServerWorld world, List<UUID> actorUuids) {
      if (world != null && actorUuids != null) {
         for (UUID actorUuid : actorUuids) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(actorUuid);
            if (player != null && player.getEntityWorld() == world) {
               return player.getUuid();
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static List<Entity> sortedActors(Entity first, Entity second) {
      ArrayList<Entity> actors = new ArrayList<>(List.of(first, second));
      actors.sort(Comparator.comparingInt(Entity::getId));
      return List.copyOf(actors);
   }

   private static int countReadyTotems(ServerPlayerEntity player) {
      int count = 0;
      if (player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
         count += player.getMainHandStack().getCount();
      }

      if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
         count += player.getOffHandStack().getCount();
      }

      return count;
   }

   private static float clampProgress(float progress) {
      return !Float.isFinite(progress) ? 0.0F : Math.max(0.0F, Math.min(1.0F, progress));
   }

   private record AttackSelection(@Nullable AnimationService.MatchedAnimation match, boolean blockedByProtector) {
   }

   private static final class Contest {
      final RegistryKey<World> worldKey;
      final UUID instanceId;
      final UUID victimUuid;
      final UUID finishOwnerUuid;
      final UUID initiatorUuid;
      final Set<UUID> activeAttackerUuids;
      final long startTick;
      final Map<UUID, Integer> lastKeys = new HashMap<>();
      final Map<UUID, Long> lastInputTicks = new HashMap<>();
      float progress;
      boolean escapable;
      boolean defeated;
      boolean resolving;

      Contest(
         RegistryKey<World> worldKey,
         UUID instanceId,
         UUID victimUuid,
         UUID finishOwnerUuid,
         UUID initiatorUuid,
         Set<UUID> activeAttackerUuids,
         long startTick,
         boolean defeated
      ) {
         this.worldKey = worldKey;
         this.instanceId = instanceId;
         this.victimUuid = victimUuid;
         this.finishOwnerUuid = finishOwnerUuid;
         this.initiatorUuid = initiatorUuid;
         this.activeAttackerUuids = Set.copyOf(activeAttackerUuids);
         this.startTick = startTick;
         this.defeated = defeated;
      }
   }

   private record DamageBypass(UUID victimUuid, UUID attackerUuid) {
   }

   private record DefeatedOwnership(RegistryKey<World> worldKey, UUID victimUuid, UUID ownerUuid, UUID instanceId) {
   }

   private record FinishAttempt(RegistryKey<World> worldKey, UUID ownerUuid, UUID instanceId, int totemsBefore, long tick) {
   }
}
