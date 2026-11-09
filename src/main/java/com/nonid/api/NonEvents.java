package com.nonid.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonEvents {
   public static final Event<NonEvents.PlayerGenderChanged> PLAYER_GENDER_CHANGED = EventFactory.createArrayBacked(
      NonEvents.PlayerGenderChanged.class, callbacks -> (player, gender, source) -> {
         for (NonEvents.PlayerGenderChanged callback : callbacks) {
            callback.onPlayerGenderChanged(player, gender, source);
         }
      }
   );
   public static final Event<NonEvents.LiquidTankChanged> LIQUID_TANK_CHANGED = EventFactory.createArrayBacked(
      NonEvents.LiquidTankChanged.class, callbacks -> (player, snapshot) -> {
         for (NonEvents.LiquidTankChanged callback : callbacks) {
            callback.onLiquidTankChanged(player, snapshot);
         }
      }
   );
   public static final Event<NonEvents.MessChanged> MESS_CHANGED = EventFactory.createArrayBacked(
      NonEvents.MessChanged.class, callbacks -> (player, snapshot) -> {
         for (NonEvents.MessChanged callback : callbacks) {
            callback.onMessChanged(player, snapshot);
         }
      }
   );
   public static final Event<NonEvents.DestroyedSkinChanged> DESTROYED_SKIN_CHANGED = EventFactory.createArrayBacked(
      NonEvents.DestroyedSkinChanged.class, callbacks -> (player, snapshot) -> {
         for (NonEvents.DestroyedSkinChanged callback : callbacks) {
            callback.onDestroyedSkinChanged(player, snapshot);
         }
      }
   );
   public static final Event<NonEvents.PregnancyChanged> PREGNANCY_CHANGED = EventFactory.createArrayBacked(
      NonEvents.PregnancyChanged.class, callbacks -> (player, snapshot) -> {
         for (NonEvents.PregnancyChanged callback : callbacks) {
            callback.onPregnancyChanged(player, snapshot);
         }
      }
   );
   public static final Event<NonEvents.ModifyLiquidGain> MODIFY_LIQUID_GAIN = EventFactory.createArrayBacked(
      NonEvents.ModifyLiquidGain.class, callbacks -> (player, sourceEntityType, amountMl) -> {
         int result = Math.max(0, amountMl);

         for (NonEvents.ModifyLiquidGain callback : callbacks) {
            result = Math.max(0, callback.modifyLiquidGain(player, sourceEntityType, result));
         }

         return result;
      }
   );
   public static final Event<NonEvents.AllowPregnancy> ALLOW_PREGNANCY = EventFactory.createArrayBacked(
      NonEvents.AllowPregnancy.class, callbacks -> (player, entityTypeId) -> {
         for (NonEvents.AllowPregnancy callback : callbacks) {
            if (!callback.allowPregnancy(player, entityTypeId)) {
               return false;
            }
         }

         return true;
      }
   );

   private NonEvents() {
   }

   @FunctionalInterface
   public interface AllowPregnancy {
      boolean allowPregnancy(ServerPlayerEntity var1, Identifier var2);
   }

   @FunctionalInterface
   public interface DestroyedSkinChanged {
      void onDestroyedSkinChanged(ServerPlayerEntity var1, NonDestroyedSkinSnapshot var2);
   }

   @FunctionalInterface
   public interface LiquidTankChanged {
      void onLiquidTankChanged(ServerPlayerEntity var1, NonLiquidTankSnapshot var2);
   }

   @FunctionalInterface
   public interface MessChanged {
      void onMessChanged(ServerPlayerEntity var1, NonMessSnapshot var2);
   }

   @FunctionalInterface
   public interface ModifyLiquidGain {
      int modifyLiquidGain(ServerPlayerEntity var1, @Nullable Identifier var2, int var3);
   }

   @FunctionalInterface
   public interface PlayerGenderChanged {
      void onPlayerGenderChanged(ServerPlayerEntity var1, NonGender var2, NonChangeSource var3);
   }

   @FunctionalInterface
   public interface PregnancyChanged {
      void onPregnancyChanged(ServerPlayerEntity var1, NonPregnancySnapshot var2);
   }
}
