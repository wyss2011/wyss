package com.nonid.network;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record MessStateS2CPayload(UUID playerUuid, int vMess, int aMess, int mMess, int tintRgb, int liquidStored, int liquidCapacity) implements CustomPayload {
   public static final Identifier ID_RAW = Identifier.of("needsofnature", "mess_state");
   public static final Id<MessStateS2CPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, MessStateS2CPayload> CODEC = PacketCodec.ofStatic((buf, payload) -> {
      buf.writeUuid(payload.playerUuid);
      buf.writeVarInt(payload.vMess);
      buf.writeVarInt(payload.aMess);
      buf.writeVarInt(payload.mMess);
      buf.writeVarInt(payload.tintRgb);
      buf.writeVarInt(payload.liquidStored);
      buf.writeVarInt(payload.liquidCapacity);
   }, buf -> new MessStateS2CPayload(buf.readUuid(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
