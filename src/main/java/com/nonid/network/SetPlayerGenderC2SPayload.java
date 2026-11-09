package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record SetPlayerGenderC2SPayload(int mask, boolean initialSelection) implements CustomPayload {
   public static final Identifier SET_PLAYER_GENDER_ID = Identifier.of("needsofnature", "set_player_gender");
   public static final Id<SetPlayerGenderC2SPayload> ID = new Id(SET_PLAYER_GENDER_ID);
   public static final PacketCodec<RegistryByteBuf, SetPlayerGenderC2SPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.INTEGER, SetPlayerGenderC2SPayload::mask, PacketCodecs.BOOLEAN, SetPlayerGenderC2SPayload::initialSelection, SetPlayerGenderC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
