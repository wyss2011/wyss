package com.nonid.network;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record StopVoluntaryAnimationC2SPayload(UUID instanceId) implements CustomPayload {
   public static final Identifier STOP_VOLUNTARY_ANIMATION_ID = Identifier.of("needsofnature", "stop_voluntary_animation");
   public static final Id<StopVoluntaryAnimationC2SPayload> ID = new Id(STOP_VOLUNTARY_ANIMATION_ID);
   public static final PacketCodec<RegistryByteBuf, StopVoluntaryAnimationC2SPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC, StopVoluntaryAnimationC2SPayload::instanceId, StopVoluntaryAnimationC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
