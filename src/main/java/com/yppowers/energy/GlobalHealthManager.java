package com.yppowers.energy;

import com.yppowers.config.YPConfig;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Applies the configurable global-health bonus to players only (never mobs). */
public final class GlobalHealthManager {
    private static final Identifier MODIFIER_ID = Identifier.of("yppowers", "global_player_health");
    private GlobalHealthManager() {}

    public static void apply(ServerPlayerEntity player) {
        if (player == null) return;
        EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (maxHealth == null) return;
        EntityAttributeModifier current = maxHealth.getModifier(MODIFIER_ID);
        if (current != null) maxHealth.removeModifier(MODIFIER_ID);
        YPConfig config = YPConfig.getInstance();
        if (config.global_health_enabled) {
            // The player's vanilla 20 HP baseline receives an additive bonus to reach the
            // configured global target. Power-specific bonuses (e.g. GOAT) remain additive.
            double bonus = Math.max(1.0, config.global_player_health_value) - 20.0;
            if (bonus != 0.0) {
                maxHealth.addPersistentModifier(new EntityAttributeModifier(
                        MODIFIER_ID, bonus, EntityAttributeModifier.Operation.ADD_VALUE));
            }
        }
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    public static void applyAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) apply(player);
    }
}
