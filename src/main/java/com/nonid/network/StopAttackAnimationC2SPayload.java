package com.nonid.network;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record StopAttackAnimationC2SPayload(UUID instanceId) implements CustomPayload {
   public static final Identifier STOP_ATTACK_ANIMATION_ID = Identifier.of("needsofnature", "stop_attack_animation");
   public static final Id<StopAttackAnimationC2SPayload> ID = new Id(STOP_ATTACK_ANIMATION_ID);
   public static final PacketCodec<RegistryByteBuf, StopAttackAnimationC2SPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC, StopAttackAnimationC2SPayload::instanceId, StopAttackAnimationC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
