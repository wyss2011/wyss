package com.nonid.mixin;

import com.nonid.GenderHolder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.data.DataTracker.Builder;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntityGenderMixin extends LivingEntity implements GenderHolder {
   @Unique
   private static final TrackedData<Integer> NON_GENDER = DataTracker.registerData(MobEntity.class, TrackedDataHandlerRegistry.INTEGER);

   protected MobEntityGenderMixin(EntityType<? extends LivingEntity> entityType, World world) {
      super(entityType, world);
   }

   @Inject(method = "initDataTracker", at = @At("TAIL"))
   private void needsOfNature$initGenderTracker(Builder builder, CallbackInfo ci) {
      builder.add(NON_GENDER, 0);
   }

   @Override
   public int getGenderMask() {
      return (Integer)this.dataTracker.get(NON_GENDER);
   }

   @Override
   public void setGenderMask(int mask) {
      this.dataTracker.set(NON_GENDER, mask & 3);
   }
}
