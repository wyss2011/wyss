package com.nonid.client;

import com.nonid.NonTrinketsIntegration;
import java.util.List;
import net.minecraft.entity.LivingEntity;

public final class NonAccessorySkinOverlays {
   private NonAccessorySkinOverlays() {
   }

   public static List<NonTrinketsIntegration.ResolvedVisualLayer> resolveEquippedOverlays(LivingEntity entity) {
      return NonTrinketsIntegration.resolveEquippedSkinOverlays(entity);
   }
}
