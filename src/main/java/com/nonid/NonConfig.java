package com.nonid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.nonid.client.NonLiquidColors;
import com.nonid.data.NonEntityProfiles;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

public final class NonConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("needsofnature.json");
   private static final int PREGNANCY_DURATION_MINUTES_MIN = 1;
   private static final int PREGNANCY_DURATION_MINUTES_MAX = 5256000;
   public static final int DEFAULT_GENDER_MALE_CHANCE_PERCENT = 48;
   public static final int DEFAULT_GENDER_FEMALE_CHANCE_PERCENT = 47;
   public static final int DEFAULT_GENDER_BOTH_CHANCE_PERCENT = 5;
   public static final int DEFAULT_LIQUID_TANK_CAPACITY_ML = 200;
   public static final int LIQUID_TANK_CAPACITY_MIN_ML = 1;
   public static final int LIQUID_TANK_CAPACITY_MAX_ML = 10000;
   public static final int DEFAULT_ATTACK_OUTCOME_FAILSAFE_THRESHOLD = 3;
   public static final int DEFAULT_ACTION_SOUND_VOLUME_PERCENT = 100;
   public static final double DEFAULT_PEAK_XP_PER_ML = 0.06666666666666667;
   public static final int DEFAULT_NORMAL_DAMAGE_DESTROYED_SKIN_CHANCE_PERCENT = 10;
   public static final int DEFAULT_LAST_DEFEATED_ENERGY_THRESHOLD = 70;
   public static final int DEFAULT_LAST_DEFEATED_SEARCH_RADIUS = 6;
   public static final int DEFAULT_LAST_DEFEATED_COOLDOWN_SECONDS = 120;
   public static final int DEFAULT_ATTACK_ESCAPE_INVULNERABILITY_SECONDS = 3;
   public static final int DEFAULT_ATTACK_ESCAPE_ANIMATION_PROTECTION_SECONDS = 3;
   public static final int ATTACK_ESCAPE_PROTECTION_SECONDS_MAX = 10;
   public static final int DEFAULT_ATTACK_ESCAPE_DAMAGE_DIFFICULTY_PERCENT = 100;
   public static final int ATTACK_ESCAPE_DAMAGE_DIFFICULTY_PERCENT_MAX = 200;
   public static final int DEFAULT_PVP_FULL_HEALTH_ESCAPE_BONUS_PERCENT = 30;
   public static final int DEFAULT_PVP_CRITICAL_HEALTH_MASH_STRENGTH_PERCENT = 25;
   public static final int DEFAULT_PVP_MAX_HEAT_ESCAPE_STRENGTH_PERCENT = 70;
   public static final int DEFAULT_PVP_ZERO_HEAT_ATTACK_STRENGTH_PERCENT = 70;
   public static final int PVP_MASH_PERCENT_MAX = 200;
   public static final int DEFAULT_POST_ESCAPE_GATHER_MAX_MOBS = 8;
   public static final int DEFAULT_ALLOWED_PLAYER_INTERACTION_PROFILES_MASK = 15;
   public static final int DEFAULT_PLAYER_REQUEST_MIN_ENERGY = 70;
   public static final boolean DEFAULT_BLOCK_ANIMATIONS_WHILE_RIDING_LIVING_ENTITIES = true;
   public static final boolean DEFAULT_BLOCK_ANIMATIONS_WHILE_RIDING_VEHICLES = false;
   public static final int PREGNANCY_OFFSPRING_COUNT_MIN = 1;
   public static final int PREGNANCY_OFFSPRING_COUNT_MAX = 16;
   public static final float ENTITY_ENERGY_GAIN_MULTIPLIER_MIN = 0.1F;
   public static final float ENTITY_ENERGY_GAIN_MULTIPLIER_MAX = 10.0F;
   public static final int PREGNANCY_CYCLE_DAYS = 30;
   public static final boolean DEFAULT_LIQUID_BASED_PREGNANCY = false;
   public static final int DEFAULT_PREGNANCY_RISKY_START_DAY = 13;
   public static final int DEFAULT_PREGNANCY_OVULATION_END_DAY = 17;
   public static final int DEFAULT_PREGNANCY_SAFE_DAY_CHANCE_PERCENT = 10;
   public static final int DEFAULT_PREGNANCY_RISKY_CHANCE_PERCENT = 300;
   public static final int DEFAULT_PREGNANCY_OVULATION_CHANCE_PERCENT = 500;
   public static final int PREGNANCY_CYCLE_CHANCE_PERCENT_MAX = 1000;
   public static final float DEFAULT_PREGNANCY_EGG_START_SIZE = 0.5F;
   public static final float DEFAULT_PREGNANCY_EGG_END_SIZE = 1.0F;
   public static final float DEFAULT_PREGNANCY_EGG_HEALTH = 2.0F;
   public static final float PREGNANCY_EGG_SIZE_MIN = 0.05F;
   public static final float PREGNANCY_EGG_SIZE_MAX = 4.0F;
   public static final float PREGNANCY_EGG_HEALTH_MIN = 0.5F;
   public static final float PREGNANCY_EGG_HEALTH_MAX = 100.0F;
   public static final String PREGNANCY_BIRTH_MODE_EGG = "egg";
   public static final String PREGNANCY_BIRTH_MODE_DIRECT = "direct";
   public static final String DEFAULT_DESTROYED_SKIN_TINT_HEX = "";
   public static final String DEFAULT_INTIFACE_SERVER_URL = "ws://127.0.0.1:12345";
   public static final int DEFAULT_INTIFACE_REACTIVE_IMPACT_STRENGTH_PERCENT = 60;
   public static final int DEFAULT_INTIFACE_REACTIVE_IMPACT_DURATION_MS = 170;
   public static final int DEFAULT_INTIFACE_PEAK_REACTIVE_IMPACT_STRENGTH_PERCENT = 20;
   public static final int DEFAULT_INTIFACE_PEAK_REACTIVE_IMPACT_DURATION_MS = 500;
   public static final int DEFAULT_INTIFACE_COOLDOWN_MS = 80;
   public static final int DEFAULT_INTIFACE_LATENCY_COMPENSATION_MS = 0;
   public static final int DEFAULT_INTIFACE_ANIMATION_BASELINE_STRENGTH_PERCENT = 1;
   public static final int DEFAULT_INTIFACE_OSCILLATOR_REGULAR_SPEED_PERCENT = 100;
   public static final int DEFAULT_INTIFACE_OSCILLATOR_PEAK_SPEED_PERCENT = 50;
   public static final int DEFAULT_INTIFACE_STROKER_MIN_DISTANCE_PERCENT = 0;
   public static final int DEFAULT_INTIFACE_STROKER_MAX_DISTANCE_PERCENT = 100;
   public static final int DEFAULT_INTIFACE_STROKER_REGULAR_MOVE_DURATION_MS = 500;
   public static final int DEFAULT_INTIFACE_STROKER_PEAK_MOVE_DURATION_MS = 300;
   public static final int DEFAULT_INTIFACE_ENERGIZED_ONE_BASE_PERCENT = 1;
   public static final int DEFAULT_INTIFACE_ENERGIZED_ONE_PULSE_PERCENT = 5;
   public static final int DEFAULT_INTIFACE_ENERGIZED_TWO_BASE_PERCENT = 5;
   public static final int DEFAULT_INTIFACE_ENERGIZED_TWO_PULSE_PERCENT = 10;
   public static final int DEFAULT_INTIFACE_ENERGIZED_THREE_BASE_PERCENT = 10;
   public static final int DEFAULT_INTIFACE_ENERGIZED_THREE_PULSE_PERCENT = 20;
   public static final float FEMALE_GENDER_BREAST_SIZE_MAX = 1.6F;
   public static final float FEMALE_GENDER_BREAST_HEIGHT_MIN = -2.0F;
   private int initialEnergyMax = 90;
   private double energyGainRate = 1.0;
   private double nearAnimationEnergyGainMult = 3.0;
   private double playerEnergyAuraMultLow = 2.0;
   private double playerEnergyAuraMultMid = 5.0;
   private double playerEnergyAuraMultHigh = 10.0;
   private double destroyedSkinAuraMultStage1 = 2.0;
   private double destroyedSkinAuraMultStage2 = 5.0;
   private double destroyedSkinAuraMultStage3 = 20.0;
   private double destroyedSkinAuraMultStage4 = 50.0;
   private int normalDamageDestroyedSkinChancePercent = 10;
   private boolean lastDefeatedEnabled = true;
   private int lastDefeatedEnergyThreshold = 70;
   private int lastDefeatedSearchRadius = 6;
   private int lastDefeatedCooldownSeconds = 120;
   private int playerEnergyAuraRadiusMin = 6;
   private int playerEnergyAuraRadiusMax = 20;
   private int playerEnergyAuraPulseTicks = 20;
   private String debugChatMode = null;
   private boolean forceVanillaEntityTextures = false;
   private int animationBlockSearchRadius = 3;
   private boolean autoSwitchThirdPersonOnAnimationStart = true;
   private int scanBudgetPerTick = 32;
   private int loopProgressSeconds = 15;
   private int peakLoopProgressSeconds = 7;
   private int genderMaleChancePercent = 48;
   private int genderFemaleChancePercent = 47;
   private int genderBothChancePercent = 5;
   private int maleMaleChancePercent = 20;
   private int femaleFemaleChancePercent = 20;
   private int entityEntityChancePercent = 20;
   private int multiActorJoinChancePercent = 75;
   private String playerGender = NonConfig.PlayerGenderSelection.FEMALE.id();
   private boolean allowPlayerGenderChangeAnytime = true;
   private boolean requirePlayerGenderSelectionOnJoin = false;
   private int allowedStartingGenderMask = 7;
   private int allowedPlayerInteractionProfilesMask = 15;
   private int playerRequestMinEnergy = 70;
   private boolean pregnancyEnabled = true;
   private boolean liquidTankEnabled = true;
   private boolean messSystemEnabled = true;
   private boolean destroyedSkinSystemEnabled = true;
   private boolean allowCraftingTableSkinRepair = false;
   private boolean keepMessAfterDeath = false;
   private boolean keepRippedSkinAfterDeath = false;
   private boolean keepLiquidTankAfterDeath = false;
   private boolean keepPregnancyAfterDeath = false;
   private boolean armorShedEffectEnabled = true;
   private boolean syncFemaleGenderModWithNonGender = true;
   private boolean destroyedSkinFemaleGenderOverridesEnabled = true;
   private NonConfig.FemaleGenderDestroyedProfile femaleGenderDestroyedLow = NonConfig.FemaleGenderDestroyedProfile.lowDefault();
   private NonConfig.FemaleGenderDestroyedProfile femaleGenderDestroyedHigh = NonConfig.FemaleGenderDestroyedProfile.highDefault();
   private int actionSoundVolumePercent = 100;
   private boolean intifaceEnabled = false;
   private boolean intifaceAutoConnectOnWorldJoin = true;
   private boolean intifaceStopOnDisconnect = true;
   private String intifaceServerUrl = "ws://127.0.0.1:12345";
   private int intifaceReactiveImpactStrengthPercent = 60;
   private int intifaceReactiveImpactDurationMs = 170;
   private int intifacePeakReactiveImpactStrengthPercent = 20;
   private int intifacePeakReactiveImpactDurationMs = 500;
   private int intifaceCooldownMs = 80;
   private int intifaceLatencyCompensationMs = 0;
   private int intifaceAnimationBaselineStrengthPercent = 1;
   private int intifaceOscillatorRegularSpeedPercent = 100;
   private int intifaceOscillatorPeakSpeedPercent = 50;
   private int intifaceStrokerMinDistancePercent = 0;
   private int intifaceStrokerMaxDistancePercent = 100;
   private int intifaceStrokerRegularMoveDurationMs = 500;
   private int intifaceStrokerPeakMoveDurationMs = 300;
   private int intifaceEnergizedOneBasePercent = 1;
   private int intifaceEnergizedOnePulsePercent = 5;
   private int intifaceEnergizedTwoBasePercent = 5;
   private int intifaceEnergizedTwoPulsePercent = 10;
   private int intifaceEnergizedThreeBasePercent = 10;
   private int intifaceEnergizedThreePulsePercent = 20;
   private int liquidTankCapacityMl = 200;
   private boolean vanillaOverridesEnabled = true;
   private boolean useAnimationBreeding = true;
   private boolean requireMaleFemaleForBreeding = true;
   private boolean femaleCowOnlyMilking = true;
   private boolean attackCreativePlayers = false;
   private boolean convertMaleOnlyVInjectionsToA = true;
   private int attackEscapeHits = 12;
   private double attackDecayPerSecond = 0.2;
   private int attackEscapeDamageDifficultyPercent = 100;
   private int pvpFullHealthEscapeBonusPercent = 30;
   private int pvpCriticalHealthMashStrengthPercent = 25;
   private int pvpMaxHeatEscapeStrengthPercent = 70;
   private int pvpZeroHeatAttackStrengthPercent = 70;
   private Integer attackEscapeInvulnerabilityTicks = null;
   private int attackEscapeInvulnerabilitySeconds = 3;
   private int attackEscapeAnimationProtectionSeconds = 3;
   private int postEscapeGatherMaxMobs = 8;
   private boolean blockAnimationsWhileRidingLivingEntities = true;
   private boolean blockAnimationsWhileRidingVehicles = false;
   private int attackOutcomeFailsafeThreshold = 3;
   private int pregnancyChancePercent = 5;
   private int pregnancyDurationMinutes = 3;
   private boolean liquidBasedPregnancy = false;
   private int pregnancyRiskyStartDay = 13;
   private int pregnancyOvulationEndDay = 17;
   private int pregnancySafeDayChancePercent = 10;
   private Integer pregnancyRiskyChancePercent;
   private int pregnancyOvulationChancePercent = 500;
   private Map<String, Integer> pregnancyChanceByEntity = new LinkedHashMap<>();
   private Map<String, NonConfig.OffspringCountRange> pregnancyOffspringCountByEntity = new LinkedHashMap<>();
   private int pregnancyEggDefaultHatchSeconds = 60;
   private Map<String, Boolean> pregnancyEggEnabledByEntity = new LinkedHashMap<>();
   private Map<String, Integer> pregnancyEggHatchSecondsByEntity = new LinkedHashMap<>();
   private boolean pregnancyAutoTameMobs = true;
   private boolean pregnancyFriendlyMobsIgnorePlayers = false;
   private Map<String, NonConfig.EntityProfile> entityProfilesByEntity = new LinkedHashMap<>();
   private double liquidDecayPerSecond = 1.0;
   private int liquidPuddleDespawnSeconds = 40;
   private double peakXpPerMl = 0.06666666666666667;
   private float filledStageOneSpeedMult = 0.8F;
   private float filledStageTwoSpeedMult = 0.7F;
   private float filledStageThreeSpeedMult = 0.6F;
   private float filledStageOneJumpMult = 0.9F;
   private float filledStageTwoJumpMult = 0.75F;
   private float filledStageThreeJumpMult = 0.6F;
   private int uiEnergyOffsetX = 0;
   private int uiEnergyOffsetY = 0;
   private int uiEnergyHeartOffsetX = 0;
   private int uiEnergyHeartOffsetY = 0;
   private int uiAttackOffsetX = 0;
   private int uiAttackOffsetY = 0;
   private int uiPromptOffsetX = 0;
   private int uiPromptOffsetY = 0;
   private int uiLiquidOffsetX = 0;
   private int uiLiquidOffsetY = 0;
   private boolean uiEnergyVisible = true;
   private boolean uiEnergyHeartVisible = true;
   private boolean uiAttackVisible = true;
   private boolean uiPromptVisible = true;
   private boolean uiLiquidVisible = true;
   private boolean debugSpinMode = false;
   private String destroyedSkinTintHex = "";
   private Map<String, NonConfig.GenderSpawnChances> genderSpawnByEntity = new LinkedHashMap<>();
   private Map<String, Integer> liquidGainByEntity = new LinkedHashMap<>();
   private Map<String, String> liquidColorByEntity = new LinkedHashMap<>();
   private List<String> externalNoNPackLoadOrder = new ArrayList<>();
   private List<String> disabledAnimationPacks = new ArrayList<>();
   private List<String> disabledAnimations = new ArrayList<>();
   private List<String> acknowledgedStartupWarnings = new ArrayList<>();

   public int getInitialEnergyMax() {
      return Math.max(0, Math.min(200, this.initialEnergyMax));
   }

   public void setInitialEnergyMax(int value) {
      this.initialEnergyMax = Math.max(0, Math.min(200, value));
   }

   public double getEnergyGainRate() {
      return Math.max(0.1, Math.min(10.0, this.energyGainRate));
   }

   public void setEnergyGainRate(double value) {
      this.energyGainRate = Math.max(0.1, Math.min(10.0, value));
   }

   public int getActionSoundVolumePercent() {
      return Math.max(0, Math.min(200, this.actionSoundVolumePercent));
   }

   public float getActionSoundVolumeMultiplier() {
      return this.getActionSoundVolumePercent() / 100.0F;
   }

   public void setActionSoundVolumePercent(int value) {
      this.actionSoundVolumePercent = Math.max(0, Math.min(200, value));
   }

   public boolean isIntifaceEnabled() {
      return this.intifaceEnabled;
   }

   public void setIntifaceEnabled(boolean value) {
      this.intifaceEnabled = value;
   }

   public boolean isIntifaceAutoConnectOnWorldJoin() {
      return this.intifaceAutoConnectOnWorldJoin;
   }

   public void setIntifaceAutoConnectOnWorldJoin(boolean value) {
      this.intifaceAutoConnectOnWorldJoin = value;
   }

   public boolean isIntifaceStopOnDisconnect() {
      return this.intifaceStopOnDisconnect;
   }

   public void setIntifaceStopOnDisconnect(boolean value) {
      this.intifaceStopOnDisconnect = value;
   }

   public String getIntifaceServerUrl() {
      return sanitizeIntifaceServerUrl(this.intifaceServerUrl);
   }

   public void setIntifaceServerUrl(String value) {
      this.intifaceServerUrl = sanitizeIntifaceServerUrl(value);
   }

   public int getIntifaceReactiveImpactStrengthPercent() {
      return clampPercent(this.intifaceReactiveImpactStrengthPercent);
   }

   public void setIntifaceReactiveImpactStrengthPercent(int value) {
      this.intifaceReactiveImpactStrengthPercent = clampPercent(value);
   }

   public int getIntifaceReactiveImpactDurationMs() {
      return clampInt(valueOrDefault(this.intifaceReactiveImpactDurationMs, 170), 20, 5000);
   }

   public void setIntifaceReactiveImpactDurationMs(int value) {
      this.intifaceReactiveImpactDurationMs = clampInt(value, 20, 5000);
   }

   public int getIntifacePeakReactiveImpactStrengthPercent() {
      return clampPercent(this.intifacePeakReactiveImpactStrengthPercent);
   }

   public void setIntifacePeakReactiveImpactStrengthPercent(int value) {
      this.intifacePeakReactiveImpactStrengthPercent = clampPercent(value);
   }

   public int getIntifacePeakReactiveImpactDurationMs() {
      return clampInt(valueOrDefault(this.intifacePeakReactiveImpactDurationMs, 500), 20, 5000);
   }

   public void setIntifacePeakReactiveImpactDurationMs(int value) {
      this.intifacePeakReactiveImpactDurationMs = clampInt(value, 20, 5000);
   }

   public int getIntifaceCooldownMs() {
      return clampInt(this.intifaceCooldownMs, 0, 5000);
   }

   public void setIntifaceCooldownMs(int value) {
      this.intifaceCooldownMs = clampInt(value, 0, 5000);
   }

   public int getIntifaceLatencyCompensationMs() {
      return clampInt(this.intifaceLatencyCompensationMs, 0, 1000);
   }

   public void setIntifaceLatencyCompensationMs(int value) {
      this.intifaceLatencyCompensationMs = clampInt(value, 0, 1000);
   }

   public int getIntifaceAnimationBaselineStrengthPercent() {
      return clampPercent(this.intifaceAnimationBaselineStrengthPercent);
   }

   public void setIntifaceAnimationBaselineStrengthPercent(int value) {
      this.intifaceAnimationBaselineStrengthPercent = clampPercent(value);
   }

   public int getIntifaceOscillatorRegularSpeedPercent() {
      return clampPercent(this.intifaceOscillatorRegularSpeedPercent);
   }

   public void setIntifaceOscillatorRegularSpeedPercent(int value) {
      this.intifaceOscillatorRegularSpeedPercent = clampPercent(value);
   }

   public int getIntifaceOscillatorPeakSpeedPercent() {
      return clampPercent(this.intifaceOscillatorPeakSpeedPercent);
   }

   public void setIntifaceOscillatorPeakSpeedPercent(int value) {
      this.intifaceOscillatorPeakSpeedPercent = clampPercent(value);
   }

   public int getIntifaceStrokerMinDistancePercent() {
      return clampPercent(this.intifaceStrokerMinDistancePercent);
   }

   public void setIntifaceStrokerMinDistancePercent(int value) {
      this.intifaceStrokerMinDistancePercent = Math.min(clampPercent(value), this.getIntifaceStrokerMaxDistancePercent());
   }

   public int getIntifaceStrokerMaxDistancePercent() {
      return clampPercent(this.intifaceStrokerMaxDistancePercent);
   }

   public void setIntifaceStrokerMaxDistancePercent(int value) {
      this.intifaceStrokerMaxDistancePercent = Math.max(clampPercent(value), this.getIntifaceStrokerMinDistancePercent());
   }

   public void setIntifaceStrokerDistancesPercent(int minValue, int maxValue) {
      int min = clampPercent(Math.min(minValue, maxValue));
      int max = clampPercent(Math.max(minValue, maxValue));
      this.intifaceStrokerMinDistancePercent = min;
      this.intifaceStrokerMaxDistancePercent = max;
   }

   public int getIntifaceStrokerRegularMoveDurationMs() {
      return clampInt(this.intifaceStrokerRegularMoveDurationMs, 0, 10000);
   }

   public void setIntifaceStrokerRegularMoveDurationMs(int value) {
      this.intifaceStrokerRegularMoveDurationMs = clampInt(value, 0, 10000);
   }

   public int getIntifaceStrokerPeakMoveDurationMs() {
      return clampInt(this.intifaceStrokerPeakMoveDurationMs, 0, 10000);
   }

   public void setIntifaceStrokerPeakMoveDurationMs(int value) {
      this.intifaceStrokerPeakMoveDurationMs = clampInt(value, 0, 10000);
   }

   public int getIntifaceEnergizedBasePercent(int level) {
      return switch (Math.max(1, Math.min(3, level))) {
         case 1 -> clampPercent(this.intifaceEnergizedOneBasePercent);
         case 2 -> clampPercent(this.intifaceEnergizedTwoBasePercent);
         default -> clampPercent(this.intifaceEnergizedThreeBasePercent);
      };
   }

   public void setIntifaceEnergizedBasePercent(int level, int value) {
      int clamped = clampPercent(value);
      switch (Math.max(1, Math.min(3, level))) {
         case 1:
            this.intifaceEnergizedOneBasePercent = clamped;
            break;
         case 2:
            this.intifaceEnergizedTwoBasePercent = clamped;
            break;
         default:
            this.intifaceEnergizedThreeBasePercent = clamped;
      }
   }

   public int getIntifaceEnergizedPulsePercent(int level) {
      return switch (Math.max(1, Math.min(3, level))) {
         case 1 -> clampPercent(this.intifaceEnergizedOnePulsePercent);
         case 2 -> clampPercent(this.intifaceEnergizedTwoPulsePercent);
         default -> clampPercent(this.intifaceEnergizedThreePulsePercent);
      };
   }

   public void setIntifaceEnergizedPulsePercent(int level, int value) {
      int clamped = clampPercent(value);
      switch (Math.max(1, Math.min(3, level))) {
         case 1:
            this.intifaceEnergizedOnePulsePercent = clamped;
            break;
         case 2:
            this.intifaceEnergizedTwoPulsePercent = clamped;
            break;
         default:
            this.intifaceEnergizedThreePulsePercent = clamped;
      }
   }

   public double getNearAnimationEnergyGainMult() {
      return Math.max(1.0, Math.min(10.0, this.nearAnimationEnergyGainMult));
   }

   public void setNearAnimationEnergyGainMult(double value) {
      this.nearAnimationEnergyGainMult = Math.max(1.0, Math.min(10.0, value));
   }

   public double getPlayerEnergyAuraMultLow() {
      return Math.max(1.0, Math.min(20.0, this.playerEnergyAuraMultLow));
   }

   public void setPlayerEnergyAuraMultLow(double value) {
      this.playerEnergyAuraMultLow = Math.max(1.0, Math.min(20.0, value));
   }

   public double getPlayerEnergyAuraMultMid() {
      return Math.max(1.0, Math.min(20.0, this.playerEnergyAuraMultMid));
   }

   public void setPlayerEnergyAuraMultMid(double value) {
      this.playerEnergyAuraMultMid = Math.max(1.0, Math.min(20.0, value));
   }

   public double getPlayerEnergyAuraMultHigh() {
      return Math.max(1.0, Math.min(20.0, this.playerEnergyAuraMultHigh));
   }

   public void setPlayerEnergyAuraMultHigh(double value) {
      this.playerEnergyAuraMultHigh = Math.max(1.0, Math.min(20.0, value));
   }

   public double getDestroyedSkinAuraMultStage1() {
      return this.clampDestroyedSkinAuraMult(this.destroyedSkinAuraMultStage1);
   }

   public void setDestroyedSkinAuraMultStage1(double value) {
      this.destroyedSkinAuraMultStage1 = this.clampDestroyedSkinAuraMult(value);
   }

   public double getDestroyedSkinAuraMultStage2() {
      return this.clampDestroyedSkinAuraMult(this.destroyedSkinAuraMultStage2);
   }

   public void setDestroyedSkinAuraMultStage2(double value) {
      this.destroyedSkinAuraMultStage2 = this.clampDestroyedSkinAuraMult(value);
   }

   public double getDestroyedSkinAuraMultStage3() {
      return this.clampDestroyedSkinAuraMult(this.destroyedSkinAuraMultStage3);
   }

   public void setDestroyedSkinAuraMultStage3(double value) {
      this.destroyedSkinAuraMultStage3 = this.clampDestroyedSkinAuraMult(value);
   }

   public double getDestroyedSkinAuraMultStage4() {
      return this.clampDestroyedSkinAuraMult(this.destroyedSkinAuraMultStage4);
   }

   public void setDestroyedSkinAuraMultStage4(double value) {
      this.destroyedSkinAuraMultStage4 = this.clampDestroyedSkinAuraMult(value);
   }

   private double clampDestroyedSkinAuraMult(double value) {
      return Math.max(1.0, Math.min(50.0, value));
   }

   public int getNormalDamageDestroyedSkinChancePercent() {
      return clampPercent(this.normalDamageDestroyedSkinChancePercent);
   }

   public void setNormalDamageDestroyedSkinChancePercent(int value) {
      this.normalDamageDestroyedSkinChancePercent = clampPercent(value);
   }

   public boolean isLastDefeatedEnabled() {
      return this.lastDefeatedEnabled;
   }

   public void setLastDefeatedEnabled(boolean value) {
      this.lastDefeatedEnabled = value;
   }

   public int getLastDefeatedEnergyThreshold() {
      return Math.max(0, Math.min(200, this.lastDefeatedEnergyThreshold));
   }

   public void setLastDefeatedEnergyThreshold(int value) {
      this.lastDefeatedEnergyThreshold = Math.max(0, Math.min(200, value));
   }

   public int getLastDefeatedSearchRadius() {
      return Math.max(1, Math.min(32, this.lastDefeatedSearchRadius));
   }

   public void setLastDefeatedSearchRadius(int value) {
      this.lastDefeatedSearchRadius = Math.max(1, Math.min(32, value));
   }

   public int getLastDefeatedCooldownSeconds() {
      return Math.max(0, Math.min(3600, this.lastDefeatedCooldownSeconds));
   }

   public void setLastDefeatedCooldownSeconds(int value) {
      this.lastDefeatedCooldownSeconds = Math.max(0, Math.min(3600, value));
   }

   public int getPlayerEnergyAuraRadiusMin() {
      return Math.max(1, Math.min(64, this.playerEnergyAuraRadiusMin));
   }

   public void setPlayerEnergyAuraRadiusMin(int value) {
      this.playerEnergyAuraRadiusMin = Math.max(1, Math.min(64, value));
   }

   public int getPlayerEnergyAuraRadiusMax() {
      return Math.max(this.getPlayerEnergyAuraRadiusMin(), Math.min(64, this.playerEnergyAuraRadiusMax));
   }

   public void setPlayerEnergyAuraRadiusMax(int value) {
      this.playerEnergyAuraRadiusMax = Math.max(1, Math.min(64, value));
   }

   public int getPlayerEnergyAuraPulseTicks() {
      return Math.max(1, Math.min(1200, this.playerEnergyAuraPulseTicks));
   }

   public void setPlayerEnergyAuraPulseTicks(int value) {
      this.playerEnergyAuraPulseTicks = Math.max(1, Math.min(1200, value));
   }

   public NonDebugChatMode debugChatMode() {
      return NonDebugChatMode.fromId(this.debugChatMode, NonDebugChatMode.SETUP_ERRORS);
   }

   public void setDebugChatMode(NonDebugChatMode mode) {
      NonDebugChatMode resolved = mode == null ? NonDebugChatMode.SETUP_ERRORS : mode;
      this.debugChatMode = resolved.id();
   }

   public boolean allowsDebugChat(NonDebugChatCategory category) {
      return this.debugChatMode().allows(category);
   }

   public boolean forceVanillaEntityTextures() {
      return this.forceVanillaEntityTextures;
   }

   public void setForceVanillaEntityTextures(boolean value) {
      this.forceVanillaEntityTextures = value;
   }

   public int blockSearchRadius() {
      return Math.max(1, Math.min(16, this.animationBlockSearchRadius));
   }

   public void setBlockSearchRadius(int value) {
      this.animationBlockSearchRadius = Math.max(1, Math.min(16, value));
   }

   public boolean autoSwitchThirdPersonOnAnimationStart() {
      return this.autoSwitchThirdPersonOnAnimationStart;
   }

   public void setAutoSwitchThirdPersonOnAnimationStart(boolean value) {
      this.autoSwitchThirdPersonOnAnimationStart = value;
   }

   public int getScanBudgetPerTick() {
      return Math.max(1, Math.min(512, this.scanBudgetPerTick));
   }

   public void setScanBudgetPerTick(int value) {
      this.scanBudgetPerTick = Math.max(1, Math.min(512, value));
   }

   public int getLoopProgressSeconds() {
      return Math.max(5, Math.min(300, this.loopProgressSeconds));
   }

   public void setLoopProgressSeconds(int seconds) {
      this.loopProgressSeconds = Math.max(5, Math.min(300, seconds));
   }

   public int getPeakLoopProgressSeconds() {
      return Math.max(1, Math.min(300, this.peakLoopProgressSeconds));
   }

   public void setPeakLoopProgressSeconds(int seconds) {
      this.peakLoopProgressSeconds = Math.max(1, Math.min(300, seconds));
   }

   public int getGenderMaleChancePercent() {
      return clampPercent(this.genderMaleChancePercent);
   }

   public void setGenderMaleChancePercent(int percent) {
      this.genderMaleChancePercent = clampPercent(percent);
   }

   public int getGenderFemaleChancePercent() {
      return clampPercent(this.genderFemaleChancePercent);
   }

   public void setGenderFemaleChancePercent(int percent) {
      this.genderFemaleChancePercent = clampPercent(percent);
   }

   public int getGenderBothChancePercent() {
      return clampPercent(this.genderBothChancePercent);
   }

   public void setGenderBothChancePercent(int percent) {
      this.genderBothChancePercent = clampPercent(percent);
   }

   public NonConfig.GenderSpawnChances getGlobalGenderSpawnChances() {
      NonConfig.GenderSpawnChances chances = new NonConfig.GenderSpawnChances(
         this.getGenderMaleChancePercent(), this.getGenderFemaleChancePercent(), this.getGenderBothChancePercent()
      );
      return chances.isValid() ? chances : defaultGenderSpawnChances();
   }

   public void setGlobalGenderSpawnChances(NonConfig.GenderSpawnChances chances) {
      NonConfig.GenderSpawnChances safe = validOrDefault(chances);
      this.setGenderMaleChancePercent(safe.male());
      this.setGenderFemaleChancePercent(safe.female());
      this.setGenderBothChancePercent(safe.both());
   }

   public int getMaleMaleChancePercent() {
      return Math.max(0, Math.min(100, this.maleMaleChancePercent));
   }

   public void setMaleMaleChancePercent(int percent) {
      this.maleMaleChancePercent = Math.max(0, Math.min(100, percent));
   }

   public int getFemaleFemaleChancePercent() {
      return Math.max(0, Math.min(100, this.femaleFemaleChancePercent));
   }

   public void setFemaleFemaleChancePercent(int percent) {
      this.femaleFemaleChancePercent = Math.max(0, Math.min(100, percent));
   }

   public int getEntityEntityChancePercent() {
      return Math.max(0, Math.min(100, this.entityEntityChancePercent));
   }

   public void setEntityEntityChancePercent(int percent) {
      this.entityEntityChancePercent = Math.max(0, Math.min(100, percent));
   }

   public int getMultiActorJoinChancePercent() {
      return Math.max(0, Math.min(100, this.multiActorJoinChancePercent));
   }

   public void setMultiActorJoinChancePercent(int percent) {
      this.multiActorJoinChancePercent = Math.max(0, Math.min(100, percent));
   }

   public NonConfig.PlayerGenderSelection getPlayerGenderSelection() {
      return NonConfig.PlayerGenderSelection.fromId(this.playerGender, NonConfig.PlayerGenderSelection.FEMALE);
   }

   public void setPlayerGenderSelection(NonConfig.PlayerGenderSelection selection) {
      NonConfig.PlayerGenderSelection resolved = selection == null ? NonConfig.PlayerGenderSelection.FEMALE : selection;
      this.playerGender = resolved.id();
   }

   public int getPlayerGenderMask() {
      return this.getPlayerGenderSelection().mask();
   }

   public boolean allowPlayerGenderChangeAnytime() {
      return this.allowPlayerGenderChangeAnytime;
   }

   public void setAllowPlayerGenderChangeAnytime(boolean value) {
      this.allowPlayerGenderChangeAnytime = value;
   }

   public boolean requirePlayerGenderSelectionOnJoin() {
      return this.requirePlayerGenderSelectionOnJoin;
   }

   public void setRequirePlayerGenderSelectionOnJoin(boolean value) {
      this.requirePlayerGenderSelectionOnJoin = value;
   }

   public int getAllowedStartingGenderMask() {
      int mask = this.allowedStartingGenderMask & 7;
      return mask == 0 ? 7 : mask;
   }

   public void setAllowedStartingGenderMask(int mask) {
      int normalized = mask & 7;
      this.allowedStartingGenderMask = normalized == 0 ? 7 : normalized;
   }

   public int getAllowedPlayerInteractionProfilesMask() {
      int normalized = this.allowedPlayerInteractionProfilesMask & 15;
      return normalized == 0 ? 15 : normalized;
   }

   public void setAllowedPlayerInteractionProfilesMask(int mask) {
      int normalized = mask & 15;
      this.allowedPlayerInteractionProfilesMask = normalized == 0 ? 15 : normalized;
   }

   public int getPlayerRequestMinEnergy() {
      return Math.max(0, Math.min(200, this.playerRequestMinEnergy));
   }

   public void setPlayerRequestMinEnergy(int value) {
      this.playerRequestMinEnergy = Math.max(0, Math.min(200, value));
   }

   public boolean isPregnancyEnabled() {
      return this.pregnancyEnabled;
   }

   public void setPregnancyEnabled(boolean value) {
      this.pregnancyEnabled = value;
   }

   public boolean isLiquidTankEnabled() {
      return this.liquidTankEnabled;
   }

   public void setLiquidTankEnabled(boolean value) {
      this.liquidTankEnabled = value;
   }

   public boolean isMessSystemEnabled() {
      return this.messSystemEnabled;
   }

   public void setMessSystemEnabled(boolean value) {
      this.messSystemEnabled = value;
   }

   public boolean isDestroyedSkinSystemEnabled() {
      return this.destroyedSkinSystemEnabled;
   }

   public void setDestroyedSkinSystemEnabled(boolean value) {
      this.destroyedSkinSystemEnabled = value;
   }

   public boolean isCraftingTableSkinRepairAllowed() {
      return this.allowCraftingTableSkinRepair;
   }

   public void setCraftingTableSkinRepairAllowed(boolean value) {
      this.allowCraftingTableSkinRepair = value;
   }

   public boolean keepMessAfterDeath() {
      return this.keepMessAfterDeath;
   }

   public void setKeepMessAfterDeath(boolean value) {
      this.keepMessAfterDeath = value;
   }

   public boolean keepRippedSkinAfterDeath() {
      return this.keepRippedSkinAfterDeath;
   }

   public void setKeepRippedSkinAfterDeath(boolean value) {
      this.keepRippedSkinAfterDeath = value;
   }

   public boolean keepLiquidTankAfterDeath() {
      return this.keepLiquidTankAfterDeath;
   }

   public void setKeepLiquidTankAfterDeath(boolean value) {
      this.keepLiquidTankAfterDeath = value;
   }

   public boolean keepPregnancyAfterDeath() {
      return this.keepPregnancyAfterDeath;
   }

   public void setKeepPregnancyAfterDeath(boolean value) {
      this.keepPregnancyAfterDeath = value;
   }

   public boolean isArmorShedEffectEnabled() {
      return this.armorShedEffectEnabled;
   }

   public void setArmorShedEffectEnabled(boolean value) {
      this.armorShedEffectEnabled = value;
   }

   public boolean isSyncFemaleGenderModWithNonGender() {
      return this.syncFemaleGenderModWithNonGender;
   }

   public void setSyncFemaleGenderModWithNonGender(boolean value) {
      this.syncFemaleGenderModWithNonGender = value;
   }

   public boolean isDestroyedSkinFemaleGenderOverridesEnabled() {
      return this.destroyedSkinFemaleGenderOverridesEnabled;
   }

   public void setDestroyedSkinFemaleGenderOverridesEnabled(boolean value) {
      this.destroyedSkinFemaleGenderOverridesEnabled = value;
   }

   public NonConfig.FemaleGenderDestroyedProfile getFemaleGenderDestroyedLow() {
      if (this.femaleGenderDestroyedLow == null) {
         this.femaleGenderDestroyedLow = NonConfig.FemaleGenderDestroyedProfile.lowDefault();
      }

      this.femaleGenderDestroyedLow = this.femaleGenderDestroyedLow.sanitized();
      return this.femaleGenderDestroyedLow;
   }

   public void setFemaleGenderDestroyedLow(NonConfig.FemaleGenderDestroyedProfile value) {
      this.femaleGenderDestroyedLow = value == null ? NonConfig.FemaleGenderDestroyedProfile.lowDefault() : value.sanitized();
   }

   public NonConfig.FemaleGenderDestroyedProfile getFemaleGenderDestroyedHigh() {
      if (this.femaleGenderDestroyedHigh == null) {
         this.femaleGenderDestroyedHigh = NonConfig.FemaleGenderDestroyedProfile.highDefault();
      }

      this.femaleGenderDestroyedHigh = this.femaleGenderDestroyedHigh.sanitized();
      return this.femaleGenderDestroyedHigh;
   }

   public void setFemaleGenderDestroyedHigh(NonConfig.FemaleGenderDestroyedProfile value) {
      this.femaleGenderDestroyedHigh = value == null ? NonConfig.FemaleGenderDestroyedProfile.highDefault() : value.sanitized();
   }

   public int getLiquidTankCapacityMl() {
      return Math.max(1, Math.min(10000, this.liquidTankCapacityMl));
   }

   public void setLiquidTankCapacityMl(int value) {
      this.liquidTankCapacityMl = Math.max(1, Math.min(10000, value));
   }

   public boolean isVanillaOverridesEnabled() {
      return this.vanillaOverridesEnabled;
   }

   public void setVanillaOverridesEnabled(boolean value) {
      this.vanillaOverridesEnabled = value;
   }

   public boolean useAnimationBreeding() {
      return this.vanillaOverridesEnabled && this.useAnimationBreeding;
   }

   public void setUseAnimationBreeding(boolean value) {
      this.useAnimationBreeding = value;
   }

   public boolean requireMaleFemaleForBreeding() {
      return this.vanillaOverridesEnabled && this.requireMaleFemaleForBreeding;
   }

   public void setRequireMaleFemaleForBreeding(boolean value) {
      this.requireMaleFemaleForBreeding = value;
   }

   public boolean femaleCowOnlyMilking() {
      return this.vanillaOverridesEnabled && this.femaleCowOnlyMilking;
   }

   public void setFemaleCowOnlyMilking(boolean value) {
      this.femaleCowOnlyMilking = value;
   }

   public boolean canAttackCreativePlayers() {
      return this.attackCreativePlayers;
   }

   public void setAttackCreativePlayers(boolean value) {
      this.attackCreativePlayers = value;
   }

   public boolean convertMaleOnlyVInjectionsToA() {
      return this.convertMaleOnlyVInjectionsToA;
   }

   public void setConvertMaleOnlyVInjectionsToA(boolean value) {
      this.convertMaleOnlyVInjectionsToA = value;
   }

   public int getAttackEscapeHits() {
      return Math.max(1, Math.min(50, this.attackEscapeHits));
   }

   public void setAttackEscapeHits(int hits) {
      this.attackEscapeHits = Math.max(1, Math.min(50, hits));
   }

   public double getAttackDecayPerSecond() {
      return Math.max(0.0, Math.min(5.0, this.attackDecayPerSecond));
   }

   public void setAttackDecayPerSecond(double decay) {
      this.attackDecayPerSecond = Math.max(0.0, Math.min(5.0, decay));
   }

   public int getAttackEscapeDamageDifficultyPercent() {
      return clampAttackEscapeDamageDifficultyPercent(this.attackEscapeDamageDifficultyPercent);
   }

   public void setAttackEscapeDamageDifficultyPercent(int percent) {
      this.attackEscapeDamageDifficultyPercent = clampAttackEscapeDamageDifficultyPercent(percent);
   }

   public int getPvpFullHealthEscapeBonusPercent() {
      return clampPvpMashPercent(this.pvpFullHealthEscapeBonusPercent);
   }

   public void setPvpFullHealthEscapeBonusPercent(int percent) {
      this.pvpFullHealthEscapeBonusPercent = clampPvpMashPercent(percent);
   }

   public int getPvpCriticalHealthMashStrengthPercent() {
      return clampPvpMashPercent(this.pvpCriticalHealthMashStrengthPercent);
   }

   public void setPvpCriticalHealthMashStrengthPercent(int percent) {
      this.pvpCriticalHealthMashStrengthPercent = clampPvpMashPercent(percent);
   }

   public int getPvpMaxHeatEscapeStrengthPercent() {
      return clampPvpMashPercent(this.pvpMaxHeatEscapeStrengthPercent);
   }

   public void setPvpMaxHeatEscapeStrengthPercent(int percent) {
      this.pvpMaxHeatEscapeStrengthPercent = clampPvpMashPercent(percent);
   }

   public int getPvpZeroHeatAttackStrengthPercent() {
      return clampPvpMashPercent(this.pvpZeroHeatAttackStrengthPercent);
   }

   public void setPvpZeroHeatAttackStrengthPercent(int percent) {
      this.pvpZeroHeatAttackStrengthPercent = clampPvpMashPercent(percent);
   }

   public int getAttackEscapeInvulnerabilitySeconds() {
      return clampAttackEscapeProtectionSeconds(this.attackEscapeInvulnerabilitySeconds);
   }

   public void setAttackEscapeInvulnerabilitySeconds(int seconds) {
      this.attackEscapeInvulnerabilityTicks = null;
      this.attackEscapeInvulnerabilitySeconds = clampAttackEscapeProtectionSeconds(seconds);
   }

   public int getAttackEscapeInvulnerabilityTicks() {
      return secondsToTicks(this.getAttackEscapeInvulnerabilitySeconds());
   }

   public int getAttackEscapeAnimationProtectionSeconds() {
      return clampAttackEscapeProtectionSeconds(this.attackEscapeAnimationProtectionSeconds);
   }

   public void setAttackEscapeAnimationProtectionSeconds(int seconds) {
      this.attackEscapeAnimationProtectionSeconds = clampAttackEscapeProtectionSeconds(seconds);
   }

   public int getAttackEscapeAnimationProtectionTicks() {
      return secondsToTicks(this.getAttackEscapeAnimationProtectionSeconds());
   }

   private static int clampAttackEscapeProtectionSeconds(int seconds) {
      return Math.max(0, Math.min(10, seconds));
   }

   private static int clampAttackEscapeDamageDifficultyPercent(int percent) {
      return Math.max(0, Math.min(200, percent));
   }

   private static int clampPvpMashPercent(int percent) {
      return Math.max(0, Math.min(200, percent));
   }

   private static int secondsToTicks(int seconds) {
      return clampAttackEscapeProtectionSeconds(seconds) * 20;
   }

   private static int ticksToSeconds(int ticks) {
      int clampedTicks = Math.max(0, ticks);
      return clampedTicks == 0 ? 0 : clampAttackEscapeProtectionSeconds((clampedTicks + 19) / 20);
   }

   public int getPostEscapeGatherMaxMobs() {
      return Math.max(0, Math.min(64, this.postEscapeGatherMaxMobs));
   }

   public void setPostEscapeGatherMaxMobs(int value) {
      this.postEscapeGatherMaxMobs = Math.max(0, Math.min(64, value));
   }

   public boolean blockAnimationsWhileRidingLivingEntities() {
      return this.blockAnimationsWhileRidingLivingEntities;
   }

   public void setBlockAnimationsWhileRidingLivingEntities(boolean value) {
      this.blockAnimationsWhileRidingLivingEntities = value;
   }

   public boolean blockAnimationsWhileRidingVehicles() {
      return this.blockAnimationsWhileRidingVehicles;
   }

   public void setBlockAnimationsWhileRidingVehicles(boolean value) {
      this.blockAnimationsWhileRidingVehicles = value;
   }

   public int getAttackOutcomeFailsafeThreshold() {
      return Math.max(1, Math.min(50, this.attackOutcomeFailsafeThreshold));
   }

   public void setAttackOutcomeFailsafeThreshold(int threshold) {
      this.attackOutcomeFailsafeThreshold = Math.max(1, Math.min(50, threshold));
   }

   public int getPregnancyChancePercent() {
      return !this.pregnancyEnabled ? 0 : Math.max(0, Math.min(100, this.pregnancyChancePercent));
   }

   public void setPregnancyChancePercent(int value) {
      this.pregnancyChancePercent = Math.max(0, Math.min(100, value));
   }

   public int getPregnancyDurationMinutes() {
      return Math.max(1, Math.min(5256000, this.pregnancyDurationMinutes));
   }

   public void setPregnancyDurationMinutes(int minutes) {
      this.pregnancyDurationMinutes = Math.max(1, Math.min(5256000, minutes));
   }

   public long getPregnancyDurationTicks() {
      return this.getPregnancyDurationMinutes() * 1200L;
   }

   public boolean isLiquidBasedPregnancyEnabled() {
      return this.liquidBasedPregnancy;
   }

   public void setLiquidBasedPregnancyEnabled(boolean value) {
      this.liquidBasedPregnancy = value;
   }

   public int getPregnancyRiskyStartDay() {
      return clampInt(this.pregnancyRiskyStartDay, 1, 28);
   }

   public void setPregnancyRiskyStartDay(int day) {
      this.pregnancyRiskyStartDay = clampInt(day, 1, 28);
      if (this.pregnancyOvulationEndDay < this.pregnancyRiskyStartDay + 2) {
         this.pregnancyOvulationEndDay = this.pregnancyRiskyStartDay + 2;
      }
   }

   public int getPregnancyOvulationEndDay() {
      return Math.max(this.getPregnancyRiskyStartDay() + 2, clampInt(this.pregnancyOvulationEndDay, 1, 30));
   }

   public void setPregnancyOvulationEndDay(int day) {
      this.pregnancyOvulationEndDay = Math.max(this.getPregnancyRiskyStartDay() + 2, clampInt(day, 1, 30));
   }

   public int getPregnancySafeDayChancePercent() {
      return clampInt(this.pregnancySafeDayChancePercent, 0, 1000);
   }

   public void setPregnancySafeDayChancePercent(int percent) {
      this.pregnancySafeDayChancePercent = clampInt(percent, 0, 1000);
   }

   public int getPregnancyRiskyChancePercent() {
      return this.pregnancyRiskyChancePercent == null ? 300 : clampInt(this.pregnancyRiskyChancePercent, 0, 1000);
   }

   public void setPregnancyRiskyChancePercent(int percent) {
      this.pregnancyRiskyChancePercent = clampInt(percent, 0, 1000);
   }

   public int getPregnancyOvulationChancePercent() {
      return clampInt(this.pregnancyOvulationChancePercent, 0, 1000);
   }

   public void setPregnancyOvulationChancePercent(int percent) {
      this.pregnancyOvulationChancePercent = clampInt(percent, 0, 1000);
   }

   public Map<String, Integer> getPregnancyChanceByEntity() {
      return this.pregnancyChanceByEntity == null ? Map.of() : Map.copyOf(this.pregnancyChanceByEntity);
   }

   public void setPregnancyChanceByEntity(Map<String, Integer> entries) {
      Map<String, Integer> cleaned = new LinkedHashMap<>();
      if (entries != null) {
         for (Entry<String, Integer> entry : entries.entrySet()) {
            if (entry != null) {
               String key = entry.getKey();
               if (key != null) {
                  key = key.trim();
                  if (!key.isEmpty()) {
                     int value = entry.getValue() == null ? 0 : entry.getValue();
                     value = Math.max(0, Math.min(100, value));
                     cleaned.put(key, value);
                  }
               }
            }
         }
      }

      this.pregnancyChanceByEntity = cleaned;
   }

   public int getPregnancyChanceFor(String entityId) {
      if (!this.pregnancyEnabled) {
         return 0;
      } else if (entityId != null && !entityId.isBlank() && this.pregnancyChanceByEntity != null) {
         Integer override = this.pregnancyChanceByEntity.get(entityId);
         return override == null ? this.getPregnancyChancePercent() : Math.max(0, Math.min(100, override));
      } else {
         return this.getPregnancyChancePercent();
      }
   }

   public Map<String, NonConfig.OffspringCountRange> getPregnancyOffspringCountByEntity() {
      if (this.pregnancyOffspringCountByEntity == null) {
         return Map.of();
      }

      Map<String, NonConfig.OffspringCountRange> copy = new LinkedHashMap<>();

      for (Entry<String, NonConfig.OffspringCountRange> entry : this.pregnancyOffspringCountByEntity.entrySet()) {
         if (entry != null && entry.getKey() != null && entry.getValue() != null) {
            copy.put(entry.getKey(), entry.getValue().sanitized());
         }
      }

      return copy;
   }

   public void setPregnancyOffspringCountByEntity(Map<String, NonConfig.OffspringCountRange> entries) {
      Map<String, NonConfig.OffspringCountRange> cleaned = new LinkedHashMap<>();
      if (entries != null) {
         for (Entry<String, NonConfig.OffspringCountRange> entry : entries.entrySet()) {
            if (entry != null) {
               String key = entry.getKey();
               if (key != null) {
                  key = key.trim();
                  if (!key.isEmpty()) {
                     NonConfig.OffspringCountRange range = entry.getValue() == null ? new NonConfig.OffspringCountRange() : entry.getValue().sanitized();
                     cleaned.put(key, range);
                  }
               }
            }
         }
      }

      this.pregnancyOffspringCountByEntity = cleaned;
   }

   public NonConfig.OffspringCountRange getPregnancyOffspringCountRangeFor(String entityId) {
      if (entityId != null && !entityId.isBlank() && this.pregnancyOffspringCountByEntity != null) {
         NonConfig.OffspringCountRange override = this.pregnancyOffspringCountByEntity.get(entityId);
         return override == null ? new NonConfig.OffspringCountRange() : override.sanitized();
      } else {
         return new NonConfig.OffspringCountRange();
      }
   }

   public int getPregnancyEggDefaultHatchSeconds() {
      return Math.max(1, Math.min(72000, this.pregnancyEggDefaultHatchSeconds));
   }

   public void setPregnancyEggDefaultHatchSeconds(int seconds) {
      this.pregnancyEggDefaultHatchSeconds = Math.max(1, Math.min(72000, seconds));
   }

   public Map<String, Boolean> getPregnancyEggEnabledByEntity() {
      return this.pregnancyEggEnabledByEntity == null ? Map.of() : Map.copyOf(this.pregnancyEggEnabledByEntity);
   }

   public void setPregnancyEggEnabledByEntity(Map<String, Boolean> entries) {
      Map<String, Boolean> cleaned = new LinkedHashMap<>();
      if (entries != null) {
         for (Entry<String, Boolean> entry : entries.entrySet()) {
            if (entry != null) {
               String key = entry.getKey();
               if (key != null) {
                  key = key.trim();
                  if (!key.isEmpty()) {
                     boolean enabled = Boolean.TRUE.equals(entry.getValue());
                     cleaned.put(key, enabled);
                  }
               }
            }
         }
      }

      this.pregnancyEggEnabledByEntity = cleaned;
   }

   public boolean isPregnancyEggEnabledFor(String entityId) {
      return entityId != null && !entityId.isBlank() && this.pregnancyEggEnabledByEntity != null
         ? Boolean.TRUE.equals(this.pregnancyEggEnabledByEntity.get(entityId))
         : false;
   }

   public Map<String, Integer> getPregnancyEggHatchSecondsByEntity() {
      return this.pregnancyEggHatchSecondsByEntity == null ? Map.of() : Map.copyOf(this.pregnancyEggHatchSecondsByEntity);
   }

   public void setPregnancyEggHatchSecondsByEntity(Map<String, Integer> entries) {
      Map<String, Integer> cleaned = new LinkedHashMap<>();
      if (entries != null) {
         for (Entry<String, Integer> entry : entries.entrySet()) {
            if (entry != null) {
               String key = entry.getKey();
               if (key != null) {
                  key = key.trim();
                  if (!key.isEmpty()) {
                     int value = entry.getValue() == null ? this.getPregnancyEggDefaultHatchSeconds() : entry.getValue();
                     value = Math.max(1, Math.min(72000, value));
                     cleaned.put(key, value);
                  }
               }
            }
         }
      }

      this.pregnancyEggHatchSecondsByEntity = cleaned;
   }

   public int getPregnancyEggHatchSecondsFor(String entityId) {
      int fallback = this.getPregnancyEggDefaultHatchSeconds();
      if (entityId != null && !entityId.isBlank() && this.pregnancyEggHatchSecondsByEntity != null) {
         Integer override = this.pregnancyEggHatchSecondsByEntity.get(entityId);
         return override == null ? fallback : Math.max(1, Math.min(72000, override));
      } else {
         return fallback;
      }
   }

   public boolean isPregnancyAutoTameMobs() {
      return this.pregnancyAutoTameMobs;
   }

   public void setPregnancyAutoTameMobs(boolean value) {
      this.pregnancyAutoTameMobs = value;
   }

   public boolean pregnancyFriendlyMobsIgnorePlayers() {
      return this.pregnancyFriendlyMobsIgnorePlayers;
   }

   public void setPregnancyFriendlyMobsIgnorePlayers(boolean value) {
      this.pregnancyFriendlyMobsIgnorePlayers = value;
   }

   public Map<String, NonConfig.EntityProfile> getEntityProfilesByEntity() {
      if (this.entityProfilesByEntity == null) {
         return Map.of();
      }

      Map<String, NonConfig.EntityProfile> copy = new LinkedHashMap<>();

      for (Entry<String, NonConfig.EntityProfile> entry : this.entityProfilesByEntity.entrySet()) {
         if (entry != null && entry.getKey() != null && entry.getValue() != null) {
            String key = entry.getKey().trim();
            if (!key.isEmpty()) {
               NonConfig.EntityProfile profile = entry.getValue().sanitized();
               if (!profile.isEmpty()) {
                  copy.put(key, profile);
               }
            }
         }
      }

      return copy;
   }

   public void setEntityProfilesByEntity(Map<String, NonConfig.EntityProfile> entries) {
      Map<String, NonConfig.EntityProfile> cleaned = new LinkedHashMap<>();
      if (entries != null) {
         for (Entry<String, NonConfig.EntityProfile> entry : entries.entrySet()) {
            if (entry != null && entry.getKey() != null && entry.getValue() != null) {
               String key = entry.getKey().trim();
               if (!key.isEmpty()) {
                  NonConfig.EntityProfile profile = entry.getValue().sanitized();
                  if (!profile.isEmpty()) {
                     cleaned.put(key, profile);
                  }
               }
            }
         }
      }

      this.entityProfilesByEntity = cleaned;
      NonEntityProfiles.invalidateEnergyGainMultiplierCache();
   }

   public double getLiquidDecayPerSecond() {
      return Math.max(0.0, Math.min(20.0, this.liquidDecayPerSecond));
   }

   public void setLiquidDecayPerSecond(double decay) {
      this.liquidDecayPerSecond = Math.max(0.0, Math.min(20.0, decay));
   }

   public int getLiquidPuddleDespawnSeconds() {
      return Math.max(1, Math.min(300, this.liquidPuddleDespawnSeconds));
   }

   public void setLiquidPuddleDespawnSeconds(int seconds) {
      this.liquidPuddleDespawnSeconds = Math.max(1, Math.min(300, seconds));
   }

   public double getPeakXpPerMl() {
      return Math.max(0.0, Math.min(10.0, this.peakXpPerMl));
   }

   public void setPeakXpPerMl(double value) {
      this.peakXpPerMl = Math.max(0.0, Math.min(10.0, value));
   }

   public float getFilledStageOneSpeedMult() {
      return this.clampMultiplier(this.filledStageOneSpeedMult);
   }

   public void setFilledStageOneSpeedMult(float value) {
      this.filledStageOneSpeedMult = this.clampMultiplier(value);
   }

   public float getFilledStageTwoSpeedMult() {
      return this.clampMultiplier(this.filledStageTwoSpeedMult);
   }

   public void setFilledStageTwoSpeedMult(float value) {
      this.filledStageTwoSpeedMult = this.clampMultiplier(value);
   }

   public float getFilledStageThreeSpeedMult() {
      return this.clampMultiplier(this.filledStageThreeSpeedMult);
   }

   public void setFilledStageThreeSpeedMult(float value) {
      this.filledStageThreeSpeedMult = this.clampMultiplier(value);
   }

   public float getFilledStageOneJumpMult() {
      return this.clampMultiplier(this.filledStageOneJumpMult);
   }

   public void setFilledStageOneJumpMult(float value) {
      this.filledStageOneJumpMult = this.clampMultiplier(value);
   }

   public float getFilledStageTwoJumpMult() {
      return this.clampMultiplier(this.filledStageTwoJumpMult);
   }

   public void setFilledStageTwoJumpMult(float value) {
      this.filledStageTwoJumpMult = this.clampMultiplier(value);
   }

   public float getFilledStageThreeJumpMult() {
      return this.clampMultiplier(this.filledStageThreeJumpMult);
   }

   public void setFilledStageThreeJumpMult(float value) {
      this.filledStageThreeJumpMult = this.clampMultiplier(value);
   }

   public int getUiEnergyOffsetX() {
      return this.clampUiOffset(this.uiEnergyOffsetX);
   }

   public void setUiEnergyOffsetX(int value) {
      this.uiEnergyOffsetX = this.clampUiOffset(value);
   }

   public int getUiEnergyOffsetY() {
      return this.clampUiOffset(this.uiEnergyOffsetY);
   }

   public void setUiEnergyOffsetY(int value) {
      this.uiEnergyOffsetY = this.clampUiOffset(value);
   }

   public int getUiEnergyHeartOffsetX() {
      return this.clampUiOffset(this.uiEnergyHeartOffsetX);
   }

   public void setUiEnergyHeartOffsetX(int value) {
      this.uiEnergyHeartOffsetX = this.clampUiOffset(value);
   }

   public int getUiEnergyHeartOffsetY() {
      return this.clampUiOffset(this.uiEnergyHeartOffsetY);
   }

   public void setUiEnergyHeartOffsetY(int value) {
      this.uiEnergyHeartOffsetY = this.clampUiOffset(value);
   }

   public int getUiAttackOffsetX() {
      return this.clampUiOffset(this.uiAttackOffsetX);
   }

   public void setUiAttackOffsetX(int value) {
      this.uiAttackOffsetX = this.clampUiOffset(value);
   }

   public int getUiAttackOffsetY() {
      return this.clampUiOffset(this.uiAttackOffsetY);
   }

   public void setUiAttackOffsetY(int value) {
      this.uiAttackOffsetY = this.clampUiOffset(value);
   }

   public int getUiPromptOffsetX() {
      return this.clampUiOffset(this.uiPromptOffsetX);
   }

   public void setUiPromptOffsetX(int value) {
      this.uiPromptOffsetX = this.clampUiOffset(value);
   }

   public int getUiPromptOffsetY() {
      return this.clampUiOffset(this.uiPromptOffsetY);
   }

   public void setUiPromptOffsetY(int value) {
      this.uiPromptOffsetY = this.clampUiOffset(value);
   }

   public int getUiLiquidOffsetX() {
      return this.clampUiOffset(this.uiLiquidOffsetX);
   }

   public void setUiLiquidOffsetX(int value) {
      this.uiLiquidOffsetX = this.clampUiOffset(value);
   }

   public int getUiLiquidOffsetY() {
      return this.clampUiOffset(this.uiLiquidOffsetY);
   }

   public void setUiLiquidOffsetY(int value) {
      this.uiLiquidOffsetY = this.clampUiOffset(value);
   }

   public boolean isUiEnergyVisible() {
      return this.uiEnergyVisible;
   }

   public void setUiEnergyVisible(boolean value) {
      this.uiEnergyVisible = value;
   }

   public boolean isUiEnergyHeartVisible() {
      return this.uiEnergyHeartVisible;
   }

   public void setUiEnergyHeartVisible(boolean value) {
      this.uiEnergyHeartVisible = value;
   }

   public boolean isUiAttackVisible() {
      return this.uiAttackVisible;
   }

   public void setUiAttackVisible(boolean value) {
      this.uiAttackVisible = value;
   }

   public boolean isUiPromptVisible() {
      return this.uiPromptVisible;
   }

   public void setUiPromptVisible(boolean value) {
      this.uiPromptVisible = value;
   }

   public boolean isUiLiquidVisible() {
      return this.uiLiquidVisible;
   }

   public void setUiLiquidVisible(boolean value) {
      this.uiLiquidVisible = value;
   }

   public boolean isDebugSpinMode() {
      return this.debugSpinMode;
   }

   public void setDebugSpinMode(boolean value) {
      this.debugSpinMode = value;
   }

   public String getDestroyedSkinTintHex() {
      return sanitizeHexColor(this.destroyedSkinTintHex);
   }

   public void setDestroyedSkinTintHex(String value) {
      this.destroyedSkinTintHex = sanitizeHexColor(value);
   }

   public boolean hasDestroyedSkinTintOverride() {
      return !this.getDestroyedSkinTintHex().isEmpty();
   }

   public int getDestroyedSkinTintRgb(int fallbackRgb) {
      String hex = this.getDestroyedSkinTintHex();
      if (hex.isEmpty()) {
         return fallbackRgb & 16777215;
      }

      try {
         return Integer.parseUnsignedInt(hex, 16) & 16777215;
      } catch (NumberFormatException e) {
         return fallbackRgb & 16777215;
      }
   }

   public Map<String, NonConfig.GenderSpawnChances> getGenderSpawnByEntity() {
      if (this.genderSpawnByEntity == null) {
         return Map.of();
      }

      Map<String, NonConfig.GenderSpawnChances> out = new LinkedHashMap<>();

      for (Entry<String, NonConfig.GenderSpawnChances> entry : this.genderSpawnByEntity.entrySet()) {
         if (entry != null) {
            String key = sanitizeKey(entry.getKey());
            if (key != null) {
               NonConfig.GenderSpawnChances chances = sanitizeGenderSpawnChances(entry.getValue());
               if (chances != null) {
                  out.put(key, chances);
               }
            }
         }
      }

      return Map.copyOf(out);
   }

   public void setGenderSpawnByEntity(Map<String, NonConfig.GenderSpawnChances> entries) {
      Map<String, NonConfig.GenderSpawnChances> cleaned = new LinkedHashMap<>();
      if (entries != null) {
         for (Entry<String, NonConfig.GenderSpawnChances> entry : entries.entrySet()) {
            if (entry != null) {
               String key = sanitizeKey(entry.getKey());
               if (key != null) {
                  NonConfig.GenderSpawnChances chances = sanitizeGenderSpawnChances(entry.getValue());
                  if (chances != null) {
                     cleaned.put(key, chances);
                  }
               }
            }
         }
      }

      this.genderSpawnByEntity = cleaned;
   }

   public Map<String, Integer> getLiquidGainByEntity() {
      return this.liquidGainByEntity == null ? Map.of() : Map.copyOf(this.liquidGainByEntity);
   }

   public int getLiquidGainFor(String entityId) {
      if (entityId != null && this.liquidGainByEntity != null) {
         Integer value = this.liquidGainByEntity.get(entityId);
         return value == null ? 0 : Math.max(0, Math.min(1000, value));
      } else {
         return 0;
      }
   }

   public void setLiquidGainByEntity(Map<String, Integer> entries) {
      Map<String, Integer> cleaned = new LinkedHashMap<>();
      if (entries != null) {
         for (Entry<String, Integer> entry : entries.entrySet()) {
            if (entry != null) {
               String key = entry.getKey();
               if (key != null) {
                  key = key.trim();
                  if (!key.isEmpty()) {
                     int value = entry.getValue() == null ? 0 : entry.getValue();
                     value = Math.max(0, Math.min(1000, value));
                     cleaned.put(key, value);
                  }
               }
            }
         }
      }

      this.liquidGainByEntity = cleaned;
   }

   public Map<String, String> getLiquidColorByEntity() {
      return this.liquidColorByEntity == null ? Map.of() : Map.copyOf(this.liquidColorByEntity);
   }

   public void setLiquidColorByEntity(Map<String, String> entries) {
      Map<String, String> cleaned = new LinkedHashMap<>();
      if (entries != null) {
         for (Entry<String, String> entry : entries.entrySet()) {
            if (entry != null) {
               String key = entry.getKey();
               if (key != null) {
                  key = key.trim();
                  if (!key.isEmpty()) {
                     String normalizedHex = NonLiquidColors.normalizeHexColor(entry.getValue());
                     if (normalizedHex != null) {
                        cleaned.put(key, normalizedHex);
                     }
                  }
               }
            }
         }
      }

      this.liquidColorByEntity = cleaned;
   }

   public Integer getLiquidColorOverrideRgb(String entityId) {
      return entityId != null && this.liquidColorByEntity != null ? NonLiquidColors.resolveEntityOverrideFromMap(this.liquidColorByEntity, entityId) : null;
   }

   public List<String> getExternalNoNPackLoadOrder() {
      return this.externalNoNPackLoadOrder == null ? List.of() : List.copyOf(this.externalNoNPackLoadOrder);
   }

   public void setExternalNoNPackLoadOrder(List<String> order) {
      this.externalNoNPackLoadOrder = sanitizePackLoadOrder(order);
   }

   public List<String> getDisabledAnimationPacks() {
      return this.disabledAnimationPacks == null ? List.of() : List.copyOf(this.disabledAnimationPacks);
   }

   public void setDisabledAnimationPacks(List<String> packIds) {
      this.disabledAnimationPacks = sanitizeStringList(packIds);
   }

   public List<String> getDisabledAnimations() {
      return this.disabledAnimations == null ? List.of() : List.copyOf(this.disabledAnimations);
   }

   public void setDisabledAnimations(List<String> animationIds) {
      this.disabledAnimations = sanitizeStringList(animationIds);
   }

   public List<String> getAcknowledgedStartupWarnings() {
      return this.acknowledgedStartupWarnings == null ? List.of() : List.copyOf(this.acknowledgedStartupWarnings);
   }

   public void setAcknowledgedStartupWarnings(List<String> warningIds) {
      this.acknowledgedStartupWarnings = sanitizeAcknowledgedStartupWarnings(warningIds);
   }

   public boolean isStartupWarningAcknowledged(String warningId) {
      String normalized = sanitizeWarningId(warningId);
      return normalized != null && this.getAcknowledgedStartupWarnings().contains(normalized);
   }

   public boolean acknowledgeStartupWarning(String warningId) {
      String normalized = sanitizeWarningId(warningId);
      if (normalized == null) {
         return false;
      }

      LinkedHashSet<String> merged = new LinkedHashSet<>(this.getAcknowledgedStartupWarnings());
      if (!merged.add(normalized)) {
         return false;
      }

      this.setAcknowledgedStartupWarnings(new ArrayList<>(merged));
      return true;
   }

   private static List<String> sanitizePackLoadOrder(List<String> order) {
      if (order != null && !order.isEmpty()) {
         LinkedHashSet<String> cleaned = new LinkedHashSet<>();

         for (String raw : order) {
            if (raw != null) {
               String trimmed = raw.trim();
               if (!trimmed.isEmpty()) {
                  cleaned.add(trimmed);
               }
            }
         }

         return new ArrayList<>(cleaned);
      } else {
         return new ArrayList<>();
      }
   }

   private static List<String> sanitizeStringList(List<String> values) {
      if (values != null && !values.isEmpty()) {
         LinkedHashSet<String> cleaned = new LinkedHashSet<>();

         for (String raw : values) {
            if (raw != null) {
               String trimmed = raw.trim();
               if (!trimmed.isEmpty()) {
                  cleaned.add(trimmed);
               }
            }
         }

         return new ArrayList<>(cleaned);
      } else {
         return new ArrayList<>();
      }
   }

   private static List<String> sanitizeAcknowledgedStartupWarnings(List<String> warningIds) {
      if (warningIds != null && !warningIds.isEmpty()) {
         LinkedHashSet<String> cleaned = new LinkedHashSet<>();

         for (String raw : warningIds) {
            String normalized = sanitizeWarningId(raw);
            if (normalized != null) {
               cleaned.add(normalized);
            }
         }

         return cleaned.stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toCollection(ArrayList::new));
      } else {
         return new ArrayList<>();
      }
   }

   private static String sanitizeWarningId(String raw) {
      if (raw == null) {
         return null;
      }

      String value = raw.trim();
      return value.isEmpty() ? null : value;
   }

   private int clampUiOffset(int value) {
      return Math.max(-999, Math.min(999, value));
   }

   private float clampMultiplier(float value) {
      return !Float.isFinite(value) ? 1.0F : Math.max(0.1F, Math.min(1.0F, value));
   }

   private static int clampPercent(int value) {
      return Math.max(0, Math.min(100, value));
   }

   private static int clampInt(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private static int valueOrDefault(int value, int fallback) {
      return value <= 0 ? fallback : value;
   }

   private static String sanitizeIntifaceServerUrl(String raw) {
      if (raw != null && !raw.isBlank()) {
         String value = raw.trim();

         try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null) {
               return "ws://127.0.0.1:12345";
            } else if (!scheme.equalsIgnoreCase("ws") && !scheme.equalsIgnoreCase("wss")) {
               return "ws://127.0.0.1:12345";
            } else {
               return uri.getHost() != null && !uri.getHost().isBlank() ? value : "ws://127.0.0.1:12345";
            }
         } catch (URISyntaxException e) {
            return "ws://127.0.0.1:12345";
         }
      } else {
         return "ws://127.0.0.1:12345";
      }
   }

   private static float clampFloat(float value, float min, float max) {
      return !Float.isFinite(value) ? min : Math.max(min, Math.min(max, value));
   }

   public static NonConfig.GenderSpawnChances defaultGenderSpawnChances() {
      return new NonConfig.GenderSpawnChances(48, 47, 5);
   }

   public static NonConfig.GenderSpawnChances validOrDefault(NonConfig.GenderSpawnChances chances) {
      NonConfig.GenderSpawnChances safe = sanitizeGenderSpawnChances(chances);
      return safe == null ? defaultGenderSpawnChances() : safe;
   }

   public static NonConfig.GenderSpawnChances sanitizeGenderSpawnChances(NonConfig.GenderSpawnChances chances) {
      if (chances == null) {
         return null;
      }

      NonConfig.GenderSpawnChances safe = chances.sanitized();
      return safe.isValid() ? safe : null;
   }

   public static String normalizeBirthMode(String value) {
      if (value == null) {
         return null;
      } else {
         String normalized = value.trim().toLowerCase(Locale.ROOT);
         if ("egg".equals(normalized)) {
            return "egg";
         } else {
            return "direct".equals(normalized) ? "direct" : null;
         }
      }
   }

   public static String normalizeEntityIdString(String value) {
      if (value == null) {
         return null;
      }

      String normalized = value.trim();
      return normalized.isEmpty() ? null : normalized;
   }

   public static String normalizeTextureIdString(String value) {
      if (value == null) {
         return null;
      }

      String normalized = value.trim();
      if (normalized.isEmpty()) {
         return null;
      }

      Identifier parsed = Identifier.tryParse(normalized);
      return parsed != null && !parsed.getPath().isBlank() ? normalized : null;
   }

   private static String sanitizeKey(String raw) {
      if (raw == null) {
         return null;
      }

      String key = raw.trim();
      return key.isEmpty() ? null : key;
   }

   private static String sanitizeHexColor(String raw) {
      if (raw == null) {
         return "";
      }

      String value = raw.trim();
      if (value.startsWith("#")) {
         value = value.substring(1);
      }

      value = value.toUpperCase(Locale.ROOT);
      if (value.isEmpty()) {
         return "";
      } else {
         return value.matches("[0-9A-F]{6}") ? value : "";
      }
   }

   private static Map<String, Boolean> defaultPregnancyEggEnabledMap() {
      Map<String, Boolean> map = new LinkedHashMap<>();
      map.put("minecraft:spider", true);
      map.put("minecraft:cave_spider", true);
      return map;
   }

   private static Map<String, NonConfig.OffspringCountRange> defaultPregnancyOffspringCountMap() {
      Map<String, NonConfig.OffspringCountRange> map = new LinkedHashMap<>();
      map.put("minecraft:spider", new NonConfig.OffspringCountRange(1, 3));
      map.put("minecraft:cave_spider", new NonConfig.OffspringCountRange(2, 5));
      map.put("minecraft:wolf", new NonConfig.OffspringCountRange(1, 2));
      map.put("minecraft:horse", new NonConfig.OffspringCountRange(1, 1));
      map.put("minecraft:fox", new NonConfig.OffspringCountRange(1, 2));
      map.put("minecraft:dolphin", new NonConfig.OffspringCountRange(1, 1));
      map.put("minecraft:pig", new NonConfig.OffspringCountRange(1, 3));
      return map;
   }

   private static Map<String, NonConfig.GenderSpawnChances> defaultGenderSpawnByEntityMap() {
      Map<String, NonConfig.GenderSpawnChances> map = new LinkedHashMap<>();
      NonConfig.GenderSpawnChances maleOnly = new NonConfig.GenderSpawnChances(100, 0, 0);
      map.put("minecraft:spider", maleOnly);
      map.put("minecraft:cave_spider", maleOnly);
      return map;
   }

   private void ensureDefaults() {
      if (this.debugChatMode == null) {
         this.debugChatMode = NonDebugChatMode.SETUP_ERRORS.id();
      } else {
         this.debugChatMode = this.debugChatMode().id();
      }

      this.animationBlockSearchRadius = this.blockSearchRadius();
      this.pregnancyChancePercent = this.getPregnancyChancePercent();
      this.pregnancyDurationMinutes = this.getPregnancyDurationMinutes();
      this.pregnancyRiskyStartDay = this.getPregnancyRiskyStartDay();
      this.pregnancyOvulationEndDay = this.getPregnancyOvulationEndDay();
      this.pregnancySafeDayChancePercent = this.getPregnancySafeDayChancePercent();
      if (this.pregnancyRiskyChancePercent == null) {
         if (this.pregnancyOvulationChancePercent == 500) {
            this.pregnancyRiskyChancePercent = 300;
         } else {
            this.pregnancyRiskyChancePercent = clampInt(this.pregnancyOvulationChancePercent, 0, 1000);
            this.pregnancyOvulationChancePercent = 500;
         }
      }

      this.pregnancyRiskyChancePercent = this.getPregnancyRiskyChancePercent();
      this.pregnancyOvulationChancePercent = this.getPregnancyOvulationChancePercent();
      this.pregnancyChanceByEntity = new LinkedHashMap<>();
      this.pregnancyOffspringCountByEntity = new LinkedHashMap<>();
      this.pregnancyEggDefaultHatchSeconds = this.getPregnancyEggDefaultHatchSeconds();
      this.pregnancyEggEnabledByEntity = new LinkedHashMap<>();
      this.pregnancyEggHatchSecondsByEntity = new LinkedHashMap<>();
      this.pregnancyAutoTameMobs = this.isPregnancyAutoTameMobs();
      this.pregnancyFriendlyMobsIgnorePlayers = this.pregnancyFriendlyMobsIgnorePlayers();
      this.setEntityProfilesByEntity(this.entityProfilesByEntity);
      Map<String, Integer> mergedGains = new LinkedHashMap<>();
      if (this.liquidGainByEntity != null) {
         this.setLiquidGainByEntity(this.liquidGainByEntity);
         mergedGains.putAll(this.liquidGainByEntity);
      }

      this.liquidGainByEntity = mergedGains;
      Map<String, String> mergedColors = new LinkedHashMap<>();
      if (this.liquidColorByEntity != null) {
         this.setLiquidColorByEntity(this.liquidColorByEntity);
         mergedColors.putAll(this.liquidColorByEntity);
      }

      this.liquidColorByEntity = mergedColors;
      this.setExternalNoNPackLoadOrder(this.externalNoNPackLoadOrder);
      this.setDisabledAnimationPacks(this.disabledAnimationPacks);
      this.setDisabledAnimations(this.disabledAnimations);
      this.destroyedSkinTintHex = this.getDestroyedSkinTintHex();
      this.liquidPuddleDespawnSeconds = this.getLiquidPuddleDespawnSeconds();
      this.peakXpPerMl = this.getPeakXpPerMl();
      this.initialEnergyMax = this.getInitialEnergyMax();
      this.energyGainRate = this.getEnergyGainRate();
      this.nearAnimationEnergyGainMult = this.getNearAnimationEnergyGainMult();
      this.playerEnergyAuraMultLow = this.getPlayerEnergyAuraMultLow();
      this.playerEnergyAuraMultMid = this.getPlayerEnergyAuraMultMid();
      this.playerEnergyAuraMultHigh = this.getPlayerEnergyAuraMultHigh();
      this.destroyedSkinAuraMultStage1 = this.getDestroyedSkinAuraMultStage1();
      this.destroyedSkinAuraMultStage2 = this.getDestroyedSkinAuraMultStage2();
      this.destroyedSkinAuraMultStage3 = this.getDestroyedSkinAuraMultStage3();
      this.destroyedSkinAuraMultStage4 = this.getDestroyedSkinAuraMultStage4();
      this.normalDamageDestroyedSkinChancePercent = this.getNormalDamageDestroyedSkinChancePercent();
      this.lastDefeatedEnabled = this.isLastDefeatedEnabled();
      this.lastDefeatedEnergyThreshold = this.getLastDefeatedEnergyThreshold();
      this.lastDefeatedSearchRadius = this.getLastDefeatedSearchRadius();
      this.lastDefeatedCooldownSeconds = this.getLastDefeatedCooldownSeconds();
      this.playerEnergyAuraRadiusMin = this.getPlayerEnergyAuraRadiusMin();
      this.playerEnergyAuraRadiusMax = this.getPlayerEnergyAuraRadiusMax();
      this.playerEnergyAuraPulseTicks = this.getPlayerEnergyAuraPulseTicks();
      this.genderSpawnByEntity = new LinkedHashMap<>();
      this.setGlobalGenderSpawnChances(this.getGlobalGenderSpawnChances());
      this.maleMaleChancePercent = this.getMaleMaleChancePercent();
      this.femaleFemaleChancePercent = this.getFemaleFemaleChancePercent();
      this.entityEntityChancePercent = this.getEntityEntityChancePercent();
      this.multiActorJoinChancePercent = this.getMultiActorJoinChancePercent();
      this.setPlayerGenderSelection(this.getPlayerGenderSelection());
      this.allowPlayerGenderChangeAnytime = this.allowPlayerGenderChangeAnytime();
      this.requirePlayerGenderSelectionOnJoin = this.requirePlayerGenderSelectionOnJoin();
      this.setAllowedStartingGenderMask(this.allowedStartingGenderMask);
      this.setAllowedPlayerInteractionProfilesMask(this.allowedPlayerInteractionProfilesMask);
      this.playerRequestMinEnergy = this.getPlayerRequestMinEnergy();
      this.setAcknowledgedStartupWarnings(this.acknowledgedStartupWarnings);
      this.messSystemEnabled = this.isMessSystemEnabled();
      this.destroyedSkinSystemEnabled = this.isDestroyedSkinSystemEnabled();
      this.allowCraftingTableSkinRepair = this.isCraftingTableSkinRepairAllowed();
      this.keepMessAfterDeath = this.keepMessAfterDeath();
      this.keepRippedSkinAfterDeath = this.keepRippedSkinAfterDeath();
      this.keepLiquidTankAfterDeath = this.keepLiquidTankAfterDeath();
      this.keepPregnancyAfterDeath = this.keepPregnancyAfterDeath();
      this.armorShedEffectEnabled = this.isArmorShedEffectEnabled();
      this.syncFemaleGenderModWithNonGender = this.isSyncFemaleGenderModWithNonGender();
      this.destroyedSkinFemaleGenderOverridesEnabled = this.isDestroyedSkinFemaleGenderOverridesEnabled();
      this.setFemaleGenderDestroyedLow(this.femaleGenderDestroyedLow);
      this.setFemaleGenderDestroyedHigh(this.femaleGenderDestroyedHigh);
      this.actionSoundVolumePercent = this.getActionSoundVolumePercent();
      this.intifaceServerUrl = this.getIntifaceServerUrl();
      this.intifaceReactiveImpactStrengthPercent = this.getIntifaceReactiveImpactStrengthPercent();
      this.intifaceReactiveImpactDurationMs = this.getIntifaceReactiveImpactDurationMs();
      this.intifacePeakReactiveImpactStrengthPercent = this.getIntifacePeakReactiveImpactStrengthPercent();
      this.intifacePeakReactiveImpactDurationMs = this.getIntifacePeakReactiveImpactDurationMs();
      this.intifaceCooldownMs = this.getIntifaceCooldownMs();
      this.intifaceLatencyCompensationMs = this.getIntifaceLatencyCompensationMs();
      this.intifaceAnimationBaselineStrengthPercent = this.getIntifaceAnimationBaselineStrengthPercent();
      this.intifaceOscillatorRegularSpeedPercent = this.getIntifaceOscillatorRegularSpeedPercent();
      this.intifaceOscillatorPeakSpeedPercent = this.getIntifaceOscillatorPeakSpeedPercent();
      this.intifaceStrokerMaxDistancePercent = this.getIntifaceStrokerMaxDistancePercent();
      this.intifaceStrokerMinDistancePercent = this.getIntifaceStrokerMinDistancePercent();
      this.intifaceStrokerRegularMoveDurationMs = this.getIntifaceStrokerRegularMoveDurationMs();
      this.intifaceStrokerPeakMoveDurationMs = this.getIntifaceStrokerPeakMoveDurationMs();
      this.intifaceEnergizedOneBasePercent = this.getIntifaceEnergizedBasePercent(1);
      this.intifaceEnergizedOnePulsePercent = this.getIntifaceEnergizedPulsePercent(1);
      this.intifaceEnergizedTwoBasePercent = this.getIntifaceEnergizedBasePercent(2);
      this.intifaceEnergizedTwoPulsePercent = this.getIntifaceEnergizedPulsePercent(2);
      this.intifaceEnergizedThreeBasePercent = this.getIntifaceEnergizedBasePercent(3);
      this.intifaceEnergizedThreePulsePercent = this.getIntifaceEnergizedPulsePercent(3);
      this.attackEscapeHits = this.getAttackEscapeHits();
      this.attackDecayPerSecond = this.getAttackDecayPerSecond();
      this.attackEscapeDamageDifficultyPercent = this.getAttackEscapeDamageDifficultyPercent();
      this.pvpFullHealthEscapeBonusPercent = this.getPvpFullHealthEscapeBonusPercent();
      this.pvpCriticalHealthMashStrengthPercent = this.getPvpCriticalHealthMashStrengthPercent();
      this.pvpMaxHeatEscapeStrengthPercent = this.getPvpMaxHeatEscapeStrengthPercent();
      this.pvpZeroHeatAttackStrengthPercent = this.getPvpZeroHeatAttackStrengthPercent();
      if (this.attackEscapeInvulnerabilityTicks != null) {
         this.attackEscapeInvulnerabilitySeconds = ticksToSeconds(this.attackEscapeInvulnerabilityTicks);
         this.attackEscapeInvulnerabilityTicks = null;
      }

      this.attackEscapeInvulnerabilitySeconds = this.getAttackEscapeInvulnerabilitySeconds();
      this.attackEscapeAnimationProtectionSeconds = this.getAttackEscapeAnimationProtectionSeconds();
      this.postEscapeGatherMaxMobs = this.getPostEscapeGatherMaxMobs();
      this.blockAnimationsWhileRidingLivingEntities = this.blockAnimationsWhileRidingLivingEntities();
      this.blockAnimationsWhileRidingVehicles = this.blockAnimationsWhileRidingVehicles();
      this.attackOutcomeFailsafeThreshold = this.getAttackOutcomeFailsafeThreshold();
      this.filledStageOneSpeedMult = this.clampMultiplier(this.filledStageOneSpeedMult);
      this.filledStageTwoSpeedMult = this.clampMultiplier(this.filledStageTwoSpeedMult);
      this.filledStageThreeSpeedMult = this.clampMultiplier(this.filledStageThreeSpeedMult);
      this.filledStageOneJumpMult = this.clampMultiplier(this.filledStageOneJumpMult);
      this.filledStageTwoJumpMult = this.clampMultiplier(this.filledStageTwoJumpMult);
      this.filledStageThreeJumpMult = this.clampMultiplier(this.filledStageThreeJumpMult);
   }

   public static NonConfig load() {
      NonConfig cfg = new NonConfig();
      boolean saveNormalizedConfig = !Files.isRegularFile(PATH);
      if (Files.isRegularFile(PATH)) {
         try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
            NonConfig fromDisk = (NonConfig)GSON.fromJson(reader, NonConfig.class);
            if (fromDisk != null) {
               cfg = fromDisk;
            }

            saveNormalizedConfig = true;
         } catch (JsonParseException | IllegalStateException e) {
            Path backup = preserveInvalidConfig();
            NeedsOfNature.LOGGER
               .warn(
                  "[NoN] Invalid config at {}. Using defaults{}.", new Object[]{PATH, backup == null ? "" : " and preserving the invalid file as " + backup, e}
               );
            saveNormalizedConfig = backup != null;
         } catch (IOException e) {
            NeedsOfNature.LOGGER.warn("[NoN] Failed to read config at {}. Using defaults.", PATH, e);
         }
      }

      cfg.ensureDefaults();
      if (saveNormalizedConfig) {
         cfg.save();
      }

      return cfg;
   }

   public NonConfig copy() {
      NonConfig copy = (NonConfig)GSON.fromJson(GSON.toJson(this), NonConfig.class);
      if (copy == null) {
         copy = new NonConfig();
      }

      copy.ensureDefaults();
      return copy;
   }

   public void save() {
      Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");

      try {
         Files.createDirectories(PATH.getParent());

         try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
         }

         try {
            Files.move(temporary, PATH, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
         }

         NeedsOfNature.onConfigSaved(this);
      } catch (IOException e) {
         NeedsOfNature.LOGGER.warn("[NoN] Failed to save config at {}.", PATH, e);

         try {
            Files.deleteIfExists(temporary);
         } catch (IOException var5) {
         }
      }
   }

   private static Path preserveInvalidConfig() {
      Path backup = PATH.resolveSibling(PATH.getFileName() + ".invalid-" + System.currentTimeMillis() + ".json");

      try {
         Files.move(PATH, backup, StandardCopyOption.REPLACE_EXISTING);
         return backup;
      } catch (IOException e) {
         NeedsOfNature.LOGGER.warn("[NoN] Failed to preserve invalid config at {}.", PATH, e);
         return null;
      }
   }

   public static final class EggProfile {
      private Float startSize;
      private Float endSize;
      private String texture;
      private Float health;

      public EggProfile() {
      }

      public EggProfile(Float startSize, Float endSize, String texture, Float health) {
         this.startSize = startSize;
         this.endSize = endSize;
         this.texture = texture;
         this.health = health;
      }

      public static NonConfig.EggProfile defaults() {
         return new NonConfig.EggProfile(0.5F, 1.0F, null, 2.0F);
      }

      public Float startSize() {
         return this.startSize == null ? null : NonConfig.clampFloat(this.startSize, 0.05F, 4.0F);
      }

      public Float endSize() {
         return this.endSize == null ? null : NonConfig.clampFloat(this.endSize, 0.05F, 4.0F);
      }

      public String texture() {
         return NonConfig.normalizeTextureIdString(this.texture);
      }

      public Float health() {
         return this.health == null ? null : NonConfig.clampFloat(this.health, 0.5F, 100.0F);
      }

      public NonConfig.EggProfile sanitized() {
         return new NonConfig.EggProfile(this.startSize(), this.endSize(), this.texture(), this.health());
      }

      public NonConfig.EggProfile resolvedOver(NonConfig.EggProfile fallback) {
         NonConfig.EggProfile base = fallback == null ? defaults() : fallback.sanitized();
         NonConfig.EggProfile safe = this.sanitized();
         return new NonConfig.EggProfile(
               safe.startSize() == null ? base.startSize() : safe.startSize(),
               safe.endSize() == null ? base.endSize() : safe.endSize(),
               safe.texture() == null ? base.texture() : safe.texture(),
               safe.health() == null ? base.health() : safe.health()
            )
            .sanitized();
      }

      public boolean isEmpty() {
         return this.startSize() == null && this.endSize() == null && this.texture() == null && this.health() == null;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else if (!(obj instanceof NonConfig.EggProfile other)) {
            return false;
         } else {
            NonConfig.EggProfile a = this.sanitized();
            NonConfig.EggProfile b = other.sanitized();
            return Objects.equals(a.startSize, b.startSize)
               && Objects.equals(a.endSize, b.endSize)
               && Objects.equals(a.texture, b.texture)
               && Objects.equals(a.health, b.health);
         }
      }

      @Override
      public int hashCode() {
         NonConfig.EggProfile safe = this.sanitized();
         return Objects.hash(safe.startSize, safe.endSize, safe.texture, safe.health);
      }
   }

   public static final class EntityProfile {
      private Integer pregnancyChancePercent;
      private Integer offspringMin;
      private Integer offspringMax;
      private String birthEntity;
      private String birthMode;
      private NonConfig.GenderSpawnChances genderSpawn;
      private NonConfig.EggProfile egg;
      private Boolean lookAtNearbyAnimations;
      private Integer multiActorJoinChancePercent;
      private Float energyGainMultiplier;

      public EntityProfile() {
      }

      public EntityProfile(
         Integer pregnancyChancePercent,
         Integer offspringMin,
         Integer offspringMax,
         String birthEntity,
         String birthMode,
         NonConfig.GenderSpawnChances genderSpawn
      ) {
         this(pregnancyChancePercent, offspringMin, offspringMax, birthEntity, birthMode, genderSpawn, null, null, null, null);
      }

      public EntityProfile(
         Integer pregnancyChancePercent,
         Integer offspringMin,
         Integer offspringMax,
         String birthEntity,
         String birthMode,
         NonConfig.GenderSpawnChances genderSpawn,
         NonConfig.EggProfile egg
      ) {
         this(pregnancyChancePercent, offspringMin, offspringMax, birthEntity, birthMode, genderSpawn, egg, null, null, null);
      }

      public EntityProfile(
         Integer pregnancyChancePercent,
         Integer offspringMin,
         Integer offspringMax,
         String birthEntity,
         String birthMode,
         NonConfig.GenderSpawnChances genderSpawn,
         NonConfig.EggProfile egg,
         Boolean lookAtNearbyAnimations,
         Integer multiActorJoinChancePercent,
         Float energyGainMultiplier
      ) {
         this.pregnancyChancePercent = pregnancyChancePercent;
         this.offspringMin = offspringMin;
         this.offspringMax = offspringMax;
         this.birthEntity = birthEntity;
         this.birthMode = birthMode;
         this.genderSpawn = genderSpawn;
         this.egg = egg;
         this.lookAtNearbyAnimations = lookAtNearbyAnimations;
         this.multiActorJoinChancePercent = multiActorJoinChancePercent;
         this.energyGainMultiplier = energyGainMultiplier;
      }

      public static NonConfig.EntityProfile full(
         int pregnancyChancePercent, int offspringMin, int offspringMax, String birthEntity, String birthMode, NonConfig.GenderSpawnChances genderSpawn
      ) {
         return full(pregnancyChancePercent, offspringMin, offspringMax, birthEntity, birthMode, genderSpawn, null, true, null, 1.0F);
      }

      public static NonConfig.EntityProfile full(
         int pregnancyChancePercent,
         int offspringMin,
         int offspringMax,
         String birthEntity,
         String birthMode,
         NonConfig.GenderSpawnChances genderSpawn,
         NonConfig.EggProfile egg
      ) {
         return full(pregnancyChancePercent, offspringMin, offspringMax, birthEntity, birthMode, genderSpawn, egg, true, null, 1.0F);
      }

      public static NonConfig.EntityProfile full(
         int pregnancyChancePercent,
         int offspringMin,
         int offspringMax,
         String birthEntity,
         String birthMode,
         NonConfig.GenderSpawnChances genderSpawn,
         NonConfig.EggProfile egg,
         boolean lookAtNearbyAnimations,
         Integer multiActorJoinChancePercent,
         float energyGainMultiplier
      ) {
         return new NonConfig.EntityProfile(
               NonConfig.clampPercent(pregnancyChancePercent),
               NonConfig.clampInt(offspringMin, 1, 16),
               NonConfig.clampInt(offspringMax, 1, 16),
               NonConfig.normalizeEntityIdString(birthEntity),
               NonConfig.normalizeBirthMode(birthMode),
               NonConfig.validOrDefault(genderSpawn),
               egg == null ? null : egg.sanitized(),
               lookAtNearbyAnimations,
               multiActorJoinChancePercent,
               energyGainMultiplier
            )
            .sanitized();
      }

      public Integer pregnancyChancePercent() {
         return this.pregnancyChancePercent == null ? null : NonConfig.clampPercent(this.pregnancyChancePercent);
      }

      public Integer offspringMin() {
         return this.offspringMin == null ? null : NonConfig.clampInt(this.offspringMin, 1, 16);
      }

      public Integer offspringMax() {
         return this.offspringMax == null ? null : NonConfig.clampInt(this.offspringMax, 1, 16);
      }

      public String birthEntity() {
         return NonConfig.normalizeEntityIdString(this.birthEntity);
      }

      public String birthMode() {
         return NonConfig.normalizeBirthMode(this.birthMode);
      }

      public NonConfig.GenderSpawnChances genderSpawn() {
         return NonConfig.sanitizeGenderSpawnChances(this.genderSpawn);
      }

      public NonConfig.EggProfile egg() {
         NonConfig.EggProfile safe = this.egg == null ? null : this.egg.sanitized();
         return safe != null && !safe.isEmpty() ? safe : null;
      }

      public Boolean lookAtNearbyAnimations() {
         return this.lookAtNearbyAnimations;
      }

      public Integer multiActorJoinChancePercent() {
         return this.multiActorJoinChancePercent == null ? null : NonConfig.clampPercent(this.multiActorJoinChancePercent);
      }

      public Float energyGainMultiplier() {
         return this.energyGainMultiplier == null ? null : NonConfig.clampFloat(this.energyGainMultiplier, 0.1F, 10.0F);
      }

      public NonConfig.EntityProfile sanitized() {
         Integer safeMin = this.offspringMin();
         Integer safeMax = this.offspringMax();
         if (safeMin != null && safeMax != null && safeMax < safeMin) {
            safeMax = safeMin;
         }

         return new NonConfig.EntityProfile(
            this.pregnancyChancePercent(),
            safeMin,
            safeMax,
            this.birthEntity(),
            this.birthMode(),
            this.genderSpawn(),
            this.egg(),
            this.lookAtNearbyAnimations(),
            this.multiActorJoinChancePercent(),
            this.energyGainMultiplier()
         );
      }

      public boolean isEmpty() {
         return this.pregnancyChancePercent() == null
            && this.offspringMin() == null
            && this.offspringMax() == null
            && this.birthEntity() == null
            && this.birthMode() == null
            && this.genderSpawn() == null
            && this.egg() == null
            && this.lookAtNearbyAnimations() == null
            && this.multiActorJoinChancePercent() == null
            && this.energyGainMultiplier() == null;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else if (!(obj instanceof NonConfig.EntityProfile other)) {
            return false;
         } else {
            NonConfig.EntityProfile a = this.sanitized();
            NonConfig.EntityProfile b = other.sanitized();
            return Objects.equals(a.pregnancyChancePercent, b.pregnancyChancePercent)
               && Objects.equals(a.offspringMin, b.offspringMin)
               && Objects.equals(a.offspringMax, b.offspringMax)
               && Objects.equals(a.birthEntity, b.birthEntity)
               && Objects.equals(a.birthMode, b.birthMode)
               && Objects.equals(a.genderSpawn, b.genderSpawn)
               && Objects.equals(a.egg, b.egg)
               && Objects.equals(a.lookAtNearbyAnimations, b.lookAtNearbyAnimations)
               && Objects.equals(a.multiActorJoinChancePercent, b.multiActorJoinChancePercent)
               && Objects.equals(a.energyGainMultiplier, b.energyGainMultiplier);
         }
      }

      @Override
      public int hashCode() {
         NonConfig.EntityProfile safe = this.sanitized();
         return Objects.hash(
            safe.pregnancyChancePercent,
            safe.offspringMin,
            safe.offspringMax,
            safe.birthEntity,
            safe.birthMode,
            safe.genderSpawn,
            safe.egg,
            safe.lookAtNearbyAnimations,
            safe.multiActorJoinChancePercent,
            safe.energyGainMultiplier
         );
      }
   }

   public static final class FemaleGenderDestroyedProfile {
      private float breastSize;
      private float separation;
      private float height;
      private float depth;
      private float rotation;
      private boolean breastPhysics;
      private boolean dualPhysics;
      private float intensity;
      private float momentum;

      public FemaleGenderDestroyedProfile() {
         this(0.8F, 0.0F, 0.5F, -0.5F, 0.0F, true, false, 0.25F, 0.625F);
      }

      public FemaleGenderDestroyedProfile(
         float breastSize,
         float separation,
         float height,
         float depth,
         float rotation,
         boolean breastPhysics,
         boolean dualPhysics,
         float intensity,
         float momentum
      ) {
         this.breastSize = NonConfig.clampFloat(breastSize, 0.0F, 1.6F);
         this.separation = NonConfig.clampFloat(separation, -1.0F, 1.0F);
         this.height = NonConfig.clampFloat(height, -2.0F, 1.0F);
         this.depth = NonConfig.clampFloat(depth, -1.0F, 0.0F);
         this.rotation = NonConfig.clampFloat(rotation, 0.0F, 0.1F);
         this.breastPhysics = breastPhysics;
         this.dualPhysics = dualPhysics;
         this.intensity = NonConfig.clampFloat(intensity, 0.0F, 0.5F);
         this.momentum = NonConfig.clampFloat(momentum, 0.25F, 1.0F);
      }

      public static NonConfig.FemaleGenderDestroyedProfile lowDefault() {
         return new NonConfig.FemaleGenderDestroyedProfile(0.8F, 0.0F, 0.5F, -0.5F, 0.0F, true, false, 0.25F, 0.625F);
      }

      public static NonConfig.FemaleGenderDestroyedProfile highDefault() {
         return new NonConfig.FemaleGenderDestroyedProfile(0.8F, -0.1F, 0.2F, 0.0F, 0.06F, true, true, 0.5F, 1.0F);
      }

      public NonConfig.FemaleGenderDestroyedProfile sanitized() {
         return new NonConfig.FemaleGenderDestroyedProfile(
            this.breastSize, this.separation, this.height, this.depth, this.rotation, this.breastPhysics, this.dualPhysics, this.intensity, this.momentum
         );
      }

      public float breastSize() {
         return NonConfig.clampFloat(this.breastSize, 0.0F, 1.6F);
      }

      public float separation() {
         return NonConfig.clampFloat(this.separation, -1.0F, 1.0F);
      }

      public float height() {
         return NonConfig.clampFloat(this.height, -2.0F, 1.0F);
      }

      public float depth() {
         return NonConfig.clampFloat(this.depth, -1.0F, 0.0F);
      }

      public float rotation() {
         return NonConfig.clampFloat(this.rotation, 0.0F, 0.1F);
      }

      public boolean breastPhysics() {
         return this.breastPhysics;
      }

      public boolean dualPhysics() {
         return this.dualPhysics;
      }

      public float intensity() {
         return NonConfig.clampFloat(this.intensity, 0.0F, 0.5F);
      }

      public float momentum() {
         return NonConfig.clampFloat(this.momentum, 0.25F, 1.0F);
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else {
            return !(obj instanceof NonConfig.FemaleGenderDestroyedProfile other)
               ? false
               : Float.compare(this.breastSize(), other.breastSize()) == 0
                  && Float.compare(this.separation(), other.separation()) == 0
                  && Float.compare(this.height(), other.height()) == 0
                  && Float.compare(this.depth(), other.depth()) == 0
                  && Float.compare(this.rotation(), other.rotation()) == 0
                  && this.breastPhysics() == other.breastPhysics()
                  && this.dualPhysics() == other.dualPhysics()
                  && Float.compare(this.intensity(), other.intensity()) == 0
                  && Float.compare(this.momentum(), other.momentum()) == 0;
         }
      }

      @Override
      public int hashCode() {
         int result = Float.hashCode(this.breastSize());
         result = 31 * result + Float.hashCode(this.separation());
         result = 31 * result + Float.hashCode(this.height());
         result = 31 * result + Float.hashCode(this.depth());
         result = 31 * result + Float.hashCode(this.rotation());
         result = 31 * result + Boolean.hashCode(this.breastPhysics());
         result = 31 * result + Boolean.hashCode(this.dualPhysics());
         result = 31 * result + Float.hashCode(this.intensity());
         return 31 * result + Float.hashCode(this.momentum());
      }
   }

   public static final class GenderSpawnChances {
      private int male;
      private int female;
      private int both;

      public GenderSpawnChances() {
         this(48, 47, 5);
      }

      public GenderSpawnChances(int male, int female, int both) {
         this.male = NonConfig.clampPercent(male);
         this.female = NonConfig.clampPercent(female);
         this.both = NonConfig.clampPercent(both);
      }

      public int male() {
         return NonConfig.clampPercent(this.male);
      }

      public int female() {
         return NonConfig.clampPercent(this.female);
      }

      public int both() {
         return NonConfig.clampPercent(this.both);
      }

      public boolean isValid() {
         return this.male() + this.female() + this.both() == 100;
      }

      public NonConfig.GenderSpawnChances sanitized() {
         return new NonConfig.GenderSpawnChances(this.male(), this.female(), this.both());
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else {
            return !(obj instanceof NonConfig.GenderSpawnChances other)
               ? false
               : this.male() == other.male() && this.female() == other.female() && this.both() == other.both();
         }
      }

      @Override
      public int hashCode() {
         int result = this.male();
         result = 31 * result + this.female();
         return 31 * result + this.both();
      }
   }

   public static final class OffspringCountRange {
      private int min;
      private int max;

      public OffspringCountRange() {
         this(1, 1);
      }

      public OffspringCountRange(int min, int max) {
         int safeMin = NonConfig.clampInt(min, 1, 16);
         int safeMax = NonConfig.clampInt(max, 1, 16);
         if (safeMax < safeMin) {
            safeMax = safeMin;
         }

         this.min = safeMin;
         this.max = safeMax;
      }

      public int min() {
         return NonConfig.clampInt(this.min, 1, 16);
      }

      public int max() {
         return Math.max(this.min(), NonConfig.clampInt(this.max, 1, 16));
      }

      public NonConfig.OffspringCountRange sanitized() {
         return new NonConfig.OffspringCountRange(this.min(), this.max());
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else {
            return !(obj instanceof NonConfig.OffspringCountRange other) ? false : this.min() == other.min() && this.max() == other.max();
         }
      }

      @Override
      public int hashCode() {
         int result = this.min();
         return 31 * result + this.max();
      }
   }

   public enum PlayerGenderSelection {
      MALE("male", 1),
      FEMALE("female", 2),
      BOTH("both", 3);

      private final String id;
      private final int mask;

      PlayerGenderSelection(String id, int mask) {
         this.id = id;
         this.mask = mask;
      }

      public String id() {
         return this.id;
      }

      public int mask() {
         return this.mask;
      }

      public NonConfig.PlayerGenderSelection next() {
         return switch (this) {
            case MALE -> FEMALE;
            case FEMALE -> BOTH;
            case BOTH -> MALE;
         };
      }

      public static NonConfig.PlayerGenderSelection fromMask(int mask, NonConfig.PlayerGenderSelection fallback) {
         int normalized = mask & 3;

         for (NonConfig.PlayerGenderSelection value : values()) {
            if (value.mask == normalized) {
               return value;
            }
         }

         return fallback == null ? FEMALE : fallback;
      }

      public static NonConfig.PlayerGenderSelection fromId(String id, NonConfig.PlayerGenderSelection fallback) {
         if (id != null) {
            String normalized = id.trim().toLowerCase(Locale.ROOT);

            for (NonConfig.PlayerGenderSelection value : values()) {
               if (value.id.equals(normalized)) {
                  return value;
               }
            }
         }

         return fallback == null ? FEMALE : fallback;
      }
   }
}
