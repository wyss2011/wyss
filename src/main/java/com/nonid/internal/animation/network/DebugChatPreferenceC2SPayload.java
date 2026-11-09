package com.nonid.internal.animation.network;

import com.nonid.internal.animation.AnimationEngine;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record DebugChatPreferenceC2SPayload(String modeId) implements CustomPayload {
   public static final Identifier DEBUG_CHAT_PREF_ID = AnimationEngine.runtimeId("debug_chat_pref");
   public static final Id<DebugChatPreferenceC2SPayload> ID = new Id(DEBUG_CHAT_PREF_ID);
   public static final PacketCodec<RegistryByteBuf, DebugChatPreferenceC2SPayload> CODEC = PacketCodec.of(
      DebugChatPreferenceC2SPayload::encode, DebugChatPreferenceC2SPayload::decode
   );

   public Id<? extends CustomPayload> getId() {
      return ID;
   }

   private static void encode(DebugChatPreferenceC2SPayload payload, RegistryByteBuf buf) {
      buf.writeString(payload.modeId());
   }

   private static DebugChatPreferenceC2SPayload decode(RegistryByteBuf buf) {
      return new DebugChatPreferenceC2SPayload(buf.readString(64));
   }
}
