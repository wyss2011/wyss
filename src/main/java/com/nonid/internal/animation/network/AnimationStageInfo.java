package com.nonid.internal.animation.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nonid.api.client.NonAnimationRenderEvents;
import com.nonid.internal.animation.util.StageTimeWarp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record AnimationStageInfo(
   Identifier animationId,
   boolean loop,
   long lengthTicks,
   boolean allowJoin,
   double speed,
   Identifier playbackAnimationId,
   List<AnimationStageInfo.ActorPropEntry> actorProps,
   double cycleMidpointOffsetSeconds,
   double cycleTicks
) {
   public static final Codec<AnimationStageInfo> DATA_CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(AnimationStageInfo::animationId),
            Codec.BOOL.optionalFieldOf("loop", true).forGetter(AnimationStageInfo::loop),
            Codec.DOUBLE.optionalFieldOf("cycle_seconds", 0.0).forGetter(stage -> exactTicksToSeconds(stage.cycleTicks())),
            Codec.BOOL.optionalFieldOf("allow_join", true).forGetter(AnimationStageInfo::allowJoin),
            Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(AnimationStageInfo::speed),
            Codec.DOUBLE.optionalFieldOf("cycle_midpoint_offset_seconds", 0.0).forGetter(AnimationStageInfo::cycleMidpointOffsetSeconds)
         )
         .apply(
            instance,
            (animationId, loop, cycleSeconds, allowJoin, speed, offset) -> new AnimationStageInfo(
               animationId, loop, secondsToTicks(cycleSeconds), allowJoin, speed, animationId, List.of(), offset, secondsToExactTicks(cycleSeconds)
            )
         )
   );
   public static final PacketCodec<RegistryByteBuf, AnimationStageInfo> CODEC = PacketCodec.tuple(
      Identifier.PACKET_CODEC.cast(),
      AnimationStageInfo::animationId,
      PacketCodecs.BOOLEAN,
      AnimationStageInfo::loop,
      PacketCodecs.LONG,
      AnimationStageInfo::lengthTicks,
      PacketCodecs.BOOLEAN,
      AnimationStageInfo::allowJoin,
      PacketCodecs.DOUBLE,
      AnimationStageInfo::speed,
      Identifier.PACKET_CODEC.cast(),
      AnimationStageInfo::playbackAnimationId,
      AnimationStageInfo.ActorPropEntry.CODEC.collect(PacketCodecs.toList()).xmap(List::copyOf, list -> list).cast(),
      AnimationStageInfo::actorProps,
      PacketCodecs.DOUBLE,
      AnimationStageInfo::cycleMidpointOffsetSeconds,
      PacketCodecs.DOUBLE,
      AnimationStageInfo::cycleTicks,
      AnimationStageInfo::new
   );

   public AnimationStageInfo {
      if (animationId == null) {
         throw new IllegalArgumentException("animationId must not be null");
      }

      if (playbackAnimationId == null) {
         playbackAnimationId = animationId;
      }

      if (lengthTicks < 0L) {
         lengthTicks = 0L;
      }

      if (!Double.isFinite(cycleTicks) || cycleTicks <= 0.0) {
         cycleTicks = lengthTicks > 0L ? lengthTicks : 0.0;
      }

      actorProps = sanitizeActorProps(actorProps);
      if (loop && !(cycleTicks <= 0.0) && Double.isFinite(cycleMidpointOffsetSeconds)) {
         cycleMidpointOffsetSeconds = StageTimeWarp.clampOffsetSeconds(cycleTicks, cycleMidpointOffsetSeconds);
      } else {
         cycleMidpointOffsetSeconds = 0.0;
      }
   }

   public AnimationStageInfo(Identifier animationId, boolean loop, long lengthTicks, boolean allowJoin, double speed) {
      this(animationId, loop, lengthTicks, allowJoin, speed, animationId, List.of(), 0.0, lengthTicks);
   }

   public AnimationStageInfo(Identifier animationId, boolean loop, long lengthTicks, boolean allowJoin, double speed, Identifier playbackAnimationId) {
      this(animationId, loop, lengthTicks, allowJoin, speed, playbackAnimationId, List.of(), 0.0, lengthTicks);
   }

   public AnimationStageInfo(
      Identifier animationId,
      boolean loop,
      long lengthTicks,
      boolean allowJoin,
      double speed,
      Identifier playbackAnimationId,
      List<AnimationStageInfo.ActorPropEntry> actorProps
   ) {
      this(animationId, loop, lengthTicks, allowJoin, speed, playbackAnimationId, actorProps, 0.0, lengthTicks);
   }

   public AnimationStageInfo(
      Identifier animationId,
      boolean loop,
      long lengthTicks,
      boolean allowJoin,
      double speed,
      Identifier playbackAnimationId,
      List<AnimationStageInfo.ActorPropEntry> actorProps,
      double cycleMidpointOffsetSeconds
   ) {
      this(animationId, loop, lengthTicks, allowJoin, speed, playbackAnimationId, actorProps, cycleMidpointOffsetSeconds, lengthTicks);
   }

   private static long secondsToTicks(double seconds) {
      return Double.isFinite(seconds) && !(seconds <= 0.0) ? Math.max(1L, (long)Math.ceil(seconds * 20.0)) : 0L;
   }

   private static double ticksToSeconds(long ticks) {
      return ticks <= 0L ? 0.0 : ticks / 20.0;
   }

   private static double secondsToExactTicks(double seconds) {
      return Double.isFinite(seconds) && !(seconds <= 0.0) ? seconds * 20.0 : 0.0;
   }

   private static double exactTicksToSeconds(double ticks) {
      return Double.isFinite(ticks) && !(ticks <= 0.0) ? ticks / 20.0 : 0.0;
   }

   @Nullable
   public AnimationStageInfo.ActorPropEntry actorPropsForKey(@Nullable String actorKey) {
      if (actorKey != null && !actorKey.isBlank() && !this.actorProps.isEmpty()) {
         for (AnimationStageInfo.ActorPropEntry entry : this.actorProps) {
            if (entry != null && actorKey.equals(entry.actorKey())) {
               return entry;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public AnimationStageInfo withActorProp(String actorKey, AnimationStageInfo.PropSlot slot, Identifier itemId) {
      if (actorKey != null && !actorKey.isBlank() && slot != null && itemId != null) {
         List<AnimationStageInfo.ActorPropEntry> updated = new ArrayList<>(this.actorProps);
         boolean replaced = false;

         for (int i = 0; i < updated.size(); i++) {
            AnimationStageInfo.ActorPropEntry entry = updated.get(i);
            if (entry != null && actorKey.equals(entry.actorKey())) {
               updated.set(i, withProp(entry, slot, itemId));
               replaced = true;
               break;
            }
         }

         if (!replaced) {
            updated.add(withProp(new AnimationStageInfo.ActorPropEntry(actorKey, null, null, null), slot, itemId));
         }

         return new AnimationStageInfo(
            this.animationId,
            this.loop,
            this.lengthTicks,
            this.allowJoin,
            this.speed,
            this.playbackAnimationId,
            updated,
            this.cycleMidpointOffsetSeconds,
            this.cycleTicks
         );
      } else {
         return this;
      }
   }

   private static AnimationStageInfo.ActorPropEntry withProp(AnimationStageInfo.ActorPropEntry entry, AnimationStageInfo.PropSlot slot, Identifier itemId) {
      return switch (slot) {
         case LEFT -> new AnimationStageInfo.ActorPropEntry(entry.actorKey(), itemId, entry.propRightItemId(), entry.propFloorItemId());
         case RIGHT -> new AnimationStageInfo.ActorPropEntry(entry.actorKey(), entry.propLeftItemId(), itemId, entry.propFloorItemId());
         case FLOOR -> new AnimationStageInfo.ActorPropEntry(entry.actorKey(), entry.propLeftItemId(), entry.propRightItemId(), itemId);
      };
   }

   public Identifier effectiveAnimationId() {
      return this.playbackAnimationId;
   }

   private static List<AnimationStageInfo.ActorPropEntry> sanitizeActorProps(List<AnimationStageInfo.ActorPropEntry> props) {
      if (props != null && !props.isEmpty()) {
         Map<String, AnimationStageInfo.ActorPropEntry> dedup = new LinkedHashMap<>();

         for (AnimationStageInfo.ActorPropEntry entry : props) {
            if (entry != null) {
               String key = entry.actorKey() == null ? "" : entry.actorKey().trim();
               if (!key.isBlank()) {
                  Identifier left = entry.propLeftItemId();
                  Identifier right = entry.propRightItemId();
                  Identifier floor = entry.propFloorItemId();
                  if (left != null || right != null || floor != null) {
                     dedup.put(key, new AnimationStageInfo.ActorPropEntry(key, left, right, floor));
                  }
               }
            }
         }

         if (dedup.isEmpty()) {
            return List.of();
         }

         List<AnimationStageInfo.ActorPropEntry> out = new ArrayList<>(dedup.values());
         return List.copyOf(out);
      } else {
         return List.of();
      }
   }

   public record ActorPropEntry(
      String actorKey, @Nullable Identifier propLeftItemId, @Nullable Identifier propRightItemId, @Nullable Identifier propFloorItemId
   ) {
      private static final PacketCodec<RegistryByteBuf, String> NULLABLE_IDENTIFIER_STRING_CODEC = PacketCodecs.STRING.cast();
      public static final PacketCodec<RegistryByteBuf, AnimationStageInfo.ActorPropEntry> CODEC = PacketCodec.tuple(
         PacketCodecs.STRING,
         AnimationStageInfo.ActorPropEntry::actorKey,
         NULLABLE_IDENTIFIER_STRING_CODEC,
         entry -> identifierToString(entry.propLeftItemId()),
         NULLABLE_IDENTIFIER_STRING_CODEC,
         entry -> identifierToString(entry.propRightItemId()),
         NULLABLE_IDENTIFIER_STRING_CODEC,
         entry -> identifierToString(entry.propFloorItemId()),
         (actorKey, leftId, rightId, floorId) -> new AnimationStageInfo.ActorPropEntry(
            actorKey, parseIdentifier(leftId), parseIdentifier(rightId), parseIdentifier(floorId)
         )
      );

      public ActorPropEntry {
         actorKey = actorKey == null ? "" : actorKey.trim();
      }

      public boolean isEmpty() {
         return this.propLeftItemId == null && this.propRightItemId == null && this.propFloorItemId == null;
      }

      @Nullable
      public ItemStack leftStack() {
         return createStack(this.propLeftItemId);
      }

      @Nullable
      public ItemStack rightStack() {
         return createStack(this.propRightItemId);
      }

      @Nullable
      public ItemStack floorStack() {
         return createStack(this.propFloorItemId);
      }

      @Nullable
      public NonAnimationRenderEvents.BoneItemProp leftBoneProp() {
         ItemStack stack = this.leftStack();
         return stack != null && !stack.isEmpty() ? new NonAnimationRenderEvents.BoneItemProp(stack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND) : null;
      }

      @Nullable
      public NonAnimationRenderEvents.BoneItemProp rightBoneProp() {
         ItemStack stack = this.rightStack();
         return stack != null && !stack.isEmpty() ? new NonAnimationRenderEvents.BoneItemProp(stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) : null;
      }

      @Nullable
      public NonAnimationRenderEvents.BoneItemProp floorBoneProp() {
         ItemStack stack = this.floorStack();
         return stack != null && !stack.isEmpty() ? new NonAnimationRenderEvents.BoneItemProp(stack, ItemDisplayContext.GROUND) : null;
      }

      @Nullable
      private static ItemStack createStack(@Nullable Identifier itemId) {
         return itemId != null && Registries.ITEM.containsId(itemId) ? new ItemStack((ItemConvertible)Registries.ITEM.get(itemId)) : null;
      }

      @Nullable
      private static Identifier parseIdentifier(String raw) {
         if (raw != null && !raw.isBlank()) {
            Identifier id = Identifier.tryParse(raw);
            return id != null && Registries.ITEM.containsId(id) ? id : null;
         } else {
            return null;
         }
      }

      private static String identifierToString(@Nullable Identifier id) {
         return id == null ? "" : id.toString();
      }
   }

   public enum PropSlot {
      LEFT,
      RIGHT,
      FLOOR;
   }
}
