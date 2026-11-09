package com.nonid.internal.animation.client.render.gecko;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class ModelVariantCandidates {
   private ModelVariantCandidates() {
   }

   static List<String> ordered(List<String> modelPaths, GenderedModelResolver.ModelGender gender) {
      LinkedHashSet<String> uniquePaths = new LinkedHashSet<>();
      if (modelPaths != null) {
         for (String path : modelPaths) {
            if (path != null) {
               String clean = path.trim();
               if (!clean.isEmpty()) {
                  uniquePaths.add(clean);
               }
            }
         }
      }

      if (uniquePaths.isEmpty()) {
         return List.of();
      }

      GenderedModelResolver.ModelGender safeGender = gender == null ? GenderedModelResolver.ModelGender.MALE : gender;

      String preferredSuffix = switch (safeGender) {
         case MALE -> ".m";
         case FEMALE -> ".f";
         case BOTH -> ".mf";
      };
      List<String> paths = List.copyOf(uniquePaths);
      List<String> candidates = new ArrayList<>();

      for (String path : paths) {
         candidates.add(path + preferredSuffix);
      }

      candidates.addAll(paths);

      for (String suffix : switch (safeGender) {
         case MALE -> List.of(".f");
         case FEMALE -> List.of(".m");
         case BOTH -> List.of(".m", ".f");
      }) {
         for (String path : paths) {
            candidates.add(path + suffix);
         }
      }

      return List.copyOf(new LinkedHashSet<>(candidates));
   }
}
