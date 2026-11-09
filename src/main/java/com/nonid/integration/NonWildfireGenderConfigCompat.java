package com.nonid.integration;

import com.nonid.NeedsOfNature;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

public final class NonWildfireGenderConfigCompat {
   private static final String MOD_ID = "wildfire_gender";
   private static final float NATIVE_BREAST_SIZE_MAX = 0.8F;
   private static boolean initialized;
   private static float supportedBreastSizeMax = 0.8F;

   private NonWildfireGenderConfigCompat() {
   }

   public static synchronized void initialize() {
      if (!initialized) {
         initialized = true;
         if (FabricLoader.getInstance().isModLoaded("wildfire_gender")) {
            try {
               Class<?> configurationClass = Class.forName("com.wildfire.main.config.Configuration");
               Object breastSizeKey = configurationClass.getField("BUST_SIZE").get(null);
               Method getMaxInclusive = breastSizeKey.getClass().getMethod("getMaxInclusive");
               float currentMax = ((Number)getMaxInclusive.invoke(breastSizeKey)).floatValue();
               float targetMax = Math.max(currentMax, 1.6F);
               if (currentMax < targetMax) {
                  Class<?> numberConfigKeyClass = Class.forName("com.wildfire.main.config.types.NumberConfigKey");
                  Field maxInclusive = numberConfigKeyClass.getDeclaredField("maxInclusive");
                  maxInclusive.setAccessible(true);
                  maxInclusive.set(breastSizeKey, targetMax);
               }

               float appliedMax = ((Number)getMaxInclusive.invoke(breastSizeKey)).floatValue();
               if (appliedMax < targetMax) {
                  throw new IllegalStateException("Female Gender Mod breast-size maximum remained at " + appliedMax);
               }

               supportedBreastSizeMax = appliedMax;
            } catch (ReflectiveOperationException | RuntimeException e) {
               NeedsOfNature.LOGGER
                  .warn("[NoN] Could not extend Female Gender Mod's breast-size range. Values above 100% will be clamped for compatibility.", e);
            }
         }
      }
   }

   public static float clampBreastSize(float breastSize) {
      initialize();
      return Math.clamp(breastSize, 0.0F, supportedBreastSizeMax);
   }
}
