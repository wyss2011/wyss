package com.nonid.client;

import com.nonid.DestroyedSkinHolder;
import com.nonid.GenderHolder;
import com.nonid.NeedsOfNature;
import com.nonid.NeedsOfNatureClient;
import com.nonid.NonConfig;
import com.nonid.NonTrinketsIntegration;
import com.nonid.client.particle.RippedFabricParticle;
import com.nonid.network.DestroyedSkinMaskUploadC2SPayload;
import com.nonid.network.DestroyedSkinUploadC2SPayload;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.NativeImage.Format;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

public final class NonDestroyedSkinClient {
   private static final int MIN_STAGE = 0;
   private static final int MAX_STAGE = 4;
   private static final int EXTERNAL_SKIN_SELECTION_POLL_TICKS = 20;
   private static final long TEXTURE_READ_RETRY_NANOS = 1000000000L;
   private static final int FALLBACK_FABRIC_RGB = 15920872;
   private static final int FALLBACK_SKIN_TINT_RGB = 13803641;
   private static final Identifier RIPPED_FABRIC_COLOR_MASK = Identifier.of("needsofnature", "textures/particle/ripped_fabric_color_mask.png");
   private static final Identifier DEFAULT_DESTROYED_TEXTURE_F = Identifier.of("needsofnature", "textures/player_destroyed/destroyed_texture_f.png");
   private static final Identifier DEFAULT_DESTROYED_TEXTURE_M = Identifier.of("needsofnature", "textures/player_destroyed/destroyed_texture_m.png");
   private static final Identifier DEFAULT_DESTROYED_TEXTURE_F_SLIM = Identifier.of("needsofnature", "textures/player_destroyed/destroyed_texture_f_slim.png");
   private static final Identifier DEFAULT_DESTROYED_TEXTURE_F_WIDE = Identifier.of("needsofnature", "textures/player_destroyed/destroyed_texture_f_wide.png");
   private static final Identifier DEFAULT_DESTROYED_TEXTURE_M_SLIM = Identifier.of("needsofnature", "textures/player_destroyed/destroyed_texture_m_slim.png");
   private static final Identifier DEFAULT_DESTROYED_TEXTURE_M_WIDE = Identifier.of("needsofnature", "textures/player_destroyed/destroyed_texture_m_wide.png");
   private static final String DEFAULT_PLAYER_SKIN_PREFIX = "textures/entity/player/";
   private static final String DEFAULT_DESTROYED_SKIN_PREFIX = "textures/player_destroyed/default/";
   private static final String[] VANILLA_DEFAULT_SKIN_NAMES = new String[]{"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"};
   private static final double DEFAULT_SKIN_PIXEL_MATCH_THRESHOLD = 0.9;
   private static final Identifier[] MASK_TEXTURES = new Identifier[]{
      null,
      Identifier.of("needsofnature", "textures/player_destroyed/mask_1.png"),
      Identifier.of("needsofnature", "textures/player_destroyed/mask_2.png"),
      Identifier.of("needsofnature", "textures/player_destroyed/mask_3.png")
   };
   private static final Identifier[][] MESS_MASK_TEXTURES = new Identifier[][]{
      {
            Identifier.of("needsofnature", "textures/entity/player/mess/v_mess1.png"),
            Identifier.of("needsofnature", "textures/entity/player/mess/v_mess2.png"),
            Identifier.of("needsofnature", "textures/entity/player/mess/v_mess3.png")
      },
      {
            Identifier.of("needsofnature", "textures/entity/player/mess/a_mess1.png"),
            Identifier.of("needsofnature", "textures/entity/player/mess/a_mess2.png"),
            Identifier.of("needsofnature", "textures/entity/player/mess/a_mess3.png")
      },
      {
            Identifier.of("needsofnature", "textures/entity/player/mess/m_mess1.png"),
            Identifier.of("needsofnature", "textures/entity/player/mess/m_mess2.png"),
            Identifier.of("needsofnature", "textures/entity/player/mess/m_mess3.png")
      }
   };
   private static final Identifier V_MESS_TANK_TEXTURE = Identifier.of("needsofnature", "textures/entity/player/mess/v_mess_tank.png");
   private static final Identifier A_MESS_TANK_TEXTURE = Identifier.of("needsofnature", "textures/entity/player/mess/a_mess_tank.png");
   private static final Map<UUID, byte[]> DESTROYED_SKINS = new HashMap<>();
   private static final Map<UUID, byte[][]> DESTROYED_SKIN_MASKS = new HashMap<>();
   private static final Map<UUID, Integer> STAGES = new HashMap<>();
   private static final Map<UUID, NonDestroyedSkinClient.MessState> MESS_STATES = new HashMap<>();
   private static final Map<NonDestroyedSkinClient.TextureKey, Identifier> TEXTURE_CACHE = new HashMap<>();
   private static final Map<NonDestroyedSkinClient.SkinColorKey, int[]> ORIGINAL_SKIN_COLOR_CACHE = new HashMap<>();
   private static final Map<UUID, NonDestroyedSkinClient.EffectiveBaseTexture> EFFECTIVE_BASE_TEXTURES = new HashMap<>();
   private static final Map<UUID, String> OBSERVED_EXTERNAL_SKINS = new HashMap<>();
   private static final Map<NonDestroyedSkinClient.TextureReadKey, Long> FAILED_TEXTURE_READS = new HashMap<>();
   private static final Set<NonDestroyedSkinClient.TextureReadKey> WARNED_TEXTURE_READS = new HashSet<>();
   private static int uploadRetryTicks = 0;
   private static boolean uploadedThisConnection = false;
   private static boolean uploadedMasksForSelection = false;
   private static boolean automaticSkinUnavailableThisConnection = false;
   @Nullable
   private static String lastUploadedAutoBaseSkinKey = null;
   private static int externalSkinSelectionPollTicks = 0;
   private static int skinOverrideCacheVersion = Integer.MIN_VALUE;

   private NonDestroyedSkinClient() {
   }

   public static void ensureConfigDirectory() {
      Path directory = configRoot();

      try {
         Files.createDirectories(directory);
      } catch (IOException e) {
         setupWarn("[NoN] Failed to create ripped-skin config directory {}.", directory, e);
      }
   }

   public static void scheduleLocalSkinUploadRetry() {
      uploadRetryTicks = 300;
      uploadedThisConnection = false;
      uploadedMasksForSelection = false;
      automaticSkinUnavailableThisConnection = false;
      lastUploadedAutoBaseSkinKey = null;
   }

   public static void clientTick(MinecraftClient client) {
      if (client != null) {
         boolean skinOverrideChanged = refreshSkinOverrideCacheVersion();
         pollExternalSkinSelections(client, skinOverrideChanged);
         if (uploadRetryTicks > 0) {
            uploadRetryTicks--;
            if (!ClientPlayNetworking.canSend(DestroyedSkinUploadC2SPayload.ID)) {
               if (uploadRetryTicks == 0) {
                  NeedsOfNature.LOGGER.info("[NoN] Destroyed skin upload retry expired: upload channel never became sendable.");
               }
            } else {
               boolean localCustomSkin = hasLocalCustomDestroyedSkin();
               if (localCustomSkin && uploadedThisConnection && uploadedMasksForSelection) {
                  uploadRetryTicks = 0;
               } else if (!localCustomSkin && automaticSkinUnavailableThisConnection) {
                  uploadedMasksForSelection = uploadedMasksForSelection | uploadLocalMasks();
                  if (uploadedMasksForSelection) {
                     uploadRetryTicks = 0;
                  }
               } else {
                  boolean uploadedSkin = uploadedThisConnection || uploadLocalSkinIfPresent();
                  if (!uploadedMasksForSelection) {
                     uploadedMasksForSelection = uploadLocalMasks();
                  }

                  uploadedThisConnection |= uploadedSkin;
                  if (localCustomSkin && uploadedThisConnection && uploadedMasksForSelection) {
                     uploadRetryTicks = 0;
                  } else if (uploadRetryTicks == 0 && !uploadedThisConnection && !automaticSkinUnavailableThisConnection) {
                     NeedsOfNature.LOGGER.info("[NoN] Destroyed skin upload retry expired without upload.");
                  }
               }
            }
         }
      }
   }

   public static boolean uploadLocalSkinIfPresent() {
      for (Path path : localOverrideCandidates("destroyed_skin.png")) {
         if (Files.isRegularFile(path)) {
            try {
               byte[] bytes = Files.readAllBytes(path);
               if (!isValidDestroyedSkinPng(bytes)) {
                  setupWarn("[NoN] Ignoring destroyed skin {}: expected a square PNG from 64x64 through 512x512 in 64-pixel steps.", path);
               } else if (ClientPlayNetworking.canSend(DestroyedSkinUploadC2SPayload.ID)) {
                  ClientPlayNetworking.send(new DestroyedSkinUploadC2SPayload(bytes));
                  return true;
               }
            } catch (IOException | RuntimeException e) {
               setupWarn("[NoN] Failed to upload local destroyed skin from {}.", path, e);
            }
         }
      }

      return uploadGeneratedSkinIfPossible();
   }

   public static boolean hasLocalCustomDestroyedSkin() {
      return firstExistingLocalOverride("destroyed_skin.png") != null;
   }

   public static boolean hasCurrentDefaultDestroyedSkin() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null) {
         Identifier baseSkin = client.player.getSkin().body().texturePath();
         if (baseSkin == null) {
            return false;
         }

         boolean defaultSkinPath = isLikelyDefaultPlayerSkin(baseSkin);
         boolean profileHasCustomTexture = hasProfileTextureProperties(client);
         if (defaultSkinPath && profileHasCustomTexture) {
            return false;
         }

         NonDestroyedSkinClient.DefaultSkinKey key = resolveDefaultSkinKey(client, baseSkin);
         return key == null ? false : readDefaultDestroyedSkin(client, key) != null;
      } else {
         return false;
      }
   }

   private static Path localDestroyedSkinPath() {
      List<Path> candidates = localOverrideCandidates("destroyed_skin.png");
      Path existing = firstExisting(candidates);
      return existing != null ? existing : candidates.getFirst();
   }

   private static List<Path> localOverrideCandidates(String fileName) {
      Path configRoot = configRoot();
      List<Path> candidates = new ArrayList<>(2);
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null) {
         UUID playerUuid = client.player.getUuid();
         NonDestroyedSkinClient.EffectiveBaseTexture effective = EFFECTIVE_BASE_TEXTURES.get(playerUuid);
         Identifier textureId = effective != null ? effective.id() : client.player.getSkin().body().texturePath();
         NonExternalSkinCompat.SkinContext selection = effective != null ? effective.externalSkin() : null;
         if (selection == null) {
            selection = NonExternalSkinCompat.resolve(client, playerUuid, textureId);
         }

         if (selection == null) {
            selection = NonExternalSkinCompat.resolveSelected(client, playerUuid);
         }

         if (selection != null && selection.folderName() != null) {
            candidates.add(configRoot.resolve(selection.folderName()).resolve(fileName));
         }
      }

      candidates.add(configRoot.resolve(fileName));
      return candidates;
   }

   private static Path configRoot() {
      return FabricLoader.getInstance().getConfigDir().resolve("needsofnature");
   }

   @Nullable
   private static Path firstExistingLocalOverride(String fileName) {
      return firstExisting(localOverrideCandidates(fileName));
   }

   @Nullable
   private static Path firstExisting(List<Path> candidates) {
      for (Path candidate : candidates) {
         if (Files.isRegularFile(candidate)) {
            return candidate;
         }
      }

      return null;
   }

   private static void pollExternalSkinSelections(MinecraftClient client, boolean force) {
      if ((force || externalSkinSelectionPollTicks-- <= 0) && client.world != null) {
         externalSkinSelectionPollTicks = 20;
         Set<UUID> presentPlayers = new HashSet<>();

         for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            UUID playerUuid = player.getUuid();
            presentPlayers.add(playerUuid);
            Identifier textureId = player.getSkin().body().texturePath();
            NonExternalSkinCompat.SkinContext selection = NonExternalSkinCompat.resolveSelected(client, playerUuid);
            String currentKey = observedSkinKey(textureId, selection);
            String previousKey = OBSERVED_EXTERNAL_SKINS.put(playerUuid, currentKey);
            if (previousKey != null && !Objects.equals(previousKey, currentKey)) {
               NonDestroyedSkinClient.EffectiveBaseTexture previousEffective = EFFECTIVE_BASE_TEXTURES.remove(playerUuid);
               if (previousEffective != null) {
                  removeOriginalSkinColors(previousEffective.id());
                  clearTextureReadFailures(previousEffective.id());
               }

               removeOriginalSkinColors(textureId);
               clearCachedTextures(playerUuid);
               clearTextureReadFailures(textureId);
               if (client.player != null && playerUuid.equals(client.player.getUuid())) {
                  if (selection != null) {
                     NeedsOfNature.LOGGER
                        .info(
                           "[NoN] {} skin selection changed to '{}'{}.",
                           new Object[]{
                              selection.providerId(),
                              selection.displayName(),
                              selection.folderName() != null ? " (config folder '" + selection.folderName() + "')" : ""
                           }
                        );
                  } else {
                     NeedsOfNature.LOGGER.info("[NoN] External skin override cleared; using the global ripped-skin configuration.");
                  }

                  scheduleLocalSkinUploadRetry();
               }
            }
         }

         OBSERVED_EXTERNAL_SKINS.keySet().removeIf(uuid -> !presentPlayers.contains(uuid));
      }
   }

   private static boolean refreshSkinOverrideCacheVersion() {
      int currentVersion = NonSkinOverrideCompat.cacheVersion();
      if (currentVersion < 0) {
         return false;
      }

      if (skinOverrideCacheVersion == Integer.MIN_VALUE) {
         skinOverrideCacheVersion = currentVersion;
         return false;
      }

      if (skinOverrideCacheVersion == currentVersion) {
         return false;
      }

      skinOverrideCacheVersion = currentVersion;
      return true;
   }

   private static String observedSkinKey(Identifier textureId, @Nullable NonExternalSkinCompat.SkinContext selection) {
      return selection != null ? selection.selectionKey() : "minecraft\u0000" + textureId;
   }

   private static boolean uploadGeneratedSkinIfPossible() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.player == null) {
         return false;
      }

      if (!ClientPlayNetworking.canSend(DestroyedSkinUploadC2SPayload.ID)) {
         return false;
      }

      if (automaticSkinUnavailableThisConnection) {
         return false;
      }

      Identifier baseSkin = client.player.getSkin().body().texturePath();
      if (baseSkin == null) {
         return false;
      }

      NonExternalSkinCompat.SkinContext externalSkin = NonExternalSkinCompat.resolve(client, client.player.getUuid(), baseSkin);
      String uploadKey = baseSkin + "\u0000" + textureIdentity(client, baseSkin) + "\u0000" + externalSkinKey(externalSkin);
      if (uploadKey.equals(lastUploadedAutoBaseSkinKey)) {
         return true;
      }

      boolean defaultSkinPath = isLikelyDefaultPlayerSkin(baseSkin);
      boolean profileHasCustomTexture = hasProfileTextureProperties(client);
      if (defaultSkinPath && profileHasCustomTexture) {
         return false;
      }

      NonDestroyedSkinClient.DefaultSkinKey defaultSkinKey = resolveDefaultSkinKey(client, baseSkin);
      if (defaultSkinKey != null) {
         byte[] defaultDestroyedSkin = readDefaultDestroyedSkin(client, defaultSkinKey);
         if (defaultDestroyedSkin != null && isValidDestroyedSkinPng(defaultDestroyedSkin)) {
            ClientPlayNetworking.send(new DestroyedSkinUploadC2SPayload(defaultDestroyedSkin));
            lastUploadedAutoBaseSkinKey = uploadKey;
            return true;
         }
      }

      try {
         byte[] bytes = createGeneratedDestroyedSkin(client, baseSkin);
         if (bytes != null && isValidDestroyedSkinPng(bytes)) {
            ClientPlayNetworking.send(new DestroyedSkinUploadC2SPayload(bytes));
            lastUploadedAutoBaseSkinKey = uploadKey;
            return true;
         } else {
            return false;
         }
      } catch (NonDestroyedSkinClient.MissingDestroyedSkinFallbackException e) {
         markAutomaticSkinUnavailable(
            "[NoN] No ripped skin fallback texture was found. Install the default NoN pack or provide {} to enable ripped skin visuals.",
            localDestroyedSkinPath()
         );
         return false;
      } catch (IOException | RuntimeException e) {
         setupWarn("[NoN] Failed to generate ripped skin fallback. Automatic ripped skin upload is disabled for this connection.", e);
         automaticSkinUnavailableThisConnection = true;
         uploadRetryTicks = 0;
         return false;
      }
   }

   private static boolean uploadLocalMasks() {
      if (!ClientPlayNetworking.canSend(DestroyedSkinMaskUploadC2SPayload.ID)) {
         return false;
      }

      for (int stage = 1; stage < 4; stage++) {
         byte[] selectedBytes = null;

         for (Path path : localOverrideCandidates("destroyed_skin_mask_" + stage + ".png")) {
            if (Files.isRegularFile(path)) {
               try {
                  byte[] bytes = Files.readAllBytes(path);
                  if (isValidMaskPng(bytes)) {
                     selectedBytes = bytes;
                     break;
                  }

                  setupWarn("[NoN] Ignoring destroyed skin mask {}: expected a 64x64 PNG.", path);
               } catch (IOException | RuntimeException e) {
                  setupWarn("[NoN] Failed to upload local destroyed skin mask from {}.", path, e);
               }
            }
         }

         ClientPlayNetworking.send(new DestroyedSkinMaskUploadC2SPayload(stage, selectedBytes != null ? selectedBytes : new byte[0]));
      }

      return true;
   }

   public static int detectSkinTintFromCurrentPlayer(int fallbackRgb) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null) {
         Identifier baseSkin = client.player.getSkin().body().texturePath();
         if (baseSkin == null) {
            return fallbackRgb & 16777215;
         }

         try {
            NativeImage image = readPlayerTextureImage(client, client.player.getUuid(), baseSkin);
            if (image != null && isSupportedStaticSkin(image)) {
               NativeImage var4 = image;

               int var6;
               try {
                  NativeImage normalized = scaledCopy(image, 64, 64);

                  try {
                     var6 = detectSkinTint(normalized);
                  } catch (Throwable var10) {
                     if (normalized != null) {
                        try {
                           normalized.close();
                        } catch (Throwable var9) {
                           var10.addSuppressed(var9);
                        }
                     }

                     throw var10;
                  }

                  if (normalized != null) {
                     normalized.close();
                  }
               } catch (Throwable var11) {
                  if (var4 != null) {
                     try {
                        var4.close();
                     } catch (Throwable var8) {
                        var11.addSuppressed(var8);
                     }
                  }

                  throw var11;
               }

               if (var4 != null) {
                  var4.close();
               }

               return var6;
            } else {
               if (image != null) {
                  image.close();
               }

               return fallbackRgb & 16777215;
            }
         } catch (IOException | RuntimeException e) {
            setupWarn("[NoN] Failed to auto-detect destroyed skin tint.", e);
            return fallbackRgb & 16777215;
         }
      } else {
         return fallbackRgb & 16777215;
      }
   }

   public static void clear() {
      DESTROYED_SKINS.clear();
      DESTROYED_SKIN_MASKS.clear();
      STAGES.clear();
      MESS_STATES.clear();
      clearResourceCaches();
      uploadRetryTicks = 0;
      uploadedThisConnection = false;
      uploadedMasksForSelection = false;
      automaticSkinUnavailableThisConnection = false;
      lastUploadedAutoBaseSkinKey = null;
      externalSkinSelectionPollTicks = 0;
      skinOverrideCacheVersion = Integer.MIN_VALUE;
      OBSERVED_EXTERNAL_SKINS.clear();
   }

   public static void clearResourceCaches() {
      MinecraftClient client = MinecraftClient.getInstance();
      List<Identifier> texturesToDestroy = new ArrayList<>(TEXTURE_CACHE.values());
      TEXTURE_CACHE.clear();
      ORIGINAL_SKIN_COLOR_CACHE.clear();
      EFFECTIVE_BASE_TEXTURES.clear();
      FAILED_TEXTURE_READS.clear();
      WARNED_TEXTURE_READS.clear();
      destroyTextures(client, texturesToDestroy);
   }

   public static void setSkin(UUID playerUuid, int stage, byte[] pngBytes) {
      if (playerUuid != null && pngBytes != null) {
         if (pngBytes.length == 0) {
            clearCachedTextures(playerUuid);
            DESTROYED_SKINS.remove(playerUuid);
            EFFECTIVE_BASE_TEXTURES.remove(playerUuid);
            setStage(playerUuid, stage);
         } else if (!isValidDestroyedSkinPng(pngBytes)) {
            setupWarn("[NoN] Ignoring synced destroyed skin for {}: expected a square PNG from 64x64 through 512x512 in 64-pixel steps.", playerUuid);
         } else {
            clearCachedTextures(playerUuid);
            DESTROYED_SKINS.put(playerUuid, (byte[])pngBytes.clone());
            setStage(playerUuid, stage);
         }
      }
   }

   public static void setMask(UUID playerUuid, int stage, byte[] pngBytes) {
      if (playerUuid != null && pngBytes != null) {
         if (stage > 0 && stage < 4) {
            if (pngBytes.length == 0) {
               byte[][] masks = DESTROYED_SKIN_MASKS.get(playerUuid);
               if (masks != null && stage < masks.length) {
                  masks[stage] = null;
                  boolean hasAny = false;

                  for (int index = 1; index < masks.length; index++) {
                     if (masks[index] != null) {
                        hasAny = true;
                        break;
                     }
                  }

                  if (!hasAny) {
                     DESTROYED_SKIN_MASKS.remove(playerUuid);
                  }
               }

               clearCachedTextures(playerUuid);
            } else if (!isValidMaskPng(pngBytes)) {
               setupWarn("[NoN] Ignoring synced destroyed skin mask for {} stage {}: expected a 64x64 PNG.", playerUuid, stage);
            } else {
               byte[][] masks = DESTROYED_SKIN_MASKS.computeIfAbsent(playerUuid, ignored -> new byte[4][]);
               masks[stage] = (byte[])pngBytes.clone();
               clearCachedTextures(playerUuid);
            }
         }
      }
   }

   public static void setStage(UUID playerUuid, int stage) {
      if (playerUuid != null) {
         int clamped = clampStage(stage);
         if (clamped <= 0) {
            STAGES.remove(playerUuid);
         } else {
            STAGES.put(playerUuid, clamped);
         }

         MinecraftClient client = MinecraftClient.getInstance();
         if (clamped > 0 && client != null && client.player != null && playerUuid.equals(client.player.getUuid()) && !DESTROYED_SKINS.containsKey(playerUuid)) {
            uploadLocalSkinIfPresent();
         }

         if (client != null && client.player != null && playerUuid.equals(client.player.getUuid())) {
            NonWildfireGenderSync.syncDestroyedSkinOverrides(NeedsOfNature.getLocalConfig(), clamped);
         }

         clearCachedTextures(playerUuid);
      }
   }

   public static int getStage(UUID playerUuid) {
      return playerUuid == null ? 0 : clampStage(STAGES.getOrDefault(playerUuid, 0));
   }

   @Nullable
   public static Integer getLiquidTintRgb(UUID playerUuid) {
      if (playerUuid == null) {
         return null;
      }

      NonDestroyedSkinClient.MessState state = MESS_STATES.get(playerUuid);
      return state == null ? null : state.tintRgb();
   }

   public static void setMess(UUID playerUuid, int vMess, int aMess, int mMess, int tintRgb, int liquidStored, int liquidCapacity) {
      if (playerUuid != null) {
         NonDestroyedSkinClient.MessState state = new NonDestroyedSkinClient.MessState(
            clampMess(vMess), clampMess(aMess), clampMess(mMess), tintRgb & 16777215, Math.max(0, liquidStored), Math.max(0, liquidCapacity)
         );
         if (!state.hasAnyVisual()) {
            MESS_STATES.remove(playerUuid);
         } else {
            MESS_STATES.put(playerUuid, state);
         }

         clearCachedTextures(playerUuid);
      }
   }

   @Nullable
   public static Identifier resolveBaseTexture(LivingEntity entity, Identifier currentBaseTexture) {
      int stage = getDestroyedStage(entity);
      NonDestroyedSkinClient.MessState messState = getMessState(entity);
      List<NonTrinketsIntegration.ResolvedVisualLayer> accessoryOverlays = NonAccessorySkinOverlays.resolveEquippedOverlays(entity);
      return stage <= 0 && !messState.hasAnyVisual() && accessoryOverlays.isEmpty() ? null : getOrCreateTexture(entity, stage, currentBaseTexture, false);
   }

   public static Identifier resolveRenderedTexture(LivingEntity entity, Identifier renderedTexture) {
      if (entity != null && renderedTexture != null && !isGeneratedTexture(renderedTexture)) {
         rememberEffectiveBaseTexture(entity, renderedTexture);
         Identifier replacement = resolveBaseTexture(entity, renderedTexture);
         return replacement != null ? replacement : renderedTexture;
      } else {
         return renderedTexture;
      }
   }

   @Nullable
   public static Identifier resolveOverlayTexture(LivingEntity entity) {
      int stage = getDestroyedStage(entity);
      return stage > 0 && stage < 4 ? getOrCreateTexture(entity, stage, null, true) : null;
   }

   public static void spawnRippedFabricParticles(UUID playerUuid, double x, double y, double z, int count, long seed) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.world != null && playerUuid != null && count > 0) {
         AbstractClientPlayerEntity player = null;

         for (AbstractClientPlayerEntity candidate : client.world.getPlayers()) {
            if (playerUuid.equals(candidate.getUuid())) {
               player = candidate;
               break;
            }
         }

         if (player != null) {
            Identifier baseSkin = player.getSkin().body().texturePath();
            int[] colors = getOriginalSkinColors(client, playerUuid, baseSkin);
            Random random = Random.create(seed);
            int particleCount = Math.max(1, Math.min(12, count));

            for (int i = 0; i < particleCount; i++) {
               int rgb = colors[random.nextInt(colors.length)];
               rgb = varyColor(rgb, random);
               double px = x + (random.nextDouble() - 0.5) * 0.16;
               double py = y + (random.nextDouble() - 0.5) * 0.2;
               double pz = z + (random.nextDouble() - 0.5) * 0.16;
               double vx = (random.nextDouble() - 0.5) * 0.001;
               double vy = -0.006 - random.nextDouble() * 0.004;
               double vz = (random.nextDouble() - 0.5) * 0.001;
               Particle particle = RippedFabricParticle.create(client.world, px, py, pz, vx, vy, vz, rgb, random);
               if (particle != null) {
                  client.particleManager.addParticle(particle);
               }
            }
         }
      }
   }

   private static int getDestroyedStage(LivingEntity entity) {
      if (!(entity instanceof PlayerEntity)) {
         return 0;
      }

      if (entity == null) {
         return 0;
      }

      if (entity instanceof DestroyedSkinHolder holder) {
         int tracked = clampStage(holder.getDestroyedSkinStage());
         if (tracked > 0) {
            return tracked;
         }
      }

      return STAGES.getOrDefault(entity.getUuid(), 0);
   }

   private static NonDestroyedSkinClient.MessState getMessState(@Nullable LivingEntity entity) {
      return entity == null ? NonDestroyedSkinClient.MessState.EMPTY : MESS_STATES.getOrDefault(entity.getUuid(), NonDestroyedSkinClient.MessState.EMPTY);
   }

   @Nullable
   private static Identifier getOrCreateTexture(LivingEntity entity, int stage, @Nullable Identifier currentBaseTexture, boolean overlayFallback) {
      if (entity == null) {
         return null;
      }

      UUID playerUuid = entity.getUuid();
      byte[] skin = DESTROYED_SKINS.get(playerUuid);
      NonDestroyedSkinClient.MessState messState = MESS_STATES.getOrDefault(playerUuid, NonDestroyedSkinClient.MessState.EMPTY);
      List<NonTrinketsIntegration.ResolvedVisualLayer> accessoryOverlays = overlayFallback
         ? List.of()
         : NonAccessorySkinOverlays.resolveEquippedOverlays(entity);
      if ((skin == null || skin.length == 0) && !messState.hasAnyVisual() && accessoryOverlays.isEmpty()) {
         return null;
      }

      NonDestroyedSkinClient.TankMaskType tankMaskType = resolveTankMaskType(entity);
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return null;
      }

      int baseTextureIdentity = textureIdentity(client, overlayFallback ? null : currentBaseTexture);
      NonExternalSkinCompat.SkinContext externalSkin = overlayFallback ? null : effectiveExternalSkin(client, playerUuid, currentBaseTexture);
      String externalSkinKey = externalSkinKey(externalSkin);
      NonDestroyedSkinClient.TextureKey key = new NonDestroyedSkinClient.TextureKey(
         playerUuid, stage, overlayFallback ? null : currentBaseTexture, baseTextureIdentity, externalSkinKey, messState, tankMaskType, accessoryOverlays
      );
      Identifier cached = TEXTURE_CACHE.get(key);
      if (cached != null) {
         return cached;
      }

      try {
         NativeImage image;
         if (overlayFallback) {
            image = createMaskedOverlay(client, playerUuid, skin, stage);
         } else {
            image = createCompositedSkin(client, playerUuid, skin, stage, currentBaseTexture, externalSkin, messState, tankMaskType, accessoryOverlays);
         }

         if (image == null) {
            return null;
         }

         Identifier id = Identifier.of(
            "needsofnature",
            "textures/dynamic/destroyed_skin/"
               + playerUuid.toString().replace("-", "")
               + "_"
               + stage
               + "_"
               + messState.cacheKey()
               + "_"
               + tankMaskType.id
               + "_"
               + Integer.toHexString(accessoryOverlays.hashCode())
               + "_"
               + Integer.toHexString(Objects.hashCode(currentBaseTexture))
               + "_"
               + Integer.toHexString(baseTextureIdentity)
               + "_"
               + Integer.toHexString(externalSkinKey.hashCode())
               + (overlayFallback ? "_overlay" : "_base")
               + ".png"
         );
         client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "non_destroyed_skin", image));
         TEXTURE_CACHE.put(key, id);
         return id;
      } catch (IOException | RuntimeException e) {
         setupWarn("[NoN] Failed to create destroyed skin texture for {} stage {}.", playerUuid, stage, e);
         return null;
      }
   }

   @Nullable
   private static NativeImage createCompositedSkin(
      MinecraftClient client,
      UUID playerUuid,
      byte[] skin,
      int stage,
      @Nullable Identifier currentBaseTexture,
      @Nullable NonExternalSkinCompat.SkinContext externalSkin,
      NonDestroyedSkinClient.MessState messState,
      NonDestroyedSkinClient.TankMaskType tankMaskType,
      List<NonTrinketsIntegration.ResolvedVisualLayer> accessoryOverlays
   ) throws IOException {
      if (currentBaseTexture == null) {
         return null;
      }

      NativeImage destroyed = stage > 0 && skin != null && skin.length > 0 ? NativeImage.read(skin) : null;
      if (destroyed != null && !isSupportedStaticSkin(destroyed)) {
         destroyed.close();
         return null;
      }

      if (stage >= 4 && destroyed != null) {
         NativeImage var27 = destroyed;

         NativeImage var29;
         try {
            NativeImage out = new NativeImage(Format.RGBA, destroyed.getWidth(), destroyed.getHeight(), false);
            copyScaled(destroyed, out);
            applyMessOverlays(client, out, messState);
            applyTankOverlay(client, out, messState, tankMaskType);
            applyAccessoryOverlays(client, out, accessoryOverlays);
            var29 = out;
         } catch (Throwable var24) {
            if (var27 != null) {
               try {
                  var27.close();
               } catch (Throwable var23) {
                  var24.addSuppressed(var23);
               }
            }

            throw var24;
         }

         if (var27 != null) {
            var27.close();
         }

         return var29;
      } else {
         NativeImage base = readTextureImage(client, currentBaseTexture, externalSkin);
         if (base == null) {
            if (destroyed != null) {
               destroyed.close();
            }

            return null;
         } else if (!isSupportedStaticSkin(base)) {
            base.close();
            if (destroyed != null) {
               destroyed.close();
            }

            return null;
         } else {
            NativeImage out = base;

            NativeImage mask;
            try {
               NativeImage var12 = destroyed;

               try {
                  int targetSize = destroyed != null ? Math.max(base.getWidth(), destroyed.getWidth()) : base.getWidth();
                  NativeImage outx = new NativeImage(Format.RGBA, targetSize, targetSize, false);
                  copyScaled(base, outx);
                  if (destroyed != null) {
                     mask = readMask(client, playerUuid, stage);
                     if (mask != null) {
                        for (int y = 0; y < outx.getHeight(); y++) {
                           for (int x = 0; x < outx.getWidth(); x++) {
                              int factor = maskFactor(sampleScaled(mask, x, y, outx.getWidth(), outx.getHeight()));
                              if (factor > 0) {
                                 int baseArgb = outx.getColorArgb(x, y);
                                 int destroyedArgb = sampleScaled(destroyed, x, y, outx.getWidth(), outx.getHeight());
                                 outx.setColorArgb(x, y, blendArgb(baseArgb, destroyedArgb, factor));
                              }
                           }
                        }

                        mask.close();
                     }
                  }

                  applyMessOverlays(client, outx, messState);
                  applyTankOverlay(client, outx, messState, tankMaskType);
                  applyAccessoryOverlays(client, outx, accessoryOverlays);
                  mask = outx;
               } catch (Throwable var25) {
                  if (var12 != null) {
                     try {
                        var12.close();
                     } catch (Throwable var22) {
                        var25.addSuppressed(var22);
                     }
                  }

                  throw var25;
               }

               if (var12 != null) {
                  var12.close();
               }
            } catch (Throwable var26) {
               if (out != null) {
                  try {
                     out.close();
                  } catch (Throwable var21) {
                     var26.addSuppressed(var21);
                  }
               }

               throw var26;
            }

            if (out != null) {
               out.close();
            }

            return mask;
         }
      }
   }

   @Nullable
   public static NativeImage readTextureImage(MinecraftClient client, Identifier textureId) throws IOException {
      UUID playerUuid = client != null && client.player != null ? client.player.getUuid() : null;
      NonExternalSkinCompat.SkinContext externalSkin = playerUuid != null ? effectiveExternalSkin(client, playerUuid, textureId) : null;
      return readTextureImage(client, textureId, externalSkin);
   }

   @Nullable
   private static NativeImage readPlayerTextureImage(MinecraftClient client, UUID playerUuid, Identifier textureId) throws IOException {
      return readTextureImage(client, textureId, effectiveExternalSkin(client, playerUuid, textureId));
   }

   @Nullable
   private static NativeImage readTextureImage(MinecraftClient client, Identifier textureId, @Nullable NonExternalSkinCompat.SkinContext externalSkin) throws IOException {
      if (client != null && textureId != null) {
         Resource resource = (Resource)client.getResourceManager().getResource(textureId).orElse(null);
         if (resource != null) {
            try (InputStream in = resource.getInputStream()) {
               return NativeImage.read(in);
            }
         } else {
            int identity = textureIdentity(client, textureId);
            NonDestroyedSkinClient.TextureReadKey readKey = new NonDestroyedSkinClient.TextureReadKey(textureId, identity, externalSkinKey(externalSkin));
            Long retryAt = FAILED_TEXTURE_READS.get(readKey);
            if (retryAt != null && System.nanoTime() < retryAt) {
               return null;
            }

            Throwable sourceFailure = null;
            Path sourcePath = externalSkin != null ? externalSkin.sourcePath() : null;
            if (sourcePath != null && Files.isRegularFile(sourcePath)) {
               try (InputStream in = Files.newInputStream(sourcePath)) {
                  NativeImage image = NativeImage.read(in);
                  clearTextureReadFailure(readKey);
                  return image;
               } catch (IOException | RuntimeException e) {
                  sourceFailure = e;
               }
            }

            if (!(client.getTextureManager().getTexture(textureId) instanceof NativeImageBackedTexture backedTexture)) {
               if (sourceFailure != null) {
                  recordTextureReadFailure(readKey, sourcePath, sourceFailure);
               }

               return null;
            } else {
               NativeImage image = backedTexture.getImage();
               if (image == null) {
                  recordTextureReadFailure(readKey, sourcePath, sourceFailure);
                  return null;
               }

               NativeImage copy = null;

               try {
                  copy = new NativeImage(Format.RGBA, image.getWidth(), image.getHeight(), false);
                  copy.copyFrom(image);
                  clearTextureReadFailure(readKey);
                  return copy;
               } catch (RuntimeException e) {
                  if (copy != null) {
                     copy.close();
                  }

                  if (sourceFailure != null) {
                     e.addSuppressed(sourceFailure);
                  }

                  recordTextureReadFailure(readKey, sourcePath, e);
                  return null;
               }
            }
         }
      } else {
         return null;
      }
   }

   private static boolean isGeneratedTexture(Identifier textureId) {
      return "needsofnature".equals(textureId.getNamespace()) && textureId.getPath().startsWith("textures/dynamic/destroyed_skin/");
   }

   private static boolean isSupportedStaticSkin(NativeImage image) {
      int width = image.getWidth();
      return width >= 64 && width == image.getHeight() && width % 64 == 0;
   }

   private static int textureIdentity(MinecraftClient client, @Nullable Identifier textureId) {
      return textureId == null ? 0 : System.identityHashCode(client.getTextureManager().getTexture(textureId));
   }

   @Nullable
   private static NonExternalSkinCompat.SkinContext effectiveExternalSkin(MinecraftClient client, UUID playerUuid, @Nullable Identifier textureId) {
      if (client != null && playerUuid != null && textureId != null) {
         NonDestroyedSkinClient.EffectiveBaseTexture effective = EFFECTIVE_BASE_TEXTURES.get(playerUuid);
         return effective != null && textureId.equals(effective.id()) ? effective.externalSkin() : NonExternalSkinCompat.resolve(client, playerUuid, textureId);
      } else {
         return null;
      }
   }

   private static String externalSkinKey(@Nullable NonExternalSkinCompat.SkinContext externalSkin) {
      return externalSkin != null ? externalSkin.cacheKey() : "";
   }

   private static void recordTextureReadFailure(NonDestroyedSkinClient.TextureReadKey key, @Nullable Path sourcePath, @Nullable Throwable error) {
      FAILED_TEXTURE_READS.put(key, System.nanoTime() + 1000000000L);
      if (error != null && WARNED_TEXTURE_READS.add(key)) {
         setupWarn(
            "[NoN] Failed to read skin texture {}{}; ripped-skin composition will retry after a short delay.",
            key.textureId(),
            sourcePath != null ? " from " + sourcePath : "",
            error
         );
      }
   }

   private static void clearTextureReadFailure(NonDestroyedSkinClient.TextureReadKey key) {
      FAILED_TEXTURE_READS.remove(key);
   }

   private static void clearTextureReadFailures(Identifier textureId) {
      FAILED_TEXTURE_READS.keySet().removeIf(key -> key.textureId().equals(textureId));
      WARNED_TEXTURE_READS.removeIf(key -> key.textureId().equals(textureId));
   }

   private static void removeOriginalSkinColors(Identifier textureId) {
      ORIGINAL_SKIN_COLOR_CACHE.keySet().removeIf(key -> key.textureId().equals(textureId));
   }

   private static void rememberEffectiveBaseTexture(LivingEntity entity, Identifier textureId) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null) {
         UUID playerUuid = entity.getUuid();
         int identity = textureIdentity(client, textureId);
         NonDestroyedSkinClient.EffectiveBaseTexture previous = EFFECTIVE_BASE_TEXTURES.get(playerUuid);
         if (previous == null || !previous.id().equals(textureId) || previous.textureIdentity() != identity) {
            NonExternalSkinCompat.SkinContext externalSkin = NonExternalSkinCompat.resolve(client, playerUuid, textureId);
            NonDestroyedSkinClient.EffectiveBaseTexture current = new NonDestroyedSkinClient.EffectiveBaseTexture(textureId, identity, externalSkin);
            previous = EFFECTIVE_BASE_TEXTURES.put(playerUuid, current);
            if (previous != null && !previous.equals(current)) {
               removeOriginalSkinColors(previous.id());
               clearTextureReadFailures(previous.id());
               clearCachedTextures(playerUuid);
               if (client.player != null && playerUuid.equals(client.player.getUuid()) && shouldRetryLocalSkinUpload(previous, current)) {
                  scheduleLocalSkinUploadRetry();
               }
            }
         }
      }
   }

   private static boolean shouldRetryLocalSkinUpload(NonDestroyedSkinClient.EffectiveBaseTexture previous, NonDestroyedSkinClient.EffectiveBaseTexture current) {
      String previousSelection = externalSelectionKey(previous.externalSkin());
      String currentSelection = externalSelectionKey(current.externalSkin());
      return !previousSelection.equals(currentSelection)
         ? true
         : previousSelection.isEmpty() && (!previous.id().equals(current.id()) || previous.textureIdentity() != current.textureIdentity());
   }

   private static String externalSelectionKey(@Nullable NonExternalSkinCompat.SkinContext externalSkin) {
      return externalSkin != null ? externalSkin.selectionKey() : "";
   }

   private static boolean hasProfileTextureProperties(MinecraftClient client) {
      if (client != null && client.player != null && client.player.getGameProfile() != null) {
         try {
            if (client.player.getGameProfile().getClass().getMethod("getProperties").invoke(client.player.getGameProfile()) instanceof Map<?, ?> map) {
               return map.containsKey("textures");
            }
         } catch (ReflectiveOperationException | RuntimeException var4) {
         }

         try {
            if (client.player.getGameProfile().getClass().getMethod("properties").invoke(client.player.getGameProfile()) instanceof Map<?, ?> map) {
               return map.containsKey("textures");
            }
         } catch (ReflectiveOperationException | RuntimeException var3) {
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean isLikelyDefaultPlayerSkin(Identifier textureId) {
      if (textureId != null && "minecraft".equals(textureId.getNamespace())) {
         String path = textureId.getPath();
         return path != null && path.startsWith("textures/entity/player/");
      } else {
         return false;
      }
   }

   public static int sendDetectedRippedSkinInfo() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null) {
         Identifier baseSkin = client.player.getSkin().body().texturePath();
         boolean defaultSkinPath = isLikelyDefaultPlayerSkin(baseSkin);
         boolean profileHasCustomTexture = hasProfileTextureProperties(client);
         NonDestroyedSkinClient.DefaultSkinKey key = parseDefaultSkinKey(baseSkin);
         NonDestroyedSkinClient.DefaultSkinMatch pixelMatch = detectDefaultSkinMatchByPixels(client, baseSkin);
         NonDestroyedSkinClient.DefaultSkinKey pixelKey = pixelMatch == null ? null : pixelMatch.key();
         NonDestroyedSkinClient.DefaultSkinKey resolvedKey = key != null ? key : (pixelMatch != null && pixelMatch.accepted() ? pixelMatch.key() : null);
         PlayerSkinType model = client.player.getSkin() == null ? null : client.player.getSkin().model();
         String profileName = client.player.getName().getString();
         UUID uuid = client.player.getUuid();
         NonDestroyedSkinClient.DefaultSkinCandidate defaultDestroyed = resolvedKey == null ? null : describeDefaultDestroyedSkinCandidate(client, resolvedKey);
         String message = "[NoN RippedSkinInfo] profile="
            + profileName
            + " uuid="
            + uuid
            + " base="
            + baseSkin
            + " model="
            + model
            + " defaultPath="
            + defaultSkinPath
            + " profileTextures="
            + profileHasCustomTexture
            + " parsed="
            + (key == null ? "<none>" : key.model() + "/" + key.name())
            + " pixel="
            + (pixelKey == null ? "<none>" : pixelKey.model() + "/" + pixelKey.name())
            + " confidence="
            + (pixelMatch == null ? "<none>" : String.format(Locale.ROOT, "%.1f%%", pixelMatch.confidence() * 100.0))
            + " accepted="
            + (pixelMatch != null && pixelMatch.accepted())
            + " resolved="
            + (resolvedKey == null ? "<none>" : resolvedKey.model() + "/" + resolvedKey.name())
            + " candidate="
            + (defaultDestroyed == null ? "<none>" : defaultDestroyed.logString());
         NeedsOfNature.LOGGER.info(message);
         sendRippedSkinInfoLine(client, "NoN Ripped Skin Info", Formatting.GOLD);
         sendRippedSkinInfoLine(client, "Player: " + profileName + " (" + uuid + ")", Formatting.GRAY);
         sendRippedSkinInfoLine(client, "Base texture: " + baseSkin, Formatting.WHITE);
         sendRippedSkinInfoLine(client, "Model: " + model, Formatting.WHITE);
         sendRippedSkinInfoLine(client, "Default path: " + defaultSkinPath + " | Profile textures: " + profileHasCustomTexture, Formatting.WHITE);
         sendRippedSkinInfoLine(client, "Parsed key: " + formatDefaultSkinKey(key), Formatting.WHITE);
         sendRippedSkinInfoLine(
            client,
            "Pixel match: "
               + formatDefaultSkinKey(pixelKey)
               + " | Confidence: "
               + (pixelMatch == null ? "<none>" : String.format(Locale.ROOT, "%.1f%%", pixelMatch.confidence() * 100.0))
               + " | Accepted: "
               + (pixelMatch != null && pixelMatch.accepted()),
            pixelMatch != null && pixelMatch.accepted() ? Formatting.GREEN : Formatting.YELLOW
         );
         sendRippedSkinInfoLine(client, "Resolved key: " + formatDefaultSkinKey(resolvedKey), resolvedKey == null ? Formatting.RED : Formatting.GREEN);
         if (defaultDestroyed == null) {
            sendRippedSkinInfoLine(client, "Ripped candidates: <none>", Formatting.RED);
         } else {
            sendRippedSkinInfoLine(
               client,
               "Ripped candidate: " + defaultDestroyed.genderSpecific() + " = " + defaultDestroyed.genderFound(),
               defaultDestroyed.genderFound() ? Formatting.GREEN : Formatting.YELLOW
            );
            sendRippedSkinInfoLine(
               client,
               "Fallback candidate: " + defaultDestroyed.generic() + " = " + defaultDestroyed.genericFound(),
               defaultDestroyed.genericFound() ? Formatting.GREEN : Formatting.GRAY
            );
         }

         return 1;
      } else {
         return 0;
      }
   }

   private static void sendRippedSkinInfoLine(MinecraftClient client, String message, Formatting color) {
      client.player.sendMessage(Text.literal(message).formatted(color), false);
   }

   private static String formatDefaultSkinKey(@Nullable NonDestroyedSkinClient.DefaultSkinKey key) {
      return key == null ? "<none>" : key.model() + "/" + key.name();
   }

   private static NonDestroyedSkinClient.DefaultSkinCandidate describeDefaultDestroyedSkinCandidate(
      MinecraftClient client, NonDestroyedSkinClient.DefaultSkinKey key
   ) {
      String genderSuffix = NeedsOfNature.getLocalConfig().getPlayerGenderSelection() == NonConfig.PlayerGenderSelection.MALE ? "_m" : "_f";
      Identifier genderSpecific = defaultDestroyedSkinId(key, genderSuffix);
      Identifier generic = defaultDestroyedSkinId(key, "");
      boolean genderFound = client.getResourceManager().getResource(genderSpecific).isPresent();
      boolean genericFound = client.getResourceManager().getResource(generic).isPresent();
      return new NonDestroyedSkinClient.DefaultSkinCandidate(genderSpecific, genderFound, generic, genericFound);
   }

   @Nullable
   private static byte[] readDefaultDestroyedSkin(MinecraftClient client, NonDestroyedSkinClient.DefaultSkinKey key) {
      if (client != null && key != null) {
         String genderSuffix = NeedsOfNature.getLocalConfig().getPlayerGenderSelection() == NonConfig.PlayerGenderSelection.MALE ? "_m" : "_f";
         byte[] genderSpecific = readDefaultDestroyedSkin(client, key, genderSuffix);
         return genderSpecific != null ? genderSpecific : readDefaultDestroyedSkin(client, key, "");
      } else {
         return null;
      }
   }

   @Nullable
   private static byte[] readDefaultDestroyedSkin(MinecraftClient client, NonDestroyedSkinClient.DefaultSkinKey key, String suffix) {
      Identifier destroyedId = defaultDestroyedSkinId(key, suffix);
      Resource resource = (Resource)client.getResourceManager().getResource(destroyedId).orElse(null);
      if (resource == null) {
         return null;
      }

      try (InputStream in = resource.getInputStream()) {
         return in.readAllBytes();
      } catch (IOException | RuntimeException e) {
         setupWarn("[NoN] Failed to read default destroyed skin {}.", destroyedId, e);
         return null;
      }
   }

   private static Identifier defaultDestroyedSkinId(NonDestroyedSkinClient.DefaultSkinKey key, String suffix) {
      return Identifier.of("needsofnature", "textures/player_destroyed/default/" + key.model() + "/" + key.name() + suffix + ".png");
   }

   @Nullable
   private static NonDestroyedSkinClient.DefaultSkinKey resolveDefaultSkinKey(MinecraftClient client, Identifier baseSkin) {
      NonDestroyedSkinClient.DefaultSkinKey parsed = parseDefaultSkinKey(baseSkin);
      if (parsed != null) {
         return parsed;
      }

      NonDestroyedSkinClient.DefaultSkinMatch match = detectDefaultSkinMatchByPixels(client, baseSkin);
      return match != null && match.accepted() ? match.key() : null;
   }

   @Nullable
   private static NonDestroyedSkinClient.DefaultSkinKey parseDefaultSkinKey(Identifier baseSkin) {
      if (baseSkin != null && "minecraft".equals(baseSkin.getNamespace())) {
         String path = baseSkin.getPath();
         if (path != null && path.startsWith("textures/entity/player/")) {
            String suffix = path.substring("textures/entity/player/".length());
            if (suffix.endsWith(".png")) {
               suffix = suffix.substring(0, suffix.length() - ".png".length());
            }

            String[] parts = suffix.split("/");
            if (parts.length != 2) {
               return null;
            } else {
               String model = parts[0];
               String name = parts[1];
               if (!"slim".equals(model) && !"wide".equals(model)) {
                  return null;
               } else {
                  return name.isBlank() ? null : new NonDestroyedSkinClient.DefaultSkinKey(model, name);
               }
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static NonDestroyedSkinClient.DefaultSkinMatch detectDefaultSkinMatchByPixels(MinecraftClient client, Identifier baseSkin) {
      if (client != null && baseSkin != null) {
         PlayerSkinType currentModel = client.player != null && client.player.getSkin() != null ? client.player.getSkin().model() : PlayerSkinType.SLIM;
         String preferredModel = currentModel == PlayerSkinType.WIDE ? "wide" : "slim";
         String fallbackModel = "wide".equals(preferredModel) ? "slim" : "wide";
         UUID playerUuid = client.player != null ? client.player.getUuid() : null;

         try {
            NativeImage base = playerUuid != null ? readPlayerTextureImage(client, playerUuid, baseSkin) : readTextureImage(client, baseSkin, null);

            NonDestroyedSkinClient.DefaultSkinMatch var17;
            label151: {
               label152: {
                  label153: {
                     label154: {
                        label155: {
                           NonDestroyedSkinClient.DefaultSkinMatch preferred;
                           try {
                              if (base != null && base.getWidth() == 64 && base.getHeight() == 64) {
                                 preferred = detectDefaultSkinMatchByPixels(client, base, preferredModel);
                                 NonDestroyedSkinClient.DefaultSkinMatch fallback = detectDefaultSkinMatchByPixels(client, base, fallbackModel);
                                 if (preferred == null) {
                                    var17 = fallback;
                                    break label151;
                                 }

                                 if (fallback == null) {
                                    var17 = preferred;
                                    break label152;
                                 }

                                 if (preferred.accepted() && !fallback.accepted()) {
                                    var17 = preferred;
                                    break label153;
                                 }

                                 if (fallback.accepted() && !preferred.accepted()) {
                                    var17 = fallback;
                                    break label154;
                                 }

                                 var17 = preferred.confidence() >= fallback.confidence() ? preferred : fallback;
                                 break label155;
                              }

                              preferred = null;
                           } catch (Throwable var11) {
                              if (base != null) {
                                 try {
                                    base.close();
                                 } catch (Throwable var10) {
                                    var11.addSuppressed(var10);
                                 }
                              }

                              throw var11;
                           }

                           if (base != null) {
                              base.close();
                           }

                           return preferred;
                        }

                        if (base != null) {
                           base.close();
                        }

                        return var17;
                     }

                     if (base != null) {
                        base.close();
                     }

                     return var17;
                  }

                  if (base != null) {
                     base.close();
                  }

                  return var17;
               }

               if (base != null) {
                  base.close();
               }

               return var17;
            }

            if (base != null) {
               base.close();
            }

            return var17;
         } catch (IOException | RuntimeException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static NonDestroyedSkinClient.DefaultSkinMatch detectDefaultSkinMatchByPixels(MinecraftClient client, NativeImage base, String model) {
      NonDestroyedSkinClient.DefaultSkinMatch best = null;

      for (String name : VANILLA_DEFAULT_SKIN_NAMES) {
         Identifier vanillaId = Identifier.ofVanilla("textures/entity/player/" + model + "/" + name + ".png");

         try {
            NativeImage vanilla = readTextureImage(client, vanillaId);

            try {
               if (vanilla != null) {
                  double confidence = imageMatchConfidence(base, vanilla);
                  if (best == null || confidence > best.confidence()) {
                     best = new NonDestroyedSkinClient.DefaultSkinMatch(new NonDestroyedSkinClient.DefaultSkinKey(model, name), confidence);
                  }
               }
            } catch (Throwable var13) {
               if (vanilla != null) {
                  try {
                     vanilla.close();
                  } catch (Throwable var12) {
                     var13.addSuppressed(var12);
                  }
               }

               throw var13;
            }

            if (vanilla != null) {
               vanilla.close();
            }
         } catch (IOException | RuntimeException var14) {
         }
      }

      return best;
   }

   private static double imageMatchConfidence(NativeImage first, NativeImage second) {
      if (first != null && second != null) {
         if (first.getWidth() == second.getWidth() && first.getHeight() == second.getHeight()) {
            int checked = 0;
            int matched = 0;

            for (int y = 0; y < first.getHeight(); y++) {
               for (int x = 0; x < first.getWidth(); x++) {
                  int firstArgb = first.getColorArgb(x, y);
                  int secondArgb = second.getColorArgb(x, y);
                  int firstAlpha = firstArgb >>> 24 & 0xFF;
                  int secondAlpha = secondArgb >>> 24 & 0xFF;
                  if (firstAlpha != 0 || secondAlpha != 0) {
                     checked++;
                     if (firstArgb == secondArgb) {
                        matched++;
                     }
                  }
               }
            }

            return checked <= 0 ? 0.0 : (double)matched / checked;
         } else {
            return 0.0;
         }
      } else {
         return 0.0;
      }
   }

   @Nullable
   private static NativeImage createMaskedOverlay(MinecraftClient client, UUID playerUuid, byte[] skin, int stage) throws IOException {
      NativeImage destroyed = NativeImage.read(skin);
      NativeImage mask = readMask(client, playerUuid, stage);
      if (mask == null) {
         destroyed.close();
         return null;
      }

      NativeImage out = new NativeImage(Format.RGBA, destroyed.getWidth(), destroyed.getHeight(), false);

      for (int y = 0; y < destroyed.getHeight(); y++) {
         for (int x = 0; x < destroyed.getWidth(); x++) {
            int skinArgb = destroyed.getColorArgb(x, y);
            int maskArgb = sampleScaled(mask, x, y, destroyed.getWidth(), destroyed.getHeight());
            int factor = maskFactor(maskArgb);
            int alpha = (skinArgb >>> 24 & 0xFF) * factor / 255;
            out.setColorArgb(x, y, skinArgb & 16777215 | alpha << 24);
         }
      }

      destroyed.close();
      mask.close();
      return out;
   }

   private static byte[] createGeneratedDestroyedSkin(MinecraftClient client, Identifier baseSkin) throws IOException {
      NativeImage base = client != null && client.player != null
         ? readPlayerTextureImage(client, client.player.getUuid(), baseSkin)
         : readTextureImage(client, baseSkin, null);
      if (base == null) {
         return null;
      }

      PlayerSkinType model = client != null && client.player != null && client.player.getSkin() != null ? client.player.getSkin().model() : PlayerSkinType.SLIM;
      NativeImage var4 = base;

      Object normalizedBase;
      label154: {
         byte[] var25;
         try {
            NativeImage destroyedOverlay = readDestroyedBaseTexture(client, model);

            label156: {
               try {
                  if (isSupportedStaticSkin(base)) {
                     NativeImage normalizedBasex = scaledCopy(base, 64, 64);

                     try {
                        NativeImage out = new NativeImage(Format.RGBA, 64, 64, false);

                        try {
                           int detectedTint = detectSkinTint(normalizedBasex);
                           NonConfig config = NeedsOfNature.getLocalConfig();
                           int tint = config.getDestroyedSkinTintRgb(detectedTint);
                           if (!config.hasDestroyedSkinTintOverride()) {
                              config.setDestroyedSkinTintHex(formatRgb(tint));
                              config.save();
                           }

                           for (int y = 0; y < 64; y++) {
                              for (int x = 0; x < 64; x++) {
                                 int overlayArgb = sampleScaled(destroyedOverlay, x, y, 64, 64);
                                 int overlayAlpha = overlayArgb >>> 24 & 0xFF;
                                 int brightness = ((overlayArgb >>> 16 & 0xFF) + (overlayArgb >>> 8 & 0xFF) + (overlayArgb & 0xFF)) / 3;
                                 out.setColorArgb(x, y, tintByBrightness(tint, brightness, overlayAlpha));
                              }
                           }

                           copyRegion(normalizedBasex, out, 0, 0, 64, 16);
                           var25 = writeImageToBytes(out);
                        } catch (Throwable var20) {
                           try {
                              out.close();
                           } catch (Throwable var19) {
                              var20.addSuppressed(var19);
                           }

                           throw var20;
                        }

                        out.close();
                     } catch (Throwable var21) {
                        if (normalizedBasex != null) {
                           try {
                              normalizedBasex.close();
                           } catch (Throwable var18) {
                              var21.addSuppressed(var18);
                           }
                        }

                        throw var21;
                     }

                     if (normalizedBasex != null) {
                        normalizedBasex.close();
                     }
                     break label156;
                  }

                  normalizedBase = null;
               } catch (Throwable var22) {
                  if (destroyedOverlay != null) {
                     try {
                        destroyedOverlay.close();
                     } catch (Throwable var17) {
                        var22.addSuppressed(var17);
                     }
                  }

                  throw var22;
               }

               if (destroyedOverlay != null) {
                  destroyedOverlay.close();
               }
               break label154;
            }

            if (destroyedOverlay != null) {
               destroyedOverlay.close();
            }
         } catch (Throwable var23) {
            if (var4 != null) {
               try {
                  var4.close();
               } catch (Throwable var16) {
                  var23.addSuppressed(var16);
               }
            }

            throw var23;
         }

         if (var4 != null) {
            var4.close();
         }

         return var25;
      }

      if (var4 != null) {
         var4.close();
      }

      return (byte[])normalizedBase;
   }

   private static NativeImage readDestroyedBaseTexture(MinecraftClient client, PlayerSkinType model) throws IOException {
      boolean useMaleTexture = NeedsOfNature.getLocalConfig().getPlayerGenderSelection() == NonConfig.PlayerGenderSelection.MALE;
      boolean wide = model == PlayerSkinType.WIDE;
      Identifier bundledModel = defaultDestroyedTexture(useMaleTexture, wide);
      NativeImage bundledModelImage = readBundledDestroyedTexture(client, bundledModel);
      if (bundledModelImage != null) {
         return bundledModelImage;
      } else {
         Identifier bundledGender = useMaleTexture ? DEFAULT_DESTROYED_TEXTURE_M : DEFAULT_DESTROYED_TEXTURE_F;
         NativeImage bundledGenderImage = readBundledDestroyedTexture(client, bundledGender);
         if (bundledGenderImage != null) {
            return bundledGenderImage;
         } else {
            throw new NonDestroyedSkinClient.MissingDestroyedSkinFallbackException(
               "Missing ripped skin fallback texture " + bundledModel + " and fallback " + bundledGender
            );
         }
      }
   }

   @Nullable
   private static NativeImage readBundledDestroyedTexture(MinecraftClient client, Identifier id) throws IOException {
      Resource resource = (Resource)client.getResourceManager().getResource(id).orElse(null);
      if (resource == null) {
         return null;
      }

      try (InputStream in = resource.getInputStream()) {
         return NativeImage.read(in);
      }
   }

   private static Identifier defaultDestroyedTexture(boolean useMaleTexture, boolean wide) {
      if (useMaleTexture) {
         return wide ? DEFAULT_DESTROYED_TEXTURE_M_WIDE : DEFAULT_DESTROYED_TEXTURE_M_SLIM;
      } else {
         return wide ? DEFAULT_DESTROYED_TEXTURE_F_WIDE : DEFAULT_DESTROYED_TEXTURE_F_SLIM;
      }
   }

   private static void copyRegion(NativeImage source, NativeImage target, int minX, int minY, int maxX, int maxY) {
      int endX = Math.min(Math.min(source.getWidth(), target.getWidth()), maxX);
      int endY = Math.min(Math.min(source.getHeight(), target.getHeight()), maxY);

      for (int y = Math.max(0, minY); y < endY; y++) {
         for (int x = Math.max(0, minX); x < endX; x++) {
            target.setColorArgb(x, y, source.getColorArgb(x, y));
         }
      }
   }

   private static byte[] writeImageToBytes(NativeImage image) throws IOException {
      Path temp = Files.createTempFile("non_destroyed_skin", ".png");

      try {
         image.writeTo(temp);
         return Files.readAllBytes(temp);
      } finally {
         try {
            Files.deleteIfExists(temp);
         } catch (IOException var9) {
         }
      }
   }

   private static int detectSkinTint(NativeImage skin) {
      Map<Integer, NonDestroyedSkinClient.ColorBucket> buckets = new HashMap<>();
      addTintSamples(skin, buckets, 9, 12, 15, 16, 5);
      addTintSamples(skin, buckets, 8, 12, 16, 16, 2);
      addTintSamples(skin, buckets, 20, 17, 28, 24, 3);
      addTintSamples(skin, buckets, 40, 20, 54, 32, 1);
      addTintSamples(skin, buckets, 32, 56, 46, 64, 1);
      if (buckets.isEmpty()) {
         return 13803641;
      }

      List<NonDestroyedSkinClient.ColorBucket> topBuckets = new ArrayList<>(buckets.values());
      topBuckets.sort((a, bx) -> Integer.compare(bx.count, a.count));
      int limit = Math.min(3, topBuckets.size());
      long r = 0L;
      long g = 0L;
      long b = 0L;
      int totalWeight = 0;

      for (int i = 0; i < limit; i++) {
         NonDestroyedSkinClient.ColorBucket bucket = topBuckets.get(i);
         int weight = Math.max(1, bucket.count);
         r += (long)bucket.averageR() * weight;
         g += (long)bucket.averageG() * weight;
         b += (long)bucket.averageB() * weight;
         totalWeight += weight;
      }

      return totalWeight <= 0 ? 13803641 : (int)(r / totalWeight) << 16 | (int)(g / totalWeight) << 8 | (int)(b / totalWeight);
   }

   private static void addTintSamples(
      NativeImage skin, Map<Integer, NonDestroyedSkinClient.ColorBucket> out, int minX, int minY, int maxX, int maxY, int weight
   ) {
      int clampedMaxX = Math.min(maxX, skin.getWidth());
      int clampedMaxY = Math.min(maxY, skin.getHeight());

      for (int y = Math.max(0, minY); y < clampedMaxY; y++) {
         for (int x = Math.max(0, minX); x < clampedMaxX; x++) {
            int argb = skin.getColorArgb(x, y);
            if (isSkinTintCandidate(argb)) {
               int rgb = argb & 16777215;
               int key = colorBucketKey(rgb);
               out.computeIfAbsent(key, ignored -> new NonDestroyedSkinClient.ColorBucket()).add(rgb, Math.max(1, weight));
            }
         }
      }
   }

   private static int colorBucketKey(int rgb) {
      int r = (rgb >>> 16 & 0xFF) / 8;
      int g = (rgb >>> 8 & 0xFF) / 8;
      int b = (rgb & 0xFF) / 8;
      return r << 10 | g << 5 | b;
   }

   private static boolean isSkinTintCandidate(int argb) {
      if ((argb >>> 24 & 0xFF) < 32) {
         return false;
      } else {
         int r = argb >>> 16 & 0xFF;
         int g = argb >>> 8 & 0xFF;
         int b = argb & 0xFF;
         int max = Math.max(r, Math.max(g, b));
         int min = Math.min(r, Math.min(g, b));
         int brightness = (r + g + b) / 3;
         if (brightness >= 35 && brightness <= 245) {
            float saturation = max == 0 ? 0.0F : (float)(max - min) / max;
            return saturation > 0.7F ? false : r >= b * 0.72F && g >= b * 0.5F;
         } else {
            return false;
         }
      }
   }

   private static int brightness(int rgb) {
      return ((rgb >>> 16 & 0xFF) + (rgb >>> 8 & 0xFF) + (rgb & 0xFF)) / 3;
   }

   private static int tintByBrightness(int tintRgb, int brightness, int alpha) {
      float shade = Math.max(0.0F, Math.min(1.0F, brightness / 255.0F));
      int r = Math.round((tintRgb >>> 16 & 0xFF) * shade);
      int g = Math.round((tintRgb >>> 8 & 0xFF) * shade);
      int b = Math.round((tintRgb & 0xFF) * shade);
      return Math.max(0, Math.min(255, alpha)) << 24 | r << 16 | g << 8 | b;
   }

   private static String formatRgb(int rgb) {
      return String.format(Locale.ROOT, "%06X", rgb & 16777215);
   }

   @Nullable
   private static NativeImage readMask(MinecraftClient client, UUID playerUuid, int stage) {
      if (stage > 0 && stage < 4) {
         byte[][] syncedMasks = DESTROYED_SKIN_MASKS.get(playerUuid);
         if (syncedMasks != null && stage < syncedMasks.length) {
            byte[] synced = syncedMasks[stage];
            if (synced != null && synced.length > 0) {
               try {
                  return NativeImage.read(synced);
               } catch (IOException | RuntimeException e) {
                  setupWarn("[NoN] Failed to read synced destroyed skin mask for {} stage {}.", playerUuid, stage, e);
               }
            }
         }

         Identifier maskId = MASK_TEXTURES[stage];
         if (maskId == null) {
            return null;
         }

         try {
            Resource resource = (Resource)client.getResourceManager().getResource(maskId).orElse(null);
            if (resource == null) {
               return null;
            }

            try (InputStream in = resource.getInputStream()) {
               return NativeImage.read(in);
            }
         } catch (IOException | RuntimeException e) {
            setupWarn("[NoN] Failed to read destroyed skin mask {}.", maskId, e);
            return null;
         }
      } else {
         return null;
      }
   }

   private static int maskFactor(int maskArgb) {
      int maskAlpha = maskArgb >>> 24 & 0xFF;
      int maskBrightness = ((maskArgb >>> 16 & 0xFF) + (maskArgb >>> 8 & 0xFF) + (maskArgb & 0xFF)) / 3;
      return maskAlpha * maskBrightness / 255;
   }

   private static int blendArgb(int baseArgb, int overlayArgb, int factor) {
      int inv = 255 - factor;
      int a = ((baseArgb >>> 24 & 0xFF) * inv + (overlayArgb >>> 24 & 0xFF) * factor) / 255;
      int r = ((baseArgb >>> 16 & 0xFF) * inv + (overlayArgb >>> 16 & 0xFF) * factor) / 255;
      int g = ((baseArgb >>> 8 & 0xFF) * inv + (overlayArgb >>> 8 & 0xFF) * factor) / 255;
      int b = ((baseArgb & 0xFF) * inv + (overlayArgb & 0xFF) * factor) / 255;
      return a << 24 | r << 16 | g << 8 | b;
   }

   private static void applyMessOverlays(MinecraftClient client, NativeImage out, NonDestroyedSkinClient.MessState state) {
      if (client != null && out != null && state != null && state.hasAnyMess()) {
         applyMessRole(client, out, state.vMess(), state.tintRgb(), MESS_MASK_TEXTURES[0]);
         applyMessRole(client, out, state.aMess(), state.tintRgb(), MESS_MASK_TEXTURES[1]);
         applyMessRole(client, out, state.mMess(), state.tintRgb(), MESS_MASK_TEXTURES[2]);
      }
   }

   private static void applyTankOverlay(
      MinecraftClient client, NativeImage out, NonDestroyedSkinClient.MessState state, NonDestroyedSkinClient.TankMaskType tankMaskType
   ) {
      if (client != null && out != null && state != null && state.hasTankOverlay()) {
         Identifier maskId = tankMaskType == NonDestroyedSkinClient.TankMaskType.A ? A_MESS_TANK_TEXTURE : V_MESS_TANK_TEXTURE;
         NativeImage mask = readMessMask(client, maskId);
         if (mask != null) {
            applyTintMask(out, mask, state.tintRgb(), state.tankAlpha());
            mask.close();
         }
      }
   }

   private static void applyAccessoryOverlays(MinecraftClient client, NativeImage out, List<NonTrinketsIntegration.ResolvedVisualLayer> overlays) {
      if (client != null && out != null && overlays != null && !overlays.isEmpty()) {
         for (NonTrinketsIntegration.ResolvedVisualLayer layer : overlays) {
            if (layer != null && layer.texture() != null) {
               NativeImage overlay = readAccessoryOverlay(client, layer.texture());
               if (overlay != null) {
                  applyRawOverlay(out, overlay, layer.tintRgb());
                  overlay.close();
               }
            }
         }
      }
   }

   @Nullable
   private static NativeImage readAccessoryOverlay(MinecraftClient client, Identifier overlayId) {
      if (client != null && overlayId != null) {
         try {
            Resource resource = (Resource)client.getResourceManager().getResource(overlayId).orElse(null);
            if (resource == null) {
               return null;
            }

            try (InputStream in = resource.getInputStream()) {
               NativeImage image = NativeImage.read(in);
               if (image.getWidth() == 64 && image.getHeight() == 64) {
                  return image;
               }

               image.close();
               setupWarn("[NoN] Ignoring accessory skin overlay {}: expected 64x64 PNG.", overlayId);
               return null;
            }
         } catch (IOException | RuntimeException e) {
            setupWarn("[NoN] Failed to read accessory skin overlay {}.", overlayId, e);
            return null;
         }
      } else {
         return null;
      }
   }

   private static void applyRawOverlay(NativeImage out, NativeImage overlay, int tintRgb) {
      int tintR = tintRgb >>> 16 & 0xFF;
      int tintG = tintRgb >>> 8 & 0xFF;
      int tintB = tintRgb & 0xFF;

      for (int y = 0; y < out.getHeight(); y++) {
         for (int x = 0; x < out.getWidth(); x++) {
            int overlayArgb = sampleScaled(overlay, x, y, out.getWidth(), out.getHeight());
            int alpha = overlayArgb >>> 24 & 0xFF;
            if (alpha > 0) {
               int red = (overlayArgb >>> 16 & 0xFF) * tintR / 255;
               int green = (overlayArgb >>> 8 & 0xFF) * tintG / 255;
               int blue = (overlayArgb & 0xFF) * tintB / 255;
               overlayArgb = alpha << 24 | red << 16 | green << 8 | blue;
               out.setColorArgb(x, y, blendArgb(out.getColorArgb(x, y), overlayArgb, alpha));
            }
         }
      }
   }

   private static void applyMessRole(MinecraftClient client, NativeImage out, int mess, int tintRgb, Identifier[] masks) {
      if (mess > 0 && masks != null && masks.length >= 3) {
         for (NonDestroyedSkinClient.MessLayer layer : messLayers(mess)) {
            if (layer.index() >= 0 && layer.index() < masks.length) {
               NativeImage mask = readMessMask(client, masks[layer.index()]);
               if (mask != null) {
                  applyTintMask(out, mask, tintRgb, layer.alpha());
                  mask.close();
               }
            }
         }
      }
   }

   private static List<NonDestroyedSkinClient.MessLayer> messLayers(int mess) {
      return switch (clampMess(mess)) {
         case 1 -> List.of(new NonDestroyedSkinClient.MessLayer(0, 85));
         case 2 -> List.of(new NonDestroyedSkinClient.MessLayer(0, 170));
         case 3 -> List.of(new NonDestroyedSkinClient.MessLayer(0, 255));
         case 4 -> List.of(new NonDestroyedSkinClient.MessLayer(0, 170), new NonDestroyedSkinClient.MessLayer(1, 85));
         case 5 -> List.of(new NonDestroyedSkinClient.MessLayer(0, 85), new NonDestroyedSkinClient.MessLayer(1, 170));
         case 6 -> List.of(new NonDestroyedSkinClient.MessLayer(1, 255));
         case 7 -> List.of(new NonDestroyedSkinClient.MessLayer(1, 170), new NonDestroyedSkinClient.MessLayer(2, 85));
         case 8 -> List.of(new NonDestroyedSkinClient.MessLayer(1, 85), new NonDestroyedSkinClient.MessLayer(2, 170));
         case 9, 10 -> List.of(new NonDestroyedSkinClient.MessLayer(2, 255));
         default -> List.of();
      };
   }

   private static void applyTintMask(NativeImage out, NativeImage mask, int tintRgb, int alpha) {
      int overlayBase = 0xFF000000 | tintRgb & 16777215;

      for (int y = 0; y < out.getHeight(); y++) {
         for (int x = 0; x < out.getWidth(); x++) {
            int factor = maskFactor(sampleScaled(mask, x, y, out.getWidth(), out.getHeight())) * alpha / 255;
            if (factor > 0) {
               out.setColorArgb(x, y, blendArgb(out.getColorArgb(x, y), overlayBase, factor));
            }
         }
      }
   }

   @Nullable
   private static NativeImage readMessMask(MinecraftClient client, Identifier maskId) {
      if (client != null && maskId != null) {
         try {
            Resource resource = (Resource)client.getResourceManager().getResource(maskId).orElse(null);
            if (resource == null) {
               return null;
            }

            try (InputStream in = resource.getInputStream()) {
               return NativeImage.read(in);
            }
         } catch (IOException | RuntimeException e) {
            setupWarn("[NoN] Failed to read mess mask {}.", maskId, e);
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean isValidDestroyedSkinPng(byte[] bytes) {
      if (bytes != null && bytes.length != 0 && bytes.length <= 1048576) {
         try {
            NativeImage image = NativeImage.read(bytes);

            boolean var3;
            try {
               int width = image.getWidth();
               var3 = width == image.getHeight() && width >= 64 && width <= 512 && width % 64 == 0;
            } catch (Throwable var5) {
               if (image != null) {
                  try {
                     image.close();
                  } catch (Throwable var4) {
                     var5.addSuppressed(var4);
                  }
               }

               throw var5;
            }

            if (image != null) {
               image.close();
            }

            return var3;
         } catch (IOException | RuntimeException e) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean isValidMaskPng(byte[] bytes) {
      if (bytes != null && bytes.length != 0 && bytes.length <= 65536) {
         try {
            NativeImage image = NativeImage.read(bytes);

            boolean var2;
            try {
               var2 = image.getWidth() == 64 && image.getHeight() == 64;
            } catch (Throwable var5) {
               if (image != null) {
                  try {
                     image.close();
                  } catch (Throwable var4) {
                     var5.addSuppressed(var4);
                  }
               }

               throw var5;
            }

            if (image != null) {
               image.close();
            }

            return var2;
         } catch (IOException | RuntimeException e) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static int[] getOriginalSkinColors(MinecraftClient client, UUID playerUuid, @Nullable Identifier baseSkin) {
      if (baseSkin == null) {
         return new int[]{15920872};
      }

      NonExternalSkinCompat.SkinContext externalSkin = effectiveExternalSkin(client, playerUuid, baseSkin);
      NonDestroyedSkinClient.SkinColorKey key = new NonDestroyedSkinClient.SkinColorKey(
         baseSkin, textureIdentity(client, baseSkin), externalSkinKey(externalSkin)
      );
      int[] cached = ORIGINAL_SKIN_COLOR_CACHE.get(key);
      if (cached != null && cached.length > 0) {
         return cached;
      }

      int[] colors = readOriginalSkinColors(client, baseSkin, externalSkin);
      ORIGINAL_SKIN_COLOR_CACHE.put(key, colors);
      return colors;
   }

   private static int[] readOriginalSkinColors(MinecraftClient client, Identifier baseSkin, @Nullable NonExternalSkinCompat.SkinContext externalSkin) {
      try {
         Resource maskResource = (Resource)client.getResourceManager().getResource(RIPPED_FABRIC_COLOR_MASK).orElse(null);
         if (maskResource == null) {
            return new int[]{15920872};
         }

         NativeImage image = readTextureImage(client, baseSkin, externalSkin);
         if (image == null) {
            return new int[]{15920872};
         }

         NativeImage var5 = image;

         int[] result;
         label144: {
            int[] var23;
            try (InputStream maskIn = maskResource.getInputStream()) {
               label147: {
                  NativeImage mask = NativeImage.read(maskIn);

                  label125: {
                     try {
                        int[] colors = new int[image.getWidth() * image.getHeight()];
                        int count = 0;

                        for (int y = 0; y < image.getHeight(); y++) {
                           for (int x = 0; x < image.getWidth(); x++) {
                              int argb = image.getColorArgb(x, y);
                              if ((argb >>> 24 & 0xFF) >= 16) {
                                 int maskArgb = sampleScaled(mask, x, y, image.getWidth(), image.getHeight());
                                 if (maskFactor(maskArgb) > 0) {
                                    colors[count++] = argb & 16777215;
                                 }
                              }
                           }
                        }

                        if (count <= 0) {
                           result = new int[]{15920872};
                           break label125;
                        }

                        result = new int[count];
                        System.arraycopy(colors, 0, result, 0, count);
                        var23 = result;
                     } catch (Throwable var17) {
                        if (mask != null) {
                           try {
                              mask.close();
                           } catch (Throwable var16) {
                              var17.addSuppressed(var16);
                           }
                        }

                        throw var17;
                     }

                     if (mask != null) {
                        mask.close();
                     }
                     break label147;
                  }

                  if (mask != null) {
                     mask.close();
                  }
                  break label144;
               }
            } catch (Throwable var19) {
               if (var5 != null) {
                  try {
                     var5.close();
                  } catch (Throwable var14) {
                     var19.addSuppressed(var14);
                  }
               }

               throw var19;
            }

            if (var5 != null) {
               var5.close();
            }

            return var23;
         }

         if (var5 != null) {
            var5.close();
         }

         return result;
      } catch (IOException | RuntimeException e) {
         return new int[]{15920872};
      }
   }

   private static int varyColor(int rgb, Random random) {
      float factor = 0.85F + random.nextFloat() * 0.3F;
      int r = Math.min(255, Math.max(0, Math.round((rgb >> 16 & 0xFF) * factor)));
      int g = Math.min(255, Math.max(0, Math.round((rgb >> 8 & 0xFF) * factor)));
      int b = Math.min(255, Math.max(0, Math.round((rgb & 0xFF) * factor)));
      return r << 16 | g << 8 | b;
   }

   private static void clearCachedTextures(UUID playerUuid) {
      MinecraftClient client = MinecraftClient.getInstance();
      List<Identifier> texturesToDestroy = new ArrayList<>();
      TEXTURE_CACHE.entrySet().removeIf(entry -> {
         if (!entry.getKey().playerUuid().equals(playerUuid)) {
            return false;
         }

         texturesToDestroy.add(entry.getValue());
         return true;
      });
      destroyTextures(client, texturesToDestroy);
   }

   private static void destroyTextures(@Nullable MinecraftClient client, List<Identifier> textureIds) {
      if (client != null && textureIds != null && !textureIds.isEmpty()) {
         List<Identifier> ids = List.copyOf(textureIds);
         client.execute(() -> {
            for (Identifier id : ids) {
               client.getTextureManager().destroyTexture(id);
            }
         });
      }
   }

   private static int sampleScaled(NativeImage source, int x, int y, int targetWidth, int targetHeight) {
      int sourceX = Math.min(source.getWidth() - 1, Math.max(0, x * source.getWidth() / targetWidth));
      int sourceY = Math.min(source.getHeight() - 1, Math.max(0, y * source.getHeight() / targetHeight));
      return source.getColorArgb(sourceX, sourceY);
   }

   private static void copyScaled(NativeImage source, NativeImage target) {
      for (int y = 0; y < target.getHeight(); y++) {
         for (int x = 0; x < target.getWidth(); x++) {
            target.setColorArgb(x, y, sampleScaled(source, x, y, target.getWidth(), target.getHeight()));
         }
      }
   }

   private static NativeImage scaledCopy(NativeImage source, int width, int height) {
      NativeImage target = new NativeImage(Format.RGBA, width, height, false);
      copyScaled(source, target);
      return target;
   }

   private static int clampStage(int stage) {
      return Math.max(0, Math.min(4, stage));
   }

   private static int clampMess(int value) {
      return Math.max(0, Math.min(10, value));
   }

   private static NonDestroyedSkinClient.TankMaskType resolveTankMaskType(@Nullable LivingEntity entity) {
      if (entity instanceof GenderHolder holder) {
         int mask = holder.getGenderMask() & 3;
         if (mask == 1) {
            return NonDestroyedSkinClient.TankMaskType.A;
         }
      }

      return NonDestroyedSkinClient.TankMaskType.V;
   }

   private static void setupWarn(String template, Object... args) {
      NeedsOfNature.LOGGER.warn(template, args);
      NeedsOfNatureClient.sendClientSetupWarning(NeedsOfNature.formatLogTemplate(template, args));
   }

   private static void markAutomaticSkinUnavailable(String template, Object... args) {
      if (!automaticSkinUnavailableThisConnection) {
         setupWarn(template, args);
      }

      automaticSkinUnavailableThisConnection = true;
      uploadRetryTicks = 0;
   }

   private static final class ColorBucket {
      long r;
      long g;
      long b;
      int count;

      void add(int rgb, int weight) {
         this.r += (long)(rgb >>> 16 & 0xFF) * weight;
         this.g += (long)(rgb >>> 8 & 0xFF) * weight;
         this.b += (long)(rgb & 0xFF) * weight;
         this.count += weight;
      }

      int averageR() {
         return this.count <= 0 ? 0 : (int)(this.r / this.count);
      }

      int averageG() {
         return this.count <= 0 ? 0 : (int)(this.g / this.count);
      }

      int averageB() {
         return this.count <= 0 ? 0 : (int)(this.b / this.count);
      }
   }

   private record DefaultSkinCandidate(Identifier genderSpecific, boolean genderFound, Identifier generic, boolean genericFound) {
      String logString() {
         return this.genderSpecific + "=" + this.genderFound + ", " + this.generic + "=" + this.genericFound;
      }
   }

   private record DefaultSkinKey(String model, String name) {
   }

   private record DefaultSkinMatch(NonDestroyedSkinClient.DefaultSkinKey key, double confidence) {
      boolean accepted() {
         return this.confidence >= 0.9;
      }
   }

   private record EffectiveBaseTexture(Identifier id, int textureIdentity, @Nullable NonExternalSkinCompat.SkinContext externalSkin) {
   }

   private record MessLayer(int index, int alpha) {
   }

   private record MessState(int vMess, int aMess, int mMess, int tintRgb, int liquidStored, int liquidCapacity) {
      static final NonDestroyedSkinClient.MessState EMPTY = new NonDestroyedSkinClient.MessState(0, 0, 0, 16777215, 0, 0);

      boolean hasAnyMess() {
         return this.vMess > 0 || this.aMess > 0 || this.mMess > 0;
      }

      boolean hasTankOverlay() {
         return this.liquidStored > 0 && this.liquidCapacity > 0;
      }

      boolean hasAnyVisual() {
         return this.hasAnyMess() || this.hasTankOverlay();
      }

      int tankAlpha() {
         if (!this.hasTankOverlay()) {
            return 0;
         }

         float ratio = Math.max(0.0F, Math.min(1.0F, (float)this.liquidStored / this.liquidCapacity));
         return Math.max(3, Math.min(255, Math.round(3.0F + ratio * 252.0F)));
      }

      String cacheKey() {
         return NonDestroyedSkinClient.clampMess(this.vMess)
            + "_"
            + NonDestroyedSkinClient.clampMess(this.aMess)
            + "_"
            + NonDestroyedSkinClient.clampMess(this.mMess)
            + "_"
            + Math.max(0, this.liquidStored)
            + "_"
            + Math.max(0, this.liquidCapacity)
            + "_"
            + Integer.toHexString(this.tintRgb & 16777215);
      }
   }

   private static final class MissingDestroyedSkinFallbackException extends IOException {
      private MissingDestroyedSkinFallbackException(String message) {
         super(message);
      }
   }

   private record SkinColorKey(Identifier textureId, int textureIdentity, String externalSkinKey) {
   }

   private enum TankMaskType {
      V("v"),
      A("a");

      private final String id;

      TankMaskType(String id) {
         this.id = id;
      }
   }

   private record TextureKey(
      UUID playerUuid,
      int stage,
      @Nullable Identifier baseTexture,
      int baseTextureIdentity,
      String externalSkinKey,
      NonDestroyedSkinClient.MessState messState,
      NonDestroyedSkinClient.TankMaskType tankMaskType,
      List<NonTrinketsIntegration.ResolvedVisualLayer> accessoryOverlays
   ) {
   }

   private record TextureReadKey(Identifier textureId, int textureIdentity, String externalSkinKey) {
   }
}
