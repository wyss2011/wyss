package com.nonid.recipe;

import com.nonid.NeedsOfNature;
import com.nonid.data.NonAccessoryLiquidFilling;
import com.nonid.potion.NonPotions;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.IngredientPlacement;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay.ItemSlotDisplay;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

public final class FillAdvancedStraponRecipe implements CraftingRecipe {
   private static final Identifier ADVANCED_STRAPON_ID = NeedsOfNature.id("strapon_advanced");
   private final CraftingRecipeCategory category;
   private IngredientPlacement ingredientPlacement;

   public FillAdvancedStraponRecipe(CraftingRecipeCategory category) {
      this.category = category;
   }

   public boolean matches(CraftingRecipeInput input, World world) {
      ItemStack strapon = ItemStack.EMPTY;
      ItemStack liquidBottle = ItemStack.EMPTY;
      int nonEmpty = 0;

      for (int i = 0; i < input.size(); i++) {
         ItemStack stack = input.getStackInSlot(i);
         if (!stack.isEmpty()) {
            nonEmpty++;
            if (isAdvancedStrapon(stack)) {
               if (!strapon.isEmpty()) {
                  return false;
               }

               strapon = stack;
            } else {
               if (!NonPotions.isLiquidBottle(stack)) {
                  return false;
               }

               if (!liquidBottle.isEmpty()) {
                  return false;
               }

               liquidBottle = stack;
            }
         }
      }

      return nonEmpty == 2 && NonAccessoryLiquidFilling.canFill(strapon, liquidBottle);
   }

   public ItemStack craft(CraftingRecipeInput input, WrapperLookup registries) {
      ItemStack strapon = ItemStack.EMPTY;
      ItemStack liquidBottle = ItemStack.EMPTY;

      for (int i = 0; i < input.size(); i++) {
         ItemStack stack = input.getStackInSlot(i);
         if (isAdvancedStrapon(stack)) {
            strapon = stack;
         } else if (NonPotions.isLiquidBottle(stack)) {
            liquidBottle = stack;
         }
      }

      return NonAccessoryLiquidFilling.createFilledCopy(strapon, liquidBottle);
   }

   public CraftingRecipeCategory getCategory() {
      return this.category;
   }

   public IngredientPlacement getIngredientPlacement() {
      if (this.ingredientPlacement == null) {
         this.ingredientPlacement = IngredientPlacement.forShapeless(
            List.of(Ingredient.ofItem(resolveAdvancedStraponItem()), NonRecipeIngredients.liquidBottle())
         );
      }

      return this.ingredientPlacement;
   }

   public List<RecipeDisplay> getDisplays() {
      return List.of(
         new ShapelessCraftingRecipeDisplay(
            List.of(new ItemSlotDisplay(resolveAdvancedStraponItem()), NonRecipeIngredients.liquidBottle().toDisplay()),
            new ItemSlotDisplay(resolveAdvancedStraponItem()),
            new ItemSlotDisplay(Items.CRAFTING_TABLE)
         )
      );
   }

   public DefaultedList<ItemStack> getRecipeRemainders(CraftingRecipeInput input) {
      DefaultedList<ItemStack> remainders = DefaultedList.ofSize(input.size(), ItemStack.EMPTY);

      for (int i = 0; i < input.size(); i++) {
         ItemStack stack = input.getStackInSlot(i);
         if (NonPotions.isLiquidBottle(stack)) {
            remainders.set(i, new ItemStack(Items.GLASS_BOTTLE));
         }
      }

      return remainders;
   }

   public RecipeSerializer<? extends CraftingRecipe> getSerializer() {
      return NonRecipeSerializers.FILL_ADVANCED_STRAPON_RECIPE;
   }

   private static boolean isAdvancedStrapon(ItemStack stack) {
      return stack != null && !stack.isEmpty() && ADVANCED_STRAPON_ID.equals(Registries.ITEM.getId(stack.getItem()));
   }

   private static Item resolveAdvancedStraponItem() {
      return (Item)Registries.ITEM.get(ADVANCED_STRAPON_ID);
   }
}
