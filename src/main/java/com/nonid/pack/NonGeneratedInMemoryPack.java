package com.nonid.pack;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.resource.AbstractFileResourcePack;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.PackVersion;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackPosition;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.ResourcePack.ResultConsumer;
import net.minecraft.resource.ResourcePackProfile.InsertionPosition;
import net.minecraft.resource.ResourcePackProfile.Metadata;
import net.minecraft.resource.ResourcePackProfile.PackFactory;
import net.minecraft.resource.metadata.PackResourceMetadata;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Range;

final class NonGeneratedInMemoryPack {
   private static final ResourcePackSource NON_SOURCE = ResourcePackSource.create(name -> Text.literal("[NoN] ").append(name), true);
   private static final ResourcePackPosition ALWAYS_ENABLED_PINNED_TOP = new ResourcePackPosition(true, InsertionPosition.TOP, true);

   private NonGeneratedInMemoryPack() {
   }

   static ResourcePackProfile createProfile(String id, Text displayName, Text description, ResourceType type, Map<Identifier, byte[]> resources) {
      if (resources != null && !resources.isEmpty()) {
         ResourcePackInfo info = new ResourcePackInfo(id, displayName, NON_SOURCE, Optional.empty());
         return ResourcePackProfile.create(
            info, new NonGeneratedInMemoryPack.GeneratedPackFactory(type, description, resources), type, ALWAYS_ENABLED_PINNED_TOP
         );
      } else {
         return null;
      }
   }

   private static final class GeneratedPackFactory implements PackFactory {
      private final ResourceType type;
      private final Text description;
      private final Map<Identifier, byte[]> resources;

      private GeneratedPackFactory(ResourceType type, Text description, Map<Identifier, byte[]> resources) {
         this.type = type;
         this.description = description;
         this.resources = Map.copyOf(resources);
      }

      public ResourcePack open(ResourcePackInfo info) {
         return new NonGeneratedInMemoryPack.GeneratedResourcePack(info, this.type, this.description, this.resources);
      }

      public ResourcePack openWithOverlays(ResourcePackInfo info, Metadata metadata) {
         return this.open(info);
      }
   }

   private static final class GeneratedResourcePack extends AbstractFileResourcePack {
      private final ResourceType type;
      private final Map<Identifier, byte[]> resources;
      private final Set<String> namespaces;
      private final PackResourceMetadata metadata;
      private final byte[] packMcmeta;

      private GeneratedResourcePack(ResourcePackInfo info, ResourceType type, Text description, Map<Identifier, byte[]> resources) {
         super(info);
         this.type = type;
         this.resources = resources;
         this.namespaces = namespaces(resources);
         this.metadata = new PackResourceMetadata(description, new Range(PackVersion.of(75)));
         this.packMcmeta = ("{\"pack\":{\"description\":\"" + escapeJson(description.getString()) + "\",\"pack_format\":75}}").getBytes(StandardCharsets.UTF_8);
      }

      public InputSupplier<InputStream> openRoot(String... segments) {
         return segments.length == 1 && "pack.mcmeta".equals(segments[0]) ? () -> new ByteArrayInputStream(this.packMcmeta) : null;
      }

      public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
         if (type != this.type) {
            return null;
         }

         byte[] bytes = this.resources.get(id);
         return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
      }

      public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {
         if (type == this.type && consumer != null) {
            for (Entry<Identifier, byte[]> entry : this.resources.entrySet()) {
               Identifier id = entry.getKey();
               if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) {
                  byte[] bytes = entry.getValue();
                  consumer.accept(id, (InputSupplier)() -> new ByteArrayInputStream(bytes));
               }
            }
         }
      }

      public Set<String> getNamespaces(ResourceType type) {
         return type == this.type ? this.namespaces : Set.of();
      }

      public <T> T parseMetadata(ResourceMetadataSerializer<T> metadataSerializer) {
         if (metadataSerializer == PackResourceMetadata.getSerializerFor(this.type)) {
            return (T)this.metadata;
         } else {
            return (T)(metadataSerializer == PackResourceMetadata.DESCRIPTION_SERIALIZER ? this.metadata.description() : null);
         }
      }

      public void close() {
      }

      private static Set<String> namespaces(Map<Identifier, byte[]> resources) {
         LinkedHashSet<String> out = new LinkedHashSet<>();

         for (Identifier id : resources.keySet()) {
            out.add(id.getNamespace());
         }

         return out.isEmpty() ? Set.of() : Set.copyOf(out);
      }

      private static String escapeJson(String value) {
         return value != null && !value.isEmpty() ? value.replace("\\", "\\\\").replace("\"", "\\\"") : "";
      }
   }
}
