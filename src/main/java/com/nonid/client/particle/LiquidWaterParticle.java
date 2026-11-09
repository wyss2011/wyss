package com.nonid.client.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.particle.BillboardParticle.RenderType;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluids;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.util.math.random.Random;

public class LiquidWaterParticle extends LiquidLeakParticle {
   private static final int WATER_LIFE_TICKS = 100;
   private static final float WATER_GRAVITY = 0.0018F;
   private static final double WATER_DAMPING = 0.6;
   private static final int WATER_FRAMES = 5;

   public LiquidWaterParticle(
      ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Sprite sprite, int maxAge, int tintRgb
   ) {
      super(world, x, y, z, Fluids.EMPTY, sprite, tintRgb);
      this.maxAge = Math.max(1, maxAge);
      this.gravityStrength = 0.0018F;
      this.velocityX = velocityX;
      this.velocityY = velocityY;
      this.velocityZ = velocityZ;
   }

   @Override
   public RenderType getRenderType() {
      return RenderType.PARTICLE_ATLAS_OPAQUE;
   }

   @Override
   public void tick() {
      this.lastX = this.x;
      this.lastY = this.y;
      this.lastZ = this.z;
      if (this.age++ >= this.maxAge) {
         this.markDead();
      } else {
         this.velocityY = this.velocityY - this.gravityStrength;
         this.move(this.velocityX, this.velocityY, this.velocityZ);
         this.velocityX *= 0.6;
         this.velocityY *= 0.6;
         this.velocityZ *= 0.6;
      }
   }

   protected float getMinV() {
      float minV = this.sprite.getMinV();
      float maxV = this.sprite.getMaxV();
      float frameSize = (maxV - minV) / 5.0F;
      int frame = Math.min(4, (int)((float)this.age / this.maxAge * 5.0F));
      return minV + frame * frameSize;
   }

   protected float getMaxV() {
      float minV = this.sprite.getMinV();
      float maxV = this.sprite.getMaxV();
      float frameSize = (maxV - minV) / 5.0F;
      int frame = Math.min(4, (int)((float)this.age / this.maxAge * 5.0F));
      return minV + (frame + 1) * frameSize;
   }

   public static final class Factory implements ParticleFactory<TintedParticleEffect> {
      private final SpriteProvider spriteProvider;

      public Factory(SpriteProvider spriteProvider) {
         this.spriteProvider = spriteProvider;
      }

      public Particle createParticle(
         TintedParticleEffect effect, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random
      ) {
         int variance = random.nextInt(21) - 10;
         int age = 100 + variance;
         return new LiquidWaterParticle(
            world, x, y, z, velocityX, velocityY, velocityZ, this.spriteProvider.getSprite(random), age, LiquidLeakParticle.rgbFrom(effect)
         );
      }
   }
}
