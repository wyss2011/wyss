package com.nonid.mixin;

import com.nonid.DestroyedSkinHolder;
import com.nonid.MessHolder;
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
public abstract class PlayerEntityDestroyedSkinMixin implements DestroyedSkinHolder, MessHolder {
   @Unique
   private static final TrackedData<Integer> NON_DESTROYED_SKIN_STAGE = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
   @Unique
   private static final TrackedData<Integer> NON_DESTROYED_SKIN_DAMAGE = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
   @Unique
   private static final TrackedData<Integer> NON_MESS_V = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
   @Unique
   private static final TrackedData<Integer> NON_MESS_A = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
   @Unique
   private static final TrackedData<Integer> NON_MESS_M = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);

   @Inject(method = "initDataTracker", at = @At("TAIL"))
   private void non$initDestroyedSkinTracker(Builder builder, CallbackInfo ci) {
      builder.add(NON_DESTROYED_SKIN_STAGE, 0);
      builder.add(NON_DESTROYED_SKIN_DAMAGE, 0);
      builder.add(NON_MESS_V, 0);
      builder.add(NON_MESS_A, 0);
      builder.add(NON_MESS_M, 0);
   }

   @Override
   public int getDestroyedSkinStage() {
      PlayerEntity self = (PlayerEntity)(Object)this;
      return (Integer)self.getDataTracker().get(NON_DESTROYED_SKIN_STAGE);
   }

   @Override
   public void setDestroyedSkinStage(int stage) {
      PlayerEntity self = (PlayerEntity)(Object)this;
      self.getDataTracker().set(NON_DESTROYED_SKIN_STAGE, Math.max(0, Math.min(4, stage)));
   }

   @Override
   public int getDestroyedSkinDamage() {
      PlayerEntity self = (PlayerEntity)(Object)this;
      return (Integer)self.getDataTracker().get(NON_DESTROYED_SKIN_DAMAGE);
   }

   @Override
   public void setDestroyedSkinDamage(int damage) {
      PlayerEntity self = (PlayerEntity)(Object)this;
      self.getDataTracker().set(NON_DESTROYED_SKIN_DAMAGE, Math.max(0, Math.min(10, damage)));
   }

   @Override
   public int getVMess() {
      PlayerEntity self = (PlayerEntity)(Object)this;
      return (Integer)self.getDataTracker().get(NON_MESS_V);
   }

   @Override
   public void setVMess(int value) {
      PlayerEntity self = (PlayerEntity)(Object)this;
      self.getDataTracker().set(NON_MESS_V, clampMess(value));
   }

   @Override
   public int getAMess() {
      PlayerEntity self = (PlayerEntity)(Object)this;
      return (Integer)self.getDataTracker().get(NON_MESS_A);
   }

   @Override
   public void setAMess(int value) {
      PlayerEntity self = (PlayerEntity)(Object)this;
      self.getDataTracker().set(NON_MESS_A, clampMess(value));
   }

   @Override
   public int getMMess() {
      PlayerEntity self = (PlayerEntity)(Object)this;
      return (Integer)self.getDataTracker().get(NON_MESS_M);
   }

   @Override
   public void setMMess(int value) {
      PlayerEntity self = (PlayerEntity)(Object)this;
      self.getDataTracker().set(NON_MESS_M, clampMess(value));
   }

   @Unique
   private static int clampMess(int value) {
      return Math.max(0, Math.min(10, value));
   }
}
