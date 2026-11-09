package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record PregnancyStateS2CPayload(String pregnantEntityTypeId) implements CustomPayload {
   public static final Identifier ID_RAW = Identifier.of("needsofnature", "pregnancy_state");
   public static final Id<PregnancyStateS2CPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, PregnancyStateS2CPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.STRING, PregnancyStateS2CPayload::pregnantEntityTypeId, PregnancyStateS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
