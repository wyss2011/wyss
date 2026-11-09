package com.nonid.internal.animation.client.render.gecko.layer;

import com.nonid.api.client.NonAnimationRenderEvents;
import com.nonid.internal.animation.client.render.gecko.GeckoRenderTickets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import software.bernie.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer;
import software.bernie.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer.RenderData;
import software.bernie.geckolib.util.RenderUtil;

public final class BoneItemGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState> extends BlockAndItemGeoLayer<T, O, R> {
   public BoneItemGeoLayer(GeoRenderer<@NotNull T, @NotNull O, @NotNull R> renderer) {
      super(renderer);
   }

   @NotNull
   protected List<RenderData<R>> getRelevantBones(R renderState, @NotNull BakedGeoModel model) {
      return List.of();
   }

   public void addRenderData(T animatable, O relatedObject, R renderState, float partialTick) {
   }

   public void submitRenderTask(RenderPassInfo<@NotNull R> renderPassInfo, @NotNull OrderedRenderCommandQueue renderTasks) {
      if (renderPassInfo.willRender()) {
         Map<String, NonAnimationRenderEvents.BoneItemProp> boneItems = this.getBoneItems((R)renderPassInfo.renderState());
         if (!boneItems.isEmpty()) {
            Map<String, BoneSnapshot> boneSnapshots = new HashMap<>();
            renderPassInfo.addBoneUpdater((passInfo, snapshots) -> this.captureBoneSnapshots(passInfo.model().topLevelBones(), snapshots, boneSnapshots));
            renderPassInfo.renderPosed(() -> {
               MatrixStack modelStack = new MatrixStack();
               modelStack.peek().copy(renderPassInfo.poseStack().peek());

               for (GeoBone bone : renderPassInfo.model().topLevelBones()) {
                  this.renderBoneItems(renderPassInfo, modelStack, bone, boneItems, boneSnapshots, renderTasks);
               }
            });
         }
      }
   }

   private Map<String, NonAnimationRenderEvents.BoneItemProp> getBoneItems(R renderState) {
      return (Map<String, NonAnimationRenderEvents.BoneItemProp>)renderState.getOrDefaultGeckolibData(GeckoRenderTickets.BONE_ITEMS, Map.of());
   }

   private void renderBoneItems(
      RenderPassInfo<@NotNull R> renderPassInfo,
      MatrixStack poseStack,
      GeoBone bone,
      Map<String, NonAnimationRenderEvents.BoneItemProp> boneItems,
      Map<String, BoneSnapshot> boneSnapshots,
      OrderedRenderCommandQueue renderTasks
   ) {
      poseStack.push();
      RenderUtil.prepMatrixForBone(poseStack, bone);
      NonAnimationRenderEvents.BoneItemProp prop = boneItems.get(bone.name());
      BoneSnapshot snapshot = boneSnapshots.get(bone.name());
      boolean hidden = snapshot != null && snapshot.isHidden();
      if (!hidden && prop != null && !prop.stack().isEmpty()) {
         ItemStack stack = prop.stack().copy();
         ItemDisplayContext context = prop.displayContext();
         poseStack.push();
         bone.translateToPivotPoint(poseStack);
         super.submitItemStackRender(
            poseStack,
            bone,
            stack,
            context,
            renderPassInfo.renderState(),
            renderTasks,
            renderPassInfo.cameraState(),
            renderPassInfo.packedLight(),
            renderPassInfo.packedOverlay(),
            renderPassInfo.renderColor()
         );
         poseStack.pop();
      }

      boolean hideChildren = snapshot != null && snapshot.areChildrenHidden();
      if (!hideChildren) {
         for (GeoBone child : bone.children()) {
            this.renderBoneItems(renderPassInfo, poseStack, child, boneItems, boneSnapshots, renderTasks);
         }
      }

      poseStack.pop();
   }

   private void captureBoneSnapshots(GeoBone[] bones, BoneSnapshots snapshots, Map<String, BoneSnapshot> out) {
      for (GeoBone bone : bones) {
         snapshots.get(bone.name()).ifPresent(snapshot -> out.put(bone.name(), snapshot));
         this.captureBoneSnapshots(bone.children(), snapshots, out);
      }
   }
}
