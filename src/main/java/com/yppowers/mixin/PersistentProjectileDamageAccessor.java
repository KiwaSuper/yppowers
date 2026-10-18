package com.yppowers.mixin;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the private configured damage value of persistent projectiles in Minecraft 1.21.11. */
@Mixin(PersistentProjectileEntity.class)
public interface PersistentProjectileDamageAccessor {
    @Accessor("damage")
    double yppowers$getProjectileDamage();
}
