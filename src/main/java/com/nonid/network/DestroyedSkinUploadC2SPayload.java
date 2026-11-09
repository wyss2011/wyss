package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record DestroyedSkinUploadC2SPayload(byte[] pngBytes) implements CustomPayload {
   public static final int MIN_DIMENSION = 64;
   public static final int MAX_DIMENSION = 512;
   public static final int DIMENSION_STEP = 64;
   public static final int MAX_BYTES = 1048576;
   public static final Identifier ID_RAW = Identifier.of("needsofnature", "destroyed_skin_upload");
   public static final Id<DestroyedSkinUploadC2SPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, DestroyedSkinUploadC2SPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.byteArray(1048576), DestroyedSkinUploadC2SPayload::pngBytes, DestroyedSkinUploadC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
