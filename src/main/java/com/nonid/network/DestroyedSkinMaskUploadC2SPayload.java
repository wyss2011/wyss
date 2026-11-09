package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record DestroyedSkinMaskUploadC2SPayload(int stage, byte[] pngBytes) implements CustomPayload {
   public static final int MAX_BYTES = 65536;
   public static final Identifier ID_RAW = Identifier.of("needsofnature", "destroyed_skin_mask_upload");
   public static final Id<DestroyedSkinMaskUploadC2SPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, DestroyedSkinMaskUploadC2SPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.VAR_INT,
      DestroyedSkinMaskUploadC2SPayload::stage,
      PacketCodecs.byteArray(65536),
      DestroyedSkinMaskUploadC2SPayload::pngBytes,
      DestroyedSkinMaskUploadC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
