package com.nonid.internal.animation.client.render.gecko;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nonid.internal.animation.AnimationEngine;
import com.nonid.internal.animation.AnimationEngineClient;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;

public final class BoneTextureOverrides {
   private static volatile Map<Identifier, Map<String, Identifier>> BONE_TEXTURES_BY_MODEL = Map.of();
   private static volatile Map<Identifier, List<Identifier>> EMISSIVE_TEXTURES_BY_MODEL = Map.of();
   private static volatile Map<Identifier, Map<String, Identifier>> EMISSIVE_BONE_TEXTURES_BY_MODEL = Map.of();
   private static volatile Map<Identifier, Map<String, BoneTextureOverrides.ModelLocator>> LOCATORS_BY_MODEL = Map.of();
   private static volatile Map<Identifier, BoneTextureOverrides.RenderSettings> RENDER_SETTINGS_BY_MODEL = Map.of();
   private static final Identifier RELOADER_ID = AnimationEngine.runtimeId("bone_textures");

   private BoneTextureOverrides() {
   }

   public static void registerReloadListener() {
      ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(RELOADER_ID, new BoneTextureOverrides.Reloader());
   }

   public static Map<String, Identifier> getBoneTextures(Identifier modelId) {
      return modelId == null ? null : BONE_TEXTURES_BY_MODEL.get(modelId);
   }

   public static List<Identifier> getEmissiveTextures(Identifier modelId) {
      return modelId == null ? null : EMISSIVE_TEXTURES_BY_MODEL.get(modelId);
   }

   public static Map<String, Identifier> getEmissiveBoneTextures(Identifier modelId) {
      return modelId == null ? null : EMISSIVE_BONE_TEXTURES_BY_MODEL.get(modelId);
   }

   public static Map<String, BoneTextureOverrides.ModelLocator> getLocators(Identifier modelId) {
      return modelId == null ? null : LOCATORS_BY_MODEL.get(modelId);
   }

   public static boolean isTranslucent(Identifier modelId) {
      if (modelId == null) {
         return false;
      }

      BoneTextureOverrides.RenderSettings settings = RENDER_SETTINGS_BY_MODEL.get(modelId);
      return settings != null && settings.translucent();
   }

   public static Set<String> getTranslucentBones(Identifier modelId) {
      if (modelId == null) {
         return Set.of();
      }

      BoneTextureOverrides.RenderSettings settings = RENDER_SETTINGS_BY_MODEL.get(modelId);
      return settings == null ? Set.of() : settings.translucentBones();
   }

   private static void reload(ResourceManager manager) {
      Map<Identifier, Resource> resources = manager.findResources("geckolib/models", id -> id.getPath().endsWith(".geo.json"));
      List<Entry<Identifier, Resource>> entries = new ArrayList<>(resources.entrySet());
      entries.sort(Comparator.comparing(ex -> ((Identifier)ex.getKey()).toString()));
      Map<Identifier, Map<String, Identifier>> loaded = new LinkedHashMap<>();
      Map<Identifier, List<Identifier>> loadedEmissive = new LinkedHashMap<>();
      Map<Identifier, Map<String, Identifier>> loadedEmissiveBones = new LinkedHashMap<>();
      Map<Identifier, Map<String, BoneTextureOverrides.ModelLocator>> loadedLocators = new LinkedHashMap<>();
      Map<Identifier, BoneTextureOverrides.RenderSettings> loadedRenderSettings = new LinkedHashMap<>();

      for (Entry<Identifier, Resource> entry : entries) {
         Identifier fileId = entry.getKey();
         Identifier modelId = modelIdFromGeoPath(fileId);
         if (modelId != null) {
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
               JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
               Map<String, Identifier> boneTextures = readBoneTextures(obj, fileId);
               if (boneTextures != null && !boneTextures.isEmpty()) {
                  loaded.put(modelId, Map.copyOf(boneTextures));
               }

               List<Identifier> emissiveTextures = readEmissiveTextures(obj, fileId);
               if (emissiveTextures != null && !emissiveTextures.isEmpty()) {
                  loadedEmissive.put(modelId, List.copyOf(emissiveTextures));
               }

               Map<String, Identifier> emissiveBoneTextures = readTextureMap(obj, fileId, "afw_emissive_bone_textures");
               if (emissiveBoneTextures != null && !emissiveBoneTextures.isEmpty()) {
                  loadedEmissiveBones.put(modelId, Map.copyOf(emissiveBoneTextures));
               }

               Map<String, BoneTextureOverrides.ModelLocator> locators = readLocators(obj);
               if (locators != null && !locators.isEmpty()) {
                  loadedLocators.put(modelId, Map.copyOf(locators));
               }

               BoneTextureOverrides.RenderSettings renderSettings = readRenderSettings(obj, fileId);
               if (renderSettings != null && !renderSettings.isDefault()) {
                  loadedRenderSettings.put(modelId, renderSettings);
               }
            } catch (IOException | RuntimeException e) {
               setupError("[NoN Animation] Failed to load Gecko model render metadata {}", fileId, e);
            }
         }
      }

      BONE_TEXTURES_BY_MODEL = Map.copyOf(loaded);
      EMISSIVE_TEXTURES_BY_MODEL = Map.copyOf(loadedEmissive);
      EMISSIVE_BONE_TEXTURES_BY_MODEL = Map.copyOf(loadedEmissiveBones);
      LOCATORS_BY_MODEL = Map.copyOf(loadedLocators);
      RENDER_SETTINGS_BY_MODEL = Map.copyOf(loadedRenderSettings);
   }

   private static Map<String, Identifier> readBoneTextures(JsonObject obj, Identifier fileId) {
      return readTextureMap(obj, fileId, "afw_bone_textures");
   }

   private static Map<String, Identifier> readTextureMap(JsonObject obj, Identifier fileId, String key) {
      if (!obj.has(key)) {
         return null;
      }

      JsonElement el = obj.get(key);
      if (el != null && el.isJsonObject()) {
         JsonObject bonesObj = el.getAsJsonObject();
         Map<String, Identifier> out = new LinkedHashMap<>();

         for (Entry<String, JsonElement> boneEntry : bonesObj.entrySet()) {
            String boneName = boneEntry.getKey();
            JsonElement textureEl = boneEntry.getValue();
            if (boneName != null && !boneName.isBlank()) {
               if (textureEl != null && textureEl.isJsonPrimitive()) {
                  String textureRaw = textureEl.getAsString();
                  Identifier textureId = Identifier.tryParse(textureRaw);
                  if (textureId == null) {
                     setupWarn("[NoN Animation] Skipping {}: invalid texture id '{}' for bone '{}' in '{}'", fileId, textureRaw, boneName, key);
                  } else {
                     out.put(boneName, textureId);
                  }
               } else {
                  setupWarn("[NoN Animation] Skipping {}: invalid texture for bone '{}' in '{}'", fileId, boneName, key);
               }
            }
         }

         return out;
      } else {
         setupWarn("[NoN Animation] Skipping {}: '{}' must be an object", fileId, key);
         return null;
      }
   }

   private static List<Identifier> readEmissiveTextures(JsonObject obj, Identifier fileId) {
      if (!obj.has("afw_emissive_textures")) {
         return null;
      }

      JsonElement el = obj.get("afw_emissive_textures");
      if (el != null && el.isJsonArray()) {
         List<Identifier> out = new ArrayList<>();

         for (JsonElement textureEl : el.getAsJsonArray()) {
            if (textureEl != null && textureEl.isJsonPrimitive()) {
               String textureRaw = textureEl.getAsString();
               Identifier textureId = Identifier.tryParse(textureRaw);
               if (textureId == null) {
                  setupWarn("[NoN Animation] Skipping {}: invalid emissive texture id '{}'", fileId, textureRaw);
               } else {
                  out.add(textureId);
               }
            } else {
               setupWarn("[NoN Animation] Skipping {}: invalid emissive texture entry", fileId);
            }
         }

         return out;
      } else {
         setupWarn("[NoN Animation] Skipping {}: 'afw_emissive_textures' must be an array", fileId);
         return null;
      }
   }

   private static BoneTextureOverrides.RenderSettings readRenderSettings(JsonObject obj, Identifier fileId) {
      if (!obj.has("afw_render")) {
         return null;
      } else {
         JsonElement el = obj.get("afw_render");
         if (el != null && el.isJsonObject()) {
            JsonObject renderObj = el.getAsJsonObject();
            boolean translucent = readOptionalBoolean(renderObj, "translucent", fileId, "afw_render", false);
            Set<String> translucentBones = readStringSet(renderObj, "translucent_bones", fileId, "afw_render");
            return new BoneTextureOverrides.RenderSettings(translucent, translucentBones);
         } else {
            setupWarn("[NoN Animation] Skipping {}: 'afw_render' must be an object", fileId);
            return null;
         }
      }
   }

   private static Set<String> readStringSet(JsonObject obj, String key, Identifier fileId, String context) {
      if (obj != null && obj.has(key)) {
         JsonElement el = obj.get(key);
         if (el != null && el.isJsonArray()) {
            Set<String> out = new LinkedHashSet<>();

            for (JsonElement entry : el.getAsJsonArray()) {
               if (entry != null && entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                  String boneName = entry.getAsString();
                  if (boneName != null && !boneName.isBlank()) {
                     out.add(boneName);
                  } else {
                     setupWarn("[NoN Animation] Skipping blank bone name in '{}.{}' for {}", context, key, fileId);
                  }
               } else {
                  setupWarn("[NoN Animation] Skipping invalid bone name in '{}.{}' for {}", context, key, fileId);
               }
            }

            return out.isEmpty() ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(out));
         } else {
            setupWarn("[NoN Animation] Skipping {}: '{}.{}' must be an array", fileId, context, key);
            return Set.of();
         }
      } else {
         return Set.of();
      }
   }

   private static boolean readOptionalBoolean(JsonObject obj, String key, Identifier fileId, String context, boolean fallback) {
      if (obj != null && obj.has(key)) {
         JsonElement el = obj.get(key);
         if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) {
            return el.getAsBoolean();
         }

         setupWarn("[NoN Animation] Skipping {}: '{}.{}' must be a boolean", fileId, context, key);
         return fallback;
      } else {
         return fallback;
      }
   }

   private static Map<String, BoneTextureOverrides.ModelLocator> readLocators(JsonObject obj) {
      JsonElement geometryEl = obj.get("minecraft:geometry");
      if (geometryEl != null && geometryEl.isJsonArray()) {
         Map<String, BoneTextureOverrides.ModelLocator> out = new LinkedHashMap<>();

         for (JsonElement geometryEntry : geometryEl.getAsJsonArray()) {
            if (geometryEntry != null && geometryEntry.isJsonObject()) {
               JsonElement bonesEl = geometryEntry.getAsJsonObject().get("bones");
               if (bonesEl != null && bonesEl.isJsonArray()) {
                  for (JsonElement boneEntry : bonesEl.getAsJsonArray()) {
                     if (boneEntry != null && boneEntry.isJsonObject()) {
                        JsonObject boneObj = boneEntry.getAsJsonObject();
                        JsonElement nameEl = boneObj.get("name");
                        if (nameEl != null && nameEl.isJsonPrimitive()) {
                           String boneName = nameEl.getAsString();
                           if (boneName != null && !boneName.isBlank()) {
                              double[] pivot = readVec3(boneObj.get("pivot"));
                              if (pivot == null) {
                                 pivot = new double[]{0.0, 0.0, 0.0};
                              }

                              JsonElement locatorsEl = boneObj.get("locators");
                              if (locatorsEl != null && locatorsEl.isJsonObject()) {
                                 for (Entry<String, JsonElement> locatorEntry : locatorsEl.getAsJsonObject().entrySet()) {
                                    String locatorName = locatorEntry.getKey();
                                    if (locatorName != null && !locatorName.isBlank()) {
                                       double[] locator = readLocatorVec3(locatorEntry.getValue());
                                       if (locator != null) {
                                          out.put(
                                             locatorName,
                                             new BoneTextureOverrides.ModelLocator(boneName, locator[0], locator[1], locator[2], pivot[0], pivot[1], pivot[2])
                                          );
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         return out;
      } else {
         return null;
      }
   }

   private static double[] readLocatorVec3(JsonElement el) {
      if (el == null) {
         return null;
      } else if (el.isJsonArray()) {
         return readVec3(el);
      } else {
         return el.isJsonObject() ? readVec3(el.getAsJsonObject().get("offset")) : null;
      }
   }

   private static double[] readVec3(JsonElement el) {
      if (el != null && el.isJsonArray()) {
         JsonArray array = el.getAsJsonArray();
         return array.size() < 3 ? null : new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
      } else {
         return null;
      }
   }

   private static Identifier modelIdFromGeoPath(Identifier fileId) {
      String path = fileId.getPath();
      String prefix = "geckolib/models/";
      String suffix = ".geo.json";
      if (path.startsWith(prefix) && path.endsWith(suffix)) {
         String modelPath = path.substring(prefix.length(), path.length() - suffix.length());
         return Identifier.of(fileId.getNamespace(), modelPath);
      } else {
         return null;
      }
   }

   private static void setupWarn(String template, Object... args) {
      AnimationEngine.LOGGER.warn(template, args);
      AnimationEngineClient.sendClientSetupWarning(AnimationEngine.formatLogTemplate(template, args));
   }

   private static void setupError(String template, Object... args) {
      AnimationEngine.LOGGER.error(template, args);
      AnimationEngineClient.sendClientSetupWarning(AnimationEngine.formatLogTemplate(template, args));
   }

   public record ModelLocator(String parentBone, double x, double y, double z, double parentPivotX, double parentPivotY, double parentPivotZ) {
   }

   public static final class Reloader implements SynchronousResourceReloader {
      public void reload(ResourceManager manager) {
         BoneTextureOverrides.reload(manager);
      }
   }

   private record RenderSettings(boolean translucent, Set<String> translucentBones) {
      private boolean isDefault() {
         return !this.translucent && this.translucentBones.isEmpty();
      }
   }
}
