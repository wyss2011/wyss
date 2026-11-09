package com.nonid.api.animation;

import java.util.List;
import java.util.Set;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record NonAnimationDefinition(
   Identifier id,
   @Nullable String displayName,
   List<NonAnimationActor> actors,
   Set<String> requiredUnionTags,
   Set<String> animationTags,
   List<String> contentTags,
   List<NonAnimationStage> stages,
   double weight,
   boolean attackEligible,
   @Nullable NonAnimationBlockRequirement blockRequirement,
   String waterRequirement,
   @Nullable String positionAnchorActor,
   @Nullable NonAnimationPack pack,
   Set<Identifier> joinAfter,
   boolean enabled
) {
   public NonAnimationDefinition {
      actors = actors == null ? List.of() : List.copyOf(actors);
      requiredUnionTags = requiredUnionTags == null ? Set.of() : Set.copyOf(requiredUnionTags);
      animationTags = animationTags == null ? Set.of() : Set.copyOf(animationTags);
      contentTags = contentTags == null ? List.of() : List.copyOf(contentTags);
      stages = stages == null ? List.of() : List.copyOf(stages);
      waterRequirement = waterRequirement == null ? "none" : waterRequirement;
      joinAfter = joinAfter == null ? Set.of() : Set.copyOf(joinAfter);
   }
}
