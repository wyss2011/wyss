package com.nonid.internal.animation.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.nonid.NeedsOfNature;
import com.nonid.data.NonLiquidRoles;
import com.nonid.internal.animation.AnimationEngine;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.util.AnimationIds;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class AnimationGameplayMetadata {
   public static final int INFINITE_STAGE_SECONDS = -1;
   private static final double MIN_STAGE_DURATION_MULTIPLIER = 0.05;
   private static final double MAX_STAGE_DURATION_MULTIPLIER = 20.0;

   private AnimationGameplayMetadata() {
   }

   public static AnimationGameplayMetadata.Data parse(Identifier fileId, AnimationDefinitions.Definition definition, JsonObject root, List<String> actorKeys) {
      AnimationGameplayMetadata.Builder builder = new AnimationGameplayMetadata.Builder();
      builder.accept(fileId, definition, root, actorKeys);
      return builder.build();
   }

   @Nullable
   public static Integer peakStage(Identifier animationId) {
      AnimationGameplayMetadata.Data data = data(animationId);
      return data == null ? null : data.peakStage();
   }

   public static Map<Identifier, Integer> peakStages() {
      Map<Identifier, Integer> out = new LinkedHashMap<>();

      for (AnimationDefinitions.Definition definition : AnimationDefinitions.getLoadedDefinitionsSnapshot()) {
         Integer peakStage = definition.gameplayMetadata().peakStage();
         if (peakStage != null) {
            out.put(definition.id(), peakStage);
         }
      }

      return Map.copyOf(out);
   }

   @Nullable
   public static Integer stageSeconds(Identifier animationId) {
      AnimationGameplayMetadata.Data data = data(animationId);
      return data != null && animationId != null ? data.stageSeconds().get(animationId) : null;
   }

   public static boolean isInfiniteStageSeconds(@Nullable Integer seconds) {
      return seconds != null && seconds < 0;
   }

   public static double cycleTicks(Identifier animationId) {
      if (animationId == null) {
         return 0.0;
      }

      AnimationGameplayMetadata.Data data = data(animationId);
      Double value = data == null ? null : data.cycleTicks().get(animationId);
      return value != null && Double.isFinite(value) && !(value <= 0.0) ? value : 0.0;
   }

   public static double stageDurationMultiplier(Identifier animationId) {
      if (animationId == null) {
         return 1.0;
      }

      AnimationGameplayMetadata.Data data = data(animationId);
      Double value = data == null ? null : data.stageDurationMultipliers().get(animationId);
      return value != null && Double.isFinite(value) && !(value <= 0.0) ? value : 1.0;
   }

   public static boolean escapable(Identifier animationId) {
      if (animationId == null) {
         return true;
      }

      AnimationGameplayMetadata.Data data = data(animationId);
      if (data == null) {
         return true;
      }

      Boolean direct = data.escapable().get(animationId);
      if (direct != null) {
         return direct;
      }

      Boolean base = data.escapable().get(AnimationIds.baseId(animationId));
      return base == null || base;
   }

   @Nullable
   public static NonLiquidRoles.LiquidRoles liquidRoles(Identifier animationId) {
      AnimationGameplayMetadata.Data data = data(animationId);
      return data == null ? null : data.liquidRoles();
   }

   @Nullable
   public static Double liquidGainMultiplier(Identifier animationId) {
      AnimationGameplayMetadata.Data data = data(animationId);
      return data == null ? null : data.liquidGainMultiplier();
   }

   @Nullable
   public static AnimationGameplayMetadata.ManualPeakRule manualPeakRule(Identifier animationId) {
      AnimationGameplayMetadata.Data data = data(animationId);
      return data == null ? null : data.manualPeakRule();
   }

   @Nullable
   private static AnimationGameplayMetadata.Data data(@Nullable Identifier animationId) {
      Identifier baseId = AnimationIds.baseId(animationId);
      AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(baseId);
      return definition == null ? null : definition.gameplayMetadata();
   }

   private static int clampStageSeconds(int value) {
      return value < 0 ? -1 : Math.max(1, Math.min(300, value));
   }

   @Nullable
   private static Integer readInteger(JsonElement element) {
      if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
         double value = element.getAsDouble();
         return Double.isFinite(value) && value == Math.rint(value) && !(value < -2.1474836E9F) && !(value > 2.147483647E9) ? (int)value : null;
      } else {
         return null;
      }
   }

   @Nullable
   private static Double readDouble(JsonElement element) {
      if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
         double value = element.getAsDouble();
         return Double.isFinite(value) ? value : null;
      } else {
         return null;
      }
   }

   @Nullable
   static Boolean readBoolean(JsonElement element) {
      return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() ? element.getAsBoolean() : null;
   }

   private static final class Builder {
      private Identifier definitionId;
      private final Map<Identifier, Integer> peakStages = new HashMap<>();
      private final Map<Identifier, Integer> stageSeconds = new HashMap<>();
      private final Map<Identifier, Double> cycleTicks = new HashMap<>();
      private final Map<Identifier, Double> stageDurationMultipliers = new HashMap<>();
      private final Map<Identifier, Boolean> escapable = new HashMap<>();
      private final Map<Identifier, NonLiquidRoles.LiquidRoles> liquidRoles = new HashMap<>();
      private final Map<Identifier, Double> liquidGainMultipliers = new HashMap<>();
      private final Map<Identifier, AnimationGameplayMetadata.ManualPeakRule> manualPeakRules = new HashMap<>();
      private boolean attackEligible = true;

      private void accept(Identifier fileId, AnimationDefinitions.Definition definition, JsonObject root, List<String> actorKeys) {
         if (definition != null && root != null) {
            this.definitionId = definition.id();
            this.readAttackEligibility(fileId, root);

            try {
               this.readStageMetadata(fileId, definition.id(), root);
            } catch (RuntimeException e) {
               AnimationEngine.LOGGER.debug("[NoN] Failed to read stage gameplay metadata from {}", fileId, e);
            }

            try {
               this.readLiquidRoles(fileId, definition, root, actorKeys);
            } catch (RuntimeException e) {
               AnimationEngine.LOGGER.debug("[NoN] Failed to read liquid roles from {}", fileId, e);
            }

            try {
               this.readManualPeak(fileId, definition.id(), root);
            } catch (RuntimeException e) {
               AnimationEngine.LOGGER.debug("[NoN] Failed to read manual peak metadata from {}", fileId, e);
            }
         }
      }

      private void readAttackEligibility(Identifier fileId, JsonObject root) {
         if (root.has("attack_eligible")) {
            Boolean parsed = AnimationGameplayMetadata.readBoolean(root.get("attack_eligible"));
            if (parsed == null) {
               AnimationEngine.logSetupWarning("[NoN] Invalid attack_eligible in {}. Expected true or false; using default true.", fileId);
            } else {
               this.attackEligible = parsed;
            }
         }
      }

      private void readStageMetadata(Identifier fileId, Identifier definitionId, JsonObject root) {
         Integer baseSeconds = AnimationGameplayMetadata.readInteger(root.get("stage_seconds"));
         if (baseSeconds != null) {
            this.stageSeconds.put(definitionId, AnimationGameplayMetadata.clampStageSeconds(baseSeconds));
         }

         Boolean defaultEscapable = AnimationGameplayMetadata.readBoolean(root.get("escapable"));
         boolean defaultEscapableValue = defaultEscapable == null || defaultEscapable;
         this.escapable.put(definitionId, defaultEscapableValue);
         Double liquidMultiplier = AnimationGameplayMetadata.readDouble(root.get("liquid_gain_multiplier"));
         if (liquidMultiplier != null && liquidMultiplier >= 0.0) {
            this.liquidGainMultipliers.put(definitionId, liquidMultiplier);
         }

         if (root.has("stages") && root.get("stages").isJsonArray()) {
            JsonArray stages = root.getAsJsonArray("stages");
            Integer firstPeak = null;

            for (int i = 0; i < stages.size(); i++) {
               if (stages.get(i).isJsonObject()) {
                  JsonObject stage = stages.get(i).getAsJsonObject();
                  Integer stageNumber = AnimationGameplayMetadata.readInteger(stage.get("stage"));
                  if (stageNumber != null && stageNumber > 0) {
                     Identifier stageId = AnimationIds.stageId(definitionId, stageNumber);
                     if (Boolean.TRUE.equals(AnimationGameplayMetadata.readBoolean(stage.get("non_peak"))) && (firstPeak == null || stageNumber < firstPeak)) {
                        firstPeak = stageNumber;
                     }

                     Integer seconds = AnimationGameplayMetadata.readInteger(stage.get("stage_seconds"));
                     if (seconds != null) {
                        this.stageSeconds.put(stageId, AnimationGameplayMetadata.clampStageSeconds(seconds));
                     }

                     Double secondsPerCycle = AnimationGameplayMetadata.readDouble(stage.get("cycle_seconds"));
                     if (secondsPerCycle != null && secondsPerCycle > 0.0) {
                        this.cycleTicks.put(stageId, secondsPerCycle * 20.0);
                     }

                     Boolean stageEscapable = AnimationGameplayMetadata.readBoolean(stage.get("escapable"));
                     this.escapable.put(stageId, stageEscapable == null ? defaultEscapableValue : stageEscapable);
                     if (stage.has("stage_duration_multiplier")) {
                        Double multiplier = AnimationGameplayMetadata.readDouble(stage.get("stage_duration_multiplier"));
                        Boolean loop = AnimationGameplayMetadata.readBoolean(stage.get("loop"));
                        if (Boolean.FALSE.equals(loop)) {
                           AnimationEngine.logSetupWarning(
                              "[NoN] Ignoring stage_duration_multiplier in {} stage {} because the stage is not looping.", definitionId, stageNumber
                           );
                        } else if (multiplier != null && !(multiplier < 0.05) && !(multiplier > 20.0)) {
                           this.stageDurationMultipliers.put(stageId, multiplier);
                        } else {
                           AnimationEngine.logSetupWarning(
                              "[NoN] Ignoring invalid stage_duration_multiplier in {} stage {}. Expected a number from {} to {}.",
                              definitionId,
                              stageNumber,
                              0.05,
                              20.0
                           );
                        }
                     }
                  }
               }
            }

            if (firstPeak != null) {
               this.peakStages.put(definitionId, firstPeak);
            }
         }
      }

      private void readLiquidRoles(Identifier fileId, AnimationDefinitions.Definition definition, JsonObject root, List<String> actorKeys) {
         if (root.has("actors") && root.get("actors").isJsonArray()) {
            JsonArray actors = root.getAsJsonArray("actors");
            if (!actors.isEmpty() && actorKeys != null && actorKeys.size() == actors.size()) {
               Set<String> injectorKeys = new LinkedHashSet<>();
               Set<String> receiverKeys = new LinkedHashSet<>();
               Map<String, NonLiquidRoles.InjectorRole> injectorRoles = new LinkedHashMap<>();
               boolean explicitReceiver = false;
               boolean[] injectors = new boolean[actors.size()];

               for (int i = 0; i < actors.size(); i++) {
                  if (actors.get(i).isJsonObject()) {
                     JsonObject actor = actors.get(i).getAsJsonObject();
                     String actorKey = actorKeys.get(i);
                     NonLiquidRoles.InjectorRole role = this.readInjectorRole(fileId, actor, i);
                     injectors[i] = role != NonLiquidRoles.InjectorRole.NONE;
                     if (role == NonLiquidRoles.InjectorRole.V || role == NonLiquidRoles.InjectorRole.A) {
                        injectorKeys.add(actorKey);
                     }

                     if (injectors[i]) {
                        injectorRoles.put(actorKey, role);
                     }

                     if (Boolean.TRUE.equals(AnimationGameplayMetadata.readBoolean(actor.get("receiver")))) {
                        receiverKeys.add(actorKey);
                        explicitReceiver = true;
                     }
                  }
               }

               if (!explicitReceiver) {
                  for (int i = 0; i < actors.size(); i++) {
                     if (!injectors[i]) {
                        receiverKeys.add(actorKeys.get(i));
                     }
                  }
               }

               if (!explicitReceiver && receiverKeys.isEmpty()) {
                  for (int i = 0; i < definition.actors().size(); i++) {
                     if (definition.actors().get(i).entityTypes().contains(Identifier.of("minecraft", "player"))) {
                        receiverKeys.add(actorKeys.get(i));
                     }
                  }
               }

               this.liquidRoles
                  .put(definition.id(), new NonLiquidRoles.LiquidRoles(Set.copyOf(injectorKeys), Set.copyOf(receiverKeys), Map.copyOf(injectorRoles)));
            }
         }
      }

      private NonLiquidRoles.InjectorRole readInjectorRole(Identifier fileId, JsonObject actor, int actorIndex) {
         JsonElement element = actor.get("injector");
         if (element == null || element.isJsonNull()) {
            return NonLiquidRoles.InjectorRole.NONE;
         }

         if (!element.isJsonPrimitive()) {
            this.warnInvalidInjector(fileId, actorIndex, "value must be true/false or V/A/M");
            return NonLiquidRoles.InjectorRole.NONE;
         }

         JsonPrimitive primitive = element.getAsJsonPrimitive();
         if (primitive.isBoolean()) {
            return primitive.getAsBoolean() ? NonLiquidRoles.InjectorRole.V : NonLiquidRoles.InjectorRole.NONE;
         }

         if (!primitive.isString()) {
            this.warnInvalidInjector(fileId, actorIndex, "value must be true/false or V/A/M");
            return NonLiquidRoles.InjectorRole.NONE;
         }

         String raw = primitive.getAsString();
         if (raw != null && !raw.isBlank()) {
            return switch (raw.trim().toUpperCase(Locale.ROOT)) {
               case "V" -> NonLiquidRoles.InjectorRole.V;
               case "A" -> NonLiquidRoles.InjectorRole.A;
               case "M" -> NonLiquidRoles.InjectorRole.M;
               default -> {
                  this.warnInvalidInjector(fileId, actorIndex, "unknown injector role '" + raw + "'");
                  yield NonLiquidRoles.InjectorRole.NONE;
               }
            };
         } else {
            return NonLiquidRoles.InjectorRole.NONE;
         }
      }

      private void warnInvalidInjector(Identifier fileId, int actorIndex, String reason) {
         AnimationEngine.logSetupWarning("[NoN] Ignoring invalid injector in {} actor {}: {}. Expected true/false or V/A/M.", fileId, actorIndex, reason);
      }

      private void readManualPeak(Identifier fileId, Identifier definitionId, JsonObject root) {
         if (root.has("manual_peak") && root.get("manual_peak").isJsonObject()) {
            JsonObject manualPeak = root.getAsJsonObject("manual_peak");
            Set<Identifier> items = new LinkedHashSet<>();
            this.addHeldItem(fileId, items, manualPeak.get("held_item"));
            if (manualPeak.has("held_items") && manualPeak.get("held_items").isJsonArray()) {
               for (JsonElement heldItem : manualPeak.getAsJsonArray("held_items")) {
                  this.addHeldItem(fileId, items, heldItem);
               }
            }

            Integer priority = AnimationGameplayMetadata.readInteger(manualPeak.get("priority"));
            boolean propFromHeldItem = Boolean.TRUE.equals(AnimationGameplayMetadata.readBoolean(manualPeak.get("prop_from_held_item")));
            AnimationStageInfo.PropSlot propSlot = this.readManualPeakPropSlot(fileId, manualPeak.get("held_item_prop_slot"));
            if (propSlot == null) {
               propFromHeldItem = false;
               propSlot = AnimationStageInfo.PropSlot.RIGHT;
            }

            this.manualPeakRules
               .put(definitionId, new AnimationGameplayMetadata.ManualPeakRule(items, priority == null ? 0 : priority, propFromHeldItem, propSlot));
         }
      }

      @Nullable
      private AnimationStageInfo.PropSlot readManualPeakPropSlot(Identifier fileId, @Nullable JsonElement element) {
         if (element == null || element.isJsonNull()) {
            return AnimationStageInfo.PropSlot.RIGHT;
         }

         if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString().trim().toLowerCase(Locale.ROOT);

            return switch (value) {
               case "left" -> AnimationStageInfo.PropSlot.LEFT;
               case "right" -> AnimationStageInfo.PropSlot.RIGHT;
               case "floor" -> AnimationStageInfo.PropSlot.FLOOR;
               default -> {
                  AnimationEngine.logSetupWarning(
                     "[NoN] Invalid manual_peak held_item_prop_slot '{}' in {}. Expected left, right, or floor; dynamic held-item prop disabled.",
                     value,
                     fileId
                  );
                  yield null;
               }
            };
         } else {
            AnimationEngine.logSetupWarning(
               "[NoN] Invalid manual_peak held_item_prop_slot in {}. Expected left, right, or floor; dynamic held-item prop disabled.", fileId
            );
            return null;
         }
      }

      private void addHeldItem(Identifier fileId, Set<Identifier> items, JsonElement element) {
         if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString();
            if (raw != null && !raw.isBlank()) {
               Identifier itemId = Identifier.tryParse(raw.trim());
               if (itemId != null && Registries.ITEM.containsId(itemId)) {
                  items.add(itemId);
               } else {
                  NeedsOfNature.logSetupWarning("[NoN] Ignoring invalid manual peak held item '{}' in {}.", raw, fileId);
               }
            }
         }
      }

      private AnimationGameplayMetadata.Data build() {
         return this.definitionId == null
            ? AnimationGameplayMetadata.Data.empty()
            : new AnimationGameplayMetadata.Data(
               this.peakStages.get(this.definitionId),
               Map.copyOf(this.stageSeconds),
               Map.copyOf(this.cycleTicks),
               Map.copyOf(this.stageDurationMultipliers),
               Map.copyOf(this.escapable),
               this.liquidRoles.get(this.definitionId),
               this.liquidGainMultipliers.get(this.definitionId),
               this.manualPeakRules.get(this.definitionId),
               this.attackEligible
            );
      }
   }

   public record Data(
      @Nullable Integer peakStage,
      Map<Identifier, Integer> stageSeconds,
      Map<Identifier, Double> cycleTicks,
      Map<Identifier, Double> stageDurationMultipliers,
      Map<Identifier, Boolean> escapable,
      @Nullable NonLiquidRoles.LiquidRoles liquidRoles,
      @Nullable Double liquidGainMultiplier,
      @Nullable AnimationGameplayMetadata.ManualPeakRule manualPeakRule,
      boolean attackEligible
   ) {
      public Data {
         stageSeconds = stageSeconds == null ? Map.of() : Map.copyOf(stageSeconds);
         cycleTicks = cycleTicks == null ? Map.of() : Map.copyOf(cycleTicks);
         stageDurationMultipliers = stageDurationMultipliers == null ? Map.of() : Map.copyOf(stageDurationMultipliers);
         escapable = escapable == null ? Map.of() : Map.copyOf(escapable);
      }

      public static AnimationGameplayMetadata.Data empty() {
         return new AnimationGameplayMetadata.Data(null, Map.of(), Map.of(), Map.of(), Map.of(), null, null, null, true);
      }
   }

   public record ManualPeakRule(Set<Identifier> heldItems, int priority, boolean propFromHeldItem, AnimationStageInfo.PropSlot heldItemPropSlot) {
      public ManualPeakRule {
         heldItems = heldItems == null ? Set.of() : Set.copyOf(heldItems);
         heldItemPropSlot = heldItemPropSlot == null ? AnimationStageInfo.PropSlot.RIGHT : heldItemPropSlot;
      }
   }
}
