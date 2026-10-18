package com.yppowers.mixin;

import com.yppowers.alliance.AllianceManager;
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

    @Inject(method = "damage", at = @At("HEAD"))
    private void yppowers$captureDamageSource(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        yppowers$currentDamageSource = source;
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
            amount = power.modifyIncomingDamage(player, source, amount);
        }
        return Math.max(0.0f, amount);
    }

    @ModifyVariable(method = "handleFallDamage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float yppowers$modifyFallMultiplier(float damageMultiplier) {
        Power power = yppowers$getPower();
        if (power != null && ((Object) this) instanceof ServerPlayerEntity player) {
            return power.modifyFallDamageMultiplier(player, damageMultiplier);
        }
        return damageMultiplier;
    }

    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void yppowers$handleFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        Power power = yppowers$getPower();
        if (power != null && ((Object) this) instanceof ServerPlayerEntity player) {
            if (power.onFallDamage(player, (float) fallDistance, damageMultiplier)) cir.setReturnValue(false);
        }
    }

    @Inject(method = "jump", at = @At("TAIL"))
    private void yppowers$onJump(CallbackInfo ci) {
        Power power = yppowers$getPower();
        if (power != null && ((Object) this) instanceof ServerPlayerEntity player) power.onJump(player);
    }
}
