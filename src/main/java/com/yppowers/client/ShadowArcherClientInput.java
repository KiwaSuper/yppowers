package com.yppowers.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Client-side input handling for Shadow Archer forms.
 *
 * <p>1) The disguise entity surrounds the player, so the vanilla crosshair would target it instead of the world.
 * Each tick, before input is processed, a crosshair that landed on the disguise is replaced by the real block or
 * entity along the look direction, so weapons, mining and block placement all work in every form.
 * <p>2) Spider forms climb walls with ladder-style physics (forward/jump climbs, otherwise they cling).
 */
@Environment(EnvType.CLIENT)
public final class ShadowArcherClientInput {
    private ShadowArcherClientInput() {}

    public static void tick(MinecraftClient client) {
        if (!ClientPowerData.shadowArcherMorphed || client.player == null || client.world == null
                || ClientPowerData.shadowArcherDisguiseEntityId < 0) return;
        repairCrosshair(client);
        tickWallClimb(client);
    }

    private static void repairCrosshair(MinecraftClient client) {
        HitResult current = client.crosshairTarget;
        int disguiseId = ClientPowerData.shadowArcherDisguiseEntityId;
        if (!(current instanceof EntityHitResult hit) || hit.getEntity().getId() != disguiseId) return;
        ClientPlayerEntity player = client.player;
        double entityReach = player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE);
        double blockReach = player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE);
        double reach = Math.max(entityReach, blockReach);
        Vec3d eye = player.getCameraPosVec(1.0F);
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d end = eye.add(look.multiply(reach));

        HitResult blockHit = player.raycast(blockReach, 1.0F, false);
        double blockDistance = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getPos().distanceTo(eye) : Double.MAX_VALUE;

        Entity best = null;
        Vec3d bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        Box search = player.getBoundingBox().stretch(look.multiply(reach)).expand(1.0);
        for (Entity candidate : client.world.getOtherEntities(player, search,
                e -> e.getId() != disguiseId && e.isAlive() && !e.isSpectator() && e instanceof LivingEntity)) {
            Optional<Vec3d> point = candidate.getBoundingBox().expand(candidate.getTargetingMargin()).raycast(eye, end);
            if (point.isEmpty()) continue;
            double distance = point.get().distanceTo(eye);
            if (distance < bestDistance && distance <= entityReach) { best = candidate; bestPos = point.get(); bestDistance = distance; }
        }
        if (best != null && bestDistance <= blockDistance) {
            client.crosshairTarget = new EntityHitResult(best, bestPos);
            client.targetedEntity = best;
        } else {
            client.crosshairTarget = blockHit;
            client.targetedEntity = null;
        }
    }

    private static boolean isSpiderForm(MinecraftClient client) {
        Entity disguise = client.world.getEntityById(ClientPowerData.shadowArcherDisguiseEntityId);
        return disguise != null && (disguise.getType() == EntityType.SPIDER || disguise.getType() == EntityType.CAVE_SPIDER);
    }

    private static boolean touchingWall(MinecraftClient client, ClientPlayerEntity player) {
        double reach = 0.42;
        for (double y : new double[] { player.getY() + 0.5, player.getY() + 1.4 }) {
            for (double[] offset : new double[][] { { reach, 0 }, { -reach, 0 }, { 0, reach }, { 0, -reach } }) {
                BlockPos pos = BlockPos.ofFloored(player.getX() + offset[0], y, player.getZ() + offset[1]);
                if (!client.world.getBlockState(pos).getCollisionShape(client.world, pos).isEmpty()) return true;
            }
        }
        return false;
    }

    private static void tickWallClimb(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (!isSpiderForm(client) || player.getAbilities().flying || player.isTouchingWater() || player.isInLava()
                || player.hasVehicle() || !touchingWall(client, player)) return;
        boolean pushing = client.options.forwardKey.isPressed() || client.options.jumpKey.isPressed();
        Vec3d velocity = player.getVelocity();
        if (pushing) {
            player.setVelocity(velocity.x, 0.2, velocity.z);
        } else if (!player.isOnGround() && velocity.y < 0.0) {
            player.setVelocity(velocity.x, 0.0, velocity.z); // cling to the wall instead of sliding
        }
    }
}
