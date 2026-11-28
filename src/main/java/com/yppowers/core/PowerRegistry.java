package com.yppowers.core;

import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Central typed registry of all 17 Power implementations. */
public final class PowerRegistry {
   private static final Map<PowerType, Power> POWERS = new EnumMap<>(PowerType.class);

   private PowerRegistry() {
   }

   public static void register(Power power) {
      if (power != null && power.getType() != null) {
         POWERS.put(power.getType(), power);
      }
   }

   public static Power get(PowerType type) {
      return type == null ? null : POWERS.get(type);
   }

   public static <T extends Power> T get(PowerType type, Class<T> clazz) {
      Power p = get(type);
      return clazz.isInstance(p) ? clazz.cast(p) : null;
   }

   public static Collection<Power> all() {
      return Collections.unmodifiableCollection(POWERS.values());
   }

   public static int size() {
      return POWERS.size();
   }
}
