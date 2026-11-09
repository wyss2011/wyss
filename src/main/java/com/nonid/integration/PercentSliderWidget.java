package com.nonid.integration;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

final class PercentSliderWidget extends SliderWidget {
   private final IntConsumer listener;
   private int percent;

   PercentSliderWidget(int x, int y, int width, int height, int initialPercent, IntConsumer listener) {
      super(x, y, width, height, Text.literal(""), clampPercent(initialPercent) / 200.0);
      this.listener = listener;
      this.percent = clampPercent(initialPercent);
      this.updateMessage();
   }

   void setPercent(int value) {
      this.percent = clampPercent(value);
      this.value = this.percent / 200.0;
      this.updateMessage();
      if (this.listener != null) {
         this.listener.accept(this.percent);
      }
   }

   protected void updateMessage() {
      this.percent = clampPercent((int)Math.round(this.value * 200.0));
      this.setMessage(Text.translatable("config.needsofnature.action_sound_volume.value", new Object[]{this.percent}));
   }

   protected void applyValue() {
      this.percent = clampPercent((int)Math.round(this.value * 200.0));
      if (this.listener != null) {
         this.listener.accept(this.percent);
      }
   }

   private static int clampPercent(int value) {
      return Math.max(0, Math.min(200, value));
   }
}
