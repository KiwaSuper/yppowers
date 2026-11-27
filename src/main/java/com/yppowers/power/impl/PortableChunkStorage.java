package com.yppowers.power.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.tick.WorldTickScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PortableChunkStorage {
   private static final Logger LOGGER = LoggerFactory.getLogger("YP Powers/Portable Chunk Storage");
   private static final int MAX_REPLICAS_PER_OWNER = 20;
   private static final int REGION_BASE_CHUNK = 12000;
   private static final int REGION_WIDTH_CHUNKS = 8;
   private static final int REPLICA_COLUMNS = 5;
   private static final int TEMP_BASE_CHUNK = 500000;
   private static final int TEMP_SPACING_CHUNKS = 2;
   private static final int ALLOCATION_COLUMNS = 2048;
   private static final Codec<PortableChunkStorage.StorageState> CODEC = Codec.PASSTHROUGH.xmap(dynamic -> {
      NbtElement element = (NbtElement)dynamic.convert(NbtOps.INSTANCE).getValue();
      return PortableChunkStorage.StorageState.fromNbt(element instanceof NbtCompound compound ? compound : new NbtCompound());
   }, state -> new Dynamic(NbtOps.INSTANCE, state.toNbt()));
   private static final PersistentStateType<PortableChunkStorage.StorageState> STATE_TYPE = new PersistentStateType<>(
      "yppowers_portable_chunk_storage", PortableChunkStorage.StorageState::new, CODEC, null
   );

   private PortableChunkStorage() {
   }

   public static int replicaCount(MinecraftServer server, UUID owner) {
      return server != null && owner != null && server.isOnThread() ? state(server).replicas.getOrDefault(owner, List.of()).size() : 0;
   }

   public static ChunkPos latestReplicaPosition(MinecraftServer server, UUID owner) {
      if (server != null && owner != null && server.isOnThread()) {
         List<PortableChunkStorage.Replica> replicas = state(server).replicas.get(owner);
         return replicas != null && !replicas.isEmpty()
            ? new ChunkPos(replicas.get(replicas.size() - 1).chunkX, replicas.get(replicas.size() - 1).chunkZ)
            : null;
      } else {
         return null;
      }
   }

   public static boolean captureReplica(MinecraftServer server, UUID owner, ServerWorld source, ChunkPos sourcePos) {
      if (server != null && owner != null && source != null && sourcePos != null && server.isOnThread() && source == server.getOverworld()) {
         ServerWorld domain = server.getWorld(TimeWarperDomainManager.TIME_DOMAIN_KEY);
         if (!sameVerticalRange(source, domain)) {
            return false;
         } else {
            PortableChunkStorage.StorageState data = state(server);
            List<PortableChunkStorage.Replica> owned = data.replicas.get(owner);
            if (owned == null) {
               owned = new ArrayList<>();
            }

            if (owned.size() < 20 && data.pending == null) {
               Integer region = data.ownerRegions.get(owner);
               boolean createdRegion = region == null;
               if (createdRegion) {
                  region = data.allocateRegion();
                  data.ownerRegions.put(owner, region);
               }

               int slot = owned.size();
               ChunkPos destinationPos = replicaPosition(region, slot);

               try {
                  copyChunk(source, sourcePos, domain, destinationPos);
                  PortableChunkStorage.Replica replica = new PortableChunkStorage.Replica(
                     region, slot, destinationPos.x, destinationPos.z, sourcePos.x, sourcePos.z
                  );
                  owned.add(replica);
                  data.replicas.put(owner, owned);
                  data.markDirty();
                  if (!flush(server, data)) {
                     throw new IllegalStateException("Could not flush captured replica state.");
                  } else {
                     return true;
                  }
               } catch (Exception var15) {
                  LOGGER.error("Failed to capture replica for owner {} from chunk {}", new Object[]{owner, sourcePos, var15});

                  try {
                     clearChunk(domain, destinationPos);
                  } catch (Exception var14) {
                     var15.addSuppressed(var14);
                  }

                  owned.removeIf(replicax -> replicax.slot == slot);
                  if (owned.isEmpty()) {
                     data.replicas.remove(owner);
                     if (createdRegion) {
                        data.ownerRegions.remove(owner);
                        data.release(region);
                     }
                  }

                  data.markDirty();

                  try {
                     flush(server, data);
                  } catch (Exception var13) {
                  }

                  return false;
               }
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   public static boolean isReplicaChunk(MinecraftServer server, UUID owner, ChunkPos pos) {
      if (server != null && owner != null && pos != null && server.isOnThread()) {
         List<PortableChunkStorage.Replica> replicas = state(server).replicas.get(owner);
         return replicas != null && replicas.stream().anyMatch(replica -> replica.chunkX == pos.x && replica.chunkZ == pos.z);
      } else {
         return false;
      }
   }

   public static boolean swapReplica(
      MinecraftServer server,
      UUID owner,
      ServerWorld targetWorld,
      ChunkPos targetPos,
      ChunkPos replicaPos,
      BiConsumer<ServerPlayerEntity, UUID> beforePlayerMoves
   ) {
      if (server != null
         && owner != null
         && targetWorld != null
         && targetPos != null
         && replicaPos != null
         && server.isOnThread()
         && targetWorld == server.getOverworld()) {
         ServerWorld domain = server.getWorld(TimeWarperDomainManager.TIME_DOMAIN_KEY);
         if (sameVerticalRange(targetWorld, domain) && !targetPos.equals(replicaPos)) {
            PortableChunkStorage.StorageState data = state(server);
            PortableChunkStorage.Replica replica = findReplica(data, owner, replicaPos);
            if (replica != null && data.pending == null) {
               int temporaryAllocation = data.allocateTemporary();
               ChunkPos temporaryPos = temporaryPosition(temporaryAllocation);
               data.pending = new PortableChunkStorage.Journal(
                  owner,
                  targetPos.x,
                  targetPos.z,
                  replicaPos.x,
                  replicaPos.z,
                  temporaryAllocation,
                  temporaryPos.x,
                  temporaryPos.z,
                  PortableChunkStorage.Phase.COPY_REPLICA_TO_TEMP
               );

               try {
                  if (!flush(server, data)) {
                     throw new IllegalStateException("Could not persist swap journal.");
                  } else {
                     runJournal(server, data, beforePlayerMoves);
                     return data.pending == null;
                  }
               } catch (Exception var12) {
                  LOGGER.error("Portable chunk swap remains journaled for owner {}", owner, var12);
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean recoverPending(MinecraftServer server, BiConsumer<ServerPlayerEntity, UUID> beforePlayerMoves) {
      if (server != null && server.isOnThread()) {
         PortableChunkStorage.StorageState data = state(server);
         if (data.pending == null) {
            return true;
         } else {
            try {
               runJournal(server, data, beforePlayerMoves);
               return data.pending == null;
            } catch (Exception var4) {
               LOGGER.error("Portable chunk swap recovery remains pending", var4);
               return false;
            }
         }
      } else {
         return false;
      }
   }

   public static boolean hasPendingRecovery(MinecraftServer server) {
      return server != null && server.isOnThread() && state(server).pending != null;
   }

   public static void rollbackLatestCapture(MinecraftServer server, UUID owner) {
      if (server != null && owner != null && server.isOnThread()) {
         PortableChunkStorage.StorageState data = state(server);
         List<PortableChunkStorage.Replica> owned = data.replicas.get(owner);
         if (owned != null && !owned.isEmpty()) {
            PortableChunkStorage.Replica latest = owned.get(owned.size() - 1);
            if (data.pending == null || data.pending.replicaX != latest.chunkX || data.pending.replicaZ != latest.chunkZ) {
               ServerWorld domain = server.getWorld(TimeWarperDomainManager.TIME_DOMAIN_KEY);
               if (domain != null) {
                  try {
                     clearChunk(domain, new ChunkPos(latest.chunkX, latest.chunkZ));
                     owned.remove(owned.size() - 1);
                     if (owned.isEmpty()) {
                        data.replicas.remove(owner);
                        Integer region = data.ownerRegions.remove(owner);
                        if (region != null) {
                           data.release(region);
                        }
                     }

                     data.markDirty();
                     flush(server, data);
                  } catch (Exception var7) {
                     LOGGER.error("Could not roll back latest replica for owner {}", owner, var7);
                  }
               }
            }
         }
      }
   }

   private static void runJournal(MinecraftServer server, PortableChunkStorage.StorageState data, BiConsumer<ServerPlayerEntity, UUID> beforePlayerMoves) throws Exception {
      PortableChunkStorage.Journal journal = data.pending;
      if (journal != null) {
         ServerWorld overworld = server.getOverworld();
         ServerWorld domain = server.getWorld(TimeWarperDomainManager.TIME_DOMAIN_KEY);
         if (!sameVerticalRange(overworld, domain)) {
            throw new IllegalStateException("Time Domain vertical range does not match the Overworld.");
         } else {
            ChunkPos target = new ChunkPos(journal.targetX, journal.targetZ);
            ChunkPos replica = new ChunkPos(journal.replicaX, journal.replicaZ);
            ChunkPos temporary = new ChunkPos(journal.temporaryX, journal.temporaryZ);
            BiConsumer<ServerPlayerEntity, UUID> callback = beforePlayerMoves == null ? (player, owner) -> {} : beforePlayerMoves;

            while (data.pending != null) {
               journal = data.pending;
               switch (journal.phase) {
                  case COPY_REPLICA_TO_TEMP:
                     copyChunk(domain, replica, domain, temporary);
                     advance(server, data, PortableChunkStorage.Phase.MOVE_REPLICA_ENTITIES_TO_TEMP);
                     break;
                  case MOVE_REPLICA_ENTITIES_TO_TEMP:
                     moveChunkEntities(domain, replica, domain, temporary, journal.owner, callback);
                     advance(server, data, PortableChunkStorage.Phase.COPY_TARGET_TO_REPLICA);
                     break;
                  case COPY_TARGET_TO_REPLICA:
                     copyChunk(overworld, target, domain, replica);
                     advance(server, data, PortableChunkStorage.Phase.MOVE_TARGET_ENTITIES_TO_REPLICA);
                     break;
                  case MOVE_TARGET_ENTITIES_TO_REPLICA:
                     moveChunkEntities(overworld, target, domain, replica, journal.owner, callback);
                     advance(server, data, PortableChunkStorage.Phase.CLEAR_TARGET);
                     break;
                  case CLEAR_TARGET:
                     clearChunk(overworld, target);
                     advance(server, data, PortableChunkStorage.Phase.COPY_TEMP_TO_TARGET);
                     break;
                  case COPY_TEMP_TO_TARGET:
                     copyChunk(domain, temporary, overworld, target);
                     advance(server, data, PortableChunkStorage.Phase.MOVE_TEMP_ENTITIES_TO_TARGET);
                     break;
                  case MOVE_TEMP_ENTITIES_TO_TARGET:
                     moveChunkEntities(domain, temporary, overworld, target, journal.owner, callback);
                     advance(server, data, PortableChunkStorage.Phase.CLEAR_TEMP);
                     break;
                  case CLEAR_TEMP:
                     clearChunk(domain, temporary);
                     int allocation = journal.temporaryAllocation;
                     data.pending = null;
                     data.release(allocation);
                     data.markDirty();
                     if (!flush(server, data)) {
                        throw new IllegalStateException("Could not persist completed swap.");
                     }
               }
            }
         }
      }
   }

   private static void advance(MinecraftServer server, PortableChunkStorage.StorageState data, PortableChunkStorage.Phase next) throws Exception {
      if (data.pending == null) {
         throw new IllegalStateException("Swap journal disappeared.");
      } else {
         data.pending.phase = next;
         data.markDirty();
         if (!flush(server, data)) {
            throw new IllegalStateException("Could not flush swap journal phase " + next + ".");
         }
      }
   }

   private static void copyChunk(ServerWorld sourceWorld, ChunkPos sourcePos, ServerWorld destinationWorld, ChunkPos destinationPos) {
      if (!sameVerticalRange(sourceWorld, destinationWorld)) {
         throw new IllegalArgumentException("Chunk worlds have incompatible vertical ranges.");
      } else {
         WorldChunk source = sourceWorld.getChunk(sourcePos.x, sourcePos.z);
         WorldChunk destination = destinationWorld.getChunk(destinationPos.x, destinationPos.z);
         BlockBox sourceBox = chunkBox(sourcePos, sourceWorld.getBottomY(), sourceWorld.getBottomY() + sourceWorld.getHeight() - 1);
         BlockBox destinationBox = chunkBox(destinationPos, destinationWorld.getBottomY(), destinationWorld.getBottomY() + destinationWorld.getHeight() - 1);
         destinationWorld.getBlockTickScheduler().clearNextTicks(destinationBox);
         destinationWorld.getFluidTickScheduler().clearNextTicks(destinationBox);

         for (BlockPos existing : new ArrayList<>(destination.getBlockEntities().keySet())) {
            destination.removeBlockEntity(existing);
         }

         for (int y = sourceWorld.getBottomY(); y < sourceWorld.getBottomY() + sourceWorld.getHeight(); y++) {
            for (int dx = 0; dx < 16; dx++) {
               for (int dz = 0; dz < 16; dz++) {
                  BlockPos from = new BlockPos(sourcePos.getStartX() + dx, y, sourcePos.getStartZ() + dz);
                  BlockPos to = new BlockPos(destinationPos.getStartX() + dx, y, destinationPos.getStartZ() + dz);
                  destinationWorld.setBlockState(to, sourceWorld.getBlockState(from), 2);
               }
            }
         }

         for (Entry<BlockPos, BlockEntity> entry : source.getBlockEntities().entrySet()) {
            BlockPos from = entry.getKey();
            if (from.getY() >= sourceWorld.getBottomY() && from.getY() < sourceWorld.getBottomY() + sourceWorld.getHeight()) {
               int localX = from.getX() - sourcePos.getStartX();
               int localZ = from.getZ() - sourcePos.getStartZ();
               BlockPos to = new BlockPos(destinationPos.getStartX() + localX, from.getY(), destinationPos.getStartZ() + localZ);
               NbtCompound nbt = entry.getValue().createNbtWithIdentifyingData(sourceWorld.getRegistryManager());
               BlockEntity copy = BlockEntity.createFromNbt(to, destinationWorld.getBlockState(to), nbt, destinationWorld.getRegistryManager());
               if (copy == null) {
                  throw new IllegalStateException("Could not deserialize block entity at " + from + ".");
               }

               destination.setBlockEntity(copy);
            }
         }

         copyBiomes(source, destination);
         WorldTickScheduler<Block> destinationBlocks = destinationWorld.getBlockTickScheduler();
         WorldTickScheduler<Fluid> destinationFluids = destinationWorld.getFluidTickScheduler();
         destinationBlocks.scheduleTicks(
            sourceWorld.getBlockTickScheduler(),
            sourceBox,
            new Vec3i(destinationPos.getStartX() - sourcePos.getStartX(), 0, destinationPos.getStartZ() - sourcePos.getStartZ())
         );
         destinationFluids.scheduleTicks(
            sourceWorld.getFluidTickScheduler(),
            sourceBox,
            new Vec3i(destinationPos.getStartX() - sourcePos.getStartX(), 0, destinationPos.getStartZ() - sourcePos.getStartZ())
         );
         destination.markNeedsSaving();
      }
   }

   private static void copyBiomes(WorldChunk source, WorldChunk destination) {
      ChunkSection[] sourceSections = source.getSectionArray();
      ChunkSection[] destinationSections = destination.getSectionArray();
      if (sourceSections.length != destinationSections.length) {
         throw new IllegalStateException("Chunk section counts differ.");
      } else {
         for (int sectionIndex = 0; sectionIndex < sourceSections.length; sectionIndex++) {
            ChunkSection from = sourceSections[sectionIndex];
            ChunkSection to = destinationSections[sectionIndex];
            if (from != null && to != null) {
               PalettedContainer<RegistryEntry<Biome>> targetPalette = (PalettedContainer<RegistryEntry<Biome>>)to.getBiomeContainer();

               for (int x = 0; x < 4; x++) {
                  for (int y = 0; y < 4; y++) {
                     for (int z = 0; z < 4; z++) {
                        targetPalette.set(x, y, z, from.getBiome(x, y, z));
                     }
                  }
               }
            }
         }
      }
   }

   private static void moveChunkEntities(
      ServerWorld source,
      ChunkPos sourcePos,
      ServerWorld destination,
      ChunkPos destinationPos,
      UUID owner,
      BiConsumer<ServerPlayerEntity, UUID> beforePlayerMoves
   ) {
      double minX = sourcePos.getStartX();
      double minZ = sourcePos.getStartZ();
      Box box = new Box(minX, source.getBottomY(), minZ, minX + 16.0, source.getBottomY() + source.getHeight(), minZ + 16.0);
      Set<Entity> roots = new HashSet<>();

      for (Entity entity : source.getEntitiesByType(TypeFilter.instanceOf(Entity.class), box, candidate -> !candidate.isRemoved())) {
         Entity root = entity.getRootVehicle();
         if (root != null && !root.isRemoved()) {
            roots.add(root);
         }
      }

      int deltaX = destinationPos.getStartX() - sourcePos.getStartX();
      int deltaZ = destinationPos.getStartZ() - sourcePos.getStartZ();

      for (Entity root : roots) {
         if (root.getEntityWorld() == source && !root.isRemoved()) {
            root.streamSelfAndPassengers()
               .filter(ServerPlayerEntity.class::isInstance)
               .map(ServerPlayerEntity.class::cast)
               .forEach(player -> beforePlayerMoves.accept(player, owner));
            if (!root.teleport(destination, root.getX() + deltaX, root.getY(), root.getZ() + deltaZ, Set.of(), root.getYaw(), root.getPitch(), true)) {
               throw new IllegalStateException("Could not move entity group " + root.getUuid() + " from " + sourcePos + ".");
            }
         }
      }
   }

   private static void clearChunk(ServerWorld world, ChunkPos pos) {
      if (world != null && sameVerticalRange(world, world)) {
         WorldChunk chunk = world.getChunk(pos.x, pos.z);
         BlockBox box = chunkBox(pos, world.getBottomY(), world.getBottomY() + world.getHeight() - 1);
         world.getBlockTickScheduler().clearNextTicks(box);
         world.getFluidTickScheduler().clearNextTicks(box);

         for (BlockPos existing : new ArrayList<>(chunk.getBlockEntities().keySet())) {
            chunk.removeBlockEntity(existing);
         }

         for (int y = world.getBottomY(); y < world.getBottomY() + world.getHeight(); y++) {
            for (int dx = 0; dx < 16; dx++) {
               for (int dz = 0; dz < 16; dz++) {
                  world.setBlockState(new BlockPos(pos.getStartX() + dx, y, pos.getStartZ() + dz), Blocks.AIR.getDefaultState(), 2);
               }
            }
         }

         chunk.markNeedsSaving();
      } else {
         throw new IllegalArgumentException("Invalid storage world.");
      }
   }

   private static boolean sameVerticalRange(ServerWorld first, ServerWorld second) {
      return first != null && second != null && first.getBottomY() == second.getBottomY() && first.getHeight() == second.getHeight();
   }

   private static BlockBox chunkBox(ChunkPos pos, int minY, int maxY) {
      return new BlockBox(pos.getStartX(), minY, pos.getStartZ(), pos.getEndX(), maxY, pos.getEndZ());
   }

   private static boolean flush(MinecraftServer server, PortableChunkStorage.StorageState data) {
      data.markDirty();
      return server.save(true, true, true);
   }

   private static PortableChunkStorage.StorageState state(MinecraftServer server) {
      return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
   }

   private static PortableChunkStorage.Replica findReplica(PortableChunkStorage.StorageState data, UUID owner, ChunkPos pos) {
      List<PortableChunkStorage.Replica> owned = data.replicas.get(owner);
      if (owned == null) {
         return null;
      } else {
         for (PortableChunkStorage.Replica replica : owned) {
            if (replica.chunkX == pos.x && replica.chunkZ == pos.z) {
               return replica;
            }
         }

         return null;
      }
   }

   private static ChunkPos replicaPosition(int region, int slot) {
      int regionColumn = region % 2048;
      int regionRow = region / 2048;
      int x = 12000 + regionColumn * 8 + slot % 5;
      int z = 12000 + regionRow * 8 + slot / 5;
      return new ChunkPos(x, z);
   }

   private static ChunkPos temporaryPosition(int encodedAllocation) {
      int allocation = -encodedAllocation - 1;
      int column = allocation % 2048;
      int row = allocation / 2048;
      return new ChunkPos(500000 + column * 2, 500000 + row * 2);
   }

   private static NbtCompound journalToNbt(PortableChunkStorage.Journal journal) {
      NbtCompound nbt = new NbtCompound();
      nbt.putString("Owner", journal.owner.toString());
      nbt.putInt("TargetX", journal.targetX);
      nbt.putInt("TargetZ", journal.targetZ);
      nbt.putInt("ReplicaX", journal.replicaX);
      nbt.putInt("ReplicaZ", journal.replicaZ);
      nbt.putInt("TemporaryAllocation", journal.temporaryAllocation);
      nbt.putInt("TemporaryX", journal.temporaryX);
      nbt.putInt("TemporaryZ", journal.temporaryZ);
      nbt.putString("Phase", journal.phase.name());
      return nbt;
   }

   private static PortableChunkStorage.Journal journalFromNbt(NbtCompound nbt) {
      try {
         UUID owner = UUID.fromString(nbt.getString("Owner", ""));
         int allocation = nbt.getInt("TemporaryAllocation", -1);
         if (allocation < 0
            && allocation >= -4194304
            && nbt.getInt("TemporaryX", Integer.MIN_VALUE) == temporaryPosition(allocation).x
            && nbt.getInt("TemporaryZ", Integer.MIN_VALUE) == temporaryPosition(allocation).z) {
            PortableChunkStorage.Phase phase = PortableChunkStorage.Phase.valueOf(nbt.getString("Phase", ""));
            return new PortableChunkStorage.Journal(
               owner,
               nbt.getInt("TargetX", 0),
               nbt.getInt("TargetZ", 0),
               nbt.getInt("ReplicaX", 0),
               nbt.getInt("ReplicaZ", 0),
               allocation,
               nbt.getInt("TemporaryX", 0),
               nbt.getInt("TemporaryZ", 0),
               phase
            );
         } else {
            return null;
         }
      } catch (IllegalArgumentException var4) {
         return null;
      }
   }

   private static final class Journal {
      final UUID owner;
      final int targetX;
      final int targetZ;
      final int replicaX;
      final int replicaZ;
      final int temporaryAllocation;
      final int temporaryX;
      final int temporaryZ;
      PortableChunkStorage.Phase phase;

      Journal(
         UUID owner,
         int targetX,
         int targetZ,
         int replicaX,
         int replicaZ,
         int temporaryAllocation,
         int temporaryX,
         int temporaryZ,
         PortableChunkStorage.Phase phase
      ) {
         this.owner = owner;
         this.targetX = targetX;
         this.targetZ = targetZ;
         this.replicaX = replicaX;
         this.replicaZ = replicaZ;
         this.temporaryAllocation = temporaryAllocation;
         this.temporaryX = temporaryX;
         this.temporaryZ = temporaryZ;
         this.phase = phase;
      }
   }

   private static enum Phase {
      COPY_REPLICA_TO_TEMP,
      MOVE_REPLICA_ENTITIES_TO_TEMP,
      COPY_TARGET_TO_REPLICA,
      MOVE_TARGET_ENTITIES_TO_REPLICA,
      CLEAR_TARGET,
      COPY_TEMP_TO_TARGET,
      MOVE_TEMP_ENTITIES_TO_TARGET,
      CLEAR_TEMP;
   }

   private static final class Replica {
      final int allocation;
      final int slot;
      final int chunkX;
      final int chunkZ;
      final int originX;
      final int originZ;

      Replica(int allocation, int slot, int chunkX, int chunkZ, int originX, int originZ) {
         this.allocation = allocation;
         this.slot = slot;
         this.chunkX = chunkX;
         this.chunkZ = chunkZ;
         this.originX = originX;
         this.originZ = originZ;
      }
   }

   private static final class StorageState extends PersistentState {
      final Map<UUID, List<PortableChunkStorage.Replica>> replicas = new HashMap<>();
      final Map<UUID, Integer> ownerRegions = new HashMap<>();
      final Set<Integer> allocations = new HashSet<>();
      int nextAllocation;
      PortableChunkStorage.Journal pending;

      private int nextFreeRegionIndex() {
         int max = 4194304;

         for (int offset = 0; offset < max; offset++) {
            int candidate = (this.nextAllocation + offset) % max;
            if (!this.allocations.contains(candidate) && !this.allocations.contains(-candidate - 1)) {
               this.nextAllocation = (candidate + 1) % max;
               return candidate;
            }
         }

         throw new IllegalStateException("No portable chunk storage allocations remain.");
      }

      int allocateRegion() {
         int allocation = this.nextFreeRegionIndex();
         this.allocations.add(allocation);
         this.markDirty();
         return allocation;
      }

      int allocateTemporary() {
         int allocation = -this.nextFreeRegionIndex() - 1;
         this.allocations.add(allocation);
         this.markDirty();
         return allocation;
      }

      void release(int allocation) {
         this.allocations.remove(allocation);
         this.markDirty();
      }

      NbtCompound toNbt() {
         NbtCompound root = new NbtCompound();
         root.putInt("NextAllocation", this.nextAllocation);
         NbtList regions = new NbtList();

         for (Entry<UUID, Integer> entry : this.ownerRegions.entrySet()) {
            NbtCompound value = new NbtCompound();
            value.putString("Owner", entry.getKey().toString());
            value.putInt("Region", entry.getValue());
            regions.add(value);
         }

         root.put("OwnerRegions", regions);
         NbtList replicaList = new NbtList();

         for (Entry<UUID, List<PortableChunkStorage.Replica>> ownerEntry : this.replicas.entrySet()) {
            for (PortableChunkStorage.Replica replica : ownerEntry.getValue()) {
               NbtCompound value = new NbtCompound();
               value.putString("Owner", ownerEntry.getKey().toString());
               value.putInt("Allocation", replica.allocation);
               value.putInt("Slot", replica.slot);
               value.putInt("ChunkX", replica.chunkX);
               value.putInt("ChunkZ", replica.chunkZ);
               value.putInt("OriginX", replica.originX);
               value.putInt("OriginZ", replica.originZ);
               replicaList.add(value);
            }
         }

         root.put("Replicas", replicaList);
         NbtList allocationList = new NbtList();

         for (int allocation : this.allocations) {
            allocationList.add(NbtInt.of(allocation));
         }

         root.put("Allocations", allocationList);
         if (this.pending != null) {
            root.put("Pending", PortableChunkStorage.journalToNbt(this.pending));
         }

         return root;
      }

      static PortableChunkStorage.StorageState fromNbt(NbtCompound root) {
         PortableChunkStorage.StorageState state = new PortableChunkStorage.StorageState();
         int max = 4194304;
         state.nextAllocation = Math.floorMod(root.getInt("NextAllocation", 0), max);

         for (NbtElement element : root.getListOrEmpty("Allocations")) {
            if (element instanceof NbtInt number && number.intValue() > -max - 1 && number.intValue() < max) {
               state.allocations.add(number.intValue());
            }
         }

         for (NbtElement elementx : root.getListOrEmpty("OwnerRegions")) {
            if (elementx instanceof NbtCompound value) {
               try {
                  UUID owner = UUID.fromString(value.getString("Owner", ""));
                  int region = value.getInt("Region", -1);
                  if (region >= 0 && region < max && !state.ownerRegions.containsValue(region)) {
                     state.ownerRegions.put(owner, region);
                     state.allocations.add(region);
                  }
               } catch (IllegalArgumentException var16) {
               }
            }
         }

         for (NbtElement elementxx : root.getListOrEmpty("Replicas")) {
            if (elementxx instanceof NbtCompound value) {
               try {
                  UUID owner = UUID.fromString(value.getString("Owner", ""));
                  int allocation = value.getInt("Allocation", -1);
                  int slot = value.getInt("Slot", -1);
                  int x = value.getInt("ChunkX", Integer.MIN_VALUE);
                  int z = value.getInt("ChunkZ", Integer.MIN_VALUE);
                  int originX = value.getInt("OriginX", 0);
                  int originZ = value.getInt("OriginZ", 0);
                  if (allocation >= 0 && allocation < max && slot >= 0 && slot < 20 && Integer.valueOf(allocation).equals(state.ownerRegions.get(owner))) {
                     ChunkPos expected = PortableChunkStorage.replicaPosition(allocation, slot);
                     if (x == expected.x && z == expected.z) {
                        List<PortableChunkStorage.Replica> owned = state.replicas.computeIfAbsent(owner, ignored -> new ArrayList<>());
                        if (owned.size() < 20 && !owned.stream().anyMatch(replica -> replica.slot == slot)) {
                           owned.add(new PortableChunkStorage.Replica(allocation, slot, x, z, originX, originZ));
                           state.allocations.add(allocation);
                        }
                     }
                  }
               } catch (IllegalArgumentException var15) {
               }
            }
         }

         if (root.get("Pending") instanceof NbtCompound pendingNbt) {
            state.pending = PortableChunkStorage.journalFromNbt(pendingNbt);
            if (state.pending != null) {
               state.allocations.add(state.pending.temporaryAllocation);
            }
         }

         return state;
      }
   }
}
