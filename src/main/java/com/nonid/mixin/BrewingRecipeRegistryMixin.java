package com.nonid.mixin;

import com.nonid.potion.NonPotions;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.BrewingRecipeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BrewingRecipeRegistry.class)
public abstract class BrewingRecipeRegistryMixin {
   @Inject(method = "hasItemRecipe", at = @At("HEAD"), cancellable = true)
   private void non$blockSplashConversionsForLiquidBottles(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
      if (NonPotions.isLiquidBottle(input)) {
         if (ingredient != null && !ingredient.isEmpty()) {
            if (ingredient.isOf(Items.GUNPOWDER) || ingredient.isOf(Items.DRAGON_BREATH)) {
               cir.setReturnValue(false);
            }
         }
      }
   }
}
