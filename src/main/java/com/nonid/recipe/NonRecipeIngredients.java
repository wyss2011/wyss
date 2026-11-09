package com.nonid.recipe;

import com.mojang.serialization.MapCodec;
import com.nonid.NeedsOfNature;
import com.nonid.potion.NonPotions;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.potion.Potion;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.display.SlotDisplay.StackSlotDisplay;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public final class NonRecipeIngredients {
   private NonRecipeIngredients() {
   }

   public static void registerAll() {
      CustomIngredientSerializer.register(NonRecipeIngredients.LiquidBottleIngredient.SERIALIZER);
   }

   public static Ingredient liquidBottle() {
      return NonRecipeIngredients.LiquidBottleIngredient.INSTANCE.toVanilla();
   }

   private enum LiquidBottleIngredient implements CustomIngredient {
      INSTANCE;

      private static final NonRecipeIngredients.LiquidBottleIngredient.Serializer SERIALIZER = new NonRecipeIngredients.LiquidBottleIngredient.Serializer();

      public boolean test(ItemStack stack) {
         return NonPotions.isLiquidBottle(stack);
      }

      public Stream<RegistryEntry<Item>> getMatchingItems() {
         return Stream.of(Registries.ITEM.getEntry(Items.POTION));
      }

      public boolean requiresTesting() {
         return true;
      }

      public CustomIngredientSerializer<?> getSerializer() {
         return SERIALIZER;
      }

      public SlotDisplay toDisplay() {
         RegistryEntry<Potion> liquidPotion = Registries.POTION.getEntry(NonPotions.LIQUID);
         ItemStack stack = new ItemStack(Items.POTION);
         stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(liquidPotion));
         return new StackSlotDisplay(stack);
      }

      private static final class Serializer implements CustomIngredientSerializer<NonRecipeIngredients.LiquidBottleIngredient> {
         private static final Identifier ID = NeedsOfNature.id("liquid_bottle");
         private static final MapCodec<NonRecipeIngredients.LiquidBottleIngredient> CODEC = MapCodec.unit(NonRecipeIngredients.LiquidBottleIngredient.INSTANCE);
         private static final PacketCodec<RegistryByteBuf, NonRecipeIngredients.LiquidBottleIngredient> PACKET_CODEC = PacketCodec.unit(
            NonRecipeIngredients.LiquidBottleIngredient.INSTANCE
         );

         public Identifier getIdentifier() {
            return ID;
         }

         public MapCodec<NonRecipeIngredients.LiquidBottleIngredient> getCodec() {
            return CODEC;
         }

         public PacketCodec<RegistryByteBuf, NonRecipeIngredients.LiquidBottleIngredient> getPacketCodec() {
            return PACKET_CODEC;
         }
      }
   }
}
