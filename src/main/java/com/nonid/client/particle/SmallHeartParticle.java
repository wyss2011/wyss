package com.nonid.client.particle;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.particle.BillboardParticle.RenderType;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

public class SmallHeartParticle extends BillboardParticle {
   protected SmallHeartParticle(ClientWorld world, double x, double y, double z, Sprite sprite) {
      super(world, x, y, z, 0.0, 0.0, 0.0, sprite);
      this.ascending = true;
      this.velocityMultiplier = 0.86F;
      this.velocityX *= 0.01F;
      this.velocityY *= 0.01F;
      this.velocityZ *= 0.01F;
      this.velocityY += 0.1;
      this.scale = 0.145F + this.random.nextFloat() * 0.08F;
      this.maxAge = 16;
      this.collidesWithWorld = false;
   }

   public RenderType getRenderType() {
      return RenderType.PARTICLE_ATLAS_OPAQUE;
   }

   public float getSize(float tickProgress) {
      float life = (this.age + tickProgress) / this.maxAge;
      float base = this.scale * MathHelper.clamp(life * 32.0F, 0.0F, 1.0F);
      if (life > 0.8F) {
         float fade = MathHelper.clamp((life - 0.8F) / 0.2F, 0.0F, 1.0F);
         base *= MathHelper.lerp(fade, 1.0F, 0.8F);
      }

      return base;
   }

   public static final class Factory implements ParticleFactory<SimpleParticleType> {
      private final SpriteProvider spriteProvider;

      public Factory(SpriteProvider spriteProvider) {
         this.spriteProvider = spriteProvider;
      }

      public Particle createParticle(
         SimpleParticleType type, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random
      ) {
         return new SmallHeartParticle(world, x, y, z, this.spriteProvider.getSprite(random));
      }
   }
}
