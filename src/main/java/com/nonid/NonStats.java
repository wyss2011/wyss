package com.nonid;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.StatFormatter;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;

public final class NonStats {
   private static final String STAT_PATH_PEAKED_TOTAL = "peaked_animations_total";
   private static final String STAT_PATH_LIQUID_GAINED_TOTAL = "liquid_gained_ml_total";
   private static final String STAT_PATH_MULTI_ACTOR_TOTAL = "multi_actor_animations_total";
   private static final String STAT_PATH_WATER_TOTAL = "water_animations_total";
   private static final String STAT_PATH_ANIMATION_START_TOTAL = "animations_started_total";
   private static final String STAT_PATH_ATTACK_ANIMATION_START_TOTAL = "attack_animations_started_total";
   private static final String STAT_PATH_ATTACK_ESCAPE_SUCCESS_TOTAL = "attack_escapes_success_total";
   private static final String STAT_PATH_M_INJECTOR_PEAKS_ML_TOTAL = "m_injector_peaks_ml_total";
   private static final String STAT_PATH_MANUAL_PEAK_TOTAL = "manual_peaks_total";
   private static final String STAT_PATH_FILL_BOTTLE_TOTAL = "bottles_filled_total";
   private static final String STAT_PATH_PREGNANCY_STARTED_TOTAL = "pregnancies_started_total";
   private static final String STAT_PATH_PREGNANCY_COMPLETED_TOTAL = "pregnancies_completed_total";
   private static final String STAT_PATH_OFFSPRING_SPAWNED_TOTAL = "offspring_spawned_total";
   private static final Identifier STAT_PEAKED_TOTAL = NeedsOfNature.id("peaked_animations_total");
   private static final Identifier STAT_LIQUID_GAINED_TOTAL = NeedsOfNature.id("liquid_gained_ml_total");
   private static final Identifier STAT_MULTI_ACTOR_TOTAL = NeedsOfNature.id("multi_actor_animations_total");
   private static final Identifier STAT_WATER_TOTAL = NeedsOfNature.id("water_animations_total");
   private static final Identifier STAT_ANIMATION_START_TOTAL = NeedsOfNature.id("animations_started_total");
   private static final Identifier STAT_ATTACK_ANIMATION_START_TOTAL = NeedsOfNature.id("attack_animations_started_total");
   private static final Identifier STAT_ATTACK_ESCAPE_SUCCESS_TOTAL = NeedsOfNature.id("attack_escapes_success_total");
   private static final Identifier STAT_M_INJECTOR_PEAKS_ML_TOTAL = NeedsOfNature.id("m_injector_peaks_ml_total");
   private static final Identifier STAT_MANUAL_PEAK_TOTAL = NeedsOfNature.id("manual_peaks_total");
   private static final Identifier STAT_FILL_BOTTLE_TOTAL = NeedsOfNature.id("bottles_filled_total");
   private static final Identifier STAT_PREGNANCY_STARTED_TOTAL = NeedsOfNature.id("pregnancies_started_total");
   private static final Identifier STAT_PREGNANCY_COMPLETED_TOTAL = NeedsOfNature.id("pregnancies_completed_total");
   private static final Identifier STAT_OFFSPRING_SPAWNED_TOTAL = NeedsOfNature.id("offspring_spawned_total");
   private static final Map<Identifier, Identifier> PEAKED_BY_ENTITY = new LinkedHashMap<>();
   private static final Map<Identifier, Identifier> LIQUID_GAINED_BY_ENTITY = new LinkedHashMap<>();
   private static final Map<Identifier, Identifier> OFFSPRING_SPAWNED_BY_ENTITY = new LinkedHashMap<>();

   private NonStats() {
   }

   public static void registerBaseStats() {
      registerStat(STAT_PEAKED_TOTAL, StatFormatter.DEFAULT);
      registerStat(STAT_LIQUID_GAINED_TOTAL, StatFormatter.DEFAULT);
      registerStat(STAT_MULTI_ACTOR_TOTAL, StatFormatter.DEFAULT);
      registerStat(STAT_WATER_TOTAL, StatFormatter.DEFAULT);
      registerStat(STAT_ANIMATION_START_TOTAL, StatFormatter.DEFAULT);
      registerStat(STAT_ATTACK_ANIMATION_START_TOTAL, StatFormatter.DEFAULT);
      registerStat(STAT_ATTACK_ESCAPE_SUCCESS_TOTAL, StatFormatter.DEFAULT);
      registerStat(STAT_M_INJECTOR_PEAKS_ML_TOTAL, StatFormatter.DEFAULT);
      registerStat(STAT_MANUAL_PEAK_TOTAL, StatFormatter.DEFAULT);
      registerStat(STAT_FILL_BOTTLE_TOTAL, StatFormatter.DEFAULT);
      registerStat(STAT_PREGNANCY_STARTED_TOTAL, StatFormatter.DEFAULT);
      registerStat(STAT_PREGNANCY_COMPLETED_TOTAL, StatFormatter.DEFAULT);
      registerStat(STAT_OFFSPRING_SPAWNED_TOTAL, StatFormatter.DEFAULT);
   }

   public static void registerKnownEntityStats() {
      for (Identifier entityId : Registries.ENTITY_TYPE.getIds()) {
         ensureEntityStats(entityId);
      }
   }

   public static void incrementAnimationStarted(ServerPlayerEntity player) {
      increase(player, STAT_ANIMATION_START_TOTAL, 1);
   }

   public static void incrementMultiActorAnimation(ServerPlayerEntity player) {
      increase(player, STAT_MULTI_ACTOR_TOTAL, 1);
   }

   public static void incrementWaterAnimation(ServerPlayerEntity player) {
      increase(player, STAT_WATER_TOTAL, 1);
   }

   public static void incrementAttackAnimationStarted(ServerPlayerEntity player) {
      increase(player, STAT_ATTACK_ANIMATION_START_TOTAL, 1);
   }

   public static void incrementAttackEscapeSuccess(ServerPlayerEntity player) {
      increase(player, STAT_ATTACK_ESCAPE_SUCCESS_TOTAL, 1);
   }

   public static void addMInjectorPeaksMl(ServerPlayerEntity player, int amount) {
      increase(player, STAT_M_INJECTOR_PEAKS_ML_TOTAL, amount);
   }

   public static void incrementManualPeak(ServerPlayerEntity player) {
      increase(player, STAT_MANUAL_PEAK_TOTAL, 1);
   }

   public static void incrementFillBottle(ServerPlayerEntity player) {
      increase(player, STAT_FILL_BOTTLE_TOTAL, 1);
   }

   public static int getBottlesFilled(ServerPlayerEntity player) {
      return get(player, STAT_FILL_BOTTLE_TOTAL);
   }

   public static void incrementPregnancyStarted(ServerPlayerEntity player) {
      increase(player, STAT_PREGNANCY_STARTED_TOTAL, 1);
   }

   public static void incrementPregnancyCompleted(ServerPlayerEntity player) {
      increase(player, STAT_PREGNANCY_COMPLETED_TOTAL, 1);
   }

   public static int getPregnanciesCompleted(ServerPlayerEntity player) {
      return get(player, STAT_PREGNANCY_COMPLETED_TOTAL);
   }

   public static void addOffspringSpawned(ServerPlayerEntity player, Identifier entityTypeId, int amount) {
      if (player != null && amount > 0) {
         increase(player, STAT_OFFSPRING_SPAWNED_TOTAL, amount);
         if (entityTypeId != null) {
            ensureEntityStats(entityTypeId);
            Identifier stat = OFFSPRING_SPAWNED_BY_ENTITY.get(entityTypeId);
            if (stat != null) {
               increase(player, stat, amount);
            }
         }
      }
   }

   public static void incrementPeakedAnimations(ServerPlayerEntity player, Set<Identifier> entityTypes) {
      increase(player, STAT_PEAKED_TOTAL, 1);
      if (entityTypes != null && !entityTypes.isEmpty()) {
         for (Identifier entityTypeId : entityTypes) {
            if (entityTypeId != null) {
               ensureEntityStats(entityTypeId);
               Identifier stat = PEAKED_BY_ENTITY.get(entityTypeId);
               if (stat != null) {
                  increase(player, stat, 1);
               }
            }
         }
      }
   }

   public static void addLiquidGained(ServerPlayerEntity player, int amount, Map<Identifier, Integer> perEntityAmounts) {
      if (amount > 0) {
         increase(player, STAT_LIQUID_GAINED_TOTAL, amount);
         if (perEntityAmounts != null && !perEntityAmounts.isEmpty()) {
            for (Entry<Identifier, Integer> entry : perEntityAmounts.entrySet()) {
               Identifier entityId = entry.getKey();
               int entityAmount = entry.getValue() == null ? 0 : entry.getValue();
               if (entityId != null && entityAmount > 0) {
                  ensureEntityStats(entityId);
                  Identifier stat = LIQUID_GAINED_BY_ENTITY.get(entityId);
                  if (stat != null) {
                     increase(player, stat, entityAmount);
                  }
               }
            }
         }
      }
   }

   private static void increase(ServerPlayerEntity player, Identifier statId, int amount) {
      if (player != null && statId != null && amount > 0) {
         if (registerStat(statId, StatFormatter.DEFAULT)) {
            player.increaseStat(statId, amount);
         }
      }
   }

   private static int get(ServerPlayerEntity player, Identifier statId) {
      if (player == null || statId == null) {
         return 0;
      } else {
         return !registerStat(statId, StatFormatter.DEFAULT) ? 0 : player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(statId));
      }
   }

   private static void ensureEntityStats(Identifier entityId) {
      synchronized (NonStats.class) {
         if (!PEAKED_BY_ENTITY.containsKey(entityId)) {
            Identifier peakedStat = NeedsOfNature.id("peaked_animations_entity_" + sanitizeEntityId(entityId));
            if (registerStat(peakedStat, StatFormatter.DEFAULT)) {
               PEAKED_BY_ENTITY.put(entityId, peakedStat);
            }
         }

         if (!LIQUID_GAINED_BY_ENTITY.containsKey(entityId)) {
            Identifier liquidStat = NeedsOfNature.id("liquid_gained_ml_entity_" + sanitizeEntityId(entityId));
            if (registerStat(liquidStat, StatFormatter.DEFAULT)) {
               LIQUID_GAINED_BY_ENTITY.put(entityId, liquidStat);
            }
         }

         if (!OFFSPRING_SPAWNED_BY_ENTITY.containsKey(entityId)) {
            Identifier offspringStat = NeedsOfNature.id("offspring_spawned_entity_" + sanitizeEntityId(entityId));
            if (registerStat(offspringStat, StatFormatter.DEFAULT)) {
               OFFSPRING_SPAWNED_BY_ENTITY.put(entityId, offspringStat);
            }
         }
      }
   }

   private static boolean registerStat(Identifier statId, StatFormatter formatter) {
      if (statId == null) {
         return false;
      }

      if (!Registries.CUSTOM_STAT.containsId(statId)) {
         try {
            Registry.register(Registries.CUSTOM_STAT, statId, statId);
         } catch (RuntimeException ex) {
            return false;
         }
      }

      Stats.CUSTOM.getOrCreateStat(statId, formatter);
      return true;
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
}
