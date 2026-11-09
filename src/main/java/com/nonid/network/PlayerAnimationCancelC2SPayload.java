package com.nonid.network;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record PlayerAnimationCancelC2SPayload(UUID requestId) implements CustomPayload {
   public static final Id<PlayerAnimationCancelC2SPayload> ID = new Id(Identifier.of("needsofnature", "player_animation_cancel"));
   public static final PacketCodec<RegistryByteBuf, PlayerAnimationCancelC2SPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC, PlayerAnimationCancelC2SPayload::requestId, PlayerAnimationCancelC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
