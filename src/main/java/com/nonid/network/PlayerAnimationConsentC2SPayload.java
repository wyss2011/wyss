package com.nonid.network;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record PlayerAnimationConsentC2SPayload(UUID requestId, boolean accepted) implements CustomPayload {
   public static final Id<PlayerAnimationConsentC2SPayload> ID = new Id(Identifier.of("needsofnature", "player_animation_consent"));
   public static final PacketCodec<RegistryByteBuf, PlayerAnimationConsentC2SPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC,
      PlayerAnimationConsentC2SPayload::requestId,
      PacketCodecs.BOOLEAN,
      PlayerAnimationConsentC2SPayload::accepted,
      PlayerAnimationConsentC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
