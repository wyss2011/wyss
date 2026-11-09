package com.nonid.internal.animation.client.runtime;

import com.nonid.api.client.NonAnimationRenderEvents;
import com.nonid.api.client.NonAnimationSoundEvents;
import com.nonid.internal.animation.client.diagnostics.AnimationAssetDiagnostics;
import com.nonid.internal.animation.client.render.gecko.GeckoResourceResolver;
import com.nonid.internal.animation.client.render.gecko.ParticleKeyframes;
import com.nonid.internal.animation.client.sound.AnimationSoundAnticipation;
import com.nonid.internal.animation.client.sound.AnimationSoundEffects;
import com.nonid.internal.animation.data.AnimationCueEffects;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.util.StageTimeWarp;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public final class ClientAnimationRuntime {
   private static final List<ClientAnimationRuntime.PendingStart> PENDING_STARTS = new ArrayList<>();
   private static final List<ClientAnimationRuntime.PendingStageAdvance> PENDING_STAGE_ADVANCES = new ArrayList<>();
   private static final Map<UUID, ClientAnimationRuntime.InstanceState> INSTANCES = new HashMap<>();
   private static final Map<UUID, Long> PENDING_STOP_TICKS = new HashMap<>();
   private static final Map<UUID, Integer> PENDING_MANUAL_ADVANCE = new HashMap<>();
   private static final Map<UUID, ClientAnimationRuntime.PendingStageAdvance> PENDING_TARGET_STAGE_ADVANCE = new HashMap<>();
   private static final Map<UUID, ClientAnimationRuntime.PendingSpeedUpdate> PENDING_SPEED_UPDATES = new HashMap<>();
   private static final ArrayDeque<UUID> COMPLETED_INSTANCES = new ArrayDeque<>();
   private static final Map<Identifier, Boolean> KNOWN_LOOP_BY_ANIMATION_ID = new HashMap<>();
   private static final Map<UUID, Identifier> ACTIVE_ACTOR_LATEST_ANIMATION_ID = new HashMap<>();
   private static final Map<UUID, List<Identifier>> ACTIVE_ACTOR_MODEL_ROOTS = new HashMap<>();
   private static final Map<UUID, String> ACTIVE_ACTOR_LATEST_KEY = new HashMap<>();
   private static final Map<UUID, Double> ACTIVE_ACTOR_SPEED = new HashMap<>();
   private static final Map<UUID, Map<String, NonAnimationRenderEvents.BoneItemProp>> ACTIVE_ACTOR_BONE_ITEMS = new HashMap<>();
   private static final Map<UUID, ClientAnimationRuntime.RenderHold> ACTOR_RENDER_HOLDS = new HashMap<>();
   private static final Map<UUID, ClientAnimationRuntime.LockedOrientation> LOCKED_ORIENTATION_BY_ACTOR = new HashMap<>();
   private static volatile boolean HAS_ACTIVE_INSTANCES = false;
   private static final ArrayDeque<UUID> START_ORDER = new ArrayDeque<>();
   private static volatile long CURRENT_TICK = 0L;
   private static final long RENDER_HANDOFF_HOLD_TICKS = 1L;
   private static final String PROP_LEFT_BONE = "propleft";
   private static final String PROP_RIGHT_BONE = "propright";
   private static final String PROP_FLOOR_BONE = "propfloor";
   private static Long STOP_ALL_AT_TICK = null;

   private ClientAnimationRuntime() {
   }

   public static void queueStart(
      Identifier animationId,
      List<List<Identifier>> modelRootsByActor,
      UUID instanceId,
      List<UUID> actorUuids,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      long startTick,
      int stageIndex,
      long stageStartTick,
      long stageEndTick,
      double speed,
      boolean lockOrientation,
      float lockedYaw,
      float lockedHeadYaw,
      float lockedPitch,
      Vec3d cameraOrbitTarget
   ) {
      List<String> safeKeys = actorKeys == null ? List.of() : List.copyOf(actorKeys);
      List<AnimationStageInfo> safeStages = stages == null ? List.of() : List.copyOf(stages);
      rememberStageMeta(animationId, safeStages);
      AnimationAssetDiagnostics.validateStartPayload(animationId, safeKeys, safeStages);
      UUID anchorUuid = actorUuids.isEmpty() ? null : actorUuids.getFirst();
      ClientAnimationRuntime.LockedOrientation locked = lockOrientation
         ? new ClientAnimationRuntime.LockedOrientation(lockedYaw, lockedHeadYaw, lockedPitch)
         : null;
      List<List<Identifier>> safeModelRoots = modelRootsByActor == null
         ? Collections.nCopies(actorUuids.size(), List.of())
         : modelRootsByActor.stream().map(roots -> roots == null ? List.<Identifier>of() : List.<Identifier>copyOf((Collection<? extends Identifier>)roots)).toList();
      PENDING_STARTS.add(
         new ClientAnimationRuntime.PendingStart(
            instanceId,
            animationId,
            safeModelRoots,
            List.copyOf(actorUuids),
            safeKeys,
            safeStages,
            startTick,
            stageIndex,
            stageStartTick,
            stageEndTick,
            sanitizeSpeed(speed),
            anchorUuid,
            lockOrientation,
            locked,
            sanitizeCameraOrbitTarget(cameraOrbitTarget)
         )
      );
   }

   public static void queueStop(UUID instanceId, long stopTick) {
      ClientAnimationRuntime.InstanceState state = INSTANCES.get(instanceId);
      if (state != null) {
         state.stopAtTick = state.stopAtTick == null ? stopTick : Math.min(state.stopAtTick, stopTick);
      } else {
         PENDING_STOP_TICKS.merge(instanceId, stopTick, Math::min);
         if (stopTick <= CURRENT_TICK) {
            PENDING_STARTS.removeIf(ps -> ps.instanceId().equals(instanceId));
         }
      }
   }

   public static void queueStopAll(long stopTick) {
      if (STOP_ALL_AT_TICK == null) {
         STOP_ALL_AT_TICK = stopTick;
      } else {
         STOP_ALL_AT_TICK = Math.min(STOP_ALL_AT_TICK, stopTick);
      }
   }

   public static void stopAllNow() {
      STOP_ALL_AT_TICK = null;
      PENDING_STARTS.clear();
      PENDING_STAGE_ADVANCES.clear();
      PENDING_STOP_TICKS.clear();
      PENDING_MANUAL_ADVANCE.clear();
      PENDING_TARGET_STAGE_ADVANCE.clear();
      PENDING_SPEED_UPDATES.clear();
      INSTANCES.clear();
      START_ORDER.clear();
      ACTIVE_ACTOR_LATEST_ANIMATION_ID.clear();
      ACTIVE_ACTOR_MODEL_ROOTS.clear();
      ACTIVE_ACTOR_LATEST_KEY.clear();
      ACTIVE_ACTOR_SPEED.clear();
      ACTIVE_ACTOR_BONE_ITEMS.clear();
      ACTOR_RENDER_HOLDS.clear();
      LOCKED_ORIENTATION_BY_ACTOR.clear();
      HAS_ACTIVE_INSTANCES = false;
      COMPLETED_INSTANCES.clear();
      KNOWN_LOOP_BY_ANIMATION_ID.clear();
      ParticleKeyframes.clearAll();
   }

   public static void requestStageAdvance(UUID instanceId) {
      ClientAnimationRuntime.InstanceState state = INSTANCES.get(instanceId);
      if (state != null) {
         state.manualAdvanceRequests++;
      } else {
         PENDING_MANUAL_ADVANCE.merge(instanceId, 1, Integer::sum);
      }
   }

   public static boolean advanceStage(UUID instanceId) {
      ClientAnimationRuntime.InstanceState state = INSTANCES.get(instanceId);
      if (state == null) {
         return false;
      }

      state.manualAdvanceRequests++;
      return true;
   }

   public static void queueStageAdvance(UUID instanceId, long advanceTick, long stageEndTick, int stageIndex) {
      if (advanceTick <= CURRENT_TICK) {
         requestStageAdvanceTo(instanceId, stageIndex, advanceTick, stageEndTick);
      } else {
         PENDING_STAGE_ADVANCES.add(new ClientAnimationRuntime.PendingStageAdvance(instanceId, advanceTick, stageEndTick, stageIndex));
      }
   }

   private static void requestStageAdvanceTo(UUID instanceId, int stageIndex, long advanceTick, long stageEndTick) {
      ClientAnimationRuntime.InstanceState state = INSTANCES.get(instanceId);
      if (state != null) {
         state.setStageIndex(stageIndex, Math.max(0L, advanceTick), stageEndTick);
         ClientAnimationRuntime.PendingSpeedUpdate queuedSpeed = PENDING_SPEED_UPDATES.remove(instanceId);
         if (queuedSpeed != null) {
            applySpeedToState(state, queuedSpeed);
         }
      } else {
         PENDING_TARGET_STAGE_ADVANCE.put(
            instanceId, new ClientAnimationRuntime.PendingStageAdvance(instanceId, Math.max(0L, advanceTick), stageEndTick, stageIndex)
         );
      }
   }

   public static void queueSpeedUpdate(UUID instanceId, double speed, long stageStartTick, long stageEndTick) {
      ClientAnimationRuntime.PendingSpeedUpdate update = new ClientAnimationRuntime.PendingSpeedUpdate(sanitizeSpeed(speed), stageStartTick, stageEndTick);
      ClientAnimationRuntime.InstanceState state = INSTANCES.get(instanceId);
      if (state != null) {
         if (hasPendingStageAdvance(instanceId)) {
            PENDING_SPEED_UPDATES.put(instanceId, update);
         } else {
            applySpeedToState(state, update);
         }
      } else {
         PENDING_SPEED_UPDATES.put(instanceId, update);
      }
   }

   private static boolean hasPendingStageAdvance(UUID instanceId) {
      if (instanceId == null) {
         return false;
      }

      if (PENDING_TARGET_STAGE_ADVANCE.containsKey(instanceId)) {
         return true;
      }

      for (ClientAnimationRuntime.PendingStageAdvance pending : PENDING_STAGE_ADVANCES) {
         if (pending != null && instanceId.equals(pending.instanceId())) {
            return true;
         }
      }

      return false;
   }

   public static List<UUID> drainCompletedInstances() {
      if (COMPLETED_INSTANCES.isEmpty()) {
         return List.of();
      }

      List<UUID> out = new ArrayList<>(COMPLETED_INSTANCES);
      COMPLETED_INSTANCES.clear();
      return out;
   }

   public static void clientTick(MinecraftClient client) {
      if (client.world != null) {
         long now = client.world.getTime();
         CURRENT_TICK = now;
         if (STOP_ALL_AT_TICK != null && now >= STOP_ALL_AT_TICK) {
            stopAllNow();
         } else if (PENDING_STARTS.isEmpty()
            && INSTANCES.isEmpty()
            && ACTOR_RENDER_HOLDS.isEmpty()
            && PENDING_TARGET_STAGE_ADVANCE.isEmpty()
            && STOP_ALL_AT_TICK == null) {
            if (!PENDING_STOP_TICKS.isEmpty()) {
               PENDING_STOP_TICKS.entrySet().removeIf(e -> e.getValue() <= now);
            }

            if (!PENDING_STAGE_ADVANCES.isEmpty()) {
               PENDING_STAGE_ADVANCES.removeIf(p -> p.advanceTick() <= now);
            }

            if (!ACTIVE_ACTOR_LATEST_ANIMATION_ID.isEmpty()
               || !ACTIVE_ACTOR_MODEL_ROOTS.isEmpty()
               || !ACTIVE_ACTOR_LATEST_KEY.isEmpty()
               || !ACTIVE_ACTOR_SPEED.isEmpty()
               || !ACTIVE_ACTOR_BONE_ITEMS.isEmpty()) {
               ACTIVE_ACTOR_LATEST_ANIMATION_ID.clear();
               ACTIVE_ACTOR_MODEL_ROOTS.clear();
               ACTIVE_ACTOR_LATEST_KEY.clear();
               ACTIVE_ACTOR_SPEED.clear();
               ACTIVE_ACTOR_BONE_ITEMS.clear();
               HAS_ACTIVE_INSTANCES = false;
            }

            START_ORDER.clear();
            ParticleKeyframes.retainActorOrigins(Set.of());
         } else {
            if (!ACTOR_RENDER_HOLDS.isEmpty()) {
               ACTOR_RENDER_HOLDS.entrySet().removeIf(e -> now > e.getValue().expiresAtTick());
            }

            int i = 0;

            while (i < PENDING_STARTS.size()) {
               ClientAnimationRuntime.PendingStart ps = PENDING_STARTS.get(i);
               if (now >= ps.startTick()) {
                  ClientAnimationRuntime.LockedOrientation anchorOrientation = ps.lockOrientation()
                     ? ps.lockedOrientation()
                     : resolveOrientation(client, ps.anchorUuid());
                  double initialSpeed = sanitizeSpeed(ps.speed());
                  long initialStageStartTick = ps.stageStartTick();
                  long initialStageEndTick = ps.stageEndTick();
                  ClientAnimationRuntime.PendingSpeedUpdate queuedSpeed = PENDING_SPEED_UPDATES.remove(ps.instanceId());
                  if (queuedSpeed != null) {
                     initialSpeed = queuedSpeed.speed();
                     initialStageStartTick = queuedSpeed.stageStartTick();
                     initialStageEndTick = queuedSpeed.stageEndTick();
                  }

                  ClientAnimationRuntime.InstanceState state = new ClientAnimationRuntime.InstanceState(
                     ps.instanceId(),
                     ps.animationId(),
                     ps.modelRootsByActor(),
                     ps.actorUuids(),
                     ps.actorKeys(),
                     ps.stages(),
                     ps.startTick(),
                     ps.stageIndex(),
                     initialStageStartTick,
                     initialStageEndTick,
                     initialSpeed,
                     ps.anchorUuid(),
                     anchorOrientation,
                     ps.cameraOrbitTarget()
                  );
                  ClientAnimationRuntime.PendingStageAdvance pendingTargetStage = PENDING_TARGET_STAGE_ADVANCE.remove(ps.instanceId());
                  if (pendingTargetStage != null) {
                     state.setStageIndex(pendingTargetStage.stageIndex(), pendingTargetStage.advanceTick(), pendingTargetStage.stageEndTick());
                  }

                  Integer pendingAdvance = PENDING_MANUAL_ADVANCE.remove(ps.instanceId());
                  if (pendingTargetStage == null && pendingAdvance != null) {
                     state.manualAdvanceRequests = pendingAdvance;
                  }

                  Long pendingStop = PENDING_STOP_TICKS.remove(ps.instanceId());
                  if (pendingStop != null) {
                     state.stopAtTick = pendingStop;
                  }

                  INSTANCES.put(ps.instanceId(), state);
                  START_ORDER.addLast(ps.instanceId());

                  for (UUID actorUuid : ps.actorUuids()) {
                     ACTOR_RENDER_HOLDS.remove(actorUuid);
                  }

                  PENDING_STARTS.remove(i);
               } else {
                  i++;
               }
            }

            if (!PENDING_STAGE_ADVANCES.isEmpty()) {
               i = 0;

               while (i < PENDING_STAGE_ADVANCES.size()) {
                  ClientAnimationRuntime.PendingStageAdvance pending = PENDING_STAGE_ADVANCES.get(i);
                  if (now >= pending.advanceTick()) {
                     requestStageAdvanceTo(pending.instanceId(), pending.stageIndex(), pending.advanceTick(), pending.stageEndTick());
                     PENDING_STAGE_ADVANCES.remove(i);
                  } else {
                     i++;
                  }
               }
            }

            if (!INSTANCES.isEmpty()) {
               Iterator<Entry<UUID, ClientAnimationRuntime.InstanceState>> expiredIt = INSTANCES.entrySet().iterator();

               while (expiredIt.hasNext()) {
                  Entry<UUID, ClientAnimationRuntime.InstanceState> entry = expiredIt.next();
                  ClientAnimationRuntime.InstanceState state = entry.getValue();
                  if (now >= state.effectiveStopTick()) {
                     rememberRenderHolds(state, now);
                     ParticleKeyframes.clearInstance(entry.getKey());
                     expiredIt.remove();
                  }
               }
            }

            if (!INSTANCES.isEmpty()) {
               List<UUID> completed = new ArrayList<>();

               for (ClientAnimationRuntime.InstanceState state : INSTANCES.values()) {
                  if (now >= state.startTick) {
                     while (state.manualAdvanceRequests > 0) {
                        state.manualAdvanceRequests--;
                        if (!state.advanceStage(now)) {
                           completed.add(state.instanceId);
                           break;
                        }

                        ClientAnimationRuntime.PendingSpeedUpdate queued = PENDING_SPEED_UPDATES.remove(state.instanceId);
                        if (queued != null) {
                           applySpeedToState(state, queued);
                        }
                     }
                  }
               }

               if (!completed.isEmpty()) {
                  for (UUID id : completed) {
                     ClientAnimationRuntime.InstanceState completedState = INSTANCES.remove(id);
                     if (completedState != null) {
                        rememberRenderHolds(completedState, now);
                     }

                     ParticleKeyframes.clearInstance(id);
                     PENDING_STOP_TICKS.remove(id);
                     PENDING_MANUAL_ADVANCE.remove(id);
                     PENDING_TARGET_STAGE_ADVANCE.remove(id);
                     PENDING_SPEED_UPDATES.remove(id);
                     START_ORDER.removeIf(existing -> existing.equals(id));
                     COMPLETED_INSTANCES.add(id);
                  }
               }
            }

            if (START_ORDER.size() > 256) {
               ArrayDeque<UUID> compact = new ArrayDeque<>(START_ORDER.size());

               for (UUID id : START_ORDER) {
                  if (INSTANCES.containsKey(id)) {
                     compact.addLast(id);
                  }
               }

               START_ORDER.clear();
               START_ORDER.addAll(compact);
            } else {
               START_ORDER.removeIf(idx -> !INSTANCES.containsKey(idx));
            }

            ACTIVE_ACTOR_LATEST_ANIMATION_ID.clear();
            ACTIVE_ACTOR_MODEL_ROOTS.clear();
            ACTIVE_ACTOR_LATEST_KEY.clear();
            ACTIVE_ACTOR_SPEED.clear();
            ACTIVE_ACTOR_BONE_ITEMS.clear();
            Iterator<UUID> it = START_ORDER.descendingIterator();

            while (it.hasNext()) {
               UUID id = it.next();
               ClientAnimationRuntime.InstanceState state = INSTANCES.get(id);
               if (state != null) {
                  for (int ix = 0; ix < state.actorUuids.size(); ix++) {
                     UUID actorUuid = state.actorUuids.get(ix);
                     ACTIVE_ACTOR_LATEST_ANIMATION_ID.putIfAbsent(actorUuid, state.currentAnimationId());
                     ACTIVE_ACTOR_MODEL_ROOTS.putIfAbsent(actorUuid, state.modelRootsForActor(ix));
                     ACTIVE_ACTOR_SPEED.putIfAbsent(actorUuid, state.speed);
                     ACTIVE_ACTOR_BONE_ITEMS.putIfAbsent(actorUuid, resolveActorBoneItems(state, ix));
                     if (ix < state.actorKeys.size()) {
                        String actorKey = state.actorKeys.get(ix);
                        if (actorKey != null && !actorKey.isEmpty()) {
                           ACTIVE_ACTOR_LATEST_KEY.putIfAbsent(actorUuid, actorKey);
                        }
                     }
                  }
               }
            }

            if (!ACTOR_RENDER_HOLDS.isEmpty()) {
               for (Entry<UUID, ClientAnimationRuntime.RenderHold> entry : ACTOR_RENDER_HOLDS.entrySet()) {
                  UUID actorUuid = entry.getKey();
                  ClientAnimationRuntime.RenderHold hold = entry.getValue();
                  ACTIVE_ACTOR_LATEST_ANIMATION_ID.putIfAbsent(actorUuid, hold.animationId());
                  ACTIVE_ACTOR_MODEL_ROOTS.putIfAbsent(actorUuid, hold.modelRoots());
                  ACTIVE_ACTOR_SPEED.putIfAbsent(actorUuid, hold.speed());
                  ACTIVE_ACTOR_BONE_ITEMS.putIfAbsent(actorUuid, hold.boneItems());
                  if (hold.actorKey() != null && !hold.actorKey().isEmpty()) {
                     ACTIVE_ACTOR_LATEST_KEY.putIfAbsent(actorUuid, hold.actorKey());
                  }
               }
            }

            ParticleKeyframes.retainActorOrigins(ACTIVE_ACTOR_LATEST_ANIMATION_ID.keySet());
            HAS_ACTIVE_INSTANCES = !ACTIVE_ACTOR_LATEST_ANIMATION_ID.isEmpty();
            syncLockedOrientations(client);
            tickSoundCues(client, now);
         }
      }
   }

   private static void rememberRenderHolds(ClientAnimationRuntime.InstanceState state, long nowTick) {
      if (state != null && !state.actorUuids.isEmpty()) {
         long expiresAt = nowTick + 1L;

         for (int i = 0; i < state.actorUuids.size(); i++) {
            UUID actorUuid = state.actorUuids.get(i);
            if (actorUuid != null) {
               String actorKey = i < state.actorKeys.size() ? state.actorKeys.get(i) : null;
               ACTOR_RENDER_HOLDS.put(
                  actorUuid,
                  new ClientAnimationRuntime.RenderHold(
                     state.instanceId,
                     state.currentAnimationId(),
                     state.modelRootsForActor(i),
                     actorKey,
                     state.speed,
                     resolveActorBoneItems(state, i),
                     state.anchorOrientation,
                     expiresAt
                  )
               );
            }
         }
      }
   }

   public static boolean hasActiveInstances() {
      return HAS_ACTIVE_INSTANCES;
   }

   public static boolean isActorActive(UUID actorUuid) {
      return ACTIVE_ACTOR_LATEST_ANIMATION_ID.containsKey(actorUuid);
   }

   public static boolean isActorPendingOrActive(UUID actorUuid) {
      if (ACTIVE_ACTOR_LATEST_ANIMATION_ID.containsKey(actorUuid)) {
         return true;
      }

      for (ClientAnimationRuntime.PendingStart ps : PENDING_STARTS) {
         for (UUID uuid : ps.actorUuids()) {
            if (uuid.equals(actorUuid)) {
               return true;
            }
         }
      }

      return false;
   }

   @Nullable
   public static ClientAnimationRuntime.LockedOrientation getLockedOrientation(UUID actorUuid) {
      ClientAnimationRuntime.LockedOrientation locked = LOCKED_ORIENTATION_BY_ACTOR.get(actorUuid);
      if (locked != null) {
         return locked;
      }

      ClientAnimationRuntime.RenderHold hold = ACTOR_RENDER_HOLDS.get(actorUuid);
      return hold != null && CURRENT_TICK <= hold.expiresAtTick() ? hold.lockedOrientation() : null;
   }

   @Nullable
   public static Vec3d findCameraOrbitTargetForActor(UUID actorUuid) {
      if (actorUuid == null) {
         return null;
      }

      long now = CURRENT_TICK;
      Iterator<UUID> it = START_ORDER.descendingIterator();

      while (it.hasNext()) {
         UUID id = it.next();
         ClientAnimationRuntime.InstanceState state = INSTANCES.get(id);
         if (state != null && state.isActive(now) && state.containsActor(actorUuid)) {
            return state.cameraOrbitTarget;
         }
      }

      return null;
   }

   @Nullable
   public static UUID findAnchorUuidForActor(UUID actorUuid) {
      for (ClientAnimationRuntime.InstanceState state : INSTANCES.values()) {
         if (state.containsActor(actorUuid)) {
            return state.anchorUuid;
         }
      }

      for (ClientAnimationRuntime.PendingStart ps : PENDING_STARTS) {
         for (UUID uuid : ps.actorUuids()) {
            if (uuid.equals(actorUuid)) {
               return ps.anchorUuid();
            }
         }
      }

      return null;
   }

   @Nullable
   public static Long findStartTickForActor(UUID actorUuid) {
      for (ClientAnimationRuntime.InstanceState state : INSTANCES.values()) {
         if (state.containsActor(actorUuid)) {
            return state.startTick;
         }
      }

      for (ClientAnimationRuntime.PendingStart ps : PENDING_STARTS) {
         for (UUID uuid : ps.actorUuids()) {
            if (uuid.equals(actorUuid)) {
               return ps.startTick();
            }
         }
      }

      return null;
   }

   public static Identifier findLatestActiveAnimationIdContaining(UUID actorUuid) {
      return ACTIVE_ACTOR_LATEST_ANIMATION_ID.get(actorUuid);
   }

   public static List<Identifier> findLatestModelRootsForActor(UUID actorUuid) {
      List<Identifier> roots = ACTIVE_ACTOR_MODEL_ROOTS.get(actorUuid);
      return roots == null ? List.of() : roots;
   }

   public static String findLatestActiveActorKey(UUID actorUuid) {
      return ACTIVE_ACTOR_LATEST_KEY.get(actorUuid);
   }

   public static double findLatestSpeedForActor(UUID actorUuid) {
      return ACTIVE_ACTOR_SPEED.getOrDefault(actorUuid, 1.0);
   }

   public static Map<String, NonAnimationRenderEvents.BoneItemProp> findLatestBoneItemsForActor(UUID actorUuid) {
      Map<String, NonAnimationRenderEvents.BoneItemProp> props = ACTIVE_ACTOR_BONE_ITEMS.get(actorUuid);
      return props != null && !props.isEmpty() ? props : Map.of();
   }

   public static void setPropOverride(UUID instanceId, UUID actorUuid, ClientAnimationRuntime.PropSlot slot, ItemStack stack) {
      if (instanceId != null && actorUuid != null && slot != null) {
         ClientAnimationRuntime.InstanceState state = INSTANCES.get(instanceId);
         if (state != null) {
            EnumMap<ClientAnimationRuntime.PropSlot, ItemStack> actorOverrides = state.propOverridesByActor
               .computeIfAbsent(actorUuid, unused -> new EnumMap<>(ClientAnimationRuntime.PropSlot.class));
            if (stack != null && !stack.isEmpty()) {
               actorOverrides.put(slot, stack.copy());
            } else {
               actorOverrides.remove(slot);
            }

            if (actorOverrides.isEmpty()) {
               state.propOverridesByActor.remove(actorUuid);
            }
         }
      }
   }

   public static void setLeftHandPropOverride(UUID instanceId, UUID actorUuid, ItemStack stack) {
      setPropOverride(instanceId, actorUuid, ClientAnimationRuntime.PropSlot.LEFT, stack);
   }

   public static void setRightHandPropOverride(UUID instanceId, UUID actorUuid, ItemStack stack) {
      setPropOverride(instanceId, actorUuid, ClientAnimationRuntime.PropSlot.RIGHT, stack);
   }

   public static void setFloorPropOverride(UUID instanceId, UUID actorUuid, ItemStack stack) {
      setPropOverride(instanceId, actorUuid, ClientAnimationRuntime.PropSlot.FLOOR, stack);
   }

   public static void clearPropOverride(UUID instanceId, UUID actorUuid, ClientAnimationRuntime.PropSlot slot) {
      if (instanceId != null && actorUuid != null && slot != null) {
         ClientAnimationRuntime.InstanceState state = INSTANCES.get(instanceId);
         if (state != null) {
            EnumMap<ClientAnimationRuntime.PropSlot, ItemStack> actorOverrides = state.propOverridesByActor.get(actorUuid);
            if (actorOverrides != null) {
               actorOverrides.remove(slot);
               if (actorOverrides.isEmpty()) {
                  state.propOverridesByActor.remove(actorUuid);
               }
            }
         }
      }
   }

   public static void clearLeftHandPropOverride(UUID instanceId, UUID actorUuid) {
      clearPropOverride(instanceId, actorUuid, ClientAnimationRuntime.PropSlot.LEFT);
   }

   public static void clearRightHandPropOverride(UUID instanceId, UUID actorUuid) {
      clearPropOverride(instanceId, actorUuid, ClientAnimationRuntime.PropSlot.RIGHT);
   }

   public static void clearFloorPropOverride(UUID instanceId, UUID actorUuid) {
      clearPropOverride(instanceId, actorUuid, ClientAnimationRuntime.PropSlot.FLOOR);
   }

   public static void clearPropOverrides(UUID instanceId, UUID actorUuid) {
      if (instanceId != null && actorUuid != null) {
         ClientAnimationRuntime.InstanceState state = INSTANCES.get(instanceId);
         if (state != null) {
            state.propOverridesByActor.remove(actorUuid);
         }
      }
   }

   public static UUID findLatestActiveInstanceContaining(UUID actorUuid) {
      long now = CURRENT_TICK;
      Iterator<UUID> it = START_ORDER.descendingIterator();

      while (it.hasNext()) {
         UUID id = it.next();
         ClientAnimationRuntime.InstanceState state = INSTANCES.get(id);
         if (state != null && state.isActive(now) && state.containsActor(actorUuid)) {
            return id;
         }
      }

      ClientAnimationRuntime.RenderHold hold = ACTOR_RENDER_HOLDS.get(actorUuid);
      return hold != null && now <= hold.expiresAtTick() ? hold.instanceId() : null;
   }

   public static boolean shouldHideNameLabelForViewer(UUID viewerUuid, UUID renderedUuid) {
      if (viewerUuid != null && renderedUuid != null && !viewerUuid.equals(renderedUuid)) {
         UUID viewerInstance = findLatestActiveInstanceContaining(viewerUuid);
         if (viewerInstance == null) {
            return false;
         }

         UUID renderedInstance = findLatestActiveInstanceContaining(renderedUuid);
         return viewerInstance.equals(renderedInstance);
      } else {
         return false;
      }
   }

   public static double findOutsideViewerNameLabelYOffset(UUID viewerUuid, UUID renderedUuid) {
      if (renderedUuid == null) {
         return 0.0;
      }

      ClientAnimationRuntime.InstanceState state = findLatestActiveStateContaining(renderedUuid);
      if (state == null) {
         return 0.0;
      }

      if (viewerUuid != null && state.containsActor(viewerUuid)) {
         return 0.0;
      }

      int actorIndex = state.actorUuids.indexOf(renderedUuid);
      return actorIndex <= 0 ? 0.0 : Math.min(1.5, actorIndex * 0.28);
   }

   private static ClientAnimationRuntime.InstanceState findLatestActiveStateContaining(UUID actorUuid) {
      if (actorUuid == null) {
         return null;
      }

      long now = CURRENT_TICK;
      Iterator<UUID> it = START_ORDER.descendingIterator();

      while (it.hasNext()) {
         UUID id = it.next();
         ClientAnimationRuntime.InstanceState state = INSTANCES.get(id);
         if (state != null && state.isActive(now) && state.containsActor(actorUuid)) {
            return state;
         }
      }

      return null;
   }

   public static AnimationStageInfo findCurrentStage(UUID instanceId) {
      ClientAnimationRuntime.InstanceState state = INSTANCES.get(instanceId);
      return state == null ? null : state.currentStage();
   }

   public static AnimationStageInfo findStage(UUID instanceId, int stageIndex) {
      ClientAnimationRuntime.InstanceState state = INSTANCES.get(instanceId);
      return state != null && stageIndex >= 0 && stageIndex < state.stages.size() ? state.stages.get(stageIndex) : null;
   }

   public static ClientAnimationRuntime.StageTimelineSnapshot findStageTimeline(UUID instanceId) {
      ClientAnimationRuntime.InstanceState state = INSTANCES.get(instanceId);
      return state == null
         ? null
         : new ClientAnimationRuntime.StageTimelineSnapshot(state.stages, state.stageIndex, state.stageStartTick, state.stageEndTick, state.speed);
   }

   public static long findStageStartTick(UUID instanceId) {
      ClientAnimationRuntime.InstanceState state = INSTANCES.get(instanceId);
      return state == null ? -1L : state.stageStartTick;
   }

   public static long findStageEndTick(UUID instanceId) {
      ClientAnimationRuntime.InstanceState state = INSTANCES.get(instanceId);
      return state == null ? -1L : state.stageEndTick;
   }

   public static Double findWarpedAuthoredTimelineSeconds(UUID actorUuid, float tickDelta) {
      if (actorUuid == null) {
         return null;
      }

      long now = CURRENT_TICK;
      Iterator<UUID> it = START_ORDER.descendingIterator();

      while (it.hasNext()) {
         UUID id = it.next();
         ClientAnimationRuntime.InstanceState state = INSTANCES.get(id);
         if (state != null && state.isActive(now) && state.containsActor(actorUuid)) {
            AnimationStageInfo stage = state.currentStage();
            double cycleTicks = exactCycleTicks(stage);
            if (stage != null && stage.loop() && !(cycleTicks <= 0.0)) {
               double offsetTicks = StageTimeWarp.offsetSecondsToTicks(stage.cycleMidpointOffsetSeconds());
               if (!StageTimeWarp.hasWarp(cycleTicks, offsetTicks)) {
                  return null;
               }

               double warpedTicks = Math.max(0.0, ((float)now + Math.max(0.0F, tickDelta) - (float)state.stageStartTick) * state.speed);
               double authoredTicks = StageTimeWarp.warpedToAuthoredCycleTicks(warpedTicks, cycleTicks, offsetTicks);
               return authoredTicks / 20.0;
            }

            return null;
         }
      }

      return null;
   }

   public static UUID findLatestActiveInstance() {
      long now = CURRENT_TICK;
      Iterator<UUID> it = START_ORDER.descendingIterator();

      while (it.hasNext()) {
         UUID id = it.next();
         ClientAnimationRuntime.InstanceState state = INSTANCES.get(id);
         if (state != null && state.isActive(now)) {
            return id;
         }
      }

      return null;
   }

   public static boolean isAnimationLooping(Identifier animationId) {
      if (animationId == null) {
         return true;
      }

      Boolean loop = KNOWN_LOOP_BY_ANIMATION_ID.get(animationId);
      return loop != null ? loop : true;
   }

   private static List<AnimationStageInfo> normalizeStages(Identifier baseAnimationId, List<AnimationStageInfo> stages) {
      return stages != null && !stages.isEmpty() ? List.copyOf(stages) : List.of(new AnimationStageInfo(baseAnimationId, true, 0L, true, 1.0));
   }

   private static double sanitizeSpeed(double speed) {
      return !Double.isNaN(speed) && !Double.isInfinite(speed) && !(speed <= 0.0) ? Math.max(0.1, Math.min(speed, 4.0)) : 1.0;
   }

   private static double exactCycleTicks(AnimationStageInfo stage) {
      if (stage == null) {
         return 0.0;
      }

      double exact = stage.cycleTicks();
      return Double.isFinite(exact) && exact > 0.0 ? exact : Math.max(0L, stage.lengthTicks());
   }

   private static Vec3d sanitizeCameraOrbitTarget(Vec3d target) {
      return target != null && target.isFinite() ? target : null;
   }

   private static void applySpeedToState(ClientAnimationRuntime.InstanceState state, ClientAnimationRuntime.PendingSpeedUpdate update) {
      state.speed = update.speed();
      state.stageStartTick = Math.max(state.startTick, update.stageStartTick());
      state.stageEndTick = update.stageEndTick();
   }

   private static void rememberStageMeta(Identifier baseAnimationId, List<AnimationStageInfo> stages) {
      if (stages != null && !stages.isEmpty()) {
         for (AnimationStageInfo stage : stages) {
            if (stage != null && stage.animationId() != null) {
               KNOWN_LOOP_BY_ANIMATION_ID.put(stage.animationId(), stage.loop());
               Identifier playbackId = stage.effectiveAnimationId();
               if (playbackId != null) {
                  KNOWN_LOOP_BY_ANIMATION_ID.put(playbackId, stage.loop());
               }
            }
         }
      } else {
         KNOWN_LOOP_BY_ANIMATION_ID.putIfAbsent(baseAnimationId, true);
      }
   }

   private static Map<String, NonAnimationRenderEvents.BoneItemProp> resolveActorBoneItems(ClientAnimationRuntime.InstanceState state, int actorIndex) {
      if (state != null && actorIndex >= 0 && actorIndex < state.actorUuids.size()) {
         String actorKey = actorIndex < state.actorKeys.size() ? state.actorKeys.get(actorIndex) : null;
         UUID actorUuid = state.actorUuids.get(actorIndex);
         LinkedHashMap<String, NonAnimationRenderEvents.BoneItemProp> out = new LinkedHashMap<>();
         AnimationStageInfo.ActorPropEntry stageProps = state.currentStage().actorPropsForKey(actorKey);
         if (stageProps != null) {
            NonAnimationRenderEvents.BoneItemProp left = stageProps.leftBoneProp();
            NonAnimationRenderEvents.BoneItemProp right = stageProps.rightBoneProp();
            NonAnimationRenderEvents.BoneItemProp floor = stageProps.floorBoneProp();
            if (left != null) {
               out.put("propleft", left);
            }

            if (right != null) {
               out.put("propright", right);
            }

            if (floor != null) {
               out.put("propfloor", floor);
            }
         }

         EnumMap<ClientAnimationRuntime.PropSlot, ItemStack> actorOverrides = state.propOverridesByActor.get(actorUuid);
         if (actorOverrides != null && !actorOverrides.isEmpty()) {
            for (Entry<ClientAnimationRuntime.PropSlot, ItemStack> entry : actorOverrides.entrySet()) {
               ClientAnimationRuntime.PropSlot slot = entry.getKey();
               ItemStack stack = entry.getValue();
               if (slot != null) {
                  if (stack != null && !stack.isEmpty()) {
                     out.put(slot.boneName(), new NonAnimationRenderEvents.BoneItemProp(stack, slot.displayContext()));
                  } else {
                     out.remove(slot.boneName());
                  }
               }
            }
         }

         return out.isEmpty() ? Map.of() : Map.copyOf(out);
      } else {
         return Map.of();
      }
   }

   private static void syncLockedOrientations(MinecraftClient client) {
      if (client != null && client.world != null) {
         if (ACTIVE_ACTOR_LATEST_ANIMATION_ID.isEmpty()) {
            LOCKED_ORIENTATION_BY_ACTOR.clear();
         } else {
            Map<UUID, ClientAnimationRuntime.LockedOrientation> rebuilt = new HashMap<>();
            Iterator<UUID> it = START_ORDER.descendingIterator();

            while (it.hasNext()) {
               UUID instanceId = it.next();
               ClientAnimationRuntime.InstanceState state = INSTANCES.get(instanceId);
               if (state != null) {
                  if (state.anchorOrientation == null) {
                     state.anchorOrientation = resolveOrientation(client, state.anchorUuid);
                  }

                  ClientAnimationRuntime.LockedOrientation anchor = state.anchorOrientation;

                  for (UUID actorUuid : state.actorUuids) {
                     if (ACTIVE_ACTOR_LATEST_ANIMATION_ID.containsKey(actorUuid) && !rebuilt.containsKey(actorUuid)) {
                        if (anchor != null) {
                           rebuilt.put(actorUuid, anchor);
                        } else if (client.world.getEntity(actorUuid) instanceof LivingEntity living) {
                           rebuilt.put(actorUuid, new ClientAnimationRuntime.LockedOrientation(living.getBodyYaw(), living.getHeadYaw(), living.getPitch()));
                        }
                     }
                  }
               }
            }

            LOCKED_ORIENTATION_BY_ACTOR.clear();
            LOCKED_ORIENTATION_BY_ACTOR.putAll(rebuilt);
         }
      }
   }

   private static ClientAnimationRuntime.LockedOrientation resolveOrientation(MinecraftClient client, UUID uuid) {
      if (uuid != null && client != null && client.world != null) {
         return client.world.getEntity(uuid) instanceof LivingEntity living
            ? new ClientAnimationRuntime.LockedOrientation(living.getBodyYaw(), living.getHeadYaw(), living.getPitch())
            : null;
      } else {
         return null;
      }
   }

   private static void tickSoundCues(MinecraftClient client, long now) {
      if (client != null && client.world != null) {
         if (!INSTANCES.isEmpty()) {
            if (AnimationSoundEffects.hasAny()) {
               ClientWorld world = client.world;
               double lookaheadWorldTicks = AnimationSoundAnticipation.lookaheadMillis() / 50.0;

               for (ClientAnimationRuntime.InstanceState state : INSTANCES.values()) {
                  if (now >= state.startTick && now < state.effectiveStopTick() && !state.actorUuids.isEmpty()) {
                     if (state.soundStageIndex != state.stageIndex) {
                        state.soundStageIndex = state.stageIndex;
                        state.lastSoundTick = -1.0;
                     }

                     if (state.anticipatedSoundStageIndex != state.stageIndex) {
                        state.anticipatedSoundStageIndex = state.stageIndex;
                        state.lastAnticipatedSoundTick = -1.0;
                     }

                     AnimationStageInfo currentStage = state.currentStage();
                     Identifier stageAnimationId = state.currentAnimationId();
                     double animTicks = Math.max(0.0, (now - state.stageStartTick) * state.speed);
                     ClientAnimationRuntime.MergedSoundTrack soundTrack = resolveMergedSoundTrack(world, state, stageAnimationId, currentStage);
                     if (soundTrack != null && !soundTrack.cues().isEmpty()) {
                        double anticipatedWorldTick = now + lookaheadWorldTicks;
                        if (state.stageEndTick > state.stageStartTick && anticipatedWorldTick >= state.stageEndTick) {
                           anticipatedWorldTick = Math.nextDown(state.stageEndTick);
                        }

                        double anticipatedTicks = Math.max(0.0, (anticipatedWorldTick - state.stageStartTick) * state.speed);
                        fireAnticipatedCues(
                           soundTrack.anchor(),
                           state,
                           soundTrack.cues(),
                           stageAnimationId,
                           state.stageIndex,
                           currentStage.loop(),
                           state.speed,
                           state.lastAnticipatedSoundTick,
                           anticipatedTicks,
                           soundTrack.cycleTicks()
                        );
                        state.lastAnticipatedSoundTick = Math.max(state.lastAnticipatedSoundTick, anticipatedTicks);
                        fireSoundCues(
                           soundTrack.anchor(),
                           state,
                           soundTrack.cues(),
                           stageAnimationId,
                           currentStage.loop(),
                           state.lastSoundTick,
                           animTicks,
                           soundTrack.cycleTicks()
                        );
                        state.lastSoundTick = animTicks;
                     }

                     anticipateNextStageCues(world, state, now, lookaheadWorldTicks);
                  }
               }
            }
         }
      }
   }

   private static ClientAnimationRuntime.MergedSoundTrack resolveMergedSoundTrack(
      ClientWorld world, ClientAnimationRuntime.InstanceState state, Identifier stageAnimationId, AnimationStageInfo stage
   ) {
      if (world != null && state != null && stageAnimationId != null && stage != null) {
         int count = state.actorUuids.size();
         if (count == 0) {
            return null;
         }

         LivingEntity anchor = resolveSoundAnchor(world, state);
         if (anchor == null) {
            return null;
         }

         List<ClientAnimationRuntime.MergedSoundCue> rawCues = new ArrayList<>();
         double maxLengthTicks = 0.0;

         for (int index : soundActorOrder(state)) {
            ClientAnimationRuntime.SoundTrackCandidate candidate = resolveSoundCandidateForIndex(world, state, stageAnimationId, index);
            if (candidate != null) {
               maxLengthTicks = Math.max(maxLengthTicks, candidate.soundAnimation().lengthTicks());

               for (AnimationSoundEffects.SoundCue cue : candidate.soundAnimation().cues()) {
                  if (cue != null) {
                     rawCues.add(new ClientAnimationRuntime.MergedSoundCue(candidate.animationResource(), candidate.animationKey(), cue, cue.timeTicks()));
                  }
               }
            }
         }

         if (rawCues.isEmpty()) {
            return null;
         }

         double exactStageCycleTicks = exactCycleTicks(stage);
         double cycleTicks = exactStageCycleTicks > 0.0 ? exactStageCycleTicks : maxLengthTicks;
         double offsetTicks = StageTimeWarp.offsetSecondsToTicks(stage.cycleMidpointOffsetSeconds());
         boolean warp = stage.loop() && StageTimeWarp.hasWarp(cycleTicks, offsetTicks);
         Map<ClientAnimationRuntime.SoundCueMergeKey, ClientAnimationRuntime.MergedSoundCue> byCue = new LinkedHashMap<>();

         for (ClientAnimationRuntime.MergedSoundCue rawCue : rawCues) {
            double cueTimeTicks = warp ? warpCueTick(rawCue.cue().timeTicks(), cycleTicks, offsetTicks) : rawCue.cue().timeTicks();
            long timeKey = soundCueTimeKey(cueTimeTicks);
            ClientAnimationRuntime.SoundCueMergeKey mergeKey = new ClientAnimationRuntime.SoundCueMergeKey(
               timeKey, AnimationCueEffects.timingFamily(rawCue.cue().effect()), rawCue.cue().randomSounds()
            );
            byCue.putIfAbsent(
               mergeKey, new ClientAnimationRuntime.MergedSoundCue(rawCue.animationResource(), rawCue.animationKey(), rawCue.cue(), cueTimeTicks)
            );
         }

         if (byCue.isEmpty()) {
            return null;
         }

         List<ClientAnimationRuntime.MergedSoundCue> cues = new ArrayList<>(byCue.values());
         cues.sort(Comparator.comparingDouble(ClientAnimationRuntime.MergedSoundCue::timeTicks));
         return new ClientAnimationRuntime.MergedSoundTrack(anchor, List.copyOf(cues), cycleTicks);
      } else {
         return null;
      }
   }

   private static LivingEntity resolveSoundAnchor(ClientWorld world, ClientAnimationRuntime.InstanceState state) {
      if (world != null && state != null) {
         if (state.anchorUuid != null && world.getEntity(state.anchorUuid) instanceof LivingEntity living) {
            return living;
         }

         for (UUID actorId : state.actorUuids) {
            if (world.getEntity(actorId) instanceof LivingEntity living) {
               return living;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static List<Integer> soundActorOrder(ClientAnimationRuntime.InstanceState state) {
      if (state != null && !state.actorUuids.isEmpty()) {
         List<Integer> order = new ArrayList<>(state.actorUuids.size());
         int anchorIndex = -1;
         if (state.anchorUuid != null) {
            for (int i = 0; i < state.actorUuids.size(); i++) {
               if (state.actorUuids.get(i).equals(state.anchorUuid)) {
                  anchorIndex = i;
                  break;
               }
            }
         }

         if (anchorIndex >= 0) {
            order.add(anchorIndex);
         }

         for (int i = 0; i < state.actorUuids.size(); i++) {
            if (i != anchorIndex) {
               order.add(i);
            }
         }

         return order;
      } else {
         return List.of();
      }
   }

   private static long soundCueTimeKey(double timeTicks) {
      return !Double.isFinite(timeTicks) ? Long.MIN_VALUE : Math.round(timeTicks * 1000.0);
   }

   private static double warpCueTick(double cueTick, double cycleTicks, double offsetTicks) {
      if (!StageTimeWarp.hasWarp(cycleTicks, offsetTicks)) {
         return cueTick;
      }

      double warped = StageTimeWarp.authoredToWarpedCycleTicks(cueTick, cycleTicks, offsetTicks);
      return cueTick > 0.0 && Math.abs(cueTick - cycleTicks) < 1.0E-4 ? cycleTicks : warped;
   }

   private static ClientAnimationRuntime.SoundTrackCandidate resolveSoundCandidateForIndex(
      ClientWorld world, ClientAnimationRuntime.InstanceState state, Identifier stageAnimationId, int index
   ) {
      if (world == null || state == null || stageAnimationId == null) {
         return null;
      }

      if (index >= 0 && index < state.actorUuids.size()) {
         UUID actorId = state.actorUuids.get(index);
         if (world.getEntity(actorId) instanceof LivingEntity anchor) {
            Identifier typeId = Registries.ENTITY_TYPE.getId(anchor.getType());
            String actorKey = index < state.actorKeys.size() ? state.actorKeys.get(index) : null;
            ClientAnimationRuntime.SoundTrackCandidate candidate = resolveSoundCandidateForParams(stageAnimationId, actorKey, typeId, true);
            if (candidate != null) {
               return candidate;
            }

            if (anchor instanceof PlayerEntity) {
               Identifier playerType = Identifier.of("minecraft", "player");
               candidate = resolveSoundCandidateForParams(stageAnimationId, "player", playerType, false);
               if (candidate != null) {
                  return candidate;
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

   private static ClientAnimationRuntime.SoundTrackCandidate resolveSoundCandidateForParams(
      Identifier stageAnimationId, String actorKey, Identifier typeId, boolean warnMissing
   ) {
      if (stageAnimationId == null) {
         return null;
      }

      Identifier animationResource = GeckoResourceResolver.resolveAnimationResource(stageAnimationId, actorKey, typeId, warnMissing);
      if (animationResource == null) {
         return null;
      }

      String animationKey = GeckoResourceResolver.resolveAnimationKey(animationResource, stageAnimationId, actorKey, typeId, warnMissing);
      if (animationKey == null) {
         return null;
      }

      AnimationSoundEffects.SoundAnimation soundAnimation = AnimationSoundEffects.getSoundAnimation(animationResource, animationKey);
      return soundAnimation != null && !soundAnimation.cues().isEmpty()
         ? new ClientAnimationRuntime.SoundTrackCandidate(animationResource, animationKey, soundAnimation)
         : null;
   }

   private static void anticipateNextStageCues(ClientWorld world, ClientAnimationRuntime.InstanceState state, long now, double lookaheadWorldTicks) {
      if (!(lookaheadWorldTicks <= 0.0)) {
         if (state.stageEndTick > state.stageStartTick) {
            if (AnimationSoundAnticipation.allowNextStageLookahead(state.currentStage().animationId(), state.stageIndex)) {
               double anticipatedWorldTick = now + lookaheadWorldTicks;
               if (!(anticipatedWorldTick < state.stageEndTick)) {
                  int nextStageIndex = state.stageIndex + 1;
                  if (nextStageIndex < state.stages.size()) {
                     if (state.anticipatedNextSoundStageIndex != nextStageIndex) {
                        state.anticipatedNextSoundStageIndex = nextStageIndex;
                        state.lastAnticipatedNextSoundTick = -1.0;
                     }

                     AnimationStageInfo nextStage = state.stages.get(nextStageIndex);
                     Identifier nextAnimationId = nextStage.effectiveAnimationId();
                     ClientAnimationRuntime.MergedSoundTrack nextTrack = resolveMergedSoundTrack(world, state, nextAnimationId, nextStage);
                     if (nextTrack != null && !nextTrack.cues().isEmpty()) {
                        double nextSpeed = sanitizeSpeed(nextStage.speed());
                        double anticipatedTicks = Math.max(0.0, (anticipatedWorldTick - state.stageEndTick) * nextSpeed);
                        if (!nextStage.loop() && nextTrack.cycleTicks() > 0.0) {
                           anticipatedTicks = Math.min(anticipatedTicks, nextTrack.cycleTicks());
                        }

                        fireAnticipatedCues(
                           nextTrack.anchor(),
                           state,
                           nextTrack.cues(),
                           nextAnimationId,
                           nextStageIndex,
                           nextStage.loop(),
                           nextSpeed,
                           state.lastAnticipatedNextSoundTick,
                           anticipatedTicks,
                           nextTrack.cycleTicks()
                        );
                        state.lastAnticipatedNextSoundTick = Math.max(state.lastAnticipatedNextSoundTick, anticipatedTicks);
                     }
                  }
               }
            }
         }
      }
   }

   private static void fireAnticipatedCues(
      LivingEntity anchor,
      ClientAnimationRuntime.InstanceState state,
      List<ClientAnimationRuntime.MergedSoundCue> cues,
      Identifier stageAnimationId,
      int stageIndex,
      boolean loop,
      double speed,
      double lastTicks,
      double anticipatedTicks,
      double cycleTicks
   ) {
      if (!(lastTicks >= 0.0) || !(anticipatedTicks <= lastTicks + 1.0E-4)) {
         visitSoundCues(
            cues,
            loop,
            lastTicks,
            anticipatedTicks,
            cycleTicks,
            mergedCue -> anticipateSoundCue(anchor, state, mergedCue, stageAnimationId, cues, stageIndex, speed, loop, cycleTicks)
         );
      }
   }

   private static void anticipateSoundCue(
      LivingEntity anchor,
      ClientAnimationRuntime.InstanceState state,
      ClientAnimationRuntime.MergedSoundCue mergedCue,
      Identifier stageAnimationId,
      List<ClientAnimationRuntime.MergedSoundCue> cues,
      int stageIndex,
      double speed,
      boolean loop,
      double cycleTicks
   ) {
      if (mergedCue != null && mergedCue.cue() != null) {
         String effect = mergedCue.cue().effect();
         if (effect != null && !effect.isBlank()) {
            AnimationSoundAnticipation.anticipate(
               createSoundContext(anchor, state, mergedCue, stageAnimationId, cues, stageIndex, speed, effect, loop, cycleTicks)
            );
         }
      }
   }

   private static void fireSoundCues(
      LivingEntity anchor,
      ClientAnimationRuntime.InstanceState state,
      List<ClientAnimationRuntime.MergedSoundCue> cues,
      Identifier stageAnimationId,
      boolean loop,
      double lastTicks,
      double animTicks,
      double cycleTicks
   ) {
      visitSoundCues(
         cues, loop, lastTicks, animTicks, cycleTicks, mergedCue -> playSoundCue(anchor, state, mergedCue, stageAnimationId, cues, loop, cycleTicks)
      );
   }

   private static void visitSoundCues(
      List<ClientAnimationRuntime.MergedSoundCue> cues,
      boolean loop,
      double lastTicks,
      double animTicks,
      double cycleTicks,
      ClientAnimationRuntime.SoundCueVisitor visitor
   ) {
      if (loop && !(cycleTicks <= 0.0)) {
         long lastCycle = lastTicks < 0.0 ? -1L : (long)Math.floor(lastTicks / cycleTicks);
         long currentCycle = (long)Math.floor(animTicks / cycleTicks);
         double lastCyclePos = lastCycle < 0L ? -1.0 : lastTicks - lastCycle * cycleTicks;
         double currentCyclePos = animTicks - currentCycle * cycleTicks;
         if (lastCycle < 0L) {
            lastCycle = currentCycle;
         }

         if (currentCycle == lastCycle) {
            visitSoundCueWindow(cues, lastCyclePos, currentCyclePos, visitor);
         } else {
            visitSoundCueWindow(cues, lastCyclePos, cycleTicks, visitor);
            long maxExtraCycles = Math.min(2L, currentCycle - lastCycle - 1L);

            for (long i = 0L; i < maxExtraCycles; i++) {
               visitSoundCueWindow(cues, -1.0, cycleTicks, visitor);
            }

            visitSoundCueWindow(cues, -1.0, currentCyclePos, visitor);
         }
      } else {
         visitSoundCueWindow(cues, lastTicks, animTicks, visitor);
      }
   }

   private static void visitSoundCueWindow(
      List<ClientAnimationRuntime.MergedSoundCue> cues, double fromTick, double toTick, ClientAnimationRuntime.SoundCueVisitor visitor
   ) {
      for (ClientAnimationRuntime.MergedSoundCue mergedCue : cues) {
         double cueTick = mergedCue.timeTicks();
         if (!(cueTick > toTick) && (!(fromTick >= 0.0) || !(cueTick <= fromTick))) {
            visitor.visit(mergedCue);
         }
      }
   }

   private static void playSoundCue(
      LivingEntity anchor,
      ClientAnimationRuntime.InstanceState state,
      ClientAnimationRuntime.MergedSoundCue mergedCue,
      Identifier stageAnimationId,
      List<ClientAnimationRuntime.MergedSoundCue> cues,
      boolean loop,
      double cycleTicks
   ) {
      if (mergedCue != null && mergedCue.cue() != null) {
         AnimationSoundEffects.SoundCue cue = mergedCue.cue();
         String effect = selectCueEffect(anchor, cue);
         if (effect != null && !effect.isBlank()) {
            NonAnimationSoundEvents.SoundContext context = createSoundContext(
               anchor, state, mergedCue, stageAnimationId, cues, state.stageIndex, state.speed, effect, loop, cycleTicks
            );
            NonAnimationSoundEvents.SoundOverride override = ((NonAnimationSoundEvents.SoundResolver)NonAnimationSoundEvents.RESOLVE.invoker())
               .resolve(context);
            if (override != null) {
               if (override.cancel()) {
                  return;
               }

               Identifier overrideId = override.soundId();
               if (overrideId != null) {
                  playSound(anchor, overrideId, override.volume(), override.pitch());
                  return;
               }
            }

            Identifier fallback = resolveEffectAsSoundId(effect, stageAnimationId, mergedCue.animationResource());
            if (fallback != null) {
               playSound(anchor, fallback, cue.volume(), cue.pitch());
            }
         }
      }
   }

   private static NonAnimationSoundEvents.SoundContext createSoundContext(
      LivingEntity anchor,
      ClientAnimationRuntime.InstanceState state,
      ClientAnimationRuntime.MergedSoundCue mergedCue,
      Identifier stageAnimationId,
      List<ClientAnimationRuntime.MergedSoundCue> cues,
      int stageIndex,
      double speed,
      String effect,
      boolean loop,
      double cycleTicks
   ) {
      AnimationSoundEffects.SoundCue cue = mergedCue.cue();
      double nextDelayTicks = nextSameEffectCueDelayTicks(cues, mergedCue, loop, cycleTicks);
      double safeSpeed = Math.max(1.0E-4, sanitizeSpeed(speed));
      double nextDelayMs = nextDelayTicks > 0.0 ? nextDelayTicks / safeSpeed * 50.0 : 0.0;
      return new NonAnimationSoundEvents.SoundContext(
         anchor,
         stageAnimationId,
         mergedCue.animationResource(),
         mergedCue.animationKey(),
         effect,
         mergedCue.timeTicks() / 20.0,
         cue.volume(),
         cue.pitch(),
         state.instanceId,
         stageIndex,
         loop,
         mergedCue.timeTicks(),
         cycleTicks,
         safeSpeed,
         nextDelayTicks,
         nextDelayMs
      );
   }

   private static String selectCueEffect(LivingEntity anchor, AnimationSoundEffects.SoundCue cue) {
      if (cue == null) {
         return null;
      } else {
         List<Identifier> sounds = cue.randomSounds();
         if (sounds != null && !sounds.isEmpty()) {
            int index = anchor.getEntityWorld().getRandom().nextInt(sounds.size());
            return sounds.get(index).toString();
         } else {
            return cue.effect();
         }
      }
   }

   private static double nextSameEffectCueDelayTicks(
      List<ClientAnimationRuntime.MergedSoundCue> cues, ClientAnimationRuntime.MergedSoundCue currentCue, boolean loop, double cycleTicks
   ) {
      if (cues != null && !cues.isEmpty() && currentCue != null) {
         if (currentCue.cue() == null) {
            return 0.0;
         }

         String effectFamily = AnimationCueEffects.timingFamily(currentCue.cue().effect());
         if (effectFamily == null) {
            return 0.0;
         }

         double currentTick = currentCue.timeTicks();
         double epsilon = 1.0E-4;
         ClientAnimationRuntime.MergedSoundCue firstSame = null;

         for (ClientAnimationRuntime.MergedSoundCue candidate : cues) {
            if (candidate != null && candidate.cue() != null) {
               AnimationSoundEffects.SoundCue cue = candidate.cue();
               if (effectFamily.equals(AnimationCueEffects.timingFamily(cue.effect()))) {
                  if (firstSame == null || candidate.timeTicks() < firstSame.timeTicks()) {
                     firstSame = candidate;
                  }

                  if (candidate.timeTicks() > currentTick + 1.0E-4) {
                     return Math.max(0.0, candidate.timeTicks() - currentTick);
                  }
               }
            }
         }

         if (loop && !(cycleTicks <= 0.0) && firstSame != null) {
            double wrappedDelay = cycleTicks - currentTick + firstSame.timeTicks();
            if (wrappedDelay <= 1.0E-4) {
               wrappedDelay = cycleTicks;
            }

            return Math.max(0.0, wrappedDelay);
         } else {
            return 0.0;
         }
      } else {
         return 0.0;
      }
   }

   private static Identifier resolveEffectAsSoundId(String effect, Identifier stageAnimationId, Identifier animationResource) {
      if (effect != null && !effect.isBlank()) {
         Identifier direct = Identifier.tryParse(effect);
         if (direct != null) {
            return direct;
         }

         String namespace = null;
         if (stageAnimationId != null) {
            namespace = stageAnimationId.getNamespace();
         } else if (animationResource != null) {
            namespace = animationResource.getNamespace();
         }

         return namespace != null && !namespace.isBlank() ? Identifier.tryParse(namespace + ":" + effect) : null;
      } else {
         return null;
      }
   }

   private static void playSound(LivingEntity anchor, Identifier soundId, float volume, float pitch) {
      if (anchor != null && soundId != null) {
         ClientWorld world = anchor.getEntityWorld() instanceof ClientWorld clientWorld ? clientWorld : null;
         if (world != null) {
            SoundEvent soundEvent = Registries.SOUND_EVENT.containsId(soundId) ? (SoundEvent)Registries.SOUND_EVENT.get(soundId) : SoundEvent.of(soundId);
            SoundCategory category = anchor instanceof PlayerEntity ? SoundCategory.PLAYERS : SoundCategory.NEUTRAL;
            world.playSoundFromEntityClient(anchor, soundEvent, category, volume, pitch);
         }
      }
   }

   private static final class InstanceState {
      final UUID instanceId;
      final Identifier animationId;
      final List<List<Identifier>> modelRootsByActor;
      final List<UUID> actorUuids;
      final Set<UUID> actorUuidSet;
      final List<String> actorKeys;
      final UUID anchorUuid;
      ClientAnimationRuntime.LockedOrientation anchorOrientation;
      final Vec3d cameraOrbitTarget;
      final long startTick;
      final List<AnimationStageInfo> stages;
      int stageIndex = 0;
      long stageStartTick;
      long stageEndTick;
      int manualAdvanceRequests = 0;
      double speed;
      int soundStageIndex = -1;
      double lastSoundTick = -1.0;
      int anticipatedSoundStageIndex = -1;
      double lastAnticipatedSoundTick = -1.0;
      int anticipatedNextSoundStageIndex = -1;
      double lastAnticipatedNextSoundTick = -1.0;
      final Map<UUID, EnumMap<ClientAnimationRuntime.PropSlot, ItemStack>> propOverridesByActor = new HashMap<>();
      Long stopAtTick;

      InstanceState(
         UUID instanceId,
         Identifier animationId,
         List<List<Identifier>> modelRootsByActor,
         List<UUID> actorUuids,
         List<String> actorKeys,
         List<AnimationStageInfo> stages,
         long startTick,
         int initialStageIndex,
         long initialStageStartTick,
         long initialStageEndTick,
         double speed,
         UUID anchorUuid,
         ClientAnimationRuntime.LockedOrientation anchorOrientation,
         Vec3d cameraOrbitTarget
      ) {
         this.instanceId = instanceId;
         this.animationId = animationId;
         this.actorUuids = List.copyOf(actorUuids);
         this.modelRootsByActor = modelRootsByActor == null
            ? Collections.nCopies(this.actorUuids.size(), List.of())
            : modelRootsByActor.stream().map(roots -> roots == null ? List.<Identifier>of() : List.<Identifier>copyOf((Collection<? extends Identifier>)roots)).toList();
         if (this.modelRootsByActor.size() != this.actorUuids.size()) {
            throw new IllegalArgumentException("modelRootsByActor size mismatch: " + this.modelRootsByActor.size());
         }

         this.actorUuidSet = Set.copyOf(this.actorUuids);
         this.actorKeys = actorKeys == null ? List.of() : List.copyOf(actorKeys);
         this.startTick = startTick;
         this.stages = ClientAnimationRuntime.normalizeStages(animationId, stages);
         this.stageIndex = this.stages.isEmpty() ? 0 : Math.max(0, Math.min(initialStageIndex, this.stages.size() - 1));
         this.stageStartTick = Math.max(startTick, initialStageStartTick);
         this.stageEndTick = initialStageEndTick;
         this.anchorUuid = anchorUuid;
         this.anchorOrientation = anchorOrientation;
         this.cameraOrbitTarget = cameraOrbitTarget;
         this.speed = ClientAnimationRuntime.sanitizeSpeed(speed);
      }

      long effectiveStopTick() {
         return this.stopAtTick != null ? this.stopAtTick : Long.MAX_VALUE;
      }

      boolean isActive(long nowTick) {
         return nowTick < this.startTick ? false : nowTick < this.effectiveStopTick();
      }

      boolean containsActor(UUID actorUuid) {
         return this.actorUuidSet.contains(actorUuid);
      }

      List<Identifier> modelRootsForActor(int actorIndex) {
         return actorIndex >= 0 && actorIndex < this.modelRootsByActor.size() ? this.modelRootsByActor.get(actorIndex) : List.of();
      }

      AnimationStageInfo currentStage() {
         return this.stages.isEmpty() ? new AnimationStageInfo(this.animationId, true, 0L, true, 1.0) : this.stages.get(this.stageIndex);
      }

      Identifier currentAnimationId() {
         return this.currentStage().effectiveAnimationId();
      }

      boolean advanceStage(long nextStageStartTick) {
         if (this.stageIndex + 1 >= this.stages.size()) {
            return false;
         }

         this.stageIndex++;
         this.stageStartTick = nextStageStartTick;
         this.stageEndTick = -1L;
         this.speed = ClientAnimationRuntime.sanitizeSpeed(this.stages.get(this.stageIndex).speed());
         this.resetSoundTimelinesForStage(this.stageIndex);
         return true;
      }

      boolean setStageIndex(int targetStageIndex, long nextStageStartTick, long nextStageEndTick) {
         if (targetStageIndex >= 0 && targetStageIndex < this.stages.size()) {
            this.stageIndex = targetStageIndex;
            this.stageStartTick = nextStageStartTick;
            this.stageEndTick = nextStageEndTick;
            this.speed = ClientAnimationRuntime.sanitizeSpeed(this.stages.get(this.stageIndex).speed());
            this.resetSoundTimelinesForStage(this.stageIndex);
            return true;
         } else {
            return false;
         }
      }

      private void resetSoundTimelinesForStage(int nextStageIndex) {
         this.soundStageIndex = nextStageIndex;
         this.lastSoundTick = -1.0;
         if (this.anticipatedNextSoundStageIndex == nextStageIndex) {
            this.anticipatedSoundStageIndex = nextStageIndex;
            this.lastAnticipatedSoundTick = this.lastAnticipatedNextSoundTick;
         } else {
            this.anticipatedSoundStageIndex = nextStageIndex;
            this.lastAnticipatedSoundTick = -1.0;
         }

         this.anticipatedNextSoundStageIndex = -1;
         this.lastAnticipatedNextSoundTick = -1.0;
      }
   }

   public record LockedOrientation(float bodyYaw, float headYaw, float pitch) {
   }

   private record MergedSoundCue(Identifier animationResource, String animationKey, AnimationSoundEffects.SoundCue cue, double timeTicks) {
   }

   private record MergedSoundTrack(LivingEntity anchor, List<ClientAnimationRuntime.MergedSoundCue> cues, double cycleTicks) {
   }

   private record PendingSpeedUpdate(double speed, long stageStartTick, long stageEndTick) {
   }

   private record PendingStageAdvance(UUID instanceId, long advanceTick, long stageEndTick, int stageIndex) {
   }

   private record PendingStart(
      UUID instanceId,
      Identifier animationId,
      List<List<Identifier>> modelRootsByActor,
      List<UUID> actorUuids,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      long startTick,
      int stageIndex,
      long stageStartTick,
      long stageEndTick,
      double speed,
      UUID anchorUuid,
      boolean lockOrientation,
      ClientAnimationRuntime.LockedOrientation lockedOrientation,
      Vec3d cameraOrbitTarget
   ) {
   }

   public enum PropSlot {
      LEFT("propleft", ItemDisplayContext.THIRD_PERSON_LEFT_HAND),
      RIGHT("propright", ItemDisplayContext.THIRD_PERSON_RIGHT_HAND),
      FLOOR("propfloor", ItemDisplayContext.GROUND);

      private final String boneName;
      private final ItemDisplayContext displayContext;

      PropSlot(String boneName, ItemDisplayContext displayContext) {
         this.boneName = boneName;
         this.displayContext = displayContext;
      }

      public String boneName() {
         return this.boneName;
      }

      public ItemDisplayContext displayContext() {
         return this.displayContext;
      }
   }

   private record RenderHold(
      UUID instanceId,
      Identifier animationId,
      List<Identifier> modelRoots,
      String actorKey,
      double speed,
      Map<String, NonAnimationRenderEvents.BoneItemProp> boneItems,
      ClientAnimationRuntime.LockedOrientation lockedOrientation,
      long expiresAtTick
   ) {
   }

   private record SoundCueMergeKey(long timeKey, @Nullable String effectFamily, List<Identifier> randomSounds) {
      private SoundCueMergeKey {
         randomSounds = randomSounds == null ? List.of() : List.copyOf(randomSounds);
      }
   }

   @FunctionalInterface
   private interface SoundCueVisitor {
      void visit(ClientAnimationRuntime.MergedSoundCue var1);
   }

   private record SoundTrackCandidate(Identifier animationResource, String animationKey, AnimationSoundEffects.SoundAnimation soundAnimation) {
   }

   public record StageTimelineSnapshot(List<AnimationStageInfo> stages, int stageIndex, long stageStartTick, long stageEndTick, double speed) {
   }
}
