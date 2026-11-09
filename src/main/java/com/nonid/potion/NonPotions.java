package com.nonid.potion;

import com.nonid.NeedsOfNature;
import com.nonid.effect.NonStatusEffects;
import java.util.Optional;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.ConsumableComponents;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonPotions {
   public static final String LIQUID_ENTITY_DATA_KEY = "liquid_entity";
   private static final ConsumableComponent HONEY_BOTTLE_DRINK_CONSUMABLE = ConsumableComponents.drink().sound(SoundEvents.ITEM_HONEY_BOTTLE_DRINK).build();
   private static final int ENERGY_BASE_DURATION_TICKS = 800;
   private static final int ENERGY_STRONG_DURATION_TICKS = 400;
   private static final int ENERGY_LONG_DURATION_TICKS = 1600;
   private static final int FERTILITY_BASE_DURATION_TICKS = 9600;
   private static final int FERTILITY_STRONG_DURATION_TICKS = 4800;
   private static final int FERTILITY_LONG_DURATION_TICKS = 19200;
   public static final Potion LIQUID = register("liquid", new Potion("needsofnature.liquid", new StatusEffectInstance[0]));
   public static final Potion ENERGY = register(
      "energy",
      new Potion("needsofnature.energy", new StatusEffectInstance[]{new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.ENERGY), 800)})
   );
   public static final Potion ENERGY_II = register(
      "energy_ii",
      new Potion(
         "needsofnature.energy_ii", new StatusEffectInstance[]{new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.ENERGY), 400, 1)}
      )
   );
   public static final Potion ENERGY_LONG = register(
      "energy_long",
      new Potion(
         "needsofnature.energy_long", new StatusEffectInstance[]{new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.ENERGY), 1600)}
      )
   );
   public static final Potion ENERGY_RELIEF = register(
      "energy_relief",
      new Potion(
         "needsofnature.energy_relief",
         new StatusEffectInstance[]{new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.ENERGY_RELIEF), 800)}
      )
   );
   public static final Potion ENERGY_RELIEF_II = register(
      "energy_relief_ii",
      new Potion(
         "needsofnature.energy_relief_ii",
         new StatusEffectInstance[]{new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.ENERGY_RELIEF), 400, 1)}
      )
   );
   public static final Potion ENERGY_RELIEF_LONG = register(
      "energy_relief_long",
      new Potion(
         "needsofnature.energy_relief_long",
         new StatusEffectInstance[]{new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.ENERGY_RELIEF), 1600)}
      )
   );
   public static final Potion FERTILE_NECTAR = register("fertile_nectar", new Potion("needsofnature.fertile_nectar", new StatusEffectInstance[0]));
   public static final Potion FERTILITY_INCREASER = register(
      "fertility_increaser",
      new Potion(
         "needsofnature.fertility_increaser",
         new StatusEffectInstance[]{new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.FERTILITY_INCREASER), 9600)}
      )
   );
   public static final Potion FERTILITY_INCREASER_II = register(
      "fertility_increaser_ii",
      new Potion(
         "needsofnature.fertility_increaser_ii",
         new StatusEffectInstance[]{new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.FERTILITY_INCREASER), 4800, 1)}
      )
   );
   public static final Potion FERTILITY_INCREASER_LONG = register(
      "fertility_increaser_long",
      new Potion(
         "needsofnature.fertility_increaser_long",
         new StatusEffectInstance[]{new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.FERTILITY_INCREASER), 19200)}
      )
   );

   private NonPotions() {
   }

   public static void registerAll() {
   }

   public static boolean isLiquidPotion(Optional<RegistryEntry<Potion>> potionEntry) {
      return potionEntry != null && potionEntry.isPresent() && potionEntry.get().value() == LIQUID;
   }

   public static boolean isLiquidBottle(ItemStack stack) {
      if (stack != null && !stack.isEmpty() && stack.isOf(Items.POTION)) {
         PotionContentsComponent contents = (PotionContentsComponent)stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
         return isLiquidPotion(contents.potion());
      } else {
         return false;
      }
   }

   public static void applyHoneyBottleDrinkSound(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         stack.set(DataComponentTypes.CONSUMABLE, HONEY_BOTTLE_DRINK_CONSUMABLE);
      }
   }

   public static void setLiquidBottleEntityTypeId(ItemStack stack, @Nullable Identifier entityTypeId) {
      if (stack != null && !stack.isEmpty()) {
         NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
            String value = entityTypeId == null ? "" : entityTypeId.toString();
            nbt.putString("liquid_entity", value);
         });
      }
   }

   @Nullable
   public static Identifier getLiquidBottleEntityTypeId(ItemStack stack) {
      if (!isLiquidBottle(stack)) {
         return null;
      }

      NbtComponent custom = (NbtComponent)stack.get(DataComponentTypes.CUSTOM_DATA);
      if (custom != null && !custom.isEmpty()) {
         NbtCompound nbt = custom.copyNbt();
         if (!nbt.contains("liquid_entity")) {
            return null;
         }

         String raw = nbt.getString("liquid_entity", "");
         return raw != null && !raw.isBlank() ? Identifier.tryParse(raw) : null;
      } else {
         return null;
      }
   }

   private static Potion register(String path, Potion potion) {
      return (Potion)Registry.register(Registries.POTION, NeedsOfNature.id(path), potion);
   }
}
