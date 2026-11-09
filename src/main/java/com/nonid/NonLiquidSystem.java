package com.nonid;

import com.nonid.client.NonLiquidColors;
import com.nonid.data.NonLiquidGainOverrides;
import com.nonid.effect.NonStatusEffects;
import com.nonid.network.LiquidStateS2CPayload;
import com.nonid.particle.NonParticles;
import com.nonid.potion.NonPotions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

final class NonLiquidSystem {
   private static final double LIQUID_SNEAK_DECAY_MULTIPLIER = 16.0;
   private static final double LIQUID_WATER_DECAY_MULTIPLIER = 2.0;
   private static final double LIQUID_DECAY_MIN_MULT = 0.1;
   private static final double LIQUID_DECAY_MAX_MULT = 1.5;
   private static final double LIQUID_SNEAK_PARTICLE_BACK_OFFSET = 0.28;
   private static final Map<UUID, Double> LIQUID_DECAY_REMAINDER = new ConcurrentHashMap<>();

   private NonLiquidSystem() {
   }

   static void clearRuntimeState() {
      LIQUID_DECAY_REMAINDER.clear();
   }

   static void syncLiquidState(ServerPlayerEntity player) {
      if (player != null) {
         if (player instanceof LiquidHolder holder) {
            enforceLiquidCapacity(holder);
            int stored = holder.getLiquidStored();
            int capacity = holder.getLiquidCapacity();
            updateFilledEffect(player, holder);
            int tint = resolveLiquidTintRgb(holder);
            ServerPlayNetworking.send(player, new LiquidStateS2CPayload(stored, capacity, tint));
            NonApiInternals.fireLiquidChanged(player);
            NonMessSystem.broadcastMessState(player);
            NonAdvancementHooks.checkLiquidAdvancements(player, holder);
         }
      }
   }

   private static void enforceLiquidCapacity(LiquidHolder holder) {
      if (holder != null) {
         int capacity = holder.getLiquidCapacity();
         int stored = holder.getLiquidStored();
         if (capacity <= 0) {
            if (stored > 0) {
               holder.setLiquidStored(0);
            }
         } else {
            if (stored > capacity) {
               holder.setLiquidStored(capacity);
            }
         }
      }
   }

   static void syncLiquidStateToAll(@Nullable MinecraftServer server) {
      if (server != null) {
         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            syncLiquidState(player);
         }
      }
   }

   static void refreshLiquidBottleTintsToAll(@Nullable MinecraftServer server) {
      if (server != null) {
         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            refreshLiquidBottleTints(player);
         }
      }
   }

   private static void refreshLiquidBottleTints(ServerPlayerEntity player) {
      if (player != null) {
         PlayerInventory inventory = player.getInventory();

         for (int i = 0; i < inventory.size(); i++) {
            refreshLiquidBottleTint(inventory.getStack(i));
         }

         refreshLiquidBottleTint(player.currentScreenHandler.getCursorStack());
      }
   }

   private static void refreshLiquidBottleTint(ItemStack stack) {
      if (NonPotions.isLiquidBottle(stack)) {
         Identifier entityTypeId = NonPotions.getLiquidBottleEntityTypeId(stack);
         int tint = resolveBottleTintRgb(entityTypeId);
         PotionContentsComponent current = (PotionContentsComponent)stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
         PotionContentsComponent updated = new PotionContentsComponent(current.potion(), Optional.of(tint), List.of(), Optional.empty());
         stack.set(DataComponentTypes.POTION_CONTENTS, updated);
      }
   }

   static int resolveLiquidTintRgb(LiquidHolder holder) {
      if (holder != null && holder.getLiquidStored() > 0) {
         return holder.getLiquidComposition() == LiquidHolder.LiquidComposition.ENTITY
            ? resolveEntityLiquidColorRgb(holder.getLiquidEntityTypeId())
            : resolveMixedLiquidColorRgb();
      } else {
         return resolveMixedLiquidColorRgb();
      }
   }

   static int resolveBottleTintRgb(@Nullable Identifier entityTypeId) {
      return entityTypeId == null ? resolveMixedLiquidColorRgb() : resolveEntityLiquidColorRgb(entityTypeId);
   }

   private static int resolveEntityLiquidColorRgb(@Nullable Identifier entityTypeId) {
      if (entityTypeId == null) {
         return 16776427;
      }

      String key = entityTypeId.toString();
      String configuredHex = NeedsOfNature.getConfig().getLiquidColorByEntity().get(key);
      Integer configuredRgb = NonLiquidColors.parseHexColor(configuredHex);
      if (configuredRgb != null) {
         return configuredRgb;
      }

      Integer packOverride = NonLiquidGainOverrides.getColorRgb(entityTypeId);
      return packOverride != null ? packOverride : 16776427;
   }

   private static int resolveMixedLiquidColorRgb() {
      String configuredHex = NeedsOfNature.getConfig().getLiquidColorByEntity().get("needsofnature:mixed");
      Integer configuredRgb = NonLiquidColors.parseHexColor(configuredHex);
      if (configuredRgb != null) {
         return configuredRgb;
      }

      Integer packOverride = NonLiquidGainOverrides.getMixedColorRgb();
      return packOverride != null ? packOverride : 15920063;
   }

   static Map<String, Integer> resolveEffectiveLiquidGainMap() {
      Map<String, Integer> merged = new LinkedHashMap<>(NonLiquidGainOverrides.getGainByEntity());
      merged.putAll(NeedsOfNature.getConfig().getLiquidGainByEntity());
      return Map.copyOf(merged);
   }

   static Map<String, String> resolveEffectiveLiquidColorMap() {
      Map<String, String> merged = new LinkedHashMap<>(NonLiquidGainOverrides.getColorByEntity());
      String packMixedHex = NonLiquidGainOverrides.getMixedColorHex();
      if (packMixedHex != null) {
         merged.put("needsofnature:mixed", packMixedHex);
      }

      merged.putAll(NeedsOfNature.getConfig().getLiquidColorByEntity());
      return Map.copyOf(merged);
   }

   static void tickLiquidDecay(ServerWorld world) {
      if (world != null) {
         if (!world.getPlayers().isEmpty()) {
            NonConfig config = NeedsOfNature.getConfig();
            boolean liquidTankEnabled = config.isLiquidTankEnabled();
            double decayPerSecond = config.getLiquidDecayPerSecond();

            for (ServerPlayerEntity player : world.getPlayers()) {
               if (player instanceof LiquidHolder holder) {
                  if (!liquidTankEnabled) {
                     LIQUID_DECAY_REMAINDER.remove(player.getUuid());
                     if (holder.getLiquidStored() > 0) {
                        holder.setLiquidStored(0);
                        syncLiquidState(player);
                     }

                     updateFilledEffect(player, holder);
                  } else {
                     updateFilledEffect(player, holder);
                     if (!(decayPerSecond <= 0.0) && NeedsOfNature.getActivePlayerInstance(player) == null) {
                        int stored = holder.getLiquidStored();
                        if (stored <= 0) {
                           LIQUID_DECAY_REMAINDER.remove(player.getUuid());
                           updateFilledEffect(player, holder);
                        } else {
                           NonAccessoryEffects accessoryEffects = NonTrinketsIntegration.getActiveTankAccessoryEffects(player);
                           double decayPerTick = decayPerSecond / 20.0;
                           boolean equalizedDecay = accessoryEffects.equalizeLiquidDecayContext();
                           if (!equalizedDecay && player.isSneaking()) {
                              decayPerTick *= 16.0;
                           }

                           if (!equalizedDecay && (player.isTouchingWater() || player.isSubmergedInWater())) {
                              decayPerTick *= 2.0;
                           }

                           int capacity = holder.getLiquidCapacity();
                           double fillRatio = 0.0;
                           if (capacity > 0) {
                              fillRatio = Math.min(1.0, Math.max(0.0, (double)stored / capacity));
                           }

                           double curve = 1.0 - Math.log1p((1.0 - fillRatio) * 9.0) / Math.log1p(9.0);
                           double decayMultiplier = 0.1 + 1.4 * curve;
                           decayPerTick *= decayMultiplier;
                           decayPerTick *= accessoryEffects.liquidDecayMultiplier();
                           double remainder = LIQUID_DECAY_REMAINDER.getOrDefault(player.getUuid(), 0.0);
                           double accum = remainder + decayPerTick;
                           int decay = (int)Math.floor(accum);
                           remainder = accum - decay;
                           LIQUID_DECAY_REMAINDER.put(player.getUuid(), remainder);
                           if (decay > 0) {
                              int next = Math.max(0, stored - decay);
                              if (next != stored) {
                                 holder.setLiquidStored(next);
                                 syncLiquidState(player);
                              }

                              spawnLiquidDecayParticles(world, player);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   static void updateFilledEffect(ServerPlayerEntity player, LiquidHolder holder) {
      if (player != null && holder != null) {
         int amplifier = getAmplifier(holder);
         RegistryEntry<StatusEffect> entry = Registries.STATUS_EFFECT.getEntry(NonStatusEffects.FILLED);
         StatusEffectInstance current = player.getStatusEffect(entry);
         if (amplifier < 0) {
            player.removeStatusEffect(entry);
         } else {
            if (current != null) {
               if (current.getAmplifier() == amplifier) {
                  return;
               }

               player.removeStatusEffect(entry);
            }

            player.addStatusEffect(new StatusEffectInstance(entry, -1, amplifier, false, false, true));
         }
      }
   }

   private static int getAmplifier(LiquidHolder holder) {
      int capacity = holder.getLiquidCapacity();
      int stored = holder.getLiquidStored();
      int amplifier = -1;
      if (capacity > 0) {
         float ratio = Math.max(0.0F, Math.min(1.0F, (float)stored / capacity));
         if (ratio >= 0.8F) {
            amplifier = 2;
         } else if (ratio >= 0.5F) {
            amplifier = 1;
         } else if (ratio >= 0.3F) {
            amplifier = 0;
         }
      }

      return amplifier;
   }

   private static void spawnLiquidDecayParticles(ServerWorld world, ServerPlayerEntity player) {
      if (world != null && player != null) {
         int tintRgb = player instanceof LiquidHolder holder ? resolveLiquidTintRgb(holder) : resolveBottleTintRgb(null);
         Vec3d spawnPos = resolveLiquidDecayParticlePos(player);
         world.spawnParticles(NonParticles.liquidFalling(tintRgb), spawnPos.x, spawnPos.y, spawnPos.z, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   private static Vec3d resolveLiquidDecayParticlePos(ServerPlayerEntity player) {
      double x = player.getX();
      double z = player.getZ();
      if (player.isSneaking()) {
         double yawRad = Math.toRadians(player.bodyYaw);
         x += Math.sin(yawRad) * 0.28;
         z -= Math.cos(yawRad) * 0.28;
      }

      return new Vec3d(x, player.getBodyY(0.4), z);
   }
}
