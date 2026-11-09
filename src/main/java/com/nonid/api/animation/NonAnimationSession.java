package com.nonid.api.animation;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record NonAnimationSession(
   UUID instanceId,
   Identifier animationId,
   List<UUID> actorUuids,
   @Nullable NonAnimationStage currentStage,
   NonAnimationDamageBehavior damageBehavior,
   boolean ignoreAttackers,
   Map<String, String> metadata
) {
   public NonAnimationSession {
      actorUuids = actorUuids == null ? List.of() : List.copyOf(actorUuids);
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
   }
}
