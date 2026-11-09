package com.nonid.internal.animation.client.render.gecko;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.math.Vec3d;

public final class ParticleKeyframes {
   private static final long MAX_SAMPLE_AGE_TICKS = 2L;
   private static final String V_LOCATOR = "v_locator";
   private static final String A_LOCATOR = "a_locator";
   private static final ConcurrentHashMap<ParticleKeyframes.LocatorKey, ParticleKeyframes.LocatorSample> LOCATOR_POSITIONS = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<UUID, Vec3d> ACTOR_ORIGINS = new ConcurrentHashMap<>();

   private ParticleKeyframes() {
   }

   public static void updateLocatorPositions(UUID instanceId, UUID actorUuid, long worldTick, Map<String, Vec3d> positions) {
      updateLocatorData(instanceId, actorUuid, worldTick, positions, Map.of());
   }

   public static void updateLocatorData(
      UUID instanceId, UUID actorUuid, long worldTick, Map<String, Vec3d> positions, Map<String, Vec3d> directions
   ) {
      if (instanceId != null && actorUuid != null) {
         ParticleKeyframes.LocatorKey key = new ParticleKeyframes.LocatorKey(instanceId, actorUuid);
         if (positions != null && !positions.isEmpty()) {
            Map<String, Vec3d> clean = new LinkedHashMap<>();
            Map<String, Vec3d> cleanDirections = new LinkedHashMap<>();

            for (Entry<String, Vec3d> entry : positions.entrySet()) {
               String locator = cleanLocator(entry.getKey());
               Vec3d position = entry.getValue();
               if (!locator.isEmpty() && position != null && position.isFinite()) {
                  clean.put(locator, position);
               }
            }

            if (directions != null) {
               for (Entry<String, Vec3d> entry : directions.entrySet()) {
                  String locator = cleanLocator(entry.getKey());
                  Vec3d direction = entry.getValue();
                  if (!locator.isEmpty() && direction != null && direction.isFinite()) {
                     cleanDirections.put(locator, direction);
                  }
               }
            }

            if (clean.isEmpty()) {
               LOCATOR_POSITIONS.remove(key);
            } else {
               LOCATOR_POSITIONS.put(key, new ParticleKeyframes.LocatorSample(worldTick, Map.copyOf(clean), Map.copyOf(cleanDirections)));
            }
         } else {
            LOCATOR_POSITIONS.remove(key);
         }
      }
   }

   public static Vec3d findLocatorPosition(UUID instanceId, UUID actorUuid, long worldTick, String locator) {
      if (instanceId != null && actorUuid != null) {
         String cleanLocator = cleanLocator(locator);
         if (cleanLocator.isEmpty()) {
            return null;
         }

         ParticleKeyframes.LocatorKey key = new ParticleKeyframes.LocatorKey(instanceId, actorUuid);
         ParticleKeyframes.LocatorSample sample = LOCATOR_POSITIONS.get(key);
         if (sample == null) {
            return null;
         }

         long age = worldTick - sample.worldTick();
         if (age >= 0L && age <= 2L) {
            return sample.positions().get(cleanLocator);
         }

         LOCATOR_POSITIONS.remove(key, sample);
         return null;
      } else {
         return null;
      }
   }

   public static Vec3d findParticleLocatorPosition(UUID instanceId, UUID actorUuid, long worldTick, String locator) {
      String cleanLocator = cleanLocator(locator);
      Vec3d position = findLocatorPosition(instanceId, actorUuid, worldTick, cleanLocator);
      return position == null && "v_locator".equals(cleanLocator) ? findLocatorPosition(instanceId, actorUuid, worldTick, "a_locator") : position;
   }

   public static Vec3d findLocatorDirection(UUID instanceId, UUID actorUuid, long worldTick, String locator) {
      if (instanceId != null && actorUuid != null) {
         String cleanLocator = cleanLocator(locator);
         if (cleanLocator.isEmpty()) {
            return null;
         }

         ParticleKeyframes.LocatorKey key = new ParticleKeyframes.LocatorKey(instanceId, actorUuid);
         ParticleKeyframes.LocatorSample sample = LOCATOR_POSITIONS.get(key);
         if (sample == null) {
            return null;
         }

         long age = worldTick - sample.worldTick();
         if (age >= 0L && age <= 2L) {
            Vec3d direction = sample.directions().get(cleanLocator);
            return direction == null && "v_locator".equals(cleanLocator) ? sample.directions().get("a_locator") : direction;
         }

         LOCATOR_POSITIONS.remove(key, sample);
         return null;
      } else {
         return null;
      }
   }

   public static void updateActorOrigin(UUID actorUuid, Vec3d position) {
      if (actorUuid != null && position != null && position.isFinite()) {
         ACTOR_ORIGINS.put(actorUuid, position);
      }
   }

   public static Vec3d findActorOrigin(UUID actorUuid) {
      return actorUuid == null ? null : ACTOR_ORIGINS.get(actorUuid);
   }

   public static void retainActorOrigins(Set<UUID> activeActorUuids) {
      if (activeActorUuids != null && !activeActorUuids.isEmpty()) {
         ACTOR_ORIGINS.keySet().removeIf(actorUuid -> !activeActorUuids.contains(actorUuid));
      } else {
         ACTOR_ORIGINS.clear();
      }
   }

   public static void clearInstance(UUID instanceId) {
      if (instanceId != null) {
         LOCATOR_POSITIONS.keySet().removeIf(key -> instanceId.equals(key.instanceId()));
      }
   }

   public static void clearAll() {
      LOCATOR_POSITIONS.clear();
      ACTOR_ORIGINS.clear();
   }

   private static String cleanLocator(String locator) {
      return locator == null ? "" : locator.trim();
   }

   private record LocatorKey(UUID instanceId, UUID actorUuid) {
   }

   private record LocatorSample(long worldTick, Map<String, Vec3d> positions, Map<String, Vec3d> directions) {
   }
}
