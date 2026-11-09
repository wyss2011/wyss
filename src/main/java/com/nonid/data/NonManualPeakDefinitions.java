package com.nonid.data;

import com.nonid.internal.animation.data.AnimationDefinitions;
import com.nonid.internal.animation.data.AnimationGameplayMetadata;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.util.WeightedPicker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonManualPeakDefinitions {
   private static final String MANUAL_PEAK_TAG = "manualpeak";

   private NonManualPeakDefinitions() {
   }

   @Nullable
   public static NonManualPeakDefinitions.Selection select(ServerWorld world, ServerPlayerEntity player) {
      if (world != null && player != null) {
         List<Entity> actors = List.of(player);
         List<AnimationDefinitions.Definition> matching = new ArrayList<>();

         for (AnimationDefinitions.Definition definition : AnimationDefinitions.getLoadedDefinitionsSnapshot()) {
            if (definition != null && definition.matches(actors, Set.of("manualpeak"))) {
               matching.add(definition);
            }
         }

         if (matching.isEmpty()) {
            return null;
         }

         NonManualPeakDefinitions.HeldItemMatch heldItemMatch = findHeldItemMatch(world, matching, player);
         if (heldItemMatch != null) {
            boolean useHeldItemProp = heldItemMatch.rule().propFromHeldItem();
            return buildSelection(
               world,
               actors,
               heldItemMatch.definition(),
               useHeldItemProp ? heldItemMatch.itemId() : null,
               useHeldItemProp ? heldItemMatch.rule().heldItemPropSlot() : null,
               heldItemMatch.itemId()
            );
         }

         List<AnimationDefinitions.Definition> fallback = new ArrayList<>();

         for (AnimationDefinitions.Definition definition : matching) {
            AnimationGameplayMetadata.ManualPeakRule rule = AnimationGameplayMetadata.manualPeakRule(definition.id());
            if (rule == null || rule.heldItems().isEmpty()) {
               fallback.add(definition);
            }
         }

         AnimationDefinitions.Definition picked = pickWeighted(world, fallback);
         return picked == null ? null : buildSelection(world, actors, picked, null, null, null);
      } else {
         return null;
      }
   }

   @Nullable
   private static NonManualPeakDefinitions.HeldItemMatch findHeldItemMatch(
      ServerWorld world, List<AnimationDefinitions.Definition> definitions, ServerPlayerEntity player
   ) {
      List<NonManualPeakDefinitions.HeldItemMatch> matches = new ArrayList<>();

      for (AnimationDefinitions.Definition definition : definitions) {
         AnimationGameplayMetadata.ManualPeakRule rule = AnimationGameplayMetadata.manualPeakRule(definition.id());
         if (rule != null && !rule.heldItems().isEmpty()) {
            Identifier itemId = firstMatchingHeldItem(player, rule.heldItems());
            if (itemId != null) {
               matches.add(new NonManualPeakDefinitions.HeldItemMatch(definition, rule, itemId));
            }
         }
      }

      if (matches.isEmpty()) {
         return null;
      }

      int highestPriority = matches.stream().mapToInt(match -> match.rule().priority()).max().orElse(Integer.MIN_VALUE);
      List<NonManualPeakDefinitions.HeldItemMatch> topPriority = matches.stream()
         .filter(match -> match.rule().priority() == highestPriority)
         .sorted(Comparator.comparing(match -> match.definition().id().toString()))
         .toList();
      return WeightedPicker.pick(topPriority, match -> match.definition().weight(), world.getRandom().nextDouble());
   }

   @Nullable
   private static Identifier firstMatchingHeldItem(ServerPlayerEntity player, Set<Identifier> accepted) {
      Identifier main = itemId(player.getMainHandStack());
      if (main != null && accepted.contains(main)) {
         return main;
      }

      Identifier offhand = itemId(player.getOffHandStack());
      return offhand != null && accepted.contains(offhand) ? offhand : null;
   }

   @Nullable
   private static Identifier itemId(ItemStack stack) {
      return stack != null && !stack.isEmpty() ? Registries.ITEM.getId(stack.getItem()) : null;
   }

   @Nullable
   private static NonManualPeakDefinitions.Selection buildSelection(
      ServerWorld world,
      List<Entity> actors,
      AnimationDefinitions.Definition definition,
      @Nullable Identifier heldItemPropItemId,
      @Nullable AnimationStageInfo.PropSlot heldItemPropSlot,
      @Nullable Identifier matchedHeldItemId
   ) {
      List<String> actorKeys = AnimationDefinitions.resolveActorKeys(definition, actors, world.getRandom());
      return actorKeys != null && actorKeys.size() == actors.size()
         ? new NonManualPeakDefinitions.Selection(definition, List.copyOf(actorKeys), heldItemPropItemId, heldItemPropSlot, matchedHeldItemId)
         : null;
   }

   @Nullable
   private static AnimationDefinitions.Definition pickWeighted(ServerWorld world, List<AnimationDefinitions.Definition> definitions) {
      if (world != null && definitions != null && !definitions.isEmpty()) {
         definitions.sort(Comparator.comparing(definition -> definition.id().toString()));
         return WeightedPicker.pick(definitions, AnimationDefinitions.Definition::weight, world.getRandom().nextDouble());
      } else {
         return null;
      }
   }

   private record HeldItemMatch(AnimationDefinitions.Definition definition, AnimationGameplayMetadata.ManualPeakRule rule, Identifier itemId) {
   }

   public record Selection(
      AnimationDefinitions.Definition definition,
      List<String> actorKeys,
      @Nullable Identifier heldItemPropItemId,
      @Nullable AnimationStageInfo.PropSlot heldItemPropSlot,
      @Nullable Identifier matchedHeldItemId
   ) {
      public List<AnimationStageInfo> stages() {
         if (this.heldItemPropItemId != null && this.heldItemPropSlot != null && !this.actorKeys.isEmpty()) {
            String actorKey = this.actorKeys.getFirst();
            return this.definition.stages().stream().map(stage -> stage.withActorProp(actorKey, this.heldItemPropSlot, this.heldItemPropItemId)).toList();
         } else {
            return this.definition.stages();
         }
      }
   }
}
