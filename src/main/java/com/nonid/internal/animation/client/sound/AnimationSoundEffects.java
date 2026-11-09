package com.nonid.internal.animation.client.sound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nonid.internal.animation.AnimationEngine;
import com.nonid.internal.animation.AnimationEngineClient;
import com.nonid.internal.animation.data.AnimationSoundCueParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;

public final class AnimationSoundEffects {
   private static volatile Map<Identifier, Map<String, AnimationSoundEffects.SoundAnimation>> SOUND_BY_ANIMATION = Map.of();
   private static final Identifier RELOADER_ID = AnimationEngine.runtimeId("sound_effects");

   private AnimationSoundEffects() {
   }

   public static void registerReloadListener() {
      ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(RELOADER_ID, new AnimationSoundEffects.Reloader());
   }

   public static boolean hasAny() {
      return !SOUND_BY_ANIMATION.isEmpty();
   }

   public static AnimationSoundEffects.SoundAnimation getSoundAnimation(Identifier animationResource, String animationKey) {
      if (animationResource != null && animationKey != null && !animationKey.isBlank()) {
         Map<String, AnimationSoundEffects.SoundAnimation> byKey = SOUND_BY_ANIMATION.get(animationResource);
         return byKey == null ? null : byKey.get(animationKey);
      } else {
         return null;
      }
   }

   private static void reload(ResourceManager manager) {
      Map<Identifier, Resource> resources = manager.findResources("geckolib/animations", id -> id.getPath().endsWith(".animation.json"));
      List<Entry<Identifier, Resource>> entries = new ArrayList<>(resources.entrySet());
      entries.sort(Comparator.comparing(ex -> ((Identifier)ex.getKey()).toString()));
      Map<Identifier, Map<String, AnimationSoundEffects.SoundAnimation>> loaded = new LinkedHashMap<>();
      int cueCount = 0;

      for (Entry<Identifier, Resource> entry : entries) {
         Identifier fileId = entry.getKey();
         Identifier animationId = animationIdFromPath(fileId);
         if (animationId != null) {
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
               JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
               JsonObject animationsObj = root.has("animations") && root.get("animations").isJsonObject() ? root.getAsJsonObject("animations") : null;
               if (animationsObj != null) {
                  for (Entry<String, JsonElement> animEntry : animationsObj.entrySet()) {
                     String animKey = animEntry.getKey();
                     JsonElement animEl = animEntry.getValue();
                     if (animKey != null && !animKey.isBlank() && animEl != null && animEl.isJsonObject()) {
                        JsonObject animObj = animEl.getAsJsonObject();
                        AnimationSoundCueParser.ParsedAnimation parsed = AnimationSoundCueParser.parse(
                           animObj, fileId.toString(), AnimationSoundEffects::setupWarn
                        );
                        List<AnimationSoundEffects.SoundCue> cues = parsed.cues()
                           .stream()
                           .map(
                              cue -> new AnimationSoundEffects.SoundCue(
                                 cue.timeSeconds(), cue.timeTicks(), cue.effect(), cue.randomSounds(), cue.volume(), cue.pitch()
                              )
                           )
                           .collect(Collectors.toCollection(ArrayList::new));
                        if (!cues.isEmpty()) {
                           cues.sort(Comparator.comparingDouble(AnimationSoundEffects.SoundCue::timeTicks));
                           AnimationSoundEffects.SoundAnimation soundAnim = new AnimationSoundEffects.SoundAnimation(parsed.lengthSeconds(), List.copyOf(cues));
                           loaded.computeIfAbsent(animationId, id -> new LinkedHashMap<>()).put(animKey, soundAnim);
                           cueCount += cues.size();
                        }
                     }
                  }
               }
            } catch (IOException | RuntimeException e) {
               setupError("[NoN Animation] Failed to load sound cues {}", fileId, e);
            }
         }
      }

      Map<Identifier, Map<String, AnimationSoundEffects.SoundAnimation>> finalized = new LinkedHashMap<>();

      for (Entry<Identifier, Map<String, AnimationSoundEffects.SoundAnimation>> entry : loaded.entrySet()) {
         finalized.put(entry.getKey(), Map.copyOf(entry.getValue()));
      }

      SOUND_BY_ANIMATION = Map.copyOf(finalized);
      AnimationEngine.LOGGER.info("[NoN Animation] Loaded {} sound cue animation(s), {} cue(s)", SOUND_BY_ANIMATION.size(), cueCount);
   }

   private static Identifier animationIdFromPath(Identifier fileId) {
      String path = fileId.getPath();
      String prefix = "geckolib/animations/";
      String suffix = ".animation.json";
      if (path.startsWith(prefix) && path.endsWith(suffix)) {
         String animPath = path.substring(prefix.length(), path.length() - suffix.length());
         return Identifier.of(fileId.getNamespace(), animPath);
      } else {
         return null;
      }
   }

   private static void setupWarn(String template, Object... args) {
      AnimationEngine.LOGGER.warn(template, args);
      AnimationEngineClient.sendClientSetupWarning(AnimationEngine.formatLogTemplate(template, args));
   }

   private static void setupError(String template, Object... args) {
      AnimationEngine.LOGGER.error(template, args);
      AnimationEngineClient.sendClientSetupWarning(AnimationEngine.formatLogTemplate(template, args));
   }

   public static final class Reloader implements SynchronousResourceReloader {
      public void reload(ResourceManager manager) {
         AnimationSoundEffects.reload(manager);
      }
   }

   public record SoundAnimation(double lengthSeconds, List<AnimationSoundEffects.SoundCue> cues) {
      public double lengthTicks() {
         return this.lengthSeconds <= 0.0 ? 0.0 : this.lengthSeconds * 20.0;
      }
   }

   public record SoundCue(double timeSeconds, double timeTicks, String effect, List<Identifier> randomSounds, float volume, float pitch) {
      public SoundCue {
         randomSounds = randomSounds == null ? List.of() : List.copyOf(randomSounds);
      }
   }
}
