package com.yppowers.core;

import com.yppowers.config.YPConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Unified contract implemented by every power module.
 * Powers own their own abilities, energy costs, cooldown gates, HUD metadata, and event hooks
 * so core dispatchers and mixins never need power-specific switch cascades.
 */
public abstract class Power {
    public abstract PowerId id();

    // -------------------------------------------------------------------------
    // Ability & Energy contract
    // -------------------------------------------------------------------------

    /**
     * Declarative HUD ability list for the player's current state (may vary by active form).
     */
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of();
    }

    /** Optional short status chip displayed next to the power badge (e.g. form, mode, shard count). */
    public String hudStatusChip(ServerPlayerEntity player) {
        return "";
    }

    /** Whether a toggle slot is currently ON for HUD dot rendering. */
    public boolean isToggleActive(ServerPlayerEntity player, AbilitySlot slot) {
        return false;
    }

    /**
     * Whether this power responds to the given input slot at all.
     * Returning false prevents spending Energy or showing Bleeding errors on unused keys.
     */
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return false;
    }

    /**
     * Server-side pre-check run before Energy is charged.
     * Powers should send a clear action-bar reason if returning false.
     */
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        return true;
    }

    /**
     * If true, the power handles its own Energy consumption (e.g. Ice Emperor Frost Guards low-energy
     * fallback, Heat Blast full-drain, Spirit Channeler Restless Shadows hazard cast).
     */
    public boolean handlesOwnEnergy(ServerPlayerEntity player, AbilitySlot slot) {
        return false;
    }

    /**
     * Energy cost for activating {@code slot}. Defaults to configured slot costs.
     */
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        YPConfig cfg = YPConfig.get();
        return switch (slot) {
            case PRIMARY, DOUBLE_JUMP -> cfg.energy.defaultPrimaryCost;
            case SECONDARY -> cfg.energy.defaultSecondaryCost;
            case TERTIARY -> cfg.energy.defaultTertiaryCost;
            case QUATERNARY -> cfg.energy.defaultQuaternaryCost;
            case ALTERNATE -> cfg.energy.defaultAlternateCost;
            case FORM_SWITCH, FEATHER_FALLING_TOGGLE, HEAT_BLAST -> 0.0;
        };
    }

    /** Whether Energy regeneration should pause for this player (e.g. Boots of the Sky in flight). */
    public boolean pausesEnergyRegen(ServerPlayerEntity player) {
        return false;
    }

    /** Whether this power is currently locked out from using active abilities (e.g. Ice Heat Blast). */
    public int abilityLockoutTicks(ServerPlayerEntity player, AbilitySlot slot) {
        return 0;
    }

    /** Dispatched after Grace, Bleeding, lockout, canActivate, and Energy checks succeed. */
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {}

    // -------------------------------------------------------------------------
    // Lifecycle hooks
    // -------------------------------------------------------------------------

    /** Called once per server tick globally (even if no online player has this power). */
    public void onServerTick(MinecraftServer server) {}

    /** Called once per server tick for each online player assigned this power. */
    public void onPlayerTick(ServerPlayerEntity player) {}

    public void onAssign(ServerPlayerEntity player) {}
    public void onRemove(ServerPlayerEntity player) {}
    public void onJoin(ServerPlayerEntity player) {}
    public void onDisconnect(ServerPlayerEntity player) {}
    public void onDeath(ServerPlayerEntity player) {}
    public void onRespawn(ServerPlayerEntity newPlayer, ServerPlayerEntity oldPlayer) {}
    public void onAnyPlayerJoin(ServerPlayerEntity player) {}
    public void onAnyPlayerDeath(ServerPlayerEntity player) {}
    public void onAnyPlayerRespawn(ServerPlayerEntity newPlayer, ServerPlayerEntity oldPlayer) {}
    public void onAnyMobKilled(ServerWorld world, MobEntity victim, DamageSource source) {}
    public void onServerStopping(MinecraftServer server) {}
    public void onServerStopped(MinecraftServer server) {}

    // -------------------------------------------------------------------------
    // Combat, movement & interaction hooks
    // -------------------------------------------------------------------------

    /** Return true to intercept and cancel a vanilla attack before damage runs (e.g. Illusionist Grip Throw). */
    public boolean onPreAttack(ServerPlayerEntity attacker, Entity target) {
        return false;
    }

    public void onAttack(ServerPlayerEntity attacker, Entity target) {}

    public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        return amount;
    }

    public float modifyOutgoingDamage(ServerPlayerEntity attacker, Entity target, DamageSource source, float amount) {
        return amount;
    }

    /**
     * Hook called after vanilla armor/enchantment/resistance mitigation (used by GOAT Overdrive debt).
     * Return modified final damage.
     */
    public float modifyPostMitigationDamage(ServerPlayerEntity player, DamageSource source, float mitigatedAmount) {
        return mitigatedAmount;
    }

    public boolean cancelFallDamage(ServerPlayerEntity player, float fallDistance, float damageMultiplier) {
        return false;
    }

    public float modifyFallDamageMultiplier(ServerPlayerEntity player, float damageMultiplier) {
        return damageMultiplier;
    }

    public void onJump(ServerPlayerEntity player) {}

    public boolean isFireImmune(ServerPlayerEntity player) {
        return false;
    }

    public boolean isLightningImmune(ServerPlayerEntity player) {
        return false;
    }

    public boolean shouldPreventShieldDisable(ServerPlayerEntity player) {
        return false;
    }

    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return false;
    }

    public boolean shouldMobIgnore(ServerPlayerEntity player, MobEntity mob) {
        return false;
    }

    public boolean shouldPreventHungerDepletion(ServerPlayerEntity player) {
        return false;
    }

    public ActionResult onUseItem(ServerPlayerEntity player, Hand hand) {
        return ActionResult.PASS;
    }

    public ActionResult onUseBlock(ServerPlayerEntity player, Hand hand, BlockPos pos) {
        return ActionResult.PASS;
    }

    public ActionResult onAttackEntity(ServerPlayerEntity player, Hand hand, Entity target) {
        return ActionResult.PASS;
    }

    public ActionResult onUseEntity(ServerPlayerEntity player, Hand hand, Entity target) {
        return ActionResult.PASS;
    }

    public void onMobKilled(ServerPlayerEntity killer, MobEntity victim) {}

    /** Global entity movement barrier check (e.g. Divinity Shield dome). */
    public boolean blocksEntityMovement(Entity entity, Vec3d movement) {
        return false;
    }

    /** Global projectile collision hook (returns true to cancel vanilla collision). */
    public boolean onProjectileCollision(ProjectileEntity projectile, HitResult hitResult) {
        return false;
    }

    /** Called when a player-owned arrow spawns in a ServerWorld (return false to cancel spawn). */
    public boolean onArrowSpawned(ServerWorld world, PersistentProjectileEntity arrow) {
        return true;
    }
}
