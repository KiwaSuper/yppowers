package com.yppowers.client.render;

import java.util.UUID;

/** Client render-state metadata for a Time Warper clone. */
public interface TimeCloneRenderStateAccess {
    UUID yppowers$getCloneOwner();
    boolean yppowers$isGoldenClone();
    void yppowers$setCloneSkin(UUID owner, boolean golden);
}
