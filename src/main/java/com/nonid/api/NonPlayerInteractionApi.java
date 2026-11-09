package com.nonid.api;

import com.nonid.NeedsOfNature;
import com.nonid.NonPlayerInteractionProfiles;
import com.nonid.NonPlayerInteractionSystem;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

public final class NonPlayerInteractionApi {
   private NonPlayerInteractionApi() {
   }

   @Nullable
   public static NonPlayerInteractionApi.Profile getProfile(ServerPlayerEntity owner) {
      ServerWorld world = serverWorld(owner);
      if (world == null) {
         return null;
      }

      NonPlayerInteractionProfiles.Snapshot snapshot = NonPlayerInteractionProfiles.get(world).snapshot(owner.getUuid(), allowedMask());
      return new NonPlayerInteractionApi.Profile(
         toApi(snapshot.defaultMode()),
         snapshot.overrides().stream().map(entry -> new NonPlayerInteractionApi.Override(entry.uuid(), entry.name(), toApi(entry.mode()))).toList(),
         snapshot.knownPlayers().stream().map(entry -> new NonPlayerInteractionApi.KnownPlayer(entry.uuid(), entry.name())).toList()
      );
   }

   @Nullable
   public static NonPlayerInteractionApi.Mode getResolvedMode(ServerPlayerEntity owner, UUID otherPlayer) {
      ServerWorld world = serverWorld(owner);
      return world != null && otherPlayer != null
         ? toApi(NonPlayerInteractionProfiles.get(world).resolveMode(owner.getUuid(), otherPlayer, allowedMask()))
         : null;
   }

   public static boolean canRequest(ServerPlayerEntity requester, ServerPlayerEntity target) {
      NonPlayerInteractionApi.Mode mode = target != null && requester != null ? getResolvedMode(target, requester.getUuid()) : null;
      return mode != null && mode.acceptsRequests();
   }

   public static boolean canAttack(ServerPlayerEntity attacker, ServerPlayerEntity target) {
      NonPlayerInteractionApi.Mode mode = target != null && attacker != null ? getResolvedMode(target, attacker.getUuid()) : null;
      return mode != null && mode.acceptsAttacks();
   }

   public static boolean setDefaultMode(ServerPlayerEntity owner, NonPlayerInteractionApi.Mode mode) {
      ServerWorld world = serverWorld(owner);
      if (world != null && mode != null) {
         NonPlayerInteractionProfiles.get(world).setDefaultMode(owner.getUuid(), toInternal(mode), allowedMask());
         sync(owner);
         return true;
      } else {
         return false;
      }
   }

   public static boolean setOverride(ServerPlayerEntity owner, ServerPlayerEntity otherPlayer, NonPlayerInteractionApi.Mode mode) {
      return otherPlayer == null ? false : setOverride(owner, otherPlayer.getUuid(), otherPlayer.getGameProfile().name(), mode);
   }

   public static boolean setOverride(ServerPlayerEntity owner, UUID otherPlayer, String otherPlayerName, NonPlayerInteractionApi.Mode mode) {
      ServerWorld world = serverWorld(owner);
      if (world != null && otherPlayer != null && mode != null && !otherPlayer.equals(owner.getUuid())) {
         NonPlayerInteractionProfiles.get(world).setOverride(owner.getUuid(), otherPlayer, otherPlayerName, toInternal(mode), allowedMask());
         sync(owner);
         return true;
      } else {
         return false;
      }
   }

   public static boolean removeOverride(ServerPlayerEntity owner, UUID otherPlayer) {
      ServerWorld world = serverWorld(owner);
      if (world != null && otherPlayer != null) {
         NonPlayerInteractionProfiles.get(world).removeOverride(owner.getUuid(), otherPlayer);
         sync(owner);
         return true;
      } else {
         return false;
      }
   }

   @Nullable
   private static ServerWorld serverWorld(@Nullable ServerPlayerEntity player) {
      return player != null && player.getEntityWorld() instanceof ServerWorld world ? world : null;
   }

   private static int allowedMask() {
      return NeedsOfNature.getConfig().getAllowedPlayerInteractionProfilesMask();
   }

   private static void sync(ServerPlayerEntity owner) {
      NonPlayerInteractionSystem.sendProfileSnapshot(owner, "");
   }

   private static NonPlayerInteractionApi.Mode toApi(NonPlayerInteractionProfiles.Mode mode) {
      return switch (mode) {
         case DENY_ALL -> NonPlayerInteractionApi.Mode.DENY_ALL;
         case REQUESTS_ONLY -> NonPlayerInteractionApi.Mode.REQUESTS_ONLY;
         case REQUESTS_AND_ATTACKS -> NonPlayerInteractionApi.Mode.REQUESTS_AND_ATTACKS;
         case AUTO_ACCEPT -> NonPlayerInteractionApi.Mode.AUTO_ACCEPT;
      };
   }

   private static NonPlayerInteractionProfiles.Mode toInternal(NonPlayerInteractionApi.Mode mode) {
      return switch (mode) {
         case DENY_ALL -> NonPlayerInteractionProfiles.Mode.DENY_ALL;
         case REQUESTS_ONLY -> NonPlayerInteractionProfiles.Mode.REQUESTS_ONLY;
         case REQUESTS_AND_ATTACKS -> NonPlayerInteractionProfiles.Mode.REQUESTS_AND_ATTACKS;
         case AUTO_ACCEPT -> NonPlayerInteractionProfiles.Mode.AUTO_ACCEPT;
      };
   }

   public record KnownPlayer(UUID playerUuid, String playerName) {
   }

   public enum Mode {
      DENY_ALL,
      REQUESTS_ONLY,
      REQUESTS_AND_ATTACKS,
      AUTO_ACCEPT;

      public String id() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      public boolean acceptsRequests() {
         return this != DENY_ALL;
      }

      public boolean automaticallyAcceptsRequests() {
         return this == AUTO_ACCEPT;
      }

      public boolean acceptsAttacks() {
         return this == REQUESTS_AND_ATTACKS || this == AUTO_ACCEPT;
      }
   }

   public record Override(UUID playerUuid, String playerName, NonPlayerInteractionApi.Mode mode) {
   }

   public record Profile(
      NonPlayerInteractionApi.Mode defaultMode, List<NonPlayerInteractionApi.Override> overrides, List<NonPlayerInteractionApi.KnownPlayer> knownPlayers
   ) {
      public Profile {
         overrides = List.copyOf(overrides);
         knownPlayers = List.copyOf(knownPlayers);
      }
   }
}
