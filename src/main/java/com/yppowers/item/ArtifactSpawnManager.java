package com.yppowers.item;

import com.yppowers.config.YPConfig;
import com.yppowers.power.PowerManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap.Type;

public final class ArtifactSpawnManager {
   private static final long TICKS_PER_DAY = 24000L;
   private static final int RETRY_INTERVAL_TICKS = 20;
   private static final String ARTIFACT_GENERATION_KEY = "YPArtifactGeneration";
   private static final int GRIMOIRE_DISTANCE = 6000;
   private static final long GRIMOIRE_DISTANCE_SQUARED = 36000000L;
   private static final long MIN_ORIGIN_DISTANCE_SQUARED = 4000000L;

   private ArtifactSpawnManager() {
   }

   public static void initialize(MinecraftServer server) {
      serverTick(server);
   }

   public static void requestSpawn(MinecraftServer server, String artifact) {
      ServerWorld world = server.getOverworld();
      PowerManager data = PowerManager.get(server);
      long now = world.getTime();
      if ("gauntlet".equals(artifact) && !data.isGauntletSiteGenerated() && !data.isGauntletSitePending()) {
         scheduleGauntlet(world, data, now, false);
      } else if ("grimoire".equals(artifact) && !data.isGrimoireSiteGenerated() && !data.isGrimoireSitePending()) {
         if (!data.isGauntletSiteGenerated() && !data.isGauntletSitePending()) {
            throw new IllegalStateException("The Gauntlet site must be scheduled first so the Grimoire can be placed 6,000 blocks away.");
         }

         scheduleGrimoire(world, data, now, false);
      }
   }

   public static void serverTick(MinecraftServer server) {
      if (server.getTicks() % 20 == 0) {
         ServerWorld world = server.getOverworld();
         PowerManager data = PowerManager.get(server);
         YPConfig config = YPConfig.getInstance();
         long now = world.getTime();
         long relocationInterval = config.artifact_relocation_days * 24000L;
         trackTouches(server, data, now);
         if (config.gauntlet_spawn_enabled) {
            if (!data.isGauntletSiteGenerated() && !data.isGauntletSitePending()) {
               scheduleGauntlet(world, data, now, false);
            }

            if (data.isGauntletSitePending()) {
               tryPlaceGauntlet(world, data, now);
            }

            if (data.isGauntletSiteGenerated()) {
               if (data.getGauntletLastTouchedTime() < 0L) {
                  data.touchGauntlet(now);
               } else if (now - data.getGauntletLastTouchedTime() >= relocationInterval) {
                  boolean grimoireCanMove = !data.isGrimoireSiteGenerated()
                     || data.getGrimoireLastTouchedTime() >= 0L && now - data.getGrimoireLastTouchedTime() >= relocationInterval;
                  if (grimoireCanMove && scheduleGauntlet(world, data, now, true) && config.grimoire_spawn_enabled && data.isGrimoireSiteGenerated()) {
                     scheduleGrimoire(world, data, now, true);
                  }
               }
            }
         }

         if (config.grimoire_spawn_enabled && (data.isGauntletSiteGenerated() || data.isGauntletSitePending())) {
            if (!data.isGrimoireSiteGenerated() && !data.isGrimoireSitePending()) {
               scheduleGrimoire(world, data, now, false);
            }

            if (data.isGrimoireSitePending()) {
               tryPlaceGrimoire(world, data, now);
            }

            if (data.isGrimoireSiteGenerated()) {
               if (data.getGrimoireLastTouchedTime() < 0L) {
                  data.touchGrimoire(now);
               } else if (now - data.getGrimoireLastTouchedTime() >= relocationInterval) {
                  scheduleGrimoire(world, data, now, true);
               }
            }
         }
      }
   }

   private static void trackTouches(MinecraftServer server, PowerManager data, long now) {
      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         PlayerInventory inventory = player.getInventory();

         for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (GauntletOfDarknessManager.isGauntlet(stack)) {
               if (isCurrentGeneration(stack, data.getGauntletGeneration())) {
                  data.touchGauntlet(now);
               } else {
                  inventory.removeStack(slot);
               }
            } else if (GrimoireOfLightManager.isGrimoire(stack)) {
               if (isCurrentGeneration(stack, data.getGrimoireGeneration())) {
                  data.touchGrimoire(now);
               } else {
                  inventory.removeStack(slot);
               }
            }
         }
      }

      if (server.getTicks() % 100 == 0) {
         for (ServerWorld world : server.getWorlds()) {
            for (ItemEntity entity : world.getEntitiesByType(TypeFilter.instanceOf(ItemEntity.class), ignored -> true)) {
               ItemStack stack = entity.getStack();
               if (GauntletOfDarknessManager.isGauntlet(stack)) {
                  if (isCurrentGeneration(stack, data.getGauntletGeneration())) {
                     data.touchGauntlet(now);
                  } else {
                     entity.discard();
                  }
               } else if (GrimoireOfLightManager.isGrimoire(stack)) {
                  if (isCurrentGeneration(stack, data.getGrimoireGeneration())) {
                     data.touchGrimoire(now);
                  } else {
                     entity.discard();
                  }
               }
            }
         }
      }
   }

   private static boolean isCurrentGeneration(ItemStack stack, int generation) {
      int itemGeneration = YPItemData.getInt(stack, "YPArtifactGeneration", 0);
      if (itemGeneration == 0 && generation == 1) {
         YPItemData.setInt(stack, "YPArtifactGeneration", generation);
         return true;
      } else {
         return itemGeneration == generation;
      }
   }

   public static void onContainerInteract(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
      if (world.getBlockEntity(pos) instanceof Inventory inventory && world.getServer() != null) {
         PowerManager data = PowerManager.get(world.getServer());
         boolean dirty = false;

         for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (GauntletOfDarknessManager.isGauntlet(stack)) {
               if (isCurrentGeneration(stack, data.getGauntletGeneration())) {
                  data.touchGauntlet(world.getTime());
               } else {
                  inventory.setStack(slot, ItemStack.EMPTY);
                  dirty = true;
               }
            } else if (GrimoireOfLightManager.isGrimoire(stack)) {
               if (isCurrentGeneration(stack, data.getGrimoireGeneration())) {
                  data.touchGrimoire(world.getTime());
               } else {
                  inventory.setStack(slot, ItemStack.EMPTY);
                  dirty = true;
               }
            }
         }

         if (dirty) {
            inventory.markDirty();
         }
      }
   }

   private static boolean scheduleGauntlet(ServerWorld world, PowerManager data, long now, boolean relocation) {
      BlockPos spawn = world.getSpawnPoint().getPos();
      Random random = new Random(world.getSeed() ^ now ^ (long)data.getGauntletGeneration() << 32 ^ 6472651306132565537L);

      for (int attempt = 0; attempt < 256; attempt++) {
         double angle = random.nextDouble() * Math.PI * 2.0;
         int radius = 2001 + random.nextInt(1600);
         int candidateX = spawn.getX() + (int)Math.round(Math.cos(angle) * radius);
         int candidateZ = spawn.getZ() + (int)Math.round(Math.sin(angle) * radius);
         int chunkX = Math.floorDiv(candidateX, 16);
         int chunkZ = Math.floorDiv(candidateZ, 16);
         int x = chunkX * 16 + 8;
         int z = chunkZ * 16 + 8;
         long dx = (long)x - spawn.getX();
         long dz = (long)z - spawn.getZ();
         if (dx * dx + dz * dz >= 4000000L && insideBorder(world, x, z, 4) && !world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
            data.planGauntletSite(x, z, now, relocation);
            System.out.println("[YP Powers] Gauntlet vault site queued for an unloaded chunk at X=" + x + ", Z=" + z + ".");
            return true;
         }
      }

      return false;
   }

   private static boolean scheduleGrimoire(ServerWorld world, PowerManager data, long now, boolean relocation) {
      List<BlockPos> offsets = exactGrimoireOffsets();
      Collections.shuffle(offsets, new Random(world.getSeed() ^ now ^ 5483552744773651823L));

      for (BlockPos offset : offsets) {
         int x = data.getGauntletSiteX() + offset.getX();
         int z = data.getGauntletSiteZ() + offset.getZ();
         long originX = x;
         long originZ = z;
         if (originX * originX + originZ * originZ >= 4000000L) {
            int chunkX = Math.floorDiv(x, 16);
            int chunkZ = Math.floorDiv(z, 16);
            if (insideBorder(world, x, z, 0) && !world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
               data.planGrimoireSite(x, z, now, relocation);
               System.out.println("[YP Powers] Grimoire chest site queued for an unloaded chunk at X=" + x + ", Z=" + z + ".");
               return true;
            }
         }
      }

      return false;
   }

   private static List<BlockPos> exactGrimoireOffsets() {
      List<BlockPos> offsets = new ArrayList<>();

      for (int dx = -6000; dx <= 6000; dx++) {
         long remaining = 36000000L - (long)dx * dx;
         int dz = (int)Math.round(Math.sqrt(remaining));
         if ((long)dz * dz == remaining) {
            offsets.add(new BlockPos(dx, 0, dz));
            if (dz != 0) {
               offsets.add(new BlockPos(dx, 0, -dz));
            }
         }
      }

      return offsets;
   }

   private static boolean insideBorder(ServerWorld world, int x, int z, int margin) {
      return world.getWorldBorder().contains(new BlockPos(x - margin, 0, z - margin))
         && world.getWorldBorder().contains(new BlockPos(x - margin, 0, z + margin))
         && world.getWorldBorder().contains(new BlockPos(x + margin, 0, z - margin))
         && world.getWorldBorder().contains(new BlockPos(x + margin, 0, z + margin));
   }

   private static void tryPlaceGauntlet(ServerWorld world, PowerManager data, long now) {
      int x = data.getGauntletSiteX();
      int z = data.getGauntletSiteZ();
      int chunkX = Math.floorDiv(x, 16);
      int chunkZ = Math.floorDiv(z, 16);
      if (world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
         BlockPos surface = world.getTopPosition(Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
         int floorY = surface.getY() - 1;
         if (!GauntletOfDarknessManager.placeVaultAt(world, x, floorY, z, data.getGauntletGeneration())) {
            System.err.println("[YP Powers] The queued Gauntlet site was unsuitable; selecting another unloaded chunk.");
            scheduleGauntlet(world, data, now, true);
         } else {
            data.touchGauntlet(now);
            System.out.println("[YP Powers] Gauntlet vault generated at X=" + x + ", Y=" + (floorY + 1) + ", Z=" + z + ".");
         }
      }
   }

   private static void tryPlaceGrimoire(ServerWorld world, PowerManager data, long now) {
      int x = data.getGrimoireSiteX();
      int z = data.getGrimoireSiteZ();
      int chunkX = Math.floorDiv(x, 16);
      int chunkZ = Math.floorDiv(z, 16);
      if (world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
         BlockPos surface = world.getTopPosition(Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
         int chestY = surface.getY();
         if (!GrimoireOfLightManager.placeAt(world, x, chestY, z, data.getGrimoireGeneration())) {
            System.err.println("[YP Powers] The queued Grimoire site was unsuitable; selecting another unloaded chunk.");
            scheduleGrimoire(world, data, now, true);
         } else {
            data.touchGrimoire(now);
            System.out.println("[YP Powers] Grimoire chest generated at X=" + x + ", Y=" + chestY + ", Z=" + z + ".");
         }
      }
   }
}
