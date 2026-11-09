package com.nonid.internal.animation.client.diagnostics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nonid.internal.animation.AnimationEngine;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

final class GeckoModelCopyDiagnostics {
   private static final Identifier DEFAULT_PACK_ID = Identifier.of("needsofnature", "default_pack");
   private static final String MODEL_SCAN_PREFIX = "geckolib/models";
   private static final String SHARED_ENTITY_PREFIX = "geckolib/models/entity/";
   private static final String LOCAL_ENTITY_PREFIX = "geckolib/models/%s/entity/";
   private static final String GEO_SUFFIX = ".geo.json";
   private static final int MAX_LISTED_MODELS = 8;

   private GeckoModelCopyDiagnostics() {
   }

   static List<String> scan(ResourceManager manager) {
      if (manager == null) {
         return List.of();
      }

      List<GeckoModelCopyDiagnostics.PackDescriptor> packs = readPackDescriptors(manager);
      List<GeckoModelCopyDiagnostics.PackDescriptor> defaultPacks = packs.stream().filter(packx -> DEFAULT_PACK_ID.equals(packx.logicalId())).toList();
      if (defaultPacks.isEmpty()) {
         return List.of();
      }

      Map<String, List<JsonElement>> defaultModels = new LinkedHashMap<>();

      for (GeckoModelCopyDiagnostics.PackDescriptor defaultPack : defaultPacks) {
         for (GeckoModelCopyDiagnostics.ModelFile model : readModels(defaultPack, null)) {
            defaultModels.computeIfAbsent(model.location().key(), ignored -> new ArrayList<>()).add(model.json());
         }
      }

      if (defaultModels.isEmpty()) {
         return List.of();
      }

      Set<String> defaultKeys = Set.copyOf(defaultModels.keySet());
      List<String> warnings = new ArrayList<>();

      for (GeckoModelCopyDiagnostics.PackDescriptor pack : packs) {
         if (!DEFAULT_PACK_ID.equals(pack.logicalId())) {
            LinkedHashSet<String> duplicates = new LinkedHashSet<>();

            for (GeckoModelCopyDiagnostics.ModelFile model : readModels(pack, defaultKeys)) {
               List<JsonElement> defaults = defaultModels.get(model.location().key());
               if (defaults != null && !defaults.stream().noneMatch(defaultModel -> modelsEqual(defaultModel, model.json()))) {
                  duplicates.add(model.location().displayName());
               }
            }

            if (!duplicates.isEmpty()) {
               warnings.add(buildWarning(pack, duplicates));
            }
         }
      }

      return warnings.isEmpty() ? List.of() : List.copyOf(warnings);
   }

   static boolean modelJsonEquals(String left, String right) {
      if (left != null && right != null) {
         try {
            JsonElement leftJson = JsonParser.parseString(left);
            JsonElement rightJson = JsonParser.parseString(right);
            return modelsEqual(leftJson, rightJson);
         } catch (RuntimeException ignored) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean modelsEqual(JsonElement left, JsonElement right) {
      return left != null && right != null && left.isJsonObject() && right.isJsonObject() && left.equals(right);
   }

   @Nullable
   static GeckoModelCopyDiagnostics.ModelLocation classifyModel(Identifier resourceId, @Nullable Identifier logicalPackId) {
      if (resourceId != null && resourceId.getPath().endsWith(".geo.json")) {
         String path = resourceId.getPath();
         if ("animationframework".equals(resourceId.getNamespace()) && path.startsWith("geckolib/models/entity/")) {
            return location(path.substring("geckolib/models/entity/".length()), GeckoModelCopyDiagnostics.ModelScope.SHARED);
         } else if (logicalPackId != null && logicalPackId.getNamespace().equals(resourceId.getNamespace())) {
            String localPrefix = "geckolib/models/%s/entity/".formatted(logicalPackId.getPath());
            return path.startsWith(localPrefix) ? location(path.substring(localPrefix.length()), GeckoModelCopyDiagnostics.ModelScope.LOCAL) : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static GeckoModelCopyDiagnostics.ModelLocation location(String relativePath, GeckoModelCopyDiagnostics.ModelScope scope) {
      if (relativePath != null && relativePath.endsWith(".geo.json")) {
         String key = relativePath.substring(0, relativePath.length() - ".geo.json".length());
         return key.isBlank() ? null : new GeckoModelCopyDiagnostics.ModelLocation(key, scope);
      } else {
         return null;
      }
   }

   private static List<GeckoModelCopyDiagnostics.PackDescriptor> readPackDescriptors(ResourceManager manager) {
      try (Stream<ResourcePack> stream = manager.streamResourcePacks()) {
         return stream.filter(pack -> pack != null).map(GeckoModelCopyDiagnostics::describePack).toList();
      } catch (RuntimeException e) {
         AnimationEngine.LOGGER.debug("[NoN Animation] Failed to inspect resource packs for duplicate GeckoLib models.", e);
         return List.of();
      }
   }

   private static GeckoModelCopyDiagnostics.PackDescriptor describePack(ResourcePack pack) {
      String runtimeId = pack.getId() != null && !pack.getId().isBlank() ? pack.getId() : "unknown";
      Identifier logicalId = null;
      String name = null;

      try {
         InputSupplier<InputStream> supplier = pack.openRoot(new String[]{"pack.mcmeta"});
         if (supplier != null) {
            try (
               InputStream input = (InputStream)supplier.get();
               InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            ) {
               JsonElement parsed = JsonParser.parseReader(reader);
               if (parsed.isJsonObject()) {
                  JsonObject root = parsed.getAsJsonObject();
                  JsonElement metadataElement = root.get("animationframework");
                  if (metadataElement != null && metadataElement.isJsonObject()) {
                     JsonObject metadata = metadataElement.getAsJsonObject();
                     logicalId = readIdentifier(metadata, "id");
                     name = readString(metadata, "name");
                  }
               }
            }
         }
      } catch (IOException | RuntimeException e) {
         AnimationEngine.LOGGER.debug("[NoN Animation] Failed to read pack identity for {}.", runtimeId, e);
      }

      return new GeckoModelCopyDiagnostics.PackDescriptor(pack, runtimeId, logicalId, name);
   }

   private static List<GeckoModelCopyDiagnostics.ModelFile> readModels(GeckoModelCopyDiagnostics.PackDescriptor descriptor, @Nullable Set<String> acceptedKeys) {
      List<GeckoModelCopyDiagnostics.ModelFile> models = new ArrayList<>();
      ResourcePack pack = descriptor.pack();

      try {
         List<String> namespaces = new ArrayList<>(pack.getNamespaces(ResourceType.CLIENT_RESOURCES));
         namespaces.sort(String::compareTo);

         for (String namespace : namespaces) {
            pack.findResources(
               ResourceType.CLIENT_RESOURCES,
               namespace,
               "geckolib/models",
               (resourceId, supplier) -> readModel(descriptor, resourceId, supplier, acceptedKeys, models)
            );
         }
      } catch (RuntimeException e) {
         AnimationEngine.LOGGER.debug("[NoN Animation] Failed to scan GeckoLib models in {}.", descriptor.runtimeId(), e);
      }

      models.sort(Comparator.comparing(model -> model.resourceId().toString()));
      return List.copyOf(models);
   }

   private static void readModel(
      GeckoModelCopyDiagnostics.PackDescriptor descriptor,
      Identifier resourceId,
      InputSupplier<InputStream> supplier,
      @Nullable Set<String> acceptedKeys,
      List<GeckoModelCopyDiagnostics.ModelFile> output
   ) {
      GeckoModelCopyDiagnostics.ModelLocation location = classifyModel(resourceId, descriptor.logicalId());
      if (location != null && (acceptedKeys == null || acceptedKeys.contains(location.key()))) {
         try (
            InputStream input = (InputStream)supplier.get();
            InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
         ) {
            JsonElement json = JsonParser.parseReader(reader);
            if (json.isJsonObject()) {
               output.add(new GeckoModelCopyDiagnostics.ModelFile(resourceId, location, json));
            }
         } catch (IOException | RuntimeException e) {
            AnimationEngine.LOGGER.debug("[NoN Animation] Failed to compare GeckoLib model {} from {}.", new Object[]{resourceId, descriptor.runtimeId(), e});
         }
      }
   }

   private static String buildWarning(GeckoModelCopyDiagnostics.PackDescriptor pack, Set<String> duplicateModels) {
      List<String> sorted = duplicateModels.stream().sorted().toList();
      int shown = Math.min(8, sorted.size());
      String models = String.join(", ", sorted.subList(0, shown));
      if (shown < sorted.size()) {
         models = models + ", +" + (sorted.size() - shown) + " more";
      }

      return "Pack '"
         + pack.displayName()
         + "' contains GeckoLib actor models identical to the NoN default pack: "
         + models
         + ". Remove these unchanged copies and let NoN use the maintained default models through fallback; copied defaults can become incompatible after updates.";
   }

   @Nullable
   private static Identifier readIdentifier(JsonObject object, String key) {
      String raw = readString(object, key);
      return raw != null && raw.indexOf(58) > 0 ? Identifier.tryParse(raw) : null;
   }

   @Nullable
   private static String readString(JsonObject object, String key) {
      if (object != null && key != null) {
         JsonElement element = object.get(key);
         if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString().trim();
            return value.isEmpty() ? null : value;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private record ModelFile(Identifier resourceId, GeckoModelCopyDiagnostics.ModelLocation location, JsonElement json) {
   }

   record ModelLocation(String key, GeckoModelCopyDiagnostics.ModelScope scope) {
      String displayName() {
         return this.key + " (" + this.scope.label + ")";
      }
   }

   enum ModelScope {
      LOCAL("local"),
      SHARED("shared");

      private final String label;

      ModelScope(String label) {
         this.label = label;
      }
   }

   private record PackDescriptor(ResourcePack pack, String runtimeId, @Nullable Identifier logicalId, @Nullable String name) {
      String displayName() {
         String identity = this.logicalId == null ? this.runtimeId : this.logicalId.toString();
         return this.name != null && !this.name.equals(identity) ? this.name + " (" + identity + ")" : identity;
      }
   }
}
