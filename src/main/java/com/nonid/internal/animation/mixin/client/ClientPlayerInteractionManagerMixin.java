package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
   @Unique
   private static boolean shouldBlock() {
      MinecraftClient client = MinecraftClient.getInstance();
      ClientPlayerEntity player = client == null ? null : client.player;
      return player == null ? false : ClientAnimationRuntime.isActorActive(player.getUuid());
   }

   @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true)
   private void afw$blockBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (shouldBlock()) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
   private void afw$blockAttack(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      if (shouldBlock()) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
   private void afw$blockBreakProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      if (shouldBlock()) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
   private void afw$interactBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
      if (shouldBlock()) {
         cir.setReturnValue(ActionResult.FAIL);
      }
   }

   @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
   private void afw$interactItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      if (shouldBlock()) {
         cir.setReturnValue(ActionResult.FAIL);
      }
   }

   @Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
   private void afw$interactEntity(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      if (shouldBlock()) {
         cir.setReturnValue(ActionResult.FAIL);
      }
   }

   @Inject(method = "interactEntityAtLocation", at = @At("HEAD"), cancellable = true)
   private void afw$interactEntityAt(PlayerEntity player, Entity entity, EntityHitResult hit, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      if (shouldBlock()) {
         cir.setReturnValue(ActionResult.FAIL);
      }
   }

   @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
   private void afw$attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
      if (shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
   private void afw$clickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
      if (shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "clickRecipe", at = @At("HEAD"), cancellable = true)
   private void afw$clickRecipe(int syncId, NetworkRecipeId recipeId, boolean craftAll, CallbackInfo ci) {
      if (shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "clickButton", at = @At("HEAD"), cancellable = true)
   private void afw$clickButton(int syncId, int buttonId, CallbackInfo ci) {
      if (shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "clickCreativeStack", at = @At("HEAD"), cancellable = true)
   private void afw$clickCreative(ItemStack stack, int slotId, CallbackInfo ci) {
      if (shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "dropCreativeStack", at = @At("HEAD"), cancellable = true)
   private void afw$dropCreative(ItemStack stack, CallbackInfo ci) {
      if (shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "stopUsingItem", at = @At("HEAD"), cancellable = true)
   private void afw$stopUsingItem(PlayerEntity player, CallbackInfo ci) {
      if (shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "pickItemFromBlock", at = @At("HEAD"), cancellable = true)
   private void afw$pickItemFromBlock(BlockPos pos, boolean includeData, CallbackInfo ci) {
      if (shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "pickItemFromEntity", at = @At("HEAD"), cancellable = true)
   private void afw$pickItemFromEntity(Entity entity, boolean includeData, CallbackInfo ci) {
      if (shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "slotChangedState", at = @At("HEAD"), cancellable = true)
   private void afw$slotChangedState(int syncId, int revision, boolean newState, CallbackInfo ci) {
      if (shouldBlock()) {
         ci.cancel();
      }
   }
}
