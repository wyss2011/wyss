package com.nonid.client;

import com.nonid.entity.PregnancyEggEntity;
import com.nonid.internal.animation.AnimationEngine;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.cache.GeckoLibResources;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class PregnancyEggGeoModel extends GeoModel<PregnancyEggEntity> {
   public static final DataTicket<@NotNull Identifier> SOURCE_ENTITY_TYPE_TICKET = DataTicket.create("non_pregnancy_egg_source_entity_type", Identifier.class);
   public static final DataTicket<@NotNull Float> GROWTH_SCALE_TICKET = DataTicket.create("non_pregnancy_egg_growth_scale", Float.class);
   public static final DataTicket<@NotNull Float> HURT_SHAKE_TICKET = DataTicket.create("non_pregnancy_egg_hurt_shake", Float.class);
   public static final DataTicket<@NotNull Identifier> EGG_TEXTURE_TICKET = DataTicket.create("non_pregnancy_egg_texture", Identifier.class);
   public static final Identifier DEFAULT_MODEL_ID = Identifier.of("needsofnature", "entity/pregnancy_egg/default");
   public static final Identifier DEFAULT_TEXTURE_ID = Identifier.of("needsofnature", "textures/entity/pregnancy_egg/default.png");
   private static final Identifier PLACEHOLDER_ANIMATION_ID = AnimationEngine.packId("afw/placeholder");
   private static final Map<Identifier, Boolean> TEXTURE_EXISTS_CACHE = new ConcurrentHashMap<>();

   @NotNull
   public Identifier getModelResource(@NotNull GeoRenderState renderState) {
      Identifier specific = resolveSpecificModelId(renderState);
      return specific != null && GeckoLibResources.getBakedModels().cache().containsKey(specific) ? specific : DEFAULT_MODEL_ID;
   }

   @NotNull
   public Identifier getTextureResource(@NotNull GeoRenderState renderState) {
      Identifier configured = (Identifier)renderState.getGeckolibData(EGG_TEXTURE_TICKET);
      if (configured != null) {
         return hasTexture(configured) ? configured : DEFAULT_TEXTURE_ID;
      }

      Identifier specific = resolveSpecificTextureId(renderState);
      return specific != null && hasTexture(specific) ? specific : DEFAULT_TEXTURE_ID;
   }

   @NotNull
   public Identifier getAnimationResource(PregnancyEggEntity animatable) {
      return PLACEHOLDER_ANIMATION_ID;
   }

   private static Identifier resolveSpecificModelId(GeoRenderState renderState) {
      Identifier sourceType = (Identifier)renderState.getGeckolibData(SOURCE_ENTITY_TYPE_TICKET);
      return sourceType == null ? null : modelIdForSourceEntity(sourceType);
   }

   private static Identifier resolveSpecificTextureId(GeoRenderState renderState) {
      Identifier sourceType = (Identifier)renderState.getGeckolibData(SOURCE_ENTITY_TYPE_TICKET);
      return sourceType == null ? null : Identifier.of("needsofnature", "textures/entity/pregnancy_egg/" + toFileStem(sourceType) + ".png");
   }

   static Identifier modelIdForSourceEntity(Identifier sourceType) {
      return Identifier.of("needsofnature", "entity/pregnancy_egg/" + toFileStem(sourceType));
   }

   private static String toFileStem(Identifier typeId) {
      String namespace = typeId.getNamespace();
      String path = typeId.getPath().replace('/', '_');
      return namespace + "_" + path;
   }

   private static boolean hasTexture(Identifier textureId) {
      return textureId == null ? false : TEXTURE_EXISTS_CACHE.computeIfAbsent(textureId, PregnancyEggGeoModel::findTexture);
   }

   public static void clearTextureCache() {
      TEXTURE_EXISTS_CACHE.clear();
   }

   private static boolean findTexture(Identifier textureId) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return false;
      }

      ResourceManager manager = client.getResourceManager();
      return manager == null ? false : manager.getResource(textureId).isPresent();
   }
}
