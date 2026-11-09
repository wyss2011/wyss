package com.nonid.network;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record PlayerAnimationSelectionS2CPayload(UUID requestId, UUID targetUuid, String targetName, String candidatesJson, boolean joinRequest)
   implements CustomPayload {
   public static final Id<PlayerAnimationSelectionS2CPayload> ID = new Id(Identifier.of("needsofnature", "player_animation_selection"));
   public static final PacketCodec<RegistryByteBuf, PlayerAnimationSelectionS2CPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC,
      PlayerAnimationSelectionS2CPayload::requestId,
      Uuids.PACKET_CODEC,
      PlayerAnimationSelectionS2CPayload::targetUuid,
      PacketCodecs.STRING,
      PlayerAnimationSelectionS2CPayload::targetName,
      PacketCodecs.STRING,
      PlayerAnimationSelectionS2CPayload::candidatesJson,
      PacketCodecs.BOOLEAN,
      PlayerAnimationSelectionS2CPayload::joinRequest,
      PlayerAnimationSelectionS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
