package com.yppowers.power.teleportgambit;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Builds and maintains the floating arena platform inside {@code yppowers:tg_domain}. */
final class DomainPlatformBuilder {
    static final int FLOOR_Y = 64;
    static final int ARENA_RADIUS = 42;

    private DomainPlatformBuilder() {}

    static void ensureArenaBuilt(ServerWorld domainWorld) {
        if (domainWorld == null) return;
        // Quick marker check: if center floor is already quartz/obsidian, arena is ready.
        if (domainWorld.getBlockState(new BlockPos(0, FLOOR_Y, 0)).isOf(Blocks.QUARTZ_BLOCK)) {
            return;
        }
        int rSq = ARENA_RADIUS * ARENA_RADIUS;
        int innerSq = (ARENA_RADIUS - 2) * (ARENA_RADIUS - 2);
        for (int dx = -ARENA_RADIUS; dx <= ARENA_RADIUS; dx++) {
            for (int dz = -ARENA_RADIUS; dz <= ARENA_RADIUS; dz++) {
                int dSq = dx * dx + dz * dz;
                if (dSq > rSq) continue;
                BlockPos floor = new BlockPos(dx, FLOOR_Y, dz);
                if (dSq >= innerSq) {
                    domainWorld.setBlockState(floor, Blocks.OBSIDIAN.getDefaultState(), 2);
                    domainWorld.setBlockState(floor.up(), Blocks.CRYING_OBSIDIAN.getDefaultState(), 2);
                } else if ((dx == 0 && dz == 0) || (Math.abs(dx) % 8 == 0 && Math.abs(dz) % 8 == 0)) {
                    domainWorld.setBlockState(floor, Blocks.QUARTZ_BLOCK.getDefaultState(), 2);
                } else {
                    domainWorld.setBlockState(floor, Blocks.DEEPSLATE_TILES.getDefaultState(), 2);
                }
                domainWorld.setBlockState(floor.down(), Blocks.BEDROCK.getDefaultState(), 2);
            }
        }
    }
}
