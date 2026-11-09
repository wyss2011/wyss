package com.nonid.internal.animation.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import com.nonid.internal.animation.AnimationEngine;
import com.nonid.internal.animation.util.AnimationIds;
import com.nonid.pack.NonExternalPackCatalog;
import com.nonid.pack.NonExternalPackProvider;
import com.nonid.pack.NonPackRootResolver;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class AnimationCueIndex {
   public static final String BIRTH_EFFECT = "birth";
   public static final String REACTIVE_IMPACT_EFFECT = "reactiveimpact";
   private static final String ANIMATION_SUFFIX = ".animation.json";
   private static final String ANIMATION_PATH_PREFIX = "geckolib/animations/afw/";
   private static volatile Map<Identifier, AnimationCueIndex.CueProfile> PROFILE_BY_CLIP = Map.of();

   private AnimationCueIndex() {
   }

   public static void registerReloadListener() {
      ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(AnimationCueIndex.Reloader.ID, new AnimationCueIndex.Reloader());
   }

   @Nullable
   public static AnimationCueIndex.CueProfile getProfile(@Nullable Identifier clipId) {
      return clipId == null ? null : PROFILE_BY_CLIP.get(clipId);
   }

   public static List<Integer> getCueTicks(@Nullable Identifier clipId, @Nullable String effect) {
      AnimationCueIndex.CueProfile profile = getProfile(clipId);
      return profile == null ? List.of() : profile.cueTicks(effect);
   }

   @Nullable
   public static Integer getFirstCueTick(@Nullable Identifier clipId, @Nullable String effect) {
      AnimationCueIndex.CueProfile profile = getProfile(clipId);
      return profile == null ? null : profile.firstCueTick(effect);
   }

   private static void reload() {
      Map<String, List<AnimationCueIndex.ParsedClip>> selectedFiles = new LinkedHashMap<>();
      scanInstalledMods(selectedFiles);
      scanExternalPacks(selectedFiles);
      Map<Identifier, AnimationCueIndex.MutableProfile> merged = new LinkedHashMap<>();

      for (List<AnimationCueIndex.ParsedClip> clips : selectedFiles.values()) {
         for (AnimationCueIndex.ParsedClip clip : clips) {
            if (clip != null && clip.clipId() != null) {
               AnimationCueIndex.MutableProfile profile = merged.computeIfAbsent(clip.clipId(), ignored -> new AnimationCueIndex.MutableProfile());

               for (Entry<String, List<Integer>> cues : clip.cueTicksByEffect().entrySet()) {
                  profile.cueTicksByEffect.computeIfAbsent(cues.getKey(), ignored -> new TreeSet<>()).addAll(cues.getValue());
               }

               Integer cycleTicks = clip.cycleTicks();
               if (cycleTicks != null && cycleTicks > 0 && (profile.cycleTicks == null || cycleTicks > profile.cycleTicks)) {
                  profile.cycleTicks = cycleTicks;
               }
            }
         }
      }

      Map<Identifier, AnimationCueIndex.CueProfile> finalized = new LinkedHashMap<>();

      for (Entry<Identifier, AnimationCueIndex.MutableProfile> entry : merged.entrySet()) {
         Map<String, List<Integer>> cuesByEffect = new LinkedHashMap<>();

         for (Entry<String, TreeSet<Integer>> cues : entry.getValue().cueTicksByEffect.entrySet()) {
            if (!cues.getValue().isEmpty()) {
               cuesByEffect.put(cues.getKey(), List.copyOf(cues.getValue()));
            }
         }

         Integer cycleTicks = entry.getValue().cycleTicks;
         if (!cuesByEffect.isEmpty() || cycleTicks != null) {
            finalized.put(entry.getKey(), new AnimationCueIndex.CueProfile(Map.copyOf(cuesByEffect), cycleTicks));
         }
      }

      PROFILE_BY_CLIP = Map.copyOf(finalized);
   }

   private static void scanInstalledMods(Map<String, List<AnimationCueIndex.ParsedClip>> selectedFiles) {
      for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
         for (Path root : mod.getRootPaths()) {
            Path assetsRoot = root.resolve("assets");
            if (Files.isDirectory(assetsRoot)) {
               loadAssetNamespaces(assetsRoot, selectedFiles);
            }
         }
      }
   }

   private static void scanExternalPacks(Map<String, List<AnimationCueIndex.ParsedClip>> selectedFiles) {
      Path packsDir = NonExternalPackProvider.resolveDefaultPacksDir();
      if (Files.isDirectory(packsDir)) {
         NonConfig config = NeedsOfNature.getConfig();
         List<String> configuredOrder = config == null ? List.of() : config.getExternalNoNPackLoadOrder();

         for (NonExternalPackCatalog.Entry entry : NonExternalPackCatalog.resolveLowToHigh(packsDir, configuredOrder)) {
            scanExternalPack(entry.root(), selectedFiles);
         }
      }
   }

   private static void scanExternalPack(NonPackRootResolver.ResolvedRoot root, Map<String, List<AnimationCueIndex.ParsedClip>> selectedFiles) {
      if (!root.isZip()) {
         Path assetsRoot = root.rootPath().resolve("assets");
         if (Files.isDirectory(assetsRoot)) {
            loadAssetNamespaces(assetsRoot, selectedFiles);
         }
      } else {
         try (ZipFile zip = new ZipFile(root.sourcePath().toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
               ZipEntry entry = entries.nextElement();
               if (entry != null && !entry.isDirectory()) {
                  String entryPath = root.stripZipPrefix(entry.getName());
                  if (entryPath != null && !entryPath.isBlank()) {
                     AnimationCueIndex.ParsedPath parsedPath = parseZipAssetPath(entryPath.replace('\\', '/'));
                     if (parsedPath != null && isAnimationAssetPath(parsedPath.namespaceRelativePath())) {
                        try (InputStream input = zip.getInputStream(entry)) {
                           selectedFiles.put(
                              parsedPath.resourceKey(),
                              parseAnimationFile(input, parsedPath.namespace(), parsedPath.namespaceRelativePath(), root.sourcePath() + "!" + entryPath)
                           );
                        } catch (IOException | RuntimeException e) {
                           NeedsOfNature.LOGGER.debug("[NoN] Failed to parse animation cues in {} entry {}", new Object[]{root.sourcePath(), entryPath, e});
                           selectedFiles.put(parsedPath.resourceKey(), List.of());
                        }
                     }
                  }
               }
            }
         } catch (IOException e) {
            NeedsOfNature.logSetupWarning("[NoN] Failed to scan external NoN pack for animation cues {}", root.sourcePath(), e);
         }
      }
   }

   private static void loadAssetNamespaces(Path assetsRoot, Map<String, List<AnimationCueIndex.ParsedClip>> selectedFiles) {
      try (Stream<Path> namespaces = Files.list(assetsRoot)) {
         namespaces.filter(x$0 -> Files.isDirectory(x$0)).forEach(namespaceDir -> loadNamespaceFiles(namespaceDir, selectedFiles));
      } catch (IOException e) {
         NeedsOfNature.LOGGER.debug("[NoN] Failed to enumerate namespaces under {}", assetsRoot, e);
      }
   }

   private static void loadNamespaceFiles(Path namespaceDir, Map<String, List<AnimationCueIndex.ParsedClip>> selectedFiles) {
      String namespace = namespaceDir.getFileName().toString();
      Path animationRoot = namespaceDir.resolve("geckolib").resolve("animations").resolve("afw");
      if (Files.isDirectory(animationRoot)) {
         try (Stream<Path> files = Files.walk(animationRoot)) {
            files.filter(x$0 -> Files.isRegularFile(x$0)).filter(path -> path.getFileName().toString().endsWith(".animation.json")).forEach(path -> {
               String relativePath = namespaceDir.relativize(path).toString().replace('\\', '/');
               String resourceKey = namespace + ":" + relativePath;

               try (InputStream input = Files.newInputStream(path)) {
                  selectedFiles.put(resourceKey, parseAnimationFile(input, namespace, relativePath, path.toString()));
               } catch (IOException | RuntimeException e) {
                  NeedsOfNature.LOGGER.debug("[NoN] Failed to parse animation cue file {}", path, e);
                  selectedFiles.put(resourceKey, List.of());
               }
            });
         } catch (IOException e) {
            NeedsOfNature.LOGGER.debug("[NoN] Failed to walk animation assets under {}", animationRoot, e);
         }
      }
   }

   private static List<AnimationCueIndex.ParsedClip> parseAnimationFile(InputStream input, String namespace, String namespaceRelativePath, String sourceName) {
      if (input != null && namespace != null && !namespace.isBlank() && isAnimationAssetPath(namespaceRelativePath)) {
         String normalizedPath = namespaceRelativePath.replace('\\', '/');
         String relativeAnimationPath = normalizedPath.substring("geckolib/animations/afw/".length());
         Identifier pathClipId = stageClipIdFromAnimationPath(namespace, relativeAnimationPath);
         Identifier conjoinedAnimationId = conjoinedAnimationIdFromPath(namespace, relativeAnimationPath);

         try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject animations = asObject(root.get("animations"));
            if (animations == null || animations.isEmpty()) {
               return List.of();
            }

            Map<Identifier, AnimationCueIndex.MutableProfile> parsedByClip = new LinkedHashMap<>();

            for (Entry<String, JsonElement> animationEntry : animations.entrySet()) {
               JsonObject animation = asObject(animationEntry.getValue());
               if (animation != null) {
                  Identifier clipId = pathClipId != null ? pathClipId : stageClipId(namespace, animationEntry.getKey());
                  if (clipId == null) {
                     clipId = conjoinedStageClipId(conjoinedAnimationId, animationEntry.getKey());
                  }

                  if (clipId != null) {
                     AnimationCueIndex.MutableProfile profile = parsedByClip.computeIfAbsent(clipId, ignored -> new AnimationCueIndex.MutableProfile());
                     AnimationSoundCueParser.ParsedAnimation parsedAnimation = AnimationSoundCueParser.parse(
                        animation, sourceName, NeedsOfNature::logSetupWarning
                     );
                     Integer cycleTicks = parsedAnimation.lengthSeconds() > 0.0 ? Math.max(1, (int)Math.round(parsedAnimation.lengthSeconds() * 20.0)) : null;
                     if (cycleTicks != null && cycleTicks > 0 && (profile.cycleTicks == null || cycleTicks > profile.cycleTicks)) {
                        profile.cycleTicks = cycleTicks;
                     }

                     for (AnimationSoundCueParser.ParsedCue cue : parsedAnimation.cues()) {
                        if (cue.effect() != null && !cue.effect().isBlank()) {
                           String effect = AnimationCueEffects.timingFamily(cue.effect());
                           if (effect != null) {
                              int cueTick = Math.max(0, (int)Math.round(cue.timeTicks()));
                              profile.cueTicksByEffect.computeIfAbsent(effect, ignored -> new TreeSet<>()).add(cueTick);
                           }
                        }
                     }
                  }
               }
            }

            List<AnimationCueIndex.ParsedClip> parsed = new ArrayList<>();

            for (Entry<Identifier, AnimationCueIndex.MutableProfile> entry : parsedByClip.entrySet()) {
               Map<String, List<Integer>> cuesByEffect = new LinkedHashMap<>();

               for (Entry<String, TreeSet<Integer>> cues : entry.getValue().cueTicksByEffect.entrySet()) {
                  if (!cues.getValue().isEmpty()) {
                     cuesByEffect.put(cues.getKey(), List.copyOf(cues.getValue()));
                  }
               }

               parsed.add(new AnimationCueIndex.ParsedClip(entry.getKey(), Map.copyOf(cuesByEffect), entry.getValue().cycleTicks));
            }

            return List.copyOf(parsed);
         } catch (IOException | RuntimeException e) {
            NeedsOfNature.LOGGER.debug("[NoN] Failed to parse animation cues in {}", sourceName, e);
            return List.of();
         }
      } else {
         return List.of();
      }
   }

   private static boolean isAnimationAssetPath(@Nullable String namespaceRelativePath) {
      if (namespaceRelativePath != null && !namespaceRelativePath.isBlank()) {
         String normalized = namespaceRelativePath.replace('\\', '/');
         return normalized.startsWith("geckolib/animations/afw/") && normalized.endsWith(".animation.json");
      } else {
         return false;
      }
   }

   @Nullable
   private static Identifier stageClipIdFromAnimationPath(String namespace, String relativeAnimationPath) {
      if (namespace != null && !namespace.isBlank() && relativeAnimationPath != null && !relativeAnimationPath.isBlank()) {
         String normalized = relativeAnimationPath.replace('\\', '/');
         int slash = normalized.lastIndexOf(47);
         String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
         return !fileName.endsWith(".animation.json") ? null : stageClipId(namespace, fileName.substring(0, fileName.length() - ".animation.json".length()));
      } else {
         return null;
      }
   }

   @Nullable
   private static Identifier conjoinedAnimationIdFromPath(String namespace, String relativeAnimationPath) {
      if (namespace != null && !namespace.isBlank() && relativeAnimationPath != null && !relativeAnimationPath.isBlank()) {
         String normalized = relativeAnimationPath.replace('\\', '/');
         if (!normalized.endsWith(".animation.json")) {
            return null;
         }

         String animationPath = normalized.substring(0, normalized.length() - ".animation.json".length());
         if (animationPath.isBlank()) {
            return null;
         }

         int slash = animationPath.lastIndexOf(47);
         if (slash > 0) {
            String fileName = animationPath.substring(slash + 1);
            String parentPath = animationPath.substring(0, slash);
            int parentSlash = parentPath.lastIndexOf(47);
            String parentName = parentSlash >= 0 ? parentPath.substring(parentSlash + 1) : parentPath;
            if (fileName.equals(parentName)) {
               animationPath = parentPath;
            }
         }

         return Identifier.tryParse(namespace + ":" + animationPath);
      } else {
         return null;
      }
   }

   @Nullable
   private static Identifier stageClipId(String namespace, String rawClipPath) {
      if (namespace != null && !namespace.isBlank() && rawClipPath != null && !rawClipPath.isBlank()) {
         int stageMarker = rawClipPath.lastIndexOf(".p");
         if (stageMarker <= 0) {
            return null;
         }

         int end = stageMarker + 2;

         while (end < rawClipPath.length() && Character.isDigit(rawClipPath.charAt(end))) {
            end++;
         }

         if (end == stageMarker + 2) {
            return null;
         } else {
            return end < rawClipPath.length() && rawClipPath.charAt(end) != '_' ? null : Identifier.tryParse(namespace + ":" + rawClipPath.substring(0, end));
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static Identifier conjoinedStageClipId(@Nullable Identifier animationId, String animationKey) {
      if (animationId != null && animationKey != null && animationKey.length() >= 4 && animationKey.charAt(0) == 'p') {
         int separator = 1;

         while (separator < animationKey.length() && Character.isDigit(animationKey.charAt(separator))) {
            separator++;
         }

         if (separator != 1 && separator < animationKey.length() - 1 && animationKey.charAt(separator) == '_') {
            int stageNumber;
            try {
               stageNumber = Integer.parseInt(animationKey.substring(1, separator));
            } catch (NumberFormatException e) {
               return null;
            }

            return stageNumber <= 0 ? null : AnimationIds.stageId(animationId, stageNumber);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static JsonObject asObject(@Nullable JsonElement element) {
      return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
   }

   @Nullable
   private static AnimationCueIndex.ParsedPath parseZipAssetPath(String entryPath) {
      if (entryPath != null && entryPath.startsWith("assets/")) {
         int namespaceStart = "assets/".length();
         int namespaceEnd = entryPath.indexOf(47, namespaceStart);
         if (namespaceEnd <= namespaceStart) {
            return null;
         }

         String namespace = entryPath.substring(namespaceStart, namespaceEnd);
         String namespaceRelativePath = entryPath.substring(namespaceEnd + 1);
         return !namespace.isBlank() && !namespaceRelativePath.isBlank() ? new AnimationCueIndex.ParsedPath(namespace, namespaceRelativePath) : null;
      } else {
         return null;
      }
   }

   public record CueProfile(Map<String, List<Integer>> cueTicksByEffect, @Nullable Integer cycleTicks) {
      public List<Integer> cueTicks(@Nullable String effect) {
         String normalized = normalizeEffect(effect);
         return normalized == null ? List.of() : this.cueTicksByEffect.getOrDefault(normalized, List.of());
      }

      @Nullable
      public Integer firstCueTick(@Nullable String effect) {
         List<Integer> ticks = this.cueTicks(effect);
         return ticks.isEmpty() ? null : ticks.getFirst();
      }

      @Nullable
      private static String normalizeEffect(@Nullable String effect) {
         return AnimationCueEffects.timingFamily(effect);
      }
   }

   private static final class MutableProfile {
      private final Map<String, TreeSet<Integer>> cueTicksByEffect = new LinkedHashMap<>();
      @Nullable
      private Integer cycleTicks;
   }

   private record ParsedClip(Identifier clipId, Map<String, List<Integer>> cueTicksByEffect, @Nullable Integer cycleTicks) {
   }

   private record ParsedPath(String namespace, String namespaceRelativePath) {
      private String resourceKey() {
         return this.namespace + ":" + this.namespaceRelativePath;
      }
   }

   private static final class Reloader implements SynchronousResourceReloader {
      private static final Identifier ID = AnimationEngine.runtimeId("cue_index");

      public void reload(ResourceManager manager) {
         AnimationCueIndex.reload();
      }
   }
}
