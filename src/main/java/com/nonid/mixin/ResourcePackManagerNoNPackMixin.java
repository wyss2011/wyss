package com.nonid.mixin;

import com.google.common.collect.ImmutableSet;
import com.nonid.pack.NonExternalPackProvider;
import com.nonid.pack.NonGeneratedAccessoryModelPackProvider;
import com.nonid.pack.NonGeneratedAccessoryTagPackProvider;
import com.nonid.pack.NonGeneratedCemPackProvider;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resource.FileResourcePackProvider;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.path.SymlinkFinder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResourcePackManager.class)
public abstract class ResourcePackManagerNoNPackMixin {
   @Shadow
   @Final
   @Mutable
   private Set<ResourcePackProvider> providers;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void non$injectExternalNoNPacks(ResourcePackProvider[] originalProviders, CallbackInfo ci) {
      boolean needsClientProvider = false;
      boolean needsServerProvider = false;
      SymlinkFinder clientSymlinkFinder = null;
      SymlinkFinder serverSymlinkFinder = null;

      for (ResourcePackProvider provider : this.providers) {
         if (provider instanceof FileResourcePackProvider fileProvider) {
            FileResourcePackProviderAccessor accessor = (FileResourcePackProviderAccessor)fileProvider;
            ResourceType type = accessor.non$getType();
            if (type == ResourceType.CLIENT_RESOURCES) {
               needsClientProvider = true;
               if (clientSymlinkFinder == null) {
                  clientSymlinkFinder = accessor.non$getSymlinkFinder();
               }
            } else if (type == ResourceType.SERVER_DATA) {
               needsServerProvider = true;
               if (serverSymlinkFinder == null) {
                  serverSymlinkFinder = accessor.non$getSymlinkFinder();
               }
            }
         }
      }

      if (needsClientProvider || needsServerProvider) {
         Path nonPackDir = NonExternalPackProvider.resolveDefaultPacksDir();
         LinkedHashSet<ResourcePackProvider> updated = new LinkedHashSet<>(this.providers);
         if (needsClientProvider && !containsNoNProvider(updated, ResourceType.CLIENT_RESOURCES)) {
            updated.add(
               new NonExternalPackProvider(
                  nonPackDir, ResourceType.CLIENT_RESOURCES, clientSymlinkFinder != null ? clientSymlinkFinder : new SymlinkFinder(path -> true)
               )
            );
         }

         if (needsClientProvider && !containsGeneratedCemProvider(updated)) {
            updated.add(new NonGeneratedCemPackProvider(nonPackDir));
         }

         if (needsClientProvider && !containsGeneratedAccessoryModelProvider(updated)) {
            updated.add(new NonGeneratedAccessoryModelPackProvider());
         }

         if (needsServerProvider && !containsNoNProvider(updated, ResourceType.SERVER_DATA)) {
            updated.add(
               new NonExternalPackProvider(
                  nonPackDir, ResourceType.SERVER_DATA, serverSymlinkFinder != null ? serverSymlinkFinder : new SymlinkFinder(path -> true)
               )
            );
         }

         if (needsServerProvider && !containsGeneratedAccessoryTagProvider(updated)) {
            updated.add(new NonGeneratedAccessoryTagPackProvider());
         }

         this.providers = ImmutableSet.copyOf(updated);
      }
   }

   @Unique
   private static boolean containsNoNProvider(Set<ResourcePackProvider> providers, ResourceType type) {
      for (ResourcePackProvider provider : providers) {
         if (provider instanceof NonExternalPackProvider nonProvider && nonProvider.getType() == type) {
            return true;
         }
      }

      return false;
   }

   @Unique
   private static boolean containsGeneratedCemProvider(Set<ResourcePackProvider> providers) {
      for (ResourcePackProvider provider : providers) {
         if (provider instanceof NonGeneratedCemPackProvider) {
            return true;
         }
      }

      return false;
   }

   @Unique
   private static boolean containsGeneratedAccessoryTagProvider(Set<ResourcePackProvider> providers) {
      for (ResourcePackProvider provider : providers) {
         if (provider instanceof NonGeneratedAccessoryTagPackProvider) {
            return true;
         }
      }

      return false;
   }

   @Unique
   private static boolean containsGeneratedAccessoryModelProvider(Set<ResourcePackProvider> providers) {
      for (ResourcePackProvider provider : providers) {
         if (provider instanceof NonGeneratedAccessoryModelPackProvider) {
            return true;
         }
      }

      return false;
   }
}
