package com.yppowers.client.render;

import java.util.UUID;

public interface TimeCloneRenderStateAccess {
   UUID yppowers$getCloneOwner();

   boolean yppowers$isGoldenClone();

   void yppowers$setCloneSkin(UUID var1, boolean var2);
}
