package com.nonid.client;

import com.nonid.NeedsOfNature;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

final class NonSkinOverrideCompat {
   private static final String MOD_ID = "skin_overrides";
   private static boolean initialized;
   private static boolean failed;
   private static Object skinOverrideManager;
   private static Constructor<?> partialProfileConstructor;
   private static Method getOverride;
   private static Method getCacheVersion;
   private static Method getTexture;
   private static Method getTexturePath;
   private static Method getPlayerIdent;
   private static Class<?> libraryOverrideClass;
   private static Method getLibraryEntry;
   private static Field libraryEntryFile;
   private static Method getEntryName;
   private static Method getEntryId;

   private NonSkinOverrideCompat() {
   }

   @Nullable
   static NonSkinOverrideCompat.SkinSelection getSelectedSkin(String playerName, UUID playerUuid) {
      if (playerName != null && !playerName.isBlank() && playerUuid != null && initialize()) {
         try {
            Object profile = partialProfileConstructor.newInstance(playerName, playerUuid);
            if (getOverride.invoke(skinOverrideManager, profile) instanceof Optional<?> optional && !optional.isEmpty()) {
               Object override = optional.get();
               if (getTexture.invoke(override) instanceof Identifier textureId) {
                  Path sourcePath = null;
                  String friendlyName = null;
                  String selectionId = String.valueOf(getPlayerIdent.invoke(override));
                  if (libraryOverrideClass.isInstance(override)) {
                     Object entry = getLibraryEntry.invoke(override);
                     if (libraryEntryFile.get(entry) instanceof File file) {
                        sourcePath = file.toPath().toAbsolutePath().normalize();
                     }

                     Object nameValue = getEntryName.invoke(entry);
                     Object idValue = getEntryId.invoke(entry);
                     if (nameValue instanceof String name && !name.isBlank()) {
                        friendlyName = name;
                     }

                     if (idValue instanceof String id && !id.isBlank()) {
                        selectionId = id;
                     }
                  } else if (getTexturePath.invoke(override) instanceof File file) {
                     sourcePath = file.toPath().toAbsolutePath().normalize();
                  }

                  String sourceFingerprint = sourceFingerprint(sourcePath);
                  return new NonSkinOverrideCompat.SkinSelection(
                     textureId,
                     selectionId,
                     friendlyName,
                     friendlyName != null ? NonSkinFolderNames.sanitize(friendlyName) : null,
                     sourcePath,
                     sourceFingerprint
                  );
               } else {
                  return null;
               }
            } else {
               return null;
            }
         } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            fail(e);
            return null;
         }
      } else {
         return null;
      }
   }

   static int cacheVersion() {
      if (!initialize()) {
         return -1;
      }

      try {
         return ((Number)getCacheVersion.invoke(null)).intValue();
      } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
         fail(e);
         return -1;
      }
   }

   private static boolean initialize() {
      if (initialized) {
         return !failed;
      }

      initialized = true;
      if (!FabricLoader.getInstance().isModLoaded("skin_overrides")) {
         failed = true;
         return false;
      }

      try {
         ClassLoader loader = NonSkinOverrideCompat.class.getClassLoader();
         Class<?> overridesClass = Class.forName("net.orifu.skin_overrides.Overrides", false, loader);
         Class<?> managerClass = Class.forName("net.orifu.skin_overrides.OverrideManager", false, loader);
         Class<?> overrideClass = Class.forName("net.orifu.skin_overrides.OverrideManager$Override", false, loader);
         Class<?> profileClass = Class.forName("net.orifu.skin_overrides.profile.Profile", false, loader);
         Class<?> partialProfileClass = Class.forName("net.orifu.skin_overrides.profile.PartialProfile", false, loader);
         libraryOverrideClass = Class.forName("net.orifu.skin_overrides.override.LibraryOverrider$LibraryOverride", false, loader);
         Class<?> libraryEntryClass = Class.forName("net.orifu.skin_overrides.Library$LibraryEntry", false, loader);
         Class<?> abstractLibraryEntryClass = Class.forName("net.orifu.skin_overrides.library.AbstractLibrary$AbstractLibraryEntry", false, loader);
         Field skinsField = overridesClass.getField("SKINS");
         skinOverrideManager = skinsField.get(null);
         partialProfileConstructor = partialProfileClass.getConstructor(String.class, UUID.class);
         getOverride = managerClass.getMethod("get", profileClass);
         getCacheVersion = overridesClass.getMethod("cacheVersion");
         getTexture = overrideClass.getMethod("texture");
         getTexturePath = overrideClass.getMethod("texturePath");
         getPlayerIdent = overrideClass.getMethod("playerIdent");
         getLibraryEntry = libraryOverrideClass.getMethod("entry");
         libraryEntryFile = abstractLibraryEntryClass.getDeclaredField("file");
         libraryEntryFile.setAccessible(true);
         getEntryName = libraryEntryClass.getMethod("getName");
         getEntryId = libraryEntryClass.getMethod("getId");
         return true;
      } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
         fail(e);
         return false;
      }
   }

   private static String sourceFingerprint(@Nullable Path sourcePath) {
      if (sourcePath == null) {
         return "";
      }

      long size = -1L;
      long modified = -1L;

      try {
         if (Files.isRegularFile(sourcePath)) {
            size = Files.size(sourcePath);
            modified = Files.getLastModifiedTime(sourcePath).toMillis();
         }
      } catch (RuntimeException | IOException var6) {
      }

      return "path=" + sourcePath + "|size=" + size + "|modified=" + modified;
   }

   private static void fail(Throwable error) {
      if (!failed) {
         failed = true;
         NeedsOfNature.LOGGER.warn("[NoN] Skin Override integration is unavailable; using the global ripped-skin configuration.", error);
      }
   }

   record SkinSelection(
      Identifier textureId, String selectionId, @Nullable String friendlyName, @Nullable String folderName, @Nullable Path sourcePath, String sourceFingerprint
   ) {
   }
}
