package com.nonid.integration;

import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import com.nonid.client.NonServerSettingsClient;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

final class NonModMenuScreens {
   private static final Map<NonConfig, Long> SERVER_DRAFT_REVISIONS = Collections.synchronizedMap(new WeakHashMap<>());

   private NonModMenuScreens() {
   }

   static Tooltip tooltip(String key) {
      return Tooltip.of(Text.translatable(key));
   }

   static Tooltip tooltip(Text text) {
      return Tooltip.of(text);
   }

   static void setTooltip(ClickableWidget widget, String key) {
      if (widget != null) {
         widget.setTooltip(tooltip(key));
         widget.setTooltipDelay(Duration.ofMillis(500L));
      }
   }

   static void setTooltip(ClickableWidget widget, Tooltip tooltip) {
      if (widget != null) {
         widget.setTooltip(tooltip);
         widget.setTooltipDelay(Duration.ofMillis(500L));
      }
   }

   static boolean canEditServerGameplaySettings() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return true;
      } else {
         return client.world == null ? true : NonServerSettingsClient.hasConfigSnapshot() && (client.getServer() != null || NonServerSettingsClient.canEdit());
      }
   }

   static boolean canEditLocalPackLoadOrder() {
      MinecraftClient client = MinecraftClient.getInstance();
      return client == null || client.world == null || client.getServer() != null;
   }

   static NonConfig serverConfigForScreen() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.world != null) {
         NonConfig draft = NonServerSettingsClient.createServerConfigDraft();
         SERVER_DRAFT_REVISIONS.put(draft, NonServerSettingsClient.configRevision());
         return draft;
      } else {
         return NeedsOfNature.getLocalConfig();
      }
   }

   static void saveServerConfig(NonConfig config) {
      if (config != null) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client == null || client.world == null) {
            config.save();
         } else if (canEditServerGameplaySettings()) {
            long expectedRevision = SERVER_DRAFT_REVISIONS.getOrDefault(config, NonServerSettingsClient.configRevision());
            NonServerSettingsClient.sendConfigUpdate(config, expectedRevision);
            SERVER_DRAFT_REVISIONS.put(config, expectedRevision + 1L);
         }
      }
   }

   static Screen createConfigScreen(Screen parent) {
      NonServerSettingsClient.requestRefresh();
      return new NonModMenuScreens.ConfigCategoryScreen(parent, NeedsOfNature.getLocalConfig());
   }

   private static class ConfigCategoryScreen extends Screen {
      private final Screen parent;
      private final NonConfig config;

      protected ConfigCategoryScreen(Screen parent, NonConfig config) {
         super(Text.translatable("config.needsofnature.categories_title"));
         this.parent = parent;
         this.config = config;
      }

      protected void init() {
         int centerX = this.width / 2;
         int y = this.height / 2 - 36;
         int gap = 24;
         ButtonWidget togglesButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.category.toggles"),
               button -> MinecraftClient.getInstance()
                  .setScreen(new NonModMenuSystemSettingsScreens.ToggleSettingsScreen(this, NonModMenuScreens.serverConfigForScreen()))
            )
            .dimensions(centerX - 100, y, 200, 20)
            .build();
         NonModMenuScreens.setTooltip(togglesButton, "config.needsofnature.tooltip.category.toggles");
         this.addDrawableChild(togglesButton);
         y += gap;
         ButtonWidget playerButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.category.player_preferences"),
               button -> MinecraftClient.getInstance().setScreen(new NonModMenuPlayerScreens.PlayerPreferencesScreen(this, this.config))
            )
            .dimensions(centerX - 100, y, 200, 20)
            .build();
         NonModMenuScreens.setTooltip(playerButton, "config.needsofnature.tooltip.category.player_preferences");
         this.addDrawableChild(playerButton);
         y += gap;
         ButtonWidget settingsButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.category.settings"),
               button -> MinecraftClient.getInstance().setScreen(new NonModMenuScreens.SettingsCategoryScreen(this, this.config))
            )
            .dimensions(centerX - 100, y, 200, 20)
            .build();
         NonModMenuScreens.setTooltip(settingsButton, "config.needsofnature.tooltip.category.settings");
         this.addDrawableChild(settingsButton);
         ButtonWidget doneButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.done"), button -> MinecraftClient.getInstance().setScreen(this.parent)
            )
            .dimensions(centerX - 100, this.height - 28, 200, 20)
            .build();
         this.addDrawableChild(doneButton);
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         super.render(context, mouseX, mouseY, deltaTicks);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 16777215);
      }

      public void close() {
         MinecraftClient.getInstance().setScreen(this.parent);
      }
   }

   static final class FlatTextButton extends ClickableWidget {
      private final NonModMenuScreens.FlatTextButton.FlatPressAction onPress;

      FlatTextButton(int width, Text text, NonModMenuScreens.FlatTextButton.FlatPressAction onPress) {
         super(0, 0, width, 12, text);
         this.onPress = onPress;
      }

      protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
         if (renderer != null) {
            int rgb = this.active ? (this.isSelected() ? 16777215 : 13684944) : 8421504;
            int alpha = Math.max(0, Math.min(255, (int)(this.getAlpha() * 255.0F)));
            int color = alpha << 24 | rgb;
            int textX = this.getX() + (this.getWidth() - renderer.getWidth(this.getMessage())) / 2;
            int textY = this.getY() + (this.getHeight() - 8) / 2;
            context.drawTextWithShadow(renderer, this.getMessage(), textX, textY, color);
         }
      }

      public void onClick(Click click, boolean doubled) {
         super.onClick(click, doubled);
         if (this.active && this.onPress != null) {
            this.onPress.onPress(this);
         }
      }

      protected void appendClickableNarrations(NarrationMessageBuilder builder) {
         this.appendDefaultNarrations(builder);
      }

      @FunctionalInterface
      interface FlatPressAction {
         void onPress(NonModMenuScreens.FlatTextButton var1);
      }
   }

   private static class SettingsCategoryScreen extends Screen {
      private final Screen parent;
      private final NonConfig config;

      protected SettingsCategoryScreen(Screen parent, NonConfig config) {
         super(Text.translatable("config.needsofnature.settings_title"));
         this.parent = parent;
         this.config = config;
      }

      protected void init() {
         NonConfig serverConfig = NonModMenuScreens.serverConfigForScreen();
         int centerX = this.width / 2;
         int y = this.height / 2 - 58;
         int gap = 24;
         ButtonWidget gameplayButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.category.gameplay"),
               button -> MinecraftClient.getInstance().setScreen(new NonModMenuGameplayScreens.GameplayCategoryScreen(this, serverConfig))
            )
            .dimensions(centerX - 100, y, 200, 20)
            .build();
         NonModMenuScreens.setTooltip(gameplayButton, "config.needsofnature.tooltip.category.gameplay");
         this.addDrawableChild(gameplayButton);
         y += gap;
         ButtonWidget vanillaOverridesButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.category.vanilla_overrides"),
               button -> MinecraftClient.getInstance().setScreen(new NonModMenuSystemSettingsScreens.VanillaOverrideScreen(this, serverConfig))
            )
            .dimensions(centerX - 100, y, 200, 20)
            .build();
         vanillaOverridesButton.active = serverConfig.isVanillaOverridesEnabled();
         NonModMenuScreens.setTooltip(
            vanillaOverridesButton,
            vanillaOverridesButton.active
               ? "config.needsofnature.tooltip.category.vanilla_overrides"
               : "config.needsofnature.tooltip.category.disabled_in_toggles"
         );
         this.addDrawableChild(vanillaOverridesButton);
         y += gap;
         ButtonWidget liquidButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.category.liquid"),
               button -> MinecraftClient.getInstance().setScreen(new NonModMenuSystemSettingsScreens.LiquidSettingsScreen(this, serverConfig))
            )
            .dimensions(centerX - 100, y, 200, 20)
            .build();
         liquidButton.active = serverConfig.isLiquidTankEnabled();
         NonModMenuScreens.setTooltip(
            liquidButton, liquidButton.active ? "config.needsofnature.tooltip.category.liquid" : "config.needsofnature.tooltip.category.disabled_in_toggles"
         );
         this.addDrawableChild(liquidButton);
         y += gap;
         ButtonWidget pregnancyButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.category.pregnancy"),
               button -> MinecraftClient.getInstance().setScreen(new NonModMenuSystemSettingsScreens.PregnancySettingsScreen(this, serverConfig))
            )
            .dimensions(centerX - 100, y, 200, 20)
            .build();
         pregnancyButton.active = serverConfig.isPregnancyEnabled();
         NonModMenuScreens.setTooltip(
            pregnancyButton,
            pregnancyButton.active ? "config.needsofnature.tooltip.category.pregnancy" : "config.needsofnature.tooltip.category.disabled_in_toggles"
         );
         this.addDrawableChild(pregnancyButton);
         y += gap;
         ButtonWidget debugButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.category.debug_advanced"),
               button -> MinecraftClient.getInstance().setScreen(new NonModMenuDebugScreens.DebugAdvancedScreen(this, serverConfig, this.config))
            )
            .dimensions(centerX - 100, y, 200, 20)
            .build();
         NonModMenuScreens.setTooltip(debugButton, "config.needsofnature.tooltip.category.debug_advanced");
         this.addDrawableChild(debugButton);
         ButtonWidget doneButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.done"), button -> MinecraftClient.getInstance().setScreen(this.parent)
            )
            .dimensions(centerX - 100, this.height - 28, 200, 20)
            .build();
         this.addDrawableChild(doneButton);
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         super.render(context, mouseX, mouseY, deltaTicks);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 16777215);
      }

      public void close() {
         MinecraftClient.getInstance().setScreen(this.parent);
      }
   }
}
