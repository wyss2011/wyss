package com.nonid.network;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record PlayerAnimationConsentS2CPayload(
   UUID requestId, UUID requesterUuid, String requesterName, String animationId, String displayName, String contentTagsJson, boolean joinRequest
) implements CustomPayload {
   public static final Id<PlayerAnimationConsentS2CPayload> ID = new Id(Identifier.of("needsofnature", "player_animation_consent_prompt"));
   public static final PacketCodec<RegistryByteBuf, PlayerAnimationConsentS2CPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC,
      PlayerAnimationConsentS2CPayload::requestId,
      Uuids.PACKET_CODEC,
      PlayerAnimationConsentS2CPayload::requesterUuid,
      PacketCodecs.STRING,
      PlayerAnimationConsentS2CPayload::requesterName,
      PacketCodecs.STRING,
      PlayerAnimationConsentS2CPayload::animationId,
      PacketCodecs.STRING,
      PlayerAnimationConsentS2CPayload::displayName,
      PacketCodecs.STRING,
      PlayerAnimationConsentS2CPayload::contentTagsJson,
      PacketCodecs.BOOLEAN,
      PlayerAnimationConsentS2CPayload::joinRequest,
      PlayerAnimationConsentS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
