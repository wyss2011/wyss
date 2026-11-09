package com.nonid.internal.animation.network;

import com.nonid.internal.animation.AnimationEngine;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record StopAnimationS2CPayload(UUID instanceId, long stopTick) implements CustomPayload {
   public static final Identifier STOP_ANIMATION_ID = AnimationEngine.runtimeId("stop_animation");
   public static final Id<StopAnimationS2CPayload> ID = new Id(STOP_ANIMATION_ID);
   public static final PacketCodec<RegistryByteBuf, StopAnimationS2CPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC, StopAnimationS2CPayload::instanceId, PacketCodecs.LONG, StopAnimationS2CPayload::stopTick, StopAnimationS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
