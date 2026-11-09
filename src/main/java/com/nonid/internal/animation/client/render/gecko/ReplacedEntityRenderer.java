package com.nonid.internal.animation.client.render.gecko;

import com.nonid.internal.animation.client.render.AnimationRenderStateAccess;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

public class ReplacedEntityRenderer<T extends GeoAnimatable, E extends Entity, R extends EntityRenderState & GeoRenderState>
   extends GeoReplacedEntityRenderer<T, E, R> {
   private static final Identifier PLAYER_TYPE_ID = Identifier.ofVanilla("player");

   public ReplacedEntityRenderer(Context context, GeoModel<@NotNull T> model, T animatable) {
      super(context, model, animatable);
   }

   public RenderLayer getRenderType(R renderState, Identifier texture) {
      boolean translucent = (Boolean)renderState.getOrDefaultGeckolibData(GeckoRenderTickets.TRANSLUCENT_RENDER, false);
      if (translucent) {
         return RenderLayers.entityTranslucent(texture);
      } else {
         return renderState instanceof AnimationRenderStateAccess access && PLAYER_TYPE_ID.equals(access.afw$getEntityTypeId())
            ? RenderLayers.entityTranslucent(texture)
            : super.getRenderType(renderState, texture);
      }
   }

   public void adjustModelBonesForRender(@NotNull RenderPassInfo<R> renderPassInfo, @NotNull BoneSnapshots snapshots) {
      super.adjustModelBonesForRender(renderPassInfo, snapshots);
      R renderState = (R)renderPassInfo.renderState();
      Map<String, Identifier> boneTextures = (Map<String, Identifier>)renderState.getOrDefaultGeckolibData(GeckoRenderTickets.BONE_TEXTURES, Map.of());
      Map<String, Boolean> boneVisibility = (Map<String, Boolean>)renderState.getOrDefaultGeckolibData(GeckoRenderTickets.BONE_VISIBILITY, Map.of());
      Set<String> translucentBones = (Set<String>)renderState.getOrDefaultGeckolibData(GeckoRenderTickets.TRANSLUCENT_BONES, Set.of());
      if (!boneTextures.isEmpty() || !boneVisibility.isEmpty() || !translucentBones.isEmpty()) {
         for (String boneName : boneTextures.keySet()) {
            if (boneName != null && !boneName.isBlank()) {
               snapshots.ifPresent(boneName, snapshot -> {
                  snapshot.skipRender(true);
                  snapshot.skipChildrenRender(false);
               });
            }
         }

         for (Entry<String, Boolean> entry : boneVisibility.entrySet()) {
            String boneName = entry.getKey();
            if (boneName != null && !boneName.isBlank() && Boolean.FALSE.equals(entry.getValue())) {
               snapshots.ifPresent(boneName, snapshot -> {
                  snapshot.skipRender(true);
                  snapshot.skipChildrenRender(false);
               });
            }
         }

         for (String boneName : translucentBones) {
            if (boneName != null && !boneName.isBlank()) {
               snapshots.ifPresent(boneName, snapshot -> {
                  snapshot.skipRender(true);
                  snapshot.skipChildrenRender(false);
               });
            }
         }
      }
   }
}
