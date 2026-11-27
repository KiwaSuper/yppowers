package com.yppowers.mixin;

import com.yppowers.power.impl.ThorPower;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({World.class})
public abstract class WorldMixin {
   @Unique
   private boolean yppowers$shouldProtectHeavyCore(BlockPos pos, BlockState replacement) {
      if (replacement.isOf(Blocks.HEAVY_CORE)) {
         return false;
      } else if ((Object)this instanceof ServerWorld serverWorld) {
         return !serverWorld.getBlockState(pos).isOf(Blocks.HEAVY_CORE)
            ? false
            : !ThorPower.isAuthorizedHeavyCoreRemoval(serverWorld, pos) && ThorPower.isProtectedHeavyCore(serverWorld, pos);
      } else {
         return false;
      }
   }

   @Inject(
      method = {"setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$protectCoreSetBlockState(BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
      if (this.yppowers$shouldProtectHeavyCore(pos, state)) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$protectCoreSetBlockStateFlags(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
      if (this.yppowers$shouldProtectHeavyCore(pos, state)) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$protectCoreSetBlockStateDepth(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
      if (this.yppowers$shouldProtectHeavyCore(pos, state)) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"breakBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$protectCoreBreakBlock(BlockPos pos, boolean drop, Entity breakingEntity, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
      if ((Object)this instanceof ServerWorld serverWorld
         && serverWorld.getBlockState(pos).isOf(Blocks.HEAVY_CORE)
         && !ThorPower.isAuthorizedHeavyCoreRemoval(serverWorld, pos)
         && ThorPower.isProtectedHeavyCore(serverWorld, pos)) {
         cir.setReturnValue(false);
      }
   }
}
