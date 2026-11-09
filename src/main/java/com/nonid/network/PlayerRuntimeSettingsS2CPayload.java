package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record PlayerRuntimeSettingsS2CPayload(int attackEscapeHits) implements CustomPayload {
   public static final Identifier ID_RAW = Identifier.of("needsofnature", "player_runtime_settings");
   public static final Id<PlayerRuntimeSettingsS2CPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, PlayerRuntimeSettingsS2CPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.INTEGER, PlayerRuntimeSettingsS2CPayload::attackEscapeHits, PlayerRuntimeSettingsS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
