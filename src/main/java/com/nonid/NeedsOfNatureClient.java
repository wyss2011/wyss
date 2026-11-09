package com.nonid;

import com.google.gson.Gson;
import com.nonid.api.client.NonAnimationParticleEvents;
import com.nonid.api.client.NonAnimationRenderEvents;
import com.nonid.api.client.NonAnimationSoundEvents;
import com.nonid.client.HorseLiquidCollectorGeoModel;
import com.nonid.client.HorseLiquidCollectorRenderer;
import com.nonid.client.NonAccessoryTooltips;
import com.nonid.client.NonArmorShedClient;
import com.nonid.client.NonClientDiagnostics;
import com.nonid.client.NonClientResourceCaches;
import com.nonid.client.NonDebugSpinMode;
import com.nonid.client.NonDebugSpinMusicClient;
import com.nonid.client.NonDestroyedSkinClient;
import com.nonid.client.NonGenderSelectionScreen;
import com.nonid.client.NonHudOverlay;
import com.nonid.client.NonInventoryScreenHooks;
import com.nonid.client.NonPackStartupWarnings;
import com.nonid.client.NonPlayerInteractionClient;
import com.nonid.client.NonPregnancyClientState;
import com.nonid.client.NonServerSettingsClient;
import com.nonid.client.NonSoundCues;
import com.nonid.client.NonSpinModelLock;
import com.nonid.client.NonWildfireGenderScreenHooks;
import com.nonid.client.NonWildfireGenderSync;
import com.nonid.client.PregnancyEggRenderer;
import com.nonid.client.intiface.NonIntifaceBridge;
import com.nonid.client.particle.LiquidFallingParticle;
import com.nonid.client.particle.LiquidParticle;
import com.nonid.client.particle.LiquidPuddleParticle;
import com.nonid.client.particle.LiquidWaterParticle;
import com.nonid.client.particle.RippedFabricParticle;
import com.nonid.client.particle.SmallHeartParticle;
import com.nonid.data.NonAccessoryItemRegistry;
import com.nonid.data.NonPeakStages;
import com.nonid.entity.HorseLiquidCollectorEntity;
import com.nonid.integration.NonCreditsButtonHooks;
import com.nonid.internal.animation.AnimationEngine;
import com.nonid.internal.animation.AnimationEngineClient;
import com.nonid.internal.animation.client.render.gecko.GeckoResourceResolver;
import com.nonid.internal.animation.client.render.gecko.GenderedModelResolver;
import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import com.nonid.internal.animation.client.sound.AnimationSoundAnticipation;
import com.nonid.internal.animation.data.AnimationCueEffects;
import com.nonid.internal.animation.data.AnimationGameplayMetadata;
import com.nonid.internal.animation.mixin.client.EntityRenderManagerAccessor;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.util.AnimationIds;
import com.nonid.network.AccessoryShedStateS2CPayload;
import com.nonid.network.AnimationCatalogChunkS2CPayload;
import com.nonid.network.AttackStateS2CPayload;
import com.nonid.network.DestroyedSkinMaskSyncS2CPayload;
import com.nonid.network.DestroyedSkinParticlesS2CPayload;
import com.nonid.network.DestroyedSkinStateS2CPayload;
import com.nonid.network.DestroyedSkinSyncS2CPayload;
import com.nonid.network.GenderSelectionPromptS2CPayload;
import com.nonid.network.HostConfigSyncData;
import com.nonid.network.HostConfigSyncS2CPayload;
import com.nonid.network.LiquidStateS2CPayload;
import com.nonid.network.MessStateS2CPayload;
import com.nonid.network.PlayerAttackInputC2SPayload;
import com.nonid.network.PlayerRuntimeSettingsS2CPayload;
import com.nonid.network.PregnancyCycleStateS2CPayload;
import com.nonid.network.PregnancyStateS2CPayload;
import com.nonid.network.ServerConfigResultS2CPayload;
import com.nonid.network.SetPlayerGenderC2SPayload;
import com.nonid.network.SkinRippedInfoRequestS2CPayload;
import com.nonid.network.StartManualPeakC2SPayload;
import com.nonid.network.StopAttackAnimationC2SPayload;
import com.nonid.network.StopVoluntaryAnimationC2SPayload;
import com.nonid.particle.NonParticles;
import com.nonid.potion.NonPotions;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStarted;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.render.entity.EntityRendererFactories;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentModelLoader;
import net.minecraft.client.render.entity.equipment.EquipmentModel.Dyeable;
import net.minecraft.client.render.entity.equipment.EquipmentModel.Layer;
import net.minecraft.client.render.entity.equipment.EquipmentModel.LayerType;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AbstractDonkeyEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.HorseMarking;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.Resource;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

public class NeedsOfNatureClient implements ClientModInitializer {
   private static final Gson HOST_CONFIG_SYNC_GSON = new Gson();
   private static final Identifier WOLF_COLLAR_TEXTURE = Identifier.ofVanilla("textures/entity/wolf/wolf_collar.png");
   private static final Identifier SHEEP_WOOL_TEXTURE = Identifier.ofVanilla("textures/entity/sheep/sheep_wool.png");
   private static final Identifier SHEEP_WOOL_UNDERCOAT_TEXTURE = Identifier.ofVanilla("textures/entity/sheep/sheep_wool_undercoat.png");
   private static final Identifier FILL_BOTTLE_ANIMATION_ID = Identifier.of("needsofnature", "player_fill_bottle");
   private static final String FILL_BOTTLE_PROP_CUE_EFFECT = "fillbottle_prop_fill";
   private static final Identifier VANILLA_BOTTLE_FILL_SOUND = Identifier.ofVanilla("item.bottle.fill");
   private static final String RIGHT_PROP_BONE = "propright";
   private static final float REACTIVE_IMPACT_FOV_DELTA = 0.5F;
   private static final float REACTIVE_IMPACT_HOLD_TICKS = 3.0F;
   private static final float REACTIVE_IMPACT_RECOVER_TICKS = 14.0F;
   private static final double NANOS_PER_CLIENT_TICK = 5.0E7;
   private static final int INTIFACE_BIRTH_INTRO_STAGE = 1;
   private static final int INTIFACE_BIRTH_LOOP_STAGE = 2;
   private static final int INTIFACE_BIRTH_FINAL_STAGE = 3;
   private static final double INTIFACE_BIRTH_LOOP_START_SCALAR = 0.6;
   private static final Set<String> INVALID_PARTICLE_COLOR_SOURCES_WARNED = new HashSet<>();
   private static long CLIENT_FALLBACK_TICKS = 0L;
   private static volatile long REACTIVE_IMPACT_PULSE_START_NANOS = Long.MIN_VALUE;
   private static int LAST_LOCAL_GENDER_SYNC_MASK = 0;
   private static final Map<Integer, Identifier> WOLF_COLLAR_TINT_CACHE = new HashMap<>();
   private static final Map<Integer, Identifier> SHEEP_WOOL_TINT_CACHE = new HashMap<>();
   private static final Map<Integer, Identifier> SHEEP_WOOL_UNDERCOAT_TINT_CACHE = new HashMap<>();
   private static final Map<String, Identifier> EQUIPMENT_TINT_CACHE = new HashMap<>();
   private static final Set<String> SHEEP_WOOL_BONES = Set.of("bodywool", "headwool", "leg1wool", "leg2wool", "leg3wool", "leg4wool");
   private static final int PLAYER_OUTER_SKIN_CUBE_INDEX = 1;
   private static final Category NON_KEY_CATEGORY = Category.create(Identifier.of("needsofnature", "main"));
   private static final KeyBinding START_MANUAL_PEAK_KEY = KeyBindingHelper.registerKeyBinding(
      new KeyBinding("key.needsofnature.start_manual_peak", Type.KEYSYM, 77, NON_KEY_CATEGORY)
   );

   public static void sendClientSetupWarning(String message) {
      NonClientDiagnostics.sendSetupWarning(message, true);
   }

   public void onInitializeClient() {
      ClientLifecycleEvents.CLIENT_STARTED.register((ClientStarted)client -> NonStats.registerKnownEntityStats());
      NonDestroyedSkinClient.ensureConfigDirectory();
      AnimationEngineClient.initialize();
      EntityRendererFactories.register(NeedsOfNature.HORSE_LIQUID_COLLECTOR_ENTITY_TYPE, HorseLiquidCollectorRenderer::new);
      EntityRendererFactories.register(NeedsOfNature.PREGNANCY_EGG_ENTITY_TYPE, PregnancyEggRenderer::new);
      NonHudOverlay.registerReloadListener();
      NonClientResourceCaches.registerReloadListener();
      ClientTickEvents.END_CLIENT_TICK.register(NonHudOverlay::clientTick);
      ClientTickEvents.END_CLIENT_TICK.register(NonSpinModelLock::clientTick);
      ClientTickEvents.END_CLIENT_TICK.register(NonDebugSpinMusicClient::clientTick);
      ClientTickEvents.END_CLIENT_TICK.register(NonPackStartupWarnings::clientTick);
      ClientTickEvents.END_CLIENT_TICK.register(NonDestroyedSkinClient::clientTick);
      ClientTickEvents.END_CLIENT_TICK.register(NonArmorShedClient::clientTick);
      NonAccessoryTooltips.register();
      NonInventoryScreenHooks.register();
      NonCreditsButtonHooks.register();
      NonPlayerInteractionClient.register();
      NonWildfireGenderScreenHooks.register();
      NonArmorShedClient.register();
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> CLIENT_FALLBACK_TICKS++);
      ClientTickEvents.END_CLIENT_TICK.register(NeedsOfNatureClient::syncLocalGenderMirror);
      ClientTickEvents.END_CLIENT_TICK.register(NonWildfireGenderSync::clientTick);
      ClientTickEvents.END_CLIENT_TICK.register(NeedsOfNatureClient::tickIntifaceBaseline);
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
         NonIntifaceBridge.onWorldDisconnect(NeedsOfNature.getConfig());
         NeedsOfNature.clearRemoteHostRules();
         NonHudOverlay.onDisconnect(client);
         NonPregnancyClientState.clear();
         NonDestroyedSkinClient.clear();
         NonArmorShedClient.clear();
         NonAccessoryShedSystem.clearAll();
         NonPeakStages.clearSyncedPeakStages();
         NonDebugSpinMusicClient.stopAll(client);
         REACTIVE_IMPACT_PULSE_START_NANOS = Long.MIN_VALUE;
         LAST_LOCAL_GENDER_SYNC_MASK = 0;
         NonWildfireGenderSync.onWorldDisconnect();
         NonPlayerInteractionClient.clear();
         NonServerSettingsClient.clear();
      });
      ClientPlayConnectionEvents.JOIN.register((Join)(handler, sender, client) -> client.execute(() -> {
         NonWildfireGenderSync.onWorldJoin();
         pushConfiguredPlayerGenderToServer();
         NonDestroyedSkinClient.scheduleLocalSkinUploadRetry();
         NonIntifaceBridge.onWorldJoin(NeedsOfNature.getConfig());
      }));
      ClientTickEvents.END_CLIENT_TICK.register(NeedsOfNatureClient::handleVoluntaryStopInput);
      ClientTickEvents.END_CLIENT_TICK.register(NeedsOfNatureClient::handleAttackInput);
      ClientTickEvents.END_CLIENT_TICK.register(NeedsOfNatureClient::handleManualPeakInput);
      ClientPlayNetworking.registerGlobalReceiver(
         AttackStateS2CPayload.ID,
         (payload, context) -> context.client()
            .execute(
               () -> NonHudOverlay.setEscapeControlState(
                  payload.instanceId(), payload.attack(), payload.escapable(), payload.escapeProfile(), payload.pvpRole(), payload.progress()
               )
            )
      );
      ClientPlayNetworking.registerGlobalReceiver(LiquidStateS2CPayload.ID, (payload, context) -> context.client().execute(() -> {
         NonHudOverlay.setLiquidState(payload.stored(), payload.capacity(), payload.tintRgb());
         MinecraftClient client = context.client();
         if (client.player instanceof LiquidHolder holder) {
            holder.setLiquidStored(payload.stored());
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(PregnancyStateS2CPayload.ID, (payload, context) -> context.client().execute(() -> {
         String raw = payload.pregnantEntityTypeId();
         Identifier id = raw != null && !raw.isBlank() ? Identifier.tryParse(raw) : null;
         NonPregnancyClientState.setPregnantEntityTypeId(id);
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         PregnancyCycleStateS2CPayload.ID,
         (payload, context) -> context.client().execute(() -> NonPregnancyClientState.setCycleState(payload.enabled(), payload.day(), payload.phase()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         PlayerRuntimeSettingsS2CPayload.ID,
         (payload, context) -> context.client().execute(() -> NonHudOverlay.setRuntimeAttackEscapeHits(payload.attackEscapeHits()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         GenderSelectionPromptS2CPayload.ID,
         (payload, context) -> context.client()
            .execute(
               () -> MinecraftClient.getInstance().setScreen(new NonGenderSelectionScreen(payload.allowedMask(), payload.currentMask(), payload.permanent()))
            )
      );
      ClientPlayNetworking.registerGlobalReceiver(HostConfigSyncS2CPayload.ID, (payload, context) -> context.client().execute(() -> {
         String raw = payload.json();
         if (raw != null && !raw.isBlank()) {
            try {
               HostConfigSyncData data = (HostConfigSyncData)HOST_CONFIG_SYNC_GSON.fromJson(raw, HostConfigSyncData.class);
               if (data == null) {
                  return;
               }

               MinecraftClient client = context.client();
               if (client.getServer() == null) {
                  NeedsOfNature.applyRemoteHostRules(data);
               }

               NonServerSettingsClient.applyConfigSnapshot(payload.editableJson(), payload.revision(), payload.canEdit());
            } catch (RuntimeException e) {
               NeedsOfNature.LOGGER.warn("[NoN] Failed to parse host config sync payload.", e);
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         AnimationCatalogChunkS2CPayload.ID,
         (payload, context) -> context.client()
            .execute(() -> NonServerSettingsClient.acceptCatalogChunk(payload.revision(), payload.index(), payload.count(), payload.data()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         ServerConfigResultS2CPayload.ID, (payload, context) -> context.client().execute(() -> NonServerSettingsClient.handleConfigResult(payload))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         DestroyedSkinSyncS2CPayload.ID,
         (payload, context) -> context.client().execute(() -> NonDestroyedSkinClient.setSkin(payload.playerUuid(), payload.stage(), payload.pngBytes()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         DestroyedSkinMaskSyncS2CPayload.ID,
         (payload, context) -> context.client().execute(() -> NonDestroyedSkinClient.setMask(payload.playerUuid(), payload.stage(), payload.pngBytes()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         DestroyedSkinStateS2CPayload.ID,
         (payload, context) -> context.client().execute(() -> NonDestroyedSkinClient.setStage(payload.playerUuid(), payload.stage()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         DestroyedSkinParticlesS2CPayload.ID,
         (payload, context) -> context.client()
            .execute(
               () -> NonDestroyedSkinClient.spawnRippedFabricParticles(
                  payload.playerUuid(), payload.x(), payload.y(), payload.z(), payload.count(), payload.seed()
               )
            )
      );
      ClientPlayNetworking.registerGlobalReceiver(
         MessStateS2CPayload.ID,
         (payload, context) -> context.client()
            .execute(
               () -> NonDestroyedSkinClient.setMess(
                  payload.playerUuid(), payload.vMess(), payload.aMess(), payload.mMess(), payload.tintRgb(), payload.liquidStored(), payload.liquidCapacity()
               )
            )
      );
      ClientPlayNetworking.registerGlobalReceiver(
         AccessoryShedStateS2CPayload.ID,
         (payload, context) -> context.client()
            .execute(
               () -> NonAccessoryShedSystem.setClientShedState(payload.playerUuid(), payload.shedV(), payload.shedA(), payload.forcedV(), payload.forcedA())
            )
      );
      ClientPlayNetworking.registerGlobalReceiver(
         SkinRippedInfoRequestS2CPayload.ID, (payload, context) -> context.client().execute(NonDestroyedSkinClient::sendDetectedRippedSkinInfo)
      );
      registerSoundCueResolver();
      registerIntifaceCueAnticipation();
      registerParticleCueResolver();
      ParticleFactoryRegistry.getInstance().register(NonParticles.LIQUID_PARTICLE, LiquidParticle.Factory::new);
      ParticleFactoryRegistry.getInstance().register(NonParticles.LIQUID_PARTICLE_FALLING, LiquidFallingParticle.Factory::new);
      ParticleFactoryRegistry.getInstance().register(NonParticles.LIQUID_PARTICLE_PUDDLE, LiquidPuddleParticle.Factory::new);
      ParticleFactoryRegistry.getInstance().register(NonParticles.LIQUID_PARTICLE_WATER, LiquidWaterParticle.Factory::new);
      ParticleFactoryRegistry.getInstance().register(NonParticles.SMALLHEART, SmallHeartParticle.Factory::new);
      ParticleFactoryRegistry.getInstance().register(NonParticles.RIPPED_FABRIC, RippedFabricParticle.Factory::new);
      NonAnimationRenderEvents.RESOLVE_RENDER
         .register(
            (NonAnimationRenderEvents.RenderResolver)(entity, entityTypeId, currentModel, currentTexture) -> {
               if (NonDebugSpinMode.isEnabled()) {
                  return new NonAnimationRenderEvents.RenderOverride(NonDebugSpinMode.DEBUG_SPIN_MODEL_RESOURCE, NonDebugSpinMode.DEBUG_SPIN_TEXTURE_RESOURCE);
               }

               NonAnimationRenderEvents.RenderOverride collectorOverride = resolveCollectorRenderOverride(entity);
               if (collectorOverride != null) {
                  return collectorOverride;
               }

               Identifier destroyedBaseTexture = NonDestroyedSkinClient.resolveBaseTexture(entity, resolveDestroyedSkinBaseTexture(entity, currentTexture));
               Identifier destroyedOverlayTexture = destroyedBaseTexture == null ? NonDestroyedSkinClient.resolveOverlayTexture(entity) : null;
               List<Identifier> layers = new ArrayList<>();
               if (destroyedOverlayTexture != null) {
                  layers.add(destroyedOverlayTexture);
               }

               layers.addAll(resolveSheepOverlays(entity));
               layers.addAll(resolveHorseOverlays(entity));
               layers.addAll(resolveWolfOverlays(entity));
               List<Identifier> layerTextures = layers.isEmpty() ? List.of() : List.copyOf(layers);
               ItemStack maleRoleAccessory = NonTrinketsIntegration.getMaleRoleAccessoryStack(entity);
               boolean forceMaleModel = !maleRoleAccessory.isEmpty();
               Map<String, Identifier> boneTextures = mergeIdentifierMaps(resolveSheepBoneTextures(entity), resolveAccessoryBoneTextures(maleRoleAccessory));
               Map<String, Boolean> boneVisibility = mergeBooleanMaps(resolveDonkeyChestBoneVisibility(entity), resolveSheepBoneVisibility(entity));
               Map<String, Set<Integer>> hiddenBoneCubeIndices = resolvePlayerSkinPartHiddenCubeIndices(entity);
               boolean hasLayerTextures = !layerTextures.isEmpty();
               boolean hasBoneTextures = !boneTextures.isEmpty();
               boolean hasBoneVisibility = !boneVisibility.isEmpty();
               boolean hasHiddenBoneCubeIndices = !hiddenBoneCubeIndices.isEmpty();
               Identifier baseModel = currentModel;
               if (baseModel == null && entityTypeId != null) {
                  baseModel = AnimationEngine.packId("entity/" + entityTypeId.getPath());
               }

               if (entity instanceof GenderHolder holder) {
                  GenderedModelResolver.ModelGender modelGender = forceMaleModel
                     ? GenderedModelResolver.ModelGender.MALE
                     : GenderedModelResolver.fromMask(holder.getGenderMask());
                  if (baseModel == null) {
                     return destroyedBaseTexture == null && !hasLayerTextures && !hasBoneTextures && !hasBoneVisibility && !hasHiddenBoneCubeIndices
                        ? null
                        : createAnimationRenderOverride(
                           null,
                           destroyedBaseTexture,
                           hasLayerTextures ? layerTextures : null,
                           hasBoneTextures ? boneTextures : null,
                           null,
                           hasBoneVisibility ? boneVisibility : null,
                           hasHiddenBoneCubeIndices ? hiddenBoneCubeIndices : null
                        );
                  }

                  if (!forceMaleModel && GenderedModelResolver.hasGenderSuffix(baseModel)) {
                     return destroyedBaseTexture == null && !hasLayerTextures && !hasBoneTextures && !hasBoneVisibility && !hasHiddenBoneCubeIndices
                        ? null
                        : createAnimationRenderOverride(
                           null,
                           destroyedBaseTexture,
                           hasLayerTextures ? layerTextures : null,
                           hasBoneTextures ? boneTextures : null,
                           null,
                           hasBoneVisibility ? boneVisibility : null,
                           hasHiddenBoneCubeIndices ? hiddenBoneCubeIndices : null
                        );
                  }

                  Identifier model;
                  if (forceMaleModel) {
                     List<Identifier> modelRoots = ClientAnimationRuntime.findLatestModelRootsForActor(entity.getUuid());
                     model = GeckoResourceResolver.resolveGenderedModelAcrossRoots(baseModel, modelRoots, GenderedModelResolver.ModelGender.MALE);
                     if (model == null) {
                        model = resolveExistingGenderedModel(GenderedModelResolver.stripGenderSuffix(baseModel), GenderedModelResolver.ModelGender.MALE);
                     }
                  } else {
                     model = resolveExistingGenderedModel(baseModel, modelGender);
                  }

                  if (model != null && model.equals(baseModel)) {
                     model = null;
                  }

                  return createAnimationRenderOverride(
                     model,
                     destroyedBaseTexture,
                     hasLayerTextures ? layerTextures : null,
                     hasBoneTextures ? boneTextures : null,
                     null,
                     hasBoneVisibility ? boneVisibility : null,
                     hasHiddenBoneCubeIndices ? hiddenBoneCubeIndices : null
                  );
               } else {
                  Identifier defaultGenderModel = resolveExistingGenderedModel(baseModel, GenderedModelResolver.ModelGender.MALE);
                  if (defaultGenderModel != null && defaultGenderModel.equals(baseModel)) {
                     defaultGenderModel = null;
                  }

                  return defaultGenderModel == null
                        && destroyedBaseTexture == null
                        && !hasLayerTextures
                        && !hasBoneTextures
                        && !hasBoneVisibility
                        && !hasHiddenBoneCubeIndices
                     ? null
                     : createAnimationRenderOverride(
                        defaultGenderModel,
                        destroyedBaseTexture,
                        hasLayerTextures ? layerTextures : null,
                        hasBoneTextures ? boneTextures : null,
                        null,
                        hasBoneVisibility ? boneVisibility : null,
                        hasHiddenBoneCubeIndices ? hiddenBoneCubeIndices : null
                     );
               }
            }
         );
   }

   @Nullable
   private static Identifier resolveExistingGenderedModel(@Nullable Identifier baseModel, GenderedModelResolver.ModelGender gender) {
      return GenderedModelResolver.resolveExisting(baseModel, gender, GeckoResourceResolver::hasGeoModel);
   }

   @Nullable
   private static NonAnimationRenderEvents.RenderOverride resolveCollectorRenderOverride(LivingEntity entity) {
      if (entity instanceof HorseLiquidCollectorEntity collector) {
         Identifier texture = collector.isCollectorFull() ? HorseLiquidCollectorGeoModel.FULL_TEXTURE_ID : HorseLiquidCollectorGeoModel.EMPTY_TEXTURE_ID;
         return new NonAnimationRenderEvents.RenderOverride(HorseLiquidCollectorGeoModel.MODEL_ID, texture);
      } else {
         return null;
      }
   }

   private static NonAnimationRenderEvents.RenderOverride createAnimationRenderOverride(
      @Nullable Identifier model,
      @Nullable Identifier texture,
      @Nullable List<Identifier> layerTextures,
      @Nullable Map<String, Identifier> boneTextures,
      @Nullable Map<String, NonAnimationRenderEvents.BoneItemProp> boneItems,
      @Nullable Map<String, Boolean> boneVisibility,
      @Nullable Map<String, Set<Integer>> hiddenBoneCubeIndices
   ) {
      return new NonAnimationRenderEvents.RenderOverride(model, texture, layerTextures, boneTextures, boneItems, boneVisibility, hiddenBoneCubeIndices);
   }

   @Nullable
   private static Identifier resolveDestroyedSkinBaseTexture(LivingEntity entity, @Nullable Identifier currentTexture) {
      if (entity instanceof AbstractClientPlayerEntity player) {
         try {
            SkinTextures skin = player.getSkin();
            if (skin != null && skin.body() != null && skin.body().texturePath() != null) {
               return skin.body().texturePath();
            }
         } catch (RuntimeException var4) {
         }
      }

      return currentTexture;
   }

   private static Map<String, Boolean> resolveDonkeyChestBoneVisibility(LivingEntity entity) {
      if (entity instanceof AbstractDonkeyEntity donkey) {
         return donkey.hasChest() ? Map.of() : Map.of("left_chest", false, "right_chest", false);
      } else {
         return Map.of();
      }
   }

   private static Map<String, Set<Integer>> resolvePlayerSkinPartHiddenCubeIndices(LivingEntity entity) {
      if (entity instanceof AbstractClientPlayerEntity player) {
         HashMap hiddenCubeIndices = new HashMap();
         hideOuterCubeIfDisabled(player, hiddenCubeIndices, PlayerModelPart.HAT, "head");
         hideOuterCubeIfDisabled(player, hiddenCubeIndices, PlayerModelPart.JACKET, "body");
         hideOuterCubeIfDisabled(player, hiddenCubeIndices, PlayerModelPart.RIGHT_SLEEVE, "rightarm");
         hideOuterCubeIfDisabled(player, hiddenCubeIndices, PlayerModelPart.LEFT_SLEEVE, "leftarm");
         hideOuterCubeIfDisabled(player, hiddenCubeIndices, PlayerModelPart.RIGHT_PANTS_LEG, "rightleg");
         hideOuterCubeIfDisabled(player, hiddenCubeIndices, PlayerModelPart.LEFT_PANTS_LEG, "leftleg");
         return hiddenCubeIndices.isEmpty() ? Map.of() : Map.copyOf(hiddenCubeIndices);
      } else {
         return Map.of();
      }
   }

   private static void hideOuterCubeIfDisabled(
      AbstractClientPlayerEntity player, Map<String, Set<Integer>> hiddenCubeIndices, PlayerModelPart part, String boneName
   ) {
      if (player != null && hiddenCubeIndices != null && part != null && boneName != null && !boneName.isBlank()) {
         if (!player.isModelPartVisible(part)) {
            hiddenCubeIndices.put(boneName, Set.of(1));
         }
      }
   }

   private static Map<String, Identifier> resolveSheepBoneTextures(LivingEntity entity) {
      if (!(entity instanceof SheepEntity sheep)) {
         return Map.of();
      } else {
         if (sheep.isSheared()) {
            return Map.of();
         }

         int rgb = getSheepColorRgb(sheep);
         Identifier woolTexture = getTintedTexture(SHEEP_WOOL_TEXTURE, rgb, "sheep_wool", SHEEP_WOOL_TINT_CACHE);
         Map<String, Identifier> textures = new HashMap<>();

         for (String bone : SHEEP_WOOL_BONES) {
            textures.put(bone, woolTexture);
         }

         return Map.copyOf(textures);
      }
   }

   private static Map<String, Identifier> resolveAccessoryBoneTextures(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         NonAccessoryItemRegistry.AccessoryItemDefinition definition = NonAccessoryItemRegistry.getDefinition(Registries.ITEM.getId(stack.getItem()));
         if (definition != null && !definition.actorVisualBehavior().animationBoneTextures().isEmpty()) {
            Map<String, Identifier> textures = new HashMap<>();

            for (Entry<String, NonAccessoryItemRegistry.VisualLayer> entry : definition.actorVisualBehavior().animationBoneTextures().entrySet()) {
               NonTrinketsIntegration.ResolvedVisualLayer layer = NonTrinketsIntegration.resolveVisualLayer(stack, entry.getValue());
               if (layer != null) {
                  Identifier texture = layer.tintRgb() == 16777215
                     ? layer.texture()
                     : getTintedEquipmentTexture(layer.texture(), layer.tintRgb(), "accessory_bone");
                  textures.put(entry.getKey(), texture);
               }
            }

            return textures.isEmpty() ? Map.of() : Map.copyOf(textures);
         } else {
            return Map.of();
         }
      } else {
         return Map.of();
      }
   }

   @SafeVarargs
   private static Map<String, Identifier> mergeIdentifierMaps(Map<String, Identifier>... maps) {
      if (maps != null && maps.length != 0) {
         Map<String, Identifier> merged = new HashMap<>();

         for (Map<String, Identifier> map : maps) {
            if (map != null && !map.isEmpty()) {
               merged.putAll(map);
            }
         }

         return merged.isEmpty() ? Map.of() : Map.copyOf(merged);
      } else {
         return Map.of();
      }
   }

   private static List<Identifier> resolveSheepOverlays(LivingEntity entity) {
      if (entity instanceof SheepEntity sheep) {
         Identifier undercoatTexture = getTintedTexture(
            SHEEP_WOOL_UNDERCOAT_TEXTURE, getSheepColorRgb(sheep), "sheep_wool_undercoat", SHEEP_WOOL_UNDERCOAT_TINT_CACHE
         );
         return List.of(undercoatTexture);
      } else {
         return List.of();
      }
   }

   private static Map<String, Boolean> resolveSheepBoneVisibility(LivingEntity entity) {
      if (!(entity instanceof SheepEntity sheep)) {
         return Map.of();
      } else {
         if (!sheep.isSheared()) {
            return Map.of();
         }

         Map<String, Boolean> visibility = new HashMap<>();

         for (String bone : SHEEP_WOOL_BONES) {
            visibility.put(bone, Boolean.FALSE);
         }

         return Map.copyOf(visibility);
      }
   }

   private static int getSheepColorRgb(SheepEntity sheep) {
      if (sheep == null) {
         return DyeColor.WHITE.getEntityColor();
      }

      DyeColor color = sheep.getColor();
      return color == null ? DyeColor.WHITE.getEntityColor() : color.getEntityColor();
   }

   @SafeVarargs
   private static Map<String, Boolean> mergeBooleanMaps(Map<String, Boolean>... maps) {
      if (maps != null && maps.length != 0) {
         Map<String, Boolean> merged = new HashMap<>();

         for (Map<String, Boolean> map : maps) {
            if (map != null && !map.isEmpty()) {
               merged.putAll(map);
            }
         }

         return merged.isEmpty() ? Map.of() : Map.copyOf(merged);
      } else {
         return Map.of();
      }
   }

   private static ItemStack createFilledBottlePropStack(LivingEntity entity) {
      int tint = NonHudOverlay.getLiquidTintRgb() & 16777215;
      if (tint == 0) {
         tint = 15920063;
      }

      PotionContentsComponent contents = new PotionContentsComponent(
         Optional.of(Registries.POTION.getEntry(NonPotions.LIQUID)), Optional.of(tint), List.of(), Optional.empty()
      );
      ItemStack stack = new ItemStack(Items.POTION);
      stack.set(DataComponentTypes.POTION_CONTENTS, contents);
      if (entity instanceof LiquidHolder holder && holder.getLiquidComposition() == LiquidHolder.LiquidComposition.ENTITY) {
         NonPotions.setLiquidBottleEntityTypeId(stack, holder.getLiquidEntityTypeId());
      } else {
         NonPotions.setLiquidBottleEntityTypeId(stack, null);
      }

      return stack;
   }

   private static boolean isFillBottleAnimation(Identifier animationId) {
      if (animationId == null) {
         return false;
      } else if (!FILL_BOTTLE_ANIMATION_ID.getNamespace().equals(animationId.getNamespace())) {
         return false;
      } else {
         String path = animationId.getPath();
         if (path != null && !path.isBlank()) {
            String normalized = path.toLowerCase(Locale.ROOT).replaceFirst("\\.p\\d+$", "");
            return FILL_BOTTLE_ANIMATION_ID.getPath().equals(normalized);
         } else {
            return false;
         }
      }
   }

   private static List<Identifier> resolveHorseOverlays(LivingEntity entity) {
      if (entity instanceof AbstractHorseEntity horse) {
         if (horse.isBaby()) {
            return List.of();
         }

         List<Identifier> overlays = new ArrayList<>();
         if (horse instanceof HorseEntity vanillaHorse) {
            HorseMarking marking = vanillaHorse.getMarking();
            Identifier markingTexture = mapHorseMarkingTexture(marking);
            if (markingTexture != null) {
               overlays.add(markingTexture);
            }

            addSaddleOverlay(overlays, vanillaHorse);
            ItemStack armor = vanillaHorse.getEquippedStack(EquipmentSlot.BODY);
            if (!armor.isEmpty()) {
               overlays.addAll(resolveHorseArmorTextures(armor));
            }

            return overlays.isEmpty() ? List.of() : List.copyOf(overlays);
         } else {
            addSaddleOverlay(overlays, horse);
            return overlays.isEmpty() ? List.of() : List.copyOf(overlays);
         }
      } else {
         return List.of();
      }
   }

   private static void addSaddleOverlay(List<Identifier> overlays, AbstractHorseEntity horse) {
      if (overlays != null && horse != null) {
         ItemStack saddle = horse.getEquippedStack(EquipmentSlot.SADDLE);
         if (!saddle.isEmpty()) {
            overlays.addAll(resolveEquipmentLayerTextures(EquipmentAssetKeys.SADDLE, saddle, resolveHorseSaddleLayerType(horse), "horse_saddle"));
         }
      }
   }

   private static LayerType resolveHorseSaddleLayerType(AbstractHorseEntity horse) {
      EntityType<?> type = horse == null ? null : horse.getType();
      if (type == EntityType.DONKEY) {
         return LayerType.DONKEY_SADDLE;
      } else if (type == EntityType.MULE) {
         return LayerType.MULE_SADDLE;
      } else if (type == EntityType.SKELETON_HORSE) {
         return LayerType.SKELETON_HORSE_SADDLE;
      } else {
         return type == EntityType.ZOMBIE_HORSE ? LayerType.ZOMBIE_HORSE_SADDLE : LayerType.HORSE_SADDLE;
      }
   }

   private static Identifier mapHorseMarkingTexture(HorseMarking marking) {
      if (marking == null || marking == HorseMarking.NONE) {
         return null;
      } else if (marking == HorseMarking.WHITE) {
         return Identifier.ofVanilla("textures/entity/horse/horse_markings_white.png");
      } else if (marking == HorseMarking.WHITE_FIELD) {
         return Identifier.ofVanilla("textures/entity/horse/horse_markings_whitefield.png");
      } else if (marking == HorseMarking.WHITE_DOTS) {
         return Identifier.ofVanilla("textures/entity/horse/horse_markings_whitedots.png");
      } else {
         return marking == HorseMarking.BLACK_DOTS ? Identifier.ofVanilla("textures/entity/horse/horse_markings_blackdots.png") : null;
      }
   }

   private static List<Identifier> resolveHorseArmorTextures(ItemStack armor) {
      return resolveEquipmentLayerTextures(armor, LayerType.HORSE_BODY, "horse_armor");
   }

   private static List<Identifier> resolveEquipmentLayerTextures(ItemStack stack, LayerType layerType, String tintCachePrefix) {
      if (stack != null && !stack.isEmpty() && layerType != null) {
         EquippableComponent equippable = (EquippableComponent)stack.get(DataComponentTypes.EQUIPPABLE);
         if (equippable == null) {
            return List.of();
         }

         Optional<RegistryKey<EquipmentAsset>> assetId = equippable.assetId();
         return assetId.isEmpty() ? List.of() : resolveEquipmentLayerTextures(assetId.get(), stack, layerType, tintCachePrefix);
      } else {
         return List.of();
      }
   }

   private static List<Identifier> resolveEquipmentLayerTextures(
      RegistryKey<EquipmentAsset> assetId, ItemStack stack, LayerType layerType, String tintCachePrefix
   ) {
      if (assetId != null && layerType != null) {
         EquipmentModelLoader loader = resolveEquipmentModelLoader();
         if (loader == null) {
            return List.of();
         }

         EquipmentModel model = loader.get(assetId);
         if (model == null) {
            return List.of();
         }

         List<Layer> layers = model.getLayers(layerType);
         if (layers != null && !layers.isEmpty()) {
            DyedColorComponent dyed = (DyedColorComponent)stack.get(DataComponentTypes.DYED_COLOR);
            List<Identifier> textures = new ArrayList<>(layers.size());

            for (Layer layer : layers) {
               if (layer != null) {
                  Identifier texture = layer.getFullTextureId(layerType);
                  if (texture != null) {
                     if (layer.dyeable().isPresent()) {
                        Optional<Integer> fallbackColor = ((Dyeable)layer.dyeable().get()).colorWhenUndyed();
                        Integer tint = dyed != null ? dyed.rgb() : fallbackColor.orElse(null);
                        if (tint != null) {
                           texture = getTintedEquipmentTexture(texture, tint, tintCachePrefix);
                        }
                     }

                     textures.add(texture);
                  }
               }
            }

            return textures.isEmpty() ? List.of() : List.copyOf(textures);
         } else {
            return List.of();
         }
      } else {
         return List.of();
      }
   }

   @Nullable
   private static EquipmentModelLoader resolveEquipmentModelLoader() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return null;
      } else {
         return client.getEntityRenderDispatcher() instanceof EntityRenderManagerAccessor accessor ? accessor.afw$getEquipmentModelLoader() : null;
      }
   }

   private static List<Identifier> resolveWolfOverlays(LivingEntity entity) {
      if (entity instanceof WolfEntity wolf) {
         ArrayList overlays = new ArrayList();
         ItemStack armor = wolf.getEquippedStack(EquipmentSlot.BODY);
         if (!armor.isEmpty()) {
            overlays.addAll(resolveEquipmentLayerTextures(armor, LayerType.WOLF_BODY, "wolf_armor"));
         }

         if (wolf.isTamed()) {
            DyeColor collarColor = wolf.getCollarColor();
            if (collarColor != null) {
               overlays.add(getTintedTexture(WOLF_COLLAR_TEXTURE, collarColor.getEntityColor(), "wolf_collar", WOLF_COLLAR_TINT_CACHE));
            }
         }

         return overlays.isEmpty() ? List.of() : List.copyOf(overlays);
      } else {
         return List.of();
      }
   }

   private static Identifier getTintedTexture(Identifier baseTexture, int rgb, String cachePrefix, Map<Integer, Identifier> cache) {
      Identifier cached = cache.get(rgb);
      if (cached != null) {
         return cached;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return baseTexture;
      }

      Optional<Resource> resourceOpt = client.getResourceManager().getResource(baseTexture);
      if (resourceOpt.isEmpty()) {
         return baseTexture;
      }

      Resource resource = resourceOpt.get();

      try (InputStream in = resource.getInputStream()) {
         NativeImage image = NativeImage.read(in);
         tintImage(image, rgb);
         String idPath = "textures/dynamic/" + cachePrefix + "_" + String.format("%06x", rgb) + ".png";
         Identifier id = Identifier.of("needsofnature", idPath);
         client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(id::toString, image));
         cache.put(rgb, id);
         return id;
      } catch (IOException e) {
         NeedsOfNature.LOGGER.debug("[NoN] Failed to tint texture {} (rgb={})", new Object[]{baseTexture, rgb, e});
         return baseTexture;
      }
   }

   private static Identifier getTintedEquipmentTexture(Identifier baseTexture, int rgb, String cachePrefix) {
      if (baseTexture == null) {
         return null;
      }

      int tint = rgb & 16777215;
      String cacheKey = baseTexture + "|" + tint;
      Identifier cached = EQUIPMENT_TINT_CACHE.get(cacheKey);
      if (cached != null) {
         return cached;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return baseTexture;
      }

      Optional<Resource> resourceOpt = client.getResourceManager().getResource(baseTexture);
      if (resourceOpt.isEmpty()) {
         return baseTexture;
      }

      Resource resource = resourceOpt.get();

      try (InputStream in = resource.getInputStream()) {
         NativeImage image = NativeImage.read(in);
         tintImage(image, tint);
         String textureKey = (baseTexture.getNamespace() + "_" + baseTexture.getPath()).replace('/', '_').replace('.', '_');
         String idPath = "textures/dynamic/" + cachePrefix + "_" + textureKey + "_" + String.format("%06x", tint) + ".png";
         Identifier id = Identifier.of("needsofnature", idPath);
         client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(id::toString, image));
         EQUIPMENT_TINT_CACHE.put(cacheKey, id);
         return id;
      } catch (IOException | RuntimeException e) {
         NeedsOfNature.LOGGER.debug("[NoN] Failed to tint equipment texture {} (rgb={})", new Object[]{baseTexture, tint, e});
         return baseTexture;
      }
   }

   private static void tintImage(NativeImage image, int rgb) {
      int tintR = rgb >> 16 & 0xFF;
      int tintG = rgb >> 8 & 0xFF;
      int tintB = rgb & 0xFF;
      int width = image.getWidth();
      int height = image.getHeight();

      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            int argb = image.getColorArgb(x, y);
            int alpha = argb >>> 24 & 0xFF;
            if (alpha != 0) {
               int r = argb >>> 16 & 0xFF;
               int g = argb >>> 8 & 0xFF;
               int b = argb & 0xFF;
               int outR = r * tintR / 255;
               int outG = g * tintG / 255;
               int outB = b * tintB / 255;
               int outArgb = alpha << 24 | outR << 16 | outG << 8 | outB;
               image.setColorArgb(x, y, outArgb);
            }
         }
      }
   }

   private static void handleVoluntaryStopInput(MinecraftClient client) {
      if (client != null) {
         ClientPlayerEntity player = client.player;
         if (player != null) {
            while (client.options.sneakKey.wasPressed()) {
               if (ClientAnimationRuntime.isActorPendingOrActive(player.getUuid())) {
                  UUID instanceId = ClientAnimationRuntime.findLatestActiveInstanceContaining(player.getUuid());
                  if (instanceId != null && ClientPlayNetworking.canSend(StopVoluntaryAnimationC2SPayload.ID)) {
                     ClientPlayNetworking.send(new StopVoluntaryAnimationC2SPayload(instanceId));
                  }
               }
            }
         }
      }
   }

   private static void handleAttackInput(MinecraftClient client) {
      if (client != null && client.world != null) {
         ClientPlayerEntity player = client.player;
         if (player != null) {
            UUID instanceId = ClientAnimationRuntime.findLatestActiveInstanceContaining(player.getUuid());
            if (NonHudOverlay.isAttackInstance(instanceId)) {
               if (NonHudOverlay.consumeAttackInputClear()) {
                  drainPendingPresses(client.options.leftKey);
                  drainPendingPresses(client.options.rightKey);
               } else {
                  int leftPresses = drainPendingPresses(client.options.leftKey);
                  int rightPresses = drainPendingPresses(client.options.rightKey);
                  if (leftPresses <= 0 || rightPresses <= 0) {
                     NonHudOverlay.AttackKey attackKey = NonHudOverlay.AttackKey.NONE;
                     if (leftPresses > 0) {
                        attackKey = NonHudOverlay.AttackKey.LEFT;
                     } else if (rightPresses > 0) {
                        attackKey = NonHudOverlay.AttackKey.RIGHT;
                     }

                     if (attackKey != NonHudOverlay.AttackKey.NONE) {
                        NonHudOverlay.recordAttackInput(instanceId, attackKey);
                        if (NonHudOverlay.isServerControlledAttack() && ClientPlayNetworking.canSend(PlayerAttackInputC2SPayload.ID)) {
                           ClientPlayNetworking.send(new PlayerAttackInputC2SPayload(instanceId, attackKey.ordinal()));
                        }
                     }

                     if (NonHudOverlay.isAttackProgressComplete() && ClientPlayNetworking.canSend(StopAttackAnimationC2SPayload.ID)) {
                        ClientPlayNetworking.send(new StopAttackAnimationC2SPayload(instanceId));
                        NonHudOverlay.resetAttackProgress();
                     }
                  }
               }
            }
         }
      }
   }

   private static void handleManualPeakInput(MinecraftClient client) {
      if (client != null && client.world != null) {
         ClientPlayerEntity player = client.player;
         if (player != null && !player.isSpectator()) {
            while (START_MANUAL_PEAK_KEY.wasPressed()) {
               UUID activeInstance = ClientAnimationRuntime.findLatestActiveInstanceContaining(player.getUuid());
               if (activeInstance == null && ClientPlayNetworking.canSend(StartManualPeakC2SPayload.ID)) {
                  ClientPlayNetworking.send(new StartManualPeakC2SPayload());
               }
            }
         }
      }
   }

   public static void pushConfiguredPlayerGenderToServer() {
      pushConfiguredPlayerGenderToServer(NeedsOfNature.getConfig());
   }

   public static void pushConfiguredPlayerGenderToServer(@Nullable NonConfig config) {
      if (config != null) {
         int mask = config.getPlayerGenderMask() & 3;
         if (mask == 0) {
            mask = NonConfig.PlayerGenderSelection.FEMALE.mask();
         }

         if (ClientPlayNetworking.canSend(SetPlayerGenderC2SPayload.ID)) {
            ClientPlayNetworking.send(new SetPlayerGenderC2SPayload(mask, false));
         }

         NonWildfireGenderSync.syncFromNonConfig(config);
         NonWildfireGenderSync.syncDestroyedSkinOverrides(config);
      }
   }

   private static void syncLocalGenderMirror(MinecraftClient client) {
      if (client != null && client.player != null) {
         if (client.player instanceof GenderHolder holder) {
            int mask = holder.getGenderMask() & 3;
            if (mask != 0 && mask != LAST_LOCAL_GENDER_SYNC_MASK) {
               LAST_LOCAL_GENDER_SYNC_MASK = mask;
               NonConfig config = NeedsOfNature.getConfig();
               NonWildfireGenderSync.syncFromMask(config, mask);
               NonWildfireGenderSync.syncDestroyedSkinOverrides(config);
            }
         }
      }
   }

   private static void registerSoundCueResolver() {
      NonAnimationSoundEvents.RESOLVE.register((NonAnimationSoundEvents.SoundResolver)context -> {
         if (NonDebugSpinMode.isEnabled()) {
            return NonAnimationSoundEvents.SoundOverride.skip();
         }

         if (isFillBottlePropCue(context)) {
            UUID instanceId = context.instanceId();
            if (instanceId != null && context.anchor() != null) {
               trySetAnimationRightHandPropOverride(instanceId, context.anchor().getUuid(), createFilledBottlePropStack(context.anchor()));
            }

            return NonAnimationSoundEvents.SoundOverride.play(VANILLA_BOTTLE_FILL_SOUND, context.volume(), context.pitch());
         } else {
            if (AnimationCueEffects.isReactiveImpact(context.effect())) {
               boolean localPlayerCue = isLocalPlayerActorInInstance(context.instanceId());
               if (localPlayerCue && isPeakReactiveImpactCue(context)) {
                  triggerReactiveImpactFovPulse();
               }

               if (AnimationCueEffects.isSilentReactiveImpact(context.effect())) {
                  return NonAnimationSoundEvents.SoundOverride.skip();
               }
            }

            Identifier soundId = NonSoundCues.resolveSoundId(context.effect(), context.anchor(), context.animationId(), context.stageIndex());
            if (soundId == null) {
               return null;
            }

            float volume = context.volume();
            if (NonSoundCues.isActionSoundId(soundId)) {
               volume *= NeedsOfNature.getConfig().getActionSoundVolumeMultiplier();
            }

            return NonAnimationSoundEvents.SoundOverride.play(soundId, volume, context.pitch());
         }
      });
   }

   private static void registerIntifaceCueAnticipation() {
      AnimationSoundAnticipation.setListener(new AnimationSoundAnticipation.Listener() {
         @Override
         public int lookaheadMillis() {
            NonConfig config = NeedsOfNature.getConfig();
            return config != null && config.isIntifaceEnabled() ? config.getIntifaceLatencyCompensationMs() : 0;
         }

         @Override
         public void anticipate(NonAnimationSoundEvents.SoundContext context) {
            if (context != null && AnimationCueEffects.isReactiveImpact(context.effect())) {
               if (NeedsOfNatureClient.isLocalPlayerActorInInstance(context.instanceId())) {
                  if (NeedsOfNatureClient.isPeakReactiveImpactCue(context)) {
                     NonIntifaceBridge.pulsePeakReactiveImpact(NeedsOfNature.getConfig(), NeedsOfNatureClient.nextSameEffectCueDelayMs(context));
                  } else {
                     NonIntifaceBridge.pulseReactiveImpact(NeedsOfNature.getConfig(), NeedsOfNatureClient.nextSameEffectCueDelayMs(context));
                  }
               }
            }
         }

         @Override
         public boolean allowNextStageLookahead(Identifier currentStageId, int stageIndex) {
            return !NeedsOfNatureClient.isBirthLoopStage(currentStageId, stageIndex);
         }
      });
   }

   public static void clearGeneratedTintTextures() {
      Set<Identifier> textureIds = new HashSet<>();
      textureIds.addAll(WOLF_COLLAR_TINT_CACHE.values());
      textureIds.addAll(SHEEP_WOOL_TINT_CACHE.values());
      textureIds.addAll(SHEEP_WOOL_UNDERCOAT_TINT_CACHE.values());
      textureIds.addAll(EQUIPMENT_TINT_CACHE.values());
      WOLF_COLLAR_TINT_CACHE.clear();
      SHEEP_WOOL_TINT_CACHE.clear();
      SHEEP_WOOL_UNDERCOAT_TINT_CACHE.clear();
      EQUIPMENT_TINT_CACHE.clear();
      INVALID_PARTICLE_COLOR_SOURCES_WARNED.clear();
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null) {
         for (Identifier textureId : textureIds) {
            client.getTextureManager().destroyTexture(textureId);
         }
      }
   }

   private static void registerParticleCueResolver() {
      NonAnimationParticleEvents.RESOLVE.register((NonAnimationParticleEvents.ParticleResolver)context -> {
         if (context != null && NonParticles.isLiquidParticleId(context.particleId())) {
            String colorSource = parseParticleColorSource(context.script());
            int tintRgb;
            if (colorSource == null || "tank".equals(colorSource)) {
               tintRgb = resolveParticleTankTint(context);
            } else if ("actor".equals(colorSource)) {
               Identifier actorTypeId = context.actor() == null ? null : Registries.ENTITY_TYPE.getId(context.actor().getType());
               tintRgb = NonLiquidSystem.resolveBottleTintRgb(actorTypeId);
            } else {
               warnInvalidParticleColorSource(context, colorSource);
               tintRgb = resolveParticleTankTint(context);
            }

            ParticleEffect particle = NonParticles.createLiquidEffect(context.particleId(), tintRgb);
            return particle == null ? null : NonAnimationParticleEvents.ParticleOverride.spawn(particle);
         } else {
            return null;
         }
      });
   }

   private static int resolveParticleTankTint(NonAnimationParticleEvents.ParticleContext context) {
      if (context.actor() instanceof PlayerEntity player) {
         Integer syncedTint = NonDestroyedSkinClient.getLiquidTintRgb(player.getUuid());
         if (syncedTint != null) {
            return syncedTint;
         }
      }

      MinecraftClient client = MinecraftClient.getInstance();
      return client == null || client.player == null || context.actor() != null && !context.actor().getUuid().equals(client.player.getUuid())
         ? NonLiquidSystem.resolveBottleTintRgb(null)
         : NonHudOverlay.getLiquidTintRgb();
   }

   @Nullable
   private static String parseParticleColorSource(@Nullable String script) {
      if (script != null && !script.isBlank()) {
         for (String entry : script.split("[;,\\r\\n]+")) {
            String[] pair = entry.trim().split("=", 2);
            if (pair.length == 2 && "color_source".equalsIgnoreCase(pair[0].trim())) {
               return pair[1].trim().toLowerCase(Locale.ROOT);
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static void warnInvalidParticleColorSource(NonAnimationParticleEvents.ParticleContext context, String colorSource) {
      String animation = context.animationId() == null ? "unknown" : context.animationId().toString();
      String warningKey = animation + "|" + colorSource;
      if (INVALID_PARTICLE_COLOR_SOURCES_WARNED.add(warningKey)) {
         NeedsOfNature.logSetupWarning("[NoN] Unknown particle color_source '{}' in animation {}; expected 'tank' or 'actor'.", colorSource, animation);
      }
   }

   private static boolean isPeakReactiveImpactCue(NonAnimationSoundEvents.SoundContext context) {
      if (context == null) {
         return false;
      } else {
         String effect = context.effect();
         if (!AnimationCueEffects.isReactiveImpact(effect)) {
            return false;
         } else if (NonSoundCues.isPeakReactiveImpactCue(effect, context.animationId(), context.stageIndex())) {
            return true;
         } else {
            UUID instanceId = context.instanceId();
            if (instanceId == null) {
               return false;
            } else {
               AnimationStageInfo stage = ClientAnimationRuntime.findStage(instanceId, context.stageIndex());
               if (stage != null && stage.animationId() != null) {
                  Identifier logicalStageId = stage.animationId();
                  Integer stageNumber = NonPeakStages.stageNumberFromId(logicalStageId);
                  Integer peakStage = NonPeakStages.getPeakStage(NonPeakStages.baseAnimationId(logicalStageId));
                  return stageNumber != null && peakStage != null && stageNumber.equals(peakStage);
               } else {
                  return false;
               }
            }
         }
      }
   }

   private static double nextSameEffectCueDelayMs(NonAnimationSoundEvents.SoundContext context) {
      return context == null ? 0.0 : Math.max(0.0, context.nextSameEffectCueDelayMs());
   }

   private static boolean isLocalPlayerActorInInstance(@Nullable UUID instanceId) {
      if (instanceId == null) {
         return false;
      } else {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null) {
            UUID playerInstance = ClientAnimationRuntime.findLatestActiveInstanceContaining(client.player.getUuid());
            return instanceId.equals(playerInstance);
         } else {
            return false;
         }
      }
   }

   private static void tickIntifaceBaseline(MinecraftClient client) {
      boolean active = false;
      boolean peak = false;
      double specialVibratorScalar = Double.NaN;
      NonConfig config = NeedsOfNature.getConfig();
      int latencyCompensationMs = config == null ? 0 : config.getIntifaceLatencyCompensationMs();
      if (client != null && client.player != null) {
         NeedsOfNatureClient.IntifaceAnimationState state = findLocalIntifaceAnimationState(client, latencyCompensationMs);
         active = state != null;
         if (state != null) {
            peak = NonSoundCues.isPeakAnimationStage(state.stageAnimationId(), state.stageIndex());
            specialVibratorScalar = resolveSpecialIntifaceVibratorScalar(state);
         }
      }

      int energizedLevel = client != null && client.player != null ? NonHudOverlay.getEnergizedLevel(client.player) : 0;
      double energizedPulse = energizedLevel > 0 ? NonHudOverlay.computeHeartPulse(energizedLevel, latencyCompensationMs) : 0.0;
      NonIntifaceBridge.clientTick(config, active, peak, specialVibratorScalar, energizedLevel, energizedPulse);
   }

   @Nullable
   private static NeedsOfNatureClient.IntifaceAnimationState findLocalIntifaceAnimationState(MinecraftClient client, int latencyCompensationMs) {
      if (client != null && client.player != null && client.world != null) {
         UUID playerUuid = client.player.getUuid();
         UUID instanceId = ClientAnimationRuntime.findLatestActiveInstanceContaining(playerUuid);
         if (instanceId == null) {
            return null;
         }

         ClientAnimationRuntime.StageTimelineSnapshot timeline = ClientAnimationRuntime.findStageTimeline(instanceId);
         if (timeline != null && !timeline.stages().isEmpty()) {
            int timelineStageIndex = Math.max(0, Math.min(timeline.stageIndex(), timeline.stages().size() - 1));
            AnimationStageInfo stage = timeline.stages().get(timelineStageIndex);
            double projectedWorldTick = client.world.getTime() + Math.max(0, latencyCompensationMs) / 50.0;
            double stageStartTick = timeline.stageStartTick();
            double stageEndTick = timeline.stageEndTick();
            double speed = Math.max(1.0E-4, timeline.speed());
            if (stageEndTick > stageStartTick
               && projectedWorldTick >= stageEndTick
               && !isBirthLoopStage(stage.animationId(), timelineStageIndex)
               && timelineStageIndex + 1 < timeline.stages().size()) {
               stage = timeline.stages().get(++timelineStageIndex);
               stageStartTick = stageEndTick;
               stageEndTick = -1.0;
               speed = Math.max(1.0E-4, stage.speed());
            }

            Identifier stageAnimationId = stage.effectiveAnimationId();
            Identifier logicalStageAnimationId = stage.animationId();
            Identifier animationId = NonPeakStages.baseAnimationId(logicalStageAnimationId);
            Integer stageNumber = NonPeakStages.stageNumberFromId(logicalStageAnimationId);
            int logicalStageIndex = stageNumber == null ? timelineStageIndex : Math.max(0, stageNumber - 1);
            double elapsedWorldTicks = Math.max(0.0, projectedWorldTick - stageStartTick);
            double elapsedTicks = elapsedWorldTicks * speed;
            double cycleTicks = AnimationGameplayMetadata.cycleTicks(logicalStageAnimationId);
            if (cycleTicks <= 0.0) {
               cycleTicks = stage.cycleTicks() > 0.0 ? stage.cycleTicks() : stage.lengthTicks();
            }

            double durationTicks = resolveIntifaceStageDurationTicks(animationId, logicalStageAnimationId, logicalStageIndex, cycleTicks);
            double normalizedStageProgress = stageEndTick > stageStartTick
               ? elapsedWorldTicks / (stageEndTick - stageStartTick)
               : elapsedWorldTicks / Math.max(1.0, estimatedStageWorldDurationTicks(stage, durationTicks, speed));
            return new NeedsOfNatureClient.IntifaceAnimationState(
               animationId, stageAnimationId, logicalStageIndex, elapsedTicks, normalizedStageProgress, cycleTicks
            );
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static double estimatedStageWorldDurationTicks(AnimationStageInfo stage, double configuredDurationTicks, double speed) {
      if (stage != null && !stage.loop()) {
         double cycleTicks = stage.cycleTicks() > 0.0 ? stage.cycleTicks() : stage.lengthTicks();
         if (cycleTicks > 0.0) {
            return cycleTicks / Math.max(1.0E-4, speed);
         }
      }

      return Math.max(1.0, configuredDurationTicks);
   }

   private static double resolveIntifaceStageDurationTicks(Identifier animationId, Identifier stageAnimationId, int stageIndex, double cycleLengthTicks) {
      double multiplier = AnimationGameplayMetadata.stageDurationMultiplier(stageAnimationId);
      Integer override = AnimationGameplayMetadata.stageSeconds(stageAnimationId);
      if (AnimationGameplayMetadata.isInfiniteStageSeconds(override)) {
         override = null;
      }

      if (override != null) {
         return Math.max(1.0, Math.max(1, override) * 20.0 * multiplier);
      }

      Identifier baseId = NonPeakStages.baseAnimationId(stageAnimationId);
      Integer baseOverride = AnimationGameplayMetadata.stageSeconds(baseId);
      if (AnimationGameplayMetadata.isInfiniteStageSeconds(baseOverride)) {
         baseOverride = null;
      }

      if (baseOverride != null) {
         return Math.max(1.0, Math.max(1, baseOverride) * 20.0 * multiplier);
      }

      Integer peakStage = NonPeakStages.getPeakStage(animationId);
      int logicalStage = stageIndex + 1;
      int seconds = Objects.equals(logicalStage, peakStage)
         ? NeedsOfNature.getConfig().getPeakLoopProgressSeconds()
         : NeedsOfNature.getConfig().getLoopProgressSeconds();
      return seconds > 0 ? Math.max(1.0, seconds * 20.0 * multiplier) : Math.max(1.0, cycleLengthTicks);
   }

   private static double resolveSpecialIntifaceVibratorScalar(NeedsOfNatureClient.IntifaceAnimationState state) {
      if (state == null || state.animationId() == null) {
         return Double.NaN;
      } else if (isManualPeakAnimation(state.animationId())) {
         return resolveManualPeakVibratorScalar(state);
      } else {
         return isBirthAnimation(state.animationId(), state.stageAnimationId()) ? resolveBirthVibratorScalar(state) : Double.NaN;
      }
   }

   private static double resolveManualPeakVibratorScalar(NeedsOfNatureClient.IntifaceAnimationState state) {
      Integer peakStage = NonPeakStages.getPeakStage(state.animationId());
      if (peakStage == null) {
         return Double.NaN;
      } else {
         int logicalStage = state.stageIndex() + 1;
         double progress = state.stageProgress();
         if (logicalStage < peakStage) {
            return resolveManualPeakBuildupScalar(state, peakStage, logicalStage, progress);
         } else {
            return logicalStage == peakStage ? Math.max(0.0, Math.min(1.0, 1.0 - progress)) : Double.NaN;
         }
      }
   }

   private static double resolveManualPeakBuildupScalar(
      NeedsOfNatureClient.IntifaceAnimationState state, int peakStage, int logicalStage, double currentStageProgress
   ) {
      double completedDuration = 0.0;
      double totalDuration = 0.0;

      for (int stageNumber = 1; stageNumber < peakStage; stageNumber++) {
         Identifier stageId = AnimationIds.stageId(state.animationId(), stageNumber);
         double cycleTicks = AnimationGameplayMetadata.cycleTicks(stageId);
         if (cycleTicks <= 0.0 && stageNumber == logicalStage) {
            cycleTicks = state.cycleDurationTicks();
         }

         double duration = resolveIntifaceStageDurationTicks(state.animationId(), stageId, stageNumber - 1, cycleTicks);
         totalDuration += duration;
         if (stageNumber < logicalStage) {
            completedDuration += duration;
         } else if (stageNumber == logicalStage) {
            completedDuration += duration * currentStageProgress;
         }
      }

      return totalDuration <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, completedDuration / totalDuration));
   }

   private static double resolveBirthVibratorScalar(NeedsOfNatureClient.IntifaceAnimationState state) {
      int logicalStage = state.stageIndex() + 1;
      double progress = state.cycleProgress();

      return switch (logicalStage) {
         case 1 -> progress;
         case 2 -> 0.6 + 0.4 * progress;
         case 3 -> 1.0 - progress;
         default -> Double.NaN;
      };
   }

   private static boolean isManualPeakAnimation(Identifier animationId) {
      return animationId != null && animationId.getPath().contains("manual_peak");
   }

   private static boolean isBirthAnimation(@Nullable Identifier animationId, @Nullable Identifier stageAnimationId) {
      return hasBirthAnimationPath(animationId) || hasBirthAnimationPath(stageAnimationId);
   }

   private static boolean isBirthLoopStage(@Nullable Identifier stageAnimationId, int stageIndex) {
      Integer stageNumber = NonPeakStages.stageNumberFromId(stageAnimationId);
      int logicalStage = stageNumber == null ? stageIndex + 1 : stageNumber;
      return logicalStage == 2 && hasBirthAnimationPath(stageAnimationId);
   }

   private static boolean hasBirthAnimationPath(@Nullable Identifier animationId) {
      return animationId == null ? false : animationId.getPath().contains("birth");
   }

   private static boolean isFillBottlePropCue(NonAnimationSoundEvents.SoundContext context) {
      if (context == null) {
         return false;
      }

      if (!isFillBottleAnimation(context.animationId())) {
         return false;
      }

      String effect = context.effect();
      return "fillbottle_prop_fill".equalsIgnoreCase(effect);
   }

   private static boolean trySetAnimationRightHandPropOverride(UUID instanceId, UUID actorUuid, ItemStack stack) {
      if (instanceId != null && actorUuid != null && stack != null && !stack.isEmpty()) {
         ClientAnimationRuntime.setRightHandPropOverride(instanceId, actorUuid, stack);
         return true;
      } else {
         return false;
      }
   }

   private static int drainPendingPresses(KeyBinding key) {
      int count = 0;

      while (key.wasPressed()) {
         count++;
      }

      return count;
   }

   private static void triggerReactiveImpactFovPulse() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null) {
         REACTIVE_IMPACT_PULSE_START_NANOS = System.nanoTime();
      }
   }

   public static float getReactiveImpactFovOffset(float tickDelta) {
      long pulseStartNanos = REACTIVE_IMPACT_PULSE_START_NANOS;
      if (pulseStartNanos == Long.MIN_VALUE) {
         return 0.0F;
      } else {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client == null) {
            return 0.0F;
         } else {
            long nowNanos = System.nanoTime();
            if (nowNanos < pulseStartNanos) {
               REACTIVE_IMPACT_PULSE_START_NANOS = nowNanos;
               return -0.5F;
            } else {
               float elapsed = (float)((nowNanos - pulseStartNanos) / 5.0E7);
               if (elapsed <= 3.0F) {
                  return -0.5F;
               } else {
                  float recovery = elapsed - 3.0F;
                  if (recovery >= 14.0F) {
                     REACTIVE_IMPACT_PULSE_START_NANOS = Long.MIN_VALUE;
                     return 0.0F;
                  } else {
                     float t = MathHelper.clamp(recovery / 14.0F, 0.0F, 1.0F);
                     float remaining = 1.0F - t;
                     float eased = remaining * remaining;
                     return -0.5F * eased;
                  }
               }
            }
         }
      }
   }

   private static long getCurrentClientTicks(MinecraftClient client) {
      return client != null && client.world != null ? client.world.getTime() : CLIENT_FALLBACK_TICKS;
   }

   private record IntifaceAnimationState(
      Identifier animationId, Identifier stageAnimationId, int stageIndex, double stageElapsedTicks, double normalizedStageProgress, double cycleDurationTicks
   ) {
      double stageProgress() {
         return Math.max(0.0, Math.min(1.0, this.normalizedStageProgress));
      }

      double cycleProgress() {
         return this.cycleDurationTicks <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, this.stageElapsedTicks / this.cycleDurationTicks));
      }
   }
}
