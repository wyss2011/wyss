package com.nonid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

final class NonAuraSystem {
   private static final int MESS_AURA_MAX_TOTAL = 15;
   private static final double MESS_AURA_MAX_MULTIPLIER = 10.0;

   private NonAuraSystem() {
   }

   static void tickPlayerEnergyAura(ServerWorld world, ServerPlayerEntity player) {
      if (world != null && player != null && player.isAlive() && !player.isRemoved()) {
         if (player instanceof EnergyHolder playerEnergyHolder) {
            NonConfig config = NeedsOfNature.getConfig();
            if (config != null) {
               int pulseTicks = config.getPlayerEnergyAuraPulseTicks();
               if (pulseTicks > 0 && world.getTime() % pulseTicks == 0L) {
                  int playerEnergy = playerEnergyHolder.getEnergy();
                  int destroyedStage = config.isDestroyedSkinSystemEnabled() ? NeedsOfNature.getDestroyedSkinStageForPlayer(player) : 0;
                  int messTotal = config.isMessSystemEnabled() ? NeedsOfNature.getTotalMessForPlayer(player) : 0;
                  if (config.isMessSystemEnabled()) {
                     NeedsOfNature.checkBadIdeaBeaconAdvancement(player);
                  }

                  double auraMultiplier = getCombinedPlayerAuraMultiplier(config, playerEnergy, destroyedStage, messTotal)
                     * NonTrinketsIntegration.getAccessoryEffects(player).playerEnergyAuraMultiplier();
                  if (Double.isFinite(auraMultiplier) && !(auraMultiplier <= 1.0)) {
                     double radius = getCombinedPlayerAuraRadius(config, playerEnergy, destroyedStage, messTotal);
                     if (Double.isFinite(radius) && !(radius <= 0.0)) {
                        double radiusSq = radius * radius;
                        Box searchBox = player.getBoundingBox().expand(radius);
                        List<MobEntity> mobs = world.getEntitiesByClass(
                           MobEntity.class,
                           searchBox,
                           mobx -> mobx.isAlive() && !mobx.isRemoved() && mobx.getEntityWorld() == world && mobx.squaredDistanceTo(player) <= radiusSq
                        );
                        if (!mobs.isEmpty()) {
                           for (MobEntity mob : mobs) {
                              if (mob instanceof EnergyHolder holder) {
                                 holder.ensureEnergyInitialized(world);
                                 int currentEnergy = holder.getEnergy();
                                 int maxEnergy = holder.getMaxEnergy();
                                 if (currentEnergy >= maxEnergy) {
                                    holder.setEnergyAuraCarry(0.0);
                                 } else if (hasPairAnimationWithPlayer(mob, player)) {
                                    double baseGain = getExpectedMobEnergyGainPerPulse(world, mob, holder, pulseTicks);
                                    if (Double.isFinite(baseGain) && !(baseGain <= 0.0)) {
                                       double bonusGain = baseGain * (auraMultiplier - 1.0);
                                       double totalGain = holder.getEnergyAuraCarry() + bonusGain;
                                       int rawApplied = MathHelper.floor(totalGain);
                                       if (rawApplied <= 0) {
                                          holder.setEnergyAuraCarry(totalGain);
                                       } else {
                                          int appliedGain = Math.min(rawApplied, maxEnergy - currentEnergy);
                                          if (appliedGain <= 0) {
                                             holder.setEnergyAuraCarry(0.0);
                                          } else {
                                             holder.setEnergy(currentEnergy + appliedGain);
                                             holder.setEnergyAuraCarry(appliedGain < rawApplied ? 0.0 : totalGain - appliedGain);
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
         }
      }
   }

   static NonAuraSystem.PlayerEnergyAuraDebug getPlayerEnergyAuraDebug(ServerWorld world, MobEntity mob, EnergyHolder holder) {
      if (world != null && mob != null && holder != null) {
         NonConfig config = NeedsOfNature.getConfig();
         if (config == null) {
            return null;
         }

         NonAuraSystem.PlayerEnergyAuraDebug best = null;

         for (ServerPlayerEntity player : world.getPlayers()) {
            if (player != null && player.isAlive() && !player.isRemoved() && player instanceof EnergyHolder playerEnergyHolder) {
               int playerEnergy = playerEnergyHolder.getEnergy();
               int destroyedStage = config.isDestroyedSkinSystemEnabled() ? NeedsOfNature.getDestroyedSkinStageForPlayer(player) : 0;
               int messTotal = config.isMessSystemEnabled() ? NeedsOfNature.getTotalMessForPlayer(player) : 0;
               double radius = getCombinedPlayerAuraRadius(config, playerEnergy, destroyedStage, messTotal);
               if (Double.isFinite(radius) && !(radius <= 0.0) && !(mob.squaredDistanceTo(player) > radius * radius) && hasPairAnimationWithPlayer(mob, player)
                  )
                {
                  double totalMultiplier = getCombinedPlayerAuraMultiplier(config, playerEnergy, destroyedStage, messTotal)
                     * NonTrinketsIntegration.getAccessoryEffects(player).playerEnergyAuraMultiplier();
                  if (Double.isFinite(totalMultiplier) && !(totalMultiplier <= 1.0) && (best == null || totalMultiplier > best.totalMultiplier())) {
                     best = new NonAuraSystem.PlayerEnergyAuraDebug(player, totalMultiplier, playerEnergy, destroyedStage, messTotal, radius);
                  }
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private static double getCombinedPlayerAuraMultiplier(NonConfig config, int playerEnergy, int destroyedStage, int messTotal) {
      if (config == null) {
         return 1.0;
      }

      double energyBonus = Math.max(0.0, getPlayerEnergyAuraMultiplier(config, playerEnergy) - 1.0);
      double destroyedBonus = Math.max(0.0, getDestroyedSkinAuraMultiplier(config, destroyedStage) - 1.0);
      double messBonus = Math.max(0.0, getMessAuraMultiplier(messTotal) - 1.0);
      return 1.0 + energyBonus + destroyedBonus + messBonus;
   }

   private static double getPlayerEnergyAuraMultiplier(NonConfig config, int playerEnergy) {
      if (config == null || playerEnergy < 100) {
         return 1.0;
      } else if (playerEnergy >= 200) {
         return config.getPlayerEnergyAuraMultHigh();
      } else {
         return playerEnergy >= 150 ? config.getPlayerEnergyAuraMultMid() : config.getPlayerEnergyAuraMultLow();
      }
   }

   private static double getDestroyedSkinAuraMultiplier(NonConfig config, int stage) {
      if (config == null) {
         return 1.0;
      }

      return switch (stage) {
         case 1 -> config.getDestroyedSkinAuraMultStage1();
         case 2 -> config.getDestroyedSkinAuraMultStage2();
         case 3 -> config.getDestroyedSkinAuraMultStage3();
         case 4 -> config.getDestroyedSkinAuraMultStage4();
         default -> 1.0;
      };
   }

   private static double getMessAuraMultiplier(int messTotal) {
      if (messTotal <= 0) {
         return 1.0;
      }

      double progress = MathHelper.clamp(messTotal / 15.0, 0.0, 1.0);
      return MathHelper.lerp(progress, 1.0, 10.0);
   }

   private static double getPlayerEnergyAuraRadius(NonConfig config, int playerEnergy) {
      if (config != null && playerEnergy >= 100) {
         double minRadius = config.getPlayerEnergyAuraRadiusMin();
         double maxRadius = config.getPlayerEnergyAuraRadiusMax();
         double progress = MathHelper.clamp((playerEnergy - 100) / 100.0, 0.0, 1.0);
         return MathHelper.lerp(progress, minRadius, maxRadius);
      } else {
         return 0.0;
      }
   }

   private static double getCombinedPlayerAuraRadius(NonConfig config, int playerEnergy, int destroyedStage, int messTotal) {
      if (config == null) {
         return 0.0;
      } else if (playerEnergy >= 100) {
         return getPlayerEnergyAuraRadius(config, playerEnergy);
      } else {
         return destroyedStage <= 0 && messTotal <= 0 ? 0.0 : config.getPlayerEnergyAuraRadiusMin();
      }
   }

   private static boolean hasPairAnimationWithPlayer(MobEntity mob, ServerPlayerEntity player) {
      if (mob != null && player != null) {
         List<Entity> pair = new ArrayList<>(2);
         pair.add(mob);
         pair.add(player);
         pair.sort(Comparator.comparingInt(Entity::getId));
         return NonAnimationMatcher.findAutomatic(player.getEntityWorld(), pair, Set.of(), null, false, null, Set.of()) != null;
      } else {
         return false;
      }
   }

   private static double getExpectedMobEnergyGainPerPulse(ServerWorld world, MobEntity mob, EnergyHolder holder, int pulseTicks) {
      if (world != null && mob != null && holder != null && pulseTicks > 0) {
         double nearAnimationMultiplier = NeedsOfNature.getNearbyVisibleAnimationGainMultiplier(world, mob);
         double scale = NonMobEnergySystem.passiveGainScale(
            mob, holder.getEnergyGainMultiplier(), holder.getEnergyGainDrift(), nearAnimationMultiplier > 1.0, nearAnimationMultiplier
         );
         return scale <= 0.0 ? 0.0 : pulseTicks * scale / 120.0;
      } else {
         return 0.0;
      }
   }

   record PlayerEnergyAuraDebug(ServerPlayerEntity player, double totalMultiplier, int playerEnergy, int destroyedSkinStage, int messTotal, double radius) {
   }
}
