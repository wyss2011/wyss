package com.nonid;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class LiquidDonorHistory {
   public static final int MAX_ENTRIES = 3;
   private static final Gson GSON = new Gson();

   private LiquidDonorHistory() {
   }

   public static List<LiquidDonorHistory.Entry> append(
      Collection<LiquidDonorHistory.Entry> current, Identifier entityTypeId, @Nullable NeedsOfNature.PregnancyVariantData variantData
   ) {
      ArrayList<LiquidDonorHistory.Entry> entries = new ArrayList<>(normalize(current));
      entries.add(new LiquidDonorHistory.Entry(entityTypeId, variantData));
      return keepNewest(entries);
   }

   public static List<LiquidDonorHistory.Entry> normalize(@Nullable Collection<LiquidDonorHistory.Entry> entries) {
      if (entries != null && !entries.isEmpty()) {
         ArrayList<LiquidDonorHistory.Entry> normalized = new ArrayList<>();

         for (LiquidDonorHistory.Entry entry : entries) {
            if (entry != null && entry.entityTypeId() != null) {
               normalized.add(new LiquidDonorHistory.Entry(entry.entityTypeId(), entry.variantData()));
            }
         }

         return keepNewest(normalized);
      } else {
         return List.of();
      }
   }

   @Nullable
   public static String encode(Collection<LiquidDonorHistory.Entry> entries) {
      List<LiquidDonorHistory.Entry> normalized = normalize(entries);
      if (normalized.isEmpty()) {
         return null;
      }

      LiquidDonorHistory.StoredEntry[] stored = normalized.stream()
         .map(entry -> new LiquidDonorHistory.StoredEntry(entry.entityTypeId().toString(), entry.variantData()))
         .toArray(LiquidDonorHistory.StoredEntry[]::new);
      return GSON.toJson(stored);
   }

   public static List<LiquidDonorHistory.Entry> decode(@Nullable String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            LiquidDonorHistory.StoredEntry[] stored = (LiquidDonorHistory.StoredEntry[])GSON.fromJson(raw, LiquidDonorHistory.StoredEntry[].class);
            if (stored != null && stored.length != 0) {
               ArrayList<LiquidDonorHistory.Entry> decoded = new ArrayList<>();

               for (LiquidDonorHistory.StoredEntry entry : stored) {
                  if (entry != null && entry.entityTypeId() != null) {
                     Identifier entityTypeId = Identifier.tryParse(entry.entityTypeId());
                     if (entityTypeId != null) {
                        decoded.add(new LiquidDonorHistory.Entry(entityTypeId, entry.variantData()));
                     }
                  }
               }

               return keepNewest(decoded);
            } else {
               return List.of();
            }
         } catch (RuntimeException ignored) {
            return List.of();
         }
      } else {
         return List.of();
      }
   }

   private static List<LiquidDonorHistory.Entry> keepNewest(List<LiquidDonorHistory.Entry> entries) {
      if (entries != null && !entries.isEmpty()) {
         int fromIndex = Math.max(0, entries.size() - 3);
         return List.copyOf(entries.subList(fromIndex, entries.size()));
      } else {
         return List.of();
      }
   }

   @Nullable
   private static NeedsOfNature.PregnancyVariantData normalizeVariantData(@Nullable NeedsOfNature.PregnancyVariantData variantData) {
      return variantData != null && !variantData.isEmpty()
         ? new NeedsOfNature.PregnancyVariantData(variantData.components(), variantData.horseMarkingIndex())
         : null;
   }

   public record Entry(Identifier entityTypeId, @Nullable NeedsOfNature.PregnancyVariantData variantData) {
      public Entry {
         if (entityTypeId == null) {
            throw new IllegalArgumentException("entityTypeId cannot be null");
         }

         variantData = LiquidDonorHistory.normalizeVariantData(variantData);
      }
   }

   private record StoredEntry(String entityTypeId, @Nullable NeedsOfNature.PregnancyVariantData variantData) {
   }
}
