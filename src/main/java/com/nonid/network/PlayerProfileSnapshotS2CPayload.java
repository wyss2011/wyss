package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record PlayerProfileSnapshotS2CPayload(String json, String errorKey) implements CustomPayload {
   public static final Id<PlayerProfileSnapshotS2CPayload> ID = new Id(Identifier.of("needsofnature", "player_profile_snapshot"));
   public static final PacketCodec<RegistryByteBuf, PlayerProfileSnapshotS2CPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.STRING,
      PlayerProfileSnapshotS2CPayload::json,
      PacketCodecs.STRING,
      PlayerProfileSnapshotS2CPayload::errorKey,
      PlayerProfileSnapshotS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
