package com.nonid.client;

import com.nonid.NeedsOfNature;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public final class NonSpinModelLock {
   private static final Set<UUID> LOCKED = new HashSet<>();
   private static boolean initialized = false;
   private static boolean available = false;
   private static Method emfEntityOfMethod;
   private static Method lockMethod;
   private static Method unlockMethod;

   private NonSpinModelLock() {
   }

   public static void clientTick(MinecraftClient client) {
      if (client != null) {
         ClientWorld world = client.world;
         if (world == null) {
            LOCKED.clear();
         } else if (!NonDebugSpinMode.isEnabled()) {
            releaseAll(world);
         } else if (ensureApi()) {
            Set<UUID> seen = new HashSet<>();

            for (Entity entity : world.getEntities()) {
               if (entity instanceof LivingEntity living) {
                  UUID uuid = living.getUuid();
                  seen.add(uuid);
                  if (LOCKED.add(uuid)) {
                     lockEntity(living);
                  }
               }
            }

            LOCKED.removeIf(uuidx -> !seen.contains(uuidx));
         }
      }
   }

   private static void releaseAll(ClientWorld world) {
      if (!LOCKED.isEmpty()) {
         if (!ensureApi()) {
            LOCKED.clear();
         } else {
            for (UUID uuid : Set.copyOf(LOCKED)) {
               if (world.getEntity(uuid) instanceof LivingEntity living) {
                  unlockEntity(living);
               }
            }

            LOCKED.clear();
         }
      }
   }

   private static void lockEntity(LivingEntity entity) {
      invokeLockMethod(entity, lockMethod);
   }

   private static void unlockEntity(LivingEntity entity) {
      invokeLockMethod(entity, unlockMethod);
   }

   private static void invokeLockMethod(LivingEntity entity, Method method) {
      if (entity != null && method != null && emfEntityOfMethod != null) {
         try {
            Object emfEntity = emfEntityOfMethod.invoke(null, entity);
            if (emfEntity != null) {
               method.invoke(null, emfEntity);
            }
         } catch (ReflectiveOperationException | RuntimeException e) {
            NeedsOfNature.LOGGER.debug("[NoN] Failed to invoke EMF spin lock integration; disabling it.", e);
            disableApi();
         }
      }
   }

   private static boolean ensureApi() {
      if (initialized) {
         return available;
      }

      initialized = true;

      try {
         Class<?> apiClass = Class.forName("traben.entity_model_features.EMFAnimationApi");
         Class<?> emfEntityClass = Class.forName("traben.entity_model_features.utils.EMFEntity");
         emfEntityOfMethod = apiClass.getMethod("emfEntityOf", Entity.class);
         lockMethod = apiClass.getMethod("lockEntityToVanillaModel", emfEntityClass);
         unlockMethod = apiClass.getMethod("unlockEntityToVanillaModel", emfEntityClass);
         available = true;
      } catch (ClassNotFoundException | NoSuchMethodException e) {
         disableApi();
      }

      return available;
   }

   private static void disableApi() {
      available = false;
      emfEntityOfMethod = null;
      lockMethod = null;
      unlockMethod = null;
   }
}
