package com.yppowers.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Thread-local render context used to tint player equipment/features with the body. */
@Environment(EnvType.CLIENT)
public final class FrozenPlayerRenderContext {
    private static final ThreadLocal<Boolean> FROZEN_PLAYER = ThreadLocal.withInitial(() -> false);

    private FrozenPlayerRenderContext() {}

    public static void setFrozenPlayer(boolean frozen) {
        FROZEN_PLAYER.set(frozen);
    }

    public static boolean isFrozenPlayer() {
        return FROZEN_PLAYER.get();
    }
}
