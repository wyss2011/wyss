package com.nonid;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nonid.api.animation.NonAnimationDamageBehavior;
import com.nonid.internal.animation.api.AnimationService;
import com.nonid.internal.animation.data.AnimationDefinitions;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.network.PlayerAnimationCancelC2SPayload;
import com.nonid.network.PlayerAnimationChoiceC2SPayload;
import com.nonid.network.PlayerAnimationConsentC2SPayload;
import com.nonid.network.PlayerAnimationConsentS2CPayload;
import com.nonid.network.PlayerAnimationRequestStatusS2CPayload;
import com.nonid.network.PlayerAnimationSelectionS2CPayload;
import com.nonid.network.PlayerProfileActionC2SPayload;
import com.nonid.network.PlayerProfileSnapshotS2CPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDamage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class NonPlayerInteractionSystem {
   private static final int REQUEST_TIMEOUT_TICKS = 300;
   private static final String STATUS_WAITING = "waiting";
   private static final String STATUS_STARTED = "started";
   private static final String STATUS_FAILED = "failed";
   private static final String STATUS_CANCELLED = "cancelled";
   private static final Map<UUID, NonPlayerInteractionSystem.SelectionSession> SELECTIONS = new HashMap<>();
   private static final Map<UUID, NonPlayerInteractionSystem.PendingRequest> REQUESTS = new HashMap<>();
   private static final Map<UUID, UUID> SESSION_BY_PLAYER = new HashMap<>();
   @Nullable
   private static MinecraftServer activeServer;

   private NonPlayerInteractionSystem() {
   }

   public static void register() {
      ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)server -> activeServer = server);
      ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> {
         if (activeServer == server) {
            activeServer = null;
         }

         SELECTIONS.clear();
         REQUESTS.clear();
         SESSION_BY_PLAYER.clear();
      });
      ServerPlayNetworking.registerGlobalReceiver(
         PlayerProfileActionC2SPayload.ID, (payload, context) -> context.server().execute(() -> handleProfileAction(context.player(), payload))
      );
      ServerPlayNetworking.registerGlobalReceiver(
         PlayerAnimationChoiceC2SPayload.ID, (payload, context) -> context.server().execute(() -> handleAnimationChoice(context.player(), payload))
      );
      ServerPlayNetworking.registerGlobalReceiver(
         PlayerAnimationConsentC2SPayload.ID, (payload, context) -> context.server().execute(() -> handleConsent(context.player(), payload))
      );
      ServerPlayNetworking.registerGlobalReceiver(
         PlayerAnimationCancelC2SPayload.ID, (payload, context) -> context.server().execute(() -> handleCancel(context.player(), payload.requestId()))
      );
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> server.execute(() -> {
         if (handler.player.getEntityWorld() instanceof ServerWorld world) {
            NonPlayerInteractionProfiles var6 = NonPlayerInteractionProfiles.get(world);

            for (ServerPlayerEntity listedPlayer : server.getPlayerManager().getPlayerList()) {
               var6.recordKnownPlayer(listedPlayer);
            }

            var6.normalizeAll(allowedMask());
         }
      }));
      ServerPlayConnectionEvents.DISCONNECT
         .register(
            (Disconnect)(handler, server) -> server.execute(() -> cancelForPlayer(handler.player.getUuid(), "gui.needsofnature.player_request.disconnected"))
         );
      ServerLivingEntityEvents.AFTER_DAMAGE.register((AfterDamage)(entity, source, baseDamageTaken, damageTaken, blocked) -> {
         if (!blocked && damageTaken > 0.0F && entity instanceof ServerPlayerEntity player) {
            cancelForPlayer(player.getUuid(), "gui.needsofnature.player_request.damaged");
         }
      });
   }

   public static boolean handleInteraction(ServerWorld world, ServerPlayerEntity requester, Entity clickedEntity) {
      if (isUsablePlayer(requester) && world != null && clickedEntity != null && !clickedEntity.isRemoved()) {
         if (requester.getEntityWorld() != world || clickedEntity.getEntityWorld() != world) {
            return false;
         } else if (requester.getUuid().equals(clickedEntity.getUuid())) {
            return false;
         } else {
            UUID activeInstance = NeedsOfNature.findActiveInstanceContainingActor(world, clickedEntity.getUuid());
            if (activeInstance != null) {
               return beginJoinRequest(world, requester, clickedEntity, activeInstance);
            } else {
               return clickedEntity instanceof ServerPlayerEntity target && requester.isSneaking() ? beginDirectSelection(world, requester, target) : false;
            }
         }
      } else {
         return false;
      }
   }

   public static void tick(ServerWorld world) {
      if (world != null) {
         long now = world.getTime();

         for (NonPlayerInteractionSystem.SelectionSession selection : List.copyOf(SELECTIONS.values())) {
            if (world.getRegistryKey().equals(selection.worldKey())) {
               ServerPlayerEntity requester = getPlayer(world, selection.requesterUuid());
               Entity target = resolveEntity(world, selection.targetUuid());
               if (now >= selection.expiresTick()) {
                  failSelection(selection, "failed", "gui.needsofnature.player_request.timeout");
               } else if (!validInteractionPair(requester, target)) {
                  failSelection(selection, "cancelled", "gui.needsofnature.player_request.out_of_range");
               } else if (selection.kind() == NonPlayerInteractionSystem.RequestKind.DIRECT && isBusyForDirectRequest(world, requester, target)) {
                  failSelection(selection, "failed", "gui.needsofnature.player_request.player_busy");
               } else if (selection.kind() == NonPlayerInteractionSystem.RequestKind.JOIN && !isSelectionSourceJoinable(world, selection)) {
                  failSelection(selection, "failed", "gui.needsofnature.player_request.animation_changed");
               }
            }
         }

         for (NonPlayerInteractionSystem.PendingRequest request : List.copyOf(REQUESTS.values())) {
            if (world.getRegistryKey().equals(request.worldKey())) {
               ServerPlayerEntity requester = getPlayer(world, request.requesterUuid());
               Entity target = resolveEntity(world, request.interactionTargetUuid());
               if (now >= request.expiresTick()) {
                  finish(request, "failed", "gui.needsofnature.player_request.timeout");
               } else if (!validInteractionPair(requester, target)) {
                  finish(request, "cancelled", "gui.needsofnature.player_request.out_of_range");
               } else if (request.kind() == NonPlayerInteractionSystem.RequestKind.DIRECT && isBusyForDirectRequest(world, requester, target)) {
                  finish(request, "failed", "gui.needsofnature.player_request.player_busy");
               } else if (request.kind() == NonPlayerInteractionSystem.RequestKind.JOIN
                  && (request.sourceInstanceUuid() == null || AnimationService.getCurrentStage(world, request.sourceInstanceUuid()) == null)) {
                  finish(request, "failed", "gui.needsofnature.player_request.animation_changed");
               }
            }
         }
      }
   }

   public static void normalizeAndSyncAll(MinecraftServer server) {
      if (server != null && server.getOverworld() != null) {
         NonPlayerInteractionProfiles profiles = NonPlayerInteractionProfiles.get(server.getOverworld());
         profiles.normalizeAll(allowedMask());

         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendProfileSnapshot(player, "");
         }
      }
   }

   public static boolean areAttacksAllowed(ServerPlayerEntity target, ServerPlayerEntity otherPlayer) {
      if (target != null && otherPlayer != null && target.getEntityWorld() instanceof ServerWorld world) {
         NonPlayerInteractionProfiles.Mode mode = NonPlayerInteractionProfiles.get(world).resolveMode(target.getUuid(), otherPlayer.getUuid(), allowedMask());
         return mode.attacksAllowed();
      } else {
         return false;
      }
   }

   private static boolean beginDirectSelection(ServerWorld world, ServerPlayerEntity requester, ServerPlayerEntity target) {
      if (!validInteractionPair(requester, target)) {
         return false;
      }

      if (!hasRequestEnergy(requester)) {
         sendStandaloneFailure(requester, "gui.needsofnature.player_request.energy_low");
         return true;
      }

      if (SESSION_BY_PLAYER.containsKey(requester.getUuid())) {
         sendStandaloneFailure(requester, "gui.needsofnature.player_request.already_pending");
         return true;
      }

      NonPlayerInteractionProfiles.Mode targetMode = resolveMode(world, target, requester);
      if (targetMode.requestPolicy() == NonPlayerInteractionProfiles.RequestPolicy.DENY) {
         sendStandaloneFailure(requester, "gui.needsofnature.player_request.not_accepting");
         return true;
      }

      if (!NeedsOfNature.hasActivePlayerAnimation(requester) && !NeedsOfNature.hasActivePlayerAnimation(target)) {
         List<Entity> actors = sortedActors(requester, target);
         List<UUID> actorUuids = actors.stream().<UUID>map(Entity::getUuid).toList();
         List<NonPlayerInteractionSystem.Candidate> candidates = enumerateCandidates(world, actors, target, null);
         if (candidates.isEmpty()) {
            sendStandaloneFailure(requester, "gui.needsofnature.player_request.no_match");
            return true;
         } else {
            UUID requestId = UUID.randomUUID();
            NonPlayerInteractionSystem.SelectionSession session = new NonPlayerInteractionSystem.SelectionSession(
               requestId,
               NonPlayerInteractionSystem.RequestKind.DIRECT,
               world.getRegistryKey(),
               requester.getUuid(),
               target.getUuid(),
               null,
               world.getTime() + 300L,
               List.copyOf(actorUuids),
               List.copyOf(candidates)
            );
            SELECTIONS.put(requestId, session);
            SESSION_BY_PLAYER.put(requester.getUuid(), requestId);
            ServerPlayNetworking.send(
               requester,
               new PlayerAnimationSelectionS2CPayload(
                  requestId, target.getUuid(), target.getGameProfile().name(), candidatesJson(session, requester.getUuid()), false
               )
            );
            return true;
         }
      } else {
         sendStandaloneFailure(requester, "gui.needsofnature.player_request.player_busy");
         return true;
      }
   }

   private static boolean beginJoinRequest(ServerWorld world, ServerPlayerEntity requester, Entity clickedEntity, UUID instanceId) {
      if (!hasRequestEnergy(requester)) {
         sendStandaloneFailure(requester, "gui.needsofnature.player_request.energy_low");
         return true;
      } else if (SESSION_BY_PLAYER.containsKey(requester.getUuid())) {
         sendStandaloneFailure(requester, "gui.needsofnature.player_request.already_pending");
         return true;
      } else if (AnimationService.isActorPendingOrActive(world, requester.getUuid())) {
         sendStandaloneFailure(requester, "gui.needsofnature.player_request.player_busy");
         return true;
      } else {
         boolean defeated = NeedsOfNature.isDefeatedInstance(world, instanceId);
         if (!defeated && !NeedsOfNature.isVoluntaryJoinableInstance(world, instanceId)) {
            return false;
         } else {
            List<Entity> actors = entitiesForJoin(world, instanceId, requester);
            if (actors.isEmpty()) {
               sendStandaloneFailure(requester, "gui.needsofnature.player_request.no_match");
               return true;
            } else {
               Identifier predecessorAnimationId = AnimationService.getActiveAnimationId(world, instanceId);
               if (predecessorAnimationId == null) {
                  sendStandaloneFailure(requester, "gui.needsofnature.player_request.animation_changed");
                  return true;
               } else if (defeated) {
                  return beginDefeatedJoin(world, requester, clickedEntity, instanceId, actors, predecessorAnimationId);
               } else if (resolveConsentPlan(world, requester, actors, false) == null) {
                  sendStandaloneFailure(requester, "gui.needsofnature.player_request.not_accepting");
                  return true;
               } else {
                  List<NonPlayerInteractionSystem.Candidate> candidates = enumerateCandidates(world, actors, clickedEntity, predecessorAnimationId);
                  if (candidates.isEmpty()) {
                     sendStandaloneFailure(requester, "gui.needsofnature.player_request.no_match");
                     return true;
                  } else {
                     UUID requestId = UUID.randomUUID();
                     NonPlayerInteractionSystem.SelectionSession selection = new NonPlayerInteractionSystem.SelectionSession(
                        requestId,
                        NonPlayerInteractionSystem.RequestKind.JOIN,
                        world.getRegistryKey(),
                        requester.getUuid(),
                        clickedEntity.getUuid(),
                        instanceId,
                        world.getTime() + 300L,
                        actors.stream().<UUID>map(Entity::getUuid).toList(),
                        List.copyOf(candidates)
                     );
                     SELECTIONS.put(requestId, selection);
                     SESSION_BY_PLAYER.put(requester.getUuid(), requestId);
                     ServerPlayNetworking.send(
                        requester,
                        new PlayerAnimationSelectionS2CPayload(
                           requestId, clickedEntity.getUuid(), clickedEntity.getName().getString(), candidatesJson(selection, requester.getUuid()), true
                        )
                     );
                     return true;
                  }
               }
            }
         }
      }
   }

   private static boolean beginDefeatedJoin(
      ServerWorld world, ServerPlayerEntity requester, Entity clickedEntity, UUID instanceId, List<Entity> actors, Identifier predecessorAnimationId
   ) {
      AnimationService.MatchedAnimation matched = NonAnimationMatcher.findAutomaticAttack(
         world, actors, Set.of(), clickedEntity, true, requester, Set.of(), null, predecessorAnimationId
      );
      if (matched == null) {
         sendStandaloneFailure(requester, "gui.needsofnature.player_request.no_match");
         return true;
      }

      AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(matched.animationId());
      if (definition != null && isPlayerInteractionDefinition(definition)) {
         NonPlayerInteractionSystem.ConsentPlan consentPlan = resolveConsentPlan(world, requester, actors, true);
         if (consentPlan == null) {
            sendStandaloneFailure(requester, "gui.needsofnature.player_request.not_accepting");
            return true;
         } else {
            NonPlayerInteractionSystem.PendingRequest request = new NonPlayerInteractionSystem.PendingRequest(
               UUID.randomUUID(),
               NonPlayerInteractionSystem.RequestKind.JOIN,
               world.getRegistryKey(),
               requester.getUuid(),
               clickedEntity.getUuid(),
               instanceId,
               actors.stream().<UUID>map(Entity::getUuid).toList(),
               matched.animationId(),
               definition.displayName(),
               List.copyOf(matched.actorKeys()),
               List.copyOf(matched.stages()),
               definition.contentTags() == null ? List.of() : List.copyOf(definition.contentTags()),
               world.getTime() + 300L,
               consentPlan.consentPlayers(),
               consentPlan.acceptedPlayers()
            );
            executeRequest(world, request);
            return true;
         }
      } else {
         sendStandaloneFailure(requester, "gui.needsofnature.player_request.no_match");
         return true;
      }
   }

   private static void handleAnimationChoice(ServerPlayerEntity requester, PlayerAnimationChoiceC2SPayload payload) {
      NonPlayerInteractionSystem.SelectionSession selection = SELECTIONS.get(payload.requestId());
      if (selection != null && selection.requesterUuid().equals(requester.getUuid())) {
         if (!(requester.getEntityWorld() instanceof ServerWorld world && world.getRegistryKey().equals(selection.worldKey()))) {
            failSelection(selection, "failed", "gui.needsofnature.player_request.invalid");
         } else if (payload.choiceIndex() >= 0 && payload.choiceIndex() < selection.candidates().size()) {
            Entity target = resolveEntity(world, selection.targetUuid());
            if (!validInteractionPair(requester, target)) {
               failSelection(selection, "failed", "gui.needsofnature.player_request.out_of_range");
            } else if (selection.kind() == NonPlayerInteractionSystem.RequestKind.DIRECT && isBusyForDirectRequest(world, requester, target)) {
               failSelection(selection, "failed", "gui.needsofnature.player_request.player_busy");
            } else if (selection.kind() == NonPlayerInteractionSystem.RequestKind.JOIN && !isSelectionSourceJoinable(world, selection)) {
               failSelection(selection, "failed", "gui.needsofnature.player_request.animation_changed");
            } else {
               List<Entity> actors = resolveActors(world, selection.actorUuids());
               if (actors.size() != selection.actorUuids().size()) {
                  failSelection(selection, "failed", "gui.needsofnature.player_request.invalid");
               } else {
                  NonPlayerInteractionSystem.Candidate candidate = selection.candidates().get(payload.choiceIndex());
                  if (!candidate.selectable()) {
                     failSelection(selection, "failed", "gui.needsofnature.player_request.block_requirement_unavailable");
                  } else {
                     NonPlayerInteractionSystem.ConsentPlan consentPlan = resolveConsentPlan(world, requester, actors, false);
                     if (consentPlan == null) {
                        failSelection(selection, "failed", "gui.needsofnature.player_request.not_accepting");
                     } else {
                        removeSelection(selection);
                        NonPlayerInteractionSystem.PendingRequest request = new NonPlayerInteractionSystem.PendingRequest(
                           selection.id(),
                           selection.kind(),
                           selection.worldKey(),
                           requester.getUuid(),
                           selection.targetUuid(),
                           selection.sourceInstanceUuid(),
                           selection.actorUuids(),
                           candidate.animationId(),
                           candidate.displayName(),
                           candidate.actorKeys(),
                           candidate.stages(),
                           candidate.contentTags(),
                           world.getTime() + 300L,
                           consentPlan.consentPlayers(),
                           consentPlan.acceptedPlayers()
                        );
                        if (consentPlan.consentPlayers().isEmpty()) {
                           executeRequest(world, request);
                        } else if (!reserveRequest(request)) {
                           sendStandaloneFailure(requester, "gui.needsofnature.player_request.recipient_busy");
                        } else {
                           promptConsent(world, request);
                        }
                     }
                  }
               }
            }
         } else {
            failSelection(selection, "failed", "gui.needsofnature.player_request.invalid");
         }
      }
   }

   private static void handleConsent(ServerPlayerEntity player, PlayerAnimationConsentC2SPayload payload) {
      NonPlayerInteractionSystem.PendingRequest request = REQUESTS.get(payload.requestId());
      if (request != null && request.consentPlayers().contains(player.getUuid())) {
         if (!payload.accepted()) {
            finish(request, "failed", "gui.needsofnature.player_request.rejected");
         } else {
            NonPlayerInteractionSystem.PendingRequest accepted = request.withAccepted(player.getUuid());
            REQUESTS.put(request.id(), accepted);
            if (accepted.acceptedPlayers().containsAll(accepted.consentPlayers())) {
               if (player.getEntityWorld() instanceof ServerWorld world && world.getRegistryKey().equals(accepted.worldKey())) {
                  executeRequest(world, accepted);
               } else {
                  finish(accepted, "failed", "gui.needsofnature.player_request.invalid");
               }
            }
         }
      }
   }

   private static void handleCancel(ServerPlayerEntity player, UUID requestId) {
      NonPlayerInteractionSystem.SelectionSession selection = SELECTIONS.get(requestId);
      if (selection != null && selection.requesterUuid().equals(player.getUuid())) {
         failSelection(selection, "cancelled", "gui.needsofnature.player_request.cancelled");
      } else {
         NonPlayerInteractionSystem.PendingRequest request = REQUESTS.get(requestId);
         if (request != null && (request.requesterUuid().equals(player.getUuid()) || request.consentPlayers().contains(player.getUuid()))) {
            finish(request, "cancelled", "gui.needsofnature.player_request.cancelled");
         }
      }
   }

   private static void executeRequest(ServerWorld world, NonPlayerInteractionSystem.PendingRequest request) {
      if (world != null && request != null) {
         ServerPlayerEntity requester = getPlayer(world, request.requesterUuid());
         Entity interactionTarget = resolveEntity(world, request.interactionTargetUuid());
         if (!validInteractionPair(requester, interactionTarget)) {
            finish(request, "failed", "gui.needsofnature.player_request.out_of_range");
         } else {
            List<Entity> actors = resolveActors(world, request.actorUuids());
            if (actors.size() != request.actorUuids().size()) {
               finish(request, "failed", "gui.needsofnature.player_request.invalid");
            } else {
               AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(request.animationId());
               if (definition != null && isPlayerInteractionDefinition(definition)) {
                  NonGatherSystem.cancelForActors(world, request.actorUuids());
                  boolean started;
                  if (request.kind() == NonPlayerInteractionSystem.RequestKind.DIRECT) {
                     for (UUID actorUuid : request.actorUuids()) {
                        if (AnimationService.isActorPendingOrActive(world, actorUuid)) {
                           finish(request, "failed", "gui.needsofnature.player_request.player_busy");
                           return;
                        }
                     }

                     if (!AnimationService.isAnimationStartEligible(world, definition, actors, request.actorKeys(), interactionTarget)) {
                        finish(request, "failed", "gui.needsofnature.player_request.animation_changed");
                        return;
                     }

                     if (NeedsOfNature.isProtectedByAccessory(world, definition, actors)
                        && NeedsOfNature.consumeProtectorForAnimation(world, definition, actors)) {
                        finish(request, "failed", "gui.needsofnature.player_request.protected");
                        return;
                     }

                     UUID instanceId = NeedsOfNature.startAnimationNowWithContext(
                        world,
                        request.animationId(),
                        actors,
                        request.actorKeys(),
                        request.stages(),
                        NonAnimationDamageBehavior.STOP_ON_DAMAGE,
                        false,
                        interactionTarget,
                        NeedsOfNature.buildModeContext(false),
                        requester,
                        false,
                        true
                     );
                     started = instanceId != null;
                  } else {
                     if (request.sourceInstanceUuid() == null || AnimationService.getCurrentStage(world, request.sourceInstanceUuid()) == null) {
                        finish(request, "failed", "gui.needsofnature.player_request.animation_changed");
                        return;
                     }

                     if (NeedsOfNature.isProtectedByAccessory(world, definition, actors)
                        && NeedsOfNature.consumeProtectorForAnimation(world, definition, actors)) {
                        finish(request, "failed", "gui.needsofnature.player_request.protected");
                        return;
                     }

                     started = NeedsOfNature.joinAnimationNow(
                        world, request.sourceInstanceUuid(), List.of(requester), requester, request.animationId(), interactionTarget, request.actorKeys()
                     );
                  }

                  finish(
                     request,
                     started ? "started" : "failed",
                     started ? "gui.needsofnature.player_request.started" : "gui.needsofnature.player_request.animation_changed"
                  );
               } else {
                  finish(request, "failed", "gui.needsofnature.player_request.animation_changed");
               }
            }
         }
      }
   }

   private static List<NonPlayerInteractionSystem.Candidate> enumerateCandidates(
      ServerWorld world, List<Entity> actors, Entity positionAnchor, @Nullable Identifier joinPredecessorAnimationId
   ) {
      List<NonPlayerInteractionSystem.Candidate> result = new ArrayList<>();

      for (AnimationService.EligibleAnimation candidate : NonAnimationMatcher.findEligibleAutomatic(
         world, actors, Set.of(), positionAnchor, false, null, Set.of(), joinPredecessorAnimationId
      )) {
         AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(candidate.animationId());
         if (definition != null && isPlayerInteractionDefinition(definition)) {
            boolean selectable = AnimationService.isAnimationStartEligible(world, definition, actors, candidate.actorKeys(), positionAnchor);
            AnimationDefinitions.BlockRequirements blockRequirements = definition.blockRequirements();
            if (selectable || blockRequirements != null) {
               result.add(
                  new NonPlayerInteractionSystem.Candidate(
                     candidate.animationId(),
                     candidate.displayName(),
                     candidate.actorKeys(),
                     candidate.stages(),
                     candidate.contentTags(),
                     selectable,
                     summarizeBlockRequirement(blockRequirements),
                     candidate.packName(),
                     candidate.packAuthor()
                  )
               );
            }
         }
      }

      return List.copyOf(result);
   }

   @Nullable
   private static NonPlayerInteractionSystem.ConsentPlan resolveConsentPlan(
      ServerWorld world, ServerPlayerEntity requester, List<Entity> actors, boolean attack
   ) {
      Set<UUID> consentPlayers = new LinkedHashSet<>();
      Set<UUID> acceptedPlayers = new LinkedHashSet<>();

      for (Entity actor : actors) {
         if (actor instanceof ServerPlayerEntity existingPlayer && !existingPlayer.getUuid().equals(requester.getUuid())) {
            NonPlayerInteractionProfiles.Mode mode = resolveMode(world, existingPlayer, requester);
            if (attack) {
               if (!mode.attacksAllowed()) {
                  return null;
               }
            } else {
               if (mode.requestPolicy() == NonPlayerInteractionProfiles.RequestPolicy.DENY) {
                  return null;
               }

               if (mode.requestPolicy() == NonPlayerInteractionProfiles.RequestPolicy.AUTO_ACCEPT) {
                  acceptedPlayers.add(existingPlayer.getUuid());
               } else {
                  consentPlayers.add(existingPlayer.getUuid());
               }
            }
         }
      }

      return new NonPlayerInteractionSystem.ConsentPlan(Set.copyOf(consentPlayers), Set.copyOf(acceptedPlayers));
   }

   private static boolean isSelectionSourceJoinable(ServerWorld world, NonPlayerInteractionSystem.SelectionSession selection) {
      if (world == null || selection == null || selection.sourceInstanceUuid() == null) {
         return false;
      }

      if (!NeedsOfNature.isVoluntaryJoinableInstance(world, selection.sourceInstanceUuid())) {
         return false;
      }

      UUID targetInstance = NeedsOfNature.findActiveInstanceContainingActor(world, selection.targetUuid());
      return selection.sourceInstanceUuid().equals(targetInstance);
   }

   static boolean isPlayerInteractionDefinition(AnimationDefinitions.Definition definition) {
      if (definition == null) {
         return false;
      }

      if (definition.animationTags() == null) {
         return true;
      }

      for (String rawTag : definition.animationTags()) {
         String tag = rawTag == null ? "" : rawTag.trim().toLowerCase(Locale.ROOT);
         if (tag.equals("manualpeak") || tag.equals("fillbottle") || tag.equals("birth") || tag.startsWith("birth_") || tag.startsWith("defeat.")) {
            return false;
         }
      }

      return true;
   }

   private static boolean reserveRequest(NonPlayerInteractionSystem.PendingRequest request) {
      Set<UUID> participants = new LinkedHashSet<>(request.consentPlayers());
      participants.add(request.requesterUuid());

      for (UUID playerUuid : participants) {
         if (SESSION_BY_PLAYER.containsKey(playerUuid)) {
            return false;
         }
      }

      REQUESTS.put(request.id(), request);

      for (UUID playerUuid : participants) {
         SESSION_BY_PLAYER.put(playerUuid, request.id());
      }

      return true;
   }

   private static void promptConsent(ServerWorld world, NonPlayerInteractionSystem.PendingRequest request) {
      ServerPlayerEntity requester = getPlayer(world, request.requesterUuid());
      if (requester == null) {
         finish(request, "failed", "gui.needsofnature.player_request.invalid");
      } else {
         ServerPlayNetworking.send(requester, new PlayerAnimationRequestStatusS2CPayload(request.id(), "waiting", "gui.needsofnature.player_request.waiting"));
         String tagsJson = stringListJson(request.contentTags());

         for (UUID recipientUuid : request.consentPlayers()) {
            ServerPlayerEntity recipient = getPlayer(world, recipientUuid);
            if (recipient == null) {
               finish(request, "failed", "gui.needsofnature.player_request.disconnected");
               return;
            }

            ServerPlayNetworking.send(
               recipient,
               new PlayerAnimationConsentS2CPayload(
                  request.id(),
                  requester.getUuid(),
                  requester.getGameProfile().name(),
                  request.animationId().toString(),
                  request.displayName() == null ? "" : request.displayName(),
                  tagsJson,
                  request.kind() == NonPlayerInteractionSystem.RequestKind.JOIN
               )
            );
         }
      }
   }

   private static void handleProfileAction(ServerPlayerEntity player, PlayerProfileActionC2SPayload payload) {
      if (player != null && player.getEntityWorld() instanceof ServerWorld world) {
         NonPlayerInteractionProfiles profiles = NonPlayerInteractionProfiles.get(world);
         profiles.recordKnownPlayer(player);
         String action = payload.action() == null ? "" : payload.action().trim().toLowerCase(Locale.ROOT);
         if (action.equals("request")) {
            sendProfileSnapshot(player, "");
         } else {
            NonPlayerInteractionProfiles.Mode mode = NonPlayerInteractionProfiles.Mode.fromId(
               payload.mode(), NonPlayerInteractionProfiles.Mode.REQUESTS_AND_ATTACKS
            );
            if (action.equals("default")) {
               profiles.setDefaultMode(player.getUuid(), mode, allowedMask());
               sendProfileSnapshot(player, "");
            } else {
               NonPlayerInteractionProfiles.KnownPlayer target = findKnownPlayer(profiles, payload.target());
               if (target == null) {
                  sendProfileSnapshot(player, "gui.needsofnature.player_profiles.unknown_player");
               } else {
                  if (action.equals("set")) {
                     profiles.setOverride(player.getUuid(), target.uuid(), target.name(), mode, allowedMask());
                  } else if (action.equals("remove")) {
                     profiles.removeOverride(player.getUuid(), target.uuid());
                  }

                  sendProfileSnapshot(player, "");
               }
            }
         }
      }
   }

   @Nullable
   private static NonPlayerInteractionProfiles.KnownPlayer findKnownPlayer(NonPlayerInteractionProfiles profiles, String input) {
      if (profiles != null && input != null && !input.isBlank()) {
         try {
            NonPlayerInteractionProfiles.KnownPlayer byUuid = profiles.findKnownPlayer(UUID.fromString(input.trim()));
            if (byUuid != null) {
               return byUuid;
            }
         } catch (IllegalArgumentException var3) {
         }

         return profiles.findKnownPlayerByName(input);
      } else {
         return null;
      }
   }

   public static void sendProfileSnapshot(ServerPlayerEntity player, String errorKey) {
      if (player != null && player.getEntityWorld() instanceof ServerWorld world) {
         NonPlayerInteractionProfiles.Snapshot var10 = NonPlayerInteractionProfiles.get(world).snapshot(player.getUuid(), allowedMask());
         JsonObject root = new JsonObject();
         root.addProperty("default_mode", var10.defaultMode().id());
         root.addProperty("allowed_mask", allowedMask());
         JsonArray overrides = new JsonArray();

         for (NonPlayerInteractionProfiles.OverrideView entry : var10.overrides()) {
            JsonObject object = new JsonObject();
            object.addProperty("uuid", entry.uuid().toString());
            object.addProperty("name", entry.name());
            object.addProperty("mode", entry.mode().id());
            overrides.add(object);
         }

         root.add("overrides", overrides);
         JsonArray knownPlayers = new JsonArray();

         for (NonPlayerInteractionProfiles.KnownPlayer entry : var10.knownPlayers()) {
            JsonObject object = new JsonObject();
            object.addProperty("uuid", entry.uuid().toString());
            object.addProperty("name", entry.name());
            knownPlayers.add(object);
         }

         root.add("known_players", knownPlayers);
         ServerPlayNetworking.send(player, new PlayerProfileSnapshotS2CPayload(root.toString(), errorKey == null ? "" : errorKey));
      }
   }

   private static String candidatesJson(NonPlayerInteractionSystem.SelectionSession session, UUID requesterUuid) {
      JsonArray array = new JsonArray();
      int requesterIndex = session.actorUuids().indexOf(requesterUuid);

      for (int index = 0; index < session.candidates().size(); index++) {
         NonPlayerInteractionSystem.Candidate candidate = session.candidates().get(index);
         JsonObject object = new JsonObject();
         object.addProperty("index", index);
         object.addProperty("animation_id", candidate.animationId().toString());
         object.addProperty("display_name", candidate.displayName() == null ? "" : candidate.displayName());
         object.addProperty("requester_role", roleAt(candidate.actorKeys(), requesterIndex));
         object.addProperty("requester_activity", activityAt(candidate, requesterIndex));
         object.addProperty("selectable", candidate.selectable());
         object.addProperty("block_requirement", candidate.blockRequirementSummary());
         object.add("actor_keys", toJsonArray(candidate.actorKeys()));
         object.add("content_tags", toJsonArray(candidate.contentTags()));
         object.addProperty("pack_name", candidate.packName() == null ? "" : candidate.packName());
         object.addProperty("pack_author", candidate.packAuthor() == null ? "" : candidate.packAuthor());
         array.add(object);
      }

      return array.toString();
   }

   private static String activityAt(NonPlayerInteractionSystem.Candidate candidate, int actorIndex) {
      AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(candidate.animationId());
      if (definition == null) {
         return "active";
      } else {
         String actorKey = roleAt(candidate.actorKeys(), actorIndex);
         int constraintIndex = AnimationDefinitions.resolveActorKeys(definition).indexOf(actorKey);
         if (constraintIndex >= 0 && constraintIndex < definition.actors().size()) {
            AnimationDefinitions.ActorActivity activity = definition.actors().get(constraintIndex).activity();
            return activity == AnimationDefinitions.ActorActivity.PASSIVE ? "passive" : "active";
         } else {
            return "active";
         }
      }
   }

   private static String summarizeBlockRequirement(@Nullable AnimationDefinitions.BlockRequirements requirements) {
      if (requirements == null) {
         return "";
      }

      List<String> parts = new ArrayList<>();
      if (requirements.isWall()) {
         parts.add("墙");
         AnimationDefinitions.WallHeight height = requirements.height();
         if (height != null) {
            parts.add(height.max() == null ? "高度 " + height.min() + "+" : "高度 " + height.min() + "-" + height.max());
         }

         AnimationDefinitions.WallWidth width = requirements.width();
         if (width != null && (width.min() > 1 || width.max() != null)) {
            parts.add(width.max() == null ? "宽度 " + width.min() + "+" : "宽度 " + width.min() + "-" + width.max());
         }

         if (requirements.facing() != null) {
            parts.add("朝向 " + localizeFacing(requirements.facing().id()));
         }
      } else if (requirements.isCenterSupport()) {
         parts.add("中心支撑");
         if (requirements.support() != null) {
            parts.add(requirements.support().id().replace('_', ' '));
         }

         AnimationDefinitions.SurfaceFootprint footprint = requirements.surfaceFootprint();
         if (footprint != null) {
            parts.add("占地 " + footprint.width() + "x" + footprint.depth());
            if (footprint.margin() > 0) {
               parts.add("边距 " + footprint.margin());
            }
         }
      } else {
         parts.add(localizeBlockType(requirements.type() == null ? "blocks" : requirements.type()));
      }

      AnimationDefinitions.Clearance clearance = requirements.clearance();
      if (clearance != null) {
         parts.add("净空 " + clearance.width() + "x" + clearance.height() + "x" + clearance.depth());
      }

      AnimationDefinitions.BlockPredicate blocks = requirements.blocks();
      if (blocks != null && !blocks.isEmpty()) {
         parts.add("方块 " + summarizeBlockPredicate(blocks));
      }

      return String.join(" | ", parts);
   }

   private static String localizeBlockType(String type) {
      return switch (type) {
         case "wall" -> "墙";
         case "center support" -> "中心支撑";
         case "surface" -> "表面";
         default -> type.replace('_', ' ');
      };
   }

   private static String localizeFacing(String facing) {
      return switch (facing) {
         case "north" -> "北";
         case "south" -> "南";
         case "east" -> "东";
         case "west" -> "西";
         case "up" -> "上";
         case "down" -> "下";
         default -> facing;
      };
   }

   private static String summarizeBlockPredicate(AnimationDefinitions.BlockPredicate blocks) {
      List<String> values = new ArrayList<>();
      blocks.blockIds().stream().map(Identifier::toString).sorted().forEach(values::add);
      blocks.tagIds().stream().map(id -> "#" + id).sorted().forEach(values::add);
      return values.size() <= 3 ? String.join(", ", values) : String.join(", ", values.subList(0, 3)) + ", +" + (values.size() - 3);
   }

   private static String roleAt(List<String> actorKeys, int index) {
      return actorKeys != null && index >= 0 && index < actorKeys.size() ? actorKeys.get(index) : "player";
   }

   private static JsonArray toJsonArray(List<String> values) {
      JsonArray result = new JsonArray();
      if (values != null) {
         for (String value : values) {
            result.add(value == null ? "" : value);
         }
      }

      return result;
   }

   private static String stringListJson(List<String> values) {
      return toJsonArray(values).toString();
   }

   private static void failSelection(NonPlayerInteractionSystem.SelectionSession selection, String state, String messageKey) {
      ServerPlayerEntity requester = findPlayer(selection.requesterUuid());
      removeSelection(selection);
      if (requester != null) {
         ServerPlayNetworking.send(requester, new PlayerAnimationRequestStatusS2CPayload(selection.id(), state, messageKey));
      }
   }

   private static void removeSelection(NonPlayerInteractionSystem.SelectionSession selection) {
      if (selection != null) {
         SELECTIONS.remove(selection.id(), selection);
         SESSION_BY_PLAYER.remove(selection.requesterUuid(), selection.id());
      }
   }

   private static void finish(NonPlayerInteractionSystem.PendingRequest request, String state, String messageKey) {
      if (request != null) {
         REQUESTS.remove(request.id());
         Set<UUID> participants = new LinkedHashSet<>(request.consentPlayers());
         participants.add(request.requesterUuid());

         for (UUID playerUuid : participants) {
            SESSION_BY_PLAYER.remove(playerUuid, request.id());
            ServerPlayerEntity player = findPlayer(playerUuid);
            if (player != null) {
               ServerPlayNetworking.send(player, new PlayerAnimationRequestStatusS2CPayload(request.id(), state, messageKey));
            }
         }
      }
   }

   private static void cancelForPlayer(UUID playerUuid, String messageKey) {
      if (playerUuid != null) {
         UUID sessionId = SESSION_BY_PLAYER.get(playerUuid);
         if (sessionId != null) {
            NonPlayerInteractionSystem.SelectionSession selection = SELECTIONS.get(sessionId);
            if (selection != null) {
               failSelection(selection, "cancelled", messageKey);
            } else {
               NonPlayerInteractionSystem.PendingRequest request = REQUESTS.get(sessionId);
               if (request != null) {
                  finish(request, "cancelled", messageKey);
               }
            }
         }
      }
   }

   private static void sendStandaloneFailure(ServerPlayerEntity player, String messageKey) {
      if (player != null) {
         UUID requestId = UUID.randomUUID();
         ServerPlayNetworking.send(player, new PlayerAnimationRequestStatusS2CPayload(requestId, "failed", messageKey));
      }
   }

   private static NonPlayerInteractionProfiles.Mode resolveMode(ServerWorld world, ServerPlayerEntity owner, ServerPlayerEntity other) {
      return NonPlayerInteractionProfiles.get(world).resolveMode(owner.getUuid(), other.getUuid(), allowedMask());
   }

   private static int allowedMask() {
      return NeedsOfNature.getConfig().getAllowedPlayerInteractionProfilesMask();
   }

   private static boolean hasRequestEnergy(ServerPlayerEntity player) {
      return player instanceof EnergyHolder holder && holder.getEnergy() >= NeedsOfNature.getConfig().getPlayerRequestMinEnergy();
   }

   private static boolean isUsablePlayer(@Nullable ServerPlayerEntity player) {
      return player != null && !player.isSpectator() && player.isAlive() && !player.isRemoved();
   }

   private static boolean validInteractionPair(@Nullable ServerPlayerEntity requester, @Nullable Entity target) {
      if (isUsablePlayer(requester) && target != null && !target.isRemoved()) {
         return requester.getEntityWorld() != target.getEntityWorld() ? false : requester.canInteractWithEntity(target, 0.0);
      } else {
         return false;
      }
   }

   private static boolean isBusyForDirectRequest(ServerWorld world, @Nullable ServerPlayerEntity requester, @Nullable Entity target) {
      return requester != null && target instanceof ServerPlayerEntity targetPlayer
         ? AnimationService.isActorPendingOrActive(world, requester.getUuid()) || AnimationService.isActorPendingOrActive(world, targetPlayer.getUuid())
         : true;
   }

   private static List<Entity> sortedActors(Entity first, Entity second) {
      List<Entity> result = new ArrayList<>(List.of(first, second));
      result.sort(Comparator.comparingInt(Entity::getId));
      return List.copyOf(result);
   }

   private static List<Entity> entitiesForJoin(ServerWorld world, UUID instanceId, ServerPlayerEntity requester) {
      List<Entity> actors = resolveActors(world, NeedsOfNature.getInstanceActors(world, instanceId));
      if (actors.isEmpty()) {
         return List.of();
      }

      if (actors.stream().noneMatch(actor -> actor.getUuid().equals(requester.getUuid()))) {
         actors.add(requester);
      }

      actors.sort(Comparator.comparingInt(Entity::getId));
      return List.copyOf(actors);
   }

   private static List<Entity> resolveActors(ServerWorld world, List<UUID> actorUuids) {
      List<Entity> result = new ArrayList<>();
      if (world != null && actorUuids != null) {
         for (UUID actorUuid : actorUuids) {
            Entity actor = resolveEntity(world, actorUuid);
            if (actor != null && !actor.isRemoved()) {
               result.add(actor);
            }
         }

         result.sort(Comparator.comparingInt(Entity::getId));
         return result;
      } else {
         return result;
      }
   }

   @Nullable
   private static Entity resolveEntity(ServerWorld world, UUID uuid) {
      if (world != null && uuid != null) {
         ServerPlayerEntity player = getPlayer(world, uuid);
         return (Entity)(player != null ? player : world.getEntity(uuid));
      } else {
         return null;
      }
   }

   @Nullable
   private static ServerPlayerEntity getPlayer(ServerWorld world, UUID uuid) {
      if (world != null && uuid != null && world.getServer() != null) {
         ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
         return player != null && player.getEntityWorld() == world ? player : null;
      } else {
         return null;
      }
   }

   @Nullable
   private static ServerPlayerEntity findPlayer(UUID uuid) {
      return uuid != null && activeServer != null ? activeServer.getPlayerManager().getPlayer(uuid) : null;
   }

   private record Candidate(
      Identifier animationId,
      @Nullable String displayName,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      List<String> contentTags,
      boolean selectable,
      String blockRequirementSummary,
      @Nullable String packName,
      @Nullable String packAuthor
   ) {
   }

   private record ConsentPlan(Set<UUID> consentPlayers, Set<UUID> acceptedPlayers) {
   }

   private record PendingRequest(
      UUID id,
      NonPlayerInteractionSystem.RequestKind kind,
      RegistryKey<World> worldKey,
      UUID requesterUuid,
      UUID interactionTargetUuid,
      @Nullable UUID sourceInstanceUuid,
      List<UUID> actorUuids,
      Identifier animationId,
      @Nullable String displayName,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      List<String> contentTags,
      long expiresTick,
      Set<UUID> consentPlayers,
      Set<UUID> acceptedPlayers
   ) {
      private NonPlayerInteractionSystem.PendingRequest withAccepted(UUID playerUuid) {
         Set<UUID> accepted = new LinkedHashSet<>(this.acceptedPlayers);
         accepted.add(playerUuid);
         return new NonPlayerInteractionSystem.PendingRequest(
            this.id,
            this.kind,
            this.worldKey,
            this.requesterUuid,
            this.interactionTargetUuid,
            this.sourceInstanceUuid,
            this.actorUuids,
            this.animationId,
            this.displayName,
            this.actorKeys,
            this.stages,
            this.contentTags,
            this.expiresTick,
            this.consentPlayers,
            Set.copyOf(accepted)
         );
      }
   }

   private enum RequestKind {
      DIRECT,
      JOIN;
   }

   private record SelectionSession(
      UUID id,
      NonPlayerInteractionSystem.RequestKind kind,
      RegistryKey<World> worldKey,
      UUID requesterUuid,
      UUID targetUuid,
      @Nullable UUID sourceInstanceUuid,
      long expiresTick,
      List<UUID> actorUuids,
      List<NonPlayerInteractionSystem.Candidate> candidates
   ) {
   }
}
