package com.nonid.api.animation;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NonAnimationEvents {
   public static final Event<NonAnimationEvents.@NotNull BeforeStart> BEFORE_START = EventFactory.createArrayBacked(
      NonAnimationEvents.BeforeStart.class, callbacks -> context -> {
         for (NonAnimationEvents.BeforeStart callback : callbacks) {
            if (!callback.allow(context)) {
               return false;
            }
         }

         return true;
      }
   );
   public static final Event<NonAnimationEvents.@NotNull Started> STARTED = EventFactory.createArrayBacked(
      NonAnimationEvents.Started.class, callbacks -> context -> {
         for (NonAnimationEvents.Started callback : callbacks) {
            callback.onStarted(context);
         }
      }
   );
   public static final Event<NonAnimationEvents.@NotNull Stopped> STOPPED = EventFactory.createArrayBacked(
      NonAnimationEvents.Stopped.class, callbacks -> context -> {
         for (NonAnimationEvents.Stopped callback : callbacks) {
            callback.onStopped(context);
         }
      }
   );
   public static final Event<NonAnimationEvents.@NotNull StageChanged> STAGE_CHANGED = EventFactory.createArrayBacked(
      NonAnimationEvents.StageChanged.class, callbacks -> context -> {
         for (NonAnimationEvents.StageChanged callback : callbacks) {
            callback.onStageChanged(context);
         }
      }
   );

   private NonAnimationEvents() {
   }

   @FunctionalInterface
   public interface BeforeStart {
      boolean allow(NonAnimationEvents.BeforeStartContext var1);
   }

   public record BeforeStartContext(
      ServerWorld world,
      Identifier animationId,
      List<UUID> actorUuids,
      List<String> actorKeys,
      List<NonAnimationStage> stages,
      @Nullable ServerPlayerEntity requester,
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      Map<String, String> metadata
   ) {
      public BeforeStartContext {
         actorUuids = List.copyOf(actorUuids);
         actorKeys = List.copyOf(actorKeys);
         stages = List.copyOf(stages);
         metadata = Map.copyOf(metadata);
      }
   }

   @FunctionalInterface
   public interface StageChanged {
      void onStageChanged(NonAnimationEvents.StageChangedContext var1);
   }

   public record StageChangedContext(
      ServerWorld world,
      UUID instanceId,
      Identifier animationId,
      List<UUID> actorUuids,
      List<String> actorKeys,
      long advanceTick,
      @Nullable NonAnimationStage currentStage
   ) {
      public StageChangedContext {
         actorUuids = List.copyOf(actorUuids);
         actorKeys = List.copyOf(actorKeys);
      }
   }

   @FunctionalInterface
   public interface Started {
      void onStarted(NonAnimationEvents.StartedContext var1);
   }

   public record StartedContext(ServerWorld world, NonAnimationSession session, @Nullable ServerPlayerEntity requester) {
   }

   @FunctionalInterface
   public interface Stopped {
      void onStopped(NonAnimationEvents.StoppedContext var1);
   }

   public record StoppedContext(
      ServerWorld world, UUID instanceId, Identifier animationId, List<UUID> actorUuids, List<String> actorKeys, List<NonAnimationStage> stages
   ) {
      public StoppedContext {
         actorUuids = List.copyOf(actorUuids);
         actorKeys = List.copyOf(actorKeys);
         stages = List.copyOf(stages);
      }
   }
}
