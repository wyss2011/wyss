package com.nonid.client.intiface;

import com.nonid.NeedsOfNature;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class NonIntifaceDependencyManager {
   private static final String GENERATED_MOD_ID = "needsofnature_intiface_libraries";
   private static final String GENERATED_JAR_NAME = "needsofnature-intiface-libs-generated.jar";
   private static final String BUNDLE_MARKER = "META-INF/needsofnature-intiface-libs.sha256";
   private static final String MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2/";
   private static final String CORE_CLASS_ENTRY = "io/github/blackspherefollower/buttplug4j/ButtplugException.class";
   private static final String CONNECTOR_CLASS_ENTRY = "io/github/blackspherefollower/buttplug4j/connectors/jetty/websocket/client/ButtplugClientWSClient.class";
   private static final String JACKSON_CLASS_ENTRY = "com/fasterxml/jackson/databind/ObjectMapper.class";
   private static final List<NonIntifaceDependencyManager.Artifact> ARTIFACTS = List.of(
      new NonIntifaceDependencyManager.Artifact(
         "io.github.blackspherefollower",
         "buttplug4j.connectors.jetty.websocket.client",
         "4.0.278",
         "6ffb6507595137ae6cd3cda932d47e3b6ef32e12393c58ef60d7ec231ff2752c",
         6320L
      ),
      new NonIntifaceDependencyManager.Artifact(
         "io.github.blackspherefollower", "buttplug4j", "4.0.278", "094055e1bf64bc416b7a984b80d5216febbc20dcf5b5149ca1e4513056ccc4fa", 82360L
      ),
      new NonIntifaceDependencyManager.Artifact(
         "org.eclipse.jetty.websocket", "websocket-client", "9.4.58.v20250814", "fdb8648fb1139ab001c8d5ebc4f3ab5baf007f48bd86ec2969c504ac3011f878", 46116L
      ),
      new NonIntifaceDependencyManager.Artifact(
         "org.eclipse.jetty.websocket", "websocket-common", "9.4.58.v20250814", "07c62b7ceaff2d7160aff98b912f28da9f2c66ed7adec972e39af5176afec1ae", 217092L
      ),
      new NonIntifaceDependencyManager.Artifact(
         "org.eclipse.jetty.websocket", "websocket-api", "9.4.58.v20250814", "213d146065ebf2cbfa8a9c06b4cdfe79f94c0af3f93c15d9e67c0e3af015067f", 52516L
      ),
      new NonIntifaceDependencyManager.Artifact(
         "org.eclipse.jetty", "jetty-client", "9.4.58.v20250814", "629371e5f65f321832930538bd485e5fd637d180412ea75f592881921e2d4b83", 331779L
      ),
      new NonIntifaceDependencyManager.Artifact(
         "org.eclipse.jetty", "jetty-http", "9.4.58.v20250814", "8f49b8583fc8dbbfe3a0ba80a04c97df64ebabca2981b56ee403c989aba9d4da", 253188L
      ),
      new NonIntifaceDependencyManager.Artifact(
         "org.eclipse.jetty", "jetty-io", "9.4.58.v20250814", "f55b2cc7c05244fd7b1773e99979b740002b0186e45b8a688243d89c7006fe21", 184923L
      ),
      new NonIntifaceDependencyManager.Artifact(
         "org.eclipse.jetty", "jetty-util", "9.4.58.v20250814", "8d591679d318d20c8fb2b1e83900562b3c4571ff7a189696be700293e0204146", 595392L
      ),
      new NonIntifaceDependencyManager.Artifact(
         "com.fasterxml.jackson.core", "jackson-annotations", "2.20", "959a2ffb2d591436f51f183c6a521fc89347912f711bf0cae008cdf045d95319", 79195L
      ),
      new NonIntifaceDependencyManager.Artifact(
         "com.fasterxml.jackson.core", "jackson-core", "2.20.1", "ffab4d957daa2796cf24cb66d0b78a7090f1bcbe17c3a4578f09affaaf137089", 593204L
      ),
      new NonIntifaceDependencyManager.Artifact(
         "com.fasterxml.jackson.core", "jackson-databind", "2.20.1", "34bbeb4526fff4f8565b12106bf85a6afcbae858966d489b54214ac46b2e26e8", 1688464L
      )
   );
   private static final ExecutorService INSTALL_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
      Thread thread = new Thread(r, "NoN Intiface Library Installer");
      thread.setDaemon(true);
      return thread;
   });
   private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15L)).followRedirects(Redirect.NORMAL).build();
   private static volatile NonIntifaceDependencyManager.InstallState installState = NonIntifaceDependencyManager.InstallState.IDLE;
   private static volatile String detail = "";
   private static volatile long cachedBundleModified = Long.MIN_VALUE;
   private static volatile boolean cachedBundleValid = false;

   private NonIntifaceDependencyManager() {
   }

   public static boolean runtimeLibrariesAvailable() {
      return NonIntifaceBridge.isRuntimeAvailable();
   }

   public static boolean librariesUsable() {
      return runtimeLibrariesAvailable() && !generatedBundleOutdated() && installState != NonIntifaceDependencyManager.InstallState.INSTALLED_RESTART_REQUIRED;
   }

   public static boolean installRequired() {
      return !runtimeLibrariesAvailable() || generatedBundleOutdated();
   }

   public static boolean generatedBundleOutdated() {
      return generatedBundleExists() && !generatedBundleValid();
   }

   private static boolean generatedBundleExists() {
      return Files.exists(generatedBundlePath());
   }

   public static boolean generatedBundleValid() {
      Path bundle = generatedBundlePath();
      long modified = -1L;

      try {
         if (Files.exists(bundle)) {
            modified = Files.getLastModifiedTime(bundle).toMillis();
         }
      } catch (IOException ignored) {
         modified = -1L;
      }

      if (modified == cachedBundleModified) {
         return cachedBundleValid;
      }

      boolean valid = verifyGeneratedBundle(bundle);
      cachedBundleModified = modified;
      cachedBundleValid = valid;
      return valid;
   }

   public static boolean restartRequired() {
      return installState == NonIntifaceDependencyManager.InstallState.INSTALLED_RESTART_REQUIRED || !runtimeLibrariesAvailable() && generatedBundleValid();
   }

   public static boolean installing() {
      return installState == NonIntifaceDependencyManager.InstallState.DOWNLOADING || installState == NonIntifaceDependencyManager.InstallState.BUILDING;
   }

   public static Text statusText() {
      if (installing()) {
         return Text.translatable("config.needsofnature.intiface.libs.status.installing", new Object[]{detail});
      } else if (restartRequired()) {
         return Text.translatable("config.needsofnature.intiface.libs.status.restart_required");
      } else if (installState == NonIntifaceDependencyManager.InstallState.FAILED) {
         return Text.translatable("config.needsofnature.intiface.libs.status.failed", new Object[]{detail});
      } else if (generatedBundleOutdated()) {
         return Text.translatable("config.needsofnature.intiface.libs.status.outdated");
      } else {
         return runtimeLibrariesAvailable()
            ? Text.translatable("config.needsofnature.intiface.libs.status.available")
            : Text.translatable("config.needsofnature.intiface.libs.status.missing", new Object[]{totalSizeMbText()});
      }
   }

   public static String totalSizeMbText() {
      double mb = totalBytes() / 1024.0 / 1024.0;
      return String.format(Locale.ROOT, "%.1f MB", mb);
   }

   public static void installAsync(Runnable callback) {
      if (!installing()) {
         installState = NonIntifaceDependencyManager.InstallState.DOWNLOADING;
         detail = "";
         INSTALL_EXECUTOR.execute(() -> {
            try {
               List<NonIntifaceDependencyManager.DownloadedArtifact> downloads = downloadArtifacts();
               installState = NonIntifaceDependencyManager.InstallState.BUILDING;
               detail = "Building library bundle";
               buildGeneratedBundle(downloads);
               cachedBundleModified = Long.MIN_VALUE;
               cachedBundleValid = false;
               installState = NonIntifaceDependencyManager.InstallState.INSTALLED_RESTART_REQUIRED;
               detail = "";
               NeedsOfNature.LOGGER.info("[NoN] Installed Buttplug.io libraries to {}", generatedBundlePath());
            } catch (Exception e) {
               installState = NonIntifaceDependencyManager.InstallState.FAILED;
               detail = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.getClass().getSimpleName();
               NeedsOfNature.LOGGER.warn("[NoN] Failed to install Buttplug.io libraries.", e);
            } finally {
               if (callback != null) {
                  MinecraftClient client = MinecraftClient.getInstance();
                  if (client != null) {
                     client.execute(callback);
                  }
               }
            }
         });
      }
   }

   private static List<NonIntifaceDependencyManager.DownloadedArtifact> downloadArtifacts() throws Exception {
      List<NonIntifaceDependencyManager.DownloadedArtifact> result = new ArrayList<>(ARTIFACTS.size());

      for (NonIntifaceDependencyManager.Artifact artifact : ARTIFACTS) {
         detail = artifact.fileName();
         URI uri = artifact.mavenUri();
         HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60L)).GET().build();
         HttpResponse<byte[]> response = HTTP_CLIENT.send(request, BodyHandlers.ofByteArray());
         if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Download failed for " + artifact.fileName() + " (" + response.statusCode() + ")");
         }

         byte[] bytes = response.body();
         String actualHash = sha256(bytes);
         if (!artifact.sha256().equalsIgnoreCase(actualHash)) {
            throw new IOException("Checksum mismatch for " + artifact.fileName());
         }

         result.add(new NonIntifaceDependencyManager.DownloadedArtifact(artifact, bytes));
      }

      return result;
   }

   private static void buildGeneratedBundle(List<NonIntifaceDependencyManager.DownloadedArtifact> downloads) throws IOException {
      Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
      Files.createDirectories(modsDir);
      Path bundle = generatedBundlePath();
      Path tempBundle = modsDir.resolve("needsofnature-intiface-libs-generated.jar.tmp");
      deleteIfExists(tempBundle);

      try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(tempBundle))) {
         putEntry(out, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMulti-Release: true\n".getBytes(StandardCharsets.UTF_8));
         putEntry(out, "fabric.mod.json", generatedFabricModJson().getBytes(StandardCharsets.UTF_8));
         mergeDownloadedJars(out, downloads);
         putEntry(out, "META-INF/needsofnature-intiface-libs.sha256", expectedBundleMarker().getBytes(StandardCharsets.UTF_8));
      }

      Files.move(tempBundle, bundle, StandardCopyOption.REPLACE_EXISTING);
   }

   private static String generatedFabricModJson() {
      return "{\n  \"schemaVersion\": 1,\n  \"id\": \"%s\",\n  \"version\": \"1.0.0\",\n  \"name\": \"NeedsOfNature Buttplug.io Libraries\",\n  \"description\": \"Optional client-side libraries for NeedsOfNature Buttplug.io integration.\",\n  \"environment\": \"client\"\n}\n"
         .formatted("needsofnature_intiface_libraries");
   }

   private static void mergeDownloadedJars(JarOutputStream out, List<NonIntifaceDependencyManager.DownloadedArtifact> downloads) throws IOException {
      Set<String> written = new HashSet<>();
      written.add("META-INF/MANIFEST.MF");
      written.add("fabric.mod.json");
      Map<String, LinkedHashSet<String>> serviceEntries = new LinkedHashMap<>();

      for (NonIntifaceDependencyManager.DownloadedArtifact download : downloads) {
         JarEntry entry;
         try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(download.bytes()))) {
            while ((entry = in.getNextJarEntry()) != null) {
               String name = entry.getName();
               if (!shouldSkipShadedEntry(entry, name)) {
                  byte[] bytes = in.readAllBytes();
                  if (name.startsWith("META-INF/services/")) {
                     serviceEntries.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(new String(bytes, StandardCharsets.UTF_8).strip());
                  } else if (written.add(name)) {
                     putEntry(out, name, bytes);
                  }
               }
            }
         }
      }

      for (Entry<String, LinkedHashSet<String>> service : serviceEntries.entrySet()) {
         if (written.add(service.getKey())) {
            String text = String.join("\n", service.getValue()).strip();
            if (!text.isBlank()) {
               text = text + "\n";
            }

            putEntry(out, service.getKey(), text.getBytes(StandardCharsets.UTF_8));
         }
      }
   }

   private static boolean shouldSkipShadedEntry(JarEntry entry, String name) {
      if (entry.isDirectory()) {
         return true;
      }

      if (name.equals("fabric.mod.json")) {
         return true;
      }

      if (name.equals("module-info.class")) {
         return true;
      }

      if (name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
         return true;
      }

      if (name.equalsIgnoreCase("META-INF/INDEX.LIST")) {
         return true;
      }

      if (name.startsWith("META-INF/maven/")) {
         return true;
      }

      if (!name.startsWith("META-INF/")) {
         return false;
      }

      String upper = name.toUpperCase(Locale.ROOT);
      return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC");
   }

   private static boolean verifyGeneratedBundle(Path bundle) {
      if (!Files.exists(bundle)) {
         return false;
      }

      try (JarFile jar = new JarFile(bundle.toFile())) {
         if (jar.getEntry("fabric.mod.json") == null) {
            return false;
         }

         JarEntry marker = jar.getJarEntry("META-INF/needsofnature-intiface-libs.sha256");
         if (marker == null) {
            return false;
         }

         try (InputStream in = jar.getInputStream(marker)) {
            String actualMarker = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (!expectedBundleMarker().equals(actualMarker)) {
               return false;
            }
         }

         return jar.getEntry("io/github/blackspherefollower/buttplug4j/ButtplugException.class") != null
            && jar.getEntry("io/github/blackspherefollower/buttplug4j/connectors/jetty/websocket/client/ButtplugClientWSClient.class") != null
            && jar.getEntry("com/fasterxml/jackson/databind/ObjectMapper.class") != null;
      } catch (IOException e) {
         return false;
      }
   }

   private static String expectedBundleMarker() {
      StringBuilder marker = new StringBuilder("format=shaded-v1\n");

      for (NonIntifaceDependencyManager.Artifact artifact : ARTIFACTS) {
         marker.append(artifact.group())
            .append(':')
            .append(artifact.artifact())
            .append(':')
            .append(artifact.version())
            .append(' ')
            .append(artifact.sha256())
            .append('\n');
      }

      return marker.toString();
   }

   private static void putEntry(JarOutputStream out, String name, byte[] bytes) throws IOException {
      JarEntry entry = new JarEntry(name);
      entry.setTime(0L);
      out.putNextEntry(entry);

      try (InputStream in = new ByteArrayInputStream(bytes)) {
         in.transferTo(out);
      }

      out.closeEntry();
   }

   private static Path generatedBundlePath() {
      return FabricLoader.getInstance().getGameDir().resolve("mods").resolve("needsofnature-intiface-libs-generated.jar");
   }

   private static long totalBytes() {
      long total = 0L;

      for (NonIntifaceDependencyManager.Artifact artifact : ARTIFACTS) {
         total += artifact.bytes();
      }

      return total;
   }

   private static String sha256(byte[] bytes) throws IOException {
      return sha256(new ByteArrayInputStream(bytes));
   }

   private static String sha256(InputStream input) throws IOException {
      try {
         MessageDigest digest = MessageDigest.getInstance("SHA-256");
         byte[] buffer = new byte[8192];

         int read;
         while ((read = input.read(buffer)) >= 0) {
            digest.update(buffer, 0, read);
         }

         return HexFormat.of().formatHex(digest.digest());
      } catch (NoSuchAlgorithmException e) {
         throw new IOException("SHA-256 unavailable", e);
      }
   }

   private static void deleteIfExists(Path path) {
      try {
         if (!Files.exists(path)) {
            return;
         }

         if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.walk(path)) {
               stream.sorted(Comparator.reverseOrder()).forEach(child -> {
                  try {
                     Files.deleteIfExists(child);
                  } catch (IOException var2) {
                  }
               });
            }
         } else {
            Files.deleteIfExists(path);
         }
      } catch (IOException var6) {
      }
   }

   private record Artifact(String group, String artifact, String version, String sha256, long bytes) {
      String fileName() {
         return this.artifact + "-" + this.version + ".jar";
      }

      URI mavenUri() {
         String groupPath = this.group.replace('.', '/');
         return URI.create("https://repo.maven.apache.org/maven2/" + groupPath + "/" + this.artifact + "/" + this.version + "/" + this.fileName());
      }
   }

   private record DownloadedArtifact(NonIntifaceDependencyManager.Artifact artifact, byte[] bytes) {
   }

   private enum InstallState {
      IDLE,
      DOWNLOADING,
      BUILDING,
      INSTALLED_RESTART_REQUIRED,
      FAILED;
   }
}
