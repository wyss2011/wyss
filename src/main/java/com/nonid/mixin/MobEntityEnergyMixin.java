package com.nonid.mixin;

import com.nonid.EnergyHolder;
import com.nonid.NeedsOfNature;
import com.nonid.NonAnimationMatcher;
import com.nonid.NonDebugChatCategory;
import com.nonid.NonGatherSystem;
import com.nonid.NonMobEnergySystem;
import com.nonid.NonMobGatherEligibility;
import com.nonid.NonPlayerAvoidanceState;
import com.nonid.data.NonEntityProfiles;
import com.nonid.internal.animation.api.AnimationService;
import com.nonid.internal.animation.data.AnimationDefinitions;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.particle.NonParticles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.data.DataTracker.Builder;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class MobEntityEnergyMixin extends LivingEntity implements EnergyHolder, NonPlayerAvoidanceState {
   @Unique
   private static final int PLAYER_AVOIDANCE_ELIGIBILITY_CACHE_TICKS = 20;
   @Unique
   private static final int LINGER_MATCH_CACHE_TICKS = 10;
   @Unique
   private static final TrackedData<Integer> ENERGY = DataTracker.registerData(MobEntity.class, TrackedDataHandlerRegistry.INTEGER);
   @Unique
   private int non$energyTickCounter;
   @Unique
   private boolean non$energyInitialized;
   @Unique
   private float non$energyGainMult = -1.0F;
   @Unique
   private float non$energyGainDrift = -1.0F;
   @Unique
   private double non$energyAuraCarry;
   @Unique
   private int non$nearAnimCheckCooldown;
   @Unique
   private boolean non$nearActiveAnimation;
   @Unique
   private double non$nearActiveAnimationGainMultiplier = 1.0;
   @Unique
   @Nullable
   private UUID non$lingerLookTargetUuid;
   @Unique
   private int non$lingerMatchCooldown;
   @Unique
   private int non$heartParticleCooldown;
   @Unique
   private int non$playerSearchCooldown;
   @Unique
   private int non$mobSearchCooldown;
   @Unique
   private long non$lastEnergyProcessedTick = Long.MIN_VALUE;
   @Unique
   private boolean non$attackFailsafeWarned;
   @Unique
   private final Map<UUID, Long> non$partnerCooldowns = new HashMap<>();
   @Unique
   private final Map<UUID, Long> non$bypassablePartnerCooldowns = new HashMap<>();
   @Unique
   private final Map<UUID, Long> non$multiActorJoinCooldowns = new HashMap<>();
   @Unique
   @Nullable
   private UUID non$avoidancePlayerUuid;
   @Unique
   @Nullable
   private UUID non$avoidanceInstanceUuid;
   @Unique
   private long non$avoidanceEligibilityExpiresTick = Long.MIN_VALUE;
   @Unique
   private boolean non$avoidanceEligible;

   protected MobEntityEnergyMixin(EntityType<? extends LivingEntity> entityType, World world) {
      super(entityType, world);
   }

   @Inject(method = "initDataTracker", at = @At("TAIL"))
   private void needsOfNature$initEnergyTracker(Builder builder, CallbackInfo ci) {
      builder.add(ENERGY, 0);
   }

   @Inject(method = "tick", at = @At("TAIL"))
   private void needsOfNature$tickEnergy(CallbackInfo ci) {
      if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
         long worldTick = serverWorld.getTime();
         if (this.non$lastEnergyProcessedTick != worldTick) {
            this.non$lastEnergyProcessedTick = worldTick;
            this.non$ensureEnergyInitialized(serverWorld);
            boolean non$hasAnimationSupport = NonMobEnergySystem.hasGenericAnimationSupport(this.getType(), this.isBaby());
            boolean attackFailsafeActive = NeedsOfNature.isMobAttackFailsafeActive(serverWorld, this.getUuid());
            if (attackFailsafeActive) {
               this.non$lingerLookTargetUuid = null;
               if (!this.non$attackFailsafeWarned) {
                  NeedsOfNature.sendDebugChatToNearby(
                     serverWorld,
                     this,
                     20.0,
                     NonDebugChatCategory.WARNING,
                     Text.translatable("debug.needsofnature.energy_locked_attack_cooldown", new Object[]{Text.translatable(this.getType().getTranslationKey())})
                  );
                  this.non$attackFailsafeWarned = true;
               }

               if (this.getEnergy() != 0) {
                  this.setEnergy(0);
               }

               this.non$energyTickCounter = 0;
               this.non$heartParticleCooldown = 0;
               this.non$playerSearchCooldown = 40;
               this.non$mobSearchCooldown = 40;
            } else {
               this.non$attackFailsafeWarned = false;
               if (!non$hasAnimationSupport) {
                  this.non$lingerLookTargetUuid = null;
                  this.non$nearActiveAnimation = false;
                  this.non$nearActiveAnimationGainMultiplier = 1.0;
                  if (this.getEnergy() >= 200) {
                     this.setEnergy(199);
                  }

                  this.non$energyTickCounter = 0;
                  this.non$heartParticleCooldown = 0;
               } else if (this.non$nearAnimCheckCooldown-- <= 0) {
                  this.non$nearActiveAnimationGainMultiplier = NeedsOfNature.getNearbyVisibleAnimationGainMultiplier(serverWorld, (MobEntity)(Object)this);
                  this.non$nearActiveAnimation = this.non$nearActiveAnimationGainMultiplier > 1.0;
                  this.non$nearAnimCheckCooldown = 20;
               }

               if (non$hasAnimationSupport) {
                  int energy = this.getEnergy();
                  if (energy >= 200 && NonMobEnergySystem.isEnergyAttackStabilized(this)) {
                     this.setEnergy(199);
                     energy = this.getEnergy();
                  }

                  int ticksPerGain = NonMobEnergySystem.ticksPerGain(
                     (MobEntity)(Object)this,
                     this.getEnergyGainMultiplier(),
                     this.getEnergyGainDrift(),
                     this.non$nearActiveAnimation,
                     this.non$nearActiveAnimationGainMultiplier
                  );
                  if (energy < 200) {
                     this.non$energyTickCounter++;
                     if (this.non$energyTickCounter >= ticksPerGain) {
                        this.non$energyTickCounter = 0;
                        this.setEnergy(Math.min(200, energy + 1));
                     }

                     this.non$heartParticleCooldown = 0;
                  } else {
                     this.non$energyTickCounter = 0;
                     this.non$spawnMaxEnergyHeartParticle(serverWorld);
                  }

                  this.non$maybeStartEnergyAnimation(serverWorld, energy);
               }
            }
         }
      }
   }

   @Inject(method = "tickNewAi", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ai/control/LookControl;tick()V"))
   private void needsOfNature$applyLingerLookTarget(CallbackInfo ci) {
      UUID targetUuid = this.non$lingerLookTargetUuid;
      if (targetUuid != null) {
         if (this.getEntityWorld() instanceof ServerWorld world) {
            MobEntity self = (MobEntity)(Object)this;
            if (NonEntityProfiles.resolve(self).lookAtNearbyAnimations()) {
               if (world.getEntity(targetUuid) instanceof ServerPlayerEntity player
                  && player.isAlive()
                  && !player.isRemoved()
                  && !player.isSpectator()
                  && !player.isCreative()
                  && NeedsOfNature.getActivePlayerInstance(player) != null
                  && !NeedsOfNature.isActorPendingOrActive(world, self.getUuid())) {
                  double lookY = Math.max(player.getY() + 0.6, player.getEyeY() - 2.0);
                  self.getLookControl().lookAt(player.getX(), lookY, player.getZ(), 30.0F, 30.0F);
               } else {
                  this.non$lingerLookTargetUuid = null;
               }
            }
         } else {
            this.non$lingerLookTargetUuid = null;
         }
      }
   }

   @Inject(method = "initialize", at = @At("TAIL"))
   private void needsOfNature$initEnergyOnSpawn(
      ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, CallbackInfoReturnable<EntityData> cir
   ) {
      if (world instanceof ServerWorld serverWorld) {
         this.non$ensureEnergyInitialized(serverWorld);
      }
   }

   @Override
   public int getEnergy() {
      return (Integer)this.dataTracker.get(ENERGY);
   }

   @Override
   public void setEnergy(int value) {
      int maxEnergy = NonMobEnergySystem.isEnergyAttackStabilized(this) ? 199 : 200;
      this.dataTracker.set(ENERGY, Math.min(maxEnergy, Math.max(0, value)));
   }

   @Override
   public float getEnergyGainMultiplier() {
      return this.non$energyGainMult <= 0.0F ? 1.0F : this.non$energyGainMult;
   }

   @Override
   public void setEnergyGainMultiplier(float value) {
      if (Float.isFinite(value)) {
         this.non$energyGainMult = Math.max(0.4F, Math.min(6.0F, value));
      }
   }

   @Override
   public float getEnergyGainDrift() {
      return this.non$energyGainDrift <= 0.0F ? 1.0F : this.non$energyGainDrift;
   }

   @Override
   public void setEnergyGainDrift(float value) {
      if (Float.isFinite(value)) {
         this.non$energyGainDrift = Math.max(0.8F, Math.min(1.2F, value));
      }
   }

   @Override
   public double getEnergyAuraCarry() {
      return this.non$energyAuraCarry > 0.0 ? this.non$energyAuraCarry : 0.0;
   }

   @Override
   public void setEnergyAuraCarry(double value) {
      if (!Double.isFinite(value)) {
         this.non$energyAuraCarry = 0.0;
      } else {
         this.non$energyAuraCarry = Math.max(0.0, value);
      }
   }

   @Override
   public boolean isEnergyInitialized() {
      return this.non$energyInitialized;
   }

   @Override
   public void markEnergyInitialized() {
      this.non$energyInitialized = true;
   }

   @Override
   public void ensureEnergyInitialized(ServerWorld world) {
      if (world != null) {
         this.non$ensureEnergyInitialized(world);
      }
   }

   @Override
   public int getMaxEnergy() {
      return 200;
   }

   @Override
   public void onNonAnimationStarted() {
      this.non$partnerCooldowns.clear();
      this.non$bypassablePartnerCooldowns.clear();
      this.non$multiActorJoinCooldowns.clear();
   }

   @Override
   public void applyPartnerCooldown(UUID partnerId, long nowTick, int cooldownTicks) {
      if (partnerId != null) {
         if (cooldownTicks > 0) {
            long until = nowTick + cooldownTicks;
            this.non$partnerCooldowns.put(partnerId, until);
            this.non$bypassablePartnerCooldowns.put(partnerId, until);
         }
      }
   }

   @Override
   public List<String> getActiveCooldownDebugLines(ServerWorld world) {
      List<String> lines = new ArrayList<>();
      if (world == null) {
         return lines;
      }

      long nowTick = world.getTime();
      this.non$pruneCooldownMaps(nowTick);
      if (this.non$playerSearchCooldown > 0) {
         lines.add("player search delay: " + NeedsOfNature.formatTicksForDebug(this.non$playerSearchCooldown));
      }

      if (this.non$mobSearchCooldown > 0) {
         lines.add("mob search delay: " + NeedsOfNature.formatTicksForDebug(this.non$mobSearchCooldown));
      }

      NonMobEnergySystem.appendCooldownMapDebug(lines, world, nowTick, "partner", this.non$partnerCooldowns, this.non$bypassablePartnerCooldowns, true);
      NonMobEnergySystem.appendCooldownMapDebug(lines, world, nowTick, "3+ join", this.non$multiActorJoinCooldowns, this.non$bypassablePartnerCooldowns, false);
      return lines;
   }

   @Override
   public void requestAttackOnPlayer(ServerWorld world, ServerPlayerEntity player) {
      if (world != null && player != null) {
         if (this.getEntityWorld() == world) {
            if (!NonMobEnergySystem.isEnergyAttackStabilized(this) || this.getEnergy() < 200) {
               if (NonMobEnergySystem.hasGenericAnimationSupport(this.getType(), this.isBaby())) {
                  if (NonMobGatherEligibility.canInitiateAutoGather((MobEntity)(Object)this)) {
                     if (NeedsOfNature.canMobAttackPlayer(player)) {
                        if (!NeedsOfNature.isActorPendingOrActive(world, this.getUuid())) {
                           if (!NeedsOfNature.isMobAttackFailsafeActive(world, this.getUuid())) {
                              this.non$playerSearchCooldown = 0;
                              this.non$partnerCooldowns.remove(player.getUuid());
                              this.non$bypassablePartnerCooldowns.remove(player.getUuid());
                              this.non$multiActorJoinCooldowns.remove(player.getUuid());
                              this.non$tryPlayerInteraction(world, this.getEnergy(), true, true, player);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public boolean shouldSuppressPlayerAvoidance(ServerPlayerEntity player) {
      if (this.getEntityWorld() instanceof ServerWorld world && player != null) {
         MobEntity self = (MobEntity)(Object)this;
         if (player.getEntityWorld() != world || !player.isAlive() || player.isRemoved() || player.isSpectator()) {
            return false;
         }

         if (NonGatherSystem.isMobGatheringWithPlayer(world, self.getUuid(), player.getUuid())) {
            return true;
         }

         if (this.getEnergy() >= 200
            && NonMobGatherEligibility.canInitiateAutoGather(self)
            && !NeedsOfNature.isActorPendingOrActive(world, self.getUuid())
            && !NeedsOfNature.isMobAttackFailsafeActive(world, self.getUuid())) {
            UUID activeInstance = NeedsOfNature.getActivePlayerInstance(player);
            boolean interactionIsAttack = activeInstance == null || NeedsOfNature.isInstanceAttack(world, activeInstance);
            if (interactionIsAttack && !NeedsOfNature.canMobAttackPlayer(player)) {
               return false;
            }

            if (activeInstance == null && NonGatherSystem.isAutomaticMobPlayerStartBlockedByMount(player)) {
               return false;
            }

            long nowTick = world.getTime();
            if (player.getUuid().equals(this.non$avoidancePlayerUuid)
               && Objects.equals(activeInstance, this.non$avoidanceInstanceUuid)
               && nowTick < this.non$avoidanceEligibilityExpiresTick) {
               return this.non$avoidanceEligible;
            }

            this.non$avoidancePlayerUuid = player.getUuid();
            this.non$avoidanceInstanceUuid = activeInstance;
            this.non$avoidanceEligibilityExpiresTick = nowTick + 20L;
            this.non$avoidanceEligible = this.non$hasEligiblePlayerInteraction(world, self, player, activeInstance);
            return this.non$avoidanceEligible;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Unique
   private boolean non$hasEligiblePlayerInteraction(ServerWorld world, MobEntity self, ServerPlayerEntity player, @Nullable UUID activeInstance) {
      List<Entity> actors;
      Entity requiredActiveActor;
      Identifier predecessorAnimationId;
      boolean attackSelection;
      if (activeInstance != null) {
         AnimationStageInfo stage = AnimationService.getCurrentStage(world, activeInstance);
         if (stage != null && !stage.allowJoin()) {
            return false;
         }

         predecessorAnimationId = AnimationService.getActiveAnimationId(world, activeInstance);
         if (predecessorAnimationId == null) {
            return false;
         }

         actors = this.non$resolveJoinActors(world, activeInstance);
         attackSelection = NeedsOfNature.isInstanceAttack(world, activeInstance);
         requiredActiveActor = attackSelection ? self : null;
      } else {
         if (AnimationService.isActorPendingOrActive(world, player.getUuid())) {
            return false;
         }

         actors = new ArrayList<>(2);
         actors.add(self);
         actors.add(player);
         actors.sort(Comparator.comparingInt(Entity::getId));
         requiredActiveActor = self;
         predecessorAnimationId = null;
         attackSelection = true;
      }

      List<AnimationService.EligibleAnimation> eligible = attackSelection
         ? NonAnimationMatcher.findEligibleAutomaticAttack(world, actors, Set.of(), player, true, requiredActiveActor, Set.of(), predecessorAnimationId)
         : NonAnimationMatcher.findEligibleAutomatic(world, actors, Set.of(), player, true, requiredActiveActor, Set.of(), predecessorAnimationId);
      return !eligible.isEmpty();
   }

   @Unique
   private void non$maybeStartEnergyAnimation(ServerWorld world, int currentEnergy) {
      if (!NonMobEnergySystem.hasGenericAnimationSupport(this.getType(), this.isBaby())) {
         this.non$lingerLookTargetUuid = null;
      } else {
         MobEntity self = (MobEntity)(Object)this;
         if (!NonMobGatherEligibility.canInitiateAutoGather(self)) {
            this.non$lingerLookTargetUuid = null;
         } else {
            long nowTick = world.getTime();
            this.non$pruneCooldownMaps(nowTick);
            if (NeedsOfNature.isActorPendingOrActive(world, this.getUuid())) {
               this.non$lingerLookTargetUuid = null;
            } else {
               boolean canJoin = currentEnergy >= 70;
               boolean canMobMatch = currentEnergy >= 120 && currentEnergy < 200;
               boolean isAttack = currentEnergy >= 200;
               if (!canJoin) {
                  this.non$lingerLookTargetUuid = null;
               } else {
                  ServerPlayerEntity lingerPlayer = null;
                  lingerPlayer = this.non$findNearestActiveAnimationPlayerForLinger(world);
                  if (lingerPlayer != null) {
                     this.non$applyPlayerAnimationLinger(lingerPlayer);
                  } else {
                     this.non$lingerLookTargetUuid = null;
                  }

                  if (this.non$playerSearchCooldown > 0) {
                     this.non$playerSearchCooldown--;
                  } else {
                     this.non$playerSearchCooldown = 40;
                     if (NeedsOfNature.consumeScanBudget(world) && this.non$tryPlayerInteraction(world, currentEnergy, true, isAttack, null)) {
                        this.non$playerSearchCooldown = 100;
                        return;
                     }
                  }

                  if (this.non$mobSearchCooldown > 0) {
                     this.non$mobSearchCooldown--;
                  } else {
                     this.non$mobSearchCooldown = 40;
                     if (NeedsOfNature.consumeScanBudget(world)) {
                        if (lingerPlayer == null) {
                           if (canMobMatch || isAttack) {
                              Identifier selfTypeId = Registries.ENTITY_TYPE.getId(this.getType());
                              boolean selfIsBaby = this.isBaby();
                              if (AnimationDefinitions.hasEnabledGenericMobPairAnimationForEntityType(selfTypeId, selfIsBaby)) {
                                 Box searchBox = this.getBoundingBox().expand(20.0);
                                 List<MobEntity> candidates = world.getEntitiesByClass(
                                    MobEntity.class,
                                    searchBox,
                                    mob -> !mob.getUuid().equals(this.getUuid())
                                       && NonMobGatherEligibility.canBeAutoGatherTarget(mob)
                                       && mob.isAlive()
                                       && !mob.isRemoved()
                                       && mob.getEntityWorld() == world
                                       && AnimationDefinitions.areEnabledGenericMobPairTypesCompatible(
                                          selfTypeId, selfIsBaby, Registries.ENTITY_TYPE.getId(mob.getType()), mob.isBaby()
                                       )
                                 );
                                 if (!candidates.isEmpty()) {
                                    candidates.sort(Comparator.comparing(candidatex -> !NonMobEnergySystem.bypassesEntityEntityRoll(this, candidatex)));
                                    boolean entityEntityRollChecked = false;
                                    boolean entityEntityRollPassed = false;

                                    for (MobEntity candidate : candidates) {
                                       boolean bypassEntityEntityRoll = NonMobEnergySystem.bypassesEntityEntityRoll(this, candidate);
                                       if (!bypassEntityEntityRoll && !entityEntityRollChecked) {
                                          entityEntityRollChecked = true;
                                          entityEntityRollPassed = NonMobEnergySystem.passesEntityEntityRoll(world, this);
                                          if (!entityEntityRollPassed) {
                                             this.non$mobSearchCooldown = 1200;
                                             return;
                                          }
                                       }

                                       if (candidate instanceof EnergyHolder holder && !NeedsOfNature.isActorPendingOrActive(world, candidate.getUuid())) {
                                          int candidateEnergy = holder.getEnergy();
                                          if (isAttack || candidateEnergy >= 70) {
                                             List<Entity> actors = new ArrayList<>(2);
                                             actors.add(this);
                                             actors.add(candidate);
                                             actors.sort(Comparator.comparingInt(Entity::getId));
                                             if (!this.non$partnerCooldowns.containsKey(candidate.getUuid())) {
                                                NonGatherSystem.GatherCandidate gatherCandidate = NonGatherSystem.findGatherCandidate(
                                                   world, actors, isAttack, (Entity)this, null
                                                );
                                                if (gatherCandidate != null) {
                                                   if (!NonMobEnergySystem.passesSameGenderRoll(world, this, actors)) {
                                                      this.non$applyRollCooldown(candidate.getUuid(), nowTick);
                                                      this.non$mobSearchCooldown = 100;
                                                   } else {
                                                      Text mobName = this.getDisplayName();
                                                      Text partnerName = candidate.getDisplayName();
                                                      NeedsOfNature.sendDebugChatToNearby(
                                                         world,
                                                         this,
                                                         20.0,
                                                         NonDebugChatCategory.INFO,
                                                         Text.translatable(
                                                            "debug.needsofnature.mob_pair_attempt",
                                                            new Object[]{
                                                               mobName, partnerName, currentEnergy, candidateEnergy, gatherCandidate.animationId().toString()
                                                            }
                                                         )
                                                      );
                                                      NonGatherSystem.startMobMobGather(
                                                         world,
                                                         (MobEntity)(Object)this,
                                                         candidate,
                                                         gatherCandidate.animationId(),
                                                         gatherCandidate.actorKeys(),
                                                         gatherCandidate.stages(),
                                                         isAttack
                                                      );
                                                      NeedsOfNature.sendDebugChatToNearby(
                                                         world,
                                                         this,
                                                         20.0,
                                                         NonDebugChatCategory.INFO,
                                                         Text.translatable(
                                                            "debug.needsofnature.mob_pair_gathering",
                                                            new Object[]{mobName, partnerName, gatherCandidate.animationId().toString()}
                                                         )
                                                      );
                                                      this.non$mobSearchCooldown = 100;
                                                   }
                                                   break;
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
               }
            }
         }
      }
   }

   @Unique
   private boolean non$tryPlayerInteraction(ServerWorld world, int currentEnergy, boolean canJoin, boolean isAttack, @Nullable ServerPlayerEntity forcedPlayer) {
      MobEntity self = (MobEntity)(Object)this;
      if (!NonMobGatherEligibility.canInitiateAutoGather(self)) {
         return false;
      }

      long nowTick = world.getTime();
      double maxDistSq = 400.0;
      Text mobName = this.getDisplayName();
      String mobTypeId = Registries.ENTITY_TYPE.getId(this.getType()).toString();
      boolean attackFailsafeActive = isAttack && NeedsOfNature.isMobAttackFailsafeActive(world, this.getUuid());
      if (forcedPlayer == null && world.getPlayers().isEmpty()) {
         return false;
      }

      if (attackFailsafeActive) {
         return false;
      }

      int mobChunkX = (int)Math.floor(this.getX()) >> 4;
      int mobChunkZ = (int)Math.floor(this.getZ()) >> 4;
      boolean playerNearbyChunk = false;
      if (forcedPlayer == null) {
         for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isAlive() && !player.isRemoved()) {
               UUID inst = NeedsOfNature.getActivePlayerInstance(player);
               boolean interactionIsAttack = inst == null ? isAttack : NeedsOfNature.isInstanceAttack(world, inst);
               if (!interactionIsAttack || NeedsOfNature.canMobAttackPlayer(player)) {
                  double distSq = player.squaredDistanceTo(this);
                  boolean bypassCooldown = this.non$shouldBypassPlayerCooldown(player, distSq);
                  if (!this.non$partnerCooldowns.containsKey(player.getUuid()) || bypassCooldown) {
                     if (bypassCooldown) {
                        this.non$partnerCooldowns.remove(player.getUuid());
                        this.non$bypassablePartnerCooldowns.remove(player.getUuid());
                     }

                     int px = (int)Math.floor(player.getX()) >> 4;
                     int pz = (int)Math.floor(player.getZ()) >> 4;
                     if (Math.abs(px - mobChunkX) <= 1 && Math.abs(pz - mobChunkZ) <= 1) {
                        playerNearbyChunk = true;
                        break;
                     }
                  }
               }
            }
         }

         if (!playerNearbyChunk) {
            return false;
         }
      }

      ServerPlayerEntity closest = null;
      double closestDistSq = Double.MAX_VALUE;
      if (forcedPlayer != null) {
         if (!forcedPlayer.isAlive() || forcedPlayer.isRemoved() || forcedPlayer.getEntityWorld() != world) {
            return false;
         }

         UUID inst = NeedsOfNature.getActivePlayerInstance(forcedPlayer);
         boolean interactionIsAttack = inst == null ? isAttack : NeedsOfNature.isInstanceAttack(world, inst);
         if (interactionIsAttack && !NeedsOfNature.canMobAttackPlayer(forcedPlayer)) {
            return false;
         }

         double distSq = forcedPlayer.squaredDistanceTo(this);
         if (distSq > maxDistSq) {
            return false;
         }

         closest = forcedPlayer;
         closestDistSq = distSq;
      } else {
         for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isAlive() && !player.isRemoved()) {
               UUID inst = NeedsOfNature.getActivePlayerInstance(player);
               boolean interactionIsAttack = inst == null ? isAttack : NeedsOfNature.isInstanceAttack(world, inst);
               if (!interactionIsAttack || NeedsOfNature.canMobAttackPlayer(player)) {
                  double distSq = player.squaredDistanceTo(this);
                  boolean bypassCooldown = this.non$shouldBypassPlayerCooldown(player, distSq);
                  if (!this.non$partnerCooldowns.containsKey(player.getUuid()) || bypassCooldown) {
                     if (bypassCooldown) {
                        this.non$partnerCooldowns.remove(player.getUuid());
                        this.non$bypassablePartnerCooldowns.remove(player.getUuid());
                     }

                     if (!(distSq > maxDistSq) && (inst != null || isAttack) && distSq < closestDistSq) {
                        closestDistSq = distSq;
                        closest = player;
                     }
                  }
               }
            }
         }
      }

      if (closest == null) {
         return false;
      }

      UUID activeInstance = NeedsOfNature.getActivePlayerInstance(closest);
      AnimationStageInfo activeStage = activeInstance == null ? null : AnimationService.getCurrentStage(world, activeInstance);
      if (activeInstance != null) {
         boolean activeIsAttack = NeedsOfNature.isInstanceAttack(world, activeInstance);
         boolean joinAsAttack = isAttack && activeIsAttack;
         if (activeStage != null && !activeStage.allowJoin()) {
            return false;
         } else {
            Identifier predecessorAnimationId = AnimationService.getActiveAnimationId(world, activeInstance);
            if (predecessorAnimationId == null) {
               return true;
            } else {
               List<Entity> combinedActors = this.non$resolveJoinActors(world, activeInstance);
               AnimationService.MatchedAnimation combinedMatch = activeIsAttack
                  ? NonAnimationMatcher.findAutomaticAttack(
                     world, combinedActors, Set.of(), closest, true, joinAsAttack ? (Entity)this : null, Set.of(), null, predecessorAnimationId
                  )
                  : NonAnimationMatcher.findAutomatic(
                     world, combinedActors, Set.of(), closest, true, joinAsAttack ? (Entity)this : null, Set.of(), null, predecessorAnimationId
                  );
               if (combinedMatch == null) {
                  NeedsOfNature.logRuntime(
                     "Energy join skipped: mobType={} player={} instance={} no expanded match", mobTypeId, closest.getName().getString(), activeInstance
                  );
                  NeedsOfNature.sendDebugChat(
                     closest, NonDebugChatCategory.WARNING, Text.translatable("debug.needsofnature.mob_join_no_expanded_match", new Object[]{mobName})
                  );
                  return true;
               } else {
                  String playerName = closest.getName().getString();
                  String joinAnimationId = combinedMatch.animationId().toString();
                  NeedsOfNature.logRuntime(
                     "Energy join attempt: mobType={} energy={} -> player={} instance={} distanceSq={}",
                     mobTypeId,
                     currentEnergy,
                     playerName,
                     activeInstance,
                     closestDistSq
                  );
                  NeedsOfNature.sendDebugChat(
                     closest,
                     NonDebugChatCategory.INFO,
                     Text.translatable("debug.needsofnature.mob_wants_join", new Object[]{mobName, currentEnergy, joinAnimationId})
                  );
                  int multiActorJoinChance = NonMobEnergySystem.resolveMultiActorJoinChancePercent(this);
                  if (combinedActors.size() > 2 && multiActorJoinChance < 100 && this.non$multiActorJoinCooldowns.containsKey(closest.getUuid())) {
                     return true;
                  } else if (!NonMobEnergySystem.passesMultiActorJoinRoll(world, this, combinedActors.size(), multiActorJoinChance)) {
                     this.non$applyNonBypassRollCooldown(closest.getUuid(), nowTick);
                     return true;
                  } else {
                     NonGatherSystem.startJoinGather(world, (MobEntity)(Object)this, closest, activeInstance, combinedMatch.animationId());
                     NeedsOfNature.logRuntime(
                        "Energy join gathering: mobType={} -> player={} instance={} anim={}",
                        mobTypeId,
                        playerName,
                        activeInstance,
                        combinedMatch.animationId()
                     );
                     NeedsOfNature.sendDebugChat(
                        closest,
                        NonDebugChatCategory.INFO,
                        Text.translatable("debug.needsofnature.join_request_accepted", new Object[]{mobName, joinAnimationId})
                     );
                     return true;
                  }
               }
            }
         }
      } else if (isAttack && canJoin) {
         List<Entity> actors = new ArrayList<>(2);
         actors.add(this);
         actors.add(closest);
         actors.sort(Comparator.comparingInt(Entity::getId));
         NonGatherSystem.GatherCandidate gatherCandidate = NonGatherSystem.findGatherCandidate(world, actors, true, (Entity)this, closest);
         if (gatherCandidate == null) {
            return false;
         } else {
            String playerName = closest.getName().getString();
            NeedsOfNature.logRuntime(
               "Energy start attempt (200): mobType={} -> player={} anim={} distanceSq={}", mobTypeId, playerName, gatherCandidate.animationId(), closestDistSq
            );
            NeedsOfNature.sendDebugChat(
               closest, NonDebugChatCategory.INFO, Text.translatable("debug.needsofnature.starting_animation_with_mob_200", new Object[]{mobName})
            );
            boolean gatherStarted = NonGatherSystem.startMobPlayerGather(
               world, (MobEntity)(Object)this, closest, gatherCandidate.animationId(), gatherCandidate.actorKeys(), gatherCandidate.stages()
            );
            if (!gatherStarted) {
               this.non$playerSearchCooldown = 100;
               return true;
            } else {
               NeedsOfNature.logRuntime("Energy start gathering: mobType={} player={} anim={}", mobTypeId, playerName, gatherCandidate.animationId());
               NeedsOfNature.sendDebugChat(
                  closest, NonDebugChatCategory.INFO, Text.translatable("debug.needsofnature.gathering_with_mob", new Object[]{mobName})
               );
               this.non$playerSearchCooldown = 100;
               return true;
            }
         }
      } else {
         return false;
      }
   }

   @Unique
   private void non$spawnMaxEnergyHeartParticle(ServerWorld world) {
      if (world != null) {
         this.non$heartParticleCooldown++;
         if (this.non$heartParticleCooldown >= 10) {
            this.non$heartParticleCooldown = 0;
            world.spawnParticles(NonParticles.SMALLHEART, this.getX(), this.getBodyY(0.6), this.getZ(), 1, 0.3, 0.3, 0.3, 0.0);
         }
      }
   }

   @Unique
   private boolean non$shouldBypassPlayerCooldown(ServerPlayerEntity player, double distSq) {
      if (player == null) {
         return false;
      }

      if (!this.non$bypassablePartnerCooldowns.containsKey(player.getUuid())) {
         return false;
      }

      if (distSq > 64.0) {
         return false;
      }

      MobEntity self = (MobEntity)(Object)this;
      return self.canSee(player);
   }

   @Unique
   @Nullable
   private ServerPlayerEntity non$findNearestActiveAnimationPlayerForLinger(ServerWorld world) {
      if (world != null && !world.getPlayers().isEmpty()) {
         MobEntity self = (MobEntity)(Object)this;
         double maxDistSq = 400.0;
         ServerPlayerEntity cachedPlayer = null;
         if (this.non$lingerLookTargetUuid != null) {
            if (world.getEntity(this.non$lingerLookTargetUuid) instanceof ServerPlayerEntity player
               && this.non$isBasicPlayerAnimationLingerTarget(player, self, maxDistSq)) {
               cachedPlayer = player;
            } else {
               this.non$lingerLookTargetUuid = null;
            }
         }

         if (this.non$lingerMatchCooldown > 0) {
            this.non$lingerMatchCooldown--;
            return cachedPlayer;
         }

         this.non$lingerMatchCooldown = 10;
         if (!NeedsOfNature.consumeScanBudget(world)) {
            return cachedPlayer;
         }

         if (cachedPlayer != null && this.non$hasPairAnimationWithPlayer(cachedPlayer)) {
            return cachedPlayer;
         }

         this.non$lingerLookTargetUuid = null;
         ServerPlayerEntity nearest = null;
         double nearestDistSq = Double.MAX_VALUE;

         for (ServerPlayerEntity player : world.getPlayers()) {
            double distSq = player.squaredDistanceTo(self);
            if (!(distSq > maxDistSq)
               && !(distSq >= nearestDistSq)
               && this.non$isBasicPlayerAnimationLingerTarget(player, self, maxDistSq)
               && this.non$hasPairAnimationWithPlayer(player)) {
               nearest = player;
               nearestDistSq = distSq;
            }
         }

         return nearest;
      } else {
         return null;
      }
   }

   @Unique
   private boolean non$isBasicPlayerAnimationLingerTarget(ServerPlayerEntity player, MobEntity self, double maxDistSq) {
      if (player == null || self == null || !player.isAlive() || player.isRemoved()) {
         return false;
      } else if (!player.isSpectator() && !player.isCreative()) {
         return player.squaredDistanceTo(self) > maxDistSq ? false : NeedsOfNature.getActivePlayerInstance(player) != null;
      } else {
         return false;
      }
   }

   @Unique
   private boolean non$hasPairAnimationWithPlayer(ServerPlayerEntity player) {
      if (player == null) {
         return false;
      } else if (player.getEntityWorld() instanceof ServerWorld world) {
         List<Entity> pair = new ArrayList<>(2);
         pair.add(this);
         pair.add(player);
         pair.sort(Comparator.comparingInt(Entity::getId));
         return NonAnimationMatcher.findAutomatic(world, pair, Set.of(), null, false, null, Set.of()) != null;
      } else {
         return false;
      }
   }

   @Unique
   private void non$applyPlayerAnimationLinger(ServerPlayerEntity player) {
      if (player != null) {
         MobEntity self = (MobEntity)(Object)this;
         this.non$lingerLookTargetUuid = player.getUuid();
         double distSq = self.squaredDistanceTo(player);
         if (distSq > 9.0 || distSq < 2.25) {
            double targetX = player.getX();
            double targetZ = player.getZ();
            double dx = self.getX() - player.getX();
            double dz = self.getZ() - player.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0E-4) {
               double angle = Math.toRadians(Math.floorMod(self.getId() * 47, 360));
               dx = Math.cos(angle);
               dz = Math.sin(angle);
               length = 1.0;
            }

            double ringDistance = 2.25;
            targetX += dx / length * ringDistance;
            targetZ += dz / length * ringDistance;
            self.getNavigation().startMovingTo(targetX, player.getY(), targetZ, 1.1);
         }
      }
   }

   @Unique
   private List<Entity> non$resolveJoinActors(ServerWorld world, UUID instanceId) {
      List<Entity> combined = new ArrayList<>();
      if (world != null && instanceId != null) {
         for (UUID uuid : NeedsOfNature.getInstanceActors(world, instanceId)) {
            Entity e = world.getEntity(uuid);
            if (e != null) {
               combined.add(e);
            }
         }
      }

      if (combined.stream().noneMatch(ex -> ex.getUuid().equals(this.getUuid()))) {
         combined.add(this);
      }

      combined.sort(Comparator.comparingInt(Entity::getId));
      return combined;
   }

   @Unique
   private void non$applyRollCooldown(UUID partnerId, long nowTick) {
      if (partnerId != null) {
         long until = nowTick + 1200L;
         this.non$partnerCooldowns.put(partnerId, until);
         this.non$bypassablePartnerCooldowns.put(partnerId, until);
      }
   }

   @Unique
   private void non$applyNonBypassRollCooldown(UUID partnerId, long nowTick) {
      if (partnerId != null) {
         long until = nowTick + 1200L;
         this.non$multiActorJoinCooldowns.put(partnerId, until);
      }
   }

   @Unique
   private void non$pruneCooldownMaps(long nowTick) {
      this.non$partnerCooldowns.entrySet().removeIf(e -> e.getValue() <= nowTick);
      this.non$bypassablePartnerCooldowns.entrySet().removeIf(e -> e.getValue() <= nowTick);
      this.non$multiActorJoinCooldowns.entrySet().removeIf(e -> e.getValue() <= nowTick);
   }

   @Unique
   private void non$ensureEnergyInitialized(ServerWorld world) {
      if (!this.non$energyInitialized) {
         this.non$energyInitialized = true;
         if (this.non$energyGainMult <= 0.0F) {
            this.non$energyGainMult = NonMobEnergySystem.randomGainMultiplier(world);
         }

         if (this.non$energyGainDrift <= 0.0F) {
            this.non$energyGainDrift = NonMobEnergySystem.randomGainDrift(world);
         }

         int maxInitial = NeedsOfNature.getConfig().getInitialEnergyMax();
         if (maxInitial > 0 && this.getEnergy() == 0) {
            int initial = world.getRandom().nextInt(maxInitial + 1);
            this.setEnergy(initial);
         }
      }
   }
}
