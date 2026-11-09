package com.nonid.internal.animation.mixin.client;

import com.nonid.internal.animation.client.render.gecko.GeckoRenderTickets;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.cache.model.cuboid.CuboidGeoBone;
import software.bernie.geckolib.cache.model.cuboid.GeoCube;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

@Mixin(value = CuboidGeoBone.class, remap = false)
public abstract class CuboidGeoBoneHiddenCubeMixin {
   @Shadow
   @Final
   public GeoCube[] cubes;

   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private <R extends GeoRenderState> void afw$skipHiddenCubeIndices(
      RenderPassInfo<R> renderPassInfo,
      MatrixStack poseStack,
      VertexConsumer vertexConsumer,
      int packedLight,
      int packedOverlay,
      int renderColor,
      CallbackInfo ci
   ) {
      if (renderPassInfo != null && renderPassInfo.renderState() != null) {
         Map<String, Set<Integer>> hiddenIndicesByBone = (Map<String, Set<Integer>>)renderPassInfo.renderState()
            .getOrDefaultGeckolibData(GeckoRenderTickets.HIDDEN_BONE_CUBE_INDICES, Map.of());
         if (!hiddenIndicesByBone.isEmpty()) {
            GeoBone bone = (GeoBone)(Object)this;
            Set<Integer> hiddenIndices = hiddenIndicesByBone.get(bone.name());
            if (hiddenIndices != null && !hiddenIndices.isEmpty()) {
               if (bone.frameSnapshot == null || !bone.frameSnapshot.isHidden()) {
                  for (int i = 0; i < this.cubes.length; i++) {
                     GeoCube cube = this.cubes[i];
                     if (cube != null && !hiddenIndices.contains(i)) {
                        poseStack.push();
                        cube.render(poseStack, vertexConsumer, packedLight, packedOverlay, renderColor);
                        poseStack.pop();
                     }
                  }
               }

               ci.cancel();
            }
         }
      }
   }
}
