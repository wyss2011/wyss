package com.nonid.data;

import java.util.Map;
import java.util.Set;

public final class NonLiquidRoles {
   private NonLiquidRoles() {
   }

   public enum InjectorRole {
      NONE,
      V,
      A,
      M;
   }

   public record LiquidRoles(Set<String> injectorKeys, Set<String> receiverKeys, Map<String, NonLiquidRoles.InjectorRole> injectorRoles) {
   }
}
