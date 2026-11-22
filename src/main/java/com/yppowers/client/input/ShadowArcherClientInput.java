package com.yppowers.client.input;

import com.yppowers.client.ClientState;
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

/** Client-side disguise crosshair passthrough and spider wall-climb physics for Shadow Archer. */
@Environment(EnvType.CLIENT)
public final class ShadowArcherClientInput {
    private ShadowArcherClientInput() {}

    public static void tick(MinecraftClient client) {
        if (!ClientState.shadowArcherMorphed || client.player == null || client.world == null
                || ClientState.shadowArcherDisguiseEntityId < 0) return;
        repairCrosshair(client);
        tickWallClimb(client);
    }

    private static void repairCrosshair(MinecraftClient client) {
        HitResult current = client.crosshairTarget;
        int disguiseId = ClientState.shadowArcherDisguiseEntityId;
        if (!(current instanceof EntityHitResult hit) || hit.getEntity().getId() != disguiseId) return;

        ClientPlayerEntity player = client.player;
        double entityReach = player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE);
        double blockReach = player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE);
        double reach = Math.max(entityReach, blockReach);
        Vec3d eye = player.getCameraPosVec(1.0f);
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d end = eye.add(look.multiply(reach));

        HitResult blockHit = player.raycast(blockReach, 1.0f, false);
        double blockDist = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getPos().distanceTo(eye) : Double.MAX_VALUE;

        Entity best = null;
        Vec3d bestPos = null;
        double bestDist = Double.MAX_VALUE;
        Box search = player.getBoundingBox().stretch(look.multiply(reach)).expand(1.0);
        for (Entity cand : client.world.getOtherEntities(player, search,
                e -> e.getId() != disguiseId && e.isAlive() && !e.isSpectator() && e instanceof LivingEntity)) {
            Optional<Vec3d> point = cand.getBoundingBox().expand(cand.getTargetingMargin()).raycast(eye, end);
            if (point.isEmpty()) continue;
            double d = point.get().distanceTo(eye);
            if (d < bestDist && d <= entityReach) {
                best = cand;
                bestPos = point.get();
                bestDist = d;
            }
        }
        if (best != null && bestDist <= blockDist) {
            client.crosshairTarget = new EntityHitResult(best, bestPos);
            client.targetedEntity = best;
        } else {
            client.crosshairTarget = blockHit;
            client.targetedEntity = null;
        }
    }

    private static void tickWallClimb(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        Entity disguise = client.world.getEntityById(ClientState.shadowArcherDisguiseEntityId);
        if (disguise == null || (disguise.getType() != EntityType.SPIDER && disguise.getType() != EntityType.CAVE_SPIDER)) return;
        if (player.getAbilities().flying || player.isTouchingWater() || player.isInLava() || player.hasVehicle()) return;
        if (!touchingWall(client, player)) return;

        boolean pushing = client.options.forwardKey.isPressed() || client.options.jumpKey.isPressed();
        Vec3d vel = player.getVelocity();
        if (pushing) player.setVelocity(vel.x, 0.2, vel.z);
        else if (!player.isOnGround() && vel.y < 0.0) player.setVelocity(vel.x, 0.0, vel.z);
    }

    private static boolean touchingWall(MinecraftClient client, ClientPlayerEntity player) {
        double r = 0.42;
        for (double y : new double[]{player.getY() + 0.5, player.getY() + 1.4}) {
            for (double[] off : new double[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
                BlockPos pos = BlockPos.ofFloored(player.getX() + off[0], y, player.getZ() + off[1]);
                if (!client.world.getBlockState(pos).getCollisionShape(client.world, pos).isEmpty()) return true;
            }
        }
        return false;
    }
}
