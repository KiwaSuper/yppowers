package com.yppowers.power.timewarper;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * O(1) array-backed circular buffer storing up to 500 ticks (25 seconds) of position/health history per player.
 */
public final class RewindHistory {
    public static final int CAPACITY = 500; // 25 seconds @ 20 TPS

    public record Snapshot(RegistryKey<World> dimension, double x, double y, double z, float yaw, float pitch, float health) {}

    private static final class RingBuffer {
        final Snapshot[] slots = new Snapshot[CAPACITY];
        int head;
        int size;
        long lastRecordedTick;

        void push(Snapshot snap, long tick) {
            slots[head] = snap;
            head = (head + 1) % CAPACITY;
            if (size < CAPACITY) size++;
            lastRecordedTick = tick;
        }

        Snapshot oldest() {
            if (size == 0) return null;
            int idx = (head - size + CAPACITY) % CAPACITY;
            return slots[idx];
        }
    }

    private final Map<UUID, RingBuffer> buffers = new HashMap<>();

    public void record(UUID playerId, Snapshot snapshot, long serverTick) {
        if (playerId == null || snapshot == null) return;
        buffers.computeIfAbsent(playerId, ignored -> new RingBuffer()).push(snapshot, serverTick);
    }

    public Snapshot getRewindTarget(UUID playerId) {
        RingBuffer rb = buffers.get(playerId);
        return rb == null ? null : rb.oldest();
    }

    public int historySize(UUID playerId) {
        RingBuffer rb = buffers.get(playerId);
        return rb == null ? 0 : rb.size;
    }

    public void sweepStale(long currentTick, long maxIdleTicks) {
        Iterator<Map.Entry<UUID, RingBuffer>> it = buffers.entrySet().iterator();
        while (it.hasNext()) {
            if (currentTick - it.next().getValue().lastRecordedTick > maxIdleTicks) {
                it.remove();
            }
        }
    }

    public void clear() {
        buffers.clear();
    }
}
