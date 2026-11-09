package com.nonid.internal.animation.client.render.gecko.layer;

import com.nonid.internal.animation.client.render.gecko.GeckoRenderTickets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.cache.model.cuboid.CuboidGeoBone;
import software.bernie.geckolib.cache.model.cuboid.GeoCube;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

public final class BoneTextureGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState> extends GeoRenderLayer<T, O, R> {
   public BoneTextureGeoLayer(GeoRenderer<@NotNull T, @NotNull O, @NotNull R> renderer) {
      super(renderer);
   }

   public void submitRenderTask(RenderPassInfo<@NotNull R> renderPassInfo, @NotNull OrderedRenderCommandQueue renderTasks) {
      if (renderPassInfo.willRender()) {
         Map<String, Identifier> boneTextures = this.getBoneTextures((R)renderPassInfo.renderState());
         if (!boneTextures.isEmpty()) {
            Set<String> translucentBones = (Set<String>)renderPassInfo.renderState().getOrDefaultGeckolibData(GeckoRenderTickets.TRANSLUCENT_BONES, Set.of());
            Map<Identifier, Set<String>> bonesByTexture = new LinkedHashMap<>();

            for (Entry<String, Identifier> entry : boneTextures.entrySet()) {
               String boneName = entry.getKey();
               Identifier texture = entry.getValue();
               if (boneName != null && !boneName.isBlank() && texture != null && !translucentBones.contains(boneName)) {
                  bonesByTexture.computeIfAbsent(texture, key -> new LinkedHashSet<>()).add(boneName);
               }
            }

            if (!bonesByTexture.isEmpty()) {
               int packedOverlay = renderPassInfo.packedOverlay();
               int packedLight = renderPassInfo.packedLight();
               int renderColor = renderPassInfo.renderColor();
               Set<String> explicitlyHiddenBones = this.getExplicitlyHiddenBones((R)renderPassInfo.renderState());

               for (Entry<Identifier, Set<String>> entry : bonesByTexture.entrySet()) {
                  Identifier texture = entry.getKey();
                  Set<String> boneNames = entry.getValue();
                  if (boneNames != null && !boneNames.isEmpty()) {
                     RenderLayer renderLayer = this.renderer.getRenderType(renderPassInfo.renderState(), texture);
                     if (renderLayer != null) {
                        renderTasks.submitCustom(
                           renderPassInfo.poseStack(),
                           renderLayer,
                           (entryMatrices, vertexConsumer) -> renderPassInfo.renderPosed(
                              () -> {
                                 MatrixStack modelStack = new MatrixStack();
                                 modelStack.peek().copy(entryMatrices);

                                 for (GeoBone bone : renderPassInfo.model().topLevelBones()) {
                                    this.renderBoneFiltered(
                                       modelStack, bone, vertexConsumer, boneNames, explicitlyHiddenBones, packedOverlay, packedLight, renderColor
                                    );
                                 }
                              }
                           )
                        );
                     }
                  }
               }
            }
         }
      }
   }

   private Map<String, Identifier> getBoneTextures(R renderState) {
      return (Map<String, Identifier>)renderState.getOrDefaultGeckolibData(GeckoRenderTickets.BONE_TEXTURES, Map.of());
   }

   private void renderBoneFiltered(
      MatrixStack poseStack,
      GeoBone bone,
      VertexConsumer buffer,
      Set<String> allowedBones,
      Set<String> explicitlyHiddenBones,
      int packedOverlay,
      int packedLight,
      int renderColor
   ) {
      poseStack.push();
      RenderUtil.prepMatrixForBone(poseStack, bone);
      BoneSnapshot snapshot = bone.frameSnapshot;
      boolean overrideBone = allowedBones.contains(bone.name());
      boolean explicitlyHidden = explicitlyHiddenBones.contains(bone.name());
      if (overrideBone && !explicitlyHidden && bone instanceof CuboidGeoBone cuboidBone) {
         for (GeoCube cube : cuboidBone.cubes) {
            poseStack.push();
            this.renderCube(cube, poseStack, buffer, packedOverlay, packedLight, renderColor);
            poseStack.pop();
         }
      }

      boolean hideChildren = snapshot != null && snapshot.areChildrenHidden();
      if (!hideChildren) {
         for (GeoBone child : bone.children()) {
            this.renderBoneFiltered(poseStack, child, buffer, allowedBones, explicitlyHiddenBones, packedOverlay, packedLight, renderColor);
         }
      }

      poseStack.pop();
   }

   private Set<String> getExplicitlyHiddenBones(R renderState) {
      Map<String, Boolean> boneVisibility = (Map<String, Boolean>)renderState.getOrDefaultGeckolibData(GeckoRenderTickets.BONE_VISIBILITY, Map.of());
      if (boneVisibility.isEmpty()) {
         return Set.of();
      }

      Set<String> out = new LinkedHashSet<>();

      for (Entry<String, Boolean> entry : boneVisibility.entrySet()) {
         String boneName = entry.getKey();
         if (boneName != null && !boneName.isBlank() && Boolean.FALSE.equals(entry.getValue())) {
            out.add(boneName);
         }
      }

      return out.isEmpty() ? Set.of() : Set.copyOf(out);
   }

   private void renderCube(GeoCube cube, MatrixStack poseStack, VertexConsumer buffer, int packedOverlay, int packedLight, int renderColor) {
      cube.render(poseStack, buffer, packedLight, packedOverlay, renderColor);
   }
}
