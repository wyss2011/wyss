package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ServerConfigRequestC2SPayload() implements CustomPayload {
   public static final Id<ServerConfigRequestC2SPayload> ID = new Id(Identifier.of("needsofnature", "server_config_request"));
   public static final PacketCodec<RegistryByteBuf, ServerConfigRequestC2SPayload> CODEC = PacketCodec.unit(new ServerConfigRequestC2SPayload());

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
