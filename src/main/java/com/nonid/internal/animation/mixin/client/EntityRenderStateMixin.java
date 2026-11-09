package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.render.AnimationRenderStateAccess;
import java.util.UUID;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements AnimationRenderStateAccess {
   @Unique
   private UUID afw$entityUuid;
   @Unique
   private Identifier afw$entityTypeId;

   @Override
   public UUID afw$getEntityUuid() {
      return this.afw$entityUuid;
   }

   @Override
   public void afw$setEntityUuid(UUID uuid) {
      this.afw$entityUuid = uuid;
   }

   @Override
   public Identifier afw$getEntityTypeId() {
      return this.afw$entityTypeId;
   }

   @Override
   public void afw$setEntityTypeId(Identifier id) {
      this.afw$entityTypeId = id;
   }
}
