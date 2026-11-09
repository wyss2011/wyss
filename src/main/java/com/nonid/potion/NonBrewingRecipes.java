package com.nonid.potion;

import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder.BuildCallback;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class NonBrewingRecipes {
   private static final TagKey<Item> EGGS_TAG = TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "eggs"));

   private NonBrewingRecipes() {
   }

   public static void registerAll() {
      FabricBrewingRecipeRegistryBuilder.BUILD.register((BuildCallback)builder -> {
         RegistryEntry<Potion> liquid = Registries.POTION.getEntry(NonPotions.LIQUID);
         RegistryEntry<Potion> fertileNectar = Registries.POTION.getEntry(NonPotions.FERTILE_NECTAR);
         RegistryEntry<Potion> energy = Registries.POTION.getEntry(NonPotions.ENERGY);
         RegistryEntry<Potion> energyStrong = Registries.POTION.getEntry(NonPotions.ENERGY_II);
         RegistryEntry<Potion> energyLong = Registries.POTION.getEntry(NonPotions.ENERGY_LONG);
         RegistryEntry<Potion> relief = Registries.POTION.getEntry(NonPotions.ENERGY_RELIEF);
         RegistryEntry<Potion> reliefStrong = Registries.POTION.getEntry(NonPotions.ENERGY_RELIEF_II);
         RegistryEntry<Potion> reliefLong = Registries.POTION.getEntry(NonPotions.ENERGY_RELIEF_LONG);
         RegistryEntry<Potion> fertility = Registries.POTION.getEntry(NonPotions.FERTILITY_INCREASER);
         RegistryEntry<Potion> fertilityStrong = Registries.POTION.getEntry(NonPotions.FERTILITY_INCREASER_II);
         RegistryEntry<Potion> fertilityLong = Registries.POTION.getEntry(NonPotions.FERTILITY_INCREASER_LONG);
         builder.registerPotionRecipe(liquid, Items.CRIMSON_FUNGUS, energy);
         builder.registerPotionRecipe(liquid, Items.WARPED_FUNGUS, relief);
         builder.registerPotionRecipe(energy, Items.GLOWSTONE_DUST, energyStrong);
         builder.registerPotionRecipe(energy, Items.REDSTONE, energyLong);
         builder.registerPotionRecipe(relief, Items.GLOWSTONE_DUST, reliefStrong);
         builder.registerPotionRecipe(relief, Items.REDSTONE, reliefLong);
         Set<Item> eggItems = new LinkedHashSet<>();
         Registries.ITEM.iterateEntries(EGGS_TAG).forEach(entry -> eggItems.add((Item)entry.value()));
         if (eggItems.isEmpty()) {
            eggItems.add(Items.EGG);
         }

         for (Item egg : eggItems) {
            builder.registerPotionRecipe(fertileNectar, egg, fertility);
         }

         builder.registerPotionRecipe(fertility, Items.GLOWSTONE_DUST, fertilityStrong);
         builder.registerPotionRecipe(fertility, Items.REDSTONE, fertilityLong);
      });
   }
}
