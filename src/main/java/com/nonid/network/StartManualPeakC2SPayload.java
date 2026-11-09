package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record StartManualPeakC2SPayload() implements CustomPayload {
   public static final Identifier START_MANUAL_PEAK_ID = Identifier.of("needsofnature", "start_manual_peak");
   public static final Id<StartManualPeakC2SPayload> ID = new Id(START_MANUAL_PEAK_ID);
   public static final PacketCodec<RegistryByteBuf, StartManualPeakC2SPayload> CODEC = PacketCodec.unit(new StartManualPeakC2SPayload());

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
