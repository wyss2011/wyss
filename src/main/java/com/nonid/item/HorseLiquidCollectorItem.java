package com.nonid.item;

import com.nonid.NeedsOfNature;
import com.nonid.client.HorseLiquidCollectorItemRenderer;
import com.nonid.entity.HorseLiquidCollectorEntity;
import java.util.function.Consumer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

public class HorseLiquidCollectorItem extends Item implements GeoItem {
   private AnimatableInstanceCache animatableCache;
   private boolean syncedAnimatableRegistered;

   public HorseLiquidCollectorItem(Settings settings) {
      super(settings);
   }

   public ActionResult use(World world, PlayerEntity user, Hand hand) {
      ItemStack stack = user.getStackInHand(hand);
      BlockHitResult hit = raycast(world, user, FluidHandling.ANY);
      if (hit.getType() != Type.BLOCK) {
         return ActionResult.PASS;
      }

      Vec3d hitPos = hit.getPos();
      HorseLiquidCollectorEntity collector = new HorseLiquidCollectorEntity(NeedsOfNature.HORSE_LIQUID_COLLECTOR_ENTITY_TYPE, world);
      collector.refreshPositionAndAngles(hitPos.x, hitPos.y + 0.01, hitPos.z, user.getYaw(), 0.0F);
      collector.setLockedOrientation(user.getYaw(), 0.0F);
      if (!world.isSpaceEmpty(collector, collector.getBoundingBox())) {
         return ActionResult.FAIL;
      }

      if (world.isClient()) {
         return ActionResult.SUCCESS;
      }

      world.spawnEntity(collector);
      world.playSound(null, collector.getX(), collector.getY(), collector.getZ(), SoundEvents.ENTITY_ARMOR_STAND_PLACE, SoundCategory.PLAYERS, 1.0F, 1.0F);
      world.emitGameEvent(user, GameEvent.ENTITY_PLACE, collector.getBlockPos());
      user.incrementStat(Stats.USED.getOrCreateStat(this));
      if (!user.isInCreativeMode()) {
         stack.decrement(1);
      }

      return ActionResult.SUCCESS;
   }

   public void registerControllers(@NotNull ControllerRegistrar controllers) {
   }

   @NotNull
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      if (!this.syncedAnimatableRegistered) {
         GeoItem.registerSyncedAnimatable(this);
         this.syncedAnimatableRegistered = true;
      }

      if (this.animatableCache == null) {
         this.animatableCache = GeckoLibUtil.createInstanceCache(this);
      }

      return this.animatableCache;
   }

   public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
      consumer.accept(new GeoRenderProvider() {
         private GeoItemRenderer<@NotNull HorseLiquidCollectorItem> renderer;

         public GeoItemRenderer<?> getGeoItemRenderer() {
            if (this.renderer == null) {
               this.renderer = new HorseLiquidCollectorItemRenderer();
            }

            return this.renderer;
         }
      });
   }
}
