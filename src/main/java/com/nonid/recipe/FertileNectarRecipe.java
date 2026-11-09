package com.nonid.recipe;

import com.nonid.potion.NonPotions;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.component.type.FoodComponent.Builder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.IngredientPlacement;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.display.SlotDisplay.ItemSlotDisplay;
import net.minecraft.recipe.display.SlotDisplay.StackSlotDisplay;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

public final class FertileNectarRecipe implements CraftingRecipe {
   private static final FoodComponent FERTILE_NECTAR_FOOD = new Builder().nutrition(7).saturationModifier(0.0F).alwaysEdible().build();
   private final CraftingRecipeCategory category;
   private IngredientPlacement ingredientPlacement;

   public FertileNectarRecipe(CraftingRecipeCategory category) {
      this.category = category;
   }

   public boolean matches(CraftingRecipeInput input, World world) {
      int liquid = 0;
      int honeyBottle = 0;
      int nonEmpty = 0;

      for (int i = 0; i < input.size(); i++) {
         ItemStack stack = input.getStackInSlot(i);
         if (!stack.isEmpty()) {
            nonEmpty++;
            if (NonPotions.isLiquidBottle(stack)) {
               liquid++;
            } else {
               if (!stack.isOf(Items.HONEY_BOTTLE)) {
                  return false;
               }

               honeyBottle++;
            }
         }
      }

      return nonEmpty == 2 && liquid == 1 && honeyBottle == 1;
   }

   public ItemStack craft(CraftingRecipeInput input, WrapperLookup registries) {
      return createPotionStack(NonPotions.FERTILE_NECTAR);
   }

   public CraftingRecipeCategory getCategory() {
      return this.category;
   }

   public IngredientPlacement getIngredientPlacement() {
      if (this.ingredientPlacement == null) {
         this.ingredientPlacement = IngredientPlacement.forShapeless(List.of(NonRecipeIngredients.liquidBottle(), Ingredient.ofItem(Items.HONEY_BOTTLE)));
      }

      return this.ingredientPlacement;
   }

   public List<RecipeDisplay> getDisplays() {
      SlotDisplay liquidDisplay = new StackSlotDisplay(createPotionStack(NonPotions.LIQUID));
      SlotDisplay honeyDisplay = Ingredient.ofItem(Items.HONEY_BOTTLE).toDisplay();
      SlotDisplay resultDisplay = new StackSlotDisplay(createPotionStack(NonPotions.FERTILE_NECTAR));
      return List.of(new ShapelessCraftingRecipeDisplay(List.of(liquidDisplay, honeyDisplay), resultDisplay, new ItemSlotDisplay(Items.CRAFTING_TABLE)));
   }

   public DefaultedList<ItemStack> getRecipeRemainders(CraftingRecipeInput input) {
      DefaultedList<ItemStack> remainders = DefaultedList.ofSize(input.size(), ItemStack.EMPTY);

      for (int i = 0; i < input.size(); i++) {
         ItemStack stack = input.getStackInSlot(i);
         if (!stack.isEmpty() && !NonPotions.isLiquidBottle(stack)) {
            remainders.set(i, stack.getItem().getRecipeRemainder());
         }
      }

      return remainders;
   }

   public RecipeSerializer<? extends CraftingRecipe> getSerializer() {
      return NonRecipeSerializers.FERTILE_NECTAR_RECIPE;
   }

   private static ItemStack createPotionStack(Potion potion) {
      RegistryEntry<Potion> potionEntry = Registries.POTION.getEntry(potion);
      ItemStack stack = new ItemStack(Items.POTION);
      stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(potionEntry));
      if (potion == NonPotions.FERTILE_NECTAR) {
         stack.set(DataComponentTypes.FOOD, FERTILE_NECTAR_FOOD);
         stack.set(DataComponentTypes.MAX_STACK_SIZE, 16);
         NonPotions.applyHoneyBottleDrinkSound(stack);
      }

      return stack;
   }
}
