package com.nonid.client.particle;

import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.particle.BillboardParticle.RenderType;
import net.minecraft.client.particle.BillboardParticle.Rotator;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

public class LiquidPuddleParticle extends BillboardParticle {
   private static final int DEFAULT_MAX_AGE_TICKS = 800;
   private static final int MAX_FADE_TICKS = 200;
   private static final Rotator PUDDLE_ROTATOR = (quaternion, camera, tickDelta) -> quaternion.rotationXYZ((float) (-Math.PI / 2), 0.0F, 0.0F);
   private final int holdTicks;
   private final int fadeTicks;

   public LiquidPuddleParticle(ClientWorld world, double x, double y, double z, Sprite sprite, float scaleFactor, int tintRgb) {
      super(world, x, y, z, sprite);
      this.maxAge = resolvePuddleMaxAgeTicks();
      this.fadeTicks = Math.min(200, this.maxAge);
      this.holdTicks = Math.max(0, this.maxAge - this.fadeTicks);
      this.gravityStrength = 0.0F;
      this.velocityX = 0.0;
      this.velocityY = 0.0;
      this.velocityZ = 0.0;
      float sizeJitter = MathHelper.nextBetween(this.random, 0.8F, 1.0F);
      this.scale = 0.5F * sizeJitter * Math.max(1.0F, scaleFactor);
      this.alpha = 1.0F;
      this.applyColor(tintRgb);
   }

   public Rotator getRotator() {
      return PUDDLE_ROTATOR;
   }

   protected RenderType getRenderType() {
      return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
   }

   public void tick() {
      this.lastX = this.x;
      this.lastY = this.y;
      this.lastZ = this.z;
      if (!this.hasSupportBlock()) {
         this.markDead();
      } else if (++this.age >= this.maxAge) {
         this.markDead();
      } else {
         if (this.age > this.holdTicks && this.fadeTicks > 0) {
            float fade = (float)(this.age - this.holdTicks) / this.fadeTicks;
            this.alpha = MathHelper.clamp(1.0F - fade, 0.0F, 1.0F);
         } else {
            this.alpha = 1.0F;
         }
      }
   }

   private static int resolvePuddleMaxAgeTicks() {
      NonConfig config = NeedsOfNature.getConfig();
      return config == null ? 800 : Math.max(1, config.getLiquidPuddleDespawnSeconds() * 20);
   }

   private boolean hasSupportBlock() {
      BlockPos supportPos = BlockPos.ofFloored(this.x, this.y - 0.05, this.z);
      return !this.world.getBlockState(supportPos).getCollisionShape(this.world, supportPos).isEmpty();
   }

   private void applyColor(int rgb) {
      float r = (rgb >> 16 & 0xFF) / 255.0F;
      float g = (rgb >> 8 & 0xFF) / 255.0F;
      float b = (rgb & 0xFF) / 255.0F;
      this.setColor(r, g, b);
   }

   public static final class Factory implements ParticleFactory<TintedParticleEffect> {
      private final SpriteProvider spriteProvider;

      public Factory(SpriteProvider spriteProvider) {
         this.spriteProvider = spriteProvider;
      }

      public Particle createParticle(
         TintedParticleEffect effect, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random
      ) {
         return new LiquidPuddleParticle(world, x, y, z, this.spriteProvider.getSprite(random), 1.0F, LiquidLeakParticle.rgbFrom(effect));
      }
   }
}
