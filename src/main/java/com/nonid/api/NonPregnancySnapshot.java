package com.nonid.api;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record NonPregnancySnapshot(@Nullable Identifier entityTypeId, long endTick, boolean pendingHatch) {
   public boolean active() {
      return this.entityTypeId != null;
   }
}
