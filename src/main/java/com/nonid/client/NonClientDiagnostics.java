package com.nonid.client;

import com.nonid.NeedsOfNature;
import com.nonid.NonDebugChatCategory;
import com.nonid.NonDiagnostics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class NonClientDiagnostics {
   private NonClientDiagnostics() {
   }

   public static boolean shouldSend(NonDebugChatCategory category) {
      return NeedsOfNature.getConfig().allowsDebugChat(category);
   }

   public static void send(NonDebugChatCategory category, Text message, boolean withPrefix) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null && message != null) {
         if (shouldSend(category)) {
            Text output = (Text)(withPrefix ? Text.translatable("debug.needsofnature.prefix", new Object[]{message}) : message);
            client.player.sendMessage(output.copy().formatted(NonDiagnostics.chatColor(category)), false);
         }
      }
   }

   public static void sendSetupWarning(String message, boolean withPrefix) {
      if (message != null && !message.isBlank()) {
         send(NonDebugChatCategory.SETUP, Text.literal(message), withPrefix);
      }
   }
}
