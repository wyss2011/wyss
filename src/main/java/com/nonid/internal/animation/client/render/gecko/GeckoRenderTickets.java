package com.nonid.internal.animation.client.render.gecko;

import com.nonid.api.client.NonAnimationRenderEvents;
import com.nonid.internal.animation.client.compat.wildfire.WildfireGenderState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.constant.dataticket.DataTicket;

public final class GeckoRenderTickets {
   public static final DataTicket<@NotNull Identifier> ANIMATION_ID = DataTicket.create("afw_animation_id", Identifier.class);
   public static final DataTicket<@NotNull Identifier> ANIMATION_RESOURCE_ID = DataTicket.create("afw_animation_resource_id", Identifier.class);
   public static final DataTicket<@NotNull String> ANIMATION_KEY = DataTicket.create("afw_animation_key", String.class);
   public static final DataTicket<@NotNull UUID> ANIMATION_INSTANCE_ID = DataTicket.create("afw_instance_id", UUID.class);
   public static final DataTicket<@NotNull UUID> ACTOR_UUID = DataTicket.create("afw_actor_uuid", UUID.class);
   public static final DataTicket<@NotNull Identifier> MODEL_ID = DataTicket.create("afw_model_id", Identifier.class);
   public static final DataTicket<@NotNull Identifier> TEXTURE_ID = DataTicket.create("afw_texture_id", Identifier.class);
   public static final DataTicket<@NotNull List<Identifier>> LAYER_TEXTURES = DataTicket.create("afw_layer_textures", (Class)List.class);
   public static final DataTicket<@NotNull List<Identifier>> EMISSIVE_TEXTURES = DataTicket.create("afw_emissive_textures", (Class)List.class);
   public static final DataTicket<@NotNull Map<String, Identifier>> BONE_TEXTURES = DataTicket.create("afw_bone_textures", (Class)Map.class);
   public static final DataTicket<@NotNull Map<String, Identifier>> EMISSIVE_BONE_TEXTURES = DataTicket.create("afw_emissive_bone_textures", (Class)Map.class);
   public static final DataTicket<@NotNull Map<String, NonAnimationRenderEvents.BoneItemProp>> BONE_ITEMS = DataTicket.create("afw_bone_items", (Class)Map.class);
   public static final DataTicket<@NotNull Map<String, Boolean>> BONE_VISIBILITY = DataTicket.create("afw_bone_visibility", (Class)Map.class);
   public static final DataTicket<@NotNull Map<String, Set<Integer>>> HIDDEN_BONE_CUBE_INDICES = DataTicket.create("afw_hidden_bone_cube_indices", (Class)Map.class);
   public static final DataTicket<@NotNull Boolean> TRANSLUCENT_RENDER = DataTicket.create("afw_translucent_render", Boolean.class);
   public static final DataTicket<@NotNull Set<String>> TRANSLUCENT_BONES = DataTicket.create("afw_translucent_bones", (Class)Set.class);
   public static final DataTicket<@NotNull Identifier> LAST_ANIMATION_RESOURCE_ID = DataTicket.create("afw_last_animation_resource_id", Identifier.class);
   public static final DataTicket<@NotNull String> LAST_ANIMATION_KEY = DataTicket.create("afw_last_animation_key", String.class);
   public static final DataTicket<@NotNull UUID> LAST_ANIMATION_INSTANCE_ID = DataTicket.create("afw_last_instance_id", UUID.class);
   public static final DataTicket<@NotNull Double> ANIMATION_SPEED = DataTicket.create("afw_animation_speed", Double.class);
   public static final DataTicket<@NotNull Double> ANIMATION_TIMELINE_SECONDS = DataTicket.create("afw_animation_timeline_seconds", Double.class);
   public static final DataTicket<@NotNull WildfireGenderState> WILDFIRE_GENDER_STATE = DataTicket.create(
      "afw_wildfire_gender_state", WildfireGenderState.class
   );

   private GeckoRenderTickets() {
   }
}
