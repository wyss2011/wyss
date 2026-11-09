package com.nonid;

import com.google.gson.Gson;
import com.nonid.api.NonEvents;
import com.nonid.api.animation.NonActorEvents;
import com.nonid.api.animation.NonAnimationDamageBehavior;
import com.nonid.client.NonLiquidColors;
import com.nonid.data.NonAccessoryDefinitions;
import com.nonid.data.NonAccessoryItemRegistry;
import com.nonid.data.NonEntityProfiles;
import com.nonid.data.NonLiquidGainOverrides;
import com.nonid.data.NonLiquidRoles;
import com.nonid.data.NonManualPeakDefinitions;
import com.nonid.data.NonPeakStages;
import com.nonid.effect.NonStatusEffects;
import com.nonid.entity.HorseLiquidCollectorEntity;
import com.nonid.entity.PregnancyEggEntity;
import com.nonid.integration.NonWildfireGenderConfigCompat;
import com.nonid.internal.animation.AnimationEngine;
import com.nonid.internal.animation.api.AnimationLifecycleEvents;
import com.nonid.internal.animation.api.AnimationService;
import com.nonid.internal.animation.data.AnimationCueIndex;
import com.nonid.internal.animation.data.AnimationDefinitions;
import com.nonid.internal.animation.data.AnimationGameplayMetadata;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.server.AnimationStartContext;
import com.nonid.internal.animation.server.ServerAnimationController;
import com.nonid.internal.animation.util.AnimationIds;
import com.nonid.internal.animation.util.StageTimeWarp;
import com.nonid.item.EnergyGainAdjustItem;
import com.nonid.item.EnergyStabilizerItem;
import com.nonid.item.HorseLiquidCollectorItem;
import com.nonid.network.AccessoryShedStateS2CPayload;
import com.nonid.network.AnimationCatalogChunkS2CPayload;
import com.nonid.network.AnimationCatalogData;
import com.nonid.network.AttackStateS2CPayload;
import com.nonid.network.DestroyedSkinMaskSyncS2CPayload;
import com.nonid.network.DestroyedSkinMaskUploadC2SPayload;
import com.nonid.network.DestroyedSkinParticlesS2CPayload;
import com.nonid.network.DestroyedSkinStateS2CPayload;
import com.nonid.network.DestroyedSkinSyncS2CPayload;
import com.nonid.network.DestroyedSkinUploadC2SPayload;
import com.nonid.network.GenderSelectionPromptS2CPayload;
import com.nonid.network.HostConfigSyncData;
import com.nonid.network.HostConfigSyncS2CPayload;
import com.nonid.network.LiquidStateS2CPayload;
import com.nonid.network.ManualDamageDestroyedSkinC2SPayload;
import com.nonid.network.MessStateS2CPayload;
import com.nonid.network.PlayerAnimationCancelC2SPayload;
import com.nonid.network.PlayerAnimationChoiceC2SPayload;
import com.nonid.network.PlayerAnimationConsentC2SPayload;
import com.nonid.network.PlayerAnimationConsentS2CPayload;
import com.nonid.network.PlayerAnimationRequestStatusS2CPayload;
import com.nonid.network.PlayerAnimationSelectionS2CPayload;
import com.nonid.network.PlayerAttackInputC2SPayload;
import com.nonid.network.PlayerProfileActionC2SPayload;
import com.nonid.network.PlayerProfileSnapshotS2CPayload;
import com.nonid.network.PlayerRuntimeSettingsS2CPayload;
import com.nonid.network.PregnancyCycleStateS2CPayload;
import com.nonid.network.PregnancyStateS2CPayload;
import com.nonid.network.RepairDestroyedSkinC2SPayload;
import com.nonid.network.ServerConfigRequestC2SPayload;
import com.nonid.network.ServerConfigResultS2CPayload;
import com.nonid.network.ServerConfigUpdateC2SPayload;
import com.nonid.network.SetPlayerGenderC2SPayload;
import com.nonid.network.SkinRippedInfoRequestS2CPayload;
import com.nonid.network.StartManualPeakC2SPayload;
import com.nonid.network.StopAttackAnimationC2SPayload;
import com.nonid.network.StopVoluntaryAnimationC2SPayload;
import com.nonid.particle.NonParticles;
import com.nonid.potion.NonBrewingRecipes;
import com.nonid.potion.NonPotions;
import com.nonid.recipe.NonRecipeSerializers;
import com.nonid.sound.NonSoundEvents;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDamage;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDeath;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDeath;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AfterRespawn;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.CopyFrom;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.EndDataPackReload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndWorldTick;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.ModifyEntriesAll;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.Blocks;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.EntityType.Builder;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.HorseColor;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.HorseMarking;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.FoxEntity.Variant;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.Item.Settings;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.LazyRegistryEntryReference;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeedsOfNature implements ModInitializer {
   public static final String MOD_ID = "needsofnature";
   public static final int MAX_ENERGY = 200;
   private static final int PLAYER_ANIMATION_START_ENERGY_GAIN = 20;
   public static final float MIN_ENERGY_GAIN_MULT = 0.4F;
   public static final float MAX_ENERGY_GAIN_MULT = 6.0F;
   public static final String ENERGY_NBT_KEY = "NeedsOfNatureEnergy";
   public static final String ENERGY_GAIN_MULT_NBT_KEY = "NeedsOfNatureEnergyGainMult";
   public static final String ENERGY_GAIN_DRIFT_NBT_KEY = "NeedsOfNatureEnergyGainDrift";
   public static final String GENDER_NBT_KEY = "NeedsOfNatureGender";
   public static final String LIQUID_NBT_KEY = "NeedsOfNatureLiquid";
   public static final String LIQUID_TYPE_NBT_KEY = "NeedsOfNatureLiquidType";
   public static final String LIQUID_DONOR_VARIANT_NBT_KEY = "NeedsOfNatureLiquidDonorVariantData";
   public static final String LIQUID_DONOR_HISTORY_NBT_KEY = "NeedsOfNatureLiquidDonorHistory";
   public static final String LIQUID_TYPE_MIXED = "mixed";
   public static final String LIQUID_COLOR_MIXED_KEY = "needsofnature:mixed";
   public static final String PREGNANCY_TYPE_NBT_KEY = "NeedsOfNaturePregnancyType";
   public static final String PREGNANCY_END_TICK_NBT_KEY = "NeedsOfNaturePregnancyEndTick";
   public static final String PREGNANCY_PENDING_NBT_KEY = "NeedsOfNaturePregnancyPending";
   public static final String PREGNANCY_VARIANT_NBT_KEY = "NeedsOfNaturePregnancyVariantData";
   public static final String PREGNANCY_OFFSPRING_COUNT_NBT_KEY = "NeedsOfNaturePregnancyOffspringCount";
   public static final String PREGNANCY_CYCLE_PROGRESS_NBT_KEY = "NeedsOfNaturePregnancyCycleProgress";
   public static final String PREGNANCY_CYCLE_LAST_GAME_TICK_NBT_KEY = "NeedsOfNaturePregnancyCycleLastGameTick";
   public static final String PREGNANCY_CYCLE_LAST_TIME_OF_DAY_NBT_KEY = "NeedsOfNaturePregnancyCycleLastTimeOfDay";
   public static final String DESTROYED_SKIN_STAGE_NBT_KEY = "NeedsOfNatureDestroyedSkinStage";
   public static final String DESTROYED_SKIN_DAMAGE_NBT_KEY = "NeedsOfNatureDestroyedSkinDamage";
   public static final String MESS_V_NBT_KEY = "NeedsOfNatureMessV";
   public static final String MESS_A_NBT_KEY = "NeedsOfNatureMessA";
   public static final String MESS_M_NBT_KEY = "NeedsOfNatureMessM";
   public static final float FILLED_STAGE_ONE_THRESHOLD = 0.3F;
   public static final float FILLED_STAGE_TWO_THRESHOLD = 0.5F;
   public static final float FILLED_STAGE_THREE_THRESHOLD = 0.8F;
   public static final float FILLED_STAGE_ONE_SPEED_MULT = 0.8F;
   public static final float FILLED_STAGE_TWO_SPEED_MULT = 0.7F;
   public static final float FILLED_STAGE_THREE_SPEED_MULT = 0.6F;
   public static final float FILLED_STAGE_ONE_JUMP_MULT = 0.9F;
   public static final float FILLED_STAGE_TWO_JUMP_MULT = 0.75F;
   public static final float FILLED_STAGE_THREE_JUMP_MULT = 0.6F;
   public static final float PREGNANCY_MIN_MOVEMENT_MULT = 0.5F;
   public static final float PREGNANCY_SLOWDOWN_CURVE_POWER = 5.0F;
   public static final Logger LOGGER = LoggerFactory.getLogger("needsofnature");
   private static final Gson HOST_CONFIG_SYNC_GSON = new Gson();
   public static final RegistryKey<Item> DEBUG_STAFF_KEY = RegistryKey.of(Registries.ITEM.getKey(), id("debug_staff"));
   public static final Item DEBUG_STAFF = registerItem(DEBUG_STAFF_KEY, new DebugStaffItem(new Settings().registryKey(DEBUG_STAFF_KEY).maxCount(1)));
   public static final RegistryKey<Item> FLOWER_MIX_KEY = RegistryKey.of(Registries.ITEM.getKey(), id("flower_mix"));
   public static final Item FLOWER_MIX = registerItem(FLOWER_MIX_KEY, new Item(new Settings().registryKey(FLOWER_MIX_KEY)));
   public static final RegistryKey<Item> ENERGY_AUGMENTER_KEY = RegistryKey.of(Registries.ITEM.getKey(), id("energy_augmenter"));
   public static final Item ENERGY_AUGMENTER = registerItem(
      ENERGY_AUGMENTER_KEY, new EnergyGainAdjustItem(new Settings().registryKey(ENERGY_AUGMENTER_KEY), 0.2F)
   );
   public static final RegistryKey<Item> ENERGY_DIMINISHER_KEY = RegistryKey.of(Registries.ITEM.getKey(), id("energy_diminisher"));
   public static final Item ENERGY_DIMINISHER = registerItem(
      ENERGY_DIMINISHER_KEY, new EnergyGainAdjustItem(new Settings().registryKey(ENERGY_DIMINISHER_KEY), -0.2F)
   );
   public static final RegistryKey<Item> ENERGY_STABILIZER_KEY = RegistryKey.of(Registries.ITEM.getKey(), id("energy_stabilizer"));
   public static final Item ENERGY_STABILIZER = registerItem(ENERGY_STABILIZER_KEY, new EnergyStabilizerItem(new Settings().registryKey(ENERGY_STABILIZER_KEY)));
   public static final List<Item> DATA_DRIVEN_ACCESSORIES = registerDataDrivenAccessories();
   public static final RegistryKey<EntityType<?>> HORSE_LIQUID_COLLECTOR_ENTITY_TYPE_KEY = RegistryKey.of(
      Registries.ENTITY_TYPE.getKey(), id("horse_liquid_collector")
   );
   public static final EntityType<HorseLiquidCollectorEntity> HORSE_LIQUID_COLLECTOR_ENTITY_TYPE = (EntityType<HorseLiquidCollectorEntity>)Registry.register(
      Registries.ENTITY_TYPE,
      id("horse_liquid_collector"),
      Builder.create(HorseLiquidCollectorEntity::new, SpawnGroup.MISC).dimensions(0.8F, 1.2F).build(HORSE_LIQUID_COLLECTOR_ENTITY_TYPE_KEY)
   );
   public static final RegistryKey<EntityType<?>> PREGNANCY_EGG_ENTITY_TYPE_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), id("pregnancy_egg"));
   public static final EntityType<PregnancyEggEntity> PREGNANCY_EGG_ENTITY_TYPE = (EntityType<PregnancyEggEntity>)Registry.register(
      Registries.ENTITY_TYPE,
      id("pregnancy_egg"),
      Builder.create(PregnancyEggEntity::new, SpawnGroup.MISC).dimensions(0.3F, 0.3F).build(PREGNANCY_EGG_ENTITY_TYPE_KEY)
   );
   public static final RegistryKey<Item> HORSE_LIQUID_COLLECTOR_KEY = RegistryKey.of(Registries.ITEM.getKey(), id("horse_liquid_collector"));
   public static final Item HORSE_LIQUID_COLLECTOR = registerItem(
      HORSE_LIQUID_COLLECTOR_KEY, new HorseLiquidCollectorItem(new Settings().registryKey(HORSE_LIQUID_COLLECTOR_KEY).maxCount(1))
   );
   private static final Map<RegistryKey<World>, NeedsOfNature.ScanBudget> SCAN_BUDGETS = new ConcurrentHashMap<>();
   private static final Map<UUID, NeedsOfNature.PostDeathStateSync> PENDING_POST_DEATH_STATE_SYNCS = new ConcurrentHashMap<>();
   private static final ItemGroup ITEM_GROUP = (ItemGroup)Registry.register(
      Registries.ITEM_GROUP,
      id("main"),
      FabricItemGroup.builder()
         .displayName(Text.translatable("itemGroup.needsofnature.main"))
         .icon(() -> new ItemStack(HORSE_LIQUID_COLLECTOR))
         .entries((context, entries) -> {
            entries.add(FLOWER_MIX);
            entries.add(ENERGY_AUGMENTER);
            entries.add(ENERGY_DIMINISHER);
            DATA_DRIVEN_ACCESSORIES.forEach(entries::add);
            entries.add(HORSE_LIQUID_COLLECTOR);
            NonItemSystem.addEntityLiquidAndStabilizersToItemGroup(entries, getConfig());
            NonItemSystem.addNonPotionVariantsToItemGroup(entries);
         })
         .build()
   );
   private static NonConfig CONFIG;
   private static volatile HostConfigSyncData REMOTE_HOST_RULES;
   private static volatile NonConfig REMOTE_EFFECTIVE_CONFIG;
   private static final AtomicLong SERVER_CONFIG_REVISION = new AtomicLong();
   private static final AtomicLong ANIMATION_CATALOG_REVISION = new AtomicLong();
   private static final int ANIMATION_CATALOG_CHUNK_CHARS = 24000;
   private static final Map<UUID, NeedsOfNature.PeakState> PEAK_STATES = new ConcurrentHashMap<>();
   private static final int PEAK_XP_CAP = 20;
   private static final Identifier PLAYER_ENTITY_TYPE_ID = Identifier.of("minecraft", "player");
   private static final int PENDING_PREGNANCY_EFFECT_TICKS = 21;
   private static final int MESS_AURA_MAX_TOTAL = 15;
   private static final double MESS_AURA_MAX_MULTIPLIER = 10.0;
   private static final int GATHER_TIMEOUT_COOLDOWN_TICKS = 1200;
   private static final int GATHER_PAIR_TRACK_TICKS = 6000;
   private static final int BREEDING_PENDING_TIMEOUT_TICKS = 600;
   private static final int REGULAR_PRUNE_INTERVAL_TICKS = 20;
   private static final int FERTILITY_INCREASER_I_MIN_CHANCE = 80;
   private static final int FERTILITY_INCREASER_II_MIN_CHANCE = 95;
   private static final double LIQUID_SNEAK_DECAY_MULTIPLIER = 16.0;
   private static final double LIQUID_WATER_DECAY_MULTIPLIER = 2.0;
   private static final double LIQUID_DECAY_MIN_MULT = 0.1;
   private static final double LIQUID_DECAY_MAX_MULT = 1.5;
   private static final double NEAR_ANIMATION_RADIUS = 20.0;
   private static final Map<NeedsOfNature.GatherPairKey, NeedsOfNature.GatherPairState> PENDING_GATHER_PAIRS = new ConcurrentHashMap<>();
   private static final Map<UUID, NeedsOfNature.NoNGatherActorState> NON_PENDING_GATHER_ACTORS = new ConcurrentHashMap<>();
   private static final String ANIMATION_TAG_DEFEAT_PREFIX = "defeat.";
   private static final String ANIMATION_TAG_MANUAL_PEAK = "manualPeak";
   private static final String ANIMATION_TAG_FILL_BOTTLE = "fillBottle";
   private static final String ANIMATION_TAG_BIRTH = "birth";
   private static final String ANIMATION_TAG_BIRTH_PREFIX = "birth_";
   private static final int BIRTH_LOOP_STAGE = 2;
   private static final int BIRTH_FINAL_STAGE = 3;
   private static final double BIRTH_LOOP_MAX_SPEED_MULTIPLIER = 2.0;
   private static final double BIRTH_LOOP_SPEED_TAPER_EXPONENT = 2.5;
   static final String NON_PREGNANCY_FRIENDLY_TAG = "non.pregnancy_friendly";
   public static final String NON_ENERGY_STABILIZED_TAG = "non.energy_stabilized";
   private static final Set<UUID> DEFEATED_QUEUED_INSTANCES = ConcurrentHashMap.newKeySet();
   private static final Map<UUID, Long> LAST_DEFEATED_COOLDOWN_UNTIL_BY_PLAYER = new ConcurrentHashMap<>();
   private static final Map<UUID, Long> POST_ESCAPE_DAMAGE_INVULNERABLE_UNTIL_BY_PLAYER = new ConcurrentHashMap<>();
   private static final int ATTACK_ESCAPE_DAMAGE_DELAY_TICKS = 1;
   private static final Map<UUID, NeedsOfNature.PendingAttackEscapeResolution> PENDING_ATTACK_ESCAPE_RESOLUTIONS = new ConcurrentHashMap<>();
   private static final List<NeedsOfNature.VariantComponentDescriptor> PREGNANCY_VARIANT_COMPONENTS = List.of(
      registryVariant("wolf_variant", DataComponentTypes.WOLF_VARIANT, RegistryKeys.WOLF_VARIANT),
      registryVariant("wolf_sound_variant", DataComponentTypes.WOLF_SOUND_VARIANT, RegistryKeys.WOLF_SOUND_VARIANT),
      registryVariant("pig_variant", DataComponentTypes.PIG_VARIANT, RegistryKeys.PIG_VARIANT),
      registryVariant("cow_variant", DataComponentTypes.COW_VARIANT, RegistryKeys.COW_VARIANT),
      lazyRegistryVariant("chicken_variant", DataComponentTypes.CHICKEN_VARIANT, RegistryKeys.CHICKEN_VARIANT),
      registryVariant("frog_variant", DataComponentTypes.FROG_VARIANT, RegistryKeys.FROG_VARIANT),
      registryVariant("cat_variant", DataComponentTypes.CAT_VARIANT, RegistryKeys.CAT_VARIANT),
      lazyRegistryVariant("zombie_nautilus_variant", DataComponentTypes.ZOMBIE_NAUTILUS_VARIANT, RegistryKeys.ZOMBIE_NAUTILUS_VARIANT),
      enumVariant("fox_variant", DataComponentTypes.FOX_VARIANT, Variant.class),
      enumVariant("salmon_size", DataComponentTypes.SALMON_SIZE, net.minecraft.entity.passive.SalmonEntity.Variant.class),
      enumVariant("parrot_variant", DataComponentTypes.PARROT_VARIANT, net.minecraft.entity.passive.ParrotEntity.Variant.class),
      enumVariant("mooshroom_variant", DataComponentTypes.MOOSHROOM_VARIANT, net.minecraft.entity.passive.MooshroomEntity.Variant.class),
      enumVariant("rabbit_variant", DataComponentTypes.RABBIT_VARIANT, net.minecraft.entity.passive.RabbitEntity.Variant.class),
      enumVariant("horse_variant", DataComponentTypes.HORSE_VARIANT, HorseColor.class),
      enumVariant("llama_variant", DataComponentTypes.LLAMA_VARIANT, net.minecraft.entity.passive.LlamaEntity.Variant.class),
      enumVariant("axolotl_variant", DataComponentTypes.AXOLOTL_VARIANT, net.minecraft.entity.passive.AxolotlEntity.Variant.class),
      enumVariant("sheep_color", DataComponentTypes.SHEEP_COLOR, DyeColor.class),
      enumVariant("tropical_fish_base_color", DataComponentTypes.TROPICAL_FISH_BASE_COLOR, DyeColor.class),
      enumVariant("tropical_fish_pattern_color", DataComponentTypes.TROPICAL_FISH_PATTERN_COLOR, DyeColor.class)
   );
   private static final int INFINITE_PEAK_LIQUID_PULSE_ML = 5;
   private static final Map<UUID, NeedsOfNature.PeakLiquidProgressState> PEAK_LIQUID_PROGRESS = new ConcurrentHashMap<>();
   private static final Map<UUID, Integer> PEAK_XP_ELIGIBLE_ML = new ConcurrentHashMap<>();
   private static final Map<UUID, NeedsOfNature.FillBottleState> FILL_BOTTLE_STATES = new ConcurrentHashMap<>();
   private static final Map<NeedsOfNature.BreedingPairKey, NeedsOfNature.PendingBreedingState> BREEDING_PENDING_BY_PAIR = new ConcurrentHashMap<>();
   private static final Map<UUID, NeedsOfNature.BreedingPairKey> BREEDING_PENDING_BY_REQUEST = new ConcurrentHashMap<>();
   private static final Map<UUID, NeedsOfNature.ActiveBreedingState> BREEDING_ACTIVE_BY_INSTANCE = new ConcurrentHashMap<>();
   private static final Map<NeedsOfNature.BreedingPairKey, UUID> BREEDING_ACTIVE_INSTANCE_BY_PAIR = new ConcurrentHashMap<>();
   private static final Map<UUID, NeedsOfNature.PregnancyHatchState> PREGNANCY_HATCH_BY_INSTANCE = new ConcurrentHashMap<>();
   private static final Map<UUID, UUID> PREGNANCY_ACTIVE_INSTANCE_BY_PLAYER = new ConcurrentHashMap<>();
   private static final Set<UUID> PREGNANCY_QUEUED_PLAYERS = ConcurrentHashMap.newKeySet();
   private static final Map<UUID, Long> PREGNANCY_START_RETRY_TICKS = new ConcurrentHashMap<>();
   private static final Map<UUID, NeedsOfNature.PregnancyCueRuntime> PREGNANCY_CUE_RUNTIME_BY_INSTANCE = new ConcurrentHashMap<>();
   private static final Set<String> REFLECTION_FAILURE_LOGS = ConcurrentHashMap.newKeySet();
   private static final Map<UUID, NeedsOfNature.ReceiverDripState> RECEIVER_DRIP = new ConcurrentHashMap<>();
   private static final Map<UUID, NeedsOfNature.PendingMInjectorReward> PENDING_M_INJECTOR_REWARDS = new ConcurrentHashMap<>();
   private static final int RECEIVER_DRIP_INTERVAL_TICKS = 20;
   private static final int RECEIVER_DRIP_DURATION_TICKS = 200;
   private static final float FILL_BOTTLE_LOOK_DOWN_MIN_PITCH = 80.0F;
   private static final Vec3d RECEIVER_DRIP_OFFSET_DEFAULT = new Vec3d(0.0, 0.0, -0.5);
   private static final Map<Identifier, Vec3d> RECEIVER_DRIP_OFFSETS = Map.of(Identifier.of("minecraft", "wolf"), new Vec3d(0.0, 0.0, -0.5));

   public static float getPregnancyMovementMultiplier(int remainingTicks) {
      if (remainingTicks == -1) {
         return 1.0F;
      }

      if (remainingTicks <= 22) {
         return 0.5F;
      }

      long configuredTotalTicks = Math.max(1L, getConfig().getPregnancyDurationMinutes() * 1200L);
      double remaining01 = MathHelper.clamp((double)remainingTicks / configuredTotalTicks, 0.0, 1.0);
      double closeness = 1.0 - remaining01;
      double curve = Math.pow(closeness, 5.0);
      double multiplier = 1.0 - 0.5 * curve;
      return (float)MathHelper.clamp(multiplier, 0.5, 1.0);
   }

   public static void applyAnimationToggleConfig() {
      NonConfig config = CONFIG;
      if (config == null) {
         AnimationDefinitions.setDisabledAnimationFilters(Set.of(), Set.of());
      } else {
         LinkedHashSet<Identifier> disabledAnimations = new LinkedHashSet<>();

         for (String raw : config.getDisabledAnimations()) {
            Identifier id = Identifier.tryParse(raw);
            if (id != null) {
               disabledAnimations.add(id);
            }
         }

         AnimationDefinitions.setDisabledAnimationFilters(disabledAnimations, config.getDisabledAnimationPacks());
      }
   }

   public void onInitialize() {
      NonWildfireGenderConfigCompat.initialize();
      NonDiagnostics.initialize();
      AnimationEngine.initialize();
      CONFIG = NonConfig.load();
      NonActorEvents.PROVIDE
         .register((NonActorEvents.Provider)entity -> entity instanceof LivingEntity living ? NonTrinketsIntegration.resolveVirtualActorTags(living) : Set.of());
      applyAnimationToggleConfig();
      NonStats.registerBaseStats();
      NonSoundEvents.registerAll();
      NonParticles.registerAll();
      NonStatusEffects.registerAll();
      ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)server -> {
         NonStats.registerKnownEntityStats();
         SERVER_CONFIG_REVISION.set(0L);
         ANIMATION_CATALOG_REVISION.set(0L);
         clearServerRuntimeState();
      });
      ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> clearServerRuntimeState());
      ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((EndDataPackReload)(server, resourceManager, success) -> {
         if (success) {
            SERVER_CONFIG_REVISION.incrementAndGet();
            ANIMATION_CATALOG_REVISION.incrementAndGet();
            syncHostConfigStateToAll(server);
            syncAnimationCatalogStateToAll(server);
         }
      });
      NonPotions.registerAll();
      NonBrewingRecipes.registerAll();
      NonRecipeSerializers.registerAll();
      NonTrinketsIntegration.registerPredicates();
      FabricDefaultAttributeRegistry.register(HORSE_LIQUID_COLLECTOR_ENTITY_TYPE, HorseLiquidCollectorEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(PREGNANCY_EGG_ENTITY_TYPE, PregnancyEggEntity.createAttributes());
      PayloadTypeRegistry.playC2S().register(StopVoluntaryAnimationC2SPayload.ID, StopVoluntaryAnimationC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(StopAttackAnimationC2SPayload.ID, StopAttackAnimationC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(PlayerAttackInputC2SPayload.ID, PlayerAttackInputC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(StartManualPeakC2SPayload.ID, StartManualPeakC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(SetPlayerGenderC2SPayload.ID, SetPlayerGenderC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(RepairDestroyedSkinC2SPayload.ID, RepairDestroyedSkinC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(ManualDamageDestroyedSkinC2SPayload.ID, ManualDamageDestroyedSkinC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(DestroyedSkinUploadC2SPayload.ID, DestroyedSkinUploadC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(DestroyedSkinMaskUploadC2SPayload.ID, DestroyedSkinMaskUploadC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(PlayerProfileActionC2SPayload.ID, PlayerProfileActionC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(PlayerAnimationChoiceC2SPayload.ID, PlayerAnimationChoiceC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(PlayerAnimationConsentC2SPayload.ID, PlayerAnimationConsentC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(PlayerAnimationCancelC2SPayload.ID, PlayerAnimationCancelC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(ServerConfigRequestC2SPayload.ID, ServerConfigRequestC2SPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(ServerConfigUpdateC2SPayload.ID, ServerConfigUpdateC2SPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(AttackStateS2CPayload.ID, AttackStateS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(LiquidStateS2CPayload.ID, LiquidStateS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(PregnancyStateS2CPayload.ID, PregnancyStateS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(PregnancyCycleStateS2CPayload.ID, PregnancyCycleStateS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(PlayerRuntimeSettingsS2CPayload.ID, PlayerRuntimeSettingsS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(GenderSelectionPromptS2CPayload.ID, GenderSelectionPromptS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(HostConfigSyncS2CPayload.ID, HostConfigSyncS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(ServerConfigResultS2CPayload.ID, ServerConfigResultS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(AnimationCatalogChunkS2CPayload.ID, AnimationCatalogChunkS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(DestroyedSkinSyncS2CPayload.ID, DestroyedSkinSyncS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(DestroyedSkinMaskSyncS2CPayload.ID, DestroyedSkinMaskSyncS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(DestroyedSkinStateS2CPayload.ID, DestroyedSkinStateS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(DestroyedSkinParticlesS2CPayload.ID, DestroyedSkinParticlesS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(MessStateS2CPayload.ID, MessStateS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(AccessoryShedStateS2CPayload.ID, AccessoryShedStateS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(SkinRippedInfoRequestS2CPayload.ID, SkinRippedInfoRequestS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(PlayerProfileSnapshotS2CPayload.ID, PlayerProfileSnapshotS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(PlayerAnimationSelectionS2CPayload.ID, PlayerAnimationSelectionS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(PlayerAnimationConsentS2CPayload.ID, PlayerAnimationConsentS2CPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(PlayerAnimationRequestStatusS2CPayload.ID, PlayerAnimationRequestStatusS2CPayload.CODEC);
      NonPlayerInteractionSystem.register();
      NonAnimationRescueSystem.register();
      NonPlayerAttackSystem.register();
      registerServerSettingsNetworking();
      NonGenderSystem.registerMobGenderTags();
      NonEntityProfiles.registerReloadListener();
      NonLiquidGainOverrides.registerReloadListener();
      this.registerDebugStaffInteraction();
      this.registerEnergyControlItemInteraction();
      this.registerPlayerAnimationJoinInteraction();
      NonGenderSystem.registerCowMilkingGenderGate();
      this.registerHorseLiquidCollectorBottleInteraction();
      this.registerHorseLiquidCollectorDrops();
      NonDestroyedSkinSystem.registerDestroyedSkinRepairNetworking();
      this.registerDebugCommands();
      this.registerLiquidPotionGroupCleanup();
      this.registerAnimationListeners();
      this.registerLoopProgression();
      NonDestroyedSkinSystem.registerDestroyedSkinNetworking();
      this.registerVoluntaryStopRequests();
      this.registerAttackStopRequests();
      this.registerManualPeakRequests();
      this.registerNormalDamageDestroyedSkinDamage();
      NonGenderSystem.registerPlayerGenderRequests();
      this.registerFillBottleUseTrigger();
      this.registerLastDefeatedDeathRescue();
      this.registerPregnancyDeathCleanup();
      this.registerDeathPersistenceCopy();
      this.registerPostEscapeDamageInvulnerability();
      this.registerDirectAttackBlockCleanup();
      this.registerLiquidSync();
   }

   private void registerDebugStaffInteraction() {
      AttackEntityCallback.EVENT.register((AttackEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (hand != Hand.MAIN_HAND || player.getStackInHand(hand).getItem() != DEBUG_STAFF) {
            return ActionResult.PASS;
         }

         if (world.isClient()) {
            return ActionResult.PASS;
         }

         if (entity instanceof PlayerEntity) {
            return ActionResult.FAIL;
         }

         if (!entity.isRemoved()) {
            world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), NonSoundEvents.DEBUG_STAFF_DISAPPEAR, SoundCategory.NEUTRAL, 1.0F, 1.0F);
            entity.discard();
         }

         return ActionResult.SUCCESS;
      });
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (entity instanceof LivingEntity living) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty() || stack.getItem() != DEBUG_STAFF) {
               return ActionResult.PASS;
            }

            if (!(living instanceof EnergyHolder)) {
               return ActionResult.PASS;
            }

            if (world.isClient()) {
               return ActionResult.SUCCESS;
            }

            ActionResult result = stack.getItem().useOnEntity(stack, player, living, hand);
            return (ActionResult)(result == ActionResult.PASS ? ActionResult.SUCCESS : result);
         } else {
            return ActionResult.PASS;
         }
      });
   }

   private void registerEnergyControlItemInteraction() {
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (entity instanceof LivingEntity living) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty()) {
               return ActionResult.PASS;
            }

            if (!(stack.getItem() instanceof EnergyStabilizerItem) && !(stack.getItem() instanceof EnergyGainAdjustItem)) {
               return ActionResult.PASS;
            }

            if (!(living instanceof EnergyHolder)) {
               return ActionResult.PASS;
            }

            ActionResult result = stack.getItem().useOnEntity(stack, player, living, hand);
            return (ActionResult)(result == ActionResult.PASS ? ActionResult.SUCCESS : result);
         } else {
            return ActionResult.PASS;
         }
      });
   }

   private void registerPlayerAnimationJoinInteraction() {
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (!(world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity joiningPlayer)) {
            return ActionResult.PASS;
         } else if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
         } else if (!joiningPlayer.getStackInHand(Hand.MAIN_HAND).isEmpty()) {
            return ActionResult.PASS;
         } else if (joiningPlayer.isSpectator()) {
            return ActionResult.PASS;
         } else if (entity != null && !entity.isRemoved() && !entity.getUuid().equals(joiningPlayer.getUuid())) {
            return (ActionResult)(NonPlayerInteractionSystem.handleInteraction(serverWorld, joiningPlayer, entity) ? ActionResult.SUCCESS : ActionResult.PASS);
         } else {
            return ActionResult.PASS;
         }
      });
   }

   private void registerHorseLiquidCollectorBottleInteraction() {
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (entity instanceof HorseLiquidCollectorEntity collector) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty() || !stack.isOf(Items.GLASS_BOTTLE)) {
               return ActionResult.PASS;
            }

            if (world.isClient()) {
               return ActionResult.SUCCESS;
            }

            if (player instanceof ServerPlayerEntity serverPlayer) {
               if (!collector.isCollectorFull()) {
                  return ActionResult.SUCCESS;
               }

               if (!NonItemSystem.consumeOneGlassBottle(serverPlayer, hand)) {
                  return ActionResult.SUCCESS;
               }

               ItemStack resultBottle = NonItemSystem.createLiquidBottleStack(collector.getBottleLiquidEntityTypeId());
               collector.clearStoredLiquid();
               NonItemSystem.giveItemOrDrop(serverPlayer, resultBottle);
               world.playSound(null, collector.getX(), collector.getY(), collector.getZ(), SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.PLAYERS, 1.0F, 1.0F);
               return ActionResult.SUCCESS;
            } else {
               return ActionResult.SUCCESS;
            }
         } else {
            return ActionResult.PASS;
         }
      });
   }

   private void registerHorseLiquidCollectorDrops() {
      ServerLivingEntityEvents.AFTER_DEATH.register((AfterDeath)(entity, damageSource) -> {
         if (entity instanceof HorseLiquidCollectorEntity) {
            if (entity.getEntityWorld() instanceof ServerWorld world) {
               if (!(damageSource.getAttacker() instanceof ServerPlayerEntity player && player.isCreative())) {
                  entity.dropStack(world, new ItemStack(HORSE_LIQUID_COLLECTOR));
               }
            }
         }
      });
   }

   private void registerDebugCommands() {
      NonCommands.register();
   }

   public static Identifier id(String path) {
      return Identifier.of("needsofnature", path);
   }

   private static void grantNoNAdvancement(ServerPlayerEntity player, String path) {
      NonAdvancementHooks.grant(player, path);
   }

   public static void grantEmergencySnackAdvancement(ServerPlayerEntity player) {
      NonAdvancementHooks.grantEmergencySnack(player);
   }

   public static void grantUdderlyUnfortunateAdvancementIfPregnant(ServerPlayerEntity player) {
      if (getPregnantEntityTypeId(player) != null) {
         NonAdvancementHooks.grantUdderlyUnfortunate(player);
      }
   }

   public static void resetStableDietStreak(ServerPlayerEntity player) {
      NonAdvancementHooks.resetStableDietStreak(player);
   }

   private static void checkLiquidAdvancements(ServerPlayerEntity player, LiquidHolder holder) {
      NonAdvancementHooks.checkLiquidAdvancements(player, holder);
   }

   private static void trackFieldResearch(ServerWorld world, ServerPlayerEntity player, Map<Identifier, Integer> perEntityAmounts) {
      NonAdvancementHooks.trackFieldResearch(world, player, perEntityAmounts);
   }

   static void checkBadIdeaBeaconAdvancement(ServerPlayerEntity player) {
      NonAdvancementHooks.checkBadIdeaBeacon(player);
   }

   static boolean isMessSystemEnabled() {
      NonConfig config = getConfig();
      return config == null || config.isMessSystemEnabled();
   }

   static boolean isDestroyedSkinSystemEnabled() {
      NonConfig config = getConfig();
      return config == null || config.isDestroyedSkinSystemEnabled();
   }

   static int getDestroyedSkinStageForPlayer(ServerPlayerEntity player) {
      return NonDestroyedSkinSystem.getDestroyedSkinStageForPlayer(player);
   }

   static int getTotalMessForPlayer(ServerPlayerEntity player) {
      if (player == null || !isMessSystemEnabled()) {
         return 0;
      } else {
         return player instanceof MessHolder holder
            ? NonMessSystem.clampMess(holder.getVMess()) + NonMessSystem.clampMess(holder.getAMess()) + NonMessSystem.clampMess(holder.getMMess())
            : 0;
      }
   }

   public static UUID getActivePlayerInstance(ServerPlayerEntity player) {
      return player != null && player.getEntityWorld() instanceof ServerWorld world
         ? ServerAnimationController.findActiveInstanceId(world, player.getUuid())
         : null;
   }

   public static boolean hasActivePlayerAnimation(ServerPlayerEntity player) {
      if (player == null) {
         return false;
      }

      UUID active = getActivePlayerInstance(player);
      return active != null;
   }

   public static boolean instanceContainsActor(ServerWorld world, UUID instanceId, UUID actorUuid) {
      if (world != null && instanceId != null && actorUuid != null) {
         ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
         return snapshot != null && snapshot.actorUuids().contains(actorUuid);
      } else {
         return false;
      }
   }

   public static boolean hasNearbyVisibleAnimation(ServerWorld world, MobEntity viewer) {
      return getNearbyVisibleAnimationGainMultiplier(world, viewer) > 1.0;
   }

   public static double getNearbyVisibleAnimationGainMultiplier(ServerWorld world, MobEntity viewer) {
      if (world != null && viewer != null) {
         double maxDistSq = 400.0;
         UUID viewerUuid = viewer.getUuid();
         double best = 1.0;

         for (ServerAnimationController.ActiveInstanceSnapshot snapshot : ServerAnimationController.getActiveInstanceSnapshots(world)) {
            List<UUID> actorUuids = snapshot.actorUuids();
            if (actorUuids != null && !actorUuids.isEmpty() && !actorUuids.contains(viewerUuid)) {
               for (UUID actorUuid : actorUuids) {
                  if (actorUuid != null
                     && world.getEntity(actorUuid) instanceof LivingEntity living
                     && living.isAlive()
                     && !living.isRemoved()
                     && !(viewer.squaredDistanceTo(living) > maxDistSq)
                     && viewer.canSee(living)) {
                     double multiplier = getNearAnimationGainMult();
                     if (living instanceof ServerPlayerEntity player) {
                        multiplier *= NonTrinketsIntegration.getAccessoryEffects(player).nearAnimationMobEnergyMultiplier();
                     }

                     best = Math.max(best, multiplier);
                  }
               }
            }
         }

         return best;
      } else {
         return 1.0;
      }
   }

   public static float getNearAnimationGainMult() {
      NonConfig config = getConfig();
      return config == null ? 3.0F : (float)config.getNearAnimationEnergyGainMult();
   }

   public static float getNearAnimationGainMult(@Nullable ServerPlayerEntity player) {
      float base = getNearAnimationGainMult();
      return player == null ? base : (float)(base * NonTrinketsIntegration.getAccessoryEffects(player).nearAnimationMobEnergyMultiplier());
   }

   public static void tickPlayerEnergyAura(ServerWorld world, ServerPlayerEntity player) {
      NonAuraSystem.tickPlayerEnergyAura(world, player);
   }

   public static NeedsOfNature.PlayerEnergyAuraDebug getPlayerEnergyAuraDebug(ServerWorld world, MobEntity mob, EnergyHolder holder) {
      NonAuraSystem.PlayerEnergyAuraDebug debug = NonAuraSystem.getPlayerEnergyAuraDebug(world, mob, holder);
      return debug == null
         ? null
         : new NeedsOfNature.PlayerEnergyAuraDebug(
            debug.player(), debug.totalMultiplier(), debug.playerEnergy(), debug.destroyedSkinStage(), debug.messTotal(), debug.radius()
         );
   }

   private static Item registerItem(RegistryKey<Item> key, Item item) {
      return (Item)Registry.register(Registries.ITEM, key, item);
   }

   private static List<Item> registerDataDrivenAccessories() {
      NonAccessoryItemRegistry.registerStartupItems();
      NonAccessoryDefinitions.loadStartupDefinitions();
      return NonAccessoryItemRegistry.getRegisteredItems();
   }

   private void registerLiquidPotionGroupCleanup() {
      ItemGroupEvents.MODIFY_ENTRIES_ALL.register((ModifyEntriesAll)(group, entries) -> {
         Identifier groupId = Registries.ITEM_GROUP.getId(group);
         if (groupId == null || !groupId.equals(id("main"))) {
            entries.getDisplayStacks().removeIf(NonItemSystem::isNonPotionVariantStack);
            entries.getSearchTabStacks().removeIf(NonItemSystem::isNonPotionVariantStack);
         }
      });
   }

   private void registerPregnancyDeathCleanup() {
      ServerLivingEntityEvents.AFTER_DEATH.register((AfterDeath)(entity, damageSource) -> {
         if (entity instanceof ServerPlayerEntity player) {
            NonConfig config = getConfig();
            if (config != null && config.keepPregnancyAfterDeath()) {
               return;
            }

            clearPregnancyOnDeath(player);
         }
      });
   }

   private void registerDeathPersistenceCopy() {
      ServerPlayerEvents.COPY_FROM.register((CopyFrom)(oldPlayer, newPlayer, alive) -> {
         if (!alive) {
            copyDeathPersistentPlayerState(oldPlayer, newPlayer);
         }
      });
      ServerPlayerEvents.AFTER_RESPAWN.register((AfterRespawn)(oldPlayer, newPlayer, alive) -> {
         if (!alive) {
            NonPlayerAttackSystem.clearForRespawn(newPlayer);
            schedulePostDeathStateSync(newPlayer);
         }
      });
   }

   private static void copyDeathPersistentPlayerState(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
      if (oldPlayer != null && newPlayer != null) {
         NonConfig config = getConfig();
         if (config != null) {
            NonPregnancyConceptionSystem.copyState(oldPlayer, newPlayer);
            if (config.isMessSystemEnabled()) {
               if (config.keepMessAfterDeath()) {
                  NeedsOfNature.MessSnapshot.copy(oldPlayer, newPlayer);
               } else {
                  NonMessSystem.clearMessStoredState(newPlayer);
               }
            }

            if (config.isDestroyedSkinSystemEnabled()) {
               if (config.keepRippedSkinAfterDeath()) {
                  NeedsOfNature.RippedSkinSnapshot.copy(oldPlayer, newPlayer);
               } else {
                  NonDestroyedSkinSystem.clearDestroyedSkinStoredState(newPlayer);
               }
            }

            if (config.isLiquidTankEnabled()) {
               if (config.keepLiquidTankAfterDeath()) {
                  NeedsOfNature.LiquidSnapshot.copy(oldPlayer, newPlayer);
               } else {
                  clearLiquidStoredState(newPlayer);
               }
            }

            if (config.isPregnancyEnabled() && config.keepPregnancyAfterDeath()) {
               NeedsOfNature.PregnancySnapshot.copy(oldPlayer, newPlayer);
            }
         }
      }
   }

   private static void schedulePostDeathStateSync(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld() instanceof ServerWorld world) {
         PENDING_POST_DEATH_STATE_SYNCS.put(player.getUuid(), new NeedsOfNature.PostDeathStateSync(world.getRegistryKey(), world.getTime() + 2L));
      }
   }

   private static void tickPostDeathStateSync(ServerWorld world, long now) {
      if (world != null && !PENDING_POST_DEATH_STATE_SYNCS.isEmpty()) {
         RegistryKey<World> worldKey = world.getRegistryKey();

         for (Entry<UUID, NeedsOfNature.PostDeathStateSync> entry : PENDING_POST_DEATH_STATE_SYNCS.entrySet()) {
            UUID playerUuid = entry.getKey();
            NeedsOfNature.PostDeathStateSync sync = entry.getValue();
            if (playerUuid != null && sync != null) {
               if (worldKey.equals(sync.worldKey()) && now >= sync.dueTick()) {
                  ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerUuid);
                  if (player != null) {
                     syncPostDeathPlayerState(player);
                  }

                  PENDING_POST_DEATH_STATE_SYNCS.remove(playerUuid, sync);
               }
            } else if (playerUuid != null) {
               PENDING_POST_DEATH_STATE_SYNCS.remove(playerUuid);
            }
         }
      }
   }

   private static void syncPostDeathPlayerState(ServerPlayerEntity player) {
      if (player != null) {
         NonConfig config = getConfig();
         if (config != null) {
            if (!config.keepMessAfterDeath()) {
               NonMessSystem.clearMessStoredState(player);
            }

            NonMessSystem.broadcastMessState(player);
            if (!config.keepRippedSkinAfterDeath()) {
               NonDestroyedSkinSystem.clearDestroyedSkinStoredState(player);
            }

            NonDestroyedSkinSystem.broadcastDestroyedSkinStage(player);
            if (!config.keepLiquidTankAfterDeath()) {
               clearLiquidStoredState(player);
            }

            syncLiquidState(player);
            syncPregnancyState(player);
            NonPregnancyConceptionSystem.syncState(player);
         }
      }
   }

   private static void clearLiquidStoredState(ServerPlayerEntity player) {
      if (player instanceof LiquidHolder holder) {
         holder.setLiquidStored(0);
         holder.clearLiquidComposition();
      }
   }

   private void registerLastDefeatedDeathRescue() {
      ServerLivingEntityEvents.ALLOW_DEATH
         .register(
            (AllowDeath)(entity, damageSource, damageAmount) -> {
               if (entity instanceof ServerPlayerEntity player) {
                  if (player.getEntityWorld() instanceof ServerWorld world) {
                     ServerPlayerEntity damagingPlayer = NonPlayerAttackSystem.resolveAttributedPlayer(damageSource);
                     if (damagingPlayer != null && NonPlayerAttackSystem.isOwnerFinishingDamage(world, player, damageSource)) {
                        NonPlayerAttackSystem.onOwnerFinishingDeath(world, player);
                        return true;
                     }

                     NonConfig config = getConfig();
                     if (config == null || !config.isLastDefeatedEnabled()) {
                        return true;
                     }

                     if (hasVanillaTotemReady(player)) {
                        return true;
                     }

                     if (AnimationService.isActorPendingOrActive(world, player.getUuid())) {
                        return true;
                     }

                     long nowTick = getGlobalTick(world);
                     Long cooldownUntil = LAST_DEFEATED_COOLDOWN_UNTIL_BY_PLAYER.get(player.getUuid());
                     if (cooldownUntil != null && cooldownUntil > nowTick) {
                        return true;
                     }

                     if (damagingPlayer != null) {
                        if (damagingPlayer != player
                           && damagingPlayer.getEntityWorld() == world
                           && damagingPlayer.isAlive()
                           && !damagingPlayer.isRemoved()
                           && !damagingPlayer.isSpectator()) {
                           if (!(damagingPlayer instanceof EnergyHolder holder && holder.getEnergy() >= config.getLastDefeatedEnergyThreshold())) {
                              return true;
                           } else if (NonPlayerInteractionSystem.areAttacksAllowed(player, damagingPlayer)
                              && !AnimationService.isActorPendingOrActive(world, damagingPlayer.getUuid())) {
                              AnimationService.MatchedAnimation attackMatch = NonPlayerAttackSystem.findViableAttack(world, damagingPlayer, player);
                              if (attackMatch == null) {
                                 return true;
                              }

                              AnimationService.MatchedAnimation defeatMatch = NonDefeatedAnimationSelector.find(world, player, attackMatch.animationId());
                              if (defeatMatch == null) {
                                 return true;
                              }

                              player.setHealth(1.0F);
                              UUID instanceId = startAnimationNowWithContext(
                                 world,
                                 defeatMatch.animationId(),
                                 List.of(player),
                                 defeatMatch.actorKeys(),
                                 defeatMatch.stages(),
                                 NonAnimationDamageBehavior.IGNORE_DAMAGE,
                                 true,
                                 player,
                                 NonPlayerAttackSystem.buildPlayerAttackContext(player.getUuid(), damagingPlayer.getUuid(), Set.of(damagingPlayer.getUuid()))
                              );
                              if (instanceId == null) {
                                 player.setHealth(0.0F);
                                 return true;
                              }

                              int cooldownTicks = config.getLastDefeatedCooldownSeconds() * 20;
                              if (cooldownTicks > 0) {
                                 LAST_DEFEATED_COOLDOWN_UNTIL_BY_PLAYER.put(player.getUuid(), nowTick + cooldownTicks);
                              }

                              logRuntime(
                                 "[NoN] Last defeated rescued player={} from lethal player damage. attacker={} sourceAnimation={} defeatedAnimation={} instance={}",
                                 player.getName().getString(),
                                 damagingPlayer.getName().getString(),
                                 attackMatch.animationId(),
                                 defeatMatch.animationId(),
                                 instanceId
                              );
                              return false;
                           } else {
                              return true;
                           }
                        } else {
                           return true;
                        }
                     } else {
                        Entity attacker = damageSource == null ? null : damageSource.getAttacker();
                        Entity sourceEntity = damageSource == null ? null : damageSource.getSource();
                        MobEntity damagingMob = attacker instanceof MobEntity mob ? mob : (sourceEntity instanceof MobEntity mob ? mob : null);
                        if (damagingMob == null) {
                           return true;
                        }

                        NeedsOfNature.LastDefeatedCandidate candidate = findLastDefeatedCandidate(world, player, damagingMob);
                        if (candidate == null) {
                           return true;
                        }

                        player.setHealth(1.0F);
                        UUID instanceId = startAnimationNowWithContext(
                           world,
                           candidate.defeatMatch().animationId(),
                           List.of(player),
                           candidate.defeatMatch().actorKeys(),
                           candidate.defeatMatch().stages(),
                           NonAnimationDamageBehavior.BLOCK_DAMAGE,
                           true,
                           player,
                           buildModeContext(true)
                        );
                        if (instanceId == null) {
                           player.setHealth(0.0F);
                           return true;
                        }

                        int cooldownTicks = config.getLastDefeatedCooldownSeconds() * 20;
                        if (cooldownTicks > 0) {
                           LAST_DEFEATED_COOLDOWN_UNTIL_BY_PLAYER.put(player.getUuid(), nowTick + cooldownTicks);
                        }

                        logRuntime(
                           "[NoN] Last defeated rescued player={} from lethal mob damage. mob={} sourceAnimation={} defeatedAnimation={} instance={}",
                           player.getName().getString(),
                           Registries.ENTITY_TYPE.getId(candidate.mob().getType()),
                           candidate.gatherCandidate().animationId(),
                           candidate.defeatMatch().animationId(),
                           instanceId
                        );
                        return false;
                     }
                  } else {
                     return true;
                  }
               } else {
                  return true;
               }
            }
         );
   }

   private void registerDirectAttackBlockCleanup() {
      ServerLivingEntityEvents.ALLOW_DAMAGE.register((AllowDamage)(entity, source, amount) -> {
         if (entity instanceof ServerPlayerEntity player) {
            if (player.getEntityWorld() instanceof ServerWorld world) {
               Entity attacker = source.getAttacker();
               if (attacker instanceof MobEntity mob) {
                  Entity directSource = source.getSource();
                  if (directSource != null && directSource != attacker) {
                     return true;
                  }

                  NeedsOfNature.AttackBlockReason reason = getMobAttackBlockReason(world, mob, player);
                  if (reason == null) {
                     return true;
                  }

                  onMobAttackBlocked(world, mob, player, reason);
                  return false;
               } else {
                  return true;
               }
            } else {
               return true;
            }
         } else {
            return true;
         }
      });
   }

   private void registerPostEscapeDamageInvulnerability() {
      ServerLivingEntityEvents.ALLOW_DAMAGE.register((AllowDamage)(entity, source, amount) -> {
         if (entity instanceof ServerPlayerEntity player) {
            return player.getEntityWorld() instanceof ServerWorld world ? !isPostEscapeDamageInvulnerable(world, player) : true;
         } else {
            return true;
         }
      });
   }

   private static void setPostEscapeDamageInvulnerable(ServerPlayerEntity player, int ticks) {
      if (player != null) {
         if (player.getEntityWorld() instanceof ServerWorld world) {
            if (ticks <= 0) {
               POST_ESCAPE_DAMAGE_INVULNERABLE_UNTIL_BY_PLAYER.remove(player.getUuid());
            } else {
               POST_ESCAPE_DAMAGE_INVULNERABLE_UNTIL_BY_PLAYER.put(player.getUuid(), world.getTime() + ticks);
            }
         }
      }
   }

   private static boolean isPostEscapeDamageInvulnerable(ServerWorld world, ServerPlayerEntity player) {
      if (world != null && player != null) {
         Long untilTick = POST_ESCAPE_DAMAGE_INVULNERABLE_UNTIL_BY_PLAYER.get(player.getUuid());
         if (untilTick == null) {
            return false;
         } else if (world.getTime() >= untilTick) {
            POST_ESCAPE_DAMAGE_INVULNERABLE_UNTIL_BY_PLAYER.remove(player.getUuid(), untilTick);
            return false;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   private void registerNormalDamageDestroyedSkinDamage() {
      ServerLivingEntityEvents.AFTER_DAMAGE.register((AfterDamage)(entity, source, baseDamageTaken, damageTaken, blocked) -> {
         if (!blocked && !(damageTaken <= 0.0F)) {
            if (entity instanceof ServerPlayerEntity player) {
               if (player.getEntityWorld() instanceof ServerWorld world) {
                  if (isDestroyedSkinSystemEnabled()) {
                     if (!AnimationService.isActorPendingOrActive(world, player.getUuid())) {
                        int chance = getConfig().getNormalDamageDestroyedSkinChancePercent();
                        if (chance > 0) {
                           if (chance >= 100 || world.getRandom().nextInt(100) < chance) {
                              NonDestroyedSkinSystem.applyDestroyedSkinAttackDamage(world, player);
                           }
                        }
                     }
                  }
               }
            }
         }
      });
   }

   private void registerAnimationListeners() {
      AnimationLifecycleEvents.START
         .register(
            (AnimationLifecycleEvents.Start)event -> {
               ServerWorld world = event.world();
               ServerAnimationController.ActiveInstanceSnapshot snapshot = event.snapshot();
               UUID instanceId = snapshot.instanceId();
               Identifier animationId = snapshot.animationId();
               List<UUID> actorUuids = snapshot.actorUuids();
               List<String> actorKeys = snapshot.actorKeys();
               List<AnimationStageInfo> stages = snapshot.stages();
               long startTick = snapshot.startTick();
               clearPendingGatherPairsForActors(world, actorUuids);
               boolean hasPlayer = false;
               List<ServerPlayerEntity> playerActors = new ArrayList<>();

               for (UUID uuid : actorUuids) {
                  if (world.getEntity(uuid) instanceof ServerPlayerEntity player) {
                     hasPlayer = true;
                     playerActors.add(player);
                  }
               }

               AnimationStartContext startContext = snapshot.startContext();
               NonAnimationSessionData sessionData = getNonSessionData(startContext);
               adoptPendingBreedingRequest(world, instanceId, sessionData);
               adoptPendingPregnancyHatch(world, instanceId, actorUuids, sessionData);
               boolean isPregnancy = hasPlayer && sessionData != null && sessionData.isPregnancy();
               boolean isAttack = hasPlayer && sessionData != null && sessionData.isAttack();
               boolean isDefeatAnimation = hasAnimationTagWithPrefix(animationId, "defeat.");
               ServerPlayerEntity attackVictim = isAttack ? NonPlayerAttackSystem.resolveAttackVictim(world, instanceId, actorUuids) : null;
               applyPlayerAnimationStartEnergy(world, playerActors, animationId, startContext);

               for (UUID uuid : actorUuids) {
                  if (world.getEntity(uuid) instanceof EnergyHolder holder) {
                     holder.onNonAnimationStarted();
                  }
               }

               if (!playerActors.isEmpty()) {
                  for (ServerPlayerEntity player : playerActors) {
                     NonStats.incrementAnimationStarted(player);
                     if (actorUuids.size() > 2) {
                        NonStats.incrementMultiActorAnimation(player);
                     }

                     if (actorUuids.size() >= 4) {
                        NonAdvancementHooks.grantGroupActivity(player);
                     }

                     if (isWaterAnimation(animationId)) {
                        NonStats.incrementWaterAnimation(player);
                     }

                     if (isAttack && !isDefeatAnimation && player == attackVictim) {
                        NonStats.incrementAttackAnimationStarted(player);
                     } else if (!isAttack) {
                        NonAdvancementHooks.resetTossedAround(player);
                     }
                  }
               }

               if (isFillBottleAnimation(animationId) && !playerActors.isEmpty()) {
                  FILL_BOTTLE_STATES.putIfAbsent(instanceId, new NeedsOfNature.FillBottleState(playerActors.getFirst().getUuid(), false));
               }

               if (isPregnancy) {
                  for (ServerPlayerEntity player : playerActors) {
                     player.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.PREGNANT));
                  }
               }

               NonAccessoryShedSystem.ShedState animationAccessoryShedState = NonAccessoryShedSystem.resolveShedStateForAnimation(animationId);
               if (!playerActors.isEmpty()) {
                  for (int actorIndex = 0; actorIndex < actorUuids.size(); actorIndex++) {
                     if (world.getEntity(actorUuids.get(actorIndex)) instanceof ServerPlayerEntity player) {
                        NonAccessoryShedSystem.ShedState accessoryShedState = animationAccessoryShedState;
                        String actorKey = actorIndex < actorKeys.size() ? actorKeys.get(actorIndex) : null;
                        accessoryShedState = resolveRoleAccessoryShedState(animationId, actorKey, player, accessoryShedState);
                        if (isPregnancy) {
                           accessoryShedState = accessoryShedState.merge(NonAccessoryShedSystem.forSlot(NonTrinketsIntegration.getActiveTankSlotId(player)));
                        }

                        if (!accessoryShedState.isEmpty()) {
                           NonAccessoryShedSystem.addServerShedState(world, player, accessoryShedState);
                        }
                     }
                  }
               }

               NonPlayerAttackSystem.onAnimationStarted(
                  world, instanceId, actorUuids, sessionData == null ? null : sessionData.playerAttack(), startTick, isDefeatAnimation
               );
               if (shouldApplyDestroyedSkinAttackDamage(isAttack, startContext, animationId) && attackVictim != null) {
                  NonDestroyedSkinSystem.applyDestroyedSkinAttackDamage(world, attackVictim);
               }

               int startStage = resolveStartStage(stages);
               AnimationStageInfo startStageInfo = stageInfoForNumber(stages, startStage);
               Integer peakStage = NonPeakStages.getPeakStage(animationId);
               if (peakStage != null) {
                  boolean grantAtStart = startStage == peakStage;
                  int actualAddedMl = 0;
                  if (grantAtStart) {
                     actualAddedMl = applyPeakStageEffects(world, instanceId, actorUuids, actorKeys, animationId, startStageInfo, startStage, startTick);
                  }

                  PEAK_STATES.put(
                     instanceId, new NeedsOfNature.PeakState(animationId, peakStage, grantAtStart, startStage, hasPlayer, grantAtStart, actualAddedMl)
                  );
                  if (grantAtStart && isAttack && !isManualPeakAnimation(animationId)) {
                     queueDefeatedTailForInstance(world, instanceId, actorUuids);
                  }
               }

               refreshPregnancyCueRuntime(world, instanceId, startTick, animationId);
               if (!playerActors.isEmpty()) {
                  sendEscapeControlState(world, instanceId, isAttack, isDefeatAnimation, startStageInfo, playerActors);
               }
            }
         );
      AnimationLifecycleEvents.STOP.register((AnimationLifecycleEvents.Stop)event -> {
         ServerWorld world = event.world();
         ServerAnimationController.ActiveInstanceSnapshot snapshot = event.snapshot();
         UUID instanceId = snapshot.instanceId();
         Identifier animationId = snapshot.animationId();
         List<UUID> actorUuids = snapshot.actorUuids();
         List<String> actorKeys = snapshot.actorKeys();
         clearPendingGatherPairsForActors(world, actorUuids);
         clearEscapeControlState(world, instanceId, actorUuids);
         NonPlayerAttackSystem.onAnimationStopped(world, instanceId);
         PEAK_LIQUID_PROGRESS.remove(instanceId);
         boolean isAttack = isAttackSnapshot(snapshot);
         NeedsOfNature.PeakState peakState = PEAK_STATES.remove(instanceId);
         if (peakState != null && peakState.peaked) {
            if (isAttack) {
               recordAttackOutcomeForMobs(world, actorUuids, "peak");
            }

            if (peakState.hasPlayer) {
               calmMobsAfterPeakedPlayerAnimation(world, actorUuids);
            }

            if (!isManualPeakAnimation(peakState.animationId) && !isFillBottleAnimation(peakState.animationId)) {
               if (peakState.hasPlayer) {
                  int xpEligibleMl = PEAK_XP_ELIGIBLE_ML.getOrDefault(instanceId, 0);
                  spawnPeakXp(world, actorUuids, xpEligibleMl);
               }

               scheduleReceiverDripOnPeak(world, actorUuids, actorKeys, peakState.animationId, world.getTime());
            }
         }

         boolean peaked = peakState != null && peakState.peaked;
         finalizeAnimationBreeding(world, instanceId, peaked);
         NeedsOfNature.FillBottleState fillState = FILL_BOTTLE_STATES.remove(instanceId);
         if (fillState != null) {
            handleFillBottleStop(world, fillState, peaked);
         }

         finalizePregnancyHatch(world, instanceId, false);
         DEFEATED_QUEUED_INSTANCES.remove(instanceId);
         PEAK_XP_ELIGIBLE_ML.remove(instanceId);
      });
      AnimationLifecycleEvents.STAGE_ADVANCE
         .register(
            (AnimationLifecycleEvents.StageAdvance)event -> {
               ServerWorld world = event.world();
               ServerAnimationController.ActiveInstanceSnapshot snapshot = event.snapshot();
               UUID instanceId = snapshot.instanceId();
               Identifier animationId = snapshot.animationId();
               List<UUID> actorUuids = snapshot.actorUuids();
               List<String> actorKeys = snapshot.actorKeys();
               long advanceTick = event.advanceTick();
               refreshPregnancyCueRuntime(world, instanceId, advanceTick, animationId);
               routePregnancyBirthStage(world, instanceId);
               AnimationStageInfo currentStage = event.currentStage();
               syncEscapeControlState(world, instanceId, actorUuids, currentStage);
               NeedsOfNature.PeakState peakState = PEAK_STATES.get(instanceId);
               if (peakState != null) {
                  int nextStage = peakState.lastStage;
                  Integer stageNumber = NonPeakStages.stageNumberFromId(currentStage == null ? null : currentStage.animationId());
                  if (stageNumber != null) {
                     nextStage = stageNumber;
                  }

                  boolean liquidGranted = peakState.liquidGranted;
                  int actualAddedMl = peakState.actualAddedMl;
                  if (!liquidGranted && stageNumber != null && stageNumber == peakState.peakStage) {
                     liquidGranted = true;
                     actualAddedMl += applyPeakStageEffects(world, instanceId, actorUuids, actorKeys, animationId, currentStage, stageNumber, advanceTick);
                     ServerAnimationController.ActiveInstanceSnapshot currentSnapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
                     if (isAttackSnapshot(currentSnapshot) && peakState.hasPlayer && !isManualPeakAnimation(animationId) && !isFillBottleAnimation(animationId)
                        )
                      {
                        queueDefeatedTailForInstance(world, instanceId, actorUuids);
                     }
                  } else if (liquidGranted && stageNumber != null && stageNumber != peakState.peakStage) {
                     PEAK_LIQUID_PROGRESS.remove(instanceId);
                  }

                  boolean peaked = peakState.peaked;
                  if (!peaked) {
                     if (stageNumber != null && stageNumber == peakState.peakStage) {
                        peaked = true;
                     } else if (peakState.lastStage == peakState.peakStage) {
                        peaked = true;
                     }
                  }

                  PEAK_STATES.put(
                     instanceId,
                     new NeedsOfNature.PeakState(
                        peakState.animationId, peakState.peakStage, peaked, nextStage, peakState.hasPlayer, liquidGranted, actualAddedMl
                     )
                  );
               }
            }
         );
   }

   private static NonAccessoryShedSystem.ShedState resolveRoleAccessoryShedState(
      Identifier animationId, @Nullable String actorKey, ServerPlayerEntity player, NonAccessoryShedSystem.ShedState baseState
   ) {
      if (player != null && NonTrinketsIntegration.hasMaleRoleAccessory(player)) {
         boolean femaleRole = false;
         AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(animationId);
         AnimationDefinitions.ActorConstraint constraint = AnimationDefinitions.resolveActorConstraintForKey(definition, actorKey);
         if (constraint != null) {
            femaleRole = constraint.requiredTags().contains("gender.female") || constraint.requiredTagsAny().contains("gender.female");
         }

         boolean receivesV = false;
         NonLiquidRoles.LiquidRoles roles = AnimationGameplayMetadata.liquidRoles(animationId);
         if (roles != null && actorKey != null && roles.receiverKeys().contains(actorKey)) {
            receivesV = roles.injectorRoles().containsValue(NonLiquidRoles.InjectorRole.V);
         }

         return !femaleRole && !receivesV ? baseState : baseState.merge(NonAccessoryShedSystem.forSlot("legs/v"));
      } else {
         return baseState;
      }
   }

   private static void syncEscapeControlState(ServerWorld world, UUID instanceId, List<UUID> actorUuids, @Nullable AnimationStageInfo currentStage) {
      List<ServerPlayerEntity> playerActors = collectPlayerActors(world, actorUuids);
      if (!playerActors.isEmpty()) {
         ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
         boolean isAttack = isAttackSnapshot(snapshot);
         Identifier animationId = snapshot == null ? null : snapshot.animationId();
         boolean isDefeatAnimation = hasAnimationTagWithPrefix(animationId, "defeat.");
         sendEscapeControlState(world, instanceId, isAttack, isDefeatAnimation, currentStage, playerActors);
      }
   }

   private static void sendEscapeControlState(
      ServerWorld world,
      UUID instanceId,
      boolean isAttack,
      boolean isDefeatAnimation,
      @Nullable AnimationStageInfo currentStage,
      List<ServerPlayerEntity> playerActors
   ) {
      if (instanceId != null && playerActors != null && !playerActors.isEmpty()) {
         boolean escapable = isAttack && isEscapableStage(currentStage);
         if (!isAttack || !NonPlayerAttackSystem.syncEscapeControl(world, instanceId, escapable, isDefeatAnimation)) {
            AttackStateS2CPayload.EscapeProfile profile = AttackStateS2CPayload.EscapeProfile.NONE;
            if (isAttack) {
               profile = isDefeatAnimation ? AttackStateS2CPayload.EscapeProfile.DEFEATED : AttackStateS2CPayload.EscapeProfile.NORMAL;
            }

            AttackStateS2CPayload payload = new AttackStateS2CPayload(instanceId, isAttack, escapable, profile);

            for (ServerPlayerEntity player : playerActors) {
               ServerPlayNetworking.send(player, payload);
            }
         }
      }
   }

   private static void clearEscapeControlState(ServerWorld world, UUID instanceId, List<UUID> actorUuids) {
      if (world != null && instanceId != null) {
         List<ServerPlayerEntity> playerActors = collectPlayerActors(world, actorUuids);
         if (!playerActors.isEmpty()) {
            AttackStateS2CPayload payload = new AttackStateS2CPayload(instanceId, false, false, AttackStateS2CPayload.EscapeProfile.NONE);

            for (ServerPlayerEntity player : playerActors) {
               ServerPlayNetworking.send(player, payload);
            }
         }
      }
   }

   private static boolean isEscapableStage(@Nullable AnimationStageInfo stage) {
      return stage != null && stage.animationId() != null ? AnimationGameplayMetadata.escapable(stage.animationId()) : true;
   }

   private static void spawnPeakXp(ServerWorld world, List<UUID> actorUuids, int xpEligibleMl) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         if (xpEligibleMl > 0) {
            double xpPerMl = getConfig().getPeakXpPerMl();
            if (!(xpPerMl <= 0.0)) {
               int xpAmount = Math.max(1, (int)Math.round(xpEligibleMl * xpPerMl));
               xpAmount = Math.min(20, xpAmount);

               for (UUID uuid : actorUuids) {
                  Entity entity = world.getEntity(uuid);
                  if (entity != null) {
                     Vec3d pos = new Vec3d(entity.getX(), entity.getBodyY(0.5), entity.getZ());
                     ExperienceOrbEntity.spawn(world, pos, xpAmount);
                  }
               }
            }
         }
      }
   }

   private static void applyPlayerAnimationStartEnergy(
      ServerWorld world, List<ServerPlayerEntity> playerActors, Identifier animationId, @Nullable AnimationStartContext startContext
   ) {
      if (world != null && playerActors != null && !playerActors.isEmpty()) {
         if (shouldApplyPlayerAnimationStartEnergy(animationId, startContext)) {
            for (ServerPlayerEntity player : playerActors) {
               if (player instanceof EnergyHolder holder) {
                  int current = Math.max(0, holder.getEnergy());
                  holder.setEnergy(Math.min(holder.getMaxEnergy(), current + 20));
                  updateEnergizedEffect(player, holder);
               }
            }
         }
      }
   }

   private static boolean shouldApplyPlayerAnimationStartEnergy(Identifier animationId, @Nullable AnimationStartContext startContext) {
      if (animationId == null) {
         return false;
      } else if (startContext != null && startContext.isJoinReplacement()) {
         return false;
      } else if (isManualPeakAnimation(animationId)) {
         return false;
      } else {
         return isBirthAnimation(animationId) ? false : !isFillBottleAnimation(animationId);
      }
   }

   private static void applyEnergyOnPeak(ServerWorld world, List<UUID> actorUuids, boolean manualPeak) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         for (UUID uuid : actorUuids) {
            Entity entity = world.getEntity(uuid);
            if (entity instanceof EnergyHolder holder) {
               if (entity instanceof ServerPlayerEntity) {
                  int current = Math.max(0, holder.getEnergy());
                  int next = manualPeak ? (int)Math.floor(current * 0.6666666666666666) : current / 2;
                  holder.setEnergy(next);
               } else {
                  holder.setEnergy(0);
                  float currentMult = holder.getEnergyGainMultiplier();
                  if (Float.isFinite(currentMult) && currentMult > 0.0F) {
                     holder.setEnergyGainMultiplier(Math.min(6.0F, currentMult + 0.05F));
                  }
               }
            }
         }
      }
   }

   private static int applyPeakStageEffects(
      ServerWorld world,
      UUID instanceId,
      List<UUID> actorUuids,
      List<String> actorKeys,
      Identifier animationId,
      @Nullable AnimationStageInfo currentStage,
      @Nullable Integer stageNumber,
      long stageStartTick
   ) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         boolean manualPeak = isManualPeakAnimation(animationId);
         boolean fillBottle = isFillBottleAnimation(animationId);
         int actualAddedMl = 0;
         if (!fillBottle) {
            applyEnergyOnPeak(world, actorUuids, manualPeak);
         }

         NeedsOfNature.PeakLiquidPlan liquidPlan = new NeedsOfNature.PeakLiquidPlan(Map.of(), Map.of(), Set.of());
         if (!manualPeak && !fillBottle) {
            liquidPlan = preparePeakLiquidPlan(world, actorUuids, actorKeys, animationId);
            actualAddedMl = startPeakLiquidProgression(world, instanceId, animationId, liquidPlan, currentStage, stageNumber, stageStartTick);
            fillHorseLiquidCollectorsOnPeak(world, actorUuids, actorKeys, animationId);
         }

         trackPeakStats(world, actorUuids, manualPeak);
         if (!manualPeak && !fillBottle) {
            maybeStartPregnancyOnPeak(world, actorUuids, actorKeys, animationId, liquidPlan.accessoryPregnancyDonors());
         }

         applyInjectionAccessoryDurabilityCosts(world, actorUuids, actorKeys, animationId);
         NonMessSystem.incrementMessOnPeak(world, actorUuids, actorKeys, animationId, liquidPlan.suppliedInjectorRoles());
         scheduleMInjectorRewardsOnPeak(world, actorUuids, actorKeys, animationId, liquidPlan.suppliedInjectorRoles());
         return actualAddedMl;
      } else {
         return 0;
      }
   }

   private static void applyInjectionAccessoryDurabilityCosts(ServerWorld world, List<UUID> actorUuids, List<String> actorKeys, Identifier animationId) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty() && animationId != null) {
         NonLiquidRoles.LiquidRoles roles = AnimationGameplayMetadata.liquidRoles(animationId);
         if (roles != null && !roles.injectorRoles().isEmpty()) {
            boolean hasV = roles.injectorRoles().containsValue(NonLiquidRoles.InjectorRole.V);
            boolean hasA = roles.injectorRoles().containsValue(NonLiquidRoles.InjectorRole.A);
            if (hasV || hasA) {
               for (ServerPlayerEntity player : collectPlayerReceiverActors(world, actorUuids, actorKeys, animationId)) {
                  if (player != null && player.isAlive() && !player.isRemoved()) {
                     if (hasV) {
                        NonTrinketsIntegration.applyInjectionDurabilityCost(world, player, NonLiquidRoles.InjectorRole.V);
                     }

                     if (hasA) {
                        NonTrinketsIntegration.applyInjectionDurabilityCost(world, player, NonLiquidRoles.InjectorRole.A);
                     }
                  }
               }
            }
         }
      }
   }

   private static void scheduleMInjectorRewardsOnPeak(
      ServerWorld world, List<UUID> actorUuids, List<String> actorKeys, Identifier animationId, Set<NonLiquidRoles.InjectorRole> suppliedInjectorRoles
   ) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         if (suppliedInjectorRoles != null && suppliedInjectorRoles.contains(NonLiquidRoles.InjectorRole.M)) {
            for (ServerPlayerEntity player : collectPlayerReceiverActors(world, actorUuids, actorKeys, animationId)) {
               if (player != null && player.isAlive() && !player.isRemoved()) {
                  PENDING_M_INJECTOR_REWARDS.put(player.getUuid(), new NeedsOfNature.PendingMInjectorReward(world.getRegistryKey(), -1L));
                  NonStats.addMInjectorPeaksMl(player, 8);
               }
            }
         }
      }
   }

   @Nullable
   private static AnimationStageInfo stageInfoForNumber(@Nullable List<AnimationStageInfo> stages, int stageNumber) {
      if (stages == null || stages.isEmpty()) {
         return null;
      }

      if (stageNumber <= 0) {
         return null;
      }

      int index = stageNumber - 1;
      return index >= 0 && index < stages.size() ? stages.get(index) : null;
   }

   private static NeedsOfNature.PeakLiquidPlan preparePeakLiquidPlan(ServerWorld world, List<UUID> actorUuids, List<String> actorKeys, Identifier animationId) {
      if (world != null && actorUuids != null && actorKeys != null && animationId != null && !actorUuids.isEmpty() && actorUuids.size() == actorKeys.size()) {
         NonLiquidRoles.LiquidRoles roles = AnimationGameplayMetadata.liquidRoles(animationId);
         if (roles != null && !roles.receiverKeys().isEmpty()) {
            Set<NonLiquidRoles.InjectorRole> suppliedInjectorRoles = EnumSet.noneOf(NonLiquidRoles.InjectorRole.class);
            double liquidGainMultiplier = resolveLiquidGainMultiplier(animationId);

            for (int i = 0; i < actorUuids.size(); i++) {
               NonLiquidRoles.InjectorRole role = roles.injectorRoles().get(actorKeys.get(i));
               if (role != null && role != NonLiquidRoles.InjectorRole.NONE) {
                  Entity entity = world.getEntity(actorUuids.get(i));
                  if (entity != null && !(entity instanceof ServerPlayerEntity player && NonTrinketsIntegration.hasMaleRoleAccessory(player))) {
                     Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());
                     int suppliedMl = applyLiquidGainMultiplier(resolveEntityLiquidGainMl(typeId), liquidGainMultiplier);
                     if (suppliedMl > 0) {
                        suppliedInjectorRoles.add(role);
                     }
                  }
               }
            }

            Map<UUID, ServerPlayerEntity> receivers = new LinkedHashMap<>();
            Map<UUID, NeedsOfNature.LiquidGainProfile> gains = new LinkedHashMap<>();

            for (int i = 0; i < actorUuids.size(); i++) {
               if (roles.receiverKeys().contains(actorKeys.get(i))
                  && world.getEntity(actorUuids.get(i)) instanceof ServerPlayerEntity player
                  && player.isAlive()
                  && !player.isRemoved()) {
                  receivers.put(player.getUuid(), player);
                  NeedsOfNature.LiquidGainProfile normalGain = resolveLiquidGainProfile(
                     world, actorUuids, actorKeys, animationId, NonGenderSystem.getLiquidTankType(player)
                  );
                  normalGain = applyApiLiquidGainModifier(player, applyAccessoryLiquidGainMultiplier(player, normalGain));
                  if (normalGain.amount() > 0) {
                     gains.put(player.getUuid(), normalGain);
                  }
               }
            }

            Map<UUID, List<NeedsOfNature.PregnancyDonor>> accessoryDonors = new LinkedHashMap<>();

            for (int i = 0; i < actorUuids.size(); i++) {
               NonLiquidRoles.InjectorRole role = roles.injectorRoles().get(actorKeys.get(i));
               if ((role == NonLiquidRoles.InjectorRole.V || role == NonLiquidRoles.InjectorRole.A || role == NonLiquidRoles.InjectorRole.M)
                  && world.getEntity(actorUuids.get(i)) instanceof ServerPlayerEntity injector) {
                  NonTrinketsIntegration.StoredInjectorLiquid stored = NonTrinketsIntegration.peekStoredInjectorLiquid(injector);
                  if (stored != null && stored.entityTypeId() != null && stored.amountMl() > 0) {
                     List<ServerPlayerEntity> compatibleReceivers = role == NonLiquidRoles.InjectorRole.M
                        ? List.copyOf(receivers.values())
                        : receivers.values()
                           .stream()
                           .filter(receiverx -> isLiquidInjectorAccepted(role, NonGenderSystem.getLiquidTankType(receiverx)))
                           .toList();
                     if (!compatibleReceivers.isEmpty()) {
                        NonTrinketsIntegration.StoredInjectorLiquid consumed = NonTrinketsIntegration.consumeStoredInjectorLiquid(injector);
                        if (consumed != null && consumed.entityTypeId() != null && consumed.amountMl() > 0) {
                           suppliedInjectorRoles.add(role);
                           if (role != NonLiquidRoles.InjectorRole.M) {
                              int baseShare = consumed.amountMl() / compatibleReceivers.size();
                              int remainder = consumed.amountMl() % compatibleReceivers.size();
                              NeedsOfNature.PregnancyDonor donor = role == NonLiquidRoles.InjectorRole.V
                                 ? new NeedsOfNature.PregnancyDonor(consumed.entityTypeId(), null)
                                 : null;

                              for (int receiverIndex = 0; receiverIndex < compatibleReceivers.size(); receiverIndex++) {
                                 ServerPlayerEntity receiver = compatibleReceivers.get(receiverIndex);
                                 int share = baseShare + (receiverIndex < remainder ? 1 : 0);
                                 if (share > 0) {
                                    NeedsOfNature.LiquidGainProfile fixedGain = new NeedsOfNature.LiquidGainProfile(
                                       share,
                                       0,
                                       LiquidHolder.LiquidComposition.ENTITY,
                                       consumed.entityTypeId(),
                                       Map.of(consumed.entityTypeId(), share),
                                       null,
                                       donor == null ? List.of() : List.of(donor)
                                    );
                                    gains.merge(receiver.getUuid(), fixedGain, NeedsOfNature::mergeLiquidGainProfiles);
                                    if (donor != null) {
                                       accessoryDonors.computeIfAbsent(receiver.getUuid(), ignored -> new ArrayList<>()).add(donor);
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            return new NeedsOfNature.PeakLiquidPlan(gains, accessoryDonors, suppliedInjectorRoles);
         } else {
            return new NeedsOfNature.PeakLiquidPlan(Map.of(), Map.of(), Set.of());
         }
      } else {
         return new NeedsOfNature.PeakLiquidPlan(Map.of(), Map.of(), Set.of());
      }
   }

   private static NeedsOfNature.LiquidGainProfile mergeLiquidGainProfiles(NeedsOfNature.LiquidGainProfile first, NeedsOfNature.LiquidGainProfile second) {
      if (first == null || first.amount() <= 0) {
         return second;
      }

      if (second != null && second.amount() > 0) {
         Map<Identifier, Integer> entityAmounts = new LinkedHashMap<>();
         first.entityAmounts().forEach((id, amount) -> {
            if (id != null && amount != null && amount > 0) {
               entityAmounts.merge(id, amount, Integer::sum);
            }
         });
         second.entityAmounts().forEach((id, amount) -> {
            if (id != null && amount != null && amount > 0) {
               entityAmounts.merge(id, amount, Integer::sum);
            }
         });
         Identifier entityTypeId = entityAmounts.size() == 1 ? entityAmounts.keySet().iterator().next() : null;
         LiquidHolder.LiquidComposition composition = entityTypeId == null ? LiquidHolder.LiquidComposition.MIXED : LiquidHolder.LiquidComposition.ENTITY;
         NeedsOfNature.PregnancyVariantData variantData = null;
         if (entityTypeId != null) {
            if (entityTypeId.equals(second.entityTypeId()) && second.donorVariantData() != null) {
               variantData = second.donorVariantData();
            } else if (entityTypeId.equals(first.entityTypeId())) {
               variantData = first.donorVariantData();
            }
         }

         List<NeedsOfNature.PregnancyDonor> recentDonors = new ArrayList<>(first.recentDonors());
         recentDonors.addAll(second.recentDonors());
         return new NeedsOfNature.LiquidGainProfile(
            first.amount() + second.amount(),
            first.xpEligibleAmount() + second.xpEligibleAmount(),
            composition,
            entityTypeId,
            Map.copyOf(entityAmounts),
            variantData,
            recentDonors
         );
      } else {
         return first;
      }
   }

   private static int startPeakLiquidProgression(
      ServerWorld world,
      UUID instanceId,
      Identifier animationId,
      NeedsOfNature.PeakLiquidPlan plan,
      @Nullable AnimationStageInfo currentStage,
      @Nullable Integer stageNumber,
      long stageStartTick
   ) {
      if (world != null && instanceId != null && animationId != null) {
         PEAK_XP_ELIGIBLE_ML.remove(instanceId);
         int plannedAmount = plan == null ? 0 : plan.maxAmount();
         if (plannedAmount <= 0) {
            PEAK_LIQUID_PROGRESS.remove(instanceId);
            return 0;
         }

         Identifier stageClipId = resolvePeakStageClipId(animationId, currentStage, stageNumber);
         List<Integer> cueOffsets = resolveReactiveImpactCueOffsets(stageClipId);
         double cycleTicks = resolveReactiveImpactCycleTicks(currentStage, stageClipId);
         cueOffsets = resolveWarpedReactiveImpactCueOffsets(currentStage, cycleTicks, cueOffsets);
         double stageSpeed = resolveStageSpeed(currentStage);
         if (!cueOffsets.isEmpty() && !(cycleTicks <= 0.0)) {
            long stageDurationTicks = resolveLoopAdvanceIntervalTicks(animationId, stageNumber, currentStage);
            if (stageDurationTicks < 0L) {
               PEAK_LIQUID_PROGRESS.put(
                  instanceId,
                  NeedsOfNature.PeakLiquidProgressState.infinite(world.getRegistryKey(), plan, cycleTicks, List.copyOf(cueOffsets), stageStartTick, stageSpeed)
               );
               return 0;
            } else {
               NeedsOfNature.FinitePulseSchedule schedule = buildFinitePulseSchedule(
                  stageStartTick, stageDurationTicks, cycleTicks, cueOffsets, plannedAmount, stageSpeed
               );
               if (schedule != null && !schedule.ticks().isEmpty()) {
                  PEAK_LIQUID_PROGRESS.put(
                     instanceId, NeedsOfNature.PeakLiquidProgressState.finite(world.getRegistryKey(), plan, schedule.ticks(), schedule.amounts())
                  );
                  return 0;
               } else {
                  PEAK_LIQUID_PROGRESS.remove(instanceId);
                  NeedsOfNature.LiquidPulseResult result = grantLiquidPulseToReceivers(world, plan, 0, plannedAmount, new HashSet<>());
                  recordPeakLiquidPulse(instanceId, result);
                  return result.addedMl();
               }
            }
         } else {
            PEAK_LIQUID_PROGRESS.remove(instanceId);
            NeedsOfNature.LiquidPulseResult result = grantLiquidPulseToReceivers(world, plan, 0, plannedAmount, new HashSet<>());
            recordPeakLiquidPulse(instanceId, result);
            return result.addedMl();
         }
      } else {
         return 0;
      }
   }

   private static Identifier resolvePeakStageClipId(Identifier animationId, @Nullable AnimationStageInfo currentStage, @Nullable Integer stageNumber) {
      if (currentStage != null) {
         Identifier playback = currentStage.effectiveAnimationId();
         if (playback != null) {
            return playback;
         }

         if (currentStage.animationId() != null) {
            return currentStage.animationId();
         }
      }

      return resolveStageId(animationId, stageNumber);
   }

   private static List<Integer> resolveReactiveImpactCueOffsets(@Nullable Identifier stageClipId) {
      AnimationCueIndex.CueProfile profile = AnimationCueIndex.getProfile(stageClipId);
      if (profile == null) {
         return List.of();
      }

      List<Integer> cueTicks = profile.cueTicks("reactiveimpact");
      if (cueTicks.isEmpty()) {
         return List.of();
      }

      List<Integer> out = new ArrayList<>();

      for (Integer tick : cueTicks) {
         if (tick != null && tick >= 0) {
            out.add(tick);
         }
      }

      if (out.isEmpty()) {
         return List.of();
      }

      out.sort(Integer::compareTo);
      return List.copyOf(out);
   }

   private static double resolveReactiveImpactCycleTicks(@Nullable AnimationStageInfo currentStage, @Nullable Identifier stageClipId) {
      AnimationCueIndex.CueProfile profile = AnimationCueIndex.getProfile(stageClipId);
      if (profile != null && profile.cycleTicks() != null) {
         long cueCycleTicks = Math.max(0L, profile.cycleTicks().intValue());
         if (cueCycleTicks > 0L) {
            return cueCycleTicks;
         }
      }

      if (currentStage != null) {
         double ticks = exactCycleTicks(currentStage.animationId(), currentStage);
         if (ticks > 0.0) {
            return ticks;
         }
      }

      return 0.0;
   }

   private static List<Integer> resolveWarpedReactiveImpactCueOffsets(@Nullable AnimationStageInfo currentStage, double cycleTicks, List<Integer> cueOffsets) {
      if (currentStage != null && cueOffsets != null && !cueOffsets.isEmpty()) {
         if (currentStage.loop() && !(cycleTicks <= 0.0)) {
            double offsetTicks = StageTimeWarp.offsetSecondsToTicks(currentStage.cycleMidpointOffsetSeconds());
            if (!StageTimeWarp.hasWarp(cycleTicks, offsetTicks)) {
               return cueOffsets;
            }

            Set<Integer> warped = new TreeSet<>();

            for (Integer cueOffset : cueOffsets) {
               if (cueOffset != null && cueOffset >= 0) {
                  double shifted = StageTimeWarp.authoredToWarpedCycleTicks(cueOffset.intValue(), cycleTicks, offsetTicks);
                  warped.add(Math.max(0, (int)Math.round(shifted)));
               }
            }

            return warped.isEmpty() ? List.of() : List.copyOf(warped);
         } else {
            return cueOffsets;
         }
      } else {
         return cueOffsets;
      }
   }

   private static double resolveStageSpeed(@Nullable AnimationStageInfo currentStage) {
      if (currentStage == null) {
         return 1.0;
      }

      double speed = currentStage.speed();
      return Double.isFinite(speed) && !(speed <= 0.0) ? clampAnimationSpeed(speed) : 1.0;
   }

   private static double clampAnimationSpeed(double speed) {
      return Double.isFinite(speed) && !(speed <= 0.0) ? Math.max(0.1, Math.min(speed, 4.0)) : 1.0;
   }

   private static long elapsedWorldTicksFromAnimationTicks(double animationTicks, double speed) {
      if (Double.isFinite(animationTicks) && !(animationTicks <= 0.0)) {
         double safeSpeed = Double.isFinite(speed) && !(speed <= 0.0) ? speed : 1.0;
         double elapsed = animationTicks / safeSpeed;
         return Double.isFinite(elapsed) && !(elapsed <= 0.0) ? Math.max(0L, (long)Math.ceil(elapsed - 1.0E-9)) : 0L;
      } else {
         return 0L;
      }
   }

   @Nullable
   private static NeedsOfNature.FinitePulseSchedule buildFinitePulseSchedule(
      long stageStartTick, long stageDurationTicks, double cycleTicks, List<Integer> cueOffsets, int totalAmountPerReceiver, double stageSpeed
   ) {
      if (stageDurationTicks > 0L && !(cycleTicks <= 0.0) && totalAmountPerReceiver > 0) {
         if (cueOffsets != null && !cueOffsets.isEmpty()) {
            long stageEndTick = stageStartTick + stageDurationTicks;
            List<Long> pulseTicks = new ArrayList<>();
            List<Integer> sortedOffsets = new ArrayList<>();

            for (Integer rawOffset : cueOffsets) {
               if (rawOffset != null) {
                  sortedOffsets.add(Math.max(0, rawOffset));
               }
            }

            if (sortedOffsets.isEmpty()) {
               return null;
            }

            sortedOffsets.sort(Integer::compareTo);
            int minOffset = sortedOffsets.getFirst();

            for (long cycleIndex = 0L; cycleIndex < 100000L; cycleIndex++) {
               double firstAnimTick = cycleIndex * cycleTicks + minOffset;
               long firstPulseTick = stageStartTick + elapsedWorldTicksFromAnimationTicks(firstAnimTick, stageSpeed);
               if (firstPulseTick >= stageEndTick) {
                  break;
               }

               for (int offset : sortedOffsets) {
                  double cueAnimTick = cycleIndex * cycleTicks + offset;
                  long pulseTick = stageStartTick + elapsedWorldTicksFromAnimationTicks(cueAnimTick, stageSpeed);
                  if (pulseTick < stageEndTick) {
                     pulseTicks.add(pulseTick);
                  }
               }
            }

            if (pulseTicks.isEmpty()) {
               return null;
            }

            pulseTicks.sort(Long::compareTo);
            int pulseCount = pulseTicks.size();
            int base = totalAmountPerReceiver / pulseCount;
            int remainder = totalAmountPerReceiver % pulseCount;
            List<Long> scheduledTicks = new ArrayList<>(pulseCount);
            List<Integer> scheduledAmounts = new ArrayList<>(pulseCount);

            for (int i = 0; i < pulseCount; i++) {
               int amount = base + (i < remainder ? 1 : 0);
               if (amount > 0) {
                  scheduledTicks.add(pulseTicks.get(i));
                  scheduledAmounts.add(amount);
               }
            }

            return scheduledTicks.isEmpty() ? null : new NeedsOfNature.FinitePulseSchedule(List.copyOf(scheduledTicks), List.copyOf(scheduledAmounts));
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static NeedsOfNature.LiquidPulseResult grantLiquidPulseToReceivers(
      ServerWorld world, NeedsOfNature.PeakLiquidPlan plan, int previousBaseAmount, int nextBaseAmount, Set<UUID> donorHistoryRecordedReceivers
   ) {
      if (world != null && plan != null && !plan.gainsByReceiver().isEmpty()) {
         int planAmount = plan.maxAmount();
         int previous = Math.max(0, Math.min(planAmount, previousBaseAmount));
         int next = Math.max(previous, Math.min(planAmount, nextBaseAmount));
         if (planAmount > 0 && next > previous) {
            int totalAdded = 0;
            int totalXpEligibleAdded = 0;

            for (Entry<UUID, NeedsOfNature.LiquidGainProfile> entry : plan.gainsByReceiver().entrySet()) {
               if (world.getEntity(entry.getKey()) instanceof ServerPlayerEntity player
                  && player.isAlive()
                  && !player.isRemoved()
                  && player instanceof LiquidHolder holder) {
                  NeedsOfNature.LiquidGainProfile receiverGain = entry.getValue();
                  if (receiverGain != null && receiverGain.amount() > 0) {
                     int previousTarget = (int)Math.round((double)receiverGain.amount() * previous / planAmount);
                     int nextTarget = (int)Math.round((double)receiverGain.amount() * next / planAmount);
                     int pulseAmount = Math.max(0, nextTarget - previousTarget);
                     if (pulseAmount > 0) {
                        NeedsOfNature.LiquidGainProfile pulseGain = scaleLiquidGainProfile(receiverGain, (double)pulseAmount / receiverGain.amount());
                        int added = addLiquidWithComposition(holder, pulseGain);
                        if (added > 0) {
                           if (!pulseGain.recentDonors().isEmpty() && donorHistoryRecordedReceivers.add(player.getUuid())) {
                              recordRecentLiquidDonors(holder, pulseGain.recentDonors());
                           }

                           totalAdded += added;
                           int previousXpTarget = (int)Math.round((double)receiverGain.xpEligibleAmount() * previous / planAmount);
                           int nextXpTarget = (int)Math.round((double)receiverGain.xpEligibleAmount() * next / planAmount);
                           int pulseXpEligible = Math.max(0, nextXpTarget - previousXpTarget);
                           if (pulseXpEligible > 0) {
                              totalXpEligibleAdded += Math.min(added, (int)Math.round((double)pulseXpEligible * added / pulseAmount));
                           }

                           Map<Identifier, Integer> scaledAmounts = scaleEntityAmounts(pulseGain.entityAmounts(), added);
                           NonStats.addLiquidGained(player, added, scaledAmounts);
                           trackFieldResearch(world, player, scaledAmounts);
                           syncLiquidState(player);
                        }
                     }
                  }
               }
            }

            return new NeedsOfNature.LiquidPulseResult(totalAdded, totalXpEligibleAdded);
         } else {
            return NeedsOfNature.LiquidPulseResult.EMPTY;
         }
      } else {
         return NeedsOfNature.LiquidPulseResult.EMPTY;
      }
   }

   private static void recordRecentLiquidDonors(LiquidHolder holder, List<NeedsOfNature.PregnancyDonor> donors) {
      if (holder != null && donors != null && !donors.isEmpty()) {
         for (NeedsOfNature.PregnancyDonor donor : donors) {
            if (donor != null) {
               NonPregnancyConceptionSystem.recordRecentDonor(holder, donor.entityTypeId(), donor.variantData());
            }
         }
      }
   }

   private static void recordPeakLiquidPulse(UUID instanceId, NeedsOfNature.LiquidPulseResult result) {
      if (instanceId != null && result != null && result.xpEligibleAddedMl() > 0) {
         PEAK_XP_ELIGIBLE_ML.merge(instanceId, result.xpEligibleAddedMl(), Integer::sum);
      }
   }

   private static void addActualAddedMlToPeakState(UUID instanceId, int addedMl) {
      if (instanceId != null && addedMl > 0) {
         NeedsOfNature.PeakState state = PEAK_STATES.get(instanceId);
         if (state != null) {
            PEAK_STATES.put(
               instanceId,
               new NeedsOfNature.PeakState(
                  state.animationId(),
                  state.peakStage(),
                  state.peaked(),
                  state.lastStage(),
                  state.hasPlayer(),
                  state.liquidGranted(),
                  state.actualAddedMl() + addedMl
               )
            );
         }
      }
   }

   private static void trackPeakStats(ServerWorld world, List<UUID> actorUuids, boolean manualPeak) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         List<ServerPlayerEntity> players = collectPlayerActors(world, actorUuids);
         if (!players.isEmpty()) {
            Set<Identifier> nonPlayerEntityTypes = collectNonPlayerEntityTypes(world, actorUuids);

            for (ServerPlayerEntity player : players) {
               NonStats.incrementPeakedAnimations(player, nonPlayerEntityTypes);
               NonAdvancementHooks.grantNaturalCuriosity(player);
               if (manualPeak) {
                  NonStats.incrementManualPeak(player);
               }
            }
         }
      }
   }

   static List<ServerPlayerEntity> collectPlayerActors(ServerWorld world, List<UUID> actorUuids) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         List<ServerPlayerEntity> players = new ArrayList<>();

         for (UUID uuid : actorUuids) {
            if (world.getEntity(uuid) instanceof ServerPlayerEntity player) {
               players.add(player);
            }
         }

         return players;
      } else {
         return List.of();
      }
   }

   static List<ServerPlayerEntity> collectPlayerReceiverActors(ServerWorld world, List<UUID> actorUuids, List<String> actorKeys, Identifier animationId) {
      if (world != null && actorUuids != null && actorKeys != null && animationId != null) {
         if (!actorUuids.isEmpty() && actorUuids.size() == actorKeys.size()) {
            NonLiquidRoles.LiquidRoles roles = AnimationGameplayMetadata.liquidRoles(animationId);
            if (roles != null && !roles.receiverKeys().isEmpty()) {
               List<ServerPlayerEntity> players = new ArrayList<>();

               for (int i = 0; i < actorUuids.size(); i++) {
                  if (roles.receiverKeys().contains(actorKeys.get(i)) && world.getEntity(actorUuids.get(i)) instanceof ServerPlayerEntity player) {
                     players.add(player);
                  }
               }

               return players;
            } else {
               return List.of();
            }
         } else {
            return List.of();
         }
      } else {
         return List.of();
      }
   }

   private static List<ServerPlayerEntity> collectPlayerActors(List<? extends Entity> actors) {
      if (actors != null && !actors.isEmpty()) {
         List<ServerPlayerEntity> players = new ArrayList<>();

         for (Entity actor : actors) {
            if (actor instanceof ServerPlayerEntity player) {
               players.add(player);
            }
         }

         return players;
      } else {
         return List.of();
      }
   }

   private static Set<Identifier> collectNonPlayerEntityTypes(ServerWorld world, List<UUID> actorUuids) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         Set<Identifier> out = new LinkedHashSet<>();

         for (UUID uuid : actorUuids) {
            Entity entity = world.getEntity(uuid);
            if (entity != null && !(entity instanceof ServerPlayerEntity)) {
               Identifier entityId = Registries.ENTITY_TYPE.getId(entity.getType());
               out.add(entityId);
            }
         }

         return out;
      } else {
         return Set.of();
      }
   }

   private static void handleFillBottleStop(ServerWorld world, NeedsOfNature.FillBottleState state, boolean peaked) {
      if (world != null && state != null) {
         ServerPlayerEntity player = findPlayerOnWorldServer(world, state.playerUuid());
         if (player != null && player.getEntityWorld() == world) {
            if (player instanceof LiquidHolder holder) {
               if (!peaked) {
                  if (state.reservedBottle()) {
                     NonItemSystem.refundGlassBottle(player);
                  }
               } else {
                  int stored = holder.getLiquidStored();
                  updateFilledEffect(player, holder);
                  int requiredMl = getFillBottleRequiredMl(holder);
                  if (stored >= requiredMl && hasFilledStageOneOrHigher(player)) {
                     ItemStack resultBottle = NonItemSystem.createBottleStackForTank(holder);
                     holder.setLiquidStored(stored - requiredMl);
                     syncLiquidState(player);
                     NonItemSystem.giveItemOrDrop(player, resultBottle);
                     NonStats.incrementFillBottle(player);
                     NonAdvancementHooks.checkCollectorsHabit(player);
                  } else {
                     if (state.reservedBottle()) {
                        NonItemSystem.refundGlassBottle(player);
                     }
                  }
               }
            } else {
               if (state.reservedBottle()) {
                  NonItemSystem.refundGlassBottle(player);
               }
            }
         }
      }
   }

   private static int getFillBottleRequiredMl(LiquidHolder holder) {
      return holder == null ? Integer.MAX_VALUE : getFilledStageOneRequiredMl(holder.getLiquidCapacity());
   }

   private static int getFilledStageOneRequiredMl(int capacity) {
      return capacity <= 0 ? Integer.MAX_VALUE : Math.max(1, (int)Math.ceil(capacity * 0.3F));
   }

   private static boolean hasFilledStageOneOrHigher(ServerPlayerEntity player) {
      if (player == null) {
         return false;
      }

      RegistryEntry<StatusEffect> entry = Registries.STATUS_EFFECT.getEntry(NonStatusEffects.FILLED);
      StatusEffectInstance effect = player.getStatusEffect(entry);
      return effect != null && effect.getAmplifier() >= 0;
   }

   private static int addLiquidWithComposition(LiquidHolder holder, NeedsOfNature.LiquidGainProfile gain) {
      if (holder != null && gain != null && gain.amount() > 0) {
         int stored = holder.getLiquidStored();
         int capacity = holder.getLiquidCapacity();
         int room = Math.max(0, capacity - stored);
         int added = Math.min(room, gain.amount());
         if (added == 0) {
            return 0;
         }

         if (stored <= 0) {
            if (gain.composition() == LiquidHolder.LiquidComposition.ENTITY && gain.entityTypeId() != null) {
               holder.setLiquidCompositionEntity(gain.entityTypeId());
               holder.setLiquidDonorVariantData(gain.donorVariantData());
            } else {
               holder.setLiquidCompositionMixed();
            }
         } else {
            LiquidHolder.LiquidComposition existing = holder.getLiquidComposition();
            if (existing == LiquidHolder.LiquidComposition.ENTITY) {
               Identifier existingType = holder.getLiquidEntityTypeId();
               boolean sameEntity = gain.composition() == LiquidHolder.LiquidComposition.ENTITY
                  && gain.entityTypeId() != null
                  && gain.entityTypeId().equals(existingType);
               if (!sameEntity) {
                  holder.setLiquidCompositionMixed();
               } else {
                  holder.setLiquidDonorVariantData(gain.donorVariantData());
               }
            } else if (existing != LiquidHolder.LiquidComposition.MIXED) {
               holder.setLiquidCompositionMixed();
            }
         }

         holder.setLiquidStored(stored + added);
         return added;
      } else {
         return 0;
      }
   }

   private static Map<Identifier, Integer> scaleEntityAmounts(Map<Identifier, Integer> source, int targetTotal) {
      if (source != null && !source.isEmpty() && targetTotal > 0) {
         int sourceTotal = 0;

         for (Integer value : source.values()) {
            if (value != null && value > 0) {
               sourceTotal += value;
            }
         }

         if (sourceTotal <= 0) {
            return Map.of();
         }

         if (targetTotal >= sourceTotal) {
            return Map.copyOf(source);
         }

         Map<Identifier, Integer> scaled = new LinkedHashMap<>();
         List<Entry<Identifier, Double>> remainders = new ArrayList<>();
         int assigned = 0;

         for (Entry<Identifier, Integer> entry : source.entrySet()) {
            if (entry.getKey() != null) {
               int amount = entry.getValue() == null ? 0 : entry.getValue();
               if (amount > 0) {
                  double exact = (double)amount * targetTotal / sourceTotal;
                  int floor = (int)Math.floor(exact);
                  if (floor > 0) {
                     scaled.put(entry.getKey(), floor);
                     assigned += floor;
                  }

                  remainders.add(Map.entry(entry.getKey(), exact - floor));
               }
            }
         }

         int remaining = targetTotal - assigned;
         if (remaining <= 0) {
            return Map.copyOf(scaled);
         }

         remainders.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

         for (int i = 0; i < remainders.size() && remaining > 0; i++) {
            Identifier entityId = remainders.get(i).getKey();
            scaled.merge(entityId, 1, Integer::sum);
            remaining--;
         }

         return Map.copyOf(scaled);
      } else {
         return Map.of();
      }
   }

   public static NonConfig getConfig() {
      NonConfig remote = REMOTE_EFFECTIVE_CONFIG;
      return remote == null ? CONFIG : remote;
   }

   public static NonConfig getLocalConfig() {
      return CONFIG;
   }

   public static synchronized void applyRemoteHostRules(HostConfigSyncData rules) {
      REMOTE_HOST_RULES = rules;
      rebuildRemoteEffectiveConfig();
   }

   public static synchronized void clearRemoteHostRules() {
      REMOTE_HOST_RULES = null;
      REMOTE_EFFECTIVE_CONFIG = null;
   }

   static synchronized void onConfigSaved(NonConfig config) {
      if (config == CONFIG && REMOTE_HOST_RULES != null) {
         rebuildRemoteEffectiveConfig();
      }
   }

   private static void rebuildRemoteEffectiveConfig() {
      if (CONFIG != null && REMOTE_HOST_RULES != null) {
         NonConfig effective = CONFIG.copy();
         REMOTE_HOST_RULES.applyTo(effective);
         REMOTE_EFFECTIVE_CONFIG = effective;
      } else {
         REMOTE_EFFECTIVE_CONFIG = null;
      }
   }

   private static void clearServerRuntimeState() {
      SCAN_BUDGETS.clear();
      PENDING_POST_DEATH_STATE_SYNCS.clear();
      PEAK_STATES.clear();
      PENDING_GATHER_PAIRS.clear();
      NON_PENDING_GATHER_ACTORS.clear();
      DEFEATED_QUEUED_INSTANCES.clear();
      LAST_DEFEATED_COOLDOWN_UNTIL_BY_PLAYER.clear();
      POST_ESCAPE_DAMAGE_INVULNERABLE_UNTIL_BY_PLAYER.clear();
      PENDING_ATTACK_ESCAPE_RESOLUTIONS.clear();
      PEAK_LIQUID_PROGRESS.clear();
      PEAK_XP_ELIGIBLE_ML.clear();
      FILL_BOTTLE_STATES.clear();
      BREEDING_PENDING_BY_PAIR.clear();
      BREEDING_PENDING_BY_REQUEST.clear();
      BREEDING_ACTIVE_BY_INSTANCE.clear();
      BREEDING_ACTIVE_INSTANCE_BY_PAIR.clear();
      PREGNANCY_HATCH_BY_INSTANCE.clear();
      PREGNANCY_ACTIVE_INSTANCE_BY_PLAYER.clear();
      PREGNANCY_QUEUED_PLAYERS.clear();
      PREGNANCY_START_RETRY_TICKS.clear();
      PREGNANCY_CUE_RUNTIME_BY_INSTANCE.clear();
      REFLECTION_FAILURE_LOGS.clear();
      RECEIVER_DRIP.clear();
      PENDING_M_INJECTOR_REWARDS.clear();
      NonGatherSystem.clearRuntimeState();
      NonLiquidSystem.clearRuntimeState();
      NonMessSystem.clearRuntimeState();
      NonAttackSystem.clearRuntimeState();
      NonPlayerAttackSystem.clearRuntimeState();
      NonDestroyedSkinSystem.clearRuntimeState();
      NonAdvancementHooks.clearRuntimeState();
      NonTrinketsIntegration.clearRuntimeState();
      NonAccessoryShedSystem.clearAll();
      NonStatusEffects.clearRuntimeState();
   }

   public static boolean canMobAttackPlayer(ServerPlayerEntity player) {
      return NonAttackSystem.canMobAttackPlayer(player);
   }

   public static boolean shouldBypassVanillaPvpForDefeatedFinish(ServerPlayerEntity victim, PlayerEntity attacker) {
      return NonPlayerAttackSystem.shouldBypassVanillaPvpCheck(victim, attacker);
   }

   public static AnimationStartContext buildModeContext(boolean attack) {
      return AnimationStartContext.empty().withSessionData(attack ? NonAnimationSessionData.attack() : NonAnimationSessionData.voluntary());
   }

   @Nullable
   private static NonAnimationSessionData getNonSessionData(@Nullable AnimationStartContext context) {
      return context != null && context.sessionData() instanceof NonAnimationSessionData data ? data : null;
   }

   private static boolean isAttackSnapshot(@Nullable ServerAnimationController.ActiveInstanceSnapshot snapshot) {
      NonAnimationSessionData data = snapshot == null ? null : getNonSessionData(snapshot.startContext());
      return data != null && data.isAttack();
   }

   private static boolean isPregnancySnapshot(@Nullable ServerAnimationController.ActiveInstanceSnapshot snapshot) {
      NonAnimationSessionData data = snapshot == null ? null : getNonSessionData(snapshot.startContext());
      return data != null && data.isPregnancy();
   }

   public static void setAnimationStartProtection(ServerPlayerEntity player, int ticks) {
      NonAttackSystem.setAnimationStartProtection(player, ticks);
   }

   public static boolean hasAnimationStartProtection(ServerWorld world, ServerPlayerEntity player) {
      return NonAttackSystem.hasAnimationStartProtection(world, player);
   }

   public static boolean isActorPendingOrActive(ServerWorld world, UUID actorUuid) {
      if (world == null || actorUuid == null) {
         return false;
      } else {
         return isNoNGatheringActor(world, actorUuid) ? true : AnimationService.isActorPendingOrActive(world, actorUuid);
      }
   }

   public static boolean isNoNGatheringActor(ServerWorld world, UUID actorUuid) {
      if (world != null && actorUuid != null) {
         NeedsOfNature.NoNGatherActorState nonGatherState = NON_PENDING_GATHER_ACTORS.get(actorUuid);
         if (nonGatherState != null) {
            if (world.getRegistryKey().equals(nonGatherState.worldKey())
               && nonGatherState.expiresTick() > world.getTime()
               && NonGatherSystem.isActorGathering(actorUuid)) {
               return true;
            }

            NON_PENDING_GATHER_ACTORS.remove(actorUuid, nonGatherState);
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean isMobAttackFailsafeActive(ServerWorld world, UUID mobUuid) {
      return NonAttackSystem.isMobAttackFailsafeActive(world, mobUuid);
   }

   public static List<String> getActivePlayerCooldownDebugLines(ServerWorld world, ServerPlayerEntity player) {
      List<String> lines = new ArrayList<>();
      if (world != null && player != null) {
         long nowTick = getGlobalTick(world);
         Long lastDefeatedUntil = LAST_DEFEATED_COOLDOWN_UNTIL_BY_PLAYER.get(player.getUuid());
         if (lastDefeatedUntil != null) {
            long remaining = lastDefeatedUntil - nowTick;
            if (remaining > 0L) {
               lines.add("last defeated: " + remaining + "t/" + formatCooldownSeconds(remaining) + "s");
            } else {
               LAST_DEFEATED_COOLDOWN_UNTIL_BY_PLAYER.remove(player.getUuid(), lastDefeatedUntil);
            }
         }

         Long postEscapeDamageInvulnerableUntil = POST_ESCAPE_DAMAGE_INVULNERABLE_UNTIL_BY_PLAYER.get(player.getUuid());
         if (postEscapeDamageInvulnerableUntil != null) {
            long remaining = postEscapeDamageInvulnerableUntil - nowTick;
            if (remaining > 0L) {
               lines.add("post-escape damage invulnerability: " + remaining + "t/" + formatCooldownSeconds(remaining) + "s");
            } else {
               POST_ESCAPE_DAMAGE_INVULNERABLE_UNTIL_BY_PLAYER.remove(player.getUuid(), postEscapeDamageInvulnerableUntil);
            }
         }

         if (AnimationService.isActorPendingOrActive(world, player.getUuid())) {
            lines.add("animation active/pending");
         }

         if (PREGNANCY_QUEUED_PLAYERS.contains(player.getUuid())) {
            lines.add("pregnancy queued");
         }

         Long pregnancyRetry = PREGNANCY_START_RETRY_TICKS.get(player.getUuid());
         if (pregnancyRetry != null) {
            long remaining = pregnancyRetry - nowTick;
            if (remaining > 0L) {
               lines.add("pregnancy retry: " + remaining + "t/" + formatCooldownSeconds(remaining) + "s");
            }
         }

         NeedsOfNature.PendingMInjectorReward pendingReward = PENDING_M_INJECTOR_REWARDS.get(player.getUuid());
         if (pendingReward != null) {
            lines.add(
               pendingReward.freeSinceTick() < 0L
                  ? "M reward pending: waiting for animation end"
                  : "M reward pending: free for " + Math.max(0L, nowTick - pendingReward.freeSinceTick()) + "t"
            );
         }

         return lines;
      } else {
         return lines;
      }
   }

   private static String formatCooldownSeconds(long ticks) {
      return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
   }

   static int clearAttackOutcomeFailsafeEntries() {
      return NonAttackSystem.clearAttackOutcomeFailsafeEntries();
   }

   public static String getMobAttackFailsafeDebug(ServerWorld world, UUID mobUuid) {
      return NonAttackSystem.getMobAttackFailsafeDebug(world, mobUuid);
   }

   public static String formatTicksForDebug(long ticks) {
      long safeTicks = Math.max(0L, ticks);
      double seconds = safeTicks / 20.0;
      return safeTicks + "t/" + String.format(Locale.ROOT, "%.1fs", seconds);
   }

   public static boolean handleAnimationBreedingAttempt(ServerWorld world, AnimalEntity first, AnimalEntity second) {
      if (world != null && first != null && second != null) {
         NonConfig config = getConfig();
         if (config == null || !config.useAnimationBreeding()) {
            return false;
         }

         if (first == second) {
            return true;
         }

         if (first.isAlive() && second.isAlive() && !first.isRemoved() && !second.isRemoved()) {
            NeedsOfNature.BreedingPairKey pairKey = breedingPairKey(first.getUuid(), second.getUuid());
            if (pairKey == null) {
               return true;
            } else if (BREEDING_PENDING_BY_PAIR.containsKey(pairKey)) {
               return true;
            } else if (BREEDING_ACTIVE_INSTANCE_BY_PAIR.containsKey(pairKey)) {
               return true;
            } else if (config.requireMaleFemaleForBreeding() && !isMaleFemalePair(first, second)) {
               return true;
            } else {
               List<Entity> actors = new ArrayList<>(2);
               actors.add(first);
               actors.add(second);
               actors.sort(Comparator.comparingInt(Entity::getId));
               AnimationService.MatchedAnimation match = NonAnimationMatcher.findAutomatic(world, actors, Set.of(), null, false, null, Set.of());
               if (match == null) {
                  return false;
               } else {
                  List<String> actorKeys = match.actorKeys();
                  if (actorKeys != null && actorKeys.size() == actors.size()) {
                     List<AnimationStageInfo> stages = match.stages();
                     UUID requestId = UUID.randomUUID();
                     long expiresTick = world.getTime() + 600L;
                     NeedsOfNature.PendingBreedingState pending = new NeedsOfNature.PendingBreedingState(
                        world.getRegistryKey(), pairKey.firstUuid(), pairKey.secondUuid(), expiresTick
                     );
                     BREEDING_PENDING_BY_PAIR.put(pairKey, pending);
                     BREEDING_PENDING_BY_REQUEST.put(requestId, pairKey);
                     AnimationStartContext startContext = AnimationStartContext.empty().withSessionData(NonAnimationSessionData.breeding(requestId));
                     UUID started = startAnimationNowWithContext(
                        world, match.animationId(), actors, actorKeys, stages, NonAnimationDamageBehavior.STOP_ON_DAMAGE, false, null, startContext
                     );
                     return true;
                  } else {
                     return false;
                  }
               }
            }
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   @Nullable
   private static NeedsOfNature.BreedingPairKey breedingPairKey(UUID firstUuid, UUID secondUuid) {
      if (firstUuid == null || secondUuid == null) {
         return null;
      } else if (firstUuid.equals(secondUuid)) {
         return null;
      } else {
         return firstUuid.compareTo(secondUuid) <= 0
            ? new NeedsOfNature.BreedingPairKey(firstUuid, secondUuid)
            : new NeedsOfNature.BreedingPairKey(secondUuid, firstUuid);
      }
   }

   private static boolean isMaleFemalePair(Entity first, Entity second) {
      if (first != null && second != null) {
         boolean firstCanBeMale = NonGenderSystem.hasMaleTag(first);
         boolean firstCanBeFemale = NonGenderSystem.hasFemaleTag(first);
         boolean secondCanBeMale = NonGenderSystem.hasMaleTag(second);
         boolean secondCanBeFemale = NonGenderSystem.hasFemaleTag(second);
         return firstCanBeMale && secondCanBeFemale || firstCanBeFemale && secondCanBeMale;
      } else {
         return false;
      }
   }

   private static void adoptPendingBreedingRequest(ServerWorld world, UUID instanceId, @Nullable NonAnimationSessionData sessionData) {
      if (world != null && instanceId != null && sessionData != null) {
         UUID requestId = sessionData.breedingRequestId();
         if (requestId != null) {
            NeedsOfNature.BreedingPairKey pairKey = BREEDING_PENDING_BY_REQUEST.remove(requestId);
            if (pairKey != null) {
               NeedsOfNature.PendingBreedingState pending = BREEDING_PENDING_BY_PAIR.remove(pairKey);
               if (pending != null) {
                  if (world.getRegistryKey().equals(pending.worldKey())) {
                     NeedsOfNature.ActiveBreedingState active = new NeedsOfNature.ActiveBreedingState(
                        pending.worldKey(), pending.firstUuid(), pending.secondUuid()
                     );
                     BREEDING_ACTIVE_BY_INSTANCE.put(instanceId, active);
                     BREEDING_ACTIVE_INSTANCE_BY_PAIR.put(pairKey, instanceId);
                  }
               }
            }
         }
      }
   }

   private static void adoptPendingPregnancyHatch(ServerWorld world, UUID instanceId, List<UUID> actorUuids, @Nullable NonAnimationSessionData sessionData) {
      if (world != null && instanceId != null && actorUuids != null && sessionData != null && sessionData.isPregnancy()) {
         NonAnimationSessionData.Pregnancy pregnancy = sessionData.pregnancy();
         if (pregnancy != null) {
            ServerPlayerEntity player = null;

            for (UUID actorUuid : actorUuids) {
               if (world.getEntity(actorUuid) instanceof ServerPlayerEntity p) {
                  player = p;
                  break;
               }
            }

            if (player != null) {
               Identifier entityTypeId = pregnancy.sourceEntityTypeId();
               Identifier birthEntityTypeId = pregnancy.birthEntityTypeId();
               String birthMode = pregnancy.birthMode();
               int spawnCount = Math.max(1, Math.min(16, pregnancy.offspringCount()));
               NeedsOfNature.PregnancyVariantData variantData = pregnancy.variantData();
               PREGNANCY_HATCH_BY_INSTANCE.put(
                  instanceId,
                  new NeedsOfNature.PregnancyHatchState(player.getUuid(), entityTypeId, birthEntityTypeId, birthMode, spawnCount, spawnCount, 0, variantData)
               );
               PREGNANCY_ACTIVE_INSTANCE_BY_PLAYER.put(player.getUuid(), instanceId);
               PREGNANCY_QUEUED_PLAYERS.remove(player.getUuid());
               PREGNANCY_START_RETRY_TICKS.remove(player.getUuid());
            }
         }
      }
   }

   private static void finalizeAnimationBreeding(ServerWorld world, UUID instanceId, boolean peaked) {
      if (world != null && instanceId != null) {
         NeedsOfNature.ActiveBreedingState active = BREEDING_ACTIVE_BY_INSTANCE.remove(instanceId);
         if (active != null) {
            NeedsOfNature.BreedingPairKey pairKey = breedingPairKey(active.firstUuid(), active.secondUuid());
            if (pairKey != null) {
               BREEDING_ACTIVE_INSTANCE_BY_PAIR.remove(pairKey, instanceId);
            }

            if (peaked) {
               if (world.getRegistryKey().equals(active.worldKey())) {
                  Entity firstEntity = world.getEntity(active.firstUuid());
                  Entity secondEntity = world.getEntity(active.secondUuid());
                  if (firstEntity instanceof AnimalEntity first && secondEntity instanceof AnimalEntity second) {
                     if (first.isAlive() && second.isAlive() && !first.isRemoved() && !second.isRemoved()) {
                        if (first.getEntityWorld() == world && second.getEntityWorld() == world) {
                           PassiveEntity baby = first.createChild(world, second);
                           if (baby != null) {
                              baby.setBaby(true);
                              baby.refreshPositionAndAngles(first.getX(), first.getY(), first.getZ(), 0.0F, 0.0F);
                              first.breed(world, second, baby);
                              world.spawnEntityAndPassengers(baby);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void clearPendingBreedingForActors(ServerWorld world, List<UUID> actorUuids) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty() && !BREEDING_PENDING_BY_PAIR.isEmpty()) {
         RegistryKey<World> worldKey = world.getRegistryKey();
         Set<UUID> actorSet = new HashSet<>(actorUuids);
         Set<NeedsOfNature.BreedingPairKey> removedPairs = new HashSet<>();
         BREEDING_PENDING_BY_PAIR.entrySet().removeIf(entry -> {
            NeedsOfNature.PendingBreedingState state = entry.getValue();
            NeedsOfNature.BreedingPairKey key = entry.getKey();
            if (state == null || key == null) {
               return true;
            }

            if (!worldKey.equals(state.worldKey())) {
               return false;
            }

            boolean matchesActors = actorSet.contains(key.firstUuid()) && actorSet.contains(key.secondUuid());
            if (matchesActors) {
               removedPairs.add(key);
            }

            return matchesActors;
         });
         if (!removedPairs.isEmpty()) {
            BREEDING_PENDING_BY_REQUEST.entrySet().removeIf(entry -> removedPairs.contains(entry.getValue()));
         }
      }
   }

   private static void prunePendingAnimationBreeding(ServerWorld world, long nowTick) {
      if (world != null && !BREEDING_PENDING_BY_PAIR.isEmpty()) {
         RegistryKey<World> worldKey = world.getRegistryKey();
         Set<NeedsOfNature.BreedingPairKey> removedPairs = new HashSet<>();
         BREEDING_PENDING_BY_PAIR.entrySet().removeIf(entry -> {
            NeedsOfNature.PendingBreedingState state = entry.getValue();
            NeedsOfNature.BreedingPairKey key = entry.getKey();
            if (state == null || key == null) {
               return true;
            }

            if (!worldKey.equals(state.worldKey())) {
               return false;
            }

            if (state.expiresTick() > nowTick) {
               return false;
            }

            removedPairs.add(key);
            return true;
         });
         if (!removedPairs.isEmpty()) {
            BREEDING_PENDING_BY_REQUEST.entrySet().removeIf(entry -> removedPairs.contains(entry.getValue()));
         }
      }
   }

   private static void trackPendingGatherPairs(ServerWorld world, List<? extends Entity> actors) {
      if (world != null && actors != null && !actors.isEmpty()) {
         long expiresTick = world.getTime() + 6000L;
         RegistryKey<World> worldKey = world.getRegistryKey();
         List<UUID> playerUuids = new ArrayList<>();
         List<UUID> mobUuids = new ArrayList<>();

         for (Entity actor : actors) {
            if (actor != null && actor.getEntityWorld() == world) {
               if (actor instanceof ServerPlayerEntity) {
                  playerUuids.add(actor.getUuid());
               } else if (actor instanceof MobEntity) {
                  mobUuids.add(actor.getUuid());
               }
            }
         }

         if (!playerUuids.isEmpty() && !mobUuids.isEmpty()) {
            for (UUID mobUuid : mobUuids) {
               for (UUID playerUuid : playerUuids) {
                  PENDING_GATHER_PAIRS.put(new NeedsOfNature.GatherPairKey(mobUuid, playerUuid), new NeedsOfNature.GatherPairState(worldKey, expiresTick));
               }
            }
         }
      }
   }

   private static void clearPendingGatherPairsForActors(ServerWorld world, List<UUID> actorUuids) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty() && !PENDING_GATHER_PAIRS.isEmpty()) {
         RegistryKey<World> worldKey = world.getRegistryKey();
         Set<UUID> actorSet = new HashSet<>(actorUuids);
         PENDING_GATHER_PAIRS.entrySet().removeIf(entry -> {
            NeedsOfNature.GatherPairState state = entry.getValue();
            NeedsOfNature.GatherPairKey key = entry.getKey();
            if (state == null || key == null) {
               return true;
            } else {
               return !worldKey.equals(state.worldKey()) ? false : actorSet.contains(key.mobUuid()) && actorSet.contains(key.playerUuid());
            }
         });
      }
   }

   private static void prunePendingGatherPairs(ServerWorld world, long nowTick) {
      if (world != null && !PENDING_GATHER_PAIRS.isEmpty()) {
         RegistryKey<World> worldKey = world.getRegistryKey();
         PENDING_GATHER_PAIRS.entrySet().removeIf(entry -> {
            NeedsOfNature.GatherPairState state = entry.getValue();
            NeedsOfNature.GatherPairKey key = entry.getKey();
            if (state == null || key == null) {
               return true;
            } else if (!worldKey.equals(state.worldKey())) {
               return false;
            } else if (state.expiresTick() <= nowTick) {
               return true;
            } else {
               return !isActorPendingOrActive(world, key.mobUuid()) ? true : !isActorPendingOrActive(world, key.playerUuid());
            }
         });
         NON_PENDING_GATHER_ACTORS.entrySet().removeIf(entry -> {
            NeedsOfNature.NoNGatherActorState state = entry.getValue();
            return state == null ? true : worldKey.equals(state.worldKey()) && state.expiresTick() <= nowTick;
         });
      }
   }

   static boolean isMobGatheringOrActiveWithPlayer(ServerWorld world, MobEntity mob, ServerPlayerEntity player) {
      if (world != null && mob != null && player != null) {
         UUID playerInstance = getActivePlayerInstance(player);
         if (instanceContainsActor(world, playerInstance, mob.getUuid())) {
            return true;
         }

         NeedsOfNature.GatherPairKey key = new NeedsOfNature.GatherPairKey(mob.getUuid(), player.getUuid());
         NeedsOfNature.GatherPairState state = PENDING_GATHER_PAIRS.get(key);
         if (state == null) {
            return false;
         }

         if (!world.getRegistryKey().equals(state.worldKey()) || state.expiresTick() <= world.getTime()) {
            PENDING_GATHER_PAIRS.remove(key);
            return false;
         }

         if (isActorPendingOrActive(world, mob.getUuid()) && isActorPendingOrActive(world, player.getUuid())) {
            return true;
         }

         PENDING_GATHER_PAIRS.remove(key);
         return false;
      } else {
         return false;
      }
   }

   public static NeedsOfNature.AttackBlockReason getMobAttackBlockReason(ServerWorld world, MobEntity mob, ServerPlayerEntity player) {
      return NonAttackSystem.getMobAttackBlockReason(world, mob, player);
   }

   public static void onMobAttackBlocked(ServerWorld world, MobEntity mob, ServerPlayerEntity player, NeedsOfNature.AttackBlockReason reason) {
      NonAttackSystem.onMobAttackBlocked(world, mob, player, reason);
   }

   private static boolean hasPlayerAndHostileActor(List<? extends Entity> actors) {
      return NonAttackSystem.hasPlayerAndHostileActor(actors);
   }

   private static void recordAttackOutcomeForMobs(ServerWorld world, List<UUID> actorUuids, String reason) {
      NonAttackSystem.recordAttackOutcomeForMobs(world, actorUuids, reason);
   }

   public static boolean enqueueAnimation(
      ServerWorld world,
      Identifier animationId,
      List<? extends Entity> actors,
      int insertIndex,
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      AnimationStartContext startContext,
      ServerPlayerEntity requester
   ) {
      if (world != null && animationId != null && actors != null && !actors.isEmpty()) {
         boolean effectiveIgnoreAttackers = ignoreAttackers || hasPlayerAndHostileActor(actors);
         return ServerAnimationController.enqueueForPlayerWithContext(
            world,
            requester,
            animationId,
            actors,
            insertIndex,
            damageBehavior,
            effectiveIgnoreAttackers,
            startContext == null ? AnimationStartContext.empty() : startContext,
            false
         );
      } else {
         return false;
      }
   }

   public static void clearQueuedAnimations(ServerWorld world, ServerPlayerEntity player) {
      if (world != null && player != null) {
         AnimationService.clearQueuedAnimations(world, player);
      }
   }

   public static List<UUID> getInstanceActors(ServerWorld world, UUID instanceId) {
      if (world != null && instanceId != null) {
         ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
         return snapshot == null ? List.of() : snapshot.actorUuids();
      } else {
         return List.of();
      }
   }

   public static boolean isInstanceAttack(ServerWorld world, UUID instanceId) {
      return world != null && instanceId != null ? isAttackSnapshot(ServerAnimationController.getActiveInstanceSnapshot(world, instanceId)) : false;
   }

   static boolean isVoluntaryManualPeakInstance(ServerWorld world, UUID instanceId) {
      if (world != null && instanceId != null) {
         ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
         NonAnimationSessionData data = snapshot == null ? null : getNonSessionData(snapshot.startContext());
         return data != null && data.isVoluntary() && isManualPeakAnimation(snapshot.animationId());
      } else {
         return false;
      }
   }

   public static void sendDebugChat(ServerPlayerEntity player, String message) {
      sendDebugChat(player, NonDebugChatCategory.INFO, message);
   }

   public static void sendDebugChat(ServerPlayerEntity player, NonDebugChatCategory category, String message) {
      sendDebugChat(player, category, Text.literal(message));
   }

   public static void sendDebugChat(ServerPlayerEntity player, NonDebugChatCategory category, Text message) {
      if (player != null) {
         NonDiagnostics.sendDebugChat(player, category, Text.translatable("debug.needsofnature.prefix", new Object[]{message}), false);
      }
   }

   public static void sendDebugChatToNearby(ServerWorld world, Entity source, double radius, String message) {
      sendDebugChatToNearby(world, source, radius, NonDebugChatCategory.INFO, message);
   }

   public static void sendDebugChatToNearby(ServerWorld world, Entity source, double radius, NonDebugChatCategory category, String message) {
      sendDebugChatToNearby(world, source, radius, category, Text.literal(message));
   }

   public static void sendDebugChatToNearby(ServerWorld world, Entity source, double radius, NonDebugChatCategory category, Text message) {
      if (world != null && source != null) {
         double maxDistSq = radius * radius;

         for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(source) <= maxDistSq) {
               NonDiagnostics.sendDebugChat(p, category, Text.translatable("debug.needsofnature.prefix", new Object[]{message}), false);
            }
         }
      }
   }

   public static void logSetupWarning(String template, Object... args) {
      NonDiagnostics.logSetupWarning(template, args);
   }

   public static void logRuntime(String template, Object... args) {
      NonDiagnostics.logRuntime(template, args);
   }

   public static void logSetupError(String template, Object... args) {
      NonDiagnostics.logSetupError(template, args);
   }

   public static void queueSetupChat(String message) {
      NonDiagnostics.queueSetupChat(message);
   }

   public static String formatLogTemplate(String template, Object... args) {
      return NonDiagnostics.formatLogTemplate(template, args);
   }

   public static boolean consumeScanBudget(ServerWorld world) {
      if (world == null) {
         return false;
      }

      RegistryKey<World> key = world.getRegistryKey();
      long now = world.getTime();
      int configured = CONFIG.getScanBudgetPerTick();
      NeedsOfNature.ScanBudget budget = SCAN_BUDGETS.get(key);
      if (budget == null || budget.tick != now) {
         budget = new NeedsOfNature.ScanBudget(now, configured);
      }

      if (budget.remaining <= 0) {
         SCAN_BUDGETS.put(key, budget);
         return false;
      } else {
         SCAN_BUDGETS.put(key, new NeedsOfNature.ScanBudget(now, budget.remaining - 1));
         return true;
      }
   }

   private void registerLoopProgression() {
      ServerTickEvents.END_WORLD_TICK.register((EndWorldTick)world -> {
         long now = world.getTime();
         tickPregnancyCueRuntime(world, now);
         tickLiquidDecay(world);
         NonPregnancyConceptionSystem.tick(world);
         tickPeakLiquidProgression(world, now);
         tickReceiverDripParticles(world, now);
         tickPregnancy(world);
         tickMInjectorRewards(world, now);
         tickPendingAttackEscapeResolutions(world, now);
         tickPostDeathStateSync(world, now);
         NonGatherSystem.tickServerWorld(world);
         NonPlayerInteractionSystem.tick(world);
         NonPlayerAttackSystem.tick(world);
         NonAccessoryShedSystem.tickServerWorld(world);
         NonMessSystem.tickMessCleaning(world, now);
         if (now % 20L == 0L) {
            prunePendingGatherPairs(world, now);
            prunePendingAnimationBreeding(world, now);
            pruneAttackOutcomeFailsafe(world, now);
            pruneAttackBlockDebug(world, now);
         }
      });
   }

   private static void tickMInjectorRewards(ServerWorld world, long now) {
      if (world != null && !PENDING_M_INJECTOR_REWARDS.isEmpty()) {
         RegistryKey<World> worldKey = world.getRegistryKey();

         for (Entry<UUID, NeedsOfNature.PendingMInjectorReward> entry : PENDING_M_INJECTOR_REWARDS.entrySet()) {
            UUID playerUuid = entry.getKey();
            NeedsOfNature.PendingMInjectorReward reward = entry.getValue();
            if (playerUuid != null && reward != null) {
               if (worldKey.equals(reward.worldKey())) {
                  ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerUuid);
                  if (player == null || player.getEntityWorld() != world || !player.isAlive() || player.isRemoved()) {
                     PENDING_M_INJECTOR_REWARDS.remove(playerUuid, reward);
                  } else if (AnimationService.isActorPendingOrActive(world, playerUuid)) {
                     if (reward.freeSinceTick() >= 0L) {
                        PENDING_M_INJECTOR_REWARDS.put(playerUuid, new NeedsOfNature.PendingMInjectorReward(reward.worldKey(), -1L));
                     }
                  } else {
                     long freeSince = reward.freeSinceTick();
                     if (freeSince < 0L) {
                        PENDING_M_INJECTOR_REWARDS.put(playerUuid, new NeedsOfNature.PendingMInjectorReward(reward.worldKey(), now));
                     } else if (now - freeSince >= 20L) {
                        applyMInjectorReward(world, player);
                        PENDING_M_INJECTOR_REWARDS.remove(playerUuid, reward);
                     }
                  }
               }
            } else if (playerUuid != null) {
               PENDING_M_INJECTOR_REWARDS.remove(playerUuid);
            }
         }
      }
   }

   private static void applyMInjectorReward(ServerWorld world, ServerPlayerEntity player) {
      if (world != null && player != null) {
         int foodBefore = player.getHungerManager().getFoodLevel();
         player.getHungerManager().add(1, 0.0F);
         if (foodBefore < 20) {
            grantEmergencySnackAdvancement(player);
         }

         world.playSound(
            null,
            player.getX(),
            player.getY() + player.getStandingEyeHeight() * 0.5,
            player.getZ(),
            SoundEvents.ITEM_HONEY_BOTTLE_DRINK,
            SoundCategory.PLAYERS,
            1.0F,
            0.95F + world.getRandom().nextFloat() * 0.1F
         );
      }
   }

   private static void tickPeakLiquidProgression(ServerWorld world, long nowTick) {
      if (world != null && !PEAK_LIQUID_PROGRESS.isEmpty()) {
         RegistryKey<World> worldKey = world.getRegistryKey();

         for (Entry<UUID, NeedsOfNature.PeakLiquidProgressState> entry : PEAK_LIQUID_PROGRESS.entrySet()) {
            UUID instanceId = entry.getKey();
            NeedsOfNature.PeakLiquidProgressState state = entry.getValue();
            if (instanceId != null && state != null) {
               if (worldKey.equals(state.worldKey)) {
                  NeedsOfNature.PeakState peakState = PEAK_STATES.get(instanceId);
                  if (peakState == null || peakState.lastStage() != peakState.peakStage()) {
                     PEAK_LIQUID_PROGRESS.remove(instanceId);
                  } else if (!state.infinite) {
                     while (state.scheduleIndex < state.scheduledTicks.size() && state.scheduledTicks.get(state.scheduleIndex) <= nowTick) {
                        int amount = state.scheduledAmounts.get(state.scheduleIndex);
                        state.scheduleIndex++;
                        if (amount > 0) {
                           int previous = state.distributedBaseMl;
                           int next = Math.min(state.plan.maxAmount(), previous + amount);
                           NeedsOfNature.LiquidPulseResult result = grantLiquidPulseToReceivers(
                              world, state.plan, previous, next, state.donorHistoryRecordedReceivers
                           );
                           state.distributedBaseMl = next;
                           recordPeakLiquidPulse(instanceId, result);
                           if (result.addedMl() > 0) {
                              addActualAddedMlToPeakState(instanceId, result.addedMl());
                           }
                        }
                     }

                     if (state.scheduleIndex >= state.scheduledTicks.size()) {
                        PEAK_LIQUID_PROGRESS.remove(instanceId);
                     }
                  } else if (state.remainingBaseMl > 0 && !(state.cycleTicks <= 0.0) && !state.cueOffsets.isEmpty()) {
                     int guard = 0;

                     while (true) {
                        if (state.remainingBaseMl > 0 && guard < 64) {
                           int cueOffset = Math.max(0, state.cueOffsets.get(state.cueIndex));
                           double cueAnimTick = state.cycleIndex * state.cycleTicks + cueOffset;
                           long dueTick = state.stageStartTick + elapsedWorldTicksFromAnimationTicks(cueAnimTick, state.stageSpeed);
                           if (nowTick >= dueTick) {
                              int amount = Math.min(5, state.remainingBaseMl);
                              state.remainingBaseMl -= amount;
                              int previous = state.distributedBaseMl;
                              int next = Math.min(state.plan.maxAmount(), previous + amount);
                              NeedsOfNature.LiquidPulseResult result = grantLiquidPulseToReceivers(
                                 world, state.plan, previous, next, state.donorHistoryRecordedReceivers
                              );
                              state.distributedBaseMl = next;
                              recordPeakLiquidPulse(instanceId, result);
                              if (result.addedMl() > 0) {
                                 addActualAddedMlToPeakState(instanceId, result.addedMl());
                              }

                              state.cueIndex++;
                              if (state.cueIndex >= state.cueOffsets.size()) {
                                 state.cueIndex = 0;
                                 state.cycleIndex++;
                              }

                              guard++;
                              continue;
                           }
                        }

                        if (state.remainingBaseMl <= 0) {
                           PEAK_LIQUID_PROGRESS.remove(instanceId);
                        }
                        break;
                     }
                  } else {
                     PEAK_LIQUID_PROGRESS.remove(instanceId);
                  }
               }
            } else if (instanceId != null) {
               PEAK_LIQUID_PROGRESS.remove(instanceId);
            }
         }
      }
   }

   private static void calmMobsAfterPeakedPlayerAnimation(ServerWorld world, List<UUID> actorUuids) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         Set<UUID> playerUuids = new HashSet<>();

         for (UUID actorUuid : actorUuids) {
            if (world.getEntity(actorUuid) instanceof ServerPlayerEntity player) {
               playerUuids.add(player.getUuid());
            }
         }

         if (!playerUuids.isEmpty()) {
            for (UUID actorUuid : actorUuids) {
               if (world.getEntity(actorUuid) instanceof MobEntity mob) {
                  if (mob.getTarget() instanceof ServerPlayerEntity player && playerUuids.contains(player.getUuid())) {
                     mob.setTarget(null);
                  }

                  if (mob instanceof Angerable angerable) {
                     LazyEntityReference<LivingEntity> angryAt = angerable.getAngryAt();
                     UUID angryAtUuid = angryAt == null ? null : angryAt.getUuid();
                     if (angryAtUuid == null || playerUuids.contains(angryAtUuid)) {
                        angerable.stopAnger();
                     }
                  }
               }
            }
         }
      }
   }

   private static void pruneAttackOutcomeFailsafe(ServerWorld world, long nowTick) {
      NonAttackSystem.pruneAttackOutcomeFailsafe(world, nowTick);
   }

   private static void pruneAttackBlockDebug(ServerWorld world, long nowTick) {
      NonAttackSystem.pruneAttackBlockDebug(world, nowTick);
   }

   private void registerLiquidSync() {
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> {
         NonGenderSystem.ensurePlayerGenderInitialized(handler.player);
         NonDestroyedSkinSystem.cacheDestroyedSkinStage(handler.player);
         NonDestroyedSkinSystem.syncDestroyedSkinStageToOtherPlayers(handler.player);
         syncLiquidState(handler.player);
         syncPregnancyState(handler.player);
         NonPregnancyConceptionSystem.syncState(handler.player);
         syncPlayerRuntimeSettings(handler.player);
         syncHostConfigState(handler.player);
         syncAnimationCatalogState(handler.player);
         syncDestroyedSkins(handler.player);
         NonMessSystem.syncMessStates(handler.player);
         NonAccessoryShedSystem.syncAllToViewer(handler.player);
      });
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> {
         UUID playerUuid = handler.player.getUuid();
         FILL_BOTTLE_STATES.entrySet().removeIf(entry -> playerUuid.equals(entry.getValue().playerUuid()));
         PENDING_GATHER_PAIRS.entrySet().removeIf(entry -> playerUuid.equals(entry.getKey().playerUuid()));
         LAST_DEFEATED_COOLDOWN_UNTIL_BY_PLAYER.remove(playerUuid);
         POST_ESCAPE_DAMAGE_INVULNERABLE_UNTIL_BY_PLAYER.remove(playerUuid);
         PENDING_ATTACK_ESCAPE_RESOLUTIONS.remove(playerUuid);
         PENDING_POST_DEATH_STATE_SYNCS.remove(playerUuid);
         UUID activeHatchInstance = PREGNANCY_ACTIVE_INSTANCE_BY_PLAYER.remove(playerUuid);
         if (activeHatchInstance != null) {
            PREGNANCY_HATCH_BY_INSTANCE.remove(activeHatchInstance);
            PREGNANCY_CUE_RUNTIME_BY_INSTANCE.remove(activeHatchInstance);
         }

         PREGNANCY_QUEUED_PLAYERS.remove(playerUuid);
         PREGNANCY_START_RETRY_TICKS.remove(playerUuid);
         NonTrinketsIntegration.clearAccessoryState(playerUuid);
         NonAccessoryShedSystem.clearServerShedState(handler.player.getEntityWorld(), playerUuid);
         NonAdvancementHooks.clearPlayerState(playerUuid);
         NonDestroyedSkinSystem.clearPlayerRuntimeState(server, playerUuid);
      });
   }

   public static void syncLiquidState(ServerPlayerEntity player) {
      NonLiquidSystem.syncLiquidState(player);
   }

   private static void syncPregnancyState(ServerPlayerEntity player) {
      if (player != null) {
         Identifier entityTypeId = getPregnantEntityTypeId(player);
         String raw = entityTypeId == null ? "" : entityTypeId.toString();
         ServerPlayNetworking.send(player, new PregnancyStateS2CPayload(raw));
      }
   }

   public static void syncPlayerRuntimeSettings(ServerPlayerEntity player) {
      if (player != null) {
         NonConfig config = getConfig();
         int attackHits = config.getAttackEscapeHits() + NonTrinketsIntegration.getAccessoryEffects(player).attackEscapeHitsAdd();
         attackHits = Math.max(1, Math.min(50, attackHits));
         ServerPlayNetworking.send(player, new PlayerRuntimeSettingsS2CPayload(attackHits));
      }
   }

   public static void syncPlayerRuntimeSettingsToAll(@Nullable MinecraftServer server) {
      if (server != null) {
         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            syncPlayerRuntimeSettings(player);
         }
      }
   }

   private static void syncHostConfigState(ServerPlayerEntity player) {
      if (player != null) {
         NonConfig config = getConfig();
         HostConfigSyncData data = HostConfigSyncData.fromConfig(config);
         data.genderSpawnByEntity = new LinkedHashMap<>(NonGenderSystem.resolveEffectiveGenderSpawnMap());
         data.entityProfilesByEntity = new LinkedHashMap<>(NonEntityProfiles.resolveEffectiveProfilesMap());
         data.liquidGainByEntity = new LinkedHashMap<>(resolveEffectiveLiquidGainMap());
         data.liquidColorByEntity = new LinkedHashMap<>(resolveEffectiveLiquidColorMap());
         String json = HOST_CONFIG_SYNC_GSON.toJson(data);
         String editableJson = HOST_CONFIG_SYNC_GSON.toJson(HostConfigSyncData.fromEditableConfig(CONFIG));
         ServerPlayNetworking.send(player, new HostConfigSyncS2CPayload(json, editableJson, SERVER_CONFIG_REVISION.get(), canEditServerSettings(player)));
      }
   }

   private static boolean canEditServerSettings(@Nullable ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         MinecraftServer server = player.getEntityWorld().getServer();
         PlayerConfigEntry entry = new PlayerConfigEntry(player.getGameProfile());
         return server.isHost(entry) || server.getPlayerManager().isOperator(entry);
      } else {
         return false;
      }
   }

   private static void registerServerSettingsNetworking() {
      ServerPlayNetworking.registerGlobalReceiver(ServerConfigRequestC2SPayload.ID, (payload, context) -> context.server().execute(() -> {
         syncHostConfigState(context.player());
         syncAnimationCatalogState(context.player());
      }));
      ServerPlayNetworking.registerGlobalReceiver(
         ServerConfigUpdateC2SPayload.ID, (payload, context) -> context.server().execute(() -> applyServerConfigUpdate(context.player(), payload))
      );
   }

   private static void applyServerConfigUpdate(ServerPlayerEntity player, ServerConfigUpdateC2SPayload payload) {
      if (player != null && payload != null) {
         if (!canEditServerSettings(player)) {
            rejectServerConfigUpdate(player, "config.needsofnature.server_settings.no_permission");
         } else if (payload.revision() != SERVER_CONFIG_REVISION.get()) {
            rejectServerConfigUpdate(player, "config.needsofnature.server_settings.stale");
         } else {
            String raw = payload.json();
            if (raw != null && !raw.isBlank() && raw.length() <= 262144) {
               try {
                  HostConfigSyncData requested = (HostConfigSyncData)HOST_CONFIG_SYNC_GSON.fromJson(raw, HostConfigSyncData.class);
                  if (requested == null) {
                     rejectServerConfigUpdate(player, "config.needsofnature.server_settings.invalid");
                     return;
                  }

                  NonConfig validated = CONFIG.copy();
                  requested.applyEditableTo(validated);
                  HostConfigSyncData.fromEditableConfig(validated).applyEditableTo(CONFIG);
                  CONFIG.save();
                  applyAnimationToggleConfig();
                  SERVER_CONFIG_REVISION.incrementAndGet();
                  ANIMATION_CATALOG_REVISION.incrementAndGet();
                  MinecraftServer server = player.getEntityWorld().getServer();
                  syncHostConfigStateToAll(server);
                  syncAnimationCatalogStateToAll(server);
                  refreshLiquidBottleTintsToAll(server);
                  ServerPlayNetworking.send(player, new ServerConfigResultS2CPayload(true, SERVER_CONFIG_REVISION.get(), ""));
               } catch (RuntimeException e) {
                  LOGGER.warn("[NoN] Rejected invalid server settings update from {}.", player.getName().getString(), e);
                  rejectServerConfigUpdate(player, "config.needsofnature.server_settings.invalid");
               }
            } else {
               rejectServerConfigUpdate(player, "config.needsofnature.server_settings.invalid");
            }
         }
      }
   }

   private static void rejectServerConfigUpdate(ServerPlayerEntity player, String messageKey) {
      syncHostConfigState(player);
      syncAnimationCatalogState(player);
      ServerPlayNetworking.send(player, new ServerConfigResultS2CPayload(false, SERVER_CONFIG_REVISION.get(), messageKey));
   }

   private static void syncAnimationCatalogState(ServerPlayerEntity player) {
      if (player != null) {
         sendAnimationCatalog(player, AnimationCatalogData.capture(CONFIG), ANIMATION_CATALOG_REVISION.get());
      }
   }

   private static void syncAnimationCatalogStateToAll(@Nullable MinecraftServer server) {
      if (server != null) {
         AnimationCatalogData catalog = AnimationCatalogData.capture(CONFIG);
         long revision = ANIMATION_CATALOG_REVISION.get();

         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendAnimationCatalog(player, catalog, revision);
         }
      }
   }

   private static void sendAnimationCatalog(ServerPlayerEntity player, AnimationCatalogData catalog, long revision) {
      String json = HOST_CONFIG_SYNC_GSON.toJson(catalog);
      List<String> chunks = splitNetworkString(json, 24000);

      for (int i = 0; i < chunks.size(); i++) {
         ServerPlayNetworking.send(player, new AnimationCatalogChunkS2CPayload(revision, i, chunks.size(), chunks.get(i)));
      }
   }

   private static List<String> splitNetworkString(String value, int maxChars) {
      if (value != null && !value.isEmpty()) {
         int safeMax = Math.max(1024, maxChars);
         List<String> chunks = new ArrayList<>((value.length() + safeMax - 1) / safeMax);
         int start = 0;

         while (start < value.length()) {
            int end = Math.min(value.length(), start + safeMax);
            if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1)) && Character.isLowSurrogate(value.charAt(end))) {
               end--;
            }

            chunks.add(value.substring(start, end));
            start = end;
         }

         return List.copyOf(chunks);
      } else {
         return List.of("");
      }
   }

   private static void syncDestroyedSkins(ServerPlayerEntity player) {
      NonDestroyedSkinSystem.syncDestroyedSkins(player);
   }

   private static boolean shouldApplyDestroyedSkinAttackDamage(boolean isAttack, @Nullable AnimationStartContext startContext, @Nullable Identifier animationId) {
      if (!isAttack) {
         return false;
      } else {
         boolean joinReplace = startContext != null && startContext.isJoinReplacement();
         boolean defeatedAnimation = hasAnimationTagWithPrefix(animationId, "defeat.");
         boolean joinedFromDefeatedAnimation = startContext != null && hasAnimationTagWithPrefix(startContext.joinPredecessor(), "defeat.");
         if (!joinReplace || !defeatedAnimation && !joinedFromDefeatedAnimation) {
            return joinReplace ? false : !defeatedAnimation;
         } else {
            return true;
         }
      }
   }

   public static void syncHostConfigStateToAll(@Nullable MinecraftServer server) {
      if (server != null) {
         clearDisabledSystemStoredStates(server);
         NonPlayerInteractionSystem.normalizeAndSyncAll(server);

         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            NonDestroyedSkinSystem.cacheDestroyedSkinStage(player);
            syncPlayerRuntimeSettings(player);
            syncHostConfigState(player);
            syncLiquidState(player);
            NonPregnancyConceptionSystem.syncState(player);
            syncDestroyedSkins(player);
            NonMessSystem.syncMessStates(player);
         }
      }
   }

   private static void clearDisabledSystemStoredStates(MinecraftServer server) {
      if (server != null) {
         boolean messEnabled = isMessSystemEnabled();
         boolean destroyedSkinEnabled = isDestroyedSkinSystemEnabled();
         if (!messEnabled || !destroyedSkinEnabled) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
               if (!messEnabled) {
                  NonMessSystem.clearMessStoredState(player);
               }

               if (!destroyedSkinEnabled) {
                  NonDestroyedSkinSystem.clearDestroyedSkinStoredState(player);
               }
            }
         }
      }
   }

   public static void syncLiquidStateToAll(@Nullable MinecraftServer server) {
      NonLiquidSystem.syncLiquidStateToAll(server);
   }

   public static void refreshLiquidBottleTintsToAll(@Nullable MinecraftServer server) {
      NonLiquidSystem.refreshLiquidBottleTintsToAll(server);
   }

   static int resolveLiquidTintRgb(LiquidHolder holder) {
      return NonLiquidSystem.resolveLiquidTintRgb(holder);
   }

   public static int resolveBottleLiquidTintRgb(@Nullable Identifier entityTypeId) {
      return NonLiquidSystem.resolveBottleTintRgb(entityTypeId);
   }

   private static int grantLiquidOnPeak(ServerWorld world, List<UUID> actorUuids, List<String> actorKeys, Identifier animationId) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         if (actorKeys != null && !actorKeys.isEmpty()) {
            if (actorKeys.size() != actorUuids.size()) {
               return 0;
            }

            NonLiquidRoles.LiquidRoles roles = AnimationGameplayMetadata.liquidRoles(animationId);
            if (roles == null) {
               return 0;
            }

            Set<String> receiverKeys = roles.receiverKeys();
            if (receiverKeys.isEmpty()) {
               return 0;
            }

            int count = Math.min(actorUuids.size(), actorKeys.size());
            int totalAdded = 0;

            for (int i = 0; i < count; i++) {
               if (receiverKeys.contains(actorKeys.get(i))
                  && world.getEntity(actorUuids.get(i)) instanceof ServerPlayerEntity player
                  && player instanceof LiquidHolder holder) {
                  NeedsOfNature.LiquidGainProfile gain = resolveLiquidGainProfile(
                     world, actorUuids, actorKeys, animationId, NonGenderSystem.getLiquidTankType(player)
                  );
                  if (gain.amount() > 0) {
                     NeedsOfNature.LiquidGainProfile playerGain = applyApiLiquidGainModifier(player, applyAccessoryLiquidGainMultiplier(player, gain));
                     int added = addLiquidWithComposition(holder, playerGain);
                     if (added > 0) {
                        recordRecentLiquidDonors(holder, playerGain.recentDonors());
                        totalAdded += added;
                        Map<Identifier, Integer> scaledAmounts = scaleEntityAmounts(playerGain.entityAmounts(), added);
                        NonStats.addLiquidGained(player, added, scaledAmounts);
                        trackFieldResearch(world, player, scaledAmounts);
                        syncLiquidState(player);
                     }
                  }
               }
            }

            return totalAdded;
         } else {
            return 0;
         }
      } else {
         return 0;
      }
   }

   private static void fillHorseLiquidCollectorsOnPeak(ServerWorld world, List<UUID> actorUuids, List<String> actorKeys, Identifier animationId) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         if (actorKeys != null && !actorKeys.isEmpty()) {
            if (actorKeys.size() == actorUuids.size()) {
               NonLiquidRoles.LiquidRoles roles = AnimationGameplayMetadata.liquidRoles(animationId);
               if (roles != null) {
                  Set<String> receiverKeys = roles.receiverKeys();
                  Set<String> injectorKeys = roles.injectorKeys();
                  if (!receiverKeys.isEmpty() && !injectorKeys.isEmpty()) {
                     List<HorseLiquidCollectorEntity> collectors = new ArrayList<>();
                     Set<Identifier> donorTypes = new LinkedHashSet<>();
                     int count = Math.min(actorUuids.size(), actorKeys.size());

                     for (int i = 0; i < count; i++) {
                        String actorKey = actorKeys.get(i);
                        Entity entity = world.getEntity(actorUuids.get(i));
                        if (entity != null) {
                           if (receiverKeys.contains(actorKey) && entity instanceof HorseLiquidCollectorEntity collector) {
                              collectors.add(collector);
                           }

                           if (injectorKeys.contains(actorKey)) {
                              EntityType<?> entityType = entity.getType();
                              if (entityType == EntityType.HORSE || entityType == EntityType.DONKEY || entityType == EntityType.MULE) {
                                 Identifier typeId = Registries.ENTITY_TYPE.getId(entityType);
                                 donorTypes.add(typeId);
                              }
                           }
                        }
                     }

                     if (!collectors.isEmpty() && !donorTypes.isEmpty()) {
                        boolean mixedDonor = donorTypes.size() > 1;
                        Identifier donorType = mixedDonor ? null : donorTypes.iterator().next();

                        for (HorseLiquidCollectorEntity collector : collectors) {
                           if (mixedDonor) {
                              collector.absorbMixedLiquid();
                           } else {
                              collector.absorbEntityLiquid(donorType);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static NeedsOfNature.LiquidGainProfile resolveLiquidGainProfile(
      ServerWorld world, List<UUID> actorUuids, List<String> actorKeys, Identifier animationId
   ) {
      return resolveLiquidGainProfile(world, actorUuids, actorKeys, animationId, null);
   }

   private static NeedsOfNature.LiquidGainProfile resolveLiquidGainProfile(
      ServerWorld world, List<UUID> actorUuids, List<String> actorKeys, Identifier animationId, @Nullable NonLiquidTankType tankType
   ) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         if (actorKeys != null && !actorKeys.isEmpty()) {
            if (actorKeys.size() != actorUuids.size()) {
               return new NeedsOfNature.LiquidGainProfile(0, 0, LiquidHolder.LiquidComposition.EMPTY, null, Map.of(), null, List.of());
            }

            NonLiquidRoles.LiquidRoles roles = AnimationGameplayMetadata.liquidRoles(animationId);
            if (roles == null) {
               return new NeedsOfNature.LiquidGainProfile(0, 0, LiquidHolder.LiquidComposition.EMPTY, null, Map.of(), null, List.of());
            }

            Map<String, NonLiquidRoles.InjectorRole> injectorRoles = roles.injectorRoles();
            if (injectorRoles.isEmpty()) {
               return new NeedsOfNature.LiquidGainProfile(0, 0, LiquidHolder.LiquidComposition.EMPTY, null, Map.of(), null, List.of());
            }

            double liquidGainMultiplier = resolveLiquidGainMultiplier(animationId);
            int total = 0;
            int xpEligible = 0;
            Set<Identifier> injectorEntityTypes = new LinkedHashSet<>();
            Map<Identifier, Integer> entityAmounts = new LinkedHashMap<>();
            NeedsOfNature.PregnancyVariantData lastDonorVariantData = null;
            List<NeedsOfNature.PregnancyDonor> recentDonors = new ArrayList<>();
            int count = Math.min(actorUuids.size(), actorKeys.size());

            for (int i = 0; i < count; i++) {
               String actorKey = actorKeys.get(i);
               NonLiquidRoles.InjectorRole role = injectorRoles.get(actorKey);
               if (isLiquidInjectorAccepted(role, tankType)) {
                  Entity entity = world.getEntity(actorUuids.get(i));
                  if (entity != null && !(entity instanceof ServerPlayerEntity player && NonTrinketsIntegration.hasMaleRoleAccessory(player))) {
                     Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());
                     int gain = applyLiquidGainMultiplier(resolveEntityLiquidGainMl(typeId), liquidGainMultiplier);
                     if (gain > 0) {
                        total += gain;
                        if (!PLAYER_ENTITY_TYPE_ID.equals(typeId)) {
                           xpEligible += gain;
                        }

                        injectorEntityTypes.add(typeId);
                        entityAmounts.merge(typeId, gain, Integer::sum);
                        NeedsOfNature.PregnancyVariantData donorVariantData = entity instanceof ServerPlayerEntity
                           ? null
                           : capturePregnancyVariantData(world, entity);
                        lastDonorVariantData = donorVariantData;
                        if (role == NonLiquidRoles.InjectorRole.V && !(entity instanceof ServerPlayerEntity)) {
                           recentDonors.add(new NeedsOfNature.PregnancyDonor(typeId, donorVariantData));
                        }
                     }
                  }
               }
            }

            if (total <= 0) {
               return new NeedsOfNature.LiquidGainProfile(0, 0, LiquidHolder.LiquidComposition.EMPTY, null, Map.of(), null, List.of());
            }

            Map<Identifier, Integer> frozenEntityAmounts = Map.copyOf(entityAmounts);
            return injectorEntityTypes.size() == 1
               ? new NeedsOfNature.LiquidGainProfile(
                  total,
                  xpEligible,
                  LiquidHolder.LiquidComposition.ENTITY,
                  injectorEntityTypes.iterator().next(),
                  frozenEntityAmounts,
                  lastDonorVariantData,
                  recentDonors
               )
               : new NeedsOfNature.LiquidGainProfile(total, xpEligible, LiquidHolder.LiquidComposition.MIXED, null, frozenEntityAmounts, null, recentDonors);
         } else {
            return new NeedsOfNature.LiquidGainProfile(0, 0, LiquidHolder.LiquidComposition.EMPTY, null, Map.of(), null, List.of());
         }
      } else {
         return new NeedsOfNature.LiquidGainProfile(0, 0, LiquidHolder.LiquidComposition.EMPTY, null, Map.of(), null, List.of());
      }
   }

   public static boolean isProtectedByAccessory(ServerWorld world, AnimationDefinitions.Definition definition, List<? extends Entity> actors) {
      if (world != null && definition != null && actors != null && !actors.isEmpty()) {
         for (ServerPlayerEntity player : collectPlayerActors(actors)) {
            for (NonLiquidRoles.InjectorRole role : resolveEffectiveInjectorRolesForPlayer(definition.id(), player)) {
               if (NonTrinketsIntegration.hasProtectorFor(player, role)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean consumeProtectorForAnimation(ServerWorld world, AnimationDefinitions.Definition definition, List<? extends Entity> actors) {
      if (world != null && definition != null && actors != null && !actors.isEmpty()) {
         for (ServerPlayerEntity player : collectPlayerActors(actors)) {
            for (NonLiquidRoles.InjectorRole role : resolveEffectiveInjectorRolesForPlayer(definition.id(), player)) {
               if (NonTrinketsIntegration.consumeProtectorFor(world, player, role)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static void trackNoNGatherPair(ServerWorld world, List<? extends Entity> actors) {
      trackPendingGatherPairs(world, actors);
      trackNoNGatherActors(world, actors);
   }

   public static void clearNoNGatherPair(ServerWorld world, List<UUID> actorUuids) {
      clearPendingGatherPairsForActors(world, actorUuids);
      clearNoNGatherActors(world, actorUuids);
   }

   private static void trackNoNGatherActors(ServerWorld world, List<? extends Entity> actors) {
      if (world != null && actors != null && !actors.isEmpty()) {
         long expiresTick = world.getTime() + 6000L;
         RegistryKey<World> worldKey = world.getRegistryKey();

         for (Entity actor : actors) {
            if (actor != null && actor.getEntityWorld() == world) {
               NON_PENDING_GATHER_ACTORS.put(actor.getUuid(), new NeedsOfNature.NoNGatherActorState(worldKey, expiresTick));
            }
         }
      }
   }

   private static void clearNoNGatherActors(ServerWorld world, List<UUID> actorUuids) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         RegistryKey<World> worldKey = world.getRegistryKey();

         for (UUID actorUuid : actorUuids) {
            if (actorUuid != null) {
               NeedsOfNature.NoNGatherActorState state = NON_PENDING_GATHER_ACTORS.get(actorUuid);
               if (state == null || worldKey.equals(state.worldKey())) {
                  NON_PENDING_GATHER_ACTORS.remove(actorUuid);
               }
            }
         }
      }
   }

   public static void applyNoNGatherCancellation(ServerWorld world, Identifier animationId, List<UUID> actorUuids, NonGatherCancelReason reason) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         clearPendingGatherPairsForActors(world, actorUuids);
         if (reason == NonGatherCancelReason.NO_LOS || reason == NonGatherCancelReason.TOO_FAR || reason == NonGatherCancelReason.TIMEOUT) {
            long now = world.getTime();

            for (UUID actorId : actorUuids) {
               if (world.getEntity(actorId) instanceof EnergyHolder holder) {
                  for (UUID otherId : actorUuids) {
                     if (!otherId.equals(actorId)) {
                        holder.applyPartnerCooldown(otherId, now, 1200);
                     }
                  }
               }
            }

            Entity chatSource = null;

            for (UUID actorId : actorUuids) {
               Entity actor = world.getEntity(actorId);
               if (actor instanceof MobEntity) {
                  chatSource = actor;
                  break;
               }

               if (chatSource == null && actor != null) {
                  chatSource = actor;
               }
            }

            if (chatSource != null) {
               sendDebugChatToNearby(
                  world,
                  chatSource,
                  20.0,
                  NonDebugChatCategory.WARNING,
                  Text.translatable(
                     "debug.needsofnature.gather_cancelled",
                     new Object[]{animationId == null ? "unknown" : animationId.toString(), reason.name().toLowerCase(Locale.ROOT)}
                  )
               );
            }

            logRuntime("[NoN] Gather cancelled: animation={} reason={} actors={}", animationId == null ? "unknown" : animationId, reason, actorUuids);
         }
      }
   }

   public static UUID startAnimationNowWithContext(
      ServerWorld world,
      Identifier animationId,
      List<? extends Entity> actors,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      @Nullable Entity positionAnchor,
      AnimationStartContext startContext
   ) {
      return startAnimationNowWithContext(
         world, animationId, actors, actorKeys, stages, damageBehavior, ignoreAttackers, positionAnchor, startContext, null, false
      );
   }

   public static UUID startAnimationNowWithContext(
      ServerWorld world,
      Identifier animationId,
      List<? extends Entity> actors,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      @Nullable Entity positionAnchor,
      AnimationStartContext startContext,
      @Nullable ServerPlayerEntity requester,
      boolean forceChat
   ) {
      return startAnimationNowWithContext(
         world, animationId, actors, actorKeys, stages, damageBehavior, ignoreAttackers, positionAnchor, startContext, requester, forceChat, false
      );
   }

   public static UUID startAnimationNowWithContext(
      ServerWorld world,
      Identifier animationId,
      List<? extends Entity> actors,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      @Nullable Entity positionAnchor,
      AnimationStartContext startContext,
      @Nullable ServerPlayerEntity requester,
      boolean forceChat,
      boolean allowMultiplePlayers
   ) {
      if (world != null && animationId != null && actors != null && !actors.isEmpty()) {
         boolean effectiveIgnoreAttackers = ignoreAttackers || hasPlayerAndHostileActor(actors);
         AnimationStartContext safeContext = startContext == null ? AnimationStartContext.empty() : startContext;
         return AnimationService.startAnimationNowWithContext(
            world,
            animationId,
            actors,
            actorKeys,
            stages,
            new AnimationService.StartOptions(
               damageBehavior, effectiveIgnoreAttackers, positionAnchor, requester, safeContext.addonMetadata(), forceChat, allowMultiplePlayers
            ),
            safeContext
         );
      } else {
         return null;
      }
   }

   public static boolean joinAnimationNow(ServerWorld world, UUID instanceId, List<? extends Entity> newActors, @Nullable ServerPlayerEntity requester) {
      return world != null && instanceId != null && newActors != null && !newActors.isEmpty()
         ? joinAnimationNow(world, instanceId, newActors, requester, null)
         : false;
   }

   @Nullable
   public static UUID findActiveInstanceContainingActor(ServerWorld world, UUID actorUuid) {
      return ServerAnimationController.findActiveInstanceId(world, actorUuid);
   }

   public static boolean isVoluntaryJoinableInstance(ServerWorld world, UUID instanceId) {
      if (world != null && instanceId != null) {
         ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
         if (snapshot != null && !isAttackSnapshot(snapshot) && !isPregnancySnapshot(snapshot)) {
            AnimationStageInfo stage = AnimationService.getCurrentStage(world, instanceId);
            return stage != null && stage.allowJoin();
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean joinAnimationNow(
      ServerWorld world, UUID instanceId, List<? extends Entity> newActors, @Nullable ServerPlayerEntity requester, @Nullable Identifier expandedAnimationId
   ) {
      return joinAnimationNow(world, instanceId, newActors, requester, expandedAnimationId, null);
   }

   public static boolean joinAnimationNow(
      ServerWorld world,
      UUID instanceId,
      List<? extends Entity> newActors,
      @Nullable ServerPlayerEntity requester,
      @Nullable Identifier expandedAnimationId,
      @Nullable Entity positionAnchor
   ) {
      return joinAnimationNow(world, instanceId, newActors, requester, expandedAnimationId, positionAnchor, null);
   }

   public static boolean joinAnimationNow(
      ServerWorld world,
      UUID instanceId,
      List<? extends Entity> newActors,
      @Nullable ServerPlayerEntity requester,
      @Nullable Identifier expandedAnimationId,
      @Nullable Entity positionAnchor,
      @Nullable List<String> exactActorKeys
   ) {
      if (world != null && instanceId != null && newActors != null && !newActors.isEmpty()) {
         ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
         if (snapshot == null) {
            return false;
         }

         boolean attackContinuation = isAttackSnapshot(snapshot);
         AnimationStageInfo currentStage = AnimationService.getCurrentStage(world, instanceId);
         if (currentStage != null && currentStage.allowJoin()) {
            Identifier predecessorAnimationId = AnimationService.getActiveAnimationId(world, instanceId);
            if (predecessorAnimationId == null) {
               return false;
            }

            List<UUID> existingActorUuids = snapshot.actorUuids();
            if (existingActorUuids.isEmpty()) {
               return false;
            }

            List<Entity> combined = collectEntities(world, existingActorUuids);
            if (combined.isEmpty()) {
               return false;
            }

            Set<UUID> existing = new HashSet<>(existingActorUuids);
            int originalCount = combined.size();

            for (Entity actor : newActors) {
               if (actor != null
                  && actor.getEntityWorld() == world
                  && !actor.isRemoved()
                  && !existing.contains(actor.getUuid())
                  && !(
                     actor instanceof ServerPlayerEntity playerActor
                        ? playerActor.isSpectator()
                        : actor instanceof PlayerEntity || !(actor instanceof MobEntity)
                  )) {
                  combined.add(actor);
                  existing.add(actor.getUuid());
               }
            }

            if (combined.size() <= originalCount) {
               return false;
            }

            combined.sort(Comparator.comparingInt(Entity::getId));
            AnimationDefinitions.Definition definition = findJoinDefinition(world, combined, expandedAnimationId, predecessorAnimationId, attackContinuation);
            if (definition == null) {
               return false;
            }

            boolean eligible = exactActorKeys == null
               ? AnimationService.isAnimationStartEligible(world, definition, combined, positionAnchor)
               : AnimationService.isAnimationStartEligible(world, definition, combined, exactActorKeys, positionAnchor);
            if (!eligible) {
               return false;
            }

            List<String> actorKeys = exactActorKeys == null
               ? AnimationDefinitions.resolveActorKeys(definition, combined, world.getRandom())
               : List.copyOf(exactActorKeys);
            if (actorKeys != null && actorKeys.size() == combined.size()) {
               AnimationStartContext replacementContext = snapshot.startContext();
               if (requester != null && isDefeatedInstance(world, instanceId)) {
                  replacementContext = NonPlayerAttackSystem.prepareDefeatedJoinContext(world, existingActorUuids, requester, replacementContext);
               }

               replacementContext = replacementContext.withJoinPredecessor(predecessorAnimationId);
               if (!ServerAnimationController.stopInstance(world, instanceId, false)) {
                  return false;
               }

               UUID replacement = startAnimationNowWithContext(
                  world,
                  definition.id(),
                  combined,
                  actorKeys,
                  definition.stages(),
                  NonPlayerAttackSystem.isPlayerAttackContext(replacementContext) ? NonAnimationDamageBehavior.STOP_ON_DAMAGE : snapshot.damageBehavior(),
                  snapshot.ignoreAttackers(),
                  positionAnchor,
                  replacementContext,
                  requester,
                  false,
                  true
               );
               return replacement != null;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Nullable
   private static AnimationDefinitions.Definition findJoinDefinition(
      ServerWorld world, List<Entity> combined, @Nullable Identifier expandedAnimationId, Identifier predecessorAnimationId, boolean attackContinuation
   ) {
      if (world == null || combined == null || combined.isEmpty() || predecessorAnimationId == null) {
         return null;
      }

      if (expandedAnimationId == null) {
         AnimationService.MatchedAnimation matched = attackContinuation
            ? NonAnimationMatcher.findAutomaticAttack(world, combined, Set.of(), null, false, null, Set.of(), null, predecessorAnimationId)
            : NonAnimationMatcher.findAutomatic(world, combined, Set.of(), null, false, null, Set.of(), null, predecessorAnimationId);
         return matched == null ? null : AnimationDefinitions.getDefinition(matched.animationId());
      }

      if (!AnimationService.isJoinTransitionAllowed(predecessorAnimationId, expandedAnimationId)) {
         return null;
      }

      AnimationDefinitions.MatchResult match = AnimationDefinitions.match(combined);
      List<AnimationDefinitions.Definition> candidates = match.candidatesSorted();
      if (candidates != null && !candidates.isEmpty()) {
         for (AnimationDefinitions.Definition candidate : candidates) {
            if (candidate != null && expandedAnimationId.equals(candidate.id())) {
               return attackContinuation && !candidate.attackEligible() ? null : candidate;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static List<Entity> collectEntities(ServerWorld world, List<UUID> actorUuids) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         List<Entity> actors = new ArrayList<>();

         for (UUID actorUuid : actorUuids) {
            Entity entity = world.getEntity(actorUuid);
            if (entity != null && !entity.isRemoved()) {
               actors.add(entity);
            }
         }

         actors.sort(Comparator.comparingInt(Entity::getId));
         return actors;
      } else {
         return List.of();
      }
   }

   private static Set<NonLiquidRoles.InjectorRole> resolveEffectiveInjectorRolesForPlayer(Identifier animationId, ServerPlayerEntity player) {
      NonLiquidRoles.LiquidRoles roles = AnimationGameplayMetadata.liquidRoles(animationId);
      if (roles != null && !roles.injectorRoles().isEmpty()) {
         LinkedHashSet<NonLiquidRoles.InjectorRole> out = new LinkedHashSet<>();

         for (NonLiquidRoles.InjectorRole role : roles.injectorRoles().values()) {
            NonLiquidRoles.InjectorRole effective = role;
            if (effective == NonLiquidRoles.InjectorRole.V
               && player != null
               && NonGenderSystem.isOnlyMale(player)
               && getConfig().convertMaleOnlyVInjectionsToA()) {
               effective = NonLiquidRoles.InjectorRole.A;
            }

            if (effective != null && effective != NonLiquidRoles.InjectorRole.NONE) {
               out.add(effective);
            }
         }

         return Set.copyOf(out);
      } else {
         return Set.of();
      }
   }

   private static boolean isLiquidInjectorAccepted(@Nullable NonLiquidRoles.InjectorRole role, @Nullable NonLiquidTankType tankType) {
      if (role != NonLiquidRoles.InjectorRole.V && role != NonLiquidRoles.InjectorRole.A) {
         return false;
      }

      if (tankType == null) {
         return true;
      }

      NonLiquidRoles.InjectorRole effective = role;
      if (tankType == NonLiquidTankType.A && role == NonLiquidRoles.InjectorRole.V && getConfig().convertMaleOnlyVInjectionsToA()) {
         effective = NonLiquidRoles.InjectorRole.A;
      }

      return tankType.accepts(effective);
   }

   private static NeedsOfNature.LiquidGainProfile scaleLiquidGainProfile(NeedsOfNature.LiquidGainProfile gain, double scale) {
      if (gain != null && gain.amount() > 0) {
         double safe = Double.isFinite(scale) && !(scale < 0.0) ? scale : 1.0;
         int amount = applyLiquidGainMultiplier(gain.amount(), safe);
         int xpEligible = Math.min(amount, applyLiquidGainMultiplier(gain.xpEligibleAmount(), safe));
         Map<Identifier, Integer> entityAmounts = new LinkedHashMap<>();

         for (Entry<Identifier, Integer> entry : gain.entityAmounts().entrySet()) {
            int scaled = applyLiquidGainMultiplier(entry.getValue(), safe);
            if (scaled > 0) {
               entityAmounts.put(entry.getKey(), scaled);
            }
         }

         return new NeedsOfNature.LiquidGainProfile(
            amount,
            xpEligible,
            amount > 0 ? gain.composition() : LiquidHolder.LiquidComposition.EMPTY,
            amount > 0 ? gain.entityTypeId() : null,
            Map.copyOf(entityAmounts),
            amount > 0 ? gain.donorVariantData() : null,
            amount > 0 ? gain.recentDonors() : List.of()
         );
      } else {
         return new NeedsOfNature.LiquidGainProfile(0, 0, LiquidHolder.LiquidComposition.EMPTY, null, Map.of(), null, List.of());
      }
   }

   private static double resolveLiquidGainMultiplier(@Nullable Identifier animationId) {
      Double value = AnimationGameplayMetadata.liquidGainMultiplier(animationId);
      return value != null && Double.isFinite(value) && !(value < 0.0) ? value : 1.0;
   }

   private static int applyLiquidGainMultiplier(int baseGain, double multiplier) {
      if (baseGain <= 0) {
         return 0;
      } else {
         double safe = Double.isFinite(multiplier) && !(multiplier < 0.0) ? multiplier : 1.0;
         long scaled = Math.round(baseGain * safe);
         if (scaled <= 0L) {
            return 0;
         } else {
            return scaled > 2147483647L ? Integer.MAX_VALUE : (int)scaled;
         }
      }
   }

   private static NeedsOfNature.LiquidGainProfile applyAccessoryLiquidGainMultiplier(ServerPlayerEntity player, NeedsOfNature.LiquidGainProfile gain) {
      if (player != null && gain != null && gain.amount() > 0) {
         double multiplier = NonTrinketsIntegration.getActiveTankAccessoryEffects(player).liquidGainMultiplier();
         if (Double.isFinite(multiplier) && !(Math.abs(multiplier - 1.0) < 1.0E-6)) {
            int amount = applyLiquidGainMultiplier(gain.amount(), multiplier);
            int xpEligible = Math.min(amount, applyLiquidGainMultiplier(gain.xpEligibleAmount(), multiplier));
            Map<Identifier, Integer> entityAmounts = new LinkedHashMap<>();

            for (Entry<Identifier, Integer> entry : gain.entityAmounts().entrySet()) {
               int scaled = applyLiquidGainMultiplier(entry.getValue(), multiplier);
               if (scaled > 0) {
                  entityAmounts.put(entry.getKey(), scaled);
               }
            }

            return new NeedsOfNature.LiquidGainProfile(
               amount,
               xpEligible,
               amount > 0 ? gain.composition() : LiquidHolder.LiquidComposition.EMPTY,
               amount > 0 ? gain.entityTypeId() : null,
               Map.copyOf(entityAmounts),
               amount > 0 ? gain.donorVariantData() : null,
               amount > 0 ? gain.recentDonors() : List.of()
            );
         } else {
            return gain;
         }
      } else {
         return gain;
      }
   }

   private static NeedsOfNature.LiquidGainProfile applyApiLiquidGainModifier(ServerPlayerEntity player, NeedsOfNature.LiquidGainProfile gain) {
      if (player != null && gain != null && gain.amount() > 0) {
         int modifiedAmount = ((NonEvents.ModifyLiquidGain)NonEvents.MODIFY_LIQUID_GAIN.invoker()).modifyLiquidGain(player, gain.entityTypeId(), gain.amount());
         if (modifiedAmount <= 0) {
            return new NeedsOfNature.LiquidGainProfile(0, 0, LiquidHolder.LiquidComposition.EMPTY, null, Map.of(), null, List.of());
         } else {
            return modifiedAmount == gain.amount() ? gain : scaleLiquidGainProfile(gain, (double)modifiedAmount / gain.amount());
         }
      } else {
         return gain;
      }
   }

   private static int resolveEntityLiquidGainMl(@Nullable Identifier entityTypeId) {
      if (entityTypeId == null) {
         return 0;
      } else {
         String key = entityTypeId.toString();
         Map<String, Integer> configMap = getConfig().getLiquidGainByEntity();
         if (configMap.containsKey(key)) {
            Integer configured = configMap.get(key);
            int value = configured == null ? 0 : configured;
            return Math.max(0, Math.min(1000, value));
         } else {
            Integer packOverride = NonLiquidGainOverrides.getGain(entityTypeId);
            return packOverride != null ? Math.max(0, Math.min(1000, packOverride)) : 0;
         }
      }
   }

   private static int resolveEntityLiquidColorRgb(@Nullable Identifier entityTypeId) {
      if (entityTypeId == null) {
         return 16776427;
      }

      String key = entityTypeId.toString();
      String configuredHex = getConfig().getLiquidColorByEntity().get(key);
      Integer configuredRgb = NonLiquidColors.parseHexColor(configuredHex);
      if (configuredRgb != null) {
         return configuredRgb;
      }

      Integer packOverride = NonLiquidGainOverrides.getColorRgb(entityTypeId);
      return packOverride != null ? packOverride : 16776427;
   }

   private static int resolveMixedLiquidColorRgb() {
      String configuredHex = getConfig().getLiquidColorByEntity().get("needsofnature:mixed");
      Integer configuredRgb = NonLiquidColors.parseHexColor(configuredHex);
      if (configuredRgb != null) {
         return configuredRgb;
      }

      Integer packOverride = NonLiquidGainOverrides.getMixedColorRgb();
      return packOverride != null ? packOverride : 15920063;
   }

   private static Map<String, Integer> resolveEffectiveLiquidGainMap() {
      return NonLiquidSystem.resolveEffectiveLiquidGainMap();
   }

   private static Map<String, String> resolveEffectiveLiquidColorMap() {
      return NonLiquidSystem.resolveEffectiveLiquidColorMap();
   }

   private static int resolveStartStage(List<AnimationStageInfo> stages) {
      int startStage = 1;
      if (stages != null && !stages.isEmpty()) {
         Integer parsed = NonPeakStages.stageNumberFromId(stages.getFirst().animationId());
         if (parsed != null) {
            startStage = parsed;
         }
      }

      return startStage;
   }

   private static int resolveLoopSeconds(Identifier animationId, Integer stageNumber) {
      Identifier stageId = resolveStageId(animationId, stageNumber);
      Integer override = AnimationGameplayMetadata.stageSeconds(stageId);
      if (override != null) {
         return override;
      }

      Integer baseOverride = AnimationGameplayMetadata.stageSeconds(animationId);
      if (baseOverride != null) {
         return baseOverride;
      }

      Integer peakStage = NonPeakStages.getPeakStage(animationId);
      return stageNumber != null && Objects.equals(stageNumber, peakStage) ? CONFIG.getPeakLoopProgressSeconds() : CONFIG.getLoopProgressSeconds();
   }

   private static long resolveLoopAdvanceIntervalTicks(Identifier animationId, Integer stageNumber, @Nullable AnimationStageInfo currentStage) {
      long targetTicks = resolveLoopDurationTargetTicks(animationId, stageNumber);
      if (targetTicks < 0L) {
         return -1L;
      }

      Identifier stageId = resolveStageId(animationId, stageNumber);
      double exactCycleTicks = exactCycleTicks(stageId, currentStage);
      if (currentStage != null && currentStage.loop() && !(exactCycleTicks <= 0.0)) {
         double speed = resolveStageSpeed(currentStage);
         double cycleWorldTicks = exactCycleTicks / speed;
         if (Double.isFinite(cycleWorldTicks) && !(cycleWorldTicks <= 0.0)) {
            long cycleCount = Math.max(1L, Math.round(targetTicks / cycleWorldTicks));
            return Math.max(1L, Math.round(cycleCount * cycleWorldTicks));
         } else {
            return targetTicks;
         }
      } else {
         return targetTicks;
      }
   }

   private static long resolveLoopDurationTargetTicks(Identifier animationId, Integer stageNumber) {
      int loopSeconds = resolveLoopSeconds(animationId, stageNumber);
      if (loopSeconds < 0) {
         return -1L;
      }

      Identifier stageId = resolveStageId(animationId, stageNumber);
      double multiplier = AnimationGameplayMetadata.stageDurationMultiplier(stageId);
      return Math.max(1L, Math.round(loopSeconds * 20.0 * multiplier));
   }

   private static double exactCycleTicks(@Nullable Identifier stageId, @Nullable AnimationStageInfo stage) {
      double exact = AnimationGameplayMetadata.cycleTicks(stageId);
      if (exact > 0.0) {
         return exact;
      } else {
         return stage == null ? 0.0 : Math.max(0L, stage.lengthTicks());
      }
   }

   private static Identifier resolveStageId(Identifier animationId, Integer stageNumber) {
      return animationId != null && stageNumber != null ? AnimationIds.stageId(animationId, stageNumber) : animationId;
   }

   private static void queueDefeatedTailForInstance(ServerWorld world, UUID instanceId, List<UUID> actorUuids) {
      if (world != null && instanceId != null && actorUuids != null && !actorUuids.isEmpty()) {
         if (DEFEATED_QUEUED_INSTANCES.add(instanceId)) {
            ServerPlayerEntity player = NonPlayerAttackSystem.resolveAttackVictim(world, instanceId, actorUuids);
            if (player == null) {
               DEFEATED_QUEUED_INSTANCES.remove(instanceId);
            } else {
               AnimationService.MatchedAnimation defeatMatch = NonDefeatedAnimationSelector.find(
                  world, player, resolveInstanceBaseAnimationId(world, instanceId)
               );
               if (defeatMatch == null) {
                  DEFEATED_QUEUED_INSTANCES.remove(instanceId);
                  LOGGER.warn(
                     "[NoN] No defeat-tag animation matched for player actor. world={} instance={} player={}",
                     new Object[]{world.getRegistryKey().getValue(), instanceId, player.getName().getString()}
                  );
                  sendDebugChat(player, NonDebugChatCategory.SETUP, Text.translatable("debug.needsofnature.no_defeat_tag_animation"));
               } else {
                  ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
                  if (snapshot == null) {
                     DEFEATED_QUEUED_INSTANCES.remove(instanceId);
                  } else {
                     boolean inheritIgnoreAttackers = snapshot.ignoreAttackers();
                     AnimationStartContext defeatedContext = NonPlayerAttackSystem.defeatedTailContext(snapshot.startContext(), player.getUuid());
                     boolean queued = enqueueAnimation(
                        world,
                        defeatMatch.animationId(),
                        List.of(player),
                        -1,
                        NonPlayerAttackSystem.defeatedDamageBehavior(defeatedContext),
                        inheritIgnoreAttackers,
                        defeatedContext,
                        player
                     );
                     if (!queued) {
                        DEFEATED_QUEUED_INSTANCES.remove(instanceId);
                     } else {
                        NonAdvancementHooks.recordTossedAround(player);
                     }
                  }
               }
            }
         }
      }
   }

   @Nullable
   private static NeedsOfNature.LastDefeatedCandidate findLastDefeatedCandidate(ServerWorld world, ServerPlayerEntity player, MobEntity damagingMob) {
      if (world != null && player != null && damagingMob != null) {
         NeedsOfNature.LastDefeatedCandidate direct = resolveLastDefeatedCandidate(world, player, damagingMob);
         if (direct != null) {
            return direct;
         }

         int radius = getConfig().getLastDefeatedSearchRadius();
         Box box = player.getBoundingBox().expand(radius);
         List<MobEntity> nearby = world.getEntitiesByClass(
            MobEntity.class, box, mobx -> mobx != null && mobx != damagingMob && mobx.isAlive() && !mobx.isRemoved()
         );
         nearby.sort(Comparator.comparingDouble(player::squaredDistanceTo));

         for (MobEntity mob : nearby) {
            NeedsOfNature.LastDefeatedCandidate candidate = resolveLastDefeatedCandidate(world, player, mob);
            if (candidate != null) {
               return candidate;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @Nullable
   private static NeedsOfNature.LastDefeatedCandidate resolveLastDefeatedCandidate(ServerWorld world, ServerPlayerEntity player, MobEntity mob) {
      if (world != null && player != null && mob != null) {
         if (mob.getEntityWorld() != world || player.getEntityWorld() != world) {
            return null;
         }

         if (mob.isAlive() && !mob.isRemoved()) {
            if (!(mob instanceof EnergyHolder holder && holder.getEnergy() >= getConfig().getLastDefeatedEnergyThreshold())) {
               return null;
            } else {
               if (!canMobAttackPlayer(player) || isMobAttackFailsafeActive(world, mob.getUuid())) {
                  return null;
               }

               if (AnimationService.isActorPendingOrActive(world, mob.getUuid())) {
                  return null;
               }

               List<Entity> actors = new ArrayList<>(2);
               actors.add(mob);
               actors.add(player);
               actors.sort(Comparator.comparingInt(Entity::getId));
               NonGatherSystem.GatherCandidate gatherCandidate = NonGatherSystem.findUnprotectedGatherCandidate(world, actors, true, mob, player);
               if (gatherCandidate == null) {
                  return null;
               }

               AnimationService.MatchedAnimation defeatMatch = NonDefeatedAnimationSelector.find(world, player, gatherCandidate.animationId());
               return defeatMatch == null ? null : new NeedsOfNature.LastDefeatedCandidate(mob, gatherCandidate, defeatMatch);
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean hasVanillaTotemReady(ServerPlayerEntity player) {
      return player == null ? false : player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) || player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
   }

   @Nullable
   private static Identifier resolveInstanceBaseAnimationId(ServerWorld world, UUID instanceId) {
      if (world != null && instanceId != null) {
         NeedsOfNature.PeakState peakState = PEAK_STATES.get(instanceId);
         if (peakState != null && peakState.animationId() != null) {
            return NonPeakStages.baseAnimationId(peakState.animationId());
         }

         AnimationStageInfo stage = AnimationService.getCurrentStage(world, instanceId);
         return stage != null && stage.animationId() != null ? NonPeakStages.baseAnimationId(stage.animationId()) : null;
      } else {
         return null;
      }
   }

   private static void tickLiquidDecay(ServerWorld world) {
      NonLiquidSystem.tickLiquidDecay(world);
   }

   private static void updateFilledEffect(ServerPlayerEntity player, LiquidHolder holder) {
      NonLiquidSystem.updateFilledEffect(player, holder);
   }

   public static void updateEnergizedEffect(ServerPlayerEntity player, EnergyHolder holder) {
      if (player != null && holder != null) {
         int amplifier = getEnergizedAmplifier(holder.getEnergy());
         RegistryEntry<StatusEffect> entry = Registries.STATUS_EFFECT.getEntry(NonStatusEffects.ENERGIZED);
         StatusEffectInstance current = player.getStatusEffect(entry);
         if (amplifier < 0) {
            if (current != null) {
               player.removeStatusEffect(entry);
            }
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

   private static int getEnergizedAmplifier(int energy) {
      if (energy >= 200) {
         return 2;
      } else if (energy >= 150) {
         return 1;
      } else {
         return energy >= 100 ? 0 : -1;
      }
   }

   private static void scheduleReceiverDripOnPeak(ServerWorld world, List<UUID> actorUuids, List<String> actorKeys, Identifier animationId, long nowTick) {
      if (world != null && actorUuids != null && !actorUuids.isEmpty()) {
         if (actorKeys != null && actorKeys.size() == actorUuids.size()) {
            NonLiquidRoles.LiquidRoles roles = AnimationGameplayMetadata.liquidRoles(animationId);
            if (roles != null) {
               Set<String> receiverKeys = roles.receiverKeys();
               if (!receiverKeys.isEmpty()) {
                  int count = Math.min(actorUuids.size(), actorKeys.size());
                  long nextTick = nowTick + 20L;
                  long endTick = nowTick + 200L;
                  NeedsOfNature.LiquidGainProfile gain = resolveLiquidGainProfile(world, actorUuids, actorKeys, animationId);
                  int tintRgb = gain.composition() == LiquidHolder.LiquidComposition.ENTITY
                     ? resolveEntityLiquidColorRgb(gain.entityTypeId())
                     : resolveMixedLiquidColorRgb();

                  for (int i = 0; i < count; i++) {
                     if (receiverKeys.contains(actorKeys.get(i))
                        && world.getEntity(actorUuids.get(i)) instanceof MobEntity mob
                        && !(mob instanceof HorseLiquidCollectorEntity)
                        && mob.isAlive()
                        && !mob.isRemoved()) {
                        RECEIVER_DRIP.put(mob.getUuid(), new NeedsOfNature.ReceiverDripState(world.getRegistryKey(), nextTick, endTick, tintRgb));
                     }
                  }
               }
            }
         }
      }
   }

   private static void tickReceiverDripParticles(ServerWorld world, long nowTick) {
      if (world != null && !RECEIVER_DRIP.isEmpty()) {
         Map<UUID, NeedsOfNature.ReceiverDripState> updates = new HashMap<>();
         Set<UUID> removals = new HashSet<>();

         for (Entry<UUID, NeedsOfNature.ReceiverDripState> entry : RECEIVER_DRIP.entrySet()) {
            UUID uuid = entry.getKey();
            NeedsOfNature.ReceiverDripState state = entry.getValue();
            if (state.worldKey.equals(world.getRegistryKey())) {
               if (nowTick > state.endTick) {
                  removals.add(uuid);
               } else if (!(world.getEntity(uuid) instanceof MobEntity mob && mob.isAlive() && !mob.isRemoved())) {
                  removals.add(uuid);
               } else if (nowTick >= state.nextTick) {
                  Vec3d spawnPos = resolveReceiverDripSpawnPos(mob);
                  world.spawnParticles(NonParticles.liquidFalling(state.tintRgb), spawnPos.x, spawnPos.y, spawnPos.z, 1, 0.0, 0.0, 0.0, 0.0);
                  long nextTick = state.nextTick + 20L;
                  if (nextTick > state.endTick) {
                     removals.add(uuid);
                  } else {
                     updates.put(uuid, new NeedsOfNature.ReceiverDripState(state.worldKey, nextTick, state.endTick, state.tintRgb));
                  }
               }
            }
         }

         for (UUID uuid : removals) {
            RECEIVER_DRIP.remove(uuid);
         }

         for (Entry<UUID, NeedsOfNature.ReceiverDripState> update : updates.entrySet()) {
            if (!removals.contains(update.getKey())) {
               RECEIVER_DRIP.put(update.getKey(), update.getValue());
            }
         }
      }
   }

   private static Vec3d resolveReceiverDripSpawnPos(MobEntity mob) {
      Vec3d localOffset = resolveReceiverDripOffset(mob);
      double yawRad = Math.toRadians(mob.getYaw());
      double sin = Math.sin(yawRad);
      double cos = Math.cos(yawRad);
      Vec3d forward = new Vec3d(-sin, 0.0, cos);
      Vec3d right = new Vec3d(forward.z, 0.0, -forward.x);
      return new Vec3d(mob.getX(), mob.getY(), mob.getZ()).add(right.multiply(localOffset.x)).add(0.0, localOffset.y, 0.0).add(forward.multiply(localOffset.z));
   }

   private static Vec3d resolveReceiverDripOffset(MobEntity mob) {
      Identifier entityId = Registries.ENTITY_TYPE.getId(mob.getType());
      return RECEIVER_DRIP_OFFSETS.getOrDefault(entityId, RECEIVER_DRIP_OFFSET_DEFAULT);
   }

   private void registerVoluntaryStopRequests() {
      ServerPlayNetworking.registerGlobalReceiver(StopVoluntaryAnimationC2SPayload.ID, (payload, context) -> context.server().execute(() -> {
         ServerPlayerEntity player = context.player();
         if (player.getEntityWorld() instanceof ServerWorld world) {
            UUID var7 = payload.instanceId();
            ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, var7);
            NonAnimationSessionData data = snapshot == null ? null : getNonSessionData(snapshot.startContext());
            if (data != null && data.isVoluntary() && !data.isPregnancy()) {
               if (snapshot.actorUuids().contains(player.getUuid())) {
                  clearQueuedAnimations(world, player);
                  AnimationService.stopAnimation(world, var7);
               }
            }
         }
      }));
   }

   private void registerManualPeakRequests() {
      ServerPlayNetworking.registerGlobalReceiver(
         StartManualPeakC2SPayload.ID,
         (payload, context) -> context.server()
            .execute(
               () -> {
                  ServerPlayerEntity player = context.player();
                  if (player.getEntityWorld() instanceof ServerWorld world) {
                     if (!player.isSpectator()) {
                        if (!hasActivePlayerAnimation(player)) {
                           if (player.isOnGround()) {
                              NonManualPeakDefinitions.Selection selection = NonManualPeakDefinitions.select(world, player);
                              if (selection != null) {
                                 List<Entity> actors = List.of(player);
                                 AnimationDefinitions.Definition definition = selection.definition();
                                 UUID instanceId = startAnimationNowWithContext(
                                    world,
                                    definition.id(),
                                    actors,
                                    selection.actorKeys(),
                                    selection.stages(),
                                    NonAnimationDamageBehavior.STOP_ON_DAMAGE,
                                    false,
                                    null,
                                    buildModeContext(false)
                                 );
                                 if (instanceId != null && selection.matchedHeldItemId() != null) {
                                    NonAdvancementHooks.grantAcquireHardware(player);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            )
      );
   }

   private void registerFillBottleUseTrigger() {
      UseItemCallback.EVENT.register((UseItemCallback)(player, world, hand) -> {
         if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer) {
            return (ActionResult)(tryStartFillBottleAnimationOnUse(serverWorld, serverPlayer, hand) ? ActionResult.SUCCESS : ActionResult.PASS);
         } else {
            return ActionResult.PASS;
         }
      });
      UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hitResult) -> {
         if (!(world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
         } else if (world.getFluidState(hitResult.getBlockPos()).isIn(FluidTags.WATER)) {
            return ActionResult.PASS;
         } else if (world.getBlockState(hitResult.getBlockPos()).isOf(Blocks.WATER_CAULDRON)) {
            return ActionResult.PASS;
         } else {
            return (ActionResult)(tryStartFillBottleAnimationOnUse(serverWorld, serverPlayer, hand) ? ActionResult.SUCCESS : ActionResult.PASS);
         }
      });
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer) {
            return (ActionResult)(tryStartFillBottleAnimationOnUse(serverWorld, serverPlayer, hand) ? ActionResult.SUCCESS : ActionResult.PASS);
         } else {
            return ActionResult.PASS;
         }
      });
   }

   private static boolean tryStartFillBottleAnimationOnUse(ServerWorld world, ServerPlayerEntity player, Hand hand) {
      if (world == null || player == null || hand == null) {
         return false;
      }

      if (player.isSpectator()) {
         return false;
      }

      if (player.isTouchingWater()) {
         return false;
      }

      if (hasActivePlayerAnimation(player)) {
         return false;
      }

      if (!player.isOnGround()) {
         return false;
      }

      if (player.getPitch() < 80.0F) {
         return false;
      }

      if (!player.getStackInHand(hand).isOf(Items.GLASS_BOTTLE)) {
         return false;
      }

      if (player instanceof LiquidHolder liquidHolder) {
         updateFilledEffect(player, liquidHolder);
         int requiredMl = getFillBottleRequiredMl(liquidHolder);
         if (liquidHolder.getLiquidStored() < requiredMl) {
            return false;
         } else if (!hasFilledStageOneOrHigher(player)) {
            return false;
         } else {
            Identifier fillBottleId = findMatchedAnimationIdByTags(List.of(player), Set.of("fillBottle"));
            if (fillBottleId == null) {
               return false;
            } else {
               AnimationDefinitions.Definition fillDefinition = AnimationDefinitions.getDefinition(fillBottleId);
               if (fillDefinition == null) {
                  return false;
               } else if (!NonItemSystem.consumeOneGlassBottle(player, hand)) {
                  return false;
               } else {
                  List<Entity> actors = List.of(player);
                  List<String> actorKeys = AnimationDefinitions.resolveActorKeys(fillDefinition, actors, world.getRandom());
                  UUID instanceId = startAnimationNowWithContext(
                     world,
                     fillDefinition.id(),
                     actors,
                     actorKeys,
                     fillDefinition.stages(),
                     NonAnimationDamageBehavior.STOP_ON_DAMAGE,
                     false,
                     null,
                     buildModeContext(false)
                  );
                  if (instanceId != null) {
                     FILL_BOTTLE_STATES.put(instanceId, new NeedsOfNature.FillBottleState(player.getUuid(), true));
                     return true;
                  } else {
                     NonItemSystem.refundGlassBottle(player);
                     return false;
                  }
               }
            }
         }
      } else {
         return false;
      }
   }

   private static void applyAttackEscapeDamage(
      ServerWorld world, ServerPlayerEntity player, @Nullable List<UUID> actorUuids, @Nullable UUID preferredAttackerUuid
   ) {
      NonAttackSystem.applyAttackEscapeDamage(world, player, actorUuids, preferredAttackerUuid);
   }

   private static void scheduleAttackEscapeResolution(
      ServerWorld world,
      ServerPlayerEntity player,
      @Nullable List<UUID> actorUuids,
      @Nullable UUID preferredAttackerUuid,
      boolean allowReattack,
      int invulnerabilityTicks
   ) {
      if (world != null && player != null) {
         List<UUID> actors = actorUuids != null && !actorUuids.isEmpty() ? List.copyOf(actorUuids) : List.of();
         PENDING_ATTACK_ESCAPE_RESOLUTIONS.put(
            player.getUuid(),
            new NeedsOfNature.PendingAttackEscapeResolution(
               world.getRegistryKey(), actors, preferredAttackerUuid, world.getTime() + 1L, allowReattack, invulnerabilityTicks
            )
         );
      }
   }

   private static void tickPendingAttackEscapeResolutions(ServerWorld world, long nowTick) {
      if (world != null && !PENDING_ATTACK_ESCAPE_RESOLUTIONS.isEmpty()) {
         RegistryKey<World> worldKey = world.getRegistryKey();

         for (Entry<UUID, NeedsOfNature.PendingAttackEscapeResolution> entry : PENDING_ATTACK_ESCAPE_RESOLUTIONS.entrySet()) {
            UUID playerUuid = entry.getKey();
            NeedsOfNature.PendingAttackEscapeResolution resolution = entry.getValue();
            if (playerUuid != null && resolution != null) {
               if (worldKey.equals(resolution.worldKey())
                  && nowTick >= resolution.dueTick()
                  && PENDING_ATTACK_ESCAPE_RESOLUTIONS.remove(playerUuid, resolution)) {
                  ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerUuid);
                  if (player != null && player.getEntityWorld() == world && player.isAlive() && !player.isRemoved()) {
                     POST_ESCAPE_DAMAGE_INVULNERABLE_UNTIL_BY_PLAYER.remove(playerUuid);
                     applyAttackEscapeDamage(world, player, resolution.actorUuids(), resolution.preferredAttackerUuid());
                     if (player.isAlive() && !player.isRemoved()) {
                        if (resolution.invulnerabilityTicks() > 0) {
                           setPostEscapeDamageInvulnerable(player, resolution.invulnerabilityTicks());
                        }

                        if (resolution.allowReattack()) {
                           startPostEscapeReattack(world, player, resolution.actorUuids());
                        }
                     }
                  }
               }
            } else if (playerUuid != null) {
               PENDING_ATTACK_ESCAPE_RESOLUTIONS.remove(playerUuid);
            }
         }
      }
   }

   private static void startPostEscapeReattack(ServerWorld world, ServerPlayerEntity player, @Nullable List<UUID> actorUuids) {
      if (world != null && player != null && player.isAlive() && !player.isRemoved()) {
         NonAttackSystem.startNearbyPostEscapeGathers(world, player, actorUuids);
         requestOriginalAttackers(world, player, actorUuids);
      }
   }

   private static void requestOriginalAttackers(ServerWorld world, ServerPlayerEntity player, @Nullable List<UUID> actorUuids) {
      if (world != null && player != null && player.isAlive() && !player.isRemoved()) {
         if (actorUuids != null && !actorUuids.isEmpty()) {
            for (UUID actor : actorUuids) {
               if (actor != null
                  && !actor.equals(player.getUuid())
                  && world.getEntity(actor) instanceof MobEntity mob
                  && mob.isAlive()
                  && mob instanceof EnergyHolder holder) {
                  holder.requestAttackOnPlayer(world, player);
               }
            }
         }
      }
   }

   public static boolean isDefeatedInstance(ServerWorld world, UUID instanceId) {
      if (world != null && instanceId != null) {
         NeedsOfNature.PeakState peakState = PEAK_STATES.get(instanceId);
         if (peakState != null && hasAnimationTagWithPrefix(peakState.animationId, "defeat.")) {
            return true;
         } else {
            AnimationStageInfo stage = AnimationService.getCurrentStage(world, instanceId);
            if (stage != null && stage.animationId() != null) {
               Identifier baseAnimationId = NonPeakStages.baseAnimationId(stage.animationId());
               return hasAnimationTagWithPrefix(baseAnimationId, "defeat.");
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private static boolean isManualPeakAnimation(Identifier animationId) {
      return hasAnimationTag(animationId, "manualPeak");
   }

   private static boolean isFillBottleAnimation(Identifier animationId) {
      return hasAnimationTag(animationId, "fillBottle");
   }

   private static boolean isBirthAnimation(Identifier animationId) {
      return hasAnimationTag(animationId, "birth") || hasAnimationTagWithPrefix(animationId, "birth_");
   }

   private static boolean isWaterAnimation(Identifier animationId) {
      if (animationId == null) {
         return false;
      }

      AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(NonPeakStages.baseAnimationId(animationId));
      return definition != null && definition.waterRequirement() != AnimationDefinitions.WaterRequirement.NONE;
   }

   private static boolean hasAnimationTag(Identifier animationId, String tag) {
      if (animationId != null && tag != null && !tag.isBlank()) {
         AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(NonPeakStages.baseAnimationId(animationId));
         if (definition == null) {
            return false;
         }

         String expected = tag.trim().toLowerCase(Locale.ROOT);
         if (expected.isEmpty()) {
            return false;
         }

         Set<String> tags = definition.animationTags();
         if (tags != null && !tags.isEmpty()) {
            for (String value : tags) {
               if (value != null && expected.equals(value.trim().toLowerCase(Locale.ROOT))) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean hasAnimationTagWithPrefix(Identifier animationId, String prefix) {
      if (animationId != null && prefix != null && !prefix.isBlank()) {
         AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(NonPeakStages.baseAnimationId(animationId));
         if (definition == null) {
            return false;
         }

         String expectedPrefix = prefix.trim().toLowerCase(Locale.ROOT);
         if (expectedPrefix.isEmpty()) {
            return false;
         }

         Set<String> tags = definition.animationTags();
         if (tags != null && !tags.isEmpty()) {
            for (String value : tags) {
               if (value != null && value.trim().toLowerCase(Locale.ROOT).startsWith(expectedPrefix)) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static Identifier findMatchedAnimationIdByTags(List<? extends Entity> actors, Set<String> requiredTags) {
      if (actors != null && !actors.isEmpty() && requiredTags != null && !requiredTags.isEmpty()) {
         List<Entity> sorted = new ArrayList<>(actors.size());
         sorted.addAll(actors);
         sorted.sort(Comparator.comparingInt(Entity::getId));
         Set<String> safeTags = new LinkedHashSet<>();

         for (String tag : requiredTags) {
            if (tag != null) {
               String normalized = tag.trim();
               if (!normalized.isEmpty()) {
                  safeTags.add(normalized);
               }
            }
         }

         return safeTags.isEmpty() ? null : AnimationService.findMatchedAnimationId(sorted, safeTags);
      } else {
         return null;
      }
   }

   private static void maybeStartPregnancyOnPeak(
      ServerWorld world,
      List<UUID> actorUuids,
      List<String> actorKeys,
      Identifier animationId,
      Map<UUID, List<NeedsOfNature.PregnancyDonor>> accessoryDonorsByReceiver
   ) {
      if (!getConfig().isLiquidBasedPregnancyEnabled()) {
         if (world != null && actorUuids != null && actorKeys != null) {
            if (!actorUuids.isEmpty() && !actorKeys.isEmpty()) {
               if (actorUuids.size() == actorKeys.size()) {
                  NonLiquidRoles.LiquidRoles roles = AnimationGameplayMetadata.liquidRoles(animationId);
                  if (roles != null) {
                     Map<String, NonLiquidRoles.InjectorRole> injectorRoles = roles.injectorRoles();
                     if (!injectorRoles.isEmpty()) {
                        List<NeedsOfNature.PregnancyDonor> donors = new ArrayList<>();

                        for (int i = 0; i < actorUuids.size(); i++) {
                           if (isLiquidInjectorAccepted(injectorRoles.get(actorKeys.get(i)), NonLiquidTankType.V)) {
                              Entity actor = world.getEntity(actorUuids.get(i));
                              if (actor != null && !(actor instanceof ServerPlayerEntity)) {
                                 donors.add(
                                    new NeedsOfNature.PregnancyDonor(Registries.ENTITY_TYPE.getId(actor.getType()), capturePregnancyVariantData(world, actor))
                                 );
                              }
                           }
                        }

                        long nowTick = getGlobalTick(world);

                        for (ServerPlayerEntity player : collectPlayerReceiverActors(world, actorUuids, actorKeys, animationId)) {
                           if (player != null
                              && player.isAlive()
                              && !player.isRemoved()
                              && !NonGenderSystem.isOnlyMale(player)
                              && getPregnantEntityTypeId(player) == null
                              && !NonTrinketsIntegration.blocksPregnancy(player)) {
                              NonAccessoryEffects accessoryEffects = NonTrinketsIntegration.getActiveTankAccessoryEffects(player);
                              List<NeedsOfNature.PregnancyDonor> eligibleDonors = new ArrayList<>(donors);
                              if (accessoryDonorsByReceiver != null) {
                                 eligibleDonors.addAll(accessoryDonorsByReceiver.getOrDefault(player.getUuid(), List.of()));
                              }

                              if (!eligibleDonors.isEmpty()) {
                                 NeedsOfNature.PregnancyDonor chosen = eligibleDonors.get(world.getRandom().nextInt(eligibleDonors.size()));
                                 Identifier chosenType = chosen.entityTypeId();
                                 int chancePercent = NonEntityProfiles.resolve(chosenType).pregnancyChancePercent();
                                 if (chancePercent > 0) {
                                    chancePercent = applyFertilityIncreaserChance(player, chancePercent);
                                    chancePercent = (int)Math.round(chancePercent * accessoryEffects.pregnancyChanceMultiplier());
                                    chancePercent = Math.max(0, Math.min(100, chancePercent));
                                    if (chancePercent > 0 && (chancePercent >= 100 || world.getRandom().nextInt(100) < chancePercent)) {
                                       long durationTicks = Math.max(
                                          1L, Math.round(getConfig().getPregnancyDurationTicks() * accessoryEffects.pregnancyDurationMultiplier())
                                       );
                                       beginPregnancy(player, chosenType, nowTick + durationTicks, chosen.variantData());
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
   }

   @Nullable
   static NeedsOfNature.PregnancyVariantData capturePregnancyVariantData(ServerWorld world, Entity entity) {
      if (world != null && entity != null) {
         Map<String, String> components = new LinkedHashMap<>();

         for (NeedsOfNature.VariantComponentDescriptor descriptor : PREGNANCY_VARIANT_COMPONENTS) {
            descriptor.capture(world, entity, components);
         }

         Integer horseMarking = null;
         if (entity instanceof HorseEntity horse) {
            horseMarking = horse.getMarking().getIndex();
         }

         return normalizePregnancyVariantData(new NeedsOfNature.PregnancyVariantData(components, horseMarking));
      } else {
         return null;
      }
   }

   @Nullable
   public static NeedsOfNature.PregnancyVariantData normalizePregnancyVariantData(@Nullable NeedsOfNature.PregnancyVariantData data) {
      return data != null && !data.isEmpty() ? data : null;
   }

   @Nullable
   public static String encodePregnancyVariantData(@Nullable NeedsOfNature.PregnancyVariantData data) {
      NeedsOfNature.PregnancyVariantData normalized = normalizePregnancyVariantData(data);
      return normalized == null ? null : HOST_CONFIG_SYNC_GSON.toJson(normalized);
   }

   @Nullable
   public static NeedsOfNature.PregnancyVariantData decodePregnancyVariantData(@Nullable String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            return normalizePregnancyVariantData(
               (NeedsOfNature.PregnancyVariantData)HOST_CONFIG_SYNC_GSON.fromJson(raw, NeedsOfNature.PregnancyVariantData.class)
            );
         } catch (RuntimeException e) {
            LOGGER.warn("[NoN] Ignoring malformed pregnancy variant data.", e);
            return null;
         }
      } else {
         return null;
      }
   }

   private static void applyPregnancyVariantData(ServerWorld world, Entity entity, @Nullable NeedsOfNature.PregnancyVariantData data) {
      NeedsOfNature.PregnancyVariantData normalized = normalizePregnancyVariantData(data);
      if (world != null && entity != null && normalized != null) {
         Map<String, String> components = normalized.components();

         for (NeedsOfNature.VariantComponentDescriptor descriptor : PREGNANCY_VARIANT_COMPONENTS) {
            descriptor.apply(world, entity, components.get(descriptor.key()));
         }

         if (normalized.horseMarkingIndex() != null && entity instanceof HorseEntity horse) {
            HorseMarking marking = HorseMarking.byIndex(normalized.horseMarkingIndex());
            HorseColor color = horse.getHorseColor();
            Method method = findMethod(horse.getClass(), "setHorseVariant", HorseColor.class, HorseMarking.class);
            if (method != null) {
               tryInvokeMethod(horse, method, color, marking);
            }
         }
      }
   }

   public static void applyPregnancyVariantDataForEgg(ServerWorld world, Entity entity, @Nullable NeedsOfNature.PregnancyVariantData data) {
      applyPregnancyVariantData(world, entity, data);
   }

   private static <T> NeedsOfNature.VariantComponentDescriptor registryVariant(
      String key, ComponentType<RegistryEntry<T>> componentType, RegistryKey<Registry<T>> registryKey
   ) {
      return new NeedsOfNature.VariantComponentDescriptor() {
         @Override
         public String key() {
            return key;
         }

         @Override
         public void capture(ServerWorld world, Entity entity, Map<String, String> out) {
            RegistryEntry<T> entry = (RegistryEntry<T>)entity.get(componentType);
            if (entry != null) {
               entry.getKey().<Identifier>map(RegistryKey::getValue).ifPresent(id -> out.put(key, id.toString()));
            }
         }

         @Override
         public void apply(ServerWorld world, Entity entity, String value) {
            Identifier id = Identifier.tryParse(value == null ? "" : value);
            if (id != null) {
               world.getRegistryManager()
                  .getOptional(registryKey)
                  .flatMap(registry -> registry.getEntry(id))
                  .ifPresent(entry -> entity.setComponent(componentType, entry));
            }
         }
      };
   }

   private static <T> NeedsOfNature.VariantComponentDescriptor lazyRegistryVariant(
      String key, ComponentType<LazyRegistryEntryReference<T>> componentType, RegistryKey<Registry<T>> registryKey
   ) {
      return new NeedsOfNature.VariantComponentDescriptor() {
         @Override
         public String key() {
            return key;
         }

         @Override
         public void capture(ServerWorld world, Entity entity, Map<String, String> out) {
            LazyRegistryEntryReference<T> value = (LazyRegistryEntryReference<T>)entity.get(componentType);
            if (value != null) {
               Optional<RegistryKey<T>> keyValue = value.getKey();
               if (keyValue.isEmpty()) {
                  keyValue = value.resolveEntry(world.getRegistryManager()).flatMap(RegistryEntry::getKey);
               }

               keyValue.<Identifier>map(RegistryKey::getValue).ifPresent(id -> out.put(key, id.toString()));
            }
         }

         @Override
         public void apply(ServerWorld world, Entity entity, String value) {
            Identifier id = Identifier.tryParse(value == null ? "" : value);
            if (id != null) {
               RegistryKey<T> valueKey = RegistryKey.of(registryKey, id);
               entity.setComponent(componentType, new LazyRegistryEntryReference(valueKey));
            }
         }
      };
   }

   private static <E extends Enum<E>> NeedsOfNature.VariantComponentDescriptor enumVariant(String key, ComponentType<E> componentType, Class<E> enumClass) {
      return new NeedsOfNature.VariantComponentDescriptor() {
         @Override
         public String key() {
            return key;
         }

         @Override
         public void capture(ServerWorld world, Entity entity, Map<String, String> out) {
            E value = (E)entity.get(componentType);
            if (value != null) {
               out.put(key, value.name());
            }
         }

         @Override
         public void apply(ServerWorld world, Entity entity, String value) {
            if (value != null && !value.isBlank()) {
               try {
                  entity.setComponent(componentType, Enum.valueOf(enumClass, value));
               } catch (IllegalArgumentException ignored) {
                  NeedsOfNature.LOGGER.warn("[NoN] Ignoring unknown pregnancy variant value {}={}.", key, value);
               }
            }
         }
      };
   }

   static int applyFertilityIncreaserChance(ServerPlayerEntity player, int baseChancePercent) {
      int base = Math.max(0, Math.min(100, baseChancePercent));
      return Math.max(base, getFertilityIncreaserChanceFloor(player));
   }

   static int getFertilityIncreaserChanceFloor(ServerPlayerEntity player) {
      if (player == null) {
         return 0;
      }

      int boostedFloor = 0;

      for (StatusEffectInstance effect : player.getStatusEffects()) {
         if (effect != null && effect.getEffectType().value() == NonStatusEffects.FERTILITY_INCREASER) {
            int floor = effect.getAmplifier() >= 1 ? 95 : 80;
            boostedFloor = Math.max(boostedFloor, floor);
         }
      }

      return boostedFloor;
   }

   private static void refreshPregnancyCueRuntime(ServerWorld world, UUID instanceId, long stageStartTick, Identifier fallbackAnimationId) {
      if (world != null && instanceId != null) {
         NeedsOfNature.PregnancyHatchState hatchState = PREGNANCY_HATCH_BY_INSTANCE.get(instanceId);
         if (hatchState == null) {
            PREGNANCY_CUE_RUNTIME_BY_INSTANCE.remove(instanceId);
         } else {
            AnimationStageInfo currentStage = AnimationService.getCurrentStage(world, instanceId);
            Identifier clipId = currentStage != null && currentStage.animationId() != null ? currentStage.animationId() : fallbackAnimationId;
            if (clipId == null) {
               PREGNANCY_CUE_RUNTIME_BY_INSTANCE.remove(instanceId);
            } else {
               Integer cueTick = AnimationCueIndex.getFirstCueTick(clipId, "birth");
               long startTick = Math.max(0L, stageStartTick);
               double birthLoopMultiplier = pregnancyBirthLoopSpeedMultiplier(clipId, hatchState);
               double runtimeStageSpeed = clampAnimationSpeed(resolveStageSpeed(currentStage) * birthLoopMultiplier);
               NeedsOfNature.PregnancyCueRuntime existing = PREGNANCY_CUE_RUNTIME_BY_INSTANCE.get(instanceId);
               if (existing == null
                  || !existing.worldKey().equals(world.getRegistryKey())
                  || existing.stageStartTick() != startTick
                  || !existing.clipId().equals(clipId)
                  || !Objects.equals(existing.cueTick(), cueTick)
                  || !(Math.abs(existing.stageSpeed() - runtimeStageSpeed) < 1.0E-4)) {
                  PREGNANCY_CUE_RUNTIME_BY_INSTANCE.put(
                     instanceId, new NeedsOfNature.PregnancyCueRuntime(world.getRegistryKey(), startTick, clipId, cueTick, false, runtimeStageSpeed)
                  );
                  applyPregnancyBirthLoopSpeed(world, instanceId, birthLoopMultiplier);
                  syncPregnancyLoopProgressDuration(world, instanceId, currentStage, runtimeStageSpeed);
                  Integer stageNumber = NonPeakStages.stageNumberFromId(clipId);
                  boolean cueExpected = stageNumber != null && stageNumber >= 2;
                  if (cueTick == null && cueExpected && (existing == null || !clipId.equals(existing.clipId()) || existing.cueTick() != null)) {
                     LOGGER.warn("[NoN] Missing birth cue in birth clip {}.", clipId);
                     ServerPlayerEntity player = findPlayerOnWorldServer(world, hatchState.playerUuid());
                     if (player != null && player.getEntityWorld() == world) {
                        sendDebugChat(
                           player, NonDebugChatCategory.SETUP, Text.translatable("debug.needsofnature.missing_birth_cue", new Object[]{clipId.toString()})
                        );
                     }
                  }
               }
            }
         }
      }
   }

   private static void applyPregnancyBirthLoopSpeed(ServerWorld world, UUID instanceId, double multiplier) {
      if (world != null && instanceId != null) {
         if (!(Math.abs(multiplier - 1.0) < 1.0E-4)) {
            ServerAnimationController.adjustSpeed(world, instanceId, multiplier, null);
         }
      }
   }

   private static void syncPregnancyLoopProgressDuration(ServerWorld world, UUID instanceId, @Nullable AnimationStageInfo stage, double stageSpeed) {
      if (world != null && instanceId != null && stage != null) {
         Integer stageNumber = NonPeakStages.stageNumberFromId(stage.animationId());
         if (stageNumber != null && stageNumber == 2) {
            long cycleWorldTicks = Math.max(1L, elapsedWorldTicksFromAnimationTicks(Math.max(1L, stage.lengthTicks()), stageSpeed));
            ServerAnimationController.setCurrentStageProgressDuration(world, instanceId, cycleWorldTicks);
         }
      }
   }

   private static double pregnancyBirthLoopSpeedMultiplier(Identifier clipId, NeedsOfNature.PregnancyHatchState hatchState) {
      Integer stageNumber = NonPeakStages.stageNumberFromId(clipId);
      if (stageNumber == null || stageNumber != 2) {
         return 1.0;
      }

      if (hatchState == null) {
         return 1.0;
      }

      int pendingRepeatsBeforeFinalLoop = Math.max(0, hatchState.remainingSpawns() - 2);
      if (pendingRepeatsBeforeFinalLoop <= 0) {
         return 1.0;
      }

      int variableRepeatCount = Math.max(1, hatchState.totalSpawnCount() - 2);
      double normalizedPending = Math.max(0.0, Math.min(1.0, (double)pendingRepeatsBeforeFinalLoop / variableRepeatCount));
      double eased = 1.0 - Math.pow(1.0 - normalizedPending, 2.5);
      double speedRange = 1.0;
      return Math.max(1.0, Math.min(2.0, 1.0 + speedRange * eased));
   }

   private static void tickPregnancyCueRuntime(ServerWorld world, long nowTick) {
      if (world != null && !PREGNANCY_CUE_RUNTIME_BY_INSTANCE.isEmpty()) {
         RegistryKey<World> worldKey = world.getRegistryKey();

         for (Entry<UUID, NeedsOfNature.PregnancyCueRuntime> entry : PREGNANCY_CUE_RUNTIME_BY_INSTANCE.entrySet()) {
            UUID instanceId = entry.getKey();
            NeedsOfNature.PregnancyCueRuntime runtime = entry.getValue();
            if (runtime.worldKey().equals(worldKey)) {
               Integer cueTick = runtime.cueTick();
               long cueDueTick = runtime.stageStartTick()
                  + elapsedWorldTicksFromAnimationTicks(cueTick == null ? -1.0 : cueTick.intValue(), runtime.stageSpeed());
               if (!runtime.cueTriggered() && cueTick != null && cueTick >= 0 && nowTick >= cueDueTick) {
                  NeedsOfNature.PregnancyCueRuntime triggered = new NeedsOfNature.PregnancyCueRuntime(
                     runtime.worldKey(), runtime.stageStartTick(), runtime.clipId(), runtime.cueTick(), true, runtime.stageSpeed()
                  );
                  if (PREGNANCY_CUE_RUNTIME_BY_INSTANCE.replace(instanceId, runtime, triggered)) {
                     handlePregnancyBirthCue(world, instanceId);
                     runtime = triggered;
                  }
               }

               if (runtime.cueTriggered()) {
                  routePregnancyBirthStageAfterCycle(world, instanceId, runtime, nowTick);
               }
            }
         }
      }
   }

   private static void tickPregnancy(ServerWorld world) {
      if (world != null && !world.getPlayers().isEmpty()) {
         long globalTick = getGlobalTick(world);

         for (ServerPlayerEntity player : world.getPlayers()) {
            tickPregnancyForPlayer(world, player, globalTick);
         }
      }
   }

   private static void tickPregnancyForPlayer(ServerWorld world, ServerPlayerEntity player, long nowTick) {
      if (world != null && player != null) {
         PregnancyHolder holder = getPregnancyHolder(player);
         if (holder != null) {
            Identifier entityTypeId = holder.getPregnantEntityTypeId();
            if (entityTypeId == null) {
               PREGNANCY_START_RETRY_TICKS.remove(player.getUuid());
            } else if (NonGenderSystem.isOnlyMale(player)) {
               clearPregnancyState(player, true);
            } else if (player.isAlive() && !player.isRemoved()) {
               if (!holder.isPregnancyPendingHatch()) {
                  long endTick = holder.getPregnancyEndTick();
                  if (endTick > nowTick) {
                     if (!player.hasStatusEffect(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.PREGNANT))) {
                        clearPregnancyState(player, false);
                     }

                     return;
                  }

                  markPregnancyPending(player, nowTick);
               } else {
                  refreshPendingPregnancyEffect(player);
               }

               tryStartPendingPregnancyHatch(world, player, nowTick);
            }
         }
      }
   }

   private static void tryStartPendingPregnancyHatch(ServerWorld world, ServerPlayerEntity player, long nowTick) {
      if (world != null && player != null) {
         PregnancyHolder holder = getPregnancyHolder(player);
         if (holder != null && holder.isPregnancyPendingHatch()) {
            if (NonGenderSystem.isOnlyMale(player)) {
               clearPregnancyState(player, true);
            } else if (player.isAlive() && !player.isRemoved() && !player.isSpectator()) {
               if (player.getEntityWorld() == world) {
                  UUID activeInstance = PREGNANCY_ACTIVE_INSTANCE_BY_PLAYER.get(player.getUuid());
                  if (activeInstance != null) {
                     if (hasActivePlayerAnimation(player)) {
                        return;
                     }

                     PREGNANCY_ACTIVE_INSTANCE_BY_PLAYER.remove(player.getUuid(), activeInstance);
                     PREGNANCY_HATCH_BY_INSTANCE.remove(activeInstance);
                     PREGNANCY_CUE_RUNTIME_BY_INSTANCE.remove(activeInstance);
                  }

                  if (hasActivePlayerAnimation(player)) {
                     queuePendingPregnancyHatch(world, player, holder, nowTick);
                  } else {
                     if (PREGNANCY_QUEUED_PLAYERS.contains(player.getUuid())) {
                        if (AnimationService.isActorPendingOrActive(world, player.getUuid())) {
                           return;
                        }

                        PREGNANCY_QUEUED_PLAYERS.remove(player.getUuid());
                     }

                     if (!player.isTouchingWater() && !player.isSubmergedInWater()) {
                        if (player.isOnGround()) {
                           long retryAt = PREGNANCY_START_RETRY_TICKS.getOrDefault(player.getUuid(), 0L);
                           if (retryAt <= nowTick) {
                              Identifier entityTypeId = holder.getPregnantEntityTypeId();
                              if (entityTypeId != null) {
                                 Identifier animationId = resolveBirthAnimationId(player, entityTypeId);
                                 if (animationId == null) {
                                    LOGGER.warn(
                                       "[NoN] Missing birth-tag animation for player {}; clearing pregnancy for {}.",
                                       player.getName().getString(),
                                       entityTypeId
                                    );
                                    sendDebugChat(
                                       player,
                                       NonDebugChatCategory.SETUP,
                                       Text.translatable("debug.needsofnature.missing_birth_animation", new Object[]{entityTypeId.toString()})
                                    );
                                    clearPregnancyState(player, true);
                                 } else {
                                    AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(animationId);
                                    if (definition == null) {
                                       LOGGER.warn("[NoN] Missing birth animation definition {}.", animationId);
                                       sendDebugChat(
                                          player,
                                          NonDebugChatCategory.SETUP,
                                          Text.translatable("debug.needsofnature.missing_birth_animation", new Object[]{entityTypeId.toString()})
                                       );
                                       clearPregnancyState(player, true);
                                    } else {
                                       List<Entity> actors = List.of(player);
                                       List<String> actorKeys = AnimationDefinitions.resolveActorKeys(definition, actors, world.getRandom());
                                       int spawnCount = resolvePregnancyOffspringCount(holder);
                                       NonEntityProfiles.ResolvedProfile profile = NonEntityProfiles.resolve(entityTypeId);
                                       AnimationStartContext startContext = buildPregnancyHatchContext(
                                          entityTypeId, profile, spawnCount, holder.getPregnancyVariantData()
                                       );
                                       UUID started = startAnimationNowWithContext(
                                          world,
                                          definition.id(),
                                          actors,
                                          actorKeys,
                                          definition.stages(),
                                          NonAnimationDamageBehavior.BLOCK_DAMAGE,
                                          true,
                                          null,
                                          startContext
                                       );
                                       if (started == null) {
                                          PREGNANCY_START_RETRY_TICKS.put(player.getUuid(), nowTick + 20L);
                                       } else {
                                          PREGNANCY_HATCH_BY_INSTANCE.putIfAbsent(
                                             started,
                                             new NeedsOfNature.PregnancyHatchState(
                                                player.getUuid(),
                                                entityTypeId,
                                                profile.birthEntityTypeId(),
                                                profile.birthMode(),
                                                spawnCount,
                                                spawnCount,
                                                0,
                                                holder.getPregnancyVariantData()
                                             )
                                          );
                                          PREGNANCY_ACTIVE_INSTANCE_BY_PLAYER.put(player.getUuid(), started);
                                          PREGNANCY_START_RETRY_TICKS.remove(player.getUuid());
                                          refreshPregnancyCueRuntime(world, started, nowTick, definition.id());
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

   private static void queuePendingPregnancyHatch(ServerWorld world, ServerPlayerEntity player, PregnancyHolder holder, long nowTick) {
      if (world != null && player != null && holder != null) {
         if (NonGenderSystem.isOnlyMale(player)) {
            clearPregnancyState(player, true);
         } else {
            UUID playerUuid = player.getUuid();
            if (!PREGNANCY_QUEUED_PLAYERS.contains(playerUuid)) {
               long retryAt = PREGNANCY_START_RETRY_TICKS.getOrDefault(playerUuid, 0L);
               if (retryAt <= nowTick) {
                  Identifier entityTypeId = holder.getPregnantEntityTypeId();
                  if (entityTypeId != null) {
                     Identifier animationId = resolveBirthAnimationId(player, entityTypeId);
                     if (animationId == null) {
                        LOGGER.warn("[NoN] Missing birth-tag animation for player {}; clearing pregnancy for {}.", player.getName().getString(), entityTypeId);
                        sendDebugChat(
                           player,
                           NonDebugChatCategory.SETUP,
                           Text.translatable("debug.needsofnature.missing_birth_animation", new Object[]{entityTypeId.toString()})
                        );
                        clearPregnancyState(player, true);
                     } else {
                        AnimationDefinitions.Definition definition = AnimationDefinitions.getDefinition(animationId);
                        if (definition == null) {
                           LOGGER.warn("[NoN] Missing birth animation definition {}.", animationId);
                           sendDebugChat(
                              player,
                              NonDebugChatCategory.SETUP,
                              Text.translatable("debug.needsofnature.missing_birth_animation", new Object[]{entityTypeId.toString()})
                           );
                           clearPregnancyState(player, true);
                        } else {
                           int spawnCount = resolvePregnancyOffspringCount(holder);
                           NonEntityProfiles.ResolvedProfile profile = NonEntityProfiles.resolve(entityTypeId);
                           boolean queued = enqueueAnimation(
                              world,
                              definition.id(),
                              List.of(player),
                              1,
                              NonAnimationDamageBehavior.BLOCK_DAMAGE,
                              true,
                              buildPregnancyHatchContext(entityTypeId, profile, spawnCount, holder.getPregnancyVariantData()),
                              player
                           );
                           if (!queued) {
                              PREGNANCY_START_RETRY_TICKS.put(playerUuid, nowTick + 20L);
                           } else {
                              PREGNANCY_QUEUED_PLAYERS.add(playerUuid);
                              PREGNANCY_START_RETRY_TICKS.remove(playerUuid);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static AnimationStartContext buildPregnancyHatchContext(
      Identifier entityTypeId, NonEntityProfiles.ResolvedProfile profile, int spawnCount, @Nullable NeedsOfNature.PregnancyVariantData variantData
   ) {
      Identifier birthEntityTypeId = profile == null ? entityTypeId : profile.birthEntityTypeId();
      String birthMode = profile == null ? "direct" : profile.birthMode();
      NonAnimationSessionData.Pregnancy pregnancy = new NonAnimationSessionData.Pregnancy(
         entityTypeId, birthEntityTypeId, birthMode, Math.max(1, Math.min(16, spawnCount)), variantData
      );
      return AnimationStartContext.empty().withSessionData(NonAnimationSessionData.pregnancy(pregnancy));
   }

   private static int resolvePregnancyOffspringCount(@Nullable PregnancyHolder holder) {
      int offspringCount = holder == null ? 0 : holder.getPregnancyOffspringCount();
      return offspringCount <= 0 ? 1 : Math.max(1, Math.min(16, offspringCount));
   }

   private static int rollPregnancyOffspringCount(ServerPlayerEntity player, Identifier entityTypeId, int exactOverride) {
      if (exactOverride > 0) {
         return Math.max(1, Math.min(16, exactOverride));
      }

      NonConfig.OffspringCountRange range = NonEntityProfiles.resolve(entityTypeId).offspringRange();
      int min = range.min();
      int max = range.max();
      return max <= min ? min : min + player.getRandom().nextInt(max - min + 1);
   }

   private static Identifier resolveBirthAnimationId(ServerPlayerEntity player, Identifier entityTypeId) {
      if (player != null && entityTypeId != null) {
         List<Entity> actors = List.of(player);
         Identifier specific = findMatchedAnimationIdByTags(actors, Set.of("birth_" + sanitizeBirthTagPart(entityTypeId)));
         return specific != null ? specific : findMatchedAnimationIdByTags(actors, Set.of("birth"));
      } else {
         return null;
      }
   }

   private static String sanitizeBirthTagPart(Identifier entityTypeId) {
      String raw = entityTypeId.toString().toLowerCase(Locale.ROOT);
      StringBuilder out = new StringBuilder(raw.length());

      for (int i = 0; i < raw.length(); i++) {
         char c = raw.charAt(i);
         if ((c < 'a' || c > 'z') && (c < '0' || c > '9')) {
            out.append('_');
         } else {
            out.append(c);
         }
      }

      return out.toString();
   }

   private static void routePregnancyBirthStage(ServerWorld world, UUID instanceId) {
      if (world != null && instanceId != null) {
         NeedsOfNature.PregnancyHatchState hatchState = PREGNANCY_HATCH_BY_INSTANCE.get(instanceId);
         if (hatchState != null && hatchState.remainingSpawns() <= 1) {
            AnimationStageInfo currentStage = AnimationService.getCurrentStage(world, instanceId);
            Integer stageNumber = NonPeakStages.stageNumberFromId(currentStage == null ? null : currentStage.animationId());
            if (stageNumber != null && stageNumber == 2) {
               AnimationService.advanceToStage(world, instanceId, 3);
            }
         }
      }
   }

   private static void routePregnancyBirthStageAfterCycle(ServerWorld world, UUID instanceId, NeedsOfNature.PregnancyCueRuntime runtime, long nowTick) {
      if (world != null && instanceId != null && runtime != null) {
         NeedsOfNature.PregnancyHatchState hatchState = PREGNANCY_HATCH_BY_INSTANCE.get(instanceId);
         if (hatchState != null && hatchState.remainingSpawns() > 0) {
            AnimationStageInfo currentStage = AnimationService.getCurrentStage(world, instanceId);
            Integer stageNumber = NonPeakStages.stageNumberFromId(currentStage == null ? null : currentStage.animationId());
            if (stageNumber != null && stageNumber == 2) {
               long cycleTicks = Math.max(1L, currentStage.lengthTicks());
               long elapsedTicks = elapsedWorldTicksFromAnimationTicks(cycleTicks, runtime.stageSpeed());
               long routeTick = runtime.stageStartTick() + Math.max(1L, elapsedTicks);
               if (nowTick >= routeTick) {
                  int targetStage = hatchState.remainingSpawns() > 1 ? 2 : 3;
                  AnimationService.advanceToStage(world, instanceId, targetStage);
               }
            }
         }
      }
   }

   private static void handlePregnancyBirthCue(ServerWorld world, UUID instanceId) {
      if (world != null && instanceId != null) {
         NeedsOfNature.PregnancyHatchState hatchState = PREGNANCY_HATCH_BY_INSTANCE.get(instanceId);
         if (hatchState != null && hatchState.remainingSpawns() > 0) {
            ServerPlayerEntity player = findPlayerOnWorldServer(world, hatchState.playerUuid());
            if (player != null && player.getEntityWorld() == world) {
               int spawnNow = 1;
               NeedsOfNature.PregnancyVariantData variantData = hatchState.sourceEntityTypeId().equals(hatchState.birthEntityTypeId())
                  ? hatchState.variantData()
                  : null;
               int spawned = spawnPregnancyOutcome(
                  world, player, hatchState.sourceEntityTypeId(), hatchState.birthEntityTypeId(), hatchState.birthMode(), spawnNow, variantData
               );
               if (spawned > 0) {
                  NonStats.addOffspringSpawned(player, hatchState.birthEntityTypeId(), spawned);
               }

               NeedsOfNature.PregnancyHatchState updated = hatchState.withProgress(hatchState.remainingSpawns() - spawnNow, hatchState.spawnedCount() + spawned);
               if (updated.remainingSpawns() <= 0) {
                  PREGNANCY_HATCH_BY_INSTANCE.remove(instanceId);
                  finishPregnancyHatch(world, instanceId, player, updated);
               } else {
                  PREGNANCY_HATCH_BY_INSTANCE.put(instanceId, updated);
               }
            }
         }
      }
   }

   private static void finalizePregnancyHatch(ServerWorld world, UUID instanceId, boolean cueTriggered) {
      if (world != null && instanceId != null) {
         NeedsOfNature.PregnancyHatchState hatchState = PREGNANCY_HATCH_BY_INSTANCE.remove(instanceId);
         NeedsOfNature.PregnancyCueRuntime cueRuntime = PREGNANCY_CUE_RUNTIME_BY_INSTANCE.remove(instanceId);
         if (hatchState != null) {
            ServerPlayerEntity player = findPlayerOnWorldServer(world, hatchState.playerUuid());
            if (player != null && player.getEntityWorld() == world) {
               if (!cueTriggered) {
                  String clip = cueRuntime != null && cueRuntime.clipId() != null ? cueRuntime.clipId().toString() : "unknown";
                  LOGGER.warn(
                     "[NoN] Birth fallback spawn on STOP for instance {} (clip={}, cue={}).",
                     new Object[]{instanceId, clip, cueRuntime == null ? "none" : String.valueOf(cueRuntime.cueTick())}
                  );
                  sendDebugChat(player, NonDebugChatCategory.SETUP, Text.translatable("debug.needsofnature.birth_fallback_spawn", new Object[]{clip}));
               }

               int remaining = Math.max(0, hatchState.remainingSpawns());
               NeedsOfNature.PregnancyVariantData variantData = hatchState.sourceEntityTypeId().equals(hatchState.birthEntityTypeId())
                  ? hatchState.variantData()
                  : null;
               int spawned = remaining > 0
                  ? spawnPregnancyOutcome(
                     world, player, hatchState.sourceEntityTypeId(), hatchState.birthEntityTypeId(), hatchState.birthMode(), remaining, variantData
                  )
                  : 0;
               if (spawned > 0) {
                  NonStats.addOffspringSpawned(player, hatchState.birthEntityTypeId(), spawned);
               }

               finishPregnancyHatch(world, instanceId, player, hatchState.withProgress(0, hatchState.spawnedCount() + spawned));
            }
         }
      }
   }

   private static void finishPregnancyHatch(ServerWorld world, UUID instanceId, ServerPlayerEntity player, NeedsOfNature.PregnancyHatchState hatchState) {
      if (world != null && instanceId != null && player != null && hatchState != null) {
         PREGNANCY_HATCH_BY_INSTANCE.remove(instanceId);
         PREGNANCY_CUE_RUNTIME_BY_INSTANCE.remove(instanceId);
         PREGNANCY_ACTIVE_INSTANCE_BY_PLAYER.remove(hatchState.playerUuid(), instanceId);
         PREGNANCY_QUEUED_PLAYERS.remove(hatchState.playerUuid());
         NonStats.incrementPregnancyCompleted(player);
         NonAdvancementHooks.checkMotherNature(player);
         if (hatchState.spawnedCount() > 1) {
            NonAdvancementHooks.grantProductivePregnancy(player);
         }

         clearPregnancyState(player, true);
      }
   }

   private static int spawnPregnancyOutcome(
      ServerWorld world,
      ServerPlayerEntity player,
      Identifier sourceEntityTypeId,
      Identifier entityTypeId,
      String birthMode,
      int spawnCount,
      @Nullable NeedsOfNature.PregnancyVariantData variantData
   ) {
      if (world == null || player == null || entityTypeId == null || spawnCount <= 0) {
         return 0;
      } else {
         return "egg".equals(NonConfig.normalizeBirthMode(birthMode))
            ? spawnPregnancyEggs(world, player, sourceEntityTypeId, entityTypeId, spawnCount, variantData)
            : spawnPregnancyOffspring(world, player, entityTypeId, spawnCount, variantData);
      }
   }

   private static int spawnPregnancyEggs(
      ServerWorld world,
      ServerPlayerEntity player,
      Identifier sourceEntityTypeId,
      Identifier entityTypeId,
      int spawnCount,
      @Nullable NeedsOfNature.PregnancyVariantData variantData
   ) {
      if (world != null && player != null && entityTypeId != null && spawnCount > 0) {
         Identifier eggSourceEntityId = sourceEntityTypeId == null ? entityTypeId : sourceEntityTypeId;
         NonConfig.EggProfile eggProfile = NonEntityProfiles.resolve(eggSourceEntityId).eggProfile();
         int hatchSeconds = getConfig().getPregnancyEggHatchSecondsFor(eggSourceEntityId.toString());
         long hatchAtTick = getGlobalTick(world) + Math.max(1, hatchSeconds) * 20L;
         int spawned = 0;

         for (int i = 0; i < spawnCount; i++) {
            PregnancyEggEntity egg = (PregnancyEggEntity)PREGNANCY_EGG_ENTITY_TYPE.create(world, SpawnReason.BREEDING);
            if (egg != null) {
               float yaw = world.getRandom().nextFloat() * 360.0F;
               Vec3d spawnOffset = randomPregnancyEggSpawnOffset(world, spawnCount);
               egg.refreshPositionAndAngles(player.getX() + spawnOffset.x, player.getY(), player.getZ() + spawnOffset.z, yaw, 0.0F);
               egg.configure(player.getUuid(), eggSourceEntityId, entityTypeId, hatchAtTick, variantData, eggProfile);
               if (world.spawnEntity(egg)) {
                  spawned++;
               }
            }
         }

         return spawned;
      } else {
         return 0;
      }
   }

   private static Vec3d randomPregnancyEggSpawnOffset(ServerWorld world, int spawnCount) {
      double angle = world.getRandom().nextDouble() * Math.PI * 2.0;
      double radius = 0.08 + world.getRandom().nextDouble() * 0.1;
      return new Vec3d(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
   }

   private static int spawnPregnancyOffspring(
      ServerWorld world, ServerPlayerEntity player, Identifier entityTypeId, int spawnCount, @Nullable NeedsOfNature.PregnancyVariantData variantData
   ) {
      if (world != null && player != null && entityTypeId != null && spawnCount > 0) {
         if (!Registries.ENTITY_TYPE.containsId(entityTypeId)) {
            LOGGER.warn("[NoN] Cannot spawn pregnancy offspring: unknown entity type {}.", entityTypeId);
            sendDebugChat(
               player,
               NonDebugChatCategory.SETUP,
               Text.translatable("debug.needsofnature.missing_pregnancy_entity_type", new Object[]{entityTypeId.toString()})
            );
            return 0;
         }

         EntityType<?> entityType = (EntityType<?>)Registries.ENTITY_TYPE.get(entityTypeId);
         int spawned = 0;

         for (int i = 0; i < spawnCount; i++) {
            Entity entity = entityType.create(world, SpawnReason.BREEDING);
            if (entity != null) {
               entity.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
               if (entity instanceof MobEntity mob) {
                  mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), SpawnReason.BREEDING, null);
                  applyPregnancyVariantData(world, mob, variantData);
                  applyPregnancyMobFlags(mob, player);
               }

               if (world.spawnEntity(entity)) {
                  spawned++;
               }
            }
         }

         return spawned;
      } else {
         return 0;
      }
   }

   private static void applyPregnancyMobFlags(MobEntity mob, ServerPlayerEntity player) {
      applyPregnancyMobFlagsForEgg(mob, player);
   }

   public static void applyPregnancyMobFlagsForEgg(MobEntity mob, @Nullable ServerPlayerEntity player) {
      if (mob != null) {
         mob.setPersistent();
         if (mob instanceof PassiveEntity passive) {
            passive.setBreedingAge(-24000);
         } else {
            tryInvokeBooleanSetter(mob, "setBaby");
         }

         if (player != null && getConfig().isPregnancyAutoTameMobs()) {
            if (mob instanceof TameableEntity tameable) {
               tameable.setTamedBy(player);
               tameable.setTarget(null);
            } else if (mob instanceof AbstractHorseEntity horse) {
               horse.bondWithPlayer(player);
               horse.setOwner(player);
            } else {
               tryInvokeBooleanSetter(mob, "setTamed");
               tryInvokeBooleanSetter(mob, "setTame");
               tryInvokeSingleArg(mob, "setOwner", PlayerEntity.class, player);
               tryInvokeSingleArg(mob, "setOwner", ServerPlayerEntity.class, player);
               tryInvokeSingleArg(mob, "setOwnerUuid", UUID.class, player.getUuid());
               tryInvokeSingleArg(mob, "bondWithPlayer", PlayerEntity.class, player);
               tryInvokeSingleArg(mob, "bondWithPlayer", ServerPlayerEntity.class, player);
            }
         }

         if (getConfig().pregnancyFriendlyMobsIgnorePlayers()) {
            if (mob instanceof Angerable angerable) {
               angerable.stopAnger();
            }

            if (mob.getTarget() instanceof ServerPlayerEntity) {
               mob.setTarget(null);
            }

            mob.addCommandTag("non.pregnancy_friendly");
         }
      }
   }

   private static void tryInvokeBooleanSetter(Object target, String methodName) {
      if (target != null && methodName != null && !methodName.isBlank()) {
         Method method = findMethod(target.getClass(), methodName, boolean.class);
         if (method != null) {
            tryInvokeMethod(target, method, true);
         }
      }
   }

   private static void tryInvokeSingleArg(Object target, String methodName, Class<?> argType, Object argValue) {
      if (target != null && methodName != null && !methodName.isBlank() && argType != null) {
         Method method = findMethod(target.getClass(), methodName, argType);
         if (method != null) {
            tryInvokeMethod(target, method, argValue);
         }
      }
   }

   private static Method findMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
      for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
         Method declared = findDeclaredMethod(cursor, methodName, parameterTypes);
         if (declared != null) {
            return declared;
         }
      }

      return findPublicMethod(Objects.requireNonNull(type), methodName, parameterTypes);
   }

   private static Method findDeclaredMethod(Class<?> owner, String methodName, Class<?>... parameterTypes) {
      Method[] methods = owner.getDeclaredMethods();

      for (Method method : methods) {
         if (method.getName().equals(methodName) && parameterTypesEqual(method.getParameterTypes(), parameterTypes)) {
            try {
               method.setAccessible(true);
               return method;
            } catch (RuntimeException e) {
               logReflectionFailureOnce("findDeclared:" + owner.getName() + "#" + methodName, e);
               return null;
            }
         }
      }

      return null;
   }

   private static Method findPublicMethod(Class<?> owner, String methodName, Class<?>... parameterTypes) {
      Method[] methods = owner.getMethods();

      for (Method method : methods) {
         if (method.getName().equals(methodName) && parameterTypesEqual(method.getParameterTypes(), parameterTypes)) {
            try {
               method.setAccessible(true);
               return method;
            } catch (RuntimeException e) {
               logReflectionFailureOnce("findPublic:" + owner.getName() + "#" + methodName, e);
               return null;
            }
         }
      }

      return null;
   }

   private static boolean parameterTypesEqual(Class<?>[] left, Class<?>[] right) {
      if (left.length != right.length) {
         return false;
      }

      for (int i = 0; i < left.length; i++) {
         if (!left[i].equals(right[i])) {
            return false;
         }
      }

      return true;
   }

   private static void tryInvokeMethod(Object target, Method method, Object... args) {
      if (target != null && method != null) {
         try {
            method.invoke(target, args);
         } catch (ReflectiveOperationException | RuntimeException e) {
            logReflectionFailureOnce("invoke:" + method.toGenericString(), e);
         }
      }
   }

   private static void logReflectionFailureOnce(String key, Throwable error) {
      if (key != null && REFLECTION_FAILURE_LOGS.add(key)) {
         LOGGER.debug("[NoN] Reflection helper failure at {}", key, error);
      }
   }

   public static boolean isPregnancyFriendlyMob(MobEntity mob) {
      return mob != null && getConfig().pregnancyFriendlyMobsIgnorePlayers() && mob.getCommandTags().contains("non.pregnancy_friendly");
   }

   static long getGlobalTick(ServerWorld world) {
      if (world != null && world.getServer() != null) {
         ServerWorld overworld = world.getServer().getOverworld();
         return overworld == null ? world.getTime() : overworld.getTime();
      } else {
         return 0L;
      }
   }

   private static PregnancyHolder getPregnancyHolder(ServerPlayerEntity player) {
      return player instanceof PregnancyHolder holder ? holder : null;
   }

   static Identifier getPregnantEntityTypeId(ServerPlayerEntity player) {
      PregnancyHolder holder = getPregnancyHolder(player);
      return holder == null ? null : holder.getPregnantEntityTypeId();
   }

   static void beginPregnancy(ServerPlayerEntity player, Identifier entityTypeId, long endTick) {
      beginPregnancy(player, entityTypeId, endTick, null);
   }

   static void beginPregnancy(ServerPlayerEntity player, Identifier entityTypeId, long endTick, @Nullable NeedsOfNature.PregnancyVariantData variantData) {
      beginPregnancy(player, entityTypeId, endTick, variantData, 0);
   }

   static void beginPregnancy(
      ServerPlayerEntity player, Identifier entityTypeId, long endTick, @Nullable NeedsOfNature.PregnancyVariantData variantData, int offspringCountOverride
   ) {
      if (player != null && entityTypeId != null) {
         if (NonGenderSystem.isOnlyMale(player)) {
            clearPregnancyState(player, true);
         } else if (((NonEvents.AllowPregnancy)NonEvents.ALLOW_PREGNANCY.invoker()).allowPregnancy(player, entityTypeId)) {
            PregnancyHolder holder = getPregnancyHolder(player);
            if (holder != null) {
               int offspringCount = rollPregnancyOffspringCount(player, entityTypeId, offspringCountOverride);
               holder.setPregnancyState(entityTypeId, Math.max(0L, endTick), false, variantData);
               holder.setPregnancyOffspringCount(offspringCount);
               syncPregnancyState(player);
               NonApiInternals.firePregnancyChanged(player);
               PREGNANCY_START_RETRY_TICKS.remove(player.getUuid());
               PREGNANCY_QUEUED_PLAYERS.remove(player.getUuid());
               UUID active = PREGNANCY_ACTIVE_INSTANCE_BY_PLAYER.remove(player.getUuid());
               if (active != null) {
                  PREGNANCY_HATCH_BY_INSTANCE.remove(active);
                  PREGNANCY_CUE_RUNTIME_BY_INSTANCE.remove(active);
               }

               long nowTick = player.getEntityWorld() instanceof ServerWorld world ? getGlobalTick(world) : 0L;
               long remaining = Math.max(1L, endTick - nowTick);
               int duration = (int)Math.min(2147483647L, remaining);
               player.addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.PREGNANT), duration, 0, false, false, true));
            }
         }
      }
   }

   static void markPregnancyPending(ServerPlayerEntity player, long nowTick) {
      if (player != null) {
         if (NonGenderSystem.isOnlyMale(player)) {
            clearPregnancyState(player, true);
         } else {
            PregnancyHolder holder = getPregnancyHolder(player);
            if (holder != null) {
               Identifier entityTypeId = holder.getPregnantEntityTypeId();
               if (entityTypeId != null) {
                  holder.setPregnancyState(entityTypeId, Math.max(0L, nowTick), true, holder.getPregnancyVariantData());
                  refreshPendingPregnancyEffect(player);
                  NonApiInternals.firePregnancyChanged(player);
               }
            }
         }
      }
   }

   private static void refreshPendingPregnancyEffect(ServerPlayerEntity player) {
      if (player != null) {
         RegistryEntry<StatusEffect> effect = Registries.STATUS_EFFECT.getEntry(NonStatusEffects.PREGNANT);
         StatusEffectInstance current = player.getStatusEffect(effect);
         if (current == null || current.getDuration() < 21) {
            player.addStatusEffect(new StatusEffectInstance(effect, 21, 0, false, false, true));
         }
      }
   }

   @Nullable
   private static ServerPlayerEntity findPlayerOnWorldServer(ServerWorld world, UUID playerUuid) {
      if (world != null && playerUuid != null) {
         MinecraftServer server = world.getServer();
         return server == null ? null : server.getPlayerManager().getPlayer(playerUuid);
      } else {
         return null;
      }
   }

   static void clearPregnancyState(ServerPlayerEntity player, boolean removeEffect) {
      if (player != null) {
         PregnancyHolder holder = getPregnancyHolder(player);
         if (holder != null) {
            holder.clearPregnancyState();
         }

         clearPregnancyRuntime(player.getUuid());
         if (removeEffect) {
            player.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.PREGNANT));
         }

         syncPregnancyState(player);
         NonApiInternals.firePregnancyChanged(player);
      }
   }

   private static void clearPregnancyRuntime(UUID playerUuid) {
      if (playerUuid != null) {
         PREGNANCY_START_RETRY_TICKS.remove(playerUuid);
         PREGNANCY_QUEUED_PLAYERS.remove(playerUuid);
         UUID active = PREGNANCY_ACTIVE_INSTANCE_BY_PLAYER.remove(playerUuid);
         if (active != null) {
            PREGNANCY_HATCH_BY_INSTANCE.remove(active);
            PREGNANCY_CUE_RUNTIME_BY_INSTANCE.remove(active);
         }
      }
   }

   private static void refreshPregnancyAfterDeathCopy(ServerPlayerEntity player) {
      PregnancyHolder holder = getPregnancyHolder(player);
      if (holder != null && holder.getPregnantEntityTypeId() != null) {
         long nowTick = player.getEntityWorld() instanceof ServerWorld world ? getGlobalTick(world) : 0L;
         if (!holder.isPregnancyPendingHatch() && holder.getPregnancyEndTick() > nowTick) {
            long remaining = Math.max(1L, holder.getPregnancyEndTick() - nowTick);
            int duration = (int)Math.min(2147483647L, remaining);
            player.addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.PREGNANT), duration, 0, false, false, true));
         } else {
            markPregnancyPending(player, nowTick);
         }
      }
   }

   public static void clearPregnancyOnDeath(ServerPlayerEntity player) {
      clearPregnancyState(player, true);
   }

   private void registerAttackStopRequests() {
      ServerPlayNetworking.registerGlobalReceiver(StopAttackAnimationC2SPayload.ID, (payload, context) -> context.server().execute(() -> {
         ServerPlayerEntity player = context.player();
         if (player.getEntityWorld() instanceof ServerWorld world) {
            UUID instanceId = payload.instanceId();
            if (!NonPlayerAttackSystem.isContest(instanceId)) {
               ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
               if (isAttackSnapshot(snapshot) && snapshot.actorUuids().contains(player.getUuid())) {
                  completeAttackEscape(world, player, instanceId, null);
               }
            }
         }
      }));
   }

   static void completeAttackEscape(ServerWorld world, ServerPlayerEntity player, UUID instanceId, @Nullable UUID preferredAttackerUuid) {
      if (world != null && player != null && instanceId != null) {
         ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
         if (isAttackSnapshot(snapshot) && snapshot.actorUuids().contains(player.getUuid())) {
            List<UUID> actors = snapshot.actorUuids();
            boolean defeatedInstance = isDefeatedInstance(world, instanceId);
            int escapeInvulnerabilityTicks = getConfig().getAttackEscapeInvulnerabilityTicks();
            int escapeAnimationProtectionTicks = getConfig().getAttackEscapeAnimationProtectionTicks();
            if (defeatedInstance && escapeInvulnerabilityTicks > 0) {
               setPostEscapeDamageInvulnerable(player, escapeInvulnerabilityTicks);
            }

            setAnimationStartProtection(player, escapeAnimationProtectionTicks);
            NeedsOfNature.PeakState peakState = PEAK_STATES.get(instanceId);
            boolean allowReattack = peakState == null || !peakState.peaked;
            clearQueuedAnimations(world, player);
            if (!defeatedInstance) {
               scheduleAttackEscapeResolution(world, player, actors, preferredAttackerUuid, allowReattack, escapeInvulnerabilityTicks);
               NonStats.incrementAttackEscapeSuccess(player);
               NonAdvancementHooks.resetTossedAround(player);
            }

            AnimationService.stopAnimation(world, instanceId);
            if (defeatedInstance && allowReattack) {
               requestOriginalAttackers(world, player, actors);
            }
         }
      }
   }

   private record ActiveBreedingState(RegistryKey<World> worldKey, UUID firstUuid, UUID secondUuid) {
   }

   public enum AttackBlockReason {
      PREGNANCY_FRIENDLY,
      GATHERING_PAIR,
      ENERGY_ABOVE_70_PLAYER_BUSY;
   }

   private record BreedingPairKey(UUID firstUuid, UUID secondUuid) {
   }

   private record FillBottleState(UUID playerUuid, boolean reservedBottle) {
   }

   private record FinitePulseSchedule(List<Long> ticks, List<Integer> amounts) {
   }

   private record GatherPairKey(UUID mobUuid, UUID playerUuid) {
   }

   private record GatherPairState(RegistryKey<World> worldKey, long expiresTick) {
   }

   private record LastDefeatedCandidate(MobEntity mob, NonGatherSystem.GatherCandidate gatherCandidate, AnimationService.MatchedAnimation defeatMatch) {
   }

   private record LiquidGainProfile(
      int amount,
      int xpEligibleAmount,
      LiquidHolder.LiquidComposition composition,
      Identifier entityTypeId,
      Map<Identifier, Integer> entityAmounts,
      @Nullable NeedsOfNature.PregnancyVariantData donorVariantData,
      List<NeedsOfNature.PregnancyDonor> recentDonors
   ) {
      LiquidGainProfile {
         entityAmounts = entityAmounts != null && !entityAmounts.isEmpty() ? Map.copyOf(entityAmounts) : Map.of();
         recentDonors = recentDonors != null && !recentDonors.isEmpty() ? List.copyOf(recentDonors) : List.of();
      }
   }

   private record LiquidPulseResult(int addedMl, int xpEligibleAddedMl) {
      private static final NeedsOfNature.LiquidPulseResult EMPTY = new NeedsOfNature.LiquidPulseResult(0, 0);
   }

   private record LiquidSnapshot(
      int stored,
      LiquidHolder.LiquidComposition composition,
      @Nullable Identifier entityTypeId,
      @Nullable NeedsOfNature.PregnancyVariantData donorVariantData,
      List<LiquidDonorHistory.Entry> recentDonors
   ) {
      static void copy(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
         if (oldPlayer instanceof LiquidHolder oldHolder && newPlayer instanceof LiquidHolder newHolder) {
            NeedsOfNature.LiquidSnapshot snapshot = new NeedsOfNature.LiquidSnapshot(
               oldHolder.getLiquidStored(),
               oldHolder.getLiquidComposition(),
               oldHolder.getLiquidEntityTypeId(),
               oldHolder.getLiquidDonorVariantData(),
               oldHolder.getRecentLiquidDonors()
            );
            newHolder.setLiquidStored(snapshot.stored());
            if (newHolder.getLiquidStored() <= 0) {
               newHolder.clearLiquidComposition();
            } else {
               if (snapshot.composition() == LiquidHolder.LiquidComposition.ENTITY && snapshot.entityTypeId() != null) {
                  newHolder.setLiquidCompositionEntity(snapshot.entityTypeId());
                  newHolder.setLiquidDonorVariantData(snapshot.donorVariantData());
               } else if (snapshot.composition() == LiquidHolder.LiquidComposition.MIXED) {
                  newHolder.setLiquidCompositionMixed();
               } else {
                  newHolder.clearLiquidComposition();
               }

               newHolder.setRecentLiquidDonors(snapshot.recentDonors());
            }
         }
      }
   }

   private record MessSnapshot(int v, int a, int m) {
      static void copy(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
         if (oldPlayer instanceof MessHolder oldHolder && newPlayer instanceof MessHolder newHolder) {
            NeedsOfNature.MessSnapshot snapshot = new NeedsOfNature.MessSnapshot(oldHolder.getVMess(), oldHolder.getAMess(), oldHolder.getMMess());
            newHolder.setVMess(snapshot.v());
            newHolder.setAMess(snapshot.a());
            newHolder.setMMess(snapshot.m());
         }
      }
   }

   private record NoNGatherActorState(RegistryKey<World> worldKey, long expiresTick) {
   }

   private record PeakLiquidPlan(
      Map<UUID, NeedsOfNature.LiquidGainProfile> gainsByReceiver,
      Map<UUID, List<NeedsOfNature.PregnancyDonor>> accessoryPregnancyDonors,
      Set<NonLiquidRoles.InjectorRole> suppliedInjectorRoles
   ) {
      PeakLiquidPlan {
         gainsByReceiver = gainsByReceiver != null && !gainsByReceiver.isEmpty() ? Map.copyOf(gainsByReceiver) : Map.of();
         if (accessoryPregnancyDonors != null && !accessoryPregnancyDonors.isEmpty()) {
            Map<UUID, List<NeedsOfNature.PregnancyDonor>> frozen = new LinkedHashMap<>();
            accessoryPregnancyDonors.forEach((uuid, donors) -> {
               if (uuid != null && donors != null && !donors.isEmpty()) {
                  frozen.put(uuid, List.copyOf((Collection<? extends NeedsOfNature.PregnancyDonor>)donors));
               }
            });
            accessoryPregnancyDonors = Map.copyOf(frozen);
         } else {
            accessoryPregnancyDonors = Map.of();
         }

         suppliedInjectorRoles = suppliedInjectorRoles != null && !suppliedInjectorRoles.isEmpty() ? Set.copyOf(suppliedInjectorRoles) : Set.of();
      }

      int maxAmount() {
         int max = 0;

         for (NeedsOfNature.LiquidGainProfile gain : this.gainsByReceiver.values()) {
            if (gain != null) {
               max = Math.max(max, gain.amount());
            }
         }

         return max;
      }
   }

   private static final class PeakLiquidProgressState {
      final RegistryKey<World> worldKey;
      final NeedsOfNature.PeakLiquidPlan plan;
      final List<Long> scheduledTicks;
      final List<Integer> scheduledAmounts;
      int scheduleIndex;
      final boolean infinite;
      final double cycleTicks;
      final List<Integer> cueOffsets;
      final long stageStartTick;
      final double stageSpeed;
      long cycleIndex;
      int cueIndex;
      int distributedBaseMl;
      int remainingBaseMl;
      final Set<UUID> donorHistoryRecordedReceivers = new HashSet<>();

      private PeakLiquidProgressState(
         RegistryKey<World> worldKey,
         NeedsOfNature.PeakLiquidPlan plan,
         List<Long> scheduledTicks,
         List<Integer> scheduledAmounts,
         boolean infinite,
         double cycleTicks,
         List<Integer> cueOffsets,
         long stageStartTick,
         double stageSpeed,
         int remainingBaseMl
      ) {
         this.worldKey = worldKey;
         this.plan = plan;
         this.scheduledTicks = scheduledTicks;
         this.scheduledAmounts = scheduledAmounts;
         this.scheduleIndex = 0;
         this.infinite = infinite;
         this.cycleTicks = cycleTicks;
         this.cueOffsets = cueOffsets;
         this.stageStartTick = stageStartTick;
         this.stageSpeed = stageSpeed;
         this.cycleIndex = 0L;
         this.cueIndex = 0;
         this.distributedBaseMl = 0;
         this.remainingBaseMl = remainingBaseMl;
      }

      static NeedsOfNature.PeakLiquidProgressState finite(
         RegistryKey<World> worldKey, NeedsOfNature.PeakLiquidPlan plan, List<Long> scheduledTicks, List<Integer> scheduledAmounts
      ) {
         return new NeedsOfNature.PeakLiquidProgressState(worldKey, plan, scheduledTicks, scheduledAmounts, false, 0.0, List.of(), 0L, 1.0, 0);
      }

      static NeedsOfNature.PeakLiquidProgressState infinite(
         RegistryKey<World> worldKey, NeedsOfNature.PeakLiquidPlan plan, double cycleTicks, List<Integer> cueOffsets, long stageStartTick, double stageSpeed
      ) {
         return new NeedsOfNature.PeakLiquidProgressState(
            worldKey, plan, List.of(), List.of(), true, cycleTicks, cueOffsets, stageStartTick, stageSpeed, plan.maxAmount()
         );
      }
   }

   private record PeakState(Identifier animationId, int peakStage, boolean peaked, int lastStage, boolean hasPlayer, boolean liquidGranted, int actualAddedMl) {
   }

   private record PendingAttackEscapeResolution(
      RegistryKey<World> worldKey, List<UUID> actorUuids, @Nullable UUID preferredAttackerUuid, long dueTick, boolean allowReattack, int invulnerabilityTicks
   ) {
   }

   private record PendingBreedingState(RegistryKey<World> worldKey, UUID firstUuid, UUID secondUuid, long expiresTick) {
   }

   private record PendingMInjectorReward(RegistryKey<World> worldKey, long freeSinceTick) {
   }

   public record PlayerEnergyAuraDebug(
      ServerPlayerEntity player, double totalMultiplier, int playerEnergy, int destroyedSkinStage, int messTotal, double radius
   ) {
   }

   private record PostDeathStateSync(RegistryKey<World> worldKey, long dueTick) {
   }

   private record PregnancyCueRuntime(
      RegistryKey<World> worldKey, long stageStartTick, Identifier clipId, Integer cueTick, boolean cueTriggered, double stageSpeed
   ) {
   }

   record PregnancyDonor(Identifier entityTypeId, @Nullable NeedsOfNature.PregnancyVariantData variantData) {
   }

   private record PregnancyHatchState(
      UUID playerUuid,
      Identifier sourceEntityTypeId,
      Identifier birthEntityTypeId,
      String birthMode,
      int totalSpawnCount,
      int remainingSpawns,
      int spawnedCount,
      @Nullable NeedsOfNature.PregnancyVariantData variantData
   ) {
      NeedsOfNature.PregnancyHatchState withProgress(int newRemainingSpawns, int newSpawnedCount) {
         return new NeedsOfNature.PregnancyHatchState(
            this.playerUuid,
            this.sourceEntityTypeId,
            this.birthEntityTypeId,
            this.birthMode,
            this.totalSpawnCount,
            Math.max(0, newRemainingSpawns),
            Math.max(0, newSpawnedCount),
            this.variantData
         );
      }
   }

   private record PregnancySnapshot(
      Identifier entityTypeId, long endTick, boolean pendingHatch, @Nullable NeedsOfNature.PregnancyVariantData variantData, int offspringCount
   ) {
      static void copy(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
         PregnancyHolder oldHolder = NeedsOfNature.getPregnancyHolder(oldPlayer);
         PregnancyHolder newHolder = NeedsOfNature.getPregnancyHolder(newPlayer);
         if (oldHolder != null && newHolder != null && oldHolder.getPregnantEntityTypeId() != null) {
            if (NonGenderSystem.isOnlyMale(newPlayer)) {
               NeedsOfNature.clearPregnancyState(newPlayer, true);
            } else {
               NeedsOfNature.PregnancySnapshot snapshot = new NeedsOfNature.PregnancySnapshot(
                  oldHolder.getPregnantEntityTypeId(),
                  oldHolder.getPregnancyEndTick(),
                  oldHolder.isPregnancyPendingHatch(),
                  oldHolder.getPregnancyVariantData(),
                  oldHolder.getPregnancyOffspringCount()
               );
               NeedsOfNature.clearPregnancyRuntime(newPlayer.getUuid());
               newHolder.setPregnancyState(snapshot.entityTypeId(), snapshot.endTick(), snapshot.pendingHatch(), snapshot.variantData());
               newHolder.setPregnancyOffspringCount(snapshot.offspringCount());
               NeedsOfNature.refreshPregnancyAfterDeathCopy(newPlayer);
               NeedsOfNature.syncPregnancyState(newPlayer);
               NonApiInternals.firePregnancyChanged(newPlayer);
            }
         }
      }
   }

   public record PregnancyVariantData(Map<String, String> components, @Nullable Integer horseMarkingIndex) {
      public PregnancyVariantData {
         components = components != null && !components.isEmpty() ? Map.copyOf(components) : Map.of();
      }

      public boolean isEmpty() {
         return this.components.isEmpty() && this.horseMarkingIndex == null;
      }
   }

   private record ReceiverDripState(RegistryKey<World> worldKey, long nextTick, long endTick, int tintRgb) {
   }

   private record RippedSkinSnapshot(int stage, int damage) {
      static void copy(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
         if (oldPlayer instanceof DestroyedSkinHolder oldHolder && newPlayer instanceof DestroyedSkinHolder newHolder) {
            NeedsOfNature.RippedSkinSnapshot snapshot = new NeedsOfNature.RippedSkinSnapshot(
               oldHolder.getDestroyedSkinStage(), oldHolder.getDestroyedSkinDamage()
            );
            newHolder.setDestroyedSkinDamage(snapshot.damage());
            NonDestroyedSkinSystem.setDestroyedSkinStage(newPlayer, snapshot.stage());
         }
      }
   }

   private record ScanBudget(long tick, int remaining) {
   }

   private interface VariantComponentDescriptor {
      String key();

      void capture(ServerWorld var1, Entity var2, Map<String, String> var3);

      void apply(ServerWorld var1, Entity var2, String var3);
   }
}
