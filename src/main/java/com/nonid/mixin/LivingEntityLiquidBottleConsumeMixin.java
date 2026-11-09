package com.nonid.mixin;

import com.nonid.NeedsOfNature;
import com.nonid.potion.NonPotions;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityLiquidBottleConsumeMixin {
   @Inject(method = "consumeItem", at = @At("HEAD"))
   private void non$grantEmergencySnackForEntityLiquidBottle(CallbackInfo ci) {
      if ((Object)this instanceof ServerPlayerEntity player) {
         ItemStack stack = player.getActiveItem();
         boolean entityLiquidBottle = NonPotions.isLiquidBottle(stack) && NonPotions.getLiquidBottleEntityTypeId(stack) != null;
         if (entityLiquidBottle) {
            NeedsOfNature.grantEmergencySnackAdvancement(player);
         } else {
            if (stack.isOf(Items.MILK_BUCKET)) {
               NeedsOfNature.grantUdderlyUnfortunateAdvancementIfPregnant(player);
            }

            if (stack.contains(DataComponentTypes.FOOD) || stack.contains(DataComponentTypes.CONSUMABLE)) {
               NeedsOfNature.resetStableDietStreak(player);
            }
         }
      }
   }
}
