package com.nonid;

import com.nonid.data.NonAccessoryBehavior;
import com.nonid.data.NonAccessoryItemRegistry;
import com.nonid.data.NonAccessoryStackData;
import com.nonid.data.NonLiquidRoles;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonTrinketsIntegration {
   private static final String TRINKETS_MOD_ID = "trinkets";
   private static final boolean TRINKETS_LOADED = FabricLoader.getInstance().isModLoaded("trinkets");

   private NonTrinketsIntegration() {
   }

   public static boolean isTrinketsLoaded() {
      return TRINKETS_LOADED;
   }

   public static void registerPredicates() {
      if (TRINKETS_LOADED) {
         NonTrinketsCompat.registerPredicates();
      }
   }

   public static void registerTrinketItem(Item item) {
      if (TRINKETS_LOADED) {
         NonTrinketsCompat.registerTrinketItem(item);
      }
   }

   public static boolean isSlotAvailableFor(LivingEntity entity, String slotId) {
      if (entity == null || slotId == null) {
         return false;
      } else if (NonAccessoryBehavior.isSlot(slotId, "legs/v")) {
         return NonGenderSystem.hasFemaleGender(entity);
      } else {
         return NonAccessoryBehavior.isSlot(slotId, "legs/d") ? NonGenderSystem.hasMaleGender(entity) : true;
      }
   }

   public static NonAccessoryEffects getAccessoryEffects(LivingEntity entity) {
      return getGlobalAccessoryEffects(entity);
   }

   public static NonAccessoryEffects getGlobalAccessoryEffects(LivingEntity entity) {
      return TRINKETS_LOADED && entity != null ? NonTrinketsCompat.getGlobalAccessoryEffects(entity) : NonAccessoryEffects.NEUTRAL;
   }

   public static NonAccessoryEffects getActiveTankAccessoryEffects(LivingEntity entity) {
      return TRINKETS_LOADED && entity != null ? NonTrinketsCompat.getActiveTankAccessoryEffects(entity) : NonAccessoryEffects.NEUTRAL;
   }

   public static String getActiveTankSlotId(LivingEntity entity) {
      return NonGenderSystem.getLiquidTankType(entity).trinketsSlotId();
   }

   public static boolean isActiveTankSlot(LivingEntity entity, String slotId) {
      return entity != null && slotId != null ? NonAccessoryBehavior.isSlot(slotId, getActiveTankSlotId(entity)) : false;
   }

   public static boolean blocksPregnancy(ServerPlayerEntity player) {
      return TRINKETS_LOADED && player != null ? NonTrinketsCompat.blocksPregnancy(player) : false;
   }

   public static boolean hasProtectorFor(ServerPlayerEntity player, NonLiquidRoles.InjectorRole role) {
      return TRINKETS_LOADED && player != null && role != null && role != NonLiquidRoles.InjectorRole.NONE
         ? NonTrinketsCompat.hasProtectorFor(player, role)
         : false;
   }

   public static boolean consumeProtectorFor(ServerWorld world, ServerPlayerEntity player, NonLiquidRoles.InjectorRole role) {
      return TRINKETS_LOADED && world != null && player != null && role != null && role != NonLiquidRoles.InjectorRole.NONE
         ? NonTrinketsCompat.consumeProtectorFor(world, player, role)
         : false;
   }

   public static void applyInjectionDurabilityCost(ServerWorld world, ServerPlayerEntity player, NonLiquidRoles.InjectorRole role) {
      if (TRINKETS_LOADED && world != null && player != null && role != null) {
         NonTrinketsCompat.applyInjectionDurabilityCost(world, player, role);
      }
   }

   public static void syncAccessoryStateIfChanged(ServerPlayerEntity player) {
      if (TRINKETS_LOADED && player != null) {
         NonTrinketsCompat.syncAccessoryStateIfChanged(player);
      }
   }

   public static void forceAccessorySync(ServerPlayerEntity player) {
      if (TRINKETS_LOADED && player != null) {
         NonTrinketsCompat.forceAccessorySync(player);
      }
   }

   public static void clearAccessoryState(UUID playerUuid) {
      if (TRINKETS_LOADED && playerUuid != null) {
         NonTrinketsCompat.clearAccessoryState(playerUuid);
      }
   }

   static void clearRuntimeState() {
      if (TRINKETS_LOADED) {
         NonTrinketsCompat.clearRuntimeState();
      }
   }

   public static List<NonTrinketsIntegration.ResolvedVisualLayer> resolveEquippedSkinOverlays(LivingEntity entity) {
      return TRINKETS_LOADED && entity != null ? NonTrinketsCompat.resolveEquippedSkinOverlays(entity) : List.of();
   }

   public static Set<String> resolveVirtualActorTags(LivingEntity entity) {
      return TRINKETS_LOADED && entity != null ? NonTrinketsCompat.resolveVirtualActorTags(entity) : Set.of();
   }

   public static ItemStack getMaleRoleAccessoryStack(LivingEntity entity) {
      return TRINKETS_LOADED && entity != null ? NonTrinketsCompat.getMaleRoleAccessoryStack(entity) : ItemStack.EMPTY;
   }

   public static boolean hasMaleRoleAccessory(LivingEntity entity) {
      return !getMaleRoleAccessoryStack(entity).isEmpty();
   }

   @Nullable
   public static NonTrinketsIntegration.StoredInjectorLiquid peekStoredInjectorLiquid(ServerPlayerEntity player) {
      return TRINKETS_LOADED && player != null ? NonTrinketsCompat.peekStoredInjectorLiquid(player) : null;
   }

   @Nullable
   public static NonTrinketsIntegration.StoredInjectorLiquid consumeStoredInjectorLiquid(ServerPlayerEntity player) {
      return TRINKETS_LOADED && player != null ? NonTrinketsCompat.consumeStoredInjectorLiquid(player) : null;
   }

   @Nullable
   public static NonTrinketsIntegration.ResolvedVisualLayer resolveVisualLayer(ItemStack stack, NonAccessoryItemRegistry.VisualLayer layer) {
      if (stack != null && !stack.isEmpty() && layer != null && layer.texture() != null) {
         Identifier itemId = Registries.ITEM.getId(stack.getItem());
         NonAccessoryItemRegistry.AccessoryItemDefinition definition = NonAccessoryItemRegistry.getDefinition(itemId);
         int fallbackDye = definition == null ? 16777215 : definition.defaultDyeColor();

         int tintRgb = switch (layer.tintMode()) {
            case NONE -> 16777215;
            case DYE -> NonAccessoryStackData.resolveDyeRgb(stack, fallbackDye);
            case STORED_LIQUID -> {
               NonAccessoryStackData.StoredLiquid liquid = NonAccessoryStackData.getStoredLiquid(stack);
               yield liquid == null ? -1 : NeedsOfNature.resolveBottleLiquidTintRgb(liquid.entityTypeId());
            }
         };
         return tintRgb < 0 ? null : new NonTrinketsIntegration.ResolvedVisualLayer(layer.texture(), tintRgb & 16777215);
      } else {
         return null;
      }
   }

   public static List<ItemStack> getVisuallyShedAccessoryStacks(LivingEntity entity) {
      return TRINKETS_LOADED && entity != null ? NonTrinketsCompat.getVisuallyShedAccessoryStacks(entity) : List.of();
   }

   public static NonTrinketsIntegration.TankScopeStatus resolveTankScopeStatus(LivingEntity entity, ItemStack stack) {
      return TRINKETS_LOADED && entity != null && stack != null && !stack.isEmpty()
         ? NonTrinketsCompat.resolveTankScopeStatus(entity, stack)
         : NonTrinketsIntegration.TankScopeStatus.NOT_EQUIPPED;
   }

   public record ResolvedVisualLayer(Identifier texture, int tintRgb) {
   }

   public record StoredInjectorLiquid(Identifier entityTypeId, int amountMl) {
   }

   public enum TankScopeStatus {
      NOT_EQUIPPED,
      ACTIVE_EQUIPPED,
      INACTIVE_EQUIPPED;
   }
}
