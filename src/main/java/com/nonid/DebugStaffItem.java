package com.nonid;

import com.nonid.data.NonEntityProfiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public class DebugStaffItem extends Item {
   public DebugStaffItem(Settings settings) {
      super(settings);
   }

   public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
      if (entity instanceof EnergyHolder holder) {
         if (user.getEntityWorld().isClient()) {
            return ActionResult.SUCCESS;
         } else {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity)user;
            if (user.isSneaking()) {
               int current = holder.getEnergy();
               int max = holder.getMaxEnergy();
               int next = Math.min(max, current + 25);
               holder.setEnergy(next);
               return ActionResult.SUCCESS;
            } else {
               this.sendDebugReport(serverPlayer, entity, holder);
               return ActionResult.SUCCESS;
            }
         }
      } else {
         return ActionResult.PASS;
      }
   }

   private void sendDebugReport(ServerPlayerEntity player, LivingEntity entity, EnergyHolder holder) {
      Identifier entityId = Registries.ENTITY_TYPE.getId(entity.getType());
      player.sendMessage(Text.literal("[NoN 调试] " + entity.getDisplayName().getString() + " (" + entityId + ")").formatted(Formatting.GREEN), false);
      player.sendMessage(Text.literal("UUID: " + entity.getUuid()).formatted(Formatting.GRAY), false);
      player.sendMessage(Text.literal("性别: " + this.resolveGender(entity)).formatted(Formatting.GRAY), false);
      player.sendMessage(
         Text.literal("能量: " + holder.getEnergy() + "/" + holder.getMaxEnergy() + " | 已初始化: " + holder.isEnergyInitialized())
            .formatted(Formatting.GRAY),
         false
      );
      player.sendMessage(
         Text.literal(
               "能量增益: 倍率 "
                  + this.format(holder.getEnergyGainMultiplier())
                  + " | 漂移 "
                  + this.format(holder.getEnergyGainDrift())
                  + " | 实体 "
                  + this.format(NonEntityProfiles.resolveEnergyGainMultiplier(entity))
            )
            .formatted(Formatting.GRAY),
         false
      );
      if (entity.getEntityWorld() instanceof ServerWorld world) {
         boolean activeOrPending = NeedsOfNature.isActorPendingOrActive(world, entity.getUuid());
         player.sendMessage(Text.literal("动画: 活跃/待定 " + activeOrPending).formatted(Formatting.GRAY), false);
         this.sendAuraReport(player, world, entity, holder);
         this.sendCooldownReport(player, world, entity, holder);
      }

      if (entity instanceof LiquidHolder liquidHolder) {
         Identifier liquidType = liquidHolder.getLiquidEntityTypeId();
         String liquidInfo = liquidHolder.getLiquidStored()
            + "/"
            + liquidHolder.getLiquidCapacity()
            + "ml | composition "
            + liquidHolder.getLiquidComposition()
            + (liquidType == null ? "" : " | 实体 " + liquidType);
         if (entity instanceof ServerPlayerEntity) {
            liquidInfo = liquidInfo + " | 槽类型 " + NonGenderSystem.getLiquidTankType(entity);
         }

         player.sendMessage(Text.literal("液体: " + liquidInfo).formatted(Formatting.GRAY), false);
      }

      if (entity instanceof PregnancyHolder pregnancyHolder) {
         Identifier pregnancyType = pregnancyHolder.getPregnantEntityTypeId();
         String pregnancyInfo = pregnancyType == null
            ? "none"
            : pregnancyType + " | 结束tick " + pregnancyHolder.getPregnancyEndTick() + " | 待孵化 " + pregnancyHolder.isPregnancyPendingHatch();
         player.sendMessage(Text.literal("孕期: " + pregnancyInfo).formatted(Formatting.GRAY), false);
      }
   }

   private void sendAuraReport(ServerPlayerEntity player, ServerWorld world, LivingEntity entity, EnergyHolder holder) {
      if (entity instanceof MobEntity mob) {
         NeedsOfNature.PlayerEnergyAuraDebug aura = NeedsOfNature.getPlayerEnergyAuraDebug(world, mob, holder);
         if (aura == null) {
            player.sendMessage(Text.literal("光环: 无 | 携带 " + this.format(holder.getEnergyAuraCarry())).formatted(Formatting.GRAY), false);
         } else {
            String sourceName = aura.player().getName().getString();
            player.sendMessage(
               Text.literal(
                     "光环: "
                        + this.format(aura.totalMultiplier())
                        + "x 来自 "
                        + sourceName
                        + " | 能量 "
                        + aura.playerEnergy()
                        + " | 破坏 "
                        + aura.destroyedSkinStage()
                        + " | 污渍 "
                        + aura.messTotal()
                        + " | 半径 "
                        + this.format(aura.radius())
                        + " | 携带 "
                        + this.format(holder.getEnergyAuraCarry())
                  )
                  .formatted(Formatting.GRAY),
               false
            );
         }
      } else {
         player.sendMessage(Text.literal("光环: 无").formatted(Formatting.GRAY), false);
      }
   }

   private void sendCooldownReport(ServerPlayerEntity player, ServerWorld world, LivingEntity entity, EnergyHolder holder) {
      List<String> cooldowns = new ArrayList<>();
      String attackFailsafe = NeedsOfNature.getMobAttackFailsafeDebug(world, entity.getUuid());
      if (attackFailsafe != null && !attackFailsafe.isBlank()) {
         cooldowns.add(attackFailsafe);
      }

      cooldowns.addAll(holder.getActiveCooldownDebugLines(world));
      if (cooldowns.isEmpty()) {
         player.sendMessage(Text.literal("冷却: 无").formatted(Formatting.GRAY), false);
      } else {
         player.sendMessage(Text.literal("冷却:").formatted(Formatting.GRAY), false);

         for (String line : cooldowns) {
            player.sendMessage(Text.literal("- " + line).formatted(Formatting.DARK_GRAY), false);
         }
      }
   }

   private String resolveGender(LivingEntity entity) {
      if (entity instanceof GenderHolder genderHolder) {
         return this.formatGenderMask(genderHolder.getGenderMask());
      } else {
         boolean hasMale = entity.getCommandTags().contains("gender.male");
         boolean hasFemale = entity.getCommandTags().contains("gender.female");
         int mask = 0;
         if (hasMale) {
            mask |= 1;
         }

         if (hasFemale) {
            mask |= 2;
         }

         return this.formatGenderMask(mask);
      }
   }

   private String formatGenderMask(int mask) {
      boolean male = (mask & 1) != 0;
      boolean female = (mask & 2) != 0;
      if (male && female) {
         return "male & female";
      } else if (male) {
         return "male";
      } else {
         return female ? "female" : "none";
      }
   }

   private String format(float value) {
      return String.format(Locale.ROOT, "%.2f", value);
   }

   private String format(double value) {
      return String.format(Locale.ROOT, "%.2f", value);
   }
}
