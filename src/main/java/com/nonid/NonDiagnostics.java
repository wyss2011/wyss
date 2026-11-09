package com.nonid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

public final class NonDiagnostics {
   private static final boolean RUNTIME_LOGS = Boolean.getBoolean("needsofnature.runtimeLogs");
   private static final int MAX_PENDING_SETUP_CHAT_MESSAGES = 100;
   private static final Map<UUID, NonDebugChatMode> DEBUG_CHAT_PREFERENCES = new ConcurrentHashMap<>();
   private static final List<NonDiagnostics.PendingSetupChatMessage> PENDING_SETUP_CHAT = new ArrayList<>();
   private static boolean initialized;

   private NonDiagnostics() {
   }

   public static synchronized void initialize() {
      if (!initialized) {
         initialized = true;
         ServerTickEvents.END_SERVER_TICK.register(NonDiagnostics::flushPendingSetupChat);
         ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> DEBUG_CHAT_PREFERENCES.remove(handler.player.getUuid()));
         ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> {
            DEBUG_CHAT_PREFERENCES.clear();
            synchronized (PENDING_SETUP_CHAT) {
               PENDING_SETUP_CHAT.clear();
            }
         });
      }
   }

   public static void setDebugChatPreference(UUID playerUuid, @Nullable NonDebugChatMode mode) {
      if (playerUuid != null) {
         DEBUG_CHAT_PREFERENCES.put(playerUuid, mode == null ? NonDebugChatMode.SETUP_ERRORS : mode);
      }
   }

   public static boolean shouldSendDebugChat(@Nullable ServerPlayerEntity player, @Nullable NonDebugChatCategory category) {
      if (player == null) {
         return false;
      }

      NonDebugChatMode mode = DEBUG_CHAT_PREFERENCES.getOrDefault(player.getUuid(), NonDebugChatMode.SETUP_ERRORS);
      return mode.allows(category == null ? NonDebugChatCategory.INFO : category);
   }

   public static boolean shouldSendDebugChat(@Nullable ServerPlayerEntity player, @Nullable NonDebugChatCategory category, boolean force) {
      return force || shouldSendDebugChat(player, category);
   }

   public static void sendDebugChat(ServerPlayerEntity player, NonDebugChatCategory category, Text message, boolean force) {
      if (message != null && shouldSendDebugChat(player, category, force)) {
         player.sendMessage(message.copy().formatted(chatColor(category)), false);
      }
   }

   public static void logSetupWarning(String template, Object... args) {
      NeedsOfNature.LOGGER.warn(template, args);
      queueSetupChat(formatLogTemplate(template, args));
   }

   public static void logSetupError(String template, Object... args) {
      NeedsOfNature.LOGGER.error(template, args);
      queueSetupChat(formatLogTemplate(template, args));
   }

   public static void logRuntime(String template, Object... args) {
      if (RUNTIME_LOGS) {
         NeedsOfNature.LOGGER.info(template, args);
      } else {
         NeedsOfNature.LOGGER.debug(template, args);
      }
   }

   public static void queueSetupChat(String message) {
      if (message != null && !message.isBlank()) {
         synchronized (PENDING_SETUP_CHAT) {
            if (PENDING_SETUP_CHAT.size() >= 100) {
               PENDING_SETUP_CHAT.removeFirst();
            }

            PENDING_SETUP_CHAT.add(new NonDiagnostics.PendingSetupChatMessage(Text.literal(message), new HashSet<>()));
         }
      }
   }

   public static String formatLogTemplate(String template, Object... args) {
      if (template == null) {
         return "";
      }

      if (args != null && args.length != 0) {
         StringBuilder out = new StringBuilder(template.length() + args.length * 8);
         int argIndex = 0;

         for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < template.length() && template.charAt(i + 1) == '}' && argIndex < args.length) {
               Object arg = args[argIndex++];
               if (!(arg instanceof Throwable)) {
                  out.append(String.valueOf(arg));
               }

               i++;
            } else {
               out.append(c);
            }
         }

         return out.toString();
      } else {
         return template;
      }
   }

   public static Formatting chatColor(@Nullable NonDebugChatCategory category) {
      if (category == null) {
         return Formatting.WHITE;
      }

      return switch (category) {
         case ALWAYS -> Formatting.DARK_GRAY;
         case SETUP -> Formatting.YELLOW;
         case WARNING -> Formatting.LIGHT_PURPLE;
         case ERROR -> Formatting.RED;
         case INFO -> Formatting.WHITE;
      };
   }

   private static void flushPendingSetupChat(MinecraftServer server) {
      if (server != null) {
         List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
         if (!players.isEmpty()) {
            synchronized (PENDING_SETUP_CHAT) {
               PENDING_SETUP_CHAT.removeIf(message -> {
                  for (ServerPlayerEntity player : players) {
                     if (player != null && !message.sentTo().contains(player.getUuid())) {
                        sendDebugChat(player, NonDebugChatCategory.SETUP, message.message(), false);
                        message.sentTo().add(player.getUuid());
                     }
                  }

                  return message.sentTo().containsAll(players.stream().map(Entity::getUuid).toList());
               });
            }
         }
      }
   }

   private record PendingSetupChatMessage(Text message, Set<UUID> sentTo) {
   }
}
