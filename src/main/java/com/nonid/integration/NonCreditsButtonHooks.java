package com.nonid.integration;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class NonCreditsButtonHooks {
   private static final int BUTTON_WIDTH = 52;
   private static final int EDGE_MARGIN = 8;
   private static boolean registered;

   private NonCreditsButtonHooks() {
   }

   public static void register() {
      if (!registered) {
         registered = true;
         ScreenEvents.AFTER_INIT
            .register(
               (AfterInit)(client, screen, scaledWidth, scaledHeight) -> {
                  if (isNoNSettingsScreen(screen)) {
                     NonModMenuScreens.FlatTextButton creditsButton = new NonModMenuScreens.FlatTextButton(
                        52, Text.translatable("config.needsofnature.category.credits"), button -> client.setScreen(new NonCreditsScreen(screen))
                     );
                     creditsButton.setX(scaledWidth - 52 - 8);
                     creditsButton.setY(scaledHeight - creditsButton.getHeight() - 8);
                     creditsButton.setAlpha(0.7F);
                     NonModMenuScreens.setTooltip(creditsButton, "config.needsofnature.tooltip.category.credits");
                     Screens.getButtons(screen).add(creditsButton);
                  }
               }
            );
      }
   }

   private static boolean isNoNSettingsScreen(Screen screen) {
      return screen != null && screen.getClass().getPackageName().equals(NonCreditsButtonHooks.class.getPackageName()) && !(screen instanceof NonCreditsScreen);
   }
}
