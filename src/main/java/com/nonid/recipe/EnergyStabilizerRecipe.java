package com.nonid.recipe;

import com.nonid.NeedsOfNature;
import com.nonid.item.EnergyStabilizerItem;
import com.nonid.potion.NonPotions;
import java.util.List;
import java.util.Optional;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.IngredientPlacement;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.display.SlotDisplay.ItemSlotDisplay;
import net.minecraft.recipe.display.SlotDisplay.StackSlotDisplay;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class EnergyStabilizerRecipe implements CraftingRecipe {
   private final CraftingRecipeCategory category;
   private IngredientPlacement ingredientPlacement;

   public EnergyStabilizerRecipe(CraftingRecipeCategory category) {
      this.category = category;
   }

   public boolean matches(CraftingRecipeInput input, World world) {
      return findTargetEntity(input) != null;
   }

   public ItemStack craft(CraftingRecipeInput input, WrapperLookup registries) {
      Identifier target = findTargetEntity(input);
      return target == null ? ItemStack.EMPTY : EnergyStabilizerItem.createStackForEntity(target);
   }

   public CraftingRecipeCategory getCategory() {
      return this.category;
   }

   public RecipeSerializer<? extends CraftingRecipe> getSerializer() {
      return NonRecipeSerializers.ENERGY_STABILIZER_RECIPE;
   }

   public IngredientPlacement getIngredientPlacement() {
      if (this.ingredientPlacement == null) {
         Ingredient liquidBottle = NonRecipeIngredients.liquidBottle();
         this.ingredientPlacement = IngredientPlacement.forMultipleSlots(
            List.of(
               Optional.of(liquidBottle),
               Optional.of(Ingredient.ofItem(Items.WARPED_FUNGUS)),
               Optional.of(liquidBottle),
               Optional.of(Ingredient.ofItem(NeedsOfNature.ENERGY_DIMINISHER)),
               Optional.of(Ingredient.ofItem(Items.GLOW_BERRIES)),
               Optional.of(Ingredient.ofItem(NeedsOfNature.ENERGY_DIMINISHER)),
               Optional.of(liquidBottle),
               Optional.of(Ingredient.ofItem(Items.WARPED_FUNGUS)),
               Optional.of(liquidBottle)
            )
         );
      }

      return this.ingredientPlacement;
   }

   public List<RecipeDisplay> getDisplays() {
      SlotDisplay liquidDisplay = new StackSlotDisplay(createLiquidDisplayStack());
      SlotDisplay fungusDisplay = Ingredient.ofItem(Items.WARPED_FUNGUS).toDisplay();
      SlotDisplay diminisherDisplay = Ingredient.ofItem(NeedsOfNature.ENERGY_DIMINISHER).toDisplay();
      SlotDisplay glowBerryDisplay = Ingredient.ofItem(Items.GLOW_BERRIES).toDisplay();
      SlotDisplay resultDisplay = new StackSlotDisplay(EnergyStabilizerItem.createStackForEntity(null));
      return List.of(
         new ShapedCraftingRecipeDisplay(
            3,
            3,
            List.of(
               liquidDisplay, fungusDisplay, liquidDisplay, diminisherDisplay, glowBerryDisplay, diminisherDisplay, liquidDisplay, fungusDisplay, liquidDisplay
            ),
            resultDisplay,
            new ItemSlotDisplay(Items.CRAFTING_TABLE)
         )
      );
   }

   public DefaultedList<ItemStack> getRecipeRemainders(CraftingRecipeInput input) {
      DefaultedList<ItemStack> remainders = DefaultedList.ofSize(input.size(), ItemStack.EMPTY);

      for (int i = 0; i < input.size(); i++) {
         ItemStack stack = input.getStackInSlot(i);
         if (!stack.isEmpty()) {
            if (isCorner(i, input.getWidth()) && NonPotions.isLiquidBottle(stack)) {
               remainders.set(i, new ItemStack(Items.GLASS_BOTTLE));
            } else {
               remainders.set(i, stack.getItem().getRecipeRemainder());
            }
         }
      }

      return remainders;
   }

   @Nullable
   private static Identifier findTargetEntity(CraftingRecipeInput input) {
      if (input.getWidth() == 3 && input.getHeight() == 3) {
         int[] corners = new int[]{0, 2, 6, 8};
         Identifier entityTypeId = null;

         for (int corner : corners) {
            ItemStack liquidStack = input.getStackInSlot(corner);
            Identifier current = NonPotions.getLiquidBottleEntityTypeId(liquidStack);
            if (current == null) {
               return null;
            }

            if (entityTypeId == null) {
               entityTypeId = current;
            } else if (!entityTypeId.equals(current)) {
               return null;
            }
         }

         if (!input.getStackInSlot(1).isOf(Items.WARPED_FUNGUS)) {
            return null;
         } else if (!input.getStackInSlot(7).isOf(Items.WARPED_FUNGUS)) {
            return null;
         } else if (!input.getStackInSlot(3).isOf(NeedsOfNature.ENERGY_DIMINISHER)) {
            return null;
         } else if (!input.getStackInSlot(5).isOf(NeedsOfNature.ENERGY_DIMINISHER)) {
            return null;
         } else {
            return !input.getStackInSlot(4).isOf(Items.GLOW_BERRIES) ? null : entityTypeId;
         }
      } else {
         return null;
      }
   }

   private static boolean isCorner(int slot, int width) {
      return width != 3 ? false : slot == 0 || slot == 2 || slot == 6 || slot == 8;
   }

   private static ItemStack createLiquidDisplayStack() {
      RegistryEntry<Potion> liquidPotion = Registries.POTION.getEntry(NonPotions.LIQUID);
      ItemStack stack = new ItemStack(Items.POTION);
      stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(liquidPotion));
      return stack;
   }
}
