package com.nonid.internal.animation.client.render.gecko.layer;

import com.nonid.internal.animation.client.render.gecko.GeckoRenderTickets;
import java.util.List;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public final class LayerTextureGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState> extends GeoRenderLayer<T, O, R> {
   public LayerTextureGeoLayer(GeoRenderer<@NotNull T, @NotNull O, @NotNull R> renderer) {
      super(renderer);
   }

   public void submitRenderTask(RenderPassInfo<@NotNull R> renderPassInfo, @NotNull OrderedRenderCommandQueue renderTasks) {
      if (renderPassInfo.willRender()) {
         List<Identifier> layerTextures = (List<Identifier>)renderPassInfo.renderState().getOrDefaultGeckolibData(GeckoRenderTickets.LAYER_TEXTURES, List.of());
         if (!layerTextures.isEmpty()) {
            int packedLight = renderPassInfo.packedLight();
            int packedOverlay = renderPassInfo.packedOverlay();
            int renderColor = -1;

            for (Identifier texture : layerTextures) {
               if (texture != null) {
                  RenderLayer renderLayer = RenderLayers.entityTranslucent(texture);
                  if (renderLayer != null) {
                     renderTasks.submitCustom(
                        renderPassInfo.poseStack(),
                        renderLayer,
                        (pose, vertexConsumer) -> {
                           renderPassInfo.poseStack().push();
                           renderPassInfo.poseStack().peek().copy(pose);
                           renderPassInfo.renderPosed(
                              () -> renderPassInfo.model().render(renderPassInfo, vertexConsumer, packedLight, packedOverlay, renderColor)
                           );
                           renderPassInfo.poseStack().pop();
                        }
                     );
                  }
               }
            }
         }
      }
   }
}
