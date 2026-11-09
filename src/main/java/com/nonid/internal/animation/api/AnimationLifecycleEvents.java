package com.nonid.internal.animation.api;

import com.nonid.api.animation.NonAnimationDamageBehavior;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.server.AnimationStartContext;
import com.nonid.internal.animation.server.ServerAnimationController;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AnimationLifecycleEvents {
   public static final Event<AnimationLifecycleEvents.@NotNull AllowStart> ALLOW_START = EventFactory.createArrayBacked(
      AnimationLifecycleEvents.AllowStart.class, callbacks -> context -> {
         for (AnimationLifecycleEvents.AllowStart callback : callbacks) {
            if (!callback.allowStart(context)) {
               return false;
            }
         }

         return true;
      }
   );
   public static final Event<AnimationLifecycleEvents.@NotNull Start> START = EventFactory.createArrayBacked(
      AnimationLifecycleEvents.Start.class, callbacks -> context -> {
         for (AnimationLifecycleEvents.Start callback : callbacks) {
            callback.onStart(context);
         }
      }
   );
   public static final Event<AnimationLifecycleEvents.@NotNull Stop> STOP = EventFactory.createArrayBacked(
      AnimationLifecycleEvents.Stop.class, callbacks -> context -> {
         for (AnimationLifecycleEvents.Stop callback : callbacks) {
            callback.onStop(context);
         }
      }
   );
   public static final Event<AnimationLifecycleEvents.@NotNull StageAdvance> STAGE_ADVANCE = EventFactory.createArrayBacked(
      AnimationLifecycleEvents.StageAdvance.class, callbacks -> context -> {
         for (AnimationLifecycleEvents.StageAdvance callback : callbacks) {
            callback.onStageAdvance(context);
         }
      }
   );

   private AnimationLifecycleEvents() {
   }

   @FunctionalInterface
   public interface AllowStart {
      boolean allowStart(AnimationLifecycleEvents.AllowStartContext var1);
   }

   public record AllowStartContext(
      ServerWorld world,
      Identifier animationId,
      List<UUID> actorUuids,
      List<String> actorKeys,
      List<AnimationStageInfo> stages,
      @Nullable ServerPlayerEntity requester,
      NonAnimationDamageBehavior damageBehavior,
      boolean ignoreAttackers,
      AnimationStartContext startContext
   ) {
      public AllowStartContext {
         actorUuids = actorUuids == null ? List.of() : List.copyOf(actorUuids);
         actorKeys = actorKeys == null ? List.of() : List.copyOf(actorKeys);
         stages = stages == null ? List.of() : List.copyOf(stages);
         startContext = startContext == null ? AnimationStartContext.empty() : startContext;
      }
   }

   @FunctionalInterface
   public interface StageAdvance {
      void onStageAdvance(AnimationLifecycleEvents.StageChangedContext var1);
   }

   public record StageChangedContext(
      ServerWorld world, ServerAnimationController.ActiveInstanceSnapshot snapshot, AnimationStageInfo currentStage, long advanceTick
   ) {
   }

   @FunctionalInterface
   public interface Start {
      void onStart(AnimationLifecycleEvents.StartedContext var1);
   }

   public record StartedContext(ServerWorld world, ServerAnimationController.ActiveInstanceSnapshot snapshot, @Nullable ServerPlayerEntity requester) {
   }

   @FunctionalInterface
   public interface Stop {
      void onStop(AnimationLifecycleEvents.StoppedContext var1);
   }

   public record StoppedContext(ServerWorld world, ServerAnimationController.ActiveInstanceSnapshot snapshot) {
   }
}
