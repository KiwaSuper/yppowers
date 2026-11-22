package com.yppowers.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityVelocityAccessor {
    @Invoker("scheduleVelocityUpdate")
    void invokeYppowersScheduleVelocityUpdate();
}
