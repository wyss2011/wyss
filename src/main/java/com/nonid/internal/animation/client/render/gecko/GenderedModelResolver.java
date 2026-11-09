package com.nonid.internal.animation.client.render.gecko;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class GenderedModelResolver {
   private static final String MALE_SUFFIX = ".m";
   private static final String FEMALE_SUFFIX = ".f";
   private static final String BOTH_SUFFIX = ".mf";
   private static final List<String> ALL_SUFFIXES = List.of(".mf", ".m", ".f");

   private GenderedModelResolver() {
   }

   @Nullable
   public static Identifier resolveExisting(@Nullable Identifier baseModel, GenderedModelResolver.ModelGender gender, Predicate<Identifier> exists) {
      if (baseModel != null && gender != null && exists != null) {
         if (hasGenderSuffix(baseModel)) {
            return exists.test(baseModel) ? baseModel : null;
         }

         List<String> suffixes = preferredSuffixes(gender);
         Identifier preferred = withSuffix(baseModel, suffixes.getFirst());
         if (exists.test(preferred)) {
            return preferred;
         }

         if (exists.test(baseModel)) {
            return baseModel;
         }

         for (int i = 1; i < suffixes.size(); i++) {
            Identifier candidate = withSuffix(baseModel, suffixes.get(i));
            if (exists.test(candidate)) {
               return candidate;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static boolean hasGenderSuffix(@Nullable Identifier model) {
      return model != null && hasGenderSuffix(model.getPath());
   }

   public static Identifier stripGenderSuffix(Identifier model) {
      String path = model.getPath();

      for (String suffix : ALL_SUFFIXES) {
         if (path.endsWith(suffix)) {
            return Identifier.of(model.getNamespace(), path.substring(0, path.length() - suffix.length()));
         }
      }

      return model;
   }

   public static Identifier withGender(Identifier baseModel, GenderedModelResolver.ModelGender gender) {
      return withSuffix(stripGenderSuffix(baseModel), preferredSuffixes(gender).getFirst());
   }

   public static GenderedModelResolver.ModelGender fromMask(int mask) {
      return switch (mask & 3) {
         case 2 -> GenderedModelResolver.ModelGender.FEMALE;
         case 3 -> GenderedModelResolver.ModelGender.BOTH;
         default -> GenderedModelResolver.ModelGender.MALE;
      };
   }

   private static boolean hasGenderSuffix(String path) {
      for (String suffix : ALL_SUFFIXES) {
         if (path.endsWith(suffix)) {
            return true;
         }
      }

      return false;
   }

   private static List<String> preferredSuffixes(GenderedModelResolver.ModelGender gender) {
      return switch (gender) {
         case MALE -> List.of(".m", ".f");
         case FEMALE -> List.of(".f", ".m");
         case BOTH -> List.of(".mf", ".m", ".f");
      };
   }

   private static Identifier withSuffix(Identifier model, String suffix) {
      return Identifier.of(model.getNamespace(), model.getPath() + suffix);
   }

   public enum ModelGender {
      MALE,
      FEMALE,
      BOTH;
   }
}
