package com.yppowers.system;

import com.yppowers.YPPowersMod;
import com.yppowers.config.YPConfig;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Applies the configurable global max-health bonus to players only (never mobs). */
public final class GlobalHealthSystem {
    private static final Identifier MODIFIER_ID = Identifier.of(YPPowersMod.MOD_ID, "global_player_health");

    private GlobalHealthSystem() {}

    public static void apply(ServerPlayerEntity player) {
        if (player == null) return;
        EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (maxHealth == null) return;
        if (maxHealth.getModifier(MODIFIER_ID) != null) {
            maxHealth.removeModifier(MODIFIER_ID);
        }
        YPConfig cfg = YPConfig.get();
        if (cfg.general.globalHealthEnabled) {
            double bonus = Math.max(1.0, cfg.general.globalPlayerHealth) - 20.0;
            if (Math.abs(bonus) > 1.0e-6) {
                maxHealth.addPersistentModifier(new EntityAttributeModifier(
                        MODIFIER_ID, bonus, EntityAttributeModifier.Operation.ADD_VALUE));
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void applyAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            apply(player);
        }
    }
}
