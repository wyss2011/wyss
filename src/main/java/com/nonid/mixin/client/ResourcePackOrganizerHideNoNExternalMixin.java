package com.nonid.mixin.client;

import java.util.List;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.resource.ResourcePackProfile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResourcePackOrganizer.class)
public abstract class ResourcePackOrganizerHideNoNExternalMixin {
   @Shadow
   @Final
   private List<ResourcePackProfile> enabledPacks;
   @Shadow
   @Final
   private List<ResourcePackProfile> disabledPacks;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void non$hideExternalNoNPacksOnInit(CallbackInfo ci) {
      this.non$filterExternalNoNPacks();
   }

   @Inject(method = "refresh", at = @At("RETURN"))
   private void non$hideExternalNoNPacksOnRefresh(CallbackInfo ci) {
      this.non$filterExternalNoNPacks();
   }

   @Unique
   private void non$filterExternalNoNPacks() {
      this.enabledPacks.removeIf(ResourcePackOrganizerHideNoNExternalMixin::non$isNoNExternalPackProfile);
      this.disabledPacks.removeIf(ResourcePackOrganizerHideNoNExternalMixin::non$isNoNExternalPackProfile);
   }

   @Unique
   private static boolean non$isNoNExternalPackProfile(ResourcePackProfile profile) {
      if (profile == null) {
         return false;
      }

      String id = profile.getId();
      return id != null && !id.isBlank() ? id.startsWith("needsofnature/client/") || id.startsWith("needsofnature/server/") : false;
   }
}
