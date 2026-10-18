package com.yppowers.mixin;

import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.impl.TimeWarperPower;
import com.yppowers.power.impl.SeriousPunchPower;
import com.yppowers.power.impl.IceEmperorPower;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Unique
    private final List<ItemStack> yppowers$keptItems = new ArrayList<>();

    @Unique
    private Power yppowers$getPower() {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (player.getEntityWorld().getServer() == null) return null;
        PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
        PowerType type = manager.getPowerType(player.getUuid());
        if (type != null) {
            return manager.getPowerInstance(type);
        }
        return null;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void yppowers$freezePlayerDuringTimeStop(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (TimeWarperPower.isEntityFrozen(player) || SeriousPunchPower.isEntityFrozen(player)
                || IceEmperorPower.isEntityFrozen(player)) ci.cancel();
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void yppowers$onDeath(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        Power power = yppowers$getPower();
        yppowers$keptItems.clear();

        if (power != null) {
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (!stack.isEmpty() && power.shouldPreventDrop(player, stack)) {
                    yppowers$keptItems.add(stack.copy());
                    player.getInventory().setStack(i, ItemStack.EMPTY);
                }
            }
        }
    }

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void yppowers$copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        if (!alive) {
            ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
            ServerPlayerEntityMixin oldMixin = (ServerPlayerEntityMixin) (Object) oldPlayer;
            if (oldMixin != null) {
                for (ItemStack stack : oldMixin.yppowers$keptItems) {
                    player.giveOrDropStack(stack);
                }
                oldMixin.yppowers$keptItems.clear();
            }
        }
    }
}
