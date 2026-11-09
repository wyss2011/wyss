package com.nonid;

import com.nonid.data.NonAccessoryDefinitions;
import com.nonid.data.NonLiquidRoles;
import com.nonid.internal.animation.api.AnimationService;
import com.nonid.internal.animation.data.AnimationGameplayMetadata;
import com.nonid.network.AccessoryShedStateS2CPayload;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public final class NonAccessoryShedSystem {
   private static final String V_SLOT = "legs/v";
   private static final String A_SLOT = "legs/a";
   private static final Map<UUID, NonAccessoryShedSystem.ShedState> SHED_STATES = new ConcurrentHashMap<>();

   private NonAccessoryShedSystem() {
   }

   public static NonAccessoryShedSystem.ShedState resolveShedStateForAnimation(Identifier animationId) {
      NonLiquidRoles.LiquidRoles roles = AnimationGameplayMetadata.liquidRoles(animationId);
      if (roles != null && !roles.injectorRoles().isEmpty()) {
         boolean shedV = false;
         boolean shedA = false;

         for (NonLiquidRoles.InjectorRole role : roles.injectorRoles().values()) {
            if (role == NonLiquidRoles.InjectorRole.V) {
               shedV = true;
            } else if (role == NonLiquidRoles.InjectorRole.A) {
               shedA = true;
            }
         }

         return new NonAccessoryShedSystem.ShedState(shedV, shedA, false, false);
      } else {
         return NonAccessoryShedSystem.ShedState.NONE;
      }
   }

   public static NonAccessoryShedSystem.ShedState forSlot(String slotId) {
      if (slotId == null) {
         return NonAccessoryShedSystem.ShedState.NONE;
      } else {
         String slot = slotId.toLowerCase(Locale.ROOT);
         if (slot.equals("legs/v") || slot.startsWith("legs/v/")) {
            return new NonAccessoryShedSystem.ShedState(true, false, true, false);
         } else {
            return !slot.equals("legs/a") && !slot.startsWith("legs/a/")
               ? NonAccessoryShedSystem.ShedState.NONE
               : new NonAccessoryShedSystem.ShedState(false, true, false, true);
         }
      }
   }

   public static void addServerShedState(ServerWorld world, ServerPlayerEntity player, NonAccessoryShedSystem.ShedState state) {
      if (world != null && player != null && state != null && !state.isEmpty()) {
         UUID uuid = player.getUuid();
         NonAccessoryShedSystem.ShedState merged = SHED_STATES.getOrDefault(uuid, NonAccessoryShedSystem.ShedState.NONE).merge(state);
         SHED_STATES.put(uuid, merged);
         syncToWorld(world, uuid, merged);
         NonTrinketsIntegration.forceAccessorySync(player);
      }
   }

   public static void clearServerShedState(ServerWorld world, UUID playerUuid) {
      if (world != null && playerUuid != null) {
         NonAccessoryShedSystem.ShedState removed = SHED_STATES.remove(playerUuid);
         if (removed != null && !removed.isEmpty()) {
            syncToWorld(world, playerUuid, NonAccessoryShedSystem.ShedState.NONE);
            if (world.getEntity(playerUuid) instanceof ServerPlayerEntity player) {
               NonTrinketsIntegration.forceAccessorySync(player);
            }
         }
      }
   }

   public static void syncAllToViewer(ServerPlayerEntity viewer) {
      if (viewer != null && !SHED_STATES.isEmpty()) {
         for (Entry<UUID, NonAccessoryShedSystem.ShedState> entry : SHED_STATES.entrySet()) {
            NonAccessoryShedSystem.ShedState state = entry.getValue();
            if (state != null && !state.isEmpty()) {
               ServerPlayNetworking.send(
                  viewer, new AccessoryShedStateS2CPayload(entry.getKey(), state.shedV(), state.shedA(), state.forcedV(), state.forcedA())
               );
            }
         }
      }
   }

   public static void setClientShedState(UUID playerUuid, boolean shedV, boolean shedA, boolean forcedV, boolean forcedA) {
      if (playerUuid != null) {
         NonAccessoryShedSystem.ShedState state = new NonAccessoryShedSystem.ShedState(shedV, shedA, forcedV, forcedA);
         if (state.isEmpty()) {
            SHED_STATES.remove(playerUuid);
         } else {
            SHED_STATES.put(playerUuid, state);
         }
      }
   }

   public static void clearAll() {
      SHED_STATES.clear();
   }

   public static void tickServerWorld(ServerWorld world) {
      if (world != null && !SHED_STATES.isEmpty()) {
         for (UUID playerUuid : List.copyOf(SHED_STATES.keySet())) {
            if (world.getEntity(playerUuid) instanceof ServerPlayerEntity player && !AnimationService.isActorPendingOrActive(world, playerUuid)) {
               clearServerShedState(world, player.getUuid());
            }
         }
      }
   }

   public static NonAccessoryShedSystem.ShedState getShedState(UUID playerUuid) {
      return playerUuid == null ? NonAccessoryShedSystem.ShedState.NONE : SHED_STATES.getOrDefault(playerUuid, NonAccessoryShedSystem.ShedState.NONE);
   }

   public static boolean isSlotShed(UUID playerUuid, String slotId) {
      return getShedState(playerUuid).isSlotShed(slotId);
   }

   public static boolean isSlotVisuallyShed(UUID playerUuid, String slotId, Identifier itemId) {
      NonAccessoryShedSystem.ShedState state = getShedState(playerUuid);
      if (!state.isSlotShed(slotId)) {
         return false;
      } else {
         return state.isSlotForcedShed(slotId) ? true : !NonAccessoryDefinitions.getBehavior(itemId).ignoreInjectorSlotVisualShedding();
      }
   }

   public static boolean isSlotEffectSuppressed(UUID playerUuid, String slotId, Identifier itemId) {
      NonAccessoryShedSystem.ShedState state = getShedState(playerUuid);
      if (!state.isSlotShed(slotId)) {
         return false;
      } else {
         return state.isSlotForcedShed(slotId) ? true : !NonAccessoryDefinitions.getBehavior(itemId).ignoreInjectorSlotEffectSuppression();
      }
   }

   private static void syncToWorld(ServerWorld world, UUID playerUuid, NonAccessoryShedSystem.ShedState state) {
      AccessoryShedStateS2CPayload payload = new AccessoryShedStateS2CPayload(playerUuid, state.shedV(), state.shedA(), state.forcedV(), state.forcedA());

      for (ServerPlayerEntity viewer : world.getPlayers()) {
         ServerPlayNetworking.send(viewer, payload);
      }
   }

   public record ShedState(boolean shedV, boolean shedA, boolean forcedV, boolean forcedA) {
      public static final NonAccessoryShedSystem.ShedState NONE = new NonAccessoryShedSystem.ShedState(false, false, false, false);

      public boolean isEmpty() {
         return !this.shedV && !this.shedA;
      }

      public NonAccessoryShedSystem.ShedState merge(NonAccessoryShedSystem.ShedState other) {
         return other != null && !other.isEmpty()
            ? new NonAccessoryShedSystem.ShedState(
               this.shedV || other.shedV, this.shedA || other.shedA, this.forcedV || other.forcedV, this.forcedA || other.forcedA
            )
            : this;
      }

      public boolean isSlotShed(String slotId) {
         if (slotId == null) {
            return false;
         } else {
            String slot = slotId.toLowerCase(Locale.ROOT);
            if (slot.equals("legs/v") || slot.startsWith("legs/v/")) {
               return this.shedV;
            } else {
               return !slot.equals("legs/a") && !slot.startsWith("legs/a/") ? false : this.shedA;
            }
         }
      }

      public boolean isSlotForcedShed(String slotId) {
         if (slotId == null) {
            return false;
         } else {
            String slot = slotId.toLowerCase(Locale.ROOT);
            if (slot.equals("legs/v") || slot.startsWith("legs/v/")) {
               return this.forcedV;
            } else {
               return !slot.equals("legs/a") && !slot.startsWith("legs/a/") ? false : this.forcedA;
            }
         }
      }
   }
}
