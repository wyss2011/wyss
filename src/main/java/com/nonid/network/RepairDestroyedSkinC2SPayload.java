package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record RepairDestroyedSkinC2SPayload() implements CustomPayload {
   public static final Identifier REPAIR_DESTROYED_SKIN_ID = Identifier.of("needsofnature", "repair_destroyed_skin");
   public static final Id<RepairDestroyedSkinC2SPayload> ID = new Id(REPAIR_DESTROYED_SKIN_ID);
   public static final PacketCodec<RegistryByteBuf, RepairDestroyedSkinC2SPayload> CODEC = PacketCodec.unit(new RepairDestroyedSkinC2SPayload());

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
