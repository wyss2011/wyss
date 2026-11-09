package com.nonid.api.client;

import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NonAnimationSoundEvents {
   public static final Event<NonAnimationSoundEvents.@NotNull SoundResolver> RESOLVE = EventFactory.createArrayBacked(
      NonAnimationSoundEvents.SoundResolver.class, callbacks -> context -> {
         for (NonAnimationSoundEvents.SoundResolver callback : callbacks) {
            NonAnimationSoundEvents.SoundOverride override = callback.resolve(context);
            if (override != null) {
               return override;
            }
         }

         return null;
      }
   );

   private NonAnimationSoundEvents() {
   }

   public record SoundContext(
      LivingEntity anchor,
      Identifier animationId,
      Identifier animationResource,
      String animationKey,
      String effect,
      double timeSeconds,
      float volume,
      float pitch,
      UUID instanceId,
      int stageIndex,
      boolean loop,
      double cueTimeTicks,
      double cycleTicks,
      double animationSpeed,
      double nextSameEffectCueDelayTicks,
      double nextSameEffectCueDelayMs
   ) {
   }

   public record SoundOverride(@Nullable Identifier soundId, float volume, float pitch, boolean cancel) {
      public static NonAnimationSoundEvents.SoundOverride play(Identifier soundId, float volume, float pitch) {
         return new NonAnimationSoundEvents.SoundOverride(soundId, volume, pitch, false);
      }

      public static NonAnimationSoundEvents.SoundOverride play(Identifier soundId) {
         return new NonAnimationSoundEvents.SoundOverride(soundId, 1.0F, 1.0F, false);
      }

      public static NonAnimationSoundEvents.SoundOverride skip() {
         return new NonAnimationSoundEvents.SoundOverride(null, 1.0F, 1.0F, true);
      }
   }

   @FunctionalInterface
   public interface SoundResolver {
      @Nullable
      NonAnimationSoundEvents.SoundOverride resolve(NonAnimationSoundEvents.SoundContext var1);
   }
}
