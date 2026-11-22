package com.yppowers.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public interface TimeCloneRenderStateAccess {
    UUID yppowers$getCloneOwner();
    boolean yppowers$isGoldenClone();
    void yppowers$setCloneSkin(UUID owner, boolean golden);
}
