package com.yppowers.power.iceemperor;

import com.yppowers.YPPowersMod;
import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.PowerId;
import com.yppowers.network.YPNetworking;
import com.yppowers.system.AllianceSystem;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Manages Ice Emperor's persistent form selection, attributes, Stealth visibility, and client sync. */
final class IceFormHandler {
    private static final String NBT_FORM = "SelectedForm";
    private static final Identifier MOD_SPEED = Identifier.of(YPPowersMod.MOD_ID, "ice_form_speed");
    private static final Identifier MOD_HEALTH = Identifier.of(YPPowersMod.MOD_ID, "ice_form_health");

    IceForm getForm(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return IceForm.ARMOURED;
        String raw = PlayerDataStore.get(server).getPowerNbt(player.getUuid(), PowerId.ICE_EMPEROR)
                .getString(NBT_FORM, YPConfig.get().powers.iceEmperor.defaultForm);
        return IceForm.parse(raw);
    }

    IceForm cycleForm(ServerPlayerEntity player) {
        PowerConfigs.IceEmperor cfg = YPConfig.get().powers.iceEmperor;
        List<IceForm> enabled = new ArrayList<>();
        if (cfg.armouredFormEnabled) enabled.add(IceForm.ARMOURED);
        if (cfg.stealthFormEnabled) enabled.add(IceForm.STEALTH);
        if (cfg.assassinFormEnabled) enabled.add(IceForm.ASSASSIN);
        if (enabled.isEmpty()) return IceForm.ARMOURED;

        IceForm current = getForm(player);
        int idx = enabled.indexOf(current);
        IceForm next = enabled.get((idx + 1) % enabled.size());
        setForm(player, next);
        return next;
    }

    void setForm(ServerPlayerEntity player, IceForm form) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        PlayerDataStore store = PlayerDataStore.get(server);
        store.getPowerNbt(player.getUuid(), PowerId.ICE_EMPEROR).putString(NBT_FORM, form.name());
        store.markDirty();
        applyAttributes(player, form);
        YPNetworking.broadcastIceEmperorForm(server, player, form.name(), true);
    }

    void applyAttributes(ServerPlayerEntity player, IceForm form) {
        clearAttributes(player);
        PowerConfigs.IceEmperor cfg = YPConfig.get().powers.iceEmperor;
        if (!cfg.formsEnabled) return;

        switch (form) {
            case ARMOURED -> setMod(player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED,
                    cfg.armouredMovementMultiplier - 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            case ASSASSIN -> {
                setMod(player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED,
                        cfg.assassinMovementMultiplier - 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                setMod(player, EntityAttributes.MAX_HEALTH, MOD_HEALTH,
                        cfg.assassinMaxHealthMultiplier - 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
            }
            case STEALTH -> {}
        }
    }

    void tickForm(ServerPlayerEntity player, boolean powerEnabled) {
        PowerConfigs.IceEmperor cfg = YPConfig.get().powers.iceEmperor;
        if (!cfg.formsEnabled || !powerEnabled) {
            clearAttributes(player);
            return;
        }
        IceForm form = getForm(player);
        if (form == IceForm.ARMOURED) {
            StatusEffectInstance res = player.getStatusEffect(StatusEffects.RESISTANCE);
            if (res == null || (res.getAmplifier() == cfg.armouredResistanceAmplifier && res.getDuration() <= 10)) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, cfg.armouredResistanceAmplifier, false, false));
            }
        } else if (form == IceForm.STEALTH && player.getEntityWorld() instanceof ServerWorld world
                && world.getServer() != null && world.getServer().getTicks() % cfg.stealthUpdateIntervalTicks == 0) {
            boolean detected = false;
            AllianceSystem alliances = AllianceSystem.get(world.getServer());
            for (ServerPlayerEntity other : world.getServer().getPlayerManager().getPlayerList()) {
                if (other == player || other.getEntityWorld() != world || other.isSpectator()) continue;
                if (!cfg.stealthAlliesCount && alliances.isAlly(player, other)) continue;
                if (other.getEntityPos().distanceTo(player.getEntityPos()) <= cfg.stealthDetectionRadius) {
                    detected = true;
                    break;
                }
            }
            if (!detected) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, cfg.stealthUpdateIntervalTicks + 15, 0, false, false));
            } else {
                player.removeStatusEffect(StatusEffects.INVISIBILITY);
            }
        }
    }

    void clearAttributes(ServerPlayerEntity player) {
        removeMod(player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED);
        removeMod(player, EntityAttributes.MAX_HEALTH, MOD_HEALTH);
    }

    private static void setMod(ServerPlayerEntity p, RegistryEntry<EntityAttribute> attr, Identifier id, double val, EntityAttributeModifier.Operation op) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        if (inst.getModifier(id) != null) inst.removeModifier(id);
        inst.addTemporaryModifier(new EntityAttributeModifier(id, val, op));
    }

    private static void removeMod(ServerPlayerEntity p, RegistryEntry<EntityAttribute> attr, Identifier id) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst != null && inst.getModifier(id) != null) inst.removeModifier(id);
    }
}
