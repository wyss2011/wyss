package com.nonid.client;

import com.nonid.NonConfig;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonLiquidColors {
   public static final int MIXED_RGB = 15920063;
   public static final int ENTITY_RGB = 16776427;

   private NonLiquidColors() {
   }

   public static int toOpaqueArgb(int rgb) {
      return 0xFF000000 | rgb & 16777215;
   }

   public static int resolveEntityRgb(@Nullable Identifier entityTypeId, @Nullable NonConfig config) {
      if (entityTypeId != null && config != null) {
         Integer override = config.getLiquidColorOverrideRgb(entityTypeId.toString());
         return override != null ? override : 16776427;
      } else {
         return 16776427;
      }
   }

   @Nullable
   public static String normalizeHexColor(@Nullable String raw) {
      if (raw == null) {
         return null;
      }

      String text = raw.trim();
      if (text.isEmpty()) {
         return null;
      }

      if (text.startsWith("#")) {
         text = text.substring(1);
      }

      if (text.length() != 6) {
         return null;
      }

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         boolean isHex = c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
         if (!isHex) {
            return null;
         }
      }

      return text.toUpperCase(Locale.ROOT);
   }

   @Nullable
   public static Integer parseHexColor(@Nullable String raw) {
      String normalized = normalizeHexColor(raw);
      if (normalized == null) {
         return null;
      }

      try {
         return Integer.parseInt(normalized, 16) & 16777215;
      } catch (NumberFormatException e) {
         return null;
      }
   }

   @Nullable
   public static Integer resolveEntityOverrideFromMap(@Nullable Map<String, String> map, @Nullable String entityId) {
      if (map != null && entityId != null) {
         String hex = map.get(entityId);
         return hex == null ? null : parseHexColor(hex);
      } else {
         return null;
      }
   }
}
