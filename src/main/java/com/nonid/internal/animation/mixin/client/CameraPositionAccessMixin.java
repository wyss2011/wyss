package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.camera.CameraPositionAccess;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Camera.class)
public abstract class CameraPositionAccessMixin implements CameraPositionAccess {
   @Shadow
   private Vec3d pos;

   @Unique
   @Override
   public Vec3d afw$getPos() {
      return this.pos;
   }
}
