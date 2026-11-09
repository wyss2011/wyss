package com.nonid.integration;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.nonid.NeedsOfNature;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class NonCreditsData {
   private static final Gson GSON = new Gson();
   private static final String CREDITS_RESOURCE = "/assets/needsofnature/credits.json";

   private NonCreditsData() {
   }

   static NonCreditsData.Credits load() {
      try (InputStream stream = NonCreditsData.class.getResourceAsStream("/assets/needsofnature/credits.json")) {
         if (stream == null) {
            NeedsOfNature.LOGGER.warn("[NoN] Bundled credits file is missing: {}", "/assets/needsofnature/credits.json");
            return fallback();
         } else {
            NonCreditsData.RawCredits raw = (NonCreditsData.RawCredits)GSON.fromJson(
               new InputStreamReader(stream, StandardCharsets.UTF_8), NonCreditsData.RawCredits.class
            );
            NonCreditsData.Credits credits = normalize(raw);
            if (credits.notice() == null && credits.sections().isEmpty() && credits.resources().isEmpty() && credits.projectLinks().isEmpty()) {
               NeedsOfNature.LOGGER.warn("[NoN] Bundled credits file contains no valid entries.");
               return fallback();
            } else {
               return credits;
            }
         }
      } catch (Exception e) {
         NeedsOfNature.LOGGER.warn("[NoN] Failed to read bundled credits; using fallback credits.", e);
         return fallback();
      }
   }

   private static NonCreditsData.Credits normalize(NonCreditsData.RawCredits raw) {
      if (raw == null) {
         return new NonCreditsData.Credits(null, List.of(), List.of(), List.of());
      }

      List<NonCreditsData.Section> sections = new ArrayList<>();
      if (raw.sections != null) {
         for (NonCreditsData.RawSection rawSection : raw.sections) {
            if (rawSection != null && !blank(rawSection.title)) {
               List<NonCreditsData.Contributor> contributors = new ArrayList<>();
               if (rawSection.contributors != null) {
                  for (NonCreditsData.RawContributor rawContributor : rawSection.contributors) {
                     if (rawContributor != null && !blank(rawContributor.name)) {
                        contributors.add(
                           new NonCreditsData.Contributor(rawContributor.name.trim(), cleanStrings(rawContributor.roles), clean(rawContributor.contribution))
                        );
                     }
                  }
               }

               if (!contributors.isEmpty()) {
                  sections.add(new NonCreditsData.Section(rawSection.title.trim(), List.copyOf(contributors)));
               }
            }
         }
      }

      List<NonCreditsData.ResourceCredit> resources = new ArrayList<>();
      if (raw.resources != null) {
         for (NonCreditsData.RawResource rawResource : raw.resources) {
            if (rawResource != null && !blank(rawResource.title)) {
               resources.add(
                  new NonCreditsData.ResourceCredit(
                     clean(rawResource.category),
                     rawResource.title.trim(),
                     cleanStrings(rawResource.assets),
                     clean(rawResource.creator),
                     clean(rawResource.source),
                     validWebUrl(rawResource.sourceUrl),
                     clean(rawResource.license),
                     validWebUrl(rawResource.licenseUrl),
                     rawResource.modified
                  )
               );
            }
         }
      }

      List<NonCreditsData.ProjectLink> projectLinks = new ArrayList<>();
      if (raw.projectLinks != null) {
         for (NonCreditsData.RawLink rawLink : raw.projectLinks) {
            if (rawLink != null && !blank(rawLink.label)) {
               String url = validWebUrl(rawLink.url);
               if (url != null) {
                  projectLinks.add(new NonCreditsData.ProjectLink(rawLink.label.trim(), url));
               }
            }
         }
      }

      return new NonCreditsData.Credits(clean(raw.notice), List.copyOf(sections), List.copyOf(resources), List.copyOf(projectLinks));
   }

   private static String validWebUrl(String value) {
      if (blank(value)) {
         return null;
      }

      try {
         URI uri = URI.create(value.trim());
         String scheme = uri.getScheme();
         if (uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return uri.toString();
         }
      } catch (IllegalArgumentException var3) {
      }

      NeedsOfNature.LOGGER.warn("[NoN] Ignoring invalid credits URL: {}", value);
      return null;
   }

   private static List<String> cleanStrings(List<String> values) {
      return values != null && !values.isEmpty() ? values.stream().filter(value -> !blank(value)).map(String::trim).toList() : List.of();
   }

   private static String clean(String value) {
      return blank(value) ? null : value.trim();
   }

   private static boolean blank(String value) {
      return value == null || value.isBlank();
   }

   private static NonCreditsData.Credits fallback() {
      NonCreditsData.Contributor contributor = new NonCreditsData.Contributor("L1Z0", List.of("Project Lead", "Programming"), null);
      return new NonCreditsData.Credits(null, List.of(new NonCreditsData.Section("Development", List.of(contributor))), List.of(), List.of());
   }

   record Contributor(String name, List<String> roles, String contribution) {
   }

   record Credits(
      String notice, List<NonCreditsData.Section> sections, List<NonCreditsData.ResourceCredit> resources, List<NonCreditsData.ProjectLink> projectLinks
   ) {
   }

   record ProjectLink(String label, String url) {
   }

   private static final class RawContributor {
      private String name;
      private List<String> roles;
      private String contribution;
   }

   private static final class RawCredits {
      private String notice;
      private List<NonCreditsData.RawSection> sections;
      private List<NonCreditsData.RawResource> resources;
      @SerializedName("project_links")
      private List<NonCreditsData.RawLink> projectLinks;
   }

   private static final class RawLink {
      private String label;
      private String url;
   }

   private static final class RawResource {
      private String category;
      private String title;
      private List<String> assets;
      private String creator;
      private String source;
      @SerializedName("source_url")
      private String sourceUrl;
      private String license;
      @SerializedName("license_url")
      private String licenseUrl;
      private boolean modified;
   }

   private static final class RawSection {
      private String title;
      private List<NonCreditsData.RawContributor> contributors;
   }

   record ResourceCredit(
      String category, String title, List<String> assets, String creator, String source, String sourceUrl, String license, String licenseUrl, boolean modified
   ) {
   }

   record Section(String title, List<NonCreditsData.Contributor> contributors) {
   }
}
