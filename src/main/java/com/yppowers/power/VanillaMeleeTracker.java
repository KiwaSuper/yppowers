package com.yppowers.power;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;

public final class VanillaMeleeTracker {
   private static final ThreadLocal<Map<UUID, Integer>> ACTIVE_ATTACKS = ThreadLocal.withInitial(HashMap::new);

   private VanillaMeleeTracker() {
   }

   public static void begin(ServerPlayerEntity player) {
      if (player != null) {
         ACTIVE_ATTACKS.get().merge(player.getUuid(), 1, Integer::sum);
      }
   }

   public static void end(ServerPlayerEntity player) {
      if (player != null) {
         Map<UUID, Integer> active = ACTIVE_ATTACKS.get();
         UUID id = player.getUuid();
         Integer depth = active.get(id);
         if (depth != null && depth > 1) {
            active.put(id, depth - 1);
         } else {
            active.remove(id);
         }

         if (active.isEmpty()) {
            ACTIVE_ATTACKS.remove();
         }
      }
   }

   public static boolean isActive(ServerPlayerEntity player) {
      return player != null && ACTIVE_ATTACKS.get().containsKey(player.getUuid());
   }
}
