package com.nonid;

import com.nonid.data.NonLiquidRoles;
import com.nonid.network.MessStateS2CPayload;
import com.nonid.particle.NonParticles;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

final class NonMessSystem {
   private static final Map<UUID, Long> MESS_CLEAN_NEXT_TICKS = new ConcurrentHashMap<>();

   private NonMessSystem() {
   }

   static void clearRuntimeState() {
      MESS_CLEAN_NEXT_TICKS.clear();
   }

   static void incrementMessOnPeak(
      ServerWorld world, List<UUID> actorUuids, List<String> actorKeys, Identifier animationId, Set<NonLiquidRoles.InjectorRole> suppliedInjectorRoles
   ) {
      if (NeedsOfNature.isMessSystemEnabled()) {
         if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
            if (suppliedInjectorRoles != null && !suppliedInjectorRoles.isEmpty()) {
               boolean hasV = suppliedInjectorRoles.contains(NonLiquidRoles.InjectorRole.V);
               boolean hasA = suppliedInjectorRoles.contains(NonLiquidRoles.InjectorRole.A);
               boolean hasM = suppliedInjectorRoles.contains(NonLiquidRoles.InjectorRole.M);
               if (hasV || hasA || hasM) {
                  for (ServerPlayerEntity player : NeedsOfNature.collectPlayerReceiverActors(world, actorUuids, actorKeys, animationId)) {
                     if (player instanceof MessHolder holder) {
                        normalizeMaleOnlyMess(player, false);
                        int messGain = rollMessGain(world, player);
                        if (messGain > 0) {
                           if (hasV) {
                              if (NonGenderSystem.isOnlyMale(player)) {
                                 if (NeedsOfNature.getConfig().convertMaleOnlyVInjectionsToA()) {
                                    holder.setAMess(clampMess(holder.getAMess() + messGain));
                                 }
                              } else {
                                 holder.setVMess(clampMess(holder.getVMess() + messGain));
                              }
                           }

                           if (hasA) {
                              holder.setAMess(clampMess(holder.getAMess() + messGain));
                           }

                           if (hasM) {
                              holder.setMMess(clampMess(holder.getMMess() + messGain));
                           }

                           broadcastMessState(player);
                           if (holder.getVMess() >= 10 && holder.getAMess() >= 10 && holder.getMMess() >= 10) {
                              NonAdvancementHooks.grantMessedUp(player);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static int rollMessGain(ServerWorld world, ServerPlayerEntity player) {
      double multiplier = NonTrinketsIntegration.getActiveTankAccessoryEffects(player).messGainMultiplier();
      if (Double.isFinite(multiplier) && !(multiplier <= 0.0)) {
         int whole = (int)Math.floor(multiplier);
         double fractional = multiplier - whole;
         if (fractional > 0.0 && world.getRandom().nextDouble() < fractional) {
            whole++;
         }

         return Math.max(0, whole);
      } else {
         return 0;
      }
   }

   static void clearMess(ServerPlayerEntity player) {
      if (player != null && player instanceof MessHolder holder) {
         holder.setVMess(0);
         holder.setAMess(0);
         holder.setMMess(0);
         MESS_CLEAN_NEXT_TICKS.remove(player.getUuid());
         broadcastMessState(player);
      }
   }

   static void clearMessStoredState(ServerPlayerEntity player) {
      if (player != null && player instanceof MessHolder holder) {
         holder.setVMess(0);
         holder.setAMess(0);
         holder.setMMess(0);
         MESS_CLEAN_NEXT_TICKS.remove(player.getUuid());
      }
   }

   static int clampMess(int value) {
      return Math.max(0, Math.min(10, value));
   }

   static void tickMessCleaning(ServerWorld world, long now) {
      if (NeedsOfNature.isMessSystemEnabled()) {
         if (world != null) {
            for (ServerPlayerEntity player : world.getPlayers()) {
               if (player instanceof MessHolder holder) {
                  normalizeMaleOnlyMess(player, false);
                  UUID playerUuid = player.getUuid();
                  if (!hasAnyMess(holder)) {
                     MESS_CLEAN_NEXT_TICKS.remove(playerUuid);
                  } else {
                     boolean inWater = player.isTouchingWater() || player.isSubmergedInWater();
                     boolean inRain = !inWater && isExposedToRain(world, player);
                     if (!inWater && !inRain) {
                        MESS_CLEAN_NEXT_TICKS.remove(playerUuid);
                     } else {
                        Long scheduledTick = MESS_CLEAN_NEXT_TICKS.get(playerUuid);
                        if (scheduledTick == null) {
                           MESS_CLEAN_NEXT_TICKS.put(playerUuid, now + 20L);
                        } else {
                           long nextTick = scheduledTick;
                           if (now >= nextTick) {
                              boolean changed = decrementMess(holder);
                              if (changed) {
                                 broadcastMessState(player);
                                 if (inWater) {
                                    spawnMessCleaningWaterParticles(world, player);
                                 }
                              }

                              if (hasAnyMess(holder)) {
                                 MESS_CLEAN_NEXT_TICKS.put(playerUuid, now + 20L);
                              } else {
                                 MESS_CLEAN_NEXT_TICKS.remove(playerUuid);
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

   static boolean normalizeMaleOnlyMess(Entity entity, boolean convertVToA) {
      if (NonGenderSystem.isOnlyMale(entity) && entity instanceof MessHolder holder) {
         int v = clampMess(holder.getVMess());
         if (v <= 0) {
            return false;
         }

         holder.setVMess(0);
         if (convertVToA) {
            holder.setAMess(clampMess(holder.getAMess() + v));
         }

         return true;
      } else {
         return false;
      }
   }

   static void broadcastMessState(ServerPlayerEntity player) {
      if (player != null) {
         NonApiInternals.fireMessChanged(player);
         MinecraftServer server = player.getEntityWorld().getServer();
         if (server != null) {
            for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
               sendMessState(target, player);
            }
         }
      }
   }

   static void syncMessStates(ServerPlayerEntity target) {
      if (target != null) {
         MinecraftServer server = target.getEntityWorld().getServer();
         if (server != null) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
               sendMessState(target, player);
            }
         }
      }
   }

   private static void sendMessState(ServerPlayerEntity target, ServerPlayerEntity player) {
      if (target != null && player != null) {
         if (ServerPlayNetworking.canSend(target, MessStateS2CPayload.ID)) {
            int v = 0;
            int a = 0;
            int m = 0;
            if (NeedsOfNature.isMessSystemEnabled() && player instanceof MessHolder holder) {
               normalizeMaleOnlyMess(player, false);
               v = clampMess(holder.getVMess());
               a = clampMess(holder.getAMess());
               m = clampMess(holder.getMMess());
            }

            int tint = 16777215;
            int stored = 0;
            int capacity = 0;
            if (player instanceof LiquidHolder liquidHolder) {
               tint = NonLiquidSystem.resolveLiquidTintRgb(liquidHolder);
               stored = Math.max(0, liquidHolder.getLiquidStored());
               capacity = Math.max(0, liquidHolder.getLiquidCapacity());
            }

            ServerPlayNetworking.send(target, new MessStateS2CPayload(player.getUuid(), v, a, m, tint, stored, capacity));
         }
      }
   }

   private static boolean hasAnyMess(MessHolder holder) {
      return holder != null && (holder.getVMess() > 0 || holder.getAMess() > 0 || holder.getMMess() > 0);
   }

   private static boolean decrementMess(MessHolder holder) {
      if (holder == null) {
         return false;
      }

      int beforeV = clampMess(holder.getVMess());
      int beforeA = clampMess(holder.getAMess());
      int beforeM = clampMess(holder.getMMess());
      if (beforeV <= 0 && beforeA <= 0 && beforeM <= 0) {
         return false;
      }

      holder.setVMess(Math.max(0, beforeV - 1));
      holder.setAMess(Math.max(0, beforeA - 1));
      holder.setMMess(Math.max(0, beforeM - 1));
      return true;
   }

   private static boolean isExposedToRain(ServerWorld world, ServerPlayerEntity player) {
      return world != null && player != null && world.isRaining() ? world.hasRain(player.getBlockPos()) : false;
   }

   private static void spawnMessCleaningWaterParticles(ServerWorld world, ServerPlayerEntity player) {
      if (world != null && player != null) {
         int tintRgb = player instanceof LiquidHolder holder ? NonLiquidSystem.resolveLiquidTintRgb(holder) : NonLiquidSystem.resolveBottleTintRgb(null);
         int count = 2 + world.getRandom().nextInt(3);
         double y = player.getBodyY(0.45);

         for (int i = 0; i < count; i++) {
            double ox = (world.getRandom().nextDouble() - 0.5) * player.getWidth();
            double oy = (world.getRandom().nextDouble() - 0.5) * 0.45;
            double oz = (world.getRandom().nextDouble() - 0.5) * player.getWidth();
            world.spawnParticles(NonParticles.liquidWater(tintRgb), player.getX() + ox, y + oy, player.getZ() + oz, 1, 0.0, -0.01, 0.0, 0.0);
         }
      }
   }
}
