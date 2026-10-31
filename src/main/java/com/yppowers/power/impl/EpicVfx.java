package com.yppowers.power.impl;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

/** Lightweight, one-shot visual accents. These helpers never alter gameplay state. */
final class EpicVfx {
    private EpicVfx() {}

    /** Layered impact accent; particle counts are deliberately bounded for multiplayer servers. */
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
    }

    /** Short vertical burst used only on ability activation, not as a per-tick aura. */
    static void activation(ServerWorld world, double x, double y, double z, double scale) {
        double s = Math.max(0.35, Math.min(2.0, scale));
        world.spawnParticles(ParticleTypes.END_ROD, x, y + 0.7 * s, z,
                (int) (14 * s), 0.28 * s, 0.65 * s, 0.28 * s, 0.035 * s);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.6 * s, z,
                (int) (18 * s), 0.5 * s, 0.55 * s, 0.5 * s, 0.045 * s);
        world.spawnParticles(ParticleTypes.ENCHANT, x, y + 0.8 * s, z,
                (int) (12 * s), 0.45 * s, 0.7 * s, 0.45 * s, 0.25 * s);
    }
}
