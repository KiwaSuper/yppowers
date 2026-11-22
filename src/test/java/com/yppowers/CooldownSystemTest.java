package com.yppowers;

import com.yppowers.system.CooldownSystem;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CooldownSystemTest {
    @Test
    void persistentAndVolatileCooldownsTickAndSerialize() {
        CooldownSystem cds = new CooldownSystem();
        UUID player = UUID.randomUUID();

        cds.set(player, "short_cd", 3);
        cds.setPersistent(player, "long_cd", 120);

        assertTrue(cds.isOnCooldown(player, "short_cd"));
        assertTrue(cds.isOnCooldown(player, "long_cd"));

        for (int t = 0; t < 3; t++) cds.tickAll(t);

        assertFalse(cds.isOnCooldown(player, "short_cd"));
        assertEquals(117, cds.getRemaining(player, "long_cd"));
        assertEquals(120, cds.getMaxDuration(player, "long_cd"));

        // Only persistent cooldown survives NBT round-trip (preserving max duration for HUD bar)
        cds.set(player, "temp_cd", 50);
        NbtCompound nbt = cds.toNbt();
        CooldownSystem restored = CooldownSystem.fromNbt(nbt);

        assertEquals(117, restored.getRemaining(player, "long_cd"));
        assertEquals(120, restored.getMaxDuration(player, "long_cd"));
        assertEquals(0, restored.getRemaining(player, "temp_cd"));

        restored.clearAll(player);
        assertFalse(restored.isOnCooldown(player, "long_cd"));
    }

    @Test
    void formatsSecondsAndMinutesCleanly() {
        assertEquals("1s", CooldownSystem.formatSeconds(1));
        assertEquals("3s", CooldownSystem.formatSeconds(60));
        assertEquals("1m", CooldownSystem.formatSeconds(1200));
        assertEquals("2m 5s", CooldownSystem.formatSeconds(2500));
    }
}
