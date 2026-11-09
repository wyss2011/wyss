package com.nonid.recipe;

import com.nonid.NeedsOfNature;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;

public final class EnergyDiminisherRecipe extends AbstractEnergyAdjustRecipe {
   public EnergyDiminisherRecipe(CraftingRecipeCategory category) {
      super(category);
   }

   @Override
   protected boolean isRequiredFungus(ItemStack stack) {
      return stack.isOf(Items.WARPED_FUNGUS);
   }

   @Override
   protected Item getRequiredFungusItem() {
      return Items.WARPED_FUNGUS;
   }

   @Override
   protected ItemStack getResultStack() {
      return new ItemStack(NeedsOfNature.ENERGY_DIMINISHER);
   }

   @Override
   public RecipeSerializer<? extends AbstractEnergyAdjustRecipe> getSerializer() {
      return NonRecipeSerializers.ENERGY_DIMINISHER_RECIPE;
   }
}
