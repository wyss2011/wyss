package com.nonid.integration;

import com.nonid.NonConfig;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class NonModMenuIntifaceTuningScreen extends Screen {
   private final Screen parent;
   private final NonConfig config;
   private final NonConfig defaults = new NonConfig();
   private int latencyCompensationMs;
   private FloatValueSliderWidget latencyCompensationSlider;
   private ButtonWidget resetLatencyCompensationButton;

   NonModMenuIntifaceTuningScreen(Screen parent, NonConfig config) {
      super(Text.translatable("config.needsofnature.intiface_toy_title"));
      this.parent = parent;
      this.config = config;
      this.loadFromConfig();
   }

   protected void init() {
      int listTop = 32;
      int bottomArea = 40;
      int listHeight = Math.max(0, this.height - listTop - bottomArea);
      SettingsList settingsList = new SettingsList(this.client, this.width, listHeight, listTop);
      this.addDrawableChild(settingsList);
      settingsList.addEntryRow(SettingsList.RowEntry.sectionHeader(this.textRenderer, Text.translatable("config.needsofnature.section.intiface_timing")));
      this.latencyCompensationSlider = new FloatValueSliderWidget(0, 0, 130, 20, this.latencyCompensationMs, 0.0F, 1000.0F, 10.0F, value -> {
         this.latencyCompensationMs = (int)Math.round(value);
         this.updateResetButton();
      }, value -> String.format(Locale.ROOT, "%dms early", Math.round(value)));
      this.resetLatencyCompensationButton = ButtonWidget.builder(Text.translatable("config.needsofnature.reset_icon"), ignored -> {
         this.latencyCompensationMs = this.defaults.getIntifaceLatencyCompensationMs();
         this.latencyCompensationSlider.setFloatValue(this.latencyCompensationMs);
         this.updateResetButton();
      }).dimensions(0, 0, 20, 20).build();
      NonModMenuScreens.setTooltip(this.resetLatencyCompensationButton, "config.needsofnature.tooltip.reset");
      settingsList.addEntryRow(
         SettingsList.RowEntry.labeledField(
            this.textRenderer,
            Text.translatable("config.needsofnature.intiface_latency_compensation"),
            this.latencyCompensationSlider,
            this.resetLatencyCompensationButton,
            NonModMenuScreens.tooltip("config.needsofnature.tooltip.intiface_latency_compensation")
         )
      );
      settingsList.addEntryRow(SettingsList.RowEntry.sectionHeader(this.textRenderer, Text.translatable("config.needsofnature.section.intiface_toy_settings")));
      ButtonWidget vibratorButton = ButtonWidget.builder(
            Text.translatable("config.needsofnature.intiface_vibrator_settings"),
            button -> MinecraftClient.getInstance().setScreen(new NonModMenuIntifaceVibratorScreen(this, this.config))
         )
         .dimensions(0, 0, 220, 20)
         .build();
      NonModMenuScreens.setTooltip(vibratorButton, "config.needsofnature.tooltip.intiface_vibrator_settings");
      settingsList.addEntryRow(
         SettingsList.RowEntry.buttonWithReset(
            this.textRenderer, vibratorButton, NonModMenuScreens.tooltip("config.needsofnature.tooltip.intiface_vibrator_settings")
         )
      );
      ButtonWidget strokerOscillatorButton = ButtonWidget.builder(
            Text.translatable("config.needsofnature.intiface_stroker_oscillator_settings"),
            button -> MinecraftClient.getInstance().setScreen(new NonModMenuIntifaceStrokerOscillatorScreen(this, this.config))
         )
         .dimensions(0, 0, 220, 20)
         .build();
      NonModMenuScreens.setTooltip(strokerOscillatorButton, "config.needsofnature.tooltip.intiface_stroker_oscillator_settings");
      settingsList.addEntryRow(
         SettingsList.RowEntry.buttonWithReset(
            this.textRenderer, strokerOscillatorButton, NonModMenuScreens.tooltip("config.needsofnature.tooltip.intiface_stroker_oscillator_settings")
         )
      );
      int centerX = this.width / 2;
      this.updateResetButton();
      ButtonWidget doneButton = ButtonWidget.builder(Text.translatable("config.needsofnature.done"), button -> this.saveAndClose())
         .dimensions(centerX - 100, this.height - 28, 200, 20)
         .build();
      NonModMenuScreens.setTooltip(doneButton, "config.needsofnature.tooltip.done_save");
      this.addDrawableChild(doneButton);
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      this.updateResetButton();
      super.render(context, mouseX, mouseY, deltaTicks);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 16777215);
   }

   public void close() {
      this.saveAndClose();
   }

   private void saveAndClose() {
      this.config.setIntifaceLatencyCompensationMs(this.latencyCompensationMs);
      this.config.save();
      MinecraftClient.getInstance().setScreen(this.parent);
   }

   private void loadFromConfig() {
      this.latencyCompensationMs = this.config.getIntifaceLatencyCompensationMs();
   }

   private void updateResetButton() {
      if (this.resetLatencyCompensationButton != null) {
         this.resetLatencyCompensationButton.active = this.latencyCompensationMs != this.defaults.getIntifaceLatencyCompensationMs();
      }
   }
}
