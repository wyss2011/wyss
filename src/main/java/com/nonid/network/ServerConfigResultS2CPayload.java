package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ServerConfigResultS2CPayload(boolean success, long revision, String messageKey) implements CustomPayload {
   public static final Id<ServerConfigResultS2CPayload> ID = new Id(Identifier.of("needsofnature", "server_config_result"));
   public static final PacketCodec<RegistryByteBuf, ServerConfigResultS2CPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.BOOLEAN,
      ServerConfigResultS2CPayload::success,
      PacketCodecs.LONG,
      ServerConfigResultS2CPayload::revision,
      PacketCodecs.STRING,
      ServerConfigResultS2CPayload::messageKey,
      ServerConfigResultS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
