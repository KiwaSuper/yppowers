package com.yppowers.power.firegod;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.item.ItemMarkerUtil;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.SoulboundItemSystem;
import com.yppowers.system.SummonSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.HoglinEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.ZoglinEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Fire God: full fire/lava immunity, clear lava vision (client), Nether mob truce,
 * infinite-fuel soulbound Blaze Rod, Ghast Cannon (G), Ember Guard Blaze (H), and Cinder Sentinel Wither Skeleton (V).
 */
public final class FireGodPower extends Power {
    private static final String CD_CANNON = "fire_god_cannon";
    private static final String CD_BLAZE = "fire_god_blaze";
    private static final String CD_WITHER = "fire_god_wither";
    private static final String EMBER_TAG = "fire_god_ember";
    private static final String CAT_BLAZE = "fire_god_blaze";
    private static final String CAT_WITHER = "fire_god_wither";

    @Override
    public PowerId id() {
        return PowerId.FIRE_GOD;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of(
                AbilitySpec.active(AbilitySlot.PRIMARY, "Ghast Cannon", CD_CANNON),
                AbilitySpec.active(AbilitySlot.SECONDARY, "Ember Guard", CD_BLAZE),
                AbilitySpec.active(AbilitySlot.TERTIARY, "Cinder Sentinel", CD_WITHER)
        );
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        return "Fire Immune";
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY || slot == AbilitySlot.SECONDARY || slot == AbilitySlot.TERTIARY;
    }

    @Override
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        PowerConfigs.FireGod cfg = YPConfig.get().powers.fireGod;
        return switch (slot) {
            case SECONDARY -> cfg.blazeEnergyCost;
            case TERTIARY -> cfg.witherSkeletonEnergyCost;
            default -> super.energyCost(player, slot);
        };
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        CooldownSystem cds = CooldownSystem.get(server);
        PowerConfigs.FireGod cfg = YPConfig.get().powers.fireGod;

        return switch (slot) {
            case PRIMARY -> cds.checkReady(player, CD_CANNON, "Ghast Cannon");
            case SECONDARY -> {
                if (!cds.checkReady(player, CD_BLAZE, "Ember Guard")) yield false;
                if (SummonSystem.countActive(server, player.getUuid(), CAT_BLAZE) >= cfg.blazeMaxActive) {
                    player.sendMessage(Text.literal("Active Ember Guard limit reached.").formatted(Formatting.YELLOW), true);
                    yield false;
                }
                yield true;
            }
            case TERTIARY -> {
                if (!cds.checkReady(player, CD_WITHER, "Cinder Sentinel")) yield false;
                if (SummonSystem.countActive(server, player.getUuid(), CAT_WITHER) >= cfg.witherSkeletonMaxActive) {
                    player.sendMessage(Text.literal("Active Cinder Sentinel limit reached.").formatted(Formatting.YELLOW), true);
                    yield false;
                }
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        switch (slot) {
            case PRIMARY -> fireGhastCannon(player);
            case SECONDARY -> summonMinion(player, true);
            case TERTIARY -> summonMinion(player, false);
            default -> {}
        }
    }

    private void fireGhastCannon(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        Vec3d look = CombatUtil.safeLook(player);
        FireballEntity fireball = new FireballEntity(world, player, look.multiply(0.1), 2);
        fireball.setPosition(player.getX() + look.x * 1.5, player.getEyeY() - 0.1 + look.y * 1.5, player.getZ() + look.z * 1.5);
        fireball.setVelocity(look.multiply(1.8));
        world.spawnEntity(fireball);

        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_CANNON, YPConfig.get().powers.fireGod.cannonCooldownTicks);
        VfxSystem.burst(world, ParticleTypes.FLAME, player.getEyePos().add(look.multiply(1.2)), 16, 0.35, 0.08);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private void summonMinion(ServerPlayerEntity player, boolean isBlaze) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.FireGod cfg = YPConfig.get().powers.fireGod;
        MobEntity mob = isBlaze
                ? EntityType.BLAZE.create(world, SpawnReason.TRIGGERED)
                : EntityType.WITHER_SKELETON.create(world, SpawnReason.TRIGGERED);
        if (mob == null) return;

        String name = isBlaze ? "Ember Guard" : "Cinder Sentinel";
        double hp = isBlaze ? cfg.blazeMaxHealth : cfg.witherSkeletonMaxHealth;
        double dmg = isBlaze ? cfg.blazeAttackDamage : cfg.witherSkeletonAttackDamage;
        int life = isBlaze ? cfg.blazeLifespanTicks : cfg.witherSkeletonLifespanTicks;

        mob.refreshPositionAndAngles(player.getX() + 1.2, player.getY() + 0.4, player.getZ() + 1.2, player.getYaw(), 0.0f);
        mob.setCustomName(Text.literal(name + " · " + player.getName().getString())
                .formatted(isBlaze ? Formatting.GOLD : Formatting.DARK_GRAY));
        mob.setCustomNameVisible(true);
        mob.setPersistent();
        if (mob.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            mob.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(hp);
        }
        mob.setHealth((float) hp);
        if (mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(dmg);
        }
        world.spawnEntity(mob);

        SummonSystem.register(mob, player.getUuid(), isBlaze ? CAT_BLAZE : CAT_WITHER, life, false);
        CooldownSystem.get(world.getServer()).set(player.getUuid(),
                isBlaze ? CD_BLAZE : CD_WITHER,
                isBlaze ? cfg.blazeCooldownTicks : cfg.witherSkeletonCooldownTicks);

        VfxSystem.risingHelix(world, isBlaze ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME, mob.getEntityPos(), 1.1, 2.4, 18);
        VfxSystem.playSound(world, mob.getEntityPos(),
                isBlaze ? SoundEvents.ENTITY_BLAZE_AMBIENT : SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT,
                SoundCategory.PLAYERS, 0.9f, isBlaze ? 1.2f : 0.85f);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        if (player.isOnFire()) player.extinguish();
        StatusEffectInstance fireRes = player.getStatusEffect(StatusEffects.FIRE_RESISTANCE);
        if (fireRes == null || (fireRes.getAmplifier() == 0 && fireRes.getDuration() <= 10)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, false, false));
        }
    }

    @Override
    public boolean isFireImmune(ServerPlayerEntity player) {
        return true;
    }

    @Override
    public boolean shouldMobIgnore(ServerPlayerEntity player, MobEntity mob) {
        return mob instanceof BlazeEntity
                || mob instanceof GhastEntity
                || mob instanceof MagmaCubeEntity
                || mob instanceof WitherSkeletonEntity
                || mob instanceof AbstractPiglinEntity
                || mob instanceof ZombifiedPiglinEntity
                || mob instanceof HoglinEntity
                || mob instanceof ZoglinEntity;
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        ensureEmber(player);
    }

    @Override
    public void onJoin(ServerPlayerEntity player) {
        ensureEmber(player);
    }

    @Override
    public void onRespawn(ServerPlayerEntity newPlayer, ServerPlayerEntity oldPlayer) {
        ensureEmber(newPlayer);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) {
            SummonSystem.discardCategory(server, player.getUuid(), CAT_BLAZE);
            SummonSystem.discardCategory(server, player.getUuid(), CAT_WITHER);
        }
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return SoulboundItemSystem.isSoulbound(stack, PowerId.FIRE_GOD, EMBER_TAG);
    }

    private static void ensureEmber(ServerPlayerEntity player) {
        SoulboundItemSystem.ensureEquippedOrPresent(player, PowerId.FIRE_GOD, EMBER_TAG, () -> {
            ItemStack rod = new ItemStack(Items.BLAZE_ROD);
            rod.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Ember of the Undying Flame").formatted(Formatting.GOLD, Formatting.BOLD));
            ItemMarkerUtil.setFlag(rod, "YPInfiniteFuel", true);
            return rod;
        });
    }
}
