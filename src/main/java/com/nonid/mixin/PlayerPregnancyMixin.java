package com.nonid.mixin;

import com.mojang.serialization.Codec;
import com.nonid.NeedsOfNature;
import com.nonid.PregnancyCycleHolder;
import com.nonid.PregnancyHolder;
import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class PlayerPregnancyMixin implements PregnancyHolder, PregnancyCycleHolder {
   @Unique
   @Nullable
   private Identifier nonPregnancyEntityType;
   @Unique
   private long nonPregnancyEndTick;
   @Unique
   private boolean nonPregnancyPendingHatch;
   @Unique
   @Nullable
   private NeedsOfNature.PregnancyVariantData nonPregnancyVariantData;
   @Unique
   private int nonPregnancyOffspringCount;
   @Unique
   private boolean nonPregnancyCycleInitialized;
   @Unique
   private long nonPregnancyCycleProgressTicks;
   @Unique
   private long nonPregnancyCycleLastGameTick;
   @Unique
   private long nonPregnancyCycleLastTimeOfDay;

   @Nullable
   @Override
   public Identifier getPregnantEntityTypeId() {
      return this.nonPregnancyEntityType;
   }

   @Override
   public long getPregnancyEndTick() {
      return this.nonPregnancyEndTick;
   }

   @Override
   public boolean isPregnancyPendingHatch() {
      return this.nonPregnancyPendingHatch;
   }

   @Nullable
   @Override
   public NeedsOfNature.PregnancyVariantData getPregnancyVariantData() {
      return this.nonPregnancyVariantData;
   }

   @Override
   public int getPregnancyOffspringCount() {
      return this.nonPregnancyOffspringCount;
   }

   @Override
   public void setPregnancyOffspringCount(int offspringCount) {
      this.nonPregnancyOffspringCount = Math.max(0, Math.min(16, offspringCount));
   }

   @Override
   public void setPregnancyState(
      @Nullable Identifier entityTypeId, long endTick, boolean pendingHatch, @Nullable NeedsOfNature.PregnancyVariantData variantData
   ) {
      if (entityTypeId == null) {
         this.clearPregnancyState();
      } else {
         this.nonPregnancyEntityType = entityTypeId;
         this.nonPregnancyEndTick = Math.max(0L, endTick);
         this.nonPregnancyPendingHatch = pendingHatch;
         this.nonPregnancyVariantData = NeedsOfNature.normalizePregnancyVariantData(variantData);
      }
   }

   @Override
   public void clearPregnancyState() {
      this.nonPregnancyEntityType = null;
      this.nonPregnancyEndTick = 0L;
      this.nonPregnancyPendingHatch = false;
      this.nonPregnancyVariantData = null;
      this.nonPregnancyOffspringCount = 0;
   }

   @Override
   public boolean isPregnancyCycleInitialized() {
      return this.nonPregnancyCycleInitialized;
   }

   @Override
   public long getPregnancyCycleProgressTicks() {
      return this.nonPregnancyCycleProgressTicks;
   }

   @Override
   public long getPregnancyCycleLastGameTick() {
      return this.nonPregnancyCycleLastGameTick;
   }

   @Override
   public long getPregnancyCycleLastTimeOfDay() {
      return this.nonPregnancyCycleLastTimeOfDay;
   }

   @Override
   public void setPregnancyCycleState(long progressTicks, long lastGameTick, long lastTimeOfDay) {
      this.nonPregnancyCycleInitialized = true;
      this.nonPregnancyCycleProgressTicks = Math.max(0L, progressTicks);
      this.nonPregnancyCycleLastGameTick = Math.max(0L, lastGameTick);
      this.nonPregnancyCycleLastTimeOfDay = Math.max(0L, lastTimeOfDay);
   }

   @Override
   public void clearPregnancyCycleState() {
      this.nonPregnancyCycleInitialized = false;
      this.nonPregnancyCycleProgressTicks = 0L;
      this.nonPregnancyCycleLastGameTick = 0L;
      this.nonPregnancyCycleLastTimeOfDay = 0L;
   }

   @Inject(method = "writeData", at = @At("TAIL"))
   private void non$writePregnancy(WriteView storage, CallbackInfo ci) {
      if ((Object)this instanceof PlayerEntity) {
         if (this.nonPregnancyCycleInitialized) {
            storage.put("NeedsOfNaturePregnancyCycleProgress", Codec.LONG, Math.max(0L, this.nonPregnancyCycleProgressTicks));
            storage.put("NeedsOfNaturePregnancyCycleLastGameTick", Codec.LONG, Math.max(0L, this.nonPregnancyCycleLastGameTick));
            storage.put("NeedsOfNaturePregnancyCycleLastTimeOfDay", Codec.LONG, Math.max(0L, this.nonPregnancyCycleLastTimeOfDay));
         }

         if (this.nonPregnancyEntityType != null) {
            storage.putString("NeedsOfNaturePregnancyType", this.nonPregnancyEntityType.toString());
            storage.put("NeedsOfNaturePregnancyEndTick", Codec.LONG, Math.max(0L, this.nonPregnancyEndTick));
            storage.put("NeedsOfNaturePregnancyPending", Codec.BOOL, this.nonPregnancyPendingHatch);
            if (this.nonPregnancyOffspringCount > 0) {
               storage.putInt("NeedsOfNaturePregnancyOffspringCount", this.nonPregnancyOffspringCount);
            }

            String variantJson = NeedsOfNature.encodePregnancyVariantData(this.nonPregnancyVariantData);
            if (variantJson != null) {
               storage.putString("NeedsOfNaturePregnancyVariantData", variantJson);
            }
         }
      }
   }

   @Inject(method = "readData", at = @At("TAIL"))
   private void non$readPregnancy(ReadView storage, CallbackInfo ci) {
      if ((Object)this instanceof PlayerEntity) {
         Optional<Long> cycleProgress = storage.read("NeedsOfNaturePregnancyCycleProgress", Codec.LONG);
         if (cycleProgress.isPresent()) {
            this.setPregnancyCycleState(
               cycleProgress.get(),
               storage.read("NeedsOfNaturePregnancyCycleLastGameTick", Codec.LONG).orElse(0L),
               storage.read("NeedsOfNaturePregnancyCycleLastTimeOfDay", Codec.LONG).orElse(0L)
            );
         } else {
            this.clearPregnancyCycleState();
         }

         String rawType = storage.getOptionalString("NeedsOfNaturePregnancyType").orElse("");
         Identifier entityTypeId = rawType.isBlank() ? null : Identifier.tryParse(rawType);
         if (entityTypeId == null) {
            this.clearPregnancyState();
         } else {
            long endTick = Math.max(0L, storage.read("NeedsOfNaturePregnancyEndTick", Codec.LONG).orElse(0L));
            boolean pending = storage.read("NeedsOfNaturePregnancyPending", Codec.BOOL).orElse(false);
            NeedsOfNature.PregnancyVariantData variantData = NeedsOfNature.decodePregnancyVariantData(
               storage.getOptionalString("NeedsOfNaturePregnancyVariantData").orElse("")
            );
            this.setPregnancyState(entityTypeId, endTick, pending, variantData);
            this.setPregnancyOffspringCount(storage.getOptionalInt("NeedsOfNaturePregnancyOffspringCount").orElse(0));
         }
      }
   }
}
