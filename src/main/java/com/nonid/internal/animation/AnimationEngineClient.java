package com.nonid.internal.animation;

import com.nonid.NeedsOfNature;
import com.nonid.NonDebugChatCategory;
import com.nonid.NonDiagnostics;
import com.nonid.api.animation.NonAnimationDamageBehavior;
import com.nonid.client.NonClientDiagnostics;
import com.nonid.internal.animation.client.camera.AnimatedCameraPoseTracker;
import com.nonid.internal.animation.client.camera.AnimationCameraZoom;
import com.nonid.internal.animation.client.diagnostics.AnimationAssetDiagnostics;
import com.nonid.internal.animation.client.render.gecko.BoneTextureOverrides;
import com.nonid.internal.animation.client.render.gecko.VanillaTextureResolver;
import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import com.nonid.internal.animation.client.sound.AnimationSoundEffects;
import com.nonid.internal.animation.network.AdjustAnimationSpeedC2SPayload;
import com.nonid.internal.animation.network.AdvanceAnimationStageS2CPayload;
import com.nonid.internal.animation.network.AnimationSpeedUpdateS2CPayload;
import com.nonid.internal.animation.network.DebugAdvanceStageC2SPayload;
import com.nonid.internal.animation.network.DebugChatPreferenceC2SPayload;
import com.nonid.internal.animation.network.DebugJoinAnimationC2SPayload;
import com.nonid.internal.animation.network.DebugStartAnimationC2SPayload;
import com.nonid.internal.animation.network.DebugStopAllAnimationsC2SPayload;
import com.nonid.internal.animation.network.DebugStopAnimationC2SPayload;
import com.nonid.internal.animation.network.StartAnimationS2CPayload;
import com.nonid.internal.animation.network.StopAllAnimationsS2CPayload;
import com.nonid.internal.animation.network.StopAnimationS2CPayload;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

public final class AnimationEngineClient {
   private static final Category KEY_CATEGORY = Category.create(Identifier.of("needsofnature", "animation"));
   private static final KeyBinding SELECT_ACTOR_KEY = KeyBindingHelper.registerKeyBinding(createUnboundDebugKey("key.needsofnature.animation.select_actor"));
   private static final KeyBinding SELECT_SELF_KEY = KeyBindingHelper.registerKeyBinding(createUnboundDebugKey("key.needsofnature.animation.select_self"));
   private static final KeyBinding START_DEBUG_KEY = KeyBindingHelper.registerKeyBinding(
      createUnboundDebugKey("key.needsofnature.animation.start_debug_animation")
   );
   private static final KeyBinding STOP_ALL_DEBUG_KEY = KeyBindingHelper.registerKeyBinding(createUnboundDebugKey("key.needsofnature.animation.stop_all_debug"));
   private static final KeyBinding STOP_INSTANCE_DEBUG_KEY = KeyBindingHelper.registerKeyBinding(
      createUnboundDebugKey("key.needsofnature.animation.stop_instance_debug")
   );
   private static final KeyBinding NEXT_STAGE_DEBUG_KEY = KeyBindingHelper.registerKeyBinding(
      createUnboundDebugKey("key.needsofnature.animation.next_stage_debug")
   );
   private static final KeyBinding SPEED_UP_DEBUG_KEY = KeyBindingHelper.registerKeyBinding(createUnboundDebugKey("key.needsofnature.animation.speed_up_debug"));
   private static final KeyBinding SPEED_DOWN_DEBUG_KEY = KeyBindingHelper.registerKeyBinding(
      createUnboundDebugKey("key.needsofnature.animation.speed_down_debug")
   );
   private static final IntArrayList SELECTED_ACTOR_IDS = new IntArrayList();
   private static UUID SELECTED_INSTANCE_ID = null;
   private static boolean wasSelfAnimating = false;
   private static boolean ownsCameraPerspective = false;
   private static Perspective preAnimationPerspective = null;
   private static final double PLAYER_ANCHOR_EPSILON_SQ = 4.0E-4;

   public static void sendClientDebugChat(NonDebugChatCategory category, Text message) {
      NonClientDiagnostics.send(category, message, false);
   }

   public static void sendClientSetupWarning(String message) {
      NonClientDiagnostics.sendSetupWarning(message, false);
   }

   private static KeyBinding createUnboundDebugKey(String translationKey) {
      return new KeyBinding(translationKey, Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), KEY_CATEGORY);
   }

   private AnimationEngineClient() {
   }

   public static void initialize() {
      VanillaTextureResolver.registerReloadListener();
      BoneTextureOverrides.registerReloadListener();
      AnimationSoundEffects.registerReloadListener();
      AnimationAssetDiagnostics.registerReloadListener();
      ClientPlayConnectionEvents.JOIN.register((Join)(handler, sender, client) -> sendDebugChatPreference());
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
         ClientAnimationRuntime.stopAllNow();
         AnimatedCameraPoseTracker.clear();
         AnimationCameraZoom.reset();
         resetCameraPerspective(client);
      });
      ClientPlayNetworking.registerGlobalReceiver(
         StartAnimationS2CPayload.ID,
         (payload, context) -> context.client()
            .execute(
               () -> {
                  ClientAnimationRuntime.queueStart(
                     payload.animationId(),
                     payload.modelRootsByActor(),
                     payload.instanceId(),
                     payload.actorUuids(),
                     payload.actorKeys(),
                     payload.stages(),
                     payload.startTick(),
                     payload.stageIndex(),
                     payload.stageStartTick(),
                     payload.stageEndTick(),
                     payload.speed(),
                     payload.lockOrientation(),
                     payload.lockedYaw(),
                     payload.lockedHeadYaw(),
                     payload.lockedPitch(),
                     payload.cameraOrbitTarget()
                  );
                  MinecraftClient client = context.client();
                  PlayerEntity player = client.player;
                  if (player != null && NonClientDiagnostics.shouldSend(NonDebugChatCategory.INFO)) {
                     String keysStr = payload.actorKeys().isEmpty() ? "none" : payload.actorKeys().toString();
                     player.sendMessage(tr("start_received", payload.animationId(), payload.actorUuids().size(), keysStr), false);
                  }
               }
            )
      );
      ClientPlayNetworking.registerGlobalReceiver(StopAllAnimationsS2CPayload.ID, (payload, context) -> context.client().execute(() -> {
         ClientAnimationRuntime.queueStopAll(payload.stopTick());
         MinecraftClient client = context.client();
         PlayerEntity player = client.player;
         if (player != null && NonClientDiagnostics.shouldSend(NonDebugChatCategory.INFO)) {
            player.sendMessage(tr("stop_all_received"), false);
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         AdvanceAnimationStageS2CPayload.ID,
         (payload, context) -> context.client()
            .execute(() -> ClientAnimationRuntime.queueStageAdvance(payload.instanceId(), payload.advanceTick(), payload.stageEndTick(), payload.stageIndex()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         AnimationSpeedUpdateS2CPayload.ID,
         (payload, context) -> context.client()
            .execute(() -> ClientAnimationRuntime.queueSpeedUpdate(payload.instanceId(), payload.speed(), payload.stageStartTick(), payload.stageEndTick()))
      );
      ClientPlayNetworking.registerGlobalReceiver(StopAnimationS2CPayload.ID, (payload, context) -> context.client().execute(() -> {
         ClientAnimationRuntime.queueStop(payload.instanceId(), payload.stopTick());
         MinecraftClient client = context.client();
         PlayerEntity player = client.player;
         if (player != null && NonClientDiagnostics.shouldSend(NonDebugChatCategory.INFO)) {
            player.sendMessage(tr("stop_instance_received"), false);
         }
      }));
      ClientTickEvents.END_CLIENT_TICK.register(AnimationEngineClient::onClientTick);
   }

   public static void sendDebugChatPreference() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null && ClientPlayNetworking.canSend(DebugChatPreferenceC2SPayload.ID)) {
         ClientPlayNetworking.send(new DebugChatPreferenceC2SPayload(NeedsOfNature.getConfig().debugChatMode().id()));
      }
   }

   private static void onClientTick(MinecraftClient client) {
      if (client.player != null && client.world != null) {
         if (client.currentScreen == null) {
            while (SELECT_ACTOR_KEY.wasPressed()) {
               toggleTargetedActorSelection(client);
            }

            while (SELECT_SELF_KEY.wasPressed()) {
               toggleSelfSelection(client);
            }

            while (START_DEBUG_KEY.wasPressed()) {
               requestDebugStart(client);
            }

            while (STOP_ALL_DEBUG_KEY.wasPressed()) {
               requestStopAll(client);
            }

            while (STOP_INSTANCE_DEBUG_KEY.wasPressed()) {
               requestStopInstance(client);
            }

            while (NEXT_STAGE_DEBUG_KEY.wasPressed()) {
               requestNextStage(client);
            }

            while (SPEED_UP_DEBUG_KEY.wasPressed()) {
               requestSpeedAdjust(client, 1.1);
            }

            while (SPEED_DOWN_DEBUG_KEY.wasPressed()) {
               requestSpeedAdjust(client, 0.9);
            }
         }

         ClientAnimationRuntime.clientTick(client);
         AnimationCameraZoom.clientTick(client);
         AnimationAssetDiagnostics.validateLoadedDefinitionsOnce();
         AnimatedCameraPoseTracker.prune(client.world.getTime());
         handleCompletedStages(client);
         syncCameraPerspective(client);
         syncLocalPlayerToAnchor(client);
      }
   }

   private static void requestStopAll(MinecraftClient client) {
      PlayerEntity player = client.player;
      if (player != null) {
         if (!ClientPlayNetworking.canSend(DebugStopAllAnimationsC2SPayload.ID)) {
            sendManualDebug(player, tr("server_cannot_receive_stop_all"));
            ClientAnimationRuntime.stopAllNow();
         } else {
            sendManualDebug(player, tr("stop_all_requested"));
            ClientPlayNetworking.send(new DebugStopAllAnimationsC2SPayload());
         }
      }
   }

   private static void requestStopInstance(MinecraftClient client) {
      PlayerEntity player = client.player;
      if (player != null) {
         UUID self = player.getUuid();
         UUID instanceToStop = ClientAnimationRuntime.findLatestActiveInstanceContaining(self);
         if (instanceToStop == null) {
            instanceToStop = ClientAnimationRuntime.findLatestActiveInstance();
         }

         if (instanceToStop == null) {
            sendManualDebug(player, tr("no_active_instance_to_stop"));
         } else if (!ClientPlayNetworking.canSend(DebugStopAnimationC2SPayload.ID)) {
            sendManualDebug(player, tr("server_cannot_receive_stop_instance"));
         } else {
            sendManualDebug(player, tr("stop_instance_requested"));
            ClientPlayNetworking.send(new DebugStopAnimationC2SPayload(instanceToStop));
         }
      }
   }

   private static void toggleTargetedActorSelection(MinecraftClient client) {
      PlayerEntity player = client.player;
      if (player != null) {
         if (client.crosshairTarget instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            if (!(target instanceof LivingEntity)) {
               sendManualDebug(player, tr("target_not_living"));
            } else {
               int id = target.getId();
               Identifier typeId = Registries.ENTITY_TYPE.getId(target.getType());
               UUID instId = ClientAnimationRuntime.findLatestActiveInstanceContaining(target.getUuid());
               if (instId != null) {
                  if (SELECTED_INSTANCE_ID != null && SELECTED_INSTANCE_ID.equals(instId)) {
                     SELECTED_INSTANCE_ID = null;
                     SELECTED_ACTOR_IDS.clear();
                     sendManualDebug(player, tr("animation_deselected"));
                  } else {
                     SELECTED_INSTANCE_ID = instId;
                     SELECTED_ACTOR_IDS.clear();
                     sendManualDebug(player, tr("animation_selected_add_actors_for_join"));
                  }
               } else {
                  int existingIndex = indexOfSelectedActor(id);
                  if (existingIndex >= 0) {
                     SELECTED_ACTOR_IDS.removeInt(existingIndex);
                     sendManualDebug(player, tr("entity_deselected_actor_count", typeId.getPath(), SELECTED_ACTOR_IDS.size()));
                  } else {
                     SELECTED_ACTOR_IDS.add(id);
                     sendManualDebug(player, tr("entity_selected_actor_count", typeId.getPath(), SELECTED_ACTOR_IDS.size()));
                  }
               }
            }
         } else {
            sendManualDebug(player, tr("no_entity_targeted"));
         }
      }
   }

   private static void toggleSelfSelection(MinecraftClient client) {
      PlayerEntity player = client.player;
      if (player != null) {
         int id = player.getId();
         int existingIndex = indexOfSelectedActor(id);
         if (existingIndex >= 0) {
            SELECTED_ACTOR_IDS.removeInt(existingIndex);
            sendManualDebug(player, tr("self_deselected_actor_count", SELECTED_ACTOR_IDS.size()));
         } else {
            SELECTED_ACTOR_IDS.add(id);
            sendManualDebug(player, tr("self_selected_actor_count", SELECTED_ACTOR_IDS.size()));
         }
      }
   }

   private static void requestDebugStart(MinecraftClient client) {
      PlayerEntity player = client.player;
      if (player != null) {
         if (SELECTED_INSTANCE_ID == null && SELECTED_ACTOR_IDS.isEmpty()) {
            sendManualDebug(player, tr("no_actors_selected"));
         } else if (SELECTED_INSTANCE_ID != null) {
            List<Integer> actorIds = new ArrayList<>(SELECTED_ACTOR_IDS.size());

            for (int i = 0; i < SELECTED_ACTOR_IDS.size(); i++) {
               actorIds.add(SELECTED_ACTOR_IDS.getInt(i));
            }

            if (actorIds.isEmpty()) {
               sendManualDebug(player, tr("no_extra_actors_for_join"));
            } else if (!ClientPlayNetworking.canSend(DebugJoinAnimationC2SPayload.ID)) {
               sendManualDebug(player, tr("server_cannot_receive_join"));
            } else {
               ClientPlayNetworking.send(new DebugJoinAnimationC2SPayload(SELECTED_INSTANCE_ID, actorIds));
               sendManualDebug(player, tr("join_requested_instance_extras", SELECTED_INSTANCE_ID, actorIds.size()));
               SELECTED_INSTANCE_ID = null;
               SELECTED_ACTOR_IDS.clear();
            }
         } else if (!ClientPlayNetworking.canSend(DebugStartAnimationC2SPayload.ID)) {
            sendManualDebug(player, tr("server_cannot_receive_debug_packets"));
         } else {
            List<Integer> actorIds = new ArrayList<>(SELECTED_ACTOR_IDS.size());

            for (int i = 0; i < SELECTED_ACTOR_IDS.size(); i++) {
               actorIds.add(SELECTED_ACTOR_IDS.getInt(i));
            }

            sendManualDebug(player, tr("start_requested_actor_count", actorIds.size()));
            ClientPlayNetworking.send(new DebugStartAnimationC2SPayload(actorIds, NonAnimationDamageBehavior.STOP_ON_DAMAGE.id(), false, -1));
            SELECTED_ACTOR_IDS.clear();
         }
      }
   }

   private static void requestNextStage(MinecraftClient client) {
      PlayerEntity player = client.player;
      if (player != null) {
         UUID instanceToAdvance = ClientAnimationRuntime.findLatestActiveInstance();
         if (instanceToAdvance == null) {
            sendManualDebug(player, tr("no_active_instance_to_advance"));
         } else {
            if (ClientPlayNetworking.canSend(DebugAdvanceStageC2SPayload.ID)) {
               ClientPlayNetworking.send(new DebugAdvanceStageC2SPayload(instanceToAdvance));
               sendManualDebug(player, tr("next_stage_requested_server"));
            } else {
               ClientAnimationRuntime.requestStageAdvance(instanceToAdvance);
               sendManualDebug(player, tr("next_stage_requested_local"));
            }
         }
      }
   }

   private static void requestSpeedAdjust(MinecraftClient client, double multiplier) {
      PlayerEntity player = client.player;
      if (player != null) {
         UUID targetInstance = ClientAnimationRuntime.findLatestActiveInstanceContaining(player.getUuid());
         if (targetInstance == null) {
            targetInstance = ClientAnimationRuntime.findLatestActiveInstance();
         }

         if (targetInstance == null) {
            sendManualDebug(player, tr("no_active_instance_to_adjust_speed"));
         } else {
            if (ClientPlayNetworking.canSend(AdjustAnimationSpeedC2SPayload.ID)) {
               ClientPlayNetworking.send(new AdjustAnimationSpeedC2SPayload(targetInstance, multiplier));
               double percent = (multiplier - 1.0) * 100.0;
               String delta = (percent >= 0.0 ? "+" : "") + String.format(Locale.ROOT, "%.0f%%", percent);
               sendManualDebug(player, tr("speed_change_requested", delta));
            } else {
               sendManualDebug(player, tr("server_cannot_receive_speed"));
            }
         }
      }
   }

   private static void handleCompletedStages(MinecraftClient client) {
      List<UUID> completed = ClientAnimationRuntime.drainCompletedInstances();
      if (!completed.isEmpty()) {
         PlayerEntity player = client.player;
         boolean canSend = ClientPlayNetworking.canSend(DebugStopAnimationC2SPayload.ID);

         for (UUID instanceId : completed) {
            if (canSend) {
               ClientPlayNetworking.send(new DebugStopAnimationC2SPayload(instanceId));
            } else if (player != null && NonClientDiagnostics.shouldSend(NonDebugChatCategory.ERROR)) {
               sendCategoryDebug(player, NonDebugChatCategory.ERROR, tr("stage_sequence_ended_server_stop_unavailable"));
            }
         }
      }
   }

   private static void syncCameraPerspective(MinecraftClient client) {
      if (client.player != null && client.options != null && client.world != null) {
         if (!NeedsOfNature.getConfig().autoSwitchThirdPersonOnAnimationStart()) {
            restoreCameraPerspective(client);
         } else {
            UUID selfId = client.player.getUuid();
            boolean shouldThirdPerson = ClientAnimationRuntime.isActorPendingOrActive(selfId);
            if (shouldThirdPerson) {
               if (!wasSelfAnimating) {
                  preAnimationPerspective = client.options.getPerspective();
                  ownsCameraPerspective = true;
                  if (client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
                     client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                  }
               } else if (ownsCameraPerspective && client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
                  preAnimationPerspective = null;
                  ownsCameraPerspective = false;
               }

               wasSelfAnimating = true;
            } else {
               if (ownsCameraPerspective || wasSelfAnimating) {
                  restoreCameraPerspective(client);
               }
            }
         }
      }
   }

   private static void resetCameraPerspective(MinecraftClient client) {
      if (client != null && client.options != null) {
         restoreCameraPerspective(client);
      }
   }

   private static void restoreCameraPerspective(MinecraftClient client) {
      if (client != null && client.options != null) {
         if (ownsCameraPerspective && preAnimationPerspective != null) {
            client.options.setPerspective(preAnimationPerspective);
         }

         wasSelfAnimating = false;
         ownsCameraPerspective = false;
         preAnimationPerspective = null;
      }
   }

   private static void syncLocalPlayerToAnchor(MinecraftClient client) {
      PlayerEntity player = client.player;
      if (player != null && client.world != null) {
         UUID selfId = player.getUuid();
         if (ClientAnimationRuntime.isActorPendingOrActive(selfId)) {
            UUID anchorId = ClientAnimationRuntime.findAnchorUuidForActor(selfId);
            if (anchorId == null) {
               return;
            }

            Entity anchor = client.world.getEntity(anchorId);
            if (anchor == null) {
               return;
            }

            double dx = player.getX() - anchor.getX();
            double dy = player.getY() - anchor.getY();
            double dz = player.getZ() - anchor.getZ();
            if (dx * dx + dy * dy + dz * dz > 4.0E-4) {
               player.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
               player.setVelocity(Vec3d.ZERO);
               player.fallDistance = 0.0;
            }
         }
      }
   }

   private static int indexOfSelectedActor(int value) {
      for (int i = 0; i < SELECTED_ACTOR_IDS.size(); i++) {
         if (SELECTED_ACTOR_IDS.getInt(i) == value) {
            return i;
         }
      }

      return -1;
   }

   private static Text tr(String key, Object... args) {
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

   private static void sendManualDebug(PlayerEntity player, Text message) {
      if (player != null) {
         player.sendMessage(message.copy().formatted(Formatting.DARK_GRAY), false);
      }
   }

   private static void sendCategoryDebug(PlayerEntity player, NonDebugChatCategory category, Text message) {
      if (player != null) {
         player.sendMessage(message.copy().formatted(NonDiagnostics.chatColor(category)), false);
      }
   }
}
