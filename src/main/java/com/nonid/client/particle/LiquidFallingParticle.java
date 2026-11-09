package com.nonid.client.particle;

import com.nonid.NeedsOfNature;
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
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

public class LiquidFallingParticle extends LiquidLeakParticle {
   public LiquidFallingParticle(
      ClientWorld world, double x, double y, double z, Fluid fluid, Sprite sprite, int tintRgb, double velocityX, double velocityY, double velocityZ
   ) {
      super(world, x, y, z, fluid, sprite, tintRgb);
      this.maxAge = (int)(64.0 / (Math.random() * 0.8 + 0.2));
      this.velocityX = velocityX;
      this.velocityY = velocityY;
      this.velocityZ = velocityZ;
   }

   @Override
   protected void updateVelocity() {
      BlockPos pos = BlockPos.ofFloored(this.x, this.y, this.z);
      if (this.world.getFluidState(pos).isIn(FluidTags.WATER)) {
         this.markDead();
         double offsetX = (this.random.nextDouble() - 0.5) * 0.2;
         double offsetZ = (this.random.nextDouble() - 0.5) * 0.2;
         double offsetY = MathHelper.nextBetween(this.random, 0.01F, 0.04F);
         ParticleEffect waterParticle = NonParticles.liquidWater(this.tintRgb);
         this.world
            .addParticleClient(
               waterParticle, this.x + offsetX, this.y + offsetY, this.z + offsetZ, this.velocityX * 0.02, this.velocityY * 0.02, this.velocityZ * 0.02
            );
      } else {
         if (this.onGround) {
            SoundEvent sound = (SoundEvent)Registries.SOUND_EVENT.get(NeedsOfNature.id("wet04"));
            float pitch = MathHelper.nextBetween(this.random, 0.3F, 1.0F);
            this.world.playSoundClient(this.x, this.y, this.z, sound, SoundCategory.BLOCKS, 0.6F, pitch, false);
            this.markDead();
            double offsetX = (this.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (this.random.nextDouble() - 0.5) * 0.3;
            double offsetY = MathHelper.nextBetween(this.random, 0.005F, 0.02F);
            ParticleEffect puddleParticle = NonParticles.liquidPuddle(this.tintRgb);
            this.world.addParticleClient(puddleParticle, this.x + offsetX, this.y + offsetY, this.z + offsetZ, 0.0, 0.0, 0.0);
         }
      }
   }

   public static final class Factory implements ParticleFactory<TintedParticleEffect> {
      private final SpriteProvider spriteProvider;

      public Factory(SpriteProvider spriteProvider) {
         this.spriteProvider = spriteProvider;
      }

      public Particle createParticle(
         TintedParticleEffect effect, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random
      ) {
         return new LiquidFallingParticle(
            world, x, y, z, Fluids.LAVA, this.spriteProvider.getSprite(random), LiquidLeakParticle.rgbFrom(effect), velocityX, velocityY, velocityZ
         );
      }
   }
}
