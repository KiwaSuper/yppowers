package com.yppowers.energy;

import com.yppowers.config.YPConfig;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class GlobalHealthManager {
   private static final Identifier MODIFIER_ID = Identifier.of("yppowers", "global_player_health");

   private GlobalHealthManager() {
   }

   public static void apply(ServerPlayerEntity player) {
      if (player != null) {
         EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
         if (maxHealth != null) {
            EntityAttributeModifier current = maxHealth.getModifier(MODIFIER_ID);
            if (current != null) {
               maxHealth.removeModifier(MODIFIER_ID);
            }

            YPConfig config = YPConfig.getInstance();
            if (config.global_health_enabled) {
               double bonus = Math.max(1.0, config.global_player_health_value) - 20.0;
               if (bonus != 0.0) {
                  maxHealth.addPersistentModifier(new EntityAttributeModifier(MODIFIER_ID, bonus, Operation.ADD_VALUE));
               }
            }

            player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
         }
      }
   }

   public static void applyAll(MinecraftServer server) {
      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         apply(player);
      }
   }
}
