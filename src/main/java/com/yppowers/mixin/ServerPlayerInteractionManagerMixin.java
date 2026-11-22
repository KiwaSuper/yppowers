package com.yppowers.mixin;

import com.yppowers.system.TempBlockSystem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {
    @Shadow @Final protected ServerPlayerEntity player;

    @Inject(method = "tryBreakBlock", at = @At("HEAD"), cancellable = true)
    private void yppowers$protectTemporaryBlocks(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (player.getEntityWorld() instanceof ServerWorld world
                && TempBlockSystem.isPlayerBreakBlocked(world, pos)) {
            cir.setReturnValue(false);
        }
    }
}
