package com.nonid.network;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record PlayerAnimationRequestStatusS2CPayload(UUID requestId, String state, String messageKey) implements CustomPayload {
   public static final Id<PlayerAnimationRequestStatusS2CPayload> ID = new Id(Identifier.of("needsofnature", "player_animation_request_status"));
   public static final PacketCodec<RegistryByteBuf, PlayerAnimationRequestStatusS2CPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC,
      PlayerAnimationRequestStatusS2CPayload::requestId,
      PacketCodecs.STRING,
      PlayerAnimationRequestStatusS2CPayload::state,
      PacketCodecs.STRING,
      PlayerAnimationRequestStatusS2CPayload::messageKey,
      PlayerAnimationRequestStatusS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
