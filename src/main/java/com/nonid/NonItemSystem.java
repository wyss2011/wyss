package com.nonid;

import com.nonid.item.EnergyStabilizerItem;
import com.nonid.potion.NonPotions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.component.type.FoodComponent.Builder;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ItemGroup.Entries;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

final class NonItemSystem {
   private static final FoodComponent LIQUID_BOTTLE_FOOD = new Builder().nutrition(1).saturationModifier(0.0F).alwaysEdible().build();
   private static final FoodComponent FERTILE_NECTAR_FOOD = new Builder().nutrition(7).saturationModifier(0.0F).alwaysEdible().build();
   private static final TooltipDisplayComponent LIQUID_BOTTLE_TOOLTIP = TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.POTION_CONTENTS, true);

   private NonItemSystem() {
   }

   static void giveItemOrDrop(ServerPlayerEntity player, ItemStack stack) {
      if (player != null && stack != null && !stack.isEmpty()) {
         if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
         }
      }
   }

   static boolean consumeOneGlassBottle(ServerPlayerEntity player, Hand hand) {
      if (player != null && hand != null) {
         ItemStack stack = player.getStackInHand(hand);
         if (!stack.isEmpty() && stack.isOf(Items.GLASS_BOTTLE)) {
            stack.decrement(1);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   static void refundGlassBottle(ServerPlayerEntity player) {
      giveItemOrDrop(player, new ItemStack(Items.GLASS_BOTTLE));
   }

   static ItemStack createBottleStackForTank(LiquidHolder holder) {
      Identifier entityTypeId = null;
      if (holder != null && holder.getLiquidComposition() == LiquidHolder.LiquidComposition.ENTITY) {
         entityTypeId = holder.getLiquidEntityTypeId();
      }

      return createLiquidBottleStack(entityTypeId);
   }

   static ItemStack createLiquidBottleStack(@Nullable Identifier entityTypeId) {
      int tint = NonLiquidSystem.resolveBottleTintRgb(entityTypeId);
      PotionContentsComponent contents = new PotionContentsComponent(
         Optional.of(Registries.POTION.getEntry(NonPotions.LIQUID)), Optional.of(tint), List.of(), Optional.empty()
      );
      ItemStack stack = new ItemStack(Items.POTION);
      stack.set(DataComponentTypes.POTION_CONTENTS, contents);
      stack.set(DataComponentTypes.MAX_STACK_SIZE, 16);
      stack.set(DataComponentTypes.FOOD, LIQUID_BOTTLE_FOOD);
      NonPotions.applyHoneyBottleDrinkSound(stack);
      stack.set(DataComponentTypes.TOOLTIP_DISPLAY, LIQUID_BOTTLE_TOOLTIP);
      NonPotions.setLiquidBottleEntityTypeId(stack, entityTypeId);
      if (entityTypeId == null) {
         stack.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("item.needsofnature.mixed_liquid_bottle").copy().styled(style -> style.withItalic(false)));
      } else {
         stack.set(
            DataComponentTypes.CUSTOM_NAME,
            Text.translatable("item.needsofnature.entity_liquid_bottle", new Object[]{resolveEntityNameText(entityTypeId)})
               .copy()
               .styled(style -> style.withItalic(false))
         );
      }

      return stack;
   }

   static void addEntityLiquidAndStabilizersToItemGroup(Entries entries, @Nullable NonConfig config) {
      for (String rawEntityId : NonLiquidSystem.resolveEffectiveLiquidGainMap().keySet()) {
         Identifier entityId = Identifier.tryParse(rawEntityId);
         if (entityId != null) {
            entries.add(createLiquidBottleStack(entityId));
            entries.add(createEnergyStabilizerStack(entityId));
         }
      }
   }

   static void addNonPotionVariantsToItemGroup(Entries entries) {
      List<RegistryEntry<Potion>> nonPotions = new ArrayList<>();

      for (Potion potion : Registries.POTION) {
         Identifier potionId = Registries.POTION.getId(potion);
         if (potionId != null && "needsofnature".equals(potionId.getNamespace())) {
            nonPotions.add(Registries.POTION.getEntry(potion));
         }
      }

      entries.add(createLiquidBottleStack(null));

      for (RegistryEntry<Potion> potionEntry : nonPotions) {
         if (potionEntry.value() != NonPotions.LIQUID) {
            entries.add(createPotionVariantStack(Items.POTION, potionEntry));
         }
      }

      for (RegistryEntry<Potion> potionEntry : nonPotions) {
         if (potionEntry.value() != NonPotions.LIQUID) {
            entries.add(createPotionVariantStack(Items.SPLASH_POTION, potionEntry));
         }
      }

      for (RegistryEntry<Potion> potionEntry : nonPotions) {
         if (potionEntry.value() != NonPotions.LIQUID) {
            entries.add(createPotionVariantStack(Items.LINGERING_POTION, potionEntry));
         }
      }

      for (RegistryEntry<Potion> potionEntry : nonPotions) {
         if (potionEntry.value() != NonPotions.LIQUID) {
            entries.add(createPotionVariantStack(Items.TIPPED_ARROW, potionEntry));
         }
      }
   }

   static ItemStack createPotionVariantStack(Item item, RegistryEntry<Potion> potionEntry) {
      ItemStack stack = new ItemStack(item);
      stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(potionEntry));
      if (item == Items.POTION && potionEntry.value() == NonPotions.FERTILE_NECTAR) {
         stack.set(DataComponentTypes.FOOD, FERTILE_NECTAR_FOOD);
         stack.set(DataComponentTypes.MAX_STACK_SIZE, 16);
         NonPotions.applyHoneyBottleDrinkSound(stack);
      }

      return stack;
   }

   static boolean isNonPotionVariantStack(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         Item item = stack.getItem();
         boolean potionLike = item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW;
         if (!potionLike) {
            return false;
         }

         PotionContentsComponent contents = (PotionContentsComponent)stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
         Optional<RegistryEntry<Potion>> potionOpt = contents.potion();
         if (potionOpt.isEmpty()) {
            return false;
         }

         Identifier potionId = Registries.POTION.getId((Potion)potionOpt.get().value());
         return potionId != null && "needsofnature".equals(potionId.getNamespace());
      } else {
         return false;
      }
   }

   static ItemStack createEnergyStabilizerStack(@Nullable Identifier entityTypeId) {
      return EnergyStabilizerItem.createStackForEntity(entityTypeId);
   }

   private static Text resolveEntityNameText(Identifier entityTypeId) {
      return (Text)(entityTypeId == null
         ? Text.literal("未知")
         : Registries.ENTITY_TYPE
            .getOptionalValue(entityTypeId)
            .<Text>map(EntityType::getName)
            .orElseGet(() -> Text.literal(formatFallbackEntityName(entityTypeId))));
   }

   private static String formatFallbackEntityName(Identifier entityTypeId) {
      String[] parts = entityTypeId.getPath().replace('/', '_').split("_");
      StringBuilder out = new StringBuilder();

      for (String part : parts) {
         if (part != null && !part.isEmpty()) {
            if (!out.isEmpty()) {
               out.append(' ');
            }

            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
               out.append(part.substring(1));
            }
         }
      }

      return out.isEmpty() ? entityTypeId.toString() : out.toString();
   }
}
