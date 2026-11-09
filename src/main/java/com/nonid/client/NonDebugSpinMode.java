package com.nonid.client;

import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import net.minecraft.util.Identifier;

public final class NonDebugSpinMode {
   public static final Identifier DEBUG_SPIN_ANIMATION_RESOURCE = Identifier.of("needsofnature", "afw/debug_spin/debug_spin");
   public static final Identifier DEBUG_SPIN_MODEL_RESOURCE = Identifier.of("needsofnature", "fish");
   public static final Identifier DEBUG_SPIN_TEXTURE_RESOURCE = Identifier.ofVanilla("textures/entity/fish/salmon.png");

   private NonDebugSpinMode() {
   }

   public static boolean isEnabled() {
      NonConfig config = NeedsOfNature.getConfig();
      return config != null && config.isDebugSpinMode();
   }
}
