package com.yppowers.system;

import com.yppowers.config.YPConfig;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;

/**
 * Upgraded shared visual and sound effects helper respecting {@code general.vfxParticleScale}.
 */
public final class VfxSystem {
    private VfxSystem() {}

    private static int scaled(int count) {
        double scale = YPConfig.get().general.vfxParticleScale;
        if (scale <= 0.0 || count <= 0) return 0;
        return Math.max(1, (int) Math.round(count * scale));
    }

    public static void ring(ServerWorld world, ParticleEffect particle, Vec3d center, double radius, int points, double speed) {
        int n = scaled(points);
        if (world == null || n <= 0) return;
        for (int i = 0; i < n; i++) {
            double angle = (Math.PI * 2.0 * i) / n;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            world.spawnParticles(particle, x, center.y, z, 1, 0.0, 0.0, 0.0, speed);
        }
    }

    public static void shockwave(ServerWorld world, ParticleEffect outer, ParticleEffect inner, Vec3d center, double radius, int points) {
        ring(world, outer, center, radius, points, 0.04);
        if (inner != null) {
            ring(world, inner, center.add(0, 0.25, 0), radius * 0.65, Math.max(8, points / 2), 0.02);
        }
    }

    public static void risingHelix(ServerWorld world, ParticleEffect particle, Vec3d base, double radius, double height, int steps) {
        int n = scaled(steps);
        if (world == null || n <= 0) return;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double angle = t * Math.PI * 4.0;
            double y = base.y + t * height;
            double x1 = base.x + Math.cos(angle) * radius;
            double z1 = base.z + Math.sin(angle) * radius;
            double x2 = base.x - Math.cos(angle) * radius;
            double z2 = base.z - Math.sin(angle) * radius;
            world.spawnParticles(particle, x1, y, z1, 1, 0.0, 0.02, 0.0, 0.01);
            world.spawnParticles(particle, x2, y, z2, 1, 0.0, 0.02, 0.0, 0.01);
        }
    }

    public static void lightPillar(ServerWorld world, ParticleEffect particle, Vec3d base, double height, int steps) {
        int n = scaled(steps);
        if (world == null || n <= 0) return;
        for (int i = 0; i < n; i++) {
            double y = base.y + (height * i) / n;
            world.spawnParticles(particle, base.x, y, base.z, 2, 0.18, 0.08, 0.18, 0.01);
        }
    }

    public static void beam(ServerWorld world, ParticleEffect particle, Vec3d from, Vec3d to, int points) {
        int n = scaled(points);
        if (world == null || n <= 0) return;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            Vec3d p = from.lerp(to, t);
            world.spawnParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    public static void burst(ServerWorld world, ParticleEffect particle, Vec3d center, int count, double spread, double speed) {
        int n = scaled(count);
        if (world == null || n <= 0) return;
        world.spawnParticles(particle, center.x, center.y, center.z, n, spread, spread, spread, speed);
    }

    public static void flash(ServerWorld world, Vec3d center) {
        if (world == null || YPConfig.get().general.vfxParticleScale <= 0.0) return;
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
    }

    public static void playSound(ServerWorld world, Vec3d pos, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        if (world == null || sound == null) return;
        world.playSound(null, pos.x, pos.y, pos.z, sound, category, volume, pitch);
    }
}
