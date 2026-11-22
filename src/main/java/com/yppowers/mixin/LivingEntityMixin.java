package com.yppowers.mixin;

import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.system.AllianceSystem;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.FreezeSystem;
import com.yppowers.system.GraceSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
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

    @Unique
    private Power yppowers$getPower() {
        if (((Object) this) instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
            return PlayerDataStore.get(player.getEntityWorld().getServer()).getPower(player);
        }
        return null;
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void yppowers$preDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        yppowers$currentDamageSource = source;
        LivingEntity target = (LivingEntity) (Object) this;

        if (FreezeSystem.shouldResetHurtInvulnerability(target)) {
            target.timeUntilRegen = 0;
            target.hurtTime = 0;
            target.maxHurtTime = 0;
        }

        if (target instanceof ServerPlayerEntity targetPlayer && GraceSystem.isActive(world.getServer())) {
            ServerPlayerEntity attacker = GraceSystem.findPlayerAttacker(source);
            if (attacker != null && attacker != targetPlayer) {
                GraceSystem.notifyPvpBlocked(attacker);
                yppowers$currentDamageSource = null;
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "damage", at = @At("RETURN"))
    private void yppowers$postDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        yppowers$currentDamageSource = null;
    }

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float yppowers$modifyDamage(float amount) {
        LivingEntity target = (LivingEntity) (Object) this;
        if (target.getEntityWorld().isClient()) return amount;
        DamageSource source = yppowers$currentDamageSource;
        if (source == null) return amount;

        MinecraftServer server = target.getEntityWorld().getServer();
        Power defenderPower = yppowers$getPower();

        // Defensive elemental immunities (e.g. Thor lightning immunity) remain active even during Grace.
        if (defenderPower != null && target instanceof ServerPlayerEntity playerTarget) {
            boolean isLightning = source.isOf(DamageTypes.LIGHTNING_BOLT)
                    || source.isIn(DamageTypeTags.IS_LIGHTNING)
                    || source.getAttacker() instanceof LightningEntity
                    || source.getSource() instanceof LightningEntity;
            if (isLightning && defenderPower.isLightningImmune(playerTarget)) {
                return 0.0f;
            }
        }

        if (server != null && GraceSystem.isActive(server)) {
            return Math.max(0.0f, amount);
        }

        Entity attackerEntity = source.getAttacker();
        if (target instanceof ServerPlayerEntity targetPlayer && attackerEntity != null) {
            if (attackerEntity instanceof ServerPlayerEntity attackerPlayer
                    && server != null && AllianceSystem.get(server).isAlly(attackerPlayer, targetPlayer)) {
                return 0.0f;
            }
            if (CombatUtil.isFriendlyMinion(targetPlayer, attackerEntity)) {
                return 0.0f;
            }
        }

        if (attackerEntity instanceof ServerPlayerEntity attacker && server != null) {
            Power attackerPower = PlayerDataStore.get(server).getPower(attacker);
            if (attackerPower != null) {
                amount = attackerPower.modifyOutgoingDamage(attacker, target, source, amount);
            }
        }

        if (defenderPower != null && target instanceof ServerPlayerEntity player) {
            if (defenderPower.isFireImmune(player) && source.isIn(DamageTypeTags.IS_FIRE)) {
                return 0.0f;
            }
            amount = defenderPower.modifyIncomingDamage(player, source, amount);
        }

        return Math.max(0.0f, amount);
    }

    @Inject(method = "modifyAppliedDamage(Lnet/minecraft/entity/damage/DamageSource;F)F",
            at = @At("RETURN"), cancellable = true)
    private void yppowers$modifyPostMitigation(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (!(((Object) this) instanceof ServerPlayerEntity player)) return;
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null || GraceSystem.isActive(server)) return;
        Power power = PlayerDataStore.get(server).getPower(player);
        if (power == null) return;
        float current = Math.max(0.0f, cir.getReturnValue());
        float modified = Math.max(0.0f, power.modifyPostMitigationDamage(player, source, current));
        if (Float.compare(modified, current) != 0) {
            cir.setReturnValue(modified);
        }
    }

    @ModifyVariable(method = "handleFallDamage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float yppowers$modifyFallMultiplier(float damageMultiplier) {
        if (((Object) this) instanceof ServerPlayerEntity player
                && !GraceSystem.isActive(player.getEntityWorld().getServer())) {
            Power power = yppowers$getPower();
            if (power != null) return power.modifyFallDamageMultiplier(player, damageMultiplier);
        }
        return damageMultiplier;
    }

    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void yppowers$handleFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (((Object) this) instanceof ServerPlayerEntity player
                && !GraceSystem.isActive(player.getEntityWorld().getServer())) {
            Power power = yppowers$getPower();
            if (power != null && power.cancelFallDamage(player, (float) fallDistance, damageMultiplier)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "jump", at = @At("TAIL"))
    private void yppowers$onJump(CallbackInfo ci) {
        if (((Object) this) instanceof ServerPlayerEntity player
                && !GraceSystem.isActive(player.getEntityWorld().getServer())) {
            Power power = yppowers$getPower();
            if (power != null) power.onJump(player);
        }
    }
}
