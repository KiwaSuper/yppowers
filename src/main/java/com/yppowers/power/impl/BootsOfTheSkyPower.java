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
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
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
    private final Map<UUID, Integer> flightEnergyDrainTicks = new HashMap<>();
    private final Map<UUID, Boolean> flightActive = new HashMap<>();
    private final Map<UUID, RaigoState> raigoCharges = new HashMap<>();

    private static final class RaigoState {
        final UUID owner;
        final ServerWorld world;
        final Vec3d target;
        final Vec3d orbStart;
        final Vec3d cloudCenter;
        int ticks;
        boolean resolved;
        final java.util.List<BlockPos> terrain = new java.util.ArrayList<>();
        int terrainCursor;
        RaigoState(UUID owner, ServerWorld world, Vec3d target, Vec3d orbStart) {
            this.owner = owner;
            this.world = world;
            this.target = target;
            this.orbStart = orbStart;
            this.cloudCenter = new Vec3d(target.x, Math.max(target.y + 16.0, orbStart.y + 24.0), target.z);
        }
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
        applyFeatherFallingState(player);
        flightTicksRemaining.putIfAbsent(player.getUuid(), YPConfig.getInstance().boots_flight_duration_ticks);
    }

    @Override
    public void onPlayerJoin(ServerPlayerEntity player) {
        giveBoots(player);
        applyFeatherFallingState(player);
        flightTicksRemaining.putIfAbsent(player.getUuid(), YPConfig.getInstance().boots_flight_duration_ticks);
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        raigoCharges.remove(player.getUuid());
        flightEnergyDrainTicks.put(player.getUuid(), 0);
        flightActive.put(player.getUuid(), false);
        player.getAbilities().allowFlying = false;
        player.getAbilities().flying = false;
        player.sendAbilitiesUpdate();
        giveBoots(player);
        applyFeatherFallingState(player);
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
            player.giveOrDropStack(boots);
        }
        applyFeatherFallingState(player);
    }

    /** Changes only Feather Falling on the YP-owned boots; unrelated enchantments/items remain intact. */
    private void applyFeatherFallingState(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null) return;
        boolean enabled = PowerManager.get(player.getEntityWorld().getServer())
                .isBootsSkyFeatherFallingEnabled(player.getUuid());
        var feather = player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.FEATHER_FALLING);
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isOf(Items.NETHERITE_BOOTS) || !YPItemData.hasFlag(stack, "YPSkyBoots")) continue;
            EnchantmentHelper.apply(stack, builder -> {
                builder.remove(entry -> entry.equals(feather));
                if (enabled) builder.add(feather, 4);
            });
        }
        player.currentScreenHandler.sendContentUpdates();
    }

    /** Server-authoritative free toggle for the marked Boots of the Sky only. */
    public void toggleFeatherFalling(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null) return;
        boolean enabled = PowerManager.get(player.getEntityWorld().getServer())
                .toggleBootsSkyFeatherFalling(player);
        applyFeatherFallingState(player);
        player.sendMessage(Text.literal("Boots of the Sky Feather Falling " + (enabled ? "enabled." : "disabled."))
                .formatted(enabled ? Formatting.GREEN : Formatting.YELLOW), true);
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
        RaigoState charge = new RaigoState(id, world, target, player.getEyePos());
        raigoCharges.put(id, charge);
        EnergyManager.get(player.getEntityWorld().getServer()).startRaigoCooldown(player);
        player.sendMessage(Text.literal("RAIGO — lightning gathers above the target!").formatted(Formatting.GOLD, Formatting.BOLD), true);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5f, 0.55f);
    }

    public boolean isRaigoCharging(UUID id) { return raigoCharges.containsKey(id); }

    public boolean isFlightActive(UUID id) { return flightActive.getOrDefault(id, false); }

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
                renderRaigoCharge(state, chargeTicks, config);
                double progress = Math.min(1.0, state.ticks / (double) chargeTicks);
                if (state.ticks % 10 == 0) state.world.playSound(null, BlockPos.ofFloored(state.target.x, state.target.y, state.target.z),
                        SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.9f, (float) (0.5 + progress));
                if (state.ticks >= chargeTicks) resolveRaigo(state, owner, config);
            }
            if (state.resolved && config.boots_raigo_terrain_damage) processRaigoTerrain(state, owner, config);
            if (state.resolved && (!config.boots_raigo_terrain_damage || state.terrainCursor >= state.terrain.size())) iterator.remove();
        }
    }

    /** Particle-only Raigo wind-up: a white orb rises, becomes a thunderhead, then rains visible lightning. */
    private void renderRaigoCharge(RaigoState state, int chargeTicks, YPConfig config) {
        int orbTravelTicks = Math.max(1, Math.min(20, chargeTicks / 3));
        int rainStart = Math.max(orbTravelTicks + 1, chargeTicks - Math.min(7, Math.max(1, chargeTicks / 4)));
        if (state.ticks <= orbTravelTicks) {
            double raw = Math.min(1.0, state.ticks / (double) orbTravelTicks);
            double eased = raw * raw * (3.0 - 2.0 * raw);
            Vec3d orb = state.orbStart.lerp(state.cloudCenter, eased);
            if (state.ticks == 1) {
                state.world.spawnParticles(TintedParticleEffect.create(ParticleTypes.FLASH, 0xFFFFFF),
                        orb.x, orb.y, orb.z, 1, 0, 0, 0, 0);
                state.world.spawnParticles(ParticleTypes.END_ROD, orb.x, orb.y, orb.z, 24, 0.16, 0.16, 0.16, 0.015);
            }
            state.world.spawnParticles(ParticleTypes.END_ROD, orb.x, orb.y, orb.z, 8, 0.08, 0.08, 0.08, 0.015);
            state.world.spawnParticles(ParticleTypes.CLOUD, orb.x, orb.y, orb.z, 3, 0.10, 0.10, 0.10, 0.008);
            state.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, orb.x, orb.y, orb.z, 2, 0.12, 0.12, 0.12, 0.02);
            return;
        }

        double cloudProgress = Math.min(1.0, (state.ticks - orbTravelTicks) / (double) Math.max(1, chargeTicks - orbTravelTicks));
        double maxCloudRadius = Math.max(5.0, Math.min(12.0, config.boots_raigo_radius * 0.55));
        double cloudRadius = 1.5 + (maxCloudRadius - 1.5) * Math.sqrt(cloudProgress);
        if (state.ticks == orbTravelTicks + 1) {
            state.world.spawnParticles(TintedParticleEffect.create(ParticleTypes.FLASH, 0xFFFFFF),
                    state.cloudCenter.x, state.cloudCenter.y, state.cloudCenter.z, 1, 0, 0, 0, 0);
            state.world.spawnParticles(ParticleTypes.END_ROD, state.cloudCenter.x, state.cloudCenter.y, state.cloudCenter.z,
                    28, 0.6, 0.45, 0.6, 0.06);
        }
        if ((state.ticks & 1) == 0) {
            int cloudCount = 14 + (int) (cloudProgress * 14);
            int smokeCount = 6 + (int) (cloudProgress * 8);
            int sparkCount = 8 + (int) (cloudProgress * 10);
            state.world.spawnParticles(ParticleTypes.CLOUD,
                    state.cloudCenter.x, state.cloudCenter.y, state.cloudCenter.z, cloudCount,
                    cloudRadius, 1.0 + cloudProgress * 1.6, cloudRadius, 0.012);
            state.world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    state.cloudCenter.x, state.cloudCenter.y, state.cloudCenter.z, smokeCount,
                    cloudRadius * 0.72, 0.65 + cloudProgress, cloudRadius * 0.72, 0.004);
            state.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    state.cloudCenter.x, state.cloudCenter.y, state.cloudCenter.z, sparkCount,
                    cloudRadius * 0.85, cloudRadius * 0.35, cloudRadius * 0.85, 0.035);
            state.world.spawnParticles(ParticleTypes.END_ROD,
                    state.cloudCenter.x, state.cloudCenter.y, state.cloudCenter.z, 4,
                    cloudRadius * 0.6, 0.8, cloudRadius * 0.6, 0.01);
        }

        // Use real LightningEntity renderers for the storm's visible bolts. Cosmetic mode keeps
        // this wind-up visual-only; the existing single Raigo impact still owns damage/terrain logic.
        if (state.ticks >= rainStart && (state.ticks & 1) == 0) {
            double spread = Math.min(8.0, Math.max(2.0, config.boots_raigo_radius * 0.4));
            int bolts = 2 + (state.ticks % 4 == 0 ? 1 : 0);
            for (int bolt = 0; bolt < bolts; bolt++) {
                double phase = state.ticks * 1.73 + bolt * 2.17;
                double offsetX = Math.sin(phase) * spread;
                double offsetZ = Math.cos(phase * 1.19) * spread;
                spawnRaigoLightning(state.world, state.target.x + offsetX, state.target.y + 0.1,
                        state.target.z + offsetZ, true);
            }
        }
    }

    private void spawnRaigoLightning(ServerWorld world, double x, double y, double z, boolean cosmetic) {
        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
        if (lightning == null) return;
        lightning.setCosmetic(false);
        lightning.refreshPositionAfterTeleport(x, y, z);
        world.spawnEntity(lightning);
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
        // Actual LightningEntity strikes: these are real world lightning, not cosmetic-only bolts.
        spawnRaigoLightning(state.world, state.target.x, state.target.y, state.target.z, true);
        for (int bolt = 0; bolt < 6; bolt++) {
            double phase = bolt * Math.PI / 3.0;
            double offset = Math.min(7.0, radius * 0.45);
            spawnRaigoLightning(state.world, state.target.x + Math.cos(phase) * offset,
                    state.target.y + 0.1, state.target.z + Math.sin(phase) * offset, true);
        }
        state.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, state.target.x, state.target.y, state.target.z,
                40, radius * 0.40, radius * 0.40, radius * 0.40, 0.16);
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
            int drainTicks = flightEnergyDrainTicks.getOrDefault(pId, 0) + 1;
            if (drainTicks >= 20) {
                drainTicks -= 20;
                MinecraftServer server = player.getEntityWorld().getServer();
                double upkeep = YPConfig.getInstance().boots_flight_energy_drain_per_second;
                if (server == null || !EnergyManager.get(server).trySpendFlightEnergy(player, upkeep)) {
                    flightActive.put(pId, false);
                    flightEnergyDrainTicks.put(pId, 0);
                    player.getAbilities().allowFlying = false;
                    player.getAbilities().flying = false;
                    player.sendAbilitiesUpdate();
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false));
                    player.sendMessage(Text.literal("Flight stopped: not enough Energy."), true);
                    return;
                }
            }
            flightEnergyDrainTicks.put(pId, drainTicks);
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
                flightEnergyDrainTicks.put(pId, 0);
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
        flightEnergyDrainTicks.remove(player.getUuid());
        player.getAbilities().allowFlying = false;
        player.getAbilities().flying = false;
        player.sendAbilitiesUpdate();
    }
}
