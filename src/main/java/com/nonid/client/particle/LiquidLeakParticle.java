package com.nonid.client.particle;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.BillboardParticle.RenderType;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.util.math.BlockPos;

abstract class LiquidLeakParticle extends BillboardParticle {
   protected static final float BASE_GRAVITY = 0.06F;
   protected static final double DAMPING = 0.98;
   protected final Fluid fluid;
   protected final int tintRgb;

   protected LiquidLeakParticle(ClientWorld world, double x, double y, double z, Fluid fluid, Sprite sprite, int tintRgb) {
      super(world, x, y, z, sprite);
      this.fluid = fluid;
      this.tintRgb = tintRgb & 16777215;
      this.setBoundingBoxSpacing(0.01F, 0.01F);
      this.gravityStrength = 0.06F;
      this.applyColor(this.tintRgb);
   }

   public RenderType getRenderType() {
      return RenderType.PARTICLE_ATLAS_OPAQUE;
   }

   public void tick() {
      this.lastX = this.x;
      this.lastY = this.y;
      this.lastZ = this.z;
      this.updateAge();
      if (!this.dead) {
         this.velocityY = this.velocityY - this.gravityStrength;
         this.move(this.velocityX, this.velocityY, this.velocityZ);
         this.updateVelocity();
         if (!this.dead) {
            this.velocityX *= 0.98;
            this.velocityY *= 0.98;
            this.velocityZ *= 0.98;
            if (this.fluid != Fluids.EMPTY) {
               BlockPos pos = BlockPos.ofFloored(this.x, this.y, this.z);
               FluidState fluidState = this.world.getFluidState(pos);
               if (fluidState.getFluid() == this.fluid) {
                  double surface = pos.getY() + fluidState.getHeight(this.world, pos);
                  if (this.y < surface) {
                     this.markDead();
                  }
               }
            }
         }
      }
   }

   protected void updateAge() {
      if (--this.maxAge <= 0) {
         this.markDead();
      }
   }

   protected void updateVelocity() {
   }

   private void applyColor(int rgb) {
      float r = (rgb >> 16 & 0xFF) / 255.0F;
      float g = (rgb >> 8 & 0xFF) / 255.0F;
      float b = (rgb & 0xFF) / 255.0F;
      this.setColor(r, g, b);
   }

   static int rgbFrom(TintedParticleEffect effect) {
      int red = Math.max(0, Math.min(255, Math.round(effect.getRed() * 255.0F)));
      int green = Math.max(0, Math.min(255, Math.round(effect.getGreen() * 255.0F)));
      int blue = Math.max(0, Math.min(255, Math.round(effect.getBlue() * 255.0F)));
      return red << 16 | green << 8 | blue;
   }
}
