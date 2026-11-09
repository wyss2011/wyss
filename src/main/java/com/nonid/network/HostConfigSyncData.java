package com.nonid.network;

import com.nonid.NonConfig;
import com.nonid.data.NonPeakStages;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HostConfigSyncData {
   public int initialEnergyMax;
   public double energyGainRate;
   public double nearAnimationEnergyGainMult;
   public double playerEnergyAuraMultLow;
   public double playerEnergyAuraMultMid;
   public double playerEnergyAuraMultHigh;
   public double destroyedSkinAuraMultStage1;
   public double destroyedSkinAuraMultStage2;
   public double destroyedSkinAuraMultStage3;
   public double destroyedSkinAuraMultStage4;
   public int normalDamageDestroyedSkinChancePercent = 10;
   public boolean lastDefeatedEnabled = true;
   public int lastDefeatedEnergyThreshold = 70;
   public int lastDefeatedSearchRadius = 6;
   public int lastDefeatedCooldownSeconds = 120;
   public int playerEnergyAuraRadiusMin;
   public int playerEnergyAuraRadiusMax;
   public int playerEnergyAuraPulseTicks;
   public int scanBudgetPerTick;
   public int animationBlockSearchRadius = 3;
   public int loopProgressSeconds;
   public int peakLoopProgressSeconds;
   public int genderMaleChancePercent;
   public int genderFemaleChancePercent;
   public int genderBothChancePercent;
   public int maleMaleChancePercent;
   public int femaleFemaleChancePercent;
   public int entityEntityChancePercent;
   public int multiActorJoinChancePercent;
   public boolean allowPlayerGenderChangeAnytime = true;
   public boolean requirePlayerGenderSelectionOnJoin = false;
   public int allowedStartingGenderMask = 7;
   public int allowedPlayerInteractionProfilesMask = 15;
   public int playerRequestMinEnergy = 70;
   public boolean pregnancyEnabled;
   public boolean liquidTankEnabled;
   public boolean messSystemEnabled = true;
   public boolean destroyedSkinSystemEnabled = true;
   public boolean allowCraftingTableSkinRepair = false;
   public boolean keepMessAfterDeath = false;
   public boolean keepRippedSkinAfterDeath = false;
   public boolean keepLiquidTankAfterDeath = false;
   public boolean keepPregnancyAfterDeath = false;
   public int liquidTankCapacityMl = 200;
   public boolean vanillaOverridesEnabled;
   public boolean useAnimationBreeding;
   public boolean requireMaleFemaleForBreeding;
   public boolean femaleCowOnlyMilking;
   public boolean attackCreativePlayers;
   public boolean convertMaleOnlyVInjectionsToA = true;
   public int attackEscapeHits;
   public double attackDecayPerSecond;
   public int attackEscapeDamageDifficultyPercent = 100;
   public int pvpFullHealthEscapeBonusPercent = 30;
   public int pvpCriticalHealthMashStrengthPercent = 25;
   public int pvpMaxHeatEscapeStrengthPercent = 70;
   public int pvpZeroHeatAttackStrengthPercent = 70;
   public int attackEscapeInvulnerabilitySeconds = 3;
   public int attackEscapeAnimationProtectionSeconds = 3;
   public int postEscapeGatherMaxMobs = 8;
   public boolean blockAnimationsWhileRidingLivingEntities = true;
   public boolean blockAnimationsWhileRidingVehicles = false;
   public int attackOutcomeFailsafeThreshold = 3;
   public int pregnancyChancePercent;
   public int pregnancyDurationMinutes;
   public boolean liquidBasedPregnancy = false;
   public int pregnancyRiskyStartDay = 13;
   public int pregnancyOvulationEndDay = 17;
   public int pregnancySafeDayChancePercent = 10;
   public int pregnancyRiskyChancePercent = 300;
   public int pregnancyOvulationChancePercent = 500;
   public Map<String, Integer> pregnancyChanceByEntity = new LinkedHashMap<>();
   public Map<String, NonConfig.OffspringCountRange> pregnancyOffspringCountByEntity = new LinkedHashMap<>();
   public int pregnancyEggDefaultHatchSeconds;
   public Map<String, Boolean> pregnancyEggEnabledByEntity = new LinkedHashMap<>();
   public Map<String, Integer> pregnancyEggHatchSecondsByEntity = new LinkedHashMap<>();
   public boolean pregnancyAutoTameMobs = true;
   public boolean pregnancyFriendlyMobsIgnorePlayers = false;
   public Map<String, NonConfig.EntityProfile> entityProfilesByEntity = new LinkedHashMap<>();
   public double liquidDecayPerSecond;
   public double peakXpPerMl = 0.06666666666666667;
   public float filledStageOneSpeedMult;
   public float filledStageTwoSpeedMult;
   public float filledStageThreeSpeedMult;
   public float filledStageOneJumpMult;
   public float filledStageTwoJumpMult;
   public float filledStageThreeJumpMult;
   public Map<String, NonConfig.GenderSpawnChances> genderSpawnByEntity = new LinkedHashMap<>();
   public Map<String, Integer> liquidGainByEntity = new LinkedHashMap<>();
   public Map<String, String> liquidColorByEntity = new LinkedHashMap<>();
   public List<String> disabledAnimationPacks = List.of();
   public List<String> disabledAnimations = List.of();
   public Map<String, Integer> peakStageByAnimation = new LinkedHashMap<>();

   public static HostConfigSyncData fromConfig(NonConfig config) {
      HostConfigSyncData data = new HostConfigSyncData();
      if (config == null) {
         return data;
      }

      data.initialEnergyMax = config.getInitialEnergyMax();
      data.energyGainRate = config.getEnergyGainRate();
      data.nearAnimationEnergyGainMult = config.getNearAnimationEnergyGainMult();
      data.playerEnergyAuraMultLow = config.getPlayerEnergyAuraMultLow();
      data.playerEnergyAuraMultMid = config.getPlayerEnergyAuraMultMid();
      data.playerEnergyAuraMultHigh = config.getPlayerEnergyAuraMultHigh();
      data.destroyedSkinAuraMultStage1 = config.getDestroyedSkinAuraMultStage1();
      data.destroyedSkinAuraMultStage2 = config.getDestroyedSkinAuraMultStage2();
      data.destroyedSkinAuraMultStage3 = config.getDestroyedSkinAuraMultStage3();
      data.destroyedSkinAuraMultStage4 = config.getDestroyedSkinAuraMultStage4();
      data.normalDamageDestroyedSkinChancePercent = config.getNormalDamageDestroyedSkinChancePercent();
      data.lastDefeatedEnabled = config.isLastDefeatedEnabled();
      data.lastDefeatedEnergyThreshold = config.getLastDefeatedEnergyThreshold();
      data.lastDefeatedSearchRadius = config.getLastDefeatedSearchRadius();
      data.lastDefeatedCooldownSeconds = config.getLastDefeatedCooldownSeconds();
      data.playerEnergyAuraRadiusMin = config.getPlayerEnergyAuraRadiusMin();
      data.playerEnergyAuraRadiusMax = config.getPlayerEnergyAuraRadiusMax();
      data.playerEnergyAuraPulseTicks = config.getPlayerEnergyAuraPulseTicks();
      data.scanBudgetPerTick = config.getScanBudgetPerTick();
      data.animationBlockSearchRadius = config.blockSearchRadius();
      data.loopProgressSeconds = config.getLoopProgressSeconds();
      data.peakLoopProgressSeconds = config.getPeakLoopProgressSeconds();
      data.genderMaleChancePercent = config.getGenderMaleChancePercent();
      data.genderFemaleChancePercent = config.getGenderFemaleChancePercent();
      data.genderBothChancePercent = config.getGenderBothChancePercent();
      data.maleMaleChancePercent = config.getMaleMaleChancePercent();
      data.femaleFemaleChancePercent = config.getFemaleFemaleChancePercent();
      data.entityEntityChancePercent = config.getEntityEntityChancePercent();
      data.multiActorJoinChancePercent = config.getMultiActorJoinChancePercent();
      data.allowPlayerGenderChangeAnytime = config.allowPlayerGenderChangeAnytime();
      data.requirePlayerGenderSelectionOnJoin = config.requirePlayerGenderSelectionOnJoin();
      data.allowedStartingGenderMask = config.getAllowedStartingGenderMask();
      data.allowedPlayerInteractionProfilesMask = config.getAllowedPlayerInteractionProfilesMask();
      data.playerRequestMinEnergy = config.getPlayerRequestMinEnergy();
      data.pregnancyEnabled = config.isPregnancyEnabled();
      data.liquidTankEnabled = config.isLiquidTankEnabled();
      data.messSystemEnabled = config.isMessSystemEnabled();
      data.destroyedSkinSystemEnabled = config.isDestroyedSkinSystemEnabled();
      data.allowCraftingTableSkinRepair = config.isCraftingTableSkinRepairAllowed();
      data.keepMessAfterDeath = config.keepMessAfterDeath();
      data.keepRippedSkinAfterDeath = config.keepRippedSkinAfterDeath();
      data.keepLiquidTankAfterDeath = config.keepLiquidTankAfterDeath();
      data.keepPregnancyAfterDeath = config.keepPregnancyAfterDeath();
      data.liquidTankCapacityMl = config.getLiquidTankCapacityMl();
      data.vanillaOverridesEnabled = config.isVanillaOverridesEnabled();
      data.useAnimationBreeding = config.useAnimationBreeding();
      data.requireMaleFemaleForBreeding = config.requireMaleFemaleForBreeding();
      data.femaleCowOnlyMilking = config.femaleCowOnlyMilking();
      data.attackCreativePlayers = config.canAttackCreativePlayers();
      data.convertMaleOnlyVInjectionsToA = config.convertMaleOnlyVInjectionsToA();
      data.attackEscapeHits = config.getAttackEscapeHits();
      data.attackDecayPerSecond = config.getAttackDecayPerSecond();
      data.attackEscapeDamageDifficultyPercent = config.getAttackEscapeDamageDifficultyPercent();
      data.pvpFullHealthEscapeBonusPercent = config.getPvpFullHealthEscapeBonusPercent();
      data.pvpCriticalHealthMashStrengthPercent = config.getPvpCriticalHealthMashStrengthPercent();
      data.pvpMaxHeatEscapeStrengthPercent = config.getPvpMaxHeatEscapeStrengthPercent();
      data.pvpZeroHeatAttackStrengthPercent = config.getPvpZeroHeatAttackStrengthPercent();
      data.attackEscapeInvulnerabilitySeconds = config.getAttackEscapeInvulnerabilitySeconds();
      data.attackEscapeAnimationProtectionSeconds = config.getAttackEscapeAnimationProtectionSeconds();
      data.postEscapeGatherMaxMobs = config.getPostEscapeGatherMaxMobs();
      data.blockAnimationsWhileRidingLivingEntities = config.blockAnimationsWhileRidingLivingEntities();
      data.blockAnimationsWhileRidingVehicles = config.blockAnimationsWhileRidingVehicles();
      data.attackOutcomeFailsafeThreshold = config.getAttackOutcomeFailsafeThreshold();
      data.pregnancyChancePercent = config.getPregnancyChancePercent();
      data.pregnancyDurationMinutes = config.getPregnancyDurationMinutes();
      data.liquidBasedPregnancy = config.isLiquidBasedPregnancyEnabled();
      data.pregnancyRiskyStartDay = config.getPregnancyRiskyStartDay();
      data.pregnancyOvulationEndDay = config.getPregnancyOvulationEndDay();
      data.pregnancySafeDayChancePercent = config.getPregnancySafeDayChancePercent();
      data.pregnancyRiskyChancePercent = config.getPregnancyRiskyChancePercent();
      data.pregnancyOvulationChancePercent = config.getPregnancyOvulationChancePercent();
      data.pregnancyChanceByEntity = new LinkedHashMap<>(config.getPregnancyChanceByEntity());
      data.pregnancyOffspringCountByEntity = new LinkedHashMap<>(config.getPregnancyOffspringCountByEntity());
      data.pregnancyEggDefaultHatchSeconds = config.getPregnancyEggDefaultHatchSeconds();
      data.pregnancyEggEnabledByEntity = new LinkedHashMap<>(config.getPregnancyEggEnabledByEntity());
      data.pregnancyEggHatchSecondsByEntity = new LinkedHashMap<>(config.getPregnancyEggHatchSecondsByEntity());
      data.pregnancyAutoTameMobs = config.isPregnancyAutoTameMobs();
      data.pregnancyFriendlyMobsIgnorePlayers = config.pregnancyFriendlyMobsIgnorePlayers();
      data.entityProfilesByEntity = new LinkedHashMap<>(config.getEntityProfilesByEntity());
      data.liquidDecayPerSecond = config.getLiquidDecayPerSecond();
      data.peakXpPerMl = config.getPeakXpPerMl();
      data.filledStageOneSpeedMult = config.getFilledStageOneSpeedMult();
      data.filledStageTwoSpeedMult = config.getFilledStageTwoSpeedMult();
      data.filledStageThreeSpeedMult = config.getFilledStageThreeSpeedMult();
      data.filledStageOneJumpMult = config.getFilledStageOneJumpMult();
      data.filledStageTwoJumpMult = config.getFilledStageTwoJumpMult();
      data.filledStageThreeJumpMult = config.getFilledStageThreeJumpMult();
      data.genderSpawnByEntity = new LinkedHashMap<>(config.getGenderSpawnByEntity());
      data.liquidGainByEntity = new LinkedHashMap<>(config.getLiquidGainByEntity());
      data.liquidColorByEntity = new LinkedHashMap<>(config.getLiquidColorByEntity());
      data.disabledAnimationPacks = List.copyOf(config.getDisabledAnimationPacks());
      data.disabledAnimations = List.copyOf(config.getDisabledAnimations());
      data.peakStageByAnimation = new LinkedHashMap<>(NonPeakStages.snapshotPeakStagesForSync());
      return data;
   }

   public static HostConfigSyncData fromEditableConfig(NonConfig config) {
      HostConfigSyncData data = fromConfig(config);
      data.peakStageByAnimation = new LinkedHashMap<>();
      return data;
   }

   public void applyTo(NonConfig config) {
      this.applyEditableTo(config);
      NonPeakStages.applySyncedPeakStages(copyIntMap(this.peakStageByAnimation));
   }

   public void applyEditableTo(NonConfig config) {
      if (config != null) {
         config.setInitialEnergyMax(this.initialEnergyMax);
         config.setEnergyGainRate(this.energyGainRate);
         config.setNearAnimationEnergyGainMult(this.nearAnimationEnergyGainMult);
         config.setPlayerEnergyAuraMultLow(this.playerEnergyAuraMultLow);
         config.setPlayerEnergyAuraMultMid(this.playerEnergyAuraMultMid);
         config.setPlayerEnergyAuraMultHigh(this.playerEnergyAuraMultHigh);
         config.setDestroyedSkinAuraMultStage1(this.destroyedSkinAuraMultStage1);
         config.setDestroyedSkinAuraMultStage2(this.destroyedSkinAuraMultStage2);
         config.setDestroyedSkinAuraMultStage3(this.destroyedSkinAuraMultStage3);
         config.setDestroyedSkinAuraMultStage4(this.destroyedSkinAuraMultStage4);
         config.setNormalDamageDestroyedSkinChancePercent(this.normalDamageDestroyedSkinChancePercent);
         config.setLastDefeatedEnabled(this.lastDefeatedEnabled);
         config.setLastDefeatedEnergyThreshold(this.lastDefeatedEnergyThreshold);
         config.setLastDefeatedSearchRadius(this.lastDefeatedSearchRadius);
         config.setLastDefeatedCooldownSeconds(this.lastDefeatedCooldownSeconds);
         config.setPlayerEnergyAuraRadiusMin(this.playerEnergyAuraRadiusMin);
         config.setPlayerEnergyAuraRadiusMax(this.playerEnergyAuraRadiusMax);
         config.setPlayerEnergyAuraPulseTicks(this.playerEnergyAuraPulseTicks);
         config.setScanBudgetPerTick(this.scanBudgetPerTick);
         config.setBlockSearchRadius(this.animationBlockSearchRadius);
         config.setLoopProgressSeconds(this.loopProgressSeconds);
         config.setPeakLoopProgressSeconds(this.peakLoopProgressSeconds);
         config.setGenderMaleChancePercent(this.genderMaleChancePercent);
         config.setGenderFemaleChancePercent(this.genderFemaleChancePercent);
         config.setGenderBothChancePercent(this.genderBothChancePercent);
         config.setMaleMaleChancePercent(this.maleMaleChancePercent);
         config.setFemaleFemaleChancePercent(this.femaleFemaleChancePercent);
         config.setEntityEntityChancePercent(this.entityEntityChancePercent);
         config.setMultiActorJoinChancePercent(this.multiActorJoinChancePercent);
         config.setAllowPlayerGenderChangeAnytime(this.allowPlayerGenderChangeAnytime);
         config.setRequirePlayerGenderSelectionOnJoin(this.requirePlayerGenderSelectionOnJoin);
         config.setAllowedStartingGenderMask(this.allowedStartingGenderMask);
         config.setAllowedPlayerInteractionProfilesMask(this.allowedPlayerInteractionProfilesMask);
         config.setPlayerRequestMinEnergy(this.playerRequestMinEnergy);
         config.setPregnancyEnabled(this.pregnancyEnabled);
         config.setLiquidTankEnabled(this.liquidTankEnabled);
         config.setMessSystemEnabled(this.messSystemEnabled);
         config.setDestroyedSkinSystemEnabled(this.destroyedSkinSystemEnabled);
         config.setCraftingTableSkinRepairAllowed(this.allowCraftingTableSkinRepair);
         config.setKeepMessAfterDeath(this.keepMessAfterDeath);
         config.setKeepRippedSkinAfterDeath(this.keepRippedSkinAfterDeath);
         config.setKeepLiquidTankAfterDeath(this.keepLiquidTankAfterDeath);
         config.setKeepPregnancyAfterDeath(this.keepPregnancyAfterDeath);
         config.setLiquidTankCapacityMl(this.liquidTankCapacityMl);
         config.setVanillaOverridesEnabled(this.vanillaOverridesEnabled);
         config.setUseAnimationBreeding(this.useAnimationBreeding);
         config.setRequireMaleFemaleForBreeding(this.requireMaleFemaleForBreeding);
         config.setFemaleCowOnlyMilking(this.femaleCowOnlyMilking);
         config.setAttackCreativePlayers(this.attackCreativePlayers);
         config.setConvertMaleOnlyVInjectionsToA(this.convertMaleOnlyVInjectionsToA);
         config.setAttackEscapeHits(this.attackEscapeHits);
         config.setAttackDecayPerSecond(this.attackDecayPerSecond);
         config.setAttackEscapeDamageDifficultyPercent(this.attackEscapeDamageDifficultyPercent);
         config.setPvpFullHealthEscapeBonusPercent(this.pvpFullHealthEscapeBonusPercent);
         config.setPvpCriticalHealthMashStrengthPercent(this.pvpCriticalHealthMashStrengthPercent);
         config.setPvpMaxHeatEscapeStrengthPercent(this.pvpMaxHeatEscapeStrengthPercent);
         config.setPvpZeroHeatAttackStrengthPercent(this.pvpZeroHeatAttackStrengthPercent);
         config.setAttackEscapeInvulnerabilitySeconds(this.attackEscapeInvulnerabilitySeconds);
         config.setAttackEscapeAnimationProtectionSeconds(this.attackEscapeAnimationProtectionSeconds);
         config.setPostEscapeGatherMaxMobs(this.postEscapeGatherMaxMobs);
         config.setBlockAnimationsWhileRidingLivingEntities(this.blockAnimationsWhileRidingLivingEntities);
         config.setBlockAnimationsWhileRidingVehicles(this.blockAnimationsWhileRidingVehicles);
         config.setAttackOutcomeFailsafeThreshold(this.attackOutcomeFailsafeThreshold);
         config.setPregnancyChancePercent(this.pregnancyChancePercent);
         config.setPregnancyDurationMinutes(this.pregnancyDurationMinutes);
         config.setLiquidBasedPregnancyEnabled(this.liquidBasedPregnancy);
         config.setPregnancyRiskyStartDay(this.pregnancyRiskyStartDay);
         config.setPregnancyOvulationEndDay(this.pregnancyOvulationEndDay);
         config.setPregnancySafeDayChancePercent(this.pregnancySafeDayChancePercent);
         config.setPregnancyRiskyChancePercent(this.pregnancyRiskyChancePercent);
         config.setPregnancyOvulationChancePercent(this.pregnancyOvulationChancePercent);
         config.setPregnancyChanceByEntity(copyIntMap(this.pregnancyChanceByEntity));
         config.setPregnancyOffspringCountByEntity(copyOffspringCountMap(this.pregnancyOffspringCountByEntity));
         config.setPregnancyEggDefaultHatchSeconds(this.pregnancyEggDefaultHatchSeconds);
         config.setPregnancyEggEnabledByEntity(copyBoolMap(this.pregnancyEggEnabledByEntity));
         config.setPregnancyEggHatchSecondsByEntity(copyIntMap(this.pregnancyEggHatchSecondsByEntity));
         config.setPregnancyAutoTameMobs(this.pregnancyAutoTameMobs);
         config.setPregnancyFriendlyMobsIgnorePlayers(this.pregnancyFriendlyMobsIgnorePlayers);
         config.setEntityProfilesByEntity(copyEntityProfileMap(this.entityProfilesByEntity));
         config.setLiquidDecayPerSecond(this.liquidDecayPerSecond);
         config.setPeakXpPerMl(this.peakXpPerMl);
         config.setFilledStageOneSpeedMult(this.filledStageOneSpeedMult);
         config.setFilledStageTwoSpeedMult(this.filledStageTwoSpeedMult);
         config.setFilledStageThreeSpeedMult(this.filledStageThreeSpeedMult);
         config.setFilledStageOneJumpMult(this.filledStageOneJumpMult);
         config.setFilledStageTwoJumpMult(this.filledStageTwoJumpMult);
         config.setFilledStageThreeJumpMult(this.filledStageThreeJumpMult);
         config.setGenderSpawnByEntity(copyGenderSpawnMap(this.genderSpawnByEntity));
         config.setLiquidGainByEntity(copyIntMap(this.liquidGainByEntity));
         config.setLiquidColorByEntity(copyStringMap(this.liquidColorByEntity));
         config.setDisabledAnimationPacks(this.disabledAnimationPacks == null ? List.of() : this.disabledAnimationPacks);
         config.setDisabledAnimations(this.disabledAnimations == null ? List.of() : this.disabledAnimations);
      }
   }

   private static Map<String, NonConfig.GenderSpawnChances> copyGenderSpawnMap(Map<String, NonConfig.GenderSpawnChances> source) {
      return source != null && !source.isEmpty() ? new LinkedHashMap<>(source) : Map.of();
   }

   private static Map<String, NonConfig.EntityProfile> copyEntityProfileMap(Map<String, NonConfig.EntityProfile> source) {
      return source != null && !source.isEmpty() ? new LinkedHashMap<>(source) : Map.of();
   }

   private static Map<String, NonConfig.OffspringCountRange> copyOffspringCountMap(Map<String, NonConfig.OffspringCountRange> source) {
      return source != null && !source.isEmpty() ? new LinkedHashMap<>(source) : Map.of();
   }

   private static Map<String, Integer> copyIntMap(Map<String, Integer> source) {
      return source != null && !source.isEmpty() ? new LinkedHashMap<>(source) : Map.of();
   }

   private static Map<String, Boolean> copyBoolMap(Map<String, Boolean> source) {
      return source != null && !source.isEmpty() ? new LinkedHashMap<>(source) : Map.of();
   }

   private static Map<String, String> copyStringMap(Map<String, String> source) {
      return source != null && !source.isEmpty() ? new LinkedHashMap<>(source) : Map.of();
   }
}
