package com.yppowers.power.impl;

import com.yppowers.config.YPConfig;
import com.yppowers.item.YPItemData;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import com.yppowers.power.PowerManager;
import com.yppowers.alliance.AllianceManager;
import com.yppowers.energy.EnergyManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BootsOfTheSkyPower extends Power {
    private final Map<UUID, Integer> flightTicksRemaining = new HashMap<>();
    private final Map<UUID, Integer> rechargeTicks = new HashMap<>();
    private final Map<UUID, Boolean> flightActive = new HashMap<>();
    private final Map<UUID, RaigoState> raigoCharges = new HashMap<>();

    private static final class RaigoState {
        final UUID owner;
        final ServerWorld world;
        final Vec3d target;
        int ticks;
        boolean resolved;
        final java.util.List<BlockPos> terrain = new java.util.ArrayList<>();
        int terrainCursor;
        RaigoState(UUID owner, ServerWorld world, Vec3d target) { this.owner = owner; this.world = world; this.target = target; }
    }

    public static void serverTick(MinecraftServer server) {
        Power power = PowerManager.get(server).getPowerInstance(PowerType.BOOTS_SKY);
        if (power instanceof BootsOfTheSkyPower boots) boots.tickRaigo(server);
    }

    @Override
    public PowerType getType() {
        return PowerType.BOOTS_SKY;
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        giveBoots(player);
        flightTicksRemaining.putIfAbsent(player.getUuid(), YPConfig.getInstance().boots_flight_duration_ticks);
    }

    @Override
    public void onPlayerJoin(ServerPlayerEntity player) {
        giveBoots(player);
        flightTicksRemaining.putIfAbsent(player.getUuid(), YPConfig.getInstance().boots_flight_duration_ticks);
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        raigoCharges.remove(player.getUuid());
        flightActive.put(player.getUuid(), false);
        player.getAbilities().allowFlying = false;
        player.getAbilities().flying = false;
        player.sendAbilitiesUpdate();
        giveBoots(player);
    }

    private void giveBoots(ServerPlayerEntity player) {
        boolean hasBoots = false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.NETHERITE_BOOTS) && YPItemData.hasFlag(stack, "YPSkyBoots")) {
                hasBoots = true;
                break;
            }
        }
        if (!hasBoots) {
            ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);
            YPItemData.setFlag(boots, "YPSkyBoots", true);
            boots.set(net.minecraft.component.DataComponentTypes.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
            EnchantmentHelper.apply(boots, builder -> builder.add(
                    player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.FEATHER_FALLING), 4));
            player.giveOrDropStack(boots);
        }
    }

    private boolean hasSkyBootsEquipped(ServerPlayerEntity player) {
        ItemStack feet = player.getEquippedStack(EquipmentSlot.FEET);
        return feet.isOf(Items.NETHERITE_BOOTS) && YPItemData.hasFlag(feet, "YPSkyBoots");
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return stack.isOf(Items.NETHERITE_BOOTS) && YPItemData.hasFlag(stack, "YPSkyBoots");
    }

    @Override
    public void onPrimaryActivate(ServerPlayerEntity player) {
        UUID pId = player.getUuid();
        if (rechargeTicks.getOrDefault(pId, 0) > 0) {
            player.sendMessage(Text.literal("Flight recharging! " + (rechargeTicks.get(pId) / 20) + " seconds remaining."), true);
            return;
        }

        boolean active = !flightActive.getOrDefault(pId, false);
        if (active && !hasSkyBootsEquipped(player)) {
            player.sendMessage(Text.literal("Equip the Boots of the Sky to fly."), true);
            return;
        }
        flightActive.put(pId, active);
        if (active) {
            player.getAbilities().allowFlying = true;
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
        } else {
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false));
        }
    }

    @Override
    public void onAlternateActivate(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null || !(player.getEntityWorld() instanceof ServerWorld world)) return;
        UUID id = player.getUuid();
        if (raigoCharges.containsKey(id)) {
            player.sendMessage(Text.literal("Raigo is already charging.").formatted(Formatting.YELLOW), true);
            return;
        }
        YPConfig config = YPConfig.getInstance();
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d end = eye.add(look.multiply(Math.max(1.0, config.boots_raigo_range)));
        var hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, player));
        Vec3d target = hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? end : hit.getPos();
        RaigoState charge = new RaigoState(id, world, target);
        raigoCharges.put(id, charge);
        EnergyManager.get(player.getEntityWorld().getServer()).startRaigoCooldown(player);
        player.sendMessage(Text.literal("RAIGO — lightning gathers above the target!").formatted(Formatting.GOLD, Formatting.BOLD), true);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5f, 0.55f);
    }

    public boolean isRaigoCharging(UUID id) { return raigoCharges.containsKey(id); }

    private void tickRaigo(MinecraftServer server) {
        if (raigoCharges.isEmpty()) return;
        var iterator = raigoCharges.entrySet().iterator();
        YPConfig config = YPConfig.getInstance();
        while (iterator.hasNext()) {
            RaigoState state = iterator.next().getValue();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(state.owner);
            if (owner == null || !owner.isAlive() || !owner.getEntityWorld().getRegistryKey().equals(state.world.getRegistryKey())
                    || PowerManager.get(server).getPowerType(owner.getUuid()) != PowerType.BOOTS_SKY
                    || com.yppowers.command.GraceManager.isActive(server)) {
                iterator.remove();
                continue;
            }
            if (!state.resolved) {
                state.ticks++;
                int chargeTicks = Math.max(1, config.boots_raigo_charge_ticks);
                double progress = Math.min(1.0, state.ticks / (double) chargeTicks);
                double sphere = 0.35 + progress * 2.5;
                state.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, state.target.x, state.target.y, state.target.z,
                        16 + (int) (progress * 60), sphere, sphere, sphere, 0.15 + progress * 0.15);
                state.world.spawnParticles(ParticleTypes.END_ROD, state.target.x, state.target.y, state.target.z,
                        3 + (int) (progress * 12), sphere * 0.5, sphere * 0.5, sphere * 0.5, 0.04);
                // Repeated target telegraph from the caster to the locked impact point.
                Vec3d line = state.target.subtract(owner.getEyePos());
                int dots = Math.min(32, Math.max(4, (int) (line.length() / 2.5)));
                for (int i = 0; i <= dots; i += 2) {
                    Vec3d dot = owner.getEyePos().add(line.multiply(i / (double) dots));
                    state.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, dot.x, dot.y, dot.z, 1, 0.03, 0.03, 0.03, 0.01);
                }
                if (state.ticks % 10 == 0) state.world.playSound(null, BlockPos.ofFloored(state.target.x, state.target.y, state.target.z),
                        SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.9f, (float) (0.5 + progress));
                if (state.ticks >= chargeTicks) resolveRaigo(state, owner, config);
            }
            if (state.resolved && config.boots_raigo_terrain_damage) processRaigoTerrain(state, owner, config);
            if (state.resolved && (!config.boots_raigo_terrain_damage || state.terrainCursor >= state.terrain.size())) iterator.remove();
        }
    }

    private void resolveRaigo(RaigoState state, ServerPlayerEntity owner, YPConfig config) {
        state.resolved = true;
        double radius = Math.max(1.0, config.boots_raigo_radius);
        Box area = new Box(state.target.x-radius, state.target.y-radius, state.target.z-radius,
                state.target.x+radius, state.target.y+radius, state.target.z+radius);
        float damage = (float) Math.max(0.0, Math.min(16.0, config.boots_raigo_max_damage));
        for (LivingEntity target : state.world.getEntitiesByClass(LivingEntity.class, area, e -> e != owner && e.isAlive() && !e.isRemoved())) {
            if (target.squaredDistanceTo(state.target) > radius * radius) continue;
            if (target instanceof ServerPlayerEntity other && AllianceManager.get(state.world.getServer()).isAlly(owner, other)) continue;
            if (damage > 0.0f) target.damage(state.world, state.world.getDamageSources().lightningBolt(), damage);
            Vec3d away = target.getEntityPos().subtract(state.target);
            if (away.lengthSquared() > 1.0e-5) target.setVelocity(away.normalize().multiply(0.9).add(0, 0.3, 0));
        }
        state.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, state.target.x, state.target.y, state.target.z,
                240, radius * 0.55, radius * 0.55, radius * 0.55, 0.28);
        state.world.spawnParticles(TintedParticleEffect.create(ParticleTypes.FLASH, 0xFFFFFF), state.target.x, state.target.y, state.target.z, 1, 0, 0, 0, 0);
        state.world.spawnParticles(ParticleTypes.EXPLOSION, state.target.x, state.target.y, state.target.z, 8, radius * 0.25, radius * 0.25, radius * 0.25, 0);
        state.world.playSound(null, BlockPos.ofFloored(state.target.x, state.target.y, state.target.z),
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 5.0f, 0.65f);
        state.world.playSound(null, BlockPos.ofFloored(state.target.x, state.target.y, state.target.z),
                SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.WEATHER, 4.0f, 0.8f);
        if (config.boots_raigo_terrain_damage && config.boots_raigo_max_blocks_per_tick > 0) {
            int r = (int) Math.ceil(radius);
            BlockPos origin = BlockPos.ofFloored(state.target.x, state.target.y, state.target.z);
            double r2 = radius * radius;
            for (int x=-r;x<=r;x++) for (int y=-r;y<=r;y++) for (int z=-r;z<=r;z++) {
                if ((double)x*x+(double)y*y+(double)z*z <= r2) state.terrain.add(origin.add(x,y,z).toImmutable());
            }
        }
        owner.sendMessage(Text.literal("RAIGO! The sky answers.").formatted(Formatting.GOLD), true);
    }

    private void processRaigoTerrain(RaigoState state, ServerPlayerEntity owner, YPConfig config) {
        int processed = 0;
        int max = Math.max(0, Math.min(512, config.boots_raigo_max_blocks_per_tick));
        while (processed < max && state.terrainCursor < state.terrain.size()) {
            BlockPos pos = state.terrain.get(state.terrainCursor++);
            processed++;
            if (!state.world.isChunkLoaded(ChunkPos.toLong(pos))) continue;
            BlockState block = state.world.getBlockState(pos);
            if (block.isAir() || block.isOf(Blocks.BEDROCK) || block.isOf(Blocks.HEAVY_CORE) || block.isOf(Blocks.END_PORTAL_FRAME)
                    || block.getHardness(state.world, pos) < 0) continue;
            BlockEntity entity = state.world.getBlockEntity(pos);
            if (entity != null) continue;
            state.world.breakBlock(pos, true, owner);
        }
    }

    @Override
    public void tick(ServerPlayerEntity player) {
        UUID pId = player.getUuid();
        if (flightActive.getOrDefault(pId, false)) {
            // Equipping these marked boots is required while using the power.
            if (!hasSkyBootsEquipped(player)) {
                flightActive.put(pId, false);
                player.getAbilities().allowFlying = false;
                player.getAbilities().flying = false;
                player.sendAbilitiesUpdate();
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false));
                player.sendMessage(Text.literal("Flight deactivated: Boots of the Sky are not equipped."), true);
            } else if (!player.getAbilities().flying) {
                // Vanilla's double-tap jump disables abilities.flying. Treat that transition as toggle-off.
                flightActive.put(pId, false);
                player.getAbilities().allowFlying = false;
                player.sendAbilitiesUpdate();
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false));
                player.sendMessage(Text.literal("Flight deactivated."), true);
            }
        }

        if (flightActive.getOrDefault(pId, false)) {
            int ticks = flightTicksRemaining.getOrDefault(pId, YPConfig.getInstance().boots_flight_duration_ticks);
            ticks--;
            flightTicksRemaining.put(pId, ticks);

            if (ticks % 200 == 0) {
                player.sendMessage(Text.literal("Flight Time Remaining: " + (ticks / 20) + "s"), true);
            }

            ((ServerWorld) player.getEntityWorld()).spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() - 0.2, player.getZ(), 1, 0.2, 0, 0.2, 0);

            if (ticks <= 0) {
                flightActive.put(pId, false);
                player.getAbilities().allowFlying = false;
                player.getAbilities().flying = false;
                player.sendAbilitiesUpdate();
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false));
                rechargeTicks.put(pId, YPConfig.getInstance().boots_recharge_duration_ticks); // 3 minutes by default
                player.sendMessage(Text.literal("Flight depleted! Recharging..."), true);
            }
        }

        if (rechargeTicks.getOrDefault(pId, 0) > 0) {
            int recharge = rechargeTicks.get(pId) - 1;
            rechargeTicks.put(pId, recharge);
            if (recharge <= 0) {
                flightTicksRemaining.put(pId, YPConfig.getInstance().boots_flight_duration_ticks);
                player.sendMessage(Text.literal("Flight recharged!"), true);
                player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        }
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        raigoCharges.remove(player.getUuid());
        if (flightActive.getOrDefault(player.getUuid(), false)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false));
        }
        flightActive.put(player.getUuid(), false);
        flightTicksRemaining.remove(player.getUuid());
        rechargeTicks.remove(player.getUuid());
        player.getAbilities().allowFlying = false;
        player.getAbilities().flying = false;
        player.sendAbilitiesUpdate();
    }
}
