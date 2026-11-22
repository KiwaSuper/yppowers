package com.yppowers.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class FrozenRenderContext {
    private static final ThreadLocal<Boolean> FROZEN = ThreadLocal.withInitial(() -> false);

    private FrozenRenderContext() {}

    public static void setFrozen(boolean value) {
        FROZEN.set(value);
    }

    public static boolean isFrozen() {
        return FROZEN.get();
    }
}
