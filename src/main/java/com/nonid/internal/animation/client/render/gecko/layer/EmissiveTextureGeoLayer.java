package com.nonid.internal.animation.client.render.gecko.layer;

import com.nonid.internal.animation.client.render.gecko.GeckoRenderTickets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
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

public final class EmissiveTextureGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState> extends GeoRenderLayer<T, O, R> {
   public EmissiveTextureGeoLayer(GeoRenderer<@NotNull T, @NotNull O, @NotNull R> renderer) {
      super(renderer);
   }

   public void submitRenderTask(RenderPassInfo<@NotNull R> renderPassInfo, @NotNull OrderedRenderCommandQueue renderTasks) {
      if (renderPassInfo.willRender()) {
         List<Identifier> emissiveTextures = (List<Identifier>)renderPassInfo.renderState()
            .getOrDefaultGeckolibData(GeckoRenderTickets.EMISSIVE_TEXTURES, List.of());
         Map<String, Identifier> emissiveBoneTextures = (Map<String, Identifier>)renderPassInfo.renderState()
            .getOrDefaultGeckolibData(GeckoRenderTickets.EMISSIVE_BONE_TEXTURES, Map.of());
         if (!emissiveTextures.isEmpty() || !emissiveBoneTextures.isEmpty()) {
            int packedOverlay = renderPassInfo.packedOverlay();
            int renderColor = -1;
            Set<String> customTextureBones = this.getCustomTextureBones((R)renderPassInfo.renderState());
            Set<String> explicitlyHiddenBones = this.getExplicitlyHiddenBones((R)renderPassInfo.renderState());

            for (Identifier texture : emissiveTextures) {
               if (texture != null) {
                  this.submitEmissivePass(
                     renderPassInfo, renderTasks, texture, Set.of(), customTextureBones, false, explicitlyHiddenBones, packedOverlay, renderColor
                  );
               }
            }

            for (Entry<Identifier, Set<String>> entry : this.groupBonesByTexture(emissiveBoneTextures).entrySet()) {
               this.submitEmissivePass(
                  renderPassInfo, renderTasks, entry.getKey(), entry.getValue(), Set.of(), true, explicitlyHiddenBones, packedOverlay, renderColor
               );
            }
         }
      }
   }

   private void submitEmissivePass(
      RenderPassInfo<@NotNull R> renderPassInfo,
      OrderedRenderCommandQueue renderTasks,
      Identifier texture,
      Set<String> includedBones,
      Set<String> excludedBones,
      boolean restrictToIncludedBones,
      Set<String> explicitlyHiddenBones,
      int packedOverlay,
      int renderColor
   ) {
      if (texture != null) {
         RenderLayer renderLayer = RenderLayers.entityCutoutNoCullZOffset(texture);
         renderTasks.submitCustom(
            renderPassInfo.poseStack(),
            renderLayer,
            (pose, vertexConsumer) -> renderPassInfo.renderPosed(
               () -> {
                  MatrixStack modelStack = new MatrixStack();
                  modelStack.peek().copy(pose);

                  for (GeoBone bone : renderPassInfo.model().topLevelBones()) {
                     this.renderBone(
                        modelStack,
                        bone,
                        vertexConsumer,
                        includedBones,
                        excludedBones,
                        restrictToIncludedBones,
                        explicitlyHiddenBones,
                        packedOverlay,
                        renderColor
                     );
                  }
               }
            )
         );
      }
   }

   private void renderBone(
      MatrixStack poseStack,
      GeoBone bone,
      VertexConsumer buffer,
      Set<String> includedBones,
      Set<String> excludedBones,
      boolean restrictToIncludedBones,
      Set<String> explicitlyHiddenBones,
      int packedOverlay,
      int renderColor
   ) {
      poseStack.push();
      RenderUtil.prepMatrixForBone(poseStack, bone);
      BoneSnapshot snapshot = bone.frameSnapshot;
      boolean selected = restrictToIncludedBones ? includedBones.contains(bone.name()) : !excludedBones.contains(bone.name());
      boolean explicitlyHidden = explicitlyHiddenBones.contains(bone.name());
      boolean hiddenBySnapshot = snapshot != null && snapshot.isHidden();
      if (selected && !explicitlyHidden && (!hiddenBySnapshot || restrictToIncludedBones) && bone instanceof CuboidGeoBone cuboidBone) {
         for (GeoCube cube : cuboidBone.cubes) {
            poseStack.push();
            cube.render(poseStack, buffer, 15728880, packedOverlay, renderColor);
            poseStack.pop();
         }
      }

      boolean hideChildren = snapshot != null && snapshot.areChildrenHidden();
      if (!hideChildren) {
         for (GeoBone child : bone.children()) {
            this.renderBone(poseStack, child, buffer, includedBones, excludedBones, restrictToIncludedBones, explicitlyHiddenBones, packedOverlay, renderColor);
         }
      }

      poseStack.pop();
   }

   private Set<String> getCustomTextureBones(R renderState) {
      Map<String, Identifier> boneTextures = (Map<String, Identifier>)renderState.getOrDefaultGeckolibData(GeckoRenderTickets.BONE_TEXTURES, Map.of());
      if (boneTextures.isEmpty()) {
         return Set.of();
      }

      Set<String> out = new LinkedHashSet<>();

      for (Entry<String, Identifier> entry : boneTextures.entrySet()) {
         String boneName = entry.getKey();
         if (boneName != null && !boneName.isBlank() && entry.getValue() != null) {
            out.add(boneName);
         }
      }

      return out.isEmpty() ? Set.of() : Set.copyOf(out);
   }

   private Map<Identifier, Set<String>> groupBonesByTexture(Map<String, Identifier> boneTextures) {
      if (boneTextures.isEmpty()) {
         return Map.of();
      }

      Map<Identifier, Set<String>> out = new LinkedHashMap<>();

      for (Entry<String, Identifier> entry : boneTextures.entrySet()) {
         String boneName = entry.getKey();
         Identifier texture = entry.getValue();
         if (boneName != null && !boneName.isBlank() && texture != null) {
            out.computeIfAbsent(texture, ignored -> new LinkedHashSet<>()).add(boneName);
         }
      }

      return out;
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
}
