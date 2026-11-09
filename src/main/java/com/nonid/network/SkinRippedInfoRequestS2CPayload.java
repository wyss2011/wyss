package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record SkinRippedInfoRequestS2CPayload() implements CustomPayload {
   public static final Identifier ID_RAW = Identifier.of("needsofnature", "skin_ripped_info_request");
   public static final Id<SkinRippedInfoRequestS2CPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, SkinRippedInfoRequestS2CPayload> CODEC = PacketCodec.unit(new SkinRippedInfoRequestS2CPayload());

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
