package com.nonid.internal.animation.network;

import com.nonid.internal.animation.AnimationEngine;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record DebugStopAllAnimationsC2SPayload() implements CustomPayload {
   public static final Identifier DEBUG_STOP_ALL_ID = AnimationEngine.runtimeId("debug_stop_all");
   public static final Id<DebugStopAllAnimationsC2SPayload> ID = new Id(DEBUG_STOP_ALL_ID);
   public static final PacketCodec<RegistryByteBuf, DebugStopAllAnimationsC2SPayload> CODEC = PacketCodec.unit(new DebugStopAllAnimationsC2SPayload());

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
