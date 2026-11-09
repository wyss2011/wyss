package com.nonid.api.animation;

import java.util.Locale;

public enum NonAnimationDamageBehavior {
   STOP_ON_DAMAGE,
   IGNORE_DAMAGE,
   BLOCK_DAMAGE;

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public static NonAnimationDamageBehavior fromId(String id, NonAnimationDamageBehavior fallback) {
      if (id != null && !id.isBlank()) {
         String norm = id.trim().toLowerCase(Locale.ROOT);

         return switch (norm) {
            case "stop_on_damage" -> STOP_ON_DAMAGE;
            case "ignore_damage" -> IGNORE_DAMAGE;
            case "block_damage" -> BLOCK_DAMAGE;
            default -> fallback;
         };
      } else {
         return fallback;
      }
   }
}
