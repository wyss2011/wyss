package com.nonid.internal.animation.client.render.gecko;

import com.nonid.EnergyHolder;
import com.nonid.NonDebugChatCategory;
import com.nonid.api.client.NonAnimationParticleEvents;
import com.nonid.internal.animation.AnimationEngineClient;
import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.LoopType;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTimeline;
import software.bernie.geckolib.animation.state.KeyFrameEvent;
import software.bernie.geckolib.cache.animation.keyframeevent.ParticleKeyframeData;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.util.GeckoLibUtil;
import org.jetbrains.annotations.Nullable;

public final class AnimationActorAnimatable implements GeoAnimatable {
   public static final AnimationActorAnimatable INSTANCE = new AnimationActorAnimatable();
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private static final Map<String, RawAnimation> LOOP_CACHE = new ConcurrentHashMap<>();
   private static final Map<String, RawAnimation> PLAY_ONCE_CACHE = new ConcurrentHashMap<>();
   private static final Set<Identifier> MISSING_ANIMATION_WARNED = Collections.newSetFromMap(new ConcurrentHashMap<>());
   private static final double PARTICLE_Y_OFFSET = -0.0625;
   private static final double LOOP_WRAP_EPSILON_SECONDS = 0.004166666666666667;
   private static final LoopType LOOP_NO_ZERO_WRAP = LoopType.register(
      "afw_loop_no_zero_wrap", (animatable, animationPoint, timelineStage, renderState, controller) -> {
         double wrapped = timelineStage.startTime() + (controller.getCurrentTimelineTime() - timelineStage.endTime());
         double start = timelineStage.startTime();
         double end = timelineStage.endTime();
         if (wrapped <= start) {
            wrapped = start + 0.004166666666666667;
         }

         if (wrapped >= end) {
            wrapped = Math.nextDown(end);
         }

         controller.setTimelineTime(wrapped);
         return true;
      }
   );

   private AnimationActorAnimatable() {
   }

   public void registerControllers(ControllerRegistrar controllers) {
      AnimationController<AnimationActorAnimatable> controller = new AnimationController(
         "afw_main",
         test -> {
            GeoRenderState renderState = test.renderState();
            if (!renderState.hasGeckolibData(GeckoRenderTickets.ANIMATION_ID)) {
               test.manager().setAnimatableData(GeckoRenderTickets.LAST_ANIMATION_INSTANCE_ID, null);
               test.manager().setAnimatableData(GeckoRenderTickets.LAST_ANIMATION_RESOURCE_ID, null);
               test.manager().setAnimatableData(GeckoRenderTickets.LAST_ANIMATION_KEY, null);
               return PlayState.STOP;
            }

            Identifier animationId = (Identifier)renderState.getGeckolibData(GeckoRenderTickets.ANIMATION_ID);
            if (animationId == null) {
               test.manager().setAnimatableData(GeckoRenderTickets.LAST_ANIMATION_INSTANCE_ID, null);
               test.manager().setAnimatableData(GeckoRenderTickets.LAST_ANIMATION_RESOURCE_ID, null);
               test.manager().setAnimatableData(GeckoRenderTickets.LAST_ANIMATION_KEY, null);
               return PlayState.STOP;
            }

            Identifier animationResource = (Identifier)renderState.getGeckolibData(GeckoRenderTickets.ANIMATION_RESOURCE_ID);
            if (animationResource == null) {
               animationResource = GeckoResourceResolver.toAnimationResource(animationId);
            }

            if (!GeckoResourceResolver.hasBakedAnimation(animationResource)) {
               warnMissingAnimation(animationResource);
               return PlayState.STOP;
            }

            boolean hasExplicitAnimationKey = renderState.hasGeckolibData(GeckoRenderTickets.ANIMATION_KEY);
            String animationKey = hasExplicitAnimationKey ? (String)renderState.getGeckolibData(GeckoRenderTickets.ANIMATION_KEY) : null;
            if (!hasExplicitAnimationKey) {
               animationKey = GeckoResourceResolver.resolveAnimationKey(animationResource);
            }

            if (animationKey == null || animationKey.isBlank()) {
               return PlayState.STOP;
            }

            if (!GeckoResourceResolver.hasBakedAnimationKey(animationResource, animationKey)) {
               warnMissingAnimation(animationResource);
               return PlayState.STOP;
            }

            UUID instanceId = renderState.hasGeckolibData(GeckoRenderTickets.ANIMATION_INSTANCE_ID)
               ? (UUID)renderState.getGeckolibData(GeckoRenderTickets.ANIMATION_INSTANCE_ID)
               : null;
            UUID lastInstanceId = (UUID)test.manager().getAnimatableData(GeckoRenderTickets.LAST_ANIMATION_INSTANCE_ID);
            Identifier lastAnimationResource = (Identifier)test.manager().getAnimatableData(GeckoRenderTickets.LAST_ANIMATION_RESOURCE_ID);
            String lastAnimationKey = (String)test.manager().getAnimatableData(GeckoRenderTickets.LAST_ANIMATION_KEY);
            boolean reloadController = false;
            if (instanceId != null && !instanceId.equals(lastInstanceId)) {
               reloadController = true;
               test.manager().setAnimatableData(GeckoRenderTickets.LAST_ANIMATION_INSTANCE_ID, instanceId);
            }

            if (lastAnimationResource == null || !lastAnimationResource.equals(animationResource)) {
               reloadController = true;
               test.manager().setAnimatableData(GeckoRenderTickets.LAST_ANIMATION_RESOURCE_ID, animationResource);
            }

            if (lastAnimationKey == null || !lastAnimationKey.equals(animationKey)) {
               reloadController = true;
               test.manager().setAnimatableData(GeckoRenderTickets.LAST_ANIMATION_KEY, animationKey);
            }

            if (reloadController) {
               test.controller().reset();
            }

            double speed = 1.0;
            if (renderState.hasGeckolibData(GeckoRenderTickets.ANIMATION_SPEED)) {
               Double s = (Double)renderState.getGeckolibData(GeckoRenderTickets.ANIMATION_SPEED);
               if (s != null && Double.isFinite(s) && s > 0.0) {
                  speed = Math.max(0.1, Math.min(4.0, s));
               }
            }

            boolean loop = ClientAnimationRuntime.isAnimationLooping(animationId);
            RawAnimation toPlay = loop ? loopAnimation(animationKey) : playOnceAnimation(animationKey);
            if (!loop && test.isCurrentAnimation(toPlay) && test.controller().hasAnimationFinished()) {
               AnimationTimeline timeline = test.controller().getTimeline();
               if (timeline != null) {
                  double holdTime = Math.nextDown(timeline.lastAnimationEndTime());
                  if (Double.isFinite(holdTime) && holdTime >= 0.0) {
                     test.controller().setTimelineTime(holdTime);
                  }
               }
            }

            PlayState playState = test.setAndContinue(toPlay);
            test.setControllerSpeed((float)speed);
            Double timelineSeconds = (Double)renderState.getGeckolibData(GeckoRenderTickets.ANIMATION_TIMELINE_SECONDS);
            if (timelineSeconds != null && Double.isFinite(timelineSeconds) && timelineSeconds >= 0.0) {
               test.controller().setTimelineTime(timelineSeconds);
            }

            return playState;
         }
      );
      controller.setSoundKeyframeHandler(event -> {});
      controller.setParticleKeyframeHandler(AnimationActorAnimatable::handleParticleKeyframe);
      controllers.add(controller);
   }

   @NotNull
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   private static RawAnimation loopAnimation(String key) {
      return LOOP_CACHE.computeIfAbsent(key, k -> RawAnimation.begin().then(k, LOOP_NO_ZERO_WRAP));
   }

   private static RawAnimation playOnceAnimation(String key) {
      return PLAY_ONCE_CACHE.computeIfAbsent(key, k -> RawAnimation.begin().thenPlay(k));
   }

   public static void clearResourceCaches() {
      LOOP_CACHE.clear();
      PLAY_ONCE_CACHE.clear();
      MISSING_ANIMATION_WARNED.clear();
   }

   private static void handleParticleKeyframe(KeyFrameEvent<AnimationActorAnimatable, ParticleKeyframeData> event) {
      if (event != null && event.keyframeData() != null && event.renderState() != null) {
         String effect = ((ParticleKeyframeData)event.keyframeData()).getEffect();
         if (effect != null && !effect.isBlank()) {
            Identifier particleId = Identifier.tryParse(effect.trim());
            if (particleId != null && Registries.PARTICLE_TYPE.containsId(particleId)) {
               ParticleType<?> type = (ParticleType<?>)Registries.PARTICLE_TYPE.get(particleId);
               ParticleEffect particle = type instanceof SimpleParticleType simpleParticle ? simpleParticle : null;
               UUID actorUuid = (UUID)event.renderState().getGeckolibData(GeckoRenderTickets.ACTOR_UUID);
               if (actorUuid != null) {
                  UUID instanceId = (UUID)event.renderState().getGeckolibData(GeckoRenderTickets.ANIMATION_INSTANCE_ID);
                  Entity actor = resolveActor(actorUuid);
                  String locator = ((ParticleKeyframeData)event.keyframeData()).getLocator();
                  MinecraftClient client = MinecraftClient.getInstance();
                  long worldTick = client != null && client.world != null ? client.world.getTime() : Long.MIN_VALUE;
                  Vec3d spawnPosition = ParticleKeyframes.findParticleLocatorPosition(instanceId, actorUuid, worldTick, locator);
                  if (spawnPosition != null) {
                     spawnPosition = spawnPosition.add(0.0, -0.0625, 0.0);
                     NonAnimationParticleEvents.ParticleOverride override = ((NonAnimationParticleEvents.ParticleResolver)NonAnimationParticleEvents.RESOLVE
                           .invoker())
                        .resolve(
                           new NonAnimationParticleEvents.ParticleContext(
                              actor,
                              actorUuid,
                              (Identifier)event.renderState().getGeckolibData(GeckoRenderTickets.ANIMATION_ID),
                              (Identifier)event.renderState().getGeckolibData(GeckoRenderTickets.ANIMATION_RESOURCE_ID),
                              (String)event.renderState().getGeckolibData(GeckoRenderTickets.ANIMATION_KEY),
                              particleId,
                              locator == null ? "" : locator,
                              ((ParticleKeyframeData)event.keyframeData()).script() == null ? "" : ((ParticleKeyframeData)event.keyframeData()).script(),
                              spawnPosition,
                              (UUID)event.renderState().getGeckolibData(GeckoRenderTickets.ANIMATION_INSTANCE_ID)
                           )
                        );
                     if (override != null) {
                        if (override.cancel()) {
                           return;
                        }

                        particle = override.particle();
                     }

                     if (particle != null) {
                        ClientWorld world = actor != null && actor.getEntityWorld() instanceof ClientWorld clientWorld ? clientWorld : null;
                        if (world == null) {
                           world = client == null ? null : client.world;
                        }

                        Vec3d velocity = computeEjectVelocity(actor, instanceId, actorUuid, worldTick, locator);
                        spawnParticle(world, particle, spawnPosition, velocity);
                     }
                  }
               }
            }
         }
      }
   }

   private static void spawnParticle(ClientWorld world, ParticleEffect particle, Vec3d pos, Vec3d velocity) {
      if (world != null && particle != null && pos != null && pos.isFinite()) {
         if (velocity != null && velocity.isFinite()) {
            world.addParticleClient(particle, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
         } else {
            world.addParticleClient(particle, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
         }
      }
   }

   private static Vec3d computeEjectVelocity(@Nullable Entity actor, @Nullable UUID instanceId, UUID actorUuid, long worldTick, @Nullable String locator) {
      if (actor == null || instanceId == null || actorUuid == null) {
         return Vec3d.ZERO;
      }

      Vec3d direction = ParticleKeyframes.findLocatorDirection(instanceId, actorUuid, worldTick, locator);
      if (direction == null || direction.lengthSquared() <= 1.0E-4) {
         return Vec3d.ZERO;
      }

      double energyRatio = 0.0;
      if (actor instanceof EnergyHolder holder) {
         int max = holder.getMaxEnergy();
         if (max > 0) {
            energyRatio = Math.max(0.0, Math.min(1.0, (double)holder.getEnergy() / (double)max));
         }
      }

      double minSpeed = 0.06;
      double maxSpeed = 0.3;
      double speed = minSpeed + (maxSpeed - minSpeed) * energyRatio;
      return direction.multiply(speed);
   }

   private static Entity resolveActor(UUID actorUuid) {
      if (actorUuid == null) {
         return null;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      return client != null && client.world != null ? client.world.getEntity(actorUuid) : null;
   }

   private static void warnMissingAnimation(Identifier animationResource) {
      if (MISSING_ANIMATION_WARNED.add(animationResource)) {
         AnimationEngineClient.sendClientDebugChat(
            NonDebugChatCategory.SETUP, Text.literal("[NoN Animation] Missing animation file on client: " + animationResource + " (skipping)")
         );
      }
   }
}
