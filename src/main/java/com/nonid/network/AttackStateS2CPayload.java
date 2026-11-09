package com.nonid.network;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

public record AttackStateS2CPayload(
   UUID instanceId, boolean attack, boolean escapable, AttackStateS2CPayload.EscapeProfile escapeProfile, AttackStateS2CPayload.PvpRole pvpRole, float progress
) implements CustomPayload {
   public static final Identifier ATTACK_STATE_ID = Identifier.of("needsofnature", "attack_state");
   public static final Id<AttackStateS2CPayload> ID = new Id(ATTACK_STATE_ID);
   public static final PacketCodec<RegistryByteBuf, AttackStateS2CPayload> CODEC = PacketCodec.tuple(
      Uuids.PACKET_CODEC,
      AttackStateS2CPayload::instanceId,
      PacketCodecs.BOOLEAN,
      AttackStateS2CPayload::attack,
      PacketCodecs.BOOLEAN,
      AttackStateS2CPayload::escapable,
      PacketCodecs.INTEGER,
      payload -> payload.escapeProfile().ordinal(),
      PacketCodecs.INTEGER,
      payload -> payload.pvpRole().ordinal(),
      PacketCodecs.FLOAT,
      AttackStateS2CPayload::progress,
      AttackStateS2CPayload::fromPacket
   );

   public AttackStateS2CPayload {
      if (escapeProfile == null) {
         escapeProfile = AttackStateS2CPayload.EscapeProfile.NONE;
      }

      if (pvpRole == null) {
         pvpRole = AttackStateS2CPayload.PvpRole.NONE;
      }

      if (!Float.isFinite(progress)) {
         progress = 0.0F;
      }

      progress = Math.max(0.0F, Math.min(1.0F, progress));
   }

   public AttackStateS2CPayload(UUID instanceId, boolean attack, boolean escapable, AttackStateS2CPayload.EscapeProfile escapeProfile) {
      this(instanceId, attack, escapable, escapeProfile, AttackStateS2CPayload.PvpRole.NONE, 0.0F);
   }

   private static AttackStateS2CPayload fromPacket(UUID instanceId, boolean attack, boolean escapable, int profileId, int roleId, float progress) {
      return new AttackStateS2CPayload(
         instanceId, attack, escapable, AttackStateS2CPayload.EscapeProfile.fromId(profileId), AttackStateS2CPayload.PvpRole.fromId(roleId), progress
      );
   }

   public Id<? extends CustomPayload> getId() {
      return ID;
   }

   public enum EscapeProfile {
      NONE,
      NORMAL,
      DEFEATED;

      public static AttackStateS2CPayload.EscapeProfile fromId(int id) {
         AttackStateS2CPayload.EscapeProfile[] values = values();
         return id >= 0 && id < values.length ? values[id] : NONE;
      }
   }

   public enum PvpRole {
      NONE,
      DEFENDER,
      ATTACKER;

      public static AttackStateS2CPayload.PvpRole fromId(int id) {
         AttackStateS2CPayload.PvpRole[] values = values();
         return id >= 0 && id < values.length ? values[id] : NONE;
      }
   }
}
