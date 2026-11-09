package com.nonid.network;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record PlayerAttackInputC2SPayload(UUID instanceId, int keyId) implements CustomPayload {
   public static final Identifier ID_RAW = Identifier.of("needsofnature", "player_attack_input");
   public static final Id<PlayerAttackInputC2SPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, PlayerAttackInputC2SPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC, PlayerAttackInputC2SPayload::instanceId, PacketCodecs.INTEGER, PlayerAttackInputC2SPayload::keyId, PlayerAttackInputC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
