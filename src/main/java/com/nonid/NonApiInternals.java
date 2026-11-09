package com.nonid;

import com.nonid.api.NonAccessoryEffectsSnapshot;
import com.nonid.api.NonChangeSource;
import com.nonid.api.NonDestroyedSkinSnapshot;
import com.nonid.api.NonEvents;
import com.nonid.api.NonGender;
import com.nonid.api.NonLiquidComposition;
import com.nonid.api.NonLiquidTankSnapshot;
import com.nonid.api.NonMessSnapshot;
import com.nonid.api.NonMessType;
import com.nonid.api.NonPlayerStateSnapshot;
import com.nonid.api.NonPregnancySnapshot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonApiInternals {
   private static final int DESTROYED_SKIN_MAX_DAMAGE = 10;

   private NonApiInternals() {
   }

   public static NonPlayerStateSnapshot snapshotPlayerState(PlayerEntity player) {
      return new NonPlayerStateSnapshot(
         getGender(player),
         snapshotLiquid(player),
         snapshotMess(player),
         snapshotDestroyedSkin(player),
         snapshotPregnancy(player),
         snapshotAccessoryEffects(player)
      );
   }

   public static NonGender getGender(PlayerEntity player) {
      return player instanceof GenderHolder holder ? NonGender.fromMask(holder.getGenderMask()) : NonGender.NONE;
   }

   public static boolean setGender(ServerPlayerEntity player, NonGender gender, NonChangeSource source) {
      if (player == null || gender == null || gender == NonGender.NONE) {
         return false;
      }

      if (!canChangeGender(player, source)) {
         return false;
      }

      int before = player instanceof GenderHolder holder ? holder.getGenderMask() & 3 : 0;
      int applied = NonGenderSystem.setPlayerGenderFromCommand(player, gender.mask(), source);
      return (before & 3) != (applied & 3);
   }

   public static boolean canChangeGender(ServerPlayerEntity player, NonChangeSource source) {
      if (player != null && source != null) {
         return switch (source) {
            case API, COMMAND, ITEM, JOIN_SELECTION, SYSTEM -> true;
            case CLIENT_PREFERENCE -> NonGenderSystem.canClientChangeGenderFreely();
         };
      } else {
         return false;
      }
   }

   public static NonLiquidTankSnapshot snapshotLiquid(PlayerEntity player) {
      return player instanceof LiquidHolder holder
         ? new NonLiquidTankSnapshot(
            Math.max(0, holder.getLiquidStored()),
            Math.max(0, holder.getLiquidCapacity()),
            toApiLiquidComposition(holder.getLiquidComposition()),
            holder.getLiquidEntityTypeId(),
            NonLiquidSystem.resolveLiquidTintRgb(holder)
         )
         : new NonLiquidTankSnapshot(0, 0, NonLiquidComposition.EMPTY, null, 16777215);
   }

   public static int addLiquid(ServerPlayerEntity player, @Nullable Identifier sourceEntityType, int amountMl) {
      if (!NeedsOfNature.getConfig().isLiquidTankEnabled()) {
         return 0;
      }

      if (player != null && player instanceof LiquidHolder holder && amountMl > 0) {
         int modifiedAmount = ((NonEvents.ModifyLiquidGain)NonEvents.MODIFY_LIQUID_GAIN.invoker()).modifyLiquidGain(player, sourceEntityType, amountMl);
         if (modifiedAmount <= 0) {
            return 0;
         }

         int added = addLiquidWithComposition(holder, sourceEntityType, modifiedAmount);
         if (added > 0) {
            NonPregnancyConceptionSystem.recordRecentDonor(holder, sourceEntityType, null);
            NeedsOfNature.syncLiquidState(player);
         }

         return added;
      } else {
         return 0;
      }
   }

   public static int drainLiquid(ServerPlayerEntity player, int amountMl) {
      if (player != null && player instanceof LiquidHolder holder && amountMl > 0) {
         int before = Math.max(0, holder.getLiquidStored());
         int removed = Math.min(before, amountMl);
         if (removed <= 0) {
            return 0;
         }

         int next = before - removed;
         holder.setLiquidStored(next);
         if (next <= 0) {
            holder.clearLiquidComposition();
         }

         NeedsOfNature.syncLiquidState(player);
         return removed;
      } else {
         return 0;
      }
   }

   public static NonMessSnapshot snapshotMess(PlayerEntity player) {
      return player instanceof MessHolder holder
         ? new NonMessSnapshot(
            NonMessSystem.clampMess(holder.getVMess()), NonMessSystem.clampMess(holder.getAMess()), NonMessSystem.clampMess(holder.getMMess())
         )
         : new NonMessSnapshot(0, 0, 0);
   }

   public static boolean addMess(ServerPlayerEntity player, NonMessType type, int amount) {
      return changeMess(player, type, Math.max(0, amount));
   }

   public static boolean cleanMess(ServerPlayerEntity player, NonMessType type, int amount) {
      return changeMess(player, type, -Math.max(0, amount));
   }

   public static boolean clearMess(ServerPlayerEntity player) {
      if (player != null && player instanceof MessHolder holder) {
         boolean changed = holder.getVMess() != 0 || holder.getAMess() != 0 || holder.getMMess() != 0;
         NonMessSystem.clearMess(player);
         return changed;
      } else {
         return false;
      }
   }

   public static NonDestroyedSkinSnapshot snapshotDestroyedSkin(PlayerEntity player) {
      return player instanceof DestroyedSkinHolder holder
         ? new NonDestroyedSkinSnapshot(
            Math.max(0, Math.min(4, holder.getDestroyedSkinStage())), Math.max(0, Math.min(10, holder.getDestroyedSkinDamage())), 10
         )
         : new NonDestroyedSkinSnapshot(0, 0, 10);
   }

   public static boolean damageDestroyedSkin(ServerPlayerEntity player, int amount) {
      return changeDestroyedSkinDamage(player, Math.max(0, amount));
   }

   public static boolean repairDestroyedSkin(ServerPlayerEntity player, int amount) {
      return changeDestroyedSkinDamage(player, -Math.max(0, amount));
   }

   public static NonPregnancySnapshot snapshotPregnancy(PlayerEntity player) {
      return player instanceof PregnancyHolder holder
         ? new NonPregnancySnapshot(holder.getPregnantEntityTypeId(), holder.getPregnancyEndTick(), holder.isPregnancyPendingHatch())
         : new NonPregnancySnapshot(null, 0L, false);
   }

   public static boolean beginPregnancy(ServerPlayerEntity player, Identifier entityTypeId, long endTick) {
      if (!NeedsOfNature.getConfig().isPregnancyEnabled()) {
         return false;
      } else if (player != null && entityTypeId != null) {
         NeedsOfNature.beginPregnancy(player, entityTypeId, Math.max(1L, endTick));
         return true;
      } else {
         return false;
      }
   }

   public static boolean clearPregnancy(ServerPlayerEntity player) {
      if (player == null) {
         return false;
      }

      boolean hadPregnancy = player instanceof PregnancyHolder holder && holder.getPregnantEntityTypeId() != null;
      NeedsOfNature.clearPregnancyState(player, true);
      return hadPregnancy;
   }

   public static NonAccessoryEffectsSnapshot snapshotAccessoryEffects(PlayerEntity player) {
      NonAccessoryEffects effects = player instanceof LivingEntity living ? NonTrinketsIntegration.getAccessoryEffects(living) : NonAccessoryEffects.NEUTRAL;
      return new NonAccessoryEffectsSnapshot(
         effects.liquidDecayMultiplier(),
         effects.equalizeLiquidDecayContext(),
         effects.playerEnergyGainMultiplier(),
         effects.liquidCapacityAdd(),
         effects.liquidGainMultiplier(),
         effects.filledEffectMultiplier(),
         effects.pregnancyChanceMultiplier(),
         effects.pregnancyDurationMultiplier(),
         effects.messGainMultiplier(),
         effects.destroyedSkinDamageMultiplier(),
         effects.attackEscapeHitsAdd(),
         effects.attackEscapeDamageMultiplier(),
         effects.playerEnergyAuraMultiplier(),
         effects.nearAnimationMobEnergyMultiplier()
      );
   }

   public static boolean isMessSystemEnabled() {
      return NeedsOfNature.getConfig().isMessSystemEnabled();
   }

   public static boolean isDestroyedSkinSystemEnabled() {
      return NeedsOfNature.getConfig().isDestroyedSkinSystemEnabled();
   }

   public static boolean isLiquidTankEnabled() {
      return NeedsOfNature.getConfig().isLiquidTankEnabled();
   }

   public static boolean isPregnancyEnabled() {
      return NeedsOfNature.getConfig().isPregnancyEnabled();
   }

   public static void fireLiquidChanged(ServerPlayerEntity player) {
      if (player != null) {
         ((NonEvents.LiquidTankChanged)NonEvents.LIQUID_TANK_CHANGED.invoker()).onLiquidTankChanged(player, snapshotLiquid(player));
      }
   }

   public static void fireMessChanged(ServerPlayerEntity player) {
      if (player != null) {
         ((NonEvents.MessChanged)NonEvents.MESS_CHANGED.invoker()).onMessChanged(player, snapshotMess(player));
      }
   }

   public static void fireDestroyedSkinChanged(ServerPlayerEntity player) {
      if (player != null) {
         ((NonEvents.DestroyedSkinChanged)NonEvents.DESTROYED_SKIN_CHANGED.invoker()).onDestroyedSkinChanged(player, snapshotDestroyedSkin(player));
      }
   }

   public static void firePregnancyChanged(ServerPlayerEntity player) {
      if (player != null) {
         ((NonEvents.PregnancyChanged)NonEvents.PREGNANCY_CHANGED.invoker()).onPregnancyChanged(player, snapshotPregnancy(player));
      }
   }

   public static void fireGenderChanged(ServerPlayerEntity player, int mask, NonChangeSource source) {
      if (player != null) {
         ((NonEvents.PlayerGenderChanged)NonEvents.PLAYER_GENDER_CHANGED.invoker()).onPlayerGenderChanged(player, NonGender.fromMask(mask), source);
      }
   }

   private static int addLiquidWithComposition(LiquidHolder holder, @Nullable Identifier sourceEntityType, int amount) {
      int stored = Math.max(0, holder.getLiquidStored());
      int capacity = Math.max(0, holder.getLiquidCapacity());
      int room = Math.max(0, capacity - stored);
      int added = Math.min(room, amount);
      if (added <= 0) {
         return 0;
      }

      if (stored <= 0) {
         if (sourceEntityType != null) {
            holder.setLiquidCompositionEntity(sourceEntityType);
         } else {
            holder.setLiquidCompositionMixed();
         }
      } else if (holder.getLiquidComposition() == LiquidHolder.LiquidComposition.ENTITY) {
         Identifier existingType = holder.getLiquidEntityTypeId();
         if (sourceEntityType == null || !sourceEntityType.equals(existingType)) {
            holder.setLiquidCompositionMixed();
         }
      } else if (holder.getLiquidComposition() != LiquidHolder.LiquidComposition.MIXED) {
         holder.setLiquidCompositionMixed();
      }

      holder.setLiquidStored(stored + added);
      return added;
   }

   private static boolean changeMess(ServerPlayerEntity player, NonMessType type, int delta) {
      if (!NeedsOfNature.getConfig().isMessSystemEnabled()) {
         return false;
      }

      if (player != null && type != null && delta != 0 && player instanceof MessHolder holder) {
         int before = switch (type) {
            case V -> holder.getVMess();
            case A -> holder.getAMess();
            case M -> holder.getMMess();
         };
         int after = NonMessSystem.clampMess(before + delta);
         if (after == before) {
            return false;
         }

         switch (type) {
            case V:
               holder.setVMess(after);
               break;
            case A:
               holder.setAMess(after);
               break;
            case M:
               holder.setMMess(after);
         }

         NonMessSystem.broadcastMessState(player);
         return true;
      } else {
         return false;
      }
   }

   private static boolean changeDestroyedSkinDamage(ServerPlayerEntity player, int delta) {
      if (!NeedsOfNature.getConfig().isDestroyedSkinSystemEnabled()) {
         return false;
      }

      if (player != null && delta != 0 && player instanceof DestroyedSkinHolder holder) {
         int before = Math.max(0, Math.min(10, holder.getDestroyedSkinDamage()));
         int after = Math.max(0, Math.min(10, before + delta));
         if (after == before) {
            return false;
         }

         holder.setDestroyedSkinDamage(after);
         NonDestroyedSkinSystem.setDestroyedSkinStage(player, destroyedSkinStageForDamage(after));
         return true;
      } else {
         return false;
      }
   }

   private static int destroyedSkinStageForDamage(int damage) {
      if (damage >= 10) {
         return 4;
      } else if (damage >= 7) {
         return 3;
      } else if (damage >= 4) {
         return 2;
      } else {
         return damage >= 1 ? 1 : 0;
      }
   }

   private static NonLiquidComposition toApiLiquidComposition(LiquidHolder.LiquidComposition composition) {
      return switch (composition) {
         case ENTITY -> NonLiquidComposition.ENTITY;
         case MIXED -> NonLiquidComposition.MIXED;
         case EMPTY -> NonLiquidComposition.EMPTY;
      };
   }
}
