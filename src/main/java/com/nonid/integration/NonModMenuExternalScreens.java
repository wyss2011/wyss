package com.nonid.integration;

import com.nonid.NeedsOfNature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

public final class NonModMenuExternalScreens {
   private NonModMenuExternalScreens() {
   }

   public static void openPlayerPreferences(Screen parent) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null) {
         client.setScreen(new NonModMenuPlayerScreens.PlayerPreferencesScreen(parent, NeedsOfNature.getLocalConfig()));
      }
   }
}
