package com.yppowers;

import com.yppowers.system.AllianceSystem;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AllianceSystemTest {
    @Test
    void alliancesAreSymmetricAndPersistWithOfflineNames() {
        AllianceSystem sys = new AllianceSystem();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        assertFalse(sys.addAlliance(alice, alice));
        assertTrue(sys.areAllied(alice, alice));

        sys.rememberName(alice, "Alice");
        sys.rememberName(bob, "Bob");
        assertTrue(sys.addAlliance(alice, bob));

        assertTrue(sys.areAllied(alice, bob));
        assertTrue(sys.areAllied(bob, alice));
        assertEquals("Bob", sys.getDisplayName(null, bob));

        NbtCompound nbt = sys.toNbt();
        AllianceSystem restored = AllianceSystem.fromNbt(nbt);

        assertTrue(restored.areAllied(alice, bob));
        assertTrue(restored.areAllied(bob, alice));
        assertEquals("Alice", restored.getDisplayName(null, alice));
        assertEquals("Bob", restored.getDisplayName(null, bob));

        assertTrue(restored.removeAlliance(alice, bob));
        assertFalse(restored.areAllied(alice, bob));
        assertFalse(restored.areAllied(bob, alice));
    }
}
