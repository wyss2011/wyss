package com.nonid.internal.animation.client.render.gecko;

import com.nonid.GenderHolder;
import com.nonid.api.client.NonAnimationRenderEvents;
import com.nonid.internal.animation.AnimationEngine;
import com.nonid.internal.animation.client.compat.wildfire.WildfireGenderCompat;
import com.nonid.internal.animation.client.compat.wildfire.WildfireGenderState;
import com.nonid.internal.animation.client.render.AnimationRenderStateAccess;
import com.nonid.internal.animation.client.render.gecko.layer.BoneItemGeoLayer;
import com.nonid.internal.animation.client.render.gecko.layer.BoneTextureGeoLayer;
import com.nonid.internal.animation.client.render.gecko.layer.CameraPoseGeoLayer;
import com.nonid.internal.animation.client.render.gecko.layer.EmissiveTextureGeoLayer;
import com.nonid.internal.animation.client.render.gecko.layer.LayerTextureGeoLayer;
import com.nonid.internal.animation.client.render.gecko.layer.ParticleKeyframeGeoLayer;
import com.nonid.internal.animation.client.render.gecko.layer.TranslucentBoneGeoLayer;
import com.nonid.internal.animation.client.render.gecko.layer.WildfireGenderGeoLayer;
import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import com.nonid.internal.animation.mixin.client.EntityRenderManagerAccessor;
import com.nonid.internal.animation.util.EntityVariants;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class GeckoReplacedRender {
   private static final ConcurrentHashMap<Identifier, GeoReplacedEntityRenderer> RENDERERS = new ConcurrentHashMap<>();
   private static final Identifier UNKNOWN_RENDERER_KEY = AnimationEngine.runtimeId("unknown_renderer");
   private static final Identifier PLAYER_TYPE_ID = Identifier.of("minecraft", "player");
   private static final String PLAYER_HEAD_BONE = "head";
   private static final float ACTIVE_MODEL_SCALE = 0.96F;
   private static final ConcurrentHashMap<Class<?>, Method> TEXTURE_METHOD_CACHE = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<Class<?>, Field> TEXTURE_FIELD_CACHE = new ConcurrentHashMap<>();
   private static final Set<String> LOGGED_TEXTURE_RESOLUTION_ERRORS = Collections.newSetFromMap(new ConcurrentHashMap<>());

   private GeckoReplacedRender() {
   }

   private static GeoReplacedEntityRenderer getOrCreateRenderer(Identifier entityTypeId, Identifier modelId) {
      Identifier key = modelId != null ? modelId : (entityTypeId != null ? entityTypeId : UNKNOWN_RENDERER_KEY);
      GeoReplacedEntityRenderer existing = RENDERERS.get(key);
      if (existing != null) {
         return existing;
      } else {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client == null) {
            return null;
         } else {
            EntityRenderManager renderManager = client.getEntityRenderDispatcher();
            if (renderManager instanceof EntityRenderManagerAccessor accessor) {
               Context context = new Context(
                  renderManager,
                  client.getItemModelManager(),
                  client.getMapRenderer(),
                  client.getBlockRenderManager(),
                  client.getResourceManager(),
                  client.getLoadedEntityModels(),
                  accessor.afw$getEquipmentModelLoader(),
                  client.getAtlasManager(),
                  client.textRenderer,
                  client.getPlayerSkinCache()
               );
               GeoReplacedEntityRenderer created = new ReplacedEntityRenderer<>(context, new AnimationActorGeoModel(), AnimationActorAnimatable.INSTANCE);
               created.withRenderLayer(new LayerTextureGeoLayer(created));
               created.withRenderLayer(new BoneTextureGeoLayer(created));
               created.withRenderLayer(new TranslucentBoneGeoLayer(created));
               created.withRenderLayer(new EmissiveTextureGeoLayer(created));
               created.withRenderLayer(new BoneItemGeoLayer(created));
               created.withRenderLayer(new ParticleKeyframeGeoLayer(created));
               created.withRenderLayer(new WildfireGenderGeoLayer(created));
               created.withRenderLayer(new CameraPoseGeoLayer(created));
               created.fireCompileRenderLayersEvent();
               GeoReplacedEntityRenderer prior = RENDERERS.putIfAbsent(key, created);
               return prior != null ? prior : created;
            } else {
               return null;
            }
         }
      }
   }

   public static void prepare(LivingEntity entity, LivingEntityRenderState state, float tickDelta) {
      if (state instanceof GeoRenderState geoState) {
         geoState.addGeckolibData(GeckoRenderTickets.ACTOR_UUID, entity.getUuid());
         Identifier entityTypeId = ((AnimationRenderStateAccess)state).afw$getEntityTypeId();
         Identifier animationId = ClientAnimationRuntime.findLatestActiveAnimationIdContaining(entity.getUuid());
         List modelRoots = ClientAnimationRuntime.findLatestModelRootsForActor(entity.getUuid());
         UUID animationInstanceId = ClientAnimationRuntime.findLatestActiveInstanceContaining(entity.getUuid());
         Identifier animationResource = null;
         double animationSpeed = 1.0;
         if (animationId != null) {
            geoState.addGeckolibData(GeckoRenderTickets.ANIMATION_ID, animationId);
            if (animationInstanceId != null) {
               geoState.addGeckolibData(GeckoRenderTickets.ANIMATION_INSTANCE_ID, animationInstanceId);
            }

            String actorKey = ClientAnimationRuntime.findLatestActiveActorKey(entity.getUuid());
            animationSpeed = ClientAnimationRuntime.findLatestSpeedForActor(entity.getUuid());
            animationResource = GeckoResourceResolver.resolveAnimationResource(animationId, actorKey, entityTypeId);
            if (animationResource != null) {
               geoState.addGeckolibData(GeckoRenderTickets.ANIMATION_RESOURCE_ID, animationResource);
               String animationKey = GeckoResourceResolver.resolveAnimationKey(animationResource, animationId, actorKey, entityTypeId);
               geoState.addGeckolibData(GeckoRenderTickets.ANIMATION_KEY, animationKey == null ? "" : animationKey);
            }

            Double timelineSeconds = ClientAnimationRuntime.findWarpedAuthoredTimelineSeconds(entity.getUuid(), tickDelta);
            if (timelineSeconds != null && Double.isFinite(timelineSeconds)) {
               geoState.addGeckolibData(GeckoRenderTickets.ANIMATION_TIMELINE_SECONDS, timelineSeconds);
            }
         }

         geoState.addGeckolibData(GeckoRenderTickets.ANIMATION_SPEED, animationSpeed);
         List<String> preferredModelPaths = preferredModelPaths(entity, entityTypeId);
         GenderedModelResolver.ModelGender modelGender = entity instanceof GenderHolder holder
            ? GenderedModelResolver.fromMask(holder.getGenderMask())
            : GenderedModelResolver.ModelGender.MALE;
         GeckoResourceResolver.ModelAndTexture modelAndTex = GeckoResourceResolver.resolveModelAndTexture(
            entityTypeId, modelRoots, preferredModelPaths, modelGender
         );
         Identifier modelId = modelAndTex.model();
         Identifier fallbackTexture = modelAndTex.texture();
         boolean missingModel = modelAndTex.missingModel();
         boolean explicitTextureOverride = false;
         NonAnimationRenderEvents.ModelOverride override = ((NonAnimationRenderEvents.ModelResolver)NonAnimationRenderEvents.RESOLVE.invoker())
            .resolve(entity, entityTypeId, modelId, fallbackTexture);
         if (override != null) {
            Identifier overrideModel = override.model();
            Identifier overrideTexture = override.texture();
            if (overrideModel != null && GeckoResourceResolver.hasGeoModel(overrideModel)) {
               modelId = overrideModel;
               missingModel = false;
            }

            if (overrideTexture != null) {
               fallbackTexture = overrideTexture;
               explicitTextureOverride = true;
            }
         }

         Identifier vanillaTexture = resolveVanillaTexture(entity, state);
         Identifier currentTexture = !missingModel && vanillaTexture != null && !explicitTextureOverride ? vanillaTexture : fallbackTexture;
         NonAnimationRenderEvents.RenderOverride renderOverride = ((NonAnimationRenderEvents.RenderResolver)NonAnimationRenderEvents.RESOLVE_RENDER.invoker())
            .resolve(entity, entityTypeId, modelId, currentTexture);
         List<Identifier> layerTextures = List.of();
         Map<String, Identifier> boneTextures = Map.of();
         Map<String, NonAnimationRenderEvents.BoneItemProp> runtimeBoneItems = ClientAnimationRuntime.findLatestBoneItemsForActor(entity.getUuid());
         Map<String, NonAnimationRenderEvents.BoneItemProp> boneItems = runtimeBoneItems;
         Map<String, Boolean> boneVisibility = Map.of();
         Map<String, Set<Integer>> hiddenBoneCubeIndices = Map.of();
         if (renderOverride != null) {
            Identifier overrideModel = renderOverride.model();
            Identifier overrideTexture = renderOverride.texture();
            if (overrideModel != null && GeckoResourceResolver.hasGeoModel(overrideModel)) {
               modelId = overrideModel;
               missingModel = false;
            }

            if (overrideTexture != null) {
               fallbackTexture = overrideTexture;
               explicitTextureOverride = true;
            }

            layerTextures = renderOverride.layerTextures();
            boneTextures = renderOverride.boneTextures();
            boneItems = mergeBoneItems(runtimeBoneItems, renderOverride.boneItems());
            boneVisibility = renderOverride.boneVisibility();
            hiddenBoneCubeIndices = renderOverride.hiddenBoneCubeIndices();
         }

         if (modelId == null || !GeckoResourceResolver.hasGeoModel(modelId)) {
            GeckoResourceResolver.ModelAndTexture missing = GeckoResourceResolver.missingModelFallback(entityTypeId);
            modelId = missing.model();
            fallbackTexture = missing.texture();
            missingModel = true;
         }

         Map<String, Identifier> resourceBoneTextures = BoneTextureOverrides.getBoneTextures(modelId);
         boneTextures = mergeBoneTextures(resourceBoneTextures, boneTextures);
         List<Identifier> emissiveTextures = sanitizeLayerTextures(BoneTextureOverrides.getEmissiveTextures(modelId));
         Map<String, Identifier> emissiveBoneTextures = sanitizeBoneTextures(BoneTextureOverrides.getEmissiveBoneTextures(modelId));
         layerTextures = sanitizeLayerTextures(layerTextures);
         boneTextures = sanitizeBoneTextures(boneTextures);
         boneItems = sanitizeBoneItems(boneItems);
         boneVisibility = sanitizeBoneVisibility(boneVisibility);
         hiddenBoneCubeIndices = sanitizeHiddenBoneCubeIndices(hiddenBoneCubeIndices);
         if (shouldHideLocalPlayerHeadInFirstPerson(entity)) {
            boneVisibility = forceHiddenBone(boneVisibility, "head");
         }

         GeoReplacedEntityRenderer renderer = getOrCreateRenderer(entityTypeId, modelId);
         if (renderer != null) {
            geoState.addGeckolibData(GeckoRenderTickets.MODEL_ID, modelId);
            Identifier finalTexture = explicitTextureOverride ? fallbackTexture : (!missingModel && vanillaTexture != null ? vanillaTexture : fallbackTexture);
            finalTexture = VanillaTextureResolver.resolveTexture(finalTexture);
            geoState.addGeckolibData(GeckoRenderTickets.TEXTURE_ID, finalTexture);
            if (PLAYER_TYPE_ID.equals(entityTypeId)) {
               WildfireGenderState wildfireGenderState = WildfireGenderCompat.resolve(entity, state);
               if (wildfireGenderState != null) {
                  geoState.addGeckolibData(GeckoRenderTickets.WILDFIRE_GENDER_STATE, wildfireGenderState);
               }
            }

            geoState.addGeckolibData(GeckoRenderTickets.LAYER_TEXTURES, layerTextures);
            geoState.addGeckolibData(GeckoRenderTickets.EMISSIVE_TEXTURES, emissiveTextures);
            geoState.addGeckolibData(GeckoRenderTickets.BONE_TEXTURES, boneTextures);
            geoState.addGeckolibData(GeckoRenderTickets.EMISSIVE_BONE_TEXTURES, emissiveBoneTextures);
            geoState.addGeckolibData(GeckoRenderTickets.BONE_ITEMS, boneItems);
            geoState.addGeckolibData(GeckoRenderTickets.BONE_VISIBILITY, boneVisibility);
            geoState.addGeckolibData(GeckoRenderTickets.HIDDEN_BONE_CUBE_INDICES, hiddenBoneCubeIndices);
            geoState.addGeckolibData(GeckoRenderTickets.TRANSLUCENT_RENDER, BoneTextureOverrides.isTranslucent(modelId));
            geoState.addGeckolibData(GeckoRenderTickets.TRANSLUCENT_BONES, BoneTextureOverrides.getTranslucentBones(modelId));

            try {
               if (animationResource != null) {
                  AnimationActorGeoModel.setCurrentAnimationResource(animationResource);
               }

               renderer.fillRenderState(AnimationActorAnimatable.INSTANCE, entity, geoState, tickDelta);
            } catch (RuntimeException e) {
               AnimationEngine.LOGGER.error("[NoN Animation] GeckoLib prepare failed", e);
            } finally {
               AnimationActorGeoModel.clearCurrentAnimationResource();
            }
         }
      }
   }

   private static List<Identifier> sanitizeLayerTextures(List<Identifier> textures) {
      if (textures != null && !textures.isEmpty()) {
         List<Identifier> resolved = new ArrayList<>(textures.size());

         for (Identifier texture : textures) {
            if (texture != null) {
               resolved.add(VanillaTextureResolver.resolveTexture(texture));
            }
         }

         return resolved.isEmpty() ? List.of() : List.copyOf(resolved);
      } else {
         return List.of();
      }
   }

   private static Map<String, Identifier> sanitizeBoneTextures(Map<String, Identifier> textures) {
      if (textures != null && !textures.isEmpty()) {
         Map<String, Identifier> resolved = new LinkedHashMap<>();

         for (Entry<String, Identifier> entry : textures.entrySet()) {
            String bone = entry.getKey();
            Identifier texture = entry.getValue();
            if (bone != null && !bone.isBlank() && texture != null) {
               resolved.put(bone, VanillaTextureResolver.resolveTexture(texture));
            }
         }

         return resolved.isEmpty() ? Map.of() : Map.copyOf(resolved);
      } else {
         return Map.of();
      }
   }

   private static Map<String, NonAnimationRenderEvents.BoneItemProp> sanitizeBoneItems(Map<String, NonAnimationRenderEvents.BoneItemProp> items) {
      if (items != null && !items.isEmpty()) {
         Map<String, NonAnimationRenderEvents.BoneItemProp> resolved = new LinkedHashMap<>();

         for (Entry<String, NonAnimationRenderEvents.BoneItemProp> entry : items.entrySet()) {
            String bone = entry.getKey();
            NonAnimationRenderEvents.BoneItemProp prop = entry.getValue();
            if (bone != null && !bone.isBlank() && prop != null && !prop.stack().isEmpty()) {
               resolved.put(bone, new NonAnimationRenderEvents.BoneItemProp(prop.stack(), prop.displayContext()));
            }
         }

         return resolved.isEmpty() ? Map.of() : Map.copyOf(resolved);
      } else {
         return Map.of();
      }
   }

   private static Map<String, NonAnimationRenderEvents.BoneItemProp> mergeBoneItems(
      Map<String, NonAnimationRenderEvents.BoneItemProp> baseItems, Map<String, NonAnimationRenderEvents.BoneItemProp> overrideItems
   ) {
      boolean baseEmpty = baseItems == null || baseItems.isEmpty();
      boolean overrideEmpty = overrideItems == null || overrideItems.isEmpty();
      if (baseEmpty && overrideEmpty) {
         return Map.of();
      }

      if (baseEmpty) {
         return Map.copyOf(overrideItems);
      }

      if (overrideEmpty) {
         return Map.copyOf(baseItems);
      }

      LinkedHashMap<String, NonAnimationRenderEvents.BoneItemProp> merged = new LinkedHashMap<>(baseItems);
      merged.putAll(overrideItems);
      return Map.copyOf(merged);
   }

   private static Map<String, Identifier> mergeBoneTextures(Map<String, Identifier> baseTextures, Map<String, Identifier> overrideTextures) {
      boolean baseEmpty = baseTextures == null || baseTextures.isEmpty();
      boolean overrideEmpty = overrideTextures == null || overrideTextures.isEmpty();
      if (baseEmpty && overrideEmpty) {
         return Map.of();
      }

      if (baseEmpty) {
         return Map.copyOf(overrideTextures);
      }

      if (overrideEmpty) {
         return Map.copyOf(baseTextures);
      }

      LinkedHashMap<String, Identifier> merged = new LinkedHashMap<>(baseTextures);
      merged.putAll(overrideTextures);
      return Map.copyOf(merged);
   }

   private static Map<String, Boolean> sanitizeBoneVisibility(Map<String, Boolean> visibility) {
      if (visibility != null && !visibility.isEmpty()) {
         Map<String, Boolean> resolved = new LinkedHashMap<>();

         for (Entry<String, Boolean> entry : visibility.entrySet()) {
            String bone = entry.getKey();
            Boolean visible = entry.getValue();
            if (bone != null && !bone.isBlank() && visible != null) {
               resolved.put(bone, visible);
            }
         }

         return resolved.isEmpty() ? Map.of() : Map.copyOf(resolved);
      } else {
         return Map.of();
      }
   }

   private static Map<String, Set<Integer>> sanitizeHiddenBoneCubeIndices(Map<String, Set<Integer>> hiddenIndices) {
      if (hiddenIndices != null && !hiddenIndices.isEmpty()) {
         Map<String, Set<Integer>> resolved = new LinkedHashMap<>();

         for (Entry<String, Set<Integer>> entry : hiddenIndices.entrySet()) {
            String bone = entry.getKey();
            Set<Integer> indices = entry.getValue();
            if (bone != null && !bone.isBlank() && indices != null && !indices.isEmpty()) {
               Set<Integer> cleanIndices = new LinkedHashSet<>();

               for (Integer index : indices) {
                  if (index != null && index >= 0) {
                     cleanIndices.add(index);
                  }
               }

               if (!cleanIndices.isEmpty()) {
                  resolved.put(bone, Set.copyOf(cleanIndices));
               }
            }
         }

         return resolved.isEmpty() ? Map.of() : Map.copyOf(resolved);
      } else {
         return Map.of();
      }
   }

   private static Map<String, Boolean> forceHiddenBone(Map<String, Boolean> visibility, String boneName) {
      if (boneName == null || boneName.isBlank()) {
         return visibility;
      } else if (visibility != null && !visibility.isEmpty()) {
         Map<String, Boolean> merged = new LinkedHashMap<>(visibility);
         merged.put(boneName, Boolean.FALSE);
         return Map.copyOf(merged);
      } else {
         return Map.of(boneName, Boolean.FALSE);
      }
   }

   private static List<String> preferredModelPaths(LivingEntity entity, Identifier entityTypeId) {
      if (entityTypeId == null) {
         return List.of();
      }

      String basePath = entityTypeId.getPath();
      List<String> paths = new ArrayList<>();
      if (PLAYER_TYPE_ID.equals(entityTypeId) && entity instanceof AbstractClientPlayerEntity player && isSlimPlayerModel(player)) {
         paths.add("player_slim");
      }

      String variant = EntityVariants.resolveVariant(entity);
      if (variant != null && !variant.isBlank()) {
         paths.add(basePath + "_" + variant);
      }

      paths.add(basePath);
      return List.copyOf(new LinkedHashSet<>(paths));
   }

   private static boolean shouldHideLocalPlayerHeadInFirstPerson(LivingEntity entity) {
      if (entity instanceof AbstractClientPlayerEntity playerEntity) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null && client.options != null) {
            if (!playerEntity.getUuid().equals(client.player.getUuid())) {
               return false;
            } else {
               return client.options.getPerspective() != Perspective.FIRST_PERSON
                  ? false
                  : ClientAnimationRuntime.isActorPendingOrActive(playerEntity.getUuid());
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean render(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
      if (state instanceof GeoRenderState geoState) {
         Identifier entityTypeId = null;
         if (state instanceof AnimationRenderStateAccess access) {
            entityTypeId = access.afw$getEntityTypeId();
         }

         Identifier modelId = (Identifier)geoState.getGeckolibData(GeckoRenderTickets.MODEL_ID);
         GeoReplacedEntityRenderer renderer = getOrCreateRenderer(entityTypeId, modelId);
         if (renderer == null) {
            return false;
         }

         try {
            matrices.push();

            try {
               matrices.scale(0.96F, 0.96F, 0.96F);
               renderer.performRenderPass(geoState, matrices, queue, camera);
            } finally {
               matrices.pop();
            }

            return true;
         } catch (RuntimeException e) {
            AnimationEngine.LOGGER.error("[NoN Animation] GeckoLib render failed", e);
            return false;
         }
      } else {
         return false;
      }
   }

   private static Identifier resolveVanillaTexture(LivingEntity entity, LivingEntityRenderState renderState) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return null;
      }

      if (entity instanceof AbstractClientPlayerEntity playerEntity) {
         try {
            SkinTextures skin = playerEntity.getSkin();
            if (skin != null && skin.body() != null) {
               Identifier skinTexture = skin.body().texturePath();
               if (skinTexture != null) {
                  return skinTexture;
               }
            }
         } catch (RuntimeException ex) {
            String key = "player|" + playerEntity.getUuidAsString();
            if (LOGGED_TEXTURE_RESOLUTION_ERRORS.add(key)) {
               AnimationEngine.LOGGER.debug("[NoN Animation] Failed to resolve player skin texture for {}", key, ex);
            }
         }
      }

      try {
         EntityRenderManager renderManager = client.getEntityRenderDispatcher();
         EntityRenderer<?, ?> vanillaRenderer = null;
         if (renderManager instanceof EntityRenderManagerAccessor accessor) {
            vanillaRenderer = accessor.afw$getRenderer(renderState);
         }

         if (vanillaRenderer == null) {
            vanillaRenderer = renderManager.getRenderer(entity);
         }

         if (vanillaRenderer != null) {
            Method m = TEXTURE_METHOD_CACHE.computeIfAbsent(vanillaRenderer.getClass(), cls -> {
               Method found = findTextureMethod(cls.getDeclaredMethods(), renderState, entity);
               return found != null ? found : findTextureMethod(cls.getMethods(), renderState, entity);
            });
            if (m != null) {
               Object arg = null;
               if (m.getParameterCount() == 1) {
                  Class<?> param = m.getParameterTypes()[0];
                  if (param.isAssignableFrom(renderState.getClass())) {
                     arg = renderState;
                  } else if (param.isAssignableFrom(entity.getClass())) {
                     arg = entity;
                  }
               }

               if ((m.getParameterCount() == 0 ? m.invoke(vanillaRenderer) : m.invoke(vanillaRenderer, arg)) instanceof Identifier id) {
                  return id;
               }
            }
         }

         Field f = TEXTURE_FIELD_CACHE.computeIfAbsent(renderState.getClass(), cls -> {
            for (Class<?> current = (Class<?>)cls; current != null; current = current.getSuperclass()) {
               for (Field field : current.getDeclaredFields()) {
                  if (Identifier.class.isAssignableFrom(field.getType()) && !Modifier.isStatic(field.getModifiers())) {
                     field.setAccessible(true);
                     return field;
                  }
               }
            }

            return null;
         });
         if (f != null && f.get(renderState) instanceof Identifier id && isUsableTextureId(client, id)) {
            return id;
         }
      } catch (ReflectiveOperationException | RuntimeException ex) {
         String key = entity.getType().toString() + "|" + renderState.getClass().getName();
         if (LOGGED_TEXTURE_RESOLUTION_ERRORS.add(key)) {
            AnimationEngine.LOGGER.debug("[NoN Animation] Failed to reflect vanilla texture for {}", key, ex);
         }
      }

      return null;
   }

   private static Method findTextureMethod(Method[] methods, LivingEntityRenderState renderState, LivingEntity entity) {
      for (Method method : methods) {
         if (!Modifier.isStatic(method.getModifiers()) && Identifier.class.isAssignableFrom(method.getReturnType())) {
            int params = method.getParameterCount();
            switch (params) {
               case 0:
                  method.setAccessible(true);
                  return method;
               case 1:
                  Class<?> param = method.getParameterTypes()[0];
                  if (param.isAssignableFrom(renderState.getClass())
                     || param.isAssignableFrom(entity.getClass())
                     || LivingEntity.class.isAssignableFrom(param)
                     || Entity.class.isAssignableFrom(param)) {
                     method.setAccessible(true);
                     return method;
                  }
            }
         }
      }

      return null;
   }

   private static boolean isUsableTextureId(MinecraftClient client, Identifier id) {
      if (id == null) {
         return false;
      }

      String path = id.getPath();
      if (path == null || !path.contains("textures/") && !path.endsWith(".png")) {
         try {
            return client.getResourceManager().getResource(id).isPresent();
         } catch (RuntimeException ex) {
            return false;
         }
      } else {
         return true;
      }
   }

   private static boolean isSlimPlayerModel(AbstractClientPlayerEntity playerEntity) {
      if (playerEntity == null) {
         return false;
      }

      try {
         SkinTextures skin = playerEntity.getSkin();
         return skin != null && skin.model() == PlayerSkinType.SLIM;
      } catch (RuntimeException ex) {
         String key = "skin_model|" + playerEntity.getUuidAsString();
         if (LOGGED_TEXTURE_RESOLUTION_ERRORS.add(key)) {
            AnimationEngine.LOGGER.debug("[NoN Animation] Failed to resolve skin model from client API for {}", key, ex);
         }

         return false;
      }
   }
}
