package com.nonid.internal.animation.network;

import com.nonid.internal.animation.AnimationEngine;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record DebugStopAnimationC2SPayload(UUID instanceId) implements CustomPayload {
   public static final Identifier DEBUG_STOP_ANIMATION_ID = AnimationEngine.runtimeId("debug_stop_animation");
   public static final Id<DebugStopAnimationC2SPayload> ID = new Id(DEBUG_STOP_ANIMATION_ID);
   public static final PacketCodec<RegistryByteBuf, DebugStopAnimationC2SPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC, DebugStopAnimationC2SPayload::instanceId, DebugStopAnimationC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
