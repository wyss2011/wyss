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

public class RippedFabricParticle extends BillboardParticle {
   private static final int FRAMES = 5;
   private static final int TICKS_PER_FRAME = 4;
   private static SpriteProvider sprites;

   public RippedFabricParticle(
      ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Sprite sprite, int rgb, int maxAge, float scale
   ) {
      super(world, x, y, z, velocityX, velocityY, velocityZ, sprite);
      this.velocityMultiplier = 0.82F;
      this.gravityStrength = 0.1F;
      this.maxAge = Math.max(1, maxAge);
      this.scale = scale;
      this.collidesWithWorld = true;
      this.setBoundingBoxSpacing(0.01F, 0.01F);
      this.setColor((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
   }

   public static Particle create(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int rgb, Random random) {
      SpriteProvider provider = sprites;
      Sprite sprite = provider != null ? provider.getFirst() : null;
      if (sprite == null) {
         return null;
      }

      int maxAge = 100 + random.nextInt(50);
      float scale = 0.075F + random.nextFloat() * 0.045F;
      return new RippedFabricParticle(world, x, y, z, velocityX, velocityY, velocityZ, sprite, rgb, maxAge, scale);
   }

   public RenderType getRenderType() {
      return RenderType.PARTICLE_ATLAS_OPAQUE;
   }

   public void tick() {
      super.tick();
      if (this.onGround) {
         this.markDead();
      }
   }

   public float getSize(float tickProgress) {
      float life = (this.age + tickProgress) / this.maxAge;
      float size = this.scale * MathHelper.clamp(life * 10.0F, 0.0F, 1.0F);
      if (life > 0.75F) {
         size *= MathHelper.lerp(MathHelper.clamp((life - 0.75F) / 0.25F, 0.0F, 1.0F), 1.0F, 0.15F);
      }

      return size;
   }

   protected float getMinV() {
      float minV = this.sprite.getMinV();
      float maxV = this.sprite.getMaxV();
      float frameSize = (maxV - minV) / 5.0F;
      int frame = this.age / 4 % 5;
      return minV + frame * frameSize;
   }

   protected float getMaxV() {
      float minV = this.sprite.getMinV();
      float maxV = this.sprite.getMaxV();
      float frameSize = (maxV - minV) / 5.0F;
      int frame = this.age / 4 % 5;
      return minV + (frame + 1) * frameSize;
   }

   public static final class Factory implements ParticleFactory<SimpleParticleType> {
      public Factory(SpriteProvider spriteProvider) {
         RippedFabricParticle.sprites = spriteProvider;
      }

      public Particle createParticle(
         SimpleParticleType type, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random
      ) {
         return RippedFabricParticle.create(world, x, y, z, velocityX, velocityY, velocityZ, 15920872, random);
      }
   }
}
