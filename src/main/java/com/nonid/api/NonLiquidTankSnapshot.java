package com.nonid.api;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record NonLiquidTankSnapshot(int storedMl, int capacityMl, NonLiquidComposition composition, @Nullable Identifier entityTypeId, int tintRgb) {
   public float fillRatio() {
      return this.capacityMl <= 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, (float)this.storedMl / this.capacityMl));
   }
}
