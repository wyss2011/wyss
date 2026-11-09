package com.nonid.integration;

import com.nonid.NonConfig;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class NonModMenuIntifaceStrokerOscillatorScreen extends Screen {
   private final Screen parent;
   private final NonConfig config;
   private final NonConfig defaults = new NonConfig();
   private int oscillatorRegularSpeedPercent;
   private int oscillatorPeakSpeedPercent;
   private int strokerMinDistancePercent;
   private int strokerMaxDistancePercent;
   private int strokerRegularMoveDurationMs;
   private int strokerPeakMoveDurationMs;
   private FloatValueSliderWidget oscillatorRegularSlider;
   private ButtonWidget resetOscillatorRegularButton;
   private FloatValueSliderWidget oscillatorPeakSlider;
   private ButtonWidget resetOscillatorPeakButton;
   private FloatValueSliderWidget strokerMinSlider;
   private ButtonWidget resetStrokerMinButton;
   private FloatValueSliderWidget strokerMaxSlider;
   private ButtonWidget resetStrokerMaxButton;
   private FloatValueSliderWidget strokerRegularSlider;
   private ButtonWidget resetStrokerRegularButton;
   private FloatValueSliderWidget strokerPeakSlider;
   private ButtonWidget resetStrokerPeakButton;

   NonModMenuIntifaceStrokerOscillatorScreen(Screen parent, NonConfig config) {
      super(Text.translatable("config.needsofnature.intiface_stroker_oscillator_title"));
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
      settingsList.addEntryRow(SettingsList.RowEntry.sectionHeader(this.textRenderer, Text.translatable("config.needsofnature.section.intiface_oscillator")));
      this.oscillatorRegularSlider = this.percentSlider(this.oscillatorRegularSpeedPercent, value -> {
         this.oscillatorRegularSpeedPercent = (int)Math.round(value);
         this.updateResetButtons();
      });
      this.resetOscillatorRegularButton = this.resetButton(() -> {
         this.oscillatorRegularSpeedPercent = this.defaults.getIntifaceOscillatorRegularSpeedPercent();
         this.oscillatorRegularSlider.setFloatValue(this.oscillatorRegularSpeedPercent);
      });
      settingsList.addEntryRow(
         SettingsList.RowEntry.labeledField(
            this.textRenderer,
            Text.translatable("config.needsofnature.intiface_oscillator_regular_speed"),
            this.oscillatorRegularSlider,
            this.resetOscillatorRegularButton,
            NonModMenuScreens.tooltip("config.needsofnature.tooltip.intiface_oscillator_regular_speed")
         )
      );
      this.oscillatorPeakSlider = this.percentSlider(this.oscillatorPeakSpeedPercent, value -> {
         this.oscillatorPeakSpeedPercent = (int)Math.round(value);
         this.updateResetButtons();
      });
      this.resetOscillatorPeakButton = this.resetButton(() -> {
         this.oscillatorPeakSpeedPercent = this.defaults.getIntifaceOscillatorPeakSpeedPercent();
         this.oscillatorPeakSlider.setFloatValue(this.oscillatorPeakSpeedPercent);
      });
      settingsList.addEntryRow(
         SettingsList.RowEntry.labeledField(
            this.textRenderer,
            Text.translatable("config.needsofnature.intiface_oscillator_peak_speed"),
            this.oscillatorPeakSlider,
            this.resetOscillatorPeakButton,
            NonModMenuScreens.tooltip("config.needsofnature.tooltip.intiface_oscillator_peak_speed")
         )
      );
      settingsList.addEntryRow(SettingsList.RowEntry.sectionHeader(this.textRenderer, Text.translatable("config.needsofnature.section.intiface_stroker")));
      this.strokerMaxSlider = this.percentSlider(this.strokerMaxDistancePercent, value -> {
         this.strokerMaxDistancePercent = (int)Math.round(value);
         this.updateResetButtons();
      });
      this.resetStrokerMaxButton = this.resetButton(() -> {
         this.strokerMaxDistancePercent = this.defaults.getIntifaceStrokerMaxDistancePercent();
         this.strokerMaxSlider.setFloatValue(this.strokerMaxDistancePercent);
      });
      settingsList.addEntryRow(
         SettingsList.RowEntry.labeledField(
            this.textRenderer,
            Text.translatable("config.needsofnature.intiface_stroker_max_distance"),
            this.strokerMaxSlider,
            this.resetStrokerMaxButton,
            NonModMenuScreens.tooltip("config.needsofnature.tooltip.intiface_stroker_max_distance")
         )
      );
      this.strokerMinSlider = this.percentSlider(this.strokerMinDistancePercent, value -> {
         this.strokerMinDistancePercent = (int)Math.round(value);
         this.updateResetButtons();
      });
      this.resetStrokerMinButton = this.resetButton(() -> {
         this.strokerMinDistancePercent = this.defaults.getIntifaceStrokerMinDistancePercent();
         this.strokerMinSlider.setFloatValue(this.strokerMinDistancePercent);
      });
      settingsList.addEntryRow(
         SettingsList.RowEntry.labeledField(
            this.textRenderer,
            Text.translatable("config.needsofnature.intiface_stroker_min_distance"),
            this.strokerMinSlider,
            this.resetStrokerMinButton,
            NonModMenuScreens.tooltip("config.needsofnature.tooltip.intiface_stroker_min_distance")
         )
      );
      this.strokerRegularSlider = this.durationSlider(this.strokerRegularMoveDurationMs, value -> {
         this.strokerRegularMoveDurationMs = (int)Math.round(value);
         this.updateResetButtons();
      });
      this.resetStrokerRegularButton = this.resetButton(() -> {
         this.strokerRegularMoveDurationMs = this.defaults.getIntifaceStrokerRegularMoveDurationMs();
         this.strokerRegularSlider.setFloatValue(this.strokerRegularMoveDurationMs);
      });
      settingsList.addEntryRow(
         SettingsList.RowEntry.labeledField(
            this.textRenderer,
            Text.translatable("config.needsofnature.intiface_stroker_regular_duration"),
            this.strokerRegularSlider,
            this.resetStrokerRegularButton,
            NonModMenuScreens.tooltip("config.needsofnature.tooltip.intiface_stroker_regular_duration")
         )
      );
      this.strokerPeakSlider = this.durationSlider(this.strokerPeakMoveDurationMs, value -> {
         this.strokerPeakMoveDurationMs = (int)Math.round(value);
         this.updateResetButtons();
      });
      this.resetStrokerPeakButton = this.resetButton(() -> {
         this.strokerPeakMoveDurationMs = this.defaults.getIntifaceStrokerPeakMoveDurationMs();
         this.strokerPeakSlider.setFloatValue(this.strokerPeakMoveDurationMs);
      });
      settingsList.addEntryRow(
         SettingsList.RowEntry.labeledField(
            this.textRenderer,
            Text.translatable("config.needsofnature.intiface_stroker_peak_duration"),
            this.strokerPeakSlider,
            this.resetStrokerPeakButton,
            NonModMenuScreens.tooltip("config.needsofnature.tooltip.intiface_stroker_peak_duration")
         )
      );
      this.updateResetButtons();
      int centerX = this.width / 2;
      ButtonWidget doneButton = ButtonWidget.builder(Text.translatable("config.needsofnature.done"), button -> this.saveAndClose())
         .dimensions(centerX - 100, this.height - 28, 200, 20)
         .build();
      NonModMenuScreens.setTooltip(doneButton, "config.needsofnature.tooltip.done_save");
      this.addDrawableChild(doneButton);
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      this.updateResetButtons();
      super.render(context, mouseX, mouseY, deltaTicks);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 16777215);
   }

   public void close() {
      this.saveAndClose();
   }

   private void saveAndClose() {
      this.saveValues();
      MinecraftClient.getInstance().setScreen(this.parent);
   }

   private void saveValues() {
      this.config.setIntifaceOscillatorRegularSpeedPercent(this.oscillatorRegularSpeedPercent);
      this.config.setIntifaceOscillatorPeakSpeedPercent(this.oscillatorPeakSpeedPercent);
      this.config.setIntifaceStrokerDistancesPercent(this.strokerMinDistancePercent, this.strokerMaxDistancePercent);
      this.config.setIntifaceStrokerRegularMoveDurationMs(this.strokerRegularMoveDurationMs);
      this.config.setIntifaceStrokerPeakMoveDurationMs(this.strokerPeakMoveDurationMs);
      this.config.save();
      this.loadFromConfig();
   }

   private void loadFromConfig() {
      this.oscillatorRegularSpeedPercent = this.config.getIntifaceOscillatorRegularSpeedPercent();
      this.oscillatorPeakSpeedPercent = this.config.getIntifaceOscillatorPeakSpeedPercent();
      this.strokerMinDistancePercent = this.config.getIntifaceStrokerMinDistancePercent();
      this.strokerMaxDistancePercent = this.config.getIntifaceStrokerMaxDistancePercent();
      this.strokerRegularMoveDurationMs = this.config.getIntifaceStrokerRegularMoveDurationMs();
      this.strokerPeakMoveDurationMs = this.config.getIntifaceStrokerPeakMoveDurationMs();
   }

   private ButtonWidget resetButton(Runnable action) {
      ButtonWidget button = ButtonWidget.builder(Text.translatable("config.needsofnature.reset_icon"), ignored -> {
         if (action != null) {
            action.run();
         }

         this.updateResetButtons();
      }).dimensions(0, 0, 20, 20).build();
      NonModMenuScreens.setTooltip(button, "config.needsofnature.tooltip.reset");
      return button;
   }

   private FloatValueSliderWidget percentSlider(int initial, DoubleConsumer listener) {
      return new FloatValueSliderWidget(0, 0, 130, 20, initial, 0.0F, 100.0F, 1.0F, listener, value -> String.format(Locale.ROOT, "%d%%", Math.round(value)));
   }

   private FloatValueSliderWidget durationSlider(int initial, DoubleConsumer listener) {
      return new FloatValueSliderWidget(0, 0, 130, 20, initial, 0.0F, 5000.0F, 10.0F, listener, value -> String.format(Locale.ROOT, "%dms", Math.round(value)));
   }

   private void updateResetButtons() {
      if (this.resetOscillatorRegularButton != null) {
         this.resetOscillatorRegularButton.active = this.oscillatorRegularSpeedPercent != this.defaults.getIntifaceOscillatorRegularSpeedPercent();
      }

      if (this.resetOscillatorPeakButton != null) {
         this.resetOscillatorPeakButton.active = this.oscillatorPeakSpeedPercent != this.defaults.getIntifaceOscillatorPeakSpeedPercent();
      }

      if (this.resetStrokerMinButton != null) {
         this.resetStrokerMinButton.active = this.strokerMinDistancePercent != this.defaults.getIntifaceStrokerMinDistancePercent();
      }

      if (this.resetStrokerMaxButton != null) {
         this.resetStrokerMaxButton.active = this.strokerMaxDistancePercent != this.defaults.getIntifaceStrokerMaxDistancePercent();
      }

      if (this.resetStrokerRegularButton != null) {
         this.resetStrokerRegularButton.active = this.strokerRegularMoveDurationMs != this.defaults.getIntifaceStrokerRegularMoveDurationMs();
      }

      if (this.resetStrokerPeakButton != null) {
         this.resetStrokerPeakButton.active = this.strokerPeakMoveDurationMs != this.defaults.getIntifaceStrokerPeakMoveDurationMs();
      }
   }
}
