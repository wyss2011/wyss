package com.nonid;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class NonSkinRepairBlocks {
   private NonSkinRepairBlocks() {
   }

   public static boolean isNearRepairBlock(World world, BlockPos center, NonConfig config) {
      if (world != null && center != null) {
         for (BlockPos pos : BlockPos.iterateOutwards(center, 1, 1, 1)) {
            if (isRepairBlock(world.getBlockState(pos), config)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean isRepairBlock(BlockState state, NonConfig config) {
      if (state == null) {
         return false;
      } else {
         return state.isOf(Blocks.LOOM) ? true : config != null && config.isCraftingTableSkinRepairAllowed() && state.isOf(Blocks.CRAFTING_TABLE);
      }
   }
}
