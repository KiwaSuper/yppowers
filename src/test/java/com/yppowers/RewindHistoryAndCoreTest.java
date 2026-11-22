package com.yppowers;

import com.yppowers.core.AbilitySlot;
import com.yppowers.core.PowerId;
import com.yppowers.power.shadowarcher.MorphRegistry;
import com.yppowers.power.timewarper.RewindHistory;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RewindHistoryAndCoreTest {
    @Test
    void ringBufferWrapsAtCapacityAndReturnsOldestSnapshot() {
        RewindHistory history = new RewindHistory();
        UUID p = UUID.randomUUID();
        RegistryKey<World> overworld = RegistryKey.of(RegistryKeys.WORLD, Identifier.ofVanilla("overworld"));

        for (int i = 0; i < RewindHistory.CAPACITY + 25; i++) {
            history.record(p, new RewindHistory.Snapshot(overworld, i, 64, 0, 0f, 0f, 20f), i);
        }

        assertEquals(RewindHistory.CAPACITY, history.historySize(p));
        RewindHistory.Snapshot oldest = history.getRewindTarget(p);
        assertNotNull(oldest);
        assertEquals(25.0, oldest.x(), 1e-6);

        history.sweepStale(2000, 100);
        assertNull(history.getRewindTarget(p));
    }

    @Test
    void powerIdsAndSlotsRoundTripCleanly() {
        assertEquals(17, PowerId.values().length);
        for (PowerId id : PowerId.values()) {
            assertEquals(id, PowerId.fromId(id.id()));
        }
        for (AbilitySlot slot : AbilitySlot.values()) {
            assertEquals(slot, AbilitySlot.fromWireId(slot.wireId()));
        }
    }

    @Test
    void morphRegistryFiltersByCategoryAndSearch() {
        var flying = MorphRegistry.filter(MorphRegistry.Category.FLYING, "bla", List.of());
        assertEquals(1, flying.size());
        assertEquals("minecraft:blaze", flying.get(0).entityId());
    }
}
