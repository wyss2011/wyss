package com.nonid.network;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record DestroyedSkinStateS2CPayload(UUID playerUuid, int stage) implements CustomPayload {
   public static final Identifier ID_RAW = Identifier.of("needsofnature", "destroyed_skin_state");
   public static final Id<DestroyedSkinStateS2CPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, DestroyedSkinStateS2CPayload> CODEC = PacketCodec.ofStatic((buf, payload) -> {
      buf.writeUuid(payload.playerUuid());
      buf.writeVarInt(payload.stage());
   }, buf -> new DestroyedSkinStateS2CPayload(buf.readUuid(), buf.readVarInt()));

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
