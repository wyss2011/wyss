package com.nonid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import org.jetbrains.annotations.Nullable;

public final class NonPlayerInteractionProfiles extends PersistentState {
   private static final Codec<NonPlayerInteractionProfiles> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, NonPlayerInteractionProfiles.PlayerProfiles.CODEC)
               .optionalFieldOf("profiles", Map.of())
               .forGetter(state -> state.profilesByOwner),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("known_players", Map.of()).forGetter(state -> state.knownPlayerNames)
         )
         .apply(instance, NonPlayerInteractionProfiles::new)
   );
   private static final PersistentStateType<NonPlayerInteractionProfiles> TYPE = new PersistentStateType(
      "needsofnature_player_interactions", NonPlayerInteractionProfiles::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE
   );
   private final Map<String, NonPlayerInteractionProfiles.PlayerProfiles> profilesByOwner;
   private final Map<String, String> knownPlayerNames;

   private NonPlayerInteractionProfiles() {
      this(Map.of(), Map.of());
   }

   private NonPlayerInteractionProfiles(Map<String, NonPlayerInteractionProfiles.PlayerProfiles> profilesByOwner, Map<String, String> knownPlayerNames) {
      this.profilesByOwner = new LinkedHashMap<>(profilesByOwner == null ? Map.of() : profilesByOwner);
      this.knownPlayerNames = new LinkedHashMap<>(knownPlayerNames == null ? Map.of() : knownPlayerNames);
   }

   public static NonPlayerInteractionProfiles get(ServerWorld world) {
      if (world != null && world.getServer() != null && world.getServer().getOverworld() != null) {
         return (NonPlayerInteractionProfiles)world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE);
      } else {
         throw new IllegalArgumentException("A live server world is required");
      }
   }

   public synchronized void recordKnownPlayer(ServerPlayerEntity player) {
      if (player != null) {
         this.recordKnownPlayer(player.getUuid(), player.getGameProfile().name());
      }
   }

   public synchronized void recordKnownPlayer(UUID uuid, String name) {
      if (uuid != null) {
         String safeName = sanitizeName(name);
         String uuidKey = uuid.toString();
         boolean changed = !safeName.equals(this.knownPlayerNames.put(uuidKey, safeName));
         Map<String, NonPlayerInteractionProfiles.PlayerProfiles> updatedOwners = new LinkedHashMap<>();

         for (Entry<String, NonPlayerInteractionProfiles.PlayerProfiles> ownerEntry : this.profilesByOwner.entrySet()) {
            NonPlayerInteractionProfiles.PlayerProfiles profiles = ownerEntry.getValue();
            NonPlayerInteractionProfiles.ProfileOverride existing = profiles.overrides().get(uuidKey);
            if (existing != null && !safeName.equals(existing.name())) {
               Map<String, NonPlayerInteractionProfiles.ProfileOverride> overrides = new LinkedHashMap<>(profiles.overrides());
               overrides.put(uuidKey, new NonPlayerInteractionProfiles.ProfileOverride(safeName, existing.mode()));
               updatedOwners.put(ownerEntry.getKey(), new NonPlayerInteractionProfiles.PlayerProfiles(profiles.defaultMode(), overrides));
            }
         }

         if (!updatedOwners.isEmpty()) {
            this.profilesByOwner.putAll(updatedOwners);
            changed = true;
         }

         if (changed) {
            this.markDirty();
         }
      }
   }

   @Nullable
   public synchronized NonPlayerInteractionProfiles.KnownPlayer findKnownPlayerByName(String name) {
      String normalized = sanitizeName(name);
      if (normalized.isEmpty()) {
         return null;
      }

      for (Entry<String, String> entry : this.knownPlayerNames.entrySet()) {
         if (entry.getValue().equalsIgnoreCase(normalized)) {
            try {
               return new NonPlayerInteractionProfiles.KnownPlayer(UUID.fromString(entry.getKey()), entry.getValue());
            } catch (IllegalArgumentException ignored) {
               return null;
            }
         }
      }

      return null;
   }

   @Nullable
   public synchronized NonPlayerInteractionProfiles.KnownPlayer findKnownPlayer(UUID uuid) {
      if (uuid == null) {
         return null;
      }

      String name = this.knownPlayerNames.get(uuid.toString());
      return name == null ? null : new NonPlayerInteractionProfiles.KnownPlayer(uuid, name);
   }

   public synchronized NonPlayerInteractionProfiles.Mode resolveMode(UUID ownerUuid, UUID otherUuid, int allowedMask) {
      NonPlayerInteractionProfiles.PlayerProfiles profiles = this.getOrDefault(ownerUuid);
      NonPlayerInteractionProfiles.ProfileOverride override = otherUuid == null ? null : profiles.overrides().get(otherUuid.toString());
      return NonPlayerInteractionProfiles.Mode.normalizeAllowed(override == null ? profiles.defaultMode() : override.mode(), allowedMask);
   }

   public synchronized NonPlayerInteractionProfiles.Snapshot snapshot(UUID ownerUuid, int allowedMask) {
      this.normalizeOwner(ownerUuid, allowedMask);
      NonPlayerInteractionProfiles.PlayerProfiles profiles = this.getOrDefault(ownerUuid);
      NonPlayerInteractionProfiles.Mode normalizedDefault = NonPlayerInteractionProfiles.Mode.normalizeAllowed(profiles.defaultMode(), allowedMask);
      List<NonPlayerInteractionProfiles.OverrideView> overrides = new ArrayList<>();

      for (Entry<String, NonPlayerInteractionProfiles.ProfileOverride> entry : profiles.overrides().entrySet()) {
         try {
            UUID uuid = UUID.fromString(entry.getKey());
            NonPlayerInteractionProfiles.ProfileOverride value = entry.getValue();
            overrides.add(new NonPlayerInteractionProfiles.OverrideView(uuid, value.name(), value.mode()));
         } catch (IllegalArgumentException var11) {
         }
      }

      overrides.sort(Comparator.comparing(NonPlayerInteractionProfiles.OverrideView::name, String.CASE_INSENSITIVE_ORDER));
      List<NonPlayerInteractionProfiles.KnownPlayer> known = new ArrayList<>();

      for (Entry<String, String> entry : this.knownPlayerNames.entrySet()) {
         try {
            UUID uuid = UUID.fromString(entry.getKey());
            if (!uuid.equals(ownerUuid)) {
               known.add(new NonPlayerInteractionProfiles.KnownPlayer(uuid, entry.getValue()));
            }
         } catch (IllegalArgumentException var10) {
         }
      }

      known.sort(Comparator.comparing(NonPlayerInteractionProfiles.KnownPlayer::name, String.CASE_INSENSITIVE_ORDER));
      return new NonPlayerInteractionProfiles.Snapshot(normalizedDefault, List.copyOf(overrides), List.copyOf(known));
   }

   public synchronized void setDefaultMode(UUID ownerUuid, NonPlayerInteractionProfiles.Mode mode, int allowedMask) {
      if (ownerUuid != null) {
         NonPlayerInteractionProfiles.PlayerProfiles existing = this.getOrDefault(ownerUuid);
         this.profilesByOwner
            .put(
               ownerUuid.toString(),
               new NonPlayerInteractionProfiles.PlayerProfiles(NonPlayerInteractionProfiles.Mode.normalizeAllowed(mode, allowedMask), existing.overrides())
            );
         this.markDirty();
      }
   }

   public synchronized void setOverride(UUID ownerUuid, UUID otherUuid, String name, NonPlayerInteractionProfiles.Mode mode, int allowedMask) {
      if (ownerUuid != null && otherUuid != null && !ownerUuid.equals(otherUuid)) {
         NonPlayerInteractionProfiles.PlayerProfiles existing = this.getOrDefault(ownerUuid);
         Map<String, NonPlayerInteractionProfiles.ProfileOverride> overrides = new LinkedHashMap<>(existing.overrides());
         String safeName = sanitizeName(name);
         if (safeName.isEmpty()) {
            safeName = this.knownPlayerNames.getOrDefault(otherUuid.toString(), otherUuid.toString());
         }

         overrides.put(
            otherUuid.toString(),
            new NonPlayerInteractionProfiles.ProfileOverride(safeName, NonPlayerInteractionProfiles.Mode.normalizeAllowed(mode, allowedMask))
         );
         this.profilesByOwner.put(ownerUuid.toString(), new NonPlayerInteractionProfiles.PlayerProfiles(existing.defaultMode(), overrides));
         this.knownPlayerNames.put(otherUuid.toString(), safeName);
         this.markDirty();
      }
   }

   public synchronized void removeOverride(UUID ownerUuid, UUID otherUuid) {
      if (ownerUuid != null && otherUuid != null) {
         NonPlayerInteractionProfiles.PlayerProfiles existing = this.getOrDefault(ownerUuid);
         if (existing.overrides().containsKey(otherUuid.toString())) {
            Map<String, NonPlayerInteractionProfiles.ProfileOverride> overrides = new LinkedHashMap<>(existing.overrides());
            overrides.remove(otherUuid.toString());
            this.profilesByOwner.put(ownerUuid.toString(), new NonPlayerInteractionProfiles.PlayerProfiles(existing.defaultMode(), overrides));
            this.markDirty();
         }
      }
   }

   public synchronized void normalizeAll(int allowedMask) {
      boolean changed = false;

      for (String ownerKey : List.copyOf(this.profilesByOwner.keySet())) {
         changed |= this.normalizeOwnerKey(ownerKey, allowedMask);
      }

      if (changed) {
         this.markDirty();
      }
   }

   private void normalizeOwner(UUID ownerUuid, int allowedMask) {
      if (ownerUuid != null && this.normalizeOwnerKey(ownerUuid.toString(), allowedMask)) {
         this.markDirty();
      }
   }

   private boolean normalizeOwnerKey(String ownerKey, int allowedMask) {
      NonPlayerInteractionProfiles.PlayerProfiles existing = this.profilesByOwner.get(ownerKey);
      if (existing == null) {
         return false;
      }

      NonPlayerInteractionProfiles.Mode normalizedDefault = NonPlayerInteractionProfiles.Mode.normalizeAllowed(existing.defaultMode(), allowedMask);
      Map<String, NonPlayerInteractionProfiles.ProfileOverride> overrides = new LinkedHashMap<>();
      boolean changed = normalizedDefault != existing.defaultMode();

      for (Entry<String, NonPlayerInteractionProfiles.ProfileOverride> entry : existing.overrides().entrySet()) {
         NonPlayerInteractionProfiles.ProfileOverride value = entry.getValue();
         NonPlayerInteractionProfiles.Mode normalized = NonPlayerInteractionProfiles.Mode.normalizeAllowed(value.mode(), allowedMask);
         overrides.put(entry.getKey(), new NonPlayerInteractionProfiles.ProfileOverride(value.name(), normalized));
         changed |= normalized != value.mode();
      }

      if (changed) {
         this.profilesByOwner.put(ownerKey, new NonPlayerInteractionProfiles.PlayerProfiles(normalizedDefault, overrides));
      }

      return changed;
   }

   private NonPlayerInteractionProfiles.PlayerProfiles getOrDefault(UUID ownerUuid) {
      return ownerUuid == null
         ? new NonPlayerInteractionProfiles.PlayerProfiles(NonPlayerInteractionProfiles.Mode.REQUESTS_AND_ATTACKS, Map.of())
         : this.profilesByOwner
            .getOrDefault(
               ownerUuid.toString(), new NonPlayerInteractionProfiles.PlayerProfiles(NonPlayerInteractionProfiles.Mode.REQUESTS_AND_ATTACKS, Map.of())
            );
   }

   public static int normalizeAllowedMask(int mask) {
      int normalized = mask & 15;
      return normalized == 0 ? 15 : normalized;
   }

   private static String sanitizeName(@Nullable String name) {
      if (name == null) {
         return "";
      }

      String trimmed = name.trim();
      return trimmed.length() <= 64 ? trimmed : trimmed.substring(0, 64);
   }

   public record KnownPlayer(UUID uuid, String name) {
   }

   public enum Mode {
      DENY_ALL("deny_all", NonPlayerInteractionProfiles.RequestPolicy.DENY, false),
      REQUESTS_ONLY("requests_only", NonPlayerInteractionProfiles.RequestPolicy.ASK, false),
      REQUESTS_AND_ATTACKS("requests_and_attacks", NonPlayerInteractionProfiles.RequestPolicy.ASK, true),
      AUTO_ACCEPT("auto_accept", NonPlayerInteractionProfiles.RequestPolicy.AUTO_ACCEPT, true);

      public static final Codec<NonPlayerInteractionProfiles.Mode> CODEC = Codec.STRING
         .xmap(value -> fromId(value, REQUESTS_AND_ATTACKS), NonPlayerInteractionProfiles.Mode::id);
      private final String id;
      private final NonPlayerInteractionProfiles.RequestPolicy requestPolicy;
      private final boolean attacksAllowed;

      Mode(String id, NonPlayerInteractionProfiles.RequestPolicy requestPolicy, boolean attacksAllowed) {
         this.id = id;
         this.requestPolicy = requestPolicy;
         this.attacksAllowed = attacksAllowed;
      }

      public String id() {
         return this.id;
      }

      public NonPlayerInteractionProfiles.RequestPolicy requestPolicy() {
         return this.requestPolicy;
      }

      public boolean attacksAllowed() {
         return this.attacksAllowed;
      }

      public int bit() {
         return 1 << this.ordinal();
      }

      public static NonPlayerInteractionProfiles.Mode fromId(@Nullable String value, NonPlayerInteractionProfiles.Mode fallback) {
         if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);

            for (NonPlayerInteractionProfiles.Mode mode : values()) {
               if (mode.id.equals(normalized)) {
                  return mode;
               }
            }
         }

         return fallback == null ? REQUESTS_AND_ATTACKS : fallback;
      }

      public static NonPlayerInteractionProfiles.Mode leastPermissiveAllowed(int allowedMask) {
         int normalized = NonPlayerInteractionProfiles.normalizeAllowedMask(allowedMask);

         for (NonPlayerInteractionProfiles.Mode mode : values()) {
            if ((normalized & mode.bit()) != 0) {
               return mode;
            }
         }

         return REQUESTS_AND_ATTACKS;
      }

      public static NonPlayerInteractionProfiles.Mode normalizeAllowed(@Nullable NonPlayerInteractionProfiles.Mode mode, int allowedMask) {
         NonPlayerInteractionProfiles.Mode resolved = mode == null ? REQUESTS_AND_ATTACKS : mode;
         return (NonPlayerInteractionProfiles.normalizeAllowedMask(allowedMask) & resolved.bit()) != 0 ? resolved : leastPermissiveAllowed(allowedMask);
      }
   }

   public record OverrideView(UUID uuid, String name, NonPlayerInteractionProfiles.Mode mode) {
   }

   public record PlayerProfiles(NonPlayerInteractionProfiles.Mode defaultMode, Map<String, NonPlayerInteractionProfiles.ProfileOverride> overrides) {
      private static final Codec<NonPlayerInteractionProfiles.PlayerProfiles> CODEC = RecordCodecBuilder.create(
         instance -> instance.group(
               NonPlayerInteractionProfiles.Mode.CODEC
                  .optionalFieldOf("default_mode", NonPlayerInteractionProfiles.Mode.REQUESTS_AND_ATTACKS)
                  .forGetter(NonPlayerInteractionProfiles.PlayerProfiles::defaultMode),
               Codec.unboundedMap(Codec.STRING, NonPlayerInteractionProfiles.ProfileOverride.CODEC)
                  .optionalFieldOf("overrides", Map.of())
                  .forGetter(NonPlayerInteractionProfiles.PlayerProfiles::overrides)
            )
            .apply(instance, NonPlayerInteractionProfiles.PlayerProfiles::new)
      );

      public PlayerProfiles {
         defaultMode = defaultMode == null ? NonPlayerInteractionProfiles.Mode.REQUESTS_AND_ATTACKS : defaultMode;
         overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
      }
   }

   public record ProfileOverride(String name, NonPlayerInteractionProfiles.Mode mode) {
      private static final Codec<NonPlayerInteractionProfiles.ProfileOverride> CODEC = RecordCodecBuilder.create(
         instance -> instance.group(
               Codec.STRING.optionalFieldOf("name", "").forGetter(NonPlayerInteractionProfiles.ProfileOverride::name),
               NonPlayerInteractionProfiles.Mode.CODEC
                  .optionalFieldOf("mode", NonPlayerInteractionProfiles.Mode.REQUESTS_AND_ATTACKS)
                  .forGetter(NonPlayerInteractionProfiles.ProfileOverride::mode)
            )
            .apply(instance, NonPlayerInteractionProfiles.ProfileOverride::new)
      );

      public ProfileOverride {
         name = NonPlayerInteractionProfiles.sanitizeName(name);
         mode = mode == null ? NonPlayerInteractionProfiles.Mode.REQUESTS_AND_ATTACKS : mode;
      }
   }

   public enum RequestPolicy {
      DENY,
      ASK,
      AUTO_ACCEPT;
   }

   public record Snapshot(
      NonPlayerInteractionProfiles.Mode defaultMode,
      List<NonPlayerInteractionProfiles.OverrideView> overrides,
      List<NonPlayerInteractionProfiles.KnownPlayer> knownPlayers
   ) {
   }
}
