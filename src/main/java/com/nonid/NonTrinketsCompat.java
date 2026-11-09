package com.nonid;

import com.nonid.data.NonLiquidRoles;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Trinkets 集成层的空实现。
 *
 * 原始实现依赖 Trinkets API（dev.emi.trinkets.*）。Trinkets 没有公开的
 * 1.21.11 构建，编译期拿不到依赖，只能把这一层换成空实现。
 *
 * NoN 本身一直把 Trinkets 当软依赖（NonTrinketsIntegration 到处判断
 * TRINKETS_LOADED），所以这里保留相同的方法签名，全部返回"没装 Trinkets"
 * 时的默认值。饰品功能不可用，其余玩法不受影响。
 */
final class NonTrinketsCompat {
   private NonTrinketsCompat() {
   }

   static void registerPredicates() {
   }

   static void registerTrinketItem(Item item) {
   }

   static NonAccessoryEffects getGlobalAccessoryEffects(LivingEntity entity) {
      return NonAccessoryEffects.NEUTRAL;
   }

   static NonAccessoryEffects getActiveTankAccessoryEffects(LivingEntity entity) {
      return NonAccessoryEffects.NEUTRAL;
   }

   static boolean blocksPregnancy(ServerPlayerEntity player) {
      return false;
   }

   static boolean hasProtectorFor(ServerPlayerEntity player, NonLiquidRoles.InjectorRole role) {
      return false;
   }

   static boolean consumeProtectorFor(ServerWorld world, ServerPlayerEntity player, NonLiquidRoles.InjectorRole role) {
      return false;
   }

   static void applyInjectionDurabilityCost(ServerWorld world, ServerPlayerEntity player, NonLiquidRoles.InjectorRole role) {
   }

   static void syncAccessoryStateIfChanged(ServerPlayerEntity player) {
   }

   static void forceAccessorySync(ServerPlayerEntity player) {
   }

   static void clearAccessoryState(UUID playerUuid) {
   }

   static void clearRuntimeState() {
   }

   static List<NonTrinketsIntegration.ResolvedVisualLayer> resolveEquippedSkinOverlays(LivingEntity entity) {
      return List.of();
   }

   static Set<String> resolveVirtualActorTags(LivingEntity entity) {
      return Set.of();
   }

   static ItemStack getMaleRoleAccessoryStack(LivingEntity entity) {
      return ItemStack.EMPTY;
   }

   static NonTrinketsIntegration.StoredInjectorLiquid peekStoredInjectorLiquid(ServerPlayerEntity player) {
      return null;
   }

   static NonTrinketsIntegration.StoredInjectorLiquid consumeStoredInjectorLiquid(ServerPlayerEntity player) {
      return null;
   }

   static List<ItemStack> getVisuallyShedAccessoryStacks(LivingEntity entity) {
      return List.of();
   }

   static NonTrinketsIntegration.TankScopeStatus resolveTankScopeStatus(LivingEntity entity, ItemStack stack) {
      return NonTrinketsIntegration.TankScopeStatus.NOT_EQUIPPED;
   }
}
