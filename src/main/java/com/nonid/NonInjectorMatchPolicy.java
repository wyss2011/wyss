package com.nonid;

import com.nonid.data.NonLiquidRoles;
import com.nonid.internal.animation.data.AnimationDefinitions;
import com.nonid.internal.animation.data.AnimationGameplayMetadata;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class NonInjectorMatchPolicy {
   private NonInjectorMatchPolicy() {
   }

   public static boolean allowsAutomaticNonMatch(ServerWorld world, AnimationDefinitions.Definition definition, List<Entity> actors) {
      if (world == null || definition == null || actors == null || actors.isEmpty()) {
         return false;
      }

      if (!hasMaleOnlyPlayer(actors)) {
         return true;
      }

      NonLiquidRoles.LiquidRoles roles = AnimationGameplayMetadata.liquidRoles(definition.id());
      if (roles != null && !roles.injectorRoles().isEmpty()) {
         boolean hasV = roles.injectorRoles().containsValue(NonLiquidRoles.InjectorRole.V);
         boolean hasA = roles.injectorRoles().containsValue(NonLiquidRoles.InjectorRole.A);
         if (!hasV) {
            return true;
         } else {
            return hasA ? false : NeedsOfNature.getConfig().convertMaleOnlyVInjectionsToA();
         }
      } else {
         return true;
      }
   }

   private static boolean hasMaleOnlyPlayer(List<Entity> actors) {
      for (Entity actor : actors) {
         if (actor instanceof ServerPlayerEntity && NonGenderSystem.isOnlyMale(actor)) {
            return true;
         }
      }

      return false;
   }
}
