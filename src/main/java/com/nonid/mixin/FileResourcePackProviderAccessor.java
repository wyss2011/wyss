package com.nonid.mixin;

import net.minecraft.resource.FileResourcePackProvider;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.path.SymlinkFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FileResourcePackProvider.class)
public interface FileResourcePackProviderAccessor {
   @Accessor("type")
   ResourceType non$getType();

   @Accessor("symlinkFinder")
   SymlinkFinder non$getSymlinkFinder();
}
