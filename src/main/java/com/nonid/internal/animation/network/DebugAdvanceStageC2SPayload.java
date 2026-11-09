package com.nonid.internal.animation.network;

import com.nonid.internal.animation.AnimationEngine;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record DebugAdvanceStageC2SPayload(UUID instanceId) implements CustomPayload {
   public static final Identifier DEBUG_ADVANCE_STAGE_ID = AnimationEngine.runtimeId("debug_advance_stage");
   public static final Id<DebugAdvanceStageC2SPayload> ID = new Id(DEBUG_ADVANCE_STAGE_ID);
   public static final PacketCodec<RegistryByteBuf, DebugAdvanceStageC2SPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC, DebugAdvanceStageC2SPayload::instanceId, DebugAdvanceStageC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
