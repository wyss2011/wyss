package com.nonid.integration;

import com.nonid.NonConfig;
import com.nonid.client.NonHudOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

final class NonModMenuHudConfigScreen {
   private NonModMenuHudConfigScreen() {
   }

   static class UiConfigScreen extends Screen {
      private final Screen parent;
      private final NonConfig config;
      private final NonConfig defaults = new NonConfig();
      private static final int UI_MIN = -999;
      private static final int UI_MAX = 999;
      private int energyX;
      private int energyY;
      private int energyHeartX;
      private int energyHeartY;
      private int attackX;
      private int attackY;
      private int promptX;
      private int promptY;
      private int liquidX;
      private int liquidY;
      private boolean energyVisible;
      private boolean energyHeartVisible;
      private boolean attackVisible;
      private boolean promptVisible;
      private boolean liquidVisible;
      private int appliedEnergyX;
      private int appliedEnergyY;
      private int appliedEnergyHeartX;
      private int appliedEnergyHeartY;
      private int appliedAttackX;
      private int appliedAttackY;
      private int appliedPromptX;
      private int appliedPromptY;
      private int appliedLiquidX;
      private int appliedLiquidY;
      private boolean appliedEnergyVisible;
      private boolean appliedEnergyHeartVisible;
      private boolean appliedAttackVisible;
      private boolean appliedPromptVisible;
      private boolean appliedLiquidVisible;
      private NonModMenuHudConfigScreen.UiConfigScreen.HudElement selectedElement = NonModMenuHudConfigScreen.UiConfigScreen.HudElement.LIQUID;
      private NonModMenuHudConfigScreen.UiConfigScreen.HudElement draggedElement = null;
      private double dragStartMouseX;
      private double dragStartMouseY;
      private int dragStartOffsetX;
      private int dragStartOffsetY;
      private ButtonWidget saveButton;
      private ButtonWidget cancelButton;
      private ButtonWidget toggleSelectedVisibilityButton;
      private ButtonWidget resetSelectedButton;
      private ButtonWidget resetAllButton;
      private ButtonWidget toolbarVisibilityButton;
      private boolean toolbarVisible = true;
      private NonHudOverlay.UiPreviewBounds lastBounds = NonHudOverlay.UiPreviewBounds.empty();

      protected UiConfigScreen(Screen parent, NonConfig config) {
         super(Text.translatable("config.needsofnature.ui_title"));
         this.parent = parent;
         this.config = config;
         this.energyX = config.getUiEnergyOffsetX();
         this.energyY = config.getUiEnergyOffsetY();
         this.energyHeartX = config.getUiEnergyHeartOffsetX();
         this.energyHeartY = config.getUiEnergyHeartOffsetY();
         this.attackX = config.getUiAttackOffsetX();
         this.attackY = config.getUiAttackOffsetY();
         this.promptX = config.getUiPromptOffsetX();
         this.promptY = config.getUiPromptOffsetY();
         this.liquidX = config.getUiLiquidOffsetX();
         this.liquidY = config.getUiLiquidOffsetY();
         this.energyVisible = config.isUiEnergyVisible();
         this.energyHeartVisible = config.isUiEnergyHeartVisible();
         this.attackVisible = config.isUiAttackVisible();
         this.promptVisible = config.isUiPromptVisible();
         this.liquidVisible = config.isUiLiquidVisible();
         this.appliedEnergyX = this.energyX;
         this.appliedEnergyY = this.energyY;
         this.appliedEnergyHeartX = this.energyHeartX;
         this.appliedEnergyHeartY = this.energyHeartY;
         this.appliedAttackX = this.attackX;
         this.appliedAttackY = this.attackY;
         this.appliedPromptX = this.promptX;
         this.appliedPromptY = this.promptY;
         this.appliedLiquidX = this.liquidX;
         this.appliedLiquidY = this.liquidY;
         this.appliedEnergyVisible = this.energyVisible;
         this.appliedEnergyHeartVisible = this.energyHeartVisible;
         this.appliedAttackVisible = this.attackVisible;
         this.appliedPromptVisible = this.promptVisible;
         this.appliedLiquidVisible = this.liquidVisible;
      }

      protected void init() {
         NonHudOverlay.setUiPreviewEnabled(true);
         this.updatePreviewOffsets();
         int buttonY = 8;
         this.saveButton = ButtonWidget.builder(Text.translatable("config.needsofnature.done"), button -> {
            this.saveOffsets();
            NonHudOverlay.setUiPreviewEnabled(false);
            MinecraftClient.getInstance().setScreen(this.parent);
         }).dimensions(0, buttonY, 48, 18).build();
         NonModMenuScreens.setTooltip(this.saveButton, "config.needsofnature.tooltip.done_save");
         this.addDrawableChild(this.saveButton);
         this.toggleSelectedVisibilityButton = ButtonWidget.builder(this.toggleSelectedVisibilityLabel(), button -> {
            this.setVisible(this.selectedElement, !this.isVisible(this.selectedElement));
            this.updatePreviewOffsets();
            this.updateButtons();
         }).dimensions(0, buttonY, 86, 18).build();
         NonModMenuScreens.setTooltip(this.toggleSelectedVisibilityButton, "config.needsofnature.tooltip.ui_toggle_selected_visibility");
         this.addDrawableChild(this.toggleSelectedVisibilityButton);
         this.resetSelectedButton = ButtonWidget.builder(Text.translatable("config.needsofnature.ui_reset_selected"), button -> {
            this.resetElement(this.selectedElement);
            this.updatePreviewOffsets();
            this.updateButtons();
         }).dimensions(0, buttonY, 86, 18).build();
         this.addDrawableChild(this.resetSelectedButton);
         this.resetAllButton = ButtonWidget.builder(Text.translatable("config.needsofnature.ui_reset_all"), button -> {
            this.resetElement(NonModMenuHudConfigScreen.UiConfigScreen.HudElement.ENERGY);
            this.resetElement(NonModMenuHudConfigScreen.UiConfigScreen.HudElement.ENERGY_HEART);
            this.resetElement(NonModMenuHudConfigScreen.UiConfigScreen.HudElement.ATTACK);
            this.resetElement(NonModMenuHudConfigScreen.UiConfigScreen.HudElement.PROMPT);
            this.resetElement(NonModMenuHudConfigScreen.UiConfigScreen.HudElement.LIQUID);
            this.updatePreviewOffsets();
            this.updateButtons();
         }).dimensions(0, buttonY, 62, 18).build();
         this.addDrawableChild(this.resetAllButton);
         this.cancelButton = ButtonWidget.builder(Text.translatable("gui.cancel"), button -> {
            this.resetToApplied();
            NonHudOverlay.setUiPreviewEnabled(false);
            MinecraftClient.getInstance().setScreen(this.parent);
         }).dimensions(0, buttonY, 54, 18).build();
         NonModMenuScreens.setTooltip(this.cancelButton, "config.needsofnature.tooltip.done_unsaved");
         this.addDrawableChild(this.cancelButton);
         this.toolbarVisibilityButton = ButtonWidget.builder(this.toolbarVisibilityLabel(), button -> {
            this.toolbarVisible = !this.toolbarVisible;
            this.updateButtons();
         }).dimensions(0, buttonY, 18, 18).build();
         NonModMenuScreens.setTooltip(this.toolbarVisibilityButton, "config.needsofnature.tooltip.ui_toolbar_visibility");
         this.addDrawableChild(this.toolbarVisibilityButton);
         this.updateButtons();
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         this.updatePreviewOffsets();
         super.render(context, mouseX, mouseY, deltaTicks);
         this.lastBounds = NonHudOverlay.getUiPreviewBounds(context);
         this.drawDragOverlays(context, mouseX, mouseY);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 16777215);
         Text help = Text.translatable("config.needsofnature.ui_drag_help");
         context.drawCenteredTextWithShadow(this.textRenderer, help, this.width / 2, 24, -2039584);
         Text selected = this.isVisible(this.selectedElement)
            ? Text.translatable("config.needsofnature.ui_selected", new Object[]{Text.translatable(this.selectedElement.labelKey)})
            : Text.translatable("config.needsofnature.ui_selected_hidden", new Object[]{Text.translatable(this.selectedElement.labelKey)});
         context.drawCenteredTextWithShadow(this.textRenderer, selected, this.width / 2, 36, this.selectedElement.color);
         this.updateButtons();
      }

      public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      }

      public boolean mouseClicked(Click click, boolean doubled) {
         if (super.mouseClicked(click, doubled)) {
            return true;
         }

         if (click.button() != 0) {
            return false;
         }

         NonModMenuHudConfigScreen.UiConfigScreen.HudElement hit = this.hitTest(click.x(), click.y());
         if (hit == null) {
            return false;
         }

         this.selectedElement = hit;
         this.draggedElement = hit;
         this.dragStartMouseX = click.x();
         this.dragStartMouseY = click.y();
         this.dragStartOffsetX = this.offsetX(hit);
         this.dragStartOffsetY = this.offsetY(hit);
         this.updateButtons();
         return true;
      }

      public boolean mouseDragged(Click click, double offsetX, double offsetY) {
         if (this.draggedElement != null && click.button() == 0) {
            int nextX = this.clampUiOffset(this.dragStartOffsetX + (int)Math.round(click.x() - this.dragStartMouseX));
            int nextY = this.clampUiOffset(this.dragStartOffsetY + (int)Math.round(click.y() - this.dragStartMouseY));
            this.setOffset(this.draggedElement, nextX, nextY);
            this.updatePreviewOffsets();
            this.updateButtons();
            return true;
         } else {
            return super.mouseDragged(click, offsetX, offsetY);
         }
      }

      public boolean mouseReleased(Click click) {
         if (click.button() == 0 && this.draggedElement != null) {
            this.draggedElement = null;
            this.updateButtons();
            return true;
         } else {
            return super.mouseReleased(click);
         }
      }

      public boolean keyPressed(KeyInput input) {
         int step = (input.modifiers() & 1) != 0 ? 5 : 1;
         int dx = 0;
         int dy = 0;
         int keyCode = input.key();
         if (keyCode == 263) {
            dx = -step;
         }

         if (keyCode == 262) {
            dx = step;
         }

         if (keyCode == 265) {
            dy = -step;
         }

         if (keyCode == 264) {
            dy = step;
         }

         if (dx == 0 && dy == 0) {
            return super.keyPressed(input);
         }

         this.setOffset(
            this.selectedElement, this.clampUiOffset(this.offsetX(this.selectedElement) + dx), this.clampUiOffset(this.offsetY(this.selectedElement) + dy)
         );
         this.updatePreviewOffsets();
         this.updateButtons();
         return true;
      }

      public void close() {
         this.resetToApplied();
         NonHudOverlay.setUiPreviewEnabled(false);
         MinecraftClient.getInstance().setScreen(this.parent);
      }

      private void drawDragOverlays(DrawContext context, int mouseX, int mouseY) {
         this.drawElementBox(context, this.lastBounds.energy(), NonModMenuHudConfigScreen.UiConfigScreen.HudElement.ENERGY, mouseX, mouseY);
         this.drawElementBox(context, this.lastBounds.energyHeart(), NonModMenuHudConfigScreen.UiConfigScreen.HudElement.ENERGY_HEART, mouseX, mouseY);
         this.drawElementBox(context, this.lastBounds.attack(), NonModMenuHudConfigScreen.UiConfigScreen.HudElement.ATTACK, mouseX, mouseY);
         this.drawElementBox(context, this.lastBounds.prompt(), NonModMenuHudConfigScreen.UiConfigScreen.HudElement.PROMPT, mouseX, mouseY);
         this.drawElementBox(context, this.lastBounds.liquid(), NonModMenuHudConfigScreen.UiConfigScreen.HudElement.LIQUID, mouseX, mouseY);
      }

      private void drawElementBox(
         DrawContext context, NonHudOverlay.Rect rawRect, NonModMenuHudConfigScreen.UiConfigScreen.HudElement element, int mouseX, int mouseY
      ) {
         NonHudOverlay.Rect rect = rawRect.padded(4);
         boolean hovered = rect.contains(mouseX, mouseY);
         boolean active = element == this.selectedElement || element == this.draggedElement || hovered;
         boolean visible = this.isVisible(element);
         if (active || !visible) {
            int color = visible ? element.color : -8355712;
            int fill = color & 16777215 | (!hovered && element != this.draggedElement ? (visible ? 402653184 : 570425344) : 855638016);
            context.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), fill);
            this.drawOutline(context, rect, color);
            Text label = visible
               ? Text.translatable(element.labelKey)
               : Text.translatable("config.needsofnature.ui_hidden_label", new Object[]{Text.translatable(element.labelKey)});
            context.drawTextWithShadow(this.textRenderer, label, rect.x(), Math.max(0, rect.y() - 10), color);
         }
      }

      private void drawOutline(DrawContext context, NonHudOverlay.Rect rect, int color) {
         int x1 = rect.x();
         int y1 = rect.y();
         int x2 = rect.x() + rect.width();
         int y2 = rect.y() + rect.height();
         context.fill(x1, y1, x2, y1 + 1, color);
         context.fill(x1, y2 - 1, x2, y2, color);
         context.fill(x1, y1, x1 + 1, y2, color);
         context.fill(x2 - 1, y1, x2, y2, color);
      }

      private NonModMenuHudConfigScreen.UiConfigScreen.HudElement hitTest(double mouseX, double mouseY) {
         if (this.lastBounds.liquid().padded(6).contains(mouseX, mouseY)) {
            return NonModMenuHudConfigScreen.UiConfigScreen.HudElement.LIQUID;
         } else if (this.lastBounds.prompt().padded(6).contains(mouseX, mouseY)) {
            return NonModMenuHudConfigScreen.UiConfigScreen.HudElement.PROMPT;
         } else if (this.lastBounds.attack().padded(6).contains(mouseX, mouseY)) {
            return NonModMenuHudConfigScreen.UiConfigScreen.HudElement.ATTACK;
         } else if (this.lastBounds.energyHeart().padded(6).contains(mouseX, mouseY)) {
            return NonModMenuHudConfigScreen.UiConfigScreen.HudElement.ENERGY_HEART;
         } else {
            return this.lastBounds.energy().padded(6).contains(mouseX, mouseY) ? NonModMenuHudConfigScreen.UiConfigScreen.HudElement.ENERGY : null;
         }
      }

      private int offsetX(NonModMenuHudConfigScreen.UiConfigScreen.HudElement element) {
         return switch (element) {
            case ENERGY -> this.energyX;
            case ENERGY_HEART -> this.energyHeartX;
            case ATTACK -> this.attackX;
            case PROMPT -> this.promptX;
            case LIQUID -> this.liquidX;
         };
      }

      private int offsetY(NonModMenuHudConfigScreen.UiConfigScreen.HudElement element) {
         return switch (element) {
            case ENERGY -> this.energyY;
            case ENERGY_HEART -> this.energyHeartY;
            case ATTACK -> this.attackY;
            case PROMPT -> this.promptY;
            case LIQUID -> this.liquidY;
         };
      }

      private void setOffset(NonModMenuHudConfigScreen.UiConfigScreen.HudElement element, int x, int y) {
         switch (element) {
            case ENERGY:
               this.energyX = x;
               this.energyY = y;
               break;
            case ENERGY_HEART:
               this.energyHeartX = x;
               this.energyHeartY = y;
               break;
            case ATTACK:
               this.attackX = x;
               this.attackY = y;
               break;
            case PROMPT:
               this.promptX = x;
               this.promptY = y;
               break;
            case LIQUID:
               this.liquidX = x;
               this.liquidY = y;
         }
      }

      private boolean isVisible(NonModMenuHudConfigScreen.UiConfigScreen.HudElement element) {
         return switch (element) {
            case ENERGY -> this.energyVisible;
            case ENERGY_HEART -> this.energyHeartVisible;
            case ATTACK -> this.attackVisible;
            case PROMPT -> this.promptVisible;
            case LIQUID -> this.liquidVisible;
         };
      }

      private void setVisible(NonModMenuHudConfigScreen.UiConfigScreen.HudElement element, boolean visible) {
         switch (element) {
            case ENERGY:
               this.energyVisible = visible;
               break;
            case ENERGY_HEART:
               this.energyHeartVisible = visible;
               break;
            case ATTACK:
               this.attackVisible = visible;
               break;
            case PROMPT:
               this.promptVisible = visible;
               break;
            case LIQUID:
               this.liquidVisible = visible;
         }
      }

      private void resetElement(NonModMenuHudConfigScreen.UiConfigScreen.HudElement element) {
         switch (element) {
            case ENERGY:
               this.setOffset(element, this.defaults.getUiEnergyOffsetX(), this.defaults.getUiEnergyOffsetY());
               this.setVisible(element, this.defaults.isUiEnergyVisible());
               break;
            case ENERGY_HEART:
               this.setOffset(element, this.defaults.getUiEnergyHeartOffsetX(), this.defaults.getUiEnergyHeartOffsetY());
               this.setVisible(element, this.defaults.isUiEnergyHeartVisible());
               break;
            case ATTACK:
               this.setOffset(element, this.defaults.getUiAttackOffsetX(), this.defaults.getUiAttackOffsetY());
               this.setVisible(element, this.defaults.isUiAttackVisible());
               break;
            case PROMPT:
               this.setOffset(element, this.defaults.getUiPromptOffsetX(), this.defaults.getUiPromptOffsetY());
               this.setVisible(element, this.defaults.isUiPromptVisible());
               break;
            case LIQUID:
               this.setOffset(element, this.defaults.getUiLiquidOffsetX(), this.defaults.getUiLiquidOffsetY());
               this.setVisible(element, this.defaults.isUiLiquidVisible());
         }
      }

      private void saveOffsets() {
         this.config.setUiEnergyOffsetX(this.energyX);
         this.config.setUiEnergyOffsetY(this.energyY);
         this.config.setUiEnergyHeartOffsetX(this.energyHeartX);
         this.config.setUiEnergyHeartOffsetY(this.energyHeartY);
         this.config.setUiAttackOffsetX(this.attackX);
         this.config.setUiAttackOffsetY(this.attackY);
         this.config.setUiPromptOffsetX(this.promptX);
         this.config.setUiPromptOffsetY(this.promptY);
         this.config.setUiLiquidOffsetX(this.liquidX);
         this.config.setUiLiquidOffsetY(this.liquidY);
         this.config.setUiEnergyVisible(this.energyVisible);
         this.config.setUiEnergyHeartVisible(this.energyHeartVisible);
         this.config.setUiAttackVisible(this.attackVisible);
         this.config.setUiPromptVisible(this.promptVisible);
         this.config.setUiLiquidVisible(this.liquidVisible);
         this.config.save();
         this.appliedEnergyX = this.energyX;
         this.appliedEnergyY = this.energyY;
         this.appliedEnergyHeartX = this.energyHeartX;
         this.appliedEnergyHeartY = this.energyHeartY;
         this.appliedAttackX = this.attackX;
         this.appliedAttackY = this.attackY;
         this.appliedPromptX = this.promptX;
         this.appliedPromptY = this.promptY;
         this.appliedLiquidX = this.liquidX;
         this.appliedLiquidY = this.liquidY;
         this.appliedEnergyVisible = this.energyVisible;
         this.appliedEnergyHeartVisible = this.energyHeartVisible;
         this.appliedAttackVisible = this.attackVisible;
         this.appliedPromptVisible = this.promptVisible;
         this.appliedLiquidVisible = this.liquidVisible;
      }

      private void resetToApplied() {
         this.energyX = this.appliedEnergyX;
         this.energyY = this.appliedEnergyY;
         this.energyHeartX = this.appliedEnergyHeartX;
         this.energyHeartY = this.appliedEnergyHeartY;
         this.attackX = this.appliedAttackX;
         this.attackY = this.appliedAttackY;
         this.promptX = this.appliedPromptX;
         this.promptY = this.appliedPromptY;
         this.liquidX = this.appliedLiquidX;
         this.liquidY = this.appliedLiquidY;
         this.energyVisible = this.appliedEnergyVisible;
         this.energyHeartVisible = this.appliedEnergyHeartVisible;
         this.attackVisible = this.appliedAttackVisible;
         this.promptVisible = this.appliedPromptVisible;
         this.liquidVisible = this.appliedLiquidVisible;
         this.updatePreviewOffsets();
      }

      private void updatePreviewOffsets() {
         NonHudOverlay.setUiPreviewOffsets(
            this.energyX,
            this.energyY,
            this.attackX,
            this.attackY,
            this.promptX,
            this.promptY,
            this.liquidX,
            this.liquidY,
            this.energyHeartX,
            this.energyHeartY,
            this.energyVisible,
            this.energyHeartVisible,
            this.attackVisible,
            this.promptVisible,
            this.liquidVisible
         );
      }

      private boolean hasPendingUiChanges() {
         return this.energyX != this.appliedEnergyX
            || this.energyY != this.appliedEnergyY
            || this.energyHeartX != this.appliedEnergyHeartX
            || this.energyHeartY != this.appliedEnergyHeartY
            || this.attackX != this.appliedAttackX
            || this.attackY != this.appliedAttackY
            || this.promptX != this.appliedPromptX
            || this.promptY != this.appliedPromptY
            || this.liquidX != this.appliedLiquidX
            || this.liquidY != this.appliedLiquidY
            || this.energyVisible != this.appliedEnergyVisible
            || this.energyHeartVisible != this.appliedEnergyHeartVisible
            || this.attackVisible != this.appliedAttackVisible
            || this.promptVisible != this.appliedPromptVisible
            || this.liquidVisible != this.appliedLiquidVisible;
      }

      private Text toolbarVisibilityLabel() {
         return Text.literal(this.toolbarVisible ? "H" : "S");
      }

      private Text toggleSelectedVisibilityLabel() {
         return Text.translatable(this.isVisible(this.selectedElement) ? "config.needsofnature.ui_hide_selected" : "config.needsofnature.ui_show_selected");
      }

      private void updateButtons() {
         this.updateToolbarLayout();
         boolean pending = this.hasPendingUiChanges();
         if (this.saveButton != null) {
            this.saveButton.active = pending;
         }

         if (this.toggleSelectedVisibilityButton != null) {
            this.toggleSelectedVisibilityButton.setMessage(this.toggleSelectedVisibilityLabel());
         }

         if (this.resetSelectedButton != null) {
            this.resetSelectedButton.active = this.offsetX(this.selectedElement) != this.defaultX(this.selectedElement)
               || this.offsetY(this.selectedElement) != this.defaultY(this.selectedElement)
               || this.isVisible(this.selectedElement) != this.defaultVisible(this.selectedElement);
         }

         if (this.resetAllButton != null) {
            this.resetAllButton.active = this.energyX != this.defaults.getUiEnergyOffsetX()
               || this.energyY != this.defaults.getUiEnergyOffsetY()
               || this.energyHeartX != this.defaults.getUiEnergyHeartOffsetX()
               || this.energyHeartY != this.defaults.getUiEnergyHeartOffsetY()
               || this.attackX != this.defaults.getUiAttackOffsetX()
               || this.attackY != this.defaults.getUiAttackOffsetY()
               || this.promptX != this.defaults.getUiPromptOffsetX()
               || this.promptY != this.defaults.getUiPromptOffsetY()
               || this.liquidX != this.defaults.getUiLiquidOffsetX()
               || this.liquidY != this.defaults.getUiLiquidOffsetY()
               || this.energyVisible != this.defaults.isUiEnergyVisible()
               || this.energyHeartVisible != this.defaults.isUiEnergyHeartVisible()
               || this.attackVisible != this.defaults.isUiAttackVisible()
               || this.promptVisible != this.defaults.isUiPromptVisible()
               || this.liquidVisible != this.defaults.isUiLiquidVisible();
         }

         if (this.saveButton != null) {
            this.saveButton.visible = this.toolbarVisible;
         }

         if (this.cancelButton != null) {
            this.cancelButton.visible = this.toolbarVisible;
         }

         if (this.toggleSelectedVisibilityButton != null) {
            this.toggleSelectedVisibilityButton.visible = this.toolbarVisible;
         }

         if (this.resetSelectedButton != null) {
            this.resetSelectedButton.visible = this.toolbarVisible;
         }

         if (this.resetAllButton != null) {
            this.resetAllButton.visible = this.toolbarVisible;
         }

         if (this.toolbarVisibilityButton != null) {
            this.toolbarVisibilityButton.visible = true;
            this.toolbarVisibilityButton.setMessage(this.toolbarVisibilityLabel());
         }
      }

      private void updateToolbarLayout() {
         if (this.toolbarVisibilityButton != null) {
            int y = 8;
            int gap = 3;
            int eyeW = this.toolbarVisibilityButton.getWidth();
            int right = this.width - 8;
            this.toolbarVisibilityButton.setPosition(right - eyeW, y);
            if (this.toolbarVisible) {
               int nextY = y + this.toolbarVisibilityButton.getHeight() + gap;
               if (this.toggleSelectedVisibilityButton != null) {
                  this.toggleSelectedVisibilityButton.setPosition(right - this.toggleSelectedVisibilityButton.getWidth(), nextY);
                  nextY += this.toggleSelectedVisibilityButton.getHeight() + gap;
               }

               if (this.resetSelectedButton != null) {
                  this.resetSelectedButton.setPosition(right - this.resetSelectedButton.getWidth(), nextY);
                  nextY += this.resetSelectedButton.getHeight() + gap;
               }

               if (this.resetAllButton != null) {
                  this.resetAllButton.setPosition(right - this.resetAllButton.getWidth(), nextY);
                  nextY += this.resetAllButton.getHeight() + gap;
               }

               if (this.saveButton != null) {
                  this.saveButton.setPosition(right - this.saveButton.getWidth(), nextY);
                  nextY += this.saveButton.getHeight() + gap;
               }

               if (this.cancelButton != null) {
                  this.cancelButton.setPosition(right - this.cancelButton.getWidth(), nextY);
               }
            }
         }
      }

      private int defaultX(NonModMenuHudConfigScreen.UiConfigScreen.HudElement element) {
         return switch (element) {
            case ENERGY -> this.defaults.getUiEnergyOffsetX();
            case ENERGY_HEART -> this.defaults.getUiEnergyHeartOffsetX();
            case ATTACK -> this.defaults.getUiAttackOffsetX();
            case PROMPT -> this.defaults.getUiPromptOffsetX();
            case LIQUID -> this.defaults.getUiLiquidOffsetX();
         };
      }

      private int defaultY(NonModMenuHudConfigScreen.UiConfigScreen.HudElement element) {
         return switch (element) {
            case ENERGY -> this.defaults.getUiEnergyOffsetY();
            case ENERGY_HEART -> this.defaults.getUiEnergyHeartOffsetY();
            case ATTACK -> this.defaults.getUiAttackOffsetY();
            case PROMPT -> this.defaults.getUiPromptOffsetY();
            case LIQUID -> this.defaults.getUiLiquidOffsetY();
         };
      }

      private boolean defaultVisible(NonModMenuHudConfigScreen.UiConfigScreen.HudElement element) {
         return switch (element) {
            case ENERGY -> this.defaults.isUiEnergyVisible();
            case ENERGY_HEART -> this.defaults.isUiEnergyHeartVisible();
            case ATTACK -> this.defaults.isUiAttackVisible();
            case PROMPT -> this.defaults.isUiPromptVisible();
            case LIQUID -> this.defaults.isUiLiquidVisible();
         };
      }

      private int clampUiOffset(int value) {
         return Math.max(-999, Math.min(999, value));
      }

      private enum HudElement {
         ENERGY("config.needsofnature.ui_energy", -8532993),
         ENERGY_HEART("config.needsofnature.ui_energy_heart", -33333),
         ATTACK("config.needsofnature.ui_attack", -38037),
         PROMPT("config.needsofnature.ui_prompt", -11930),
         LIQUID("config.needsofnature.ui_liquid", -10616907);

         private final String labelKey;
         private final int color;

         HudElement(String labelKey, int color) {
            this.labelKey = labelKey;
            this.color = color;
         }
      }
   }
}
