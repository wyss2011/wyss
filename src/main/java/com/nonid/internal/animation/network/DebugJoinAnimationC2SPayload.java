package com.nonid.internal.animation.network;

import com.nonid.internal.animation.AnimationEngine;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record DebugJoinAnimationC2SPayload(UUID instanceId, List<Integer> actorEntityIds) implements CustomPayload {
   public static final Identifier DEBUG_JOIN_ANIMATION_ID = AnimationEngine.runtimeId("debug_join_animation");
   public static final Id<DebugJoinAnimationC2SPayload> ID = new Id(DEBUG_JOIN_ANIMATION_ID);
   public static final PacketCodec<RegistryByteBuf, DebugJoinAnimationC2SPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC,
      DebugJoinAnimationC2SPayload::instanceId,
      PacketCodecs.INTEGER.collect(PacketCodecs.toList(32)),
      DebugJoinAnimationC2SPayload::actorEntityIds,
      DebugJoinAnimationC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
