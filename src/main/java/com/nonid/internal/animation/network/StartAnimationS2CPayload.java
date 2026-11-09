package com.nonid.internal.animation.network;

import com.nonid.internal.animation.AnimationEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public record StartAnimationS2CPayload(
   Identifier animationId,
   List<List<Identifier>> modelRootsByActor,
   UUID instanceId,
   List<UUID> actorUuids,
   List<String> actorKeys,
   List<AnimationStageInfo> stages,
   long startTick,
   int stageIndex,
   long stageStartTick,
   long stageEndTick,
   double speed,
   boolean lockOrientation,
   float lockedYaw,
   float lockedHeadYaw,
   float lockedPitch,
   @Nullable Vec3d cameraOrbitTarget
) implements CustomPayload {
   public static final Identifier START_ANIMATION_ID = AnimationEngine.runtimeId("start_animation");
   public static final Id<StartAnimationS2CPayload> ID = new Id(START_ANIMATION_ID);
   private static final int MAX_ACTORS = 16;
   private static final int MAX_STAGES = 16;
   private static final int MAX_MODEL_ROOTS_PER_ACTOR = 32;
   public static final PacketCodec<RegistryByteBuf, StartAnimationS2CPayload> CODEC = PacketCodec.of(
      StartAnimationS2CPayload::encode, StartAnimationS2CPayload::decode
   );

   public StartAnimationS2CPayload {
      actorUuids = List.copyOf(actorUuids);
      modelRootsByActor = modelRootsByActor != null && !modelRootsByActor.isEmpty()
         ? modelRootsByActor.stream().map(rootsx -> rootsx == null ? List.<Identifier>of() : List.copyOf(rootsx)).toList()
         : Collections.nCopies(actorUuids.size(), List.of());
      actorKeys = actorKeys == null ? List.of() : List.copyOf(actorKeys);
      stages = stages == null ? List.of() : List.copyOf(stages);
      if (actorUuids.size() > 16) {
         throw new IllegalArgumentException("Too many actor UUIDs: " + actorUuids.size());
      }

      if (modelRootsByActor.size() != actorUuids.size()) {
         throw new IllegalArgumentException("modelRootsByActor size mismatch: " + modelRootsByActor.size());
      }

      for (List<Identifier> roots : modelRootsByActor) {
         if (roots.size() > 32) {
            throw new IllegalArgumentException("Too many model roots for actor: " + roots.size());
         }
      }

      if (!actorKeys.isEmpty() && actorKeys.size() != actorUuids.size()) {
         throw new IllegalArgumentException("actorKeys size mismatch: " + actorKeys.size());
      }

      if (stages.size() > 16) {
         throw new IllegalArgumentException("Too many stages: " + stages.size());
      }

      stageIndex = stages.isEmpty() ? 0 : Math.max(0, Math.min(stageIndex, stages.size() - 1));
      stageStartTick = Math.max(startTick, stageStartTick);
      if (Double.isNaN(speed) || Double.isInfinite(speed) || speed <= 0.0) {
         speed = 1.0;
      }

      if (!lockOrientation) {
         lockedYaw = 0.0F;
         lockedHeadYaw = 0.0F;
         lockedPitch = 0.0F;
      }

      if (cameraOrbitTarget != null && !cameraOrbitTarget.isFinite()) {
         cameraOrbitTarget = null;
      }
   }

   public Id<? extends CustomPayload> getId() {
      return ID;
   }

   private static void encode(StartAnimationS2CPayload payload, RegistryByteBuf buf) {
      Identifier.PACKET_CODEC.encode(buf, payload.animationId());
      Uuids.PACKET_CODEC.encode(buf, payload.instanceId());
      buf.writeVarInt(payload.actorUuids().size());

      for (UUID uuid : payload.actorUuids()) {
         Uuids.PACKET_CODEC.encode(buf, uuid);
      }

      for (List<Identifier> roots : payload.modelRootsByActor()) {
         buf.writeVarInt(roots.size());

         for (Identifier root : roots) {
            Identifier.PACKET_CODEC.encode(buf, root);
         }
      }

      buf.writeVarInt(payload.actorKeys().size());

      for (String key : payload.actorKeys()) {
         buf.writeString(key);
      }

      buf.writeVarInt(payload.stages().size());

      for (AnimationStageInfo stage : payload.stages()) {
         AnimationStageInfo.CODEC.encode(buf, stage);
      }

      buf.writeLong(payload.startTick());
      buf.writeVarInt(payload.stageIndex());
      buf.writeLong(payload.stageStartTick());
      buf.writeLong(payload.stageEndTick());
      buf.writeDouble(payload.speed());
      buf.writeBoolean(payload.lockOrientation());
      buf.writeFloat(payload.lockedYaw());
      buf.writeFloat(payload.lockedHeadYaw());
      buf.writeFloat(payload.lockedPitch());
      buf.writeBoolean(payload.cameraOrbitTarget() != null);
      if (payload.cameraOrbitTarget() != null) {
         Vec3d target = payload.cameraOrbitTarget();
         buf.writeDouble(target.x);
         buf.writeDouble(target.y);
         buf.writeDouble(target.z);
      }
   }

   private static StartAnimationS2CPayload decode(RegistryByteBuf buf) {
      Identifier animationId = (Identifier)Identifier.PACKET_CODEC.decode(buf);
      UUID instanceId = (UUID)Uuids.PACKET_CODEC.decode(buf);
      int actorCount = buf.readVarInt();
      if (actorCount >= 0 && actorCount <= 16) {
         List<UUID> actorUuids = new ArrayList<>(actorCount);

         for (int i = 0; i < actorCount; i++) {
            actorUuids.add((UUID)Uuids.PACKET_CODEC.decode(buf));
         }

         List<List<Identifier>> modelRootsByActor = new ArrayList<>(actorCount);

         for (int i = 0; i < actorCount; i++) {
            int rootCount = buf.readVarInt();
            if (rootCount < 0 || rootCount > 32) {
               throw new IllegalArgumentException("Too many model roots for actor: " + rootCount);
            }

            List<Identifier> roots = new ArrayList<>(rootCount);

            for (int rootIndex = 0; rootIndex < rootCount; rootIndex++) {
               roots.add((Identifier)Identifier.PACKET_CODEC.decode(buf));
            }

            modelRootsByActor.add(List.copyOf(roots));
         }

         int keyCount = buf.readVarInt();
         if (keyCount >= 0 && keyCount <= 16) {
            List<String> actorKeys = new ArrayList<>(keyCount);

            for (int i = 0; i < keyCount; i++) {
               actorKeys.add(buf.readString(128));
            }

            int stageCount = buf.readVarInt();
            if (stageCount >= 0 && stageCount <= 16) {
               List<AnimationStageInfo> stages = new ArrayList<>(stageCount);

               for (int i = 0; i < stageCount; i++) {
                  stages.add((AnimationStageInfo)AnimationStageInfo.CODEC.decode(buf));
               }

               long startTick = buf.readLong();
               int stageIndex = buf.readVarInt();
               long stageStartTick = buf.readLong();
               long stageEndTick = buf.readLong();
               double speed = buf.readDouble();
               boolean lockOrientation = buf.readBoolean();
               float lockedYaw = buf.readFloat();
               float lockedHeadYaw = buf.readFloat();
               float lockedPitch = buf.readFloat();
               Vec3d cameraOrbitTarget = null;
               if (buf.readBoolean()) {
                  cameraOrbitTarget = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
               }

               return new StartAnimationS2CPayload(
                  animationId,
                  modelRootsByActor,
                  instanceId,
                  actorUuids,
                  actorKeys,
                  stages,
                  startTick,
                  stageIndex,
                  stageStartTick,
                  stageEndTick,
                  speed,
                  lockOrientation,
                  lockedYaw,
                  lockedHeadYaw,
                  lockedPitch,
                  cameraOrbitTarget
               );
            } else {
               throw new IllegalArgumentException("Too many stages: " + stageCount);
            }
         } else {
            throw new IllegalArgumentException("Too many actor keys: " + keyCount);
         }
      } else {
         throw new IllegalArgumentException("Too many actor UUIDs: " + actorCount);
      }
   }
}
