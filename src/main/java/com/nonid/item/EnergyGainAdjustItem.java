package com.nonid.item;

import com.nonid.EnergyHolder;
import com.nonid.particle.NonParticles;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class EnergyGainAdjustItem extends Item {
   private final float delta;

   public EnergyGainAdjustItem(Settings settings, float delta) {
      super(settings);
      this.delta = delta;
   }

   public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
      if (!(entity instanceof MobEntity mob && mob instanceof EnergyHolder holder)) {
         return ActionResult.PASS;
      } else {
         if (user.getEntityWorld().isClient()) {
            return ActionResult.SUCCESS;
         }

         boolean removedStabilization = false;
         if (this.delta > 0.0F && mob.getCommandTags().contains("non.energy_stabilized")) {
            mob.removeCommandTag("non.energy_stabilized");
            removedStabilization = true;
         }

         float current = holder.getEnergyGainMultiplier();
         float target = clamp(current + this.delta, 0.4F, 6.0F);
         if (Math.abs(target - current) < 1.0E-6F && !removedStabilization) {
            return ActionResult.FAIL;
         }

         if (Math.abs(target - current) >= 1.0E-6F) {
            holder.setEnergyGainMultiplier(target);
         }

         if (user.getEntityWorld() instanceof ServerWorld serverWorld) {
            this.playFeedback(serverWorld, mob, removedStabilization, target);
         }

         if (!user.isInCreativeMode()) {
            stack.decrement(1);
         }

         return ActionResult.SUCCESS;
      }
   }

   private void playFeedback(ServerWorld world, MobEntity mob, boolean removedStabilization, float appliedMultiplier) {
      if (removedStabilization) {
         world.spawnParticles(ParticleTypes.WITCH, mob.getX(), mob.getBodyY(0.6), mob.getZ(), 12, 0.35, 0.35, 0.35, 0.01);
         world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.NEUTRAL, 0.9F, 0.95F);
      } else {
         boolean augmenter = this.delta > 0.0F;
         ParticleEffect particle = augmenter ? NonParticles.SMALLHEART : ParticleTypes.SMOKE;
         SoundEvent sound = augmenter ? SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP : SoundEvents.BLOCK_BREWING_STAND_BREW;
         float pitch = pitchForMultiplier(appliedMultiplier);
         world.spawnParticles(particle, mob.getX(), mob.getBodyY(0.6), mob.getZ(), 10, 0.35, 0.35, 0.35, 0.01);
         world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundCategory.NEUTRAL, 0.9F, pitch);
      }
   }

   private static float pitchForMultiplier(float multiplier) {
      float clamped = clamp(multiplier, 0.4F, 6.0F);
      float span = 5.6F;
      float t = (clamped - 0.4F) / span;
      return 0.65F + 0.9F * t;
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }
}
