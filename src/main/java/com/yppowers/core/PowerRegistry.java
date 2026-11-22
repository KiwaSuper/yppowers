package com.yppowers.core;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Central immutable-after-boot registry of all power implementations. */
public final class PowerRegistry {
    private static final Map<PowerId, Power> POWERS = new EnumMap<>(PowerId.class);

    private PowerRegistry() {}

    public static void register(Power power) {
        if (power == null || power.id() == null) {
            throw new IllegalArgumentException("Power and PowerId must not be null");
        }
        POWERS.put(power.id(), power);
    }

    public static Power get(PowerId id) {
        return id == null ? null : POWERS.get(id);
    }

    public static Collection<Power> all() {
        return Collections.unmodifiableCollection(POWERS.values());
    }

    public static void clearForTests() {
        POWERS.clear();
    }
}
