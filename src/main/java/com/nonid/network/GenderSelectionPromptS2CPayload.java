package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record GenderSelectionPromptS2CPayload(int allowedMask, int currentMask, boolean permanent) implements CustomPayload {
   public static final Identifier ID_RAW = Identifier.of("needsofnature", "gender_selection_prompt");
   public static final Id<GenderSelectionPromptS2CPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, GenderSelectionPromptS2CPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.INTEGER,
      GenderSelectionPromptS2CPayload::allowedMask,
      PacketCodecs.INTEGER,
      GenderSelectionPromptS2CPayload::currentMask,
      PacketCodecs.BOOLEAN,
      GenderSelectionPromptS2CPayload::permanent,
      GenderSelectionPromptS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
