package com.nonid.internal.animation.mixin.client;

import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.equipment.EquipmentModelLoader;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderManager.class)
public interface EntityRenderManagerAccessor {
   @Accessor("equipmentModelLoader")
   EquipmentModelLoader afw$getEquipmentModelLoader();

   @Invoker("getRenderer")
   EntityRenderer<?, ?> afw$getRenderer(EntityRenderState var1);
}
