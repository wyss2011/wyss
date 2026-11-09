package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record LiquidStateS2CPayload(int stored, int capacity, int tintRgb) implements CustomPayload {
   public static final Identifier ID_RAW = Identifier.of("needsofnature", "liquid_state");
   public static final Id<LiquidStateS2CPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, LiquidStateS2CPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.INTEGER,
      LiquidStateS2CPayload::stored,
      PacketCodecs.INTEGER,
      LiquidStateS2CPayload::capacity,
      PacketCodecs.INTEGER,
      LiquidStateS2CPayload::tintRgb,
      LiquidStateS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
