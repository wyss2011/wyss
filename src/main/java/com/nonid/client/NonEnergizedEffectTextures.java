package com.nonid.client;

import com.nonid.effect.NonStatusEffects;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class NonEnergizedEffectTextures {
   private static final Identifier ENERGIZED_SPRITE_ID = Identifier.of("needsofnature", "mob_effect/energized");
   private static final Identifier ENERGIZED_STAGE_ONE_TEXTURE = Identifier.of("needsofnature", "textures/mob_effects/energized1.png");
   private static final Identifier ENERGIZED_STAGE_TWO_TEXTURE = Identifier.of("needsofnature", "textures/mob_effects/energized2.png");
   private static final Identifier ENERGIZED_STAGE_THREE_TEXTURE = Identifier.of("needsofnature", "textures/mob_effects/energized3.png");

   private NonEnergizedEffectTextures() {
   }

   public static boolean isEnergizedSprite(Identifier sprite) {
      return ENERGIZED_SPRITE_ID.equals(sprite);
   }

   public static Identifier resolveTexture(ClientPlayerEntity player) {
      if (player == null) {
         return ENERGIZED_STAGE_ONE_TEXTURE;
      } else {
         StatusEffectInstance effect = player.getStatusEffect(Registries.STATUS_EFFECT.getEntry(NonStatusEffects.ENERGIZED));
         if (effect == null) {
            return ENERGIZED_STAGE_ONE_TEXTURE;
         } else {
            int amplifier = Math.max(0, effect.getAmplifier());
            if (amplifier >= 2) {
               return ENERGIZED_STAGE_THREE_TEXTURE;
            } else {
               return amplifier == 1 ? ENERGIZED_STAGE_TWO_TEXTURE : ENERGIZED_STAGE_ONE_TEXTURE;
            }
         }
      }
   }
}
