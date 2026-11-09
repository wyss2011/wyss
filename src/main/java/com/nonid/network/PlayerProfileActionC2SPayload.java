package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record PlayerProfileActionC2SPayload(String action, String target, String mode) implements CustomPayload {
   public static final Id<PlayerProfileActionC2SPayload> ID = new Id(Identifier.of("needsofnature", "player_profile_action"));
   public static final PacketCodec<RegistryByteBuf, PlayerProfileActionC2SPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.STRING,
      PlayerProfileActionC2SPayload::action,
      PacketCodecs.STRING,
      PlayerProfileActionC2SPayload::target,
      PacketCodecs.STRING,
      PlayerProfileActionC2SPayload::mode,
      PlayerProfileActionC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
