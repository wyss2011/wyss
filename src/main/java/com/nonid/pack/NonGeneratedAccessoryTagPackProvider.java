package com.nonid.pack;

import com.nonid.NonTrinketsIntegration;
import com.nonid.data.NonAccessoryItemRegistry;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class NonGeneratedAccessoryTagPackProvider implements ResourcePackProvider {
   public void register(Consumer<ResourcePackProfile> profileAdder) {
      if (NonTrinketsIntegration.isTrinketsLoaded()) {
         Map<Identifier, byte[]> resources = buildGeneratedResources();
         if (!resources.isEmpty()) {
            ResourcePackProfile profile = NonGeneratedInMemoryPack.createProfile(
               "needsofnature/server/generated_accessory_tags",
               Text.literal("NoN 生成的配件标签"),
               Text.literal("生成的 NoN 配件饰品标签"),
               ResourceType.SERVER_DATA,
               resources
            );
            if (profile != null) {
               profileAdder.accept(profile);
            }
         }
      }
   }

   private static Map<Identifier, byte[]> buildGeneratedResources() {
      Map<Identifier, byte[]> resources = new LinkedHashMap<>();
      addSlotTag(resources, "legs/v");
      addSlotTag(resources, "legs/a");
      addSlotTag(resources, "legs/d");
      addItemTag(resources, "minecraft", "dyeable", NonAccessoryItemRegistry.getDyeableItemIds());
      List<Identifier> accessoryIds = NonAccessoryItemRegistry.getRegisteredItemIds();
      addItemTag(resources, "minecraft", "enchantable/equippable", accessoryIds);
      addItemTag(resources, "minecraft", "enchantable/vanishing", accessoryIds);
      addItemTag(resources, "minecraft", "enchantable/durability", NonAccessoryItemRegistry.getDurabilityEnchantableItemIds());
      return resources;
   }

   private static void addSlotTag(Map<Identifier, byte[]> resources, String slotId) {
      List<Identifier> itemIds = NonAccessoryItemRegistry.getItemIdsForTrinketsSlot(slotId);
      addItemTag(resources, "trinkets", slotId, itemIds);
   }

   private static void addItemTag(Map<Identifier, byte[]> resources, String namespace, String path, List<Identifier> itemIds) {
      if (!itemIds.isEmpty()) {
         String resourcePath = "tags/item/" + path + ".json";
         StringBuilder json = new StringBuilder();
         json.append("{\n  \"replace\": false,\n  \"values\": [\n");

         for (int i = 0; i < itemIds.size(); i++) {
            json.append("    \"").append(itemIds.get(i)).append("\"");
            if (i + 1 < itemIds.size()) {
               json.append(',');
            }

            json.append('\n');
         }

         json.append("  ]\n}\n");
         resources.put(Identifier.of(namespace, resourcePath), json.toString().getBytes(StandardCharsets.UTF_8));
      }
   }
}
