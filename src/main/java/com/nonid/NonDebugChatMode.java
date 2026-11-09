package com.nonid;

public enum NonDebugChatMode {
   ALL("all"),
   SETUP_WARNINGS_ERRORS("setup_warnings_errors"),
   SETUP_ERRORS("setup_errors"),
   ERRORS_ONLY("errors_only");

   private final String id;

   NonDebugChatMode(String id) {
      this.id = id;
   }

   public String id() {
      return this.id;
   }

   public boolean allows(NonDebugChatCategory category) {
      if (category == NonDebugChatCategory.ALWAYS) {
         return true;
      }

      return switch (this) {
         case ALL -> true;
         case SETUP_WARNINGS_ERRORS -> category == NonDebugChatCategory.SETUP
            || category == NonDebugChatCategory.WARNING
            || category == NonDebugChatCategory.ERROR;
         case SETUP_ERRORS -> category == NonDebugChatCategory.SETUP || category == NonDebugChatCategory.ERROR;
         case ERRORS_ONLY -> category == NonDebugChatCategory.ERROR;
      };
   }

   public static NonDebugChatMode fromId(String id, NonDebugChatMode fallback) {
      if (id != null) {
         for (NonDebugChatMode mode : values()) {
            if (mode.id.equalsIgnoreCase(id)) {
               return mode;
            }
         }
      }

      return fallback;
   }
}
