package com.nonid.recipe;

import com.nonid.NeedsOfNature;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class NonRecipeSerializers {
   public static final RecipeSerializer<EnergyAugmenterRecipe> ENERGY_AUGMENTER_RECIPE = (RecipeSerializer<EnergyAugmenterRecipe>)Registry.register(
      Registries.RECIPE_SERIALIZER, NeedsOfNature.id("crafting_energy_augmenter"), new SpecialRecipeSerializer(EnergyAugmenterRecipe::new)
   );
   public static final RecipeSerializer<EnergyDiminisherRecipe> ENERGY_DIMINISHER_RECIPE = (RecipeSerializer<EnergyDiminisherRecipe>)Registry.register(
      Registries.RECIPE_SERIALIZER, NeedsOfNature.id("crafting_energy_diminisher"), new SpecialRecipeSerializer(EnergyDiminisherRecipe::new)
   );
   public static final RecipeSerializer<EnergyStabilizerRecipe> ENERGY_STABILIZER_RECIPE = (RecipeSerializer<EnergyStabilizerRecipe>)Registry.register(
      Registries.RECIPE_SERIALIZER, NeedsOfNature.id("crafting_energy_stabilizer"), new SpecialRecipeSerializer(EnergyStabilizerRecipe::new)
   );
   public static final RecipeSerializer<FertileNectarRecipe> FERTILE_NECTAR_RECIPE = (RecipeSerializer<FertileNectarRecipe>)Registry.register(
      Registries.RECIPE_SERIALIZER, NeedsOfNature.id("crafting_fertile_nectar"), new SpecialRecipeSerializer(FertileNectarRecipe::new)
   );
   public static final RecipeSerializer<FillAdvancedStraponRecipe> FILL_ADVANCED_STRAPON_RECIPE = (RecipeSerializer<FillAdvancedStraponRecipe>)Registry.register(
      Registries.RECIPE_SERIALIZER, NeedsOfNature.id("crafting_fill_advanced_strapon"), new SpecialRecipeSerializer(FillAdvancedStraponRecipe::new)
   );

   private NonRecipeSerializers() {
   }

   public static void registerAll() {
      NonRecipeIngredients.registerAll();
      RecipeSynchronization.synchronizeRecipeSerializer(ENERGY_AUGMENTER_RECIPE);
      RecipeSynchronization.synchronizeRecipeSerializer(ENERGY_DIMINISHER_RECIPE);
      RecipeSynchronization.synchronizeRecipeSerializer(ENERGY_STABILIZER_RECIPE);
      RecipeSynchronization.synchronizeRecipeSerializer(FERTILE_NECTAR_RECIPE);
      RecipeSynchronization.synchronizeRecipeSerializer(FILL_ADVANCED_STRAPON_RECIPE);
   }
}
