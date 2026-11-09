package com.nonid.client;

import com.nonid.GenderHolder;
import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import com.nonid.integration.NonWildfireGenderConfigCompat;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public final class NonWildfireGenderSync {
   private static final String MOD_ID = "wildfire_gender";
   private static final float WILDFIRE_NATIVE_BREAST_HEIGHT_MIN = -1.0F;
   private static final int RECONCILIATION_RETRY_INTERVAL_TICKS = 5;
   private static final int RECONCILIATION_MAX_ATTEMPTS = 40;
   private static final int RECONCILIATION_VERIFY_DELAY_TICKS = 20;
   private static boolean initialized;
   private static boolean failed;
   private static boolean loggedFailure;
   private static boolean breastTextureEditorInitialized;
   private static boolean loggedBreastTextureEditorFailure;
   private static Method getPlayerByIdMethod;
   private static Method updateGenderMethod;
   private static Method updateBustSizeMethod;
   private static Method updateBreastPhysicsMethod;
   private static Method updateBounceMultiplierMethod;
   private static Method updateFloppinessMethod;
   private static Method getBreastsMethod;
   private static Method updateXOffsetMethod;
   private static Method updateYOffsetMethod;
   private static Method updateZOffsetMethod;
   private static Method updateCleavageMethod;
   private static Method updateUniboobMethod;
   private static Method getBustSizeMethod;
   private static Method hasBreastPhysicsMethod;
   private static Method getBounceMultiplierMethod;
   private static Method getFloppinessMethod;
   private static Method getXOffsetMethod;
   private static Method getYOffsetMethod;
   private static Method getZOffsetMethod;
   private static Method getCleavageMethod;
   private static Method isUniboobMethod;
   private static Method getLeftBreastPhysicsMethod;
   private static Method getRightBreastPhysicsMethod;
   private static Method renderPlayerInFrameMethod;
   private static Method saveMethod;
   private static Field bustSizeField;
   private static Field breastPhysicsField;
   private static Field bounceMultiplierField;
   private static Field floppyMultiplierField;
   private static Field xOffsetField;
   private static Field yOffsetField;
   private static Field zOffsetField;
   private static Field cleavageField;
   private static Field uniboobField;
   private static Field physicsBreastSizeField;
   private static Field physicsPreBreastSizeField;
   private static Constructor<?> breastCustomizationScreenConstructor;
   private static Constructor<?> breastTextureEditorScreenConstructor;
   private static Object wildfireMaleGender;
   private static Object wildfireFemaleGender;
   private static Object wildfireOtherGender;
   private static NonWildfireGenderSync.ReconciliationPhase reconciliationPhase = NonWildfireGenderSync.ReconciliationPhase.IDLE;
   private static UUID reconciliationPlayerUuid;
   private static int reconciliationDelayTicks;
   private static int reconciliationAttemptsRemaining;

   private NonWildfireGenderSync() {
   }

   public static boolean isAvailable() {
      return FabricLoader.getInstance().isModLoaded("wildfire_gender");
   }

   public static boolean shouldLockWildfireGenderControls(NonConfig config) {
      return config != null && config.isSyncFemaleGenderModWithNonGender() && isAvailable();
   }

   public static boolean shouldLockWildfirePersonalizationControls(NonConfig config) {
      return config != null && config.isDestroyedSkinSystemEnabled() && config.isDestroyedSkinFemaleGenderOverridesEnabled() && isAvailable();
   }

   public static void syncFromNonConfig(NonConfig config) {
      if (config != null && config.isSyncFemaleGenderModWithNonGender() && isAvailable()) {
         requestReconciliation(config);
         syncFromSelection(config, config.getPlayerGenderSelection());
      }
   }

   public static void syncFromMask(NonConfig config, int mask) {
      if (config != null && config.isSyncFemaleGenderModWithNonGender() && isAvailable()) {
         requestReconciliation(config);
         syncFromSelection(config, NonConfig.PlayerGenderSelection.fromMask(mask, config.getPlayerGenderSelection()));
      }
   }

   public static void syncDestroyedSkinOverrides(NonConfig config) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null) {
         syncDestroyedSkinOverrides(config, NonDestroyedSkinClient.getStage(client.player.getUuid()));
      }
   }

   public static void syncDestroyedSkinOverrides(NonConfig config, int destroyedStage) {
      if (config != null && config.isDestroyedSkinSystemEnabled() && config.isDestroyedSkinFemaleGenderOverridesEnabled() && isAvailable()) {
         requestReconciliation(config);
         if (init()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
               NonConfig.FemaleGenderDestroyedProfile profile = destroyedStage >= 2
                  ? config.getFemaleGenderDestroyedHigh()
                  : config.getFemaleGenderDestroyedLow();

               try {
                  Object playerConfig = getPlayerByIdMethod.invoke(null, client.player.getUuid());
                  if (playerConfig == null) {
                     return;
                  }

                  applyProfile(playerConfig, profile, true);
               } catch (ReflectiveOperationException | RuntimeException e) {
                  logFailure(e);
               }
            }
         }
      }
   }

   public static void onWorldJoin() {
      requestReconciliation(NeedsOfNature.getLocalConfig());
   }

   public static void onWorldDisconnect() {
      clearReconciliation();
   }

   private static void clearReconciliation() {
      reconciliationPhase = NonWildfireGenderSync.ReconciliationPhase.IDLE;
      reconciliationPlayerUuid = null;
      reconciliationDelayTicks = 0;
      reconciliationAttemptsRemaining = 0;
   }

   public static void clientTick(MinecraftClient client) {
      if (reconciliationPhase != NonWildfireGenderSync.ReconciliationPhase.IDLE && client != null && client.world != null && client.player != null) {
         UUID playerUuid = client.player.getUuid();
         if (!playerUuid.equals(reconciliationPlayerUuid)) {
            reconciliationPlayerUuid = playerUuid;
            reconciliationPhase = NonWildfireGenderSync.ReconciliationPhase.WAITING_FOR_PROFILE;
            reconciliationDelayTicks = 0;
            reconciliationAttemptsRemaining = 40;
         }

         if (reconciliationDelayTicks > 0) {
            reconciliationDelayTicks--;
         } else {
            NonWildfireGenderSync.ReconciliationResult result = reconcileLocalState(client, NeedsOfNature.getLocalConfig());
            if (result == NonWildfireGenderSync.ReconciliationResult.FAILED) {
               clearReconciliation();
            } else if (result == NonWildfireGenderSync.ReconciliationResult.NOT_READY) {
               reconciliationAttemptsRemaining--;
               if (reconciliationAttemptsRemaining <= 0) {
                  clearReconciliation();
               } else {
                  reconciliationDelayTicks = 5;
               }
            } else if (reconciliationPhase == NonWildfireGenderSync.ReconciliationPhase.WAITING_FOR_PROFILE) {
               reconciliationPhase = NonWildfireGenderSync.ReconciliationPhase.VERIFYING;
               reconciliationDelayTicks = 20;
            } else {
               clearReconciliation();
            }
         }
      }
   }

   private static void requestReconciliation(NonConfig config) {
      if (isAvailable() && managesWildfireState(config)) {
         MinecraftClient client = MinecraftClient.getInstance();
         reconciliationPlayerUuid = client != null && client.player != null ? client.player.getUuid() : null;
         reconciliationPhase = NonWildfireGenderSync.ReconciliationPhase.WAITING_FOR_PROFILE;
         reconciliationDelayTicks = 0;
         reconciliationAttemptsRemaining = 40;
      }
   }

   private static boolean managesWildfireState(NonConfig config) {
      return config != null
         && (config.isSyncFemaleGenderModWithNonGender() || config.isDestroyedSkinSystemEnabled() && config.isDestroyedSkinFemaleGenderOverridesEnabled());
   }

   private static NonWildfireGenderSync.ReconciliationResult reconcileLocalState(MinecraftClient client, NonConfig config) {
      if (!managesWildfireState(config)) {
         return NonWildfireGenderSync.ReconciliationResult.READY;
      }

      if (!init()) {
         return NonWildfireGenderSync.ReconciliationResult.FAILED;
      }

      try {
         Object playerConfig = getPlayerByIdMethod.invoke(null, client.player.getUuid());
         if (playerConfig == null) {
            return NonWildfireGenderSync.ReconciliationResult.NOT_READY;
         }

         boolean changed = false;
         if (config.isSyncFemaleGenderModWithNonGender()) {
            int mask = config.getPlayerGenderMask() & 3;
            if (client.player instanceof GenderHolder holder) {
               int syncedMask = holder.getGenderMask() & 3;
               if (syncedMask != 0) {
                  mask = syncedMask;
               }
            }

            NonConfig.PlayerGenderSelection selection = NonConfig.PlayerGenderSelection.fromMask(mask, config.getPlayerGenderSelection());
            changed |= Boolean.TRUE.equals(updateGenderMethod.invoke(playerConfig, toWildfireGender(selection)));
         }

         if (config.isDestroyedSkinSystemEnabled() && config.isDestroyedSkinFemaleGenderOverridesEnabled()) {
            int destroyedStage = NonDestroyedSkinClient.getStage(client.player.getUuid());
            NonConfig.FemaleGenderDestroyedProfile profile = destroyedStage >= 2 ? config.getFemaleGenderDestroyedHigh() : config.getFemaleGenderDestroyedLow();
            changed |= updateProfile(playerConfig, profile);
         }

         if (changed) {
            saveMethod.invoke(playerConfig);
         }

         return NonWildfireGenderSync.ReconciliationResult.READY;
      } catch (ReflectiveOperationException | RuntimeException e) {
         logFailure(e);
         return NonWildfireGenderSync.ReconciliationResult.FAILED;
      }
   }

   public static boolean openBreastCustomizationScreen(Screen parent) {
      if (isAvailable() && init()) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null && breastCustomizationScreenConstructor != null) {
            try {
               if (breastCustomizationScreenConstructor.newInstance(parent, client.player.getUuid()) instanceof Screen minecraftScreen) {
                  client.setScreen(minecraftScreen);
                  return true;
               }
            } catch (ReflectiveOperationException | RuntimeException e) {
               logFailure(e);
            }

            return false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean canOpenBreastTextureEditor() {
      return isAvailable() && initBreastTextureEditor();
   }

   public static boolean openBreastTextureEditor(Screen parent) {
      if (isAvailable() && initBreastTextureEditor()) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null && breastTextureEditorScreenConstructor != null) {
            try {
               if (breastTextureEditorScreenConstructor.newInstance(parent, client.player.getUuid()) instanceof Screen minecraftScreen) {
                  client.setScreen(minecraftScreen);
                  return true;
               }
            } catch (ReflectiveOperationException | RuntimeException e) {
               logBreastTextureEditorFailure(e);
            }

            return false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean renderBreastPreview(
      Screen parent, DrawContext context, NonConfig.FemaleGenderDestroyedProfile profile, int centerX, int frameY, int mouseX, int mouseY
   ) {
      if (parent != null && context != null && profile != null && isAvailable() && init()) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null && breastCustomizationScreenConstructor != null && renderPlayerInFrameMethod != null) {
            try {
               Object playerConfig = getPlayerByIdMethod.invoke(null, client.player.getUuid());
               if (playerConfig == null) {
                  return false;
               }

               NonConfig.FemaleGenderDestroyedProfile previous = readProfile(playerConfig);
               NonWildfireGenderSync.BreastPhysicsSnapshot physicsSnapshot = snapshotBreastPhysics(playerConfig);

               try {
                  applyProfile(playerConfig, profile, false);
                  applyBreastPhysicsSize(playerConfig, profile.breastSize());
                  Object screen = breastCustomizationScreenConstructor.newInstance(parent, client.player.getUuid());
                  renderPlayerInFrameMethod.invoke(screen, context, centerX, frameY, mouseX, mouseY);
               } finally {
                  applyProfile(playerConfig, previous, false);
                  if (physicsSnapshot != null) {
                     physicsSnapshot.restore();
                  }
               }

               return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
               logFailure(e);
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean applyProfile(Object playerConfig, NonConfig.FemaleGenderDestroyedProfile profile, boolean save) throws ReflectiveOperationException {
      if (!save) {
         applyProfileFields(playerConfig, profile);
         return true;
      }

      boolean changed = updateProfile(playerConfig, profile);
      if (changed) {
         saveMethod.invoke(playerConfig);
      }

      return changed;
   }

   private static boolean updateProfile(Object playerConfig, NonConfig.FemaleGenderDestroyedProfile profile) throws ReflectiveOperationException {
      boolean changed = false;
      changed |= applyBreastSize(playerConfig, profile.breastSize());
      changed |= Boolean.TRUE.equals(updateBreastPhysicsMethod.invoke(playerConfig, profile.breastPhysics()));
      changed |= Boolean.TRUE.equals(updateBounceMultiplierMethod.invoke(playerConfig, profile.intensity()));
      changed |= Boolean.TRUE.equals(updateFloppinessMethod.invoke(playerConfig, profile.momentum()));
      Object breasts = getBreastsMethod.invoke(playerConfig);
      if (breasts != null) {
         changed |= Boolean.TRUE.equals(updateXOffsetMethod.invoke(breasts, profile.separation()));
         changed |= applyBreastHeight(breasts, profile.height());
         changed |= Boolean.TRUE.equals(updateZOffsetMethod.invoke(breasts, profile.depth()));
         changed |= Boolean.TRUE.equals(updateCleavageMethod.invoke(breasts, profile.rotation()));
         changed |= Boolean.TRUE.equals(updateUniboobMethod.invoke(breasts, !profile.dualPhysics()));
      }

      return changed;
   }

   private static boolean applyBreastSize(Object playerConfig, float breastSize) throws ReflectiveOperationException {
      float supportedSize = NonWildfireGenderConfigCompat.clampBreastSize(breastSize);
      return Boolean.TRUE.equals(updateBustSizeMethod.invoke(playerConfig, supportedSize));
   }

   private static boolean applyBreastHeight(Object breasts, float height) throws ReflectiveOperationException {
      if (height >= -1.0F) {
         return Boolean.TRUE.equals(updateYOffsetMethod.invoke(breasts, height));
      }

      if (Float.compare(yOffsetField.getFloat(breasts), height) == 0) {
         return false;
      }

      yOffsetField.setFloat(breasts, height);
      return true;
   }

   private static void applyProfileFields(Object playerConfig, NonConfig.FemaleGenderDestroyedProfile profile) throws ReflectiveOperationException {
      bustSizeField.setFloat(playerConfig, profile.breastSize());
      breastPhysicsField.setBoolean(playerConfig, profile.breastPhysics());
      bounceMultiplierField.setFloat(playerConfig, profile.intensity());
      floppyMultiplierField.setFloat(playerConfig, profile.momentum());
      Object breasts = getBreastsMethod.invoke(playerConfig);
      if (breasts != null) {
         xOffsetField.setFloat(breasts, profile.separation());
         yOffsetField.setFloat(breasts, profile.height());
         zOffsetField.setFloat(breasts, profile.depth());
         cleavageField.setFloat(breasts, profile.rotation());
         uniboobField.setBoolean(breasts, !profile.dualPhysics());
      }
   }

   private static NonConfig.FemaleGenderDestroyedProfile readProfile(Object playerConfig) throws ReflectiveOperationException {
      Object breasts = getBreastsMethod.invoke(playerConfig);
      float rotation = 0.0F;
      float height = 0.0F;
      float depth = 0.0F;
      float separation = 0.0F;
      boolean dualPhysics = false;
      if (breasts != null) {
         separation = ((Number)getXOffsetMethod.invoke(breasts)).floatValue();
         height = ((Number)getYOffsetMethod.invoke(breasts)).floatValue();
         depth = ((Number)getZOffsetMethod.invoke(breasts)).floatValue();
         rotation = ((Number)getCleavageMethod.invoke(breasts)).floatValue();
         dualPhysics = !Boolean.TRUE.equals(isUniboobMethod.invoke(breasts));
      }

      return new NonConfig.FemaleGenderDestroyedProfile(
         ((Number)getBustSizeMethod.invoke(playerConfig)).floatValue(),
         separation,
         height,
         depth,
         rotation,
         Boolean.TRUE.equals(hasBreastPhysicsMethod.invoke(playerConfig)),
         dualPhysics,
         ((Number)getBounceMultiplierMethod.invoke(playerConfig)).floatValue(),
         ((Number)getFloppinessMethod.invoke(playerConfig)).floatValue()
      );
   }

   private static NonWildfireGenderSync.BreastPhysicsSnapshot snapshotBreastPhysics(Object playerConfig) throws ReflectiveOperationException {
      Object left = getLeftBreastPhysicsMethod.invoke(playerConfig);
      Object right = getRightBreastPhysicsMethod.invoke(playerConfig);
      return left != null && right != null
         ? new NonWildfireGenderSync.BreastPhysicsSnapshot(
            left,
            right,
            physicsBreastSizeField.getFloat(left),
            physicsPreBreastSizeField.getFloat(left),
            physicsBreastSizeField.getFloat(right),
            physicsPreBreastSizeField.getFloat(right)
         )
         : null;
   }

   private static void applyBreastPhysicsSize(Object playerConfig, float breastSize) throws ReflectiveOperationException {
      setBreastPhysicsSize(getLeftBreastPhysicsMethod.invoke(playerConfig), breastSize);
      setBreastPhysicsSize(getRightBreastPhysicsMethod.invoke(playerConfig), breastSize);
   }

   private static void setBreastPhysicsSize(Object breastPhysics, float breastSize) throws IllegalAccessException {
      if (breastPhysics != null) {
         physicsBreastSizeField.setFloat(breastPhysics, breastSize);
         physicsPreBreastSizeField.setFloat(breastPhysics, breastSize);
      }
   }

   private static void syncFromSelection(NonConfig config, NonConfig.PlayerGenderSelection selection) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null) {
         if (init()) {
            try {
               UUID playerUuid = client.player.getUuid();
               Object playerConfig = getPlayerByIdMethod.invoke(null, playerUuid);
               if (playerConfig == null) {
                  return;
               }

               Object targetGender = toWildfireGender(selection);
               Object changed = updateGenderMethod.invoke(playerConfig, targetGender);
               if (Boolean.TRUE.equals(changed)) {
                  saveMethod.invoke(playerConfig);
               }
            } catch (ReflectiveOperationException | RuntimeException e) {
               logFailure(e);
            }
         }
      }
   }

   private static Object toWildfireGender(NonConfig.PlayerGenderSelection selection) {
      if (selection == NonConfig.PlayerGenderSelection.MALE) {
         return wildfireMaleGender;
      } else {
         return selection == NonConfig.PlayerGenderSelection.BOTH ? wildfireOtherGender : wildfireFemaleGender;
      }
   }

   private static boolean init() {
      if (initialized) {
         return !failed;
      }

      initialized = true;
      if (!isAvailable()) {
         failed = true;
         return false;
      }

      try {
         Class<?> apiClass = Class.forName("com.wildfire.api.WildfireAPI");
         Class<?> playerConfigClass = Class.forName("com.wildfire.main.entitydata.PlayerConfig");
         Class<?> entityConfigClass = Class.forName("com.wildfire.main.entitydata.EntityConfig");
         Class<?> breastsClass = Class.forName("com.wildfire.main.entitydata.Breasts");
         Class<?> genderClass = Class.forName("com.wildfire.main.config.enums.Gender");
         Class<?> breastPhysicsClass = Class.forName("com.wildfire.physics.BreastPhysics");
         Class<?> breastCustomizationScreenClass = Class.forName("com.wildfire.gui.screen.WildfireBreastCustomizationScreen");
         getPlayerByIdMethod = apiClass.getMethod("getPlayerById", UUID.class);
         updateGenderMethod = playerConfigClass.getMethod("updateGender", genderClass);
         updateBustSizeMethod = playerConfigClass.getMethod("updateBustSize", float.class);
         updateBreastPhysicsMethod = playerConfigClass.getMethod("updateBreastPhysics", boolean.class);
         updateBounceMultiplierMethod = playerConfigClass.getMethod("updateBounceMultiplier", float.class);
         updateFloppinessMethod = playerConfigClass.getMethod("updateFloppiness", float.class);
         getBreastsMethod = playerConfigClass.getMethod("getBreasts");
         getBustSizeMethod = playerConfigClass.getMethod("getBustSize");
         hasBreastPhysicsMethod = playerConfigClass.getMethod("hasBreastPhysics");
         getBounceMultiplierMethod = playerConfigClass.getMethod("getBounceMultiplier");
         getFloppinessMethod = playerConfigClass.getMethod("getFloppiness");
         updateXOffsetMethod = breastsClass.getMethod("updateXOffset", float.class);
         updateYOffsetMethod = breastsClass.getMethod("updateYOffset", float.class);
         updateZOffsetMethod = breastsClass.getMethod("updateZOffset", float.class);
         updateCleavageMethod = breastsClass.getMethod("updateCleavage", float.class);
         updateUniboobMethod = breastsClass.getMethod("updateUniboob", boolean.class);
         getXOffsetMethod = breastsClass.getMethod("getXOffset");
         getYOffsetMethod = breastsClass.getMethod("getYOffset");
         getZOffsetMethod = breastsClass.getMethod("getZOffset");
         getCleavageMethod = breastsClass.getMethod("getCleavage");
         isUniboobMethod = breastsClass.getMethod("isUniboob");
         getLeftBreastPhysicsMethod = entityConfigClass.getMethod("getLeftBreastPhysics");
         getRightBreastPhysicsMethod = entityConfigClass.getMethod("getRightBreastPhysics");
         saveMethod = playerConfigClass.getMethod("save");
         breastCustomizationScreenConstructor = breastCustomizationScreenClass.getConstructor(Screen.class, UUID.class);
         Class<?> baseWildfireScreenClass = Class.forName("com.wildfire.gui.screen.BaseWildfireScreen");
         renderPlayerInFrameMethod = baseWildfireScreenClass.getDeclaredMethod(
            "renderPlayerInFrame", DrawContext.class, int.class, int.class, int.class, int.class
         );
         renderPlayerInFrameMethod.setAccessible(true);
         bustSizeField = accessibleField(entityConfigClass, "pBustSize");
         breastPhysicsField = accessibleField(entityConfigClass, "breastPhysics");
         bounceMultiplierField = accessibleField(entityConfigClass, "bounceMultiplier");
         floppyMultiplierField = accessibleField(entityConfigClass, "floppyMultiplier");
         xOffsetField = accessibleField(breastsClass, "xOffset");
         yOffsetField = accessibleField(breastsClass, "yOffset");
         zOffsetField = accessibleField(breastsClass, "zOffset");
         cleavageField = accessibleField(breastsClass, "cleavage");
         uniboobField = accessibleField(breastsClass, "uniboob");
         physicsBreastSizeField = accessibleField(breastPhysicsClass, "breastSize");
         physicsPreBreastSizeField = accessibleField(breastPhysicsClass, "preBreastSize");
         wildfireMaleGender = enumConstant(genderClass, "MALE");
         wildfireFemaleGender = enumConstant(genderClass, "FEMALE");
         wildfireOtherGender = enumConstant(genderClass, "OTHER");
         return true;
      } catch (ReflectiveOperationException | RuntimeException e) {
         failed = true;
         logFailure(e);
         return false;
      }
   }

   private static synchronized boolean initBreastTextureEditor() {
      if (breastTextureEditorInitialized) {
         return breastTextureEditorScreenConstructor != null;
      }

      breastTextureEditorInitialized = true;

      try {
         Class<?> screenClass = Class.forName("com.wildfire.gui.screen.WildfireBreastUVEditorScreen");
         breastTextureEditorScreenConstructor = screenClass.getConstructor(Screen.class, UUID.class);
         return true;
      } catch (ReflectiveOperationException | RuntimeException e) {
         logBreastTextureEditorFailure(e);
         return false;
      }
   }

   private static Object enumConstant(Class<?> enumClass, String name) {
      return Enum.valueOf(enumClass.asSubclass(Enum.class), name);
   }

   private static Field accessibleField(Class<?> owner, String name) throws NoSuchFieldException {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      return field;
   }

   private static void logFailure(Exception e) {
      if (!loggedFailure) {
         loggedFailure = true;
         NeedsOfNature.LOGGER.warn("[NoN] Failed to integrate with Female Gender Mod.", e);
      }
   }

   private static void logBreastTextureEditorFailure(Exception e) {
      if (!loggedBreastTextureEditorFailure) {
         loggedBreastTextureEditorFailure = true;
         NeedsOfNature.LOGGER.warn("[NoN] Female Gender Mod breast texture editor is unavailable.", e);
      }
   }

   private static final class BreastPhysicsSnapshot {
      private final Object left;
      private final Object right;
      private final float leftSize;
      private final float leftPreSize;
      private final float rightSize;
      private final float rightPreSize;

      private BreastPhysicsSnapshot(Object left, Object right, float leftSize, float leftPreSize, float rightSize, float rightPreSize) {
         this.left = left;
         this.right = right;
         this.leftSize = leftSize;
         this.leftPreSize = leftPreSize;
         this.rightSize = rightSize;
         this.rightPreSize = rightPreSize;
      }

      private void restore() throws IllegalAccessException {
         NonWildfireGenderSync.physicsBreastSizeField.setFloat(this.left, this.leftSize);
         NonWildfireGenderSync.physicsPreBreastSizeField.setFloat(this.left, this.leftPreSize);
         NonWildfireGenderSync.physicsBreastSizeField.setFloat(this.right, this.rightSize);
         NonWildfireGenderSync.physicsPreBreastSizeField.setFloat(this.right, this.rightPreSize);
      }
   }

   private enum ReconciliationPhase {
      IDLE,
      WAITING_FOR_PROFILE,
      VERIFYING;
   }

   private enum ReconciliationResult {
      READY,
      NOT_READY,
      FAILED;
   }
}
