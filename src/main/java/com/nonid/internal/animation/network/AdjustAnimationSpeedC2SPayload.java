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

public record AdjustAnimationSpeedC2SPayload(UUID instanceId, double multiplier) implements CustomPayload {
   public static final Identifier ID_RAW = AnimationEngine.runtimeId("adjust_animation_speed");
   public static final Id<AdjustAnimationSpeedC2SPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, AdjustAnimationSpeedC2SPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC,
      AdjustAnimationSpeedC2SPayload::instanceId,
      PacketCodecs.DOUBLE,
      AdjustAnimationSpeedC2SPayload::multiplier,
      AdjustAnimationSpeedC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
