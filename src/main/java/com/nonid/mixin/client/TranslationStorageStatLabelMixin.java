package com.nonid.mixin.client;

import com.nonid.client.NonStatLabelResolver;
import net.minecraft.client.resource.language.TranslationStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TranslationStorage.class)
public abstract class TranslationStorageStatLabelMixin {
   @Shadow
   public abstract boolean hasTranslation(String var1);

   @Inject(method = "get", at = @At("HEAD"), cancellable = true)
   private void non$resolveDynamicStatLabel(String key, String fallback, CallbackInfoReturnable<String> cir) {
      if (key != null && !this.hasTranslation(key)) {
         String resolved = NonStatLabelResolver.resolve(key);
         if (resolved != null) {
            cir.setReturnValue(resolved);
         }
      }
   }
}
