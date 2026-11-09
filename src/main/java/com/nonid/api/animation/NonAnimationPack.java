package com.nonid.api.animation;

import java.util.List;
import java.util.Set;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record NonAnimationPack(
   String id,
   String resourcePackId,
   String name,
   String author,
   @Nullable String version,
   @Nullable String description,
   @Nullable Identifier localModelRoot,
   List<NonAnimationPack.ModelImport> modelImports
) {
   public NonAnimationPack {
      modelImports = modelImports == null ? List.of() : List.copyOf(modelImports);
   }

   public NonAnimationPack(
      String id, String resourcePackId, String name, String author, @Nullable String version, @Nullable String description, @Nullable Identifier localModelRoot
   ) {
      this(id, resourcePackId, name, author, version, description, localModelRoot, List.of());
   }

   public record ModelImport(String packId, Identifier modelRoot, Set<Identifier> entityTypes) {
      public ModelImport {
         entityTypes = entityTypes == null ? Set.of() : Set.copyOf(entityTypes);
      }
   }
}
