package com.nonid.internal.animation.network;

import com.nonid.internal.animation.AnimationEngine;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record DebugStartAnimationC2SPayload(List<Integer> actorEntityIds, String damageBehaviorId, boolean ignoreAttackers, int anchorEntityId)
   implements CustomPayload {
   public static final Identifier DEBUG_START_ANIMATION_ID = AnimationEngine.runtimeId("debug_start_animation");
   public static final Id<DebugStartAnimationC2SPayload> ID = new Id(DEBUG_START_ANIMATION_ID);
   public static final PacketCodec<RegistryByteBuf, DebugStartAnimationC2SPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.INTEGER.collect(PacketCodecs.toList(32)),
      DebugStartAnimationC2SPayload::actorEntityIds,
      PacketCodecs.STRING,
      DebugStartAnimationC2SPayload::damageBehaviorId,
      PacketCodecs.BOOLEAN,
      DebugStartAnimationC2SPayload::ignoreAttackers,
      PacketCodecs.INTEGER,
      DebugStartAnimationC2SPayload::anchorEntityId,
      DebugStartAnimationC2SPayload::new
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
