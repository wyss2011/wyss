package com.nonid.item;

import com.nonid.EnergyHolder;
import com.nonid.NeedsOfNature;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class EnergyStabilizerItem extends Item {
   private static final String STABILIZER_ENTITY_DATA_KEY = "stabilizer_entity";

   public EnergyStabilizerItem(Settings settings) {
      super(settings);
   }

   public Text getName(ItemStack stack) {
      Identifier target = getTargetEntityTypeId(stack);
      if (target == null) {
         return super.getName(stack);
      }

      Text entityName = Registries.ENTITY_TYPE.getOptionalValue(target).<Text>map(EntityType::getName).orElseGet(() -> Text.literal(target.toString()));
      return Text.translatable("item.needsofnature.entity_energy_stabilizer", new Object[]{entityName});
   }

   public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
      if (entity instanceof MobEntity mob && mob instanceof EnergyHolder) {
         Identifier target = getTargetEntityTypeId(stack);
         if (target == null) {
            return ActionResult.FAIL;
         }

         Identifier mobType = Registries.ENTITY_TYPE.getId(mob.getType());
         if (!target.equals(mobType)) {
            return ActionResult.FAIL;
         }

         if (mob.getCommandTags().contains("non.energy_stabilized")) {
            return ActionResult.FAIL;
         }

         if (user.getEntityWorld().isClient()) {
            return ActionResult.SUCCESS;
         }

         EnergyHolder holder = (EnergyHolder)mob;
         mob.addCommandTag("non.energy_stabilized");
         holder.setEnergy(Math.min(holder.getEnergy(), 199));
         mob.setTarget(null);
         if (mob instanceof Angerable angerable) {
            angerable.stopAnger();
         }

         if (user.getEntityWorld() instanceof ServerWorld serverWorld) {
            playFeedback(serverWorld, mob);
         }

         if (!user.isInCreativeMode()) {
            stack.decrement(1);
         }

         return ActionResult.SUCCESS;
      } else {
         return ActionResult.PASS;
      }
   }

   public static ItemStack createStackForEntity(@Nullable Identifier entityTypeId) {
      ItemStack stack = new ItemStack(NeedsOfNature.ENERGY_STABILIZER);
      if (entityTypeId != null) {
         setTargetEntityTypeId(stack, entityTypeId);
      }

      return stack;
   }

   public static void setTargetEntityTypeId(ItemStack stack, @Nullable Identifier entityTypeId) {
      if (stack != null && !stack.isEmpty()) {
         NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
            String value = entityTypeId == null ? "" : entityTypeId.toString();
            nbt.putString("stabilizer_entity", value);
         });
      }
   }

   @Nullable
   public static Identifier getTargetEntityTypeId(ItemStack stack) {
      if (stack != null && !stack.isEmpty() && stack.isOf(NeedsOfNature.ENERGY_STABILIZER)) {
         NbtComponent custom = (NbtComponent)stack.get(DataComponentTypes.CUSTOM_DATA);
         if (custom != null && !custom.isEmpty()) {
            NbtCompound nbt = custom.copyNbt();
            if (!nbt.contains("stabilizer_entity")) {
               return null;
            }

            String raw = nbt.getString("stabilizer_entity", "");
            return raw != null && !raw.isBlank() ? Identifier.tryParse(raw) : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static void playFeedback(ServerWorld world, MobEntity mob) {
      world.spawnParticles(ParticleTypes.END_ROD, mob.getX(), mob.getBodyY(0.65), mob.getZ(), 12, 0.4, 0.4, 0.4, 0.02);
      world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.NEUTRAL, 0.9F, 1.0F);
   }
}
