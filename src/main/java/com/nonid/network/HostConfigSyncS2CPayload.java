package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HostConfigSyncS2CPayload(String json, String editableJson, long revision, boolean canEdit) implements CustomPayload {
   public static final Identifier ID_RAW = Identifier.of("needsofnature", "host_config_sync");
   public static final Id<HostConfigSyncS2CPayload> ID = new Id(ID_RAW);
   public static final PacketCodec<RegistryByteBuf, HostConfigSyncS2CPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.string(262144),
      HostConfigSyncS2CPayload::json,
      PacketCodecs.string(262144),
      HostConfigSyncS2CPayload::editableJson,
      PacketCodecs.LONG,
      HostConfigSyncS2CPayload::revision,
      PacketCodecs.BOOLEAN,
      HostConfigSyncS2CPayload::canEdit,
      HostConfigSyncS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
