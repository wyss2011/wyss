package com.nonid.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nonid.NeedsOfNature;
import com.nonid.client.NonLiquidColors;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonLiquidGainOverrides {
   private static final String ROOT_PATH = "non_liquid_gains";
   private static final String ENTRIES_KEY = "entries";
   private static final String ENTITY_KEY = "entity";
   private static final String GAIN_ML_KEY = "gain_ml";
   private static final String COLOR_KEY = "color";
   private static final String MIXED_COLOR_KEY = "mixed_color";
   private static volatile Map<Identifier, Integer> GAIN_BY_ENTITY = Map.of();
   private static volatile Map<Identifier, String> COLOR_HEX_BY_ENTITY = Map.of();
   @Nullable
   private static volatile String MIXED_COLOR_HEX = null;

   private NonLiquidGainOverrides() {
   }

   public static void registerReloadListener() {
      ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(NonLiquidGainOverrides.Reloader.RELOADER_ID, new NonLiquidGainOverrides.Reloader());
   }

   @Nullable
   public static Integer getGain(@Nullable Identifier entityTypeId) {
      return entityTypeId == null ? null : GAIN_BY_ENTITY.get(entityTypeId);
   }

   @Nullable
   public static Integer getColorRgb(@Nullable Identifier entityTypeId) {
      if (entityTypeId == null) {
         return null;
      }

      String hex = COLOR_HEX_BY_ENTITY.get(entityTypeId);
      return NonLiquidColors.parseHexColor(hex);
   }

   @Nullable
   public static Integer getMixedColorRgb() {
      return NonLiquidColors.parseHexColor(MIXED_COLOR_HEX);
   }

   @Nullable
   public static String getMixedColorHex() {
      return MIXED_COLOR_HEX;
   }

   public static Map<String, Integer> getGainByEntity() {
      if (GAIN_BY_ENTITY.isEmpty()) {
         return Map.of();
      }

      Map<String, Integer> out = new LinkedHashMap<>();

      for (Entry<Identifier, Integer> entry : GAIN_BY_ENTITY.entrySet()) {
         out.put(entry.getKey().toString(), entry.getValue());
      }

      return Map.copyOf(out);
   }

   public static Map<String, String> getColorByEntity() {
      if (COLOR_HEX_BY_ENTITY.isEmpty()) {
         return Map.of();
      }

      Map<String, String> out = new LinkedHashMap<>();

      for (Entry<Identifier, String> entry : COLOR_HEX_BY_ENTITY.entrySet()) {
         out.put(entry.getKey().toString(), entry.getValue());
      }

      return Map.copyOf(out);
   }

   private static void reload(ResourceManager resourceManager) {
      Map<Identifier, Resource> resources = resourceManager.findResources("non_liquid_gains", id -> id.getPath().endsWith(".json"));
      Map<Identifier, Integer> gains = new LinkedHashMap<>();
      Map<Identifier, String> colors = new LinkedHashMap<>();
      String mixedColorHex = null;
      List<Entry<Identifier, Resource>> ordered = new ArrayList<>(resources.entrySet());
      ordered.sort(Comparator.comparing(entryx -> ((Identifier)entryx.getKey()).toString()));

      for (Entry<Identifier, Resource> entry : ordered) {
         Identifier fileId = entry.getKey();

         try (InputStreamReader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String mixedColor = parseOptionalColor(root.get("mixed_color"));
            if (mixedColor != null) {
               mixedColorHex = mixedColor;
            }

            JsonArray entries = asArray(root.get("entries"));
            if (entries != null && !entries.isEmpty()) {
               parseEntries(fileId, entries, gains, colors);
            }
         } catch (IOException | RuntimeException e) {
            NeedsOfNature.LOGGER.debug("[NoN] Failed to read liquid gain override file {}", fileId, e);
         }
      }

      GAIN_BY_ENTITY = Map.copyOf(gains);
      COLOR_HEX_BY_ENTITY = Map.copyOf(colors);
      MIXED_COLOR_HEX = NonLiquidColors.normalizeHexColor(mixedColorHex);
   }

   private static void parseEntries(Identifier fileId, JsonArray entries, Map<Identifier, Integer> gains, Map<Identifier, String> colors) {
      for (int i = 0; i < entries.size(); i++) {
         JsonObject obj = asObject(entries.get(i));
         if (obj == null) {
            warnMalformed(fileId, i, "Entry is not an object.");
         } else {
            Identifier entityId = parseEntityId(obj.get("entity"));
            if (entityId == null) {
               warnMalformed(fileId, i, "Missing or invalid 'entity'.");
            } else {
               Integer gain = parseOptionalInt(obj.get("gain_ml"));
               if (gain != null) {
                  gains.put(entityId, Math.max(0, Math.min(1000, gain)));
               }

               String normalizedHex = parseOptionalColor(obj.get("color"));
               if (normalizedHex != null) {
                  colors.put(entityId, normalizedHex);
               }
            }
         }
      }
   }

   @Nullable
   private static Identifier parseEntityId(@Nullable JsonElement element) {
      if (element == null || !element.isJsonPrimitive()) {
         return null;
      }

      if (!element.getAsJsonPrimitive().isString()) {
         return null;
      }

      String raw = element.getAsString();
      return raw != null && !raw.isBlank() ? Identifier.tryParse(raw.trim()) : null;
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
   private static String parseOptionalColor(@Nullable JsonElement element) {
      if (element == null || !element.isJsonPrimitive()) {
         return null;
      } else {
         return !element.getAsJsonPrimitive().isString() ? null : NonLiquidColors.normalizeHexColor(element.getAsString());
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
      NeedsOfNature.logSetupWarning("[NoN] Ignoring malformed liquid gain entry in {} at index {}: {}", fileId, index, reason);
   }

   public static final class Reloader implements SynchronousResourceReloader {
      static final Identifier RELOADER_ID = Identifier.of("needsofnature", "liquid_gain_overrides");

      public void reload(ResourceManager manager) {
         NonLiquidGainOverrides.reload(manager);
      }
   }
}
