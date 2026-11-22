package com.yppowers;

import com.yppowers.system.CombatTagSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CombatTagSystemTest {
    @AfterEach
    void tearDown() {
        CombatTagSystem.clearAll();
    }

    @Test
    void tagsRefreshCountdownAndExpireCleanly() {
        UUID player = UUID.randomUUID();
        assertFalse(CombatTagSystem.isTagged(player));

        assertTrue(CombatTagSystem.tagPure(player, 600));
        assertTrue(CombatTagSystem.isTagged(player));
        assertEquals(600, CombatTagSystem.getRemainingTicks(player));

        // Refreshing an existing tag returns false (not newly entered) and resets timer
        assertFalse(CombatTagSystem.tickPure(player));
        assertEquals(599, CombatTagSystem.getRemainingTicks(player));
        assertFalse(CombatTagSystem.tagPure(player, 600));
        assertEquals(600, CombatTagSystem.getRemainingTicks(player));

        // Tick down to 1 remaining, then expire on final tick
        for (int i = 0; i < 599; i++) {
            assertFalse(CombatTagSystem.tickPure(player));
        }
        assertTrue(CombatTagSystem.tickPure(player));
        assertFalse(CombatTagSystem.isTagged(player));
        assertEquals(0, CombatTagSystem.getRemainingTicks(player));
    }

    @Test
    void clearTagRemovesImmediately() {
        UUID player = UUID.randomUUID();
        CombatTagSystem.tagPure(player, 600);
        assertTrue(CombatTagSystem.isTagged(player));
        CombatTagSystem.clearTag(player);
        assertFalse(CombatTagSystem.isTagged(player));
    }
}
