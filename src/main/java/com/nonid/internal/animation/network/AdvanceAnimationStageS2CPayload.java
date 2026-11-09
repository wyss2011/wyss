package com.nonid.internal.animation.network;

import com.nonid.internal.animation.AnimationEngine;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record AdvanceAnimationStageS2CPayload(UUID instanceId, long advanceTick, long stageEndTick, int stageIndex) implements CustomPayload {
   public static final Identifier ADVANCE_STAGE_ID = AnimationEngine.runtimeId("advance_stage");
   public static final Id<AdvanceAnimationStageS2CPayload> ID = new Id(ADVANCE_STAGE_ID);
   public static final PacketCodec<RegistryByteBuf, AdvanceAnimationStageS2CPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC,
      AdvanceAnimationStageS2CPayload::instanceId,
      PacketCodecs.LONG,
      AdvanceAnimationStageS2CPayload::advanceTick,
      PacketCodecs.LONG,
      AdvanceAnimationStageS2CPayload::stageEndTick,
      PacketCodecs.INTEGER,
      AdvanceAnimationStageS2CPayload::stageIndex,
      AdvanceAnimationStageS2CPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
