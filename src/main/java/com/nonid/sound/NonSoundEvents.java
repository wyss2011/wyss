package com.nonid.sound;

import com.nonid.NeedsOfNature;
import java.util.Locale;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class NonSoundEvents {
   public static final SoundEvent DEBUG_STAFF_DISAPPEAR = SoundEvent.of(NeedsOfNature.id("a"));
   private static final int DRY_IMPACT_COUNT = 11;
   private static final int WET_IMPACT_COUNT = 16;
   private static final int WET_COUNT = 12;
   private static final int SHOT_IN_COUNT = 3;
   private static final int SHOT_OUT_COUNT = 6;
   private static final int MOTION_COUNT = 10;
   private static final int RETRACT_COUNT = 3;
   private static final int RIP_COUNT = 3;

   private NonSoundEvents() {
   }

   public static void registerAll() {
      registerSeries("impactdry", 11);
      registerSeries("impactwet", 16);
      registerSeries("wet", 12);
      registerSeries("shot_in", 3);
      registerSeries("shot_out", 6);
      registerSeries("motion", 10);
      registerSeries("retract", 3);
      registerSeries("rip", 3);
      register("a", DEBUG_STAFF_DISAPPEAR);
      register("funkytown");
   }

   private static void registerSeries(String prefix, int count) {
      for (int i = 1; i <= count; i++) {
         String name = prefix + String.format(Locale.ROOT, "%02d", i);
         register(name);
      }
   }

   private static void register(String path) {
      register(path, SoundEvent.of(NeedsOfNature.id(path)));
   }

   private static void register(String path, SoundEvent soundEvent) {
      Identifier id = NeedsOfNature.id(path);
      if (!Registries.SOUND_EVENT.containsId(id)) {
         Registry.register(Registries.SOUND_EVENT, id, soundEvent);
      }
   }
}
