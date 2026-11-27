package com.yppowers.item;

import com.yppowers.power.PowerManager;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class GrimoireOfLightManager {
   private GrimoireOfLightManager() {
   }

   public static boolean isGrimoire(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.isOf(YPItems.GRIMOIRE_OF_LIGHT);
   }

   public static boolean hasGrimoire(ServerPlayerEntity player) {
      for (int slot = 0; slot < player.getInventory().size(); slot++) {
         if (isGrimoire(player.getInventory().getStack(slot))) {
            return true;
         }
      }

      return false;
   }

   public static boolean placeAt(ServerWorld world, int x, int y, int z, int generation) {
      BlockPos chestPos = new BlockPos(x, y, z);
      if (y > world.getBottomY()
         && y < world.getTopYInclusive()
         && world.getBlockState(chestPos).isAir()
         && world.getBlockState(chestPos.down()).isSolidBlock(world, chestPos.down())) {
         world.setBlockState(chestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH), 3);
         if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setStack(13, createGrimoire(generation));
            chest.markDirty();
            PowerManager.get(world.getServer()).markGrimoireSiteGenerated(y);
            emitLightParticles(world, x + 0.5, y + 0.6, z + 0.5, 10);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static ItemStack createGrimoire(int generation) {
      ItemStack stack = new ItemStack(YPItems.GRIMOIRE_OF_LIGHT);
      stack.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("item.yppowers.grimoire_of_light").formatted(Formatting.GOLD, Formatting.BOLD));
      stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
      YPItemData.setInt(stack, "YPArtifactGeneration", generation);
      return stack;
   }

   public static ItemStack createGrimoire() {
      return createGrimoire(1);
   }

   public static void serverTick(MinecraftServer server) {
      if (server.getTicks() % 10 == 0) {
         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (hasGrimoire(player)) {
               ServerWorld var4 = player.getEntityWorld();
               if (var4 instanceof ServerWorld) {
                  emitLightParticles(var4, player.getX(), player.getY() + 1.0, player.getZ(), 3);
               }
            }
         }
      }
   }

   public static void emitLightParticles(ServerWorld world, double x, double y, double z, int count) {
      world.spawnParticles(ParticleTypes.END_ROD, true, true, x, y, z, count, 0.38, 0.65, 0.38, 0.006);
      world.spawnParticles(ParticleTypes.WAX_ON, true, true, x, y, z, Math.max(1, count / 2), 0.32, 0.55, 0.32, 0.0);
   }
}
