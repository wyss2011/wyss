package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record AnimationCatalogChunkS2CPayload(long revision, int index, int count, String data) implements CustomPayload {
   public static final Id<AnimationCatalogChunkS2CPayload> ID = new Id(Identifier.of("needsofnature", "animation_catalog_chunk"));
   public static final PacketCodec<RegistryByteBuf, AnimationCatalogChunkS2CPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.LONG,
      AnimationCatalogChunkS2CPayload::revision,
      PacketCodecs.VAR_INT,
      AnimationCatalogChunkS2CPayload::index,
      PacketCodecs.VAR_INT,
      AnimationCatalogChunkS2CPayload::count,
      PacketCodecs.STRING,
      AnimationCatalogChunkS2CPayload::data,
      AnimationCatalogChunkS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
