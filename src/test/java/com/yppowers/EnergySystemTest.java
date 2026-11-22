package com.yppowers;

import com.yppowers.config.YPConfig;
import com.yppowers.system.EnergySystem;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EnergySystemTest {
    @BeforeEach
    void resetConfig() {
        YPConfig.setForTests(new YPConfig());
    }

    @Test
    void spendAndTriggerNonStackingBleeding() {
        EnergySystem sys = new EnergySystem();
        UUID player = UUID.randomUUID();

        assertEquals(100.0, sys.getEnergy(player), 1e-6);
        assertTrue(sys.trySpendPure(player, 40.0));
        assertEquals(60.0, sys.getEnergy(player), 1e-6);
        assertFalse(sys.isBleeding(player));

        // Overspending triggers Bleeding without modifying energy
        assertFalse(sys.trySpendPure(player, 80.0));
        assertTrue(sys.isBleeding(player));
        int initialBleed = sys.getBleedingTicks(player);
        assertEquals(80, initialBleed);

        // Bleeding ticks down and retrying does not re-extend Bleeding
        sys.tickRegenPure(player, true);
        assertEquals(79, sys.getBleedingTicks(player));
        assertFalse(sys.trySpendPure(player, 10.0));
        assertEquals(79, sys.getBleedingTicks(player));
    }

    @Test
    void regenPausesAndPersistsThroughNbt() {
        EnergySystem sys = new EnergySystem();
        UUID player = UUID.randomUUID();
        sys.setEnergy(player, 50.0);

        sys.tickRegenPure(player, true);
        assertEquals(50.0, sys.getEnergy(player), 1e-6);

        sys.tickRegenPure(player, false);
        assertEquals(50.25, sys.getEnergy(player), 1e-6);

        NbtCompound nbt = sys.toNbt();
        EnergySystem restored = EnergySystem.fromNbt(nbt);
        assertEquals(50.25, restored.getEnergy(player), 1e-6);
    }
}
