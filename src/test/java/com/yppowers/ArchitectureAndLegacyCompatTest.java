package com.yppowers;

import com.google.gson.Gson;
import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilityKey;
import com.yppowers.core.HudPill;
import com.yppowers.power.PowerType;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureAndLegacyCompatTest {
   @Test
   void abilityKeysRoundTripWireIds() {
      for (AbilityKey key : AbilityKey.values()) {
         assertSame(key, AbilityKey.fromWireId(key.wireId()));
         assertNotNull(key.defaultLabel());
         assertFalse(key.defaultLabel().isBlank());
      }
      assertNull(AbilityKey.fromWireId(999));
   }

   @Test
   void allSeventeenPowersHaveUniqueIdsAndAccentColors() {
      assertEquals(17, PowerType.values().length);
      for (PowerType type : PowerType.values()) {
         assertSame(type, PowerType.fromId(type.getId()));
         assertNotEquals(0, type.getAccentColor());
      }
   }

   @Test
   void hudPillClampsCooldownsCleanly() {
      HudPill p = HudPill.ability(AbilityKey.PRIMARY, "Time Stop", 120, 60, true);
      assertEquals(120, p.cooldownTicks());
      assertEquals(120, p.maxCooldownTicks());
      assertFalse(p.isToggle());

      HudPill t = HudPill.toggle(AbilityKey.ALTERNATE, "Passives", true);
      assertTrue(t.isToggle());
      assertTrue(t.toggleActive());
   }

   @Test
   void legacyFlatJsonConfigRoundTripsWithoutLosingExistingKeys() {
      String legacyJson = """
         {
           "config_version": 36,
           "energy_max": 150.0,
           "goat_max_speed": 6.5,
           "divinity_shield_radius": 14,
           "gauntlet_spawn_enabled": true,
           "combat_tag_duration_seconds": 45,
           "energy_hud_anchor": "TOP_CENTER",
           "energy_hud_show_cooldown_pills": false
         }
         """;
      YPConfig cfg = new Gson().fromJson(legacyJson, YPConfig.class);
      cfg.validate();
      assertEquals(150.0, cfg.energy_max, 0.001);
      assertEquals(6.5, cfg.goat_max_speed, 0.001);
      assertEquals(14, cfg.divinity_shield_radius);
      assertTrue(cfg.gauntlet_spawn_enabled);
      assertEquals(45, cfg.combat_tag_duration_seconds);
      assertEquals("TOP_CENTER", cfg.energy_hud_anchor);
      assertFalse(cfg.energy_hud_show_cooldown_pills);
   }

   @Test
   void allianceWorldSaveNbtRoundTripsAlliesAndCachedNames() {
      AllianceManager mgr = new AllianceManager();
      UUID a = UUID.randomUUID();
      UUID b = UUID.randomUUID();
      mgr.addAlliance(a, b);
      mgr.rememberName(a, "Alice");
      mgr.rememberName(b, "Bob");

      NbtCompound nbt = mgr.toNbt();
      AllianceManager restored = AllianceManager.fromNbt(nbt);
      assertTrue(restored.areAllied(a, b));
      assertTrue(restored.areAllied(b, a));
      assertEquals("Alice", restored.getDisplayName(null, a));
      assertEquals("Bob", restored.getDisplayName(null, b));
   }
}
