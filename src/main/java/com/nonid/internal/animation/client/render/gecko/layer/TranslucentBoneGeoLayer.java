package com.nonid.internal.animation.client.render.gecko.layer;

import com.nonid.internal.animation.client.render.gecko.GeckoRenderTickets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
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

public final class TranslucentBoneGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState> extends GeoRenderLayer<T, O, R> {
   private static final int OVERLAY_QUEUE = 1;

   public TranslucentBoneGeoLayer(GeoRenderer<@NotNull T, @NotNull O, @NotNull R> renderer) {
      super(renderer);
   }

   public void submitRenderTask(RenderPassInfo<@NotNull R> renderPassInfo, @NotNull OrderedRenderCommandQueue renderTasks) {
      if (renderPassInfo.willRender()) {
         R renderState = (R)renderPassInfo.renderState();
         Set<String> translucentBones = (Set<String>)renderState.getOrDefaultGeckolibData(GeckoRenderTickets.TRANSLUCENT_BONES, Set.of());
         if (!translucentBones.isEmpty()) {
            Identifier baseTexture = (Identifier)renderState.getGeckolibData(GeckoRenderTickets.TEXTURE_ID);
            Map<String, Identifier> boneTextures = (Map<String, Identifier>)renderState.getOrDefaultGeckolibData(GeckoRenderTickets.BONE_TEXTURES, Map.of());
            Map<Identifier, Set<String>> bonesByTexture = this.groupBonesByTexture(translucentBones, boneTextures, baseTexture);
            if (!bonesByTexture.isEmpty()) {
               int packedOverlay = renderPassInfo.packedOverlay();
               int packedLight = renderPassInfo.packedLight();
               int renderColor = renderPassInfo.renderColor();
               Set<String> explicitlyHiddenBones = this.getExplicitlyHiddenBones(renderState);
               Map<String, Set<Integer>> hiddenCubeIndices = (Map<String, Set<Integer>>)renderState.getOrDefaultGeckolibData(
                  GeckoRenderTickets.HIDDEN_BONE_CUBE_INDICES, Map.of()
               );
               RenderCommandQueue overlayTasks = renderTasks.getBatchingQueue(1);

               for (Entry<Identifier, Set<String>> entry : bonesByTexture.entrySet()) {
                  RenderLayer renderLayer = RenderLayers.entityTranslucent(entry.getKey());
                  Set<String> boneNames = entry.getValue();
                  overlayTasks.submitCustom(
                     renderPassInfo.poseStack(),
                     renderLayer,
                     (entryMatrices, vertexConsumer) -> renderPassInfo.renderPosed(
                        () -> {
                           MatrixStack modelStack = new MatrixStack();
                           modelStack.peek().copy(entryMatrices);

                           for (GeoBone bone : renderPassInfo.model().topLevelBones()) {
                              this.renderBoneFiltered(
                                 modelStack, bone, vertexConsumer, boneNames, explicitlyHiddenBones, hiddenCubeIndices, packedOverlay, packedLight, renderColor
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

   private Map<Identifier, Set<String>> groupBonesByTexture(Set<String> translucentBones, Map<String, Identifier> boneTextures, Identifier baseTexture) {
      Map<Identifier, Set<String>> out = new LinkedHashMap<>();

      for (String boneName : translucentBones) {
         if (boneName != null && !boneName.isBlank()) {
            Identifier texture = boneTextures.getOrDefault(boneName, baseTexture);
            if (texture != null) {
               out.computeIfAbsent(texture, ignored -> new LinkedHashSet<>()).add(boneName);
            }
         }
      }

      return out;
   }

   private void renderBoneFiltered(
      MatrixStack poseStack,
      GeoBone bone,
      VertexConsumer buffer,
      Set<String> allowedBones,
      Set<String> explicitlyHiddenBones,
      Map<String, Set<Integer>> hiddenCubeIndices,
      int packedOverlay,
      int packedLight,
      int renderColor
   ) {
      poseStack.push();
      RenderUtil.prepMatrixForBone(poseStack, bone);
      BoneSnapshot snapshot = bone.frameSnapshot;
      boolean selected = allowedBones.contains(bone.name());
      boolean explicitlyHidden = explicitlyHiddenBones.contains(bone.name());
      if (selected && !explicitlyHidden && bone instanceof CuboidGeoBone cuboidBone) {
         Set<Integer> hiddenIndices = hiddenCubeIndices.getOrDefault(bone.name(), Set.of());

         for (int index = 0; index < cuboidBone.cubes.length; index++) {
            if (!hiddenIndices.contains(index)) {
               GeoCube cube = cuboidBone.cubes[index];
               poseStack.push();
               cube.render(poseStack, buffer, packedLight, packedOverlay, renderColor);
               poseStack.pop();
            }
         }
      }

      boolean hideChildren = snapshot != null && snapshot.areChildrenHidden();
      if (!hideChildren) {
         for (GeoBone child : bone.children()) {
            this.renderBoneFiltered(poseStack, child, buffer, allowedBones, explicitlyHiddenBones, hiddenCubeIndices, packedOverlay, packedLight, renderColor);
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
}
