package com.nonid.internal.animation.mixin;

import com.nonid.internal.animation.server.ServerAnimationController;
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PickItemFromBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PickItemFromEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.SlotChangedStateC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
   @Shadow
   public ServerPlayerEntity player;

   @Unique
   private boolean afw$shouldBlock() {
      if (this.player == null) {
         return false;
      } else {
         return this.player.getEntityWorld() instanceof ServerWorld world ? ServerAnimationController.isActorActive(world, this.player.getUuid()) : false;
      }
   }

   @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
   private void afw$blockPlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
      if (this.afw$shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "onPlayerInteractBlock", at = @At("HEAD"), cancellable = true)
   private void afw$blockInteractBlock(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
      if (this.afw$shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "onPlayerInteractItem", at = @At("HEAD"), cancellable = true)
   private void afw$blockInteractItem(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
      if (this.afw$shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "onPlayerInteractEntity", at = @At("HEAD"), cancellable = true)
   private void afw$blockInteractEntity(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
      if (this.afw$shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "onClickSlot", at = @At("HEAD"), cancellable = true)
   private void afw$blockClickSlot(ClickSlotC2SPacket packet, CallbackInfo ci) {
      if (this.afw$shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "onButtonClick", at = @At("HEAD"), cancellable = true)
   private void afw$blockButtonClick(ButtonClickC2SPacket packet, CallbackInfo ci) {
      if (this.afw$shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "onCreativeInventoryAction", at = @At("HEAD"), cancellable = true)
   private void afw$blockCreative(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
      if (this.afw$shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "onSlotChangedState", at = @At("HEAD"), cancellable = true)
   private void afw$blockSlotState(SlotChangedStateC2SPacket packet, CallbackInfo ci) {
      if (this.afw$shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "onUpdateSelectedSlot", at = @At("HEAD"), cancellable = true)
   private void afw$blockSelectedSlot(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
      if (this.afw$shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "onPickItemFromBlock", at = @At("HEAD"), cancellable = true)
   private void afw$blockPickFromBlock(PickItemFromBlockC2SPacket packet, CallbackInfo ci) {
      if (this.afw$shouldBlock()) {
         ci.cancel();
      }
   }

   @Inject(method = "onPickItemFromEntity", at = @At("HEAD"), cancellable = true)
   private void afw$blockPickFromEntity(PickItemFromEntityC2SPacket packet, CallbackInfo ci) {
      if (this.afw$shouldBlock()) {
         ci.cancel();
      }
   }
}
