package com.nonid.network;

import com.nonid.NonConfig;
import com.nonid.data.NonLiquidRoles;
import com.nonid.data.NonPeakStages;
import com.nonid.internal.animation.data.AnimationDefinitions;
import com.nonid.internal.animation.data.AnimationGameplayMetadata;
import com.nonid.internal.animation.network.AnimationStageInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record AnimationCatalogData(List<AnimationCatalogData.Entry> animations) {
   public static AnimationCatalogData capture(NonConfig config) {
      Set<String> disabledAnimations = config == null ? Set.of() : Set.copyOf(config.getDisabledAnimations());
      Set<String> disabledPacks = config == null ? Set.of() : Set.copyOf(config.getDisabledAnimationPacks());
      List<AnimationCatalogData.Entry> entries = new ArrayList<>();

      for (AnimationDefinitions.Definition definition : AnimationDefinitions.getLoadedDefinitionsSnapshot()) {
         entries.add(fromDefinition(definition, disabledAnimations, disabledPacks));
      }

      entries.sort(Comparator.comparing(AnimationCatalogData.Entry::id));
      return new AnimationCatalogData(List.copyOf(entries));
   }

   private static AnimationCatalogData.Entry fromDefinition(
      AnimationDefinitions.Definition definition, Set<String> disabledAnimations, Set<String> disabledPacks
   ) {
      List<String> actorKeys = AnimationDefinitions.resolveActorKeys(definition);
      NonLiquidRoles.LiquidRoles liquidRoles = AnimationGameplayMetadata.liquidRoles(definition.id());
      List<AnimationCatalogData.Actor> actors = new ArrayList<>(definition.actors().size());

      for (int i = 0; i < definition.actors().size(); i++) {
         AnimationDefinitions.ActorConstraint constraint = definition.actors().get(i);
         List<String> entityTypes = constraint.entityTypes().stream().filter(Objects::nonNull).<String>map(Identifier::toString).sorted().toList();
         List<String> requiredTags = constraint.requiredTags().stream().sorted().toList();
         List<String> requiredTagsAny = constraint.requiredTagsAny().stream().sorted().toList();
         String key = i < actorKeys.size() ? actorKeys.get(i) : constraint.derivedKey();
         NonLiquidRoles.InjectorRole injectorRole = liquidRoles == null
            ? NonLiquidRoles.InjectorRole.NONE
            : liquidRoles.injectorRoles().getOrDefault(key, NonLiquidRoles.InjectorRole.NONE);
         boolean receiver = liquidRoles != null && liquidRoles.receiverKeys().contains(key);
         actors.add(new AnimationCatalogData.Actor(key, entityTypes, requiredTags, requiredTagsAny, injectorRole.name(), receiver));
      }

      List<AnimationCatalogData.Stage> stages = new ArrayList<>();

      for (AnimationStageInfo stage : definition.stages()) {
         double cycleTicks = AnimationGameplayMetadata.cycleTicks(stage.animationId());
         if (cycleTicks <= 0.0) {
            cycleTicks = stage.lengthTicks();
         }

         stages.add(
            new AnimationCatalogData.Stage(
               stage.effectiveAnimationId().toString(),
               stage.loop(),
               stage.lengthTicks(),
               stage.speed(),
               cycleTicks,
               NonPeakStages.stageNumberFromId(stage.animationId())
            )
         );
      }

      if (stages.isEmpty()) {
         stages.add(new AnimationCatalogData.Stage(definition.id().toString(), false, 0L, 1.0, 0.0, null));
      }

      AnimationDefinitions.AnimationPackInfo packInfo = definition.packInfo();
      AnimationCatalogData.Pack pack = packInfo == null
         ? new AnimationCatalogData.Pack("unknown", "unknown", "Unknown Pack", "Unknown", null, null, null, List.of())
         : new AnimationCatalogData.Pack(
            packInfo.id(),
            packInfo.resourcePackId(),
            packInfo.name(),
            packInfo.author(),
            packInfo.version(),
            packInfo.description(),
            packInfo.localModelRoot() == null ? null : packInfo.localModelRoot().toString(),
            packInfo.modelImports()
               .stream()
               .map(
                  modelImport -> new AnimationCatalogData.ModelImport(
                     modelImport.packId(),
                     modelImport.modelRoot().toString(),
                     modelImport.entityTypes().stream().<String>map(Identifier::toString).sorted().toList()
                  )
               )
               .toList()
         );
      String id = definition.id().toString();
      return new AnimationCatalogData.Entry(
         id,
         definition.displayName(),
         List.copyOf(actors),
         List.copyOf(stages),
         NonPeakStages.getPeakStage(definition.id()),
         definition.contentTags() == null ? List.of() : List.copyOf(definition.contentTags()),
         definition.attackEligible(),
         summarizeBlockRequirements(definition.blockRequirements()),
         definition.waterRequirement() == null ? null : definition.waterRequirement().name().toLowerCase(Locale.ROOT),
         pack,
         disabledAnimations.contains(id),
         disabledPacks.contains(pack.id())
      );
   }

   @Nullable
   private static String summarizeBlockRequirements(@Nullable AnimationDefinitions.BlockRequirements requirements) {
      if (requirements == null) {
         return null;
      }

      List<String> parts = new ArrayList<>();
      String type = requirements.type() != null && !requirements.type().isBlank() ? requirements.type() : "unknown";
      parts.add(localizeBlockType(type));
      if (requirements.isWall()) {
         AnimationDefinitions.WallHeight height = requirements.height();
         if (height != null) {
            parts.add(height.max() == null ? "高度 " + height.min() + "+" : "高度 " + height.min() + "-" + height.max());
         }

         AnimationDefinitions.WallWidth width = requirements.width();
         if (width != null && (width.min() > 1 || width.max() != null)) {
            parts.add(width.max() == null ? "宽度 " + width.min() + "+" : "宽度 " + width.min() + "-" + width.max());
         }

         if (requirements.facing() != null) {
            parts.add("朝向 " + localizeFacing(requirements.facing().id()));
         }
      } else if (requirements.isCenterSupport()) {
         if (requirements.support() != null) {
            parts.add(requirements.support().id());
         }

         if (requirements.placement() != null) {
            parts.add(requirements.placement().id());
         }

         if (requirements.surfaceFootprint() != null) {
            AnimationDefinitions.SurfaceFootprint footprint = requirements.surfaceFootprint();
            parts.add("占地 " + footprint.width() + "x" + footprint.depth() + "x" + footprint.height());
            if (footprint.margin() > 0) {
               parts.add("边距 " + footprint.margin());
            }
         } else if (requirements.surfaceRadius() > 0) {
            parts.add("表面半径 " + requirements.surfaceRadius());
         }
      }

      AnimationDefinitions.Clearance clearance = requirements.clearance();
      if (clearance != null) {
         parts.add("净空 " + clearance.width() + "x" + clearance.height() + "x" + clearance.depth());
      }

      AnimationDefinitions.BlockPredicate blocks = requirements.blocks();
      if (blocks != null && !blocks.isEmpty()) {
         parts.add("方块 " + summarizeBlockPredicate(blocks));
      }

      return "方块: " + String.join(" | ", parts);
   }

   private static String localizeBlockType(String type) {
      return switch (type) {
         case "wall" -> "墙";
         case "center support" -> "中心支撑";
         case "surface" -> "表面";
         default -> type.replace('_', ' ');
      };
   }

   private static String localizeFacing(String facing) {
      return switch (facing) {
         case "north" -> "北";
         case "south" -> "南";
         case "east" -> "东";
         case "west" -> "西";
         case "up" -> "上";
         case "down" -> "下";
         default -> facing;
      };
   }

   private static String summarizeBlockPredicate(AnimationDefinitions.BlockPredicate blocks) {
      LinkedHashSet<String> values = new LinkedHashSet<>();
      if (blocks.blockIds() != null) {
         blocks.blockIds().stream().filter(Objects::nonNull).map(Identifier::toString).sorted().forEach(values::add);
      }

      if (blocks.tagIds() != null) {
         blocks.tagIds().stream().filter(Objects::nonNull).map(id -> "#" + id).sorted().forEach(values::add);
      }

      if (values.isEmpty()) {
         return "-";
      }

      List<String> list = List.copyOf(values);
      int take = Math.min(3, list.size());
      String summary = String.join(", ", list.subList(0, take));
      int remaining = list.size() - take;
      return remaining > 0 ? summary + ", +" + remaining : summary;
   }

   public record Actor(String key, List<String> entityTypes, List<String> requiredTags, List<String> requiredTagsAny, String injectorType, boolean receiver) {
   }

   public record Entry(
      String id,
      @Nullable String displayName,
      List<AnimationCatalogData.Actor> actors,
      List<AnimationCatalogData.Stage> stages,
      @Nullable Integer peakStage,
      List<String> contentTags,
      boolean attackEligible,
      @Nullable String blockRequirement,
      @Nullable String waterRequirement,
      AnimationCatalogData.Pack pack,
      boolean animationDisabled,
      boolean packDisabled
   ) {
   }

   public record ModelImport(String packId, String modelRoot, List<String> entityTypes) {
      public ModelImport {
         entityTypes = entityTypes == null ? List.of() : List.copyOf(entityTypes);
      }
   }

   public record Pack(
      String id,
      String resourcePackId,
      String name,
      String author,
      @Nullable String version,
      @Nullable String description,
      @Nullable String localModelRoot,
      List<AnimationCatalogData.ModelImport> modelImports
   ) {
      public Pack {
         modelImports = modelImports == null ? List.of() : List.copyOf(modelImports);
      }
   }

   public record Stage(String animationId, boolean loop, long lengthTicks, double speed, double cycleTicks, @Nullable Integer stageNumber) {
   }
}
