package com.nonid.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.nonid.NeedsOfNature;
import com.nonid.NonAccessoryEffects;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonAccessoryDefinitions {
   private static volatile Map<Identifier, NonAccessoryItemRegistry.AccessoryItemDefinition> DEFINITIONS_BY_ITEM = Map.of();

   private NonAccessoryDefinitions() {
   }

   public static void loadStartupDefinitions() {
      DEFINITIONS_BY_ITEM = NonAccessoryItemRegistry.getDefinitionsByItem();
   }

   public static NonAccessoryEffects getEffects(Identifier itemId) {
      if (itemId == null) {
         return NonAccessoryEffects.NEUTRAL;
      }

      NonAccessoryItemRegistry.AccessoryItemDefinition definition = DEFINITIONS_BY_ITEM.get(itemId);
      return definition == null ? NonAccessoryEffects.NEUTRAL : definition.effects();
   }

   public static boolean hasDefinition(Identifier itemId) {
      return itemId != null && DEFINITIONS_BY_ITEM.containsKey(itemId);
   }

   public static NonAccessoryBehavior getBehavior(Identifier itemId) {
      if (itemId == null) {
         return NonAccessoryBehavior.NONE;
      }

      NonAccessoryItemRegistry.AccessoryItemDefinition definition = DEFINITIONS_BY_ITEM.get(itemId);
      return definition == null ? NonAccessoryBehavior.NONE : definition.behavior();
   }

   @Nullable
   public static NonAccessoryDefinitions.TooltipColor getTooltipColor(Identifier itemId, String effectKey) {
      if (itemId != null && effectKey != null) {
         NonAccessoryItemRegistry.AccessoryItemDefinition definition = DEFINITIONS_BY_ITEM.get(itemId);
         Map<String, NonAccessoryDefinitions.TooltipColor> colors = definition == null ? null : definition.tooltipColors();
         return colors == null ? null : colors.get(effectKey);
      } else {
         return null;
      }
   }

   public static NonAccessoryEffects parseEffectsForStartup(Identifier itemId, JsonObject root) {
      JsonObject effects = objectOrSelf(root.get("effects"), root);
      return parseEffectsObject(itemId, effects);
   }

   public static Map<String, NonAccessoryDefinitions.TooltipColor> parseTooltipColorsForStartup(Identifier itemId, JsonObject root) {
      JsonElement effectsElement = root.get("effects");
      if (effectsElement != null && effectsElement.isJsonObject()) {
         JsonObject effects = effectsElement.getAsJsonObject();
         return parseInlineTooltipColors(itemId, effects);
      } else {
         return Map.of();
      }
   }

   public static NonAccessoryBehavior parseBehaviorForStartup(Identifier itemId, JsonObject root) {
      return parseBehavior(itemId, root);
   }

   private static NonAccessoryEffects parseEffectsObject(Identifier fileId, JsonObject effects) {
      return new NonAccessoryEffects(
         parseMultiplier(fileId, effects, "liquid_decay_multiplier", 1.0, 0.0, 20.0),
         parseBoolean(effects, "equalize_liquid_decay_context", false),
         parseMultiplier(fileId, effects, "player_energy_gain_multiplier", 1.0, 0.0, 20.0),
         parseInt(fileId, effects, "liquid_capacity_add", 0, -10000, 10000),
         parseMultiplier(fileId, effects, "liquid_gain_multiplier", 1.0, 0.0, 20.0),
         parseMultiplier(fileId, effects, "filled_effect_multiplier", 1.0, 0.0, 20.0),
         parseMultiplier(fileId, effects, "pregnancy_chance_multiplier", 1.0, 0.0, 20.0),
         parseMultiplier(fileId, effects, "pregnancy_duration_multiplier", 1.0, 0.01, 20.0),
         parseMultiplier(fileId, effects, "mess_gain_multiplier", 1.0, 0.0, 20.0),
         parseMultiplier(fileId, effects, "destroyed_skin_damage_multiplier", 1.0, 0.0, 20.0),
         parseInt(fileId, effects, "attack_escape_hits_add", 0, -49, 49),
         parseMultiplier(fileId, effects, "attack_escape_damage_multiplier", 1.0, 0.0, 20.0),
         parseMultiplier(fileId, effects, "player_energy_aura_multiplier", 1.0, 0.0, 20.0),
         parseMultiplier(fileId, effects, "near_animation_mob_energy_multiplier", 1.0, 0.0, 20.0)
      );
   }

   private static JsonObject objectOrSelf(@Nullable JsonElement element, JsonObject fallback) {
      return element != null && element.isJsonObject() ? element.getAsJsonObject() : fallback;
   }

   private static NonAccessoryBehavior parseBehavior(Identifier fileId, JsonObject root) {
      Set<String> occupiesSlots = parseSlotSet(root.get("occupies_slots"));
      Set<NonLiquidRoles.InjectorRole> blockedRoles = parseInjectorRoleSet(fileId, root.get("blocks_injector_types"));
      String exclusiveGroup = parseOptionalString(root.get("exclusive_group"));
      int maxDurability = parseInt(fileId, root, "max_durability", 0, 0, 100000);
      int durabilityCost = parseInt(fileId, root, "protection_durability_cost", 1, 1, 100);
      boolean ignoreVisualShedding = parseBoolean(root, "ignore_injector_slot_visual_shedding", false);
      boolean ignoreEffectSuppression = parseBoolean(root, "ignore_injector_slot_effect_suppression", false);
      int vInjectionDurabilityCost = parseInt(fileId, root, "v_injection_durability_cost", 0, 0, 100);
      int aInjectionDurabilityCost = parseInt(fileId, root, "a_injection_durability_cost", 0, 0, 100);
      boolean blocksPregnancy = parseBoolean(root, "blocks_pregnancy", false);
      return new NonAccessoryBehavior(
         occupiesSlots,
         blockedRoles,
         exclusiveGroup,
         maxDurability,
         durabilityCost,
         ignoreVisualShedding,
         ignoreEffectSuppression,
         vInjectionDurabilityCost,
         aInjectionDurabilityCost,
         blocksPregnancy
      );
   }

   private static Set<String> parseSlotSet(@Nullable JsonElement element) {
      if (element == null) {
         return Set.of();
      }

      LinkedHashSet<String> slots = new LinkedHashSet<>();
      if (element.isJsonArray()) {
         for (JsonElement child : element.getAsJsonArray()) {
            addSlot(slots, parseOptionalString(child));
         }
      } else {
         addSlot(slots, parseOptionalString(element));
      }

      return Set.copyOf(slots);
   }

   private static void addSlot(Set<String> slots, @Nullable String raw) {
      if (raw != null && !raw.isBlank()) {
         String slot = NonAccessoryBehavior.normalizeSlot(raw);
         if (!"legs/v".equals(slot) && !"legs/a".equals(slot) && !"legs/d".equals(slot)) {
            NeedsOfNature.logSetupWarning("[NoN] Ignoring unknown accessory occupied slot '{}'.", raw);
         } else {
            slots.add(slot);
         }
      }
   }

   private static Set<NonLiquidRoles.InjectorRole> parseInjectorRoleSet(Identifier fileId, @Nullable JsonElement element) {
      if (element == null) {
         return Set.of();
      }

      LinkedHashSet<NonLiquidRoles.InjectorRole> roles = new LinkedHashSet<>();
      if (element.isJsonArray()) {
         for (JsonElement child : element.getAsJsonArray()) {
            addInjectorRole(fileId, roles, parseOptionalString(child));
         }
      } else {
         addInjectorRole(fileId, roles, parseOptionalString(element));
      }

      return Set.copyOf(roles);
   }

   private static void addInjectorRole(Identifier fileId, Set<NonLiquidRoles.InjectorRole> roles, @Nullable String raw) {
      if (raw != null && !raw.isBlank()) {
         String normalized = raw.trim().toUpperCase(Locale.ROOT);

         try {
            NonLiquidRoles.InjectorRole role = NonLiquidRoles.InjectorRole.valueOf(normalized);
            if (role != NonLiquidRoles.InjectorRole.NONE) {
               roles.add(role);
            }
         } catch (IllegalArgumentException ignored) {
            warn(fileId, "Ignoring unknown blocked injector type '" + raw + "'.");
         }
      }
   }

   @Nullable
   private static String parseOptionalString(@Nullable JsonElement element) {
      if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
         String raw = element.getAsString();
         return raw != null && !raw.isBlank() ? raw.trim() : null;
      } else {
         return null;
      }
   }

   private static double parseMultiplier(Identifier fileId, JsonObject object, String key, double fallback, double min, double max) {
      Double value = parseDouble(object.get(key));
      if (value == null) {
         return fallback;
      } else if (!Double.isFinite(value)) {
         warn(fileId, "Ignoring non-finite '" + key + "'.");
         return fallback;
      } else {
         return Math.max(min, Math.min(max, value));
      }
   }

   private static int parseInt(Identifier fileId, JsonObject object, String key, int fallback, int min, int max) {
      Double value = parseDouble(object.get(key));
      if (value == null) {
         return fallback;
      }

      if (Double.isFinite(value) && value == Math.rint(value)) {
         return Math.max(min, Math.min(max, value.intValue()));
      }

      warn(fileId, "Ignoring non-integer '" + key + "'.");
      return fallback;
   }

   @Nullable
   private static Double parseDouble(@Nullable JsonElement element) {
      element = unwrapEffectValue(element);
      if (element != null && element.isJsonPrimitive()) {
         JsonPrimitive primitive = element.getAsJsonPrimitive();
         return primitive.isNumber() ? primitive.getAsDouble() : null;
      } else {
         return null;
      }
   }

   private static boolean parseBoolean(JsonObject object, String key, boolean fallback) {
      JsonElement element = unwrapEffectValue(object.get(key));
      if (element != null && element.isJsonPrimitive()) {
         JsonPrimitive primitive = element.getAsJsonPrimitive();
         return primitive.isBoolean() ? primitive.getAsBoolean() : fallback;
      } else {
         return fallback;
      }
   }

   @Nullable
   private static JsonElement unwrapEffectValue(@Nullable JsonElement element) {
      return element != null && element.isJsonObject() ? element.getAsJsonObject().get("value") : element;
   }

   private static Map<String, NonAccessoryDefinitions.TooltipColor> parseInlineTooltipColors(Identifier fileId, JsonObject effects) {
      LinkedHashMap<String, NonAccessoryDefinitions.TooltipColor> colors = new LinkedHashMap<>();

      for (Entry<String, JsonElement> entry : effects.entrySet()) {
         JsonElement value = entry.getValue();
         if (value != null && value.isJsonObject()) {
            JsonElement tooltip = value.getAsJsonObject().get("tooltip");
            NonAccessoryDefinitions.TooltipColor color = parseTooltipColor(fileId, entry.getKey(), tooltip);
            if (color != null) {
               colors.put(entry.getKey(), color);
            }
         }
      }

      return Map.copyOf(colors);
   }

   @Nullable
   private static NonAccessoryDefinitions.TooltipColor parseTooltipColor(Identifier fileId, String key, @Nullable JsonElement element) {
      if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
         String raw = element.getAsString().trim();
         if (raw.isEmpty()) {
            return null;
         }

         if ("positive".equalsIgnoreCase(raw)) {
            return NonAccessoryDefinitions.TooltipColor.formatting(Formatting.GREEN);
         }

         if ("negative".equalsIgnoreCase(raw)) {
            return NonAccessoryDefinitions.TooltipColor.formatting(Formatting.RED);
         }

         if ("neutral".equalsIgnoreCase(raw)) {
            return NonAccessoryDefinitions.TooltipColor.formatting(Formatting.WHITE);
         }

         String hex = raw.startsWith("#") ? raw.substring(1) : raw;
         if (hex.length() == 6) {
            try {
               return NonAccessoryDefinitions.TooltipColor.rgb(Integer.parseInt(hex, 16));
            } catch (NumberFormatException var6) {
            }
         }

         warn(fileId, "Ignoring unknown tooltip color '" + raw + "' for '" + key + "'.");
         return null;
      } else {
         warn(fileId, "Ignoring invalid tooltip color for '" + key + "'.");
         return null;
      }
   }

   private static void warn(Identifier fileId, String reason) {
      NeedsOfNature.logSetupWarning("[NoN] Ignoring accessory definition {}: {}", fileId, reason);
   }

   public record TooltipColor(@Nullable Formatting formatting, @Nullable Integer rgb) {
      public static NonAccessoryDefinitions.TooltipColor formatting(Formatting formatting) {
         return new NonAccessoryDefinitions.TooltipColor(formatting, null);
      }

      public static NonAccessoryDefinitions.TooltipColor rgb(int rgb) {
         return new NonAccessoryDefinitions.TooltipColor(null, rgb & 16777215);
      }
   }
}
