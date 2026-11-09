package com.nonid.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nonid.NeedsOfNature;
import com.nonid.NonAccessoryEffects;
import com.nonid.NonTrinketsIntegration;
import com.nonid.item.NonDataAccessoryItem;
import com.nonid.pack.NonExternalPackCatalog;
import com.nonid.pack.NonExternalPackProvider;
import com.nonid.pack.NonPackRootResolver;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.Item;
import net.minecraft.item.Item.Settings;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class NonAccessoryItemRegistry {
   private static final String ITEM_ROOT = "assets/needsofnature/non_accessory_items";
   private static final int DURABLE_ACCESSORY_ENCHANTABILITY = 10;
   private static final List<Item> REGISTERED_ITEMS = new ArrayList<>();
   private static final Map<Identifier, NonAccessoryItemRegistry.AccessoryItemDefinition> DEFINITIONS_BY_ITEM = new LinkedHashMap<>();
   private static final Map<String, List<Identifier>> ITEM_IDS_BY_TRINKETS_SLOT = new LinkedHashMap<>();

   private NonAccessoryItemRegistry() {
   }

   public static void registerStartupItems() {
      if (REGISTERED_ITEMS.isEmpty()) {
         if (!NonTrinketsIntegration.isTrinketsLoaded()) {
            NeedsOfNature.LOGGER.info("[NoN] Trinkets is not installed; skipping data-driven accessory item registration.");
         } else {
            LinkedHashMap<Identifier, NonAccessoryItemRegistry.AccessoryItemDefinition> definitions = new LinkedHashMap<>();
            loadBundledDefinitions(definitions);
            loadExternalDefinitions(definitions);
            definitions.values().forEach(NonAccessoryItemRegistry::register);
            NeedsOfNature.LOGGER.info("[NoN] Registered {} startup data-driven accessory items.", REGISTERED_ITEMS.size());
         }
      }
   }

   public static List<Item> getRegisteredItems() {
      return List.copyOf(REGISTERED_ITEMS);
   }

   public static Map<Identifier, NonAccessoryItemRegistry.AccessoryItemDefinition> getDefinitionsByItem() {
      return Map.copyOf(DEFINITIONS_BY_ITEM);
   }

   public static List<Identifier> getItemIdsForTrinketsSlot(String slotId) {
      return slotId == null ? List.of() : ITEM_IDS_BY_TRINKETS_SLOT.getOrDefault(slotId, List.of());
   }

   public static NonAccessoryItemRegistry.SkinOverlays getSkinOverlays(Identifier itemId) {
      if (itemId == null) {
         return NonAccessoryItemRegistry.SkinOverlays.EMPTY;
      }

      NonAccessoryItemRegistry.AccessoryItemDefinition definition = DEFINITIONS_BY_ITEM.get(itemId);
      return definition == null ? NonAccessoryItemRegistry.SkinOverlays.EMPTY : definition.skinOverlays();
   }

   public static Map<Identifier, NonAccessoryItemRegistry.ItemClientModel> getClientModelsByItem() {
      LinkedHashMap<Identifier, NonAccessoryItemRegistry.ItemClientModel> models = new LinkedHashMap<>();

      for (NonAccessoryItemRegistry.AccessoryItemDefinition definition : DEFINITIONS_BY_ITEM.values()) {
         if (!definition.clientModel().isEmpty()) {
            models.put(definition.id(), definition.clientModel());
         }
      }

      return Map.copyOf(models);
   }

   private static void loadBundledDefinitions(Map<Identifier, NonAccessoryItemRegistry.AccessoryItemDefinition> definitions) {
      FabricLoader.getInstance().getModContainer("needsofnature").ifPresent(container -> {
         for (Path root : container.getRootPaths()) {
            readDefinitionsFromDirectory(root.resolve("assets/needsofnature/non_accessory_items"), definitions);
         }
      });
   }

   private static void loadExternalDefinitions(Map<Identifier, NonAccessoryItemRegistry.AccessoryItemDefinition> definitions) {
      Path packsDir = NonExternalPackProvider.resolveDefaultPacksDir();
      List<String> configuredOrder = NeedsOfNature.getConfig() == null ? List.of() : NeedsOfNature.getConfig().getExternalNoNPackLoadOrder();

      for (NonExternalPackCatalog.Entry entry : NonExternalPackCatalog.resolveLowToHigh(packsDir, configuredOrder)) {
         readDefinitionsFromPack(entry.root(), definitions);
      }
   }

   @Nullable
   public static NonAccessoryItemRegistry.AccessoryItemDefinition getDefinition(Identifier itemId) {
      return itemId == null ? null : DEFINITIONS_BY_ITEM.get(itemId);
   }

   public static List<Identifier> getDyeableItemIds() {
      return DEFINITIONS_BY_ITEM.values()
         .stream()
         .filter(NonAccessoryItemRegistry.AccessoryItemDefinition::dyeable)
         .map(NonAccessoryItemRegistry.AccessoryItemDefinition::id)
         .toList();
   }

   public static List<Identifier> getRegisteredItemIds() {
      return DEFINITIONS_BY_ITEM.values().stream().map(NonAccessoryItemRegistry.AccessoryItemDefinition::id).toList();
   }

   public static List<Identifier> getDurabilityEnchantableItemIds() {
      return DEFINITIONS_BY_ITEM.values()
         .stream()
         .filter(definition -> supportsDurabilityEnchantments(definition.maxDurability()))
         .map(NonAccessoryItemRegistry.AccessoryItemDefinition::id)
         .toList();
   }

   static boolean supportsDurabilityEnchantments(int maxDurability) {
      return maxDurability > 1;
   }

   private static void readDefinitionsFromPack(
      NonPackRootResolver.ResolvedRoot root, Map<Identifier, NonAccessoryItemRegistry.AccessoryItemDefinition> definitions
   ) {
      if (!root.isZip()) {
         readDefinitionsFromDirectory(root.rootPath().resolve("assets/needsofnature/non_accessory_items"), definitions);
      } else {
         Path path = root.sourcePath();

         try (ZipFile zip = new ZipFile(path.toFile())) {
            zip.stream()
               .filter(entry -> !entry.isDirectory())
               .filter(entry -> {
                  String name = root.stripZipPrefix(entry.getName());
                  return name != null && name.startsWith("assets/needsofnature/non_accessory_items/") && name.endsWith(".json");
               })
               .sorted(Comparator.comparing(ZipEntry::getName, String.CASE_INSENSITIVE_ORDER))
               .forEach(entry -> readDefinitionFromZip(path, root, zip, entry, definitions));
         } catch (IOException e) {
            NeedsOfNature.logSetupWarning("[NoN] Failed to scan accessory item pack {}", path, e);
         }
      }
   }

   private static void readDefinitionsFromDirectory(Path dir, Map<Identifier, NonAccessoryItemRegistry.AccessoryItemDefinition> definitions) {
      if (Files.isDirectory(dir)) {
         try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
               .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
               .forEach(path -> readDefinitionFromPath(path, definitions));
         } catch (IOException e) {
            NeedsOfNature.logSetupWarning("[NoN] Failed to scan accessory item definitions in {}", dir, e);
         }
      }
   }

   private static void readDefinitionFromPath(Path path, Map<Identifier, NonAccessoryItemRegistry.AccessoryItemDefinition> definitions) {
      try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
         readDefinition(path.toString(), JsonParser.parseReader(reader).getAsJsonObject(), definitions);
      } catch (IOException | RuntimeException e) {
         NeedsOfNature.logSetupWarning("[NoN] Failed to read accessory item definition {}", path, e);
      }
   }

   private static void readDefinitionFromZip(
      Path zipPath,
      NonPackRootResolver.ResolvedRoot root,
      ZipFile zip,
      ZipEntry entry,
      Map<Identifier, NonAccessoryItemRegistry.AccessoryItemDefinition> definitions
   ) {
      String logicalPath = root.stripZipPrefix(entry.getName());
      if (logicalPath != null) {
         try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            readDefinition(zipPath + "!" + logicalPath, JsonParser.parseReader(reader).getAsJsonObject(), definitions);
         } catch (IOException | RuntimeException e) {
            NeedsOfNature.logSetupWarning("[NoN] Failed to read accessory item definition {}!{}", zipPath, logicalPath, e);
         }
      }
   }

   private static void readDefinition(String source, JsonObject root, Map<Identifier, NonAccessoryItemRegistry.AccessoryItemDefinition> definitions) {
      Identifier id = parseItemId(root);
      if (id == null) {
         NeedsOfNature.logSetupWarning("[NoN] Ignoring accessory item definition {}: missing or invalid id.", source);
      } else if (!"needsofnature".equals(id.getNamespace())) {
         NeedsOfNature.logSetupWarning("[NoN] Ignoring accessory item definition {}: namespace must be '{}'.", source, "needsofnature");
      } else if (definitions.containsKey(id)) {
         NeedsOfNature.logSetupWarning("[NoN] Ignoring duplicate accessory item definition {} for {}.", source, id);
      } else {
         int maxCount = parseInt(root.get("max_count"), 1, 1, 64);
         int maxDurability = parseInt(root.get("max_durability"), 0, 0, 100000);
         if (maxDurability > 0) {
            maxCount = 1;
         }

         boolean showInItemGroup = parseBoolean(root.get("show_in_item_group"), true);
         Set<String> trinketsSlots = parseTrinketsSlots(root.get("trinkets_slots"));
         if (trinketsSlots.isEmpty()) {
            trinketsSlots = Set.of("legs/v", "legs/a");
         }

         NonAccessoryEffects effects = root.has("effects") ? NonAccessoryDefinitions.parseEffectsForStartup(id, root) : NonAccessoryEffects.NEUTRAL;
         Map<String, NonAccessoryDefinitions.TooltipColor> tooltipColors = NonAccessoryDefinitions.parseTooltipColorsForStartup(id, root);
         NonAccessoryBehavior behavior = NonAccessoryDefinitions.parseBehaviorForStartup(id, root);
         NonAccessoryItemRegistry.SkinOverlays skinOverlays = parseSkinOverlays(id, root.get("skin_overlays"));
         NonAccessoryItemRegistry.ItemClientModel clientModel = parseItemClientModel(id, root);
         boolean dyeable = parseBoolean(root.get("dyeable"), false);
         int defaultDyeColor = parseDyeColor(root.get("default_dye_color"));
         NonAccessoryItemRegistry.ActorVisualBehavior actorVisualBehavior = parseActorVisualBehavior(id, root);
         definitions.put(
            id,
            new NonAccessoryItemRegistry.AccessoryItemDefinition(
               id,
               maxCount,
               maxDurability,
               showInItemGroup,
               trinketsSlots,
               effects,
               tooltipColors,
               behavior,
               skinOverlays,
               clientModel,
               dyeable,
               defaultDyeColor,
               actorVisualBehavior
            )
         );
      }
   }

   private static void register(NonAccessoryItemRegistry.AccessoryItemDefinition definition) {
      RegistryKey<Item> key = RegistryKey.of(Registries.ITEM.getKey(), definition.id());
      Settings settings = new Settings().registryKey(key).maxCount(definition.maxCount());
      if (definition.maxDurability() > 0) {
         settings.maxDamage(definition.maxDurability());
         settings.component(DataComponentTypes.MAX_DAMAGE, definition.maxDurability());
         settings.component(DataComponentTypes.DAMAGE, 0);
      }

      if (supportsDurabilityEnchantments(definition.maxDurability())) {
         settings.enchantable(10);
      }

      if (definition.dyeable()) {
         settings.component(DataComponentTypes.DYED_COLOR, new DyedColorComponent(definition.defaultDyeColor()));
      }

      Item item = new NonDataAccessoryItem(settings);
      Registry.register(Registries.ITEM, key, item);
      NonTrinketsIntegration.registerTrinketItem(item);
      if (definition.showInItemGroup()) {
         REGISTERED_ITEMS.add(item);
      }

      DEFINITIONS_BY_ITEM.put(definition.id(), definition);

      for (String slot : definition.trinketsSlots()) {
         List<Identifier> ids = new ArrayList<>(ITEM_IDS_BY_TRINKETS_SLOT.getOrDefault(slot, List.of()));
         ids.add(definition.id());
         ITEM_IDS_BY_TRINKETS_SLOT.put(slot, List.copyOf(ids));
      }
   }

   private static NonAccessoryItemRegistry.SkinOverlays parseSkinOverlays(Identifier itemId, @Nullable JsonElement element) {
      if (element != null && element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         List<NonAccessoryItemRegistry.VisualLayer> v = parseVisualLayers(itemId, "v", object.get("v"), true);
         List<NonAccessoryItemRegistry.VisualLayer> a = parseVisualLayers(itemId, "a", object.get("a"), true);
         List<NonAccessoryItemRegistry.VisualLayer> d = parseVisualLayers(itemId, "d", object.get("d"), true);
         return new NonAccessoryItemRegistry.SkinOverlays(v, a, d);
      } else {
         return NonAccessoryItemRegistry.SkinOverlays.EMPTY;
      }
   }

   private static NonAccessoryItemRegistry.ItemClientModel parseItemClientModel(Identifier itemId, JsonObject root) {
      Identifier itemTexture = parseAssetIdentifier(itemId, root.get("item_texture"), "item/");
      Identifier itemModel = parseAssetIdentifier(itemId, root.get("item_model"), "item/");
      List<NonAccessoryItemRegistry.VisualLayer> layers = parseVisualLayers(itemId, "item_layers", root.get("item_layers"), false);
      if (layers.isEmpty() && itemTexture != null) {
         layers = List.of(new NonAccessoryItemRegistry.VisualLayer(itemTexture, NonAccessoryItemRegistry.TintMode.NONE));
      }

      if (itemModel == null && !layers.isEmpty()) {
         itemModel = Identifier.of(itemId.getNamespace(), "item/" + itemId.getPath());
      }

      return new NonAccessoryItemRegistry.ItemClientModel(itemModel, layers);
   }

   private static NonAccessoryItemRegistry.ActorVisualBehavior parseActorVisualBehavior(Identifier itemId, JsonObject root) {
      Set<String> virtualTags = parseStringSet(root.get("virtual_actor_tags"));
      String modelGender = parseString(root.get("animation_model_gender"));
      if (modelGender != null) {
         modelGender = modelGender.trim().toLowerCase(Locale.ROOT);
         if (!"male".equals(modelGender) && !"female".equals(modelGender)) {
            NeedsOfNature.logSetupWarning("[NoN] Ignoring unknown animation model gender '{}' for {}.", modelGender, itemId);
            modelGender = null;
         }
      }

      Map<String, NonAccessoryItemRegistry.VisualLayer> boneTextures = new LinkedHashMap<>();
      JsonElement boneElement = root.get("animation_bone_textures");
      if (boneElement != null && boneElement.isJsonObject()) {
         for (Entry<String, JsonElement> entry : boneElement.getAsJsonObject().entrySet()) {
            List<NonAccessoryItemRegistry.VisualLayer> layers = parseVisualLayers(itemId, entry.getKey(), entry.getValue(), true);
            if (!layers.isEmpty()) {
               boneTextures.put(entry.getKey(), layers.getFirst());
            }
         }
      }

      int storedLiquidCapacityMl = parseInt(root.get("stored_liquid_capacity_ml"), 0, 0, 1000);
      return new NonAccessoryItemRegistry.ActorVisualBehavior(virtualTags, modelGender, Map.copyOf(boneTextures), storedLiquidCapacityMl);
   }

   private static List<NonAccessoryItemRegistry.VisualLayer> parseVisualLayers(
      Identifier itemId, String key, @Nullable JsonElement element, boolean texturePath
   ) {
      if (element == null) {
         return List.of();
      }

      List<NonAccessoryItemRegistry.VisualLayer> layers = new ArrayList<>();
      if (element.isJsonArray()) {
         for (JsonElement child : element.getAsJsonArray()) {
            NonAccessoryItemRegistry.VisualLayer layer = parseVisualLayer(itemId, key, child, texturePath);
            if (layer != null) {
               layers.add(layer);
            }
         }
      } else {
         NonAccessoryItemRegistry.VisualLayer layer = parseVisualLayer(itemId, key, element, texturePath);
         if (layer != null) {
            layers.add(layer);
         }
      }

      return layers.isEmpty() ? List.of() : List.copyOf(layers);
   }

   @Nullable
   private static NonAccessoryItemRegistry.VisualLayer parseVisualLayer(Identifier itemId, String key, JsonElement element, boolean texturePath) {
      JsonElement textureElement = element;
      NonAccessoryItemRegistry.TintMode tintMode = NonAccessoryItemRegistry.TintMode.NONE;
      if (element != null && element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         textureElement = object.get("texture");
         tintMode = NonAccessoryItemRegistry.TintMode.parse(parseString(object.get("tint")));
      }

      Identifier texture = texturePath ? parseTextureIdentifier(itemId, key, textureElement) : parseAssetIdentifier(itemId, textureElement, "item/");
      return texture == null ? null : new NonAccessoryItemRegistry.VisualLayer(texture, tintMode);
   }

   private static Set<String> parseStringSet(@Nullable JsonElement element) {
      if (element == null) {
         return Set.of();
      }

      LinkedHashSet<String> values = new LinkedHashSet<>();
      if (element.isJsonArray()) {
         for (JsonElement child : element.getAsJsonArray()) {
            String value = parseString(child);
            if (value != null && !value.isBlank()) {
               values.add(value.trim());
            }
         }
      } else {
         String value = parseString(element);
         if (value != null && !value.isBlank()) {
            values.add(value.trim());
         }
      }

      return Set.copyOf(values);
   }

   private static int parseDyeColor(@Nullable JsonElement element) {
      String raw = parseString(element);
      DyeColor color = DyeColor.byId(raw == null ? "purple" : raw.trim().toLowerCase(Locale.ROOT), DyeColor.PURPLE);
      return color.getEntityColor();
   }

   @Nullable
   private static Identifier parseAssetIdentifier(Identifier itemId, @Nullable JsonElement element, String defaultPathPrefix) {
      String raw = parseString(element);
      if (raw != null && !raw.isBlank()) {
         raw = raw.trim();
         if (!raw.contains(":")) {
            raw = itemId.getNamespace() + ":" + defaultPathPrefix + raw;
         }

         Identifier id = Identifier.tryParse(raw);
         if (id == null) {
            NeedsOfNature.logSetupWarning("[NoN] Ignoring invalid accessory asset identifier '{}' for {}.", raw, itemId);
         }

         return id;
      } else {
         return null;
      }
   }

   @Nullable
   private static Identifier parseTextureIdentifier(Identifier itemId, String key, @Nullable JsonElement element) {
      String raw = parseString(element);
      if (raw != null && !raw.isBlank()) {
         raw = raw.trim();
         if (!raw.contains(":")) {
            raw = itemId.getNamespace() + ":" + raw;
         }

         Identifier id = Identifier.tryParse(raw);
         if (id == null) {
            NeedsOfNature.logSetupWarning("[NoN] Ignoring invalid accessory skin overlay '{}' for {}.", key, itemId);
            return null;
         } else {
            return toTexturePath(id);
         }
      } else {
         return null;
      }
   }

   private static Identifier toTexturePath(Identifier id) {
      String path = id.getPath();
      if (!path.startsWith("textures/")) {
         path = "textures/" + path;
      }

      if (!path.endsWith(".png")) {
         path = path + ".png";
      }

      return Identifier.of(id.getNamespace(), path);
   }

   private static Set<String> parseTrinketsSlots(@Nullable JsonElement element) {
      if (element == null) {
         return Set.of();
      }

      LinkedHashSet<String> slots = new LinkedHashSet<>();
      if (element.isJsonArray()) {
         for (JsonElement child : element.getAsJsonArray()) {
            addTrinketsSlot(slots, parseString(child));
         }
      } else {
         addTrinketsSlot(slots, parseString(element));
      }

      return Set.copyOf(slots);
   }

   private static void addTrinketsSlot(Set<String> slots, @Nullable String raw) {
      if (raw != null && !raw.isBlank()) {
         String slot = raw.trim().toLowerCase(Locale.ROOT);
         if ("v".equals(slot)) {
            slot = "legs/v";
         }

         if ("a".equals(slot)) {
            slot = "legs/a";
         }

         if ("d".equals(slot)) {
            slot = "legs/d";
         }

         if (!"legs/v".equals(slot) && !"legs/a".equals(slot) && !"legs/d".equals(slot)) {
            NeedsOfNature.logSetupWarning("[NoN] Ignoring unknown accessory Trinkets slot '{}'.", raw);
         } else {
            slots.add(slot);
         }
      }
   }

   @Nullable
   private static Identifier parseItemId(JsonObject root) {
      String raw = parseString(root.get("id"));
      if (raw != null && !raw.isBlank()) {
         raw = raw.trim();
         if (!raw.contains(":")) {
            raw = "needsofnature:" + raw;
         }

         return Identifier.tryParse(raw);
      } else {
         return null;
      }
   }

   @Nullable
   private static String parseString(@Nullable JsonElement element) {
      return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
   }

   private static int parseInt(@Nullable JsonElement element, int fallback, int min, int max) {
      if (element == null || !element.isJsonPrimitive()) {
         return fallback;
      }

      if (!element.getAsJsonPrimitive().isNumber()) {
         return fallback;
      }

      try {
         int value = element.getAsInt();
         return Math.max(min, Math.min(max, value));
      } catch (RuntimeException ignored) {
         return fallback;
      }
   }

   private static boolean parseBoolean(@Nullable JsonElement element, boolean fallback) {
      if (element != null && element.isJsonPrimitive()) {
         JsonPrimitive primitive = element.getAsJsonPrimitive();
         return primitive.isBoolean() ? primitive.getAsBoolean() : fallback;
      } else {
         return fallback;
      }
   }

   public record AccessoryItemDefinition(
      Identifier id,
      int maxCount,
      int maxDurability,
      boolean showInItemGroup,
      Set<String> trinketsSlots,
      NonAccessoryEffects effects,
      Map<String, NonAccessoryDefinitions.TooltipColor> tooltipColors,
      NonAccessoryBehavior behavior,
      NonAccessoryItemRegistry.SkinOverlays skinOverlays,
      NonAccessoryItemRegistry.ItemClientModel clientModel,
      boolean dyeable,
      int defaultDyeColor,
      NonAccessoryItemRegistry.ActorVisualBehavior actorVisualBehavior
   ) {
   }

   public record ActorVisualBehavior(
      Set<String> virtualActorTags,
      @Nullable String animationModelGender,
      Map<String, NonAccessoryItemRegistry.VisualLayer> animationBoneTextures,
      int storedLiquidCapacityMl
   ) {
      public static final NonAccessoryItemRegistry.ActorVisualBehavior NONE = new NonAccessoryItemRegistry.ActorVisualBehavior(Set.of(), null, Map.of(), 0);

      public boolean substitutesMaleRole() {
         return this.virtualActorTags.contains("gender.male") && "male".equals(this.animationModelGender);
      }
   }

   public record ItemClientModel(@Nullable Identifier itemModel, List<NonAccessoryItemRegistry.VisualLayer> layers) {
      public static final NonAccessoryItemRegistry.ItemClientModel EMPTY = new NonAccessoryItemRegistry.ItemClientModel(null, List.of());

      public boolean isEmpty() {
         return this.itemModel == null && this.layers.isEmpty();
      }
   }

   public record SkinOverlays(
      List<NonAccessoryItemRegistry.VisualLayer> v, List<NonAccessoryItemRegistry.VisualLayer> a, List<NonAccessoryItemRegistry.VisualLayer> d
   ) {
      public static final NonAccessoryItemRegistry.SkinOverlays EMPTY = new NonAccessoryItemRegistry.SkinOverlays(List.of(), List.of(), List.of());

      public boolean isEmpty() {
         return this.v.isEmpty() && this.a.isEmpty() && this.d.isEmpty();
      }

      public List<NonAccessoryItemRegistry.VisualLayer> forSlot(String slotId) {
         if (slotId == null) {
            return List.of();
         } else {
            String slot = slotId.toLowerCase(Locale.ROOT);
            if (slot.equals("legs/v") || slot.startsWith("legs/v/")) {
               return this.v;
            } else if (slot.equals("legs/a") || slot.startsWith("legs/a/")) {
               return this.a;
            } else {
               return !slot.equals("legs/d") && !slot.startsWith("legs/d/") ? List.of() : this.d;
            }
         }
      }
   }

   public enum TintMode {
      NONE,
      DYE,
      STORED_LIQUID;

      static NonAccessoryItemRegistry.TintMode parse(@Nullable String raw) {
         if (raw != null && !raw.isBlank()) {
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
               case "dye" -> DYE;
               case "stored_liquid" -> STORED_LIQUID;
               default -> NONE;
            };
         } else {
            return NONE;
         }
      }
   }

   public record VisualLayer(Identifier texture, NonAccessoryItemRegistry.TintMode tintMode) {
   }
}
