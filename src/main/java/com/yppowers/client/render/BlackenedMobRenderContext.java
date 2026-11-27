package com.yppowers.client.render;

public final class BlackenedMobRenderContext {
   private static final ThreadLocal<Boolean> BLACKENED = new ThreadLocal<>();

   private BlackenedMobRenderContext() {
   }

   public static boolean isBlackened() {
      return Boolean.TRUE.equals(BLACKENED.get());
   }

   public static void setBlackened(boolean blackened) {
      if (blackened) {
         BLACKENED.set(true);
      } else {
         BLACKENED.remove();
      }
   }
}
