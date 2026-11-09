package com.nonid.client.intiface;

import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import io.github.blackspherefollower.buttplug4j.client.ButtplugClientDevice;
import io.github.blackspherefollower.buttplug4j.client.ButtplugOutput;
import io.github.blackspherefollower.buttplug4j.connectors.jetty.websocket.client.ButtplugClientWSClient;
import io.github.blackspherefollower.buttplug4j.protocol.ButtplugMessage;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.text.Text;

public final class NonIntifaceClient {
   private static final int CONNECT_TIMEOUT_MS = 8000;
   private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, "NoN Intiface");
      thread.setDaemon(true);
      return thread;
   });
   private static volatile ButtplugClientWSClient client;
   private static volatile NonIntifaceClient.State state = NonIntifaceClient.State.DISCONNECTED;
   private static volatile String detail = "";
   private static volatile long lastPulseMs = 0L;
   private static volatile long pulseToken = 0L;
   private static volatile ScheduledFuture<?> pendingStop;
   private static volatile ScheduledFuture<?> pendingStrokerReturn;
   private static volatile boolean requestedAnimationActive = false;
   private static volatile boolean requestedPeakStage = false;
   private static volatile boolean requestedIntifaceEnabled = false;
   private static volatile int requestedBaselineStrengthPercent = Integer.MIN_VALUE;
   private static volatile int requestedOscillatorRegularSpeedPercent = Integer.MIN_VALUE;
   private static volatile int requestedOscillatorPeakSpeedPercent = Integer.MIN_VALUE;
   private static volatile double requestedSpecialVibratorScalar = Double.NaN;
   private static volatile int requestedEnergizedLevel = 0;
   private static volatile int requestedEnergizedBasePercent = 0;
   private static volatile int requestedEnergizedPulsePercent = 0;
   private static volatile int requestedEnergizedPulsePermille = 0;
   private static volatile boolean baselineActive = false;
   private static volatile int baselineStrengthPercent = 0;
   private static volatile boolean oscillatorActive = false;
   private static volatile int oscillatorSpeedPercent = 0;
   private static volatile boolean specialVibratorActive = false;
   private static volatile double specialVibratorScalar = 0.0;
   private static volatile boolean energizedActive = false;
   private static volatile int energizedBasePercent = 0;
   private static volatile int energizedPulsePercent = 0;
   private static volatile double energizedPulse = 0.0;
   private static volatile double currentScalar = 0.0;
   private static volatile double currentOscillatorScalar = 0.0;
   private static volatile boolean suppressBaselineUntilInactive = false;
   private static volatile long connectAttemptId = 0L;

   private NonIntifaceClient() {
   }

   public static void onWorldJoin(NonConfig config) {
      if (config != null && config.isIntifaceEnabled() && config.isIntifaceAutoConnectOnWorldJoin()) {
         connect(config);
      } else {
         updateDisabledState(config);
      }
   }

   public static void onWorldDisconnect(NonConfig config) {
      if (config != null && config.isIntifaceStopOnDisconnect()) {
         stopAll();
      }

      disconnect();
   }

   public static void connect(NonConfig config) {
      if (config != null && config.isIntifaceEnabled()) {
         String url = config.getIntifaceServerUrl();
         NeedsOfNature.LOGGER.info("[NoN] Buttplug.io connect requested: url={}", url);
         executeSafely("Connect failed", () -> connectInternal(url));
      } else {
         state = NonIntifaceClient.State.DISABLED;
         detail = "";
      }
   }

   public static void disconnect() {
      executeSafely("Disconnect failed", () -> {
         cancelPendingStop();
         cancelPendingStrokerReturn();
         clearBaselineState();
         disconnectInternal();
         state = NonIntifaceClient.State.DISCONNECTED;
         detail = "";
      });
   }

   public static void startScanning(NonConfig config) {
      if (config != null && config.isIntifaceEnabled()) {
         executeSafely("Scan failed", () -> {
            connectInternal(config.getIntifaceServerUrl());
            ButtplugClientWSClient activeClient = client;
            if (activeClient != null && activeClient.isConnected()) {
               try {
                  state = NonIntifaceClient.State.SCANNING;
                  detail = "";
                  activeClient.startScanning();
               } catch (Exception e) {
                  setError("Scan failed", e);
               }
            }
         });
      } else {
         state = NonIntifaceClient.State.DISABLED;
         detail = "";
      }
   }

   public static void stopAll() {
      executeSafely("Stop failed", () -> {
         suppressBaselineUntilInactive = true;
         baselineActive = false;
         oscillatorActive = false;
         specialVibratorActive = false;
         specialVibratorScalar = 0.0;
         energizedActive = false;
         energizedBasePercent = 0;
         energizedPulsePercent = 0;
         energizedPulse = 0.0;
         cancelPendingStop();
         cancelPendingStrokerReturn();
         stopAllInternal(true);
      });
   }

   public static void testPulse(NonConfig config, boolean peak) {
      if (peak) {
         pulsePeakReactiveImpact(config);
      } else {
         pulseReactiveImpact(config);
      }
   }

   public static void pulseReactiveImpact(NonConfig config) {
      pulseReactiveImpact(config, 0.0);
   }

   public static void pulseReactiveImpact(NonConfig config, double nextReactiveImpactDelayMs) {
      if (config != null && config.isIntifaceEnabled()) {
         queueReactiveImpact(config, false, nextReactiveImpactDelayMs);
      }
   }

   public static void pulsePeakReactiveImpact(NonConfig config) {
      pulsePeakReactiveImpact(config, 0.0);
   }

   public static void pulsePeakReactiveImpact(NonConfig config, double nextReactiveImpactDelayMs) {
      if (config != null && config.isIntifaceEnabled()) {
         queueReactiveImpact(config, true, nextReactiveImpactDelayMs);
      }
   }

   public static void clientTick(NonConfig config, boolean localAnimationActive, boolean peakStage) {
      clientTick(config, localAnimationActive, peakStage, Double.NaN);
   }

   public static void clientTick(NonConfig config, boolean localAnimationActive, boolean peakStage, double specialVibratorScalar) {
      clientTick(config, localAnimationActive, peakStage, specialVibratorScalar, 0, 0.0);
   }

   public static void clientTick(
      NonConfig config, boolean localAnimationActive, boolean peakStage, double specialVibratorScalar, int energizedLevel, double energizedHeartPulse
   ) {
      boolean enabled = config != null && config.isIntifaceEnabled();
      int strengthPercent = enabled ? config.getIntifaceAnimationBaselineStrengthPercent() : 0;
      int oscillatorRegularSpeedPercent = enabled ? config.getIntifaceOscillatorRegularSpeedPercent() : 0;
      int oscillatorPeakSpeedPercent = enabled ? config.getIntifaceOscillatorPeakSpeedPercent() : 0;
      double sanitizedSpecialScalar = sanitizeSpecialVibratorScalar(specialVibratorScalar);
      int sanitizedEnergizedLevel = enabled ? Math.max(0, Math.min(3, energizedLevel)) : 0;
      int energizedBase = sanitizedEnergizedLevel > 0 ? config.getIntifaceEnergizedBasePercent(sanitizedEnergizedLevel) : 0;
      int energizedPeak = sanitizedEnergizedLevel > 0 ? config.getIntifaceEnergizedPulsePercent(sanitizedEnergizedLevel) : 0;
      double sanitizedEnergizedPulse = sanitizeUnit(energizedHeartPulse);
      int pulsePermille = (int)Math.round(sanitizedEnergizedPulse * 1000.0);
      if (!localAnimationActive && suppressBaselineUntilInactive) {
         suppressBaselineUntilInactive = false;
      }

      if (localAnimationActive != requestedAnimationActive
         || peakStage != requestedPeakStage
         || strengthPercent != requestedBaselineStrengthPercent
         || oscillatorRegularSpeedPercent != requestedOscillatorRegularSpeedPercent
         || oscillatorPeakSpeedPercent != requestedOscillatorPeakSpeedPercent
         || !specialScalarEquals(sanitizedSpecialScalar, requestedSpecialVibratorScalar)
         || sanitizedEnergizedLevel != requestedEnergizedLevel
         || energizedBase != requestedEnergizedBasePercent
         || energizedPeak != requestedEnergizedPulsePercent
         || pulsePermille != requestedEnergizedPulsePermille
         || enabled != requestedIntifaceEnabled) {
         requestedAnimationActive = localAnimationActive;
         requestedPeakStage = peakStage;
         requestedIntifaceEnabled = enabled;
         requestedBaselineStrengthPercent = strengthPercent;
         requestedOscillatorRegularSpeedPercent = oscillatorRegularSpeedPercent;
         requestedOscillatorPeakSpeedPercent = oscillatorPeakSpeedPercent;
         requestedSpecialVibratorScalar = sanitizedSpecialScalar;
         requestedEnergizedLevel = sanitizedEnergizedLevel;
         requestedEnergizedBasePercent = energizedBase;
         requestedEnergizedPulsePercent = energizedPeak;
         requestedEnergizedPulsePermille = pulsePermille;
         executeSafely(
            "Update failed",
            () -> updateAnimationOutputs(
               enabled,
               localAnimationActive,
               peakStage,
               strengthPercent,
               oscillatorRegularSpeedPercent,
               oscillatorPeakSpeedPercent,
               sanitizedSpecialScalar,
               sanitizedEnergizedLevel,
               energizedBase,
               energizedPeak,
               sanitizedEnergizedPulse
            )
         );
      }
   }

   public static Text statusText(NonConfig config) {
      updateDisabledState(config);
      int devices = deviceCount();
      String suffix = detail != null && !detail.isBlank() ? " - " + detail : "";
      return Text.translatable("config.needsofnature.intiface.status." + state.name().toLowerCase(Locale.ROOT), new Object[]{devices})
         .append(Text.literal(suffix));
   }

   public static int deviceCount() {
      ButtplugClientWSClient activeClient = client;
      if (activeClient != null && activeClient.isConnected()) {
         try {
            return activeClient.getDevices().size();
         } catch (RuntimeException e) {
            return 0;
         }
      } else {
         return 0;
      }
   }

   public static boolean isConnected() {
      ButtplugClientWSClient activeClient = client;
      return activeClient != null && activeClient.isConnected();
   }

   private static void queueReactiveImpact(NonConfig config, boolean peak, double nextReactiveImpactDelayMs) {
      int strengthPercent = peak ? config.getIntifacePeakReactiveImpactStrengthPercent() : config.getIntifaceReactiveImpactStrengthPercent();
      int durationMs = peak ? config.getIntifacePeakReactiveImpactDurationMs() : config.getIntifaceReactiveImpactDurationMs();
      int cooldownMs = config.getIntifaceCooldownMs();
      double scalar = Math.max(sustainedVibratorScalar(), Math.max(0.0, Math.min(1.0, strengthPercent / 100.0)));
      int strokerMin = config.getIntifaceStrokerMinDistancePercent();
      int strokerMax = config.getIntifaceStrokerMaxDistancePercent();
      int strokerMoveDurationMs = peak ? config.getIntifaceStrokerPeakMoveDurationMs() : config.getIntifaceStrokerRegularMoveDurationMs();
      executeSafely("Pulse failed", () -> {
         ButtplugClientWSClient activeClient = client;
         if (activeClient != null && activeClient.isConnected()) {
            long now = System.currentTimeMillis();
            if (cooldownMs <= 0 || now - lastPulseMs >= cooldownMs) {
               boolean sentAny = false;
               if (!specialVibratorActive && scalar > 0.0 && durationMs > 0) {
                  sentAny = sendVibrate(activeClient, scalar) || sentAny;
                  long token = ++pulseToken;
                  cancelPendingStop();
                  pendingStop = EXECUTOR.schedule(() -> {
                     if (token == pulseToken) {
                        returnToBaselineOrStop(activeClient);
                        pendingStop = null;
                     }
                  }, durationMs, TimeUnit.MILLISECONDS);
               }

               sentAny = triggerStrokerImpact(activeClient, strokerMin, strokerMax, strokerMoveDurationMs, nextReactiveImpactDelayMs) || sentAny;
               if (sentAny) {
                  lastPulseMs = now;
               }
            }
         }
      });
   }

   private static void updateAnimationOutputs(
      boolean enabled,
      boolean localAnimationActive,
      boolean peakStage,
      int strengthPercent,
      int oscillatorRegularSpeedPercent,
      int oscillatorPeakSpeedPercent,
      double specialScalar,
      int energizedLevel,
      int energizedBaseStrengthPercent,
      int energizedPulseStrengthPercent,
      double energizedHeartPulse
   ) {
      if (!localAnimationActive) {
         suppressBaselineUntilInactive = false;
      }

      boolean shouldUseSpecialVibrator = enabled && localAnimationActive && !Double.isNaN(specialScalar) && !suppressBaselineUntilInactive;
      int clampedStrength = Math.max(0, Math.min(100, strengthPercent));
      boolean shouldUseBaseline = enabled && localAnimationActive && clampedStrength > 0 && !suppressBaselineUntilInactive && !shouldUseSpecialVibrator;
      baselineActive = shouldUseBaseline;
      baselineStrengthPercent = shouldUseBaseline ? clampedStrength : 0;
      specialVibratorActive = shouldUseSpecialVibrator;
      specialVibratorScalar = shouldUseSpecialVibrator ? Math.max(0.0, Math.min(1.0, specialScalar)) : 0.0;
      int clampedEnergizedBase = Math.max(0, Math.min(100, energizedBaseStrengthPercent));
      int clampedEnergizedPeak = Math.max(0, Math.min(100, energizedPulseStrengthPercent));
      boolean shouldUseEnergized = enabled && energizedLevel > 0 && Math.max(clampedEnergizedBase, clampedEnergizedPeak) > 0 && !shouldUseSpecialVibrator;
      energizedActive = shouldUseEnergized;
      energizedBasePercent = shouldUseEnergized ? clampedEnergizedBase : 0;
      energizedPulsePercent = shouldUseEnergized ? clampedEnergizedPeak : 0;
      energizedPulse = shouldUseEnergized ? sanitizeUnit(energizedHeartPulse) : 0.0;
      int requestedOscillatorSpeed = Math.max(0, Math.min(100, peakStage ? oscillatorPeakSpeedPercent : oscillatorRegularSpeedPercent));
      boolean shouldUseOscillator = enabled && localAnimationActive && requestedOscillatorSpeed > 0 && !suppressBaselineUntilInactive;
      oscillatorActive = shouldUseOscillator;
      oscillatorSpeedPercent = shouldUseOscillator ? requestedOscillatorSpeed : 0;
      if (shouldUseSpecialVibrator) {
         cancelPendingStop();
      }

      if (!shouldUseBaseline && !shouldUseOscillator && !shouldUseSpecialVibrator && !shouldUseEnergized) {
         cancelPendingStop();
         cancelPendingStrokerReturn();
         if (currentScalar > 0.0) {
            sendVibrate(client, 0.0);
         }

         if (currentOscillatorScalar > 0.0) {
            sendOscillate(client, 0.0);
         }
      } else {
         ButtplugClientWSClient activeClient = client;
         if (activeClient != null && activeClient.isConnected()) {
            if (pendingStop == null || shouldUseSpecialVibrator) {
               double vibrateScalar = shouldUseSpecialVibrator ? specialVibratorScalar : sustainedVibratorScalar();
               if (vibrateScalar > 0.0 && Math.abs(currentScalar - vibrateScalar) > 0.001) {
                  sendVibrate(activeClient, vibrateScalar);
               } else if (vibrateScalar <= 0.0 && currentScalar > 0.0) {
                  sendVibrate(activeClient, 0.0);
               }

               double oscillateScalar = oscillatorScalar();
               if (oscillateScalar > 0.0 && Math.abs(currentOscillatorScalar - oscillateScalar) > 0.001) {
                  sendOscillate(activeClient, oscillateScalar);
               } else if (oscillateScalar <= 0.0 && currentOscillatorScalar > 0.0) {
                  sendOscillate(activeClient, 0.0);
               }
            }
         }
      }
   }

   private static double baselineScalar() {
      return baselineActive && baselineStrengthPercent > 0 ? Math.max(0.0, Math.min(1.0, baselineStrengthPercent / 100.0)) : 0.0;
   }

   private static double energizedScalar() {
      if (!energizedActive) {
         return 0.0;
      }

      double base = Math.max(0.0, Math.min(1.0, energizedBasePercent / 100.0));
      double peak = Math.max(0.0, Math.min(1.0, energizedPulsePercent / 100.0));
      return base + (peak - base) * sanitizeUnit(energizedPulse);
   }

   private static double sustainedVibratorScalar() {
      return Math.max(baselineScalar(), energizedScalar());
   }

   private static double oscillatorScalar() {
      return oscillatorActive && oscillatorSpeedPercent > 0 ? Math.max(0.0, Math.min(1.0, oscillatorSpeedPercent / 100.0)) : 0.0;
   }

   private static double sanitizeSpecialVibratorScalar(double scalar) {
      if (Double.isNaN(scalar)) {
         return Double.NaN;
      } else {
         return Double.isInfinite(scalar) ? 0.0 : Math.max(0.0, Math.min(1.0, scalar));
      }
   }

   private static double sanitizeUnit(double value) {
      return !Double.isNaN(value) && !Double.isInfinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
   }

   private static boolean specialScalarEquals(double first, double second) {
      return !Double.isNaN(first) && !Double.isNaN(second) ? Math.abs(first - second) < 0.001 : Double.isNaN(first) && Double.isNaN(second);
   }

   private static void returnToBaselineOrStop(ButtplugClientWSClient activeClient) {
      double scalar = sustainedVibratorScalar();
      if (!(scalar > 0.0) || activeClient == null || !activeClient.isConnected() || !sendVibrate(activeClient, scalar)) {
         if (activeClient != null && activeClient.isConnected()) {
            sendVibrate(activeClient, 0.0);
         } else {
            currentScalar = 0.0;
         }
      }
   }

   private static void connectInternal(String url) {
      long attemptId = ++connectAttemptId;
      ButtplugClientWSClient activeClient = client;
      if (activeClient != null && activeClient.isConnected()) {
         state = NonIntifaceClient.State.CONNECTED;
         detail = "";
         NeedsOfNature.LOGGER.info("[NoN] Buttplug.io connect skipped: already connected. attempt={}", attemptId);
      } else {
         disconnectInternal();
         ButtplugClientWSClient nextClient = null;

         try {
            state = NonIntifaceClient.State.CONNECTING;
            detail = "Connecting to " + url;
            NeedsOfNature.LOGGER.info("[NoN] Buttplug.io connect started: attempt={} url={} timeout={}ms", new Object[]{attemptId, url, 8000});
            nextClient = new ButtplugClientWSClient("NeedsOfNature");
            nextClient.setDeviceAddedHandler(
               device -> {
                  if (attemptId == connectAttemptId) {
                     state = NonIntifaceClient.State.CONNECTED;
                     detail = Text.translatable("config.needsofnature.intiface.status.device_added", new Object[]{device.getDisplayName()}).getString();
                     updateAnimationOutputs(
                        requestedIntifaceEnabled,
                        requestedAnimationActive,
                        requestedPeakStage,
                        requestedBaselineStrengthPercent,
                        requestedOscillatorRegularSpeedPercent,
                        requestedOscillatorPeakSpeedPercent,
                        requestedSpecialVibratorScalar,
                        requestedEnergizedLevel,
                        requestedEnergizedBasePercent,
                        requestedEnergizedPulsePercent,
                        requestedEnergizedPulsePermille / 1000.0
                     );
                  }
               }
            );
            nextClient.setDeviceRemovedHandler(index -> {
               if (attemptId == connectAttemptId) {
                  state = NonIntifaceClient.State.CONNECTED;
                  detail = Text.translatable("config.needsofnature.intiface.status.device_removed").getString();
               }
            });
            nextClient.setScanningFinishedHandler(() -> {
               if (attemptId == connectAttemptId) {
                  state = NonIntifaceClient.State.CONNECTED;
                  detail = Text.translatable("config.needsofnature.intiface.status.scan_finished").getString();
               }
            });
            nextClient.setErrorHandler(error -> {
               if (attemptId == connectAttemptId) {
                  setError(error.getErrorMessage(), error.getException());
               }
            });
            ButtplugClientWSClient connectClient = nextClient;
            FutureTask<Void> connectTask = new FutureTask<>(() -> {
               connectClient.connect(new URI(url));
               return null;
            });
            Thread connectThread = new Thread(connectTask, "NoN Intiface Connect");
            connectThread.setDaemon(true);
            connectThread.start();
            connectTask.get(8000L, TimeUnit.MILLISECONDS);
            if (attemptId != connectAttemptId) {
               disconnectClient(nextClient);
               return;
            }

            client = nextClient;
            state = NonIntifaceClient.State.CONNECTED;
            detail = "";
            NeedsOfNature.LOGGER.info("[NoN] Buttplug.io connect succeeded: attempt={} url={}", attemptId, url);
            updateAnimationOutputs(
               requestedIntifaceEnabled,
               requestedAnimationActive,
               requestedPeakStage,
               requestedBaselineStrengthPercent,
               requestedOscillatorRegularSpeedPercent,
               requestedOscillatorPeakSpeedPercent,
               requestedSpecialVibratorScalar,
               requestedEnergizedLevel,
               requestedEnergizedBasePercent,
               requestedEnergizedPulsePercent,
               requestedEnergizedPulsePermille / 1000.0
            );
         } catch (TimeoutException e) {
            connectAttemptId++;
            NeedsOfNature.LOGGER.warn("[NoN] Buttplug.io connect timed out: attempt={} url={} timeout={}ms", new Object[]{attemptId, url, 8000});
            setError("Connect timed out after 8s", e);
            disconnectClient(nextClient);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            connectAttemptId++;
            NeedsOfNature.LOGGER.warn("[NoN] Buttplug.io connect interrupted: attempt={} url={}", new Object[]{attemptId, url, e});
            setError("Connect interrupted", e);
            disconnectClient(nextClient);
         } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            connectAttemptId++;
            handleConnectFailure(attemptId, url, cause);
            disconnectClient(nextClient);
         } catch (Exception e) {
            connectAttemptId++;
            handleConnectFailure(attemptId, url, e);
            disconnectClient(nextClient);
         } catch (Throwable e) {
            connectAttemptId++;
            handleConnectFailure(attemptId, url, e);
            disconnectClient(nextClient);
         }
      }
   }

   private static void handleConnectFailure(long attemptId, String url, Throwable throwable) {
      String expectedMessage = expectedConnectFailureMessage(throwable);
      if (expectedMessage != null) {
         NeedsOfNature.LOGGER.warn("[NoN] Buttplug.io connect failed: attempt={} url={} reason={}", new Object[]{attemptId, url, expectedMessage});
         setError(expectedMessage, null);
      } else {
         NeedsOfNature.LOGGER.warn("[NoN] Buttplug.io connect failed: attempt={} url={}", new Object[]{attemptId, url, throwable});
         setError("Connect failed: " + simpleThrowableName(throwable), throwable);
      }
   }

   private static String expectedConnectFailureMessage(Throwable throwable) {
      for (Throwable current = throwable; current != null; current = current.getCause()) {
         if (current instanceof ConnectException) {
            return "Could not reach Buttplug.io?";
         }

         if (current instanceof NoRouteToHostException || current instanceof UnknownHostException || current instanceof UnresolvedAddressException) {
            return "Could not reach Buttplug.io server";
         }

         String className = current.getClass().getSimpleName().toLowerCase(Locale.ROOT);
         if (className.contains("upgradeexception")) {
            return "Could not connect to Buttplug.io. Is Intiface Central running and using the configured WebSocket URL?";
         }

         String message = current.getMessage();
         if (message != null) {
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("connection refused")
               || lower.contains("failed to connect")
               || lower.contains("connection reset")
               || lower.contains("connection timed out")
               || lower.contains("connection timeout")
               || lower.contains("connect timed out")) {
               return "Could not reach Buttplug.io. Is Intiface Central running?";
            }

            if (lower.contains("failed to upgrade to websocket") || lower.contains("unexpected http response status code") || lower.contains("400 bad request")
               )
             {
               return "Could not connect to Buttplug.io. Is Intiface Central running and using the configured WebSocket URL?";
            }
         }
      }

      return null;
   }

   private static String simpleThrowableName(Throwable throwable) {
      return throwable == null ? "Unknown error" : throwable.getClass().getSimpleName();
   }

   private static boolean triggerStrokerImpact(
      ButtplugClientWSClient activeClient, int minDistancePercent, int maxDistancePercent, int moveDurationMs, double nextReactiveImpactDelayMs
   ) {
      if (activeClient != null && activeClient.isConnected()) {
         int min = Math.max(0, Math.min(100, Math.min(minDistancePercent, maxDistancePercent)));
         int max = Math.max(0, Math.min(100, Math.max(minDistancePercent, maxDistancePercent)));
         int durationMs = Math.max(0, Math.min(10000, moveDurationMs));
         if (durationMs > 0 && max > min) {
            double minPosition = min / 100.0;
            double maxPosition = max / 100.0;
            boolean sentAny = sendLinear(activeClient, maxPosition, durationMs);
            if (!sentAny) {
               return false;
            }

            cancelPendingStrokerReturn();
            long arrivalDelayMs = nextReactiveImpactDelayMs > 0.0 ? Math.max(0L, Math.round(nextReactiveImpactDelayMs / 2.0)) : 500L;
            long scheduleDelayMs = Math.max(durationMs, arrivalDelayMs - durationMs);
            pendingStrokerReturn = EXECUTOR.schedule(() -> {
               sendLinear(activeClient, minPosition, durationMs);
               pendingStrokerReturn = null;
            }, scheduleDelayMs, TimeUnit.MILLISECONDS);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean sendVibrate(ButtplugClientWSClient activeClient, double scalar) {
      List<ButtplugClientDevice> devices = activeClient.getDevices();
      if (devices.isEmpty()) {
         detail = Text.translatable("config.needsofnature.intiface.status.no_devices").getString();
         return false;
      }

      boolean sentAny = false;

      for (ButtplugClientDevice device : devices) {
         try {
            if (device.hasOutput(ButtplugOutput.VIBRATE)) {
               waitForDeviceCommands(device.runVibrateFloat((float)scalar));
               sentAny = true;
            }
         } catch (Exception e) {
            NeedsOfNature.LOGGER.debug("[NoN] Intiface pulse failed for device {}.", device.getDisplayName(), e);
         }
      }

      if (sentAny) {
         currentScalar = scalar;
      }

      if (!sentAny) {
         detail = Text.translatable("config.needsofnature.intiface.status.no_vibrate_devices").getString();
      }

      return sentAny;
   }

   private static boolean sendOscillate(ButtplugClientWSClient activeClient, double scalar) {
      if (activeClient != null && activeClient.isConnected()) {
         List<ButtplugClientDevice> devices = activeClient.getDevices();
         boolean sentAny = false;

         for (ButtplugClientDevice device : devices) {
            try {
               if (device.hasOutput(ButtplugOutput.OSCILLATE)) {
                  waitForDeviceCommands(device.runOscillateFloat((float)scalar));
                  sentAny = true;
               }
            } catch (Exception e) {
               NeedsOfNature.LOGGER.debug("[NoN] Intiface oscillate failed for device {}.", device.getDisplayName(), e);
            }
         }

         if (sentAny) {
            currentOscillatorScalar = scalar;
         }

         return sentAny;
      } else {
         currentOscillatorScalar = 0.0;
         return false;
      }
   }

   private static boolean sendLinear(ButtplugClientWSClient activeClient, double position, long durationMs) {
      if (activeClient != null && activeClient.isConnected()) {
         List<ButtplugClientDevice> devices = activeClient.getDevices();
         boolean sentAny = false;

         for (ButtplugClientDevice device : devices) {
            try {
               if (device.hasOutput(ButtplugOutput.HW_POSITION_WITH_DURATION)) {
                  waitForDeviceCommands(device.runHwPositionWithDurationFloat((float)position, (int)Math.max(0L, durationMs)));
               } else {
                  if (!device.hasOutput(ButtplugOutput.POSITION)) {
                     continue;
                  }

                  waitForDeviceCommands(device.runPositionFloat((float)position));
               }

               sentAny = true;
            } catch (Exception e) {
               NeedsOfNature.LOGGER.debug("[NoN] Intiface linear failed for device {}.", device.getDisplayName(), e);
            }
         }

         return sentAny;
      } else {
         return false;
      }
   }

   private static void waitForDeviceCommands(List<Future<ButtplugMessage>> futures) throws Exception {
      if (futures != null && !futures.isEmpty()) {
         for (Future<?> future : futures) {
            if (future != null) {
               future.get(750L, TimeUnit.MILLISECONDS);
            }
         }
      }
   }

   private static void stopAllInternal(boolean userVisible) {
      ButtplugClientWSClient activeClient = client;
      if (activeClient != null && activeClient.isConnected()) {
         try {
            activeClient.stopAllDevices();
            currentScalar = 0.0;
            currentOscillatorScalar = 0.0;
            if (userVisible) {
               detail = Text.translatable("config.needsofnature.intiface.status.stopped").getString();
            }
         } catch (Exception e) {
            setError("Stop failed", e);
         }
      } else {
         currentScalar = 0.0;
         currentOscillatorScalar = 0.0;
      }
   }

   private static void disconnectInternal() {
      ButtplugClientWSClient activeClient = client;
      client = null;
      currentScalar = 0.0;
      currentOscillatorScalar = 0.0;
      if (activeClient != null) {
         try {
            activeClient.disconnect();
         } catch (RuntimeException e) {
            NeedsOfNature.LOGGER.debug("[NoN] Intiface disconnect failed.", e);
         }
      }
   }

   private static void disconnectClient(ButtplugClientWSClient targetClient) {
      if (targetClient != null) {
         if (client == targetClient) {
            client = null;
         }

         currentScalar = 0.0;
         currentOscillatorScalar = 0.0;

         try {
            targetClient.disconnect();
         } catch (RuntimeException e) {
            NeedsOfNature.LOGGER.debug("[NoN] Intiface disconnect failed.", e);
         }
      }
   }

   private static void cancelPendingStop() {
      ScheduledFuture<?> stop = pendingStop;
      pendingStop = null;
      if (stop != null) {
         stop.cancel(false);
      }
   }

   private static void cancelPendingStrokerReturn() {
      ScheduledFuture<?> stop = pendingStrokerReturn;
      pendingStrokerReturn = null;
      if (stop != null) {
         stop.cancel(false);
      }
   }

   private static void setError(String message, Throwable throwable) {
      state = NonIntifaceClient.State.ERROR;
      detail = message != null && !message.isBlank() ? message : "Unknown error";
      if (throwable != null) {
         NeedsOfNature.LOGGER.debug("[NoN] Intiface error: {}", detail, throwable);
      } else {
         NeedsOfNature.LOGGER.debug("[NoN] Intiface error: {}", detail);
      }
   }

   private static void executeSafely(String errorMessage, Runnable action) {
      EXECUTOR.execute(() -> {
         try {
            action.run();
         } catch (Throwable throwable) {
            NeedsOfNature.LOGGER.warn("[NoN] Buttplug.io worker failure: {}", errorMessage, throwable);
            setError(errorMessage + ": " + throwable.getClass().getSimpleName(), throwable);
         }
      });
   }

   private static void updateDisabledState(NonConfig config) {
      if (config != null && !config.isIntifaceEnabled()) {
         state = NonIntifaceClient.State.DISABLED;
         detail = "";
      }
   }

   private static void clearBaselineState() {
      requestedAnimationActive = false;
      requestedPeakStage = false;
      requestedIntifaceEnabled = false;
      requestedBaselineStrengthPercent = Integer.MIN_VALUE;
      requestedOscillatorRegularSpeedPercent = Integer.MIN_VALUE;
      requestedOscillatorPeakSpeedPercent = Integer.MIN_VALUE;
      requestedSpecialVibratorScalar = Double.NaN;
      requestedEnergizedLevel = 0;
      requestedEnergizedBasePercent = 0;
      requestedEnergizedPulsePercent = 0;
      requestedEnergizedPulsePermille = 0;
      baselineActive = false;
      baselineStrengthPercent = 0;
      oscillatorActive = false;
      oscillatorSpeedPercent = 0;
      specialVibratorActive = false;
      specialVibratorScalar = 0.0;
      energizedActive = false;
      energizedBasePercent = 0;
      energizedPulsePercent = 0;
      energizedPulse = 0.0;
      currentScalar = 0.0;
      currentOscillatorScalar = 0.0;
      suppressBaselineUntilInactive = false;
   }

   private enum State {
      DISABLED,
      DISCONNECTED,
      CONNECTING,
      CONNECTED,
      SCANNING,
      ERROR;
   }
}
