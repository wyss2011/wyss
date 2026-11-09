package com.nonid.client;

import com.nonid.NeedsOfNature;
import java.lang.reflect.Method;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

final class NonQuickSkinCompat {
   private static final String MOD_ID = "quickskin";
   private static boolean initialized;
   private static boolean failed;
   private static Method appearanceServiceGetInstance;
   private static Method getAppearance;
   private static Method getSkinId;
   private static Method assetManagerGetInstance;
   private static Method getMetadata;
   private static Method getFriendlyName;

   private NonQuickSkinCompat() {
   }

   @Nullable
   static NonQuickSkinCompat.SkinSelection getSelectedSkin(UUID playerUuid) {
      if (playerUuid != null && initialize()) {
         try {
            Object appearanceService = appearanceServiceGetInstance.invoke(null);
            Object appearance = getAppearance.invoke(appearanceService, playerUuid);
            if (appearance == null) {
               return null;
            }

            String skinId = (String)getSkinId.invoke(appearance);
            String assetHash = assetHash(skinId);
            if (assetHash == null) {
               return null;
            }

            Identifier textureId = Identifier.tryParse(skinId);
            if (textureId == null) {
               return null;
            }

            Object assetManager = assetManagerGetInstance.invoke(null);
            Object metadata = getMetadata.invoke(assetManager, assetHash);
            if (metadata == null) {
               return null;
            }

            String friendlyName = (String)getFriendlyName.invoke(metadata);
            return friendlyName != null && !friendlyName.isBlank()
               ? new NonQuickSkinCompat.SkinSelection(textureId, skinId, friendlyName, NonSkinFolderNames.sanitize(friendlyName))
               : null;
         } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            fail(e);
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean initialize() {
      if (initialized) {
         return !failed;
      }

      initialized = true;
      if (!FabricLoader.getInstance().isModLoaded("quickskin")) {
         failed = true;
         return false;
      }

      try {
         ClassLoader loader = NonQuickSkinCompat.class.getClassLoader();
         Class<?> appearanceServiceClass = Class.forName("com.quickskin.mod.client.services.PlayerAppearanceService", false, loader);
         Class<?> appearanceClass = Class.forName("com.quickskin.mod.common.data.PlayerAppearance", false, loader);
         Class<?> assetManagerClass = Class.forName("com.quickskin.mod.client.services.LocalAssetManager", false, loader);
         Class<?> metadataClass = Class.forName("com.quickskin.mod.common.data.AssetMetadata", false, loader);
         appearanceServiceGetInstance = appearanceServiceClass.getMethod("getInstance");
         getAppearance = appearanceServiceClass.getMethod("getAppearance", UUID.class);
         getSkinId = appearanceClass.getMethod("getSkinId");
         assetManagerGetInstance = assetManagerClass.getMethod("getInstance");
         getMetadata = assetManagerClass.getMethod("getMetadata", String.class);
         getFriendlyName = metadataClass.getMethod("friendlyName");
         return true;
      } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
         fail(e);
         return false;
      }
   }

   @Nullable
   private static String assetHash(@Nullable String skinId) {
      if (skinId != null && !skinId.isBlank()) {
         int separator = skinId.lastIndexOf(58);
         String hash = separator >= 0 ? skinId.substring(separator + 1) : skinId;
         return hash.isBlank() ? null : hash;
      } else {
         return null;
      }
   }

   private static void fail(Throwable error) {
      if (!failed) {
         failed = true;
         NeedsOfNature.LOGGER.warn("[NoN] Quick Skin name integration is unavailable; using the global ripped-skin configuration.", error);
      }
   }

   record SkinSelection(Identifier textureId, String skinId, String friendlyName, String folderName) {
      String cacheKey() {
         return this.skinId + "\u0000" + this.folderName;
      }
   }
}
