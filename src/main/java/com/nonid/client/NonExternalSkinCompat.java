package com.nonid.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

final class NonExternalSkinCompat {
   private NonExternalSkinCompat() {
   }

   @Nullable
   static NonExternalSkinCompat.SkinContext resolve(MinecraftClient client, UUID playerUuid, @Nullable Identifier expectedTexture) {
      return client != null && playerUuid != null ? selectMatching(expectedTexture, candidates(client, playerUuid)) : null;
   }

   @Nullable
   static NonExternalSkinCompat.SkinContext resolveSelected(MinecraftClient client, UUID playerUuid) {
      if (client != null && playerUuid != null) {
         List<NonExternalSkinCompat.SkinContext> candidates = candidates(client, playerUuid);
         return candidates.isEmpty() ? null : candidates.getFirst();
      } else {
         return null;
      }
   }

   private static List<NonExternalSkinCompat.SkinContext> candidates(MinecraftClient client, UUID playerUuid) {
      String playerName = playerName(client, playerUuid);
      List<NonExternalSkinCompat.SkinContext> candidates = new ArrayList<>(2);
      NonSkinOverrideCompat.SkinSelection skinOverride = NonSkinOverrideCompat.getSelectedSkin(playerName, playerUuid);
      if (skinOverride != null) {
         candidates.add(
            new NonExternalSkinCompat.SkinContext(
               "skin_overrides",
               skinOverride.textureId(),
               skinOverride.selectionId(),
               skinOverride.friendlyName(),
               skinOverride.folderName(),
               skinOverride.sourcePath(),
               skinOverride.sourceFingerprint()
            )
         );
      }

      NonQuickSkinCompat.SkinSelection quickSkin = NonQuickSkinCompat.getSelectedSkin(playerUuid);
      if (quickSkin != null) {
         candidates.add(
            new NonExternalSkinCompat.SkinContext(
               "quickskin", quickSkin.textureId(), quickSkin.skinId(), quickSkin.friendlyName(), quickSkin.folderName(), null, "skin=" + quickSkin.skinId()
            )
         );
      }

      return candidates;
   }

   @Nullable
   static NonExternalSkinCompat.SkinContext selectMatching(@Nullable Identifier expectedTexture, List<NonExternalSkinCompat.SkinContext> candidates) {
      if (candidates != null && !candidates.isEmpty()) {
         if (expectedTexture == null) {
            return candidates.getFirst();
         }

         for (NonExternalSkinCompat.SkinContext candidate : candidates) {
            if (expectedTexture.equals(candidate.textureId())) {
               return candidate;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static String playerName(MinecraftClient client, UUID playerUuid) {
      if (client.player != null && playerUuid.equals(client.player.getUuid())) {
         return client.player.getGameProfile().name();
      }

      if (client.world != null) {
         PlayerEntity player = client.world.getPlayerByUuid(playerUuid);
         if (player != null) {
            return player.getGameProfile().name();
         }
      }

      return "";
   }

   record SkinContext(
      String providerId,
      Identifier textureId,
      String selectionId,
      @Nullable String friendlyName,
      @Nullable String folderName,
      @Nullable Path sourcePath,
      String sourceFingerprint
   ) {
      String cacheKey() {
         return this.providerId + "\u0000" + this.textureId + "\u0000" + this.selectionId + "\u0000" + this.sourceFingerprint;
      }

      String selectionKey() {
         return this.providerId + "\u0000" + this.selectionId + "\u0000" + this.sourceFingerprint;
      }

      String displayName() {
         return this.friendlyName != null ? this.friendlyName : this.selectionId;
      }
   }
}
