package com.nonid.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class NonModMenuIntegration implements ModMenuApi {
   public ConfigScreenFactory<?> getModConfigScreenFactory() {
      return NonModMenuScreens::createConfigScreen;
   }
}
