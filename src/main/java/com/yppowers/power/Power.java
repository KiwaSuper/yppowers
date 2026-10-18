package com.yppowers.power;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public abstract class Power {
    public abstract PowerType getType();

    public void onPrimaryActivate(ServerPlayerEntity player) {} // G key
    public void onSecondaryActivate(ServerPlayerEntity player) {} // H key
    public void onTertiaryActivate(ServerPlayerEntity player) {} // V key
    public void onQuaternaryActivate(ServerPlayerEntity player) {} // B key
    public void onAlternateActivate(ServerPlayerEntity player) {} // R key

    public void tick(ServerPlayerEntity player) {} // every server tick for this player

    public void onAssign(ServerPlayerEntity player) {} // when power is assigned
    public void onRemove(ServerPlayerEntity player) {} // when power is removed

    public void onPlayerJoin(ServerPlayerEntity player) {} // player joins server with this power
    public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {} // after respawn

    public void onAttack(ServerPlayerEntity attacker, Entity target) {} // when player attacks entity

    public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) { return amount; }
    public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, float amount) { return amount; }
    /** Source-aware overload; existing powers retain their general damage behavior by default. */
    public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, DamageSource source, float amount) {
        return modifyOutgoingDamage(player, target, amount);
    }

    public boolean onFallDamage(ServerPlayerEntity player, float fallDistance, float damageMultiplier) { return false; } // true=cancel
    public float modifyFallDamageMultiplier(ServerPlayerEntity player, float damageMultiplier) { return damageMultiplier; }

    public void onJump(ServerPlayerEntity player) {}

    public boolean isFireImmune(ServerPlayerEntity player) { return false; }

    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) { return false; }

    public boolean shouldPreventShieldDisable(ServerPlayerEntity player) { return false; }

    public boolean shouldMobIgnore(ServerPlayerEntity player, MobEntity mob) { return false; }
}
