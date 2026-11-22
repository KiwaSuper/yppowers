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

    @Test
    void consentInvitesPreventUnilateralForceAllyAndExpire() {
        AllianceSystem sys = new AllianceSystem();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        // Self-invite rejected
        assertEquals(AllianceSystem.InviteResult.SELF_INVITE, sys.sendInvite(alice, alice, 100L));

        // Sending invite does NOT ally yet
        assertEquals(AllianceSystem.InviteResult.SENT, sys.sendInvite(alice, bob, 100L));
        assertFalse(sys.areAllied(alice, bob));
        assertTrue(sys.hasPendingInvite(alice, bob, 200L));
        assertEquals(AllianceSystem.InviteResult.ALREADY_PENDING, sys.sendInvite(alice, bob, 200L));

        // Expired invite cannot be accepted
        long afterExpiry = 100L + AllianceSystem.INVITE_LIFETIME_TICKS + 1L;
        assertFalse(sys.hasPendingInvite(alice, bob, afterExpiry));
        assertFalse(sys.acceptInvite(bob, alice, afterExpiry));
        assertFalse(sys.areAllied(alice, bob));

        // Decline clears invite without allying
        assertEquals(AllianceSystem.InviteResult.SENT, sys.sendInvite(alice, bob, 2000L));
        assertTrue(sys.declineInvite(bob, alice));
        assertFalse(sys.acceptInvite(bob, alice, 2010L));
        assertFalse(sys.areAllied(alice, bob));

        // Accept creates mutual alliance and blocks future invites
        assertEquals(AllianceSystem.InviteResult.SENT, sys.sendInvite(alice, bob, 3000L));
        assertTrue(sys.acceptInvite(bob, alice, 3050L));
        assertTrue(sys.areAllied(alice, bob));
        assertTrue(sys.areAllied(bob, alice));
        assertEquals(AllianceSystem.InviteResult.ALREADY_ALLIED, sys.sendInvite(alice, bob, 3100L));
    }
}
