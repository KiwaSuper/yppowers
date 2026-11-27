package com.yppowers;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.combat.CombatTagManager;
import com.yppowers.config.YPConfig;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatAndAllianceTest {
   @AfterEach
   void tearDown() {
      CombatTagManager.clearAll();
   }

   @Test
   void combatTagRefreshCountdownAndClear() {
      UUID p = UUID.randomUUID();
      assertFalse(CombatTagManager.isTagged(p));
      assertTrue(CombatTagManager.tagPure(p, 600));
      assertEquals(600, CombatTagManager.getRemainingTicks(p));
      assertFalse(CombatTagManager.tickPure(p));
      assertEquals(599, CombatTagManager.getRemainingTicks(p));
      assertFalse(CombatTagManager.tagPure(p, 600));
      for (int i = 0; i < 599; i++) {
         assertFalse(CombatTagManager.tickPure(p));
      }
      assertTrue(CombatTagManager.tickPure(p));
      assertFalse(CombatTagManager.isTagged(p));
   }

   @Test
   void allianceRequiresConsentAndExpires() {
      AllianceManager mgr = new AllianceManager();
      UUID a = UUID.randomUUID();
      UUID b = UUID.randomUUID();
      assertEquals(AllianceManager.InviteResult.SENT, mgr.sendInvite(a, b, 100L));
      assertFalse(mgr.areAllied(a, b));
      assertTrue(mgr.hasPendingInvite(a, b, 500L));
      assertFalse(mgr.hasPendingInvite(a, b, 100L + AllianceManager.INVITE_LIFETIME_TICKS));

      assertEquals(AllianceManager.InviteResult.SENT, mgr.sendInvite(a, b, 2000L));
      assertTrue(mgr.acceptInvite(b, a, 2100L));
      assertTrue(mgr.areAllied(a, b));
      assertTrue(mgr.areAllied(b, a));
   }

   @Test
   void legacyConfigValidatesCombatAndHudFields() {
      YPConfig cfg = new YPConfig();
      cfg.combat_tag_duration_seconds = 1;
      cfg.energy_hud_anchor = "invalid";
      cfg.energy_hud_offset_x = -50;
      cfg.validate();
      assertEquals(5, cfg.combat_tag_duration_seconds);
      assertEquals("BOTTOM_RIGHT", cfg.energy_hud_anchor);
      assertEquals(0, cfg.energy_hud_offset_x);
   }
}
