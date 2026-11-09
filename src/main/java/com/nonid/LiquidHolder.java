package com.nonid;

import java.util.List;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public interface LiquidHolder {
   int getLiquidStored();

   int getLiquidCapacity();

   void setLiquidStored(int var1);

   LiquidHolder.LiquidComposition getLiquidComposition();

   @Nullable
   Identifier getLiquidEntityTypeId();

   @Nullable
   NeedsOfNature.PregnancyVariantData getLiquidDonorVariantData();

   void setLiquidCompositionMixed();

   void setLiquidCompositionEntity(Identifier var1);

   void setLiquidDonorVariantData(@Nullable NeedsOfNature.PregnancyVariantData var1);

   List<LiquidDonorHistory.Entry> getRecentLiquidDonors();

   void setRecentLiquidDonors(List<LiquidDonorHistory.Entry> var1);

   default void recordRecentLiquidDonor(Identifier entityTypeId, @Nullable NeedsOfNature.PregnancyVariantData variantData) {
      if (entityTypeId != null) {
         this.setRecentLiquidDonors(LiquidDonorHistory.append(this.getRecentLiquidDonors(), entityTypeId, variantData));
      }
   }

   default void clearRecentLiquidDonors() {
      this.setRecentLiquidDonors(List.of());
   }

   void clearLiquidComposition();

   default void addLiquidStored(int amount) {
      if (amount != 0) {
         this.setLiquidStored(this.getLiquidStored() + amount);
      }
   }

   enum LiquidComposition {
      EMPTY,
      MIXED,
      ENTITY;
   }
}
