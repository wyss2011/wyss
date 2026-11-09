package com.nonid;

import com.nonid.data.NonEntityProfiles;
import com.nonid.network.PregnancyCycleStateS2CPayload;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

final class NonPregnancyConceptionSystem {
   private static final Identifier PLAYER_ENTITY_TYPE_ID = Identifier.of("minecraft", "player");
   private static final long TICKS_PER_DAY = 24000L;
   private static final long CYCLE_TICKS = 720000L;
   private static final long CHECK_INTERVAL_TICKS = 20L;
   private static final double CHECKS_PER_MINUTE = 60.0;
   private static final int SAFE_PHASE_FIRST_END_DAY = 5;
   private static final int SAFE_PHASE_SECOND_START_DAY = 26;

   private NonPregnancyConceptionSystem() {
   }

   static void tick(ServerWorld world) {
      if (world != null && !world.getPlayers().isEmpty() && world.getTime() % 20L == 0L) {
         for (ServerPlayerEntity player : world.getPlayers()) {
            if (isCycleEnabledFor(player)) {
               updateCycle(player, world, true);
               tryConception(world, player);
            }
         }
      }
   }

   static void syncState(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld() instanceof ServerWorld world) {
         if (!isCycleEnabledFor(player)) {
            ServerPlayNetworking.send(player, new PregnancyCycleStateS2CPayload(false, 0, PregnancyCyclePhase.NORMAL));
         } else {
            updateCycle(player, world, false);
            int day = getCycleDay(player);
            ServerPlayNetworking.send(player, new PregnancyCycleStateS2CPayload(true, day, resolvePhase(day)));
         }
      }
   }

   static String describeCycle(ServerPlayerEntity player) {
      if (player != null && isCycleEnabledFor(player)) {
         if (player.getEntityWorld() instanceof ServerWorld world) {
            updateCycle(player, world, false);
         }

         int day = getCycleDay(player);
         return "pregnancy cycle: day " + day + "/30 (" + resolvePhase(day).displayName() + ")";
      } else {
         return "pregnancy cycle: disabled";
      }
   }

   static void copyState(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
      if (oldPlayer instanceof PregnancyCycleHolder oldHolder && newPlayer instanceof PregnancyCycleHolder newHolder) {
         if (!oldHolder.isPregnancyCycleInitialized()) {
            newHolder.clearPregnancyCycleState();
         } else {
            newHolder.setPregnancyCycleState(
               oldHolder.getPregnancyCycleProgressTicks(), oldHolder.getPregnancyCycleLastGameTick(), oldHolder.getPregnancyCycleLastTimeOfDay()
            );
         }
      }
   }

   static void recordRecentDonor(LiquidHolder holder, Identifier entityTypeId, NeedsOfNature.PregnancyVariantData variantData) {
      if (holder != null && holder.getLiquidStored() > 0 && entityTypeId != null && !PLAYER_ENTITY_TYPE_ID.equals(entityTypeId)) {
         holder.recordRecentLiquidDonor(entityTypeId, variantData);
      }
   }

   static boolean setCycleDay(ServerPlayerEntity player, int day) {
      if (player instanceof PregnancyCycleHolder holder && player.getEntityWorld() instanceof ServerWorld world) {
         ServerWorld clockWorld = world.getServer() != null && world.getServer().getOverworld() != null ? world.getServer().getOverworld() : world;
         int clampedDay = Math.max(1, Math.min(30, day));
         long progressTicks = (clampedDay - 1) * 24000L;
         holder.setPregnancyCycleState(progressTicks, Math.max(0L, clockWorld.getTime()), Math.max(0L, clockWorld.getTimeOfDay()));
         syncState(player);
         return true;
      } else {
         return false;
      }
   }

   private static void tryConception(ServerWorld world, ServerPlayerEntity player) {
      NonConfig config = NeedsOfNature.getConfig();
      if (config != null && config.isPregnancyEnabled() && config.isLiquidBasedPregnancyEnabled()) {
         if (config.isLiquidTankEnabled() && player.isAlive() && !player.isRemoved() && !player.isSpectator()) {
            if (NeedsOfNature.getPregnantEntityTypeId(player) == null && !NonTrinketsIntegration.blocksPregnancy(player)) {
               if (player instanceof LiquidHolder holder) {
                  if (holder.getLiquidStored() > 0 && holder.getLiquidCapacity() > 0) {
                     Identifier sourceType;
                     NeedsOfNature.PregnancyVariantData sourceVariantData;
                     if (holder.getLiquidComposition() == LiquidHolder.LiquidComposition.ENTITY) {
                        sourceType = holder.getLiquidEntityTypeId();
                        sourceVariantData = holder.getLiquidDonorVariantData();
                     } else {
                        if (holder.getLiquidComposition() != LiquidHolder.LiquidComposition.MIXED) {
                           return;
                        }

                        List<LiquidDonorHistory.Entry> donors = holder.getRecentLiquidDonors();
                        if (donors.isEmpty()) {
                           return;
                        }

                        LiquidDonorHistory.Entry selected = donors.get(world.getRandom().nextInt(donors.size()));
                        sourceType = selected.entityTypeId();
                        sourceVariantData = selected.variantData();
                     }

                     if (sourceType != null && !PLAYER_ENTITY_TYPE_ID.equals(sourceType)) {
                        int profileChance = NonEntityProfiles.resolve(sourceType).pregnancyChancePercent();
                        if (profileChance > 0) {
                           NonAccessoryEffects accessoryEffects = NonTrinketsIntegration.getActiveTankAccessoryEffects(player);
                           double fillRatio = Math.min(1.0, (double)holder.getLiquidStored() / holder.getLiquidCapacity());
                           int cycleDay = getCycleDay(player);
                           PregnancyCyclePhase phase = resolvePhase(cycleDay);

                           double cycleMultiplier = switch (phase) {
                              case SAFE -> config.getPregnancySafeDayChancePercent() / 100.0;
                              case NORMAL -> 1.0;
                              case RISKY -> config.getPregnancyRiskyChancePercent() / 100.0;
                              case OVULATION -> config.getPregnancyOvulationChancePercent() / 100.0;
                           };
                           double accessoryMultiplier = Math.max(0.0, accessoryEffects.pregnancyChanceMultiplier());
                           double naturalMinuteChance = profileChance / 100.0 * fillRatio * cycleMultiplier * accessoryMultiplier;
                           int fertilityFloor = NeedsOfNature.getFertilityIncreaserChanceFloor(player);
                           double potionMinuteChance = 0.0;
                           if (fertilityFloor > 0) {
                              potionMinuteChance = fertilityFloor / 100.0 * fillRatio * cycleMultiplier * accessoryMultiplier;
                              if (phase == PregnancyCyclePhase.RISKY || phase == PregnancyCyclePhase.OVULATION) {
                                 potionMinuteChance = Math.min(potionMinuteChance, fertilityFloor / 100.0 * fillRatio);
                              }
                           }

                           double minuteChance = Math.max(naturalMinuteChance, potionMinuteChance);
                           minuteChance = Math.max(0.0, Math.min(1.0, minuteChance));
                           if (!(minuteChance <= 0.0)) {
                              double checkChance = minuteChance >= 1.0 ? 1.0 : 1.0 - Math.pow(1.0 - minuteChance, 0.016666666666666666);
                              if (!(world.getRandom().nextDouble() >= checkChance)) {
                                 long durationTicks = Math.max(
                                    1L, Math.round(config.getPregnancyDurationTicks() * Math.max(0.01, accessoryEffects.pregnancyDurationMultiplier()))
                                 );
                                 NeedsOfNature.beginPregnancy(player, sourceType, NeedsOfNature.getGlobalTick(world) + durationTicks, sourceVariantData);
                                 if (sourceType.equals(NeedsOfNature.getPregnantEntityTypeId(player))) {
                                    NonStats.incrementPregnancyStarted(player);
                                    NonAdvancementHooks.grantFirstPregnancy(player);
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

   private static boolean isCycleEnabledFor(ServerPlayerEntity player) {
      NonConfig config = NeedsOfNature.getConfig();
      return player != null && config != null && config.isPregnancyEnabled() && config.isLiquidBasedPregnancyEnabled() && !NonGenderSystem.isOnlyMale(player);
   }

   private static void updateCycle(ServerPlayerEntity player, ServerWorld world, boolean syncOnDayChange) {
      if (player instanceof PregnancyCycleHolder holder) {
         ServerWorld clockWorld = world.getServer() != null && world.getServer().getOverworld() != null ? world.getServer().getOverworld() : world;
         long nowGameTick = Math.max(0L, clockWorld.getTime());
         long nowTimeOfDay = Math.max(0L, clockWorld.getTimeOfDay());
         if (!holder.isPregnancyCycleInitialized()) {
            long initialProgress = Math.floorMod(world.getRandom().nextLong(), 720000L);
            holder.setPregnancyCycleState(initialProgress, nowGameTick, nowTimeOfDay);
            if (syncOnDayChange) {
               syncState(player);
            }
         } else {
            int oldDay = getCycleDay(holder.getPregnancyCycleProgressTicks());
            long gameDelta = nonNegativeDelta(nowGameTick, holder.getPregnancyCycleLastGameTick());
            long daytimeDelta = nonNegativeDelta(nowTimeOfDay, holder.getPregnancyCycleLastTimeOfDay());
            long elapsed = Math.max(gameDelta, daytimeDelta);
            long progress = Math.floorMod(holder.getPregnancyCycleProgressTicks() + Math.floorMod(elapsed, 720000L), 720000L);
            holder.setPregnancyCycleState(progress, nowGameTick, nowTimeOfDay);
            if (syncOnDayChange && oldDay != getCycleDay(progress)) {
               syncState(player);
            }
         }
      }
   }

   private static long nonNegativeDelta(long current, long previous) {
      return current >= previous ? current - previous : 0L;
   }

   private static int getCycleDay(ServerPlayerEntity player) {
      return player instanceof PregnancyCycleHolder holder && holder.isPregnancyCycleInitialized() ? getCycleDay(holder.getPregnancyCycleProgressTicks()) : 1;
   }

   private static int getCycleDay(long progressTicks) {
      long normalized = Math.floorMod(progressTicks, 720000L);
      return (int)(normalized / 24000L) + 1;
   }

   private static PregnancyCyclePhase resolvePhase(int day) {
      NonConfig config = NeedsOfNature.getConfig();
      if (config != null) {
         int riskyStartDay = config.getPregnancyRiskyStartDay();
         int ovulationEndDay = config.getPregnancyOvulationEndDay();
         int ovulationStartDay = Math.max(riskyStartDay, ovulationEndDay - 1);
         if (day >= ovulationStartDay && day <= ovulationEndDay) {
            return PregnancyCyclePhase.OVULATION;
         }

         if (day >= riskyStartDay && day < ovulationStartDay) {
            return PregnancyCyclePhase.RISKY;
         }
      }

      return day > 5 && day < 26 ? PregnancyCyclePhase.NORMAL : PregnancyCyclePhase.SAFE;
   }
}
