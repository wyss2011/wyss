package com.nonid.pack;

import com.nonid.NeedsOfNature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jetbrains.annotations.Nullable;

public final class NonPackRootResolver {
   private static final Set<String> WARNED_NESTED_PACKS = ConcurrentHashMap.newKeySet();
   private static final Set<String> WARNED_INVALID_PACKS = ConcurrentHashMap.newKeySet();

   private NonPackRootResolver() {
   }

   public static boolean isSupportedExternalPackPath(Path path) {
      if (path == null) {
         return false;
      }

      if (Files.isDirectory(path)) {
         return true;
      }

      Path fileName = path.getFileName();
      return fileName == null ? false : fileName.toString().toLowerCase(Locale.ROOT).endsWith(".zip");
   }

   public static Optional<NonPackRootResolver.ResolvedRoot> resolve(Path path) {
      return resolve(path, true);
   }

   public static Optional<NonPackRootResolver.ResolvedRoot> resolve(Path path, boolean warn) {
      if (path == null || !Files.exists(path)) {
         return Optional.empty();
      }

      if (Files.isDirectory(path)) {
         return resolveDirectory(path, warn);
      }

      Path fileName = path.getFileName();
      String lowerName = fileName == null ? "" : fileName.toString().toLowerCase(Locale.ROOT);
      return !lowerName.endsWith(".zip") ? Optional.empty() : resolveZip(path, warn);
   }

   private static Optional<NonPackRootResolver.ResolvedRoot> resolveDirectory(Path path, boolean warn) {
      if (isPackRootDirectory(path)) {
         return Optional.of(NonPackRootResolver.ResolvedRoot.directory(path, path, "", null));
      }

      LinkedHashSet<Path> candidates = new LinkedHashSet<>();

      try (Stream<Path> entries = Files.list(path)) {
         entries.filter(x$0 -> Files.isDirectory(x$0)).filter(NonPackRootResolver::isPackRootDirectory).forEach(candidates::add);
      } catch (IOException e) {
         if (warn) {
            warnInvalid(path, "failed to inspect directory pack root");
         }

         return Optional.empty();
      }

      if (candidates.size() == 1) {
         Path nestedRoot = candidates.iterator().next();
         String folder = fileName(nestedRoot);
         if (warn) {
            warnNested(path, folder);
         }

         return Optional.of(NonPackRootResolver.ResolvedRoot.directory(path, nestedRoot, "", folder));
      } else {
         if (warn) {
            if (candidates.size() > 1) {
               warnInvalid(path, "contains multiple one-level nested pack roots");
            } else {
               warnInvalid(path, "does not contain assets/, data/, or pack.mcmeta at the root or inside one wrapper folder");
            }
         }

         return Optional.empty();
      }
   }

   private static Optional<NonPackRootResolver.ResolvedRoot> resolveZip(Path path, boolean warn) {
      try (ZipFile zip = new ZipFile(path.toFile())) {
         boolean rootMatches = false;
         LinkedHashSet<String> candidates = new LinkedHashSet<>();
         Enumeration<? extends ZipEntry> entries = zip.entries();

         while (entries.hasMoreElements()) {
            String normalized = normalizeZipEntryName(entries.nextElement().getName());
            if (normalized != null && !normalized.isBlank()) {
               if (isPackRootEntry(normalized)) {
                  rootMatches = true;
                  break;
               }

               int slash = normalized.indexOf(47);
               if (slash > 0 && slash < normalized.length() - 1) {
                  String top = normalized.substring(0, slash);
                  String nested = normalized.substring(slash + 1);
                  if (!isIgnoredTopLevelFolder(top) && isPackRootEntry(nested)) {
                     candidates.add(top);
                  }
               }
            }
         }

         if (rootMatches) {
            return Optional.of(NonPackRootResolver.ResolvedRoot.zip(path, "", null));
         }

         if (candidates.size() == 1) {
            String folder = candidates.iterator().next();
            if (warn) {
               warnNested(path, folder);
            }

            return Optional.of(NonPackRootResolver.ResolvedRoot.zip(path, folder + "/", folder));
         }

         if (warn) {
            if (candidates.size() > 1) {
               warnInvalid(path, "contains multiple one-level nested pack roots");
            } else {
               warnInvalid(path, "does not contain assets/, data/, or pack.mcmeta at the root or inside one wrapper folder");
            }
         }
      } catch (IOException e) {
         if (warn) {
            warnInvalid(path, "failed to inspect zip pack root");
         }
      }

      return Optional.empty();
   }

   private static boolean isPackRootDirectory(Path path) {
      return Files.isRegularFile(path.resolve("pack.mcmeta")) || Files.isDirectory(path.resolve("assets")) || Files.isDirectory(path.resolve("data"));
   }

   private static boolean isPackRootEntry(String entryPath) {
      return entryPath != null && !entryPath.isBlank()
         ? "pack.mcmeta".equals(entryPath) || entryPath.startsWith("assets/") || entryPath.startsWith("data/")
         : false;
   }

   private static boolean isIgnoredTopLevelFolder(String folder) {
      return "__MACOSX".equals(folder) || folder.startsWith(".");
   }

   @Nullable
   public static String normalizeZipEntryName(@Nullable String raw) {
      if (raw == null) {
         return null;
      }

      String normalized = raw.replace('\\', '/');

      while (normalized.startsWith("/")) {
         normalized = normalized.substring(1);
      }

      return !normalized.contains("../") && !normalized.equals("..") && !normalized.startsWith("../") ? normalized : null;
   }

   private static void warnNested(Path path, String folder) {
      String key = normalizedKey(path) + "|" + folder;
      if (WARNED_NESTED_PACKS.add(key)) {
         NeedsOfNature.logSetupWarning(
            "[NoN] External pack '{}' has its pack contents inside the folder '{}'. NoN will load it, but released packs should place assets/, data/, and pack.mcmeta directly at the pack root.",
            fileName(path),
            folder
         );
      }
   }

   private static void warnInvalid(Path path, String reason) {
      String key = normalizedKey(path) + "|" + reason;
      if (WARNED_INVALID_PACKS.add(key)) {
         NeedsOfNature.logSetupWarning("[NoN] Ignoring external NoN pack '{}': {}.", fileName(path), reason);
      }
   }

   private static String normalizedKey(Path path) {
      return path == null ? "<null>" : path.toAbsolutePath().normalize().toString();
   }

   private static String fileName(Path path) {
      return path != null && path.getFileName() != null ? path.getFileName().toString() : String.valueOf(path);
   }

   public record ResolvedRoot(Path sourcePath, Path rootPath, String zipPrefix, @Nullable String wrapperFolder) {
      static NonPackRootResolver.ResolvedRoot directory(Path sourcePath, Path rootPath, String zipPrefix, @Nullable String wrapperFolder) {
         return new NonPackRootResolver.ResolvedRoot(sourcePath, rootPath, zipPrefix, wrapperFolder);
      }

      static NonPackRootResolver.ResolvedRoot zip(Path sourcePath, String zipPrefix, @Nullable String wrapperFolder) {
         return new NonPackRootResolver.ResolvedRoot(sourcePath, sourcePath, zipPrefix == null ? "" : zipPrefix, wrapperFolder);
      }

      public boolean isZip() {
         return Files.isRegularFile(this.sourcePath);
      }

      public boolean isNested() {
         return this.wrapperFolder != null && !this.wrapperFolder.isBlank();
      }

      public String prefixPath(String relativePath) {
         String relative = relativePath == null ? "" : relativePath.replace('\\', '/');
         return this.zipPrefix != null && !this.zipPrefix.isBlank() ? this.zipPrefix + relative : relative;
      }

      @Nullable
      public String stripZipPrefix(String entryPath) {
         String normalized = NonPackRootResolver.normalizeZipEntryName(entryPath);
         if (normalized == null) {
            return null;
         } else if (this.zipPrefix == null || this.zipPrefix.isBlank()) {
            return normalized;
         } else {
            return !normalized.startsWith(this.zipPrefix) ? null : normalized.substring(this.zipPrefix.length());
         }
      }
   }
}
