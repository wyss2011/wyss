package com.nonid.pack;

import com.nonid.NeedsOfNature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class NonExternalPackCatalog {
   private NonExternalPackCatalog() {
   }

   public static List<String> listFileNames(Path directory) {
      return listPaths(directory).stream().map(NonExternalPackCatalog::fileName).toList();
   }

   public static List<NonExternalPackCatalog.Entry> resolveHighToLow(Path directory, List<String> configuredOrder) {
      List<Path> paths = listPaths(directory);
      if (paths.isEmpty()) {
         return List.of();
      }

      Map<String, Path> pathByName = new LinkedHashMap<>();

      for (Path path : paths) {
         pathByName.put(fileName(path), path);
      }

      List<String> orderedNames = normalizeOrder(configuredOrder, List.copyOf(pathByName.keySet()));
      List<NonExternalPackCatalog.Entry> entries = new ArrayList<>(orderedNames.size());

      for (String name : orderedNames) {
         Path path = pathByName.get(name);
         if (path != null) {
            NonPackRootResolver.resolve(path).ifPresent(root -> entries.add(new NonExternalPackCatalog.Entry(name, path, root)));
         }
      }

      return List.copyOf(entries);
   }

   public static List<NonExternalPackCatalog.Entry> resolveLowToHigh(Path directory, List<String> configuredOrder) {
      List<NonExternalPackCatalog.Entry> entries = new ArrayList<>(resolveHighToLow(directory, configuredOrder));
      Collections.reverse(entries);
      return List.copyOf(entries);
   }

   public static List<String> normalizeOrder(List<String> configuredOrder, List<String> discoveredNames) {
      List<String> discovered = discoveredNames == null ? List.of() : discoveredNames;
      if (discovered.isEmpty()) {
         return List.of();
      }

      LinkedHashSet<String> discoveredSet = new LinkedHashSet<>(discovered);
      LinkedHashSet<String> ordered = new LinkedHashSet<>();
      if (configuredOrder != null) {
         for (String raw : configuredOrder) {
            if (raw != null) {
               String name = raw.trim();
               if (!name.isEmpty() && discoveredSet.contains(name)) {
                  ordered.add(name);
               }
            }
         }
      }

      ordered.addAll(discovered);
      return List.copyOf(ordered);
   }

   private static List<Path> listPaths(Path directory) {
      if (directory != null && Files.isDirectory(directory)) {
         try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(NonPackRootResolver::isSupportedExternalPackPath)
               .sorted((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(fileName(left), fileName(right)))
               .toList();
         } catch (IOException e) {
            NeedsOfNature.logSetupWarning("[NoN] Failed to list external NoN packs in {}.", directory, e);
            return List.of();
         }
      } else {
         return List.of();
      }
   }

   private static String fileName(Path path) {
      Path fileName = path == null ? null : path.getFileName();
      return fileName == null ? String.valueOf(path) : fileName.toString();
   }

   public record Entry(String fileName, Path path, NonPackRootResolver.ResolvedRoot root) {
   }
}
