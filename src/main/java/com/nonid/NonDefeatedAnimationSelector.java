package com.nonid;

import com.nonid.data.NonPeakStages;
import com.nonid.internal.animation.api.AnimationService;
import com.nonid.internal.animation.data.AnimationDefinitions;
import com.nonid.internal.animation.util.WeightedPicker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

final class NonDefeatedAnimationSelector {
   private static final String DEFEATED_TAG_PREFIX = "defeat.";

   private NonDefeatedAnimationSelector() {
   }

   @Nullable
   static AnimationService.MatchedAnimation find(ServerWorld world, ServerPlayerEntity player, @Nullable Identifier sourceAnimationId) {
      if (world != null && player != null && player.getEntityWorld() == world) {
         List<Entity> actors = List.of(player);
         List<AnimationDefinitions.Definition> candidates = new ArrayList<>();

         for (AnimationDefinitions.Definition definition : AnimationDefinitions.findActorMatchingCandidates(actors)) {
            if (isDefeatedDefinition(definition) && NonInjectorMatchPolicy.allowsAutomaticNonMatch(world, definition, actors)) {
               candidates.add(definition);
            }
         }

         AnimationDefinitions.Definition picked = pickPreferredCandidate(
            candidates,
            AnimationDefinitions.Definition::contentTags,
            AnimationDefinitions.Definition::weight,
            sourceContentTags(sourceAnimationId),
            world.getRandom().nextDouble()
         );
         if (picked == null) {
            return null;
         }

         List<String> actorKeys = AnimationDefinitions.resolveActorKeys(picked, actors, world.getRandom());
         return actorKeys.size() != actors.size()
            ? null
            : new AnimationService.MatchedAnimation(picked.id(), List.copyOf(actorKeys), picked.stages() == null ? List.of() : List.copyOf(picked.stages()));
      } else {
         return null;
      }
   }

   @Nullable
   static <T> T pickPreferredCandidate(
      List<T> candidates,
      Function<T, ? extends Collection<String>> contentTags,
      ToDoubleFunction<T> weight,
      Collection<String> sourceContentTags,
      double randomValue
   ) {
      if (candidates != null && !candidates.isEmpty() && contentTags != null && weight != null) {
         Set<String> sourceTags = normalizeTags(sourceContentTags);
         List<T> preferred = new ArrayList<>();

         for (T candidate : candidates) {
            if (candidate != null) {
               Set<String> candidateTags = normalizeTags((Collection<String>)contentTags.apply(candidate));
               if (sharesContentTag(sourceTags, candidateTags)) {
                  preferred.add(candidate);
               }
            }
         }

         List<T> pool = preferred.isEmpty() ? candidates : preferred;
         return WeightedPicker.pick(pool, weight, randomValue);
      } else {
         return null;
      }
   }

   private static boolean isDefeatedDefinition(AnimationDefinitions.Definition definition) {
      if (definition != null && definition.animationTags() != null) {
         for (String tag : definition.animationTags()) {
            if (tag != null && tag.startsWith("defeat.")) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static List<String> sourceContentTags(@Nullable Identifier sourceAnimationId) {
      if (sourceAnimationId == null) {
         return List.of();
      }

      AnimationDefinitions.Definition source = AnimationDefinitions.getDefinition(NonPeakStages.baseAnimationId(sourceAnimationId));
      return source != null && source.contentTags() != null ? source.contentTags() : List.of();
   }

   private static boolean sharesContentTag(Set<String> first, Set<String> second) {
      if (!first.isEmpty() && !second.isEmpty()) {
         for (String tag : first) {
            if (second.contains(tag)) {
               return true;
            }
         }

         return false;
      } else {
         return first.isEmpty() && second.isEmpty();
      }
   }

   private static Set<String> normalizeTags(Collection<String> tags) {
      if (tags != null && !tags.isEmpty()) {
         LinkedHashSet<String> normalized = new LinkedHashSet<>();

         for (String raw : tags) {
            if (raw != null) {
               String tag = raw.trim().toLowerCase(Locale.ROOT);
               if (!tag.isEmpty()) {
                  normalized.add(tag);
               }
            }
         }

         return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
      } else {
         return Set.of();
      }
   }
}
