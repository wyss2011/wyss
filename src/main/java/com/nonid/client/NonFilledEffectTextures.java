package com.nonid.client;

import com.nonid.effect.NonStatusEffects;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class NonFilledEffectTextures {
   private static final Identifier FILLED_SPRITE_ID = Identifier.of("needsofnature", "mob_effect/filled");
   private static final Identifier FILLED_STAGE_ONE_TEXTURE = Identifier.of("needsofnature", "textures/mob_effects/filled1.png");
   private static final Identifier FILLED_STAGE_TWO_TEXTURE = Identifier.of("needsofnature", "textures/mob_effects/filled2.png");
   private static final Identifier FILLED_STAGE_THREE_TEXTURE = Identifier.of("needsofnature", "textures/mob_effects/filled3.png");

   private NonFilledEffectTextures() {
   }

   public static boolean isFilledSprite(Identifier sprite) {
      return FILLED_SPRITE_ID.equals(sprite);
   }

   public static Identifier resolveTexture(ClientPlayerEntity player) {
      if (player == null) {
         return FILLED_STAGE_ONE_TEXTURE;
      } else {
         StatusEffectInstance effect = player.getStatusEffect(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.FILLED));
         if (effect == null) {
            return FILLED_STAGE_ONE_TEXTURE;
         } else {
            int amplifier = Math.max(0, effect.getAmplifier());
            if (amplifier >= 2) {
               return FILLED_STAGE_THREE_TEXTURE;
            } else {
               return amplifier == 1 ? FILLED_STAGE_TWO_TEXTURE : FILLED_STAGE_ONE_TEXTURE;
            }
         }
      }
   }
}
