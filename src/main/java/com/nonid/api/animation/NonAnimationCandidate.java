package com.nonid.api.animation;

import java.util.List;

public record NonAnimationCandidate(NonAnimationDefinition definition, List<String> actorKeys, boolean startEligible) {
   public NonAnimationCandidate {
      actorKeys = actorKeys == null ? List.of() : List.copyOf(actorKeys);
   }
}
