package com.nonid.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.resource.AbstractFileResourcePack;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.ResourcePack.ResultConsumer;
import net.minecraft.resource.ResourcePackProfile.Metadata;
import net.minecraft.resource.ResourcePackProfile.PackFactory;
import net.minecraft.util.Identifier;

final class NonPrefixedZipResourcePack extends AbstractFileResourcePack {
   private final ZipFile zipFile;
   private final String rootPrefix;

   private NonPrefixedZipResourcePack(ResourcePackInfo info, Path zipPath, String rootPrefix) throws IOException {
      super(info);
      this.zipFile = new ZipFile(zipPath.toFile());
      this.rootPrefix = rootPrefix != null && !rootPrefix.isBlank() ? rootPrefix : "";
   }

   public InputSupplier<InputStream> openRoot(String... segments) {
      String relative = joinRootSegments(segments);
      if (relative == null) {
         return null;
      }

      ZipEntry entry = this.zipFile.getEntry(this.rootPrefix + relative);
      return entry != null && !entry.isDirectory() ? InputSupplier.create(this.zipFile, entry) : null;
   }

   public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
      if (type != null && id != null) {
         String path = this.rootPrefix + type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath();
         ZipEntry entry = this.zipFile.getEntry(path);
         return entry != null && !entry.isDirectory() ? InputSupplier.create(this.zipFile, entry) : null;
      } else {
         return null;
      }
   }

   public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {
      if (type != null && namespace != null && prefix != null && consumer != null) {
         String scanPrefix = this.rootPrefix + type.getDirectory() + "/" + namespace + "/" + prefix;
         Enumeration<? extends ZipEntry> entries = this.zipFile.entries();

         while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry != null && !entry.isDirectory()) {
               String normalized = NonPackRootResolver.normalizeZipEntryName(entry.getName());
               if (normalized != null && normalized.startsWith(scanPrefix)) {
                  String idPath = normalized.substring((this.rootPrefix + type.getDirectory() + "/" + namespace + "/").length());
                  if (!idPath.isBlank()) {
                     Identifier id = Identifier.tryParse(namespace, idPath);
                     if (id != null) {
                        consumer.accept(id, InputSupplier.create(this.zipFile, entry));
                     }
                  }
               }
            }
         }
      }
   }

   public Set<String> getNamespaces(ResourceType type) {
      if (type == null) {
         return Set.of();
      }

      String typePrefix = this.rootPrefix + type.getDirectory() + "/";
      LinkedHashSet<String> namespaces = new LinkedHashSet<>();
      Enumeration<? extends ZipEntry> entries = this.zipFile.entries();

      while (entries.hasMoreElements()) {
         ZipEntry entry = entries.nextElement();
         if (entry != null && !entry.isDirectory()) {
            String normalized = NonPackRootResolver.normalizeZipEntryName(entry.getName());
            if (normalized != null && normalized.startsWith(typePrefix)) {
               int namespaceStart = typePrefix.length();
               int namespaceEnd = normalized.indexOf(47, namespaceStart);
               if (namespaceEnd > namespaceStart) {
                  String namespace = normalized.substring(namespaceStart, namespaceEnd);
                  if (Identifier.isNamespaceValid(namespace)) {
                     namespaces.add(namespace);
                  }
               }
            }
         }
      }

      return Set.copyOf(namespaces);
   }

   public void close() {
      try {
         this.zipFile.close();
      } catch (IOException var2) {
      }
   }

   private static String joinRootSegments(String... segments) {
      if (segments != null && segments.length != 0) {
         StringBuilder out = new StringBuilder();
         String[] var2 = segments;
         int var3 = var2.length;
         int var4 = 0;

         while (var4 < var3) {
            String segment = var2[var4];
            if (segment != null && !segment.isBlank()) {
               String normalized = segment.replace('\\', '/');
               if (!normalized.contains("/") && !normalized.equals(".") && !normalized.equals("..")) {
                  if (!out.isEmpty()) {
                     out.append('/');
                  }

                  out.append(normalized);
                  var4++;
                  continue;
               }

               return null;
            }

            return null;
         }

         return out.toString();
      } else {
         return "";
      }
   }

   static final class Factory implements PackFactory {
      private final Path zipPath;
      private final String rootPrefix;

      Factory(Path zipPath, String rootPrefix) {
         this.zipPath = zipPath;
         this.rootPrefix = rootPrefix;
      }

      public ResourcePack open(ResourcePackInfo info) {
         try {
            return new NonPrefixedZipResourcePack(info, this.zipPath, this.rootPrefix);
         } catch (IOException e) {
            throw new RuntimeException("Failed to open prefixed NoN pack " + this.zipPath, e);
         }
      }

      public ResourcePack openWithOverlays(ResourcePackInfo info, Metadata metadata) {
         return this.open(info);
      }
   }
}
