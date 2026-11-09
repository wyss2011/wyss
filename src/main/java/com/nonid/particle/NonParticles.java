package com.nonid.particle;

import com.nonid.NeedsOfNature;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonParticles {
   public static final Identifier LIQUID_PARTICLE_ID = NeedsOfNature.id("liquid_particle");
   public static final Identifier LIQUID_PARTICLE_FALLING_ID = NeedsOfNature.id("liquid_particle_falling");
   public static final Identifier LIQUID_PARTICLE_PUDDLE_ID = NeedsOfNature.id("liquid_particle_puddle");
   public static final Identifier LIQUID_PARTICLE_WATER_ID = NeedsOfNature.id("liquid_particle_water");
   public static final ParticleType<TintedParticleEffect> LIQUID_PARTICLE = registerTinted(LIQUID_PARTICLE_ID);
   public static final ParticleType<TintedParticleEffect> LIQUID_PARTICLE_FALLING = registerTinted(LIQUID_PARTICLE_FALLING_ID);
   public static final ParticleType<TintedParticleEffect> LIQUID_PARTICLE_PUDDLE = registerTinted(LIQUID_PARTICLE_PUDDLE_ID);
   public static final ParticleType<TintedParticleEffect> LIQUID_PARTICLE_WATER = registerTinted(LIQUID_PARTICLE_WATER_ID);
   public static final SimpleParticleType SMALLHEART = register(NeedsOfNature.id("smallheart"));
   public static final SimpleParticleType RIPPED_FABRIC = register(NeedsOfNature.id("ripped_fabric"));

   private NonParticles() {
   }

   public static void registerAll() {
   }

   public static boolean isLiquidParticleId(@Nullable Identifier id) {
      return LIQUID_PARTICLE_ID.equals(id)
         || LIQUID_PARTICLE_FALLING_ID.equals(id)
         || LIQUID_PARTICLE_PUDDLE_ID.equals(id)
         || LIQUID_PARTICLE_WATER_ID.equals(id);
   }

   @Nullable
   public static ParticleEffect createLiquidEffect(@Nullable Identifier id, int rgb) {
      if (LIQUID_PARTICLE_ID.equals(id)) {
         return liquid(rgb);
      } else if (LIQUID_PARTICLE_FALLING_ID.equals(id)) {
         return liquidFalling(rgb);
      } else if (LIQUID_PARTICLE_PUDDLE_ID.equals(id)) {
         return liquidPuddle(rgb);
      } else {
         return LIQUID_PARTICLE_WATER_ID.equals(id) ? liquidWater(rgb) : null;
      }
   }

   public static TintedParticleEffect liquid(int rgb) {
      return tinted(LIQUID_PARTICLE, rgb);
   }

   public static TintedParticleEffect liquidFalling(int rgb) {
      return tinted(LIQUID_PARTICLE_FALLING, rgb);
   }

   public static TintedParticleEffect liquidPuddle(int rgb) {
      return tinted(LIQUID_PARTICLE_PUDDLE, rgb);
   }

   public static TintedParticleEffect liquidWater(int rgb) {
      return tinted(LIQUID_PARTICLE_WATER, rgb);
   }

   private static TintedParticleEffect tinted(ParticleType<TintedParticleEffect> type, int rgb) {
      return TintedParticleEffect.create(type, 0xFF000000 | rgb & 16777215);
   }

   private static SimpleParticleType register(Identifier id) {
      return (SimpleParticleType)Registry.register(Registries.PARTICLE_TYPE, id, FabricParticleTypes.simple());
   }

   private static ParticleType<TintedParticleEffect> registerTinted(Identifier id) {
      return (ParticleType<TintedParticleEffect>)Registry.register(
         Registries.PARTICLE_TYPE, id, FabricParticleTypes.complex(TintedParticleEffect::createCodec, TintedParticleEffect::createPacketCodec)
      );
   }
}
