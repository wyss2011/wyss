package com.nonid.integration;

import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import com.nonid.NonDebugChatMode;
import com.nonid.client.NonAnimationPreviewRenderer;
import com.nonid.client.NonLiquidColors;
import com.nonid.client.NonServerSettingsClient;
import com.nonid.client.NonTagLocalizer;
import com.nonid.data.NonEntityProfiles;
import com.nonid.data.NonLiquidGainOverrides;
import com.nonid.internal.animation.AnimationEngine;
import com.nonid.internal.animation.AnimationEngineClient;
import com.nonid.internal.animation.client.render.gecko.GeckoResourceResolver;
import com.nonid.internal.animation.client.render.gecko.GenderedModelResolver;
import com.nonid.internal.animation.client.render.gecko.VanillaTextureResolver;
import com.nonid.network.AnimationCatalogData;
import com.nonid.pack.NonExternalPackProvider;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget.PressAction;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.registry.Registries;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

final class NonModMenuDebugScreens {
   private NonModMenuDebugScreens() {
   }

   static class DebugAdvancedScreen extends Screen {
      private final Screen parent;
      private final NonConfig config;
      private final NonConfig localConfig;
      private final NonConfig defaults = new NonConfig();
      private boolean serverConfigEditable;
      private int scanBudget;
      private NonDebugChatMode debugChatMode;
      private boolean debugSpinMode;
      private boolean convertMaleOnlyVInjectionsToA;
      private final boolean initialForceVanillaEntityTextures;
      private boolean forceVanillaEntityTextures;
      private int animationBlockSearchRadius;
      private TextFieldWidget budgetField;
      private ButtonWidget debugModeButton;
      private ButtonWidget debugSpinModeButton;
      private ButtonWidget convertMaleOnlyVInjectionsToAButton;
      private ButtonWidget resetBudgetButton;
      private ButtonWidget resetDebugModeButton;
      private ButtonWidget resetDebugSpinModeButton;
      private ButtonWidget resetConvertMaleOnlyVInjectionsToAButton;
      private ButtonWidget forceVanillaEntityTexturesButton;
      private ButtonWidget animationBlockSearchRadiusButton;
      private ButtonWidget resetForceVanillaEntityTexturesButton;
      private ButtonWidget resetAnimationBlockSearchRadiusButton;

      protected DebugAdvancedScreen(Screen parent, NonConfig config, NonConfig localConfig) {
         super(Text.translatable("config.needsofnature.debug_advanced_title"));
         this.parent = parent;
         this.config = config;
         this.localConfig = localConfig;
         this.scanBudget = config.getScanBudgetPerTick();
         this.debugChatMode = localConfig.debugChatMode();
         this.debugSpinMode = localConfig.isDebugSpinMode();
         this.convertMaleOnlyVInjectionsToA = config.convertMaleOnlyVInjectionsToA();
         this.initialForceVanillaEntityTextures = localConfig.forceVanillaEntityTextures();
         this.forceVanillaEntityTextures = this.initialForceVanillaEntityTextures;
         this.animationBlockSearchRadius = config.blockSearchRadius();
      }

      protected void init() {
         this.serverConfigEditable = NonModMenuScreens.canEditServerGameplaySettings();
         if (this.budgetField != null) {
            this.scanBudget = this.parseField(this.budgetField, this.scanBudget);
         }

         int listTop = 32;
         int bottomArea = 40;
         int listHeight = Math.max(0, this.height - listTop - bottomArea);
         SettingsList settingsList = new SettingsList(this.client, this.width, listHeight, listTop);
         this.addDrawableChild(settingsList);
         int fieldWidth = 50;
         int resetW = 20;
         settingsList.addEntryRow(SettingsList.RowEntry.sectionHeader(this.textRenderer, Text.translatable("config.needsofnature.section.runtime_budget")));
         this.budgetField = this.newNumberField(fieldWidth, this.scanBudget);
         this.resetBudgetButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.reset_icon"), button -> this.budgetField.setText(String.valueOf(this.defaults.getScanBudgetPerTick()))
            )
            .dimensions(0, 0, resetW, 20)
            .build();
         NonModMenuScreens.setTooltip(this.resetBudgetButton, "config.needsofnature.tooltip.reset");
         settingsList.addEntryRow(
            SettingsList.RowEntry.labeledField(
               this.textRenderer,
               Text.translatable("config.needsofnature.scan_budget"),
               this.budgetField,
               this.resetBudgetButton,
               NonModMenuScreens.tooltip("config.needsofnature.tooltip.scan_budget")
            )
         );
         this.budgetField.setEditable(this.serverConfigEditable);
         settingsList.addEntryRow(SettingsList.RowEntry.sectionHeader(this.textRenderer, Text.translatable("config.needsofnature.section.debug_feedback")));
         this.debugModeButton = ButtonWidget.builder(this.debugChatLabel(this.debugChatMode), button -> {
            this.debugChatMode = this.nextDebugChatMode(this.debugChatMode);
            button.setMessage(this.debugChatLabel(this.debugChatMode));
            this.updateResetButtons();
         }).dimensions(0, 0, 100, 20).build();
         this.resetDebugModeButton = ButtonWidget.builder(Text.translatable("config.needsofnature.reset_icon"), button -> {
            this.debugChatMode = this.defaults.debugChatMode();
            this.debugModeButton.setMessage(this.debugChatLabel(this.debugChatMode));
            this.updateResetButtons();
         }).dimensions(0, 0, resetW, 20).build();
         NonModMenuScreens.setTooltip(this.resetDebugModeButton, "config.needsofnature.tooltip.reset");
         settingsList.addEntryRow(
            SettingsList.RowEntry.labeledField(
               this.textRenderer,
               Text.translatable("config.needsofnature.debug_chat_mode"),
               this.debugModeButton,
               this.resetDebugModeButton,
               NonModMenuScreens.tooltip("config.needsofnature.tooltip.debug_chat_mode")
            )
         );
         this.debugSpinModeButton = ButtonWidget.builder(this.debugSpinModeLabel(this.debugSpinMode), button -> {
            this.debugSpinMode = !this.debugSpinMode;
            button.setMessage(this.debugSpinModeLabel(this.debugSpinMode));
            this.updateResetButtons();
         }).dimensions(0, 0, 100, 20).build();
         this.resetDebugSpinModeButton = ButtonWidget.builder(Text.translatable("config.needsofnature.reset_icon"), button -> {
            this.debugSpinMode = this.defaults.isDebugSpinMode();
            this.debugSpinModeButton.setMessage(this.debugSpinModeLabel(this.debugSpinMode));
            this.updateResetButtons();
         }).dimensions(0, 0, resetW, 20).build();
         NonModMenuScreens.setTooltip(this.resetDebugSpinModeButton, "config.needsofnature.tooltip.reset");
         settingsList.addEntryRow(
            SettingsList.RowEntry.labeledField(
               this.textRenderer,
               Text.translatable("config.needsofnature.debug_spin_mode"),
               this.debugSpinModeButton,
               this.resetDebugSpinModeButton,
               NonModMenuScreens.tooltip("config.needsofnature.tooltip.debug_spin_mode")
            )
         );
         this.convertMaleOnlyVInjectionsToAButton = ButtonWidget.builder(this.debugBooleanLabel(this.convertMaleOnlyVInjectionsToA), button -> {
            this.convertMaleOnlyVInjectionsToA = !this.convertMaleOnlyVInjectionsToA;
            button.setMessage(this.debugBooleanLabel(this.convertMaleOnlyVInjectionsToA));
            this.updateResetButtons();
         }).dimensions(0, 0, 100, 20).build();
         this.resetConvertMaleOnlyVInjectionsToAButton = ButtonWidget.builder(Text.translatable("config.needsofnature.reset_icon"), button -> {
            this.convertMaleOnlyVInjectionsToA = this.defaults.convertMaleOnlyVInjectionsToA();
            this.convertMaleOnlyVInjectionsToAButton.setMessage(this.debugBooleanLabel(this.convertMaleOnlyVInjectionsToA));
            this.updateResetButtons();
         }).dimensions(0, 0, resetW, 20).build();
         NonModMenuScreens.setTooltip(this.resetConvertMaleOnlyVInjectionsToAButton, "config.needsofnature.tooltip.reset");
         settingsList.addEntryRow(
            SettingsList.RowEntry.labeledField(
               this.textRenderer,
               Text.translatable("config.needsofnature.convert_male_v_to_a"),
               this.convertMaleOnlyVInjectionsToAButton,
               this.resetConvertMaleOnlyVInjectionsToAButton,
               NonModMenuScreens.tooltip("config.needsofnature.tooltip.convert_male_v_to_a")
            )
         );
         settingsList.addEntryRow(SettingsList.RowEntry.sectionHeader(this.textRenderer, Text.translatable("config.needsofnature.section.animation_runtime")));
         this.forceVanillaEntityTexturesButton = ButtonWidget.builder(this.onOffLabel(this.forceVanillaEntityTextures), button -> {
            this.forceVanillaEntityTextures = !this.forceVanillaEntityTextures;
            button.setMessage(this.onOffLabel(this.forceVanillaEntityTextures));
            this.updateResetButtons();
         }).dimensions(0, 0, 100, 20).build();
         this.resetForceVanillaEntityTexturesButton = this.resetButton(button -> {
            this.forceVanillaEntityTextures = this.defaults.forceVanillaEntityTextures();
            this.forceVanillaEntityTexturesButton.setMessage(this.onOffLabel(this.forceVanillaEntityTextures));
            this.updateResetButtons();
         });
         settingsList.addEntryRow(
            SettingsList.RowEntry.labeledField(
               this.textRenderer,
               Text.translatable("config.needsofnature.animation.force_vanilla_entity_textures"),
               this.forceVanillaEntityTexturesButton,
               this.resetForceVanillaEntityTexturesButton,
               null
            )
         );
         this.animationBlockSearchRadiusButton = ButtonWidget.builder(Text.literal(String.valueOf(this.animationBlockSearchRadius)), button -> {
            this.animationBlockSearchRadius = this.animationBlockSearchRadius >= 16 ? 1 : this.animationBlockSearchRadius + 1;
            button.setMessage(Text.literal(String.valueOf(this.animationBlockSearchRadius)));
            this.updateResetButtons();
         }).dimensions(0, 0, 100, 20).build();
         this.resetAnimationBlockSearchRadiusButton = this.resetButton(button -> {
            this.animationBlockSearchRadius = this.defaults.blockSearchRadius();
            this.animationBlockSearchRadiusButton.setMessage(Text.literal(String.valueOf(this.animationBlockSearchRadius)));
            this.updateResetButtons();
         });
         settingsList.addEntryRow(
            SettingsList.RowEntry.labeledField(
               this.textRenderer,
               Text.translatable("config.needsofnature.animation.block_search_radius"),
               this.animationBlockSearchRadiusButton,
               this.resetAnimationBlockSearchRadiusButton,
               null
            )
         );
         settingsList.addEntryRow(SettingsList.RowEntry.sectionHeader(this.textRenderer, Text.translatable("config.needsofnature.section.animation_data")));
         ButtonWidget loadedAnimationsButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.loaded_animations"),
               button -> MinecraftClient.getInstance().setScreen(new NonModMenuDebugScreens.LoadedAnimationsScreen(this, this.config))
            )
            .dimensions(0, 0, 220, 20)
            .build();
         NonModMenuScreens.setTooltip(loadedAnimationsButton, "config.needsofnature.tooltip.loaded_animations");
         settingsList.addEntryRow(
            SettingsList.RowEntry.buttonWithReset(
               this.textRenderer, loadedAnimationsButton, NonModMenuScreens.tooltip("config.needsofnature.tooltip.loaded_animations")
            )
         );
         ButtonWidget loadOrderButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.loaded_animations.load_order"),
               button -> MinecraftClient.getInstance().setScreen(new NonModMenuDebugScreens.LoadOrderScreen(this, this.localConfig))
            )
            .dimensions(0, 0, 220, 20)
            .build();
         NonModMenuScreens.setTooltip(loadOrderButton, "config.needsofnature.tooltip.loaded_animations.load_order");
         settingsList.addEntryRow(
            SettingsList.RowEntry.buttonWithReset(
               this.textRenderer, loadOrderButton, NonModMenuScreens.tooltip("config.needsofnature.tooltip.loaded_animations.load_order")
            )
         );
         this.budgetField.setChangedListener(ignored -> this.updateResetButtons());
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
         if (this.serverConfigEditable) {
            this.scanBudget = this.clampBudget(this.parseField(this.budgetField, this.scanBudget));
            this.config.setScanBudgetPerTick(this.scanBudget);
            this.config.setConvertMaleOnlyVInjectionsToA(this.convertMaleOnlyVInjectionsToA);
            this.config.setBlockSearchRadius(this.animationBlockSearchRadius);
            NonModMenuScreens.saveServerConfig(this.config);
         }

         this.localConfig.setDebugChatMode(this.debugChatMode);
         this.localConfig.setDebugSpinMode(this.debugSpinMode);
         this.localConfig.setForceVanillaEntityTextures(this.forceVanillaEntityTextures);
         this.localConfig.save();
         if (this.forceVanillaEntityTextures != this.initialForceVanillaEntityTextures) {
            VanillaTextureResolver.clearCache();
         }

         AnimationEngineClient.sendDebugChatPreference();
         MinecraftClient.getInstance().setScreen(this.parent);
      }

      private TextFieldWidget newNumberField(int w, int initial) {
         TextFieldWidget field = new TextFieldWidget(this.textRenderer, 0, 0, w, 20, Text.literal(""));
         field.setText(String.valueOf(initial));
         field.setMaxLength(8);
         field.setEditable(true);
         return field;
      }

      private int parseField(TextFieldWidget field, int fallback) {
         try {
            return Integer.parseInt(field.getText().trim());
         } catch (NumberFormatException e) {
            return fallback;
         }
      }

      private int clampBudget(int v) {
         return Math.max(1, Math.min(512, v));
      }

      private Text debugChatLabel(NonDebugChatMode mode) {
         NonDebugChatMode resolved = mode == null ? NonDebugChatMode.SETUP_ERRORS : mode;
         return Text.translatable("config.needsofnature.debug_chat_mode." + resolved.id());
      }

      private NonDebugChatMode nextDebugChatMode(NonDebugChatMode current) {
         if (current == null) {
            return NonDebugChatMode.SETUP_ERRORS;
         }

         return switch (current) {
            case ALL -> NonDebugChatMode.SETUP_WARNINGS_ERRORS;
            case SETUP_WARNINGS_ERRORS -> NonDebugChatMode.SETUP_ERRORS;
            case SETUP_ERRORS -> NonDebugChatMode.ERRORS_ONLY;
            case ERRORS_ONLY -> NonDebugChatMode.ALL;
         };
      }

      private Text debugSpinModeLabel(boolean enabled) {
         return Text.translatable("config.needsofnature.debug_spin_mode.value", new Object[]{Text.translatable(enabled ? "options.on" : "options.off")});
      }

      private Text debugBooleanLabel(boolean enabled) {
         return Text.translatable(enabled ? "options.on" : "options.off");
      }

      private Text onOffLabel(boolean enabled) {
         return Text.translatable(enabled ? "options.on" : "options.off");
      }

      private ButtonWidget resetButton(PressAction action) {
         ButtonWidget button = ButtonWidget.builder(Text.translatable("config.needsofnature.reset_icon"), action).dimensions(0, 0, 20, 20).build();
         NonModMenuScreens.setTooltip(button, "config.needsofnature.tooltip.reset");
         return button;
      }

      private void updateResetButtons() {
         if (this.resetBudgetButton != null) {
            this.resetBudgetButton.active = this.serverConfigEditable
               && this.parseField(this.budgetField, this.defaults.getScanBudgetPerTick()) != this.defaults.getScanBudgetPerTick();
         }

         if (this.resetDebugModeButton != null) {
            this.resetDebugModeButton.active = this.debugChatMode != this.defaults.debugChatMode();
         }

         if (this.resetDebugSpinModeButton != null) {
            this.resetDebugSpinModeButton.active = this.debugSpinMode != this.defaults.isDebugSpinMode();
         }

         if (this.resetConvertMaleOnlyVInjectionsToAButton != null) {
            this.resetConvertMaleOnlyVInjectionsToAButton.active = this.serverConfigEditable
               && this.convertMaleOnlyVInjectionsToA != this.defaults.convertMaleOnlyVInjectionsToA();
         }

         if (this.resetForceVanillaEntityTexturesButton != null) {
            this.resetForceVanillaEntityTexturesButton.active = this.forceVanillaEntityTextures != this.defaults.forceVanillaEntityTextures();
         }

         if (this.resetAnimationBlockSearchRadiusButton != null) {
            this.resetAnimationBlockSearchRadiusButton.active = this.serverConfigEditable
               && this.animationBlockSearchRadius != this.defaults.blockSearchRadius();
         }

         if (this.animationBlockSearchRadiusButton != null) {
            this.animationBlockSearchRadiusButton.active = this.serverConfigEditable;
         }

         if (this.convertMaleOnlyVInjectionsToAButton != null) {
            this.convertMaleOnlyVInjectionsToAButton.active = this.serverConfigEditable;
         }
      }
   }

   static class GenderSpawnConfigScreen extends Screen {
      private final Screen parent;
      private final NonConfig config;
      private boolean serverConfigEditable;
      private Map<String, NonConfig.GenderSpawnChances> appliedMap;
      private NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList genderList;
      private final List<NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList.GenderEntry> rows = new ArrayList<>();
      private ButtonWidget applyButton;
      private ButtonWidget doneButton;

      protected GenderSpawnConfigScreen(Screen parent, NonConfig config) {
         super(Text.translatable("config.needsofnature.gender_spawn_title"));
         this.parent = parent;
         this.config = config;
         this.serverConfigEditable = NonModMenuScreens.canEditServerGameplaySettings();
         this.appliedMap = new LinkedHashMap<>(NonEntityProfiles.resolveEffectiveGenderSpawnMap());
      }

      protected void init() {
         this.serverConfigEditable = NonModMenuScreens.canEditServerGameplaySettings();
         List<NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderRowData> preserved = this.snapshotRows();
         if (preserved == null) {
            preserved = new ArrayList<>();

            for (Entry<String, NonConfig.GenderSpawnChances> entry : this.appliedMap.entrySet()) {
               NonConfig.GenderSpawnChances chances = entry.getValue();
               if (chances != null) {
                  preserved.add(
                     new NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderRowData(
                        entry.getKey(), String.valueOf(chances.male()), String.valueOf(chances.female()), String.valueOf(chances.both())
                     )
                  );
               }
            }
         }

         int listTop = 44;
         int bottomArea = 64;
         int listHeight = Math.max(0, this.height - listTop - bottomArea);
         this.genderList = new NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList(this.client, this.width, listHeight, listTop);
         this.addDrawableChild(this.genderList);
         this.rows.clear();

         for (NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderRowData data : preserved) {
            this.addRow(data.id(), data.male(), data.female(), data.both());
         }

         if (this.rows.isEmpty()) {
            this.addRow("", "48", "47", "5");
         }

         int centerX = this.width / 2;
         ButtonWidget addButton = ButtonWidget.builder(Text.translatable("config.needsofnature.gender_spawn_add"), button -> {
            this.addRow("", "48", "47", "5");
            this.updateApplyButton();
         }).dimensions(centerX - 100, this.height - 52, 96, 20).build();
         addButton.active = this.serverConfigEditable;
         NonModMenuScreens.setTooltip(addButton, "config.needsofnature.tooltip.gender_spawn_add");
         this.addDrawableChild(addButton);
         ButtonWidget resetDefaultsButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.gender_spawn_reset"),
               button -> MinecraftClient.getInstance().setScreen(new NonModMenuDebugScreens.GenderSpawnConfigScreen.ResetGenderSpawnConfirmScreen(this, () -> {
                  this.applyDefaultsToRows();
                  this.updateApplyButton();
                  MinecraftClient.getInstance().setScreen(this);
               }))
            )
            .dimensions(centerX + 4, this.height - 52, 96, 20)
            .build();
         resetDefaultsButton.active = this.serverConfigEditable;
         NonModMenuScreens.setTooltip(resetDefaultsButton, "config.needsofnature.tooltip.gender_spawn_reset");
         this.addDrawableChild(resetDefaultsButton);
         this.applyButton = ButtonWidget.builder(Text.translatable("config.needsofnature.apply"), button -> {
            if (this.serverConfigEditable) {
               if (this.hasInvalidGenderChanceRows()) {
                  this.updateApplyButton();
               } else {
                  Map<String, NonConfig.GenderSpawnChances> next = this.collectGenderMapFromFields();
                  this.config.setEntityProfilesByEntity(this.mergeGenderSpawnIntoEntityProfiles(next));
                  NonModMenuScreens.saveServerConfig(this.config);
                  this.appliedMap = new LinkedHashMap<>(next);
                  this.updateApplyButton();
               }
            }
         }).dimensions(centerX - 100, this.height - 28, 96, 20).build();
         NonModMenuScreens.setTooltip(this.applyButton, "config.needsofnature.tooltip.apply");
         this.addDrawableChild(this.applyButton);
         this.doneButton = ButtonWidget.builder(Text.translatable("config.needsofnature.done"), button -> MinecraftClient.getInstance().setScreen(this.parent))
            .dimensions(centerX + 4, this.height - 28, 96, 20)
            .build();
         NonModMenuScreens.setTooltip(this.doneButton, "config.needsofnature.tooltip.done_unsaved");
         this.addDrawableChild(this.doneButton);
         this.updateApplyButton();
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         this.updateApplyButton();
         super.render(context, mouseX, mouseY, deltaTicks);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 16777215);
         this.drawGenderSpawnHeader(context);
      }

      public void close() {
         MinecraftClient.getInstance().setScreen(this.parent);
      }

      private void drawGenderSpawnHeader(DrawContext context) {
         if (this.genderList != null) {
            int rowX = this.genderList.getRowLeft();
            int rowWidth = this.genderList.getRowWidth();
            int fieldW = 42;
            int removeW = 20;
            int idW = Math.max(130, rowWidth - removeW - fieldW * 3 - 30);
            int maleX = rowX + idW + 6;
            int femaleX = maleX + fieldW + 6;
            int bothX = femaleX + fieldW + 6;
            int y = 34;
            int color = -4342339;
            context.drawTextWithShadow(this.textRenderer, Text.translatable("config.needsofnature.gender_spawn_column.entity"), rowX + 4, y, color);
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.translatable("config.needsofnature.gender_spawn_column.male"), maleX + fieldW / 2, y, color
            );
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.translatable("config.needsofnature.gender_spawn_column.female"), femaleX + fieldW / 2, y, color
            );
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.translatable("config.needsofnature.gender_spawn_column.both"), bothX + fieldW / 2, y, color
            );
         }
      }

      private void addRow(String idText, String maleText, String femaleText, String bothText) {
         NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList.GenderEntry[] holder = new NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList.GenderEntry[1];
         NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList.GenderEntry entry = new NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList.GenderEntry(
            this.textRenderer, idText, maleText, femaleText, bothText, () -> {
               NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList.GenderEntry current = holder[0];
               if (current != null) {
                  this.rows.removeIf(row -> row == current);
                  this.genderList.removeEntryRow(current);
                  this.updateApplyButton();
               }
            }
         );
         holder[0] = entry;
         entry.setOnChange(this::updateApplyButton);
         entry.setEditable(this.serverConfigEditable);
         this.rows.add(entry);
         this.genderList.addEntryRow(entry);
      }

      private void updateApplyButton() {
         if (this.applyButton != null) {
            if (!this.serverConfigEditable) {
               this.applyButton.active = false;
               if (this.doneButton != null) {
                  this.doneButton.setMessage(Text.translatable("config.needsofnature.done"));
               }
            } else {
               Map<String, NonConfig.GenderSpawnChances> current = this.collectGenderMapFromFields();
               boolean hasUnsavedChanges = !current.equals(this.appliedMap);
               boolean hasInvalidRows = this.hasInvalidGenderChanceRows();
               this.applyButton.active = hasUnsavedChanges && !hasInvalidRows;
               this.applyButton
                  .setMessage(Text.translatable(hasInvalidRows ? "config.needsofnature.gender_spawn_invalid_total" : "config.needsofnature.apply"));
               if (this.doneButton != null) {
                  this.doneButton.setMessage(Text.translatable(hasUnsavedChanges ? "config.needsofnature.done_unsaved" : "config.needsofnature.done"));
               }
            }
         }
      }

      private boolean hasInvalidGenderChanceRows() {
         for (NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList.GenderEntry row : this.rows) {
            if (row != null) {
               String id = row.getIdText();
               if (id != null && !id.trim().isEmpty()) {
                  int total = this.parseInt(row.getMaleText()) + this.parseInt(row.getFemaleText()) + this.parseInt(row.getBothText());
                  if (total != 100) {
                     return true;
                  }
               }
            }
         }

         return false;
      }

      private void applyDefaultsToRows() {
         Map<String, NonConfig.GenderSpawnChances> defaultsMap = new LinkedHashMap<>();

         for (Entry<String, NonConfig.EntityProfile> entry : NonEntityProfiles.resolveDefaultProfilesMap().entrySet()) {
            NonConfig.GenderSpawnChances chances = entry.getValue().genderSpawn();
            if (chances != null) {
               defaultsMap.put(entry.getKey(), chances);
            }
         }

         if (this.genderList != null) {
            for (NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList.GenderEntry entry : new ArrayList<>(this.rows)) {
               this.genderList.removeEntryRow(entry);
            }
         }

         this.rows.clear();

         for (Entry<String, NonConfig.GenderSpawnChances> entry : defaultsMap.entrySet()) {
            NonConfig.GenderSpawnChances chances = entry.getValue();
            if (chances != null) {
               this.addRow(entry.getKey(), String.valueOf(chances.male()), String.valueOf(chances.female()), String.valueOf(chances.both()));
            }
         }

         if (this.rows.isEmpty()) {
            this.addRow("", "48", "47", "5");
         }
      }

      private Map<String, NonConfig.EntityProfile> mergeGenderSpawnIntoEntityProfiles(Map<String, NonConfig.GenderSpawnChances> genderMap) {
         Map<String, NonConfig.EntityProfile> profiles = new LinkedHashMap<>();
         if (genderMap != null) {
            for (Entry<String, NonConfig.GenderSpawnChances> entry : genderMap.entrySet()) {
               Identifier id = Identifier.tryParse(entry.getKey());
               if (id != null && entry.getValue() != null) {
                  NonConfig.EntityProfile base = this.config.getEntityProfilesByEntity().get(entry.getKey());
                  NonConfig.GenderSpawnChances defaultGender = NonEntityProfiles.resolveDefaultProfile(id).genderSpawn();
                  NonConfig.GenderSpawnChances savedGender = entry.getValue().equals(defaultGender) ? null : entry.getValue();
                  NonConfig.EntityProfile next = new NonConfig.EntityProfile(
                        base == null ? null : base.pregnancyChancePercent(),
                        base == null ? null : base.offspringMin(),
                        base == null ? null : base.offspringMax(),
                        base == null ? null : base.birthEntity(),
                        base == null ? null : base.birthMode(),
                        savedGender,
                        base == null ? null : base.egg(),
                        base == null ? null : base.lookAtNearbyAnimations(),
                        base == null ? null : base.multiActorJoinChancePercent(),
                        base == null ? null : base.energyGainMultiplier()
                     )
                     .sanitized();
                  if (!next.isEmpty()) {
                     profiles.put(entry.getKey(), next);
                  }
               }
            }
         }

         return profiles;
      }

      private Map<String, NonConfig.GenderSpawnChances> collectGenderMapFromFields() {
         Map<String, NonConfig.GenderSpawnChances> map = new LinkedHashMap<>();

         for (NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList.GenderEntry row : this.rows) {
            if (row != null) {
               String id = row.getIdText();
               if (id != null) {
                  id = id.trim();
                  if (!id.isEmpty() && Identifier.tryParse(id) != null) {
                     NonConfig.GenderSpawnChances chances = NonConfig.sanitizeGenderSpawnChances(
                        new NonConfig.GenderSpawnChances(this.parseInt(row.getMaleText()), this.parseInt(row.getFemaleText()), this.parseInt(row.getBothText()))
                     );
                     if (chances != null) {
                        map.put(id, chances);
                     }
                  }
               }
            }
         }

         return map;
      }

      private List<NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderRowData> snapshotRows() {
         if (this.rows.isEmpty()) {
            return null;
         }

         List<NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderRowData> data = new ArrayList<>();

         for (NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList.GenderEntry row : this.rows) {
            data.add(
               new NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderRowData(row.getIdText(), row.getMaleText(), row.getFemaleText(), row.getBothText())
            );
         }

         return data;
      }

      private int parseInt(String value) {
         if (value == null) {
            return 0;
         }

         try {
            return Integer.parseInt(value.trim());
         } catch (NumberFormatException e) {
            return 0;
         }
      }

      private record GenderRowData(String id, String male, String female, String both) {
      }

      private static class GenderSpawnList extends ElementListWidget<NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList.GenderEntry> {
         private GenderSpawnList(MinecraftClient client, int width, int height, int top) {
            super(client, width, height, top, 24);
            this.centerListVertically = false;
         }

         private void addEntryRow(NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList.GenderEntry entry) {
            super.addEntry(entry);
         }

         private void removeEntryRow(NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList.GenderEntry entry) {
            super.removeEntry(entry);
         }

         public int getRowWidth() {
            return 420;
         }

         public int getRowLeft() {
            return (this.getWidth() - this.getRowWidth()) / 2;
         }

         private static final class GenderEntry
            extends net.minecraft.client.gui.widget.ElementListWidget.Entry<NonModMenuDebugScreens.GenderSpawnConfigScreen.GenderSpawnList.GenderEntry> {
            private final TextFieldWidget idField;
            private final TextFieldWidget maleField;
            private final TextFieldWidget femaleField;
            private final TextFieldWidget bothField;
            private final ButtonWidget removeButton;
            private final List<ClickableWidget> widgets;
            private Runnable onChange;

            private GenderEntry(TextRenderer textRenderer, String idText, String maleText, String femaleText, String bothText, Runnable onRemove) {
               this.idField = new TextFieldWidget(textRenderer, 0, 0, 170, 20, Text.literal(""));
               this.idField.setText(idText == null ? "" : idText);
               this.idField.setMaxLength(80);
               NonModMenuScreens.setTooltip(this.idField, "config.needsofnature.tooltip.gender_spawn_entry_entity");
               this.maleField = percentField(textRenderer, maleText);
               NonModMenuScreens.setTooltip(this.maleField, "config.needsofnature.tooltip.gender_spawn_entry_male");
               this.femaleField = percentField(textRenderer, femaleText);
               NonModMenuScreens.setTooltip(this.femaleField, "config.needsofnature.tooltip.gender_spawn_entry_female");
               this.bothField = percentField(textRenderer, bothText);
               NonModMenuScreens.setTooltip(this.bothField, "config.needsofnature.tooltip.gender_spawn_entry_both");
               this.removeButton = ButtonWidget.builder(Text.translatable("config.needsofnature.remove_icon"), button -> {
                  if (onRemove != null) {
                     onRemove.run();
                  }
               }).dimensions(0, 0, 20, 20).build();
               NonModMenuScreens.setTooltip(this.removeButton, "config.needsofnature.tooltip.remove");
               this.widgets = new ArrayList<>(5);
               this.widgets.add(this.idField);
               this.widgets.add(this.maleField);
               this.widgets.add(this.femaleField);
               this.widgets.add(this.bothField);
               this.widgets.add(this.removeButton);
               this.idField.setChangedListener(ignored -> this.notifyChanged());
               this.maleField.setChangedListener(ignored -> this.notifyChanged());
               this.femaleField.setChangedListener(ignored -> this.notifyChanged());
               this.bothField.setChangedListener(ignored -> this.notifyChanged());
            }

            private static TextFieldWidget percentField(TextRenderer textRenderer, String text) {
               TextFieldWidget field = new TextFieldWidget(textRenderer, 0, 0, 42, 20, Text.literal(""));
               field.setText(text == null ? "0" : text);
               field.setMaxLength(3);
               return field;
            }

            private void setOnChange(Runnable onChange) {
               this.onChange = onChange;
            }

            private void notifyChanged() {
               if (this.onChange != null) {
                  this.onChange.run();
               }
            }

            private void setEditable(boolean editable) {
               this.idField.setEditable(editable);
               this.maleField.setEditable(editable);
               this.femaleField.setEditable(editable);
               this.bothField.setEditable(editable);
               this.removeButton.active = editable;
            }

            private String getIdText() {
               return this.idField.getText();
            }

            private String getMaleText() {
               return this.maleField.getText();
            }

            private String getFemaleText() {
               return this.femaleField.getText();
            }

            private String getBothText() {
               return this.bothField.getText();
            }

            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
               int rowX = this.getContentX();
               int rowY = this.getContentY();
               int rowWidth = this.getContentWidth();
               int widgetY = rowY + 2;
               int removeW = this.removeButton.getWidth();
               int fieldW = this.maleField.getWidth();
               int idW = Math.max(130, rowWidth - removeW - fieldW * 3 - 30);
               int maleX = rowX + idW + 6;
               int femaleX = maleX + fieldW + 6;
               int bothX = femaleX + fieldW + 6;
               int removeX = bothX + fieldW + 6;
               this.idField.setX(rowX);
               this.idField.setY(widgetY);
               this.idField.setWidth(idW);
               this.maleField.setX(maleX);
               this.maleField.setY(widgetY);
               this.femaleField.setX(femaleX);
               this.femaleField.setY(widgetY);
               this.bothField.setX(bothX);
               this.bothField.setY(widgetY);
               this.removeButton.setX(removeX);
               this.removeButton.setY(widgetY);

               for (ClickableWidget widget : this.widgets) {
                  widget.render(context, mouseX, mouseY, deltaTicks);
               }
            }

            public List<? extends Selectable> selectableChildren() {
               return this.widgets;
            }

            public List<? extends Element> children() {
               return this.widgets;
            }
         }
      }

      private static final class ResetGenderSpawnConfirmScreen extends Screen {
         private final Screen parent;
         private final Runnable onConfirm;

         private ResetGenderSpawnConfirmScreen(Screen parent, Runnable onConfirm) {
            super(Text.translatable("config.needsofnature.gender_spawn_reset_title"));
            this.parent = parent;
            this.onConfirm = onConfirm;
         }

         protected void init() {
            int centerX = this.width / 2;
            int buttonY = this.height / 2 + 20;
            ButtonWidget yesButton = ButtonWidget.builder(Text.translatable("gui.yes"), button -> {
               if (this.onConfirm != null) {
                  this.onConfirm.run();
               }

               MinecraftClient.getInstance().setScreen(this.parent);
            }).dimensions(centerX - 100, buttonY, 96, 20).build();
            this.addDrawableChild(yesButton);
            ButtonWidget noButton = ButtonWidget.builder(Text.translatable("gui.cancel"), button -> MinecraftClient.getInstance().setScreen(this.parent))
               .dimensions(centerX + 4, buttonY, 96, 20)
               .build();
            this.addDrawableChild(noButton);
         }

         public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
            super.render(context, mouseX, mouseY, deltaTicks);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 20, -1);
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.translatable("config.needsofnature.gender_spawn_reset_body"), this.width / 2, this.height / 2 - 4, -3355444
            );
         }
      }
   }

   static class LiquidGainConfigScreen extends Screen {
      private static final String MIXED_COLOR_ROW_ID = "needsofnature:mixed";
      private final Screen parent;
      private final NonConfig config;
      private final NonConfig defaults = new NonConfig();
      private boolean serverConfigEditable;
      private Map<String, Integer> appliedMap;
      private Map<String, String> appliedColorMap;
      private NonModMenuDebugScreens.LiquidGainConfigScreen.GainList gainList;
      private final List<NonModMenuDebugScreens.LiquidGainConfigScreen.GainList.GainEntry> rows = new ArrayList<>();
      private ButtonWidget applyButton;
      private ButtonWidget doneButton;

      protected LiquidGainConfigScreen(Screen parent, NonConfig config) {
         super(Text.translatable("config.needsofnature.liquid_gain_title"));
         this.parent = parent;
         this.config = config;
         this.serverConfigEditable = NonModMenuScreens.canEditServerGameplaySettings();
         this.appliedMap = new LinkedHashMap<>(NonLiquidGainOverrides.getGainByEntity());
         this.appliedMap.putAll(config.getLiquidGainByEntity());
         this.appliedColorMap = new LinkedHashMap<>(NonLiquidGainOverrides.getColorByEntity());
         this.appliedColorMap.putAll(config.getLiquidColorByEntity());
         this.appliedMap.remove("needsofnature:mixed");
      }

      protected void init() {
         this.serverConfigEditable = NonModMenuScreens.canEditServerGameplaySettings();
         List<NonModMenuDebugScreens.LiquidGainConfigScreen.RowData> preserved = this.snapshotRows();
         if (preserved == null) {
            preserved = new ArrayList<>();
            preserved.add(
               new NonModMenuDebugScreens.LiquidGainConfigScreen.RowData(
                  "needsofnature:mixed", "0", this.appliedColorMap.getOrDefault("needsofnature:mixed", resolveDefaultMixedColorHex())
               )
            );
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            ids.addAll(this.appliedMap.keySet());
            ids.addAll(this.appliedColorMap.keySet());
            ids.remove("needsofnature:mixed");

            for (String id : ids) {
               int gain = this.appliedMap.getOrDefault(id, 0);
               String color = this.appliedColorMap.getOrDefault(id, "");
               preserved.add(new NonModMenuDebugScreens.LiquidGainConfigScreen.RowData(id, String.valueOf(gain), color));
            }
         }

         preserved = this.ensureMixedRowPresent(preserved);
         int listTop = 44;
         int bottomArea = 64;
         int listHeight = Math.max(0, this.height - listTop - bottomArea);
         this.gainList = new NonModMenuDebugScreens.LiquidGainConfigScreen.GainList(this.client, this.width, listHeight, listTop);
         this.addDrawableChild(this.gainList);
         this.rows.clear();

         for (NonModMenuDebugScreens.LiquidGainConfigScreen.RowData data : preserved) {
            this.addRow(data.id(), data.value(), data.colorHex());
         }

         if (this.rows.isEmpty()) {
            this.addRow("", "0", "");
         }

         int centerX = this.width / 2;
         ButtonWidget addButton = ButtonWidget.builder(Text.translatable("config.needsofnature.liquid_gain_add"), button -> {
            this.addRow("", "0", "");
            this.updateApplyButton();
         }).dimensions(centerX - 100, this.height - 52, 96, 20).build();
         addButton.active = this.serverConfigEditable;
         NonModMenuScreens.setTooltip(addButton, "config.needsofnature.tooltip.liquid_gain_add");
         this.addDrawableChild(addButton);
         ButtonWidget resetDefaultsButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.liquid_gain_reset"),
               button -> MinecraftClient.getInstance().setScreen(new NonModMenuDebugScreens.LiquidGainConfigScreen.ResetLiquidGainConfirmScreen(this, () -> {
                  this.applyDefaultsToRows();
                  this.updateApplyButton();
                  MinecraftClient.getInstance().setScreen(this);
               }))
            )
            .dimensions(centerX + 4, this.height - 52, 96, 20)
            .build();
         resetDefaultsButton.active = this.serverConfigEditable;
         NonModMenuScreens.setTooltip(resetDefaultsButton, "config.needsofnature.tooltip.liquid_gain_reset");
         this.addDrawableChild(resetDefaultsButton);
         this.applyButton = ButtonWidget.builder(Text.translatable("config.needsofnature.apply"), button -> {
            if (this.serverConfigEditable) {
               Map<String, Integer> nextGain = this.collectGainMapFromFields();
               Map<String, String> nextColor = this.collectColorMapFromFields();
               this.config.setLiquidGainByEntity(nextGain);
               this.config.setLiquidColorByEntity(nextColor);
               NonModMenuScreens.saveServerConfig(this.config);
               this.appliedMap = new LinkedHashMap<>(nextGain);
               this.appliedColorMap = new LinkedHashMap<>(nextColor);
               this.updateApplyButton();
            }
         }).dimensions(centerX - 100, this.height - 28, 96, 20).build();
         NonModMenuScreens.setTooltip(this.applyButton, "config.needsofnature.tooltip.apply");
         this.addDrawableChild(this.applyButton);
         this.doneButton = ButtonWidget.builder(Text.translatable("config.needsofnature.done"), button -> MinecraftClient.getInstance().setScreen(this.parent))
            .dimensions(centerX + 4, this.height - 28, 96, 20)
            .build();
         NonModMenuScreens.setTooltip(this.doneButton, "config.needsofnature.tooltip.done_unsaved");
         this.addDrawableChild(this.doneButton);
         this.updateApplyButton();
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         this.updateApplyButton();
         super.render(context, mouseX, mouseY, deltaTicks);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 16777215);
         this.drawLiquidGainHeader(context);
      }

      public void close() {
         MinecraftClient.getInstance().setScreen(this.parent);
      }

      private void drawLiquidGainHeader(DrawContext context) {
         if (this.gainList != null) {
            int rowX = this.gainList.getRowLeft();
            int rowWidth = this.gainList.getRowWidth();
            int removeW = 20;
            int valueW = 50;
            int colorW = 80;
            int idW = Math.max(120, rowWidth - removeW - valueW - colorW - 18);
            int valueX = rowX + idW + 6;
            int colorX = valueX + valueW + 6;
            int y = 34;
            int color = -4342339;
            context.drawTextWithShadow(this.textRenderer, Text.translatable("config.needsofnature.liquid_gain_column.entity"), rowX + 4, y, color);
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.translatable("config.needsofnature.liquid_gain_column.ml"), valueX + valueW / 2, y, color
            );
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.translatable("config.needsofnature.liquid_gain_column.color"), colorX + colorW / 2, y, color
            );
         }
      }

      private void addRow(String idText, String valueText, String colorHexText) {
         NonModMenuDebugScreens.LiquidGainConfigScreen.GainList.GainEntry[] holder = new NonModMenuDebugScreens.LiquidGainConfigScreen.GainList.GainEntry[1];
         NonModMenuDebugScreens.LiquidGainConfigScreen.GainList.GainEntry entry = new NonModMenuDebugScreens.LiquidGainConfigScreen.GainList.GainEntry(
            this.textRenderer, idText, valueText, colorHexText, () -> {
               NonModMenuDebugScreens.LiquidGainConfigScreen.GainList.GainEntry current = holder[0];
               if (current != null) {
                  this.rows.removeIf(row -> row == current);
                  this.gainList.removeEntryRow(current);
                  this.updateApplyButton();
               }
            }
         );
         holder[0] = entry;
         entry.setOnChange(this::updateApplyButton);
         entry.setEditable(this.serverConfigEditable);
         if (isMixedRowId(idText)) {
            entry.setMixedRowMode(this.serverConfigEditable, "needsofnature:mixed");
         }

         this.rows.add(entry);
         this.gainList.addEntryRow(entry);
      }

      private void updateApplyButton() {
         if (this.applyButton != null) {
            if (!this.serverConfigEditable) {
               this.applyButton.active = false;
               if (this.doneButton != null) {
                  this.doneButton.setMessage(Text.translatable("config.needsofnature.done"));
               }
            } else {
               Map<String, Integer> currentGain = this.collectGainMapFromFields();
               Map<String, String> currentColor = this.collectColorMapFromFields();
               boolean hasUnsavedChanges = !currentGain.equals(this.appliedMap) || !currentColor.equals(this.appliedColorMap);
               this.applyButton.active = hasUnsavedChanges;
               if (this.doneButton != null) {
                  this.doneButton.setMessage(Text.translatable(hasUnsavedChanges ? "config.needsofnature.done_unsaved" : "config.needsofnature.done"));
               }
            }
         }
      }

      private void applyDefaultsToRows() {
         Map<String, Integer> defaultsMap = new LinkedHashMap<>(this.defaults.getLiquidGainByEntity());
         defaultsMap.putAll(NonLiquidGainOverrides.getGainByEntity());
         defaultsMap.remove("needsofnature:mixed");
         Map<String, String> defaultColors = new LinkedHashMap<>(this.defaults.getLiquidColorByEntity());
         defaultColors.putAll(NonLiquidGainOverrides.getColorByEntity());
         defaultColors.remove("needsofnature:mixed");
         if (this.gainList != null) {
            for (NonModMenuDebugScreens.LiquidGainConfigScreen.GainList.GainEntry entry : new ArrayList<>(this.rows)) {
               this.gainList.removeEntryRow(entry);
            }
         }

         this.rows.clear();
         this.addRow("needsofnature:mixed", "0", resolveDefaultMixedColorHex());
         LinkedHashSet<String> ids = new LinkedHashSet<>();
         ids.addAll(defaultsMap.keySet());
         ids.addAll(defaultColors.keySet());

         for (String id : ids) {
            int gain = defaultsMap.getOrDefault(id, 0);
            String color = defaultColors.getOrDefault(id, "");
            this.addRow(id, String.valueOf(gain), color);
         }

         if (this.rows.isEmpty()) {
            this.addRow("", "0", "");
         }
      }

      private Map<String, Integer> collectGainMapFromFields() {
         Map<String, Integer> map = new LinkedHashMap<>();

         for (NonModMenuDebugScreens.LiquidGainConfigScreen.GainList.GainEntry row : this.rows) {
            if (row != null) {
               String id = row.getIdText();
               if (id != null) {
                  id = id.trim();
                  if (!id.isEmpty() && !isMixedRowId(id)) {
                     int value = this.parseInt(row.getValueText());
                     value = Math.max(0, Math.min(1000, value));
                     map.put(id, value);
                  }
               }
            }
         }

         return map;
      }

      private Map<String, String> collectColorMapFromFields() {
         Map<String, String> map = new LinkedHashMap<>();

         for (NonModMenuDebugScreens.LiquidGainConfigScreen.GainList.GainEntry row : this.rows) {
            if (row != null) {
               String id = row.getIdText();
               if (id != null) {
                  id = id.trim();
                  if (!id.isEmpty()) {
                     String normalized = NonLiquidColors.normalizeHexColor(row.getColorHexText());
                     if (normalized != null) {
                        map.put(id, normalized);
                     }
                  }
               }
            }
         }

         return map;
      }

      private List<NonModMenuDebugScreens.LiquidGainConfigScreen.RowData> snapshotRows() {
         if (this.rows.isEmpty()) {
            return null;
         }

         List<NonModMenuDebugScreens.LiquidGainConfigScreen.RowData> data = new ArrayList<>();

         for (NonModMenuDebugScreens.LiquidGainConfigScreen.GainList.GainEntry row : this.rows) {
            data.add(new NonModMenuDebugScreens.LiquidGainConfigScreen.RowData(row.getIdText(), row.getValueText(), row.getColorHexText()));
         }

         return data;
      }

      private List<NonModMenuDebugScreens.LiquidGainConfigScreen.RowData> ensureMixedRowPresent(
         List<NonModMenuDebugScreens.LiquidGainConfigScreen.RowData> source
      ) {
         List<NonModMenuDebugScreens.LiquidGainConfigScreen.RowData> out = new ArrayList<>();
         String mixedColor = resolveDefaultMixedColorHex();
         if (source != null) {
            for (NonModMenuDebugScreens.LiquidGainConfigScreen.RowData row : source) {
               if (row != null) {
                  if (isMixedRowId(row.id())) {
                     String candidate = NonLiquidColors.normalizeHexColor(row.colorHex());
                     if (candidate != null) {
                        mixedColor = candidate;
                     }
                  } else {
                     out.add(row);
                  }
               }
            }
         }

         out.add(0, new NonModMenuDebugScreens.LiquidGainConfigScreen.RowData("needsofnature:mixed", "0", mixedColor));
         return out;
      }

      private static boolean isMixedRowId(String id) {
         return id == null ? false : "needsofnature:mixed".equals(id.trim());
      }

      private static String resolveDefaultMixedColorHex() {
         String packHex = NonLiquidGainOverrides.getMixedColorHex();
         return packHex != null ? packHex : String.format(Locale.ROOT, "%06X", 15920063);
      }

      private int parseInt(String value) {
         if (value == null) {
            return 0;
         }

         try {
            return Integer.parseInt(value.trim());
         } catch (NumberFormatException e) {
            return 0;
         }
      }

      private static final class GainList extends ElementListWidget<NonModMenuDebugScreens.LiquidGainConfigScreen.GainList.GainEntry> {
         private GainList(MinecraftClient client, int width, int height, int top) {
            super(client, width, height, top, 24);
            this.centerListVertically = false;
         }

         private void addEntryRow(NonModMenuDebugScreens.LiquidGainConfigScreen.GainList.GainEntry entry) {
            super.addEntry(entry);
         }

         private void removeEntryRow(NonModMenuDebugScreens.LiquidGainConfigScreen.GainList.GainEntry entry) {
            super.removeEntry(entry);
         }

         public int getRowWidth() {
            return 360;
         }

         public int getRowLeft() {
            return (this.getWidth() - this.getRowWidth()) / 2;
         }

         private static final class GainEntry
            extends net.minecraft.client.gui.widget.ElementListWidget.Entry<NonModMenuDebugScreens.LiquidGainConfigScreen.GainList.GainEntry> {
            private final TextFieldWidget idField;
            private final TextFieldWidget valueField;
            private final TextFieldWidget colorHexField;
            private final ButtonWidget removeButton;
            private final List<ClickableWidget> widgets;
            private Runnable onChange;

            private GainEntry(TextRenderer textRenderer, String idText, String valueText, String colorHexText, Runnable onRemove) {
               this.idField = new TextFieldWidget(textRenderer, 0, 0, 160, 20, Text.literal(""));
               this.idField.setText(idText == null ? "" : idText);
               this.idField.setMaxLength(80);
               NonModMenuScreens.setTooltip(this.idField, "config.needsofnature.tooltip.liquid_gain_entry_entity");
               this.valueField = new TextFieldWidget(textRenderer, 0, 0, 50, 20, Text.literal(""));
               this.valueField.setText(valueText == null ? "0" : valueText);
               this.valueField.setMaxLength(6);
               NonModMenuScreens.setTooltip(this.valueField, "config.needsofnature.tooltip.liquid_gain_entry_ml");
               this.colorHexField = new TextFieldWidget(textRenderer, 0, 0, 80, 20, Text.literal(""));
               this.colorHexField.setText(colorHexText == null ? "" : colorHexText);
               this.colorHexField.setPlaceholder(Text.literal("#RRGGBB"));
               this.colorHexField.setMaxLength(7);
               NonModMenuScreens.setTooltip(this.colorHexField, "config.needsofnature.tooltip.liquid_gain_entry_color");
               this.removeButton = ButtonWidget.builder(Text.translatable("config.needsofnature.remove_icon"), button -> {
                  if (onRemove != null) {
                     onRemove.run();
                  }
               }).dimensions(0, 0, 20, 20).build();
               NonModMenuScreens.setTooltip(this.removeButton, "config.needsofnature.tooltip.remove");
               this.widgets = new ArrayList<>(4);
               this.widgets.add(this.idField);
               this.widgets.add(this.valueField);
               this.widgets.add(this.colorHexField);
               this.widgets.add(this.removeButton);
               this.idField.setChangedListener(ignored -> {
                  if (this.onChange != null) {
                     this.onChange.run();
                  }
               });
               this.valueField.setChangedListener(ignored -> {
                  if (this.onChange != null) {
                     this.onChange.run();
                  }
               });
               this.colorHexField.setChangedListener(ignored -> {
                  if (this.onChange != null) {
                     this.onChange.run();
                  }
               });
            }

            private void setOnChange(Runnable onChange) {
               this.onChange = onChange;
            }

            private void setEditable(boolean editable) {
               this.idField.setEditable(editable);
               this.valueField.setEditable(editable);
               this.colorHexField.setEditable(editable);
               this.removeButton.active = editable;
            }

            private void setMixedRowMode(boolean editable, String mixedRowId) {
               this.idField.setText(mixedRowId);
               this.idField.setEditable(false);
               this.valueField.setText("0");
               this.valueField.setEditable(false);
               this.colorHexField.setEditable(editable);
               this.removeButton.active = false;
            }

            private String getIdText() {
               return this.idField.getText();
            }

            private String getValueText() {
               return this.valueField.getText();
            }

            private String getColorHexText() {
               return this.colorHexField.getText();
            }

            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
               int rowX = this.getContentX();
               int rowY = this.getContentY();
               int rowWidth = this.getContentWidth();
               int widgetY = rowY + 2;
               int removeW = this.removeButton.getWidth();
               int valueW = this.valueField.getWidth();
               int colorW = this.colorHexField.getWidth();
               int idW = Math.max(120, rowWidth - removeW - valueW - colorW - 18);
               int valueX = rowX + idW + 6;
               int colorX = valueX + valueW + 6;
               int removeX = colorX + colorW + 6;
               this.idField.setX(rowX);
               this.idField.setY(widgetY);
               this.idField.setWidth(idW);
               this.valueField.setX(valueX);
               this.valueField.setY(widgetY);
               this.colorHexField.setX(colorX);
               this.colorHexField.setY(widgetY);
               this.removeButton.setX(removeX);
               this.removeButton.setY(widgetY);

               for (ClickableWidget widget : this.widgets) {
                  widget.render(context, mouseX, mouseY, deltaTicks);
               }
            }

            public List<? extends Selectable> selectableChildren() {
               return List.copyOf(this.widgets);
            }

            public void forEachChild(Consumer<ClickableWidget> consumer) {
               this.widgets.forEach(consumer);
            }

            public List<? extends Element> children() {
               return List.copyOf(this.widgets);
            }
         }
      }

      private static final class ResetLiquidGainConfirmScreen extends Screen {
         private final Screen parent;
         private final Runnable onConfirm;

         private ResetLiquidGainConfirmScreen(Screen parent, Runnable onConfirm) {
            super(Text.translatable("config.needsofnature.liquid_gain_reset_title"));
            this.parent = parent;
            this.onConfirm = onConfirm;
         }

         protected void init() {
            int centerX = this.width / 2;
            int buttonY = this.height / 2 + 20;
            ButtonWidget yesButton = ButtonWidget.builder(Text.translatable("gui.yes"), button -> {
               if (this.onConfirm != null) {
                  this.onConfirm.run();
               }

               MinecraftClient.getInstance().setScreen(this.parent);
            }).dimensions(centerX - 100, buttonY, 96, 20).build();
            this.addDrawableChild(yesButton);
            ButtonWidget noButton = ButtonWidget.builder(Text.translatable("gui.cancel"), button -> MinecraftClient.getInstance().setScreen(this.parent))
               .dimensions(centerX + 4, buttonY, 96, 20)
               .build();
            this.addDrawableChild(noButton);
         }

         public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
            super.render(context, mouseX, mouseY, deltaTicks);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 20, -1);
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.translatable("config.needsofnature.liquid_gain_reset_body"), this.width / 2, this.height / 2 - 4, -4144960
            );
         }
      }

      private record RowData(String id, String value, String colorHex) {
      }
   }

   static class LoadOrderScreen extends Screen {
      private final Screen parent;
      private final NonConfig config;
      private boolean editable;
      private final List<String> externalPackOrder = new ArrayList<>();
      private List<String> appliedExternalOrder = List.of();
      private List<String> defaultExternalOrder = List.of();
      private NonModMenuDebugScreens.LoadOrderScreen.PackOrderList packOrderList;
      private ButtonWidget resetButton;

      protected LoadOrderScreen(Screen parent, NonConfig config) {
         super(Text.translatable("config.needsofnature.loaded_animations.load_order_title"));
         this.parent = parent;
         this.config = config == null ? new NonConfig() : config;
      }

      protected void init() {
         this.editable = NonModMenuScreens.canEditLocalPackLoadOrder();
         this.reloadExternalOrder();
         int centerX = this.width / 2;
         int listTop = 40;
         int bottomArea = 64;
         int listHeight = Math.max(0, this.height - listTop - bottomArea);
         this.packOrderList = new NonModMenuDebugScreens.LoadOrderScreen.PackOrderList(
            this.client, this.width, listHeight, listTop, this.textRenderer, this::moveUp, this::moveDown, this.editable
         );
         this.addDrawableChild(this.packOrderList);
         this.rebuildRows();
         this.resetButton = ButtonWidget.builder(Text.translatable("config.needsofnature.liquid_gain_reset"), button -> {
            this.externalPackOrder.clear();
            this.externalPackOrder.addAll(this.defaultExternalOrder);
            this.rebuildRows();
            this.updateButtons();
         }).dimensions(centerX - 100, this.height - 50, 98, 20).build();
         NonModMenuScreens.setTooltip(this.resetButton, "config.needsofnature.tooltip.loaded_animations.load_order.reset");
         this.addDrawableChild(this.resetButton);
         ButtonWidget doneButton = ButtonWidget.builder(Text.translatable("config.needsofnature.done"), button -> {
            boolean changed = !this.externalPackOrder.equals(this.appliedExternalOrder);
            if (this.editable) {
               this.config.setExternalNoNPackLoadOrder(this.externalPackOrder);
               this.config.save();
               this.appliedExternalOrder = List.copyOf(this.externalPackOrder);
               if (changed) {
                  this.triggerInWorldReload();
               }
            }

            MinecraftClient.getInstance().setScreen(this.parent);
         }).dimensions(centerX + 2, this.height - 50, 98, 20).build();
         NonModMenuScreens.setTooltip(doneButton, "config.needsofnature.tooltip.done_save");
         this.addDrawableChild(doneButton);
         this.updateButtons();
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         super.render(context, mouseX, mouseY, deltaTicks);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, -1);
         context.drawTextWithShadow(this.textRenderer, Text.translatable("config.needsofnature.loaded_animations.load_order.summary"), 8, 24, -5197648);
         if (!this.editable) {
            context.drawTextWithShadow(this.textRenderer, Text.translatable("config.needsofnature.loaded_animations.load_order.read_only"), 8, 33, -14739);
         }
      }

      public void close() {
         MinecraftClient.getInstance().setScreen(this.parent);
      }

      private void reloadExternalOrder() {
         List<String> discovered = NonExternalPackProvider.listExternalPackFileNames(NonExternalPackProvider.resolveDefaultPacksDir());
         this.defaultExternalOrder = List.copyOf(discovered);
         this.externalPackOrder.clear();
         this.externalPackOrder.addAll(NonExternalPackProvider.normalizeConfiguredOrder(this.config.getExternalNoNPackLoadOrder(), discovered));
         this.appliedExternalOrder = List.copyOf(this.externalPackOrder);
      }

      private void triggerInWorldReload() {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.world != null) {
            IntegratedServer server = client.getServer();
            if (server != null) {
               server.execute(() -> {
                  try {
                     server.getCommandManager().getDispatcher().execute("reload", server.getCommandSource());
                  } catch (Exception e) {
                     server.reloadResources(server.getDataPackManager().getEnabledIds());
                  }
               });
            }

            client.reloadResources();
         }
      }

      private void moveUp(String key) {
         this.moveBy(key, -1);
      }

      private void moveDown(String key) {
         this.moveBy(key, 1);
      }

      private void moveBy(String key, int delta) {
         if (this.editable && key != null && !key.isBlank()) {
            int index = this.externalPackOrder.indexOf(key);
            if (index >= 0) {
               int target = index + delta;
               if (target >= 0 && target < this.externalPackOrder.size()) {
                  String tmp = this.externalPackOrder.get(index);
                  this.externalPackOrder.set(index, this.externalPackOrder.get(target));
                  this.externalPackOrder.set(target, tmp);
                  this.rebuildRows();
                  this.updateButtons();
               }
            }
         }
      }

      private void rebuildRows() {
         if (this.packOrderList != null) {
            List<NonModMenuDebugScreens.LoadOrderScreen.PackOrderRow> rows = new ArrayList<>();

            for (int i = 0; i < this.externalPackOrder.size(); i++) {
               String fileName = this.externalPackOrder.get(i);
               rows.add(NonModMenuDebugScreens.LoadOrderScreen.PackOrderRow.external(fileName, i, this.externalPackOrder.size()));
            }

            rows.add(
               NonModMenuDebugScreens.LoadOrderScreen.PackOrderRow.locked(
                  "builtin:needsofnature", Text.translatable("config.needsofnature.loaded_animations.load_order.base_non")
               )
            );
            this.packOrderList.setRows(rows);
         }
      }

      private void updateButtons() {
         if (this.resetButton != null) {
            this.resetButton.active = this.editable && !this.externalPackOrder.equals(this.defaultExternalOrder);
         }
      }

      private static final class PackOrderList extends ElementListWidget<NonModMenuDebugScreens.LoadOrderScreen.PackOrderList.RowEntry> {
         private static final int ROW_HEIGHT = 30;
         private static final int MIN_ROW_WIDTH = 260;
         private static final int MAX_ROW_WIDTH = 420;
         private final TextRenderer textRenderer;
         private final Consumer<String> onMoveUp;
         private final Consumer<String> onMoveDown;
         private final boolean editable;
         private int preferredRowWidth = 260;

         private PackOrderList(
            MinecraftClient client,
            int width,
            int height,
            int top,
            TextRenderer textRenderer,
            Consumer<String> onMoveUp,
            Consumer<String> onMoveDown,
            boolean editable
         ) {
            super(client, width, height, top, 30);
            this.textRenderer = textRenderer;
            this.onMoveUp = onMoveUp;
            this.onMoveDown = onMoveDown;
            this.editable = editable;
            this.centerListVertically = false;
         }

         private void setRows(List<NonModMenuDebugScreens.LoadOrderScreen.PackOrderRow> rows) {
            this.clearEntries();
            int widestContent = 260;

            for (NonModMenuDebugScreens.LoadOrderScreen.PackOrderRow row : rows) {
               int requiredWidth = this.textRenderer.getWidth(row.label()) + 28;
               if (row.movable()) {
                  requiredWidth += 42;
               }

               widestContent = Math.max(widestContent, requiredWidth);
               this.addEntry(
                  new NonModMenuDebugScreens.LoadOrderScreen.PackOrderList.RowEntry(row, this.textRenderer, this.onMoveUp, this.onMoveDown, this.editable), 30
               );
            }

            this.preferredRowWidth = Math.min(420, widestContent);
         }

         public int getRowWidth() {
            return Math.min(this.preferredRowWidth, Math.max(120, this.getWidth() - 32));
         }

         public int getRowLeft() {
            return (this.getWidth() - this.getRowWidth()) / 2;
         }

         protected int getScrollbarX() {
            return Math.min(this.getWidth() - 8, this.getRowLeft() + this.getRowWidth() + 6);
         }

         private static final class OrderArrowButton extends ClickableWidget {
            private static final int SIZE = 15;
            private final boolean pointsUp;
            private final Runnable onPress;

            private OrderArrowButton(boolean pointsUp, Runnable onPress) {
               super(
                  0,
                  0,
                  15,
                  15,
                  Text.translatable(
                     pointsUp ? "config.needsofnature.loaded_animations.load_order.move_up" : "config.needsofnature.loaded_animations.load_order.move_down"
                  )
               );
               this.pointsUp = pointsUp;
               this.onPress = onPress;
            }

            protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
               boolean highlighted = this.active && this.isSelected();
               int background = !this.active ? -2009055168 : (highlighted ? -12035736 : -583779260);
               int border = !this.active ? -11513776 : (highlighted ? -7227697 : -10062464);
               int arrow = !this.active ? -8947849 : (highlighted ? -1 : -2563609);
               int left = this.getX();
               int top = this.getY();
               int right = left + this.getWidth();
               int bottom = top + this.getHeight();
               context.fill(left, top, right, bottom, background);
               context.fill(left, top, right, top + 1, border);
               context.fill(left, bottom - 1, right, bottom, border);
               context.fill(left, top, left + 1, bottom, border);
               context.fill(right - 1, top, right, bottom, border);
               int centerX = left + this.getWidth() / 2;
               int centerY = top + this.getHeight() / 2;
               if (this.pointsUp) {
                  context.fill(centerX, centerY - 3, centerX + 1, centerY - 2, arrow);
                  context.fill(centerX - 1, centerY - 2, centerX + 2, centerY - 1, arrow);
                  context.fill(centerX - 2, centerY - 1, centerX + 3, centerY, arrow);
               } else {
                  context.fill(centerX - 2, centerY, centerX + 3, centerY + 1, arrow);
                  context.fill(centerX - 1, centerY + 1, centerX + 2, centerY + 2, arrow);
                  context.fill(centerX, centerY + 2, centerX + 1, centerY + 3, arrow);
               }
            }

            public void onClick(Click click, boolean doubled) {
               super.onClick(click, doubled);
               if (this.active && this.onPress != null) {
                  this.onPress.run();
               }
            }

            protected void appendClickableNarrations(NarrationMessageBuilder builder) {
               this.appendDefaultNarrations(builder);
            }
         }

         private static final class RowEntry
            extends net.minecraft.client.gui.widget.ElementListWidget.Entry<NonModMenuDebugScreens.LoadOrderScreen.PackOrderList.RowEntry> {
            private final NonModMenuDebugScreens.LoadOrderScreen.PackOrderRow row;
            private final TextRenderer textRenderer;
            private final Consumer<String> onMoveUp;
            private final Consumer<String> onMoveDown;
            private final boolean editable;
            private final NonModMenuDebugScreens.LoadOrderScreen.PackOrderList.OrderArrowButton upButton;
            private final NonModMenuDebugScreens.LoadOrderScreen.PackOrderList.OrderArrowButton downButton;
            private final List<ClickableWidget> widgets;

            private RowEntry(
               NonModMenuDebugScreens.LoadOrderScreen.PackOrderRow row,
               TextRenderer textRenderer,
               Consumer<String> onMoveUp,
               Consumer<String> onMoveDown,
               boolean editable
            ) {
               this.row = row;
               this.textRenderer = textRenderer;
               this.onMoveUp = onMoveUp;
               this.onMoveDown = onMoveDown;
               this.editable = editable;
               if (row.movable()) {
                  this.upButton = new NonModMenuDebugScreens.LoadOrderScreen.PackOrderList.OrderArrowButton(true, () -> {
                     if (this.onMoveUp != null) {
                        this.onMoveUp.accept(this.row.key());
                     }
                  });
                  this.downButton = new NonModMenuDebugScreens.LoadOrderScreen.PackOrderList.OrderArrowButton(false, () -> {
                     if (this.onMoveDown != null) {
                        this.onMoveDown.accept(this.row.key());
                     }
                  });
                  NonModMenuScreens.setTooltip(this.upButton, "config.needsofnature.tooltip.loaded_animations.load_order.move_up");
                  NonModMenuScreens.setTooltip(this.downButton, "config.needsofnature.tooltip.loaded_animations.load_order.move_down");
                  this.widgets = List.of(this.upButton, this.downButton);
               } else {
                  this.upButton = null;
                  this.downButton = null;
                  this.widgets = List.of();
               }
            }

            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
               int cardLeft = this.getContentX() + 2;
               int cardTop = this.getContentY() + 2;
               int cardRight = this.getContentX() + this.getContentWidth() - 2;
               int cardBottom = cardTop + 24;
               int background = this.row.movable() ? (hovered ? -801490374 : -1339545560) : (hovered ? -802343867 : -1339939021);
               int border = this.row.movable() ? -11513776 : -12365718;
               context.fill(cardLeft, cardTop, cardRight, cardBottom, background);
               context.fill(cardLeft, cardTop, cardRight, cardTop + 1, border);
               context.fill(cardLeft, cardBottom - 1, cardRight, cardBottom, border);
               context.fill(cardLeft, cardTop, cardLeft + 1, cardBottom, border);
               context.fill(cardRight - 1, cardTop, cardRight, cardBottom, border);
               int x = cardLeft + 8;
               int y = cardTop + 8;
               int right = cardRight - 6;
               int textRight = this.row.movable() ? right - 40 : right;
               int textWidth = Math.max(20, textRight - x);
               int color = this.row.movable() ? -2565928 : -7362305;
               context.drawTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(this.row.label().getString(), textWidth), x, y, color);
               if (this.upButton != null && this.downButton != null) {
                  this.upButton.active = this.editable && this.row.canMoveUp();
                  this.downButton.active = this.editable && this.row.canMoveDown();
                  this.upButton.setX(right - 32);
                  this.upButton.setY(cardTop + 4);
                  this.downButton.setX(right - 15);
                  this.downButton.setY(cardTop + 4);
                  this.upButton.render(context, mouseX, mouseY, deltaTicks);
                  this.downButton.render(context, mouseX, mouseY, deltaTicks);
               }
            }

            public List<? extends Selectable> selectableChildren() {
               return List.copyOf(this.widgets);
            }

            public void forEachChild(Consumer<ClickableWidget> consumer) {
               this.widgets.forEach(consumer);
            }

            public List<? extends Element> children() {
               return List.copyOf(this.widgets);
            }
         }
      }

      private record PackOrderRow(String key, Text label, boolean movable, boolean canMoveUp, boolean canMoveDown) {
         private static NonModMenuDebugScreens.LoadOrderScreen.PackOrderRow external(String fileName, int index, int total) {
            String safeName = fileName != null && !fileName.isBlank() ? fileName : "<unknown>";
            return new NonModMenuDebugScreens.LoadOrderScreen.PackOrderRow(
               safeName, Text.literal(index + 1 + ". " + safeName), true, index > 0, index < total - 1
            );
         }

         private static NonModMenuDebugScreens.LoadOrderScreen.PackOrderRow locked(String key, Text label) {
            String safeKey = key != null && !key.isBlank() ? key : "<locked>";
            Text safeLabel = (Text)(label == null ? Text.literal(safeKey) : label);
            return new NonModMenuDebugScreens.LoadOrderScreen.PackOrderRow(safeKey, safeLabel, false, false, false);
         }
      }
   }

   static class LoadedAnimationsScreen extends Screen {
      private final Screen parent;
      private final NonConfig config;
      private final Set<String> expandedRowKeys = new HashSet<>();
      private final Set<String> expandedPackIds = new HashSet<>();
      private final Map<String, String> sourceHintByDefinitionId = new LinkedHashMap<>();
      private TextFieldWidget filterField;
      private ButtonWidget filterAllButton;
      private ButtonWidget filterMissingButton;
      private ButtonWidget filterPartialButton;
      private ButtonWidget filterOkButton;
      private ButtonWidget sortModeButton;
      private ButtonWidget sortOrderButton;
      private NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationsList loadedAnimationsList;
      private List<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow> allRows = List.of();
      private final Map<String, Identifier> packIconByResourcePackId = new LinkedHashMap<>();
      private final Set<String> disabledAnimationPacks = new LinkedHashSet<>();
      private final Set<String> disabledAnimations = new LinkedHashSet<>();
      private int filteredCount = 0;
      private boolean editable = true;
      private NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter statusFilter = NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter.ALL;
      private NonModMenuDebugScreens.LoadedAnimationsScreen.SortMode sortMode = NonModMenuDebugScreens.LoadedAnimationsScreen.SortMode.ID;
      private boolean sortAscending = true;
      private long observedCatalogRevision = Long.MIN_VALUE;
      private Text copyFeedbackText = Text.literal("");
      private long copyFeedbackUntilMs = 0L;
      private static final long COPY_FEEDBACK_DURATION_MS = 1600L;
      private static final int MISSING_ANIM_PREVIEW_COUNT = 3;
      private static final int MISSING_MODEL_PREVIEW_COUNT = 3;
      private static final int MAX_CONTENT_WIDTH = 500;

      protected LoadedAnimationsScreen(Screen parent, NonConfig config) {
         super(Text.translatable("config.needsofnature.loaded_animations_title"));
         this.parent = parent;
         this.config = config;
      }

      protected void init() {
         String previousFilter = this.filterField == null ? "" : this.filterField.getText();
         NonServerSettingsClient.requestRefresh();
         this.editable = NonModMenuScreens.canEditServerGameplaySettings();
         this.reloadDisabledAnimationState();
         int centerX = this.width / 2;
         int filterY = 34;
         int contentWidth = this.contentWidth();
         int contentLeft = (this.width - contentWidth) / 2;
         int contentRight = contentLeft + contentWidth;
         this.filterField = new TextFieldWidget(
            this.textRenderer, contentLeft, filterY, contentWidth, 20, Text.translatable("config.needsofnature.loaded_animations_search")
         );
         this.filterField.setMaxLength(128);
         this.filterField.setText(previousFilter);
         this.filterField.setChangedListener(this::applyFilter);
         this.addDrawableChild(this.filterField);
         int controlsY = filterY + 24;
         int buttonH = 20;
         int smallButtonW = 62;
         int filterGap = 4;
         int startX = contentLeft;
         this.filterAllButton = ButtonWidget.builder(Text.literal("全部"), button -> {
            this.statusFilter = NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter.ALL;
            this.updateControlButtons();
            this.applyFilter(this.filterField.getText());
         }).dimensions(startX, controlsY, smallButtonW, buttonH).build();
         this.addDrawableChild(this.filterAllButton);
         this.filterMissingButton = ButtonWidget.builder(Text.literal("缺失"), button -> {
            this.statusFilter = NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter.MISSING;
            this.updateControlButtons();
            this.applyFilter(this.filterField.getText());
         }).dimensions(startX + smallButtonW + filterGap, controlsY, smallButtonW, buttonH).build();
         this.addDrawableChild(this.filterMissingButton);
         this.filterPartialButton = ButtonWidget.builder(Text.literal("部分"), button -> {
            this.statusFilter = NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter.PARTIAL;
            this.updateControlButtons();
            this.applyFilter(this.filterField.getText());
         }).dimensions(startX + (smallButtonW + filterGap) * 2, controlsY, smallButtonW, buttonH).build();
         this.addDrawableChild(this.filterPartialButton);
         this.filterOkButton = ButtonWidget.builder(Text.literal("确定"), button -> {
            this.statusFilter = NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter.OK;
            this.updateControlButtons();
            this.applyFilter(this.filterField.getText());
         }).dimensions(startX + (smallButtonW + filterGap) * 3, controlsY, smallButtonW, buttonH).build();
         this.addDrawableChild(this.filterOkButton);
         int orderButtonW = 56;
         int sortButtonW = 116;
         int orderX = contentRight - orderButtonW;
         int sortX = orderX - 6 - sortButtonW;
         this.sortModeButton = ButtonWidget.builder(this.sortModeLabel(), button -> {
            this.sortMode = switch (this.sortMode) {
               case ID -> NonModMenuDebugScreens.LoadedAnimationsScreen.SortMode.STATUS;
               case STATUS -> NonModMenuDebugScreens.LoadedAnimationsScreen.SortMode.MISSING;
               case MISSING -> NonModMenuDebugScreens.LoadedAnimationsScreen.SortMode.ENTITIES;
               case ENTITIES -> NonModMenuDebugScreens.LoadedAnimationsScreen.SortMode.ID;
            };
            this.updateControlButtons();
            this.applyFilter(this.filterField.getText(), false);
         }).dimensions(sortX, controlsY, sortButtonW, buttonH).build();
         this.addDrawableChild(this.sortModeButton);
         this.sortOrderButton = ButtonWidget.builder(this.sortOrderLabel(), button -> {
            this.sortAscending = !this.sortAscending;
            this.updateControlButtons();
            this.applyFilter(this.filterField.getText(), false);
         }).dimensions(orderX, controlsY, orderButtonW, buttonH).build();
         this.addDrawableChild(this.sortOrderButton);
         int listTop = controlsY + buttonH + 6;
         int bottomArea = 64;
         int listHeight = Math.max(0, this.height - listTop - bottomArea);
         this.loadedAnimationsList = new NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationsList(
            this.client,
            this.width,
            listHeight,
            listTop,
            this.textRenderer,
            this::togglePackExpanded,
            this::toggleRowExpanded,
            this::copyRowMissingDetails,
            this::togglePackEnabled,
            this::toggleAnimationEnabled,
            () -> this.editable
         );
         this.addDrawableChild(this.loadedAnimationsList);
         this.rebuildSourceHints();
         this.rebuildRows();
         this.updateControlButtons();
         this.applyFilter(previousFilter);
         ButtonWidget doneButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.done"), button -> MinecraftClient.getInstance().setScreen(this.parent)
            )
            .dimensions(centerX - 100, this.height - 28, 200, 20)
            .build();
         NonModMenuScreens.setTooltip(doneButton, "config.needsofnature.tooltip.done");
         this.addDrawableChild(doneButton);
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         super.render(context, mouseX, mouseY, deltaTicks);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, -1);
         int contentWidth = this.contentWidth();
         int contentLeft = (this.width - contentWidth) / 2;
         context.drawTextWithShadow(
            this.textRenderer,
            Text.translatable("config.needsofnature.loaded_animations_summary", new Object[]{this.allRows.size(), this.filteredCount}),
            contentLeft,
            22,
            -5197648
         );
         if (this.copyFeedbackText != null && !this.copyFeedbackText.getString().isBlank() && System.currentTimeMillis() < this.copyFeedbackUntilMs) {
            int feedbackWidth = this.textRenderer.getWidth(this.copyFeedbackText);
            int feedbackX = Math.max(contentLeft, contentLeft + contentWidth - feedbackWidth);
            context.drawTextWithShadow(this.textRenderer, this.copyFeedbackText, feedbackX, 22, -6560902);
         }
      }

      private int contentWidth() {
         return Math.min(500, Math.max(120, this.width - 16));
      }

      public void tick() {
         super.tick();
         long revision = NonServerSettingsClient.catalogRevision();
         if (revision != this.observedCatalogRevision) {
            this.observedCatalogRevision = revision;
            this.reloadDisabledAnimationState();
            this.rebuildRows();
            this.applyFilter(this.filterField == null ? "" : this.filterField.getText(), false);
         }
      }

      public void close() {
         MinecraftClient.getInstance().setScreen(this.parent);
      }

      private void updateControlButtons() {
         if (this.filterAllButton != null) {
            this.filterAllButton.setMessage(this.statusFilterLabel(NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter.ALL));
            this.filterAllButton.active = this.statusFilter != NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter.ALL;
         }

         if (this.filterMissingButton != null) {
            this.filterMissingButton.setMessage(this.statusFilterLabel(NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter.MISSING));
            this.filterMissingButton.active = this.statusFilter != NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter.MISSING;
         }

         if (this.filterPartialButton != null) {
            this.filterPartialButton.setMessage(this.statusFilterLabel(NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter.PARTIAL));
            this.filterPartialButton.active = this.statusFilter != NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter.PARTIAL;
         }

         if (this.filterOkButton != null) {
            this.filterOkButton.setMessage(this.statusFilterLabel(NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter.OK));
            this.filterOkButton.active = this.statusFilter != NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter.OK;
         }

         if (this.sortModeButton != null) {
            this.sortModeButton.setMessage(this.sortModeLabel());
         }

         if (this.sortOrderButton != null) {
            this.sortOrderButton.setMessage(this.sortOrderLabel());
         }
      }

      private Text statusFilterLabel(NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter mode) {
         boolean selected = this.statusFilter == mode;

         String base = switch (mode) {
            case ALL -> "All";
            case MISSING -> "Missing";
            case PARTIAL -> "Partial";
            case OK -> "OK";
         };
         return Text.literal(selected ? "[" + base + "]" : base);
      }

      private Text sortModeLabel() {
         String value = switch (this.sortMode) {
            case ID -> "ID";
            case STATUS -> "Status";
            case MISSING -> "Missing";
            case ENTITIES -> "Entities";
         };
         return Text.literal("Sort: " + value);
      }

      private Text sortOrderLabel() {
         return Text.literal(this.sortAscending ? "Asc" : "Desc");
      }

      private void rebuildSourceHints() {
         this.sourceHintByDefinitionId.clear();
         ResourceManager manager = this.resolveResourceManagerForAnimdefs();
         if (manager != null) {
            try {
               Map<Identifier, List<Resource>> resources = manager.findAllResources("afw_animdefs", id -> id.getPath().endsWith(".json"));

               for (Entry<Identifier, List<Resource>> entry : resources.entrySet()) {
                  Identifier definitionId = definitionIdFromFile(entry.getKey());
                  String sourceHint = summarizeSources(entry.getValue());
                  this.sourceHintByDefinitionId.put(definitionId.toString(), sourceHint);
               }
            } catch (RuntimeException e) {
               NeedsOfNature.LOGGER.debug("[NoN] Failed to resolve animation definition source hints.", e);
            }
         }
      }

      private ResourceManager resolveResourceManagerForAnimdefs() {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client == null) {
            return null;
         }

         try {
            if (client.getServer() != null) {
               return client.getServer().getResourceManager();
            }
         } catch (RuntimeException e) {
            NeedsOfNature.LOGGER.debug("[NoN] Failed to resolve server resource manager for loaded animations.", e);
         }

         try {
            return client.getResourceManager();
         } catch (RuntimeException e) {
            NeedsOfNature.LOGGER.debug("[NoN] Failed to resolve client resource manager for loaded animations.", e);
            return null;
         }
      }

      private static Identifier definitionIdFromFile(Identifier fileId) {
         if (fileId == null) {
            return AnimationEngine.packId("unknown");
         }

         String path = fileId.getPath();
         String prefix = "afw_animdefs/";
         if (path.startsWith(prefix)) {
            path = path.substring(prefix.length());
         }

         if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
         }

         return Identifier.of(fileId.getNamespace(), path);
      }

      private static String summarizeSources(List<Resource> resources) {
         if (resources != null && !resources.isEmpty()) {
            LinkedHashSet<String> sources = new LinkedHashSet<>();

            for (Resource resource : resources) {
               if (resource != null) {
                  sources.add(normalizeSourceId(resource.getPackId()));
               }
            }

            if (sources.isEmpty()) {
               return "unknown";
            }

            List<String> list = new ArrayList<>(sources);
            int take = Math.min(2, list.size());
            String summary = String.join(", ", list.subList(0, take));
            int more = list.size() - take;
            if (more > 0) {
               summary = summary + ", +" + more + " more";
            }

            return summary;
         } else {
            return "unknown";
         }
      }

      private static String normalizeSourceId(String rawPackId) {
         if (rawPackId == null || rawPackId.isBlank()) {
            return "unknown";
         }

         if ("vanilla".equals(rawPackId)) {
            return "builtin:vanilla";
         }

         if (!rawPackId.startsWith("needsofnature/")) {
            return rawPackId;
         }

         String value = rawPackId.substring("needsofnature/".length());
         int slash = value.lastIndexOf(47);
         String tail = slash >= 0 && slash < value.length() - 1 ? value.substring(slash + 1) : value;
         int underscore = tail.indexOf(95);
         if (underscore > 0) {
            boolean numericPrefix = true;

            for (int i = 0; i < underscore; i++) {
               char c = tail.charAt(i);
               if (c < '0' || c > '9') {
                  numericPrefix = false;
                  break;
               }
            }

            if (numericPrefix && underscore + 1 < tail.length()) {
               tail = tail.substring(underscore + 1);
            }
         }

         return "nonpack:" + tail;
      }

      private void reloadDisabledAnimationState() {
         this.disabledAnimationPacks.clear();
         this.disabledAnimations.clear();
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.world != null && NonServerSettingsClient.hasAnimationCatalog()) {
            for (AnimationCatalogData.Entry entry : NonServerSettingsClient.animationCatalogSnapshot()) {
               if (entry != null) {
                  if (entry.packDisabled() && entry.pack() != null) {
                     this.disabledAnimationPacks.add(entry.pack().id());
                  }

                  if (entry.animationDisabled()) {
                     this.disabledAnimations.add(entry.id());
                  }
               }
            }
         } else if (this.config != null) {
            this.disabledAnimationPacks.addAll(this.config.getDisabledAnimationPacks());
            this.disabledAnimations.addAll(this.config.getDisabledAnimations());
         }
      }

      private void togglePackEnabled(String packId) {
         if (this.editable && packId != null && !packId.isBlank()) {
            if (this.disabledAnimationPacks.contains(packId)) {
               this.disabledAnimationPacks.remove(packId);
            } else {
               this.disabledAnimationPacks.add(packId);
            }

            this.saveDisabledAnimationState();
         }
      }

      private void toggleAnimationEnabled(String animationId) {
         if (this.editable && animationId != null && !animationId.isBlank()) {
            if (this.disabledAnimations.contains(animationId)) {
               this.disabledAnimations.remove(animationId);
            } else {
               this.disabledAnimations.add(animationId);
            }

            this.saveDisabledAnimationState();
         }
      }

      private void saveDisabledAnimationState() {
         if (this.config != null) {
            this.config.setDisabledAnimationPacks(new ArrayList<>(this.disabledAnimationPacks));
            this.config.setDisabledAnimations(new ArrayList<>(this.disabledAnimations));
            NonModMenuScreens.saveServerConfig(this.config);
            this.rebuildRows();
            this.applyFilter(this.filterField == null ? "" : this.filterField.getText(), false);
         }
      }

      @Nullable
      private Identifier resolvePackIcon(String resourcePackId) {
         if (resourcePackId == null || resourcePackId.isBlank()) {
            return null;
         }

         if (this.packIconByResourcePackId.containsKey(resourcePackId)) {
            return this.packIconByResourcePackId.get(resourcePackId);
         }

         Identifier textureId = this.loadPackIcon(resourcePackId);
         this.packIconByResourcePackId.put(resourcePackId, textureId);
         return textureId;
      }

      @Nullable
      private Identifier loadPackIcon(String resourcePackId) {
         MinecraftClient client = MinecraftClient.getInstance();
         ResourceManager manager = this.resolveResourceManagerForAnimdefs();
         if (client != null && manager != null) {
            try (Stream<ResourcePack> stream = manager.streamResourcePacks()) {
               ResourcePack pack = stream.filter(candidate -> resourcePackId.equals(candidate.getId())).findFirst().orElse(null);
               if (pack == null) {
                  return null;
               }

               try (InputStream input = openRootResource(pack, "pack.png")) {
                  if (input != null) {
                     NativeImage image = NativeImage.read(input);
                     Identifier id = Identifier.of("needsofnature", "dynamic/animation_pack_icons/" + sanitizeTexturePath(resourcePackId));
                     client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(id::toString, image));
                     return id;
                  } else {
                     return null;
                  }
               }
            } catch (IOException | RuntimeException e) {
               NeedsOfNature.LOGGER.debug("[NoN] Failed to load animation pack icon for {}", resourcePackId, e);
               return null;
            }
         } else {
            return null;
         }
      }

      @Nullable
      private static InputStream openRootResource(ResourcePack pack, String fileName) {
         if (pack != null && fileName != null) {
            InputSupplier<InputStream> supplier = pack.openRoot(new String[]{fileName});
            if (supplier == null) {
               return null;
            }

            try {
               return (InputStream)supplier.get();
            } catch (IOException | RuntimeException e) {
               return null;
            }
         } else {
            return null;
         }
      }

      private static String sanitizeTexturePath(String raw) {
         if (raw != null && !raw.isBlank()) {
            String lower = raw.toLowerCase(Locale.ROOT);
            StringBuilder sb = new StringBuilder(lower.length());

            for (int i = 0; i < lower.length(); i++) {
               char c = lower.charAt(i);
               if ((c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '_' && c != '-' && c != '/') {
                  sb.append('_');
               } else {
                  sb.append(c);
               }
            }

            return sb.isEmpty() ? "unknown" : sb.toString();
         } else {
            return "unknown";
         }
      }

      private void copyRowMissingDetails(NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow row) {
         if (row != null && row.hasMissingDetails()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
               String text = String.join("\n", row.missingLines());
               client.keyboard.setClipboard(text);
               this.showCopyFeedback(Text.translatable("config.needsofnature.loaded_animations.copied_missing"));
            }
         }
      }

      private void showCopyFeedback(Text message) {
         this.copyFeedbackText = (Text)(message == null ? Text.literal("") : message);
         this.copyFeedbackUntilMs = System.currentTimeMillis() + 1600L;
      }

      private void rebuildRows() {
         MinecraftClient client = MinecraftClient.getInstance();
         List<AnimationCatalogData.Entry> entries;
         if (client != null && client.world != null && NonServerSettingsClient.hasAnimationCatalog()) {
            entries = NonServerSettingsClient.animationCatalogSnapshot();
            this.observedCatalogRevision = NonServerSettingsClient.catalogRevision();
         } else {
            entries = AnimationCatalogData.capture(this.config).animations();
         }

         List<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow> rows = new ArrayList<>(entries.size());

         for (AnimationCatalogData.Entry entry : entries) {
            if (entry != null) {
               rows.add(this.buildRow(entry));
            }
         }

         this.allRows = List.copyOf(rows);
      }

      private NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow buildRow(AnimationCatalogData.Entry definition) {
         List<NonModMenuDebugScreens.LoadedAnimationsScreen.ActorDisplay> actors = buildActorDisplays(definition.actors());
         Integer peakStage = definition.peakStage();
         String peakLabel = peakStage == null ? "-" : "p" + peakStage;
         NonModMenuDebugScreens.LoadedAnimationsScreen.ResourceCheck check = evaluateResourceCheck(definition);
         NonModMenuDebugScreens.LoadedAnimationsScreen.ResourceStatus status = check.status();
         String key = definition.id();
         Identifier definitionId = Identifier.tryParse(key);
         AnimationCatalogData.Pack packInfo = definition.pack();
         String packId = packInfo == null ? "unknown" : packInfo.id();
         String resourcePackId = packInfo == null ? "unknown" : packInfo.resourcePackId();
         String packName = packInfo == null ? "Unknown Pack" : packInfo.name();
         String packAuthor = packInfo == null ? "Unknown" : packInfo.author();
         boolean packDisabled = this.disabledAnimationPacks.contains(packId);
         boolean animationDisabled = this.disabledAnimations.contains(key);
         String source = definitionId == null ? "unknown:" : definitionId.getNamespace() + ":";
         String title = definitionId == null ? key : definitionId.getPath();
         title = title + (packDisabled ? " [pack off]" : (animationDisabled ? " [off]" : ""));
         List<String> detailLines = new ArrayList<>();
         detailLines.add("stages: " + definition.stages().size() + " | peak: " + peakLabel);
         detailLines.add(summarizeCatalogStages(definition.stages()));
         detailLines.addAll(summarizeCatalogActorRoles(definition.actors()));
         String resourcesLine = "resources: anim "
            + check.animationFound
            + "/"
            + check.animationTotal
            + " | model "
            + check.modelFound
            + "/"
            + check.modelTotal;
         if (check.modelUnchecked > 0) {
            resourcesLine = resourcesLine + " | model n/a " + check.modelUnchecked;
         }

         List<String> contentTags = definition.contentTags() == null
            ? List.of()
            : definition.contentTags().stream().filter(Objects::nonNull).map(String::trim).filter(tag -> !tag.isEmpty()).distinct().toList();
         String blockRequirementLine = definition.blockRequirement();
         List<String> infoLines = new ArrayList<>();
         if (!definition.attackEligible()) {
            infoLines.add("attacks: disabled");
         }

         if (blockRequirementLine != null) {
            infoLines.add(blockRequirementLine);
         }

         if (definition.waterRequirement() != null && !"none".equals(definition.waterRequirement())) {
            infoLines.add("water: " + definition.waterRequirement());
         }

         if (status != NonModMenuDebugScreens.LoadedAnimationsScreen.ResourceStatus.OK) {
            infoLines.add(resourcesLine);
         }

         if (!check.localModelDetails().isEmpty()) {
            int take = Math.min(3, check.localModelDetails().size());
            String localSummary = String.join(", ", check.localModelDetails().subList(0, take));
            int remaining = check.localModelDetails().size() - take;
            infoLines.add("local models: " + localSummary + (remaining > 0 ? ", +" + remaining : ""));
         }

         boolean hasMissingDetails = check.hasMissingDetails();
         List<String> missingLines = check.missingLinesStructured();
         int entityTypeCount = definition.actors().stream().mapToInt(actor -> actor.entityTypes() == null ? 0 : actor.entityTypes().size()).sum();
         int missingCount = check.totalMissingCount();
         String actorSearch = actors.stream().map(NonModMenuDebugScreens.LoadedAnimationsScreen.ActorDisplay::searchText).collect(Collectors.joining(" "));
         String searchBlob = (definition.id()
               + " "
               + actorSearch
               + " "
               + String.join(" ", infoLines)
               + " "
               + String.join(" ", detailLines)
               + " "
               + String.join(" ", contentTags)
               + " "
               + resourcesLine
               + " "
               + packName
               + " "
               + packAuthor
               + " "
               + this.sourceHintByDefinitionId.getOrDefault(key, "unknown")
               + (hasMissingDetails ? " " + String.join(" ", missingLines) : ""))
            .toLowerCase(Locale.ROOT);
         return new NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow(
            key,
            packId,
            resourcePackId,
            packName,
            packAuthor,
            source,
            title,
            actors,
            List.copyOf(detailLines),
            List.copyOf(infoLines),
            List.copyOf(contentTags),
            missingLines,
            searchBlob,
            status,
            missingCount,
            entityTypeCount,
            packDisabled,
            animationDisabled,
            hasMissingDetails,
            this.expandedRowKeys.contains(key)
         );
      }

      private void applyFilter(String rawFilter) {
         this.applyFilter(rawFilter, true);
      }

      private void applyFilter(String rawFilter, boolean resetScroll) {
         String filter = rawFilter == null ? "" : rawFilter.trim().toLowerCase(Locale.ROOT);
         List<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow> filtered = new ArrayList<>();

         for (NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow row : this.allRows) {
            if (row.matches(filter) && row.matchesStatus(this.statusFilter)) {
               filtered.add(row);
            }
         }

         filtered.sort(this.groupedRowComparator());
         this.filteredCount = filtered.size();
         if (this.loadedAnimationsList != null) {
            this.loadedAnimationsList.setRows(this.buildGroupedDisplayRows(filtered));
            if (resetScroll) {
               this.loadedAnimationsList.setScrollY(0.0);
            }
         }
      }

      private List<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedListRow> buildGroupedDisplayRows(
         List<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow> filtered
      ) {
         if (filtered != null && !filtered.isEmpty()) {
            Map<String, Integer> totalsByPack = new LinkedHashMap<>();
            Map<String, Integer> disabledByPack = new LinkedHashMap<>();

            for (NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow row : this.allRows) {
               totalsByPack.merge(row.packId(), 1, Integer::sum);
               if (row.animationDisabled()) {
                  disabledByPack.merge(row.packId(), 1, Integer::sum);
               }
            }

            List<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedListRow> out = new ArrayList<>();
            String currentPackId = null;
            List<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow> currentRows = new ArrayList<>();

            for (NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow row : filtered) {
               if (!Objects.equals(currentPackId, row.packId())) {
                  this.appendPackGroup(out, currentPackId, currentRows, totalsByPack, disabledByPack);
                  currentRows.clear();
                  currentPackId = row.packId();
               }

               currentRows.add(row);
            }

            this.appendPackGroup(out, currentPackId, currentRows, totalsByPack, disabledByPack);
            return List.copyOf(out);
         } else {
            return List.of();
         }
      }

      private void appendPackGroup(
         List<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedListRow> out,
         @Nullable String packId,
         List<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow> rows,
         Map<String, Integer> totalsByPack,
         Map<String, Integer> disabledByPack
      ) {
         if (out != null && packId != null && rows != null && !rows.isEmpty()) {
            NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow first = rows.getFirst();
            out.add(
               new NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedPackHeaderRow(
                  "pack:" + packId,
                  packId,
                  first.resourcePackId(),
                  first.packName(),
                  first.packAuthor(),
                  rows.size(),
                  totalsByPack.getOrDefault(packId, rows.size()),
                  disabledByPack.getOrDefault(packId, 0),
                  this.disabledAnimationPacks.contains(packId),
                  this.expandedPackIds.contains(packId),
                  this.resolvePackIcon(first.resourcePackId())
               )
            );
            if (this.expandedPackIds.contains(packId)) {
               out.addAll(rows);
            }
         }
      }

      private void togglePackExpanded(String packId) {
         if (packId != null && !packId.isBlank()) {
            if (this.expandedPackIds.contains(packId)) {
               this.expandedPackIds.remove(packId);
            } else {
               this.expandedPackIds.add(packId);
            }

            this.applyFilter(this.filterField == null ? "" : this.filterField.getText(), false);
         }
      }

      private void toggleRowExpanded(String key) {
         if (key != null && !key.isBlank()) {
            if (this.expandedRowKeys.contains(key)) {
               this.expandedRowKeys.remove(key);
            } else {
               this.expandedRowKeys.add(key);
            }

            this.rebuildRows();
            this.applyFilter(this.filterField == null ? "" : this.filterField.getText(), false);
         }
      }

      private Comparator<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow> rowComparator() {
         Comparator<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow> comparator = switch (this.sortMode) {
            case ID -> Comparator.comparing(NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow::key);
            case STATUS -> Comparator.<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow>comparingInt(row -> row.status().sortRank())
               .thenComparing(NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow::key);
            case MISSING -> Comparator.<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow>comparingInt(row -> row.missingCount())
               .thenComparing(NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow::key);
            case ENTITIES -> Comparator.<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow>comparingInt(row -> row.entityTypeCount())
               .thenComparing(NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow::key);
         };
         return this.sortAscending ? comparator : comparator.reversed();
      }

      private Comparator<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow> groupedRowComparator() {
         return Comparator.<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow, String>comparing(row -> row.packName().toLowerCase(Locale.ROOT))
            .thenComparing(row -> row.packId().toLowerCase(Locale.ROOT))
            .thenComparing(this.rowComparator());
      }

      private static NonModMenuDebugScreens.LoadedAnimationsScreen.ResourceCheck evaluateResourceCheck(AnimationCatalogData.Entry definition) {
         LinkedHashSet<Identifier> stageIds = new LinkedHashSet<>();
         if (definition.stages() != null) {
            for (AnimationCatalogData.Stage stage : definition.stages()) {
               if (stage != null) {
                  Identifier id = Identifier.tryParse(stage.animationId());
                  if (id != null) {
                     stageIds.add(id);
                  }
               }
            }
         }

         if (stageIds.isEmpty()) {
            Identifier id = Identifier.tryParse(definition.id());
            if (id != null) {
               stageIds.add(id);
            }
         }

         int animationTotal = 0;
         int animationFound = 0;
         int modelTotal = 0;
         int modelFound = 0;
         int modelUnchecked = 0;
         LinkedHashSet<String> missingAnimations = new LinkedHashSet<>();
         LinkedHashSet<String> missingModels = new LinkedHashSet<>();
         LinkedHashSet<String> localModelDetails = new LinkedHashSet<>();

         for (AnimationCatalogData.Actor actor : definition.actors() == null ? List.<AnimationCatalogData.Actor>of() : definition.actors()) {
            if (actor != null) {
               String actorKey = actor.key();
               List<Identifier> runtimeTypes = normalizeEntityTypesForRuntime(actor.entityTypes());
               if (runtimeTypes.isEmpty()) {
                  modelUnchecked++;

                  for (Identifier stageId : stageIds) {
                     Identifier animationResource = GeckoResourceResolver.resolveAnimationResource(stageId, actorKey, null, false);
                     String animationKey = GeckoResourceResolver.resolveAnimationKey(animationResource, stageId, actorKey, null, false);
                     animationTotal++;
                     if (GeckoResourceResolver.hasBakedAnimationKey(animationResource, animationKey)) {
                        animationFound++;
                     } else {
                        missingAnimations.add("actor=" + actorKey + " -> " + missingAnimationDetail(animationResource, animationKey));
                     }
                  }
               } else {
                  for (Identifier entityTypeId : runtimeTypes) {
                     List<NonModMenuDebugScreens.LoadedAnimationsScreen.ModelProvider> modelProviders = modelProvidersFor(definition.pack(), entityTypeId);
                     NonModMenuDebugScreens.LoadedAnimationsScreen.ModelCoverage coverage = evaluateModelCoverage(
                        actor.requiredTags(), entityTypeId, modelProviders
                     );
                     modelTotal++;
                     if (coverage.found()) {
                        modelFound++;
                     } else {
                        missingModels.add(coverage.missingDetail());
                     }

                     localModelDetails.addAll(coverage.localModelDetails());

                     for (Identifier stageId : stageIds) {
                        Identifier animationResource = GeckoResourceResolver.resolveAnimationResource(stageId, actorKey, entityTypeId, false);
                        String animationKey = GeckoResourceResolver.resolveAnimationKey(animationResource, stageId, actorKey, entityTypeId, false);
                        animationTotal++;
                        if (GeckoResourceResolver.hasBakedAnimationKey(animationResource, animationKey)) {
                           animationFound++;
                        } else {
                           missingAnimations.add(
                              "actor=" + actorKey + " type=" + entityTypeId + " -> " + missingAnimationDetail(animationResource, animationKey)
                           );
                        }
                     }
                  }
               }
            }
         }

         return new NonModMenuDebugScreens.LoadedAnimationsScreen.ResourceCheck(
            animationTotal,
            animationFound,
            modelTotal,
            modelFound,
            modelUnchecked,
            List.copyOf(missingAnimations),
            List.copyOf(missingModels),
            List.copyOf(localModelDetails)
         );
      }

      private static String missingAnimationDetail(Identifier resource, String animationKey) {
         return animationKey != null && !animationKey.isBlank() ? resource + "#" + animationKey : resource + " (actor clip missing)";
      }

      private static List<Identifier> normalizeEntityTypesForRuntime(List<String> entityTypes) {
         if (entityTypes != null && !entityTypes.isEmpty()) {
            LinkedHashSet<Identifier> normalized = new LinkedHashSet<>();

            for (String raw : entityTypes) {
               if (raw != null && !raw.isBlank()) {
                  Identifier id = Identifier.tryParse(raw);
                  if (id != null) {
                     normalized.add(id);
                  }
               }
            }

            List<Identifier> sorted = new ArrayList<>(normalized);
            sorted.sort(Comparator.comparing(Identifier::toString));
            return List.copyOf(sorted);
         } else {
            return List.of();
         }
      }

      private static NonModMenuDebugScreens.LoadedAnimationsScreen.ModelCoverage evaluateModelCoverage(
         List<String> requiredTags, Identifier entityTypeId, List<NonModMenuDebugScreens.LoadedAnimationsScreen.ModelProvider> modelProviders
      ) {
         Set<String> tags = requiredTags == null ? Set.of() : new HashSet<>(requiredTags);
         return evaluateModelCoverage(tags, entityTypeId, modelProviders);
      }

      private static NonModMenuDebugScreens.LoadedAnimationsScreen.ModelCoverage evaluateModelCoverage(
         Set<String> requiredTags, Identifier entityTypeId, List<NonModMenuDebugScreens.LoadedAnimationsScreen.ModelProvider> modelProviders
      ) {
         List<Identifier> modelRoots = modelProviders.stream().map(NonModMenuDebugScreens.LoadedAnimationsScreen.ModelProvider::root).toList();
         GeckoResourceResolver.ModelAndTexture maleModel = GeckoResourceResolver.resolveModelAndTexture(
            entityTypeId, modelRoots, List.of(entityTypeId.getPath()), GenderedModelResolver.ModelGender.MALE
         );
         GeckoResourceResolver.ModelAndTexture femaleModel = GeckoResourceResolver.resolveModelAndTexture(
            entityTypeId, modelRoots, List.of(entityTypeId.getPath()), GenderedModelResolver.ModelGender.FEMALE
         );
         GeckoResourceResolver.ModelAndTexture bothModel = GeckoResourceResolver.resolveModelAndTexture(
            entityTypeId, modelRoots, List.of(entityTypeId.getPath()), GenderedModelResolver.ModelGender.BOTH
         );
         NonModMenuDebugScreens.LoadedAnimationsScreen.GenderRequirement requirement = genderRequirement(requiredTags);

         boolean found = switch (requirement) {
            case ANY -> !maleModel.missingModel() && !femaleModel.missingModel();
            case MALE_ONLY -> !maleModel.missingModel();
            case FEMALE_ONLY -> !femaleModel.missingModel();
            case BOTH_ONLY -> !bothModel.missingModel();
         };
         LinkedHashSet<String> localDetails = new LinkedHashSet<>();
         switch (requirement) {
            case ANY:
               addLocalModelDetail(localDetails, entityTypeId, "♂", maleModel.modelRoot(), modelProviders);
               addLocalModelDetail(localDetails, entityTypeId, "♀", femaleModel.modelRoot(), modelProviders);
               break;
            case MALE_ONLY:
               addLocalModelDetail(localDetails, entityTypeId, "♂", maleModel.modelRoot(), modelProviders);
               break;
            case FEMALE_ONLY:
               addLocalModelDetail(localDetails, entityTypeId, "♀", femaleModel.modelRoot(), modelProviders);
               break;
            case BOTH_ONLY:
               addLocalModelDetail(localDetails, entityTypeId, "♂♀", bothModel.modelRoot(), modelProviders);
         }

         if (found) {
            return NonModMenuDebugScreens.LoadedAnimationsScreen.ModelCoverage.present(List.copyOf(localDetails));
         }

         String detail = switch (requirement) {
            case ANY -> entityTypeId + " (need base or both .m/.f)";
            case MALE_ONLY -> entityTypeId + " (need base or .m)";
            case FEMALE_ONLY -> entityTypeId + " (need base or .f)";
            case BOTH_ONLY -> entityTypeId + " (need base or .mf; .m/.f can fall back)";
         };
         return NonModMenuDebugScreens.LoadedAnimationsScreen.ModelCoverage.missingCoverage(detail, List.copyOf(localDetails));
      }

      private static List<NonModMenuDebugScreens.LoadedAnimationsScreen.ModelProvider> modelProvidersFor(
         AnimationCatalogData.Pack pack, Identifier entityTypeId
      ) {
         if (pack != null && entityTypeId != null) {
            LinkedHashMap<Identifier, String> providers = new LinkedHashMap<>();
            Identifier localRoot = pack.localModelRoot() == null ? null : Identifier.tryParse(pack.localModelRoot());
            if (localRoot != null) {
               providers.put(localRoot, "");
            }

            if (pack.modelImports() != null) {
               for (AnimationCatalogData.ModelImport modelImport : pack.modelImports()) {
                  if (modelImport != null && modelImport.entityTypes() != null && modelImport.entityTypes().contains(entityTypeId.toString())) {
                     Identifier root = modelImport.modelRoot() == null ? null : Identifier.tryParse(modelImport.modelRoot());
                     if (root != null) {
                        providers.putIfAbsent(root, modelImport.packId());
                     }
                  }
               }
            }

            return providers.entrySet()
               .stream()
               .map(entry -> new NonModMenuDebugScreens.LoadedAnimationsScreen.ModelProvider(entry.getKey(), entry.getValue()))
               .toList();
         } else {
            return List.of();
         }
      }

      private static void addLocalModelDetail(
         LinkedHashSet<String> details,
         Identifier entityTypeId,
         String gender,
         @Nullable Identifier selectedRoot,
         List<NonModMenuDebugScreens.LoadedAnimationsScreen.ModelProvider> providers
      ) {
         if (selectedRoot != null) {
            String provider = providers.stream()
               .filter(candidate -> candidate.root().equals(selectedRoot))
               .map(NonModMenuDebugScreens.LoadedAnimationsScreen.ModelProvider::label)
               .findFirst()
               .orElse(selectedRoot.toString());
            String detail = entityTypeId.getPath() + " " + gender;
            if (!provider.isBlank()) {
               detail = detail + " (" + provider + ")";
            }

            details.add(detail);
         }
      }

      private static NonModMenuDebugScreens.LoadedAnimationsScreen.GenderRequirement genderRequirement(Set<String> requiredTags) {
         if (requiredTags != null && !requiredTags.isEmpty()) {
            boolean needsMale = requiredTags.contains("gender.male");
            boolean needsFemale = requiredTags.contains("gender.female");
            if (needsMale && needsFemale) {
               return NonModMenuDebugScreens.LoadedAnimationsScreen.GenderRequirement.BOTH_ONLY;
            } else if (needsMale && !needsFemale) {
               return NonModMenuDebugScreens.LoadedAnimationsScreen.GenderRequirement.MALE_ONLY;
            } else {
               return needsFemale && !needsMale
                  ? NonModMenuDebugScreens.LoadedAnimationsScreen.GenderRequirement.FEMALE_ONLY
                  : NonModMenuDebugScreens.LoadedAnimationsScreen.GenderRequirement.ANY;
            }
         } else {
            return NonModMenuDebugScreens.LoadedAnimationsScreen.GenderRequirement.ANY;
         }
      }

      private static String summarizeCatalogStages(List<AnimationCatalogData.Stage> stages) {
         if (stages != null && !stages.isEmpty()) {
            List<String> parts = new ArrayList<>();
            int previewCount = Math.min(3, stages.size());

            for (int i = 0; i < previewCount; i++) {
               AnimationCatalogData.Stage stage = stages.get(i);
               String stageLabel = "p" + (stage.stageNumber() == null ? "?" : stage.stageNumber());
               String loopLabel = stage.loop() ? "loop" : "once";
               String cycle = stage.cycleTicks() > 0.0 ? String.format(Locale.ROOT, "%.2fs", stage.cycleTicks() / 20.0) : "-";
               String speed = String.format(Locale.ROOT, "%.2fx", stage.speed());
               parts.add(stageLabel + " " + loopLabel + " " + cycle + " @" + speed);
            }

            if (stages.size() > previewCount) {
               parts.add("+" + (stages.size() - previewCount) + " more");
            }

            return "loop/cycle: " + String.join(", ", parts);
         } else {
            return "loop/cycle: -";
         }
      }

      private static List<String> summarizeCatalogActorRoles(List<AnimationCatalogData.Actor> actors) {
         List<String> injectors = new ArrayList<>();
         List<String> receivers = new ArrayList<>();
         if (actors != null) {
            for (AnimationCatalogData.Actor actor : actors) {
               if (actor != null) {
                  String actorKey = actor.key() != null && !actor.key().isBlank() ? actor.key() : "actor";
                  String injectorType = actor.injectorType();
                  if (injectorType != null && !injectorType.isBlank() && !"NONE".equalsIgnoreCase(injectorType)) {
                     injectors.add(actorKey + " (" + injectorType.toUpperCase(Locale.ROOT) + ")");
                  }

                  if (actor.receiver()) {
                     receivers.add(actorKey);
                  }
               }
            }
         }

         return List.of(
            "injectors: " + (injectors.isEmpty() ? "none" : String.join(", ", injectors)),
            "receivers: " + (receivers.isEmpty() ? "none" : String.join(", ", receivers))
         );
      }

      private static List<NonModMenuDebugScreens.LoadedAnimationsScreen.ActorDisplay> buildActorDisplays(List<AnimationCatalogData.Actor> actors) {
         if (actors != null && !actors.isEmpty()) {
            List<NonModMenuDebugScreens.LoadedAnimationsScreen.ActorDisplay> displays = new ArrayList<>(actors.size());

            for (int actorIndex = 0; actorIndex < actors.size(); actorIndex++) {
               AnimationCatalogData.Actor actor = actors.get(actorIndex);
               if (actor != null) {
                  List<NonModMenuDebugScreens.LoadedAnimationsScreen.EntityTypeDisplay> entityTypes = new ArrayList<>();

                  for (Identifier id : normalizeEntityTypesForRuntime(actor.entityTypes())) {
                     String displayName = Registries.ENTITY_TYPE.getOptionalValue(id).map(type -> type.getName().getString()).orElse(id.getPath());
                     entityTypes.add(
                        new NonModMenuDebugScreens.LoadedAnimationsScreen.EntityTypeDisplay(
                           id.getNamespace(), displayName, id.toString(), "minecraft".equals(id.getNamespace())
                        )
                     );
                  }

                  String key = actor.key() != null && !actor.key().isBlank() ? actor.key() : "actor";
                  NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge gender = resolveActorGenderBadge(actor.requiredTags(), actor.requiredTagsAny());
                  if (gender == NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge.NONE
                     && actor.receiver()
                     && hasOtherVInjector(actors, actorIndex)) {
                     gender = NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge.FEMALE_IMPLIED;
                  }

                  displays.add(new NonModMenuDebugScreens.LoadedAnimationsScreen.ActorDisplay(key, List.copyOf(entityTypes), gender));
               }
            }

            return List.copyOf(displays);
         } else {
            return List.of();
         }
      }

      private static boolean hasOtherVInjector(List<AnimationCatalogData.Actor> actors, int receiverIndex) {
         for (int actorIndex = 0; actorIndex < actors.size(); actorIndex++) {
            if (actorIndex != receiverIndex) {
               AnimationCatalogData.Actor actor = actors.get(actorIndex);
               if (actor != null && "V".equalsIgnoreCase(actor.injectorType())) {
                  return true;
               }
            }
         }

         return false;
      }

      private static NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge resolveActorGenderBadge(
         List<String> requiredTags, List<String> requiredTagsAny
      ) {
         Set<String> required = requiredTags == null ? Set.of() : new HashSet<>(requiredTags);
         Set<String> any = requiredTagsAny == null ? Set.of() : new HashSet<>(requiredTagsAny);
         boolean requiredMale = required.contains("gender.male");
         boolean requiredFemale = required.contains("gender.female");
         boolean anyMale = any.contains("gender.male");
         boolean anyFemale = any.contains("gender.female");
         boolean anyOther = any.stream().anyMatch(tag -> !"gender.male".equals(tag) && !"gender.female".equals(tag));
         if (requiredMale && requiredFemale) {
            return NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge.BOTH;
         }

         if (requiredMale) {
            return !any.isEmpty() && anyFemale && !anyMale && !anyOther
               ? NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge.BOTH
               : NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge.MALE;
         }

         if (!requiredFemale) {
            if (anyOther) {
               return NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge.NONE;
            } else if (anyMale && anyFemale) {
               return NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge.EITHER;
            } else if (anyMale) {
               return NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge.MALE;
            } else {
               return anyFemale
                  ? NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge.FEMALE
                  : NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge.NONE;
            }
         } else {
            return !any.isEmpty() && anyMale && !anyFemale && !anyOther
               ? NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge.BOTH
               : NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge.FEMALE;
         }
      }

      private record ActorDisplay(
         String key,
         List<NonModMenuDebugScreens.LoadedAnimationsScreen.EntityTypeDisplay> entityTypes,
         NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge gender
      ) {
         private String searchText() {
            String types = this.entityTypes == null
               ? ""
               : this.entityTypes.stream().map(NonModMenuDebugScreens.LoadedAnimationsScreen.EntityTypeDisplay::searchText).collect(Collectors.joining(" "));
            return this.key + " " + types + " " + (this.gender == null ? "" : this.gender.name());
         }
      }

      private enum ActorGenderBadge {
         NONE,
         MALE,
         FEMALE,
         FEMALE_IMPLIED,
         BOTH,
         EITHER;
      }

      private record EntityTypeDisplay(String namespace, String displayName, String rawId, boolean vanilla) {
         private String searchText() {
            return this.rawId + " " + this.namespace + " " + this.displayName;
         }
      }

      private enum GenderRequirement {
         ANY,
         MALE_ONLY,
         FEMALE_ONLY,
         BOTH_ONLY;
      }

      private record LoadedAnimationRow(
         String key,
         String packId,
         String resourcePackId,
         String packName,
         String packAuthor,
         String source,
         String title,
         List<NonModMenuDebugScreens.LoadedAnimationsScreen.ActorDisplay> actors,
         List<String> detailLines,
         List<String> infoLines,
         List<String> contentTags,
         List<String> missingLines,
         String searchBlob,
         NonModMenuDebugScreens.LoadedAnimationsScreen.ResourceStatus status,
         int missingCount,
         int entityTypeCount,
         boolean packDisabled,
         boolean animationDisabled,
         boolean hasMissingDetails,
         boolean expanded
      ) implements NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedListRow {
         boolean effectivelyDisabled() {
            return this.packDisabled || this.animationDisabled;
         }

         private boolean matches(String filter) {
            return filter != null && !filter.isBlank() ? this.searchBlob.contains(filter) : true;
         }

         private boolean matchesStatus(NonModMenuDebugScreens.LoadedAnimationsScreen.StatusFilter filter) {
            return switch (filter) {
               case ALL -> true;
               case MISSING -> this.status == NonModMenuDebugScreens.LoadedAnimationsScreen.ResourceStatus.MISSING;
               case PARTIAL -> this.status == NonModMenuDebugScreens.LoadedAnimationsScreen.ResourceStatus.PARTIAL;
               case OK -> this.status == NonModMenuDebugScreens.LoadedAnimationsScreen.ResourceStatus.OK;
            };
         }
      }

      private static final class LoadedAnimationsList extends ElementListWidget<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationsList.RowEntry> {
         private static final int TAG_CARD_HEIGHT = 13;
         private static final int TAG_CARD_PADDING = 4;
         private static final int TAG_HORIZONTAL_GAP = 4;
         private static final int TAG_VERTICAL_GAP = 3;
         private static final int CARD_BOTTOM_PADDING = 8;
         private static final int ANIMATION_PREVIEW_SIZE = 46;
         private static final int ANIMATION_PREVIEW_COLUMN_WIDTH = 54;
         private static final int MIN_ANIMATION_CARD_HEIGHT = 64;
         private final TextRenderer textRenderer;
         private final Consumer<String> onTogglePackExpanded;
         private final Consumer<String> onToggleRow;
         private final Consumer<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow> onCopyMissing;
         private final Consumer<String> onTogglePackEnabled;
         private final Consumer<String> onToggleAnimationEnabled;
         private final BooleanSupplier editableSupplier;

         private LoadedAnimationsList(
            MinecraftClient client,
            int width,
            int height,
            int top,
            TextRenderer textRenderer,
            Consumer<String> onTogglePackExpanded,
            Consumer<String> onToggleRow,
            Consumer<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow> onCopyMissing,
            Consumer<String> onTogglePackEnabled,
            Consumer<String> onToggleAnimationEnabled,
            BooleanSupplier editableSupplier
         ) {
            super(client, width, height, top, 60);
            this.textRenderer = textRenderer;
            this.onTogglePackExpanded = onTogglePackExpanded;
            this.onToggleRow = onToggleRow;
            this.onCopyMissing = onCopyMissing;
            this.onTogglePackEnabled = onTogglePackEnabled;
            this.onToggleAnimationEnabled = onToggleAnimationEnabled;
            this.editableSupplier = editableSupplier;
            this.centerListVertically = false;
         }

         private void setRows(List<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedListRow> rows) {
            this.clearEntries();

            for (NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedListRow row : rows) {
               this.addEntry(
                  new NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationsList.RowEntry(
                     row,
                     this.textRenderer,
                     this.onToggleRow,
                     this.onCopyMissing,
                     this.onTogglePackExpanded,
                     this.onTogglePackEnabled,
                     this.onToggleAnimationEnabled,
                     this.editableSupplier
                  ),
                  this.rowHeight(row)
               );
            }
         }

         private int rowHeight(NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedListRow row) {
            if (row instanceof NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedPackHeaderRow) {
               return 34;
            }

            if (row instanceof NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow animationRow) {
               int infoLines = Math.max(0, animationRow.infoLines() == null ? 0 : animationRow.infoLines().size());
               int contentEnd = 23 + infoLines * 9;
               int tagRows = countTagRows(this.textRenderer, animationRow.contentTags(), Math.max(20, this.getRowWidth() - 12 - 54));
               if (tagRows > 0) {
                  contentEnd += 4 + tagRows * 13 + (tagRows - 1) * 3;
               }

               int collapsedHeight = contentEnd + 18 + 8;
               if (!animationRow.expanded()) {
                  return Math.max(64, collapsedHeight);
               }

               int detailLines = animationRow.detailLines() == null ? 0 : animationRow.detailLines().size();
               int missingLines = animationRow.hasMissingDetails() && animationRow.missingLines() != null
                  ? Math.min(10, animationRow.missingLines().size())
                  : 0;
               return Math.max(64, collapsedHeight + detailLines * 9 + (missingLines > 0 ? 2 + missingLines * 9 : 0));
            } else {
               return 48;
            }
         }

         private static int countTagRows(TextRenderer textRenderer, List<String> tags, int maxWidth) {
            if (tags != null && !tags.isEmpty() && maxWidth > 0) {
               int rows = 1;
               int usedWidth = 0;

               for (String tag : tags) {
                  if (tag != null && !tag.isBlank()) {
                     int width = Math.min(maxWidth, textRenderer.getWidth(tag) + 8);
                     if (usedWidth > 0 && usedWidth + 4 + width > maxWidth) {
                        rows++;
                        usedWidth = width;
                     } else {
                        usedWidth += (usedWidth == 0 ? 0 : 4) + width;
                     }
                  }
               }

               return usedWidth == 0 ? 0 : rows;
            } else {
               return 0;
            }
         }

         public int getRowWidth() {
            return Math.min(500, Math.max(120, this.getWidth() - 24));
         }

         public int getRowLeft() {
            return (this.getWidth() - this.getRowWidth()) / 2;
         }

         protected int getScrollbarX() {
            return Math.min(this.getWidth() - 8, this.getRowLeft() + this.getRowWidth() + 6);
         }

         private static final class RowEntry
            extends net.minecraft.client.gui.widget.ElementListWidget.Entry<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationsList.RowEntry> {
            private final NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedListRow row;
            private final TextRenderer textRenderer;
            private final Consumer<String> onToggleRow;
            private final Consumer<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow> onCopyMissing;
            private final Consumer<String> onTogglePackExpanded;
            private final Consumer<String> onTogglePackEnabled;
            private final Consumer<String> onToggleAnimationEnabled;
            private final BooleanSupplier editableSupplier;
            private final NonModMenuScreens.FlatTextButton toggleButton;
            private final NonModMenuScreens.FlatTextButton copyMissingButton;
            private final ButtonWidget enabledButton;
            private final List<ClickableWidget> widgets;

            private RowEntry(
               NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedListRow row,
               TextRenderer textRenderer,
               Consumer<String> onToggleRow,
               Consumer<NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow> onCopyMissing,
               Consumer<String> onTogglePackExpanded,
               Consumer<String> onTogglePackEnabled,
               Consumer<String> onToggleAnimationEnabled,
               BooleanSupplier editableSupplier
            ) {
               this.row = row;
               this.textRenderer = textRenderer;
               this.onToggleRow = onToggleRow;
               this.onCopyMissing = onCopyMissing;
               this.onTogglePackExpanded = onTogglePackExpanded;
               this.onTogglePackEnabled = onTogglePackEnabled;
               this.onToggleAnimationEnabled = onToggleAnimationEnabled;
               this.editableSupplier = editableSupplier == null ? () -> true : editableSupplier;
               this.toggleButton = new NonModMenuScreens.FlatTextButton(
                  18,
                  Text.literal(
                     row instanceof NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow animationRow && animationRow.expanded() ? "-" : "+"
                  ),
                  button -> {
                     if (this.row instanceof NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedPackHeaderRow headerRow) {
                        if (this.onTogglePackExpanded != null) {
                           this.onTogglePackExpanded.accept(headerRow.packId());
                        }
                     } else if (this.row instanceof NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow animationRowx && this.onToggleRow != null) {
                        this.onToggleRow.accept(animationRowx.key());
                     }
                  }
               );
               this.copyMissingButton = new NonModMenuScreens.FlatTextButton(
                  24,
                  Text.literal("MS"),
                  button -> {
                     if (this.row instanceof NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow animationRowx
                        && animationRowx.hasMissingDetails()
                        && this.onCopyMissing != null) {
                        this.onCopyMissing.accept(animationRowx);
                     }
                  }
               );
               this.enabledButton = ButtonWidget.builder(
                     Text.literal("ON"),
                     button -> {
                        if (this.editableSupplier.getAsBoolean()) {
                           if (this.row instanceof NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedPackHeaderRow headerRow) {
                              if (this.onTogglePackEnabled != null) {
                                 this.onTogglePackEnabled.accept(headerRow.packId());
                              }
                           } else if (this.row instanceof NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow animationRowx
                              && this.onToggleAnimationEnabled != null) {
                              this.onToggleAnimationEnabled.accept(animationRowx.key());
                           }
                        }
                     }
                  )
                  .dimensions(0, 0, 42, 18)
                  .build();
               List<ClickableWidget> ws = new ArrayList<>();
               ws.add(this.enabledButton);
               if (row instanceof NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow animationRow) {
                  ws.add(this.toggleButton);
                  if (animationRow.hasMissingDetails()) {
                     ws.add(this.copyMissingButton);
                  }
               }

               this.widgets = List.copyOf(ws);
            }

            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
               int x = this.getContentX() + 4;
               int y = this.getContentY() + 3;
               int right = this.getContentX() + this.getContentWidth() - 2;
               if (this.row instanceof NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedPackHeaderRow headerRow) {
                  this.renderPackHeader(context, mouseX, mouseY, deltaTicks, x, y, right, headerRow);
               } else if (this.row instanceof NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow animationRow) {
                  int var47 = this.getContentX() + 2;
                  int cardTop = this.getContentY() + 1;
                  int cardRight = this.getContentX() + this.getContentWidth() - 2;
                  int cardBottom = this.getContentY() + this.getContentHeight() - 2;
                  int cardBorder = animationRow.effectivelyDisabled() ? -14079703 : (hovered ? -9281155 : -11450790);
                  int cardBackground = animationRow.effectivelyDisabled() ? 1108809495 : (hovered ? 1747199012 : 1427971357);
                  context.fill(var47 + 1, cardTop + 1, cardRight - 1, cardBottom - 1, cardBackground);
                  context.fill(var47, cardTop, cardRight, cardTop + 1, cardBorder);
                  context.fill(var47, cardBottom - 1, cardRight, cardBottom, cardBorder);
                  context.fill(var47, cardTop + 1, var47 + 1, cardBottom - 1, cardBorder);
                  context.fill(cardRight - 1, cardTop + 1, cardRight, cardBottom - 1, cardBorder);
                  x = this.getContentX() + 6;
                  y = this.getContentY() + 5;
                  right = this.getContentX() + this.getContentWidth() - 4;
                  this.enabledButton.setMessage(Text.literal(animationRow.animationDisabled() ? "OFF" : "ON"));
                  this.enabledButton.active = this.editableSupplier.getAsBoolean();
                  this.enabledButton.setX(right - 44);
                  this.enabledButton.setY(y - 3);
                  this.enabledButton.render(context, mouseX, mouseY, deltaTicks);
                  if (animationRow.hasMissingDetails()) {
                     this.copyMissingButton.setX(right - 86);
                     this.copyMissingButton.setY(y - 1);
                     this.copyMissingButton.render(context, mouseX, mouseY, deltaTicks);
                  }

                  NonAnimationPreviewRenderer.draw(context, this.textRenderer, animationRow.key(), x, y, 46);
                  if (animationRow.effectivelyDisabled()) {
                     context.fill(x, y, x + 46, y + 46, 1711276032);
                  }

                  int textX = x + 54;
                  int statusButtonLeft = animationRow.hasMissingDetails() ? right - 86 : right - 44;
                  String statusText = "[" + Text.translatable(animationRow.status().translationKey).getString() + "]";
                  int statusWidth = this.textRenderer.getWidth(statusText);
                  int statusX = Math.max(textX, statusButtonLeft - statusWidth - 6);
                  int textWidth = Math.max(20, statusX - textX - 6);
                  int titleColor = animationRow.effectivelyDisabled() ? -8947849 : animationRow.status().color;
                  int statusColor = animationRow.effectivelyDisabled() ? -8947849 : animationRow.status().color;
                  int sourceColor = animationRow.effectivelyDisabled() ? -10526881 : -8947849;
                  String visibleSource = this.textRenderer.trimToWidth(animationRow.source(), textWidth);
                  context.drawTextWithShadow(this.textRenderer, visibleSource, textX, y, sourceColor);
                  int sourceWidth = this.textRenderer.getWidth(visibleSource);
                  int remainingTitleWidth = Math.max(0, textWidth - sourceWidth);
                  if (remainingTitleWidth > 0) {
                     context.drawTextWithShadow(
                        this.textRenderer, this.textRenderer.trimToWidth(animationRow.title(), remainingTitleWidth), textX + sourceWidth, y, titleColor
                     );
                  }

                  context.drawTextWithShadow(this.textRenderer, statusText, statusX, y, statusColor);
                  Text actorText = this.buildActorText(animationRow);
                  List<OrderedText> actorLines = this.textRenderer.wrapLines(actorText, textWidth);
                  if (!actorLines.isEmpty()) {
                     context.drawTextWithShadow(this.textRenderer, actorLines.getFirst(), textX, y + 9, -1);
                  }

                  List<String> infoLines = animationRow.infoLines() == null ? List.of() : animationRow.infoLines();

                  for (int i = 0; i < infoLines.size(); i++) {
                     int color = animationRow.effectivelyDisabled()
                        ? -8947849
                        : (
                           infoLines.get(i).startsWith("attacks:")
                              ? -2710919
                              : (infoLines.get(i).startsWith("block:") ? -4795766 : (infoLines.get(i).startsWith("local models:") ? -4086064 : -7303024))
                        );
                     context.drawTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(infoLines.get(i), textWidth), textX, y + 18 + i * 9, color);
                  }

                  int detailsY = y + 18 + infoLines.size() * 9;
                  List<String> contentTags = animationRow.contentTags() == null ? List.of() : animationRow.contentTags();
                  if (!contentTags.isEmpty()) {
                     detailsY = this.renderTagCards(context, contentTags, textX, detailsY + 2, right - 2, mouseX, mouseY, animationRow.effectivelyDisabled())
                        + 2;
                  }

                  int dividerColor = animationRow.effectivelyDisabled() ? -13816531 : -12306101;
                  context.fill(textX, detailsY + 1, right - 2, detailsY + 2, dividerColor);
                  int footerY = detailsY + 3;
                  int detailsTextX = textX + 20;
                  int detailsWidth = Math.max(20, right - detailsTextX - 2);
                  this.toggleButton.setMessage(Text.literal(animationRow.expanded() ? "-" : "+"));
                  this.toggleButton.setX(textX);
                  this.toggleButton.setY(footerY - 1);
                  this.toggleButton.render(context, mouseX, mouseY, deltaTicks);
                  context.drawTextWithShadow(
                     this.textRenderer,
                     this.textRenderer.trimToWidth(Text.translatable("config.needsofnature.loaded_animations.details").getString(), detailsWidth),
                     detailsTextX,
                     footerY,
                     animationRow.effectivelyDisabled() ? -9474193 : -6642524
                  );
                  if (animationRow.expanded()) {
                     int lineY = footerY + 13;
                     List<String> detailLines = animationRow.detailLines() == null ? List.of() : animationRow.detailLines();

                     for (int i = 0; i < detailLines.size(); i++) {
                        context.drawTextWithShadow(
                           this.textRenderer,
                           this.textRenderer.trimToWidth(detailLines.get(i), detailsWidth),
                           detailsTextX,
                           lineY,
                           animationRow.effectivelyDisabled() ? -9474193 : (i == 0 ? -4671304 : -7303024)
                        );
                        lineY += 9;
                     }

                     if (animationRow.hasMissingDetails()) {
                        lineY += 2;
                        List<String> missingLines = animationRow.missingLines();
                        int maxLines = Math.min(10, missingLines == null ? 0 : missingLines.size());

                        for (int i = 0; i < maxLines; i++) {
                           context.drawTextWithShadow(
                              this.textRenderer,
                              this.textRenderer.trimToWidth(missingLines.get(i), detailsWidth),
                              detailsTextX,
                              lineY,
                              animationRow.effectivelyDisabled() ? -9474193 : -7303024
                           );
                           lineY += 9;
                        }
                     }
                  }
               }
            }

            private Text buildActorText(NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedAnimationRow animationRow) {
               boolean disabled = animationRow.effectivelyDisabled();
               int labelColor = disabled ? 7829367 : 9079434;
               int entityColor = disabled ? 7829367 : 13684944;
               int namespaceColor = disabled ? 6710886 : 7570829;
               int separatorColor = disabled ? 6710886 : 7829367;
               List<NonModMenuDebugScreens.LoadedAnimationsScreen.ActorDisplay> actors = animationRow.actors() == null ? List.of() : animationRow.actors();
               if (actors.isEmpty()) {
                  return coloredText("No actors", labelColor);
               }

               MutableText line = Text.literal("");

               for (int actorIndex = 0; actorIndex < actors.size(); actorIndex++) {
                  if (actorIndex > 0) {
                     line.append(coloredText(",  ", separatorColor));
                  }

                  NonModMenuDebugScreens.LoadedAnimationsScreen.ActorDisplay actor = actors.get(actorIndex);
                  line.append(coloredText(actor.key() + ": ", labelColor));
                  List<NonModMenuDebugScreens.LoadedAnimationsScreen.EntityTypeDisplay> entityTypes = actor.entityTypes() == null
                     ? List.of()
                     : actor.entityTypes();
                  if (entityTypes.isEmpty()) {
                     line.append(coloredText("Any entity", entityColor));
                  } else {
                     for (int typeIndex = 0; typeIndex < entityTypes.size(); typeIndex++) {
                        if (typeIndex > 0) {
                           line.append(coloredText(" | ", separatorColor));
                        }

                        NonModMenuDebugScreens.LoadedAnimationsScreen.EntityTypeDisplay entityType = entityTypes.get(typeIndex);
                        if (!entityType.vanilla()) {
                           line.append(coloredText(entityType.namespace() + ": ", namespaceColor));
                        }

                        line.append(coloredText(entityType.displayName(), entityColor));
                     }
                  }

                  appendGenderBadge(line, actor.gender(), disabled);
               }

               return line;
            }

            private static MutableText coloredText(String value, int color) {
               return Text.literal(value).styled(style -> style.withColor(color));
            }

            private static void appendGenderBadge(MutableText line, NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge badge, boolean disabled) {
               if (badge != null && badge != NonModMenuDebugScreens.LoadedAnimationsScreen.ActorGenderBadge.NONE) {
                  int neutral = disabled ? 7829367 : 10066329;
                  int male = disabled ? 7829367 : 5880040;
                  int female = disabled ? 7829367 : 15239605;
                  line.append(coloredText(" ", neutral));
                  switch (badge) {
                     case NONE:
                     default:
                        break;
                     case MALE:
                        line.append(coloredText("♂", male));
                        break;
                     case FEMALE:
                        line.append(coloredText("♀", female));
                        break;
                     case FEMALE_IMPLIED:
                        line.append(coloredText("♀", neutral));
                        break;
                     case BOTH:
                        line.append(coloredText("♂", male)).append(coloredText("♀", female));
                        break;
                     case EITHER:
                        line.append(coloredText("♂", male)).append(coloredText("/", neutral)).append(coloredText("♀", female));
                  }
               }
            }

            private int renderTagCards(DrawContext context, List<String> tags, int startX, int startY, int endX, int mouseX, int mouseY, boolean disabled) {
               int maxWidth = Math.max(1, endX - startX);
               int x = startX;
               int y = startY;
               String truncatedHoveredTag = null;

               for (String tag : tags) {
                  if (tag != null && !tag.isBlank()) {
                     String display = NonTagLocalizer.localize(tag);
                     int naturalWidth = this.textRenderer.getWidth(display) + 8;
                     int cardWidth = Math.min(maxWidth, naturalWidth);
                     if (x > startX && x + cardWidth > endX) {
                        x = startX;
                        y += 16;
                     }

                     boolean hovered = mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + 13;
                     int border = disabled ? -11974327 : (hovered ? -7703658 : -10135953);
                     int background = disabled ? -1339348181 : (hovered ? -532925628 : -802281165);
                     int textColor = disabled ? -8947849 : -1712918;
                     context.fill(x, y, x + cardWidth, y + 13, border);
                     context.fill(x + 1, y + 1, x + cardWidth - 1, y + 13 - 1, background);
                     context.drawText(this.textRenderer, this.textRenderer.trimToWidth(display, Math.max(1, cardWidth - 8)), x + 4, y + 2, textColor, false);
                     if (hovered && naturalWidth > cardWidth) {
                        truncatedHoveredTag = display;
                     }

                     x += cardWidth + 4;
                  }
               }

               if (truncatedHoveredTag != null) {
                  context.drawTooltip(this.textRenderer, Text.literal(truncatedHoveredTag), mouseX, mouseY);
               }

               return y + 13;
            }

            public boolean mouseClicked(Click click, boolean doubled) {
               if (this.row instanceof NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedPackHeaderRow headerRow
                  && click.button() == 0
                  && this.onTogglePackExpanded != null
                  && !this.enabledButton.isMouseOver(click.x(), click.y())) {
                  this.onTogglePackExpanded.accept(headerRow.packId());
                  return true;
               } else {
                  return super.mouseClicked(click, doubled);
               }
            }

            private void renderPackHeader(
               DrawContext context,
               int mouseX,
               int mouseY,
               float delta,
               int x,
               int y,
               int right,
               NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedPackHeaderRow headerRow
            ) {
               this.enabledButton.setMessage(Text.literal(headerRow.disabled() ? "OFF" : "ON"));
               this.enabledButton.active = this.editableSupplier.getAsBoolean();
               this.enabledButton.setX(right - 44);
               this.enabledButton.setY(y + 4);
               this.enabledButton.render(context, mouseX, mouseY, delta);
               int iconSize = 24;
               int iconX = x;
               int iconY = y + 1;
               context.fill(iconX - 1, iconY - 1, iconX + iconSize + 1, iconY + iconSize + 1, -14671840);
               if (headerRow.iconTexture() != null) {
                  context.drawTexture(RenderPipelines.GUI_TEXTURED, headerRow.iconTexture(), iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
               } else {
                  context.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, -12829636);
                  context.drawTextWithShadow(this.textRenderer, "NoN", iconX + 2, iconY + 8, -4671304);
               }

               int textX = iconX + iconSize + 7;
               int textRight = right - 48;
               String disabledBadge = headerRow.disabledAnimations() > 0
                  ? "["
                     + Text.translatable("config.needsofnature.loaded_animations.disabled_count", new Object[]{headerRow.disabledAnimations()}).getString()
                     + "]"
                  : "";
               int disabledBadgeWidth = disabledBadge.isEmpty() ? 0 : this.textRenderer.getWidth(disabledBadge);
               int badgeRight = textRight;
               int disabledBadgeX = badgeRight - disabledBadgeWidth;
               if (!disabledBadge.isEmpty()) {
                  badgeRight = disabledBadgeX - 6;
               }

               int titleRight = badgeRight;
               int titleWidth = Math.max(20, titleRight - textX);
               int subtitleWidth = Math.max(20, textRight - textX);
               int titleColor = headerRow.disabled() ? -7829368 : -1;
               String title = headerRow.packName()
                  + " ("
                  + headerRow.visibleAnimations()
                  + "/"
                  + headerRow.totalAnimations()
                  + ")"
                  + (headerRow.expanded() ? "" : " [collapsed]");
               context.drawTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(title, titleWidth), textX, y + 2, titleColor);
               if (!disabledBadge.isEmpty()) {
                  context.drawTextWithShadow(this.textRenderer, disabledBadge, disabledBadgeX, y + 2, headerRow.disabled() ? -7702680 : -2778520);
               }

               String subtitle = "author: " + headerRow.packAuthor() + " | id: " + headerRow.packId();
               context.drawTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(subtitle, subtitleWidth), textX, y + 13, -6645094);
            }

            public List<? extends Selectable> selectableChildren() {
               return List.copyOf(this.widgets);
            }

            public void forEachChild(Consumer<ClickableWidget> consumer) {
               this.widgets.forEach(consumer);
            }

            public List<? extends Element> children() {
               return List.copyOf(this.widgets);
            }
         }
      }

      private interface LoadedListRow {
         String key();
      }

      private record LoadedPackHeaderRow(
         String key,
         String packId,
         String resourcePackId,
         String packName,
         String packAuthor,
         int visibleAnimations,
         int totalAnimations,
         int disabledAnimations,
         boolean disabled,
         boolean expanded,
         @Nullable Identifier iconTexture
      ) implements NonModMenuDebugScreens.LoadedAnimationsScreen.LoadedListRow {
      }

      private record ModelCoverage(boolean found, String missingDetail, List<String> localModelDetails) {
         private static NonModMenuDebugScreens.LoadedAnimationsScreen.ModelCoverage present(List<String> localModelDetails) {
            return new NonModMenuDebugScreens.LoadedAnimationsScreen.ModelCoverage(true, "", localModelDetails);
         }

         private static NonModMenuDebugScreens.LoadedAnimationsScreen.ModelCoverage missingCoverage(String detail, List<String> localModelDetails) {
            return new NonModMenuDebugScreens.LoadedAnimationsScreen.ModelCoverage(false, detail, localModelDetails);
         }
      }

      private record ModelProvider(Identifier root, String label) {
      }

      private record ResourceCheck(
         int animationTotal,
         int animationFound,
         int modelTotal,
         int modelFound,
         int modelUnchecked,
         List<String> missingAnimationDetails,
         List<String> missingModelDetails,
         List<String> localModelDetails
      ) {
         private NonModMenuDebugScreens.LoadedAnimationsScreen.ResourceStatus status() {
            boolean animationsOk = this.animationTotal == 0 || this.animationFound == this.animationTotal;
            boolean modelsOk = this.modelTotal == 0 || this.modelFound == this.modelTotal;
            if (animationsOk && modelsOk) {
               return NonModMenuDebugScreens.LoadedAnimationsScreen.ResourceStatus.OK;
            }

            boolean anyFound = this.animationFound > 0 || this.modelFound > 0;
            boolean anyChecked = this.animationTotal > 0 || this.modelTotal > 0;
            return anyChecked && !anyFound
               ? NonModMenuDebugScreens.LoadedAnimationsScreen.ResourceStatus.MISSING
               : NonModMenuDebugScreens.LoadedAnimationsScreen.ResourceStatus.PARTIAL;
         }

         private boolean hasMissingDetails() {
            return this.missingAnimationDetails != null && !this.missingAnimationDetails.isEmpty()
               || this.missingModelDetails != null && !this.missingModelDetails.isEmpty();
         }

         private int totalMissingCount() {
            int anim = this.missingAnimationDetails == null ? 0 : this.missingAnimationDetails.size();
            int model = this.missingModelDetails == null ? 0 : this.missingModelDetails.size();
            return anim + model;
         }

         private List<String> missingLinesStructured() {
            if (!this.hasMissingDetails()) {
               return List.of();
            }

            List<String> lines = new ArrayList<>();
            lines.add("missing:");
            if (this.missingAnimationDetails != null && !this.missingAnimationDetails.isEmpty()) {
               lines.add("anim:");
               addMissingLines(lines, this.missingAnimationDetails, 3);
            } else {
               lines.add("anim: none");
            }

            if (this.missingModelDetails != null && !this.missingModelDetails.isEmpty()) {
               lines.add("model:");
               addMissingLines(lines, this.missingModelDetails, 3);
            } else {
               lines.add("model: none");
            }

            return List.copyOf(lines);
         }

         private static void addMissingLines(List<String> out, List<String> values, int maxEntries) {
            if (values != null && !values.isEmpty()) {
               int safeMax = Math.max(1, maxEntries);
               int take = Math.min(safeMax, values.size());

               for (int i = 0; i < take; i++) {
                  out.add(values.get(i));
               }

               int remaining = values.size() - take;
               if (remaining > 0) {
                  out.add("+" + remaining + " more");
               }
            }
         }
      }

      private enum ResourceStatus {
         OK("config.needsofnature.loaded_animations.status.ok", -11141291),
         PARTIAL("config.needsofnature.loaded_animations.status.partial", -8090),
         MISSING("config.needsofnature.loaded_animations.status.missing", -43691);

         private final String translationKey;
         private final int color;

         ResourceStatus(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
         }

         private int sortRank() {
            return switch (this) {
               case OK -> 2;
               case PARTIAL -> 1;
               case MISSING -> 0;
            };
         }
      }

      private enum SortMode {
         ID,
         STATUS,
         MISSING,
         ENTITIES;
      }

      private enum StatusFilter {
         ALL,
         MISSING,
         PARTIAL,
         OK;
      }
   }

   static class OffspringCountConfigScreen extends Screen {
      private final Screen parent;
      private final NonConfig config;
      private boolean serverConfigEditable;
      private Map<String, NonConfig.EntityProfile> appliedMap;
      private NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList offspringList;
      private final List<NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry> rows = new ArrayList<>();
      private ButtonWidget applyButton;
      private ButtonWidget doneButton;

      protected OffspringCountConfigScreen(Screen parent, NonConfig config) {
         super(Text.translatable("config.needsofnature.offspring_count_title"));
         this.parent = parent;
         this.config = config;
         this.serverConfigEditable = NonModMenuScreens.canEditServerGameplaySettings();
         this.appliedMap = new LinkedHashMap<>(config.getEntityProfilesByEntity());
      }

      protected void init() {
         this.serverConfigEditable = NonModMenuScreens.canEditServerGameplaySettings();
         List<NonModMenuDebugScreens.OffspringCountConfigScreen.RowData> preserved = this.snapshotRows();
         if (preserved == null) {
            preserved = new ArrayList<>();

            for (Entry<String, NonConfig.EntityProfile> entry : NonEntityProfiles.resolveEffectiveProfilesMap().entrySet()) {
               NonConfig.EntityProfile profile = entry.getValue() == null
                  ? NonEntityProfiles.resolve(Identifier.tryParse(entry.getKey())).toConfigProfile()
                  : entry.getValue().sanitized();
               NonConfig.EntityProfile configOverride = this.config.getEntityProfilesByEntity().get(entry.getKey());
               preserved.add(
                  new NonModMenuDebugScreens.OffspringCountConfigScreen.RowData(
                     entry.getKey(),
                     configOverride != null && configOverride.pregnancyChancePercent() != null ? String.valueOf(configOverride.pregnancyChancePercent()) : "",
                     String.valueOf(profile.offspringMin() == null ? 1 : profile.offspringMin()),
                     String.valueOf(profile.offspringMax() == null ? 1 : profile.offspringMax()),
                     profile.birthEntity() == null ? entry.getKey() : profile.birthEntity(),
                     profile.birthMode() == null ? "direct" : profile.birthMode(),
                     profile.egg() == null ? NonConfig.EggProfile.defaults() : profile.egg()
                  )
               );
            }
         }

         int listTop = 44;
         int bottomArea = 64;
         int listHeight = Math.max(0, this.height - listTop - bottomArea);
         this.offspringList = new NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList(this.client, this.width, listHeight, listTop);
         this.addDrawableChild(this.offspringList);
         this.rows.clear();

         for (NonModMenuDebugScreens.OffspringCountConfigScreen.RowData data : preserved) {
            this.addRow(data.id(), data.chance(), data.min(), data.max(), data.birthEntity(), data.birthMode(), data.eggProfile());
         }

         if (this.rows.isEmpty()) {
            this.addRow("", "", "1", "1", "", "direct", NonConfig.EggProfile.defaults());
         }

         int centerX = this.width / 2;
         ButtonWidget addButton = ButtonWidget.builder(Text.translatable("config.needsofnature.offspring_count_add"), button -> {
            this.addRow("", "", "1", "1", "", "direct", NonConfig.EggProfile.defaults());
            this.updateApplyButton();
         }).dimensions(centerX - 100, this.height - 52, 96, 20).build();
         addButton.active = this.serverConfigEditable;
         NonModMenuScreens.setTooltip(addButton, "config.needsofnature.tooltip.offspring_count_add");
         this.addDrawableChild(addButton);
         ButtonWidget resetDefaultsButton = ButtonWidget.builder(
               Text.translatable("config.needsofnature.offspring_count_reset"),
               button -> MinecraftClient.getInstance()
                  .setScreen(new NonModMenuDebugScreens.OffspringCountConfigScreen.ResetOffspringCountConfirmScreen(this, () -> {
                     this.applyDefaultsToRows();
                     this.updateApplyButton();
                     MinecraftClient.getInstance().setScreen(this);
                  }))
            )
            .dimensions(centerX + 4, this.height - 52, 96, 20)
            .build();
         resetDefaultsButton.active = this.serverConfigEditable;
         NonModMenuScreens.setTooltip(resetDefaultsButton, "config.needsofnature.tooltip.offspring_count_reset");
         this.addDrawableChild(resetDefaultsButton);
         this.applyButton = ButtonWidget.builder(Text.translatable("config.needsofnature.apply"), button -> {
            if (this.serverConfigEditable) {
               Map<String, NonConfig.EntityProfile> next = this.collectMapFromFields();
               this.config.setEntityProfilesByEntity(next);
               NonModMenuScreens.saveServerConfig(this.config);
               this.appliedMap = new LinkedHashMap<>(this.config.getEntityProfilesByEntity());
               this.updateApplyButton();
            }
         }).dimensions(centerX - 100, this.height - 28, 96, 20).build();
         NonModMenuScreens.setTooltip(this.applyButton, "config.needsofnature.tooltip.apply");
         this.addDrawableChild(this.applyButton);
         this.doneButton = ButtonWidget.builder(Text.translatable("config.needsofnature.done"), button -> MinecraftClient.getInstance().setScreen(this.parent))
            .dimensions(centerX + 4, this.height - 28, 96, 20)
            .build();
         NonModMenuScreens.setTooltip(this.doneButton, "config.needsofnature.tooltip.done_unsaved");
         this.addDrawableChild(this.doneButton);
         this.updateApplyButton();
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         this.updateApplyButton();
         super.render(context, mouseX, mouseY, deltaTicks);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 16777215);
         this.drawHeader(context);
      }

      public void close() {
         MinecraftClient.getInstance().setScreen(this.parent);
      }

      private void drawHeader(DrawContext context) {
         if (this.offspringList != null) {
            int rowX = this.offspringList.getRowLeft();
            int rowWidth = this.offspringList.getRowWidth();
            int removeW = 20;
            int chanceW = 44;
            int minW = 34;
            int maxW = 34;
            int birthW = 140;
            int modeW = 54;
            int eggW = 42;
            int idW = Math.max(105, rowWidth - removeW - chanceW - minW - maxW - birthW - modeW - eggW - 48);
            int chanceX = rowX + idW + 6;
            int minX = chanceX + chanceW + 6;
            int maxX = minX + minW + 6;
            int birthX = maxX + maxW + 6;
            int modeX = birthX + birthW + 6;
            int eggX = modeX + modeW + 6;
            int y = 34;
            int color = -4342339;
            context.drawTextWithShadow(this.textRenderer, Text.translatable("config.needsofnature.offspring_count_column.entity"), rowX + 4, y, color);
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.translatable("config.needsofnature.entity_profile_column.chance"), chanceX + chanceW / 2, y, color
            );
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.translatable("config.needsofnature.offspring_count_column.min"), minX + minW / 2, y, color
            );
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.translatable("config.needsofnature.offspring_count_column.max"), maxX + maxW / 2, y, color
            );
            context.drawTextWithShadow(this.textRenderer, Text.translatable("config.needsofnature.entity_profile_column.birth_entity"), birthX + 4, y, color);
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.translatable("config.needsofnature.entity_profile_column.birth_mode"), modeX + modeW / 2, y, color
            );
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.translatable("config.needsofnature.entity_profile_column.egg"), eggX + eggW / 2, y, color
            );
         }
      }

      private void addRow(
         String idText, String chanceText, String minText, String maxText, String birthEntityText, String birthModeText, NonConfig.EggProfile eggProfile
      ) {
         NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry[] holder = new NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry[1];
         NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry entry = new NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry(
            this.textRenderer, idText, chanceText, minText, maxText, birthEntityText, birthModeText, eggProfile, () -> {
               NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry current = holder[0];
               if (current != null) {
                  this.rows.removeIf(row -> row == current);
                  this.offspringList.removeEntryRow(current);
                  this.updateApplyButton();
               }
            }, this::chanceSuggestionFor, this::openEggSettings
         );
         holder[0] = entry;
         entry.setOnChange(this::updateApplyButton);
         entry.setEditable(this.serverConfigEditable);
         this.rows.add(entry);
         this.offspringList.addEntryRow(entry);
      }

      private void updateApplyButton() {
         if (this.applyButton != null) {
            if (!this.serverConfigEditable) {
               this.applyButton.active = false;
               if (this.doneButton != null) {
                  this.doneButton.setMessage(Text.translatable("config.needsofnature.done"));
               }
            } else {
               Map<String, NonConfig.EntityProfile> current = this.collectMapFromFields();
               boolean hasUnsavedChanges = !current.equals(this.appliedMap);
               this.applyButton.active = hasUnsavedChanges;
               if (this.doneButton != null) {
                  this.doneButton.setMessage(Text.translatable(hasUnsavedChanges ? "config.needsofnature.done_unsaved" : "config.needsofnature.done"));
               }
            }
         }
      }

      private void applyDefaultsToRows() {
         if (this.offspringList != null) {
            for (NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry entry : new ArrayList<>(this.rows)) {
               this.offspringList.removeEntryRow(entry);
            }
         }

         this.rows.clear();

         for (Entry<String, NonConfig.EntityProfile> entry : NonEntityProfiles.resolveDefaultProfilesMap().entrySet()) {
            NonConfig.EntityProfile profile = entry.getValue();
            this.addRow(
               entry.getKey(),
               "",
               String.valueOf(profile.offspringMin() == null ? 1 : profile.offspringMin()),
               String.valueOf(profile.offspringMax() == null ? 1 : profile.offspringMax()),
               profile.birthEntity() == null ? entry.getKey() : profile.birthEntity(),
               profile.birthMode() == null ? "direct" : profile.birthMode(),
               profile.egg() == null ? NonConfig.EggProfile.defaults() : profile.egg()
            );
         }

         if (this.rows.isEmpty()) {
            this.addRow("", "", "1", "1", "", "direct", NonConfig.EggProfile.defaults());
         }
      }

      private Map<String, NonConfig.EntityProfile> collectMapFromFields() {
         Map<String, NonConfig.EntityProfile> map = new LinkedHashMap<>();

         for (NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry row : this.rows) {
            if (row != null) {
               String id = row.getIdText();
               if (id != null) {
                  id = id.trim();
                  if (!id.isEmpty()) {
                     Identifier parsedId = Identifier.tryParse(id);
                     if (parsedId != null) {
                        String rawChance = row.getChanceText() == null ? "" : row.getChanceText().trim();
                        int min = this.parseInt(row.getMinText(), 1);
                        int max = this.parseInt(row.getMaxText(), min);
                        if (max < min) {
                           max = min;
                        }

                        String birthEntity = row.getBirthEntityText();
                        if (birthEntity != null && !birthEntity.trim().isEmpty()) {
                           birthEntity = birthEntity.trim();
                        } else {
                           birthEntity = id;
                        }

                        if (Identifier.tryParse(birthEntity) == null) {
                           birthEntity = id;
                        }

                        String birthMode = NonConfig.normalizeBirthMode(row.getBirthModeText());
                        if (birthMode == null) {
                           birthMode = "direct";
                        }

                        NonConfig.EggProfile eggProfile = row.getEggProfile();
                        NonConfig.EntityProfile existing = this.config.getEntityProfilesByEntity().get(id);
                        NonConfig.EntityProfile defaultProfile = NonEntityProfiles.resolveDefaultProfile(parsedId).toConfigProfile();
                        int defaultChance = defaultProfile.pregnancyChancePercent() == null
                           ? this.config.getPregnancyChancePercent()
                           : defaultProfile.pregnancyChancePercent();
                        Integer chanceOverride = rawChance.isEmpty() ? null : Math.max(0, Math.min(100, this.parseInt(rawChance, defaultChance)));
                        Integer minOverride = min == defaultProfile.offspringMin() ? null : min;
                        Integer maxOverride = max == defaultProfile.offspringMax() ? null : max;
                        String birthEntityOverride = birthEntity.equals(defaultProfile.birthEntity()) ? null : birthEntity;
                        String birthModeOverride = birthMode.equals(defaultProfile.birthMode()) ? null : birthMode;
                        NonConfig.EggProfile defaultEgg = defaultProfile.egg() == null ? NonConfig.EggProfile.defaults() : defaultProfile.egg();
                        NonConfig.EggProfile eggOverride = Objects.equals(eggProfile, defaultEgg) ? null : eggProfile;
                        NonConfig.EntityProfile next = new NonConfig.EntityProfile(
                              chanceOverride != null && chanceOverride != defaultChance ? chanceOverride : null,
                              minOverride,
                              maxOverride,
                              birthEntityOverride,
                              birthModeOverride,
                              existing == null ? null : existing.genderSpawn(),
                              eggOverride,
                              existing == null ? null : existing.lookAtNearbyAnimations(),
                              existing == null ? null : existing.multiActorJoinChancePercent(),
                              existing == null ? null : existing.energyGainMultiplier()
                           )
                           .sanitized();
                        if (!next.isEmpty()) {
                           map.put(id, next);
                        }
                     }
                  }
               }
            }
         }

         return map;
      }

      private String chanceSuggestionFor(String idText) {
         Identifier id = idText != null && !idText.isBlank() ? Identifier.tryParse(idText.trim()) : null;
         int defaultChance = id == null ? this.config.getPregnancyChancePercent() : NonEntityProfiles.resolveDefaultProfile(id).pregnancyChancePercent();
         return String.valueOf(defaultChance);
      }

      private List<NonModMenuDebugScreens.OffspringCountConfigScreen.RowData> snapshotRows() {
         if (this.rows.isEmpty()) {
            return null;
         }

         List<NonModMenuDebugScreens.OffspringCountConfigScreen.RowData> data = new ArrayList<>();

         for (NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry row : this.rows) {
            data.add(
               new NonModMenuDebugScreens.OffspringCountConfigScreen.RowData(
                  row.getIdText(),
                  row.getChanceText(),
                  row.getMinText(),
                  row.getMaxText(),
                  row.getBirthEntityText(),
                  row.getBirthModeText(),
                  row.getEggProfile()
               )
            );
         }

         return data;
      }

      private int parseInt(String value, int fallback) {
         if (value == null) {
            return fallback;
         }

         try {
            return Integer.parseInt(value.trim());
         } catch (NumberFormatException e) {
            return fallback;
         }
      }

      private void openEggSettings(NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry row) {
         if (row != null) {
            Identifier id = Identifier.tryParse(row.getIdText() == null ? "" : row.getIdText().trim());
            NonConfig.EggProfile defaultEgg = id == null ? NonConfig.EggProfile.defaults() : NonEntityProfiles.resolveDefaultProfile(id).eggProfile();
            MinecraftClient.getInstance()
               .setScreen(new NonModMenuDebugScreens.OffspringCountConfigScreen.EggProfileSettingsScreen(this, row.getEggProfile(), defaultEgg, updated -> {
                  row.setEggProfile(updated);
                  this.updateApplyButton();
               }, this.serverConfigEditable));
         }
      }

      private static final class EggProfileSettingsScreen extends Screen {
         private final Screen parent;
         private final NonConfig.EggProfile initialProfile;
         private final NonConfig.EggProfile defaultProfile;
         private final Consumer<NonConfig.EggProfile> onApply;
         private final boolean editable;
         private TextFieldWidget startSizeField;
         private TextFieldWidget endSizeField;
         private TextFieldWidget textureField;
         private TextFieldWidget healthField;

         private EggProfileSettingsScreen(
            Screen parent, NonConfig.EggProfile initialProfile, NonConfig.EggProfile defaultProfile, Consumer<NonConfig.EggProfile> onApply, boolean editable
         ) {
            super(Text.translatable("config.needsofnature.egg_profile_title"));
            this.parent = parent;
            this.initialProfile = initialProfile == null ? NonConfig.EggProfile.defaults() : initialProfile.sanitized();
            this.defaultProfile = defaultProfile == null ? NonConfig.EggProfile.defaults() : defaultProfile.sanitized();
            this.onApply = onApply;
            this.editable = editable;
         }

         protected void init() {
            int centerX = this.width / 2;
            int labelX = centerX - 180;
            int fieldX = centerX - 40;
            int y = 58;
            int textureFieldW = Math.min(460, Math.max(220, this.width - fieldX - 20));
            this.startSizeField = this.newTextField(70, formatFloat(valueOrDefault(this.initialProfile.startSize(), this.defaultProfile.startSize(), 0.5F)));
            this.endSizeField = this.newTextField(70, formatFloat(valueOrDefault(this.initialProfile.endSize(), this.defaultProfile.endSize(), 1.0F)));
            this.textureField = this.newTextField(textureFieldW, valueOrDefault(this.initialProfile.texture(), this.defaultProfile.texture(), ""));
            this.healthField = this.newTextField(70, formatFloat(valueOrDefault(this.initialProfile.health(), this.defaultProfile.health(), 2.0F)));
            this.addField(this.startSizeField, fieldX, y, "config.needsofnature.tooltip.egg_profile_start_size");
            y += 28;
            this.addField(this.endSizeField, fieldX, y, "config.needsofnature.tooltip.egg_profile_end_size");
            y += 28;
            this.addField(this.textureField, fieldX, y, "config.needsofnature.tooltip.egg_profile_texture");
            y += 28;
            this.addField(this.healthField, fieldX, y, "config.needsofnature.tooltip.egg_profile_health");
            ButtonWidget resetButton = ButtonWidget.builder(Text.translatable("config.needsofnature.reset"), button -> {
               this.startSizeField.setText(formatFloat(valueOrDefault(this.defaultProfile.startSize(), null, 0.5F)));
               this.endSizeField.setText(formatFloat(valueOrDefault(this.defaultProfile.endSize(), null, 1.0F)));
               this.textureField.setText(valueOrDefault(this.defaultProfile.texture(), null, ""));
               this.healthField.setText(formatFloat(valueOrDefault(this.defaultProfile.health(), null, 2.0F)));
            }).dimensions(centerX - 154, this.height - 52, 96, 20).build();
            resetButton.active = this.editable;
            NonModMenuScreens.setTooltip(resetButton, "config.needsofnature.tooltip.egg_profile_reset");
            this.addDrawableChild(resetButton);
            ButtonWidget applyButton = ButtonWidget.builder(Text.translatable("config.needsofnature.apply"), button -> {
               if (this.editable) {
                  if (this.onApply != null) {
                     this.onApply.accept(this.collectProfile());
                  }

                  MinecraftClient.getInstance().setScreen(this.parent);
               }
            }).dimensions(centerX - 50, this.height - 52, 96, 20).build();
            applyButton.active = this.editable;
            NonModMenuScreens.setTooltip(applyButton, "config.needsofnature.tooltip.apply");
            this.addDrawableChild(applyButton);
            ButtonWidget cancelButton = ButtonWidget.builder(Text.translatable("gui.cancel"), button -> MinecraftClient.getInstance().setScreen(this.parent))
               .dimensions(centerX + 54, this.height - 52, 96, 20)
               .build();
            this.addDrawableChild(cancelButton);
            this.startSizeField.setEditable(this.editable);
            this.endSizeField.setEditable(this.editable);
            this.textureField.setEditable(this.editable);
            this.healthField.setEditable(this.editable);
         }

         public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
            super.render(context, mouseX, mouseY, deltaTicks);
            int centerX = this.width / 2;
            int labelX = centerX - 180;
            int y = 63;
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 24, -1);
            this.drawLabel(context, "config.needsofnature.egg_profile_start_size", labelX, y);
            y += 28;
            this.drawLabel(context, "config.needsofnature.egg_profile_end_size", labelX, y);
            y += 28;
            this.drawLabel(context, "config.needsofnature.egg_profile_texture", labelX, y);
            y += 28;
            this.drawLabel(context, "config.needsofnature.egg_profile_health", labelX, y);
         }

         public void close() {
            MinecraftClient.getInstance().setScreen(this.parent);
         }

         private void addField(TextFieldWidget field, int x, int y, String tooltipKey) {
            field.setX(x);
            field.setY(y);
            NonModMenuScreens.setTooltip(field, tooltipKey);
            this.addDrawableChild(field);
         }

         private TextFieldWidget newTextField(int width, String value) {
            TextFieldWidget field = new TextFieldWidget(this.textRenderer, 0, 0, width, 20, Text.literal(""));
            field.setMaxLength(128);
            field.setText(value == null ? "" : value);
            return field;
         }

         private void drawLabel(DrawContext context, String key, int x, int y) {
            context.drawTextWithShadow(this.textRenderer, Text.translatable(key), x, y, -2039584);
         }

         private NonConfig.EggProfile collectProfile() {
            NonConfig.EggProfile raw = new NonConfig.EggProfile(
               parseFloat(this.startSizeField.getText(), this.defaultProfile.startSize(), 0.5F),
               parseFloat(this.endSizeField.getText(), this.defaultProfile.endSize(), 1.0F),
               this.textureField.getText() != null && !this.textureField.getText().trim().isEmpty() ? this.textureField.getText().trim() : null,
               parseFloat(this.healthField.getText(), this.defaultProfile.health(), 2.0F)
            );
            return raw.resolvedOver(this.defaultProfile);
         }

         private static Float parseFloat(String raw, Float fallback, float hardFallback) {
            if (raw != null && !raw.trim().isEmpty()) {
               try {
                  return Float.parseFloat(raw.trim());
               } catch (NumberFormatException e) {
                  return fallback == null ? hardFallback : fallback;
               }
            } else {
               return fallback == null ? hardFallback : fallback;
            }
         }

         private static float valueOrDefault(Float value, Float fallback, float hardFallback) {
            if (value != null) {
               return value;
            } else {
               return fallback != null ? fallback : hardFallback;
            }
         }

         private static String valueOrDefault(String value, String fallback, String hardFallback) {
            if (value != null && !value.isBlank()) {
               return value;
            } else {
               return fallback != null && !fallback.isBlank() ? fallback : hardFallback;
            }
         }

         private static String formatFloat(float value) {
            String formatted = String.format(Locale.ROOT, "%.3f", value);

            while (formatted.contains(".") && formatted.endsWith("0")) {
               formatted = formatted.substring(0, formatted.length() - 1);
            }

            if (formatted.endsWith(".")) {
               formatted = formatted.substring(0, formatted.length() - 1);
            }

            return formatted;
         }
      }

      private static final class OffspringCountList
         extends ElementListWidget<NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry> {
         private OffspringCountList(MinecraftClient client, int width, int height, int top) {
            super(client, width, height, top, 24);
            this.centerListVertically = false;
         }

         private void addEntryRow(NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry entry) {
            super.addEntry(entry);
         }

         private void removeEntryRow(NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry entry) {
            super.removeEntry(entry);
         }

         public int getRowWidth() {
            return 620;
         }

         public int getRowLeft() {
            return (this.getWidth() - this.getRowWidth()) / 2;
         }

         private static final class OffspringEntry
            extends net.minecraft.client.gui.widget.ElementListWidget.Entry<NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry> {
            private final TextFieldWidget idField;
            private final TextFieldWidget chanceField;
            private final TextFieldWidget minField;
            private final TextFieldWidget maxField;
            private final TextFieldWidget birthEntityField;
            private final TextFieldWidget birthModeField;
            private final ButtonWidget eggButton;
            private final ButtonWidget removeButton;
            private final List<ClickableWidget> widgets;
            private final Function<String, String> chanceSuggestionSupplier;
            private NonConfig.EggProfile eggProfile;
            private boolean editable;
            private Runnable onChange;

            private OffspringEntry(
               TextRenderer textRenderer,
               String idText,
               String chanceText,
               String minText,
               String maxText,
               String birthEntityText,
               String birthModeText,
               NonConfig.EggProfile eggProfile,
               Runnable onRemove,
               Function<String, String> chanceSuggestionSupplier,
               Consumer<NonModMenuDebugScreens.OffspringCountConfigScreen.OffspringCountList.OffspringEntry> onEggSettings
            ) {
               this.chanceSuggestionSupplier = chanceSuggestionSupplier;
               this.eggProfile = eggProfile == null ? NonConfig.EggProfile.defaults() : eggProfile.sanitized();
               this.idField = new TextFieldWidget(textRenderer, 0, 0, 120, 20, Text.literal(""));
               this.idField.setText(idText == null ? "" : idText);
               this.idField.setMaxLength(80);
               NonModMenuScreens.setTooltip(this.idField, "config.needsofnature.tooltip.offspring_count_entry_entity");
               this.chanceField = new TextFieldWidget(textRenderer, 0, 0, 44, 20, Text.literal(""));
               this.chanceField.setText(chanceText == null ? "" : chanceText);
               this.chanceField.setMaxLength(3);
               this.updateChanceSuggestion();
               NonModMenuScreens.setTooltip(this.chanceField, "config.needsofnature.tooltip.entity_profile_entry_chance");
               this.minField = new TextFieldWidget(textRenderer, 0, 0, 34, 20, Text.literal(""));
               this.minField.setText(minText == null ? "1" : minText);
               this.minField.setMaxLength(3);
               NonModMenuScreens.setTooltip(this.minField, "config.needsofnature.tooltip.offspring_count_entry_min");
               this.maxField = new TextFieldWidget(textRenderer, 0, 0, 34, 20, Text.literal(""));
               this.maxField.setText(maxText == null ? "1" : maxText);
               this.maxField.setMaxLength(3);
               NonModMenuScreens.setTooltip(this.maxField, "config.needsofnature.tooltip.offspring_count_entry_max");
               this.birthEntityField = new TextFieldWidget(textRenderer, 0, 0, 140, 20, Text.literal(""));
               this.birthEntityField.setText(birthEntityText == null ? "" : birthEntityText);
               this.birthEntityField.setMaxLength(80);
               NonModMenuScreens.setTooltip(this.birthEntityField, "config.needsofnature.tooltip.entity_profile_entry_birth_entity");
               this.birthModeField = new TextFieldWidget(textRenderer, 0, 0, 54, 20, Text.literal(""));
               this.birthModeField.setText(birthModeText == null ? "direct" : birthModeText);
               this.birthModeField.setMaxLength(6);
               NonModMenuScreens.setTooltip(this.birthModeField, "config.needsofnature.tooltip.entity_profile_entry_birth_mode");
               this.eggButton = ButtonWidget.builder(Text.translatable("config.needsofnature.entity_profile_egg_button"), button -> {
                  if (onEggSettings != null) {
                     onEggSettings.accept(this);
                  }
               }).dimensions(0, 0, 42, 20).build();
               NonModMenuScreens.setTooltip(this.eggButton, "config.needsofnature.tooltip.entity_profile_entry_egg");
               this.removeButton = ButtonWidget.builder(Text.translatable("config.needsofnature.remove_icon"), button -> {
                  if (onRemove != null) {
                     onRemove.run();
                  }
               }).dimensions(0, 0, 20, 20).build();
               NonModMenuScreens.setTooltip(this.removeButton, "config.needsofnature.tooltip.remove");
               this.widgets = new ArrayList<>(8);
               this.widgets.add(this.idField);
               this.widgets.add(this.chanceField);
               this.widgets.add(this.minField);
               this.widgets.add(this.maxField);
               this.widgets.add(this.birthEntityField);
               this.widgets.add(this.birthModeField);
               this.widgets.add(this.eggButton);
               this.widgets.add(this.removeButton);
               this.idField.setChangedListener(ignored -> {
                  this.updateChanceSuggestion();
                  if (this.onChange != null) {
                     this.onChange.run();
                  }
               });
               this.chanceField.setChangedListener(ignored -> {
                  if (this.onChange != null) {
                     this.onChange.run();
                  }
               });
               this.minField.setChangedListener(ignored -> {
                  if (this.onChange != null) {
                     this.onChange.run();
                  }
               });
               this.maxField.setChangedListener(ignored -> {
                  if (this.onChange != null) {
                     this.onChange.run();
                  }
               });
               this.birthEntityField.setChangedListener(ignored -> {
                  if (this.onChange != null) {
                     this.onChange.run();
                  }
               });
               this.birthModeField.setChangedListener(ignored -> {
                  this.updateEggButtonActive();
                  if (this.onChange != null) {
                     this.onChange.run();
                  }
               });
               this.updateEggButtonActive();
            }

            private void setOnChange(Runnable onChange) {
               this.onChange = onChange;
            }

            private void updateChanceSuggestion() {
               String suggestion = this.chanceSuggestionSupplier == null ? null : this.chanceSuggestionSupplier.apply(this.idField.getText());
               this.chanceField.setPlaceholder(suggestion != null && !suggestion.isBlank() ? Text.literal(suggestion) : null);
            }

            private void setEditable(boolean editable) {
               this.editable = editable;
               this.idField.setEditable(editable);
               this.chanceField.setEditable(editable);
               this.minField.setEditable(editable);
               this.maxField.setEditable(editable);
               this.birthEntityField.setEditable(editable);
               this.birthModeField.setEditable(editable);
               this.updateEggButtonActive();
               this.removeButton.active = editable;
            }

            private void updateEggButtonActive() {
               this.eggButton.active = this.editable && "egg".equals(NonConfig.normalizeBirthMode(this.birthModeField.getText()));
            }

            private String getIdText() {
               return this.idField.getText();
            }

            private String getMinText() {
               return this.minField.getText();
            }

            private String getMaxText() {
               return this.maxField.getText();
            }

            private String getChanceText() {
               return this.chanceField.getText();
            }

            private String getBirthEntityText() {
               return this.birthEntityField.getText();
            }

            private String getBirthModeText() {
               return this.birthModeField.getText();
            }

            private NonConfig.EggProfile getEggProfile() {
               return this.eggProfile == null ? NonConfig.EggProfile.defaults() : this.eggProfile.sanitized();
            }

            private void setEggProfile(NonConfig.EggProfile eggProfile) {
               this.eggProfile = eggProfile == null ? NonConfig.EggProfile.defaults() : eggProfile.sanitized();
               if (this.onChange != null) {
                  this.onChange.run();
               }
            }

            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
               int rowX = this.getContentX();
               int rowY = this.getContentY();
               int rowWidth = this.getContentWidth();
               int widgetY = rowY + 2;
               int removeW = this.removeButton.getWidth();
               int chanceW = this.chanceField.getWidth();
               int minW = this.minField.getWidth();
               int maxW = this.maxField.getWidth();
               int birthW = this.birthEntityField.getWidth();
               int modeW = this.birthModeField.getWidth();
               int eggW = this.eggButton.getWidth();
               int idW = Math.max(105, rowWidth - removeW - chanceW - minW - maxW - birthW - modeW - eggW - 48);
               int chanceX = rowX + idW + 6;
               int minX = chanceX + chanceW + 6;
               int maxX = minX + minW + 6;
               int birthX = maxX + maxW + 6;
               int modeX = birthX + birthW + 6;
               int eggX = modeX + modeW + 6;
               int removeX = eggX + eggW + 6;
               this.idField.setX(rowX);
               this.idField.setY(widgetY);
               this.idField.setWidth(idW);
               this.chanceField.setX(chanceX);
               this.chanceField.setY(widgetY);
               this.minField.setX(minX);
               this.minField.setY(widgetY);
               this.maxField.setX(maxX);
               this.maxField.setY(widgetY);
               this.birthEntityField.setX(birthX);
               this.birthEntityField.setY(widgetY);
               this.birthModeField.setX(modeX);
               this.birthModeField.setY(widgetY);
               this.eggButton.setX(eggX);
               this.eggButton.setY(widgetY);
               this.removeButton.setX(removeX);
               this.removeButton.setY(widgetY);

               for (ClickableWidget widget : this.widgets) {
                  widget.render(context, mouseX, mouseY, deltaTicks);
               }
            }

            public List<? extends Selectable> selectableChildren() {
               return List.copyOf(this.widgets);
            }

            public void forEachChild(Consumer<ClickableWidget> consumer) {
               this.widgets.forEach(consumer);
            }

            public List<? extends Element> children() {
               return List.copyOf(this.widgets);
            }
         }
      }

      private static final class ResetOffspringCountConfirmScreen extends Screen {
         private final Screen parent;
         private final Runnable onConfirm;

         private ResetOffspringCountConfirmScreen(Screen parent, Runnable onConfirm) {
            super(Text.translatable("config.needsofnature.offspring_count_reset_title"));
            this.parent = parent;
            this.onConfirm = onConfirm;
         }

         protected void init() {
            int centerX = this.width / 2;
            int buttonY = this.height / 2 + 20;
            ButtonWidget yesButton = ButtonWidget.builder(Text.translatable("gui.yes"), button -> {
               if (this.onConfirm != null) {
                  this.onConfirm.run();
               }

               MinecraftClient.getInstance().setScreen(this.parent);
            }).dimensions(centerX - 100, buttonY, 96, 20).build();
            this.addDrawableChild(yesButton);
            ButtonWidget noButton = ButtonWidget.builder(Text.translatable("gui.cancel"), button -> MinecraftClient.getInstance().setScreen(this.parent))
               .dimensions(centerX + 4, buttonY, 96, 20)
               .build();
            this.addDrawableChild(noButton);
         }

         public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
            super.render(context, mouseX, mouseY, deltaTicks);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 20, -1);
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.translatable("config.needsofnature.offspring_count_reset_body"), this.width / 2, this.height / 2 - 4, -4144960
            );
         }
      }

      private record RowData(String id, String chance, String min, String max, String birthEntity, String birthMode, NonConfig.EggProfile eggProfile) {
      }
   }
}
