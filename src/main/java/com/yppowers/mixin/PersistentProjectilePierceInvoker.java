package com.yppowers.mixin;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the non-public pierce-level setter of persistent projectiles in Minecraft 1.21.11. */
@Mixin(PersistentProjectileEntity.class)
public interface PersistentProjectilePierceInvoker {
    @Invoker("setPierceLevel")
    void yppowers$setPierceLevel(byte level);
}
