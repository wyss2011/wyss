package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ServerConfigUpdateC2SPayload(long revision, String json) implements CustomPayload {
   public static final Id<ServerConfigUpdateC2SPayload> ID = new Id(Identifier.of("needsofnature", "server_config_update"));
   public static final PacketCodec<RegistryByteBuf, ServerConfigUpdateC2SPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.LONG,
      ServerConfigUpdateC2SPayload::revision,
      PacketCodecs.string(262144),
      ServerConfigUpdateC2SPayload::json,
      ServerConfigUpdateC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
