package com.nonid.internal.animation.client.render.gecko;

import com.nonid.client.NonDebugSpinMode;
import com.nonid.internal.animation.AnimationEngine;
import com.nonid.internal.animation.AnimationEngineClient;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.GeckoLibConstants;
import software.bernie.geckolib.cache.GeckoLibResources;
import software.bernie.geckolib.loading.object.BakedAnimations;

public final class GeckoResourceResolver {
   private static final String SAFETY_FALLBACK_ANIMATION_KEY = "animation";
   private static final Identifier DEFAULT_MISSING_MODEL = AnimationEngine.packId("entity/missingmodel");
   private static final Identifier DEFAULT_MISSING_TEXTURE = AnimationEngine.packId("textures/missingmodel.png");
   private static final Set<String> LOGGED_MISSING_TYPES = Collections.newSetFromMap(new ConcurrentHashMap<>());
   private static final Set<String> LOGGED_MISSING_ANIMATIONS = Collections.newSetFromMap(new ConcurrentHashMap<>());
   private static final Set<String> LOGGED_MISSING_ANIMATION_KEYS = Collections.newSetFromMap(new ConcurrentHashMap<>());
   private static final Set<String> LOGGED_RESOURCE_LOOKUP_ERRORS = Collections.newSetFromMap(new ConcurrentHashMap<>());
   private static final Map<GeckoResourceResolver.AnimationResourceKey, GeckoResourceResolver.AnimationResourceResolution> ANIMATION_RESOURCE_CACHE = new ConcurrentHashMap<>();
   private static final Map<GeckoResourceResolver.AnimationKeyKey, Optional<String>> ANIMATION_KEY_CACHE = new ConcurrentHashMap<>();
   private static final Map<Identifier, String> FIRST_ANIMATION_KEY_CACHE = new ConcurrentHashMap<>();
   private static final Map<GeckoResourceResolver.ModelKey, GeckoResourceResolver.ModelAndTexture> MODEL_AND_TEXTURE_CACHE = new ConcurrentHashMap<>();
   private static final Map<Identifier, Boolean> GEO_MODEL_CACHE = new ConcurrentHashMap<>();
   private static final Map<Identifier, Boolean> RESOURCE_EXISTS_CACHE = new ConcurrentHashMap<>();

   private GeckoResourceResolver() {
   }

   public static Identifier toAnimationResource(Identifier animationId) {
      if (NonDebugSpinMode.isEnabled()) {
         return NonDebugSpinMode.DEBUG_SPIN_ANIMATION_RESOURCE;
      } else {
         return animationId == null ? null : Identifier.of(animationId.getNamespace(), nestedAnimationPath(animationId.getPath(), null));
      }
   }

   public static Identifier toAnimationResource(Identifier animationId, String actorKey) {
      if (NonDebugSpinMode.isEnabled()) {
         return NonDebugSpinMode.DEBUG_SPIN_ANIMATION_RESOURCE;
      } else {
         return animationId == null ? null : Identifier.of(animationId.getNamespace(), nestedAnimationPath(animationId.getPath(), actorKey));
      }
   }

   public static Identifier toConjoinedAnimationResource(Identifier animationId) {
      if (animationId == null) {
         return null;
      }

      String folder = animationFolderPath(animationId.getPath());
      return folder.equals(animationId.getPath()) ? null : Identifier.of(animationId.getNamespace(), "afw/" + folder);
   }

   private static Identifier toNestedConjoinedAnimationResource(Identifier animationId) {
      if (animationId == null) {
         return null;
      }

      String folder = animationFolderPath(animationId.getPath());
      return folder.equals(animationId.getPath()) ? null : Identifier.of(animationId.getNamespace(), "afw/" + folder + "/" + folder);
   }

   public static Identifier resolveAnimationResource(Identifier animationId, String actorKey, Identifier entityTypeId) {
      return resolveAnimationResource(animationId, actorKey, entityTypeId, true);
   }

   public static Identifier resolveAnimationResource(Identifier animationId, String actorKey, Identifier entityTypeId, boolean warnMissing) {
      if (NonDebugSpinMode.isEnabled()) {
         return NonDebugSpinMode.DEBUG_SPIN_ANIMATION_RESOURCE;
      }

      if (animationId == null) {
         return null;
      }

      GeckoResourceResolver.AnimationResourceKey key = new GeckoResourceResolver.AnimationResourceKey(animationId, actorKey, entityTypeId);
      GeckoResourceResolver.AnimationResourceResolution resolution = ANIMATION_RESOURCE_CACHE.computeIfAbsent(key, GeckoResourceResolver::findAnimationResource);
      if (warnMissing && resolution.warnableMissing()) {
         String logKey = animationId + "|actor=" + actorKey + "|type=" + entityTypeId;
         if (LOGGED_MISSING_ANIMATIONS.add(logKey)) {
            String message = AnimationEngine.formatLogTemplate(
               "[NoN Animation] Missing per-actor animation for {} (actorKey={}, typeId={}); no base animation found.", animationId, actorKey, entityTypeId
            );
            AnimationEngine.LOGGER.warn("{}", message);
            AnimationEngineClient.sendClientSetupWarning(message);
         }
      }

      return resolution.resource();
   }

   private static GeckoResourceResolver.AnimationResourceResolution findAnimationResource(GeckoResourceResolver.AnimationResourceKey key) {
      Identifier animationId = key.animationId();
      String actorKey = key.actorKey();
      Identifier entityTypeId = key.entityTypeId();
      Identifier conjoined = toConjoinedAnimationResource(animationId);
      if (hasBakedAnimation(conjoined)) {
         return new GeckoResourceResolver.AnimationResourceResolution(conjoined, false);
      }

      Identifier nestedConjoined = toNestedConjoinedAnimationResource(animationId);
      if (hasBakedAnimation(nestedConjoined)) {
         return new GeckoResourceResolver.AnimationResourceResolution(nestedConjoined, false);
      }

      Identifier actorCandidate = null;
      if (actorKey != null && !actorKey.isBlank()) {
         actorCandidate = Identifier.of(animationId.getNamespace(), nestedAnimationPath(animationId.getPath(), actorKey));
         if (hasBakedAnimation(actorCandidate)) {
            return new GeckoResourceResolver.AnimationResourceResolution(actorCandidate, false);
         }
      }

      Identifier typeCandidate = null;
      if (entityTypeId != null) {
         String typeKey = entityTypeId.getPath();
         typeCandidate = Identifier.of(animationId.getNamespace(), nestedAnimationPath(animationId.getPath(), typeKey));
         if (hasBakedAnimation(typeCandidate)) {
            return new GeckoResourceResolver.AnimationResourceResolution(typeCandidate, false);
         }

         String namespacedKey = entityTypeId.getNamespace() + "_" + entityTypeId.getPath();
         if (!namespacedKey.equals(typeKey)) {
            Identifier namespacedCandidate = Identifier.of(animationId.getNamespace(), nestedAnimationPath(animationId.getPath(), namespacedKey));
            if (hasBakedAnimation(namespacedCandidate)) {
               return new GeckoResourceResolver.AnimationResourceResolution(namespacedCandidate, false);
            }
         }
      }

      Identifier base = Identifier.of(animationId.getNamespace(), nestedAnimationPath(animationId.getPath(), null));
      boolean baseBaked = hasBakedAnimation(base);
      return new GeckoResourceResolver.AnimationResourceResolution(base, !baseBaked && (actorCandidate != null || typeCandidate != null));
   }

   public static boolean hasBakedAnimation(Identifier animationResource) {
      return animationResource != null && GeckoLibResources.getBakedAnimations().cache().containsKey(animationResource);
   }

   public static boolean hasBakedAnimationKey(Identifier animationResource, String animationKey) {
      if (animationResource != null && animationKey != null && !animationKey.isBlank()) {
         BakedAnimations baked = (BakedAnimations)GeckoLibResources.getBakedAnimations().cache().get(animationResource);
         return baked != null && baked.animations().containsKey(animationKey);
      } else {
         return false;
      }
   }

   public static String resolveAnimationKey(Identifier animationResource) {
      return animationResource == null ? "animation" : FIRST_ANIMATION_KEY_CACHE.computeIfAbsent(animationResource, resource -> {
         BakedAnimations baked = (BakedAnimations)GeckoLibResources.getBakedAnimations().cache().get(resource);
         return baked != null && !baked.animations().isEmpty() ? (String)baked.animations().keySet().iterator().next() : "animation";
      });
   }

   @Nullable
   public static String resolveAnimationKey(Identifier animationResource, Identifier animationId, String actorKey, Identifier entityTypeId) {
      return resolveAnimationKey(animationResource, animationId, actorKey, entityTypeId, true);
   }

   @Nullable
   public static String resolveAnimationKey(Identifier animationResource, Identifier animationId, String actorKey, Identifier entityTypeId, boolean warnMissing) {
      if (animationResource != null && animationId != null) {
         GeckoResourceResolver.AnimationKeyKey key = new GeckoResourceResolver.AnimationKeyKey(animationResource, animationId, actorKey, entityTypeId);
         Optional<String> resolved = ANIMATION_KEY_CACHE.computeIfAbsent(key, GeckoResourceResolver::findAnimationKey);
         if (resolved.isPresent()) {
            return resolved.get();
         }

         if (warnMissing) {
            String logKey = animationResource + "|stage=" + animationId + "|actor=" + actorKey + "|type=" + entityTypeId;
            if (LOGGED_MISSING_ANIMATION_KEYS.add(logKey)) {
               String message = AnimationEngine.formatLogTemplate(
                  "[NoN Animation] Missing actor clip in conjoined animation {} for stage {} (actorKey={}, typeId={}).",
                  animationResource,
                  animationId,
                  actorKey,
                  entityTypeId
               );
               AnimationEngine.LOGGER.warn("{}", message);
               AnimationEngineClient.sendClientSetupWarning(message);
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static Optional<String> findAnimationKey(GeckoResourceResolver.AnimationKeyKey key) {
      Identifier animationResource = key.animationResource();
      Identifier animationId = key.animationId();
      Identifier conjoined = toConjoinedAnimationResource(animationId);
      Identifier nestedConjoined = toNestedConjoinedAnimationResource(animationId);
      boolean isConjoined = animationResource.equals(conjoined) || animationResource.equals(nestedConjoined);
      if (!isConjoined) {
         return hasBakedAnimation(animationResource) ? Optional.of(resolveAnimationKey(animationResource)) : Optional.empty();
      }

      for (String candidate : conjoinedAnimationKeyCandidates(animationId, key.actorKey(), key.entityTypeId())) {
         if (hasBakedAnimationKey(animationResource, candidate)) {
            return Optional.of(candidate);
         }
      }

      return Optional.empty();
   }

   private static Set<String> conjoinedAnimationKeyCandidates(Identifier animationId, String actorKey, Identifier entityTypeId) {
      Set<String> candidates = new LinkedHashSet<>();
      String stageKey = conjoinedStageKey(animationId.getPath());
      if (stageKey == null) {
         return candidates;
      }

      if (actorKey != null && !actorKey.isBlank()) {
         candidates.add(stageKey + "_" + actorKey);
      }

      if (entityTypeId != null) {
         candidates.add(stageKey + "_" + entityTypeId.getPath());
         candidates.add(stageKey + "_" + entityTypeId.getNamespace() + "_" + entityTypeId.getPath());
      }

      return candidates;
   }

   @Nullable
   private static String conjoinedStageKey(String animationPath) {
      String folder = animationFolderPath(animationPath);
      return folder != null && !folder.equals(animationPath) && folder.length() + 1 < animationPath.length()
         ? animationPath.substring(folder.length() + 1)
         : null;
   }

   public static GeckoResourceResolver.ModelAndTexture resolveModelAndTexture(Identifier entityTypeId) {
      return entityTypeId == null
         ? missingWithLog(null)
         : resolveModelAndTexture(entityTypeId, List.of(), List.of(entityTypeId.getPath()), GenderedModelResolver.ModelGender.MALE);
   }

   public static GeckoResourceResolver.ModelAndTexture resolveModelAndTexture(
      Identifier entityTypeId, List<Identifier> modelRoots, List<String> preferredModelPaths, GenderedModelResolver.ModelGender gender
   ) {
      if (entityTypeId == null) {
         return missingWithLog(null);
      }

      List<Identifier> safeRoots = normalizeRoots(modelRoots);
      List<String> safePaths = normalizeModelPaths(preferredModelPaths, entityTypeId.getPath());
      GenderedModelResolver.ModelGender safeGender = gender == null ? GenderedModelResolver.ModelGender.MALE : gender;
      return MODEL_AND_TEXTURE_CACHE.computeIfAbsent(
         new GeckoResourceResolver.ModelKey(entityTypeId, safeRoots, safePaths, safeGender), GeckoResourceResolver::findModelAndTexture
      );
   }

   private static GeckoResourceResolver.ModelAndTexture findModelAndTexture(GeckoResourceResolver.ModelKey key) {
      Identifier entityTypeId = key.entityTypeId();
      GeckoResourceResolver.ResolvedModel resolved = findPreferredModel(key.modelRoots(), key.modelPaths(), key.gender());
      Identifier resolvedModel = resolved == null ? modelIdAtRoot(null, key.modelPaths().getFirst()) : resolved.model();
      String entityPath = entityTypeId.getPath();
      Identifier derivedTexture = Identifier.of(entityTypeId.getNamespace(), "textures/entity/" + entityPath + "/" + entityPath + ".png");
      Identifier resolvedTexture = resourceExists(derivedTexture) ? derivedTexture : DEFAULT_MISSING_TEXTURE;
      return new GeckoResourceResolver.ModelAndTexture(resolvedModel, resolvedTexture, resolved == null, resolved == null ? null : resolved.modelRoot());
   }

   @Nullable
   public static Identifier resolveGenderedModelAcrossRoots(
      @Nullable Identifier currentModel, List<Identifier> modelRoots, GenderedModelResolver.ModelGender gender
   ) {
      String modelPath = logicalModelPath(currentModel, modelRoots);
      if (modelPath == null) {
         return null;
      }

      GeckoResourceResolver.ResolvedModel resolved = findPreferredModel(
         normalizeRoots(modelRoots), List.of(modelPath), gender == null ? GenderedModelResolver.ModelGender.MALE : gender
      );
      return resolved == null ? null : resolved.model();
   }

   @Nullable
   private static GeckoResourceResolver.ResolvedModel findPreferredModel(
      List<Identifier> roots, List<String> modelPaths, GenderedModelResolver.ModelGender gender
   ) {
      for (String candidatePath : ModelVariantCandidates.ordered(modelPaths, gender)) {
         GeckoResourceResolver.ResolvedModel resolved = findAcrossProviders(roots, candidatePath);
         if (resolved != null) {
            return resolved;
         }
      }

      return null;
   }

   @Nullable
   private static GeckoResourceResolver.ResolvedModel findAcrossProviders(List<Identifier> roots, String modelPath) {
      for (Identifier root : roots) {
         Identifier candidate = modelIdAtRoot(root, modelPath);
         if (hasGeoModel(candidate)) {
            return new GeckoResourceResolver.ResolvedModel(candidate, root);
         }
      }

      Identifier shared = modelIdAtRoot(null, modelPath);
      if (hasGeoModel(shared)) {
         return new GeckoResourceResolver.ResolvedModel(shared, null);
      }

      Identifier legacyShared = AnimationEngine.packId(modelPath);
      return hasGeoModel(legacyShared) ? new GeckoResourceResolver.ResolvedModel(legacyShared, null) : null;
   }

   private static List<Identifier> normalizeRoots(List<Identifier> roots) {
      if (roots != null && !roots.isEmpty()) {
         LinkedHashSet<Identifier> unique = new LinkedHashSet<>();

         for (Identifier root : roots) {
            if (root != null) {
               unique.add(root);
            }
         }

         return unique.isEmpty() ? List.of() : List.copyOf(unique);
      } else {
         return List.of();
      }
   }

   private static List<String> normalizeModelPaths(List<String> paths, String fallback) {
      LinkedHashSet<String> unique = new LinkedHashSet<>();
      if (paths != null) {
         for (String path : paths) {
            if (path != null) {
               String clean = path.trim();
               if (!clean.isEmpty()) {
                  unique.add(clean);
               }
            }
         }
      }

      if (unique.isEmpty() && fallback != null && !fallback.isBlank()) {
         unique.add(fallback);
      }

      return List.copyOf(unique);
   }

   @Nullable
   private static String logicalModelPath(@Nullable Identifier model, List<Identifier> roots) {
      if (model == null) {
         return null;
      }

      Identifier base = GenderedModelResolver.stripGenderSuffix(model);

      for (Identifier root : normalizeRoots(roots)) {
         if (root.getNamespace().equals(base.getNamespace())) {
            String prefix = root.getPath() + "/";
            if (base.getPath().startsWith(prefix)) {
               return base.getPath().substring(prefix.length());
            }
         }
      }

      if ("animationframework".equals(base.getNamespace())) {
         String path = base.getPath();
         return path.startsWith("entity/") ? path.substring("entity/".length()) : path;
      } else {
         return null;
      }
   }

   public static Identifier modelIdAtRoot(@Nullable Identifier modelRoot, String modelPath) {
      String safePath = modelPath == null ? "" : modelPath;
      return modelRoot == null ? AnimationEngine.packId("entity/" + safePath) : Identifier.of(modelRoot.getNamespace(), modelRoot.getPath() + "/" + safePath);
   }

   public static GeckoResourceResolver.ModelAndTexture missingModelFallback(Identifier typeId) {
      return missingWithLog(typeId);
   }

   public static boolean hasGeoModel(Identifier modelId) {
      return modelId == null ? false : GEO_MODEL_CACHE.computeIfAbsent(modelId, GeckoResourceResolver::findGeoModel);
   }

   private static boolean findGeoModel(Identifier modelId) {
      if (GeckoLibResources.getBakedModels().cache().containsKey(modelId)) {
         return true;
      }

      Identifier geoPath = geoModelPath(modelId);
      return resourceExists(geoPath);
   }

   private static String nestedAnimationPath(String animationPath, String suffix) {
      String folder = animationFolderPath(animationPath);
      String file = suffix != null && !suffix.isBlank() ? animationPath + "_" + suffix : animationPath;
      return "afw/" + folder + "/" + file;
   }

   private static String animationFolderPath(String animationPath) {
      if (animationPath != null && !animationPath.isEmpty()) {
         int stageIndex = animationPath.lastIndexOf(".p");
         if (stageIndex > 0 && stageIndex + 2 < animationPath.length()) {
            for (int i = stageIndex + 2; i < animationPath.length(); i++) {
               char c = animationPath.charAt(i);
               if (c < '0' || c > '9') {
                  return animationPath;
               }
            }

            return animationPath.substring(0, stageIndex);
         } else {
            return animationPath;
         }
      } else {
         return animationPath;
      }
   }

   private static Identifier geoModelPath(Identifier modelId) {
      return Identifier.of(modelId.getNamespace(), "geckolib/models/" + modelId.getPath() + ".geo.json");
   }

   private static boolean resourceExists(Identifier resourceId) {
      return resourceId == null ? false : RESOURCE_EXISTS_CACHE.computeIfAbsent(resourceId, GeckoResourceResolver::findResource);
   }

   private static boolean findResource(Identifier resourceId) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return false;
      }

      try {
         return client.getResourceManager().getResource(resourceId).isPresent();
      } catch (RuntimeException ex) {
         if (resourceId != null && LOGGED_RESOURCE_LOOKUP_ERRORS.add(resourceId.toString())) {
            AnimationEngine.LOGGER.debug("[NoN Animation] Resource lookup failed for {}", resourceId, ex);
         }

         return false;
      }
   }

   private static GeckoResourceResolver.ModelAndTexture missingWithLog(Identifier typeId) {
      if (typeId != null && LOGGED_MISSING_TYPES.add(typeId.toString())) {
         GeckoLibConstants.LOGGER.info("[NoN Animation] No specific gecko model for entity type {}, using missingmodel.", typeId);
      }

      return new GeckoResourceResolver.ModelAndTexture(DEFAULT_MISSING_MODEL, DEFAULT_MISSING_TEXTURE, true, null);
   }

   public static void clearResourceCaches() {
      ANIMATION_RESOURCE_CACHE.clear();
      ANIMATION_KEY_CACHE.clear();
      FIRST_ANIMATION_KEY_CACHE.clear();
      MODEL_AND_TEXTURE_CACHE.clear();
      GEO_MODEL_CACHE.clear();
      RESOURCE_EXISTS_CACHE.clear();
      LOGGED_MISSING_TYPES.clear();
      LOGGED_MISSING_ANIMATIONS.clear();
      LOGGED_MISSING_ANIMATION_KEYS.clear();
      LOGGED_RESOURCE_LOOKUP_ERRORS.clear();
   }

   private record AnimationKeyKey(Identifier animationResource, Identifier animationId, String actorKey, Identifier entityTypeId) {
   }

   private record AnimationResourceKey(Identifier animationId, String actorKey, Identifier entityTypeId) {
   }

   private record AnimationResourceResolution(Identifier resource, boolean warnableMissing) {
   }

   public record ModelAndTexture(Identifier model, Identifier texture, boolean missingModel, @Nullable Identifier modelRoot) {
   }

   private record ModelKey(Identifier entityTypeId, List<Identifier> modelRoots, List<String> modelPaths, GenderedModelResolver.ModelGender gender) {
   }

   private record ResolvedModel(Identifier model, @Nullable Identifier modelRoot) {
   }
}
