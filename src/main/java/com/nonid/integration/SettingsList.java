package com.nonid.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.ElementListWidget.Entry;
import net.minecraft.text.Text;

final class SettingsList extends ElementListWidget<SettingsList.RowEntry> {
   SettingsList(MinecraftClient client, int width, int height, int top) {
      super(client, width, height, top, 24);
      this.centerListVertically = false;
   }

   void addEntryRow(SettingsList.RowEntry entry) {
      super.addEntry(entry);
   }

   public int getRowWidth() {
      return 440;
   }

   public int getRowLeft() {
      return (this.getWidth() - this.getRowWidth()) / 2;
   }

   static final class RowEntry extends Entry<SettingsList.RowEntry> {
      private final TextRenderer textRenderer;
      private final Text label;
      private final TextWidget labelWidget;
      private final ClickableWidget primary;
      private final ClickableWidget reset;
      private final SettingsList.RowEntry.RowType type;
      private final List<ClickableWidget> widgets;

      private RowEntry(
         TextRenderer textRenderer, Text label, ClickableWidget primary, ClickableWidget reset, SettingsList.RowEntry.RowType type, Tooltip tooltip
      ) {
         this.textRenderer = textRenderer;
         this.label = label;
         this.labelWidget = label == null ? null : new TextWidget(0, 0, 0, 20, label, textRenderer);
         this.primary = primary;
         this.reset = reset;
         this.type = type;
         this.widgets = new ArrayList<>(2);
         if (primary != null) {
            this.widgets.add(primary);
         }

         if (reset != null) {
            this.widgets.add(reset);
         }

         if (tooltip != null) {
            if (this.labelWidget != null) {
               NonModMenuScreens.setTooltip(this.labelWidget, tooltip);
            }

            if (this.primary != null) {
               NonModMenuScreens.setTooltip(this.primary, tooltip);
            }
         }
      }

      static SettingsList.RowEntry labeledField(TextRenderer textRenderer, Text label, ClickableWidget field, ClickableWidget reset, Tooltip tooltip) {
         return new SettingsList.RowEntry(textRenderer, label, field, reset, SettingsList.RowEntry.RowType.LABELED_FIELD, tooltip);
      }

      static SettingsList.RowEntry groupedField(TextRenderer textRenderer, Text label, Tooltip tooltip, ClickableWidget... fields) {
         SettingsList.RowEntry entry = new SettingsList.RowEntry(textRenderer, label, null, null, SettingsList.RowEntry.RowType.GROUPED_FIELD, tooltip);
         if (fields != null) {
            for (ClickableWidget field : fields) {
               if (field != null) {
                  entry.widgets.add(field);
               }
            }
         }

         return entry;
      }

      static SettingsList.RowEntry buttonWithReset(TextRenderer textRenderer, ClickableWidget button, Tooltip tooltip) {
         return new SettingsList.RowEntry(textRenderer, null, button, null, SettingsList.RowEntry.RowType.BUTTON_RESET, tooltip);
      }

      static SettingsList.RowEntry sectionHeader(TextRenderer textRenderer, Text label) {
         return new SettingsList.RowEntry(textRenderer, label, null, null, SettingsList.RowEntry.RowType.SECTION_HEADER, null);
      }

      public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
         int rowX = this.getContentX();
         int rowY = this.getContentY();
         int rowWidth = this.getContentWidth();
         int widgetY = rowY + 2;
         if (this.type == SettingsList.RowEntry.RowType.SECTION_HEADER) {
            if (this.label != null) {
               int bandY = rowY + 3;
               int bandH = 18;
               int centerX = rowX + rowWidth / 2;
               int textY = rowY + 8;
               int lineY = rowY + 12;
               int textW = this.textRenderer.getWidth(this.label);
               int leftEnd = centerX - textW / 2 - 10;
               int rightStart = centerX + textW / 2 + 10;
               context.fill(rowX + 2, bandY, rowX + rowWidth - 2, bandY + bandH, 855638016);
               context.fill(rowX + 8, lineY, Math.max(rowX + 8, leftEnd), lineY + 1, 1717986918);
               context.fill(Math.min(rowX + rowWidth - 8, rightStart), lineY, rowX + rowWidth - 8, lineY + 1, 1717986918);
               context.drawCenteredTextWithShadow(this.textRenderer, this.label, centerX, textY, -3158065);
            }
         } else {
            if (this.type == SettingsList.RowEntry.RowType.GROUPED_FIELD) {
               int totalW = 0;

               for (ClickableWidget widget : this.widgets) {
                  totalW += widget.getWidth();
               }

               totalW += Math.max(0, this.widgets.size() - 1) * 4;
               int x = rowX + rowWidth - totalW;
               if (this.labelWidget != null) {
                  int labelWidth = Math.max(20, x - rowX - 8);
                  this.labelWidget.setX(rowX + 4);
                  this.labelWidget.setY(rowY + 6);
                  this.labelWidget.setWidth(labelWidth);
               }

               for (ClickableWidget widget : this.widgets) {
                  widget.setX(x);
                  widget.setY(widgetY);
                  x += widget.getWidth() + 4;
               }
            } else if (this.primary != null) {
               if (this.type == SettingsList.RowEntry.RowType.LABELED_FIELD) {
                  int resetW = this.reset == null ? 0 : this.reset.getWidth();
                  int fieldW = this.primary.getWidth();
                  int resetX = rowX + rowWidth - resetW;
                  int fieldX = resetX - 6 - fieldW;
                  this.primary.setX(fieldX);
                  this.primary.setY(widgetY);
                  if (this.reset != null) {
                     this.reset.setX(resetX);
                     this.reset.setY(widgetY);
                  }

                  if (this.labelWidget != null) {
                     int labelWidth = Math.max(20, fieldX - rowX - 8);
                     this.labelWidget.setX(rowX + 4);
                     this.labelWidget.setY(rowY + 6);
                     this.labelWidget.setWidth(labelWidth);
                  }
               } else {
                  int resetW = this.reset == null ? 0 : this.reset.getWidth();
                  int buttonW = Math.max(20, rowWidth - resetW - 6);
                  this.primary.setX(rowX);
                  this.primary.setY(widgetY);
                  this.primary.setWidth(buttonW);
                  if (this.reset != null) {
                     this.reset.setX(rowX + rowWidth - resetW);
                     this.reset.setY(widgetY);
                  }
               }
            }

            if (this.labelWidget != null) {
               this.labelWidget.render(context, mouseX, mouseY, deltaTicks);
            }

            for (ClickableWidget widget : this.widgets) {
               widget.render(context, mouseX, mouseY, deltaTicks);
            }
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

      private enum RowType {
         LABELED_FIELD,
         GROUPED_FIELD,
         BUTTON_RESET,
         SECTION_HEADER;
      }
   }
}
