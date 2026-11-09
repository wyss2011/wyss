package com.nonid.internal.animation.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nonid.api.animation.NonActorEvents;
import com.nonid.internal.animation.AnimationEngine;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.util.AnimationIds;
import com.nonid.internal.animation.util.EntityVariants;
import com.nonid.internal.animation.util.StageTimeWarp;
import com.nonid.internal.animation.util.WeightedPicker;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import org.jetbrains.annotations.Nullable;

public final class AnimationDefinitions {
   private static final int MAX_MODEL_PROVIDER_ROOTS = 32;
   private static final String NORMAL_MATCH_TAG = "normal_match";
   private static final Identifier PLAYER_ID = Identifier.of("minecraft", "player");
   private static final Identifier PLAYER_SLIM_ID = Identifier.of("minecraft", "player_slim");
   private static final String INVALID_ENTITY_VARIANT = "\u0000";
   private static final Set<String> KNOWN_DEFINITION_KEYS = Set.of(
      "actors",
      "display_name",
      "animation_tags",
      "content_tags",
      "join_after",
      "required_union_tags",
      "weight",
      "attack_eligible",
      "speed",
      "block_requirements",
      "water",
      "position_anchor_actor",
      "stages",
      "manual_peak",
      "liquid_gain_multiplier",
      "stage_seconds",
      "escapable"
   );
   private static final Set<String> KNOWN_ACTOR_KEYS = Set.of(
      "label",
      "entity_types",
      "entity_variant",
      "actor_tags",
      "actor_tags_any",
      "age",
      "activity",
      "prop_left",
      "prop_right",
      "prop_floor",
      "injector",
      "receiver"
   );
   private static final Set<String> KNOWN_STAGE_KEYS = Set.of(
      "stage",
      "use_stage",
      "loop",
      "cycle_seconds",
      "speed",
      "allow_join",
      "cycle_midpoint_offset_seconds",
      "props",
      "stage_seconds",
      "stage_duration_multiplier",
      "escapable",
      "non_peak"
   );
   private static final Set<String> KNOWN_BLOCK_REQUIREMENT_KEYS = Set.of(
      "type", "height", "width", "facing", "clearance", "support", "placement", "surface_radius", "surface_footprint", "blocks"
   );
   private static final Set<String> KNOWN_WALL_HEIGHT_KEYS = Set.of("min", "max");
   private static final Set<String> KNOWN_WALL_WIDTH_KEYS = Set.of("min", "max");
   private static final Set<String> KNOWN_CLEARANCE_KEYS = Set.of("width", "height", "depth");
   private static final Set<String> KNOWN_SURFACE_FOOTPRINT_KEYS = Set.of("width", "height", "depth", "margin");
   private static final Set<String> KNOWN_STAGE_ACTOR_PROP_KEYS = Set.of("prop_left", "prop_right", "prop_floor");
   private static volatile List<AnimationDefinitions.Definition> DEFINITIONS = List.of();
   private static volatile Map<Identifier, AnimationDefinitions.Definition> DEFINITIONS_BY_ID = Map.of();
   private static volatile Map<Integer, List<AnimationDefinitions.Definition>> DEFINITIONS_BY_ACTOR_COUNT = Map.of();
   private static volatile Map<Identifier, List<Integer>> MATCH_ORDER_BY_DEFINITION = Map.of();
   private static volatile Map<Identifier, List<AnimationDefinitions.Definition>> JOIN_FOLLOWERS_BY_PREDECESSOR = Map.of();
   private static volatile Set<AnimationDefinitions.ActorKey> GENERIC_ACTOR_TYPES = Set.of();
   private static volatile Set<AnimationDefinitions.ActorKey> GENERIC_MOB_PAIR_TYPES = Set.of();
   private static volatile Map<AnimationDefinitions.ActorKey, Set<AnimationDefinitions.ActorKey>> GENERIC_MOB_PAIR_COMPATIBILITY = Map.of();
   private static volatile Set<Identifier> DISABLED_ANIMATION_IDS = Set.of();
   private static volatile Set<String> DISABLED_ANIMATION_PACK_IDS = Set.of();

   private AnimationDefinitions() {
   }

   public static void registerReloadListener() {
      ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(AnimationDefinitions.Reloader.RELOADER_ID, new AnimationDefinitions.Reloader());
   }

   public static AnimationDefinitions.MatchResult match(List<Entity> selectedActorsSortedById) {
      return match(selectedActorsSortedById, Set.of());
   }

   public static AnimationDefinitions.MatchResult match(List<Entity> selectedActorsSortedById, Set<String> requiredAnimationTags) {
      List<AnimationDefinitions.Definition> candidates = findMatchingCandidates(selectedActorsSortedById, requiredAnimationTags);
      AnimationDefinitions.Definition chosen = pickWeighted(candidates, new Random());
      return new AnimationDefinitions.MatchResult(candidates, chosen);
   }

   public static List<AnimationDefinitions.Definition> findMatchingCandidates(List<Entity> selectedActorsSortedById, Set<String> requiredAnimationTags) {
      List<AnimationDefinitions.Definition> actorMatches = findActorMatchingCandidates(selectedActorsSortedById);
      if (actorMatches.isEmpty()) {
         return List.of();
      }

      Set<String> normalizedRequiredTags = normalizeTags(requiredAnimationTags);
      List<AnimationDefinitions.Definition> candidates = new ArrayList<>();

      for (AnimationDefinitions.Definition definition : actorMatches) {
         if (definition.matchesAnimationTags(normalizedRequiredTags)) {
            candidates.add(definition);
         }
      }

      return candidates.isEmpty() ? List.of() : List.copyOf(candidates);
   }

   public static List<AnimationDefinitions.Definition> findActorMatchingCandidates(List<Entity> selectedActorsSortedById) {
      if (selectedActorsSortedById != null && !selectedActorsSortedById.isEmpty()) {
         AnimationDefinitions.MatchContext context = AnimationDefinitions.MatchContext.create(selectedActorsSortedById);
         if (context.actors().size() != selectedActorsSortedById.size()) {
            return List.of();
         }

         List<AnimationDefinitions.Definition> candidates = new ArrayList<>();

         for (AnimationDefinitions.Definition d : DEFINITIONS_BY_ACTOR_COUNT.getOrDefault(context.actors().size(), List.of())) {
            if (isDefinitionEnabled(d) && d.matchesActors(context)) {
               candidates.add(d);
            }
         }

         return candidates.isEmpty() ? List.of() : List.copyOf(candidates);
      } else {
         return List.of();
      }
   }

   private static AnimationDefinitions.Definition pickWeighted(List<AnimationDefinitions.Definition> definitions, Random random) {
      return random == null ? null : WeightedPicker.pick(definitions, AnimationDefinitions.Definition::weight, random.nextDouble());
   }

   public static AnimationDefinitions.Definition getDefinition(Identifier id) {
      return id == null ? null : DEFINITIONS_BY_ID.get(id);
   }

   public static boolean isDefinitionEnabled(@Nullable AnimationDefinitions.Definition definition) {
      if (definition == null) {
         return false;
      }

      if (DISABLED_ANIMATION_IDS.contains(definition.id())) {
         return false;
      }

      AnimationDefinitions.AnimationPackInfo packInfo = definition.packInfo();
      return packInfo == null || !DISABLED_ANIMATION_PACK_IDS.contains(packInfo.id());
   }

   public static boolean isAnimationEnabled(Identifier animationId) {
      AnimationDefinitions.Definition definition = getDefinition(animationId);
      return definition != null && isDefinitionEnabled(definition);
   }

   public static boolean isJoinCandidateAllowed(@Nullable Identifier predecessorAnimationId, @Nullable AnimationDefinitions.Definition candidate) {
      if (candidate == null || !isDefinitionEnabled(candidate)) {
         return false;
      }

      if (predecessorAnimationId == null) {
         return true;
      }

      boolean hasEnabledFollower = false;

      for (AnimationDefinitions.Definition follower : JOIN_FOLLOWERS_BY_PREDECESSOR.getOrDefault(predecessorAnimationId, List.of())) {
         if (isDefinitionEnabled(follower)) {
            hasEnabledFollower = true;
            if (follower.id().equals(candidate.id())) {
               return true;
            }
         }
      }

      return !hasEnabledFollower && candidate.joinAfter().isEmpty();
   }

   public static boolean hasEnabledGenericAnimationForEntityType(@Nullable Identifier entityTypeId, boolean baby) {
      return entityTypeId != null && GENERIC_ACTOR_TYPES.contains(new AnimationDefinitions.ActorKey(entityTypeId, baby));
   }

   public static boolean hasEnabledGenericMobPairAnimationForEntityType(@Nullable Identifier entityTypeId, boolean baby) {
      return entityTypeId != null && GENERIC_MOB_PAIR_TYPES.contains(new AnimationDefinitions.ActorKey(entityTypeId, baby));
   }

   public static boolean areEnabledGenericMobPairTypesCompatible(
      @Nullable Identifier firstType, boolean firstBaby, @Nullable Identifier secondType, boolean secondBaby
   ) {
      if (firstType != null && secondType != null) {
         Set<AnimationDefinitions.ActorKey> partners = GENERIC_MOB_PAIR_COMPATIBILITY.get(new AnimationDefinitions.ActorKey(firstType, firstBaby));
         return partners != null && partners.contains(new AnimationDefinitions.ActorKey(secondType, secondBaby));
      } else {
         return false;
      }
   }

   private static boolean isGenericMatchDefinition(AnimationDefinitions.Definition definition) {
      if (definition == null) {
         return false;
      }

      Set<String> tags = definition.animationTags();
      return tags == null || tags.isEmpty() || tags.contains("normal_match");
   }

   public static void setDisabledAnimationFilters(@Nullable Collection<Identifier> animationIds, @Nullable Collection<String> packIds) {
      LinkedHashSet<Identifier> cleanedAnimations = new LinkedHashSet<>();
      if (animationIds != null) {
         for (Identifier id : animationIds) {
            if (id != null) {
               cleanedAnimations.add(id);
            }
         }
      }

      LinkedHashSet<String> cleanedPacks = new LinkedHashSet<>();
      if (packIds != null) {
         for (String raw : packIds) {
            String id = sanitizeNullable(raw);
            if (id != null) {
               cleanedPacks.add(id);
            }
         }
      }

      DISABLED_ANIMATION_IDS = Set.copyOf(cleanedAnimations);
      DISABLED_ANIMATION_PACK_IDS = Set.copyOf(cleanedPacks);
      rebuildEligibilityIndexes();
   }

   public static List<AnimationDefinitions.Definition> getLoadedDefinitionsSnapshot() {
      return List.copyOf(DEFINITIONS);
   }

   public static List<String> resolveActorKeys(AnimationDefinitions.Definition definition) {
      return definition != null && !definition.actors().isEmpty() ? List.of(buildUniqueActorKeys(definition.actors())) : List.of();
   }

   @Nullable
   public static AnimationDefinitions.ActorConstraint resolveActorConstraintForKey(AnimationDefinitions.Definition definition, String actorKey) {
      if (definition != null && actorKey != null && !actorKey.isBlank()) {
         String[] actorKeys = buildUniqueActorKeys(definition.actors());

         for (int i = 0; i < actorKeys.length; i++) {
            if (actorKey.equals(actorKeys[i])) {
               return definition.actors().get(i);
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static List<String> resolveActorKeys(AnimationDefinitions.Definition definition, List<Entity> selectedActorsSortedById) {
      return resolveActorKeys(definition, selectedActorsSortedById, net.minecraft.util.math.random.Random.create());
   }

   @Nullable
   public static AnimationDefinitions.ActorConstraint resolveActorConstraintForEntity(
      AnimationDefinitions.Definition definition, List<Entity> selectedActorsSortedById, Entity actor
   ) {
      return resolveActorConstraintForEntity(definition, selectedActorsSortedById, actor, net.minecraft.util.math.random.Random.create());
   }

   @Nullable
   public static AnimationDefinitions.ActorConstraint resolveActorConstraintForEntity(
      AnimationDefinitions.Definition definition, List<Entity> selectedActorsSortedById, Entity actor, net.minecraft.util.math.random.Random rng
   ) {
      if (definition != null && selectedActorsSortedById != null && actor != null) {
         if (selectedActorsSortedById.size() != definition.actors().size()) {
            return null;
         }

         int actorIndex = -1;

         for (int i = 0; i < selectedActorsSortedById.size(); i++) {
            Entity candidate = selectedActorsSortedById.get(i);
            if (candidate == actor || candidate.getUuid().equals(actor.getUuid())) {
               actorIndex = i;
               break;
            }
         }

         if (actorIndex < 0) {
            return null;
         }

         int[] assignment = matchUnorderedWithAssignment(selectedActorsSortedById, definition.actors(), rng);
         if (assignment == null) {
            return null;
         }

         for (int ci = 0; ci < assignment.length; ci++) {
            if (assignment[ci] == actorIndex) {
               return definition.actors().get(ci);
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static boolean canResolveActorAsActivity(
      AnimationDefinitions.Definition definition, List<Entity> selectedActorsSortedById, Entity actor, AnimationDefinitions.ActorActivity activity
   ) {
      if (definition != null && selectedActorsSortedById != null && actor != null && activity != null) {
         if (selectedActorsSortedById.size() != definition.actors().size()) {
            return false;
         }

         int actorIndex = -1;

         for (int i = 0; i < selectedActorsSortedById.size(); i++) {
            Entity candidate = selectedActorsSortedById.get(i);
            if (candidate == actor || candidate.getUuid().equals(actor.getUuid())) {
               actorIndex = i;
               break;
            }
         }

         if (actorIndex < 0) {
            return false;
         }

         List<AnimationDefinitions.ActorConstraint> constraints = definition.actors();
         Entity actorEntity = selectedActorsSortedById.get(actorIndex);

         for (int ci = 0; ci < constraints.size(); ci++) {
            AnimationDefinitions.ActorConstraint forced = constraints.get(ci);
            AnimationDefinitions.ActorActivity forcedActivity = forced.activity() == null ? AnimationDefinitions.ActorActivity.ACTIVE : forced.activity();
            if (forcedActivity == activity && forced.matches(actorEntity)) {
               boolean[] used = new boolean[selectedActorsSortedById.size()];
               used[actorIndex] = true;
               List<Integer> order = new ArrayList<>(constraints.size() - 1);

               for (int i = 0; i < constraints.size(); i++) {
                  if (i != ci) {
                     order.add(i);
                  }
               }

               order.sort((a, b) -> {
                  AnimationDefinitions.ActorConstraint ca = constraints.get(a);
                  AnimationDefinitions.ActorConstraint cb = constraints.get(b);
                  int cmp = Integer.compare(cb.assignmentPriority(), ca.assignmentPriority());
                  return cmp != 0 ? cmp : ca.stableKey().compareTo(cb.stableKey());
               });
               if (backtrackAssign(0, order, constraints, selectedActorsSortedById, buildEntityOrder(selectedActorsSortedById.size(), null), used, null)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static List<String> resolveActorKeys(
      AnimationDefinitions.Definition definition, List<Entity> selectedActorsSortedById, net.minecraft.util.math.random.Random rng
   ) {
      if (definition == null) {
         return List.of();
      }

      if (selectedActorsSortedById.size() != definition.actors().size()) {
         return fallbackActorKeys(selectedActorsSortedById);
      }

      int[] assignment = matchUnorderedWithAssignment(selectedActorsSortedById, definition.actors(), rng);
      if (assignment == null) {
         return fallbackActorKeys(selectedActorsSortedById);
      }

      String[] constraintKeys = buildUniqueActorKeys(definition.actors());
      String[] keysByEntityIndex = new String[selectedActorsSortedById.size()];

      for (int ci = 0; ci < assignment.length; ci++) {
         int entityIndex = assignment[ci];
         if (entityIndex >= 0 && entityIndex < keysByEntityIndex.length) {
            keysByEntityIndex[entityIndex] = constraintKeys[ci];
         }
      }

      for (int i = 0; i < keysByEntityIndex.length; i++) {
         if (keysByEntityIndex[i] == null) {
            keysByEntityIndex[i] = fallbackActorKey(selectedActorsSortedById.get(i));
         }
      }

      return List.of(keysByEntityIndex);
   }

   public static List<List<String>> resolveAllActorKeyAssignments(AnimationDefinitions.Definition definition, List<Entity> selectedActorsSortedById) {
      if (definition != null && selectedActorsSortedById != null) {
         if (selectedActorsSortedById.size() != definition.actors().size()) {
            return List.of();
         }

         List<AnimationDefinitions.ActorConstraint> constraints = definition.actors();
         List<Integer> constraintOrder = new ArrayList<>(constraints.size());

         for (int i = 0; i < constraints.size(); i++) {
            constraintOrder.add(i);
         }

         constraintOrder.sort((a, b) -> {
            AnimationDefinitions.ActorConstraint ca = constraints.get(a);
            AnimationDefinitions.ActorConstraint cb = constraints.get(b);
            int cmp = Integer.compare(cb.assignmentPriority(), ca.assignmentPriority());
            return cmp != 0 ? cmp : ca.stableKey().compareTo(cb.stableKey());
         });
         int[] assignment = new int[constraints.size()];
         Arrays.fill(assignment, -1);
         List<int[]> assignments = new ArrayList<>();
         collectAssignments(0, constraintOrder, constraints, selectedActorsSortedById, new boolean[selectedActorsSortedById.size()], assignment, assignments);
         String[] constraintKeys = buildUniqueActorKeys(constraints);
         LinkedHashSet<List<String>> unique = new LinkedHashSet<>();

         for (int[] resolved : assignments) {
            String[] keysByEntityIndex = new String[selectedActorsSortedById.size()];

            for (int constraintIndex = 0; constraintIndex < resolved.length; constraintIndex++) {
               int entityIndex = resolved[constraintIndex];
               if (entityIndex >= 0 && entityIndex < keysByEntityIndex.length) {
                  keysByEntityIndex[entityIndex] = constraintKeys[constraintIndex];
               }
            }

            if (Arrays.stream(keysByEntityIndex).noneMatch(Objects::isNull)) {
               unique.add(List.of(keysByEntityIndex));
            }
         }

         List<List<String>> result = new ArrayList<>(unique);
         result.sort(Comparator.comparing(keys -> String.join("\u0000", keys)));
         return List.copyOf(result);
      } else {
         return List.of();
      }
   }

   private static void collectAssignments(
      int pos,
      List<Integer> constraintOrder,
      List<AnimationDefinitions.ActorConstraint> constraints,
      List<Entity> entitiesSortedById,
      boolean[] used,
      int[] assignment,
      List<int[]> output
   ) {
      if (pos >= constraintOrder.size()) {
         output.add(Arrays.copyOf(assignment, assignment.length));
      } else {
         int constraintIndex = constraintOrder.get(pos);
         AnimationDefinitions.ActorConstraint constraint = constraints.get(constraintIndex);

         for (int entityIndex = 0; entityIndex < entitiesSortedById.size(); entityIndex++) {
            if (!used[entityIndex] && constraint.matches(entitiesSortedById.get(entityIndex))) {
               used[entityIndex] = true;
               assignment[constraintIndex] = entityIndex;
               collectAssignments(pos + 1, constraintOrder, constraints, entitiesSortedById, used, assignment, output);
               assignment[constraintIndex] = -1;
               used[entityIndex] = false;
            }
         }
      }
   }

   private static boolean matchesUnordered(
      List<AnimationDefinitions.MatchActor> actors, List<AnimationDefinitions.ActorConstraint> constraints, @Nullable List<Integer> cachedOrder
   ) {
      int n = actors.size();
      boolean[] used = new boolean[n];
      List<Integer> order = cachedOrder == null ? buildConstraintOrder(constraints) : cachedOrder;
      return backtrackMatchActors(0, order, constraints, actors, used);
   }

   private static boolean backtrackMatchActors(
      int pos,
      List<Integer> constraintOrder,
      List<AnimationDefinitions.ActorConstraint> constraints,
      List<AnimationDefinitions.MatchActor> actors,
      boolean[] used
   ) {
      if (pos >= constraintOrder.size()) {
         return true;
      }

      AnimationDefinitions.ActorConstraint constraint = constraints.get(constraintOrder.get(pos));

      for (int actorIndex = 0; actorIndex < actors.size(); actorIndex++) {
         if (!used[actorIndex] && constraint.matches(actors.get(actorIndex))) {
            used[actorIndex] = true;
            if (backtrackMatchActors(pos + 1, constraintOrder, constraints, actors, used)) {
               return true;
            }

            used[actorIndex] = false;
         }
      }

      return false;
   }

   private static List<Integer> buildConstraintOrder(List<AnimationDefinitions.ActorConstraint> constraints) {
      List<Integer> order = new ArrayList<>(constraints.size());

      for (int i = 0; i < constraints.size(); i++) {
         order.add(i);
      }

      order.sort((a, b) -> {
         AnimationDefinitions.ActorConstraint left = constraints.get(a);
         AnimationDefinitions.ActorConstraint right = constraints.get(b);
         int priority = Integer.compare(right.assignmentPriority(), left.assignmentPriority());
         return priority != 0 ? priority : left.stableKey().compareTo(right.stableKey());
      });
      return List.copyOf(order);
   }

   private static int[] matchUnorderedWithAssignment(
      List<Entity> entitiesSortedById, List<AnimationDefinitions.ActorConstraint> constraints, net.minecraft.util.math.random.Random rng
   ) {
      int n = entitiesSortedById.size();
      boolean[] used = new boolean[n];
      int[] assignment = new int[constraints.size()];
      Arrays.fill(assignment, -1);
      int[] entityOrder = buildEntityOrder(n, rng);
      List<Integer> order = new ArrayList<>(constraints.size());

      for (int i = 0; i < constraints.size(); i++) {
         order.add(i);
      }

      order.sort((a, b) -> {
         AnimationDefinitions.ActorConstraint ca = constraints.get(a);
         AnimationDefinitions.ActorConstraint cb = constraints.get(b);
         int cmp = Integer.compare(cb.assignmentPriority(), ca.assignmentPriority());
         return cmp != 0 ? cmp : ca.stableKey().compareTo(cb.stableKey());
      });
      return backtrackAssign(0, order, constraints, entitiesSortedById, entityOrder, used, assignment) ? assignment : null;
   }

   private static boolean backtrackAssign(
      int pos,
      List<Integer> constraintOrder,
      List<AnimationDefinitions.ActorConstraint> constraints,
      List<Entity> entitiesSortedById,
      int[] entityOrder,
      boolean[] used,
      int[] assignment
   ) {
      if (pos >= constraintOrder.size()) {
         return true;
      }

      int constraintIndex = constraintOrder.get(pos);
      AnimationDefinitions.ActorConstraint c = constraints.get(constraintIndex);

      for (int ei : entityOrder) {
         if (!used[ei]) {
            Entity e = entitiesSortedById.get(ei);
            if (c.matches(e)) {
               used[ei] = true;
               if (assignment != null) {
                  assignment[constraintIndex] = ei;
               }

               if (backtrackAssign(pos + 1, constraintOrder, constraints, entitiesSortedById, entityOrder, used, assignment)) {
                  return true;
               }

               used[ei] = false;
               if (assignment != null) {
                  assignment[constraintIndex] = -1;
               }
            }
         }
      }

      return false;
   }

   private static int[] buildEntityOrder(int size, net.minecraft.util.math.random.Random rng) {
      int[] order = new int[size];
      int i = 0;

      while (i < size) {
         order[i] = i++;
      }

      if (rng != null && size > 1) {
         for (int ix = size - 1; ix > 0; ix--) {
            int j = rng.nextInt(ix + 1);
            int tmp = order[ix];
            order[ix] = order[j];
            order[j] = tmp;
         }

         return order;
      } else {
         return order;
      }
   }

   private static List<String> fallbackActorKeys(List<Entity> selectedActorsSortedById) {
      if (selectedActorsSortedById.isEmpty()) {
         return List.of();
      }

      String[] out = new String[selectedActorsSortedById.size()];

      for (int i = 0; i < selectedActorsSortedById.size(); i++) {
         out[i] = fallbackActorKey(selectedActorsSortedById.get(i));
      }

      return List.of(out);
   }

   private static String fallbackActorKey(Entity entity) {
      Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());
      return typeId.getPath();
   }

   private static String[] buildUniqueActorKeys(List<AnimationDefinitions.ActorConstraint> constraints) {
      String[] out = new String[constraints.size()];
      Map<String, Integer> counts = new HashMap<>();

      for (int i = 0; i < constraints.size(); i++) {
         String base = constraints.get(i).derivedKey();
         int count = counts.merge(base, 1, Integer::sum);
         out[i] = count == 1 ? base : base + "_" + count;
      }

      return out;
   }

   private static String sanitizeKeyPart(String value) {
      if (value != null && !value.isEmpty()) {
         String lower = value.toLowerCase(Locale.ROOT);
         StringBuilder sb = new StringBuilder(lower.length());

         for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '_') {
               sb.append('_');
            } else {
               sb.append(c);
            }
         }

         return sb.toString();
      } else {
         return "actor";
      }
   }

   private static void reload(ResourceManager resourceManager) {
      Map<String, AnimationDefinitions.AnimationPackInfo> packInfoByResourcePackId = readAnimationPackInfos(resourceManager);
      Map<Identifier, Resource> resources = resourceManager.findResources("afw_animdefs", id -> id.getPath().endsWith(".json"));
      List<AnimationDefinitions.Definition> loaded = new ArrayList<>();

      for (Entry<Identifier, Resource> entry : resources.entrySet()) {
         Identifier fileId = entry.getKey();
         String resourcePackId = entry.getValue().getPackId();
         AnimationDefinitions.AnimationPackInfo packInfo = packInfoByResourcePackId.getOrDefault(resourcePackId, fallbackPackInfo(resourcePackId));

         try (InputStreamReader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            warnUnknownKeys(obj, fileId, "animdef", KNOWN_DEFINITION_KEYS);
            if (!obj.has("actors")) {
               AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: missing 'actors' array", fileId);
            } else {
               JsonArray actorsArr = obj.getAsJsonArray("actors");
               if (actorsArr.isEmpty()) {
                  AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: 'actors' is empty", fileId);
               } else {
                  List<AnimationDefinitions.ActorConstraint> actorConstraints = new ArrayList<>();

                  for (int i = 0; i < actorsArr.size(); i++) {
                     JsonObject a = actorsArr.get(i).getAsJsonObject();
                     warnUnknownKeys(a, fileId, "actor[" + i + "]", KNOWN_ACTOR_KEYS);
                     Set<Identifier> types = readEntityTypes(a, fileId);
                     if (types == null) {
                        actorConstraints = null;
                        break;
                     }

                     String entityVariant = readEntityVariant(a, fileId, "actor[" + i + "]");
                     if ("\u0000".equals(entityVariant)) {
                        actorConstraints = null;
                        break;
                     }

                     Set<String> reqTags = readStringArray(a, "actor_tags");
                     Set<String> reqTagsAny = readStringArray(a, "actor_tags_any");
                     String label = a.has("label") ? a.get("label").getAsString() : null;
                     AnimationDefinitions.AgeRequirement ageRequirement = readAgeRequirement(a, fileId);
                     if (ageRequirement == null) {
                        actorConstraints = null;
                        break;
                     }

                     AnimationDefinitions.ActorActivity activity = readActorActivity(a, fileId);
                     Identifier propLeftItemId = readOptionalItemId(a, "prop_left", fileId, "actor[" + i + "]");
                     Identifier propRightItemId = readOptionalItemId(a, "prop_right", fileId, "actor[" + i + "]");
                     Identifier propFloorItemId = readOptionalItemId(a, "prop_floor", fileId, "actor[" + i + "]");
                     actorConstraints.add(
                        new AnimationDefinitions.ActorConstraint(
                           Set.copyOf(types),
                           entityVariant,
                           Set.copyOf(reqTags),
                           Set.copyOf(reqTagsAny),
                           label,
                           ageRequirement,
                           activity,
                           propLeftItemId,
                           propRightItemId,
                           propFloorItemId
                        )
                     );
                  }

                  if (actorConstraints != null) {
                     Set<String> animationTags = normalizeTags(readStringArray(obj, "animation_tags"));
                     List<String> contentTags = normalizeTagList(readStringArray(obj, "content_tags"));
                     Set<String> requiredUnionTags = readStringArray(obj, "required_union_tags");
                     double weight = readWeightOrDefault(obj, fileId);
                     String displayName = readOptionalDisplayName(obj, fileId);
                     Identifier defId = definitionIdFromFile(fileId);
                     Set<Identifier> joinAfter = readJoinAfter(obj, fileId, defId);
                     AnimationDefinitions.BlockRequirements blockRequirements = readBlockRequirements(obj, fileId);
                     AnimationDefinitions.WaterRequirement waterRequirement = readWaterRequirement(obj, fileId);
                     String positionAnchorActor = readOptionalAnchorActorKey(obj, "position_anchor_actor");
                     List<AnimationStageInfo> stages = readStageInfos(obj, defId, fileId, actorConstraints);
                     if (stages.isEmpty()) {
                        AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: missing 'stages' array", fileId);
                     } else {
                        AnimationDefinitions.Definition definition = new AnimationDefinitions.Definition(
                           defId,
                           List.copyOf(actorConstraints),
                           Set.copyOf(requiredUnionTags),
                           Set.copyOf(animationTags),
                           contentTags,
                           stages,
                           weight,
                           blockRequirements,
                           waterRequirement,
                           positionAnchorActor,
                           packInfo,
                           displayName,
                           joinAfter
                        );
                        definition = definition.withGameplayMetadata(
                           AnimationGameplayMetadata.parse(fileId, definition, obj, List.of(buildUniqueActorKeys(actorConstraints)))
                        );
                        loaded.add(definition);
                     }
                  }
               }
            }
         } catch (IOException | RuntimeException e) {
            AnimationEngine.logSetupError("[NoN Animation] Failed to load {}", fileId, e);
         }
      }

      loaded.sort(Comparator.comparing(d -> d.id().toString()));
      validateJoinAfterReferences(loaded);
      DEFINITIONS = List.copyOf(loaded);
      Map<Identifier, AnimationDefinitions.Definition> definitionsById = new LinkedHashMap<>();
      DEFINITIONS.forEach(definitionx -> definitionsById.put(definitionx.id(), definitionx));
      DEFINITIONS_BY_ID = Map.copyOf(definitionsById);
      rebuildMatchingIndexes();
      JOIN_FOLLOWERS_BY_PREDECESSOR = buildJoinFollowerIndex(DEFINITIONS);
      rebuildEligibilityIndexes();
      AnimationEngine.LOGGER.info("[NoN Animation] Loaded {} animation definition(s) from datapacks", DEFINITIONS.size());
   }

   private static void rebuildMatchingIndexes() {
      Map<Integer, List<AnimationDefinitions.Definition>> byActorCount = new LinkedHashMap<>();
      Map<Identifier, List<Integer>> matchOrders = new LinkedHashMap<>();

      for (AnimationDefinitions.Definition definition : DEFINITIONS) {
         byActorCount.computeIfAbsent(definition.actors().size(), ignored -> new ArrayList<>()).add(definition);
         matchOrders.put(definition.id(), buildConstraintOrder(definition.actors()));
      }

      Comparator<AnimationDefinitions.Definition> order = Comparator.comparing(definition -> definition.id().toString());
      Map<Integer, List<AnimationDefinitions.Definition>> finalized = new LinkedHashMap<>();
      byActorCount.forEach((actorCount, definitions) -> {
         definitions.sort(order);
         finalized.put(actorCount, List.copyOf((Collection<? extends AnimationDefinitions.Definition>)definitions));
      });
      DEFINITIONS_BY_ACTOR_COUNT = Map.copyOf(finalized);
      MATCH_ORDER_BY_DEFINITION = Map.copyOf(matchOrders);
   }

   private static void rebuildEligibilityIndexes() {
      Set<AnimationDefinitions.ActorKey> actorTypes = new LinkedHashSet<>();
      Set<AnimationDefinitions.ActorKey> pairTypes = new LinkedHashSet<>();
      Map<AnimationDefinitions.ActorKey, Set<AnimationDefinitions.ActorKey>> compatibility = new LinkedHashMap<>();

      for (AnimationDefinitions.Definition definition : DEFINITIONS) {
         if (isDefinitionEnabled(definition) && isGenericMatchDefinition(definition)) {
            List<AnimationDefinitions.ActorConstraint> actors = definition.actors();
            if (actors != null && actors.size() >= 2) {
               List<Set<AnimationDefinitions.ActorKey>> keysByActor = actors.stream().map(actor -> {
                  boolean baby = actor.ageRequirement() == AnimationDefinitions.AgeRequirement.BABY;
                  Set<AnimationDefinitions.ActorKey> keys = new LinkedHashSet<>();

                  for (Identifier entityType : actor.entityTypes()) {
                     AnimationDefinitions.ActorKey key = new AnimationDefinitions.ActorKey(entityType, baby);
                     keys.add(key);
                     actorTypes.add(key);
                  }

                  return Set.copyOf(keys);
               }).toList();
               if (keysByActor.size() == 2 && !keysByActor.get(0).isEmpty() && !keysByActor.get(1).isEmpty()) {
                  for (AnimationDefinitions.ActorKey left : keysByActor.get(0)) {
                     for (AnimationDefinitions.ActorKey right : keysByActor.get(1)) {
                        pairTypes.add(left);
                        pairTypes.add(right);
                        compatibility.computeIfAbsent(left, ignored -> new LinkedHashSet<>()).add(right);
                        compatibility.computeIfAbsent(right, ignored -> new LinkedHashSet<>()).add(left);
                     }
                  }
               }
            }
         }
      }

      Map<AnimationDefinitions.ActorKey, Set<AnimationDefinitions.ActorKey>> immutableCompatibility = new LinkedHashMap<>();
      compatibility.forEach((key, value) -> immutableCompatibility.put(key, Set.copyOf((Collection<? extends AnimationDefinitions.ActorKey>)value)));
      GENERIC_ACTOR_TYPES = Set.copyOf(actorTypes);
      GENERIC_MOB_PAIR_TYPES = Set.copyOf(pairTypes);
      GENERIC_MOB_PAIR_COMPATIBILITY = Map.copyOf(immutableCompatibility);
   }

   private static Map<String, AnimationDefinitions.AnimationPackInfo> readAnimationPackInfos(ResourceManager resourceManager) {
      LinkedHashMap<String, AnimationDefinitions.AnimationPackInfo> out = new LinkedHashMap<>();
      if (resourceManager == null) {
         return out;
      }

      try (Stream<ResourcePack> stream = resourceManager.streamResourcePacks()) {
         stream.forEach(pack -> {
            if (pack != null) {
               String resourcePackId = pack.getId();
               if (resourcePackId != null && !resourcePackId.isBlank()) {
                  AnimationDefinitions.AnimationPackInfo info = readAnimationPackInfo(pack, resourcePackId);
                  out.put(resourcePackId, info == null ? fallbackPackInfo(resourcePackId) : info);
               }
            }
         });
      } catch (RuntimeException e) {
         AnimationEngine.LOGGER.debug("[NoN Animation] Failed to read animation pack metadata from pack.mcmeta.", e);
      }

      validateModelProviders(out.values());
      return out;
   }

   private static void validateModelProviders(Collection<AnimationDefinitions.AnimationPackInfo> packInfos) {
      if (packInfos != null && !packInfos.isEmpty()) {
         Map<Identifier, String> ownerByRoot = new LinkedHashMap<>();
         Set<String> availablePackIds = new LinkedHashSet<>();

         for (AnimationDefinitions.AnimationPackInfo info : packInfos) {
            if (info != null && info.localModelRoot() != null) {
               availablePackIds.add(info.id());
               String previousOwner = ownerByRoot.putIfAbsent(info.localModelRoot(), info.resourcePackId());
               if (previousOwner != null && !previousOwner.equals(info.resourcePackId())) {
                  AnimationEngine.logSetupWarning(
                     "[NoN Animation] Local model root {} is shared by resource packs '{}' and '{}'. Give each pack a unique animationframework.id.",
                     info.localModelRoot(),
                     previousOwner,
                     info.resourcePackId()
                  );
               }
            }
         }

         for (AnimationDefinitions.AnimationPackInfo info : packInfos) {
            if (info != null) {
               for (AnimationDefinitions.ModelImport modelImport : info.modelImports()) {
                  if (!availablePackIds.contains(modelImport.packId())) {
                     AnimationEngine.logSetupWarning(
                        "[NoN Animation] Pack '{}' imports models from missing pack '{}'. Missing models will fall back to shared resources.",
                        info.id(),
                        modelImport.packId()
                     );
                  }
               }
            }
         }
      }
   }

   @Nullable
   private static AnimationDefinitions.AnimationPackInfo readAnimationPackInfo(ResourcePack pack, String resourcePackId) {
      try (InputStream stream = openRootResource(pack, "pack.mcmeta")) {
         if (stream == null) {
            return null;
         }

         InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);

         JsonObject root;
         try {
            root = JsonParser.parseReader(reader).getAsJsonObject();
         } catch (Throwable var17) {
            try {
               reader.close();
            } catch (Throwable var16) {
               var17.addSuppressed(var16);
            }

            throw var17;
         }

         reader.close();
         JsonObject var21 = null;
         if (root.has("animationframework") && root.get("animationframework").isJsonObject()) {
            var21 = root.getAsJsonObject("animationframework");
         }

         String packDescription = readPackDescription(root);
         if (var21 == null) {
            AnimationDefinitions.AnimationPackInfo fallback = fallbackPackInfo(resourcePackId);
            return new AnimationDefinitions.AnimationPackInfo(
               fallback.id(), resourcePackId, fallback.name(), fallback.author(), fallback.version(), packDescription, null
            );
         }

         String rawId = readOptionalString(var21, "id");
         String id = sanitizePackMetadataId(rawId, resourcePackId);
         String name = readOptionalString(var21, "name");
         String author = readOptionalString(var21, "author");
         String version = readOptionalString(var21, "version");
         String description = readOptionalString(var21, "description");
         if (description == null) {
            description = packDescription;
         }

         Identifier localModelRoot = localModelRoot(rawId);
         warnRemovedModelScope(var21, resourcePackId);
         List<AnimationDefinitions.ModelImport> modelImports = readModelImports(var21, resourcePackId, rawId, true);
         return new AnimationDefinitions.AnimationPackInfo(id, resourcePackId, name, author, version, description, localModelRoot, modelImports);
      } catch (IOException | RuntimeException e) {
         AnimationEngine.LOGGER.debug("[NoN Animation] Failed to parse animation pack metadata for {}", resourcePackId, e);
         return null;
      }
   }

   @Nullable
   private static InputStream openRootResource(ResourcePack pack, String fileName) {
      if (pack != null && fileName != null) {
         InputSupplier<InputStream> supplier = pack.openRoot(new String[]{fileName});
         if (supplier == null) {
            return null;
         }

         try {
            return (InputStream)supplier.get();
         } catch (IOException | RuntimeException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static AnimationDefinitions.AnimationPackInfo fallbackPackInfo(String resourcePackId) {
      String safePackId = sanitizeNonBlank(resourcePackId, "unknown");
      return new AnimationDefinitions.AnimationPackInfo(safePackId, safePackId, displayNameFromPackId(safePackId), "Unknown", null, null, null);
   }

   @Nullable
   private static Identifier localModelRoot(@Nullable String rawPackId) {
      String cleaned = sanitizeNullable(rawPackId);
      Identifier packId = cleaned != null && cleaned.indexOf(58) > 0 ? Identifier.tryParse(cleaned) : null;
      return packId == null ? null : Identifier.of(packId.getNamespace(), packId.getPath() + "/entity");
   }

   private static void warnRemovedModelScope(JsonObject animationMetadata, String resourcePackId) {
      if (animationMetadata != null && animationMetadata.has("model_scope")) {
         AnimationEngine.logSetupWarning(
            "[NoN Animation] animationframework.model_scope in pack.mcmeta for {} is no longer needed and has been ignored. All packs now use local models automatically.",
            resourcePackId
         );
      }
   }

   static List<AnimationDefinitions.ModelImport> readModelImports(
      JsonObject animationMetadata, String resourcePackId, @Nullable String ownPackId, boolean logWarnings
   ) {
      if (animationMetadata != null && animationMetadata.has("model_imports")) {
         JsonElement importsElement = animationMetadata.get("model_imports");
         if (importsElement != null && importsElement.isJsonArray()) {
            List<AnimationDefinitions.ModelImport> imports = new ArrayList<>();
            Set<String> seenMappings = new LinkedHashSet<>();
            Identifier ownId = ownPackId != null && ownPackId.indexOf(58) > 0 ? Identifier.tryParse(ownPackId) : null;

            for (JsonElement importElement : importsElement.getAsJsonArray()) {
               if (importElement != null && importElement.isJsonObject()) {
                  JsonObject importObject = importElement.getAsJsonObject();
                  String importedPackId = readOptionalString(importObject, "pack");
                  Identifier importedId = importedPackId != null && importedPackId.indexOf(58) > 0 ? Identifier.tryParse(importedPackId) : null;
                  if (importedId == null) {
                     logPackMetadataWarning(
                        logWarnings,
                        "[NoN Animation] Ignoring invalid model import pack id '{}' in pack.mcmeta for {}. Use a namespaced id like creator:model_pack.",
                        importedPackId,
                        resourcePackId
                     );
                  } else if (importedId.equals(ownId)) {
                     logPackMetadataWarning(
                        logWarnings, "[NoN Animation] Ignoring self model import '{}' in pack.mcmeta for {}.", importedPackId, resourcePackId
                     );
                  } else {
                     JsonElement entitiesElement = importObject.get("entities");
                     if (entitiesElement != null && entitiesElement.isJsonArray()) {
                        LinkedHashSet<Identifier> entityTypes = new LinkedHashSet<>();

                        for (JsonElement entityElement : entitiesElement.getAsJsonArray()) {
                           String rawEntity = entityElement != null && entityElement.isJsonPrimitive() && entityElement.getAsJsonPrimitive().isString()
                              ? entityElement.getAsString().trim()
                              : null;
                           Identifier entityId = rawEntity != null && rawEntity.indexOf(58) > 0 ? Identifier.tryParse(rawEntity) : null;
                           if (entityId == null) {
                              logPackMetadataWarning(
                                 logWarnings,
                                 "[NoN Animation] Ignoring invalid entity '{}' in model import '{}' for {}.",
                                 rawEntity,
                                 importedPackId,
                                 resourcePackId
                              );
                           } else {
                              String mappingKey = importedId + "|" + entityId;
                              if (seenMappings.add(mappingKey)) {
                                 entityTypes.add(entityId);
                              }
                           }
                        }

                        if (entityTypes.isEmpty()) {
                           logPackMetadataWarning(
                              logWarnings,
                              "[NoN Animation] Ignoring model import '{}' in pack.mcmeta for {} because it contains no valid entities.",
                              importedPackId,
                              resourcePackId
                           );
                        } else {
                           imports.add(
                              new AnimationDefinitions.ModelImport(
                                 importedId.toString(), Identifier.of(importedId.getNamespace(), importedId.getPath() + "/entity"), entityTypes
                              )
                           );
                        }
                     } else {
                        logPackMetadataWarning(
                           logWarnings,
                           "[NoN Animation] Ignoring model import '{}' in pack.mcmeta for {}: 'entities' must be a non-empty array.",
                           importedPackId,
                           resourcePackId
                        );
                     }
                  }
               } else {
                  logPackMetadataWarning(
                     logWarnings,
                     "[NoN Animation] Ignoring invalid model_imports entry in pack.mcmeta for {}: expected an object with 'pack' and 'entities'.",
                     resourcePackId
                  );
               }
            }

            return imports.isEmpty() ? List.of() : List.copyOf(imports);
         } else {
            logPackMetadataWarning(
               logWarnings, "[NoN Animation] Ignoring invalid animationframework.model_imports in pack.mcmeta for {}: expected an array.", resourcePackId
            );
            return List.of();
         }
      } else {
         return List.of();
      }
   }

   private static void logPackMetadataWarning(boolean enabled, String template, Object... args) {
      if (enabled) {
         AnimationEngine.logSetupWarning(template, args);
      }
   }

   private static String sanitizePackMetadataId(@Nullable String rawId, String resourcePackId) {
      String fallback = sanitizeNonBlank(resourcePackId, "unknown");
      String cleaned = sanitizeNullable(rawId);
      if (cleaned == null) {
         return fallback;
      } else if (Identifier.tryParse(cleaned) == null) {
         AnimationEngine.logSetupWarning(
            "[NoN Animation] Ignoring invalid animationframework.id '{}' in pack.mcmeta for {}. Use a namespaced id like creator:pack_name.",
            cleaned,
            resourcePackId
         );
         return fallback;
      } else {
         return cleaned;
      }
   }

   @Nullable
   private static String readPackDescription(JsonObject root) {
      if (root != null && root.has("pack") && root.get("pack").isJsonObject()) {
         JsonObject pack = root.getAsJsonObject("pack");
         return readOptionalString(pack, "description");
      } else {
         return null;
      }
   }

   @Nullable
   private static String readOptionalString(JsonObject object, String key) {
      if (object != null && key != null && object.has(key)) {
         JsonElement element = object.get(key);
         if (element != null && element.isJsonPrimitive()) {
            try {
               return sanitizeNullable(element.getAsString());
            } catch (RuntimeException e) {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static String readOptionalDisplayName(JsonObject object, Identifier fileId) {
      if (object != null && object.has("display_name")) {
         JsonElement element = object.get("display_name");
         if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString().trim();
            if (value.isEmpty()) {
               AnimationEngine.logSetupWarning("[NoN Animation] Ignoring blank display_name in {}. The animation id will be shown instead.", fileId);
               return null;
            } else {
               return value;
            }
         } else {
            AnimationEngine.logSetupWarning("[NoN Animation] Ignoring invalid display_name in {}: expected a non-empty string.", fileId);
            return null;
         }
      } else {
         return null;
      }
   }

   private static String displayNameFromPackId(String rawPackId) {
      String id = sanitizeNonBlank(rawPackId, "Unknown Pack");
      int slash = id.lastIndexOf(47);
      if (slash >= 0 && slash < id.length() - 1) {
         id = id.substring(slash + 1);
      }

      int colon = id.indexOf(58);
      if (colon >= 0 && colon < id.length() - 1) {
         id = id.substring(colon + 1);
      }

      int underscore = id.indexOf(95);
      if (underscore > 0 && isNumericPrefix(id, underscore)) {
         id = id.substring(underscore + 1);
      }

      id = id.replace('_', ' ').replace('-', ' ').trim();
      return id.isBlank() ? "Unknown Pack" : id;
   }

   private static boolean isNumericPrefix(String value, int endExclusive) {
      if (value != null && endExclusive > 0 && endExclusive <= value.length()) {
         for (int i = 0; i < endExclusive; i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static String sanitizeNonBlank(@Nullable String value, String fallback) {
      String cleaned = sanitizeNullable(value);
      return cleaned == null ? fallback : cleaned;
   }

   @Nullable
   private static String sanitizeNullable(@Nullable String value) {
      if (value == null) {
         return null;
      }

      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
   }

   private static void warnUnknownKeys(JsonObject obj, Identifier fileId, String context, Set<String> knownKeys) {
      if (obj != null && knownKeys != null && !knownKeys.isEmpty()) {
         String safeContext = context != null && !context.isBlank() ? context : "object";

         for (Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if (key != null && !knownKeys.contains(key)) {
               AnimationEngine.logSetupWarning(
                  "[NoN Animation] Unknown field '{}' in {} {}. It will be ignored; check for typos.", key, fileId == null ? "<unknown>" : fileId, safeContext
               );
            }
         }
      }
   }

   private static Set<String> readStringArray(JsonObject obj, String key) {
      if (!obj.has(key)) {
         return new LinkedHashSet<>();
      }

      JsonArray arr = obj.getAsJsonArray(key);
      Set<String> out = new LinkedHashSet<>();

      for (int i = 0; i < arr.size(); i++) {
         out.add(arr.get(i).getAsString());
      }

      return out;
   }

   private static double readWeightOrDefault(JsonObject obj, Identifier fileId) {
      if (obj != null && obj.has("weight")) {
         JsonElement element = obj.get("weight");
         if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            double value;
            try {
               value = element.getAsDouble();
            } catch (RuntimeException ignored) {
               AnimationEngine.logSetupWarning("[NoN Animation] Invalid weight in {} (must be a positive number). Using default 1.0.", fileId);
               return 1.0;
            }

            if (Double.isFinite(value) && !(value <= 0.0)) {
               return value;
            }

            AnimationEngine.logSetupWarning("[NoN Animation] Invalid weight '{}' in {} (must be > 0). Using default 1.0.", value, fileId);
            return 1.0;
         } else {
            AnimationEngine.logSetupWarning("[NoN Animation] Invalid weight in {} (must be a positive number). Using default 1.0.", fileId);
            return 1.0;
         }
      } else {
         return 1.0;
      }
   }

   @Nullable
   private static String readOptionalAnchorActorKey(JsonObject obj, String key) {
      if (obj == null || key == null || key.isBlank()) {
         return null;
      }

      if (!obj.has(key)) {
         return null;
      }

      JsonElement element = obj.get(key);
      if (element != null && element.isJsonPrimitive()) {
         String raw = element.getAsString();
         if (raw == null) {
            return null;
         }

         String trimmed = raw.trim();
         return trimmed.isEmpty() ? null : trimmed;
      } else {
         return null;
      }
   }

   private static Set<String> normalizeTags(Set<String> tags) {
      if (tags != null && !tags.isEmpty()) {
         Set<String> out = new LinkedHashSet<>();

         for (String raw : tags) {
            if (raw != null) {
               String value = raw.trim().toLowerCase(Locale.ROOT);
               if (!value.isEmpty()) {
                  out.add(value);
               }
            }
         }

         return out.isEmpty() ? Set.of() : Set.copyOf(out);
      } else {
         return Set.of();
      }
   }

   private static AnimationDefinitions.AgeRequirement readAgeRequirement(JsonObject obj, Identifier fileId) {
      if (!obj.has("age")) {
         return AnimationDefinitions.AgeRequirement.ADULT;
      }

      String raw = obj.get("age").getAsString();
      if (raw != null && !raw.isBlank()) {
         String value = raw.toLowerCase(Locale.ROOT).trim();

         return switch (value) {
            case "adult" -> AnimationDefinitions.AgeRequirement.ADULT;
            case "baby" -> AnimationDefinitions.AgeRequirement.BABY;
            default -> {
               AnimationEngine.logSetupWarning("[NoN Animation] Unknown age '{}' in {} (expected adult|baby). Skipping definition.", raw, fileId);
               yield null;
            }
         };
      } else {
         return AnimationDefinitions.AgeRequirement.ADULT;
      }
   }

   private static AnimationDefinitions.ActorActivity readActorActivity(JsonObject obj, Identifier fileId) {
      if (!obj.has("activity")) {
         return AnimationDefinitions.ActorActivity.ACTIVE;
      }

      String raw = obj.get("activity").getAsString();
      if (raw != null && !raw.isBlank()) {
         String value = raw.toLowerCase(Locale.ROOT).trim();

         return switch (value) {
            case "active" -> AnimationDefinitions.ActorActivity.ACTIVE;
            case "passive" -> AnimationDefinitions.ActorActivity.PASSIVE;
            default -> {
               AnimationEngine.logSetupWarning("[NoN Animation] Unknown actor activity '{}' in {} (expected active|passive). Using active.", raw, fileId);
               yield AnimationDefinitions.ActorActivity.ACTIVE;
            }
         };
      } else {
         return AnimationDefinitions.ActorActivity.ACTIVE;
      }
   }

   static AnimationDefinitions.BlockRequirements readBlockRequirements(JsonObject obj, Identifier fileId) {
      if (!obj.has("block_requirements")) {
         return null;
      }

      JsonObject req = obj.getAsJsonObject("block_requirements");
      if (req == null) {
         return null;
      }

      warnUnknownKeys(req, fileId, "block_requirements", KNOWN_BLOCK_REQUIREMENT_KEYS);
      String type = req.has("type") ? req.get("type").getAsString() : "wall";
      String normalizedType = type == null ? "wall" : type.trim().toLowerCase(Locale.ROOT);
      AnimationDefinitions.BlockPredicate blocks = readBlockPredicate(req, fileId);
      if (req.has("blocks") && blocks == null) {
         return null;
      }

      if ("wall".equals(normalizedType)) {
         AnimationDefinitions.WallHeight height = readWallHeight(req, fileId);
         if (height == null) {
            return null;
         }

         AnimationDefinitions.WallWidth width = readWallWidth(req, fileId);
         if (width == null) {
            return null;
         }

         AnimationDefinitions.WallFacing facing = readWallFacing(req, fileId, blocks);
         if (req.has("facing") && facing == null) {
            return null;
         }

         AnimationDefinitions.Clearance clearance = readClearance(req, fileId);
         return new AnimationDefinitions.BlockRequirements("wall", height, width, facing, clearance, null, null, 0, null, blocks);
      } else if ("center_support".equals(normalizedType)) {
         if (req.has("facing")) {
            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: block_requirements facing is only supported for walls.", fileId);
            return null;
         }

         if (req.has("width")) {
            AnimationEngine.logSetupWarning("[NoN Animation] {}: center_support ignores wall 'width'.", fileId);
         }

         AnimationDefinitions.CenterSupport support = readCenterSupport(req, fileId);
         if (support == null) {
            return null;
         }

         if (support != AnimationDefinitions.CenterSupport.SURFACE || blocks != null && !blocks.isEmpty()) {
            AnimationDefinitions.CenterPlacement placement = readCenterPlacement(req, fileId);
            if (placement == null) {
               return null;
            }

            AnimationDefinitions.Clearance clearance = null;
            int surfaceRadius = 0;
            AnimationDefinitions.SurfaceFootprint surfaceFootprint = null;
            if (placement == AnimationDefinitions.CenterPlacement.SURFACE_FOOTPRINT) {
               surfaceFootprint = readSurfaceFootprint(req, fileId);
               if (surfaceFootprint == null) {
                  return null;
               }

               if (req.has("clearance")) {
                  AnimationEngine.logSetupWarning("[NoN Animation] {}: center_support surface_footprint ignores 'clearance'.", fileId);
               }

               if (req.has("surface_radius")) {
                  AnimationEngine.logSetupWarning("[NoN Animation] {}: center_support surface_footprint ignores 'surface_radius'.", fileId);
               }
            } else {
               clearance = readClearance(req, fileId);
               surfaceRadius = readNonNegativeInt(req, "surface_radius", 0);
               if (req.has("surface_footprint")) {
                  AnimationEngine.logSetupWarning("[NoN Animation] {}: center_support directional_clearance ignores 'surface_footprint'.", fileId);
               }
            }

            return new AnimationDefinitions.BlockRequirements(
               "center_support", null, null, null, clearance, support, placement, surfaceRadius, surfaceFootprint, blocks
            );
         } else {
            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: center_support surface requires 'blocks'.", fileId);
            return null;
         }
      } else {
         AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: unsupported block_requirements type '{}'", fileId, type);
         return null;
      }
   }

   @Nullable
   private static AnimationDefinitions.WallFacing readWallFacing(JsonObject req, Identifier fileId, @Nullable AnimationDefinitions.BlockPredicate blocks) {
      if (!req.has("facing")) {
         return null;
      }

      if (blocks != null && !blocks.isEmpty()) {
         JsonElement element = req.get("facing");
         if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString();
            String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);

            for (AnimationDefinitions.WallFacing facing : AnimationDefinitions.WallFacing.values()) {
               if (facing.id().equals(value)) {
                  return facing;
               }
            }

            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: unsupported wall facing '{}' (expected front|back|left|right).", fileId, raw);
            return null;
         } else {
            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: wall block_requirements facing must be a string.", fileId);
            return null;
         }
      } else {
         AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: wall block_requirements facing requires 'blocks'.", fileId);
         return null;
      }
   }

   private static AnimationDefinitions.CenterPlacement readCenterPlacement(JsonObject req, Identifier fileId) {
      if (!req.has("placement")) {
         return AnimationDefinitions.CenterPlacement.DIRECTIONAL_CLEARANCE;
      }

      String raw = req.get("placement").getAsString();
      if (raw != null && !raw.isBlank()) {
         String value = raw.trim().toLowerCase(Locale.ROOT);

         for (AnimationDefinitions.CenterPlacement placement : AnimationDefinitions.CenterPlacement.values()) {
            if (placement.id().equals(value)) {
               return placement;
            }
         }

         AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: unsupported center_support placement '{}'", fileId, raw);
         return null;
      } else {
         AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: center_support block_requirements has empty placement", fileId);
         return null;
      }
   }

   private static AnimationDefinitions.CenterSupport readCenterSupport(JsonObject req, Identifier fileId) {
      if (!req.has("support")) {
         AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: center_support block_requirements missing support", fileId);
         return null;
      }

      String raw = req.get("support").getAsString();
      if (raw != null && !raw.isBlank()) {
         String value = raw.trim().toLowerCase(Locale.ROOT);

         for (AnimationDefinitions.CenterSupport support : AnimationDefinitions.CenterSupport.values()) {
            if (support.id().equals(value)) {
               return support;
            }
         }

         AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: unsupported center_support support '{}'", fileId, raw);
         return null;
      } else {
         AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: center_support block_requirements has empty support", fileId);
         return null;
      }
   }

   @Nullable
   private static AnimationDefinitions.SurfaceFootprint readSurfaceFootprint(JsonObject req, Identifier fileId) {
      if (!req.has("surface_footprint")) {
         AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: center_support surface_footprint placement requires 'surface_footprint'.", fileId);
         return null;
      } else {
         JsonElement element = req.get("surface_footprint");
         if (element != null && element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            warnUnknownKeys(obj, fileId, "block_requirements.surface_footprint", KNOWN_SURFACE_FOOTPRINT_KEYS);
            int width = readPositiveInt(obj, "width", 1);
            int depth = readPositiveInt(obj, "depth", 1);
            int height = readPositiveInt(obj, "height", 2);
            int margin = readNonNegativeInt(obj, "margin", 0);
            return new AnimationDefinitions.SurfaceFootprint(width, depth, height, margin);
         } else {
            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: center_support surface_footprint must be an object.", fileId);
            return null;
         }
      }
   }

   @Nullable
   private static AnimationDefinitions.BlockPredicate readBlockPredicate(JsonObject req, Identifier fileId) {
      if (!req.has("blocks")) {
         return null;
      }

      JsonElement blocksElement = req.get("blocks");
      if (blocksElement != null && !blocksElement.isJsonNull()) {
         Set<Identifier> blockIds = new LinkedHashSet<>();
         Set<Identifier> tagIds = new LinkedHashSet<>();
         if (blocksElement.isJsonArray()) {
            JsonArray arr = blocksElement.getAsJsonArray();
            if (arr.isEmpty()) {
               AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: block_requirements blocks array is empty.", fileId);
               return null;
            }

            for (JsonElement element : arr) {
               if (!readBlockPredicateEntry(element, fileId, blockIds, tagIds)) {
                  return null;
               }
            }
         } else if (!readBlockPredicateEntry(blocksElement, fileId, blockIds, tagIds)) {
            return null;
         }

         if (blockIds.isEmpty() && tagIds.isEmpty()) {
            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: block_requirements blocks did not contain any valid entries.", fileId);
            return null;
         } else {
            return new AnimationDefinitions.BlockPredicate(Set.copyOf(blockIds), Set.copyOf(tagIds));
         }
      } else {
         AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: block_requirements blocks must not be null.", fileId);
         return null;
      }
   }

   private static boolean readBlockPredicateEntry(JsonElement element, Identifier fileId, Set<Identifier> blockIds, Set<Identifier> tagIds) {
      if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
         String raw = element.getAsString();
         if (raw != null && !raw.isBlank()) {
            String trimmed = raw.trim();
            boolean tag = trimmed.startsWith("#");
            String idText = tag ? trimmed.substring(1) : trimmed;
            if (!idText.contains(":")) {
               AnimationEngine.logSetupWarning(
                  "[NoN Animation] Skipping {}: block_requirements blocks entry '{}' must use a namespaced id like 'minecraft:chest' or '#minecraft:beds'.",
                  fileId,
                  raw
               );
               return false;
            }

            Identifier id = Identifier.tryParse(idText);
            if (id == null) {
               AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: invalid block_requirements blocks identifier '{}'.", fileId, raw);
               return false;
            }

            if (tag) {
               tagIds.add(id);
            } else {
               if (!Registries.BLOCK.containsId(id)) {
                  AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: unknown block id '{}' in block_requirements blocks.", fileId, raw);
                  return false;
               }

               blockIds.add(id);
            }

            return true;
         } else {
            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: block_requirements blocks contains an empty entry.", fileId);
            return false;
         }
      } else {
         AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: block_requirements blocks entries must be strings.", fileId);
         return false;
      }
   }

   private static AnimationDefinitions.WaterRequirement readWaterRequirement(JsonObject obj, Identifier fileId) {
      if (!obj.has("water")) {
         return AnimationDefinitions.WaterRequirement.NONE;
      }

      JsonElement el = obj.get("water");
      if (el != null && el.isJsonPrimitive()) {
         String raw = el.getAsString();
         if (raw != null && !raw.isBlank()) {
            String value = raw.toLowerCase(Locale.ROOT).trim();

            return switch (value) {
               case "surface" -> AnimationDefinitions.WaterRequirement.SURFACE;
               case "underwater" -> AnimationDefinitions.WaterRequirement.UNDERWATER;
               case "none" -> AnimationDefinitions.WaterRequirement.NONE;
               default -> {
                  AnimationEngine.logSetupWarning("[NoN Animation] Unknown water requirement '{}' in {}; expected none|surface|underwater.", raw, fileId);
                  yield AnimationDefinitions.WaterRequirement.NONE;
               }
            };
         } else {
            return AnimationDefinitions.WaterRequirement.NONE;
         }
      } else {
         return AnimationDefinitions.WaterRequirement.NONE;
      }
   }

   private static AnimationDefinitions.WallHeight readWallHeight(JsonObject req, Identifier fileId) {
      int min = 2;
      Integer max = null;
      boolean minSet = false;
      if (req.has("height")) {
         JsonElement heightEl = req.get("height");
         if (heightEl.isJsonObject()) {
            JsonObject heightObj = heightEl.getAsJsonObject();
            warnUnknownKeys(heightObj, fileId, "block_requirements.height", KNOWN_WALL_HEIGHT_KEYS);
            Integer minVal = readOptionalInt(heightObj, "min");
            Integer maxVal = readOptionalInt(heightObj, "max");
            if (minVal != null) {
               min = minVal;
               minSet = true;
            }

            if (maxVal != null) {
               max = maxVal;
               if (!minSet) {
                  min = 1;
               }
            }
         } else {
            Integer exact = readOptionalInt(heightEl);
            if (exact != null) {
               min = exact;
               max = exact;
            }
         }
      }

      if (min < 1) {
         AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: invalid block_requirements height min '{}'", fileId, min);
         return null;
      }

      if (max != null) {
         if (max < 1) {
            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: invalid block_requirements height max '{}'", fileId, max);
            return null;
         }

         if (max < min) {
            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: block_requirements height max '{}' < min '{}'", fileId, max, min);
            return null;
         }
      }

      return new AnimationDefinitions.WallHeight(min, max);
   }

   static AnimationDefinitions.WallWidth readWallWidth(JsonObject req, Identifier fileId) {
      if (!req.has("width")) {
         return new AnimationDefinitions.WallWidth(1, null);
      }

      int min = 1;
      Integer max = null;
      JsonElement widthElement = req.get("width");
      if (widthElement.isJsonObject()) {
         JsonObject widthObject = widthElement.getAsJsonObject();
         warnUnknownKeys(widthObject, fileId, "block_requirements.width", KNOWN_WALL_WIDTH_KEYS);
         if (!widthObject.has("min") && !widthObject.has("max")) {
            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: block_requirements width must define 'min' or 'max'.", fileId);
            return null;
         }

         Integer minValue = readOptionalInt(widthObject, "min");
         Integer maxValue = readOptionalInt(widthObject, "max");
         if (widthObject.has("min") && minValue == null) {
            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: invalid block_requirements width min.", fileId);
            return null;
         }

         if (widthObject.has("max") && maxValue == null) {
            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: invalid block_requirements width max.", fileId);
            return null;
         }

         if (minValue != null) {
            min = minValue;
         }

         max = maxValue;
      } else {
         Integer minimum = readOptionalInt(widthElement);
         if (minimum == null) {
            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: invalid block_requirements width.", fileId);
            return null;
         }

         min = minimum;
      }

      if (min < 1) {
         AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: invalid block_requirements width min '{}'.", fileId, min);
         return null;
      } else if (max != null && max < min) {
         AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: block_requirements width max '{}' < min '{}'.", fileId, max, min);
         return null;
      } else {
         return new AnimationDefinitions.WallWidth(min, max);
      }
   }

   static AnimationDefinitions.Clearance readClearance(JsonObject req, Identifier fileId) {
      int width = 1;
      int height = 2;
      int depth = 1;
      if (req.has("clearance")) {
         JsonObject clearance = req.getAsJsonObject("clearance");
         if (clearance != null) {
            warnUnknownKeys(clearance, fileId, "block_requirements.clearance", KNOWN_CLEARANCE_KEYS);
            width = readPositiveInt(clearance, "width", width);
            height = readPositiveInt(clearance, "height", height);
            depth = readPositiveInt(clearance, "depth", depth);
         }
      }

      return new AnimationDefinitions.Clearance(width, height, depth);
   }

   private static int readPositiveInt(JsonObject obj, String key, int fallback) {
      if (!obj.has(key)) {
         return fallback;
      } else {
         Integer value = readOptionalInt(obj, key);
         if (value == null) {
            AnimationEngine.logSetupWarning("[NoN Animation] Invalid integer value for {}", key);
            return fallback;
         } else {
            return Math.max(1, value);
         }
      }
   }

   private static int readNonNegativeInt(JsonObject obj, String key, int fallback) {
      if (!obj.has(key)) {
         return fallback;
      } else {
         Integer value = readOptionalInt(obj, key);
         if (value == null) {
            AnimationEngine.logSetupWarning("[NoN Animation] Invalid integer value for {}", key);
            return fallback;
         } else {
            return Math.max(0, value);
         }
      }
   }

   private static Integer readOptionalInt(JsonObject obj, String key) {
      return !obj.has(key) ? null : readOptionalInt(obj.get(key));
   }

   private static Set<Identifier> readEntityTypes(JsonObject obj, Identifier fileId) {
      String key = "entity_types";
      if (!obj.has(key)) {
         return new LinkedHashSet<>();
      }

      JsonArray arr = obj.getAsJsonArray(key);
      Set<Identifier> out = new LinkedHashSet<>();

      for (int i = 0; i < arr.size(); i++) {
         String s = arr.get(i).getAsString();
         Identifier id = Identifier.tryParse(s);
         if (id == null) {
            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: invalid identifier in {}: '{}'", fileId, key, s);
            return null;
         }

         if (PLAYER_SLIM_ID.equals(id)) {
            AnimationEngine.logSetupWarning(
               "[NoN Animation] Skipping {}: '{}' is no longer supported in {}. Use '{}' only.", fileId, PLAYER_SLIM_ID, key, PLAYER_ID
            );
            return null;
         }

         out.add(id);
      }

      return out;
   }

   private static Set<Identifier> readJoinAfter(JsonObject obj, Identifier fileId, Identifier definitionId) {
      if (!obj.has("join_after")) {
         return Set.of();
      }

      JsonElement element = obj.get("join_after");
      if (element != null && element.isJsonArray()) {
         LinkedHashSet<Identifier> out = new LinkedHashSet<>();
         JsonArray values = element.getAsJsonArray();

         for (int i = 0; i < values.size(); i++) {
            JsonElement value = values.get(i);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
               String raw = value.getAsString().trim();
               Identifier id = raw.indexOf(58) > 0 ? Identifier.tryParse(raw) : null;
               if (id == null) {
                  AnimationEngine.logSetupWarning(
                     "[NoN Animation] Invalid join_after entry '{}' in {}: use an exact namespaced id such as creator:wolfmplayer.", raw, fileId
                  );
               } else if (id.equals(definitionId)) {
                  AnimationEngine.logSetupWarning("[NoN Animation] Ignoring self-referencing join_after entry '{}' in {}.", id, fileId);
               } else {
                  out.add(id);
               }
            } else {
               AnimationEngine.logSetupWarning("[NoN Animation] Invalid join_after entry {} in {}: expected an exact namespaced animation id.", i, fileId);
            }
         }

         return Set.copyOf(out);
      } else {
         AnimationEngine.logSetupWarning("[NoN Animation] Invalid join_after in {}: expected an array of exact namespaced animation ids.", fileId);
         return Set.of();
      }
   }

   private static void validateJoinAfterReferences(List<AnimationDefinitions.Definition> definitions) {
      Set<Identifier> loadedIds = new HashSet<>();

      for (AnimationDefinitions.Definition definition : definitions) {
         loadedIds.add(definition.id());
      }

      for (AnimationDefinitions.Definition definition : definitions) {
         for (Identifier predecessorId : definition.joinAfter()) {
            if (!loadedIds.contains(predecessorId)) {
               AnimationEngine.logSetupWarning("[NoN Animation] Animation {} references missing join_after predecessor {}.", definition.id(), predecessorId);
            }
         }
      }
   }

   private static Map<Identifier, List<AnimationDefinitions.Definition>> buildJoinFollowerIndex(List<AnimationDefinitions.Definition> definitions) {
      LinkedHashMap<Identifier, List<AnimationDefinitions.Definition>> mutable = new LinkedHashMap<>();

      for (AnimationDefinitions.Definition definition : definitions) {
         for (Identifier predecessorId : definition.joinAfter()) {
            mutable.computeIfAbsent(predecessorId, ignored -> new ArrayList<>()).add(definition);
         }
      }

      LinkedHashMap<Identifier, List<AnimationDefinitions.Definition>> immutable = new LinkedHashMap<>();
      mutable.forEach((id, followers) -> immutable.put(id, List.copyOf((Collection<? extends AnimationDefinitions.Definition>)followers)));
      return Map.copyOf(immutable);
   }

   @Nullable
   private static String readEntityVariant(JsonObject obj, Identifier fileId, String contextLabel) {
      String key = "entity_variant";
      if (obj != null && obj.has(key)) {
         JsonElement el = obj.get(key);
         if (el == null || el.isJsonNull()) {
            return null;
         }

         if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String value = el.getAsString();
            if (value != null && !value.isBlank()) {
               value = value.trim().toLowerCase(Locale.ROOT);

               for (int i = 0; i < value.length(); i++) {
                  char c = value.charAt(i);
                  boolean valid = c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_';
                  if (!valid) {
                     AnimationEngine.logSetupWarning(
                        "[NoN Animation] Skipping {}: invalid {} '{}' in {}. Use lowercase letters, numbers, and underscores.",
                        fileId,
                        key,
                        value,
                        contextLabel
                     );
                     return "\u0000";
                  }
               }

               return value;
            } else {
               AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: {} in {} must not be blank.", fileId, key, contextLabel);
               return "\u0000";
            }
         } else {
            AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: {} in {} must be a string.", fileId, key, contextLabel);
            return "\u0000";
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static Identifier readOptionalItemId(JsonObject obj, String key, Identifier fileId, String contextLabel) {
      if (obj != null && obj.has(key)) {
         JsonElement el = obj.get(key);
         if (el == null || el.isJsonNull()) {
            return null;
         }

         if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String raw = el.getAsString();
            if (raw != null && !raw.isBlank()) {
               Identifier id = Identifier.tryParse(raw.trim());
               if (id != null && Registries.ITEM.containsId(id)) {
                  return id;
               }

               AnimationEngine.logSetupWarning("[NoN Animation] Invalid item id '{}' for {} in {} {}.", raw, key, fileId, contextLabel);
               return null;
            } else {
               return null;
            }
         } else {
            AnimationEngine.logSetupWarning("[NoN Animation] Invalid {} in {} {}: expected item id string.", key, fileId, contextLabel);
            return null;
         }
      } else {
         return null;
      }
   }

   private static List<AnimationStageInfo> readStageInfos(
      JsonObject obj, Identifier defId, Identifier fileId, List<AnimationDefinitions.ActorConstraint> actorConstraints
   ) {
      List<AnimationStageInfo> stages = new ArrayList<>();
      double baseSpeed = readSpeed(obj, 1.0);
      Map<String, AnimationStageInfo.ActorPropEntry> defaultActorProps = buildDefaultActorPropMap(actorConstraints);
      Set<String> validActorKeys = buildActorKeySet(actorConstraints);
      if (obj.has("stages")) {
         JsonArray stagesArr = obj.getAsJsonArray("stages");
         Map<Integer, AnimationDefinitions.StageSpec> stageSpecs = new TreeMap<>();
         Map<Integer, List<AnimationStageInfo.ActorPropEntry>> stagePropsByNumber = new TreeMap<>();

         for (int i = 0; i < stagesArr.size(); i++) {
            JsonObject stageObj = stagesArr.get(i).getAsJsonObject();
            warnUnknownKeys(stageObj, fileId, "stage[" + i + "]", KNOWN_STAGE_KEYS);
            Integer stageNumber = parseStageNumber(stageObj, fileId);
            if (stageNumber != null && stageNumber > 0) {
               if (stageSpecs.containsKey(stageNumber)) {
                  AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: duplicate stage p{}", fileId, stageNumber);
               } else {
                  boolean loop = readLoopFlag(stageObj);
                  Double cycleSeconds = readCycleSeconds(stageObj);
                  long lengthTicks = secondsToTicks(cycleSeconds == null ? 0.0 : cycleSeconds);
                  double cycleTicks = secondsToExactTicks(cycleSeconds == null ? 0.0 : cycleSeconds);
                  boolean allowJoin = readAllowJoin(stageObj);
                  double speed = readSpeed(stageObj, baseSpeed);
                  Integer useStage = readOptionalStageReference(stageObj, "use_stage", fileId);
                  double cycleMidpointOffsetSeconds = readCycleMidpointOffsetSeconds(stageObj, fileId, stageNumber, loop, cycleTicks);
                  if (useStage != null && useStage <= 0) {
                     AnimationEngine.logSetupWarning(
                        "[NoN Animation] Invalid use_stage '{}' in {} stage p{}; falling back to stage itself.", useStage, fileId, stageNumber
                     );
                     useStage = null;
                  }

                  if (!loop && lengthTicks <= 0L) {
                     AnimationEngine.logSetupWarning("[NoN Animation] Stage p{} in {} is non-looping but has no cycle_seconds.", stageNumber, fileId);
                  }

                  stageSpecs.put(
                     stageNumber, new AnimationDefinitions.StageSpec(loop, lengthTicks, cycleTicks, allowJoin, speed, useStage, cycleMidpointOffsetSeconds)
                  );
                  stagePropsByNumber.put(stageNumber, resolveStageActorProps(stageObj, fileId, "p" + stageNumber, validActorKeys, defaultActorProps));
               }
            }
         }

         Map<Integer, AnimationStageInfo> stageMap = new TreeMap<>();

         for (Entry<Integer, AnimationDefinitions.StageSpec> entry : stageSpecs.entrySet()) {
            int stageNumber = entry.getKey();
            AnimationDefinitions.StageSpec spec = entry.getValue();
            Identifier stageId = AnimationIds.stageId(defId, stageNumber);
            List<AnimationStageInfo.ActorPropEntry> stageProps = stagePropsByNumber.getOrDefault(stageNumber, List.of());
            Identifier playbackId = stageId;
            if (spec.useStage != null && spec.useStage != stageNumber) {
               if (stageSpecs.containsKey(spec.useStage)) {
                  playbackId = AnimationIds.stageId(defId, spec.useStage);
               } else {
                  AnimationEngine.logSetupWarning(
                     "[NoN Animation] use_stage '{}' in {} stage p{} points to missing stage; falling back to p{}.",
                     spec.useStage,
                     fileId,
                     stageNumber,
                     stageNumber
                  );
               }
            }

            stageMap.put(
               stageNumber,
               new AnimationStageInfo(
                  stageId, spec.loop, spec.lengthTicks, spec.allowJoin, spec.speed, playbackId, stageProps, spec.cycleMidpointOffsetSeconds, spec.cycleTicks
               )
            );
         }

         stages.addAll(stageMap.values());
      }

      return List.copyOf(stages);
   }

   private static Map<String, AnimationStageInfo.ActorPropEntry> buildDefaultActorPropMap(List<AnimationDefinitions.ActorConstraint> actorConstraints) {
      if (actorConstraints != null && !actorConstraints.isEmpty()) {
         String[] actorKeys = buildUniqueActorKeys(actorConstraints);
         Map<String, AnimationStageInfo.ActorPropEntry> defaults = new LinkedHashMap<>();
         int count = Math.min(actorConstraints.size(), actorKeys.length);

         for (int i = 0; i < count; i++) {
            AnimationDefinitions.ActorConstraint constraint = actorConstraints.get(i);
            Identifier left = constraint.propLeftItemId();
            Identifier right = constraint.propRightItemId();
            Identifier floor = constraint.propFloorItemId();
            if (left != null || right != null || floor != null) {
               String actorKey = actorKeys[i];
               defaults.put(actorKey, new AnimationStageInfo.ActorPropEntry(actorKey, left, right, floor));
            }
         }

         return defaults.isEmpty() ? Map.of() : Map.copyOf(defaults);
      } else {
         return Map.of();
      }
   }

   private static Set<String> buildActorKeySet(List<AnimationDefinitions.ActorConstraint> actorConstraints) {
      if (actorConstraints != null && !actorConstraints.isEmpty()) {
         String[] actorKeys = buildUniqueActorKeys(actorConstraints);
         LinkedHashSet<String> out = new LinkedHashSet<>();
         Collections.addAll(out, actorKeys);
         return out.isEmpty() ? Set.of() : Set.copyOf(out);
      } else {
         return Set.of();
      }
   }

   private static List<String> normalizeTagList(Set<String> tags) {
      if (tags != null && !tags.isEmpty()) {
         List<String> out = new ArrayList<>();
         Set<String> seen = new LinkedHashSet<>();

         for (String raw : tags) {
            if (raw != null) {
               String value = raw.trim().toLowerCase(Locale.ROOT);
               if (!value.isEmpty() && seen.add(value)) {
                  out.add(value);
               }
            }
         }

         return out.isEmpty() ? List.of() : List.copyOf(out);
      } else {
         return List.of();
      }
   }

   private static List<AnimationStageInfo.ActorPropEntry> resolveStageActorProps(
      JsonObject stageObj, Identifier fileId, String stageLabel, Set<String> validActorKeys, Map<String, AnimationStageInfo.ActorPropEntry> defaultActorProps
   ) {
      if (stageObj == null) {
         return List.of();
      }

      Map<String, AnimationStageInfo.ActorPropEntry> effective = new LinkedHashMap<>();
      if (defaultActorProps != null && !defaultActorProps.isEmpty()) {
         effective.putAll(defaultActorProps);
      }

      if (stageObj.has("props")) {
         JsonElement propsEl = stageObj.get("props");
         if (propsEl == null || propsEl.isJsonNull()) {
            effective.clear();
         } else if (!propsEl.isJsonObject()) {
            AnimationEngine.logSetupWarning("[NoN Animation] Invalid props in {} {}: expected object.", fileId, stageLabel);
         } else {
            JsonObject propsObj = propsEl.getAsJsonObject();

            for (Entry<String, JsonElement> entry : propsObj.entrySet()) {
               String actorKey = entry.getKey() == null ? "" : entry.getKey().trim();
               if (actorKey.isBlank()) {
                  AnimationEngine.logSetupWarning("[NoN Animation] Invalid props actor key in {} {}: blank key.", fileId, stageLabel);
               } else if (validActorKeys != null && !validActorKeys.isEmpty() && !validActorKeys.contains(actorKey)) {
                  AnimationEngine.logSetupWarning(
                     "[NoN Animation] Unknown props actor key '{}' in {} {}. Valid keys: {}", actorKey, fileId, stageLabel, validActorKeys
                  );
               } else {
                  JsonElement actorPropsEl = entry.getValue();
                  if (actorPropsEl == null || actorPropsEl.isJsonNull()) {
                     effective.remove(actorKey);
                  } else if (!actorPropsEl.isJsonObject()) {
                     AnimationEngine.logSetupWarning(
                        "[NoN Animation] Invalid props value for actor '{}' in {} {}: expected object.", actorKey, fileId, stageLabel
                     );
                  } else {
                     JsonObject actorProps = actorPropsEl.getAsJsonObject();
                     warnUnknownKeys(actorProps, fileId, stageLabel + ".props." + actorKey, KNOWN_STAGE_ACTOR_PROP_KEYS);
                     AnimationStageInfo.ActorPropEntry current = effective.get(actorKey);
                     Identifier left = current == null ? null : current.propLeftItemId();
                     Identifier right = current == null ? null : current.propRightItemId();
                     Identifier floor = current == null ? null : current.propFloorItemId();
                     boolean touched = false;
                     if (actorProps.has("prop_left")) {
                        touched = true;
                        left = parseStagePropItemId(actorProps.get("prop_left"), fileId, stageLabel, actorKey, "prop_left", left);
                     }

                     if (actorProps.has("prop_right")) {
                        touched = true;
                        right = parseStagePropItemId(actorProps.get("prop_right"), fileId, stageLabel, actorKey, "prop_right", right);
                     }

                     if (actorProps.has("prop_floor")) {
                        touched = true;
                        floor = parseStagePropItemId(actorProps.get("prop_floor"), fileId, stageLabel, actorKey, "prop_floor", floor);
                     }

                     if (touched) {
                        if (left == null && right == null && floor == null) {
                           effective.remove(actorKey);
                        } else {
                           effective.put(actorKey, new AnimationStageInfo.ActorPropEntry(actorKey, left, right, floor));
                        }
                     }
                  }
               }
            }
         }
      }

      if (effective.isEmpty()) {
         return List.of();
      }

      List<AnimationStageInfo.ActorPropEntry> out = new ArrayList<>(effective.values());
      out.sort(Comparator.comparing(AnimationStageInfo.ActorPropEntry::actorKey));
      return List.copyOf(out);
   }

   @Nullable
   private static Identifier parseStagePropItemId(
      JsonElement element, Identifier fileId, String stageLabel, String actorKey, String field, @Nullable Identifier fallback
   ) {
      if (element == null || element.isJsonNull()) {
         return null;
      }

      if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
         String raw = element.getAsString();
         if (raw != null && !raw.isBlank()) {
            Identifier id = Identifier.tryParse(raw.trim());
            if (id != null && Registries.ITEM.containsId(id)) {
               return id;
            }

            AnimationEngine.logSetupWarning("[NoN Animation] Invalid item id '{}' for {} actor '{}' in {} {}.", raw, field, actorKey, fileId, stageLabel);
            return fallback;
         } else {
            return null;
         }
      } else {
         AnimationEngine.logSetupWarning(
            "[NoN Animation] Invalid {} for actor '{}' in {} {}: expected item id string or null.", field, actorKey, fileId, stageLabel
         );
         return fallback;
      }
   }

   private static Integer parseStageNumber(JsonObject obj, Identifier fileId) {
      if (!obj.has("stage")) {
         AnimationEngine.logSetupWarning("[NoN Animation] Skipping stage in {}: missing 'stage' number", fileId);
         return null;
      }

      JsonElement el = obj.get("stage");
      if (el != null && el.isJsonPrimitive()) {
         JsonPrimitive prim = el.getAsJsonPrimitive();
         if (prim.isNumber()) {
            return prim.getAsInt();
         }

         AnimationEngine.logSetupWarning("[NoN Animation] Skipping {}: 'stage' must be a number", fileId);
         return null;
      } else {
         AnimationEngine.logSetupWarning("[NoN Animation] Skipping stage in {}: invalid 'stage' value", fileId);
         return null;
      }
   }

   private static Integer readOptionalStageReference(JsonObject obj, String key, Identifier fileId) {
      if (!obj.has(key)) {
         return null;
      }

      JsonElement el = obj.get(key);
      if (el != null && el.isJsonPrimitive()) {
         JsonPrimitive prim = el.getAsJsonPrimitive();
         if (prim.isNumber()) {
            return prim.getAsInt();
         }

         AnimationEngine.logSetupWarning("[NoN Animation] Invalid {} in {}: value must be a number", key, fileId);
         return null;
      } else {
         AnimationEngine.logSetupWarning("[NoN Animation] Invalid {} in {}: value is not a primitive", key, fileId);
         return null;
      }
   }

   private static boolean readLoopFlag(JsonObject obj) {
      if (!obj.has("loop")) {
         return true;
      } else {
         JsonElement el = obj.get("loop");
         if (el != null && el.isJsonPrimitive()) {
            JsonPrimitive prim = el.getAsJsonPrimitive();
            return prim.isBoolean() ? prim.getAsBoolean() : true;
         } else {
            return true;
         }
      }
   }

   private static boolean readAllowJoin(JsonObject obj) {
      if (!obj.has("allow_join")) {
         return true;
      } else {
         JsonElement el = obj.get("allow_join");
         if (el != null && el.isJsonPrimitive()) {
            JsonPrimitive prim = el.getAsJsonPrimitive();
            return prim.isBoolean() ? prim.getAsBoolean() : true;
         } else {
            return true;
         }
      }
   }

   private static Double readCycleSeconds(JsonObject obj) {
      Double seconds = readDouble(obj, "cycle_seconds");
      return seconds == null ? null : seconds;
   }

   private static Double readDouble(JsonObject obj, String key) {
      return !obj.has(key) ? null : readFiniteDouble(obj.get(key));
   }

   private static double readSpeed(JsonObject obj, double fallback) {
      Double value = readDouble(obj, "speed");
      return value != null && Double.isFinite(value) && !(value <= 0.0) ? value : fallback;
   }

   private static double readCycleMidpointOffsetSeconds(JsonObject obj, Identifier fileId, int stageNumber, boolean loop, double cycleTicks) {
      if (!obj.has("cycle_midpoint_offset_seconds")) {
         return 0.0;
      }

      Double value = readDouble(obj, "cycle_midpoint_offset_seconds");
      if (value == null || Math.abs(value) < 1.0E-6) {
         return 0.0;
      }

      if (!loop) {
         AnimationEngine.logSetupWarning(
            "[NoN Animation] Ignoring cycle_midpoint_offset_seconds in {} stage p{}: timing warp only applies to looping stages.", fileId, stageNumber
         );
         return 0.0;
      }

      if (cycleTicks <= 0.0) {
         AnimationEngine.logSetupWarning(
            "[NoN Animation] Ignoring cycle_midpoint_offset_seconds in {} stage p{}: stage has no positive cycle_seconds.", fileId, stageNumber
         );
         return 0.0;
      }

      double clamped = StageTimeWarp.clampOffsetSeconds(cycleTicks, value);
      if (Math.abs(clamped - value) > 1.0E-6) {
         AnimationEngine.logSetupWarning(
            "[NoN Animation] Clamped cycle_midpoint_offset_seconds in {} stage p{} from {} to {}.", fileId, stageNumber, value, clamped
         );
      }

      return clamped;
   }

   private static long secondsToTicks(double lengthSeconds) {
      return lengthSeconds <= 0.0 ? 0L : Math.max(1L, (long)Math.ceil(lengthSeconds * 20.0));
   }

   private static double secondsToExactTicks(double lengthSeconds) {
      return Double.isFinite(lengthSeconds) && !(lengthSeconds <= 0.0) ? lengthSeconds * 20.0 : 0.0;
   }

   private static Integer readOptionalInt(JsonElement element) {
      Double value = readFiniteDouble(element);
      if (value == null) {
         return null;
      } else if (value < -2.1474836E9F || value > 2.147483647E9) {
         return null;
      } else {
         return value != Math.rint(value) ? null : value.intValue();
      }
   }

   private static Double readFiniteDouble(JsonElement element) {
      if (element != null && element.isJsonPrimitive()) {
         JsonPrimitive primitive = element.getAsJsonPrimitive();
         if (primitive.isNumber()) {
            double value = primitive.getAsDouble();
            return Double.isFinite(value) ? value : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static Identifier definitionIdFromFile(Identifier fileId) {
      String path = fileId.getPath();
      String prefix = "afw_animdefs/";
      if (path.startsWith(prefix)) {
         path = path.substring(prefix.length());
      }

      if (path.endsWith(".json")) {
         path = path.substring(0, path.length() - 5);
      }

      return Identifier.of(fileId.getNamespace(), path);
   }

   public enum ActorActivity {
      ACTIVE,
      PASSIVE;
   }

   public record ActorConstraint(
      Set<Identifier> entityTypes,
      @Nullable String entityVariant,
      Set<String> requiredTags,
      Set<String> requiredTagsAny,
      String label,
      AnimationDefinitions.AgeRequirement ageRequirement,
      AnimationDefinitions.ActorActivity activity,
      @Nullable Identifier propLeftItemId,
      @Nullable Identifier propRightItemId,
      @Nullable Identifier propFloorItemId
   ) {
      public boolean matches(Entity e) {
         return this.matches(AnimationDefinitions.MatchActor.from(e));
      }

      private boolean matches(AnimationDefinitions.MatchActor actor) {
         if (!this.entityTypes.isEmpty() && !this.entityTypes.contains(actor.entityType())) {
            return false;
         } else if (this.entityVariant != null && !this.entityVariant.equals(actor.variant())) {
            return false;
         } else {
            Set<String> tags = actor.tags();
            if (!this.requiredTags.isEmpty() && !tags.containsAll(this.requiredTags)) {
               return false;
            } else {
               return !this.requiredTagsAny.isEmpty() && this.requiredTagsAny.stream().noneMatch(tags::contains) ? false : this.matchesAge(actor.baby());
            }
         }
      }

      public int assignmentPriority() {
         return this.entityTypes.size() + this.requiredTags.size() + this.requiredTagsAny.size() + (this.entityVariant == null ? 0 : 1);
      }

      public String stableKey() {
         List<String> types = this.entityTypes.stream().<String>map(Identifier::toString).sorted().toList();
         List<String> tagsAll = this.requiredTags.stream().sorted().toList();
         List<String> tagsAny = this.requiredTagsAny.stream().sorted().toList();
         return "types="
            + types
            + "|variant="
            + this.entityVariant
            + "|tagsAll="
            + tagsAll
            + "|tagsAny="
            + tagsAny
            + "|age="
            + this.ageRequirement
            + "|activity="
            + this.activity;
      }

      public String derivedKey() {
         if (this.label != null && !this.label.isBlank()) {
            return AnimationDefinitions.sanitizeKeyPart(this.label);
         }

         String base = "actor";
         if (!this.entityTypes.isEmpty()) {
            if (this.entityTypes.size() == 1) {
               base = this.entityTypes.iterator().next().getPath();
            } else {
               List<String> paths = this.entityTypes.stream().<String>map(Identifier::getPath).sorted().toList();
               base = String.join("_", paths);
            }
         }

         if (!this.requiredTags.isEmpty()) {
            List<String> tags = this.requiredTags.stream().sorted().map(AnimationDefinitions::sanitizeKeyPart).toList();
            base = base + "_" + String.join("_", tags);
         }

         if (!this.requiredTagsAny.isEmpty()) {
            List<String> tagsAny = this.requiredTagsAny.stream().sorted().map(AnimationDefinitions::sanitizeKeyPart).toList();
            base = base + "_any_" + String.join("_", tagsAny);
         }

         if (this.entityVariant != null && !this.entityVariant.isBlank()) {
            base = base + "_" + this.entityVariant;
         }

         if (this.ageRequirement == AnimationDefinitions.AgeRequirement.BABY) {
            base = base + "_baby";
         }

         return AnimationDefinitions.sanitizeKeyPart(base);
      }

      private boolean matchesAge(Entity e) {
         boolean baby = false;
         if (e instanceof LivingEntity living) {
            baby = living.isBaby();
         }

         return this.matchesAge(baby);
      }

      private boolean matchesAge(boolean baby) {
         AnimationDefinitions.AgeRequirement req = this.ageRequirement == null ? AnimationDefinitions.AgeRequirement.ADULT : this.ageRequirement;
         return req == AnimationDefinitions.AgeRequirement.BABY == baby;
      }
   }

   private record ActorKey(Identifier entityType, boolean baby) {
   }

   public enum AgeRequirement {
      ADULT,
      BABY;
   }

   public record AnimationPackInfo(
      String id,
      String resourcePackId,
      String name,
      String author,
      String version,
      String description,
      @Nullable Identifier localModelRoot,
      List<AnimationDefinitions.ModelImport> modelImports
   ) {
      public AnimationPackInfo {
         id = AnimationDefinitions.sanitizeNonBlank(id, "unknown");
         resourcePackId = AnimationDefinitions.sanitizeNonBlank(resourcePackId, id);
         name = AnimationDefinitions.sanitizeNonBlank(name, AnimationDefinitions.displayNameFromPackId(id));
         author = AnimationDefinitions.sanitizeNonBlank(author, "Unknown");
         version = AnimationDefinitions.sanitizeNullable(version);
         description = AnimationDefinitions.sanitizeNullable(description);
         modelImports = modelImports == null ? List.of() : List.copyOf(modelImports);
      }

      public AnimationPackInfo(
         String id, String resourcePackId, String name, String author, String version, String description, @Nullable Identifier localModelRoot
      ) {
         this(id, resourcePackId, name, author, version, description, localModelRoot, List.of());
      }

      public List<Identifier> modelRootsFor(@Nullable Identifier entityTypeId) {
         LinkedHashSet<Identifier> roots = new LinkedHashSet<>();
         if (this.localModelRoot != null) {
            roots.add(this.localModelRoot);
         }

         if (entityTypeId != null) {
            for (AnimationDefinitions.ModelImport modelImport : this.modelImports) {
               if (modelImport.entityTypes().contains(entityTypeId)) {
                  roots.add(modelImport.modelRoot());
                  if (roots.size() >= 32) {
                     break;
                  }
               }
            }
         }

         return roots.isEmpty() ? List.of() : List.copyOf(roots);
      }
   }

   public record BlockPredicate(Set<Identifier> blockIds, Set<Identifier> tagIds) {
      public boolean isEmpty() {
         return this.blockIds.isEmpty() && this.tagIds.isEmpty();
      }
   }

   public record BlockRequirements(
      String type,
      AnimationDefinitions.WallHeight height,
      AnimationDefinitions.WallWidth width,
      @Nullable AnimationDefinitions.WallFacing facing,
      AnimationDefinitions.Clearance clearance,
      AnimationDefinitions.CenterSupport support,
      AnimationDefinitions.CenterPlacement placement,
      int surfaceRadius,
      AnimationDefinitions.SurfaceFootprint surfaceFootprint,
      @Nullable AnimationDefinitions.BlockPredicate blocks
   ) {
      public boolean isWall() {
         return "wall".equalsIgnoreCase(this.type);
      }

      public boolean isCenterSupport() {
         return "center_support".equalsIgnoreCase(this.type);
      }
   }

   public enum CenterPlacement {
      DIRECTIONAL_CLEARANCE("directional_clearance"),
      SURFACE_FOOTPRINT("surface_footprint");

      private final String id;

      CenterPlacement(String id) {
         this.id = id;
      }

      public String id() {
         return this.id;
      }
   }

   public enum CenterSupport {
      FULL_BLOCK("full_block"),
      SLAB("slab"),
      HALF_HEIGHT("half_height"),
      SURFACE("surface");

      private final String id;

      CenterSupport(String id) {
         this.id = id;
      }

      public String id() {
         return this.id;
      }
   }

   public record Clearance(int width, int height, int depth) {
   }

   public record Definition(
      Identifier id,
      List<AnimationDefinitions.ActorConstraint> actors,
      Set<String> requiredUnionTags,
      Set<String> animationTags,
      List<String> contentTags,
      List<AnimationStageInfo> stages,
      double weight,
      AnimationDefinitions.BlockRequirements blockRequirements,
      AnimationDefinitions.WaterRequirement waterRequirement,
      @Nullable String positionAnchorActor,
      AnimationDefinitions.AnimationPackInfo packInfo,
      @Nullable String displayName,
      Set<Identifier> joinAfter,
      AnimationGameplayMetadata.Data gameplayMetadata
   ) {
      public Definition {
         joinAfter = joinAfter == null ? Set.of() : Set.copyOf(joinAfter);
         gameplayMetadata = gameplayMetadata == null ? AnimationGameplayMetadata.Data.empty() : gameplayMetadata;
      }

      public Definition(
         Identifier id,
         List<AnimationDefinitions.ActorConstraint> actors,
         Set<String> requiredUnionTags,
         Set<String> animationTags,
         List<String> contentTags,
         List<AnimationStageInfo> stages,
         double weight,
         AnimationDefinitions.BlockRequirements blockRequirements,
         AnimationDefinitions.WaterRequirement waterRequirement,
         @Nullable String positionAnchorActor,
         AnimationDefinitions.AnimationPackInfo packInfo,
         @Nullable String displayName,
         Set<Identifier> joinAfter
      ) {
         this(
            id,
            actors,
            requiredUnionTags,
            animationTags,
            contentTags,
            stages,
            weight,
            blockRequirements,
            waterRequirement,
            positionAnchorActor,
            packInfo,
            displayName,
            joinAfter,
            AnimationGameplayMetadata.Data.empty()
         );
      }

      public Definition(
         Identifier id,
         List<AnimationDefinitions.ActorConstraint> actors,
         Set<String> requiredUnionTags,
         Set<String> animationTags,
         List<String> contentTags,
         List<AnimationStageInfo> stages,
         double weight,
         AnimationDefinitions.BlockRequirements blockRequirements,
         AnimationDefinitions.WaterRequirement waterRequirement,
         @Nullable String positionAnchorActor,
         AnimationDefinitions.AnimationPackInfo packInfo,
         @Nullable String displayName
      ) {
         this(
            id,
            actors,
            requiredUnionTags,
            animationTags,
            contentTags,
            stages,
            weight,
            blockRequirements,
            waterRequirement,
            positionAnchorActor,
            packInfo,
            displayName,
            Set.of()
         );
      }

      public Definition(
         Identifier id,
         List<AnimationDefinitions.ActorConstraint> actors,
         Set<String> requiredUnionTags,
         Set<String> animationTags,
         List<String> contentTags,
         List<AnimationStageInfo> stages,
         double weight,
         AnimationDefinitions.BlockRequirements blockRequirements,
         AnimationDefinitions.WaterRequirement waterRequirement,
         @Nullable String positionAnchorActor,
         AnimationDefinitions.AnimationPackInfo packInfo
      ) {
         this(
            id, actors, requiredUnionTags, animationTags, contentTags, stages, weight, blockRequirements, waterRequirement, positionAnchorActor, packInfo, null
         );
      }

      public boolean matches(List<Entity> selectedActorsSortedById, Set<String> requiredAnimationTags) {
         return this.matches(AnimationDefinitions.MatchContext.create(selectedActorsSortedById), requiredAnimationTags);
      }

      private boolean matches(AnimationDefinitions.MatchContext context, Set<String> requiredAnimationTags) {
         return !this.matchesAnimationTags(requiredAnimationTags) ? false : this.matchesActors(context);
      }

      private boolean matchesActors(AnimationDefinitions.MatchContext context) {
         if (context.actors().size() != this.actors.size()) {
            return false;
         } else {
            return !context.unionTags().containsAll(this.requiredUnionTags)
               ? false
               : AnimationDefinitions.matchesUnordered(context.actors(), this.actors, AnimationDefinitions.MATCH_ORDER_BY_DEFINITION.get(this.id));
         }
      }

      private boolean matchesAnimationTags(Set<String> requiredAnimationTags) {
         Set<String> required = requiredAnimationTags == null ? Set.of() : requiredAnimationTags;
         if (!required.isEmpty()) {
            return this.animationTags != null && !this.animationTags.isEmpty() ? this.animationTags.containsAll(required) : false;
         } else {
            return this.animationTags == null || this.animationTags.isEmpty() || this.animationTags.contains("normal_match");
         }
      }

      public AnimationDefinitions.Definition withGameplayMetadata(AnimationGameplayMetadata.Data metadata) {
         return new AnimationDefinitions.Definition(
            this.id,
            this.actors,
            this.requiredUnionTags,
            this.animationTags,
            this.contentTags,
            this.stages,
            this.weight,
            this.blockRequirements,
            this.waterRequirement,
            this.positionAnchorActor,
            this.packInfo,
            this.displayName,
            this.joinAfter,
            metadata
         );
      }

      public boolean attackEligible() {
         return this.gameplayMetadata.attackEligible();
      }
   }

   private record MatchActor(Entity entity, Identifier entityType, @Nullable String variant, Set<String> tags, boolean baby) {
      private static AnimationDefinitions.MatchActor from(Entity entity) {
         boolean baby = entity instanceof LivingEntity living && living.isBaby();
         return new AnimationDefinitions.MatchActor(
            entity, Registries.ENTITY_TYPE.getId(entity.getType()), EntityVariants.resolveVariant(entity), Set.copyOf(NonActorEvents.resolve(entity)), baby
         );
      }
   }

   private record MatchContext(List<AnimationDefinitions.MatchActor> actors, Set<String> unionTags) {
      private static AnimationDefinitions.MatchContext create(List<Entity> entities) {
         if (entities != null && !entities.isEmpty()) {
            List<AnimationDefinitions.MatchActor> actors = new ArrayList<>(entities.size());
            Set<String> unionTags = new LinkedHashSet<>();

            for (Entity entity : entities) {
               if (entity != null) {
                  AnimationDefinitions.MatchActor actor = AnimationDefinitions.MatchActor.from(entity);
                  actors.add(actor);
                  unionTags.addAll(actor.tags());
               }
            }

            return new AnimationDefinitions.MatchContext(List.copyOf(actors), Set.copyOf(unionTags));
         } else {
            return new AnimationDefinitions.MatchContext(List.of(), Set.of());
         }
      }
   }

   public record MatchResult(List<AnimationDefinitions.Definition> candidatesSorted, AnimationDefinitions.Definition chosen) {
   }

   public record ModelImport(String packId, Identifier modelRoot, Set<Identifier> entityTypes) {
      public ModelImport {
         packId = AnimationDefinitions.sanitizeNonBlank(packId, "unknown");
         entityTypes = entityTypes == null ? Set.of() : Set.copyOf(entityTypes);
      }
   }

   public static final class Reloader implements SynchronousResourceReloader {
      static final Identifier RELOADER_ID = AnimationEngine.runtimeId("definitions");

      public void reload(ResourceManager manager) {
         AnimationDefinitions.reload(manager);
      }
   }

   private record StageSpec(
      boolean loop, long lengthTicks, double cycleTicks, boolean allowJoin, double speed, Integer useStage, double cycleMidpointOffsetSeconds
   ) {
   }

   public record SurfaceFootprint(int width, int depth, int height, int margin) {
   }

   public enum WallFacing {
      FRONT("front"),
      BACK("back"),
      LEFT("left"),
      RIGHT("right");

      private final String id;

      WallFacing(String id) {
         this.id = id;
      }

      public String id() {
         return this.id;
      }

      public boolean matches(Direction blockFacing, Direction wallSurfaceFacing) {
         if (blockFacing != null && wallSurfaceFacing != null && blockFacing.getAxis() != Axis.Y && wallSurfaceFacing.getAxis() != Axis.Y) {
            Direction requiredSurface = switch (this) {
               case FRONT -> blockFacing;
               case BACK -> blockFacing.getOpposite();
               case LEFT -> blockFacing.rotateYCounterclockwise();
               case RIGHT -> blockFacing.rotateYClockwise();
            };
            return requiredSurface == wallSurfaceFacing;
         } else {
            return false;
         }
      }
   }

   public record WallHeight(int min, Integer max) {
   }

   public record WallWidth(int min, Integer max) {
   }

   public enum WaterRequirement {
      NONE,
      SURFACE,
      UNDERWATER;
   }
}
