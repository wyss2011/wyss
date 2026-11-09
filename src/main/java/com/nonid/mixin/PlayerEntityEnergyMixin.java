package com.nonid.mixin;

import com.nonid.EnergyHolder;
import com.nonid.NeedsOfNature;
import com.nonid.NonTrinketsIntegration;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.data.DataTracker.Builder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityEnergyMixin implements EnergyHolder {
   @Unique
   private static final TrackedData<Integer> ENERGY = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
   @Unique
   private static final int DEFAULT_TICKS_PER_ENERGY = 120;
   @Unique
   private static final int WATER_ENERGY_DECAY_TICKS = 10;
   @Unique
   private int non$energyTickCounter;
   @Unique
   private int non$waterEnergyDecayTickCounter;
   @Unique
   private boolean non$energyInitialized;
   @Unique
   private float non$energyGainMult = 1.0F;
   @Unique
   private float non$energyGainDrift = 1.0F;

   @Inject(method = "initDataTracker", at = @At("TAIL"))
   private void needsOfNature$initEnergyTracker(Builder builder, CallbackInfo ci) {
      builder.add(ENERGY, 0);
   }

   @Inject(method = "tick", at = @At("TAIL"))
   private void needsOfNature$tickEnergy(CallbackInfo ci) {
      PlayerEntity self = (PlayerEntity)(Object)this;
      if (!self.getEntityWorld().isClient()) {
         if (self.getEntityWorld() instanceof ServerWorld serverWorld) {
            this.ensureEnergyInitialized(serverWorld);
            if (self instanceof ServerPlayerEntity serverPlayer) {
               NonTrinketsIntegration.syncAccessoryStateIfChanged(serverPlayer);
            }

            int energy = this.getEnergy();
            energy = this.non$applyWaterEnergyDecay(self, energy);
            int ticksPerGain = this.non$getTicksPerGain();
            if (energy >= 200) {
               this.non$energyTickCounter = 0;
               if (self instanceof ServerPlayerEntity serverPlayer) {
                  NeedsOfNature.updateEnergizedEffect(serverPlayer, this);
                  NeedsOfNature.tickPlayerEnergyAura(serverWorld, serverPlayer);
               }
            } else {
               this.non$energyTickCounter++;
               if (this.non$energyTickCounter >= ticksPerGain) {
                  this.non$energyTickCounter = 0;
                  this.setEnergy(Math.min(200, energy + 1));
               }

               if (self instanceof ServerPlayerEntity serverPlayer) {
                  NeedsOfNature.updateEnergizedEffect(serverPlayer, this);
                  NeedsOfNature.tickPlayerEnergyAura(serverWorld, serverPlayer);
               }
            }
         }
      }
   }

   @Unique
   private int non$applyWaterEnergyDecay(PlayerEntity self, int energy) {
      if (self.isTouchingWater() && energy > 0) {
         this.non$waterEnergyDecayTickCounter++;
         if (this.non$waterEnergyDecayTickCounter < 10) {
            return energy;
         }

         this.non$waterEnergyDecayTickCounter = 0;
         int next = Math.max(0, energy - 1);
         this.setEnergy(next);
         return next;
      } else {
         this.non$waterEnergyDecayTickCounter = 0;
         return energy;
      }
   }

   @Override
   public int getEnergy() {
      PlayerEntity self = (PlayerEntity)(Object)this;
      return (Integer)self.getDataTracker().get(ENERGY);
   }

   @Override
   public int getMaxEnergy() {
      return 200;
   }

   @Override
   public void setEnergy(int value) {
      PlayerEntity self = (PlayerEntity)(Object)this;
      self.getDataTracker().set(ENERGY, Math.min(200, Math.max(0, value)));
   }

   @Override
   public float getEnergyGainMultiplier() {
      return this.non$energyGainMult <= 0.0F ? 1.0F : this.non$energyGainMult;
   }

   @Override
   public void setEnergyGainMultiplier(float value) {
      if (Float.isFinite(value)) {
         this.non$energyGainMult = Math.max(0.1F, Math.min(10.0F, value));
      }
   }

   @Override
   public float getEnergyGainDrift() {
      return this.non$energyGainDrift <= 0.0F ? 1.0F : this.non$energyGainDrift;
   }

   @Override
   public void setEnergyGainDrift(float value) {
      if (Float.isFinite(value)) {
         this.non$energyGainDrift = Math.max(0.1F, Math.min(10.0F, value));
      }
   }

   @Override
   public boolean isEnergyInitialized() {
      return this.non$energyInitialized;
   }

   @Override
   public void markEnergyInitialized() {
      this.non$energyInitialized = true;
   }

   @Override
   public void ensureEnergyInitialized(ServerWorld world) {
      if (!this.non$energyInitialized) {
         this.non$energyInitialized = true;
         if (this.non$energyGainMult <= 0.0F) {
            this.non$energyGainMult = 1.0F;
         }

         if (this.non$energyGainDrift <= 0.0F) {
            this.non$energyGainDrift = 1.0F;
         }

         if (this.getEnergy() < 0) {
            this.setEnergy(0);
         }
      }
   }

   @Unique
   private int non$getTicksPerGain() {
      double rate = NeedsOfNature.getConfig().getEnergyGainRate();
      float scale = this.getEnergyGainMultiplier() * this.getEnergyGainDrift() * (float)rate;
      PlayerEntity self = (PlayerEntity)(Object)this;
      if (self instanceof ServerPlayerEntity serverPlayer) {
         scale = (float)(scale * NonTrinketsIntegration.getAccessoryEffects(serverPlayer).playerEnergyGainMultiplier());
      }

      if (Float.isFinite(scale) && !(scale <= 0.0F)) {
         int effective = Math.round(120.0F / scale);
         return Math.max(1, effective);
      } else {
         return 120;
      }
   }
}
