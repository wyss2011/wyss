package com.nonid.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.ElementListWidget.Entry;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

final class NonCreditsScreen extends Screen {
   private final Screen parent;
   private final NonCreditsData.Credits credits;

   NonCreditsScreen(Screen parent) {
      super(Text.translatable("config.needsofnature.credits_title"));
      this.parent = parent;
      this.credits = NonCreditsData.load();
   }

   protected void init() {
      int listTop = 42;
      int listHeight = Math.max(0, this.height - listTop - 40);
      NonCreditsScreen.CreditsList creditsList = new NonCreditsScreen.CreditsList(
         this.client, this.width, listHeight, listTop, this.textRenderer, url -> ConfirmLinkScreen.open(this, url)
      );
      creditsList.setCredits(this.credits);
      this.addDrawableChild(creditsList);
      this.addDrawableChild(
         ButtonWidget.builder(Text.translatable("config.needsofnature.done"), button -> MinecraftClient.getInstance().setScreen(this.parent))
            .dimensions(this.width / 2 - 100, this.height - 28, 200, 20)
            .build()
      );
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      super.render(context, mouseX, mouseY, deltaTicks);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, -1);
      context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("config.needsofnature.credits_intro"), this.width / 2, 24, -4671304);
   }

   public void close() {
      MinecraftClient.getInstance().setScreen(this.parent);
   }

   private record ContributorRow(NonCreditsData.Contributor contributor) implements NonCreditsScreen.CreditsRow {
   }

   private static final class CreditsList extends ElementListWidget<NonCreditsScreen.CreditsList.RowEntry> {
      private final TextRenderer textRenderer;
      private final Consumer<String> linkOpener;

      private CreditsList(MinecraftClient client, int width, int height, int top, TextRenderer textRenderer, Consumer<String> linkOpener) {
         super(client, width, height, top, 28);
         this.textRenderer = textRenderer;
         this.linkOpener = linkOpener;
         this.centerListVertically = false;
      }

      private void setCredits(NonCreditsData.Credits credits) {
         this.clearEntries();
         int contentWidth = Math.max(100, this.getRowWidth() - 16);
         if (credits.notice() != null) {
            this.addRow(new NonCreditsScreen.NoticeRow(credits.notice()), contentWidth);
         }

         for (NonCreditsData.Section section : credits.sections()) {
            this.addRow(new NonCreditsScreen.SectionRow(Text.literal(section.title())), contentWidth);

            for (NonCreditsData.Contributor contributor : section.contributors()) {
               this.addRow(new NonCreditsScreen.ContributorRow(contributor), contentWidth);
            }
         }

         if (!credits.resources().isEmpty()) {
            this.addRow(new NonCreditsScreen.SectionRow(Text.translatable("config.needsofnature.credits.resource_credits")), contentWidth);

            for (NonCreditsData.ResourceCredit resource : credits.resources()) {
               this.addRow(new NonCreditsScreen.ResourceRow(resource), contentWidth);
            }
         }

         if (!credits.projectLinks().isEmpty()) {
            this.addRow(new NonCreditsScreen.SectionRow(Text.translatable("config.needsofnature.credits.project_links")), contentWidth);
            this.addRow(new NonCreditsScreen.LinksRow(credits.projectLinks()), contentWidth);
         }
      }

      private void addRow(NonCreditsScreen.CreditsRow row, int contentWidth) {
         NonCreditsScreen.CreditsList.RowEntry entry = new NonCreditsScreen.CreditsList.RowEntry(row, this.textRenderer, contentWidth, this.linkOpener);
         this.addEntry(entry, entry.rowHeight());
      }

      public int getRowWidth() {
         return Math.min(520, Math.max(120, this.getWidth() - 32));
      }

      public int getRowLeft() {
         return (this.getWidth() - this.getRowWidth()) / 2;
      }

      protected int getScrollbarX() {
         return this.getWidth() - 8;
      }

      private static final class RowEntry extends Entry<NonCreditsScreen.CreditsList.RowEntry> {
         private final NonCreditsScreen.CreditsRow row;
         private final TextRenderer textRenderer;
         private final int rowHeight;
         private final List<ClickableWidget> widgets = new ArrayList<>();

         private RowEntry(NonCreditsScreen.CreditsRow row, TextRenderer textRenderer, int contentWidth, Consumer<String> linkOpener) {
            this.row = row;
            this.textRenderer = textRenderer;
            this.rowHeight = calculateHeight(row, textRenderer, contentWidth);
            if (row instanceof NonCreditsScreen.ResourceRow resourceRow) {
               NonCreditsData.ResourceCredit resource = resourceRow.resource();
               this.addLinkButton("config.needsofnature.credits.source", resource.sourceUrl(), linkOpener);
               this.addLinkButton("config.needsofnature.credits.license", resource.licenseUrl(), linkOpener);
            } else if (row instanceof NonCreditsScreen.LinksRow linksRow) {
               for (NonCreditsData.ProjectLink link : linksRow.links()) {
                  ButtonWidget button = ButtonWidget.builder(Text.literal(link.label()), ignored -> linkOpener.accept(link.url()))
                     .dimensions(0, 0, 180, 20)
                     .build();
                  NonModMenuScreens.setTooltip(button, NonModMenuScreens.tooltip(Text.literal(link.url())));
                  this.widgets.add(button);
               }
            }
         }

         private void addLinkButton(String translationKey, String url, Consumer<String> linkOpener) {
            if (url != null) {
               ButtonWidget button = ButtonWidget.builder(Text.translatable(translationKey), ignored -> linkOpener.accept(url))
                  .dimensions(0, 0, 90, 18)
                  .build();
               NonModMenuScreens.setTooltip(button, NonModMenuScreens.tooltip(Text.literal(url)));
               this.widgets.add(button);
            }
         }

         private static int calculateHeight(NonCreditsScreen.CreditsRow row, TextRenderer renderer, int contentWidth) {
            if (row instanceof NonCreditsScreen.NoticeRow noticeRow) {
               int lines = wrappedLineCount(renderer, noticeRow.text(), contentWidth - 16);
               return Math.max(34, 16 + lines * 10);
            } else if (row instanceof NonCreditsScreen.SectionRow) {
               return 30;
            } else if (row instanceof NonCreditsScreen.ContributorRow) {
               return 18;
            } else if (row instanceof NonCreditsScreen.ResourceRow resourceRow) {
               NonCreditsData.ResourceCredit resource = resourceRow.resource();
               int detailLines = resourceDetailLines(resource);
               String assets = joinedAssets(resource.assets());
               int assetLines = assets == null
                  ? 0
                  : renderer.wrapLines(Text.translatable("config.needsofnature.credits.assets_value", new Object[]{assets}), Math.max(20, contentWidth - 8))
                     .size();
               int buttonHeight = resource.sourceUrl() == null && resource.licenseUrl() == null ? 0 : 22;
               return 18 + detailLines * 10 + assetLines * 10 + buttonHeight + 6;
            } else {
               return row instanceof NonCreditsScreen.LinksRow linksRow ? Math.max(26, 6 + linksRow.links().size() * 24) : 28;
            }
         }

         private static int wrappedLineCount(TextRenderer renderer, String value, int width) {
            return value != null && !value.isBlank() ? renderer.wrapLines(Text.literal(value), Math.max(20, width)).size() : 0;
         }

         private static int resourceDetailLines(NonCreditsData.ResourceCredit resource) {
            int count = 0;
            if (resource.category() != null) {
               count++;
            }

            if (resource.creator() != null) {
               count++;
            }

            if (resource.source() != null) {
               count++;
            }

            if (resource.license() != null || resource.modified()) {
               count++;
            }

            return count;
         }

         private static String joinedAssets(List<String> assets) {
            return assets != null && !assets.isEmpty() ? String.join(", ", assets) : null;
         }

         public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            int x = this.getContentX() + 8;
            int y = this.getContentY() + 3;
            int width = this.getContentWidth() - 16;
            if (this.row instanceof NonCreditsScreen.NoticeRow noticeRow) {
               this.renderNotice(context, x, y, width, noticeRow.text());
            } else if (this.row instanceof NonCreditsScreen.SectionRow sectionRow) {
               this.renderSection(context, x, y, width, sectionRow.title());
            } else if (this.row instanceof NonCreditsScreen.ContributorRow contributorRow) {
               this.renderContributor(context, x, y, width, contributorRow.contributor());
            } else if (this.row instanceof NonCreditsScreen.ResourceRow resourceRow) {
               this.renderResource(context, mouseX, mouseY, deltaTicks, x, y, width, hovered, resourceRow.resource());
            } else if (this.row instanceof NonCreditsScreen.LinksRow) {
               this.renderProjectLinks(context, mouseX, mouseY, deltaTicks, x, y, width);
            }
         }

         private void renderNotice(DrawContext context, int x, int y, int width, String notice) {
            context.fill(x, y - 1, x + width, y + this.rowHeight - 5, 1144000528);
            int textY = y + 6;

            for (OrderedText line : this.textRenderer.wrapLines(Text.literal(notice), Math.max(20, width - 16))) {
               context.drawCenteredTextWithShadow(this.textRenderer, line, x + width / 2, textY, -20304);
               textY += 10;
            }
         }

         private void renderSection(DrawContext context, int x, int y, int width, Text title) {
            Text displayedTitle = Text.literal(this.textRenderer.trimToWidth(title.getString(), Math.max(20, width - 40)));
            int centerX = x + width / 2;
            int textWidth = this.textRenderer.getWidth(displayedTitle);
            int lineY = y + 10;
            context.fill(x, y + 2, x + width, y + 22, 671088640);
            context.fill(x + 6, lineY, Math.max(x + 6, centerX - textWidth / 2 - 10), lineY + 1, 1717197402);
            context.fill(Math.min(x + width - 6, centerX + textWidth / 2 + 10), lineY, x + width - 6, lineY + 1, 1717197402);
            context.drawCenteredTextWithShadow(this.textRenderer, displayedTitle, centerX, y + 6, -2042444);
         }

         private void renderContributor(DrawContext context, int x, int y, int width, NonCreditsData.Contributor contributor) {
            int textX = x + 5;
            int textY = y + 2;
            String prefix = contributor.name();
            String details = contributorDetails(contributor);
            if (details != null) {
               prefix = prefix + ": ";
            }

            prefix = this.textRenderer.trimToWidth(prefix, width - 10);
            context.drawTextWithShadow(this.textRenderer, prefix, textX, textY, -796531);
            if (details != null) {
               int detailX = textX + this.textRenderer.getWidth(prefix);
               int detailWidth = Math.max(0, x + width - 5 - detailX);
               context.drawTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(details, detailWidth), detailX, textY, -4671304);
            }
         }

         private static String contributorDetails(NonCreditsData.Contributor contributor) {
            String roles = contributor.roles().isEmpty() ? null : String.join(", ", contributor.roles());
            if (contributor.contribution() == null) {
               return roles;
            } else {
               return roles == null ? contributor.contribution() : roles + " - " + contributor.contribution();
            }
         }

         private void renderResource(
            DrawContext context, int mouseX, int mouseY, float delta, int x, int y, int width, boolean hovered, NonCreditsData.ResourceCredit resource
         ) {
            context.fill(x, y - 1, x + width, y + this.rowHeight - 5, hovered ? 939524096 : 570425344);
            int textX = x + 5;
            int currentY = y + 3;
            context.drawTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(resource.title(), width - 10), textX, currentY, -796531);
            currentY += 10;
            if (resource.category() != null) {
               this.drawTrimmed(
                  context,
                  Text.translatable("config.needsofnature.credits.category_value", new Object[]{resource.category()}),
                  textX,
                  currentY,
                  width - 10,
                  -5592406
               );
               currentY += 10;
            }

            if (resource.creator() != null) {
               this.drawTrimmed(
                  context,
                  Text.translatable("config.needsofnature.credits.creator_value", new Object[]{resource.creator()}),
                  textX,
                  currentY,
                  width - 10,
                  -3092272
               );
               currentY += 10;
            }

            if (resource.source() != null) {
               this.drawTrimmed(
                  context,
                  Text.translatable("config.needsofnature.credits.source_value", new Object[]{resource.source()}),
                  textX,
                  currentY,
                  width - 10,
                  -4671304
               );
               currentY += 10;
            }

            if (resource.license() != null || resource.modified()) {
               Text license = resource.license() == null
                  ? Text.translatable("config.needsofnature.credits.modified")
                  : (
                     resource.modified()
                        ? Text.translatable("config.needsofnature.credits.license_modified_value", new Object[]{resource.license()})
                        : Text.translatable("config.needsofnature.credits.license_value", new Object[]{resource.license()})
                  );
               this.drawTrimmed(context, license, textX, currentY, width - 10, -4671304);
               currentY += 10;
            }

            String assets = joinedAssets(resource.assets());
            if (assets != null) {
               Text assetText = Text.translatable("config.needsofnature.credits.assets_value", new Object[]{assets});

               for (OrderedText line : this.textRenderer.wrapLines(assetText, Math.max(20, width - 10))) {
                  context.drawTextWithShadow(this.textRenderer, line, textX, currentY, -7303024);
                  currentY += 10;
               }
            }

            if (!this.widgets.isEmpty()) {
               int totalWidth = this.widgets.size() * 90 + Math.max(0, this.widgets.size() - 1) * 4;
               int buttonX = x + (width - totalWidth) / 2;
               int buttonY = y + this.rowHeight - 27;

               for (ClickableWidget widget : this.widgets) {
                  widget.setX(buttonX);
                  widget.setY(buttonY);
                  widget.render(context, mouseX, mouseY, delta);
                  buttonX += 94;
               }
            }
         }

         private void renderProjectLinks(DrawContext context, int mouseX, int mouseY, float delta, int x, int y, int width) {
            int buttonWidth = Math.min(180, width - 8);
            int buttonX = x + (width - buttonWidth) / 2;
            int buttonY = y + 2;

            for (ClickableWidget widget : this.widgets) {
               widget.setWidth(buttonWidth);
               widget.setX(buttonX);
               widget.setY(buttonY);
               widget.render(context, mouseX, mouseY, delta);
               buttonY += 24;
            }
         }

         private void drawTrimmed(DrawContext context, Text value, int x, int y, int width, int color) {
            context.drawTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(value.getString(), Math.max(20, width)), x, y, color);
         }

         private int rowHeight() {
            return this.rowHeight;
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

   private sealed interface CreditsRow
      permits NonCreditsScreen.NoticeRow,
      NonCreditsScreen.SectionRow,
      NonCreditsScreen.ContributorRow,
      NonCreditsScreen.ResourceRow,
      NonCreditsScreen.LinksRow {
   }

   private record LinksRow(List<NonCreditsData.ProjectLink> links) implements NonCreditsScreen.CreditsRow {
   }

   private record NoticeRow(String text) implements NonCreditsScreen.CreditsRow {
   }

   private record ResourceRow(NonCreditsData.ResourceCredit resource) implements NonCreditsScreen.CreditsRow {
   }

   private record SectionRow(Text title) implements NonCreditsScreen.CreditsRow {
   }
}
