package com.nonid.api.animation;

import java.util.List;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record NonAnimationStage(
   Identifier animationId,
   boolean looping,
   long lengthTicks,
   boolean joinsAllowed,
   double speed,
   Identifier playbackAnimationId,
   List<NonAnimationStage.ActorProp> actorProps,
   double cycleMidpointOffsetSeconds,
   double cycleTicks
) {
   public NonAnimationStage {
      actorProps = actorProps == null ? List.of() : List.copyOf(actorProps);
   }

   public record ActorProp(String actorKey, @Nullable Identifier leftItemId, @Nullable Identifier rightItemId, @Nullable Identifier floorItemId) {
   }
}
