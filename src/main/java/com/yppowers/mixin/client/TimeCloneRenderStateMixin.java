package com.yppowers.mixin.client;

import com.yppowers.client.render.TimeCloneRenderStateAccess;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(ZombieEntityRenderState.class)
public abstract class TimeCloneRenderStateMixin implements TimeCloneRenderStateAccess {
    @Unique private UUID yppowers$cloneOwner;
    @Unique private boolean yppowers$goldenClone;

    @Override public UUID yppowers$getCloneOwner() { return yppowers$cloneOwner; }
    @Override public boolean yppowers$isGoldenClone() { return yppowers$goldenClone; }
    @Override public void yppowers$setCloneSkin(UUID owner, boolean golden) {
        yppowers$cloneOwner = owner;
        yppowers$goldenClone = golden;
    }
}
