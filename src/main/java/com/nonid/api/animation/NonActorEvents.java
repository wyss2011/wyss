package com.nonid.api.animation;

import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.NotNull;

public final class NonActorEvents {
   public static final Event<NonActorEvents.@NotNull Provider> PROVIDE = EventFactory.createArrayBacked(NonActorEvents.Provider.class, callbacks -> entity -> {
      if (callbacks.length != 0 && entity != null) {
         LinkedHashSet<String> tags = new LinkedHashSet<>();

         for (NonActorEvents.Provider callback : callbacks) {
            Set<String> provided = callback.provide(entity);
            if (provided != null && !provided.isEmpty()) {
               for (String tag : provided) {
                  if (tag != null && !tag.isBlank()) {
                     tags.add(tag);
                  }
               }
            }
         }

         return tags.isEmpty() ? Set.of() : Set.copyOf(tags);
      } else {
         return Set.of();
      }
   });

   private NonActorEvents() {
   }

   public static Set<String> resolve(Entity entity) {
      if (entity == null) {
         return Set.of();
      }

      Set<String> commandTags = entity.getCommandTags();
      Set<String> provided = ((NonActorEvents.Provider)PROVIDE.invoker()).provide(entity);
      if (provided.isEmpty()) {
         return commandTags;
      }

      LinkedHashSet<String> tags = new LinkedHashSet<>(commandTags);
      tags.addAll(provided);
      return Set.copyOf(tags);
   }

   @FunctionalInterface
   public interface Provider {
      Set<String> provide(Entity var1);
   }
}
