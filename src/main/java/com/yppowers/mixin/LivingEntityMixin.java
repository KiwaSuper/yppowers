package com.yppowers.mixin;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.energy.EnergyManager;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.impl.TimeWarperPower;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Unique
    private DamageSource yppowers$currentDamageSource;

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void yppowers$captureDamageSourceAndApplyCombatRules(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        yppowers$currentDamageSource = source;
        LivingEntity target = (LivingEntity) (Object) this;

        // Freeze player/mob movement for the full Time Stop duration but still allow the
        // caster to land repeated hits. Since frozen entities do not tick, vanilla's hurt
        // cooldown otherwise never counts down after the first hit.
        if (TimeWarperPower.isEntityFrozen(target)) {
            target.timeUntilRegen = 0;
            target.hurtTime = 0;
            target.maxHurtTime = 0;
        }

        // Grace blocks player-caused damage against players, including projectiles and
        // player-channelled lightning. PvE damage remains available.
        if (target instanceof ServerPlayerEntity targetPlayer
                && GraceManager.isActive(targetPlayer.getEntityWorld().getServer())) {
            ServerPlayerEntity attacker = GraceManager.findPlayerAttacker(source);
            if (attacker != null && attacker != targetPlayer) {
                GraceManager.notifyPvpBlocked(attacker);
                yppowers$currentDamageSource = null;
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "damage", at = @At("RETURN"))
    private void yppowers$clearDamageSource(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        yppowers$currentDamageSource = null;
    }

    @Unique
    private Power yppowers$getPower() {
        if (((Object) this) instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
            PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
            PowerType type = manager.getPowerType(player.getUuid());
            return type == null ? null : manager.getPowerInstance(type);
        }
        return null;
    }

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float yppowers$modifyDamage(float amount) {
        LivingEntity target = (LivingEntity) (Object) this;
        if (target.getEntityWorld().isClient()) return amount;
        DamageSource source = yppowers$currentDamageSource;
        if (source == null) return amount;
        if (target instanceof ServerPlayerEntity playerTarget
                && playerTarget.getEntityWorld().getServer() != null
                && PowerManager.get(playerTarget.getEntityWorld().getServer()).getPowerType(playerTarget.getUuid()) == PowerType.THOR
                && (source.isOf(net.minecraft.entity.damage.DamageTypes.LIGHTNING_BOLT)
                    || source.isIn(DamageTypeTags.IS_LIGHTNING)
                    || source.getAttacker() instanceof net.minecraft.entity.LightningEntity
                    || source.getSource() instanceof net.minecraft.entity.LightningEntity)) {
            // Thor's explicitly requested lightning immunity is defensive, not an activatable
            // ability, so Grace must not remove it.
            return 0.0f;
        }
        if (target.getEntityWorld().getServer() != null
                && GraceManager.isActive(target.getEntityWorld().getServer())) {
            // Grace suppresses power-based offensive bonuses and most defensive power effects
            // while leaving ordinary Minecraft damage untouched. Thor's lightning immunity is
            // the explicit exception above; PvP itself is cancelled before this point.
            return Math.max(0.0f, amount);
        }

        Entity attackerEntity = source.getAttacker();
        if (target instanceof ServerPlayerEntity targetPlayer && attackerEntity instanceof ServerPlayerEntity attackerPlayer) {
            if (AllianceManager.get(targetPlayer.getEntityWorld().getServer()).isAlly(attackerPlayer, targetPlayer)) return 0.0f;
        }

        // Apply the attacker's power before the target's defensive power.
        if (attackerEntity instanceof ServerPlayerEntity attacker && attacker.getEntityWorld().getServer() != null) {
            PowerManager manager = PowerManager.get(attacker.getEntityWorld().getServer());
            PowerType type = manager.getPowerType(attacker.getUuid());
            Power power = type == null ? null : manager.getPowerInstance(type);
            if (power != null) amount = power.modifyOutgoingDamage(attacker, target, source, amount);
        }

        Power power = yppowers$getPower();
        if (power != null && target instanceof ServerPlayerEntity player) {
            if (power.isFireImmune(player) && source.isIn(DamageTypeTags.IS_FIRE)) return 0.0f;
            // GOAT Overdrive records the post-mitigation amount in applyDamage below. Do not
            // intercept here, because damage()'s input has not yet passed armor/resistance math.
            if (power.getType() == PowerType.GOAT && player.getEntityWorld().getServer() != null
                    && EnergyManager.get(player.getEntityWorld().getServer()).isOverdriveActive(player.getUuid())) {
                return Math.max(0.0f, amount);
            }
            amount = power.modifyIncomingDamage(player, source, amount);
        }
        return Math.max(0.0f, amount);
    }

    /**
     * Intercepts the value after vanilla armor/enchantment/resistance math. Absorption is spent
     * exactly as it would be for a normal hit, then only the remaining health damage becomes debt.
     * Returning zero prevents the health hit now; the bounded debt manager repays it later.
     */
    @Inject(method = "modifyAppliedDamage(Lnet/minecraft/entity/damage/DamageSource;F)F",
            at = @At("RETURN"), cancellable = true)
    private void yppowers$deferGoatDamageAfterMitigation(DamageSource source, float amount,
                                                          CallbackInfoReturnable<Float> cir) {
        if (!(((Object) this) instanceof ServerPlayerEntity player)
                || player.getEntityWorld().getServer() == null
                || GraceManager.isActive(player.getEntityWorld().getServer())) return;
        PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
        if (manager.getPowerType(player.getUuid()) != PowerType.GOAT) return;
        EnergyManager energy = EnergyManager.get(player.getEntityWorld().getServer());
        if (!energy.isOverdriveActive(player.getUuid())) return;
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)
                || source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL)) return;
        float mitigated = Math.max(0.0f, cir.getReturnValue());
        float absorption = Math.max(0.0f, player.getAbsorptionAmount());
        float absorbed = Math.min(absorption, mitigated);
        if (absorbed > 0.0f) player.setAbsorptionAmount(absorption - absorbed);
        float healthDamageDebt = mitigated - absorbed;
        if (healthDamageDebt > 0.0f) energy.addGoatDamageDebt(player, healthDamageDebt);
        cir.setReturnValue(0.0f);
    }

    @ModifyVariable(method = "handleFallDamage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float yppowers$modifyFallMultiplier(float damageMultiplier) {
        Power power = yppowers$getPower();
        if (power != null && ((Object) this) instanceof ServerPlayerEntity player
                && !GraceManager.isActive(player.getEntityWorld().getServer())) {
            return power.modifyFallDamageMultiplier(player, damageMultiplier);
        }
        return damageMultiplier;
    }

    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void yppowers$handleFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        Power power = yppowers$getPower();
        if (power != null && ((Object) this) instanceof ServerPlayerEntity player
                && !GraceManager.isActive(player.getEntityWorld().getServer())) {
            if (power.onFallDamage(player, (float) fallDistance, damageMultiplier)) cir.setReturnValue(false);
        }
    }

    @Inject(method = "jump", at = @At("TAIL"))
    private void yppowers$onJump(CallbackInfo ci) {
        Power power = yppowers$getPower();
        if (power != null && ((Object) this) instanceof ServerPlayerEntity player
                && !GraceManager.isActive(player.getEntityWorld().getServer())) power.onJump(player);
    }
}
