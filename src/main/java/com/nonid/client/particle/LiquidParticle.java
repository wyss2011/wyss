package com.nonid.client.particle;

import com.nonid.particle.NonParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.util.math.random.Random;

public class LiquidParticle extends LiquidLeakParticle {
   private static final float GRAVITY_SCALE = 0.02F;
   private static final int MAX_AGE_TICKS = 20;
   private static final double VELOCITY_SCALE = 0.02;
   private final ParticleEffect nextParticle;

   public LiquidParticle(ClientWorld world, double x, double y, double z, Fluid fluid, ParticleEffect nextParticle, Sprite sprite, int tintRgb) {
      super(world, x, y, z, fluid, sprite, tintRgb);
      this.nextParticle = nextParticle;
      this.gravityStrength *= 0.02F;
      this.maxAge = 20;
   }

   @Override
   protected void updateAge() {
      if (--this.maxAge <= 0) {
         this.markDead();
         this.world.addParticleClient(this.nextParticle, this.x, this.y, this.z, this.velocityX, this.velocityY, this.velocityZ);
      }
   }

   @Override
   protected void updateVelocity() {
      this.velocityX *= 0.02;
      this.velocityY *= 0.02;
      this.velocityZ *= 0.02;
   }

   public static final class Factory implements ParticleFactory<TintedParticleEffect> {
      private final SpriteProvider spriteProvider;

      public Factory(SpriteProvider spriteProvider) {
         this.spriteProvider = spriteProvider;
      }

      public Particle createParticle(
         TintedParticleEffect effect, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random
      ) {
         int tintRgb = LiquidLeakParticle.rgbFrom(effect);
         return new LiquidParticle(world, x, y, z, Fluids.LAVA, NonParticles.liquidFalling(tintRgb), this.spriteProvider.getSprite(random), tintRgb);
      }
   }
}
