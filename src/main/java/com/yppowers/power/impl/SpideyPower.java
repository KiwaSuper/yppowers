package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import com.yppowers.item.YPItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Spidey traversal/combat implementation using server-authoritative movement and effects. */
public final class SpideyPower extends Power {
    private final Map<UUID, Integer> mlgCooldowns = new HashMap<>();
    private final Map<UUID, Integer> grenadeCooldowns = new HashMap<>();
    private final Map<UUID, Integer> rageCooldowns = new HashMap<>();
    private final Map<UUID, Integer> rageTicks = new HashMap<>();
    private final Map<UUID, Integer> diveCooldowns = new HashMap<>();
    private final Map<UUID, Integer> diveChain = new HashMap<>();
    private final Map<UUID, Integer> diveWindow = new HashMap<>();
    private final Map<UUID, Boolean> mlgEnabled = new HashMap<>();
    private final Map<UUID, Integer> mlgProbeClock = new HashMap<>();
    private final Map<UUID, Integer> webLaunchBoost = new HashMap<>();
    private final Map<UUID, Double> lastY = new HashMap<>();
    private final Map<UUID, List<TempWeb>> tempWebs = new HashMap<>();

    /** Cobweb placed by the automatic MLG; removed shortly after so landing spots are not littered. */
    private static final class TempWeb { final ServerWorld world; final BlockPos pos; int ticks = 80;
        TempWeb(ServerWorld world, BlockPos pos) { this.world = world; this.pos = pos; } }

    @Override public PowerType getType() { return PowerType.SPIDEY; }

    @Override public void onAssign(ServerPlayerEntity player) { mlgEnabled.putIfAbsent(player.getUuid(), true); ensureWebSlingers(player); }
    @Override public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) { ensureWebSlingers(player); }
    @Override public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) { return stack.isOf(YPItems.WEB_SLINGERS); }

    private void ensureWebSlingers(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(YPItems.WEB_SLINGERS)) return;
        }
        ItemStack stack = new ItemStack(YPItems.WEB_SLINGERS);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Web Slingers").formatted(Formatting.AQUA, Formatting.BOLD));
        player.giveOrDropStack(stack);
    }

    public int getDiveChain(UUID owner) { return diveChain.getOrDefault(owner, 0); }

    @Override public void onPrimaryActivate(ServerPlayerEntity player) { webSling(player); }
    @Override public void onSecondaryActivate(ServerPlayerEntity player) { webGrenade(player); }
    @Override public void onTertiaryActivate(ServerPlayerEntity player) { rageMode(player); }
    @Override public void onQuaternaryActivate(ServerPlayerEntity player) { spiderDive(player); }
    @Override public void onAlternateActivate(ServerPlayerEntity player) {
        boolean enabled = !mlgEnabled.getOrDefault(player.getUuid(), true);
        mlgEnabled.put(player.getUuid(), enabled);
        player.sendMessage(Text.literal("Automatic Cobweb MLG " + (enabled ? "enabled" : "disabled"))
                .formatted(enabled ? Formatting.GREEN : Formatting.YELLOW), true);
    }

    public void useWebSling(ServerPlayerEntity player) { webSling(player); }

    private void webSling(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        Vec3d look = PowerCombatUtil.safeLook(player);
        double range = Math.max(2, YPConfig.getInstance().spidey_web_sling_range);
        Vec3d end = player.getEyePos().add(look.multiply(range));
        BlockHitResult hit = world.raycast(new RaycastContext(player.getEyePos(), end,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.sendMessage(Text.literal("No solid anchor in web-sling range."), true); return;
        }
        Vec3d anchor = Vec3d.ofCenter(hit.getBlockPos());
        Vec3d pull = anchor.subtract(player.getEntityPos());
        if (pull.lengthSquared() < 0.01) return;
        double velocity = Math.max(0.1, YPConfig.getInstance().spidey_web_sling_velocity);
        if (rageTicks.getOrDefault(player.getUuid(), 0) > 0) velocity *= Math.max(1.0, YPConfig.getInstance().spidey_rage_web_velocity_multiplier);
        Vec3d towardAnchor = pull.normalize();
        Vec3d tangent = new Vec3d(-towardAnchor.z, 0, towardAnchor.x);
        if (tangent.lengthSquared() > 1.0e-5) tangent = tangent.normalize().multiply(velocity * 0.25);
        Vec3d impulse = towardAnchor.multiply(velocity).add(tangent);
        player.addVelocity(impulse.x, impulse.y + 0.12, impulse.z);
        ((com.yppowers.mixin.EntityVelocityAccessor) (Object) player).invokeYppowersScheduleVelocityUpdate();
        webLaunchBoost.put(player.getUuid(), 40);
        // Visible web line from the player to the anchor point.
        Vec3d lineStart = player.getEyePos().add(0, -0.3, 0);
        Vec3d line = hit.getPos().subtract(lineStart);
        int lineSteps = (int) Math.max(1, Math.min(48, line.length() / 0.7));
        for (int i = 1; i <= lineSteps; i++) {
            Vec3d point = lineStart.add(line.multiply((double) i / lineSteps));
            world.spawnParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        world.spawnParticles(ParticleTypes.WHITE_SMOKE, player.getEyePos().x, player.getEyePos().y, player.getEyePos().z, 12, .15, .15, .15, .02);
        world.spawnParticles(ParticleTypes.CRIT, anchor.x, anchor.y, anchor.z, 12, .2, .2, .2, .02);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, .8f, 1.5f);
    }

    private void webGrenade(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        UUID id = player.getUuid(); int cd = grenadeCooldowns.getOrDefault(id, 0);
        if (cd > 0) { player.sendMessage(Text.literal("Web Grenade cooldown: " + ((cd+19)/20) + "s"), true); return; }
        Vec3d center = player.getEyePos().add(PowerCombatUtil.safeLook(player).multiply(8.0));
        double radius = Math.max(1, YPConfig.getInstance().spidey_web_grenade_radius);
        EpicVfx.shockwave(world, center.x, center.y, center.z, Math.min(12.0, radius));
        EpicVfx.sphereBurst(world, ParticleTypes.CLOUD, center.x, center.y, center.z, 40, 0.3);
        EpicVfx.sphereBurst(world, ParticleTypes.END_ROD, center.x, center.y, center.z, 24, 0.2);
        AllianceManager allies = AllianceManager.get(world.getServer());
        Box area = new Box(center.x-radius, center.y-radius, center.z-radius, center.x+radius, center.y+radius, center.z+radius);
        int duration = Math.max(1, YPConfig.getInstance().spidey_web_grenade_duration_ticks);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
            if (target.squaredDistanceTo(center) > radius*radius) continue;
            if (!PowerCombatUtil.canHarm(player, target)) continue;
            if (target instanceof ServerPlayerEntity p && allies.isAlly(player, p)) continue;
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, 2));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, duration, 0));
            target.damage(world, world.getDamageSources().magic(), (float)Math.max(0, YPConfig.getInstance().spidey_web_grenade_damage));
        }
        for (int i=0;i<45;i++) {
            double x = center.x + (world.random.nextDouble()*2-1)*radius;
            double y = center.y + (world.random.nextDouble()*2-1)*radius*.6;
            double z = center.z + (world.random.nextDouble()*2-1)*radius;
            world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 1, .05, .05, .05, .02);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, x, y, z, 1, .03, .03, .03, 0);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.0f, 1.2f);
        grenadeCooldowns.put(id, Math.max(0, YPConfig.getInstance().spidey_web_grenade_cooldown_ticks));
    }

    private void rageMode(ServerPlayerEntity player) {
        UUID id = player.getUuid(); int cd = rageCooldowns.getOrDefault(id, 0);
        if (rageTicks.getOrDefault(id, 0) > 0) { player.sendMessage(Text.literal("Rage Mode is already active."), true); return; }
        if (cd > 0) { player.sendMessage(Text.literal("Rage Mode cooldown: " + ((cd+19)/20) + "s"), true); return; }
        int duration = Math.max(1, YPConfig.getInstance().spidey_rage_duration_ticks);
        rageTicks.put(id, duration);
        rageCooldowns.put(id, Math.max(0, YPConfig.getInstance().spidey_rage_cooldown_ticks));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, Math.max(0, (int)Math.round(YPConfig.getInstance().spidey_rage_speed_multiplier)-1), false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, duration, 1, false, true));
        player.sendMessage(Text.literal("RAGE MODE — web launch and melee power increased!").formatted(Formatting.RED, Formatting.BOLD), true);
        player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, .8f, 1.4f);
        if (player.getEntityWorld() instanceof ServerWorld rageWorld) {
            EpicVfx.shockwave(rageWorld, player.getX(), player.getY(), player.getZ(), 6.0);
            EpicVfx.activation(rageWorld, player.getX(), player.getY(), player.getZ(), 1.2);
        }
    }

    private void spiderDive(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        UUID id = player.getUuid();
        if (diveCooldowns.getOrDefault(id, 0) > 0) { player.sendMessage(Text.literal("Spider Dive cooldown: " + ((diveCooldowns.get(id)+19)/20) + "s"), true); return; }
        int chain = diveChain.getOrDefault(id, 0);
        if (chain == 0) {
            Vec3d look = PowerCombatUtil.safeLook(player);
            player.setVelocity(-look.x*.45, 1.05, -look.z*.45);
            ((com.yppowers.mixin.EntityVelocityAccessor) (Object) player).invokeYppowersScheduleVelocityUpdate();
            diveChain.put(id, 1);
            diveWindow.put(id, Math.max(1, YPConfig.getInstance().spidey_dive_chain_window_ticks));
            world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 18, .4, .2, .4, .03);
            EpicVfx.activation(world, player.getX(), player.getY(), player.getZ(), 0.8);
            player.sendMessage(Text.literal("Spider Dive: launch! Press B again to dive at a target.").formatted(Formatting.AQUA), true);
            return;
        }
        LivingEntity target = findLookTarget(player, 20.0);
        if (target == null || !PowerCombatUtil.canHarm(player, target)) {
            player.setVelocity(player.getRotationVec(1).multiply(0.8).add(0, .2, 0));
            ((com.yppowers.mixin.EntityVelocityAccessor) (Object) player).invokeYppowersScheduleVelocityUpdate();
            diveChain.remove(id); diveWindow.remove(id);
            diveCooldowns.put(id, Math.max(0, YPConfig.getInstance().spidey_dive_cooldown_ticks));
            player.sendMessage(Text.literal("Spider Dive missed; sequence ended."), true); return;
        }
        int maxChain = Math.max(1, YPConfig.getInstance().spidey_dive_max_chain);
        boolean finisher = chain >= maxChain;
        double damage = finisher ? YPConfig.getInstance().spidey_dive_finisher_damage : YPConfig.getInstance().spidey_dive_hit_damage;
        target.damage(world, world.getDamageSources().playerAttack(player), (float)Math.max(0, damage));
        Vec3d dir = target.getEntityPos().subtract(player.getEntityPos()).normalize();
        target.addVelocity(dir.x*0.6, finisher ? 0.9 : 0.35, dir.z*0.6);
        ((com.yppowers.mixin.EntityVelocityAccessor) (Object) target).invokeYppowersScheduleVelocityUpdate();
        world.spawnParticles(finisher ? ParticleTypes.EXPLOSION : ParticleTypes.CRIT, target.getX(), target.getBodyY(.5), target.getZ(), finisher ? 2 : 10, .3,.2,.3,.02);
        if (finisher) EpicVfx.impact(world, target.getX(), target.getBodyY(.5), target.getZ(), 1.0);
        if (finisher) {
            diveChain.remove(id); diveWindow.remove(id);
            diveCooldowns.put(id, Math.max(0, YPConfig.getInstance().spidey_dive_cooldown_ticks));
        } else {
            diveChain.put(id, chain+1);
            diveWindow.put(id, Math.max(1, YPConfig.getInstance().spidey_dive_chain_window_ticks));
            player.setVelocity(0, 0.9, 0); ((com.yppowers.mixin.EntityVelocityAccessor) (Object) player).invokeYppowersScheduleVelocityUpdate();
        }
    }

    private LivingEntity findLookTarget(ServerPlayerEntity player, double maxRange) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return null;
        Vec3d start = player.getEyePos(); Vec3d direction = PowerCombatUtil.safeLook(player);
        Box box = player.getBoundingBox().stretch(direction.multiply(maxRange)).expand(1.5);
        AllianceManager allies = AllianceManager.get(world.getServer());
        LivingEntity best = null; double bestDot = -1;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box,
                x -> x != player && x.isAlive() && (!(x instanceof ServerPlayerEntity p) || !allies.isAlly(player,p)))) {
            Vec3d toward = e.getBoundingBox().getCenter().subtract(start);
            double distance = toward.length(); if (distance > maxRange || distance < .01) continue;
            double dot = direction.dotProduct(toward.normalize());
            if (dot > .94 && dot > bestDot) { best = e; bestDot = dot; }
        }
        return best;
    }

    @Override public void onAttack(ServerPlayerEntity attacker, Entity target) {
        if (target instanceof LivingEntity living && PowerCombatUtil.canHarm(attacker, target)
                && diveChain.getOrDefault(attacker.getUuid(), 0) > 0) {
            int chain = diveChain.getOrDefault(attacker.getUuid(), 0);
            if (chain >= Math.max(1, YPConfig.getInstance().spidey_dive_max_chain)) {
                diveChain.remove(attacker.getUuid()); diveWindow.remove(attacker.getUuid());
                diveCooldowns.put(attacker.getUuid(), Math.max(0, YPConfig.getInstance().spidey_dive_cooldown_ticks));
            }
        }
    }

    @Override public void tick(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        decrement(mlgCooldowns,id); decrement(grenadeCooldowns,id); decrement(rageCooldowns,id); decrement(diveCooldowns,id); decrement(webLaunchBoost,id);
        int rage = rageTicks.getOrDefault(id, 0);
        if (rage > 0) {
            rage--;
            if (rage == 0) {
                rageTicks.remove(id);
                int debuff = Math.max(1, YPConfig.getInstance().spidey_rage_debuff_ticks);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, debuff, 0, false, true));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, debuff, 0, false, true));
                player.sendMessage(Text.literal("Heart Palpitations: Rage Mode has ended; movement and melee are weakened briefly.").formatted(Formatting.DARK_RED), true);
            } else rageTicks.put(id, rage);
        }
        int window = diveWindow.getOrDefault(id, 0);
        if (window > 0) { window--; if (window == 0) { diveWindow.remove(id); diveChain.remove(id); } else diveWindow.put(id, window); }
        if (!(player.getEntityWorld() instanceof ServerWorld world) || player.getEntityWorld().getServer() == null) return;
        tickTempWebs(id);
        // Checked every server tick: players can cross the whole landing window in two or three ticks.
        tryAutomaticMlg(player, world);
    }

    private void tickTempWebs(UUID id) {
        List<TempWeb> webs = tempWebs.get(id);
        if (webs == null) return;
        webs.removeIf(w -> {
            if (--w.ticks > 0) return false;
            if (w.world.getBlockState(w.pos).isOf(Blocks.COBWEB)) w.world.setBlockState(w.pos, Blocks.AIR.getDefaultState());
            return true;
        });
        if (webs.isEmpty()) tempWebs.remove(id);
    }

    private void tryAutomaticMlg(ServerPlayerEntity player, ServerWorld world) {
        UUID id = player.getUuid();
        // The server does not track a client-driven player's velocity, so measure the real fall speed from Y.
        double previousY = lastY.getOrDefault(id, player.getY());
        double fallSpeed = previousY - player.getY();
        lastY.put(id, player.getY());
        if (!mlgEnabled.getOrDefault(id, true) || GraceManager.isActive(world.getServer())
                || player.isOnGround() || player.isCreative() || player.isSpectator()
                || fallSpeed < 0.4
                || player.fallDistance < Math.max(1, YPConfig.getInstance().spidey_mlg_fall_threshold)
                || mlgCooldowns.getOrDefault(id, 0) > 0) return;
        int reach = (int) Math.max(4, Math.min(14, Math.ceil(fallSpeed * 3.0) + 2));
        BlockPos feet = player.getBlockPos();
        for (int dy = 0; dy <= reach; dy++) {
            BlockPos pos = feet.down(dy);
            BlockState state = world.getBlockState(pos);
            if (state.isOf(Blocks.COBWEB)) return;                 // already protected
            if (!state.getFluidState().isEmpty()) return;           // water/lava below: no cobweb
            if (state.isAir() || state.getCollisionShape(world, pos).isEmpty()) continue;
            if (dy == 0) return;
            BlockPos webPos = pos.up();
            if (!world.getBlockState(webPos).isAir()) return;
            if (world.setBlockState(webPos, Blocks.COBWEB.getDefaultState())) {
                tempWebs.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new TempWeb(world, webPos));
                mlgCooldowns.put(id, Math.max(1, YPConfig.getInstance().spidey_mlg_cooldown_ticks));
                world.spawnParticles(ParticleTypes.CLOUD, webPos.getX()+.5, webPos.getY()+.5, webPos.getZ()+.5, 12,.2,.2,.2,.02);
                world.playSound(null, webPos, SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, .8f, 1.6f);
            }
            return;
        }
    }

    private static void decrement(Map<UUID,Integer> map, UUID id) { int v=map.getOrDefault(id,0); if(v>0)map.put(id,v-1); }
    @Override public void onPlayerDeath(ServerPlayerEntity player) { clearTransient(player.getUuid()); }
    @Override public void onRemove(ServerPlayerEntity player) { clearTransient(player.getUuid()); }
    @Override public void onPlayerJoin(ServerPlayerEntity player) { mlgEnabled.putIfAbsent(player.getUuid(), true); ensureWebSlingers(player); }
    private void clearTransient(UUID id) {
        rageTicks.remove(id); diveChain.remove(id); diveWindow.remove(id); mlgProbeClock.remove(id); webLaunchBoost.remove(id); lastY.remove(id);
        List<TempWeb> webs = tempWebs.remove(id);
        if (webs != null) for (TempWeb w : webs) if (w.world.getBlockState(w.pos).isOf(Blocks.COBWEB)) w.world.setBlockState(w.pos, Blocks.AIR.getDefaultState());
        rageCooldowns.remove(id); diveCooldowns.remove(id); grenadeCooldowns.remove(id); mlgCooldowns.remove(id);
    }
}
