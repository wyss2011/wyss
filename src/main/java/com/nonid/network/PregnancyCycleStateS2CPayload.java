package com.nonid.network;

import com.nonid.PregnancyCyclePhase;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record PregnancyCycleStateS2CPayload(boolean enabled, int day, PregnancyCyclePhase phase) implements CustomPayload {
   public static final Identifier ID_RAW = Identifier.of("needsofnature", "pregnancy_cycle_state");
   public static final Id<PregnancyCycleStateS2CPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, PregnancyCycleStateS2CPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.BOOLEAN,
      PregnancyCycleStateS2CPayload::enabled,
      PacketCodecs.INTEGER,
      PregnancyCycleStateS2CPayload::day,
      PacketCodecs.INTEGER,
      payload -> payload.phase().ordinal(),
      PregnancyCycleStateS2CPayload::fromPacket
   );

   public PregnancyCycleStateS2CPayload {
      phase = phase == null ? PregnancyCyclePhase.NORMAL : phase;
   }

   private static PregnancyCycleStateS2CPayload fromPacket(boolean enabled, int day, int phaseId) {
      return new PregnancyCycleStateS2CPayload(enabled, day, PregnancyCyclePhase.fromNetworkId(phaseId));
   }

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
