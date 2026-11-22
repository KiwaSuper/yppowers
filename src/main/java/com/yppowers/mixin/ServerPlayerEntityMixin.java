package com.yppowers.mixin;

import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.system.FreezeSystem;
import com.yppowers.system.SoulboundItemSystem;
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

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void yppowers$freezeTick(CallbackInfo ci) {
        if (FreezeSystem.isFrozen((ServerPlayerEntity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void yppowers$keepSoulboundItems(DamageSource source, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        yppowers$keptItems.clear();
        if (player.getEntityWorld().getServer() == null) return;
        Power power = PlayerDataStore.get(player.getEntityWorld().getServer()).getPower(player);
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            boolean keep = SoulboundItemSystem.isSoulbound(stack)
                    || (power != null && power.shouldPreventDrop(player, stack));
            if (keep) {
                yppowers$keptItems.add(stack.copy());
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }
    }

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void yppowers$restoreKeptItems(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        if (alive) return;
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        ServerPlayerEntityMixin oldMixin = (ServerPlayerEntityMixin) (Object) oldPlayer;
        for (ItemStack stack : oldMixin.yppowers$keptItems) {
            player.giveOrDropStack(stack);
        }
        oldMixin.yppowers$keptItems.clear();
    }
}
