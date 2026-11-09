package com.nonid;

import com.nonid.network.DestroyedSkinMaskSyncS2CPayload;
import com.nonid.network.DestroyedSkinMaskUploadC2SPayload;
import com.nonid.network.DestroyedSkinParticlesS2CPayload;
import com.nonid.network.DestroyedSkinStateS2CPayload;
import com.nonid.network.DestroyedSkinSyncS2CPayload;
import com.nonid.network.DestroyedSkinUploadC2SPayload;
import com.nonid.network.ManualDamageDestroyedSkinC2SPayload;
import com.nonid.network.RepairDestroyedSkinC2SPayload;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

final class NonDestroyedSkinSystem {
   private static final int DESTROYED_SKIN_MAX_DAMAGE = 10;
   private static final int DESTROYED_SKIN_STAGE_1_DAMAGE = 1;
   private static final int DESTROYED_SKIN_STAGE_2_DAMAGE = 4;
   private static final int DESTROYED_SKIN_STAGE_3_DAMAGE = 7;
   private static final int DESTROYED_SKIN_STAGE_4_DAMAGE = 10;
   private static final int DESTROYED_SKIN_REPAIR_COOLDOWN_TICKS = 7;
   private static final int UPLOAD_COOLDOWN_TICKS = 20;
   private static final Map<UUID, byte[]> DESTROYED_SKINS_BY_PLAYER = new ConcurrentHashMap<>();
   private static final Map<UUID, byte[][]> DESTROYED_SKIN_MASKS_BY_PLAYER = new ConcurrentHashMap<>();
   private static final Map<UUID, Integer> DESTROYED_SKIN_STAGE_BY_PLAYER = new ConcurrentHashMap<>();
   private static final Map<UUID, Long> DESTROYED_SKIN_REPAIR_COOLDOWNS = new ConcurrentHashMap<>();
   private static final Map<NonDestroyedSkinSystem.UploadKey, Long> NEXT_UPLOAD_TICK = new ConcurrentHashMap<>();

   private NonDestroyedSkinSystem() {
   }

   static void syncDestroyedSkins(ServerPlayerEntity player) {
      if (player != null) {
         MinecraftServer server = player.getEntityWorld().getServer();
         if (server != null) {
            boolean enabled = NeedsOfNature.isDestroyedSkinSystemEnabled();

            for (ServerPlayerEntity listedPlayer : server.getPlayerManager().getPlayerList()) {
               UUID uuid = listedPlayer.getUuid();
               if (!enabled) {
                  sendDestroyedSkinStage(player, uuid, 0);
               } else {
                  byte[] skin = DESTROYED_SKINS_BY_PLAYER.get(uuid);
                  if (skin != null && skin.length > 0) {
                     sendDestroyedSkin(player, uuid, getDestroyedSkinStage(uuid), skin);
                  } else {
                     sendDestroyedSkinStage(player, uuid, getDestroyedSkinStage(uuid));
                  }

                  byte[][] masks = DESTROYED_SKIN_MASKS_BY_PLAYER.get(uuid);
                  if (masks != null) {
                     for (int stage = 1; stage < Math.min(masks.length, 4); stage++) {
                        byte[] bytes = masks[stage];
                        if (bytes != null && bytes.length > 0) {
                           sendDestroyedSkinMask(player, uuid, stage, bytes);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   static void registerDestroyedSkinNetworking() {
      ServerPlayNetworking.registerGlobalReceiver(
         DestroyedSkinUploadC2SPayload.ID,
         (payload, context) -> context.server()
            .execute(
               () -> {
                  ServerPlayerEntity player = context.player();
                  if (player != null) {
                     if (NeedsOfNature.isDestroyedSkinSystemEnabled()) {
                        byte[] bytes = payload.pngBytes();
                        UUID playerUuid = player.getUuid();
                        if (acceptUpload(player, new NonDestroyedSkinSystem.UploadKey(playerUuid, 0))) {
                           if (!isValidDestroyedSkinPayload(bytes)) {
                              NeedsOfNature.LOGGER
                                 .warn(
                                    "[NoN] Ignoring invalid destroyed skin upload from {} ({} bytes).",
                                    player.getName().getString(),
                                    bytes == null ? 0 : bytes.length
                                 );
                           } else {
                              byte[] previous = DESTROYED_SKINS_BY_PLAYER.get(playerUuid);
                              if (!Arrays.equals(previous, bytes)) {
                                 DESTROYED_SKINS_BY_PLAYER.put(playerUuid, (byte[])bytes.clone());
                                 NeedsOfNature.logRuntime(
                                    "[NoN] Received destroyed skin upload from {} ({} bytes, stage {}).",
                                    player.getName().getString(),
                                    bytes.length,
                                    getDestroyedSkinStage(playerUuid)
                                 );
                                 broadcastDestroyedSkin(context.server(), playerUuid, getDestroyedSkinStage(playerUuid), bytes);
                              }
                           }
                        }
                     }
                  }
               }
            )
      );
      ServerPlayNetworking.registerGlobalReceiver(
         DestroyedSkinMaskUploadC2SPayload.ID,
         (payload, context) -> context.server()
            .execute(
               () -> {
                  ServerPlayerEntity player = context.player();
                  if (player != null) {
                     if (NeedsOfNature.isDestroyedSkinSystemEnabled()) {
                        int stage = payload.stage();
                        byte[] bytes = payload.pngBytes();
                        if (stage > 0 && stage < 4 && bytes != null) {
                           UUID playerUuid = player.getUuid();
                           if (acceptUpload(player, new NonDestroyedSkinSystem.UploadKey(playerUuid, stage))) {
                              byte[][] existingMasks = DESTROYED_SKIN_MASKS_BY_PLAYER.get(playerUuid);
                              byte[] previous = existingMasks == null ? null : existingMasks[stage];
                              if (!Arrays.equals(previous, bytes.length == 0 ? null : bytes)) {
                                 if (bytes.length == 0) {
                                    byte[][] masks = existingMasks;
                                    if (masks != null && stage < masks.length) {
                                       masks[stage] = null;
                                       if (!hasAnyMask(masks)) {
                                          DESTROYED_SKIN_MASKS_BY_PLAYER.remove(playerUuid);
                                       }
                                    }

                                    broadcastDestroyedSkinMask(context.server(), playerUuid, stage, bytes);
                                 } else if (!isValidDestroyedSkinMaskPayload(bytes)) {
                                    NeedsOfNature.LOGGER
                                       .warn(
                                          "[NoN] Ignoring invalid destroyed skin mask upload from {} (stage {}, {} bytes).",
                                          new Object[]{player.getName().getString(), stage, bytes.length}
                                       );
                                 } else {
                                    byte[][] masks = DESTROYED_SKIN_MASKS_BY_PLAYER.computeIfAbsent(playerUuid, ignored -> new byte[4][]);
                                    masks[stage] = (byte[])bytes.clone();
                                    NeedsOfNature.logRuntime(
                                       "[NoN] Received destroyed skin mask upload from {} (stage {}, {} bytes).",
                                       player.getName().getString(),
                                       stage,
                                       bytes.length
                                    );
                                    broadcastDestroyedSkinMask(context.server(), playerUuid, stage, bytes);
                                 }
                              }
                           }
                        } else {
                           NeedsOfNature.LOGGER
                              .warn(
                                 "[NoN] Ignoring invalid destroyed skin mask upload from {} (stage {}, {} bytes).",
                                 new Object[]{player.getName().getString(), stage, bytes == null ? 0 : bytes.length}
                              );
                        }
                     }
                  }
               }
            )
      );
   }

   static void registerDestroyedSkinRepairNetworking() {
      ServerPlayNetworking.registerGlobalReceiver(
         RepairDestroyedSkinC2SPayload.ID, (payload, context) -> context.server().execute(() -> repairDestroyedSkin(context.player()))
      );
      ServerPlayNetworking.registerGlobalReceiver(
         ManualDamageDestroyedSkinC2SPayload.ID, (payload, context) -> context.server().execute(() -> manuallyDamageDestroyedSkin(context.player()))
      );
   }

   private static void repairDestroyedSkin(ServerPlayerEntity player) {
      if (NeedsOfNature.isDestroyedSkinSystemEnabled()) {
         if (player != null && player.isAlive() && player instanceof DestroyedSkinHolder holder) {
            if (player.getEntityWorld() instanceof ServerWorld world) {
               if (NonSkinRepairBlocks.isNearRepairBlock(world, player.getBlockPos(), NeedsOfNature.getConfig())) {
                  UUID uuid = player.getUuid();
                  long now = world.getTime();
                  long blockedUntil = DESTROYED_SKIN_REPAIR_COOLDOWNS.getOrDefault(uuid, 0L);
                  if (now >= blockedUntil) {
                     int beforeDamage = clampDestroyedSkinDamage(holder.getDestroyedSkinDamage());
                     if (beforeDamage <= 0) {
                        DESTROYED_SKIN_REPAIR_COOLDOWNS.remove(uuid);
                     } else {
                        int afterDamage = clampDestroyedSkinDamage(beforeDamage - 1);
                        holder.setDestroyedSkinDamage(afterDamage);
                        setDestroyedSkinStage(player, destroyedSkinStageForDamage(afterDamage));
                        playDestroyedSkinRepairSound(world, player);
                        if (afterDamage > 0) {
                           DESTROYED_SKIN_REPAIR_COOLDOWNS.put(uuid, now + 7L);
                        } else {
                           DESTROYED_SKIN_REPAIR_COOLDOWNS.remove(uuid);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void manuallyDamageDestroyedSkin(ServerPlayerEntity player) {
      if (NeedsOfNature.isDestroyedSkinSystemEnabled()) {
         if (player != null && player.isAlive()) {
            if (player.getEntityWorld() instanceof ServerWorld world) {
               if (hasShearsForManualDestroyedSkinDamage(player)) {
                  int beforeDamage = player instanceof DestroyedSkinHolder holder ? clampDestroyedSkinDamage(holder.getDestroyedSkinDamage()) : 10;
                  applyDestroyedSkinAttackDamage(world, player);
                  int afterDamage = player instanceof DestroyedSkinHolder holder ? clampDestroyedSkinDamage(holder.getDestroyedSkinDamage()) : beforeDamage;
                  if (afterDamage > beforeDamage) {
                     playDestroyedSkinShearSound(world, player);
                  }
               }
            }
         }
      }
   }

   private static boolean hasShearsForManualDestroyedSkinDamage(ServerPlayerEntity player) {
      if (player == null) {
         return false;
      }

      ItemStack cursorStack = player.currentScreenHandler != null ? player.currentScreenHandler.getCursorStack() : ItemStack.EMPTY;
      return cursorStack.isOf(Items.SHEARS) || player.getMainHandStack().isOf(Items.SHEARS) || player.getOffHandStack().isOf(Items.SHEARS);
   }

   private static boolean isValidDestroyedSkinPayload(byte[] bytes) {
      if (bytes != null && bytes.length > 0 && bytes.length <= 1048576) {
         int[] dimensions = readPngDimensions(bytes);
         if (dimensions == null) {
            return false;
         }

         int width = dimensions[0];
         int height = dimensions[1];
         return width == height && width >= 64 && width <= 512 && width % 64 == 0 && hasValidPngChunkStructure(bytes);
      } else {
         return false;
      }
   }

   private static boolean isValidDestroyedSkinMaskPayload(byte[] bytes) {
      if (bytes != null && bytes.length > 0 && bytes.length <= 65536) {
         int[] dimensions = readPngDimensions(bytes);
         return dimensions != null && dimensions[0] == 64 && dimensions[1] == 64 && hasValidPngChunkStructure(bytes);
      } else {
         return false;
      }
   }

   private static boolean acceptUpload(ServerPlayerEntity player, NonDestroyedSkinSystem.UploadKey key) {
      long now = player.getEntityWorld().getTime();
      long next = NEXT_UPLOAD_TICK.getOrDefault(key, Long.MIN_VALUE);
      if (now < next) {
         return false;
      }

      NEXT_UPLOAD_TICK.put(key, now + 20L);
      return true;
   }

   private static boolean hasValidPngChunkStructure(byte[] bytes) {
      int offset = 8;
      boolean sawIdat = false;
      boolean first = true;

      while (offset + 12 <= bytes.length) {
         long length = Integer.toUnsignedLong(readBigEndianInt(bytes, offset));
         if (length > bytes.length - offset - 12L) {
            return false;
         }

         int typeOffset = offset + 4;
         boolean ihdr = chunkTypeEquals(bytes, typeOffset, 'I', 'H', 'D', 'R');
         boolean idat = chunkTypeEquals(bytes, typeOffset, 'I', 'D', 'A', 'T');
         boolean iend = chunkTypeEquals(bytes, typeOffset, 'I', 'E', 'N', 'D');
         if (first && (!ihdr || length != 13L)) {
            return false;
         }

         first = false;
         sawIdat |= idat;
         offset += Math.toIntExact(length + 12L);
         if (iend) {
            return length == 0L && sawIdat && offset == bytes.length;
         }
      }

      return false;
   }

   private static boolean chunkTypeEquals(byte[] bytes, int offset, char a, char b, char c, char d) {
      return offset >= 0
         && offset + 4 <= bytes.length
         && bytes[offset] == (byte)a
         && bytes[offset + 1] == (byte)b
         && bytes[offset + 2] == (byte)c
         && bytes[offset + 3] == (byte)d;
   }

   static void clearPlayerRuntimeState(MinecraftServer server, UUID playerUuid) {
      if (playerUuid != null) {
         broadcastDestroyedSkin(server, playerUuid, 0, new byte[0]);

         for (int stage = 1; stage < 4; stage++) {
            broadcastDestroyedSkinMask(server, playerUuid, stage, new byte[0]);
         }

         broadcastDestroyedSkinStage(server, playerUuid, 0);
         DESTROYED_SKINS_BY_PLAYER.remove(playerUuid);
         DESTROYED_SKIN_MASKS_BY_PLAYER.remove(playerUuid);
         DESTROYED_SKIN_STAGE_BY_PLAYER.remove(playerUuid);
         DESTROYED_SKIN_REPAIR_COOLDOWNS.remove(playerUuid);
         NEXT_UPLOAD_TICK.keySet().removeIf(key -> playerUuid.equals(key.playerUuid()));
      }
   }

   static void clearRuntimeState() {
      DESTROYED_SKINS_BY_PLAYER.clear();
      DESTROYED_SKIN_MASKS_BY_PLAYER.clear();
      DESTROYED_SKIN_STAGE_BY_PLAYER.clear();
      DESTROYED_SKIN_REPAIR_COOLDOWNS.clear();
      NEXT_UPLOAD_TICK.clear();
   }

   private static boolean hasAnyMask(byte[][] masks) {
      if (masks == null) {
         return false;
      }

      for (int stage = 1; stage < masks.length; stage++) {
         if (masks[stage] != null && masks[stage].length > 0) {
            return true;
         }
      }

      return false;
   }

   private static int[] readPngDimensions(byte[] bytes) {
      if (bytes == null || bytes.length < 24) {
         return null;
      } else if ((bytes[0] & 255) == 137
         && bytes[1] == 80
         && bytes[2] == 78
         && bytes[3] == 71
         && bytes[4] == 13
         && bytes[5] == 10
         && bytes[6] == 26
         && bytes[7] == 10
         && bytes[8] == 0
         && bytes[9] == 0
         && bytes[10] == 0
         && bytes[11] == 13
         && bytes[12] == 73
         && bytes[13] == 72
         && bytes[14] == 68
         && bytes[15] == 82) {
         int width = readBigEndianInt(bytes, 16);
         int height = readBigEndianInt(bytes, 20);
         return width > 0 && height > 0 ? new int[]{width, height} : null;
      } else {
         return null;
      }
   }

   private static int readBigEndianInt(byte[] bytes, int offset) {
      return (bytes[offset] & 0xFF) << 24 | (bytes[offset + 1] & 0xFF) << 16 | (bytes[offset + 2] & 0xFF) << 8 | bytes[offset + 3] & 0xFF;
   }

   private static int getDestroyedSkinStage(UUID playerUuid) {
      return playerUuid == null ? 0 : Math.max(0, Math.min(4, DESTROYED_SKIN_STAGE_BY_PLAYER.getOrDefault(playerUuid, 0)));
   }

   static int getDestroyedSkinStageForPlayer(ServerPlayerEntity player) {
      if (player == null) {
         return 0;
      } else if (!NeedsOfNature.isDestroyedSkinSystemEnabled()) {
         return 0;
      } else {
         return player instanceof DestroyedSkinHolder holder
            ? Math.max(0, Math.min(4, holder.getDestroyedSkinStage()))
            : getDestroyedSkinStage(player.getUuid());
      }
   }

   static void broadcastDestroyedSkinStage(ServerPlayerEntity player) {
      if (player != null) {
         MinecraftServer server = player.getEntityWorld().getServer();
         if (server != null) {
            broadcastDestroyedSkinStage(server, player.getUuid(), getDestroyedSkinStageForPlayer(player));
         }
      }
   }

   static void syncDestroyedSkinStageToOtherPlayers(ServerPlayerEntity player) {
      if (player != null) {
         MinecraftServer server = player.getEntityWorld().getServer();
         if (server != null) {
            int stage = getDestroyedSkinStageForPlayer(player);

            for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
               if (!target.getUuid().equals(player.getUuid())) {
                  sendDestroyedSkinStage(target, player.getUuid(), stage);
               }
            }
         }
      }
   }

   static void cacheDestroyedSkinStage(ServerPlayerEntity player) {
      if (player != null) {
         if (!NeedsOfNature.isDestroyedSkinSystemEnabled()) {
            DESTROYED_SKIN_STAGE_BY_PLAYER.remove(player.getUuid());
         } else if (player instanceof DestroyedSkinHolder holder) {
            int damage = clampDestroyedSkinDamage(holder.getDestroyedSkinDamage());
            int stage = damage > 0 ? destroyedSkinStageForDamage(damage) : Math.max(0, Math.min(4, holder.getDestroyedSkinStage()));
            if (stage == 0) {
               DESTROYED_SKIN_STAGE_BY_PLAYER.remove(player.getUuid());
            } else {
               DESTROYED_SKIN_STAGE_BY_PLAYER.put(player.getUuid(), stage);
            }
         }
      }
   }

   static void setDestroyedSkinStage(ServerPlayerEntity player, int stage) {
      if (player != null) {
         UUID uuid = player.getUuid();
         if (!NeedsOfNature.isDestroyedSkinSystemEnabled()) {
            clearDestroyedSkinStoredState(player);
            if (player.getEntityWorld() instanceof ServerWorld world) {
               broadcastDestroyedSkinStage(world.getServer(), uuid, 0);
            }
         } else {
            int clamped = Math.max(0, Math.min(4, stage));
            if (player instanceof DestroyedSkinHolder holder) {
               holder.setDestroyedSkinStage(clamped);
            }

            if (clamped == 0) {
               DESTROYED_SKIN_STAGE_BY_PLAYER.remove(uuid);
            } else {
               DESTROYED_SKIN_STAGE_BY_PLAYER.put(uuid, clamped);
            }

            if (player.getEntityWorld() instanceof ServerWorld world) {
               broadcastDestroyedSkinStage(world.getServer(), uuid, clamped);
            }

            NonApiInternals.fireDestroyedSkinChanged(player);
            NeedsOfNature.checkBadIdeaBeaconAdvancement(player);
         }
      }
   }

   static void applyDestroyedSkinAttackDamage(ServerWorld world, ServerPlayerEntity player) {
      if (NeedsOfNature.isDestroyedSkinSystemEnabled()) {
         if (world != null && player != null && player instanceof DestroyedSkinHolder holder) {
            int damageGain = rollDestroyedSkinDamage(world, player);
            applyDestroyedSkinDamage(world, player, holder, damageGain);
         }
      }
   }

   static void applyDestroyedSkinVoluntaryJoinInitialDamage(ServerWorld world, ServerPlayerEntity player) {
      if (NeedsOfNature.isDestroyedSkinSystemEnabled()) {
         if (world != null && player != null && player instanceof DestroyedSkinHolder holder) {
            int beforeDamage = clampDestroyedSkinDamage(holder.getDestroyedSkinDamage());
            if (beforeDamage == 0) {
               applyDestroyedSkinDamage(world, player, holder, 1);
            }
         }
      }
   }

   private static void applyDestroyedSkinDamage(ServerWorld world, ServerPlayerEntity player, DestroyedSkinHolder holder, int damageGain) {
      if (world != null && player != null && holder != null && damageGain > 0) {
         int beforeDamage = clampDestroyedSkinDamage(holder.getDestroyedSkinDamage());
         int afterDamage = clampDestroyedSkinDamage(beforeDamage + damageGain);
         if (afterDamage != beforeDamage) {
            holder.setDestroyedSkinDamage(afterDamage);
            int beforeStage = destroyedSkinStageForDamage(beforeDamage);
            int afterStage = destroyedSkinStageForDamage(afterDamage);
            setDestroyedSkinStage(player, afterStage);
            playDestroyedSkinRipSound(world, player);
            broadcastDestroyedSkinRipParticles(world, player);
            if (afterStage != beforeStage) {
               NeedsOfNature.logRuntime("[NoN] {} destroyed skin advanced to stage {} ({}/{}).", player.getName().getString(), afterStage, afterDamage, 10);
            }
         }
      }
   }

   private static int rollDestroyedSkinDamage(ServerWorld world, ServerPlayerEntity player) {
      double multiplier = NonTrinketsIntegration.getAccessoryEffects(player).destroyedSkinDamageMultiplier();
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

   static void resetDestroyedSkinDamage(ServerPlayerEntity player) {
      if (player != null) {
         if (player instanceof DestroyedSkinHolder holder) {
            holder.setDestroyedSkinDamage(0);
         }

         setDestroyedSkinStage(player, 0);
      }
   }

   static void clearDestroyedSkinStoredState(ServerPlayerEntity player) {
      if (player != null) {
         UUID uuid = player.getUuid();
         if (player instanceof DestroyedSkinHolder holder) {
            holder.setDestroyedSkinDamage(0);
            holder.setDestroyedSkinStage(0);
         }

         DESTROYED_SKIN_STAGE_BY_PLAYER.remove(uuid);
         DESTROYED_SKIN_REPAIR_COOLDOWNS.remove(uuid);
      }
   }

   private static int clampDestroyedSkinDamage(int damage) {
      return Math.max(0, Math.min(10, damage));
   }

   private static int destroyedSkinStageForDamage(int damage) {
      int clamped = clampDestroyedSkinDamage(damage);
      if (clamped >= 10) {
         return 4;
      } else if (clamped >= 7) {
         return 3;
      } else if (clamped >= 4) {
         return 2;
      } else {
         return clamped >= 1 ? 1 : 0;
      }
   }

   static int destroyedSkinDamageForStage(int stage) {
      return switch (Math.max(0, Math.min(4, stage))) {
         case 1 -> 1;
         case 2 -> 4;
         case 3 -> 7;
         case 4 -> 10;
         default -> 0;
      };
   }

   private static void playDestroyedSkinRipSound(ServerWorld world, ServerPlayerEntity player) {
      if (world != null && player != null) {
         int index = 1 + world.getRandom().nextInt(3);
         String soundPath = "rip" + String.format(Locale.ROOT, "%02d", index);
         float pitch = 0.94F + world.getRandom().nextFloat() * 0.12F;
         world.playSound(
            null,
            player.getX(),
            player.getY() + player.getStandingEyeHeight() * 0.5,
            player.getZ(),
            (SoundEvent)Registries.SOUND_EVENT.get(NeedsOfNature.id(soundPath)),
            SoundCategory.PLAYERS,
            0.75F,
            pitch
         );
      }
   }

   private static void playDestroyedSkinShearSound(ServerWorld world, ServerPlayerEntity player) {
      if (world != null && player != null) {
         world.playSound(
            null,
            player.getX(),
            player.getY() + player.getStandingEyeHeight() * 0.5,
            player.getZ(),
            SoundEvents.ENTITY_SHEEP_SHEAR,
            SoundCategory.PLAYERS,
            0.8F,
            0.95F + world.getRandom().nextFloat() * 0.1F
         );
      }
   }

   private static void playDestroyedSkinRepairSound(ServerWorld world, ServerPlayerEntity player) {
      if (world != null && player != null) {
         world.playSound(
            null,
            player.getX(),
            player.getY() + player.getStandingEyeHeight() * 0.5,
            player.getZ(),
            SoundEvents.BLOCK_WOOL_PLACE,
            SoundCategory.PLAYERS,
            1.35F,
            1.15F + world.getRandom().nextFloat() * 0.15F
         );
      }
   }

   private static void broadcastDestroyedSkinRipParticles(ServerWorld world, ServerPlayerEntity player) {
      if (world != null && player != null) {
         int count = 3 + world.getRandom().nextInt(4);
         long seed = world.getRandom().nextLong();
         double x = player.getX();
         double y = player.getY() + player.getHeight() * 0.55;
         double z = player.getZ();
         DestroyedSkinParticlesS2CPayload payload = new DestroyedSkinParticlesS2CPayload(player.getUuid(), x, y, z, count, seed);

         for (ServerPlayerEntity target : world.getPlayers()) {
            if (!(target.squaredDistanceTo(player) > 9216.0) && ServerPlayNetworking.canSend(target, DestroyedSkinParticlesS2CPayload.ID)) {
               ServerPlayNetworking.send(target, payload);
            }
         }
      }
   }

   private static void broadcastDestroyedSkin(MinecraftServer server, UUID playerUuid, int stage, byte[] bytes) {
      if (server != null && playerUuid != null && bytes != null) {
         for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            sendDestroyedSkin(target, playerUuid, stage, bytes);
         }
      }
   }

   private static void broadcastDestroyedSkinMask(MinecraftServer server, UUID playerUuid, int stage, byte[] bytes) {
      if (server != null && playerUuid != null && bytes != null) {
         for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            sendDestroyedSkinMask(target, playerUuid, stage, bytes);
         }
      }
   }

   private static void broadcastDestroyedSkinStage(MinecraftServer server, UUID playerUuid, int stage) {
      if (server != null && playerUuid != null) {
         for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            sendDestroyedSkinStage(target, playerUuid, stage);
         }
      }
   }

   private static void sendDestroyedSkin(ServerPlayerEntity target, UUID playerUuid, int stage, byte[] bytes) {
      if (target != null && playerUuid != null && bytes != null) {
         if (ServerPlayNetworking.canSend(target, DestroyedSkinSyncS2CPayload.ID)) {
            if (!NeedsOfNature.isDestroyedSkinSystemEnabled()) {
               stage = 0;
            }

            ServerPlayNetworking.send(target, new DestroyedSkinSyncS2CPayload(playerUuid, stage, bytes));
         }
      }
   }

   private static void sendDestroyedSkinMask(ServerPlayerEntity target, UUID playerUuid, int stage, byte[] bytes) {
      if (target != null && playerUuid != null && bytes != null) {
         if (stage > 0 && stage < 4) {
            if (ServerPlayNetworking.canSend(target, DestroyedSkinMaskSyncS2CPayload.ID)) {
               ServerPlayNetworking.send(target, new DestroyedSkinMaskSyncS2CPayload(playerUuid, stage, bytes));
            }
         }
      }
   }

   private static void sendDestroyedSkinStage(ServerPlayerEntity target, UUID playerUuid, int stage) {
      if (target != null && playerUuid != null) {
         if (ServerPlayNetworking.canSend(target, DestroyedSkinStateS2CPayload.ID)) {
            if (!NeedsOfNature.isDestroyedSkinSystemEnabled()) {
               stage = 0;
            }

            ServerPlayNetworking.send(target, new DestroyedSkinStateS2CPayload(playerUuid, stage));
         }
      }
   }

   private record UploadKey(UUID playerUuid, int maskStage) {
   }
}
