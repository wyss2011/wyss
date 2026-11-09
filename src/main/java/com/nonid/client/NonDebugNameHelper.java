package com.nonid.client;

import com.nonid.EnergyHolder;
import com.nonid.GenderHolder;
import com.nonid.NeedsOfNature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public final class NonDebugNameHelper {
   private NonDebugNameHelper() {
   }

   public static boolean shouldShowDebugLabel(Entity entity) {
      if (!(entity instanceof MobEntity)) {
         return false;
      }

      if (!(entity instanceof EnergyHolder)) {
         return false;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      PlayerEntity player = client.player;
      if (player == null) {
         return false;
      }

      ItemStack main = player.getMainHandStack();
      ItemStack off = player.getOffHandStack();
      return main.getItem() == NeedsOfNature.DEBUG_STAFF || off.getItem() == NeedsOfNature.DEBUG_STAFF;
   }

   public static String resolveGender(Entity entity) {
      if (entity instanceof GenderHolder holder) {
         boolean male = holder.hasGender(1);
         boolean female = holder.hasGender(2);
         if (male && female) {
            return "male & female";
         } else if (male) {
            return "male";
         } else {
            return female ? "female" : "none";
         }
      } else {
         boolean hasMale = entity.getCommandTags().contains("gender.male");
         boolean hasFemale = entity.getCommandTags().contains("gender.female");
         if (hasMale && hasFemale) {
            return "male & female";
         } else if (hasMale) {
            return "male";
         } else {
            return hasFemale ? "female" : "none";
         }
      }
   }

   public static String resolveGenderSymbol(Entity entity) {
      if (entity instanceof GenderHolder holder) {
         boolean male = holder.hasGender(1);
         boolean female = holder.hasGender(2);
         if (male && female) {
            return "♂♀";
         } else if (male) {
            return "♂";
         } else {
            return female ? "♀" : "";
         }
      } else {
         boolean hasMale = entity.getCommandTags().contains("gender.male");
         boolean hasFemale = entity.getCommandTags().contains("gender.female");
         if (hasMale && hasFemale) {
            return "♂♀";
         } else if (hasMale) {
            return "♂";
         } else {
            return hasFemale ? "♀" : "";
         }
      }
   }
}
