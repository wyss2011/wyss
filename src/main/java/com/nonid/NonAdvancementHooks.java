package com.nonid;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

final class NonAdvancementHooks {
   private static final int MOTHER_NATURE_PREGNANCIES = 25;
   private static final int COLLECTORS_HABIT_BOTTLES = 25;
   private static final int FIELD_RESEARCH_TYPES = 5;
   private static final int TOSSED_AROUND_CHAIN = 5;
   private static final int STABLE_DIET_CHAIN = 3;
   private static final String ROOT = "root";
   private static final String NATURAL_CURIOSITY = "natural_curiosity";
   private static final String FILLED_TO_THE_BRIM = "filled_to_the_brim";
   private static final String FAVOURITISM = "favouritism";
   private static final String FIRST_PREGNANCY = "first_pregnancy";
   private static final String MOTHER_NATURE = "mother_nature";
   private static final String MESSED_UP = "messed_up";
   private static final String GROUP_ACTIVITY = "group_activity";
   private static final String TOSSED_AROUND = "tossed_around";
   private static final String COLLECTORS_HABIT = "collectors_habit";
   private static final String FIELD_RESEARCH = "field_research";
   private static final String BAD_IDEA_BEACON = "bad_idea_beacon";
   private static final String PRODUCTIVE_PREGNANCY = "productive_pregnancy";
   private static final String EMERGENCY_SNACK = "emergency_snack";
   private static final String STABLE_DIET = "stable_diet";
   private static final String UDDERLY_UNFORTUNATE = "udderly_unfortunate";
   private static final String ACQUIRE_HARDWARE = "acquire_hardware";
   private static final Map<UUID, NonAdvancementHooks.FieldResearchState> FIELD_RESEARCH_STATES = new ConcurrentHashMap<>();
   private static final Map<UUID, Integer> TOSSED_AROUND_STREAKS = new ConcurrentHashMap<>();
   private static final Map<UUID, Integer> STABLE_DIET_STREAKS = new ConcurrentHashMap<>();

   private NonAdvancementHooks() {
   }

   static void clearRuntimeState() {
      FIELD_RESEARCH_STATES.clear();
      TOSSED_AROUND_STREAKS.clear();
      STABLE_DIET_STREAKS.clear();
   }

   static void clearPlayerState(UUID playerUuid) {
      if (playerUuid != null) {
         FIELD_RESEARCH_STATES.remove(playerUuid);
         TOSSED_AROUND_STREAKS.remove(playerUuid);
         STABLE_DIET_STREAKS.remove(playerUuid);
      }
   }

   static void grant(ServerPlayerEntity player, String path) {
      if (player != null && path != null && !path.isBlank()) {
         if (player.getEntityWorld() instanceof ServerWorld world) {
            MinecraftServer server = world.getServer();
            if (server != null) {
               if (!"root".equals(path)) {
                  grant(player, "root");
               }

               AdvancementEntry advancement = server.getAdvancementLoader().get(NeedsOfNature.id(path));
               if (advancement != null) {
                  player.getAdvancementTracker().grantCriterion(advancement, "done");
               }
            }
         }
      }
   }

   static void grantEmergencySnack(ServerPlayerEntity player) {
      grant(player, "emergency_snack");
      recordStableDietSnack(player);
   }

   static void resetStableDietStreak(ServerPlayerEntity player) {
      if (player != null) {
         STABLE_DIET_STREAKS.remove(player.getUuid());
      }
   }

   private static void recordStableDietSnack(ServerPlayerEntity player) {
      if (player != null) {
         int streak = STABLE_DIET_STREAKS.merge(player.getUuid(), 1, Integer::sum);
         if (streak >= 3) {
            grant(player, "stable_diet");
         }
      }
   }

   static void checkLiquidAdvancements(ServerPlayerEntity player, LiquidHolder holder) {
      if (player != null && holder != null) {
         int capacity = holder.getLiquidCapacity();
         if (capacity > 0 && holder.getLiquidStored() >= capacity) {
            grant(player, "filled_to_the_brim");
            if (holder.getLiquidComposition() == LiquidHolder.LiquidComposition.ENTITY && holder.getLiquidEntityTypeId() != null) {
               grant(player, "favouritism");
            }
         }
      }
   }

   static void trackFieldResearch(ServerWorld world, ServerPlayerEntity player, Map<Identifier, Integer> perEntityAmounts) {
      if (world != null && player != null && perEntityAmounts != null && !perEntityAmounts.isEmpty()) {
         Set<Identifier> gainedTypes = new LinkedHashSet<>();

         for (Entry<Identifier, Integer> entry : perEntityAmounts.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
               gainedTypes.add(entry.getKey());
            }
         }

         if (!gainedTypes.isEmpty()) {
            long day = world.getTimeOfDay() / 24000L;
            UUID playerUuid = player.getUuid();
            NonAdvancementHooks.FieldResearchState existing = FIELD_RESEARCH_STATES.get(playerUuid);
            Set<Identifier> entityTypes = new LinkedHashSet<>();
            if (existing != null && existing.day() == day) {
               entityTypes.addAll(existing.entityTypes());
            }

            entityTypes.addAll(gainedTypes);
            FIELD_RESEARCH_STATES.put(playerUuid, new NonAdvancementHooks.FieldResearchState(day, Set.copyOf(entityTypes)));
            if (entityTypes.size() >= 5) {
               grant(player, "field_research");
            }
         }
      }
   }

   static void checkBadIdeaBeacon(ServerPlayerEntity player) {
      if (player != null && player instanceof EnergyHolder energyHolder) {
         if (energyHolder.getEnergy() >= 200) {
            if (NeedsOfNature.getDestroyedSkinStageForPlayer(player) >= 4) {
               grant(player, "bad_idea_beacon");
            }
         }
      }
   }

   static void grantNaturalCuriosity(ServerPlayerEntity player) {
      grant(player, "natural_curiosity");
   }

   static void grantGroupActivity(ServerPlayerEntity player) {
      grant(player, "group_activity");
   }

   static void grantMessedUp(ServerPlayerEntity player) {
      grant(player, "messed_up");
   }

   static void checkCollectorsHabit(ServerPlayerEntity player) {
      if (player != null) {
         if (NonStats.getBottlesFilled(player) >= 25) {
            grant(player, "collectors_habit");
         }
      }
   }

   static void resetTossedAround(ServerPlayerEntity player) {
      if (player != null) {
         TOSSED_AROUND_STREAKS.remove(player.getUuid());
      }
   }

   static void recordTossedAround(ServerPlayerEntity player) {
      if (player != null) {
         int streak = TOSSED_AROUND_STREAKS.merge(player.getUuid(), 1, Integer::sum);
         if (streak >= 5) {
            grant(player, "tossed_around");
         }
      }
   }

   static void grantFirstPregnancy(ServerPlayerEntity player) {
      grant(player, "first_pregnancy");
   }

   static void checkMotherNature(ServerPlayerEntity player) {
      if (player != null) {
         if (NonStats.getPregnanciesCompleted(player) >= 25) {
            grant(player, "mother_nature");
         }
      }
   }

   static void grantProductivePregnancy(ServerPlayerEntity player) {
      grant(player, "productive_pregnancy");
   }

   static void grantUdderlyUnfortunate(ServerPlayerEntity player) {
      grant(player, "udderly_unfortunate");
   }

   static void grantAcquireHardware(ServerPlayerEntity player) {
      grant(player, "acquire_hardware");
   }

   private record FieldResearchState(long day, Set<Identifier> entityTypes) {
   }
}
