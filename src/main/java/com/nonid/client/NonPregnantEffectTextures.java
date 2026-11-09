package com.nonid.client;

import com.nonid.PregnancyHolder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonPregnantEffectTextures {
   private static final Identifier PREGNANT_SPRITE_ID = Identifier.of("needsofnature", "mob_effect/pregnant");
   private static final int ITEM_ICON_SIZE = 16;

   private NonPregnantEffectTextures() {
   }

   public static boolean shouldDrawSourceEgg(Identifier sprite) {
      return PREGNANT_SPRITE_ID.equals(sprite) && !NonPregnancyClientState.isCycleEnabled();
   }

   public static ItemStack resolveSourceEgg(@Nullable ClientPlayerEntity player) {
      Identifier entityTypeId = NonPregnancyClientState.getPregnantEntityTypeId();
      if (entityTypeId == null && player instanceof PregnancyHolder holder) {
         entityTypeId = holder.getPregnantEntityTypeId();
      }

      if (entityTypeId == null) {
         return ItemStack.EMPTY;
      }

      EntityType<?> entityType = (EntityType<?>)Registries.ENTITY_TYPE.getOptionalValue(entityTypeId).orElse(null);
      SpawnEggItem egg = entityType == null ? null : SpawnEggItem.forEntity(entityType);
      return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
   }

   public static void drawSourceEgg(DrawContext context, ItemStack eggStack, int x, int y, int width, int height) {
      int drawX = x + Math.max(0, (width - 16) / 2);
      int drawY = y + Math.max(0, (height - 16) / 2);
      context.drawItemWithoutEntity(eggStack, drawX, drawY);
   }
}
