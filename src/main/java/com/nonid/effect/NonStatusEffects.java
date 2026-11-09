package com.nonid.effect;

import com.nonid.NeedsOfNature;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class NonStatusEffects {
   public static final EnergyStatusEffect ENERGY = register("energy", new EnergyStatusEffect());
   public static final EnergyReliefStatusEffect ENERGY_RELIEF = register("energy_relief", new EnergyReliefStatusEffect());
   public static final StatusEffect FERTILITY_INCREASER = register("fertility_increaser", new FertilityIncreaserStatusEffect());
   public static final StatusEffect FILLED = register("filled", new FilledStatusEffect());
   public static final StatusEffect ENERGIZED = register("energized", new EnergizedStatusEffect());
   public static final StatusEffect PREGNANT = register("pregnant", new PregnantStatusEffect());

   private NonStatusEffects() {
   }

   public static void registerAll() {
   }

   public static void clearRuntimeState() {
      ENERGY.clearRuntimeState();
      ENERGY_RELIEF.clearRuntimeState();
   }

   private static <T extends StatusEffect> T register(String path, T effect) {
      return (T)Registry.register(Registries.STATUS_EFFECT, NeedsOfNature.id(path), effect);
   }
}
