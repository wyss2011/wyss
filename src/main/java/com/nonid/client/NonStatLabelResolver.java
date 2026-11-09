package com.nonid.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class NonStatLabelResolver {
   private static final String STAT_PREFIX = "stat.needsofnature.";
   private static final String PEAK_ENTITY_PREFIX = "stat.needsofnature.peaked_animations_entity_";
   private static final String LIQUID_ENTITY_PREFIX = "stat.needsofnature.liquid_gained_ml_entity_";
   private static final String OFFSPRING_ENTITY_PREFIX = "stat.needsofnature.offspring_spawned_entity_";
   private static final Map<String, String> ENTITY_NAME_CACHE = new ConcurrentHashMap<>();

   private NonStatLabelResolver() {
   }

   public static void clearCache() {
      ENTITY_NAME_CACHE.clear();
   }

   public static String resolve(String key) {
      if (key == null || key.isBlank()) {
         return null;
      } else if (key.startsWith("stat.needsofnature.peaked_animations_entity_")) {
         String suffix = key.substring("stat.needsofnature.peaked_animations_entity_".length());
         String entityName = resolveEntityNameFromSuffix(suffix);
         return "NoN: Peaked Animations (" + entityName + ")";
      } else if (key.startsWith("stat.needsofnature.liquid_gained_ml_entity_")) {
         String suffix = key.substring("stat.needsofnature.liquid_gained_ml_entity_".length());
         String entityName = resolveEntityNameFromSuffix(suffix);
         return "NoN: Liquid Gained (ml) (" + entityName + ")";
      } else if (key.startsWith("stat.needsofnature.offspring_spawned_entity_")) {
         String suffix = key.substring("stat.needsofnature.offspring_spawned_entity_".length());
         String entityName = resolveEntityNameFromSuffix(suffix);
         return "NoN: Offspring Spawned (" + entityName + ")";
      } else {
         return null;
      }
   }

   private static String resolveEntityNameFromSuffix(String suffix) {
      return suffix != null && !suffix.isBlank() ? ENTITY_NAME_CACHE.computeIfAbsent(suffix, NonStatLabelResolver::lookupEntityName) : "Unknown";
   }

   private static String lookupEntityName(String suffix) {
      for (Identifier entityId : Registries.ENTITY_TYPE.getIds()) {
         if (suffix.equals(sanitizeEntityId(entityId))) {
            EntityType<?> type = (EntityType<?>)Registries.ENTITY_TYPE.get(entityId);
            return type.getName().getString();
         }
      }

      return prettifySuffix(suffix);
   }

   private static String sanitizeEntityId(Identifier entityId) {
      return sanitizePathPart(entityId.getNamespace()) + "_" + sanitizePathPart(entityId.getPath());
   }

   private static String sanitizePathPart(String value) {
      if (value != null && !value.isBlank()) {
         StringBuilder out = new StringBuilder(value.length());

         for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 'a' && c <= 'z') {
               out.append(c);
            } else if (c >= '0' && c <= '9') {
               out.append(c);
            } else {
               out.append('_');
            }
         }

         return out.isEmpty() ? "unknown" : out.toString();
      } else {
         return "unknown";
      }
   }

   private static String prettifySuffix(String suffix) {
      String normalized = suffix.replace('_', ' ').trim();
      if (normalized.isEmpty()) {
         return "Unknown";
      }

      StringBuilder out = new StringBuilder(normalized.length());
      boolean cap = true;

      for (int i = 0; i < normalized.length(); i++) {
         char c = normalized.charAt(i);
         if (cap && c >= 'a' && c <= 'z') {
            out.append((char)(c - ' '));
         } else {
            out.append(c);
         }

         cap = c == ' ';
      }

      return out.toString();
   }
}
