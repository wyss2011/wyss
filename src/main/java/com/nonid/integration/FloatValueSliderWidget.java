package com.nonid.integration;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

final class FloatValueSliderWidget extends SliderWidget {
   private final double min;
   private final double max;
   private final double step;
   private final DoubleConsumer listener;
   private final DoubleFunction<String> formatter;
   private float floatValue;
   private double warningAbove = Double.POSITIVE_INFINITY;

   FloatValueSliderWidget(int x, int y, int width, int height, float initialValue, float min, float max, float step, DoubleConsumer listener) {
      this(x, y, width, height, initialValue, min, max, step, listener, FloatValueSliderWidget::format);
   }

   FloatValueSliderWidget(
      int x, int y, int width, int height, float initialValue, float min, float max, float step, DoubleConsumer listener, DoubleFunction<String> formatter
   ) {
      super(x, y, width, height, Text.literal(""), toSliderValue(initialValue, min, max));
      this.min = min;
      this.max = max;
      this.step = Math.max(1.0E-4, step);
      this.listener = listener;
      this.formatter = formatter == null ? FloatValueSliderWidget::format : formatter;
      this.floatValue = this.clampAndRound(initialValue);
      this.value = toSliderValue(this.floatValue, min, max);
      this.updateMessage();
   }

   float getFloatValue() {
      return this.floatValue;
   }

   void setFloatValue(float value) {
      this.floatValue = this.clampAndRound(value);
      this.value = toSliderValue(this.floatValue, this.min, this.max);
      this.updateMessage();
      if (this.listener != null) {
         this.listener.accept(this.floatValue);
      }
   }

   void setWarningAbove(float value) {
      this.warningAbove = value;
      this.updateMessage();
   }

   protected void updateMessage() {
      this.floatValue = this.clampAndRound((float)(this.min + this.value * (this.max - this.min)));
      MutableText message = Text.literal(this.formatter.apply(this.floatValue));
      if (this.floatValue > this.warningAbove) {
         message.formatted(Formatting.YELLOW);
      }

      this.setMessage(message);
   }

   protected void applyValue() {
      this.floatValue = this.clampAndRound((float)(this.min + this.value * (this.max - this.min)));
      this.value = toSliderValue(this.floatValue, this.min, this.max);
      if (this.listener != null) {
         this.listener.accept(this.floatValue);
      }
   }

   private float clampAndRound(float input) {
      double clamped = Math.max(this.min, Math.min(this.max, input));
      double rounded = Math.round(clamped / this.step) * this.step;
      return (float)Math.max(this.min, Math.min(this.max, rounded));
   }

   private static double toSliderValue(double value, double min, double max) {
      return max <= min ? 0.0 : Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
   }

   private static String format(double value) {
      return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
   }
}
