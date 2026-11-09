package com.nonid.client;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Locale;
import java.util.Set;

final class NonSkinFolderNames {
   private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
      "con",
      "prn",
      "aux",
      "nul",
      "com1",
      "com2",
      "com3",
      "com4",
      "com5",
      "com6",
      "com7",
      "com8",
      "com9",
      "lpt1",
      "lpt2",
      "lpt3",
      "lpt4",
      "lpt5",
      "lpt6",
      "lpt7",
      "lpt8",
      "lpt9"
   );

   private NonSkinFolderNames() {
   }

   static String sanitize(String friendlyName) {
      String normalized = Normalizer.normalize(friendlyName, Form.NFKD).toLowerCase(Locale.ROOT);
      StringBuilder result = new StringBuilder(Math.min(normalized.length(), 64));
      boolean previousSeparator = false;
      int offset = 0;

      while (offset < normalized.length() && result.length() < 64) {
         int codePoint = normalized.codePointAt(offset);
         offset += Character.charCount(codePoint);
         if (Character.getType(codePoint) != 6) {
            boolean allowed = codePoint >= 97 && codePoint <= 122 || codePoint >= 48 && codePoint <= 57 || codePoint == 45;
            if (allowed) {
               result.appendCodePoint(codePoint);
               previousSeparator = false;
            } else if (!previousSeparator && !result.isEmpty()) {
               result.append('_');
               previousSeparator = true;
            }
         }
      }

      while (!result.isEmpty() && result.charAt(result.length() - 1) == '_') {
         result.setLength(result.length() - 1);
      }

      String folderName = result.isEmpty() ? "skin" : result.toString();
      return WINDOWS_RESERVED_NAMES.contains(folderName) ? "skin_" + folderName : folderName;
   }
}
