package com.nonid;

public enum PregnancyCyclePhase {
   SAFE,
   NORMAL,
   RISKY,
   OVULATION;

   public static PregnancyCyclePhase fromNetworkId(int id) {
      PregnancyCyclePhase[] values = values();
      return id >= 0 && id < values.length ? values[id] : NORMAL;
   }

   public String displayName() {
      return switch (this) {
         case SAFE -> "safe";
         case NORMAL -> "normal";
         case RISKY -> "risky";
         case OVULATION -> "ovulation";
      };
   }
}
