package com.yppowers.item;

import com.yppowers.config.YPConfig;
import com.yppowers.mixin.DoubleInventoryAccessor;
import com.yppowers.power.PowerManager;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class GauntletOfDarknessManager {
   private static final int BROADCAST_INTERVAL_TICKS = 1200;
   public static final String BLACKENED_MOB_TAG = "yppowers_gauntlet_darkness_blackened";
   private static UUID lastAnnouncedHolder;
   private static long lastBroadcastTick = Long.MIN_VALUE;

   private GauntletOfDarknessManager() {
   }

   public static boolean isGauntlet(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.isOf(YPItems.GAUNTLET_OF_DARKNESS);
   }

   public static boolean isAllowedStorageInventory(Inventory inventory) {
      if (inventory instanceof PlayerInventory) {
         return true;
      } else if (inventory instanceof TrappedChestBlockEntity) {
         return false;
      } else if (inventory instanceof ChestBlockEntity) {
         return true;
      } else if (!(inventory instanceof DoubleInventory doubleInventory)) {
         return false;
      } else {
         DoubleInventoryAccessor accessor = (DoubleInventoryAccessor)doubleInventory;
         return isAllowedStorageInventory(accessor.yppowers$getFirst()) && isAllowedStorageInventory(accessor.yppowers$getSecond());
      }
   }

   public static void serverStarted(MinecraftServer server) {
      lastAnnouncedHolder = null;
      lastBroadcastTick = Long.MIN_VALUE;
      ArtifactSpawnManager.initialize(server);
   }

   public static boolean placeVaultAt(ServerWorld world, int centerX, int floorY, int centerZ, int generation) {
      if (floorY < world.getBottomY() + 2 || floorY + 6 > world.getTopYInclusive()) {
         return false;
      } else if (!isSafeVaultFootprint(world, centerX, floorY, centerZ)) {
         return false;
      } else {
         buildVault(world, centerX, floorY, centerZ);
         BlockPos chestPos = new BlockPos(centerX, floorY + 1, centerZ);
         if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest && !(chest instanceof TrappedChestBlockEntity)) {
            chest.setStack(13, createGauntlet(generation));
            chest.markDirty();
            PowerManager.get(world.getServer()).markGauntletSiteGenerated(floorY);
            return true;
         } else {
            return false;
         }
      }
   }

   private static boolean isSafeVaultFootprint(ServerWorld world, int centerX, int floorY, int centerZ) {
      for (int dx = -4; dx <= 4; dx++) {
         for (int dz = -4; dz <= 4; dz++) {
            BlockPos floor = new BlockPos(centerX + dx, floorY, centerZ + dz);
            BlockState floorState = world.getBlockState(floor);
            if (world.getBlockEntity(floor) != null || !world.getFluidState(floor).isEmpty() || !isNaturalSolid(floorState)) {
               return false;
            }

            for (int dy = 1; dy <= 6; dy++) {
               BlockPos pos = floor.up(dy);
               BlockState state = world.getBlockState(pos);
               if (world.getBlockEntity(pos) != null || !world.getFluidState(pos).isEmpty()) {
                  return false;
               }

               if (!state.isAir() && !state.isReplaceable() && !isNaturalSolid(state)) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private static boolean isNaturalSolid(BlockState state) {
      return state.isIn(BlockTags.BASE_STONE_OVERWORLD)
         || state.isIn(BlockTags.DIRT)
         || state.isIn(BlockTags.SAND)
         || state.isOf(Blocks.GRAVEL)
         || state.isOf(Blocks.CLAY)
         || state.isOf(Blocks.SNOW_BLOCK);
   }

   public static ItemStack createGauntlet(int generation) {
      ItemStack stack = new ItemStack(YPItems.GAUNTLET_OF_DARKNESS);
      stack.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("item.yppowers.gauntlet_of_darkness").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
      stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
      YPItemData.setInt(stack, "YPArtifactGeneration", generation);
      return stack;
   }

   private static void buildVault(ServerWorld world, int centerX, int floorY, int centerZ) {
      BlockState brick = Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
      BlockState accent = Blocks.CRYING_OBSIDIAN.getDefaultState();

      for (int dx = -4; dx <= 4; dx++) {
         for (int dz = -4; dz <= 4; dz++) {
            BlockPos floor = new BlockPos(centerX + dx, floorY, centerZ + dz);
            world.setBlockState(floor, brick, 3);

            for (int dy = 1; dy <= 5; dy++) {
               boolean wall = Math.abs(dx) == 4 || Math.abs(dz) == 4;
               BlockPos pos = new BlockPos(centerX + dx, floorY + dy, centerZ + dz);
               if (!wall) {
                  world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
               } else {
                  boolean entrance = dz == -4 && dx == 0 && dy <= 2;
                  if (entrance) {
                     world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                  } else {
                     boolean corner = Math.abs(dx) == 4 && Math.abs(dz) == 4 && (dy == 1 || dy == 3 || dy == 5);
                     world.setBlockState(pos, corner ? accent : brick, 3);
                  }
               }
            }

            world.setBlockState(new BlockPos(centerX + dx, floorY + 6, centerZ + dz), brick, 3);
         }
      }

      BlockPos chestPos = new BlockPos(centerX, floorY + 1, centerZ);
      world.setBlockState(chestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH), 3);
      world.setBlockState(new BlockPos(centerX, floorY + 6, centerZ), accent, 3);
   }

   public static boolean isInsideDarknessInfluence(MobEntity mob) {
      if (mob != null && mob.getEntityWorld() instanceof ServerWorld world) {
         MinecraftServer server = world.getServer();
         if (server != null && world == server.getOverworld()) {
            PowerManager data = PowerManager.get(server);
            if (!data.isGauntletSiteGenerated()) {
               return false;
            } else {
               YPConfig config = YPConfig.getInstance();
               int chunkMinX = Math.floorDiv(data.getGauntletSiteX(), 16) * 16;
               int chunkMinZ = Math.floorDiv(data.getGauntletSiteZ(), 16) * 16;
               int chunkMaxX = chunkMinX + 15;
               int chunkMaxZ = chunkMinZ + 15;
               double dx = mob.getX() < chunkMinX ? chunkMinX - mob.getX() : (mob.getX() > chunkMaxX ? mob.getX() - chunkMaxX : 0.0);
               double dz = mob.getZ() < chunkMinZ ? chunkMinZ - mob.getZ() : (mob.getZ() > chunkMaxZ ? mob.getZ() - chunkMaxZ : 0.0);
               double distance = Math.sqrt(dx * dx + dz * dz);
               double radius = config.gauntlet_black_mob_influence_radius_blocks;
               if (distance > radius) {
                  return false;
               } else {
                  double progress = 1.0 - distance / radius;
                  double chance = config.gauntlet_black_mob_min_spawn_chance
                     + (config.gauntlet_black_mob_max_spawn_chance - config.gauntlet_black_mob_min_spawn_chance) * progress;
                  return mob.getRandom().nextFloat() < chance;
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean hasGauntletInInventory(ServerPlayerEntity player) {
      for (int slot = 0; slot < player.getInventory().size(); slot++) {
         if (isGauntlet(player.getInventory().getStack(slot))) {
            return true;
         }
      }

      return false;
   }

   public static void serverTick(MinecraftServer server) {
      if (server.getTicks() % 20 == 0) {
         ServerPlayerEntity holder = findHolder(server);
         UUID holderId = holder == null ? null : holder.getUuid();
         if (holderId == null) {
            lastAnnouncedHolder = null;
            lastBroadcastTick = Long.MIN_VALUE;
         } else {
            boolean changed = !holderId.equals(lastAnnouncedHolder);
            if (changed || lastBroadcastTick == Long.MIN_VALUE || server.getTicks() - lastBroadcastTick >= 1200L) {
               broadcastLocation(server, holder);
               lastAnnouncedHolder = holderId;
               lastBroadcastTick = server.getTicks();
            }
         }
      }
   }

   private static ServerPlayerEntity findHolder(MinecraftServer server) {
      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         if (hasGauntletInInventory(player)) {
            return player;
         }
      }

      return null;
   }

   private static void broadcastLocation(MinecraftServer server, ServerPlayerEntity holder) {
      BlockPos pos = holder.getBlockPos();
      String dimension = holder.getEntityWorld().getRegistryKey().getValue().toString();
      Text message = Text.literal(
            "Gauntlet of Darkness held by " + holder.getName().getString() + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " in " + dimension
         )
         .formatted(Formatting.DARK_PURPLE, Formatting.BOLD);
      server.getPlayerManager().broadcast(message, false);
   }
}
