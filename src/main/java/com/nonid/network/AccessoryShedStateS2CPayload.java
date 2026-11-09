package com.nonid.network;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record AccessoryShedStateS2CPayload(UUID playerUuid, boolean shedV, boolean shedA, boolean forcedV, boolean forcedA) implements CustomPayload {
   public static final Identifier ID_RAW = Identifier.of("needsofnature", "accessory_shed_state");
   public static final Id<AccessoryShedStateS2CPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, AccessoryShedStateS2CPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC,
      AccessoryShedStateS2CPayload::playerUuid,
      PacketCodecs.BOOLEAN,
      AccessoryShedStateS2CPayload::shedV,
      PacketCodecs.BOOLEAN,
      AccessoryShedStateS2CPayload::shedA,
      PacketCodecs.BOOLEAN,
      AccessoryShedStateS2CPayload::forcedV,
      PacketCodecs.BOOLEAN,
      AccessoryShedStateS2CPayload::forcedA,
      AccessoryShedStateS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
