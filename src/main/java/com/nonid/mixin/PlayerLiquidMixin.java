package com.nonid.mixin;

import com.nonid.LiquidDonorHistory;
import com.nonid.LiquidHolder;
import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import com.nonid.NonTrinketsIntegration;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class PlayerLiquidMixin implements LiquidHolder {
   @Unique
   private int nonLiquidStored = 0;
   @Unique
   private boolean nonLiquidMixed = false;
   @Unique
   private Identifier nonLiquidEntityType = null;
   @Unique
   private NeedsOfNature.PregnancyVariantData nonLiquidDonorVariantData = null;
   @Unique
   private List<LiquidDonorHistory.Entry> nonRecentLiquidDonors = List.of();

   @Override
   public int getLiquidStored() {
      return this.nonLiquidStored;
   }

   @Override
   public int getLiquidCapacity() {
      NonConfig config = NeedsOfNature.getConfig();
      if (config != null && !config.isLiquidTankEnabled()) {
         return 0;
      }

      int baseCapacity = config != null ? config.getLiquidTankCapacityMl() : 200;
      if ((Object)this instanceof PlayerEntity player) {
         baseCapacity += NonTrinketsIntegration.getActiveTankAccessoryEffects(player).liquidCapacityAdd();
      }

      return Math.max(0, baseCapacity);
   }

   @Override
   public void setLiquidStored(int value) {
      int cap = this.getLiquidCapacity();
      if (cap <= 0) {
         this.nonLiquidStored = 0;
         this.clearLiquidComposition();
         this.clearRecentLiquidDonors();
      } else {
         this.nonLiquidStored = Math.max(0, Math.min(cap, value));
         if (this.nonLiquidStored == 0) {
            this.clearLiquidComposition();
            this.clearRecentLiquidDonors();
         }
      }
   }

   @Override
   public LiquidHolder.LiquidComposition getLiquidComposition() {
      if (this.nonLiquidStored == 0) {
         return LiquidHolder.LiquidComposition.EMPTY;
      } else if (this.nonLiquidMixed) {
         return LiquidHolder.LiquidComposition.MIXED;
      } else {
         return this.nonLiquidEntityType != null ? LiquidHolder.LiquidComposition.ENTITY : LiquidHolder.LiquidComposition.MIXED;
      }
   }

   @Override
   public Identifier getLiquidEntityTypeId() {
      return this.nonLiquidEntityType;
   }

   @Override
   public NeedsOfNature.PregnancyVariantData getLiquidDonorVariantData() {
      return this.nonLiquidDonorVariantData;
   }

   @Override
   public void setLiquidCompositionMixed() {
      this.nonLiquidMixed = true;
      this.nonLiquidEntityType = null;
      this.nonLiquidDonorVariantData = null;
   }

   @Override
   public void setLiquidCompositionEntity(Identifier entityTypeId) {
      if (entityTypeId == null) {
         this.setLiquidCompositionMixed();
      } else {
         this.nonLiquidMixed = false;
         this.nonLiquidEntityType = entityTypeId;
      }
   }

   @Override
   public void setLiquidDonorVariantData(NeedsOfNature.PregnancyVariantData variantData) {
      this.nonLiquidDonorVariantData = NeedsOfNature.normalizePregnancyVariantData(variantData);
   }

   @Override
   public List<LiquidDonorHistory.Entry> getRecentLiquidDonors() {
      return this.nonRecentLiquidDonors;
   }

   @Override
   public void setRecentLiquidDonors(List<LiquidDonorHistory.Entry> donors) {
      this.nonRecentLiquidDonors = LiquidDonorHistory.normalize(donors);
   }

   @Override
   public void clearLiquidComposition() {
      this.nonLiquidMixed = false;
      this.nonLiquidEntityType = null;
      this.nonLiquidDonorVariantData = null;
   }

   @Inject(method = "writeData", at = @At("TAIL"))
   private void needsOfNature$writeLiquid(WriteView storage, CallbackInfo ci) {
      if ((Object)this instanceof PlayerEntity) {
         if (this.nonLiquidStored > 0) {
            storage.putInt("NeedsOfNatureLiquid", this.nonLiquidStored);
            if (this.nonLiquidMixed) {
               storage.putString("NeedsOfNatureLiquidType", "mixed");
            } else if (this.nonLiquidEntityType != null) {
               storage.putString("NeedsOfNatureLiquidType", this.nonLiquidEntityType.toString());
               String variantJson = NeedsOfNature.encodePregnancyVariantData(this.nonLiquidDonorVariantData);
               if (variantJson != null) {
                  storage.putString("NeedsOfNatureLiquidDonorVariantData", variantJson);
               }
            }

            String donorHistoryJson = LiquidDonorHistory.encode(this.nonRecentLiquidDonors);
            if (donorHistoryJson != null) {
               storage.putString("NeedsOfNatureLiquidDonorHistory", donorHistoryJson);
            }
         }
      }
   }

   @Inject(method = "readData", at = @At("TAIL"))
   private void needsOfNature$readLiquid(ReadView storage, CallbackInfo ci) {
      if ((Object)this instanceof PlayerEntity) {
         int stored = storage.getOptionalInt("NeedsOfNatureLiquid").orElse(0);
         this.setLiquidStored(stored);
         if (this.nonLiquidStored > 0) {
            String rawType = storage.getOptionalString("NeedsOfNatureLiquidType").orElse("");
            if (!rawType.isBlank() && !"mixed".equalsIgnoreCase(rawType)) {
               Identifier entityTypeId = Identifier.tryParse(rawType);
               if (entityTypeId != null) {
                  this.setLiquidCompositionEntity(entityTypeId);
                  this.setLiquidDonorVariantData(
                     NeedsOfNature.decodePregnancyVariantData(storage.getOptionalString("NeedsOfNatureLiquidDonorVariantData").orElse(""))
                  );
               } else {
                  this.setLiquidCompositionMixed();
               }
            } else {
               this.setLiquidCompositionMixed();
            }

            this.setRecentLiquidDonors(LiquidDonorHistory.decode(storage.getOptionalString("NeedsOfNatureLiquidDonorHistory").orElse("")));
         }
      }
   }
}
