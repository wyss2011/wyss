package com.nonid.integration;

import com.nonid.NonConfig;
import com.nonid.client.intiface.NonIntifaceDependencyManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class NonModMenuIntifaceInstallScreen extends Screen {
   private final Screen parent;
   private final NonConfig config;
   private final boolean enableAfterInstall;
   private ButtonWidget downloadButton;

   NonModMenuIntifaceInstallScreen(Screen parent, NonConfig config, boolean enableAfterInstall) {
      super(Text.translatable("config.needsofnature.intiface.libs.install_title"));
      this.parent = parent;
      this.config = config;
      this.enableAfterInstall = enableAfterInstall;
   }

   protected void init() {
      int centerX = this.width / 2;
      this.downloadButton = ButtonWidget.builder(
            Text.translatable(
               NonIntifaceDependencyManager.generatedBundleOutdated()
                  ? "config.needsofnature.intiface.libs.redownload"
                  : "config.needsofnature.intiface.libs.download"
            ),
            button -> this.startInstall()
         )
         .dimensions(centerX - 155, this.height - 52, 150, 20)
         .build();
      this.addDrawableChild(this.downloadButton);
      ButtonWidget cancelButton = ButtonWidget.builder(Text.translatable("gui.cancel"), button -> MinecraftClient.getInstance().setScreen(this.parent))
         .dimensions(centerX + 5, this.height - 52, 150, 20)
         .build();
      this.addDrawableChild(cancelButton);
      ButtonWidget doneButton = ButtonWidget.builder(
            Text.translatable("config.needsofnature.done"), button -> MinecraftClient.getInstance().setScreen(this.parent)
         )
         .dimensions(centerX - 75, this.height - 28, 150, 20)
         .build();
      this.addDrawableChild(doneButton);
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      super.render(context, mouseX, mouseY, deltaTicks);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 28, 16777215);
      int y = 58;
      this.drawCentered(context, Text.translatable("config.needsofnature.intiface.libs.install_line_1"), y);
      y += 14;
      this.drawCentered(
         context, Text.translatable("config.needsofnature.intiface.libs.install_line_2", new Object[]{NonIntifaceDependencyManager.totalSizeMbText()}), y
      );
      y += 14;
      this.drawCentered(context, Text.translatable("config.needsofnature.intiface.libs.install_line_3"), y);
      y += 22;
      this.drawCentered(context, NonIntifaceDependencyManager.statusText(), y);
      if (this.downloadButton != null) {
         this.downloadButton
            .setMessage(
               Text.translatable(
                  NonIntifaceDependencyManager.generatedBundleOutdated()
                     ? "config.needsofnature.intiface.libs.redownload"
                     : "config.needsofnature.intiface.libs.download"
               )
            );
         this.downloadButton.active = !NonIntifaceDependencyManager.installing()
            && !NonIntifaceDependencyManager.restartRequired()
            && NonIntifaceDependencyManager.installRequired();
      }
   }

   public void close() {
      MinecraftClient.getInstance().setScreen(this.parent);
   }

   private void startInstall() {
      if (this.downloadButton != null) {
         this.downloadButton.active = false;
      }

      NonIntifaceDependencyManager.installAsync(() -> {
         if (this.enableAfterInstall && NonIntifaceDependencyManager.restartRequired()) {
            this.config.setIntifaceEnabled(true);
            this.config.save();
         }
      });
   }

   private void drawCentered(DrawContext context, Text text, int y) {
      context.drawCenteredTextWithShadow(this.textRenderer, text, this.width / 2, y, -2039584);
   }
}
