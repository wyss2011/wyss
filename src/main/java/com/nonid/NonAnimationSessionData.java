package com.nonid;

import com.nonid.internal.animation.server.AnimationSessionData;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record NonAnimationSessionData(
   NonAnimationSessionData.Mode mode,
   @Nullable UUID breedingRequestId,
   @Nullable NonAnimationSessionData.Pregnancy pregnancy,
   @Nullable NonAnimationSessionData.PlayerAttack playerAttack
) implements AnimationSessionData {
   public NonAnimationSessionData {
      mode = mode == null ? NonAnimationSessionData.Mode.UNSPECIFIED : mode;
   }

   public static NonAnimationSessionData voluntary() {
      return new NonAnimationSessionData(NonAnimationSessionData.Mode.VOLUNTARY, null, null, null);
   }

   public static NonAnimationSessionData attack() {
      return new NonAnimationSessionData(NonAnimationSessionData.Mode.ATTACK, null, null, null);
   }

   public static NonAnimationSessionData breeding(UUID requestId) {
      return new NonAnimationSessionData(NonAnimationSessionData.Mode.UNSPECIFIED, requestId, null, null);
   }

   public static NonAnimationSessionData pregnancy(NonAnimationSessionData.Pregnancy pregnancy) {
      return new NonAnimationSessionData(NonAnimationSessionData.Mode.PREGNANCY, null, pregnancy, null);
   }

   public static NonAnimationSessionData playerAttack(NonAnimationSessionData.PlayerAttack attack) {
      return new NonAnimationSessionData(NonAnimationSessionData.Mode.ATTACK, null, null, attack);
   }

   public NonAnimationSessionData withPlayerAttack(@Nullable NonAnimationSessionData.PlayerAttack attack) {
      return new NonAnimationSessionData(NonAnimationSessionData.Mode.ATTACK, this.breedingRequestId, this.pregnancy, attack);
   }

   public boolean isAttack() {
      return this.mode == NonAnimationSessionData.Mode.ATTACK;
   }

   public boolean isVoluntary() {
      return this.mode == NonAnimationSessionData.Mode.VOLUNTARY;
   }

   public boolean isPregnancy() {
      return this.mode == NonAnimationSessionData.Mode.PREGNANCY;
   }

   public enum Mode {
      UNSPECIFIED,
      VOLUNTARY,
      ATTACK,
      PREGNANCY;
   }

   public record PlayerAttack(UUID victimUuid, UUID finishOwnerUuid, UUID initiatorUuid, Set<UUID> attackerUuids) {
      public PlayerAttack {
         if (victimUuid != null && finishOwnerUuid != null && initiatorUuid != null) {
            LinkedHashSet<UUID> attackers = new LinkedHashSet<>();
            if (attackerUuids != null) {
               attackers.addAll(attackerUuids);
            }

            attackers.remove(victimUuid);
            attackers.add(initiatorUuid);
            attackerUuids = Set.copyOf(attackers);
         } else {
            throw new IllegalArgumentException("Player attack participants must not be null");
         }
      }

      public NonAnimationSessionData.PlayerAttack withAdditionalAttacker(UUID attackerUuid) {
         LinkedHashSet<UUID> attackers = new LinkedHashSet<>(this.attackerUuids);
         if (attackerUuid != null && !attackerUuid.equals(this.victimUuid)) {
            attackers.add(attackerUuid);
         }

         return new NonAnimationSessionData.PlayerAttack(this.victimUuid, this.finishOwnerUuid, attackerUuid, attackers);
      }

      public NonAnimationSessionData.PlayerAttack forDefeatedTail(UUID victim) {
         return new NonAnimationSessionData.PlayerAttack(victim, this.finishOwnerUuid, this.initiatorUuid, this.attackerUuids);
      }
   }

   public record Pregnancy(
      Identifier sourceEntityTypeId,
      Identifier birthEntityTypeId,
      String birthMode,
      int offspringCount,
      @Nullable NeedsOfNature.PregnancyVariantData variantData
   ) {
      public Pregnancy {
         if (sourceEntityTypeId == null) {
            throw new IllegalArgumentException("sourceEntityTypeId must not be null");
         }

         birthEntityTypeId = birthEntityTypeId == null ? sourceEntityTypeId : birthEntityTypeId;
         birthMode = birthMode == null ? "direct" : birthMode;
         offspringCount = Math.max(1, offspringCount);
         variantData = NeedsOfNature.normalizePregnancyVariantData(variantData);
      }
   }
}
