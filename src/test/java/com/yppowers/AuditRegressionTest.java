package com.yppowers;

import com.yppowers.power.goat.GoatPower;
import com.yppowers.power.shadowarcher.ShadowArcherMorphRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuditRegressionTest {
   @Test
   void goatOverdriveSpeedBonusNeverTurnsNegativeWhenSprinting() {
      // Previously: (configuredMultiplier - 1.0) - goatSpeedModifier went negative (e.g. 0.5 - 8.0 = -7.5),
      // causing ADD_MULTIPLIED_TOTAL to freeze the player in place while sprinting in Overdrive.
      for (double configured : new double[]{0.5, 1.0, 1.5, 2.0, 4.0}) {
         for (double sprintMod : new double[]{0.0, 1.0, 3.0, 8.0}) {
            double bonus = GoatPower.computeOverdriveSpeedBonus(configured, sprintMod);
            assertTrue(bonus >= 0.0, "Overdrive bonus must never be negative for configured=" + configured + ", sprint=" + sprintMod);
            assertTrue(1.0 + bonus > 0.0);
         }
      }
      assertEquals(0.0, GoatPower.computeOverdriveSpeedBonus(1.5, 8.0), 1.0E-9);
      assertEquals(0.5, GoatPower.computeOverdriveSpeedBonus(1.5, 0.0), 1.0E-9);
      assertEquals(0.25, GoatPower.computeOverdriveSpeedBonus(2.5, 1.0), 1.0E-9);
   }

   @Test
   void shadowArcherMorphRegistrySupportsConcurrentReadsSafely() throws Exception {
      ExecutorService pool = Executors.newFixedThreadPool(4);
      try {
         List<Callable<Boolean>> tasks = new ArrayList<>();
         String[] ids = {"zombie", "blaze", "enderman", "warden", "phantom", "axolotl", "pig", "creeper"};
         for (int t = 0; t < 8; t++) {
            final int offset = t;
            tasks.add(() -> {
               for (int i = 0; i < 200; i++) {
                  String id = ids[(i + offset) % ids.length];
                  ShadowArcherMorphRegistry.Profile p = ShadowArcherMorphRegistry.forId(id);
                  if (p == null || !id.equals(p.id())) {
                     return false;
                  }
               }
               return true;
            });
         }
         for (Future<Boolean> f : pool.invokeAll(tasks)) {
            assertTrue(f.get());
         }
      } finally {
         pool.shutdownNow();
      }
   }

   @Test
   void mixinsConfigTargetsJava21AndExcludesDeadWorldMixin() throws Exception {
      try (InputStream in = getClass().getClassLoader().getResourceAsStream("yppowers.mixins.json")) {
         assertNotNull(in, "yppowers.mixins.json must be present on classpath");
         String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
         assertTrue(json.contains("\"JAVA_21\""), "Mixin compatibility level must be JAVA_21");
         assertFalse(json.contains("\"WorldMixin\""), "Dead WorldMixin must not be registered");
      }
   }
}
