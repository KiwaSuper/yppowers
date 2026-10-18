package com.yppowers.mixin;

import com.yppowers.power.impl.TeleportGambitPower;
import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndPortalBlock.class)
public abstract class EndPortalBlockMixin {
    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void yppowers$keepCustomGatewayInCurrentDimension(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler collisionHandler, boolean canTeleport, CallbackInfo ci) {
        if (!world.isClient() && entity instanceof ServerPlayerEntity player && TeleportGambitPower.isCustomGateway(player, pos)) ci.cancel();
    }
}
