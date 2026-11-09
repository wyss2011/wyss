package com.nonid.internal.animation.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class AnimationSoundCueParser {
   private AnimationSoundCueParser() {
   }

   public static AnimationSoundCueParser.ParsedAnimation parse(JsonObject animation, String sourceName, AnimationSoundCueParser.WarningSink warnings) {
      if (animation == null) {
         return new AnimationSoundCueParser.ParsedAnimation(-1.0, List.of());
      }

      double lengthSeconds = readAnimationLength(animation);
      JsonElement soundElement = animation.get("sound_effects");
      if (soundElement == null) {
         return new AnimationSoundCueParser.ParsedAnimation(lengthSeconds, List.of());
      }

      if (!soundElement.isJsonObject()) {
         warnings.warn("[NoN Animation] Skipping {}: 'sound_effects' must be an object", sourceName);
         return new AnimationSoundCueParser.ParsedAnimation(lengthSeconds, List.of());
      }

      List<AnimationSoundCueParser.ParsedCue> cues = new ArrayList<>();

      for (Entry<String, JsonElement> entry : soundElement.getAsJsonObject().entrySet()) {
         String timeKey = entry.getKey();
         Double timeSeconds = parseTime(timeKey);
         if (timeSeconds != null && !(timeSeconds < 0.0)) {
            collectCues(entry.getValue(), timeSeconds, sourceName, warnings, cues);
         } else {
            warnings.warn("[NoN Animation] Skipping {}: invalid sound time '{}'", sourceName, timeKey);
         }
      }

      return new AnimationSoundCueParser.ParsedAnimation(lengthSeconds, List.copyOf(cues));
   }

   private static void collectCues(
      JsonElement element, double timeSeconds, String sourceName, AnimationSoundCueParser.WarningSink warnings, List<AnimationSoundCueParser.ParsedCue> output
   ) {
      if (element != null && !element.isJsonNull() && element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         String effect = readString(object, "effect");
         if (effect != null && !effect.isBlank()) {
            float volume = readFloat(object, "volume", 1.0F);
            float pitch = readFloat(object, "pitch", 1.0F);
            collectCue(effect, timeSeconds, sourceName, warnings, output, volume, pitch);
            String sound = readString(object, "sound");
            if (sound != null && !sound.isBlank() && !sound.trim().equals(effect.trim())) {
               collectCue(sound, timeSeconds, sourceName, warnings, output, volume, pitch);
            }
         } else {
            warnMissingEffect(sourceName, timeSeconds, warnings);
         }
      } else {
         warnings.warn("[NoN Animation] Skipping {}: sound cue at {} must be an object", sourceName, String.format(Locale.ROOT, "%.3f", timeSeconds));
      }
   }

   private static void collectCue(
      String effect,
      double timeSeconds,
      String sourceName,
      AnimationSoundCueParser.WarningSink warnings,
      List<AnimationSoundCueParser.ParsedCue> output,
      float volume,
      float pitch
   ) {
      String normalizedEffect = effect.trim();
      if (normalizedEffect.startsWith("random(")) {
         List<Identifier> sounds = readInlineSoundPool(normalizedEffect, sourceName, timeSeconds, warnings);
         if (sounds != null) {
            output.add(new AnimationSoundCueParser.ParsedCue(timeSeconds, null, sounds, volume, pitch));
         }
      } else {
         output.add(new AnimationSoundCueParser.ParsedCue(timeSeconds, normalizedEffect, List.of(), volume, pitch));
      }
   }

   private static void warnMissingEffect(String sourceName, double timeSeconds, AnimationSoundCueParser.WarningSink warnings) {
      warnings.warn("[NoN Animation] Skipping {}: sound cue at {} missing 'effect'", sourceName, String.format(Locale.ROOT, "%.3f", timeSeconds));
   }

   @Nullable
   private static List<Identifier> readInlineSoundPool(String effect, String sourceName, double timeSeconds, AnimationSoundCueParser.WarningSink warnings) {
      String formattedTime = String.format(Locale.ROOT, "%.3f", timeSeconds);
      if (!effect.endsWith(")")) {
         warnings.warn("[NoN Animation] Skipping {}: malformed random sound pool at {}. Expected random(namespace:sound, ...)", sourceName, formattedTime);
         return null;
      }

      String contents = effect.substring("random(".length(), effect.length() - 1).trim();
      if (contents.isEmpty()) {
         warnings.warn("[NoN Animation] Skipping {}: random sound pool at {} cannot be empty", sourceName, formattedTime);
         return null;
      }

      List<Identifier> sounds = new ArrayList<>();

      for (String candidate : contents.split(",", -1)) {
         String raw = candidate.trim();
         Identifier soundId = raw.indexOf(58) > 0 ? Identifier.tryParse(raw) : null;
         if (soundId == null) {
            warnings.warn("[NoN Animation] Skipping {}: invalid or unqualified sound id '{}' in random sound pool at {}", sourceName, raw, formattedTime);
            return null;
         }

         sounds.add(soundId);
      }

      return List.copyOf(sounds);
   }

   private static double readAnimationLength(JsonObject animation) {
      Double animationLength = readFiniteDouble(animation.get("animation_length"));
      if (animationLength != null) {
         return animationLength;
      }

      Double length = readFiniteDouble(animation.get("length"));
      return length == null ? -1.0 : length;
   }

   @Nullable
   private static Double parseTime(String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : null;
         } catch (NumberFormatException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static String readString(JsonObject object, String key) {
      JsonElement value = object.get(key);
      return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
   }

   private static float readFloat(JsonObject object, String key, float fallback) {
      Double value = readFiniteDouble(object.get(key));
      return value == null ? fallback : value.floatValue();
   }

   @Nullable
   private static Double readFiniteDouble(JsonElement element) {
      if (element != null && element.isJsonPrimitive()) {
         JsonPrimitive primitive = element.getAsJsonPrimitive();
         if (primitive.isNumber()) {
            double value = primitive.getAsDouble();
            return Double.isFinite(value) ? value : null;
         } else {
            return !primitive.isString() ? null : parseTime(primitive.getAsString());
         }
      } else {
         return null;
      }
   }

   public record ParsedAnimation(double lengthSeconds, List<AnimationSoundCueParser.ParsedCue> cues) {
   }

   public record ParsedCue(double timeSeconds, @Nullable String effect, List<Identifier> randomSounds, float volume, float pitch) {
      public ParsedCue {
         randomSounds = randomSounds == null ? List.of() : List.copyOf(randomSounds);
      }

      public double timeTicks() {
         return this.timeSeconds * 20.0;
      }
   }

   @FunctionalInterface
   public interface WarningSink {
      void warn(String var1, Object... var2);
   }
}
