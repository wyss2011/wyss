package com.nonid.recipe;

import com.nonid.NeedsOfNature;
import com.nonid.potion.NonPotions;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
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

abstract class AbstractEnergyAdjustRecipe implements CraftingRecipe {
   private final CraftingRecipeCategory category;
   private IngredientPlacement ingredientPlacement;

   protected AbstractEnergyAdjustRecipe(CraftingRecipeCategory category) {
      this.category = category;
   }

   public boolean matches(CraftingRecipeInput input, World world) {
      int liquid = 0;
      int honeycomb = 0;
      int sugar = 0;
      int flowerMix = 0;
      int fungus = 0;
      int nonEmpty = 0;

      for (int i = 0; i < input.size(); i++) {
         ItemStack stack = input.getStackInSlot(i);
         if (!stack.isEmpty()) {
            nonEmpty++;
            if (NonPotions.isLiquidBottle(stack)) {
               liquid++;
            } else if (stack.isOf(Items.HONEYCOMB)) {
               honeycomb++;
            } else if (stack.isOf(Items.SUGAR)) {
               sugar++;
            } else if (stack.isOf(NeedsOfNature.FLOWER_MIX)) {
               flowerMix++;
            } else {
               if (!this.isRequiredFungus(stack)) {
                  return false;
               }

               fungus++;
            }
         }
      }

      return nonEmpty == 5 && liquid == 1 && honeycomb == 1 && sugar == 1 && flowerMix == 1 && fungus == 1;
   }

   public ItemStack craft(CraftingRecipeInput input, WrapperLookup registries) {
      return this.getResultStack().copy();
   }

   public CraftingRecipeCategory getCategory() {
      return this.category;
   }

   public IngredientPlacement getIngredientPlacement() {
      if (this.ingredientPlacement == null) {
         this.ingredientPlacement = IngredientPlacement.forShapeless(
            List.of(
               NonRecipeIngredients.liquidBottle(),
               Ingredient.ofItem(Items.HONEYCOMB),
               Ingredient.ofItem(Items.SUGAR),
               Ingredient.ofItem(NeedsOfNature.FLOWER_MIX),
               Ingredient.ofItem(this.getRequiredFungusItem())
            )
         );
      }

      return this.ingredientPlacement;
   }

   public List<RecipeDisplay> getDisplays() {
      SlotDisplay liquidDisplay = new StackSlotDisplay(createLiquidDisplayStack());
      SlotDisplay honeycombDisplay = Ingredient.ofItem(Items.HONEYCOMB).toDisplay();
      SlotDisplay sugarDisplay = Ingredient.ofItem(Items.SUGAR).toDisplay();
      SlotDisplay flowerMixDisplay = Ingredient.ofItem(NeedsOfNature.FLOWER_MIX).toDisplay();
      SlotDisplay fungusDisplay = Ingredient.ofItem(this.getRequiredFungusItem()).toDisplay();
      return List.of(
         new ShapelessCraftingRecipeDisplay(
            List.of(liquidDisplay, honeycombDisplay, sugarDisplay, flowerMixDisplay, fungusDisplay),
            new StackSlotDisplay(this.getResultStack()),
            new ItemSlotDisplay(Items.CRAFTING_TABLE)
         )
      );
   }

   public DefaultedList<ItemStack> getRecipeRemainders(CraftingRecipeInput input) {
      DefaultedList<ItemStack> remainders = DefaultedList.ofSize(input.size(), ItemStack.EMPTY);

      for (int i = 0; i < input.size(); i++) {
         ItemStack stack = input.getStackInSlot(i);
         if (!stack.isEmpty()) {
            if (NonPotions.isLiquidBottle(stack)) {
               remainders.set(i, new ItemStack(Items.GLASS_BOTTLE));
            } else {
               remainders.set(i, stack.getItem().getRecipeRemainder());
            }
         }
      }

      return remainders;
   }

   private static ItemStack createLiquidDisplayStack() {
      RegistryEntry<Potion> liquidPotion = Registries.POTION.getEntry(NonPotions.LIQUID);
      ItemStack stack = new ItemStack(Items.POTION);
      stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(liquidPotion));
      return stack;
   }

   protected abstract Item getRequiredFungusItem();

   protected abstract boolean isRequiredFungus(ItemStack var1);

   protected abstract ItemStack getResultStack();

   public abstract RecipeSerializer<? extends AbstractEnergyAdjustRecipe> getSerializer();
}
