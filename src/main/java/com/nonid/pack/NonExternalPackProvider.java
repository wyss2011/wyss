package com.nonid.pack;

import com.mojang.logging.LogUtils;
import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackPosition;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.DirectoryResourcePack.DirectoryBackedFactory;
import net.minecraft.resource.ResourcePackProfile.InsertionPosition;
import net.minecraft.resource.ResourcePackProfile.PackFactory;
import net.minecraft.resource.ZipResourcePack.ZipBackedFactory;
import net.minecraft.text.Text;
import net.minecraft.util.path.PathUtil;
import net.minecraft.util.path.SymlinkEntry;
import net.minecraft.util.path.SymlinkFinder;
import net.minecraft.util.path.SymlinkValidationException;
import org.slf4j.Logger;

public final class NonExternalPackProvider implements ResourcePackProvider {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String EXTERNAL_PACKS_DIR_NAME = "needsofnature";
   private static final ResourcePackSource NON_SOURCE = ResourcePackSource.create(name -> Text.literal("[NoN] ").append(name), true);
   private static final ResourcePackPosition ALWAYS_ENABLED_TOP = new ResourcePackPosition(true, InsertionPosition.TOP, false);
   private final Path packsDir;
   private final ResourceType type;
   private final SymlinkFinder symlinkFinder;

   public NonExternalPackProvider(Path packsDir, ResourceType type, SymlinkFinder symlinkFinder) {
      this.packsDir = packsDir;
      this.type = type;
      this.symlinkFinder = symlinkFinder;
   }

   public ResourceType getType() {
      return this.type;
   }

   public static Path resolveDefaultPacksDir() {
      return FabricLoader.getInstance().getGameDir().resolve("needsofnature");
   }

   public static List<String> listExternalPackFileNames(Path dir) {
      return NonExternalPackCatalog.listFileNames(dir);
   }

   public static List<String> normalizeConfiguredOrder(List<String> configuredOrder, List<String> discoveredNames) {
      return NonExternalPackCatalog.normalizeOrder(configuredOrder, discoveredNames);
   }

   public void register(Consumer<ResourcePackProfile> profileAdder) {
      try {
         PathUtil.createDirectories(this.packsDir);
         List<NonExternalPackProvider.PackCandidate> discovered = NonExternalPackCatalog.resolveHighToLow(this.packsDir, resolveConfiguredOrder())
            .stream()
            .filter(entry -> this.isSymlinkSafe(entry.root()))
            .map(entry -> new NonExternalPackProvider.PackCandidate(entry.path(), createPackFactory(entry.root())))
            .toList();
         List<String> normalizedOrder = discovered.stream().map(NonExternalPackProvider.PackCandidate::fileName).toList();
         Map<String, Integer> indexByName = indexByName(normalizedOrder);

         for (int i = discovered.size() - 1; i >= 0; i--) {
            NonExternalPackProvider.PackCandidate candidate = discovered.get(i);
            int rank = indexByName.getOrDefault(candidate.fileName(), Integer.MAX_VALUE);
            ResourcePackInfo info = this.createPackInfo(candidate.path(), rank);
            ResourcePackProfile profile = ResourcePackProfile.create(info, candidate.packFactory(), this.type, ALWAYS_ENABLED_TOP);
            if (profile != null) {
               profileAdder.accept(profile);
            }
         }
      } catch (IOException e) {
         LOGGER.warn("Failed to list NoN packs in {}", this.packsDir, e);
      }
   }

   private boolean isSymlinkSafe(NonPackRootResolver.ResolvedRoot root) {
      if (root != null && this.symlinkFinder != null) {
         List<SymlinkEntry> entries = new ArrayList<>();

         try {
            if (root.isZip()) {
               if (Files.isSymbolicLink(root.sourcePath())) {
                  this.symlinkFinder.validate(root.sourcePath(), entries);
               }
            } else {
               this.symlinkFinder.validateRecursively(root.rootPath(), entries);
            }
         } catch (IOException e) {
            LOGGER.warn("Failed to validate NoN pack symlinks in {}, ignoring", root.sourcePath(), e);
            return false;
         }

         if (!entries.isEmpty()) {
            LOGGER.warn("Ignoring potential NoN pack entry: {}", SymlinkValidationException.getMessage(root.sourcePath(), entries));
            return false;
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private static PackFactory createPackFactory(NonPackRootResolver.ResolvedRoot root) {
      if (root.isZip()) {
         return (PackFactory)(root.isNested()
            ? new NonPrefixedZipResourcePack.Factory(root.sourcePath(), root.zipPrefix())
            : new ZipBackedFactory(root.sourcePath()));
      } else {
         return new DirectoryBackedFactory(root.rootPath());
      }
   }

   private ResourcePackInfo createPackInfo(Path path, int rank) {
      String fileName = path.getFileName().toString();
      String channel = this.type == ResourceType.CLIENT_RESOURCES ? "client" : "server";
      String safeFileName = sanitizeForPackId(fileName);
      String orderPrefix = formatOrderPrefix(rank);
      String id = "needsofnature/" + channel + "/" + orderPrefix + "_" + safeFileName;
      return new ResourcePackInfo(id, Text.literal(fileName), NON_SOURCE, Optional.empty());
   }

   private static List<String> resolveConfiguredOrder() {
      NonConfig config = NeedsOfNature.getConfig();
      return config != null ? config.getExternalNoNPackLoadOrder() : List.of();
   }

   private static Map<String, Integer> indexByName(List<String> names) {
      Map<String, Integer> index = new LinkedHashMap<>();
      if (names == null) {
         return index;
      }

      for (int i = 0; i < names.size(); i++) {
         index.putIfAbsent(names.get(i), i);
      }

      return index;
   }

   private static String formatOrderPrefix(int rank) {
      int clamped = rank;
      if (clamped < 0) {
         clamped = 0;
      }

      if (clamped > 9999) {
         clamped = 9999;
      }

      int encoded = 9999 - clamped;
      return String.format(Locale.ROOT, "%04d", encoded);
   }

   private static String sanitizeForPackId(String fileName) {
      if (fileName != null && !fileName.isBlank()) {
         StringBuilder out = new StringBuilder(fileName.length());

         for (int i = 0; i < fileName.length(); i++) {
            char c = fileName.charAt(i);
            boolean safe = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '.' || c == '_' || c == '-';
            out.append(safe ? c : '_');
         }

         return out.toString();
      } else {
         return "unknown";
      }
   }

   private record PackCandidate(Path path, PackFactory packFactory) {
      private String fileName() {
         Path fileName = this.path.getFileName();
         return fileName == null ? this.path.toString() : fileName.toString();
      }
   }
}
