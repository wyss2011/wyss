package com.nonid.api.animation;

import java.util.Set;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record NonAnimationBlockRequirement(
   String type,
   @Nullable NonAnimationBlockRequirement.WallHeight wallHeight,
   @Nullable NonAnimationBlockRequirement.WallWidth wallWidth,
   @Nullable String facing,
   @Nullable NonAnimationBlockRequirement.Dimensions clearance,
   @Nullable String support,
   @Nullable String placement,
   int surfaceRadius,
   @Nullable NonAnimationBlockRequirement.Footprint surfaceFootprint,
   Set<Identifier> blockIds,
   Set<Identifier> blockTagIds
) {
   public NonAnimationBlockRequirement {
      blockIds = blockIds == null ? Set.of() : Set.copyOf(blockIds);
      blockTagIds = blockTagIds == null ? Set.of() : Set.copyOf(blockTagIds);
   }

   public record Dimensions(int width, int height, int depth) {
   }

   public record Footprint(int width, int depth, int height, int margin) {
   }

   public record WallHeight(int minimum, @Nullable Integer maximum) {
   }

   public record WallWidth(int minimum, @Nullable Integer maximum) {
   }
}
