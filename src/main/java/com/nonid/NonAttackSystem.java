package com.nonid;

import com.nonid.internal.animation.api.AnimationService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

final class NonAttackSystem {
   private static final float ATTACK_ESCAPE_DAMAGE = 4.0F;
   private static final float ATTACK_ESCAPE_MIN_DAMAGE = 1.0F;
   private static final float ATTACK_ESCAPE_MIN_HEALTH = 1.0F;
   private static final int ATTACK_OUTCOME_FAILSAFE_WINDOW_TICKS = 12000;
   private static final int ATTACK_OUTCOME_FAILSAFE_COOLDOWN_TICKS = 12000;
   private static final int ATTACK_BLOCK_DEBUG_COOLDOWN_TICKS = 400;
   private static final double ATTACK_BLOCK_DEBUG_RADIUS = 24.0;
   private static final double POST_ESCAPE_GATHER_RADIUS = 10.0;
   private static final float ATTACK_ESCAPE_MIN_HEALTH_FACTOR = 0.3F;
   private static final long DEFEATED_EASE_DURATION_TICKS = 300L;
   private static final float DEFEATED_MAX_STEP_MULTIPLIER = 4.0F;
   private static final float PVP_CRITICAL_HEALTH_THRESHOLD = 0.25F;
   private static final float PVP_HEALTHY_DEFENDER_THRESHOLD = 0.75F;
   private static final float PVP_DEFENDER_HEAT_NEUTRAL_THRESHOLD = 0.6F;
   private static final int PVP_MASH_BASE_HITS = 12;
   private static final Map<UUID, NonAttackSystem.AttackOutcomeFailsafeState> ATTACK_OUTCOME_FAILSAFE = new ConcurrentHashMap<>();
   private static final Map<UUID, NonAttackSystem.AttackBlockDebugState> ATTACK_BLOCK_DEBUG = new ConcurrentHashMap<>();

   private NonAttackSystem() {
   }

   static void clearRuntimeState() {
      ATTACK_OUTCOME_FAILSAFE.clear();
      ATTACK_BLOCK_DEBUG.clear();
   }

   static boolean canMobAttackPlayer(ServerPlayerEntity player) {
      if (player == null) {
         return false;
      }

      if (player.isSpectator()) {
         return false;
      }

      if (!player.isCreative()) {
         return true;
      }

      NonConfig config = NeedsOfNature.getConfig();
      return config != null && config.canAttackCreativePlayers();
   }

   static void setAnimationStartProtection(ServerPlayerEntity player, int ticks) {
      if (player != null) {
         AnimationService.setAnimationStartProtection(player, ticks);
      }
   }

   static boolean hasAnimationStartProtection(ServerWorld world, ServerPlayerEntity player) {
      return world != null && player != null ? AnimationService.hasAnimationStartProtection(world, player) : false;
   }

   static boolean isMobAttackFailsafeActive(ServerWorld world, UUID mobUuid) {
      if (world != null && mobUuid != null) {
         NonAttackSystem.AttackOutcomeFailsafeState state = ATTACK_OUTCOME_FAILSAFE.get(mobUuid);
         if (state == null) {
            return false;
         }

         if (!state.worldKey.equals(world.getRegistryKey())) {
            return false;
         }

         long nowTick = world.getTime();
         pruneAttackOutcomeWindow(state, nowTick);
         if (state.cooldownUntilTick <= nowTick) {
            if (state.recentAttackOutcomeTicks.isEmpty()) {
               ATTACK_OUTCOME_FAILSAFE.remove(mobUuid, state);
            }

            return false;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   static int clearAttackOutcomeFailsafeEntries() {
      int count = ATTACK_OUTCOME_FAILSAFE.size();
      ATTACK_OUTCOME_FAILSAFE.clear();
      return count;
   }

   static String getMobAttackFailsafeDebug(ServerWorld world, UUID mobUuid) {
      if (world != null && mobUuid != null) {
         NonAttackSystem.AttackOutcomeFailsafeState state = ATTACK_OUTCOME_FAILSAFE.get(mobUuid);
         if (state != null && state.worldKey.equals(world.getRegistryKey())) {
            long nowTick = world.getTime();
            pruneAttackOutcomeWindow(state, nowTick);
            long remaining = Math.max(0L, state.cooldownUntilTick - nowTick);
            int recent = state.recentAttackOutcomeTicks.size();
            if (remaining <= 0L && recent <= 0) {
               ATTACK_OUTCOME_FAILSAFE.remove(mobUuid, state);
               return null;
            } else {
               return remaining > 0L
                  ? "attack failsafe: " + NeedsOfNature.formatTicksForDebug(remaining) + " remaining; peaks " + recent + "/" + attackOutcomeFailsafeThreshold()
                  : "attack failsafe: peaks " + recent + "/" + attackOutcomeFailsafeThreshold() + " in 10m window";
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   static NeedsOfNature.AttackBlockReason getMobAttackBlockReason(ServerWorld world, MobEntity mob, ServerPlayerEntity player) {
      if (world != null && mob != null && player != null) {
         if (mob.getEntityWorld() != world || player.getEntityWorld() != world) {
            return null;
         } else if (NeedsOfNature.isPregnancyFriendlyMob(mob)) {
            return NeedsOfNature.AttackBlockReason.PREGNANCY_FRIENDLY;
         } else if (!isAggressiveThreat(mob, player)) {
            return null;
         } else {
            boolean mobBusy = NeedsOfNature.isMobGatheringOrActiveWithPlayer(world, mob, player);
            boolean playerBusy = NeedsOfNature.hasActivePlayerAnimation(player);
            if (mobBusy) {
               return NeedsOfNature.AttackBlockReason.GATHERING_PAIR;
            } else {
               return playerBusy && mob instanceof EnergyHolder holder && holder.getEnergy() > 70
                  ? NeedsOfNature.AttackBlockReason.ENERGY_ABOVE_70_PLAYER_BUSY
                  : null;
            }
         }
      } else {
         return null;
      }
   }

   static void onMobAttackBlocked(ServerWorld world, MobEntity mob, ServerPlayerEntity player, NeedsOfNature.AttackBlockReason reason) {
      if (world != null && mob != null && player != null && reason != null) {
         if (reason == NeedsOfNature.AttackBlockReason.PREGNANCY_FRIENDLY) {
            if (mob.getTarget() == player) {
               mob.setTarget(null);
            }

            mob.getNavigation().stop();
            if (mob instanceof Angerable angerable) {
               LazyEntityReference<LivingEntity> angryAt = angerable.getAngryAt();
               UUID angryAtUuid = angryAt == null ? null : angryAt.getUuid();
               if (angryAtUuid == null || angryAtUuid.equals(player.getUuid())) {
                  angerable.stopAnger();
               }
            }
         }

         long now = world.getTime();
         RegistryKey<World> worldKey = world.getRegistryKey();
         UUID mobUuid = mob.getUuid();
         NonAttackSystem.AttackBlockDebugState current = ATTACK_BLOCK_DEBUG.get(mobUuid);
         if (current == null || !current.worldKey.equals(worldKey) || now >= current.nextTick) {
            ATTACK_BLOCK_DEBUG.put(mobUuid, new NonAttackSystem.AttackBlockDebugState(worldKey, now + 400L));
            Text mobName = Text.translatable(mob.getType().getTranslationKey());
            Text playerName = player.getName();

            Text message = switch (reason) {
               case PREGNANCY_FRIENDLY -> Text.translatable("debug.needsofnature.attack_blocked_pregnancy_friendly", new Object[]{mobName, playerName});
               case GATHERING_PAIR -> Text.translatable("debug.needsofnature.attack_blocked_gathering_pair", new Object[]{mobName, playerName});
               case ENERGY_ABOVE_70_PLAYER_BUSY -> Text.translatable("debug.needsofnature.attack_blocked_energy_busy", new Object[]{mobName, playerName});
            };
            NeedsOfNature.sendDebugChatToNearby(world, mob, 24.0, NonDebugChatCategory.WARNING, message);
         }
      }
   }

   private static boolean isAggressiveThreat(MobEntity mob, ServerPlayerEntity player) {
      if (mob == null || player == null) {
         return false;
      }

      if (mob instanceof Monster) {
         return true;
      }

      if (mob instanceof Angerable angerable) {
         LazyEntityReference<LivingEntity> angryAt = angerable.getAngryAt();
         UUID angryAtUuid = angryAt == null ? null : angryAt.getUuid();
         if (angryAtUuid != null && angryAtUuid.equals(player.getUuid())) {
            return true;
         }
      }

      LivingEntity target = mob.getTarget();
      return target != null && target.getUuid().equals(player.getUuid());
   }

   static boolean hasPlayerAndHostileActor(List<? extends Entity> actors) {
      if (actors != null && !actors.isEmpty()) {
         boolean hasPlayer = false;
         boolean hasHostile = false;

         for (Entity actor : actors) {
            if (actor instanceof ServerPlayerEntity) {
               hasPlayer = true;
            } else if (isHostileOrAngryNeutral(actor)) {
               hasHostile = true;
            }

            if (hasPlayer && hasHostile) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean isHostileOrAngryNeutral(Entity entity) {
      if (!(entity instanceof LivingEntity)) {
         return false;
      } else if (entity instanceof Monster) {
         return true;
      } else {
         return entity instanceof Angerable angerable && entity instanceof MobEntity mob
            ? angerable.getAngryAt() != null || mob.getTarget() instanceof ServerPlayerEntity
            : false;
      }
   }

   static void recordAttackOutcomeForMobs(ServerWorld world, List<UUID> actorUuids, String reason) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         RegistryKey<World> worldKey = world.getRegistryKey();
         long nowTick = world.getTime();

         for (UUID actorUuid : actorUuids) {
            if (world.getEntity(actorUuid) instanceof MobEntity mob && mob.isAlive() && !mob.isRemoved()) {
               NonAttackSystem.AttackOutcomeFailsafeState state = ATTACK_OUTCOME_FAILSAFE.computeIfAbsent(
                  actorUuid, unused -> new NonAttackSystem.AttackOutcomeFailsafeState(worldKey)
               );
               if (!state.worldKey.equals(worldKey)) {
                  state.worldKey = worldKey;
                  state.recentAttackOutcomeTicks.clear();
                  state.cooldownUntilTick = 0L;
               }

               if (state.cooldownUntilTick <= nowTick) {
                  pruneAttackOutcomeWindow(state, nowTick);
                  state.recentAttackOutcomeTicks.addLast(nowTick);
                  if (state.recentAttackOutcomeTicks.size() >= attackOutcomeFailsafeThreshold()) {
                     state.recentAttackOutcomeTicks.clear();
                     state.cooldownUntilTick = nowTick + 12000L;
                     NeedsOfNature.logRuntime(
                        "[NoN] Attack peak failsafe armed for mob={} in world={} reason={} untilTick={}",
                        mob.getUuid(),
                        worldKey.getValue(),
                        reason,
                        state.cooldownUntilTick
                     );
                  }
               }
            }
         }
      }
   }

   static void startNearbyPostEscapeGathers(ServerWorld world, ServerPlayerEntity player, @Nullable List<UUID> escapedActorUuids) {
      if (world != null && player != null && player.getEntityWorld() == world) {
         if (canMobAttackPlayer(player)) {
            if (player.isAlive() && !player.isRemoved()) {
               if (!NonGatherSystem.isAutomaticMobPlayerStartBlockedByMount(player)) {
                  int maxMobs = NeedsOfNature.getConfig().getPostEscapeGatherMaxMobs();
                  if (maxMobs > 0) {
                     Box searchBox = player.getBoundingBox().expand(10.0);
                     List<MobEntity> candidates = world.getEntitiesByClass(
                        MobEntity.class, searchBox, mobx -> isPostEscapeGatherCandidate(world, player, mobx, escapedActorUuids)
                     );
                     if (!candidates.isEmpty()) {
                        candidates.sort(Comparator.comparingDouble(player::squaredDistanceTo));
                        int started = 0;

                        for (MobEntity mob : candidates) {
                           if (started >= maxMobs) {
                              break;
                           }

                           List<Entity> actors = new ArrayList<>(2);
                           actors.add(mob);
                           actors.add(player);
                           actors.sort(Comparator.comparingInt(Entity::getId));
                           NonGatherSystem.GatherCandidate gatherCandidate = NonGatherSystem.findGatherCandidate(world, actors, true, mob, player);
                           if (gatherCandidate != null) {
                              boolean gatherStarted = NonGatherSystem.startMobPlayerGather(
                                 world, mob, player, gatherCandidate.animationId(), gatherCandidate.actorKeys(), gatherCandidate.stages()
                              );
                              if (gatherStarted) {
                                 started++;
                                 NeedsOfNature.logRuntime(
                                    "Post-escape gather started: mobType={} player={} anim={} energy={}",
                                    Registries.ENTITY_TYPE.getId(mob.getType()),
                                    player.getName().getString(),
                                    gatherCandidate.animationId(),
                                    ((EnergyHolder)mob).getEnergy()
                                 );
                              }
                           }
                        }

                        if (started > 0) {
                           NeedsOfNature.sendDebugChat(
                              player, NonDebugChatCategory.INFO, Text.translatable("debug.needsofnature.post_escape_gather_started", new Object[]{started})
                           );
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean isPostEscapeGatherCandidate(ServerWorld world, ServerPlayerEntity player, MobEntity mob, @Nullable List<UUID> escapedActorUuids) {
      if (mob == null || mob.getEntityWorld() != world) {
         return false;
      }

      if (!mob.isAlive() || mob.isRemoved()) {
         return false;
      }

      if (!NonMobGatherEligibility.canInitiateAutoGather(mob)) {
         return false;
      }

      if (mob instanceof EnergyHolder holder) {
         if (holder.getEnergy() < 70) {
            return false;
         } else if (NonMobEnergySystem.isEnergyAttackStabilized(mob) && holder.getEnergy() >= 200) {
            return false;
         } else if (!NonMobEnergySystem.hasGenericAnimationSupport(mob.getType(), mob.isBaby())) {
            return false;
         } else if (NeedsOfNature.isActorPendingOrActive(world, mob.getUuid())) {
            return false;
         } else if (NeedsOfNature.isMobAttackFailsafeActive(world, mob.getUuid())) {
            return false;
         } else if (!mob.canSee(player)) {
            return false;
         } else {
            return escapedActorUuids != null && escapedActorUuids.contains(mob.getUuid()) ? true : mob.squaredDistanceTo(player) <= 100.0;
         }
      } else {
         return false;
      }
   }

   private static int attackOutcomeFailsafeThreshold() {
      NonConfig config = NeedsOfNature.getConfig();
      return config == null ? 3 : config.getAttackOutcomeFailsafeThreshold();
   }

   private static void pruneAttackOutcomeWindow(NonAttackSystem.AttackOutcomeFailsafeState state, long nowTick) {
      if (state != null) {
         long oldestAllowedTick = nowTick - 12000L;

         while (!state.recentAttackOutcomeTicks.isEmpty() && state.recentAttackOutcomeTicks.peekFirst() <= oldestAllowedTick) {
            state.recentAttackOutcomeTicks.removeFirst();
         }
      }
   }

   static void pruneAttackOutcomeFailsafe(ServerWorld world, long nowTick) {
      if (world != null && !ATTACK_OUTCOME_FAILSAFE.isEmpty()) {
         RegistryKey<World> worldKey = world.getRegistryKey();
         ATTACK_OUTCOME_FAILSAFE.entrySet().removeIf(entry -> {
            NonAttackSystem.AttackOutcomeFailsafeState state = entry.getValue();
            if (!state.worldKey.equals(worldKey)) {
               return false;
            }

            pruneAttackOutcomeWindow(state, nowTick);
            return state.cooldownUntilTick <= nowTick && state.recentAttackOutcomeTicks.isEmpty();
         });
      }
   }

   static void pruneAttackBlockDebug(ServerWorld world, long nowTick) {
      if (world != null && !ATTACK_BLOCK_DEBUG.isEmpty()) {
         RegistryKey<World> worldKey = world.getRegistryKey();
         ATTACK_BLOCK_DEBUG.entrySet().removeIf(entry -> {
            NonAttackSystem.AttackBlockDebugState state = entry.getValue();
            return !state.worldKey.equals(worldKey) ? false : state.nextTick <= nowTick;
         });
      }
   }

   static void applyAttackEscapeDamage(ServerWorld world, ServerPlayerEntity player, @Nullable List<UUID> actorUuids) {
      applyAttackEscapeDamage(world, player, actorUuids, null);
   }

   static void applyAttackEscapeDamage(ServerWorld world, ServerPlayerEntity player, @Nullable List<UUID> actorUuids, @Nullable UUID preferredAttackerUuid) {
      if (world != null && player != null) {
         if (player.isAlive()) {
            float health = player.getHealth();
            if (!(health <= 1.0F)) {
               float maxRaw = health - 1.0F;
               if (!(maxRaw <= 0.0F)) {
                  LivingEntity attacker = resolveAttackEscapeAttacker(world, player, actorUuids, preferredAttackerUuid);
                  float rawDamage = resolveAttackEscapeRawDamage(attacker);
                  rawDamage *= (float)NonTrinketsIntegration.getAccessoryEffects(player).attackEscapeDamageMultiplier();
                  rawDamage = Math.min(rawDamage, maxRaw);
                  if (!(rawDamage <= 0.0F)) {
                     if (attacker instanceof ServerPlayerEntity attackingPlayer) {
                        NonPlayerAttackSystem.applyPlayerAttributedEscapeDamage(world, player, attackingPlayer, rawDamage);
                     } else {
                        DamageSource source = resolveAttackEscapeDamageSource(player, attacker);
                        player.damage(world, source, rawDamage);
                     }

                     float after = player.getHealth();
                     float dealt = health - after;
                     if (dealt < 1.0F && after > 1.0F) {
                        float extra = Math.min(1.0F - dealt, after - 1.0F);
                        if (extra > 0.0F) {
                           player.setHealth(after - extra);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Nullable
   private static LivingEntity resolveAttackEscapeAttacker(
      ServerWorld world, ServerPlayerEntity player, @Nullable List<UUID> actorUuids, @Nullable UUID preferredAttackerUuid
   ) {
      if (world != null && player != null) {
         if (preferredAttackerUuid != null && !preferredAttackerUuid.equals(player.getUuid())) {
            Entity preferred = world.getEntity(preferredAttackerUuid);
            if (preferred == null) {
               preferred = world.getServer().getPlayerManager().getPlayer(preferredAttackerUuid);
            }

            if (preferred instanceof LivingEntity living && living.isAlive() && !living.isRemoved()) {
               return living;
            }
         }

         if (actorUuids != null && !actorUuids.isEmpty()) {
            for (UUID actorUuid : actorUuids) {
               if (actorUuid != null
                  && !actorUuid.equals(player.getUuid())
                  && world.getEntity(actorUuid) instanceof LivingEntity living
                  && living.isAlive()
                  && !living.isRemoved()) {
                  return living;
               }
            }

            return null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static float resolveAttackEscapeRawDamage(@Nullable LivingEntity attacker) {
      float baseDamage = 4.0F;
      if (attacker != null) {
         EntityAttributeInstance attackDamage = attacker.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
         if (attackDamage != null) {
            double attributeDamage = attackDamage.getValue();
            if (Double.isFinite(attributeDamage) && attributeDamage > 0.0) {
               baseDamage = (float)attributeDamage;
            }
         }
      }

      if (!Float.isFinite(baseDamage)) {
         baseDamage = 4.0F;
      }

      return Math.max(1.0F, baseDamage);
   }

   private static DamageSource resolveAttackEscapeDamageSource(ServerPlayerEntity player, @Nullable LivingEntity attacker) {
      if (player == null) {
         throw new IllegalArgumentException("player");
      } else {
         return attacker instanceof MobEntity mob ? player.getDamageSources().mobAttack(mob) : player.getDamageSources().generic();
      }
   }

   static float resolveServerAttackEscapeStep(ServerWorld world, ServerPlayerEntity victim, boolean defeated, long contestStartTick) {
      if (world != null && victim != null) {
         float base = 1.0F / resolveAttackEscapeHits(victim);
         float healthFactor = resolveAttackHealthFactor(victim);
         float energyFactor = resolveAttackEnergyFactor(victim);
         float randomScale = healthFactor >= 0.999F ? 1.0F : 0.5F + world.getRandom().nextFloat();
         if (!defeated) {
            return Math.max(0.0F, base * healthFactor * energyFactor * randomScale);
         }

         float ease = MathHelper.clamp((float)Math.max(0L, world.getTime() - contestStartTick) / 300.0F, 0.0F, 1.0F);
         float adjustedHealth = MathHelper.lerp(ease, healthFactor, 1.0F);
         float adjustedEnergy = MathHelper.lerp(ease, energyFactor, 1.0F);
         float adjustedRandom = MathHelper.lerp(ease, randomScale, 1.0F);
         float multiplier = MathHelper.lerp(ease, 1.0F, 4.0F);
         return Math.max(0.0F, base * adjustedHealth * adjustedEnergy * adjustedRandom * multiplier);
      } else {
         return 0.0F;
      }
   }

   static NonAttackSystem.PvpMashFactors resolveServerPlayerContestStep(
      ServerWorld world, ServerPlayerEntity participant, boolean defender, boolean defeated, long contestStartTick
   ) {
      if (world != null && participant != null) {
         float base = 0.083333336F;
         float healthMultiplier = resolvePvpCriticalHealthMultiplier(participant);
         float heatMultiplier = resolvePvpHeatMultiplier(participant, defender);
         float healthyDefenderMultiplier = defender ? resolvePvpHealthyDefenderMultiplier(participant) : 1.0F;
         float defeatedEaseMultiplier = 1.0F;
         if (defender && defeated) {
            float ease = MathHelper.clamp((float)Math.max(0L, world.getTime() - contestStartTick) / 300.0F, 0.0F, 1.0F);
            healthMultiplier = MathHelper.lerp(ease, healthMultiplier, 1.0F);
            heatMultiplier = MathHelper.lerp(ease, heatMultiplier, 1.0F);
            healthyDefenderMultiplier = MathHelper.lerp(ease, healthyDefenderMultiplier, 1.0F);
            defeatedEaseMultiplier = MathHelper.lerp(ease, 1.0F, 4.0F);
         }

         float step = base * healthMultiplier * heatMultiplier * healthyDefenderMultiplier * defeatedEaseMultiplier;
         return new NonAttackSystem.PvpMashFactors(Math.max(0.0F, step), healthMultiplier, heatMultiplier, healthyDefenderMultiplier, defeatedEaseMultiplier);
      } else {
         return NonAttackSystem.PvpMashFactors.none();
      }
   }

   private static int resolveAttackEscapeHits(ServerPlayerEntity victim) {
      int hits = NeedsOfNature.getConfig().getAttackEscapeHits() + NonTrinketsIntegration.getAccessoryEffects(victim).attackEscapeHitsAdd();
      return Math.max(1, Math.min(50, hits));
   }

   private static float resolvePvpCriticalHealthMultiplier(ServerPlayerEntity player) {
      float healthRatio = resolveHealthRatio(player);
      if (healthRatio >= 0.25F) {
         return 1.0F;
      }

      float criticalStrength = NeedsOfNature.getConfig().getPvpCriticalHealthMashStrengthPercent() / 100.0F;
      float progress = MathHelper.clamp(healthRatio / 0.25F, 0.0F, 1.0F);
      return MathHelper.lerp(progress, criticalStrength, 1.0F);
   }

   private static float resolvePvpHealthyDefenderMultiplier(ServerPlayerEntity player) {
      float healthRatio = resolveHealthRatio(player);
      if (healthRatio <= 0.75F) {
         return 1.0F;
      }

      float progress = MathHelper.clamp((healthRatio - 0.75F) / 0.25F, 0.0F, 1.0F);
      float fullHealthMultiplier = 1.0F + NeedsOfNature.getConfig().getPvpFullHealthEscapeBonusPercent() / 100.0F;
      return MathHelper.lerp(progress, 1.0F, fullHealthMultiplier);
   }

   private static float resolvePvpHeatMultiplier(ServerPlayerEntity player, boolean defender) {
      float energyRatio = resolveEnergyRatio(player);
      NonConfig config = NeedsOfNature.getConfig();
      if (!defender) {
         float zeroHeatStrength = config.getPvpZeroHeatAttackStrengthPercent() / 100.0F;
         return MathHelper.lerp(energyRatio, zeroHeatStrength, 1.0F);
      }

      if (energyRatio <= 0.6F) {
         return 1.0F;
      }

      float progress = MathHelper.clamp((energyRatio - 0.6F) / 0.39999998F, 0.0F, 1.0F);
      float maxHeatStrength = config.getPvpMaxHeatEscapeStrengthPercent() / 100.0F;
      return MathHelper.lerp(progress, 1.0F, maxHeatStrength);
   }

   private static float resolveHealthRatio(ServerPlayerEntity player) {
      float maxHealth = player.getMaxHealth();
      return Float.isFinite(maxHealth) && !(maxHealth <= 0.0F) ? MathHelper.clamp(player.getHealth() / maxHealth, 0.0F, 1.0F) : 0.0F;
   }

   private static float resolveEnergyRatio(ServerPlayerEntity player) {
      if (player instanceof EnergyHolder holder) {
         int maxEnergy = Math.max(1, holder.getMaxEnergy());
         return MathHelper.clamp((float)holder.getEnergy() / maxEnergy, 0.0F, 1.0F);
      } else {
         return 1.0F;
      }
   }

   private static float resolveAttackHealthFactor(ServerPlayerEntity victim) {
      float maxHealth = victim.getMaxHealth();
      float minHealth = 0.5F;
      if (maxHealth <= minHealth) {
         return 0.3F;
      }

      float raw = MathHelper.clamp((victim.getHealth() - minHealth) / (maxHealth - minHealth), 0.0F, 1.0F);
      float impact = NeedsOfNature.getConfig().getAttackEscapeDamageDifficultyPercent() / 100.0F;
      return MathHelper.clamp(1.0F - (1.0F - raw) * impact, 0.3F, 1.0F);
   }

   private static float resolveAttackEnergyFactor(ServerPlayerEntity victim) {
      if (victim instanceof EnergyHolder holder) {
         int max = Math.max(1, holder.getMaxEnergy());
         float energy = Math.max(0.0F, Math.min(max, holder.getEnergy()));
         if (energy <= 100.0F) {
            return 1.0F - 0.1F * energy / 100.0F;
         }

         float t = Math.min(1.0F, (energy - 100.0F) / 100.0F);
         return Math.max(0.08F, 0.9F - 0.82F * t * t);
      } else {
         return 1.0F;
      }
   }

   private record AttackBlockDebugState(RegistryKey<World> worldKey, long nextTick) {
   }

   private static final class AttackOutcomeFailsafeState {
      RegistryKey<World> worldKey;
      final ArrayDeque<Long> recentAttackOutcomeTicks = new ArrayDeque<>();
      long cooldownUntilTick = 0L;

      AttackOutcomeFailsafeState(RegistryKey<World> worldKey) {
         this.worldKey = worldKey;
      }
   }

   record PvpMashFactors(float step, float healthMultiplier, float heatMultiplier, float healthyDefenderMultiplier, float defeatedEaseMultiplier) {
      private static NonAttackSystem.PvpMashFactors none() {
         return new NonAttackSystem.PvpMashFactors(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
      }
   }
}
