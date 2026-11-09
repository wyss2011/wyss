package com.nonid.api.client;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NonAnimationRenderEvents {
   public static final Event<NonAnimationRenderEvents.@NotNull ModelResolver> RESOLVE = EventFactory.createArrayBacked(
      NonAnimationRenderEvents.ModelResolver.class, callbacks -> (entity, entityTypeId, currentModel, currentTexture) -> {
         for (NonAnimationRenderEvents.ModelResolver callback : callbacks) {
            NonAnimationRenderEvents.ModelOverride resolved = callback.resolve(entity, entityTypeId, currentModel, currentTexture);
            if (resolved != null) {
               return resolved;
            }
         }

         return null;
      }
   );
   public static final Event<NonAnimationRenderEvents.@NotNull RenderResolver> RESOLVE_RENDER = EventFactory.createArrayBacked(
      NonAnimationRenderEvents.RenderResolver.class, callbacks -> (entity, entityTypeId, currentModel, currentTexture) -> {
         for (NonAnimationRenderEvents.RenderResolver callback : callbacks) {
            NonAnimationRenderEvents.RenderOverride resolved = callback.resolve(entity, entityTypeId, currentModel, currentTexture);
            if (resolved != null) {
               return resolved;
            }
         }

         return null;
      }
   );

   private NonAnimationRenderEvents() {
   }

   public record BoneItemProp(ItemStack stack, ItemDisplayContext displayContext) {
      public BoneItemProp(ItemStack stack) {
         this(stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
      }

      public BoneItemProp {
         stack = stack == null ? ItemStack.EMPTY : stack.copy();
         displayContext = displayContext == null ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : displayContext;
      }
   }

   public record ModelOverride(@Nullable Identifier model, @Nullable Identifier texture) {
   }

   @FunctionalInterface
   public interface ModelResolver {
      @Nullable
      NonAnimationRenderEvents.ModelOverride resolve(LivingEntity var1, @Nullable Identifier var2, Identifier var3, Identifier var4);
   }

   public record RenderOverride(
      @Nullable Identifier model,
      @Nullable Identifier texture,
      @Nullable List<Identifier> layerTextures,
      @Nullable Map<String, Identifier> boneTextures,
      @Nullable Map<String, NonAnimationRenderEvents.BoneItemProp> boneItems,
      @Nullable Map<String, Boolean> boneVisibility,
      @Nullable Map<String, Set<Integer>> hiddenBoneCubeIndices
   ) {
      public RenderOverride(@Nullable Identifier model, @Nullable Identifier texture) {
         this(model, texture, null, null, null, null, null);
      }

      public RenderOverride(@Nullable List<Identifier> layerTextures, @Nullable Map<String, Identifier> boneTextures) {
         this(null, null, layerTextures, boneTextures, null, null, null);
      }

      public RenderOverride(
         @Nullable List<Identifier> layerTextures,
         @Nullable Map<String, Identifier> boneTextures,
         @Nullable Map<String, NonAnimationRenderEvents.BoneItemProp> boneItems
      ) {
         this(null, null, layerTextures, boneTextures, boneItems, null, null);
      }

      public RenderOverride(
         @Nullable Identifier model,
         @Nullable Identifier texture,
         @Nullable List<Identifier> layerTextures,
         @Nullable Map<String, Identifier> boneTextures,
         @Nullable Map<String, NonAnimationRenderEvents.BoneItemProp> boneItems,
         @Nullable Map<String, Boolean> boneVisibility
      ) {
         this(model, texture, layerTextures, boneTextures, boneItems, boneVisibility, null);
      }
   }

   @FunctionalInterface
   public interface RenderResolver {
      @Nullable
      NonAnimationRenderEvents.RenderOverride resolve(LivingEntity var1, @Nullable Identifier var2, Identifier var3, Identifier var4);
   }
}
