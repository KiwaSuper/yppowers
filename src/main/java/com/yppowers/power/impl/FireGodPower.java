package com.yppowers.power.impl;

import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.item.YPItemData;
import com.yppowers.power.PowerType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FireGodPower extends Power {
    private final Map<UUID, Integer> ghastCooldowns = new HashMap<>();

    @Override
    public PowerType getType() {
        return PowerType.FIRE_GOD;
    }

    @Override
    public void tick(ServerPlayerEntity player) {
        if (player.isOnFire()) {
            player.extinguish();
        }
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, false, false));
        // MobEntityMixin blocks fresh target assignments and clears stale ones before each mob's
        // AI tick. Avoid a per-player world-wide entity scan here to keep this passive inexpensive.

        UUID pId = player.getUuid();
        if (ghastCooldowns.getOrDefault(pId, 0) > 0) {
            ghastCooldowns.put(pId, ghastCooldowns.get(pId) - 1);
        }
    }

    @Override
    public boolean isFireImmune(ServerPlayerEntity player) {
        return true;
    }

    @Override
    public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (source.isOf(DamageTypes.IN_FIRE) || source.isOf(DamageTypes.ON_FIRE) || source.isOf(DamageTypes.LAVA) || source.isOf(DamageTypes.HOT_FLOOR)) {
            return 0;
        }
        return amount;
    }

    @Override
    public boolean shouldMobIgnore(ServerPlayerEntity player, MobEntity mob) {
        // These Nether-native hostiles should ignore Fire God even if they encounter the player
        // outside the Nether. Include variants that do not inherit from PiglinEntity.
        return mob instanceof BlazeEntity
                || mob instanceof GhastEntity
                || mob instanceof PiglinEntity
                || mob instanceof PiglinBruteEntity
                || mob instanceof ZombifiedPiglinEntity
                || mob instanceof MagmaCubeEntity
                || mob instanceof WitherSkeletonEntity
                || mob instanceof HoglinEntity
                || mob instanceof ZoglinEntity
                // Skeletons naturally spawn in soul-sand valleys and Endermen also spawn in Nether biomes.
                || mob instanceof SkeletonEntity
                || mob instanceof EndermanEntity;
    }

    @Override
    public void onPrimaryActivate(ServerPlayerEntity player) {
        UUID pId = player.getUuid();
        if (ghastCooldowns.getOrDefault(pId, 0) <= 0) {
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            Vec3d look = player.getRotationVec(1.0F);
            FireballEntity fireball = new FireballEntity(world, player, new Vec3d(look.x * 0.5, look.y * 0.5, look.z * 0.5), 1);
            fireball.setPosition(player.getX(), player.getEyeY(), player.getZ());
            world.spawnEntity(fireball);
            
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
            world.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getEyeY(), player.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
            
            ghastCooldowns.put(pId, Math.max(0, YPConfig.getInstance().fire_god_cannon_cooldown_ticks));
        } else {
            player.sendMessage(Text.literal("Ghast Cannon on cooldown: " + (ghastCooldowns.get(pId) / 20) + "s"), true);
        }
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        giveRod(player);
    }

    @Override
    public void onPlayerJoin(ServerPlayerEntity player) {
        giveRod(player);
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        giveRod(player);
    }

    private void giveRod(ServerPlayerEntity player) {
        boolean hasRod = false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.BLAZE_ROD) && YPItemData.hasFlag(stack, "YPSoulbound")) {
                hasRod = true;
                break;
            }
        }
        if (!hasRod) {
            ItemStack rod = new ItemStack(Items.BLAZE_ROD);
            rod.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Ember of the Undying Flame").formatted(Formatting.ITALIC, Formatting.GOLD));
            YPItemData.setFlag(rod, "YPSoulbound", true);
            player.giveOrDropStack(rod);
        }
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return stack.isOf(Items.BLAZE_ROD) && YPItemData.hasFlag(stack, "YPSoulbound");
    }
}
