package com.nonid.api.animation;

import java.util.Set;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record NonAnimationActor(
   Set<Identifier> entityTypes,
   @Nullable String entityVariant,
   Set<String> requiredTags,
   Set<String> requiredAnyTags,
   String label,
   boolean baby,
   NonAnimationActor.Activity activity,
   @Nullable Identifier leftPropItemId,
   @Nullable Identifier rightPropItemId,
   @Nullable Identifier floorPropItemId
) {
   public NonAnimationActor {
      entityTypes = entityTypes == null ? Set.of() : Set.copyOf(entityTypes);
      requiredTags = requiredTags == null ? Set.of() : Set.copyOf(requiredTags);
      requiredAnyTags = requiredAnyTags == null ? Set.of() : Set.copyOf(requiredAnyTags);
      label = label == null ? "" : label;
      activity = activity == null ? NonAnimationActor.Activity.ACTIVE : activity;
   }

   public enum Activity {
      ACTIVE,
      PASSIVE;
   }
}
