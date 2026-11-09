package com.nonid;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public interface PregnancyHolder {
   @Nullable
   Identifier getPregnantEntityTypeId();

   long getPregnancyEndTick();

   boolean isPregnancyPendingHatch();

   @Nullable
   NeedsOfNature.PregnancyVariantData getPregnancyVariantData();

   int getPregnancyOffspringCount();

   void setPregnancyOffspringCount(int var1);

   void setPregnancyState(@Nullable Identifier var1, long var2, boolean var4, @Nullable NeedsOfNature.PregnancyVariantData var5);

   default void setPregnancyState(@Nullable Identifier entityTypeId, long endTick, boolean pendingHatch) {
      this.setPregnancyState(entityTypeId, endTick, pendingHatch, null);
   }

   void clearPregnancyState();

   default boolean hasPregnancyState() {
      return this.getPregnantEntityTypeId() != null;
   }
}
