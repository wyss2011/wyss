package com.nonid;

public interface PregnancyCycleHolder {
   boolean isPregnancyCycleInitialized();

   long getPregnancyCycleProgressTicks();

   long getPregnancyCycleLastGameTick();

   long getPregnancyCycleLastTimeOfDay();

   void setPregnancyCycleState(long var1, long var3, long var5);

   void clearPregnancyCycleState();
}
