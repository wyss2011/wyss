package com.nonid.api.client;

import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NonAnimationParticleEvents {
   public static final Event<NonAnimationParticleEvents.@NotNull ParticleResolver> RESOLVE = EventFactory.createArrayBacked(
      NonAnimationParticleEvents.ParticleResolver.class, callbacks -> context -> {
         for (NonAnimationParticleEvents.ParticleResolver callback : callbacks) {
            NonAnimationParticleEvents.ParticleOverride override = callback.resolve(context);
            if (override != null) {
               return override;
            }
         }

         return null;
      }
   );

   private NonAnimationParticleEvents() {
   }

   public record ParticleContext(
      @Nullable Entity actor,
      UUID actorUuid,
      @Nullable Identifier animationId,
      @Nullable Identifier animationResource,
      @Nullable String animationKey,
      Identifier particleId,
      String locator,
      String script,
      Vec3d position,
      @Nullable UUID instanceId
   ) {
   }

   public record ParticleOverride(@Nullable ParticleEffect particle, boolean cancel) {
      public static NonAnimationParticleEvents.ParticleOverride spawn(ParticleEffect particle) {
         return new NonAnimationParticleEvents.ParticleOverride(particle, false);
      }

      public static NonAnimationParticleEvents.ParticleOverride skip() {
         return new NonAnimationParticleEvents.ParticleOverride(null, true);
      }
   }

   @FunctionalInterface
   public interface ParticleResolver {
      @Nullable
      NonAnimationParticleEvents.ParticleOverride resolve(NonAnimationParticleEvents.ParticleContext var1);
   }
}
