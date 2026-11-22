package com.yppowers.mixin;

import com.yppowers.config.YPConfig;
import com.yppowers.item.ThorMaceHook;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives Thor's soulbound mace a trident-style hold-to-charge Riptide III launch anywhere,
 * properly gated by Thor power ownership and Grace period.
 */
@Mixin(Item.class)
public abstract class ThorMaceItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void yppowers$thorMaceUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack stack = user.getStackInHand(hand);
        if (ThorMaceHook.canUseRiptide(user, stack)) {
            user.setCurrentHand(hand);
            cir.setReturnValue(ActionResult.CONSUME);
        }
    }

    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void yppowers$thorMaceMaxUseTime(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        if (ThorMaceHook.isThorMace(stack)) {
            cir.setReturnValue(72000);
        }
    }

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void yppowers$thorMaceUseAction(ItemStack stack, CallbackInfoReturnable<UseAction> cir) {
        if (ThorMaceHook.isThorMace(stack)) {
            cir.setReturnValue(UseAction.TRIDENT);
        }
    }

    @Inject(method = "onStoppedUsing", at = @At("HEAD"), cancellable = true)
    private void yppowers$thorMaceOnStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!(user instanceof PlayerEntity player) || !ThorMaceHook.canUseRiptide(player, stack)) {
            return;
        }
        int charge = 72000 - remainingUseTicks;
        if (charge < YPConfig.get().powers.thor.riptideMinChargeTicks) {
            return;
        }
        yppowers$launchRiptide(world, player, stack);
        cir.setReturnValue(true);
    }

    @Unique
    private static void yppowers$launchRiptide(World world, PlayerEntity player, ItemStack stack) {
        float strength = (float) YPConfig.get().powers.thor.riptideStrength;
        Vec3d look = player.getRotationVec(1.0f);
        player.setVelocity(look.x * strength, look.y * strength, look.z * strength);
        player.velocityDirty = true;
        player.useRiptide(20, 11.0f, stack);
        world.playSoundFromEntity(
                null,
                player,
                SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(),
                SoundCategory.PLAYERS,
                1.0f,
                1.0f
        );
    }
}
