package com.yppowers.power.spiritchanneler;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.network.YPNetworking;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.EnergySystem;
import com.yppowers.system.SummonSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spirit Channeler: permanent unlockable spirits (Wolf, Guardian, Wraith), Shadow Step (H),
 * Restless Shadows ultimate with hazardous low-Energy cast (V), and spirit cycling (B/R).
 */
public final class SpiritChannelerPower extends Power {
    private static final String CD_SUMMON = "spirit_summon";
    private static final String CD_STEP = "spirit_step";
    private static final String CD_SHADOWS = "spirit_shadows";
    private static final String CAT_SPIRIT = "spirit_summon";
    private static final String CAT_SHADOW = "spirit_restless_shadow";
    private static final String NBT_UNLOCKS = "Unlocks";
    private static final List<String> ALL_FORMS = List.of("WOLF", "GUARDIAN", "WRAITH");

    private final Map<UUID, String> selectedForm = new HashMap<>();

    @Override
    public PowerId id() {
        return PowerId.SPIRIT_CHANNELER;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of(
                AbilitySpec.active(AbilitySlot.PRIMARY, "Summon Spirit", CD_SUMMON),
                AbilitySpec.active(AbilitySlot.SECONDARY, "Shadow Step", CD_STEP),
                AbilitySpec.active(AbilitySlot.TERTIARY, "Restless Shadows", CD_SHADOWS),
                AbilitySpec.utility(AbilitySlot.QUATERNARY, "Cycle Spirit")
        );
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        Set<String> unlocked = getUnlocks(player);
        if (unlocked.isEmpty()) return "0/3 Spirits";
        String sel = getSelected(player, unlocked);
        return sel + " (" + unlocked.size() + "/3)";
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY || slot == AbilitySlot.SECONDARY
                || slot == AbilitySlot.TERTIARY || slot == AbilitySlot.QUATERNARY || slot == AbilitySlot.ALTERNATE;
    }

    @Override
    public boolean handlesOwnEnergy(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.TERTIARY; // Restless Shadows permits hazardous low-Energy cast
    }

    @Override
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        PowerConfigs.SpiritChanneler cfg = YPConfig.get().powers.spiritChanneler;
        return switch (slot) {
            case PRIMARY -> cfg.summonEnergyCost;
            case SECONDARY -> cfg.shadowStepEnergyCost;
            case TERTIARY -> cfg.restlessShadowsEnergyCost;
            case QUATERNARY, ALTERNATE -> 0.0;
            default -> super.energyCost(player, slot);
        };
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        CooldownSystem cds = CooldownSystem.get(server);
        return switch (slot) {
            case PRIMARY -> {
                if (!cds.checkReady(player, CD_SUMMON, "Spirit Summon")) yield false;
                if (getUnlocks(player).isEmpty()) {
                    player.sendMessage(Text.literal("Unlock a Spirit Form first (Wolf+Bone, Guardian+Prismarine Shard, Wither Skeleton+Phantom Membrane).").formatted(Formatting.YELLOW), true);
                    yield false;
                }
                if (SummonSystem.countActive(server, player.getUuid(), CAT_SPIRIT) >= 3) {
                    player.sendMessage(Text.literal("You already have 3 active spirits.").formatted(Formatting.YELLOW), true);
                    yield false;
                }
                yield true;
            }
            case SECONDARY -> cds.checkReady(player, CD_STEP, "Shadow Step");
            case TERTIARY -> {
                if (EnergySystem.get(server).isBleeding(player.getUuid())) {
                    player.sendMessage(Text.literal("You are Bleeding. Abilities are disabled.").formatted(Formatting.RED), true);
                    yield false;
                }
                yield cds.checkReady(player, CD_SHADOWS, "Restless Shadows");
            }
            case QUATERNARY, ALTERNATE -> true;
            default -> false;
        };
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        switch (slot) {
            case PRIMARY -> summonSpirit(player);
            case SECONDARY -> shadowStep(player);
            case TERTIARY -> castRestlessShadows(player);
            case QUATERNARY, ALTERNATE -> cycleSpirit(player);
            default -> {}
        }
    }

    @Override
    public void onMobKilled(ServerPlayerEntity killer, MobEntity victim) {
        String form = null;
        Item req = null;
        if (victim.getType() == EntityType.WOLF) {
            form = "WOLF";
            req = Items.BONE;
        } else if (victim.getType() == EntityType.GUARDIAN) {
            form = "GUARDIAN";
            req = Items.PRISMARINE_SHARD;
        } else if (victim.getType() == EntityType.WITHER_SKELETON) {
            form = "WRAITH";
            req = Items.PHANTOM_MEMBRANE;
        }
        if (form == null) return;
        if (getUnlocks(killer).contains(form)) return;
        if (!consumeOne(killer, req)) {
            killer.sendMessage(Text.literal("Spirit trace found! Carry a " + req.getName().getString() + " to bind " + form + ".").formatted(Formatting.GRAY), true);
            return;
        }
        addUnlock(killer, form);
        killer.sendMessage(Text.literal("Spirit Form unlocked permanently: " + form).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
    }

    private void cycleSpirit(ServerPlayerEntity player) {
        Set<String> unlocked = getUnlocks(player);
        List<String> list = new ArrayList<>();
        for (String f : ALL_FORMS) if (unlocked.contains(f)) list.add(f);
        if (list.isEmpty()) {
            player.sendMessage(Text.literal("No Spirit Forms unlocked yet.").formatted(Formatting.YELLOW), true);
            return;
        }
        String current = getSelected(player, unlocked);
        int idx = list.indexOf(current);
        String next = list.get((idx + 1) % list.size());
        selectedForm.put(player.getUuid(), next);
        player.sendMessage(Text.literal("Selected Spirit: " + next).formatted(Formatting.LIGHT_PURPLE), true);
    }

    private void summonSpirit(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        String form = getSelected(player, getUnlocks(player));
        MobEntity mob = switch (form) {
            case "GUARDIAN" -> EntityType.IRON_GOLEM.create(world, SpawnReason.TRIGGERED);
            case "WRAITH" -> EntityType.VEX.create(world, SpawnReason.TRIGGERED);
            default -> EntityType.WOLF.create(world, SpawnReason.TRIGGERED);
        };
        if (mob == null) return;

        double hp = switch (form) {
            case "GUARDIAN" -> 80.0;
            case "WRAITH" -> 18.0;
            default -> 30.0;
        };
        mob.refreshPositionAndAngles(player.getX() + 1.0, player.getY(), player.getZ() + 1.0, player.getYaw(), 0.0f);
        mob.setCustomName(Text.literal(form + " Spirit · " + player.getName().getString()).formatted(Formatting.LIGHT_PURPLE));
        mob.setCustomNameVisible(true);
        mob.setPersistent();
        if (mob.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            mob.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(hp);
        }
        mob.setHealth((float) hp);
        if (mob instanceof WolfEntity wolf) wolf.setOwner(player);
        if ("GUARDIAN".equals(form)) {
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, StatusEffectInstance.INFINITE, 0, false, false));
        }
        world.spawnEntity(mob);

        PowerConfigs.SpiritChanneler cfg = YPConfig.get().powers.spiritChanneler;
        SummonSystem.register(mob, player.getUuid(), CAT_SPIRIT, cfg.spiritLifespanTicks, false);
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_SUMMON, cfg.summonCooldownTicks);
        VfxSystem.risingHelix(world, ParticleTypes.SOUL, mob.getEntityPos(), 1.0, 2.2, 16);
        VfxSystem.playSound(world, mob.getEntityPos(), SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private void shadowStep(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.SpiritChanneler cfg = YPConfig.get().powers.spiritChanneler;
        HitResult hit = player.raycast(cfg.shadowStepRange, 0.0f, false);
        Vec3d dest = hit.getPos().subtract(CombatUtil.safeLook(player).multiply(0.6));
        BlockPos safe = BlockPos.ofFloored(dest);
        if (!world.getBlockState(safe).isAir()) safe = safe.up();

        VfxSystem.burst(world, ParticleTypes.LARGE_SMOKE, player.getEntityPos().add(0, 1.0, 0), 16, 0.4, 0.05);
        player.requestTeleport(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
        VfxSystem.burst(world, ParticleTypes.SOUL, player.getEntityPos().add(0, 1.0, 0), 18, 0.4, 0.05);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.8f);
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_STEP, cfg.shadowStepCooldownTicks);
    }

    private void castRestlessShadows(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        MinecraftServer server = world.getServer();
        PowerConfigs.SpiritChanneler cfg = YPConfig.get().powers.spiritChanneler;
        EnergySystem energy = EnergySystem.get(server);

        boolean lowEnergy = energy.getEnergy(player.getUuid()) + 1.0e-6 < cfg.restlessShadowsEnergyCost;
        if (lowEnergy) {
            energy.drainAll(player);
            YPNetworking.sendHudWarning(player, "ARE YOU SURE?", 80);
            // Broad hazardous knockback affecting everyone including caster and allies
            Box box = player.getBoundingBox().expand(cfg.restlessShadowsRadius);
            for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                Vec3d out = e.getEntityPos().subtract(player.getEntityPos());
                out = out.lengthSquared() < 1.0e-4 ? new Vec3d(0, 1, 0) : out.normalize();
                e.setVelocity(out.x * 1.8, 0.75, out.z * 1.8);
                ((EntityVelocityAccessor) e).invokeYppowersScheduleVelocityUpdate();
            }
        } else {
            energy.trySpend(player, cfg.restlessShadowsEnergyCost, "Restless Shadows");
        }

        WitherSkeletonEntity shadow = EntityType.WITHER_SKELETON.create(world, SpawnReason.TRIGGERED);
        if (shadow != null) {
            shadow.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0.0f);
            shadow.setCustomName(Text.literal(lowEnergy ? "Uncontrolled Shadow" : "Restless Shadow").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
            shadow.setCustomNameVisible(false);
            shadow.setPersistent();
            if (shadow.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
                shadow.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(cfg.restlessShadowsHealth);
            }
            shadow.setHealth((float) cfg.restlessShadowsHealth);
            if (shadow.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
                shadow.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(cfg.restlessShadowsDamage);
            }
            shadow.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, cfg.restlessShadowsDurationTicks, 0, false, false));
            shadow.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, cfg.restlessShadowsDurationTicks, 2, false, false));
            world.spawnEntity(shadow);
            SummonSystem.register(shadow, player.getUuid(), CAT_SHADOW, cfg.restlessShadowsDurationTicks, lowEnergy);
        }

        CooldownSystem.get(server).set(player.getUuid(), CD_SHADOWS, cfg.restlessShadowsCooldownTicks);
        VfxSystem.shockwave(world, ParticleTypes.SMOKE, ParticleTypes.SOUL, player.getEntityPos().add(0, 0.5, 0), 6.0, 32);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1.5f, 0.6f);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null || server.getTicks() % 20 != 0) return;
        PowerConfigs.SpiritChanneler cfg = YPConfig.get().powers.spiritChanneler;
        for (MobEntity shadow : SummonSystem.getOwnedMobs(server, player.getUuid(), CAT_SHADOW)) {
            if (!(shadow.getEntityWorld() instanceof ServerWorld world)) continue;
            VfxSystem.burst(world, ParticleTypes.SMOKE, shadow.getEntityPos().add(0, 1.0, 0), 6, 0.35, 0.02);
            VfxSystem.playSound(world, shadow.getEntityPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 0.8f, 0.8f);
            if (cfg.restlessShadowsDarknessRadius > 0.0) {
                Box box = shadow.getBoundingBox().expand(cfg.restlessShadowsDarknessRadius);
                for (ServerPlayerEntity sp : world.getEntitiesByClass(ServerPlayerEntity.class, box,
                        p -> SummonSystem.isRogue(shadow) || CombatUtil.canHarm(player, p))) {
                    sp.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, cfg.restlessShadowsDarknessTicks, 0, false, false));
                }
            }
        }
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) {
            SummonSystem.discardCategory(server, player.getUuid(), CAT_SPIRIT);
            SummonSystem.discardCategory(server, player.getUuid(), CAT_SHADOW);
        }
    }

    private Set<String> getUnlocks(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return Set.of();
        NbtCompound nbt = PlayerDataStore.get(server).getPowerNbt(player.getUuid(), PowerId.SPIRIT_CHANNELER);
        NbtList list = nbt.getListOrEmpty(NBT_UNLOCKS);
        Set<String> set = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String s = list.getString(i, "");
            if (!s.isBlank()) set.add(s);
        }
        return set;
    }

    private void addUnlock(ServerPlayerEntity player, String form) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        PlayerDataStore store = PlayerDataStore.get(server);
        NbtCompound nbt = store.getPowerNbt(player.getUuid(), PowerId.SPIRIT_CHANNELER);
        NbtList list = nbt.getListOrEmpty(NBT_UNLOCKS);
        for (int i = 0; i < list.size(); i++) {
            if (form.equals(list.getString(i, ""))) return;
        }
        list.add(NbtString.of(form));
        nbt.put(NBT_UNLOCKS, list);
        store.markDirty();
    }

    private String getSelected(ServerPlayerEntity player, Set<String> unlocked) {
        String cur = selectedForm.get(player.getUuid());
        if (cur != null && unlocked.contains(cur)) return cur;
        for (String f : ALL_FORMS) {
            if (unlocked.contains(f)) {
                selectedForm.put(player.getUuid(), f);
                return f;
            }
        }
        return "";
    }

    private static boolean consumeOne(ServerPlayerEntity player, Item item) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(item)) {
                s.decrement(1);
                return true;
            }
        }
        return false;
    }
}
