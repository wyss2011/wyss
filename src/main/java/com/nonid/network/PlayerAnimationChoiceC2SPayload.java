package com.nonid.network;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record PlayerAnimationChoiceC2SPayload(UUID requestId, int choiceIndex) implements CustomPayload {
   public static final Id<PlayerAnimationChoiceC2SPayload> ID = new Id(Identifier.of("needsofnature", "player_animation_choice"));
   public static final PacketCodec<RegistryByteBuf, PlayerAnimationChoiceC2SPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC,
      PlayerAnimationChoiceC2SPayload::requestId,
      PacketCodecs.INTEGER,
      PlayerAnimationChoiceC2SPayload::choiceIndex,
      PlayerAnimationChoiceC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
