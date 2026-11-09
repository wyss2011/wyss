package com.nonid.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import com.nonid.pack.NonExternalPackProvider;
import com.nonid.pack.NonPackRootResolver;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

public final class NonPackStartupWarnings {
   private static final String WARNING_PATH = "assets/needsofnature/non_warnings.json";
   private static boolean checkedThisSession = false;

   private NonPackStartupWarnings() {
   }

   public static void clientTick(MinecraftClient client) {
      if (client != null && !checkedThisSession) {
         if (client.getOverlay() == null) {
            if (client.currentScreen instanceof TitleScreen) {
               checkedThisSession = true;
               NonPackStartupWarnings.Warning warning = findHighestPriorityWarning();
               if (warning != null && !isAcknowledged(warning.id())) {
                  client.setScreen(new NonPackWarningScreen(client.currentScreen, warning));
               }
            }
         }
      }
   }

   public static void acknowledge(String warningId) {
      if (warningId != null && !warningId.isBlank()) {
         NonConfig config = NeedsOfNature.getLocalConfig();
         if (config != null && config.acknowledgeStartupWarning(warningId)) {
            config.save();
         }
      }
   }

   @Nullable
   private static NonPackStartupWarnings.Warning findHighestPriorityWarning() {
      Path packsDir = NonExternalPackProvider.resolveDefaultPacksDir();
      List<String> discovered = NonExternalPackProvider.listExternalPackFileNames(packsDir);
      if (discovered.isEmpty()) {
         return null;
      }

      NonConfig config = NeedsOfNature.getLocalConfig();
      List<String> configuredOrder = config == null ? List.of() : config.getExternalNoNPackLoadOrder();

      for (String packName : NonExternalPackProvider.normalizeConfiguredOrder(configuredOrder, discovered)) {
         NonPackStartupWarnings.Warning warning = readWarning(packsDir.resolve(packName), packName);
         if (warning != null) {
            return warning;
         }
      }

      return null;
   }

   @Nullable
   private static NonPackStartupWarnings.Warning readWarning(Path packPath, String packName) {
      if (packPath != null && packName != null) {
         try {
            Optional<NonPackRootResolver.ResolvedRoot> resolved = NonPackRootResolver.resolve(packPath);
            if (resolved.isEmpty()) {
               return null;
            }

            NonPackRootResolver.ResolvedRoot root = resolved.get();
            if (!root.isZip()) {
               Path warningPath = root.rootPath()
                  .resolve("assets/needsofnature/non_warnings.json".replace("/", root.rootPath().getFileSystem().getSeparator()));
               if (!Files.isRegularFile(warningPath)) {
                  return null;
               }

               try (Reader reader = Files.newBufferedReader(warningPath, StandardCharsets.UTF_8)) {
                  return parseWarning(reader, packName);
               }
            } else {
               try (ZipFile zip = new ZipFile(packPath.toFile())) {
                  ZipEntry entry = zip.getEntry(root.prefixPath("assets/needsofnature/non_warnings.json"));
                  if (entry == null || entry.isDirectory()) {
                     return null;
                  }

                  try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                     return parseWarning(reader, packName);
                  }
               }
            }
         } catch (IOException | RuntimeException e) {
            NeedsOfNature.LOGGER.warn("[NoN] Failed to load startup warning from pack {}.", packName, e);
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static NonPackStartupWarnings.Warning parseWarning(Reader reader, String packName) {
      JsonElement root = JsonParser.parseReader(reader);
      if (!root.isJsonObject()) {
         warnInvalid(packName, "root must be an object");
         return null;
      }

      JsonObject obj = root.getAsJsonObject();
      String id = stringOrNull(obj, "id");
      if (id != null && !id.isBlank()) {
         Text title = readText(obj, "title", "title_key");
         Text body = readText(obj, "text", "text_key");
         if (title != null && body != null) {
            return new NonPackStartupWarnings.Warning(packName, id.trim(), title, body);
         }

         warnInvalid(packName, "missing title/text or title_key/text_key");
         return null;
      } else {
         warnInvalid(packName, "missing id");
         return null;
      }
   }

   @Nullable
   private static Text readText(JsonObject obj, String literalKey, String translationKey) {
      String key = stringOrNull(obj, translationKey);
      if (key != null && !key.isBlank()) {
         return Text.translatable(key.trim());
      }

      String literal = stringOrNull(obj, literalKey);
      return literal != null && !literal.isBlank() ? parseFormattedLiteral(literal) : null;
   }

   private static MutableText parseFormattedLiteral(String raw) {
      MutableText out = Text.literal("");
      List<Formatting> active = new ArrayList<>();
      StringBuilder segment = new StringBuilder();

      for (int i = 0; i < raw.length(); i++) {
         char c = raw.charAt(i);
         if (c == '&' && i + 1 < raw.length()) {
            Formatting formatting = Formatting.byCode(raw.charAt(i + 1));
            if (formatting == null) {
               segment.append(c);
            } else {
               appendSegment(out, segment, active);
               i++;
               if (formatting == Formatting.RESET) {
                  active.clear();
               } else if (formatting.isColor()) {
                  active.removeIf(Formatting::isColor);
                  active.add(formatting);
               } else if (!active.contains(formatting)) {
                  active.add(formatting);
               }
            }
         } else {
            segment.append(c);
         }
      }

      appendSegment(out, segment, active);
      return out;
   }

   private static void appendSegment(MutableText out, StringBuilder segment, List<Formatting> active) {
      if (!segment.isEmpty()) {
         MutableText text = Text.literal(segment.toString());
         if (!active.isEmpty()) {
            text.formatted(active.toArray(Formatting[]::new));
         }

         out.append(text);
         segment.setLength(0);
      }
   }

   @Nullable
   private static String stringOrNull(JsonObject obj, String key) {
      if (obj != null && key != null && obj.has(key)) {
         JsonElement element = obj.get(key);
         if (element != null && element.isJsonPrimitive()) {
            try {
               return element.getAsString();
            } catch (RuntimeException e) {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean isAcknowledged(String warningId) {
      NonConfig config = NeedsOfNature.getLocalConfig();
      return config != null && config.isStartupWarningAcknowledged(warningId);
   }

   private static void warnInvalid(String packName, String reason) {
      NeedsOfNature.LOGGER.warn("[NoN] Ignoring startup warning in pack {}: {}.", packName, reason);
   }

   public record Warning(String packName, String id, Text title, Text body) {
   }
}
