package com.nonid.item;

import com.nonid.data.NonAccessoryLiquidFilling;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.Item.Settings;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ClickType;

public final class NonDataAccessoryItem extends Item {
   public NonDataAccessoryItem(Settings settings) {
      super(settings);
   }

   public boolean onClicked(ItemStack stack, ItemStack otherStack, Slot slot, ClickType clickType, PlayerEntity player, StackReference cursorStackReference) {
      if (clickType != ClickType.LEFT || !slot.canTakePartial(player)) {
         return false;
      }

      if (!NonAccessoryLiquidFilling.fillInPlace(stack, otherStack)) {
         return false;
      }

      otherStack.decrement(1);
      if (otherStack.isEmpty()) {
         cursorStackReference.set(new ItemStack(Items.GLASS_BOTTLE));
      } else {
         cursorStackReference.set(otherStack);
         ItemStack glassBottle = new ItemStack(Items.GLASS_BOTTLE);
         if (!player.getInventory().insertStack(glassBottle) && !player.getEntityWorld().isClient()) {
            player.dropItem(glassBottle, false);
         }
      }

      if (player.getEntityWorld().isClient()) {
         player.playSound(SoundEvents.ITEM_BOTTLE_FILL, 1.0F, 1.0F);
      }

      return true;
   }
}
