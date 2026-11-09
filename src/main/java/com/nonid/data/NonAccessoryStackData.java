package com.nonid.data;

import com.nonid.NeedsOfNature;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonAccessoryStackData {
   private static final String LIQUID_ENTITY_KEY = "NonAccessoryLiquidEntity";
   private static final String LIQUID_AMOUNT_KEY = "NonAccessoryLiquidAmount";

   private NonAccessoryStackData() {
   }

   public static int resolveDyeRgb(ItemStack stack, int fallbackRgb) {
      if (stack != null && !stack.isEmpty()) {
         DyedColorComponent dyed = (DyedColorComponent)stack.get(DataComponentTypes.DYED_COLOR);
         return (dyed == null ? fallbackRgb : dyed.rgb()) & 16777215;
      } else {
         return fallbackRgb & 16777215;
      }
   }

   @Nullable
   public static NonAccessoryStackData.StoredLiquid getStoredLiquid(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         NbtComponent custom = (NbtComponent)stack.get(DataComponentTypes.CUSTOM_DATA);
         if (custom != null && !custom.isEmpty()) {
            NbtCompound nbt = custom.copyNbt();
            Identifier entityTypeId = Identifier.tryParse(nbt.getString("NonAccessoryLiquidEntity", ""));
            int amount = nbt.getInt("NonAccessoryLiquidAmount", 0);
            return entityTypeId != null && amount > 0 ? new NonAccessoryStackData.StoredLiquid(entityTypeId, amount) : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static void setStoredLiquid(ItemStack stack, @Nullable Identifier entityTypeId, int amountMl) {
      if (stack != null && !stack.isEmpty()) {
         int amount = Math.max(0, amountMl);
         if (entityTypeId != null && amount != 0) {
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
               nbt.putString("NonAccessoryLiquidEntity", entityTypeId.toString());
               nbt.putInt("NonAccessoryLiquidAmount", amount);
            });
            int argb = 0xFF000000 | NeedsOfNature.resolveBottleLiquidTintRgb(entityTypeId);
            stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(List.of(), List.of(true), List.of(), List.of(argb)));
         } else {
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
               nbt.remove("NonAccessoryLiquidEntity");
               nbt.remove("NonAccessoryLiquidAmount");
            });
            stack.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
         }
      }
   }

   public record StoredLiquid(Identifier entityTypeId, int amountMl) {
   }
}
