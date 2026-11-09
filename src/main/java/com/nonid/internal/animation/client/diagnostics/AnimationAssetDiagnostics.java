package com.nonid.internal.animation.client.diagnostics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nonid.NonDebugChatCategory;
import com.nonid.client.NonClientDiagnostics;
import com.nonid.internal.animation.AnimationEngine;
import com.nonid.internal.animation.client.render.gecko.GeckoResourceResolver;
import com.nonid.internal.animation.data.AnimationDefinitions;
import com.nonid.internal.animation.network.AnimationStageInfo;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class AnimationAssetDiagnostics {
   private static final Identifier RELOADER_ID = AnimationEngine.runtimeId("asset_diagnostics");
   private static final double LENGTH_WARNING_TOLERANCE_SECONDS = 0.05;
   private static final Pattern CONJOINED_ACTOR_KEY = Pattern.compile("^(.+\\.p\\d+)_(.+)$");
   private static volatile Map<Identifier, Map<String, AnimationAssetDiagnostics.AnimationAssetLength>> LENGTHS_BY_RESOURCE = Map.of();
   private static volatile List<String> RESOURCE_WARNINGS = List.of();
   private static final Set<String> WARNED_MISMATCHES = new LinkedHashSet<>();
   private static final Set<String> WARNED_RESOURCE_CHAT = new LinkedHashSet<>();
   private static long reloadRevision = 0L;
   private static String lastDefinitionValidationKey = "";

   private AnimationAssetDiagnostics() {
   }

   public static void registerReloadListener() {
      ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(RELOADER_ID, new AnimationAssetDiagnostics.Reloader());
   }

   public static void validateLoadedDefinitionsOnce() {
      sendResourceWarningsToChat();
      List<AnimationDefinitions.Definition> definitions = AnimationDefinitions.getLoadedDefinitionsSnapshot();
      if (!definitions.isEmpty() && !LENGTHS_BY_RESOURCE.isEmpty()) {
         String validationKey = buildDefinitionValidationKey(definitions);
         if (!validationKey.equals(lastDefinitionValidationKey)) {
            lastDefinitionValidationKey = validationKey;

            for (AnimationDefinitions.Definition definition : definitions) {
               validateDefinition(definition);
            }
         }
      }
   }

   public static void validateStartPayload(Identifier animationId, List<String> actorKeys, List<AnimationStageInfo> stages) {
      if (animationId != null && stages != null && !stages.isEmpty() && !LENGTHS_BY_RESOURCE.isEmpty()) {
         List<String> safeActorKeys = actorKeys == null ? List.of() : actorKeys;

         for (AnimationStageInfo stage : stages) {
            if (stage != null) {
               if (safeActorKeys.isEmpty()) {
                  validateStageResource(animationId, stage, null, List.of());
               } else {
                  for (String actorKey : safeActorKeys) {
                     validateStageResource(animationId, stage, actorKey, List.of());
                  }
               }
            }
         }
      }
   }

   private static void validateDefinition(AnimationDefinitions.Definition definition) {
      if (definition != null && !definition.stages().isEmpty()) {
         List<String> actorKeys = AnimationDefinitions.resolveActorKeys(definition);
         List<AnimationDefinitions.ActorConstraint> actors = definition.actors();

         for (AnimationStageInfo stage : definition.stages()) {
            if (stage != null) {
               int count = Math.max(actorKeys.size(), actors.size());
               if (count == 0) {
                  validateStageResource(definition.id(), stage, null, List.of());
               } else {
                  for (int i = 0; i < count; i++) {
                     String actorKey = i < actorKeys.size() ? actorKeys.get(i) : null;
                     AnimationDefinitions.ActorConstraint actor = i < actors.size() ? actors.get(i) : null;
                     List<Identifier> entityTypes = actor == null ? List.of() : sortedEntityTypes(actor.entityTypes());
                     validateStageResource(definition.id(), stage, actorKey, entityTypes);
                  }
               }
            }
         }
      }
   }

   private static void validateStageResource(Identifier definitionId, AnimationStageInfo stage, @Nullable String actorKey, List<Identifier> entityTypes) {
      if (definitionId != null && stage != null && stage.lengthTicks() > 0L) {
         Identifier playbackId = stage.effectiveAnimationId();
         if (playbackId != null) {
            if (entityTypes != null && !entityTypes.isEmpty()) {
               for (Identifier entityType : entityTypes) {
                  if (entityType != null) {
                     validateResolvedResource(definitionId, stage, actorKey, entityType);
                  }
               }
            } else {
               validateResolvedResource(definitionId, stage, actorKey, null);
            }
         }
      }
   }

   private static void validateResolvedResource(Identifier definitionId, AnimationStageInfo stage, @Nullable String actorKey, @Nullable Identifier entityType) {
      Identifier playbackId = stage.effectiveAnimationId();
      Identifier animationResource = GeckoResourceResolver.resolveAnimationResource(playbackId, actorKey, entityType, false);
      if (animationResource != null) {
         String animationKey = GeckoResourceResolver.resolveAnimationKey(animationResource, playbackId, actorKey, entityType, false);
         if (animationKey != null) {
            validateIfPresent(definitionId, stage, actorKey, animationResource, animationKey);
         }
      }
   }

   private static boolean validateIfPresent(
      Identifier definitionId, AnimationStageInfo stage, @Nullable String actorKey, @Nullable Identifier animationResource, @Nullable String animationKey
   ) {
      if (animationResource != null && animationKey != null) {
         Map<String, AnimationAssetDiagnostics.AnimationAssetLength> resourceAnimations = LENGTHS_BY_RESOURCE.get(animationResource);
         AnimationAssetDiagnostics.AnimationAssetLength asset = resourceAnimations == null ? null : resourceAnimations.get(animationKey);
         if (asset != null && !(asset.lengthSeconds() <= 0.0)) {
            double stageSeconds = stageCycleSeconds(stage);
            double delta = Math.abs(stageSeconds - asset.lengthSeconds());
            if (delta > 0.05) {
               warnMismatch(definitionId, stage, actorKey, animationResource, asset, stageSeconds);
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static void warnMismatch(
      Identifier definitionId,
      AnimationStageInfo stage,
      @Nullable String actorKey,
      Identifier animationResource,
      AnimationAssetDiagnostics.AnimationAssetLength asset,
      double stageSeconds
   ) {
      String key = definitionId + "|" + stage.animationId() + "|" + actorKey + "|" + animationResource + "|" + asset.animationKey();
      if (WARNED_MISMATCHES.add(key)) {
         String actorText = actorKey != null && !actorKey.isBlank() ? "actor '" + actorKey + "'" : "base";
         String playbackText = stage.animationId().equals(stage.effectiveAnimationId()) ? "" : " (plays " + stage.effectiveAnimationId() + ")";
         String message = String.format(
            Locale.ROOT,
            "%s stage %s%s declares cycle_seconds=%ss, but %s animation %s key '%s' has animation_length=%ss. cycle_seconds should match the authored GeckoLib clip length; use stage speed to slow/accelerate playback.",
            definitionId,
            stage.animationId(),
            playbackText,
            formatSeconds(stageSeconds),
            actorText,
            animationResource,
            asset.animationKey(),
            formatSeconds(asset.lengthSeconds())
         );
         AnimationEngine.LOGGER.warn("[NoN Animation] Setup warning: {}", message);
         sendSetupChatWarning(message);
      }
   }

   private static void sendSetupChatWarning(String message) {
      if (message != null && !message.isBlank()) {
         NonClientDiagnostics.send(NonDebugChatCategory.SETUP, Text.literal("[NoN Animation] Setup warning: " + message), false);
      }
   }

   private static void sendResourceWarningsToChat() {
      for (String warning : RESOURCE_WARNINGS) {
         if (warning != null && WARNED_RESOURCE_CHAT.add(warning)) {
            sendSetupChatWarning(warning);
         }
      }
   }

   private static List<Identifier> sortedEntityTypes(Set<Identifier> entityTypes) {
      if (entityTypes != null && !entityTypes.isEmpty()) {
         List<Identifier> out = new ArrayList<>(entityTypes);
         out.sort(Comparator.comparing(Identifier::toString));
         return List.copyOf(out);
      } else {
         return List.of();
      }
   }

   private static String buildDefinitionValidationKey(List<AnimationDefinitions.Definition> definitions) {
      StringBuilder sb = new StringBuilder();
      sb.append(reloadRevision);

      for (AnimationDefinitions.Definition definition : definitions) {
         if (definition != null) {
            sb.append('|').append(definition.id());

            for (AnimationStageInfo stage : definition.stages()) {
               if (stage != null) {
                  sb.append(':')
                     .append(stage.animationId())
                     .append("->")
                     .append(stage.effectiveAnimationId())
                     .append('@')
                     .append(stage.lengthTicks())
                     .append('~')
                     .append(stage.cycleTicks())
                     .append('/')
                     .append(stage.cycleMidpointOffsetSeconds());
               }
            }
         }
      }

      return sb.toString();
   }

   private static void reload(ResourceManager manager) {
      Map<Identifier, Resource> resources = manager.findResources("geckolib/animations", id -> id.getPath().endsWith(".animation.json"));
      List<Entry<Identifier, Resource>> entries = new ArrayList<>(resources.entrySet());
      entries.sort(Comparator.comparing(ex -> ((Identifier)ex.getKey()).toString()));
      Map<Identifier, Map<String, AnimationAssetDiagnostics.AnimationAssetLength>> loaded = new LinkedHashMap<>();
      List<String> resourceWarnings = new ArrayList<>(GeckoModelCopyDiagnostics.scan(manager));

      for (String warning : resourceWarnings) {
         AnimationEngine.LOGGER.warn("[NoN Animation] Setup warning: {}", warning);
      }

      for (Entry<Identifier, Resource> entry : entries) {
         Identifier fileId = entry.getKey();
         Identifier animationResource = animationIdFromPath(fileId);
         if (animationResource != null) {
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
               JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
               JsonObject animationsObj = root.has("animations") && root.get("animations").isJsonObject() ? root.getAsJsonObject("animations") : null;
               if (animationsObj != null && !animationsObj.isEmpty()) {
                  Map<String, AnimationAssetDiagnostics.AnimationAssetLength> resourceLengths = new LinkedHashMap<>();
                  Map<String, List<String>> soundOwnersByStage = new LinkedHashMap<>();

                  for (Entry<String, JsonElement> animationEntry : animationsObj.entrySet()) {
                     String animationKey = animationEntry.getKey();
                     JsonElement animationEl = animationEntry.getValue();
                     if (animationKey != null && !animationKey.isBlank() && animationEl != null && animationEl.isJsonObject()) {
                        JsonObject animationObj = animationEl.getAsJsonObject();
                        double lengthSeconds = readAnimationLength(animationObj);
                        if (lengthSeconds > 0.0) {
                           resourceLengths.put(animationKey, new AnimationAssetDiagnostics.AnimationAssetLength(animationKey, lengthSeconds));
                        }

                        Matcher actorKeyMatcher = CONJOINED_ACTOR_KEY.matcher(animationKey);
                        if (actorKeyMatcher.matches() && hasNonEmptyObject(animationObj, "sound_effects")) {
                           soundOwnersByStage.computeIfAbsent(actorKeyMatcher.group(1), ignored -> new ArrayList<>()).add(animationKey);
                        }
                     }
                  }

                  if (!resourceLengths.isEmpty()) {
                     loaded.put(animationResource, Map.copyOf(resourceLengths));
                  }

                  for (Entry<String, List<String>> owners : soundOwnersByStage.entrySet()) {
                     if (owners.getValue().size() > 1) {
                        String warning = "Conjoined animation "
                           + animationResource
                           + " stage '"
                           + owners.getKey()
                           + "' contains sound_effects on multiple actor clips "
                           + owners.getValue()
                           + ". Keep the sound track on exactly one actor clip.";
                        resourceWarnings.add(warning);
                        AnimationEngine.LOGGER.warn("[NoN Animation] Setup warning: {}", warning);
                     }
                  }
               }
            } catch (IOException | RuntimeException e) {
               AnimationEngine.LOGGER.debug("[NoN Animation] Failed to read animation length from {}", fileId, e);
            }
         }
      }

      LENGTHS_BY_RESOURCE = Map.copyOf(loaded);
      RESOURCE_WARNINGS = List.copyOf(resourceWarnings);
      WARNED_MISMATCHES.clear();
      WARNED_RESOURCE_CHAT.clear();
      lastDefinitionValidationKey = "";
      reloadRevision++;
   }

   private static double stageCycleSeconds(AnimationStageInfo stage) {
      if (stage == null) {
         return 0.0;
      } else {
         double exactTicks = stage.cycleTicks();
         if (Double.isFinite(exactTicks) && exactTicks > 0.0) {
            return exactTicks / 20.0;
         } else {
            return stage.lengthTicks() > 0L ? stage.lengthTicks() / 20.0 : 0.0;
         }
      }
   }

   private static double readAnimationLength(JsonObject animObj) {
      Double animationLength = readFiniteDouble(animObj.get("animation_length"));
      if (animationLength != null) {
         return animationLength;
      }

      Double length = readFiniteDouble(animObj.get("length"));
      return length != null ? length : -1.0;
   }

   private static boolean hasNonEmptyObject(JsonObject object, String key) {
      return object != null && key != null && object.has(key) && object.get(key).isJsonObject() ? !object.getAsJsonObject(key).isEmpty() : false;
   }

   private static Double readFiniteDouble(JsonElement element) {
      if (element != null && element.isJsonPrimitive()) {
         JsonPrimitive primitive = element.getAsJsonPrimitive();
         if (primitive.isNumber()) {
            double value = primitive.getAsDouble();
            return Double.isFinite(value) ? value : null;
         }

         if (!primitive.isString()) {
            return null;
         }

         String raw = primitive.getAsString();
         if (raw != null && !raw.isBlank()) {
            try {
               double value = Double.parseDouble(raw.trim());
               return Double.isFinite(value) ? value : null;
            } catch (NumberFormatException ignored) {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static Identifier animationIdFromPath(Identifier fileId) {
      String path = fileId.getPath();
      String prefix = "geckolib/animations/";
      String suffix = ".animation.json";
      if (path.startsWith(prefix) && path.endsWith(suffix)) {
         String animPath = path.substring(prefix.length(), path.length() - suffix.length());
         return Identifier.of(fileId.getNamespace(), animPath);
      } else {
         return null;
      }
   }

   private static String formatSeconds(double seconds) {
      return String.format(Locale.ROOT, "%.3f", seconds);
   }

   private record AnimationAssetLength(String animationKey, double lengthSeconds) {
   }

   public static final class Reloader implements SynchronousResourceReloader {
      public void reload(ResourceManager manager) {
         AnimationAssetDiagnostics.reload(manager);
      }
   }
}
