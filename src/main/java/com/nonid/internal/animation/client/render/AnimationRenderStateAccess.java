package com.nonid.internal.animation.client.render;

import java.util.UUID;
import net.minecraft.util.Identifier;

public interface AnimationRenderStateAccess {
   UUID afw$getEntityUuid();

   void afw$setEntityUuid(UUID var1);

   Identifier afw$getEntityTypeId();

   void afw$setEntityTypeId(Identifier var1);
}
