package com.yppowers.power.impl;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Cinematic, one-shot visual building blocks. These helpers never alter gameplay state.
 *
 * <p>Most effects use the vanilla "count = 0" particle convention, where the offset is treated as a velocity.
 * One packet therefore produces a particle that keeps travelling on the client, which is what makes a ring
 * look like it expands or a helix look like it rises without any server-side scheduling.
 * Particle budgets are deliberately bounded for multiplayer servers.
 */
final class EpicVfx {
    private EpicVfx() {}

    /** Flat ring whose particles fly outward from {@code startRadius}; reads as an expanding shockwave. */
    static void ring(ServerWorld world, ParticleEffect type, double x, double y, double z,
                     double startRadius, double speed, int points, double lift) {
        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points;
            double c = Math.cos(angle), s = Math.sin(angle);
            world.spawnParticles(type, x + c * startRadius, y, z + s * startRadius, 0, c, lift, s, speed);
        }
    }

    /** Ground shockwave: dust ring, fast spark ring and a floating light ring. */
    static void shockwave(ServerWorld world, double x, double y, double z, double radius) {
        double r = Math.max(1.0, Math.min(24.0, radius));
        int points = (int) Math.max(16, Math.min(36, 16 + r * 4));
        ring(world, ParticleTypes.CLOUD, x, y + 0.1, z, 0.4, r * 0.045, points, 0.03);
        ring(world, ParticleTypes.ELECTRIC_SPARK, x, y + 0.25, z, 0.3, r * 0.07, points / 2, 0.05);
        ring(world, ParticleTypes.END_ROD, x, y + 0.4, z, 0.3, r * 0.08, points / 2, 0.12);
    }

    /** Ring that collapses inward; used for charge-ups and activations. */
    static void implosion(ServerWorld world, ParticleEffect type, double x, double y, double z,
                          double radius, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points;
            double c = Math.cos(angle), s = Math.sin(angle);
            world.spawnParticles(type, x + c * radius, y, z + s * radius, 0, -c, 0.0, -s, speed);
        }
    }

    /** Spherical burst of {@code count} particles flying outward in an even (Fibonacci) distribution. */
    static void sphereBurst(ServerWorld world, ParticleEffect type, double x, double y, double z, int count, double speed) {
        int n = Math.max(1, Math.min(160, count));
        for (int i = 0; i < n; i++) {
            double t = (i + 0.5) / n;
            double phi = Math.acos(1.0 - 2.0 * t);
            double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * i;
            world.spawnParticles(type, x, y, z, 0,
                    Math.sin(phi) * Math.cos(theta), Math.cos(phi), Math.sin(phi) * Math.sin(theta), speed);
        }
    }

    /** Rising helix; particles are placed along the spiral and drift upward. */
    static void helix(ServerWorld world, ParticleEffect type, double x, double y, double z,
                      double height, double radius, double turns, int points, double upSpeed) {
        for (int i = 0; i < points; i++) {
            double f = (double) i / points;
            double angle = 2.0 * Math.PI * turns * f;
            world.spawnParticles(type, x + Math.cos(angle) * radius, y + height * f, z + Math.sin(angle) * radius,
                    0, 0.0, 1.0, 0.0, upSpeed);
        }
    }

    /** Vertical pillar of light. */
    static void column(ServerWorld world, ParticleEffect type, double x, double y, double z, double height, int points) {
        for (int i = 0; i < points; i++) {
            world.spawnParticles(type, x, y + height * i / Math.max(1, points), z, 0, 0.0, 1.0, 0.0, 0.18);
        }
    }

    /** Straight beam between two points. */
    static void beam(ServerWorld world, ParticleEffect type, Vec3d from, Vec3d to, double step) {
        Vec3d line = to.subtract(from);
        int points = (int) Math.max(1, Math.min(72, line.length() / Math.max(0.15, step)));
        for (int i = 0; i <= points; i++) {
            Vec3d p = from.add(line.multiply((double) i / points));
            world.spawnParticles(type, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /** Full-screen style white flash particle. */
    static void flash(ServerWorld world, double x, double y, double z) {
        world.spawnParticles(TintedParticleEffect.create(ParticleTypes.FLASH, 0xFFFFFF), x, y, z, 1, 0, 0, 0, 0);
    }

    /** Layered impact accent: sparks, shockwave, spherical burst, and for large scales a flash and light pillar. */
    static void impact(ServerWorld world, double x, double y, double z, double scale) {
        double s = Math.max(0.35, Math.min(2.5, scale));
        world.spawnParticles(ParticleTypes.END_ROD, x, y + 0.35 * s, z,
                (int) (12 * s), 0.45 * s, 0.25 * s, 0.45 * s, 0.035 * s);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.2 * s, z,
                (int) (16 * s), 0.7 * s, 0.22 * s, 0.7 * s, 0.055 * s);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.15 * s, z,
                (int) (10 * s), 0.55 * s, 0.3 * s, 0.55 * s, 0.025 * s);
        world.spawnParticles(ParticleTypes.CLOUD, x, y + 0.05 * s, z,
                (int) (8 * s), 0.65 * s, 0.08 * s, 0.65 * s, 0.025 * s);
        shockwave(world, x, y, z, 2.0 + 3.0 * s);
        sphereBurst(world, ParticleTypes.END_ROD, x, y + 0.4 * s, z, (int) (14 * s), 0.22 * s);
        if (s >= 1.2) {
            flash(world, x, y + 0.5, z);
            world.spawnParticles(ParticleTypes.EXPLOSION, x, y + 0.5, z, 1, 0, 0, 0, 0);
        }
        if (s >= 1.6) column(world, ParticleTypes.END_ROD, x, y, z, 3.0 * s, 10);
    }

    /** Ability activation: converging light, a rising helix and a bright ground pulse. Not meant as a per-tick aura. */
    static void activation(ServerWorld world, double x, double y, double z, double scale) {
        double s = Math.max(0.35, Math.min(2.0, scale));
        world.spawnParticles(ParticleTypes.END_ROD, x, y + 0.7 * s, z,
                (int) (14 * s), 0.28 * s, 0.65 * s, 0.28 * s, 0.035 * s);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.6 * s, z,
                (int) (18 * s), 0.5 * s, 0.55 * s, 0.5 * s, 0.045 * s);
        world.spawnParticles(ParticleTypes.ENCHANT, x, y + 0.8 * s, z,
                (int) (12 * s), 0.45 * s, 0.7 * s, 0.45 * s, 0.25 * s);
        implosion(world, ParticleTypes.END_ROD, x, y + 0.2, z, 2.2 * s, 14, 0.18 * s);
        helix(world, ParticleTypes.ELECTRIC_SPARK, x, y, z, 2.2 * s, 0.8 * s, 1.5, 18, 0.1);
        ring(world, ParticleTypes.CLOUD, x, y + 0.1, z, 0.3, 0.12 * s, 16, 0.0);
    }
}
