package com.nonid.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ManualDamageDestroyedSkinC2SPayload() implements CustomPayload {
   public static final Identifier MANUAL_DAMAGE_DESTROYED_SKIN_ID = Identifier.of("needsofnature", "manual_damage_destroyed_skin");
   public static final Id<ManualDamageDestroyedSkinC2SPayload> ID = new Id(MANUAL_DAMAGE_DESTROYED_SKIN_ID);
   public static final PacketCodec<RegistryByteBuf, ManualDamageDestroyedSkinC2SPayload> CODEC = PacketCodec.unit(new ManualDamageDestroyedSkinC2SPayload());

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
