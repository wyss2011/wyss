package com.nonid.internal.animation.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class EntityVariants {
   private EntityVariants() {
   }

   @Nullable
   public static String resolveVariant(Entity entity) {
      return entity instanceof SlimeEntity slime ? "size_" + Math.max(0, slime.getSize() - 1) : null;
   }

   @Nullable
   public static Identifier resolveVariantModel(LivingEntity entity, @Nullable Identifier baseModelId) {
      String variant = resolveVariant(entity);
      return variant == null ? null : variantModelId(baseModelId, variant);
   }

   @Nullable
   public static Identifier variantModelId(@Nullable Identifier baseModelId, @Nullable String variant) {
      return baseModelId != null && variant != null && !variant.isBlank()
         ? Identifier.of(baseModelId.getNamespace(), baseModelId.getPath() + "_" + variant)
         : null;
   }
}
