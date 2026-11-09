package com.nonid.integration;

import com.nonid.GenderHolder;
import com.nonid.NeedsOfNature;
import com.nonid.NeedsOfNatureClient;
import com.nonid.NonConfig;
import com.nonid.client.NonDestroyedSkinClient;
import com.nonid.client.NonWildfireGenderSync;
import java.io.IOException;
import java.util.Locale;
import java.util.function.DoubleFunction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

final class NonModMenuPlayerScreens {
   private NonModMenuPlayerScreens() {
   }

   static class DestroyedSkinTintPickerScreen extends Screen {
      private final Screen parent;
      private final NonConfig config;
      private final String initialHex;
      private Identifier skinTexture;
      private NativeImage skinImage;
      private int selectedRgb = 13803641;
      private int skinX;
      private int skinY;
      private int skinSize;
      private int selectedPixelX = -1;
      private int selectedPixelY = -1;
      private TextFieldWidget tintField;

      protected DestroyedSkinTintPickerScreen(Screen parent, NonConfig config, String initialHex) {
         super(Text.translatable("config.needsofnature.destroyed_skin_tint.pick_title"));
         this.parent = parent;
         this.config = config;
         this.initialHex = initialHex == null ? "" : initialHex;
         this.selectedRgb = parseHexOrDefault(this.initialHex, this.selectedRgb);
      }

      protected void init() {
         this.loadSkinImage();
         this.skinSize = Math.min(320, Math.max(128, Math.min(this.width - 40, this.height - 136)));
         this.skinX = (this.width - this.skinSize) / 2;
         this.skinY = 40;
         int centerX = this.width / 2;
         this.tintField = new TextFieldWidget(this.textRenderer, centerX - 15, this.height - 62, 80, 20, Text.literal(""));
         this.tintField.setMaxLength(7);
         this.tintField.setText(formatRgb(this.selectedRgb));
         this.tintField.setChangedListener(ignored -> this.updateSelectedRgbFromField());
         this.addDrawableChild(this.tintField);
         ButtonWidget useButton = ButtonWidget.builder(Text.translatable("config.needsofnature.destroyed_skin_tint.use"), button -> {
            String pickedHex = this.sanitizeHexInput(this.tintField, formatRgb(this.selectedRgb));
            this.selectedRgb = parseHexOrDefault(pickedHex, this.selectedRgb);
            this.config.setDestroyedSkinTintHex(pickedHex);
            this.config.save();
            NonDestroyedSkinClient.uploadLocalSkinIfPresent();
            MinecraftClient.getInstance().setScreen(this.parent);
         }).dimensions(centerX - 155, this.height - 28, 100, 20).build();
         this.addDrawableChild(useButton);
         ButtonWidget autoButton = ButtonWidget.builder(Text.translatable("config.needsofnature.destroyed_skin_tint.auto"), button -> {
            this.setSelectedRgb(NonDestroyedSkinClient.detectSkinTintFromCurrentPlayer(this.selectedRgb));
            this.selectedPixelX = -1;
            this.selectedPixelY = -1;
         }).dimensions(centerX - 50, this.height - 28, 100, 20).build();
         NonModMenuScreens.setTooltip(autoButton, "config.needsofnature.tooltip.destroyed_skin_tint.auto");
         this.addDrawableChild(autoButton);
         ButtonWidget cancelButton = ButtonWidget.builder(Text.translatable("gui.cancel"), button -> MinecraftClient.getInstance().setScreen(this.parent))
            .dimensions(centerX + 55, this.height - 28, 100, 20)
            .build();
         this.addDrawableChild(cancelButton);
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         super.render(context, mouseX, mouseY, deltaTicks);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, 16777215);
         if (this.skinTexture != null) {
            context.fill(this.skinX, this.skinY, this.skinX + this.skinSize, this.skinY + this.skinSize, 0xFF000000 | this.selectedRgb);
            context.drawTexture(
               RenderPipelines.GUI_TEXTURED, this.skinTexture, this.skinX, this.skinY, 0.0F, 0.0F, this.skinSize, this.skinSize, 64, 64, 64, 64
            );
            context.fill(this.skinX - 1, this.skinY - 1, this.skinX + this.skinSize + 1, this.skinY, -1);
            context.fill(this.skinX - 1, this.skinY + this.skinSize, this.skinX + this.skinSize + 1, this.skinY + this.skinSize + 1, -1);
            context.fill(this.skinX - 1, this.skinY - 1, this.skinX, this.skinY + this.skinSize + 1, -1);
            context.fill(this.skinX + this.skinSize, this.skinY - 1, this.skinX + this.skinSize + 1, this.skinY + this.skinSize + 1, -1);
         } else {
            context.drawCenteredTextWithShadow(
               this.textRenderer,
               Text.translatable("config.needsofnature.destroyed_skin_tint.no_skin"),
               this.width / 2,
               this.skinY + this.skinSize / 2,
               16744576
            );
         }

         if (this.selectedPixelX >= 0 && this.selectedPixelY >= 0) {
            int px = this.skinX + this.selectedPixelX * this.skinSize / 64;
            int py = this.skinY + this.selectedPixelY * this.skinSize / 64;
            int cell = Math.max(1, this.skinSize / 64);
            context.fill(px - 1, py - 1, px + cell + 1, py, -256);
            context.fill(px - 1, py + cell, px + cell + 1, py + cell + 1, -256);
            context.fill(px - 1, py - 1, px, py + cell + 1, -256);
            context.fill(px + cell, py - 1, px + cell + 1, py + cell + 1, -256);
         }

         int previewY = this.height - 58;
         int previewX = this.width / 2 - 78;
         context.fill(previewX, previewY, previewX + 24, previewY + 24, 0xFF000000 | this.selectedRgb);
         context.fill(previewX - 1, previewY - 1, previewX + 25, previewY, -1);
         context.fill(previewX - 1, previewY + 24, previewX + 25, previewY + 25, -1);
         context.fill(previewX - 1, previewY - 1, previewX, previewY + 25, -1);
         context.fill(previewX + 24, previewY - 1, previewX + 25, previewY + 25, -1);
         context.drawTextWithShadow(this.textRenderer, Text.translatable("config.needsofnature.destroyed_skin_tint"), previewX + 34, previewY + 8, 16777215);
      }

      public boolean mouseClicked(Click click, boolean doubled) {
         return this.tryPickColor(click.x(), click.y()) ? true : super.mouseClicked(click, doubled);
      }

      public void close() {
         MinecraftClient.getInstance().setScreen(this.parent);
      }

      public void removed() {
         if (this.skinImage != null) {
            this.skinImage.close();
            this.skinImage = null;
         }
      }

      private void loadSkinImage() {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null) {
            this.skinTexture = client.player.getSkin().body().texturePath();

            try {
               this.skinImage = NonDestroyedSkinClient.readTextureImage(client, this.skinTexture);
            } catch (IOException | RuntimeException e) {
               NeedsOfNature.LOGGER.warn("[NoN] Failed to read player skin for tint picker.", e);
               this.skinImage = null;
            }
         }
      }

      private boolean tryPickColor(double mouseX, double mouseY) {
         if (this.skinImage == null) {
            return false;
         }

         if (!(mouseX < this.skinX) && !(mouseX >= this.skinX + this.skinSize) && !(mouseY < this.skinY) && !(mouseY >= this.skinY + this.skinSize)) {
            int pixelX = Math.max(0, Math.min(63, (int)((mouseX - this.skinX) * 64.0 / this.skinSize)));
            int pixelY = Math.max(0, Math.min(63, (int)((mouseY - this.skinY) * 64.0 / this.skinSize)));
            if (pixelX < this.skinImage.getWidth() && pixelY < this.skinImage.getHeight()) {
               int argb = this.skinImage.getColorArgb(pixelX, pixelY);
               if ((argb >>> 24 & 0xFF) < 32) {
                  return true;
               }

               this.setSelectedRgb(argb & 16777215);
               this.selectedPixelX = pixelX;
               this.selectedPixelY = pixelY;
               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      }

      private void setSelectedRgb(int rgb) {
         this.selectedRgb = rgb & 16777215;
         if (this.tintField != null) {
            this.tintField.setText(formatRgb(this.selectedRgb));
         }
      }

      private void updateSelectedRgbFromField() {
         this.selectedRgb = parseHexOrDefault(this.sanitizeHexInput(this.tintField, formatRgb(this.selectedRgb)), this.selectedRgb);
      }

      private String sanitizeHexInput(TextFieldWidget field, String fallback) {
         if (field == null) {
            return fallback == null ? "" : fallback;
         }

         String value = field.getText() == null ? "" : field.getText().trim();
         if (value.startsWith("#")) {
            value = value.substring(1);
         }

         value = value.toUpperCase(Locale.ROOT);
         if (value.isEmpty()) {
            return "";
         } else {
            return value.matches("[0-9A-F]{6}") ? value : (fallback == null ? "" : fallback);
         }
      }

      private static int parseHexOrDefault(String hex, int fallback) {
         if (hex == null) {
            return fallback;
         }

         String value = hex.trim();
         if (value.startsWith("#")) {
            value = value.substring(1);
         }

         if (!value.matches("[0-9a-fA-F]{6}")) {
            return fallback;
         }

         try {
            return Integer.parseUnsignedInt(value, 16) & 16777215;
         } catch (NumberFormatException e) {
            return fallback;
         }
      }

      private static String formatRgb(int rgb) {
         return String.format(Locale.ROOT, "%06X", rgb & 16777215);
      }
   }

   static class FemaleGenderDestroyedOverridesScreen extends Screen {
      private final Screen parent;
      private final NonConfig config;
      private final NonConfig defaults = new NonConfig();
      private boolean overridesEnabled;
      private ButtonWidget overridesButton;
      private ButtonWidget resetOverridesButton;
      private NonModMenuPlayerScreens.FemaleGenderDestroyedOverridesScreen.ProfileFields lowFields;
      private NonModMenuPlayerScreens.FemaleGenderDestroyedOverridesScreen.ProfileFields highFields;

      protected FemaleGenderDestroyedOverridesScreen(Screen parent, NonConfig config) {
         super(Text.translatable("config.needsofnature.destroyed_skin_female_gender_profiles_title"));
         this.parent = parent;
         this.config = config;
         this.overridesEnabled = config.isDestroyedSkinFemaleGenderOverridesEnabled();
      }

      protected void init() {
         int listTop = 156;
         int bottomArea = 40;
         int listHeight = Math.max(0, this.height - listTop - bottomArea);
         SettingsList settingsList = new SettingsList(this.client, this.width, listHeight, listTop);
         this.addDrawableChild(settingsList);
         this.overridesButton = ButtonWidget.builder(toggleText(this.overridesEnabled), button -> {
            this.overridesEnabled = !this.overridesEnabled;
            this.updateOverrideControls();
         }).dimensions(0, 0, 100, 20).build();
         NonModMenuScreens.setTooltip(this.overridesButton, "config.needsofnature.tooltip.destroyed_skin_female_gender_overrides");
         this.resetOverridesButton = ButtonWidget.builder(Text.translatable("config.needsofnature.reset_icon"), button -> {
            this.overridesEnabled = this.defaults.isDestroyedSkinFemaleGenderOverridesEnabled();
            this.updateOverrideControls();
         }).dimensions(0, 0, 20, 20).build();
         NonModMenuScreens.setTooltip(this.resetOverridesButton, "config.needsofnature.tooltip.reset");
         settingsList.addEntryRow(
            SettingsList.RowEntry.labeledField(
               this.textRenderer,
               Text.translatable("config.needsofnature.destroyed_skin_female_gender_overrides"),
               this.overridesButton,
               this.resetOverridesButton,
               NonModMenuScreens.tooltip("config.needsofnature.tooltip.destroyed_skin_female_gender_overrides")
            )
         );
         ButtonWidget breastTextureEditorButton = ButtonWidget.builder(Text.translatable("wildfire_gender.uv_editor"), button -> {
            this.saveValues();
            NonWildfireGenderSync.openBreastTextureEditor(this);
         }).dimensions(0, 0, 220, 20).build();
         breastTextureEditorButton.active = NonWildfireGenderSync.canOpenBreastTextureEditor();
         NonModMenuScreens.setTooltip(breastTextureEditorButton, "config.needsofnature.tooltip.female_gender.breast_texture_editor");
         settingsList.addEntryRow(
            SettingsList.RowEntry.buttonWithReset(
               this.textRenderer, breastTextureEditorButton, NonModMenuScreens.tooltip("config.needsofnature.tooltip.female_gender.breast_texture_editor")
            )
         );
         this.lowFields = new NonModMenuPlayerScreens.FemaleGenderDestroyedOverridesScreen.ProfileFields(this.config.getFemaleGenderDestroyedLow());
         this.highFields = new NonModMenuPlayerScreens.FemaleGenderDestroyedOverridesScreen.ProfileFields(this.config.getFemaleGenderDestroyedHigh());
         this.lowFields.addRows(settingsList, this.textRenderer, Text.translatable("config.needsofnature.destroyed_skin_female_gender_profile.low"));
         this.highFields.addRows(settingsList, this.textRenderer, Text.translatable("config.needsofnature.destroyed_skin_female_gender_profile.high"));
         this.updateOverrideControls();
         int centerX = this.width / 2;
         ButtonWidget doneButton = ButtonWidget.builder(Text.translatable("config.needsofnature.done"), button -> {
            this.saveValues();
            MinecraftClient.getInstance().setScreen(this.parent);
         }).dimensions(centerX - 100, this.height - 28, 200, 20).build();
         NonModMenuScreens.setTooltip(doneButton, "config.needsofnature.tooltip.done_save");
         this.addDrawableChild(doneButton);
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         super.render(context, mouseX, mouseY, deltaTicks);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 16777215);
         this.renderPreviewPanel(context, mouseX, mouseY);
      }

      public void close() {
         this.saveValues();
         MinecraftClient.getInstance().setScreen(this.parent);
      }

      private void saveValues() {
         this.config.setDestroyedSkinFemaleGenderOverridesEnabled(this.overridesEnabled);
         if (this.lowFields != null) {
            this.config.setFemaleGenderDestroyedLow(this.lowFields.toProfile(this.config.getFemaleGenderDestroyedLow()));
         }

         if (this.highFields != null) {
            this.config.setFemaleGenderDestroyedHigh(this.highFields.toProfile(this.config.getFemaleGenderDestroyedHigh()));
         }

         this.config.save();
         NonWildfireGenderSync.syncDestroyedSkinOverrides(this.config);
      }

      private void updateOverrideControls() {
         if (this.overridesButton != null) {
            this.overridesButton.setMessage(toggleText(this.overridesEnabled));
         }

         if (this.resetOverridesButton != null) {
            this.resetOverridesButton.active = this.overridesEnabled != this.defaults.isDestroyedSkinFemaleGenderOverridesEnabled();
         }

         if (this.lowFields != null) {
            this.lowFields.setActive(this.overridesEnabled);
         }

         if (this.highFields != null) {
            this.highFields.setActive(this.overridesEnabled);
         }
      }

      private static Text toggleText(boolean enabled) {
         return Text.translatable(enabled ? "options.on" : "options.off");
      }

      private void renderPreviewPanel(DrawContext context, int mouseX, int mouseY) {
         int panelTop = 34;
         int panelBottom = 148;
         int panelWidth = 160;
         int gap = 14;
         int leftX = this.width / 2 - panelWidth - gap / 2;
         int rightX = this.width / 2 + gap / 2;
         this.renderSinglePreview(
            context,
            mouseX,
            mouseY,
            leftX,
            panelTop,
            panelWidth,
            panelBottom - panelTop,
            Text.translatable("config.needsofnature.destroyed_skin_female_gender_profile.low"),
            this.lowFields == null ? this.config.getFemaleGenderDestroyedLow() : this.lowFields.toProfile(this.config.getFemaleGenderDestroyedLow())
         );
         this.renderSinglePreview(
            context,
            mouseX,
            mouseY,
            rightX,
            panelTop,
            panelWidth,
            panelBottom - panelTop,
            Text.translatable("config.needsofnature.destroyed_skin_female_gender_profile.high"),
            this.highFields == null ? this.config.getFemaleGenderDestroyedHigh() : this.highFields.toProfile(this.config.getFemaleGenderDestroyedHigh())
         );
      }

      private void renderSinglePreview(
         DrawContext context, int mouseX, int mouseY, int x, int y, int w, int h, Text label, NonConfig.FemaleGenderDestroyedProfile profile
      ) {
         context.fill(x, y, x + w, y + h, 1711276032);
         context.fill(x, y, x + w, y + 1, -1996488705);
         context.fill(x, y + h - 1, x + w, y + h, -2013265920);
         context.fill(x, y, x + 1, y + h, -1996488705);
         context.fill(x + w - 1, y, x + w, y + h, -2013265920);
         context.drawCenteredTextWithShadow(this.textRenderer, label, x + w / 2, y + 6, -2039584);
         boolean rendered = NonWildfireGenderSync.renderBreastPreview(this, context, profile, x + w / 2, y + h - 12, mouseX, mouseY);
         if (!rendered) {
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.translatable("config.needsofnature.female_gender.preview_unavailable"), x + w / 2, y + h / 2, -32640
            );
         }
      }

      private final class ProfileFields {
         private final FloatValueSliderWidget breastSize;
         private final FloatValueSliderWidget separation;
         private final FloatValueSliderWidget height;
         private final FloatValueSliderWidget depth;
         private final FloatValueSliderWidget rotation;
         private final ButtonWidget breastPhysics;
         private final ButtonWidget dualPhysics;
         private final FloatValueSliderWidget intensity;
         private final FloatValueSliderWidget momentum;
         private boolean breastPhysicsValue;
         private boolean dualPhysicsValue;

         private ProfileFields(NonConfig.FemaleGenderDestroyedProfile profile) {
            this.breastSize = this.newFloatSlider(profile.breastSize(), 0.0F, 1.6F, 0.008F, value -> this.formatPercent(value * 125.0));
            this.breastSize.setWarningAbove(0.8F);
            this.separation = this.newFloatSlider(profile.separation(), -1.0F, 1.0F, 0.1F, value -> this.formatPlain(value * 10.0));
            this.height = this.newFloatSlider(profile.height(), -2.0F, 1.0F, 0.1F, value -> this.formatPlain(value * 10.0));
            this.depth = this.newFloatSlider(profile.depth(), -1.0F, 0.0F, 0.1F, value -> this.formatPlain(value * 10.0));
            this.rotation = this.newFloatSlider(profile.rotation(), 0.0F, 0.1F, 0.01F, value -> this.formatPlain(value * 100.0) + "°");
            this.breastPhysicsValue = profile.breastPhysics();
            this.dualPhysicsValue = profile.dualPhysics();
            this.breastPhysics = ButtonWidget.builder(
                  NonModMenuPlayerScreens.FemaleGenderDestroyedOverridesScreen.toggleText(this.breastPhysicsValue), button -> {
                     this.breastPhysicsValue = !this.breastPhysicsValue;
                     button.setMessage(NonModMenuPlayerScreens.FemaleGenderDestroyedOverridesScreen.toggleText(this.breastPhysicsValue));
                  }
               )
               .dimensions(0, 0, 70, 20)
               .build();
            this.dualPhysics = ButtonWidget.builder(NonModMenuPlayerScreens.FemaleGenderDestroyedOverridesScreen.toggleText(this.dualPhysicsValue), button -> {
               this.dualPhysicsValue = !this.dualPhysicsValue;
               button.setMessage(NonModMenuPlayerScreens.FemaleGenderDestroyedOverridesScreen.toggleText(this.dualPhysicsValue));
            }).dimensions(0, 0, 70, 20).build();
            this.intensity = this.newFloatSlider(profile.intensity(), 0.0F, 0.5F, 0.0033333334F, value -> this.formatPercent(value * 300.0));
            this.momentum = this.newFloatSlider(profile.momentum(), 0.25F, 1.0F, 0.0075F, value -> this.formatPercent((value - 0.25) / 0.75 * 100.0));
         }

         private void addRows(SettingsList settingsList, TextRenderer textRenderer, Text title) {
            settingsList.addEntryRow(SettingsList.RowEntry.sectionHeader(textRenderer, title));
            settingsList.addEntryRow(SettingsList.RowEntry.sectionHeader(textRenderer, Text.translatable("config.needsofnature.female_gender.customization")));
            this.addFloatRow(
               settingsList, textRenderer, "breast_size", this.breastSize, "0% - 200%", "config.needsofnature.tooltip.female_gender.breast_size_extended"
            );
            this.addFloatRow(settingsList, textRenderer, "separation", this.separation, "-10 - 10");
            this.addFloatRow(settingsList, textRenderer, "height", this.height, "-20 - 10");
            this.addFloatRow(settingsList, textRenderer, "depth", this.depth, "-10 - 0");
            this.addFloatRow(settingsList, textRenderer, "rotation", this.rotation, "0° - 10°");
            settingsList.addEntryRow(SettingsList.RowEntry.sectionHeader(textRenderer, Text.translatable("config.needsofnature.female_gender.physics")));
            settingsList.addEntryRow(
               SettingsList.RowEntry.labeledField(
                  textRenderer,
                  Text.translatable("config.needsofnature.female_gender.breast_physics"),
                  this.breastPhysics,
                  null,
                  NonModMenuScreens.tooltip("config.needsofnature.tooltip.female_gender.breast_physics")
               )
            );
            settingsList.addEntryRow(
               SettingsList.RowEntry.labeledField(
                  textRenderer,
                  Text.translatable("config.needsofnature.female_gender.dual_physics"),
                  this.dualPhysics,
                  null,
                  NonModMenuScreens.tooltip("config.needsofnature.tooltip.female_gender.dual_physics")
               )
            );
            this.addFloatRow(settingsList, textRenderer, "intensity", this.intensity, "0% - 150%");
            this.addFloatRow(settingsList, textRenderer, "momentum", this.momentum, "0% - 100%");
         }

         private void addFloatRow(SettingsList settingsList, TextRenderer textRenderer, String key, FloatValueSliderWidget field, String range) {
            this.addFloatRow(settingsList, textRenderer, key, field, range, null);
         }

         private void addFloatRow(
            SettingsList settingsList, TextRenderer textRenderer, String key, FloatValueSliderWidget field, String range, String warningKey
         ) {
            MutableText tooltipText = Text.translatable("config.needsofnature.tooltip.female_gender.range", new Object[]{range});
            if (warningKey != null) {
               tooltipText.append("\n").append(Text.translatable(warningKey).formatted(Formatting.YELLOW));
            }

            settingsList.addEntryRow(
               SettingsList.RowEntry.labeledField(
                  textRenderer, Text.translatable("config.needsofnature.female_gender." + key), field, null, NonModMenuScreens.tooltip(tooltipText)
               )
            );
         }

         private void setActive(boolean active) {
            this.breastSize.active = active;
            this.separation.active = active;
            this.height.active = active;
            this.depth.active = active;
            this.rotation.active = active;
            this.breastPhysics.active = active;
            this.dualPhysics.active = active;
            this.intensity.active = active;
            this.momentum.active = active;
         }

         private NonConfig.FemaleGenderDestroyedProfile toProfile(NonConfig.FemaleGenderDestroyedProfile fallback) {
            return new NonConfig.FemaleGenderDestroyedProfile(
               this.breastSize.getFloatValue(),
               this.separation.getFloatValue(),
               this.height.getFloatValue(),
               this.depth.getFloatValue(),
               this.rotation.getFloatValue(),
               this.breastPhysicsValue,
               this.dualPhysicsValue,
               this.intensity.getFloatValue(),
               this.momentum.getFloatValue()
            );
         }

         private FloatValueSliderWidget newFloatSlider(float initial, float min, float max, float step, DoubleFunction<String> formatter) {
            return new FloatValueSliderWidget(0, 0, 160, 20, initial, min, max, step, ignored -> {}, formatter);
         }

         private String formatPercent(double value) {
            return this.formatPlain(value) + "%";
         }

         private String formatPlain(double value) {
            return String.format(Locale.ROOT, "%.0f", value);
         }
      }
   }

   static class PlayerPreferencesScreen extends Screen {
      private final Screen parent;
      private final NonConfig config;
      private final NonConfig defaults = new NonConfig();
      private NonConfig.PlayerGenderSelection playerGenderSelection;
      private boolean syncFemaleGenderModWithNonGender;
      private boolean armorShedEffectEnabled;
      private boolean autoSwitchThirdPersonOnAnimationStart;
      private int actionSoundVolumePercent;
      private int liquidPuddleDespawnSeconds;
      private ButtonWidget playerGenderButton;
      private ButtonWidget resetPlayerGenderButton;
      private ButtonWidget syncFemaleGenderModWithNonGenderButton;
      private ButtonWidget resetSyncFemaleGenderModWithNonGenderButton;
      private ButtonWidget armorShedEffectButton;
      private ButtonWidget resetArmorShedEffectButton;
      private ButtonWidget autoSwitchThirdPersonButton;
      private ButtonWidget resetAutoSwitchThirdPersonButton;
      private PercentSliderWidget actionSoundVolumeSlider;
      private ButtonWidget resetActionSoundVolumeButton;
      private TextFieldWidget liquidPuddleDespawnField;
      private ButtonWidget resetLiquidPuddleDespawnButton;
      private boolean playerGenderEditable;

      protected PlayerPreferencesScreen(Screen parent, NonConfig config) {
         super(Text.translatable("config.needsofnature.player_preferences_title"));
         this.parent = parent;
         this.config = config;
         this.playerGenderSelection = config.getPlayerGenderSelection();
         this.syncFemaleGenderModWithNonGender = config.isSyncFemaleGenderModWithNonGender();
         this.armorShedEffectEnabled = config.isArmorShedEffectEnabled();
         this.autoSwitchThirdPersonOnAnimationStart = config.autoSwitchThirdPersonOnAnimationStart();
         this.actionSoundVolumePercent = config.getActionSoundVolumePercent();
         this.liquidPuddleDespawnSeconds = config.getLiquidPuddleDespawnSeconds();
      }

      protected void init() {
         if (this.liquidPuddleDespawnField != null) {
            this.liquidPuddleDespawnSeconds = this.clampLiquidPuddleDespawnSeconds(
               this.parseField(this.liquidPuddleDespawnField, this.liquidPuddleDespawnSeconds)
            );
         }

         int listTop = 32;
         int bottomArea = 40;
         int listHeight = Math.max(0, this.height - listTop - bottomArea);
         SettingsList settingsList = new SettingsList(this.client, this.width, listHeight, listTop);
         this.addDrawableChild(settingsList);
         int fieldWidth = 50;
         int resetW = 20;
         this.playerGenderEditable = this.config.allowPlayerGenderChangeAnytime();
         if (!this.playerGenderEditable) {
            this.playerGenderSelection = this.currentPlayerGenderSelection(this.playerGenderSelection);
         }

         settingsList.addEntryRow(SettingsList.RowEntry.sectionHeader(this.textRenderer, Text.translatable("config.needsofnature.section.player_identity")));
         this.playerGenderButton = ButtonWidget.builder(this.playerGenderText(this.playerGenderSelection), button -> {
            this.playerGenderSelection = this.playerGenderSelection.next();
            button.setMessage(this.playerGenderText(this.playerGenderSelection));
            this.updateResetButtons();
         }).dimensions(0, 0, 140, 20).build();
         this.playerGenderButton.active = this.playerGenderEditable;
         NonModMenuScreens.setTooltip(
            this.playerGenderButton,
            this.playerGenderEditable ? "config.needsofnature.tooltip.player_gender" : "config.needsofnature.tooltip.player_gender.locked"
         );
         this.resetPlayerGenderButton = ButtonWidget.builder(Text.translatable("config.needsofnature.reset_icon"), button -> {
            this.playerGenderSelection = this.defaults.getPlayerGenderSelection();
            if (this.playerGenderButton != null) {
               this.playerGenderButton.setMessage(this.playerGenderText(this.playerGenderSelection));
            }

            this.updateResetButtons();
         }).dimensions(0, 0, resetW, 20).build();
         NonModMenuScreens.setTooltip(this.resetPlayerGenderButton, "config.needsofnature.tooltip.reset");
         this.resetPlayerGenderButton.active = this.playerGenderEditable;
         settingsList.addEntryRow(
            SettingsList.RowEntry.labeledField(
               this.textRenderer,
               Text.translatable("config.needsofnature.player_gender"),
               this.playerGenderButton,
               this.resetPlayerGenderButton,
               NonModMenuScreens.tooltip(
                  this.playerGenderEditable ? "config.needsofnature.tooltip.player_gender" : "config.needsofnature.tooltip.player_gender.locked"
               )
            )
         );
         ButtonWidget playerProfilesButton = ButtonWidget.builder(
               Text.translatable("gui.needsofnature.player_profiles.title"),
               button -> MinecraftClient.getInstance().setScreen(new NonPlayerProfilesScreen(this))
            )
            .dimensions(0, 0, 220, 20)
            .build();
         NonModMenuScreens.setTooltip(playerProfilesButton, "gui.needsofnature.player_profiles.tooltip");
         settingsList.addEntryRow(
            SettingsList.RowEntry.buttonWithReset(
               this.textRenderer, playerProfilesButton, NonModMenuScreens.tooltip("gui.needsofnature.player_profiles.tooltip")
            )
         );
         if (NonWildfireGenderSync.isAvailable()) {
            this.syncFemaleGenderModWithNonGenderButton = ButtonWidget.builder(this.toggleText(this.syncFemaleGenderModWithNonGender), button -> {
               this.syncFemaleGenderModWithNonGender = !this.syncFemaleGenderModWithNonGender;
               button.setMessage(this.toggleText(this.syncFemaleGenderModWithNonGender));
               this.updateResetButtons();
            }).dimensions(0, 0, 100, 20).build();
            NonModMenuScreens.setTooltip(this.syncFemaleGenderModWithNonGenderButton, "config.needsofnature.tooltip.sync_female_gender_mod");
            this.resetSyncFemaleGenderModWithNonGenderButton = ButtonWidget.builder(Text.translatable("config.needsofnature.reset_icon"), button -> {
               this.syncFemaleGenderModWithNonGender = this.defaults.isSyncFemaleGenderModWithNonGender();
               if (this.syncFemaleGenderModWithNonGenderButton != null) {
                  this.syncFemaleGenderModWithNonGenderButton.setMessage(this.toggleText(this.syncFemaleGenderModWithNonGender));
               }

               this.updateResetButtons();
            }).dimensions(0, 0, resetW, 20).build();
            NonModMenuScreens.setTooltip(this.resetSyncFemaleGenderModWithNonGenderButton, "config.needsofnature.tooltip.reset");
            settingsList.addEntryRow(
               SettingsList.RowEntry.labeledField(
                  this.textRenderer,
                  Text.translatable("config.needsofnature.sync_female_gender_mod"),
                  this.syncFemaleGenderModWithNonGenderButton,
                  this.resetSyncFemaleGenderModWithNonGenderButton,
                  NonModMenuScreens.tooltip("config.needsofnature.tooltip.sync_female_gender_mod")
               )
            );
            ButtonWidget destroyedSkinFemaleGenderProfilesButton = ButtonWidget.builder(
                  Text.translatable("config.needsofnature.destroyed_skin_female_gender_profiles"),
                  button -> MinecraftClient.getInstance().setScreen(new NonModMenuPlayerScreens.FemaleGenderDestroyedOverridesScreen(this, this.config))
               )
               .dimensions(0, 0, 220, 20)
               .build();
            NonModMenuScreens.setTooltip(destroyedSkinFemaleGenderProfilesButton, "config.needsofnature.tooltip.destroyed_skin_female_gender_profiles");
            settingsList.addEntryRow(
               SettingsList.RowEntry.buttonWithReset(
                  this.textRenderer,
                  destroyedSkinFemaleGenderProfilesButton,
                  NonModMenuScreens.tooltip("config.needsofnature.tooltip.destroyed_skin_female_gender_profiles")
               )
            );
         }

         boolean destroyedSkinSystemEnabled = this.config.isDestroyedSkinSystemEnabled();
         boolean hasCustomDestroyedSkin = NonDestroyedSkinClient.hasLocalCustomDestroyedSkin();
         boolean hasDefaultDestroyedSkin = NonDestroyedSkinClient.hasCurrentDefaultDestroyedSkin();
         String destroyedSkinTintTooltip = !destroyedSkinSystemEnabled
            ? "config.needsofnature.tooltip.destroyed_skin_tint.disabled_system"
            : (
               hasCustomDestroyedSkin
                  ? "config.needsofnature.tooltip.destroyed_skin_tint.custom_skin"
                  : (
                     hasDefaultDestroyedSkin
                        ? "config.needsofnature.tooltip.destroyed_skin_tint.default_skin"
                        : "config.needsofnature.tooltip.destroyed_skin_tint.pick"
                  )
            );
         ButtonWidget pickerButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.destroyed_skin_tint.pick"),
               button -> MinecraftClient.getInstance()
                  .setScreen(new NonModMenuPlayerScreens.DestroyedSkinTintPickerScreen(this, this.config, this.config.getDestroyedSkinTintHex()))
            )
            .dimensions(0, 0, 220, 20)
            .build();
         pickerButton.active = destroyedSkinSystemEnabled && !hasCustomDestroyedSkin && !hasDefaultDestroyedSkin;
         NonModMenuScreens.setTooltip(pickerButton, destroyedSkinTintTooltip);
         settingsList.addEntryRow(SettingsList.RowEntry.buttonWithReset(this.textRenderer, pickerButton, NonModMenuScreens.tooltip(destroyedSkinTintTooltip)));
         settingsList.addEntryRow(SettingsList.RowEntry.sectionHeader(this.textRenderer, Text.translatable("config.needsofnature.section.local_visuals")));
         this.autoSwitchThirdPersonButton = ButtonWidget.builder(this.toggleText(this.autoSwitchThirdPersonOnAnimationStart), button -> {
            this.autoSwitchThirdPersonOnAnimationStart = !this.autoSwitchThirdPersonOnAnimationStart;
            button.setMessage(this.toggleText(this.autoSwitchThirdPersonOnAnimationStart));
            this.updateResetButtons();
         }).dimensions(0, 0, 100, 20).build();
         NonModMenuScreens.setTooltip(this.autoSwitchThirdPersonButton, "config.needsofnature.tooltip.animation.auto_switch_third_person");
         this.resetAutoSwitchThirdPersonButton = ButtonWidget.builder(Text.translatable("config.needsofnature.reset_icon"), button -> {
            this.autoSwitchThirdPersonOnAnimationStart = this.defaults.autoSwitchThirdPersonOnAnimationStart();
            this.autoSwitchThirdPersonButton.setMessage(this.toggleText(this.autoSwitchThirdPersonOnAnimationStart));
            this.updateResetButtons();
         }).dimensions(0, 0, resetW, 20).build();
         NonModMenuScreens.setTooltip(this.resetAutoSwitchThirdPersonButton, "config.needsofnature.tooltip.reset");
         settingsList.addEntryRow(
            SettingsList.RowEntry.labeledField(
               this.textRenderer,
               Text.translatable("config.needsofnature.animation.auto_switch_third_person"),
               this.autoSwitchThirdPersonButton,
               this.resetAutoSwitchThirdPersonButton,
               NonModMenuScreens.tooltip("config.needsofnature.tooltip.animation.auto_switch_third_person")
            )
         );
         this.armorShedEffectButton = ButtonWidget.builder(this.toggleText(this.armorShedEffectEnabled), button -> {
            this.armorShedEffectEnabled = !this.armorShedEffectEnabled;
            button.setMessage(this.toggleText(this.armorShedEffectEnabled));
            this.updateResetButtons();
         }).dimensions(0, 0, 100, 20).build();
         NonModMenuScreens.setTooltip(this.armorShedEffectButton, "config.needsofnature.tooltip.armor_shed_effect");
         this.resetArmorShedEffectButton = ButtonWidget.builder(Text.translatable("config.needsofnature.reset_icon"), button -> {
            this.armorShedEffectEnabled = this.defaults.isArmorShedEffectEnabled();
            if (this.armorShedEffectButton != null) {
               this.armorShedEffectButton.setMessage(this.toggleText(this.armorShedEffectEnabled));
            }

            this.updateResetButtons();
         }).dimensions(0, 0, resetW, 20).build();
         NonModMenuScreens.setTooltip(this.resetArmorShedEffectButton, "config.needsofnature.tooltip.reset");
         settingsList.addEntryRow(
            SettingsList.RowEntry.labeledField(
               this.textRenderer,
               Text.translatable("config.needsofnature.armor_shed_effect"),
               this.armorShedEffectButton,
               this.resetArmorShedEffectButton,
               NonModMenuScreens.tooltip("config.needsofnature.tooltip.armor_shed_effect")
            )
         );
         this.actionSoundVolumeSlider = new PercentSliderWidget(0, 0, 180, 20, this.actionSoundVolumePercent, value -> {
            this.actionSoundVolumePercent = value;
            this.updateResetButtons();
         });
         NonModMenuScreens.setTooltip(this.actionSoundVolumeSlider, "config.needsofnature.tooltip.action_sound_volume");
         this.resetActionSoundVolumeButton = ButtonWidget.builder(Text.translatable("config.needsofnature.reset_icon"), button -> {
            this.actionSoundVolumePercent = this.defaults.getActionSoundVolumePercent();
            if (this.actionSoundVolumeSlider != null) {
               this.actionSoundVolumeSlider.setPercent(this.actionSoundVolumePercent);
            }

            this.updateResetButtons();
         }).dimensions(0, 0, resetW, 20).build();
         NonModMenuScreens.setTooltip(this.resetActionSoundVolumeButton, "config.needsofnature.tooltip.reset");
         settingsList.addEntryRow(
            SettingsList.RowEntry.labeledField(
               this.textRenderer,
               Text.translatable("config.needsofnature.action_sound_volume"),
               this.actionSoundVolumeSlider,
               this.resetActionSoundVolumeButton,
               NonModMenuScreens.tooltip("config.needsofnature.tooltip.action_sound_volume")
            )
         );
         this.liquidPuddleDespawnField = this.newNumberField(fieldWidth, this.liquidPuddleDespawnSeconds);
         this.resetLiquidPuddleDespawnButton = ButtonWidget.builder(Text.translatable("config.needsofnature.reset_icon"), button -> {
            this.liquidPuddleDespawnField.setText(String.valueOf(this.defaults.getLiquidPuddleDespawnSeconds()));
            this.updateResetButtons();
         }).dimensions(0, 0, resetW, 20).build();
         NonModMenuScreens.setTooltip(this.resetLiquidPuddleDespawnButton, "config.needsofnature.tooltip.reset");
         settingsList.addEntryRow(
            SettingsList.RowEntry.labeledField(
               this.textRenderer,
               Text.translatable("config.needsofnature.liquid_puddle_despawn_seconds"),
               this.liquidPuddleDespawnField,
               this.resetLiquidPuddleDespawnButton,
               NonModMenuScreens.tooltip("config.needsofnature.tooltip.liquid_puddle_despawn_seconds")
            )
         );
         this.liquidPuddleDespawnField.setChangedListener(ignored -> this.updateResetButtons());
         ButtonWidget intifaceButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.category.intiface"),
               button -> MinecraftClient.getInstance().setScreen(new NonModMenuIntifaceScreen(this, this.config))
            )
            .dimensions(0, 0, 220, 20)
            .build();
         NonModMenuScreens.setTooltip(intifaceButton, "config.needsofnature.tooltip.category.intiface");
         settingsList.addEntryRow(
            SettingsList.RowEntry.buttonWithReset(
               this.textRenderer, intifaceButton, NonModMenuScreens.tooltip("config.needsofnature.tooltip.category.intiface")
            )
         );
         ButtonWidget interfaceButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.category.interface"),
               button -> MinecraftClient.getInstance().setScreen(new NonModMenuHudConfigScreen.UiConfigScreen(this, this.config))
            )
            .dimensions(0, 0, 220, 20)
            .build();
         NonModMenuScreens.setTooltip(interfaceButton, "config.needsofnature.tooltip.category.interface");
         settingsList.addEntryRow(
            SettingsList.RowEntry.buttonWithReset(
               this.textRenderer, interfaceButton, NonModMenuScreens.tooltip("config.needsofnature.tooltip.category.interface")
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
         this.liquidPuddleDespawnSeconds = this.clampLiquidPuddleDespawnSeconds(this.parseField(this.liquidPuddleDespawnField, this.liquidPuddleDespawnSeconds));
         if (this.playerGenderEditable) {
            this.config.setPlayerGenderSelection(this.playerGenderSelection);
         }

         this.config.setSyncFemaleGenderModWithNonGender(this.syncFemaleGenderModWithNonGender);
         this.config.setArmorShedEffectEnabled(this.armorShedEffectEnabled);
         this.config.setAutoSwitchThirdPersonOnAnimationStart(this.autoSwitchThirdPersonOnAnimationStart);
         this.config.setActionSoundVolumePercent(this.actionSoundVolumePercent);
         this.config.setLiquidPuddleDespawnSeconds(this.liquidPuddleDespawnSeconds);
         this.config.save();
         if (this.playerGenderEditable) {
            NeedsOfNatureClient.pushConfiguredPlayerGenderToServer(this.config);
         }

         NonWildfireGenderSync.syncDestroyedSkinOverrides(this.config);
         MinecraftClient.getInstance().setScreen(this.parent);
      }

      private Text playerGenderText(NonConfig.PlayerGenderSelection selection) {
         NonConfig.PlayerGenderSelection resolved = selection == null ? NonConfig.PlayerGenderSelection.FEMALE : selection;

         return switch (resolved) {
            case MALE -> Text.translatable("config.needsofnature.player_gender.male");
            case FEMALE -> Text.translatable("config.needsofnature.player_gender.female");
            case BOTH -> Text.translatable("config.needsofnature.player_gender.both");
         };
      }

      private Text toggleText(boolean enabled) {
         return Text.translatable(enabled ? "options.on" : "options.off");
      }

      private NonConfig.PlayerGenderSelection currentPlayerGenderSelection(NonConfig.PlayerGenderSelection fallback) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player instanceof GenderHolder holder) {
            int mask = holder.getGenderMask() & 3;
            if (mask != 0) {
               return NonConfig.PlayerGenderSelection.fromMask(mask, fallback);
            }
         }

         return fallback == null ? NonConfig.PlayerGenderSelection.FEMALE : fallback;
      }

      private TextFieldWidget newNumberField(int w, int initial) {
         TextFieldWidget field = new TextFieldWidget(this.textRenderer, 0, 0, w, 20, Text.literal(""));
         field.setText(String.valueOf(initial));
         field.setMaxLength(8);
         field.setEditable(true);
         return field;
      }

      private int parseField(TextFieldWidget field, int fallback) {
         if (field == null) {
            return fallback;
         }

         try {
            return Integer.parseInt(field.getText().trim());
         } catch (NumberFormatException e) {
            return fallback;
         }
      }

      private int clampLiquidPuddleDespawnSeconds(int v) {
         return Math.max(1, Math.min(300, v));
      }

      private void updateResetButtons() {
         if (this.resetPlayerGenderButton != null) {
            this.resetPlayerGenderButton.active = this.playerGenderEditable && this.playerGenderSelection != this.defaults.getPlayerGenderSelection();
         }

         if (this.playerGenderButton != null) {
            this.playerGenderButton.active = this.playerGenderEditable;
         }

         if (this.resetSyncFemaleGenderModWithNonGenderButton != null) {
            this.resetSyncFemaleGenderModWithNonGenderButton.active = this.syncFemaleGenderModWithNonGender
               != this.defaults.isSyncFemaleGenderModWithNonGender();
         }

         if (this.resetArmorShedEffectButton != null) {
            this.resetArmorShedEffectButton.active = this.armorShedEffectEnabled != this.defaults.isArmorShedEffectEnabled();
         }

         if (this.resetAutoSwitchThirdPersonButton != null) {
            this.resetAutoSwitchThirdPersonButton.active = this.autoSwitchThirdPersonOnAnimationStart != this.defaults.autoSwitchThirdPersonOnAnimationStart();
         }

         if (this.resetActionSoundVolumeButton != null) {
            this.resetActionSoundVolumeButton.active = this.actionSoundVolumePercent != this.defaults.getActionSoundVolumePercent();
         }

         if (this.resetLiquidPuddleDespawnButton != null) {
            this.resetLiquidPuddleDespawnButton.active = this.parseField(this.liquidPuddleDespawnField, this.defaults.getLiquidPuddleDespawnSeconds())
               != this.defaults.getLiquidPuddleDespawnSeconds();
         }
      }
   }
}
