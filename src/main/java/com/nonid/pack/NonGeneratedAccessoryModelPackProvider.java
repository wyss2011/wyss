package com.nonid.pack;

import com.nonid.data.NonAccessoryItemRegistry;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class NonGeneratedAccessoryModelPackProvider implements ResourcePackProvider {
   public void register(Consumer<ResourcePackProfile> profileAdder) {
      Map<Identifier, byte[]> resources = buildGeneratedResources();
      if (!resources.isEmpty()) {
         ResourcePackProfile profile = NonGeneratedInMemoryPack.createProfile(
            "needsofnature/client/generated_accessory_models",
            Text.literal("NoN 生成的配件模型"),
            Text.literal("生成的 NoN 配件物品模型"),
            ResourceType.CLIENT_RESOURCES,
            resources
         );
         if (profile != null) {
            profileAdder.accept(profile);
         }
      }
   }

   private static Map<Identifier, byte[]> buildGeneratedResources() {
      Map<Identifier, byte[]> resources = new LinkedHashMap<>();

      for (Entry<Identifier, NonAccessoryItemRegistry.ItemClientModel> entry : NonAccessoryItemRegistry.getClientModelsByItem().entrySet()) {
         Identifier itemId = entry.getKey();
         NonAccessoryItemRegistry.ItemClientModel model = entry.getValue();
         if (model.itemModel() != null) {
            addItemDefinition(resources, itemId, model);
         }

         if (!model.layers().isEmpty()) {
            addGeneratedItemModel(resources, itemId, model.layers());
            List<NonAccessoryItemRegistry.VisualLayer> emptyLayers = withoutStoredLiquidLayers(model.layers());
            if (emptyLayers.size() != model.layers().size()) {
               addGeneratedItemModel(resources, Identifier.of(itemId.getNamespace(), itemId.getPath() + "_empty"), emptyLayers);
            }
         }
      }

      return resources;
   }

   private static void addItemDefinition(Map<Identifier, byte[]> resources, Identifier itemId, NonAccessoryItemRegistry.ItemClientModel model) {
      StringBuilder json = new StringBuilder("{\n  \"model\": ");
      List<NonAccessoryItemRegistry.VisualLayer> emptyLayers = withoutStoredLiquidLayers(model.layers());
      if (emptyLayers.size() != model.layers().size()) {
         Identifier emptyModel = Identifier.of(itemId.getNamespace(), "item/" + itemId.getPath() + "_empty");
         json.append("{\n")
            .append("    \"type\": \"minecraft:condition\",\n")
            .append("    \"property\": \"minecraft:custom_model_data\",\n")
            .append("    \"index\": 0,\n")
            .append("    \"on_true\": ")
            .append(basicModelJson(model.itemModel(), model.layers(), "    "))
            .append(",\n    \"on_false\": ")
            .append(basicModelJson(emptyModel, emptyLayers, "    "))
            .append("\n  }");
      } else {
         json.append(basicModelJson(model.itemModel(), model.layers(), "  "));
      }

      json.append("\n}\n");
      resources.put(Identifier.of(itemId.getNamespace(), "items/" + itemId.getPath() + ".json"), json.toString().getBytes(StandardCharsets.UTF_8));
   }

   private static String basicModelJson(Identifier modelId, List<NonAccessoryItemRegistry.VisualLayer> layers, String indent) {
      if (layers.isEmpty()) {
         return "{\n" + indent + "  \"type\": \"minecraft:empty\"\n" + indent + "}";
      }

      StringBuilder json = new StringBuilder("{\n")
         .append(indent)
         .append("  \"type\": \"minecraft:model\",\n")
         .append(indent)
         .append("  \"model\": \"")
         .append(modelId)
         .append('"');
      int lastTintIndex = lastTintIndex(layers);
      if (lastTintIndex >= 0) {
         json.append(",\n").append(indent).append("  \"tints\": [\n");

         for (int i = 0; i <= lastTintIndex; i++) {
            json.append(indent).append("    ").append(tintJson(layers.get(i).tintMode()));
            if (i < lastTintIndex) {
               json.append(',');
            }

            json.append('\n');
         }

         json.append(indent).append("  ]\n").append(indent).append('}');
      } else {
         json.append('\n').append(indent).append('}');
      }

      return json.toString();
   }

   private static void addGeneratedItemModel(Map<Identifier, byte[]> resources, Identifier itemId, List<NonAccessoryItemRegistry.VisualLayer> layers) {
      StringBuilder json = new StringBuilder("{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n");

      for (int i = 0; i < layers.size(); i++) {
         json.append("    \"layer").append(i).append("\": \"").append(layers.get(i).texture()).append('"');
         if (i + 1 < layers.size()) {
            json.append(',');
         }

         json.append('\n');
      }

      json.append("  }\n}\n");
      resources.put(Identifier.of(itemId.getNamespace(), "models/item/" + itemId.getPath() + ".json"), json.toString().getBytes(StandardCharsets.UTF_8));
   }

   private static int lastTintIndex(List<NonAccessoryItemRegistry.VisualLayer> layers) {
      for (int i = layers.size() - 1; i >= 0; i--) {
         if (layers.get(i).tintMode() != NonAccessoryItemRegistry.TintMode.NONE) {
            return i;
         }
      }

      return -1;
   }

   private static List<NonAccessoryItemRegistry.VisualLayer> withoutStoredLiquidLayers(List<NonAccessoryItemRegistry.VisualLayer> layers) {
      return layers.stream().filter(layer -> layer.tintMode() != NonAccessoryItemRegistry.TintMode.STORED_LIQUID).toList();
   }

   private static String tintJson(NonAccessoryItemRegistry.TintMode tintMode) {
      return switch (tintMode) {
         case DYE -> "{ \"type\": \"minecraft:dye\", \"default\": -1 }";
         case STORED_LIQUID -> "{ \"type\": \"minecraft:custom_model_data\", \"index\": 0, \"default\": -1 }";
         case NONE -> "{ \"type\": \"minecraft:constant\", \"value\": -1 }";
      };
   }
}
