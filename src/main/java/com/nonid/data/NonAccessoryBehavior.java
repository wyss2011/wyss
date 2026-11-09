package com.nonid.data;

import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

public record NonAccessoryBehavior(
   Set<String> occupiesSlots,
   Set<NonLiquidRoles.InjectorRole> blocksInjectorTypes,
   @Nullable String exclusiveGroup,
   int maxDurability,
   int protectionDurabilityCost,
   boolean ignoreInjectorSlotVisualShedding,
   boolean ignoreInjectorSlotEffectSuppression,
   int vInjectionDurabilityCost,
   int aInjectionDurabilityCost,
   boolean blocksPregnancy
) {
   public static final NonAccessoryBehavior NONE = new NonAccessoryBehavior(Set.of(), Set.of(), null, 0, 1, false, false, 0, 0, false);

   public boolean isEmpty() {
      return this.occupiesSlots.isEmpty()
         && this.blocksInjectorTypes.isEmpty()
         && (this.exclusiveGroup == null || this.exclusiveGroup.isBlank())
         && !this.ignoreInjectorSlotVisualShedding
         && !this.ignoreInjectorSlotEffectSuppression
         && this.vInjectionDurabilityCost <= 0
         && this.aInjectionDurabilityCost <= 0
         && !this.blocksPregnancy;
   }

   public boolean occupiesSlot(String slotId) {
      if (slotId == null) {
         return false;
      }

      String normalized = normalizeSlot(slotId);

      for (String occupied : this.occupiesSlots) {
         if (isSlot(normalized, occupied)) {
            return true;
         }
      }

      return false;
   }

   public boolean blocks(NonLiquidRoles.InjectorRole role) {
      return role != null && this.blocksInjectorTypes.contains(role);
   }

   public static String normalizeSlot(String slotId) {
      if (slotId == null) {
         return "";
      } else {
         String slot = slotId.trim().toLowerCase(Locale.ROOT);
         if ("v".equals(slot)) {
            return "legs/v";
         } else if ("a".equals(slot)) {
            return "legs/a";
         } else {
            return "d".equals(slot) ? "legs/d" : slot;
         }
      }
   }

   public static boolean isSlot(String actual, String expected) {
      if (actual != null && expected != null) {
         String normalizedActual = normalizeSlot(actual);
         String normalizedExpected = normalizeSlot(expected);
         return normalizedActual.equals(normalizedExpected) || normalizedActual.startsWith(normalizedExpected + "/");
      } else {
         return false;
      }
   }
}
