package com.nonid.internal.animation.api;

import com.nonid.api.animation.NonAnimationEvents;
import com.nonid.internal.animation.server.ServerAnimationController;

public final class NonAnimationApiBridge {
   private static boolean initialized;

   private NonAnimationApiBridge() {
   }

   public static synchronized void initialize() {
      if (!initialized) {
         initialized = true;
         AnimationLifecycleEvents.ALLOW_START
            .register(
               (AnimationLifecycleEvents.AllowStart)context -> ((NonAnimationEvents.BeforeStart)NonAnimationEvents.BEFORE_START.invoker())
                  .allow(
                     new NonAnimationEvents.BeforeStartContext(
                        context.world(),
                        context.animationId(),
                        context.actorUuids(),
                        context.actorKeys(),
                        NonAnimationViews.stages(context.stages()),
                        context.requester(),
                        context.damageBehavior(),
                        context.ignoreAttackers(),
                        context.startContext().addonMetadata()
                     )
                  )
            );
         AnimationLifecycleEvents.START
            .register(
               (AnimationLifecycleEvents.Start)context -> ((NonAnimationEvents.Started)NonAnimationEvents.STARTED.invoker())
                  .onStarted(new NonAnimationEvents.StartedContext(context.world(), NonAnimationViews.session(context.snapshot()), context.requester()))
            );
         AnimationLifecycleEvents.STOP
            .register(
               (AnimationLifecycleEvents.Stop)context -> {
                  ServerAnimationController.ActiveInstanceSnapshot snapshot = context.snapshot();
                  ((NonAnimationEvents.Stopped)NonAnimationEvents.STOPPED.invoker())
                     .onStopped(
                        new NonAnimationEvents.StoppedContext(
                           context.world(),
                           snapshot.instanceId(),
                           snapshot.animationId(),
                           snapshot.actorUuids(),
                           snapshot.actorKeys(),
                           NonAnimationViews.stages(snapshot.stages())
                        )
                     );
               }
            );
         AnimationLifecycleEvents.STAGE_ADVANCE
            .register(
               (AnimationLifecycleEvents.StageAdvance)context -> {
                  ServerAnimationController.ActiveInstanceSnapshot snapshot = context.snapshot();
                  ((NonAnimationEvents.StageChanged)NonAnimationEvents.STAGE_CHANGED.invoker())
                     .onStageChanged(
                        new NonAnimationEvents.StageChangedContext(
                           context.world(),
                           snapshot.instanceId(),
                           snapshot.animationId(),
                           snapshot.actorUuids(),
                           snapshot.actorKeys(),
                           context.advanceTick(),
                           NonAnimationViews.stage(context.currentStage())
                        )
                     );
               }
            );
      }
   }
}
