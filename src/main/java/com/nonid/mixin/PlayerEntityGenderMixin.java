package com.nonid.mixin;

import com.nonid.GenderHolder;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.data.DataTracker.Builder;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityGenderMixin implements GenderHolder {
   @Unique
   private static final TrackedData<Integer> NON_GENDER = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);

   @Inject(method = "initDataTracker", at = @At("TAIL"))
   private void non$initGenderTracker(Builder builder, CallbackInfo ci) {
      builder.add(NON_GENDER, 0);
   }

   @Override
   public int getGenderMask() {
      PlayerEntity self = (PlayerEntity)(Object)this;
      return (Integer)self.getDataTracker().get(NON_GENDER);
   }

   @Override
   public void setGenderMask(int mask) {
      PlayerEntity self = (PlayerEntity)(Object)this;
      self.getDataTracker().set(NON_GENDER, mask & 3);
   }
}
