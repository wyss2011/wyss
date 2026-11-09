package com.nonid.client.intiface;

import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import net.minecraft.text.Text;

public final class NonIntifaceBridge {
   private static final String CORE_CLASS = "io.github.blackspherefollower.buttplug4j.ButtplugException";
   private static final String CONNECTOR_CLASS = "io.github.blackspherefollower.buttplug4j.connectors.jetty.websocket.client.ButtplugClientWSClient";
   private static final String JACKSON_CORE_CLASS = "com.fasterxml.jackson.core.JsonProcessingException";
   private static final String JACKSON_DATABIND_CLASS = "com.fasterxml.jackson.databind.ObjectMapper";
   private static volatile Boolean available;
   private static volatile boolean warnedUnavailable;

   private NonIntifaceBridge() {
   }

   public static boolean isRuntimeAvailable() {
      return isAvailable();
   }

   public static Text statusText(NonConfig config) {
      if (!isUsable()) {
         return Text.translatable("config.needsofnature.intiface.status.unavailable");
      }

      try {
         return NonIntifaceClient.statusText(config);
      } catch (LinkageError e) {
         markUnavailable(e);
         return Text.translatable("config.needsofnature.intiface.status.unavailable");
      }
   }

   public static boolean isConnected() {
      if (!isUsable()) {
         return false;
      }

      try {
         return NonIntifaceClient.isConnected();
      } catch (LinkageError e) {
         markUnavailable(e);
         return false;
      }
   }

   public static void onWorldJoin(NonConfig config) {
      if (isUsable()) {
         try {
            NonIntifaceClient.onWorldJoin(config);
         } catch (LinkageError e) {
            markUnavailable(e);
         }
      }
   }

   public static void onWorldDisconnect(NonConfig config) {
      if (isUsable()) {
         try {
            NonIntifaceClient.onWorldDisconnect(config);
         } catch (LinkageError e) {
            markUnavailable(e);
         }
      }
   }

   public static void connect(NonConfig config) {
      if (isUsable()) {
         try {
            NonIntifaceClient.connect(config);
         } catch (LinkageError e) {
            markUnavailable(e);
         }
      }
   }

   public static void disconnect() {
      if (isUsable()) {
         try {
            NonIntifaceClient.disconnect();
         } catch (LinkageError e) {
            markUnavailable(e);
         }
      }
   }

   public static void startScanning(NonConfig config) {
      if (isUsable()) {
         try {
            NonIntifaceClient.startScanning(config);
         } catch (LinkageError e) {
            markUnavailable(e);
         }
      }
   }

   public static void stopAll() {
      if (isUsable()) {
         try {
            NonIntifaceClient.stopAll();
         } catch (LinkageError e) {
            markUnavailable(e);
         }
      }
   }

   public static void testPulse(NonConfig config, boolean peak) {
      if (isUsable()) {
         try {
            NonIntifaceClient.testPulse(config, peak);
         } catch (LinkageError e) {
            markUnavailable(e);
         }
      }
   }

   public static void pulseReactiveImpact(NonConfig config, double nextReactiveImpactDelayMs) {
      if (isUsable()) {
         try {
            NonIntifaceClient.pulseReactiveImpact(config, nextReactiveImpactDelayMs);
         } catch (LinkageError e) {
            markUnavailable(e);
         }
      }
   }

   public static void pulsePeakReactiveImpact(NonConfig config, double nextReactiveImpactDelayMs) {
      if (isUsable()) {
         try {
            NonIntifaceClient.pulsePeakReactiveImpact(config, nextReactiveImpactDelayMs);
         } catch (LinkageError e) {
            markUnavailable(e);
         }
      }
   }

   public static void clientTick(NonConfig config, boolean localAnimationActive, boolean peakStage, double specialVibratorScalar) {
      if (isUsable()) {
         try {
            NonIntifaceClient.clientTick(config, localAnimationActive, peakStage, specialVibratorScalar);
         } catch (LinkageError e) {
            markUnavailable(e);
         }
      }
   }

   public static void clientTick(
      NonConfig config, boolean localAnimationActive, boolean peakStage, double specialVibratorScalar, int energizedLevel, double energizedHeartPulse
   ) {
      if (isUsable()) {
         try {
            NonIntifaceClient.clientTick(config, localAnimationActive, peakStage, specialVibratorScalar, energizedLevel, energizedHeartPulse);
         } catch (LinkageError e) {
            markUnavailable(e);
         }
      }
   }

   private static boolean isAvailable() {
      Boolean cached = available;
      if (cached != null) {
         return cached;
      }

      try {
         ClassLoader loader = NonIntifaceBridge.class.getClassLoader();
         Class.forName("io.github.blackspherefollower.buttplug4j.ButtplugException", false, loader);
         Class.forName("io.github.blackspherefollower.buttplug4j.connectors.jetty.websocket.client.ButtplugClientWSClient", false, loader);
         Class.forName("com.fasterxml.jackson.core.JsonProcessingException", false, loader);
         Class.forName("com.fasterxml.jackson.databind.ObjectMapper", false, loader);
         available = true;
         return true;
      } catch (ClassNotFoundException | LinkageError e) {
         markUnavailable(e);
         return false;
      }
   }

   private static boolean isUsable() {
      return isAvailable() && !NonIntifaceDependencyManager.generatedBundleOutdated() && !NonIntifaceDependencyManager.restartRequired();
   }

   private static void markUnavailable(Throwable error) {
      available = false;
      if (!warnedUnavailable) {
         warnedUnavailable = true;
         NeedsOfNature.LOGGER.warn("[NoN] Buttplug.io integration is unavailable; disabling toy output.", error);
      }
   }
}
