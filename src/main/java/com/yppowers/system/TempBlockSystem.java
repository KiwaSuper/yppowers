package com.yppowers.system;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tracks temporary block overrides (Ice Emperor trails/prisons, Illusionist Spatial Erasure,
 * Teleport Gambit gateways, Spidey Auto-MLG cobwebs) with automatic restoration and shutdown rollback.
 */
public final class TempBlockSystem {
    public record BlockKey(RegistryKey<World> dimension, BlockPos pos) {}

    public static final class Entry {
        public final BlockState originalState;
        public final BlockState expectedTempState;
        public final String tag;
        public int ticksRemaining;
        public final boolean unbreakableByPlayers;
        public final Runnable onRestore;

        Entry(BlockState originalState, BlockState expectedTempState, String tag, int ticksRemaining,
              boolean unbreakableByPlayers, Runnable onRestore) {
            this.originalState = originalState;
            this.expectedTempState = expectedTempState;
            this.tag = tag;
            this.ticksRemaining = ticksRemaining;
            this.unbreakableByPlayers = unbreakableByPlayers;
            this.onRestore = onRestore;
        }
    }

    private static final Map<BlockKey, Entry> TRACKED = new HashMap<>();

    private TempBlockSystem() {}

    /**
     * Replaces a block temporarily and schedules automatic restoration to its prior state.
     * Refuses to overwrite bedrock, end portals/frames, barriers, or block entities.
     */
    public static boolean setTemporary(ServerWorld world, BlockPos pos, BlockState tempState,
                                       int durationTicks, String tag, boolean unbreakableByPlayers, Runnable onRestore) {
        if (world == null || pos == null || tempState == null || durationTicks <= 0) return false;
        BlockPos immutable = pos.toImmutable();
        BlockKey key = new BlockKey(world.getRegistryKey(), immutable);

        Entry existing = TRACKED.get(key);
        if (existing != null) {
            // Preserve the true original state if already tracked; just refresh duration/state.
            world.setBlockState(immutable, tempState);
            TRACKED.put(key, new Entry(existing.originalState, tempState, tag, durationTicks, unbreakableByPlayers, onRestore));
            return true;
        }

        BlockState original = world.getBlockState(immutable);
        if (isProtectedBlock(world, immutable, original)) return false;

        world.setBlockState(immutable, tempState);
        TRACKED.put(key, new Entry(original, tempState, tag, durationTicks, unbreakableByPlayers, onRestore));
        return true;
    }

    public static boolean isProtectedBlock(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.getHardness(world, pos) < 0.0f) return true;
        if (state.hasBlockEntity()) return true;
        return state.isOf(Blocks.BEDROCK)
                || state.isOf(Blocks.BARRIER)
                || state.isOf(Blocks.END_PORTAL)
                || state.isOf(Blocks.END_PORTAL_FRAME)
                || state.isOf(Blocks.NETHER_PORTAL)
                || state.isOf(Blocks.COMMAND_BLOCK)
                || state.isOf(Blocks.CHAIN_COMMAND_BLOCK)
                || state.isOf(Blocks.REPEATING_COMMAND_BLOCK)
                || state.isOf(Blocks.STRUCTURE_BLOCK);
    }

    public static boolean isPlayerBreakBlocked(ServerWorld world, BlockPos pos) {
        Entry e = TRACKED.get(new BlockKey(world.getRegistryKey(), pos.toImmutable()));
        return e != null && e.unbreakableByPlayers;
    }

    public static boolean hasTag(ServerWorld world, BlockPos pos, String tag) {
        Entry e = TRACKED.get(new BlockKey(world.getRegistryKey(), pos.toImmutable()));
        return e != null && tag.equals(e.tag);
    }

    public static void restoreNow(ServerWorld world, BlockPos pos) {
        BlockKey key = new BlockKey(world.getRegistryKey(), pos.toImmutable());
        Entry e = TRACKED.remove(key);
        if (e != null) applyRestore(world, key.pos(), e);
    }

    public static void serverTick(MinecraftServer server) {
        if (TRACKED.isEmpty()) return;
        Iterator<Map.Entry<BlockKey, Entry>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockKey, Entry> pair = it.next();
            Entry e = pair.getValue();
            e.ticksRemaining--;
            if (e.ticksRemaining <= 0) {
                BlockKey key = pair.getKey();
                it.remove();
                ServerWorld world = server.getWorld(key.dimension());
                if (world != null) applyRestore(world, key.pos(), e);
            }
        }
    }

    /** Guaranteed rollback of all temporary blocks before the server saves and exits. */
    public static void restoreAll(MinecraftServer server) {
        if (TRACKED.isEmpty()) return;
        Map<BlockKey, Entry> snapshot = new HashMap<>(TRACKED);
        TRACKED.clear();
        snapshot.forEach((key, entry) -> {
            ServerWorld world = server.getWorld(key.dimension());
            if (world != null) applyRestore(world, key.pos(), entry);
        });
    }

    private static void applyRestore(ServerWorld world, BlockPos pos, Entry e) {
        BlockState current = world.getBlockState(pos);
        // Restore if still our temporary state or air (for spatial erasure / melted ice)
        if (current.equals(e.expectedTempState) || current.isAir()) {
            world.setBlockState(pos, e.originalState);
        }
        if (e.onRestore != null) {
            e.onRestore.run();
        }
    }

    public static int trackedCount() {
        return TRACKED.size();
    }

    public static void clearForTests() {
        TRACKED.clear();
    }
}
