package com.nonid.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonEntityProfiles {
   private static final String ROOT_PATH = "non_entity_profiles";
   private static final String ENTRIES_KEY = "entries";
   private static final String ENTITY_KEY = "entity";
   private static final String PREGNANCY_CHANCE_KEY = "pregnancy_chance_percent";
   private static final String OFFSPRING_MIN_KEY = "offspring_min";
   private static final String OFFSPRING_MAX_KEY = "offspring_max";
   private static final String BIRTH_ENTITY_KEY = "birth_entity";
   private static final String BIRTH_MODE_KEY = "birth_mode";
   private static final String EGG_KEY = "egg";
   private static final String EGG_START_SIZE_KEY = "start_size";
   private static final String EGG_END_SIZE_KEY = "end_size";
   private static final String EGG_TEXTURE_KEY = "texture";
   private static final String EGG_HEALTH_KEY = "health";
   private static final String GENDER_SPAWN_KEY = "gender_spawn";
   private static final String MALE_CHANCE_KEY = "male_chance";
   private static final String FEMALE_CHANCE_KEY = "female_chance";
   private static final String BOTH_CHANCE_KEY = "both_chance";
   private static final String LOOK_AT_NEARBY_ANIMATIONS_KEY = "look_at_nearby_animations";
   private static final String MULTI_ACTOR_JOIN_CHANCE_KEY = "multi_actor_join_chance_percent";
   private static final String ENERGY_GAIN_MULTIPLIER_KEY = "energy_gain_multiplier";
   private static volatile Map<Identifier, NonConfig.EntityProfile> PACK_PROFILES_BY_ENTITY = Map.of();
   private static volatile ConcurrentMap<Identifier, Float> ENERGY_GAIN_MULTIPLIER_CACHE = new ConcurrentHashMap<>();

   private NonEntityProfiles() {
   }

   public static void registerReloadListener() {
      ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(NonEntityProfiles.Reloader.RELOADER_ID, new NonEntityProfiles.Reloader());
   }

   public static NonEntityProfiles.ResolvedProfile resolve(@Nullable Entity entity) {
      return entity == null ? fallbackProfile(null) : resolve(Registries.ENTITY_TYPE.getId(entity.getType()));
   }

   public static NonEntityProfiles.ResolvedProfile resolve(@Nullable Identifier entityTypeId) {
      NonEntityProfiles.ResolvedProfile profile = fallbackProfile(entityTypeId);
      if (entityTypeId == null) {
         return profile;
      }

      NonConfig.EntityProfile hardcodedDefault = hardcodedDefaultProfile(entityTypeId);
      if (hardcodedDefault != null) {
         profile = apply(profile, hardcodedDefault, entityTypeId);
      }

      NonConfig.EntityProfile packProfile = PACK_PROFILES_BY_ENTITY.get(entityTypeId);
      if (packProfile != null) {
         profile = apply(profile, packProfile, entityTypeId);
      }

      NonConfig.EntityProfile configProfile = NeedsOfNature.getConfig().getEntityProfilesByEntity().get(entityTypeId.toString());
      if (configProfile != null) {
         profile = apply(profile, configProfile, entityTypeId);
      }

      return profile;
   }

   public static float resolveEnergyGainMultiplier(@Nullable Entity entity) {
      if (entity == null) {
         return 1.0F;
      }

      Identifier entityTypeId = Registries.ENTITY_TYPE.getId(entity.getType());
      return ENERGY_GAIN_MULTIPLIER_CACHE.computeIfAbsent(entityTypeId, id -> resolve(id).energyGainMultiplier());
   }

   public static void invalidateEnergyGainMultiplierCache() {
      ENERGY_GAIN_MULTIPLIER_CACHE = new ConcurrentHashMap<>();
   }

   public static Map<String, NonConfig.EntityProfile> getPackProfilesByEntity() {
      if (PACK_PROFILES_BY_ENTITY.isEmpty()) {
         return Map.of();
      }

      Map<String, NonConfig.EntityProfile> out = new LinkedHashMap<>();

      for (Entry<Identifier, NonConfig.EntityProfile> entry : PACK_PROFILES_BY_ENTITY.entrySet()) {
         out.put(entry.getKey().toString(), entry.getValue().sanitized());
      }

      return Map.copyOf(out);
   }

   public static Map<String, NonConfig.EntityProfile> resolveEffectiveProfilesMap() {
      Set<Identifier> ids = new LinkedHashSet<>();
      ids.addAll(hardcodedDefaultProfileIds());
      ids.addAll(PACK_PROFILES_BY_ENTITY.keySet());

      for (String raw : NeedsOfNature.getConfig().getEntityProfilesByEntity().keySet()) {
         Identifier id = Identifier.tryParse(raw);
         if (id != null) {
            ids.add(id);
         }
      }

      Map<String, NonConfig.EntityProfile> out = new LinkedHashMap<>();

      for (Identifier id : ids) {
         out.put(id.toString(), resolve(id).toConfigProfile());
      }

      return Map.copyOf(out);
   }

   public static Map<String, NonConfig.EntityProfile> resolveDefaultProfilesMap() {
      Set<Identifier> ids = new LinkedHashSet<>();
      ids.addAll(hardcodedDefaultProfileIds());
      ids.addAll(PACK_PROFILES_BY_ENTITY.keySet());
      Map<String, NonConfig.EntityProfile> out = new LinkedHashMap<>();

      for (Identifier id : ids) {
         NonEntityProfiles.ResolvedProfile profile = fallbackProfile(id);
         NonConfig.EntityProfile hardcodedDefault = hardcodedDefaultProfile(id);
         if (hardcodedDefault != null) {
            profile = apply(profile, hardcodedDefault, id);
         }

         NonConfig.EntityProfile packProfile = PACK_PROFILES_BY_ENTITY.get(id);
         if (packProfile != null) {
            profile = apply(profile, packProfile, id);
         }

         out.put(id.toString(), profile.toConfigProfile());
      }

      return Map.copyOf(out);
   }

   public static NonEntityProfiles.ResolvedProfile resolveDefaultProfile(@Nullable Identifier entityTypeId) {
      NonEntityProfiles.ResolvedProfile profile = fallbackProfile(entityTypeId);
      if (entityTypeId == null) {
         return profile;
      }

      NonConfig.EntityProfile hardcodedDefault = hardcodedDefaultProfile(entityTypeId);
      if (hardcodedDefault != null) {
         profile = apply(profile, hardcodedDefault, entityTypeId);
      }

      NonConfig.EntityProfile packProfile = PACK_PROFILES_BY_ENTITY.get(entityTypeId);
      if (packProfile != null) {
         profile = apply(profile, packProfile, entityTypeId);
      }

      return profile;
   }

   public static Map<String, NonConfig.GenderSpawnChances> resolveEffectiveGenderSpawnMap() {
      Map<String, NonConfig.GenderSpawnChances> out = new LinkedHashMap<>();

      for (Entry<String, NonConfig.EntityProfile> entry : resolveEffectiveProfilesMap().entrySet()) {
         NonConfig.GenderSpawnChances chances = entry.getValue().genderSpawn();
         if (chances != null) {
            out.put(entry.getKey(), chances);
         }
      }

      return Map.copyOf(out);
   }

   private static NonEntityProfiles.ResolvedProfile fallbackProfile(@Nullable Identifier entityTypeId) {
      String entity = entityTypeId == null ? "minecraft:pig" : entityTypeId.toString();
      return new NonEntityProfiles.ResolvedProfile(
         NeedsOfNature.getConfig().getPregnancyChancePercent(),
         1,
         1,
         Identifier.tryParse(entity) == null ? Identifier.of("minecraft", "pig") : Identifier.tryParse(entity),
         "direct",
         NeedsOfNature.getConfig().getGlobalGenderSpawnChances(),
         NonConfig.EggProfile.defaults(),
         true,
         null,
         1.0F
      );
   }

   private static NonEntityProfiles.ResolvedProfile apply(NonEntityProfiles.ResolvedProfile base, NonConfig.EntityProfile patch, Identifier sourceEntityId) {
      if (patch == null) {
         return base;
      }

      NonConfig.EntityProfile safe = patch.sanitized();
      int chance = safe.pregnancyChancePercent() == null ? base.pregnancyChancePercent() : safe.pregnancyChancePercent();
      int min = safe.offspringMin() == null ? base.offspringMin() : safe.offspringMin();
      int max = safe.offspringMax() == null ? base.offspringMax() : safe.offspringMax();
      if (max < min) {
         max = min;
      }

      Identifier birthEntity = base.birthEntityTypeId();
      String rawBirthEntity = safe.birthEntity();
      if (rawBirthEntity != null) {
         Identifier parsed = Identifier.tryParse(rawBirthEntity);
         if (parsed != null && Registries.ENTITY_TYPE.containsId(parsed)) {
            birthEntity = parsed;
         } else {
            NeedsOfNature.logSetupWarning("[NoN setup] Ignoring invalid birth_entity '{}' for entity profile {}.", rawBirthEntity, sourceEntityId);
         }
      }

      String birthMode = safe.birthMode() == null ? base.birthMode() : safe.birthMode();
      NonConfig.GenderSpawnChances genderSpawn = safe.genderSpawn() == null ? base.genderSpawn() : safe.genderSpawn();
      NonConfig.EggProfile eggProfile = safe.egg() == null ? base.eggProfile() : safe.egg().resolvedOver(base.eggProfile());
      boolean lookAtNearbyAnimations = safe.lookAtNearbyAnimations() == null ? base.lookAtNearbyAnimations() : safe.lookAtNearbyAnimations();
      Integer multiActorJoinChancePercent = safe.multiActorJoinChancePercent() == null
         ? base.multiActorJoinChancePercent()
         : safe.multiActorJoinChancePercent();
      float energyGainMultiplier = safe.energyGainMultiplier() == null ? base.energyGainMultiplier() : safe.energyGainMultiplier();
      return new NonEntityProfiles.ResolvedProfile(
         chance, min, max, birthEntity, birthMode, genderSpawn, eggProfile, lookAtNearbyAnimations, multiActorJoinChancePercent, energyGainMultiplier
      );
   }

   @Nullable
   private static NonConfig.EntityProfile hardcodedDefaultProfile(Identifier entityTypeId) {
      return null;
   }

   private static Set<Identifier> hardcodedDefaultProfileIds() {
      return Set.of();
   }

   private static void reload(ResourceManager resourceManager) {
      Map<Identifier, Resource> resources = resourceManager.findResources("non_entity_profiles", id -> id.getPath().endsWith(".json"));
      List<Entry<Identifier, Resource>> ordered = new ArrayList<>(resources.entrySet());
      ordered.sort(Comparator.comparing(entryx -> ((Identifier)entryx.getKey()).toString()));
      Map<Identifier, NonConfig.EntityProfile> profiles = new LinkedHashMap<>();

      for (Entry<Identifier, Resource> entry : ordered) {
         Identifier fileId = entry.getKey();

         try (InputStreamReader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray entries = asArray(root.get("entries"));
            if (entries != null && !entries.isEmpty()) {
               parseEntries(fileId, entries, profiles);
            }
         } catch (IOException | RuntimeException e) {
            NeedsOfNature.LOGGER.debug("[NoN] Failed to read entity profile file {}", fileId, e);
         }
      }

      PACK_PROFILES_BY_ENTITY = Map.copyOf(profiles);
      invalidateEnergyGainMultiplierCache();
   }

   private static void parseEntries(Identifier fileId, JsonArray entries, Map<Identifier, NonConfig.EntityProfile> profiles) {
      for (int i = 0; i < entries.size(); i++) {
         JsonObject obj = asObject(entries.get(i));
         if (obj == null) {
            warnMalformed(fileId, i, "Entry is not an object.");
         } else {
            Identifier entityId = parseEntityId(obj.get("entity"));
            if (entityId == null) {
               warnMalformed(fileId, i, "Missing or invalid 'entity'.");
            } else {
               NonConfig.EntityProfile profile = parseProfile(fileId, i, obj, entityId);
               if (profile != null && !profile.isEmpty()) {
                  profiles.put(entityId, profile);
               } else {
                  warnMalformed(fileId, i, "Entry has no valid profile fields.");
               }
            }
         }
      }
   }

   @Nullable
   private static NonConfig.EntityProfile parseProfile(Identifier fileId, int index, JsonObject obj, Identifier entityId) {
      Integer chance = parseOptionalInt(obj.get("pregnancy_chance_percent"));
      Integer offspringMin = parseOptionalInt(obj.get("offspring_min"));
      Integer offspringMax = parseOptionalInt(obj.get("offspring_max"));
      String birthEntity = null;
      if (obj.has("birth_entity")) {
         Identifier parsed = parseEntityId(obj.get("birth_entity"));
         if (parsed != null && Registries.ENTITY_TYPE.containsId(parsed)) {
            birthEntity = parsed.toString();
         } else {
            warnMalformed(fileId, index, "Invalid 'birth_entity'.");
         }
      }

      String birthMode = null;
      if (obj.has("birth_mode")) {
         birthMode = parseOptionalString(obj.get("birth_mode"));
         birthMode = NonConfig.normalizeBirthMode(birthMode);
         if (birthMode == null) {
            warnMalformed(fileId, index, "Invalid 'birth_mode'. Use 'egg' or 'direct'.");
         }
      }

      NonConfig.GenderSpawnChances genderSpawn = null;
      JsonObject genderObj = asObject(obj.get("gender_spawn"));
      if (genderObj != null) {
         genderSpawn = parseGenderSpawn(fileId, index, genderObj);
      }

      NonConfig.EggProfile eggProfile = null;
      JsonObject eggObj = asObject(obj.get("egg"));
      if (eggObj != null) {
         eggProfile = parseEggProfile(fileId, index, eggObj);
      }

      Boolean lookAtNearbyAnimations = parseOptionalBoolean(obj.get("look_at_nearby_animations"));
      if (obj.has("look_at_nearby_animations") && lookAtNearbyAnimations == null) {
         warnMalformed(fileId, index, "'look_at_nearby_animations' must be a boolean.");
      }

      Integer multiActorJoinChancePercent = parseOptionalInt(obj.get("multi_actor_join_chance_percent"));
      if (obj.has("multi_actor_join_chance_percent") && multiActorJoinChancePercent == null) {
         warnMalformed(fileId, index, "'multi_actor_join_chance_percent' must be an integer from 0 to 100.");
      } else if (multiActorJoinChancePercent != null && (multiActorJoinChancePercent < 0 || multiActorJoinChancePercent > 100)) {
         warnMalformed(fileId, index, "'multi_actor_join_chance_percent' must be 0-100.");
         multiActorJoinChancePercent = null;
      }

      Float energyGainMultiplier = parseOptionalFloat(obj.get("energy_gain_multiplier"));
      if (obj.has("energy_gain_multiplier") && energyGainMultiplier == null) {
         warnMalformed(fileId, index, "'energy_gain_multiplier' must be a number from 0.1 to 10.0.");
      } else if (energyGainMultiplier != null && (energyGainMultiplier < 0.1F || energyGainMultiplier > 10.0F)) {
         warnMalformed(fileId, index, "'energy_gain_multiplier' must be 0.1-10.0.");
         energyGainMultiplier = null;
      }

      if (chance != null && (chance < 0 || chance > 100)) {
         warnMalformed(fileId, index, "'pregnancy_chance_percent' must be 0-100.");
         chance = null;
      }

      if (offspringMin != null && (offspringMin < 1 || offspringMin > 16)) {
         warnMalformed(fileId, index, "'offspring_min' is out of range.");
         offspringMin = null;
      }

      if (offspringMax != null && (offspringMax < 1 || offspringMax > 16)) {
         warnMalformed(fileId, index, "'offspring_max' is out of range.");
         offspringMax = null;
      }

      if (offspringMin != null && offspringMax != null && offspringMax < offspringMin) {
         warnMalformed(fileId, index, "'offspring_max' must be at least 'offspring_min'.");
         offspringMax = offspringMin;
      }

      return new NonConfig.EntityProfile(
            chance,
            offspringMin,
            offspringMax,
            birthEntity,
            birthMode,
            genderSpawn,
            eggProfile,
            lookAtNearbyAnimations,
            multiActorJoinChancePercent,
            energyGainMultiplier
         )
         .sanitized();
   }

   @Nullable
   private static NonConfig.EggProfile parseEggProfile(Identifier fileId, int index, JsonObject obj) {
      Float startSize = parseOptionalFloat(obj.get("start_size"));
      Float endSize = parseOptionalFloat(obj.get("end_size"));
      Float health = parseOptionalFloat(obj.get("health"));
      String texture = null;
      if (obj.has("texture")) {
         texture = parseOptionalString(obj.get("texture"));
         if (texture != null && NonConfig.normalizeTextureIdString(texture) == null) {
            warnMalformed(fileId, index, "Invalid egg 'texture'.");
            texture = null;
         }
      }

      if (obj.has("start_size") && startSize == null) {
         warnMalformed(fileId, index, "Egg 'start_size' must be a number.");
      }

      if (obj.has("end_size") && endSize == null) {
         warnMalformed(fileId, index, "Egg 'end_size' must be a number.");
      }

      if (obj.has("health") && health == null) {
         warnMalformed(fileId, index, "Egg 'health' must be a number.");
      }

      if (startSize != null && (startSize < 0.05F || startSize > 4.0F)) {
         warnMalformed(fileId, index, "Egg 'start_size' is out of range.");
         startSize = null;
      }

      if (endSize != null && (endSize < 0.05F || endSize > 4.0F)) {
         warnMalformed(fileId, index, "Egg 'end_size' is out of range.");
         endSize = null;
      }

      if (health != null && (health < 0.5F || health > 100.0F)) {
         warnMalformed(fileId, index, "Egg 'health' is out of range.");
         health = null;
      }

      NonConfig.EggProfile profile = new NonConfig.EggProfile(startSize, endSize, texture, health).sanitized();
      return profile.isEmpty() ? null : profile;
   }

   @Nullable
   private static NonConfig.GenderSpawnChances parseGenderSpawn(Identifier fileId, int index, JsonObject obj) {
      Integer male = parseOptionalInt(obj.get("male_chance"));
      Integer female = parseOptionalInt(obj.get("female_chance"));
      Integer both = parseOptionalInt(obj.get("both_chance"));
      if (male != null && female != null && both != null) {
         NonConfig.GenderSpawnChances chances = NonConfig.sanitizeGenderSpawnChances(new NonConfig.GenderSpawnChances(male, female, both));
         if (chances == null) {
            warnMalformed(fileId, index, "Gender spawn percentages must be 0-100 and total exactly 100.");
         }

         return chances;
      } else {
         warnMalformed(fileId, index, "Gender spawn needs male_chance, female_chance, and both_chance.");
         return null;
      }
   }

   @Nullable
   private static Identifier parseEntityId(@Nullable JsonElement element) {
      String raw = parseOptionalString(element);
      return raw == null ? null : Identifier.tryParse(raw);
   }

   @Nullable
   private static String parseOptionalString(@Nullable JsonElement element) {
      if (element == null || !element.isJsonPrimitive()) {
         return null;
      }

      if (!element.getAsJsonPrimitive().isString()) {
         return null;
      }

      String raw = element.getAsString();
      return raw != null && !raw.isBlank() ? raw.trim() : null;
   }

   @Nullable
   private static Integer parseOptionalInt(@Nullable JsonElement element) {
      if (element != null && element.isJsonPrimitive()) {
         JsonPrimitive primitive = element.getAsJsonPrimitive();
         if (primitive.isNumber()) {
            double value = primitive.getAsDouble();
            if (!Double.isFinite(value) || value != Math.rint(value)) {
               return null;
            } else {
               return !(value < -2.1474836E9F) && !(value > 2.147483647E9) ? (int)value : null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static Float parseOptionalFloat(@Nullable JsonElement element) {
      if (element != null && element.isJsonPrimitive()) {
         JsonPrimitive primitive = element.getAsJsonPrimitive();
         if (!primitive.isNumber()) {
            return null;
         } else {
            double value = primitive.getAsDouble();
            if (!Double.isFinite(value)) {
               return null;
            } else {
               return !(value < -Float.MAX_VALUE) && !(value > Float.MAX_VALUE) ? (float)value : null;
            }
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static Boolean parseOptionalBoolean(@Nullable JsonElement element) {
      if (element != null && element.isJsonPrimitive()) {
         JsonPrimitive primitive = element.getAsJsonPrimitive();
         return primitive.isBoolean() ? primitive.getAsBoolean() : null;
      } else {
         return null;
      }
   }

   @Nullable
   private static JsonObject asObject(@Nullable JsonElement element) {
      return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
   }

   @Nullable
   private static JsonArray asArray(@Nullable JsonElement element) {
      return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
   }

   private static void warnMalformed(Identifier fileId, int index, String reason) {
      NeedsOfNature.logSetupWarning("[NoN setup] Ignoring malformed entity profile entry in {} at index {}: {}", fileId, index, reason);
   }

   public static final class Reloader implements SynchronousResourceReloader {
      static final Identifier RELOADER_ID = Identifier.of("needsofnature", "entity_profiles");

      public void reload(ResourceManager manager) {
         NonEntityProfiles.reload(manager);
      }
   }

   public record ResolvedProfile(
      int pregnancyChancePercent,
      int offspringMin,
      int offspringMax,
      Identifier birthEntityTypeId,
      String birthMode,
      NonConfig.GenderSpawnChances genderSpawn,
      NonConfig.EggProfile eggProfile,
      boolean lookAtNearbyAnimations,
      @Nullable Integer multiActorJoinChancePercent,
      float energyGainMultiplier
   ) {
      public ResolvedProfile {
         pregnancyChancePercent = Math.max(0, Math.min(100, pregnancyChancePercent));
         offspringMin = Math.max(1, Math.min(16, offspringMin));
         offspringMax = Math.max(offspringMin, Math.min(16, offspringMax));
         if (birthEntityTypeId == null) {
            birthEntityTypeId = Identifier.of("minecraft", "pig");
         }

         birthMode = NonConfig.normalizeBirthMode(birthMode);
         if (birthMode == null) {
            birthMode = "direct";
         }

         genderSpawn = NonConfig.validOrDefault(genderSpawn);
         eggProfile = eggProfile == null ? NonConfig.EggProfile.defaults() : eggProfile.resolvedOver(NonConfig.EggProfile.defaults());
         if (multiActorJoinChancePercent != null) {
            multiActorJoinChancePercent = Math.max(0, Math.min(100, multiActorJoinChancePercent));
         }

         if (!Float.isFinite(energyGainMultiplier)) {
            energyGainMultiplier = 1.0F;
         }

         energyGainMultiplier = Math.max(0.1F, Math.min(10.0F, energyGainMultiplier));
      }

      public NonConfig.OffspringCountRange offspringRange() {
         return new NonConfig.OffspringCountRange(this.offspringMin, this.offspringMax);
      }

      public NonConfig.EntityProfile toConfigProfile() {
         return NonConfig.EntityProfile.full(
            this.pregnancyChancePercent,
            this.offspringMin,
            this.offspringMax,
            this.birthEntityTypeId.toString(),
            this.birthMode,
            this.genderSpawn,
            this.eggProfile,
            this.lookAtNearbyAnimations,
            this.multiActorJoinChancePercent,
            this.energyGainMultiplier
         );
      }
   }
}
