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

public record AnimationSpeedUpdateS2CPayload(UUID instanceId, double speed, long stageStartTick, long stageEndTick) implements CustomPayload {
   public static final Identifier ID_RAW = AnimationEngine.runtimeId("animation_speed_update");
   public static final Id<AnimationSpeedUpdateS2CPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, AnimationSpeedUpdateS2CPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC,
      AnimationSpeedUpdateS2CPayload::instanceId,
      PacketCodecs.DOUBLE,
      AnimationSpeedUpdateS2CPayload::speed,
      PacketCodecs.LONG,
      AnimationSpeedUpdateS2CPayload::stageStartTick,
      PacketCodecs.LONG,
      AnimationSpeedUpdateS2CPayload::stageEndTick,
      AnimationSpeedUpdateS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
