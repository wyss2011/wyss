package com.nonid.data;

import com.nonid.potion.NonPotions;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonAccessoryLiquidFilling {
   private NonAccessoryLiquidFilling() {
   }

   public static boolean canFill(ItemStack accessory, ItemStack liquidBottle) {
      return resolveFill(accessory, liquidBottle) != null;
   }

   public static boolean fillInPlace(ItemStack accessory, ItemStack liquidBottle) {
      NonAccessoryLiquidFilling.FillData fill = resolveFill(accessory, liquidBottle);
      if (fill == null) {
         return false;
      }

      NonAccessoryStackData.setStoredLiquid(accessory, fill.entityTypeId(), fill.amountMl());
      return true;
   }

   public static ItemStack createFilledCopy(ItemStack accessory, ItemStack liquidBottle) {
      NonAccessoryLiquidFilling.FillData fill = resolveFill(accessory, liquidBottle);
      if (fill == null) {
         return ItemStack.EMPTY;
      }

      ItemStack result = accessory.copyWithCount(1);
      NonAccessoryStackData.setStoredLiquid(result, fill.entityTypeId(), fill.amountMl());
      return result;
   }

   @Nullable
   private static NonAccessoryLiquidFilling.FillData resolveFill(ItemStack accessory, ItemStack liquidBottle) {
      if (accessory == null || accessory.isEmpty() || liquidBottle == null || liquidBottle.isEmpty()) {
         return null;
      }

      if (NonAccessoryStackData.getStoredLiquid(accessory) != null) {
         return null;
      }

      Identifier accessoryId = Registries.ITEM.getId(accessory.getItem());
      NonAccessoryItemRegistry.AccessoryItemDefinition definition = NonAccessoryItemRegistry.getDefinition(accessoryId);
      if (definition == null) {
         return null;
      }

      int capacityMl = definition.actorVisualBehavior().storedLiquidCapacityMl();
      if (capacityMl <= 0) {
         return null;
      }

      Identifier entityTypeId = NonPotions.getLiquidBottleEntityTypeId(liquidBottle);
      return entityTypeId == null ? null : new NonAccessoryLiquidFilling.FillData(entityTypeId, capacityMl);
   }

   private record FillData(Identifier entityTypeId, int amountMl) {
   }
}
