package com.nonid.mixin;

import com.nonid.DestroyedSkinHolder;
import com.nonid.GenderHolder;
import com.nonid.MessHolder;
import net.minecraft.entity.Entity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityGenderPersistenceMixin {
   @Inject(method = "writeData", at = @At("TAIL"))
   private void needsOfNature$writeGender(WriteView storage, CallbackInfo ci) {
      if (this instanceof GenderHolder holder) {
         storage.putInt("NeedsOfNatureGender", holder.getGenderMask());
      }

      if (this instanceof DestroyedSkinHolder holder) {
         storage.putInt("NeedsOfNatureDestroyedSkinStage", holder.getDestroyedSkinStage());
         storage.putInt("NeedsOfNatureDestroyedSkinDamage", holder.getDestroyedSkinDamage());
      }

      if (this instanceof MessHolder holder) {
         storage.putInt("NeedsOfNatureMessV", holder.getVMess());
         storage.putInt("NeedsOfNatureMessA", holder.getAMess());
         storage.putInt("NeedsOfNatureMessM", holder.getMMess());
      }
   }

   @Inject(method = "readData", at = @At("TAIL"))
   private void needsOfNature$readGender(ReadView storage, CallbackInfo ci) {
      if (this instanceof GenderHolder holder) {
         storage.getOptionalInt("NeedsOfNatureGender").ifPresent(holder::setGenderMask);
      }

      if (this instanceof DestroyedSkinHolder holder) {
         storage.getOptionalInt("NeedsOfNatureDestroyedSkinStage").ifPresent(holder::setDestroyedSkinStage);
         storage.getOptionalInt("NeedsOfNatureDestroyedSkinDamage").ifPresent(holder::setDestroyedSkinDamage);
      }

      if (this instanceof MessHolder holder) {
         storage.getOptionalInt("NeedsOfNatureMessV").ifPresent(holder::setVMess);
         storage.getOptionalInt("NeedsOfNatureMessA").ifPresent(holder::setAMess);
         storage.getOptionalInt("NeedsOfNatureMessM").ifPresent(holder::setMMess);
      }
   }
}
