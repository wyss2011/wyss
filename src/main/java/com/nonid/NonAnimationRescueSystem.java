package com.nonid;

import com.nonid.internal.animation.server.ServerAnimationController;
import java.util.Comparator;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

public final class NonAnimationRescueSystem {
   private NonAnimationRescueSystem() {
   }

   static void register() {
      AttackEntityCallback.EVENT
         .register(
            (AttackEntityCallback)(player, world, hand, entity, hitResult) -> {
               if (hand == Hand.MAIN_HAND
                  && world instanceof ServerWorld serverWorld
                  && player instanceof ServerPlayerEntity attacker
                  && entity instanceof ServerPlayerEntity animatedPlayer) {
                  MobEntity mob = findRescueTarget(serverWorld, attacker, animatedPlayer);
                  if (mob == null) {
                     return ActionResult.PASS;
                  }

                  attacker.attack(mob);
                  return ActionResult.SUCCESS;
               } else {
                  return ActionResult.PASS;
               }
            }
         );
   }

   public static Entity redirectProjectileTarget(ServerWorld world, ProjectileEntity projectile, Entity originalTarget) {
      if (projectile.getOwner() instanceof ServerPlayerEntity attacker && originalTarget instanceof ServerPlayerEntity animatedPlayer) {
         MobEntity mob = findRescueTarget(world, attacker, animatedPlayer);
         return (Entity)(mob == null ? originalTarget : mob);
      } else {
         return originalTarget;
      }
   }

   @Nullable
   public static MobEntity findDirectDamageRedirectTarget(ServerWorld world, LivingEntity originalTarget, DamageSource source) {
      if (originalTarget instanceof ServerPlayerEntity animatedPlayer && source != null && source.isDirect()) {
         ServerPlayerEntity attacker;
         if (source.getAttacker() instanceof ServerPlayerEntity attributedAttacker) {
            attacker = attributedAttacker;
         } else {
            if (!(source.getSource() instanceof ServerPlayerEntity directAttacker)) {
               return null;
            }

            attacker = directAttacker;
         }

         return findRescueTarget(world, attacker, animatedPlayer);
      } else {
         return null;
      }
   }

   @Nullable
   private static MobEntity findRescueTarget(ServerWorld world, ServerPlayerEntity attacker, ServerPlayerEntity animatedPlayer) {
      UUID instanceId = ServerAnimationController.findActiveInstanceId(world, animatedPlayer.getUuid());
      if (instanceId == null) {
         return null;
      }

      ServerAnimationController.ActiveInstanceSnapshot snapshot = ServerAnimationController.getActiveInstanceSnapshot(world, instanceId);
      return snapshot != null && !snapshot.actorUuids().contains(attacker.getUuid())
         ? snapshot.actorUuids()
            .stream()
            .<Entity>map(world::getEntity)
            .filter(MobEntity.class::isInstance)
            .map(MobEntity.class::cast)
            .filter(Entity::isAlive)
            .min(Comparator.<MobEntity>comparingDouble(mob -> attacker.squaredDistanceTo(mob)).thenComparingInt(Entity::getId))
            .orElse(null)
         : null;
   }
}
