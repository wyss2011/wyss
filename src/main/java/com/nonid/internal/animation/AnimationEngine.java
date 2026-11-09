package com.nonid.internal.animation;

import com.nonid.NeedsOfNature;
import com.nonid.NonDebugChatCategory;
import com.nonid.NonDebugChatMode;
import com.nonid.NonDiagnostics;
import com.nonid.api.animation.NonAnimationDamageBehavior;
import com.nonid.internal.animation.api.AnimationService;
import com.nonid.internal.animation.api.NonAnimationApiBridge;
import com.nonid.internal.animation.data.AnimationCueIndex;
import com.nonid.internal.animation.data.AnimationDefinitions;
import com.nonid.internal.animation.network.AdjustAnimationSpeedC2SPayload;
import com.nonid.internal.animation.network.AdvanceAnimationStageS2CPayload;
import com.nonid.internal.animation.network.AnimationSpeedUpdateS2CPayload;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.network.DebugAdvanceStageC2SPayload;
import com.nonid.internal.animation.network.DebugChatPreferenceC2SPayload;
import com.nonid.internal.animation.network.DebugJoinAnimationC2SPayload;
import com.nonid.internal.animation.network.DebugStartAnimationC2SPayload;
import com.nonid.internal.animation.network.DebugStopAllAnimationsC2SPayload;
import com.nonid.internal.animation.network.DebugStopAnimationC2SPayload;
import com.nonid.internal.animation.network.StartAnimationS2CPayload;
import com.nonid.internal.animation.network.StopAllAnimationsS2CPayload;
import com.nonid.internal.animation.network.StopAnimationS2CPayload;
import com.nonid.internal.animation.server.AnimationStartContext;
import com.nonid.internal.animation.server.ServerAnimationController;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

public final class AnimationEngine {
   public static final String PACK_NAMESPACE = "animationframework";
   public static final String RUNTIME_NAMESPACE = "needsofnature";
   public static final Logger LOGGER = NeedsOfNature.LOGGER;

   private AnimationEngine() {
   }

   public static Identifier packId(String path) {
      return Identifier.of("animationframework", path);
   }

   public static Identifier runtimeId(String path) {
      return Identifier.of("needsofnature", "animation/" + path);
   }

   public static void initialize() {
      PayloadTypeRegistry.playC2S().register(DebugStartAnimationC2SPayload.ID, DebugStartAnimationC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(DebugStopAllAnimationsC2SPayload.ID, DebugStopAllAnimationsC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(DebugStopAnimationC2SPayload.ID, DebugStopAnimationC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(DebugChatPreferenceC2SPayload.ID, DebugChatPreferenceC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(DebugJoinAnimationC2SPayload.ID, DebugJoinAnimationC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(DebugAdvanceStageC2SPayload.ID, DebugAdvanceStageC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(AdjustAnimationSpeedC2SPayload.ID, AdjustAnimationSpeedC2SPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(StartAnimationS2CPayload.ID, StartAnimationS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(AdvanceAnimationStageS2CPayload.ID, AdvanceAnimationStageS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(StopAllAnimationsS2CPayload.ID, StopAllAnimationsS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(StopAnimationS2CPayload.ID, StopAnimationS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(AnimationSpeedUpdateS2CPayload.ID, AnimationSpeedUpdateS2CPayload.CODEC);
      NonAnimationApiBridge.initialize();
      AnimationDefinitions.registerReloadListener();
      AnimationCueIndex.registerReloadListener();
      ServerAnimationController.init();
      registerStartupResourceReload();
      ServerPlayNetworking.registerGlobalReceiver(DebugStartAnimationC2SPayload.ID, (payload, context) -> context.server().execute(() -> {
         ServerPlayerEntity player = context.player();
         if (requireDebugControlPermission(player, "start")) {
            NonAnimationDamageBehavior behavior = NonAnimationDamageBehavior.fromId(payload.damageBehaviorId(), NonAnimationDamageBehavior.STOP_ON_DAMAGE);
            handleDebugStartRequest(player, payload.actorEntityIds(), behavior, payload.ignoreAttackers(), payload.anchorEntityId());
         }
      }));
      ServerPlayNetworking.registerGlobalReceiver(DebugStopAllAnimationsC2SPayload.ID, (payload, context) -> context.server().execute(() -> {
         ServerPlayerEntity player = context.player();
         if (requireDebugControlPermission(player, "stop_all")) {
            if (player.getEntityWorld() instanceof ServerWorld world) {
               AnimationService.stopAll(world);
               sendDebugChat(player, NonDebugChatCategory.ALWAYS, Text.translatable("debug.needsofnature.animation.stop_all_broadcast_queued"), true);
            }
         }
      }));
      ServerPlayNetworking.registerGlobalReceiver(DebugStopAnimationC2SPayload.ID, (payload, context) -> context.server().execute(() -> {
         ServerPlayerEntity player = context.player();
         if (requireDebugControlPermission(player, "stop_instance")) {
            ServerAnimationController.stopInstanceAndBroadcast(player, payload.instanceId());
         }
      }));
      ServerPlayNetworking.registerGlobalReceiver(DebugChatPreferenceC2SPayload.ID, (payload, context) -> context.server().execute(() -> {
         UUID uuid = context.player().getUuid();
         NonDebugChatMode mode = NonDebugChatMode.fromId(payload.modeId(), NonDebugChatMode.SETUP_ERRORS);
         NonDiagnostics.setDebugChatPreference(uuid, mode);
      }));
      ServerPlayNetworking.registerGlobalReceiver(DebugJoinAnimationC2SPayload.ID, (payload, context) -> context.server().execute(() -> {
         ServerPlayerEntity player = context.player();
         if (requireDebugControlPermission(player, "join")) {
            handleDebugJoinRequest(player, payload.instanceId(), payload.actorEntityIds());
         }
      }));
      ServerPlayNetworking.registerGlobalReceiver(DebugAdvanceStageC2SPayload.ID, (payload, context) -> context.server().execute(() -> {
         ServerPlayerEntity player = context.player();
         if (requireDebugControlPermission(player, "advance_stage")) {
            if (player.getEntityWorld() instanceof ServerWorld world) {
               ServerAnimationController.advanceStage(world, payload.instanceId());
            }
         }
      }));
      ServerPlayNetworking.registerGlobalReceiver(AdjustAnimationSpeedC2SPayload.ID, (payload, context) -> context.server().execute(() -> {
         ServerPlayerEntity player = context.player();
         if (requireDebugControlPermission(player, "adjust_speed")) {
            if (player.getEntityWorld() instanceof ServerWorld world) {
               ServerAnimationController.adjustSpeed(world, payload.instanceId(), payload.multiplier(), player);
            }
         }
      }));
      ServerPlayConnectionEvents.JOIN
         .register((Join)(handler, sender, server) -> server.execute(() -> ServerAnimationController.sendActiveInstancesToPlayer(handler.player)));
      LOGGER.info("[NoN] Animation engine initialized");
   }

   private static void registerStartupResourceReload() {
      ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)server -> server.execute(() -> {
         try {
            LOGGER.info("[NoN] Reloading startup resources so generated and default packs are active.");
            server.reloadResources(server.getDataPackManager().getEnabledIds()).exceptionally(throwable -> {
               LOGGER.warn("[NoN] Startup resource reload failed.", throwable);
               return null;
            });
         } catch (RuntimeException e) {
            LOGGER.warn("[NoN] Failed to schedule startup resource reload.", e);
         }
      }));
   }

   public static void logSetupWarning(String template, Object... args) {
      NonDiagnostics.logSetupWarning(template, args);
   }

   public static void logRuntime(String template, Object... args) {
      NonDiagnostics.logRuntime(template, args);
   }

   public static void logSetupError(String template, Object... args) {
      NonDiagnostics.logSetupError(template, args);
   }

   public static void queueSetupChat(String message) {
      NonDiagnostics.queueSetupChat(message);
   }

   public static String formatLogTemplate(String template, Object... args) {
      return NonDiagnostics.formatLogTemplate(template, args);
   }

   public static boolean shouldSendDebugChat(ServerPlayerEntity player, NonDebugChatCategory category) {
      return NonDiagnostics.shouldSendDebugChat(player, category);
   }

   public static boolean shouldSendDebugChat(ServerPlayerEntity player, NonDebugChatCategory category, boolean force) {
      return NonDiagnostics.shouldSendDebugChat(player, category, force);
   }

   public static void sendDebugChat(ServerPlayerEntity player, NonDebugChatCategory category, String message, boolean force) {
      sendDebugChat(player, category, Text.literal(message), force);
   }

   public static void sendDebugChat(ServerPlayerEntity player, NonDebugChatCategory category, Text message, boolean force) {
      NonDiagnostics.sendDebugChat(player, category, message, force);
   }

   private static boolean requireDebugControlPermission(ServerPlayerEntity player, String action) {
      if (player != null
         && player.getEntityWorld() instanceof ServerWorld world
         && world.getServer().getPlayerManager().isOperator(new PlayerConfigEntry(player.getGameProfile()))) {
         return true;
      }

      if (player != null) {
         LOGGER.warn("Denied animation debug control '{}' from non-OP player {}", action, player.getName().getString());
         sendDebugChat(player, NonDebugChatCategory.WARNING, Text.literal("[NoN 动画] 调试控制需要管理员权限。"), true);
      }

      return false;
   }

   private static void handleDebugStartRequest(
      ServerPlayerEntity player, List<Integer> requestedActorIds, NonAnimationDamageBehavior damageBehavior, boolean ignoreAttackers, int anchorEntityId
   ) {
      if (!requestedActorIds.isEmpty()) {
         List<Entity> resolved = new ArrayList<>();
         Entity anchorEntity = null;

         for (int entityId : requestedActorIds) {
            Entity entity = player.getEntityWorld().getEntityById(entityId);
            if (entity instanceof LivingEntity) {
               resolved.add(entity);
               if (anchorEntity == null && entityId == anchorEntityId) {
                  anchorEntity = entity;
               }
            }
         }

         if (!resolved.isEmpty()) {
            resolved.sort(Comparator.comparingInt(Entity::getId));
            NonAnimationDamageBehavior safeBehavior = damageBehavior == null ? NonAnimationDamageBehavior.STOP_ON_DAMAGE : damageBehavior;
            UUID anchorUuid = anchorEntity != null ? anchorEntity.getUuid() : null;
            ServerAnimationController.MatchedStartRequest start = ServerAnimationController.startEligibleMatchedNow(
               player.getEntityWorld(), player, resolved, Set.of(), safeBehavior, ignoreAttackers, anchorUuid, AnimationStartContext.empty(), true
            );
            String chosenStr = start == null ? "(none)" : start.animationId().toString();
            List<String> actorKeys = start == null ? List.of() : start.actorKeys();
            String keysStr = actorKeys.isEmpty() ? "none" : actorKeys.toString();
            MutableText msg = Text.translatable("debug.needsofnature.animation.server_eval", new Object[]{resolved.size(), chosenStr, keysStr});
            sendDebugChat(player, NonDebugChatCategory.ALWAYS, msg, true);
            if (start != null) {
               logRuntime("Debug request: player={} chosen={} actors={}", player.getName().getString(), start.animationId(), resolved.size());
            }
         }
      }
   }

   private static void handleDebugJoinRequest(ServerPlayerEntity player, UUID instanceId, List<Integer> requestedActorIds) {
      if (player != null && requestedActorIds != null && !requestedActorIds.isEmpty()) {
         if (player.getEntityWorld() instanceof ServerWorld world) {
            ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
            if (snapshot == null) {
               sendDebugChat(player, NonDebugChatCategory.WARNING, Text.translatable("debug.needsofnature.animation.join_failed_no_active_instance"), true);
            } else {
               AnimationStageInfo currentStage = ServerAnimationController.getCurrentStage(world, instanceId);
               if (currentStage != null && currentStage.allowJoin()) {
                  List<Entity> combined = new ArrayList<>();
                  Set<UUID> existingActorUuids = new HashSet<>(snapshot.actorUuids());

                  for (UUID actorUuid : snapshot.actorUuids()) {
                     Entity actor = world.getEntity(actorUuid);
                     if (actor != null) {
                        combined.add(actor);
                     }
                  }

                  if (combined.size() != snapshot.actorUuids().size()) {
                     sendDebugChat(
                        player, NonDebugChatCategory.WARNING, Text.translatable("debug.needsofnature.animation.join_failed_no_active_instance"), true
                     );
                  } else {
                     List<Entity> additions = new ArrayList<>();

                     for (int id : requestedActorIds) {
                        Entity e = world.getEntityById(id);
                        if (e instanceof MobEntity && existingActorUuids.add(e.getUuid())) {
                           additions.add(e);
                        }
                     }

                     if (!additions.isEmpty()) {
                        combined.addAll(additions);
                        combined.sort(Comparator.comparingInt(Entity::getId));
                        AnimationDefinitions.MatchResult match = AnimationDefinitions.match(combined);
                        AnimationDefinitions.Definition definition = match.chosen();
                        if (definition == null) {
                           sendDebugChat(
                              player, NonDebugChatCategory.WARNING, Text.translatable("debug.needsofnature.animation.join_failed_no_expanded_definition"), true
                           );
                        } else {
                           List<String> actorKeys = AnimationDefinitions.resolveActorKeys(definition, combined, world.getRandom());
                           if (actorKeys != null && actorKeys.size() == combined.size()) {
                              AnimationStartContext replacementContext = snapshot.startContext().withJoinPredecessor(snapshot.animationId());
                              if (!ServerAnimationController.stopInstance(world, instanceId, false)) {
                                 sendDebugChat(
                                    player,
                                    NonDebugChatCategory.WARNING,
                                    Text.translatable("debug.needsofnature.animation.join_failed_no_active_instance"),
                                    true
                                 );
                              } else {
                                 ServerAnimationController.startNowWithContext(
                                    world,
                                    player,
                                    definition.id(),
                                    combined,
                                    actorKeys,
                                    definition.stages(),
                                    snapshot.damageBehavior(),
                                    snapshot.ignoreAttackers(),
                                    null,
                                    replacementContext,
                                    true,
                                    false
                                 );
                              }
                           } else {
                              sendDebugChat(
                                 player,
                                 NonDebugChatCategory.WARNING,
                                 Text.translatable("debug.needsofnature.animation.join_failed_no_expanded_definition"),
                                 true
                              );
                           }
                        }
                     }
                  }
               } else {
                  sendDebugChat(player, NonDebugChatCategory.WARNING, Text.translatable("debug.needsofnature.animation.join_denied_stage_disallows"), true);
               }
            }
         }
      }
   }
}
