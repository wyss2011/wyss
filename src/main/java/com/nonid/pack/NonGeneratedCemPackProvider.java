package com.nonid.pack;

import com.nonid.NeedsOfNature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class NonGeneratedCemPackProvider implements ResourcePackProvider {
   private static final String CEM_ROOT = "assets/minecraft/optifine/cem/";
   private static final byte[] VANILLA_FALLBACK_JEM_BYTES = "{\n  \"textureSize\": [64, 64],\n  \"models\": []\n}\n".getBytes(StandardCharsets.UTF_8);
   private final Path packsDir;

   public NonGeneratedCemPackProvider(Path packsDir) {
      this.packsDir = packsDir;
   }

   public void register(Consumer<ResourcePackProfile> profileAdder) {
      Map<Identifier, byte[]> generatedResources = this.buildGeneratedResources();
      if (!generatedResources.isEmpty()) {
         ResourcePackProfile profile = NonGeneratedInMemoryPack.createProfile(
            "needsofnature/client/generated_cem",
            Text.literal("NoN 生成的 CEM 规则"),
            Text.literal("生成的 NoN CEM 性别与年龄规则"),
            ResourceType.CLIENT_RESOURCES,
            generatedResources
         );
         if (profile != null) {
            profileAdder.accept(profile);
         }
      }
   }

   private Map<Identifier, byte[]> buildGeneratedResources() {
      List<NonGeneratedCemPackProvider.ScanSource> orderedSources = listOrderedScanSources(this.packsDir);
      if (orderedSources.isEmpty()) {
         return Map.of();
      }

      Map<String, NonGeneratedCemPackProvider.Resolution> resolved = new LinkedHashMap<>();

      for (NonGeneratedCemPackProvider.ScanSource source : orderedSources) {
         NonGeneratedCemPackProvider.PackScan scan = scanSource(source);
         if (!scan.isEmpty()) {
            LinkedHashSet<String> bases = new LinkedHashSet<>();
            bases.addAll(scan.explicitProperties);
            bases.addAll(scan.variants.keySet());

            for (String base : bases) {
               if (!resolved.containsKey(base)) {
                  if (scan.explicitProperties.contains(base)) {
                     resolved.put(base, NonGeneratedCemPackProvider.Resolution.explicit());
                  } else {
                     NonGeneratedCemPackProvider.CemVariants variants = scan.variants.get(base);
                     if (variants != null && variants.needsGeneratedProperties()) {
                        resolved.put(base, NonGeneratedCemPackProvider.Resolution.generated(variants));
                     }
                  }
               }
            }
         }
      }

      if (resolved.isEmpty()) {
         return Map.of();
      }

      Map<Identifier, byte[]> generated = new LinkedHashMap<>();

      for (Entry<String, NonGeneratedCemPackProvider.Resolution> entry : resolved.entrySet()) {
         String base = entry.getKey();
         NonGeneratedCemPackProvider.Resolution resolution = entry.getValue();
         if (resolution.kind != NonGeneratedCemPackProvider.ResolutionKind.EXPLICIT) {
            generated.put(cemIdentifier(base + ".properties"), generatedPropertiesBytes(resolution.variants));
            generated.put(cemIdentifier(base + ".jem"), VANILLA_FALLBACK_JEM_BYTES);
            putGeneratedVariant(generated, base, 3, resolution.variants.adultPrimaryBytes());
            putGeneratedVariant(generated, base, 2, resolution.variants.adultTwo);
            putGeneratedVariant(generated, base, 5, resolution.variants.babyPrimaryBytes());
            putGeneratedVariant(generated, base, 4, resolution.variants.babyTwo);
         }
      }

      return generated;
   }

   private static void putGeneratedVariant(Map<Identifier, byte[]> generated, String base, int suffix, byte[] bytes) {
      if (bytes != null && bytes.length != 0) {
         generated.put(cemIdentifier(base + suffix + ".jem"), bytes);
      }
   }

   private static List<NonGeneratedCemPackProvider.ScanSource> listOrderedScanSources(Path externalDir) {
      List<NonGeneratedCemPackProvider.ScanSource> ordered = new ArrayList<>();
      ordered.addAll(listOrderedExternalPackSources(externalDir));
      resolveBuiltinCemRoot().ifPresent(cemRoot -> ordered.add(NonGeneratedCemPackProvider.ScanSource.cemRoot("builtin", cemRoot)));
      return List.copyOf(ordered);
   }

   private static List<NonGeneratedCemPackProvider.ScanSource> listOrderedExternalPackSources(Path dir) {
      List<NonGeneratedCemPackProvider.ScanSource> ordered = new ArrayList<>();

      for (NonExternalPackCatalog.Entry entry : NonExternalPackCatalog.resolveHighToLow(dir, NeedsOfNature.getConfig().getExternalNoNPackLoadOrder())) {
         ordered.add(NonGeneratedCemPackProvider.ScanSource.packRoot(entry.fileName(), entry.root()));
      }

      return List.copyOf(ordered);
   }

   private static Optional<Path> resolveBuiltinCemRoot() {
      Optional<ModContainer> container = FabricLoader.getInstance().getModContainer("needsofnature");
      return container.isEmpty()
         ? Optional.empty()
         : container.get().findPath("assets/minecraft/optifine/cem/".substring(0, "assets/minecraft/optifine/cem/".length() - 1));
   }

   private static NonGeneratedCemPackProvider.PackScan scanSource(NonGeneratedCemPackProvider.ScanSource source) {
      if (source == null) {
         return NonGeneratedCemPackProvider.PackScan.EMPTY;
      }

      try {
         if (source.kind == NonGeneratedCemPackProvider.ScanSourceKind.CEM_ROOT) {
            return scanCemRoot(source.path);
         } else if (source.packRoot == null) {
            return NonGeneratedCemPackProvider.PackScan.EMPTY;
         } else {
            return source.packRoot.isZip() ? scanZipPack(source.packRoot) : scanDirectoryPack(source.packRoot.rootPath());
         }
      } catch (IOException e) {
         NeedsOfNature.LOGGER.debug("[NoN] Failed to scan {} for generated CEM rules.", source.debugName, e);
         return NonGeneratedCemPackProvider.PackScan.EMPTY;
      }
   }

   private static NonGeneratedCemPackProvider.PackScan scanDirectoryPack(Path packPath) throws IOException {
      Path cemRoot = packPath.resolve("assets/minecraft/optifine/cem/");
      return !Files.isDirectory(cemRoot) ? NonGeneratedCemPackProvider.PackScan.EMPTY : scanCemRoot(cemRoot);
   }

   private static NonGeneratedCemPackProvider.PackScan scanCemRoot(Path cemRoot) throws IOException {
      Map<String, NonGeneratedCemPackProvider.CemVariants> variants = new LinkedHashMap<>();
      Set<String> explicitProperties = new LinkedHashSet<>();

      try (Stream<Path> paths = Files.walk(cemRoot)) {
         for (Path path : paths.filter(x$0 -> Files.isRegularFile(x$0)).toList()) {
            String relative = cemRoot.relativize(path).toString().replace('\\', '/');
            byte[] bytes = relative.endsWith(".jem") ? Files.readAllBytes(path) : null;
            processCemPath(relative, bytes, explicitProperties, variants);
         }
      }

      return new NonGeneratedCemPackProvider.PackScan(explicitProperties, variants);
   }

   private static NonGeneratedCemPackProvider.PackScan scanZipPack(NonPackRootResolver.ResolvedRoot packRoot) throws IOException {
      Set<String> explicitProperties = new LinkedHashSet<>();
      Map<String, NonGeneratedCemPackProvider.CemVariants> variants = new LinkedHashMap<>();

      try (ZipFile zipFile = new ZipFile(packRoot.sourcePath().toFile())) {
         Enumeration<? extends ZipEntry> entries = zipFile.entries();

         while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
               String name = packRoot.stripZipPrefix(entry.getName());
               if (name != null && name.startsWith("assets/minecraft/optifine/cem/")) {
                  String relative = name.substring("assets/minecraft/optifine/cem/".length());
                  byte[] bytes = relative.endsWith(".jem") ? zipFile.getInputStream(entry).readAllBytes() : null;
                  processCemPath(relative, bytes, explicitProperties, variants);
               }
            }
         }
      }

      return new NonGeneratedCemPackProvider.PackScan(explicitProperties, variants);
   }

   private static void processCemPath(
      String relativePath, byte[] bytes, Set<String> explicitProperties, Map<String, NonGeneratedCemPackProvider.CemVariants> variants
   ) {
      if (relativePath != null && !relativePath.isBlank()) {
         String normalized = relativePath.replace('\\', '/');
         if (normalized.endsWith(".properties")) {
            String base = normalized.substring(0, normalized.length() - ".properties".length());
            if (!base.isBlank()) {
               explicitProperties.add(base);
            }
         } else if (normalized.endsWith(".jem")) {
            String stem = normalized.substring(0, normalized.length() - ".jem".length());
            boolean baby = false;
            if (stem.endsWith("_baby") && stem.length() > "_baby".length()) {
               stem = stem.substring(0, stem.length() - "_baby".length());
               baby = true;
            }

            String base;
            NonGeneratedCemPackProvider.CemVariantKind kind;
            if (stem.endsWith("1") && stem.length() > 1) {
               base = stem.substring(0, stem.length() - 1);
               kind = NonGeneratedCemPackProvider.CemVariantKind.ONE;
            } else if (stem.endsWith("2") && stem.length() > 1) {
               base = stem.substring(0, stem.length() - 1);
               kind = NonGeneratedCemPackProvider.CemVariantKind.TWO;
            } else {
               base = stem;
               kind = NonGeneratedCemPackProvider.CemVariantKind.BASE;
            }

            if (!base.isBlank()) {
               NonGeneratedCemPackProvider.CemVariants entry = variants.computeIfAbsent(base, key -> new NonGeneratedCemPackProvider.CemVariants());
               entry.mark(kind, baby, bytes);
            }
         }
      }
   }

   private static Identifier cemIdentifier(String relativePath) {
      return Identifier.of("minecraft", "optifine/cem/" + relativePath);
   }

   private static byte[] generatedPropertiesBytes(NonGeneratedCemPackProvider.CemVariants variants) {
      StringBuilder content = new StringBuilder();
      int rule = 1;
      if (variants.hasAdultPrimary()) {
         rule = appendPrimaryGenderRules(content, rule, 3, false);
      }

      if (variants.hasAdultTwo) {
         rule = appendAgeGenderRule(content, rule, 2, false, 2, true);
      }

      if (variants.hasBabyPrimary()) {
         rule = appendPrimaryGenderRules(content, rule, 5, true);
      }

      if (variants.hasBabyTwo) {
         appendAgeGenderRule(content, rule, 4, true, 2, true);
      }

      return content.toString().getBytes(StandardCharsets.UTF_8);
   }

   private static int appendPrimaryGenderRules(StringBuilder content, int rule, int modelSuffix, boolean baby) {
      rule = appendAgeGenderRule(content, rule, modelSuffix, baby, 1, true);
      return appendAgeGenderRule(content, rule, modelSuffix, baby, 3, true);
   }

   private static int appendAgeGenderRule(StringBuilder content, int rule, int modelSuffix, boolean baby, int gender, boolean enabled) {
      if (!enabled) {
         return rule;
      }

      appendRuleHeader(content, rule, modelSuffix);
      content.append("nbt.").append(rule).append('.').append("NeedsOfNatureGender").append('=').append(gender).append('\n');
      content.append("baby.").append(rule).append('=').append(baby).append('\n');
      return rule + 1;
   }

   private static void appendRuleHeader(StringBuilder content, int rule, int modelSuffix) {
      if (!content.isEmpty()) {
         content.append('\n');
      }

      content.append("models.").append(rule).append('=').append(modelSuffix).append('\n');
   }

   private enum CemVariantKind {
      BASE,
      ONE,
      TWO;
   }

   private static final class CemVariants {
      private boolean hasAdultBase;
      private boolean hasAdultOne;
      private boolean hasAdultTwo;
      private boolean hasBabyBase;
      private boolean hasBabyOne;
      private boolean hasBabyTwo;
      private byte[] adultBase;
      private byte[] adultOne;
      private byte[] adultTwo;
      private byte[] babyBase;
      private byte[] babyOne;
      private byte[] babyTwo;

      private void mark(NonGeneratedCemPackProvider.CemVariantKind kind, boolean baby, byte[] bytes) {
         if (baby) {
            switch (kind) {
               case BASE:
                  this.hasBabyBase = true;
                  this.babyBase = bytes;
                  break;
               case ONE:
                  this.hasBabyOne = true;
                  this.babyOne = bytes;
                  break;
               case TWO:
                  this.hasBabyTwo = true;
                  this.babyTwo = bytes;
            }
         } else {
            switch (kind) {
               case BASE:
                  this.hasAdultBase = true;
                  this.adultBase = bytes;
                  break;
               case ONE:
                  this.hasAdultOne = true;
                  this.adultOne = bytes;
                  break;
               case TWO:
                  this.hasAdultTwo = true;
                  this.adultTwo = bytes;
            }
         }
      }

      private boolean hasAdultPrimary() {
         return this.hasAdultOne || this.hasAdultBase;
      }

      private boolean hasBabyPrimary() {
         return this.hasBabyOne || this.hasBabyBase;
      }

      private byte[] adultPrimaryBytes() {
         return this.adultOne != null ? this.adultOne : this.adultBase;
      }

      private byte[] babyPrimaryBytes() {
         return this.babyOne != null ? this.babyOne : this.babyBase;
      }

      private boolean needsGeneratedProperties() {
         return this.hasAdultModelRules() || this.hasBabyModelRules();
      }

      private boolean hasAdultModelRules() {
         return this.hasAdultBase || this.hasAdultOne || this.hasAdultTwo;
      }

      private boolean hasBabyModelRules() {
         return this.hasBabyBase || this.hasBabyOne || this.hasBabyTwo;
      }
   }

   private record PackScan(Set<String> explicitProperties, Map<String, NonGeneratedCemPackProvider.CemVariants> variants) {
      private static final NonGeneratedCemPackProvider.PackScan EMPTY = new NonGeneratedCemPackProvider.PackScan(Set.of(), Map.of());

      private boolean isEmpty() {
         return this.explicitProperties.isEmpty() && this.variants.isEmpty();
      }
   }

   private static final class Resolution {
      private final NonGeneratedCemPackProvider.ResolutionKind kind;
      private final NonGeneratedCemPackProvider.CemVariants variants;

      private Resolution(NonGeneratedCemPackProvider.ResolutionKind kind, NonGeneratedCemPackProvider.CemVariants variants) {
         this.kind = kind;
         this.variants = variants;
      }

      private static NonGeneratedCemPackProvider.Resolution explicit() {
         return new NonGeneratedCemPackProvider.Resolution(NonGeneratedCemPackProvider.ResolutionKind.EXPLICIT, null);
      }

      private static NonGeneratedCemPackProvider.Resolution generated(NonGeneratedCemPackProvider.CemVariants variants) {
         return new NonGeneratedCemPackProvider.Resolution(NonGeneratedCemPackProvider.ResolutionKind.GENERATED, variants);
      }
   }

   private enum ResolutionKind {
      EXPLICIT,
      GENERATED;
   }

   private static final class ScanSource {
      private final NonGeneratedCemPackProvider.ScanSourceKind kind;
      private final String debugName;
      private final Path path;
      private final NonPackRootResolver.ResolvedRoot packRoot;

      private ScanSource(NonGeneratedCemPackProvider.ScanSourceKind kind, String debugName, Path path, NonPackRootResolver.ResolvedRoot packRoot) {
         this.kind = kind;
         this.debugName = debugName;
         this.path = path;
         this.packRoot = packRoot;
      }

      private static NonGeneratedCemPackProvider.ScanSource packRoot(String debugName, NonPackRootResolver.ResolvedRoot packRoot) {
         return new NonGeneratedCemPackProvider.ScanSource(NonGeneratedCemPackProvider.ScanSourceKind.PACK_ROOT, debugName, null, packRoot);
      }

      private static NonGeneratedCemPackProvider.ScanSource cemRoot(String debugName, Path path) {
         return new NonGeneratedCemPackProvider.ScanSource(NonGeneratedCemPackProvider.ScanSourceKind.CEM_ROOT, debugName, path, null);
      }
   }

   private enum ScanSourceKind {
      PACK_ROOT,
      CEM_ROOT;
   }
}
