package com.nonid.client;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import com.nonid.network.AnimationCatalogData;
import com.nonid.network.HostConfigSyncData;
import com.nonid.network.ServerConfigRequestC2SPayload;
import com.nonid.network.ServerConfigResultS2CPayload;
import com.nonid.network.ServerConfigUpdateC2SPayload;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class NonServerSettingsClient {
   private static final Gson GSON = new Gson();
   private static volatile boolean canEdit;
   private static volatile long configRevision = -1L;
   private static volatile NonServerSettingsClient.HostConfigSnapshot editableSnapshot;
   private static volatile long catalogRevision = -1L;
   private static volatile List<AnimationCatalogData.Entry> animationCatalog = List.of();
   private static NonServerSettingsClient.CatalogTransfer catalogTransfer;

   private NonServerSettingsClient() {
   }

   public static void applyConfigSnapshot(String editableJson, long revision, boolean editable) {
      if (revision >= configRevision) {
         try {
            HostConfigSyncData data = (HostConfigSyncData)GSON.fromJson(editableJson, HostConfigSyncData.class);
            if (data == null) {
               return;
            }

            editableSnapshot = new NonServerSettingsClient.HostConfigSnapshot(data);
            configRevision = revision;
            canEdit = editable;
         } catch (JsonParseException | IllegalStateException e) {
            NeedsOfNature.LOGGER.warn("[NoN] Failed to parse editable server config snapshot.", e);
         }
      }
   }

   public static NonConfig createServerConfigDraft() {
      NonConfig draft = NeedsOfNature.getLocalConfig().copy();
      NonServerSettingsClient.HostConfigSnapshot snapshot = editableSnapshot;
      if (snapshot != null) {
         snapshot.data().applyEditableTo(draft);
      }

      return draft;
   }

   public static boolean canEdit() {
      return canEdit;
   }

   public static long configRevision() {
      return configRevision;
   }

   public static boolean hasConfigSnapshot() {
      return editableSnapshot != null;
   }

   public static void requestRefresh() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.getNetworkHandler() != null) {
         ClientPlayNetworking.send(new ServerConfigRequestC2SPayload());
      }
   }

   public static void sendConfigUpdate(NonConfig config, long expectedRevision) {
      if (config != null && canEdit) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.getNetworkHandler() != null) {
            String json = GSON.toJson(HostConfigSyncData.fromEditableConfig(config));
            ClientPlayNetworking.send(new ServerConfigUpdateC2SPayload(expectedRevision, json));
         }
      }
   }

   public static void acceptCatalogChunk(long revision, int index, int count, String data) {
      if (index >= 0 && count > 0 && index < count && count <= 4096 && data != null) {
         if (revision >= catalogRevision) {
            if (index == 0) {
               catalogTransfer = new NonServerSettingsClient.CatalogTransfer(revision, new String[count]);
            } else if (catalogTransfer == null || catalogTransfer.revision != revision || catalogTransfer.chunks.length != count) {
               return;
            }

            NonServerSettingsClient.CatalogTransfer transfer = catalogTransfer;
            if (transfer != null && transfer.revision == revision && transfer.chunks.length == count) {
               transfer.chunks[index] = data;

               for (String chunk : transfer.chunks) {
                  if (chunk == null) {
                     return;
                  }
               }

               try {
                  StringBuilder joined = new StringBuilder();
                  Arrays.stream(transfer.chunks).forEach(joined::append);
                  AnimationCatalogData catalog = (AnimationCatalogData)GSON.fromJson(joined.toString(), AnimationCatalogData.class);
                  animationCatalog = catalog != null && catalog.animations() != null ? List.copyOf(catalog.animations()) : List.of();
                  catalogRevision = revision;
               } catch (JsonParseException | IllegalStateException e) {
                  NeedsOfNature.LOGGER.warn("[NoN] Failed to parse server animation catalog.", e);
               } finally {
                  catalogTransfer = null;
               }
            }
         }
      }
   }

   public static boolean hasAnimationCatalog() {
      return catalogRevision >= 0L;
   }

   public static long catalogRevision() {
      return catalogRevision;
   }

   public static List<AnimationCatalogData.Entry> animationCatalogSnapshot() {
      return animationCatalog;
   }

   public static void handleConfigResult(ServerConfigResultS2CPayload payload) {
      if (payload != null) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.inGameHud != null) {
            String key = payload.messageKey();
            if (key != null && !key.isBlank()) {
               client.inGameHud.setOverlayMessage(Text.translatable(key), false);
            }
         }
      }
   }

   public static void clear() {
      canEdit = false;
      configRevision = -1L;
      editableSnapshot = null;
      catalogRevision = -1L;
      animationCatalog = List.of();
      catalogTransfer = null;
   }

   private static final class CatalogTransfer {
      private final long revision;
      private final String[] chunks;

      private CatalogTransfer(long revision, String[] chunks) {
         this.revision = revision;
         this.chunks = chunks;
      }
   }

   private record HostConfigSnapshot(HostConfigSyncData data) {
   }
}
