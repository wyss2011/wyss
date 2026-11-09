package com.nonid.internal.animation.network;

import com.nonid.internal.animation.AnimationEngine;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record StopAllAnimationsS2CPayload(long stopTick) implements CustomPayload {
   public static final Identifier STOP_ALL_ID = AnimationEngine.runtimeId("stop_all_animations");
   public static final Id<StopAllAnimationsS2CPayload> ID = new Id(STOP_ALL_ID);
   public static final PacketCodec<RegistryByteBuf, StopAllAnimationsS2CPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.LONG, StopAllAnimationsS2CPayload::stopTick, StopAllAnimationsS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
