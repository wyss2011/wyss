package com.nonid.mixin;

import com.mojang.serialization.Codec;
import com.nonid.EnergyHolder;
import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityEnergyPersistenceMixin {
   @Inject(method = "writeData", at = @At("TAIL"))
   private void needsOfNature$writeEnergy(WriteView storage, CallbackInfo ci) {
      if (this instanceof EnergyHolder holder) {
         storage.putInt("NeedsOfNatureEnergy", holder.getEnergy());
         storage.putFloat("NeedsOfNatureEnergyGainMult", holder.getEnergyGainMultiplier());
         storage.putFloat("NeedsOfNatureEnergyGainDrift", holder.getEnergyGainDrift());
      }
   }

   @Inject(method = "readData", at = @At("TAIL"))
   private void needsOfNature$readEnergy(ReadView storage, CallbackInfo ci) {
      if (this instanceof EnergyHolder holder) {
         boolean loaded = false;
         Optional<Integer> energyOpt = storage.getOptionalInt("NeedsOfNatureEnergy");
         if (energyOpt.isPresent()) {
            holder.setEnergy(energyOpt.get());
            loaded = true;
         }

         Optional<Float> multOpt = storage.read("NeedsOfNatureEnergyGainMult", Codec.FLOAT);
         if (multOpt.isPresent()) {
            holder.setEnergyGainMultiplier(multOpt.get());
            loaded = true;
         }

         Optional<Float> driftOpt = storage.read("NeedsOfNatureEnergyGainDrift", Codec.FLOAT);
         if (driftOpt.isPresent()) {
            holder.setEnergyGainDrift(driftOpt.get());
            loaded = true;
         }

         if (loaded) {
            holder.markEnergyInitialized();
         }
      }
   }
}
