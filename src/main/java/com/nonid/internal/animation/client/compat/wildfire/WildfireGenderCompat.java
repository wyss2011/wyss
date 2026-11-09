package com.nonid.internal.animation.client.compat.wildfire;

import com.nonid.internal.animation.AnimationEngine;
import com.nonid.internal.animation.mixin.client.EntityRenderManagerAccessor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class WildfireGenderCompat {
   private static final String MOD_ID = "wildfire_gender";
   private static final WildfireGenderState.UvRect DEFAULT_LEFT_BASE_UV = WildfireGenderState.UvRect.pixels(20, 20, 24, 26);
   private static final WildfireGenderState.UvRect DEFAULT_RIGHT_BASE_UV = WildfireGenderState.UvRect.pixels(24, 20, 28, 26);
   private static final WildfireGenderState.UvRect DEFAULT_LEFT_OVERLAY_UV = WildfireGenderState.UvRect.pixels(20, 36, 24, 42);
   private static final WildfireGenderState.UvRect DEFAULT_RIGHT_OVERLAY_UV = WildfireGenderState.UvRect.pixels(24, 36, 28, 42);
   private static final float POSITION_X_MIN = -1.25F;
   private static final float POSITION_X_MAX = 1.25F;
   private static final float POSITION_Y_MIN = -1.25F;
   private static final float POSITION_Y_MAX = 1.75F;
   private static final float POSITION_REFERENCE_DELTA = 0.08F;
   private static final float POSITION_RESPONSE_EXPONENT = 0.55F;
   private static final float PHYSICS_TARGET_RESPONSE = 0.1F;
   private static final float GRAVITY_MOVEMENT_BOOST = 0.8F;
   private static final float GRAVITY_SIDE_POSITION_SCALE = 0.35F;
   private static final float GRAVITY_FORWARD_POSITION_SCALE = 0.45F;
   private static final float LOCAL_FORWARD_VERTICAL_SCALE = 0.4F;
   private static final float ROTATION_YAW_DIVISOR = 15.0F;
   private static final float ROTATION_VERTICAL_SCALE = 18.0F;
   private static final float ROTATION_LATERAL_SCALE = 16.0F;
   private static final float ROTATION_FORWARD_SCALE = 20.0F;
   private static final float GRAVITY_SIDE_ROTATION_SCALE = 4.0F;
   private static final float GRAVITY_FORWARD_ROTATION_SCALE = 6.0F;
   private static final float ROTATION_MIN = -25.0F;
   private static final float ROTATION_MAX = 25.0F;
   private static final float CHEST_SAMPLE_LOCAL_X = 0.0F;
   private static final float CHEST_SAMPLE_LOCAL_Y = 0.3F;
   private static final float CHEST_SAMPLE_LOCAL_Z = -0.35F;
   private static final float BOUNCE_INTENSITY_SCALE = 12.0F;
   private static final float BOUNCE_INTENSITY_MIN = 0.15F;
   private static final float BOUNCE_INTENSITY_MAX = 3.0F;
   private static final float RENDER_GRAVITY_POSITION_X_SCALE = 0.45F;
   private static final float RENDER_GRAVITY_POSITION_Y_SCALE = 0.85F;
   private static final float RENDER_GRAVITY_ROTATION_SIDE_SCALE = 10.0F;
   private static final float RENDER_GRAVITY_ROTATION_FORWARD_SCALE = 2.0F;
   private static final float RENDER_GRAVITY_POSITION_X_LIMIT = 0.8F;
   private static final float RENDER_GRAVITY_POSITION_Y_LIMIT = 1.2F;
   private static final float RENDER_GRAVITY_ROTATION_LIMIT = 10.0F;
   private static boolean initialized;
   private static boolean available;
   private static boolean loggedFailure;
   private static Method getRenderState;
   private static Field genderField;
   private static Field bustSizeField;
   private static Field armorPhysicsOverrideField;
   private static Field showBreastsInArmorField;
   private static Field hasJacketLayerField;
   private static Field breastsField;
   private static Field leftPhysicsField;
   private static Field rightPhysicsField;
   private static Field leftUvField;
   private static Field rightUvField;
   private static Field leftOverlayUvField;
   private static Field rightOverlayUvField;
   private static Method genderCanHaveBreasts;
   private static Field breastXOffsetField;
   private static Field breastYOffsetField;
   private static Field breastZOffsetField;
   private static Field breastCleavageField;
   private static Field breastUniboobField;
   private static Method physicsPositionXMethod;
   private static Method physicsPositionYMethod;
   private static Method physicsBreastSizeMethod;
   private static Method physicsBounceRotationMethod;
   private static Field physicsPrePositionXField;
   private static Field physicsPositionXField;
   private static Field physicsPrePositionYField;
   private static Field physicsPositionYField;
   private static Field physicsPreBounceRotationField;
   private static Field physicsBounceRotationField;
   private static Method uvGetAllSidesMethod;
   private static Method uvQuadX1Method;
   private static Method uvQuadY1Method;
   private static Method uvQuadX2Method;
   private static Method uvQuadY2Method;
   private static Class<?> genderLayerClass;
   private static Method genderLayerSubmitMethod;
   private static Method contextModelMethod;
   private static Field modelBodyField;
   private static Field bodyOriginXField;
   private static Field bodyOriginYField;
   private static Field bodyOriginZField;
   private static Field bodyPitchField;
   private static Field bodyYawField;
   private static Field bodyRollField;
   private static Method entityConfigGetEntityMethod;
   private static Constructor<?> breastPhysicsConstructor;
   private static Method breastPhysicsFinishTickMethod;
   private static Field breastPhysicsTargetBounceXField;
   private static Field breastPhysicsTargetBounceYField;
   private static Field breastPhysicsTargetRotVelField;
   private static Field breastPhysicsPrePositionXField;
   private static Field breastPhysicsPositionXField;
   private static Field breastPhysicsPrePositionYField;
   private static Field breastPhysicsPositionYField;
   private static Field breastPhysicsPreBounceRotationField;
   private static Field breastPhysicsBounceRotationField;
   private static final Map<Object, Object> GENDER_LAYERS = new IdentityHashMap<>();
   private static final Map<UUID, WildfireGenderCompat.AnimationPhysicsPair> ANIMATION_PHYSICS = new HashMap<>();

   private WildfireGenderCompat() {
   }

   public static WildfireGenderState resolve(LivingEntity entity, LivingEntityRenderState renderState) {
      if (entity == null || renderState == null) {
         return null;
      }

      if (!isAvailable()) {
         return null;
      }

      try {
         Object wildfireState = getRenderState.invoke(null, renderState);
         if (wildfireState == null) {
            return null;
         }

         Object gender = genderField.get(wildfireState);
         if (!canHaveBreasts(gender)) {
            return null;
         }

         Object breasts = breastsField.get(wildfireState);
         if (breasts == null) {
            return null;
         }

         cacheAnimationPhysics(entity);
         float bustSize = getFloat(bustSizeField, wildfireState, 0.0F);
         if (bustSize <= 0.02F) {
            return null;
         }

         boolean hasJacketLayer = getBoolean(hasJacketLayerField, wildfireState, false);
         float xOffset = getFloat(breastXOffsetField, breasts, 0.0F);
         float yOffset = getFloat(breastYOffsetField, breasts, 0.0F);
         float zOffset = getFloat(breastZOffsetField, breasts, 0.0F);
         float cleavage = getFloat(breastCleavageField, breasts, 0.05F);
         boolean uniboob = getBoolean(breastUniboobField, breasts, false);
         return new WildfireGenderState(
            bustSize,
            xOffset,
            yOffset,
            zOffset,
            cleavage,
            uniboob,
            hasJacketLayer,
            readSide(
               leftPhysicsField.get(wildfireState),
               leftUvField.get(wildfireState),
               leftOverlayUvField.get(wildfireState),
               DEFAULT_LEFT_BASE_UV,
               DEFAULT_LEFT_OVERLAY_UV
            ),
            readSide(
               rightPhysicsField.get(wildfireState),
               rightUvField.get(wildfireState),
               rightOverlayUvField.get(wildfireState),
               DEFAULT_RIGHT_BASE_UV,
               DEFAULT_RIGHT_OVERLAY_UV
            )
         );
      } catch (ReflectiveOperationException | RuntimeException e) {
         logFailure("Failed to read Female Gender render state.", e);
         return null;
      }
   }

   public static boolean submitOriginalLayer(LivingEntityRenderState renderState, MatrixStack matrices, OrderedRenderCommandQueue queue, int packedLight) {
      if (renderState == null || matrices == null || queue == null) {
         return false;
      }

      if (!isAvailable()) {
         return false;
      }

      try {
         Object layer = getOrCreateOriginalLayer(renderState);
         if (layer != null && genderLayerSubmitMethod != null) {
            Object wildfireState = getRenderState.invoke(null, renderState);
            Boolean previousShowBreastsInArmor = forceShowBreastsInArmor(wildfireState);
            Boolean previousArmorPhysicsOverride = forceArmorPhysicsOverride(wildfireState);
            WildfireGenderCompat.RenderPoseSnapshot renderPose = neutralizeRenderPose(renderState);
            WildfireGenderCompat.BodyTransformSnapshot bodyTransform = neutralizeContextBodyTransform(layer);

            try {
               genderLayerSubmitMethod.invoke(layer, matrices, queue, packedLight, renderState, 0.0F, 0.0F);
               return true;
            } finally {
               restoreContextBodyTransform(bodyTransform);
               restoreRenderPose(renderState, renderPose);
               restoreShowBreastsInArmor(wildfireState, previousShowBreastsInArmor);
               restoreArmorPhysicsOverride(wildfireState, previousArmorPhysicsOverride);
            }
         } else {
            return false;
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         logFailure("Failed to submit Female Gender original render layer.", e);
         return false;
      }
   }

   @Nullable
   private static Boolean forceShowBreastsInArmor(@Nullable Object wildfireState) throws IllegalAccessException {
      if (wildfireState != null && showBreastsInArmorField != null) {
         boolean previous = showBreastsInArmorField.getBoolean(wildfireState);
         showBreastsInArmorField.setBoolean(wildfireState, true);
         return previous;
      } else {
         return null;
      }
   }

   private static void restoreShowBreastsInArmor(@Nullable Object wildfireState, @Nullable Boolean previous) throws IllegalAccessException {
      if (wildfireState != null && previous != null && showBreastsInArmorField != null) {
         showBreastsInArmorField.setBoolean(wildfireState, previous);
      }
   }

   @Nullable
   private static Boolean forceArmorPhysicsOverride(@Nullable Object wildfireState) throws IllegalAccessException {
      if (wildfireState != null && armorPhysicsOverrideField != null) {
         boolean previous = armorPhysicsOverrideField.getBoolean(wildfireState);
         armorPhysicsOverrideField.setBoolean(wildfireState, true);
         return previous;
      } else {
         return null;
      }
   }

   private static void restoreArmorPhysicsOverride(@Nullable Object wildfireState, @Nullable Boolean previous) throws IllegalAccessException {
      if (wildfireState != null && previous != null && armorPhysicsOverrideField != null) {
         armorPhysicsOverrideField.setBoolean(wildfireState, previous);
      }
   }

   public static void overrideRenderPhysics(UUID playerUuid, LivingEntityRenderState renderState, MatrixStack rootPose, MatrixStack bonePose) {
      if (playerUuid != null && renderState != null && rootPose != null && bonePose != null) {
         if (isAvailable()) {
            try {
               Object wildfireState = getRenderState.invoke(null, renderState);
               if (wildfireState == null) {
                  return;
               }

               WildfireGenderCompat.AnimationPhysicsPair physicsPair = ANIMATION_PHYSICS.get(playerUuid);
               if (physicsPair == null) {
                  return;
               }

               WildfireGenderCompat.BonePhysicsSample sample = WildfireGenderCompat.BonePhysicsSample.from(rootPose, bonePose);
               if (sample == null) {
                  return;
               }

               long worldTick = currentClientWorldTick();
               if (physicsPair.lastSample == null) {
                  physicsPair.lastSample = sample;
                  physicsPair.lastTick = worldTick;
               } else if (physicsPair.lastTick != worldTick) {
                  stepAnimationPhysics(wildfireState, physicsPair, physicsPair.lastSample, sample);
                  physicsPair.lastSample = sample;
                  physicsPair.lastTick = worldTick;
               }

               Object leftPhysics = leftPhysicsField.get(wildfireState);
               Object rightPhysics = rightPhysicsField.get(wildfireState);
               copyRealPhysicsToRenderState(leftPhysics, physicsPair.left, physicsPair.previousLeftBias, physicsPair.leftBias);
               copyRealPhysicsToRenderState(rightPhysics, physicsPair.right, physicsPair.previousRightBias, physicsPair.rightBias);
            } catch (ReflectiveOperationException | RuntimeException e) {
               logFailure("Failed to override Female Gender render physics from animated bone motion.", e);
            }
         }
      }
   }

   private static void stepAnimationPhysics(
      Object wildfireState,
      WildfireGenderCompat.AnimationPhysicsPair physicsPair,
      WildfireGenderCompat.BonePhysicsSample previous,
      WildfireGenderCompat.BonePhysicsSample current
   ) throws ReflectiveOperationException {
      float dx = current.chestX() - previous.chestX();
      float dy = current.chestY() - previous.chestY();
      float dz = current.chestZ() - previous.chestZ();
      Vec3d delta = new Vec3d(dx, dy, dz);
      float lateral = (float)delta.dotProduct(previous.right());
      float localUp = (float)delta.dotProduct(previous.up());
      float localForward = (float)delta.dotProduct(previous.forward());
      float yawDelta = signedYawDelta(previous.forward(), current.forward());
      WildfireGenderCompat.GravityBias gravity = WildfireGenderCompat.GravityBias.from(current);
      float movementBoost = 1.0F + gravity.tiltInfluence() * 0.8F;
      float verticalMovement = localUp + localForward * gravity.forwardDown() * 0.4F;
      float bustSize = getFloat(bustSizeField, wildfireState, 0.0F);
      float bounceIntensity = clamp(bustSize * 12.0F, 0.15F, 3.0F);
      float targetX = shapedImpulse(lateral * movementBoost, 0.08F, -1.25F, 1.25F, 0.55F);
      float targetY = shapedImpulse(-verticalMovement * movementBoost, 0.08F, -1.25F, 1.75F, 0.55F);
      targetX = clamp(targetX + gravity.sidePull() * 0.35F, -1.25F, 1.25F);
      targetY = clamp(targetY + gravity.forwardDown() * 0.45F, -1.25F, 1.75F);
      float targetRotVel = clamp(
         -(yawDelta / 15.0F) * bounceIntensity
            + -localUp * 18.0F * bounceIntensity
            + lateral * 16.0F * bounceIntensity
            + localForward * gravity.forwardDown() * 20.0F * bounceIntensity
            + gravity.sidePull() * 4.0F * bounceIntensity
            + gravity.forwardDown() * 6.0F * bounceIntensity,
         -25.0F,
         25.0F
      );
      boolean uniboob = isUniboob(wildfireState);
      physicsPair.stepRenderBias(gravity, uniboob);
      WildfireGenderCompat.TargetImpulse smoothedTarget = physicsPair.smoothTarget(targetX, targetY, targetRotVel);
      if (uniboob) {
         driveRealPhysics(physicsPair.left, smoothedTarget.x(), smoothedTarget.y(), 0.0F);
         driveRealPhysics(physicsPair.right, smoothedTarget.x(), smoothedTarget.y(), 0.0F);
      } else {
         driveRealPhysics(physicsPair.left, smoothedTarget.x(), smoothedTarget.y(), smoothedTarget.rotation());
         driveRealPhysics(physicsPair.right, -smoothedTarget.x() * 0.85F, smoothedTarget.y() * 0.95F, -smoothedTarget.rotation() * 0.85F);
      }
   }

   private static void cacheAnimationPhysics(LivingEntity entity) throws ReflectiveOperationException {
      if (entity != null && entityConfigGetEntityMethod != null) {
         Object config = entityConfigGetEntityMethod.invoke(null, entity);
         if (config != null) {
            UUID uuid = entity.getUuid();
            WildfireGenderCompat.AnimationPhysicsPair existing = ANIMATION_PHYSICS.get(uuid);
            if (existing == null || existing.config != config) {
               Object left = breastPhysicsConstructor.newInstance(config);
               Object right = breastPhysicsConstructor.newInstance(config);
               ANIMATION_PHYSICS.put(uuid, new WildfireGenderCompat.AnimationPhysicsPair(config, left, right));
            }
         }
      }
   }

   private static void driveRealPhysics(Object physics, float targetX, float targetY, float targetRotVel) throws ReflectiveOperationException {
      if (physics != null) {
         breastPhysicsPrePositionXField.setFloat(physics, breastPhysicsPositionXField.getFloat(physics));
         breastPhysicsPrePositionYField.setFloat(physics, breastPhysicsPositionYField.getFloat(physics));
         breastPhysicsPreBounceRotationField.setFloat(physics, breastPhysicsBounceRotationField.getFloat(physics));
         breastPhysicsTargetBounceXField.setFloat(physics, targetX);
         breastPhysicsTargetBounceYField.setFloat(physics, targetY);
         breastPhysicsTargetRotVelField.setFloat(physics, targetRotVel);
         breastPhysicsFinishTickMethod.invoke(physics);
      }
   }

   private static void copyRealPhysicsToRenderState(
      Object renderPhysics, Object realPhysics, WildfireGenderCompat.SideRenderBias previousBias, WildfireGenderCompat.SideRenderBias bias
   ) throws IllegalAccessException {
      if (renderPhysics != null && realPhysics != null) {
         if (physicsPositionXField != null && physicsPrePositionXField != null) {
            physicsPrePositionXField.setFloat(renderPhysics, breastPhysicsPrePositionXField.getFloat(realPhysics) + previousBias.x());
            physicsPositionXField.setFloat(renderPhysics, breastPhysicsPositionXField.getFloat(realPhysics) + bias.x());
         }

         if (physicsPositionYField != null && physicsPrePositionYField != null) {
            physicsPrePositionYField.setFloat(renderPhysics, breastPhysicsPrePositionYField.getFloat(realPhysics) + previousBias.y());
            physicsPositionYField.setFloat(renderPhysics, breastPhysicsPositionYField.getFloat(realPhysics) + bias.y());
         }

         if (physicsBounceRotationField != null && physicsPreBounceRotationField != null) {
            physicsPreBounceRotationField.setFloat(renderPhysics, breastPhysicsPreBounceRotationField.getFloat(realPhysics) + previousBias.rotation());
            physicsBounceRotationField.setFloat(renderPhysics, breastPhysicsBounceRotationField.getFloat(realPhysics) + bias.rotation());
         }
      }
   }

   private static long currentClientWorldTick() {
      MinecraftClient client = MinecraftClient.getInstance();
      return client != null && client.world != null ? client.world.getTime() : Long.MIN_VALUE;
   }

   private static boolean isUniboob(Object wildfireState) throws ReflectiveOperationException {
      Object breasts = breastsField.get(wildfireState);
      return breasts != null && getBoolean(breastUniboobField, breasts, false);
   }

   private static Object getOrCreateOriginalLayer(LivingEntityRenderState renderState) throws ReflectiveOperationException {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.getEntityRenderDispatcher() != null) {
         if (!(client.getEntityRenderDispatcher() instanceof EntityRenderManagerAccessor accessor)) {
            return null;
         } else {
            EntityRenderer<?, ?> renderer = accessor.afw$getRenderer(renderState);
            if (renderer == null) {
               return null;
            }

            Object cached = GENDER_LAYERS.get(renderer);
            if (cached != null) {
               return cached;
            }

            Constructor<?> constructor = null;

            for (Constructor<?> candidate : genderLayerClass.getConstructors()) {
               if (candidate.getParameterCount() == 1) {
                  Class<?> parameter = candidate.getParameterTypes()[0];
                  if (parameter.isAssignableFrom(renderer.getClass())) {
                     candidate.setAccessible(true);
                     constructor = candidate;
                     break;
                  }
               }
            }

            if (constructor == null) {
               return null;
            }

            Object created = constructor.newInstance(renderer);
            GENDER_LAYERS.put(renderer, created);
            return created;
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static WildfireGenderCompat.RenderPoseSnapshot neutralizeRenderPose(LivingEntityRenderState renderState) {
      if (renderState == null) {
         return null;
      }

      WildfireGenderCompat.RenderPoseSnapshot snapshot = new WildfireGenderCompat.RenderPoseSnapshot(
         renderState.pose, renderState instanceof BipedEntityRenderState biped && biped.isInSneakingPose
      );
      renderState.pose = EntityPose.STANDING;
      if (renderState instanceof BipedEntityRenderState biped) {
         biped.isInSneakingPose = false;
      }

      return snapshot;
   }

   private static void restoreRenderPose(LivingEntityRenderState renderState, @Nullable WildfireGenderCompat.RenderPoseSnapshot snapshot) {
      if (renderState != null && snapshot != null) {
         renderState.pose = snapshot.pose();
         if (renderState instanceof BipedEntityRenderState biped) {
            biped.isInSneakingPose = snapshot.sneaking();
         }
      }
   }

   private static WildfireGenderCompat.BodyTransformSnapshot neutralizeContextBodyTransform(Object layer) throws IllegalAccessException {
      Object body = getContextBody(layer);
      if (body != null
         && bodyOriginXField != null
         && bodyOriginYField != null
         && bodyOriginZField != null
         && bodyPitchField != null
         && bodyYawField != null
         && bodyRollField != null) {
         WildfireGenderCompat.BodyTransformSnapshot snapshot = new WildfireGenderCompat.BodyTransformSnapshot(
            body,
            bodyOriginXField.getFloat(body),
            bodyOriginYField.getFloat(body),
            bodyOriginZField.getFloat(body),
            bodyPitchField.getFloat(body),
            bodyYawField.getFloat(body),
            bodyRollField.getFloat(body)
         );
         bodyOriginXField.setFloat(body, 0.0F);
         bodyOriginYField.setFloat(body, 0.0F);
         bodyOriginZField.setFloat(body, 0.0F);
         bodyPitchField.setFloat(body, 0.0F);
         bodyYawField.setFloat(body, 0.0F);
         bodyRollField.setFloat(body, 0.0F);
         return snapshot;
      } else {
         return null;
      }
   }

   private static void restoreContextBodyTransform(WildfireGenderCompat.BodyTransformSnapshot snapshot) throws IllegalAccessException {
      if (snapshot != null) {
         bodyOriginXField.setFloat(snapshot.body(), snapshot.originX());
         bodyOriginYField.setFloat(snapshot.body(), snapshot.originY());
         bodyOriginZField.setFloat(snapshot.body(), snapshot.originZ());
         bodyPitchField.setFloat(snapshot.body(), snapshot.pitch());
         bodyYawField.setFloat(snapshot.body(), snapshot.yaw());
         bodyRollField.setFloat(snapshot.body(), snapshot.roll());
      }
   }

   private static Object getContextBody(Object layer) {
      try {
         if (contextModelMethod == null) {
            contextModelMethod = findNoArgMethod(layer.getClass(), "getContextModel", "method_17165");
         }

         if (contextModelMethod == null) {
            return null;
         }

         Object model = contextModelMethod.invoke(layer);
         if (model == null) {
            return null;
         }

         if (modelBodyField == null) {
            modelBodyField = findField(model.getClass(), "body", "field_3391");
         }

         if (modelBodyField == null) {
            return null;
         }

         Object body = modelBodyField.get(model);
         if (body == null) {
            return null;
         }

         Class<?> bodyClass = body.getClass();
         if (bodyOriginXField == null) {
            bodyOriginXField = findField(bodyClass, "originX", "pivotX", "field_3657");
         }

         if (bodyOriginYField == null) {
            bodyOriginYField = findField(bodyClass, "originY", "pivotY", "field_3656");
         }

         if (bodyOriginZField == null) {
            bodyOriginZField = findField(bodyClass, "originZ", "pivotZ", "field_3655");
         }

         if (bodyPitchField == null) {
            bodyPitchField = findField(bodyClass, "pitch", "xRot", "field_3674");
         }

         if (bodyYawField == null) {
            bodyYawField = findField(bodyClass, "yaw", "yRot", "field_3675");
         }

         if (bodyRollField == null) {
            bodyRollField = findField(bodyClass, "roll", "zRot", "field_3654");
         }

         return body;
      } catch (ReflectiveOperationException | RuntimeException e) {
         logFailure("Failed to neutralize Female Gender vanilla body transform.", e);
         return null;
      }
   }

   private static WildfireGenderState.Side readSide(
      Object physics, Object baseUv, Object overlayUv, WildfireGenderState.UvRect defaultBase, WildfireGenderState.UvRect defaultOverlay
   ) throws ReflectiveOperationException {
      return new WildfireGenderState.Side(
         invokeFloat(physicsPositionXMethod, physics, 0.0F),
         invokeFloat(physicsPositionYMethod, physics, 0.0F),
         invokeFloat(physicsBreastSizeMethod, physics, 0.0F),
         invokeFloat(physicsBounceRotationMethod, physics, 0.0F),
         readNorthUv(baseUv, defaultBase),
         readNorthUv(overlayUv, defaultOverlay)
      );
   }

   private static WildfireGenderState.UvRect readNorthUv(Object uvLayout, WildfireGenderState.UvRect fallback) throws ReflectiveOperationException {
      if (uvLayout != null && uvGetAllSidesMethod != null) {
         if (uvGetAllSidesMethod.invoke(uvLayout) instanceof Map<?, ?> map) {
            for (Entry<?, ?> entry : map.entrySet()) {
               Object direction = entry.getKey();
               Object quad = entry.getValue();
               if (direction != null && quad != null && "NORTH".equals(String.valueOf(direction))) {
                  int x1 = (Integer)uvQuadX1Method.invoke(quad);
                  int y1 = (Integer)uvQuadY1Method.invoke(quad);
                  int x2 = (Integer)uvQuadX2Method.invoke(quad);
                  int y2 = (Integer)uvQuadY2Method.invoke(quad);
                  return WildfireGenderState.UvRect.pixels(x1, y1, x2, y2);
               }
            }

            return fallback;
         } else {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private static boolean canHaveBreasts(Object gender) throws ReflectiveOperationException {
      if (gender == null) {
         return false;
      } else {
         return genderCanHaveBreasts != null && genderCanHaveBreasts.invoke(gender) instanceof Boolean bool ? bool : !"MALE".equals(String.valueOf(gender));
      }
   }

   private static boolean isAvailable() {
      if (initialized) {
         return available;
      }

      initialized = true;
      if (!FabricLoader.getInstance().isModLoaded("wildfire_gender")) {
         available = false;
         return false;
      }

      try {
         Class<?> renderStateClass = Class.forName("com.wildfire.render.GenderRenderState");
         genderLayerClass = Class.forName("com.wildfire.render.GenderLayer");
         getRenderState = findStaticMethod(renderStateClass, "get", 1);
         genderLayerSubmitMethod = findInstanceMethod(genderLayerClass, "submit", 6);
         genderField = field(renderStateClass, "gender");
         bustSizeField = field(renderStateClass, "bustSize");
         armorPhysicsOverrideField = field(renderStateClass, "armorPhysicsOverride");
         showBreastsInArmorField = field(renderStateClass, "showBreastsInArmor");
         hasJacketLayerField = field(renderStateClass, "hasJacketLayer");
         breastsField = field(renderStateClass, "breasts");
         leftPhysicsField = field(renderStateClass, "leftBreastPhysics");
         rightPhysicsField = field(renderStateClass, "rightBreastPhysics");
         leftUvField = field(renderStateClass, "leftBreastUVLayout");
         rightUvField = field(renderStateClass, "rightBreastUVLayout");
         leftOverlayUvField = field(renderStateClass, "leftBreastOverlayUVLayout");
         rightOverlayUvField = field(renderStateClass, "rightBreastOverlayUVLayout");
         Class<?> genderClass = Class.forName("com.wildfire.main.config.enums.Gender");
         genderCanHaveBreasts = methodOrNull(genderClass, "canHaveBreasts");
         Class<?> breastStateClass = Class.forName("com.wildfire.render.GenderRenderState$BreastState");
         breastXOffsetField = field(breastStateClass, "xOffset");
         breastYOffsetField = field(breastStateClass, "yOffset");
         breastZOffsetField = field(breastStateClass, "zOffset");
         breastCleavageField = field(breastStateClass, "cleavage");
         breastUniboobField = field(breastStateClass, "uniboob");
         Class<?> physicsClass = Class.forName("com.wildfire.render.GenderRenderState$BreastPhysicsState");
         physicsPositionXMethod = methodOrNull(physicsClass, "getPositionX");
         physicsPositionYMethod = methodOrNull(physicsClass, "getPositionY");
         physicsBreastSizeMethod = methodOrNull(physicsClass, "getBreastSize");
         physicsBounceRotationMethod = methodOrNull(physicsClass, "getBounceRotation");
         physicsPrePositionXField = declaredField(physicsClass, "prePositionX");
         physicsPositionXField = declaredField(physicsClass, "positionX");
         physicsPrePositionYField = declaredField(physicsClass, "prePositionY");
         physicsPositionYField = declaredField(physicsClass, "positionY");
         physicsPreBounceRotationField = declaredField(physicsClass, "preBounceRotation");
         physicsBounceRotationField = declaredField(physicsClass, "bounceRotation");
         Class<?> entityConfigClass = Class.forName("com.wildfire.main.entitydata.EntityConfig");
         entityConfigGetEntityMethod = findStaticMethod(entityConfigClass, "getEntity", 1);
         Class<?> breastPhysicsClass = Class.forName("com.wildfire.physics.BreastPhysics");
         breastPhysicsConstructor = breastPhysicsClass.getConstructor(entityConfigClass);
         breastPhysicsConstructor.setAccessible(true);
         breastPhysicsFinishTickMethod = declaredMethod(breastPhysicsClass, "finishTick");
         breastPhysicsTargetBounceXField = declaredField(breastPhysicsClass, "targetBounceX");
         breastPhysicsTargetBounceYField = declaredField(breastPhysicsClass, "targetBounceY");
         breastPhysicsTargetRotVelField = declaredField(breastPhysicsClass, "targetRotVel");
         breastPhysicsPrePositionXField = declaredField(breastPhysicsClass, "prePositionX");
         breastPhysicsPositionXField = declaredField(breastPhysicsClass, "positionX");
         breastPhysicsPrePositionYField = declaredField(breastPhysicsClass, "prePositionY");
         breastPhysicsPositionYField = declaredField(breastPhysicsClass, "positionY");
         breastPhysicsPreBounceRotationField = declaredField(breastPhysicsClass, "wfg_preBounceRotation");
         breastPhysicsBounceRotationField = declaredField(breastPhysicsClass, "wfg_bounceRotation");
         Class<?> uvLayoutClass = Class.forName("com.wildfire.main.uvs.UVLayout");
         uvGetAllSidesMethod = methodOrNull(uvLayoutClass, "getAllSides");
         Class<?> uvQuadClass = Class.forName("com.wildfire.main.uvs.UVQuad");
         uvQuadX1Method = methodOrNull(uvQuadClass, "x1");
         uvQuadY1Method = methodOrNull(uvQuadClass, "y1");
         uvQuadX2Method = methodOrNull(uvQuadClass, "x2");
         uvQuadY2Method = methodOrNull(uvQuadClass, "y2");
         available = getRenderState != null
            && genderLayerSubmitMethod != null
            && entityConfigGetEntityMethod != null
            && breastPhysicsConstructor != null
            && breastPhysicsFinishTickMethod != null
            && breastPhysicsTargetBounceXField != null
            && breastPhysicsTargetBounceYField != null
            && breastPhysicsTargetRotVelField != null
            && breastPhysicsPreBounceRotationField != null
            && breastPhysicsBounceRotationField != null;
      } catch (ReflectiveOperationException | RuntimeException e) {
         available = false;
         logFailure("Female Gender compatibility disabled: unsupported API shape.", e);
      }

      return available;
   }

   private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
      Field field = owner.getField(name);
      field.setAccessible(true);
      return field;
   }

   private static Field declaredField(Class<?> owner, String name) throws NoSuchFieldException {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      return field;
   }

   private static Method declaredMethod(Class<?> owner, String name) throws NoSuchMethodException {
      Method method = owner.getDeclaredMethod(name);
      method.setAccessible(true);
      return method;
   }

   private static Field findField(Class<?> owner, String... names) {
      for (Class<?> current = owner; current != null && current != Object.class; current = current.getSuperclass()) {
         for (String name : names) {
            try {
               Field field = current.getDeclaredField(name);
               field.setAccessible(true);
               return field;
            } catch (NoSuchFieldException var8) {
            }
         }
      }

      return null;
   }

   private static Method findStaticMethod(Class<?> owner, String name, int parameterCount) {
      for (Method method : owner.getMethods()) {
         if (method.getName().equals(name) && method.getParameterCount() == parameterCount && Modifier.isStatic(method.getModifiers())) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   private static Method findInstanceMethod(Class<?> owner, String name, int parameterCount) {
      for (Method method : owner.getMethods()) {
         if (method.getName().equals(name) && method.getParameterCount() == parameterCount && !Modifier.isStatic(method.getModifiers())) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   private static Method methodOrNull(Class<?> owner, String name) {
      try {
         Method method = owner.getMethod(name);
         method.setAccessible(true);
         return method;
      } catch (NoSuchMethodException e) {
         return null;
      }
   }

   private static Method findNoArgMethod(Class<?> owner, String... names) {
      for (Class<?> current = owner; current != null && current != Object.class; current = current.getSuperclass()) {
         for (Method method : current.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && !Modifier.isStatic(method.getModifiers())) {
               for (String name : names) {
                  if (method.getName().equals(name)) {
                     method.setAccessible(true);
                     return method;
                  }
               }
            }
         }
      }

      return null;
   }

   private static float invokeFloat(Method method, Object target, float fallback) throws ReflectiveOperationException {
      if (method != null && target != null) {
         return method.invoke(target) instanceof Number number ? number.floatValue() : fallback;
      } else {
         return fallback;
      }
   }

   private static float getFloat(Field field, Object target, float fallback) throws IllegalAccessException {
      return field != null && target != null ? field.getFloat(target) : fallback;
   }

   private static boolean getBoolean(Field field, Object target, boolean fallback) throws IllegalAccessException {
      return field != null && target != null ? field.getBoolean(target) : fallback;
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }

   private static float shapedImpulse(float value, float reference, float min, float max, float exponent) {
      if (Math.abs(value) < 1.0E-6F) {
         return 0.0F;
      }

      float magnitude = Math.min(Math.abs(value) / Math.max(reference, 1.0E-6F), 1.0F);
      float shaped = (float)Math.pow(magnitude, exponent);
      return Math.signum(value) * (value < 0.0F ? Math.abs(min) : max) * shaped;
   }

   private static float signedYawDelta(Vec3d previousForward, Vec3d currentForward) {
      if (previousForward != null && currentForward != null) {
         Vec3d a = new Vec3d(previousForward.x, 0.0, previousForward.z);
         Vec3d b = new Vec3d(currentForward.x, 0.0, currentForward.z);
         if (!(a.lengthSquared() < 1.0E-8) && !(b.lengthSquared() < 1.0E-8)) {
            a = a.normalize();
            b = b.normalize();
            double dot = MathHelper.clamp(a.dotProduct(b), -1.0, 1.0);
            double crossY = a.z * b.x - a.x * b.z;
            return (float)Math.toDegrees(Math.atan2(crossY, dot));
         } else {
            return 0.0F;
         }
      } else {
         return 0.0F;
      }
   }

   private static void logFailure(String message, Throwable error) {
      if (!loggedFailure) {
         loggedFailure = true;
         AnimationEngine.LOGGER.warn("[NoN Animation] {}", message, error);
      }
   }

   private static final class AnimationPhysicsPair {
      private final Object config;
      private final Object left;
      private final Object right;
      private long lastTick = Long.MIN_VALUE;
      private WildfireGenderCompat.BonePhysicsSample lastSample;
      private WildfireGenderCompat.SideRenderBias previousLeftBias = WildfireGenderCompat.SideRenderBias.ZERO;
      private WildfireGenderCompat.SideRenderBias leftBias = WildfireGenderCompat.SideRenderBias.ZERO;
      private WildfireGenderCompat.SideRenderBias previousRightBias = WildfireGenderCompat.SideRenderBias.ZERO;
      private WildfireGenderCompat.SideRenderBias rightBias = WildfireGenderCompat.SideRenderBias.ZERO;
      private boolean hasSmoothedTarget;
      private float smoothedTargetX;
      private float smoothedTargetY;
      private float smoothedTargetRotation;

      private AnimationPhysicsPair(Object config, Object left, Object right) {
         this.config = config;
         this.left = left;
         this.right = right;
      }

      private void stepRenderBias(WildfireGenderCompat.GravityBias gravity, boolean uniboob) {
         this.previousLeftBias = this.leftBias;
         this.previousRightBias = this.rightBias;
         float x = WildfireGenderCompat.clamp(gravity.sidePull() * 0.45F, -0.8F, 0.8F);
         float y = WildfireGenderCompat.clamp(gravity.forwardDown() * 0.85F, 0.0F, 1.2F);
         float rotation = WildfireGenderCompat.clamp(gravity.sidePull() * 10.0F + gravity.forwardPull() * 2.0F, -10.0F, 10.0F);
         this.leftBias = new WildfireGenderCompat.SideRenderBias(x, y, uniboob ? 0.0F : rotation);
         this.rightBias = uniboob ? this.leftBias : this.leftBias.mirrored();
      }

      private WildfireGenderCompat.TargetImpulse smoothTarget(float x, float y, float rotation) {
         if (!this.hasSmoothedTarget) {
            this.hasSmoothedTarget = true;
            this.smoothedTargetX = x;
            this.smoothedTargetY = y;
            this.smoothedTargetRotation = rotation;
            return new WildfireGenderCompat.TargetImpulse(x, y, rotation);
         } else {
            this.smoothedTargetX = MathHelper.lerp(0.1F, this.smoothedTargetX, x);
            this.smoothedTargetY = MathHelper.lerp(0.1F, this.smoothedTargetY, y);
            this.smoothedTargetRotation = MathHelper.lerp(0.1F, this.smoothedTargetRotation, rotation);
            return new WildfireGenderCompat.TargetImpulse(this.smoothedTargetX, this.smoothedTargetY, this.smoothedTargetRotation);
         }
      }
   }

   private record BodyTransformSnapshot(Object body, float originX, float originY, float originZ, float pitch, float yaw, float roll) {
   }

   private record BonePhysicsSample(float x, float y, float z, float chestX, float chestY, float chestZ, Vec3d forward, Vec3d right, Vec3d up) {
      static WildfireGenderCompat.BonePhysicsSample from(MatrixStack rootPose, MatrixStack bonePose) {
         Matrix4f relativePose = new Matrix4f(rootPose.peek().getPositionMatrix()).invert().mul(new Matrix4f(bonePose.peek().getPositionMatrix()));
         Vector3f origin = relativePose.transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
         Vector3f forwardPoint = relativePose.transformPosition(0.0F, 0.0F, -1.0F, new Vector3f());
         Vector3f rightPoint = relativePose.transformPosition(1.0F, 0.0F, 0.0F, new Vector3f());
         Vector3f upPoint = relativePose.transformPosition(0.0F, 1.0F, 0.0F, new Vector3f());
         Vector3f chestPoint = relativePose.transformPosition(0.0F, 0.3F, -0.35F, new Vector3f());
         Vec3d forward = normalize(new Vec3d(forwardPoint.x() - origin.x(), forwardPoint.y() - origin.y(), forwardPoint.z() - origin.z()));
         Vec3d right = normalize(new Vec3d(rightPoint.x() - origin.x(), rightPoint.y() - origin.y(), rightPoint.z() - origin.z()));
         Vec3d up = normalize(new Vec3d(upPoint.x() - origin.x(), upPoint.y() - origin.y(), upPoint.z() - origin.z()));
         return forward != null && right != null && up != null
            ? new WildfireGenderCompat.BonePhysicsSample(origin.x(), origin.y(), origin.z(), chestPoint.x(), chestPoint.y(), chestPoint.z(), forward, right, up)
            : null;
      }

      private static Vec3d normalize(Vec3d vector) {
         if (vector != null && !(vector.lengthSquared() < 1.0E-8)) {
            Vec3d normalized = vector.normalize();
            return normalized.isFinite() ? normalized : null;
         } else {
            return null;
         }
      }
   }

   private record GravityBias(float sidePull, float forwardPull, float forwardDown, float tiltInfluence) {
      private static final Vec3d MODEL_DOWN = new Vec3d(0.0, -1.0, 0.0);

      static WildfireGenderCompat.GravityBias from(WildfireGenderCompat.BonePhysicsSample sample) {
         if (sample == null) {
            return new WildfireGenderCompat.GravityBias(0.0F, 0.0F, 0.0F, 0.0F);
         }

         float sidePull = (float)sample.right().dotProduct(MODEL_DOWN);
         float forwardPull = (float)sample.forward().dotProduct(MODEL_DOWN);
         float forwardDown = Math.abs(forwardPull);
         float upsideDown = Math.max(0.0F, (float)sample.up().dotProduct(MODEL_DOWN));
         float tiltInfluence = WildfireGenderCompat.clamp(Math.max(Math.abs(sidePull), Math.max(forwardDown, upsideDown)), 0.0F, 1.0F);
         return new WildfireGenderCompat.GravityBias(sidePull, forwardPull, forwardDown, tiltInfluence);
      }
   }

   private record RenderPoseSnapshot(EntityPose pose, boolean sneaking) {
   }

   private record SideRenderBias(float x, float y, float rotation) {
      private static final WildfireGenderCompat.SideRenderBias ZERO = new WildfireGenderCompat.SideRenderBias(0.0F, 0.0F, 0.0F);

      private WildfireGenderCompat.SideRenderBias mirrored() {
         return new WildfireGenderCompat.SideRenderBias(-this.x * 0.85F, this.y * 0.95F, -this.rotation * 0.85F);
      }
   }

   private record TargetImpulse(float x, float y, float rotation) {
   }
}
