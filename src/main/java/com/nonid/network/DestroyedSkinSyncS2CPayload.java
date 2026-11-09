package com.nonid.network;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record DestroyedSkinSyncS2CPayload(UUID playerUuid, int stage, byte[] pngBytes) implements CustomPayload {
   public static final Identifier ID_RAW = Identifier.of("needsofnature", "destroyed_skin_sync");
   public static final Id<DestroyedSkinSyncS2CPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, DestroyedSkinSyncS2CPayload> CODEC = PacketCodec.ofStatic((buf, payload) -> {
      buf.writeUuid(payload.playerUuid());
      buf.writeVarInt(payload.stage());
      buf.writeByteArray(payload.pngBytes());
   }, buf -> new DestroyedSkinSyncS2CPayload(buf.readUuid(), buf.readVarInt(), buf.readByteArray(1048576)));

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
