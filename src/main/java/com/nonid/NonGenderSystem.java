package com.nonid;

import com.nonid.api.NonChangeSource;
import com.nonid.data.NonEntityProfiles;
import com.nonid.network.GenderSelectionPromptS2CPayload;
import com.nonid.network.SetPlayerGenderC2SPayload;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.Load;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

final class NonGenderSystem {
   private static final String GENDER_TAG_MALE = "gender.male";
   private static final String GENDER_TAG_FEMALE = "gender.female";
   private static final String GENDER_TAG_SELECTED = "gender.selected";
   private static final int DEFAULT_PLAYER_GENDER_MASK = 2;

   private NonGenderSystem() {
   }

   static void registerMobGenderTags() {
      ServerEntityEvents.ENTITY_LOAD.register((Load)(entity, world) -> {
         if (entity instanceof MobEntity) {
            if (entity.getType() != NeedsOfNature.HORSE_LIQUID_COLLECTOR_ENTITY_TYPE) {
               if (entity.getType() != NeedsOfNature.PREGNANCY_EGG_ENTITY_TYPE) {
                  Set<String> tags = entity.getCommandTags();
                  boolean hasMale = tags.contains("gender.male");
                  boolean hasFemale = tags.contains("gender.female");
                  if (!hasMale && !hasFemale) {
                     NonConfig.GenderSpawnChances chances = resolveGenderSpawnChances(entity);
                     int roll = world.getRandom().nextInt(100);
                     if (roll < chances.male()) {
                        entity.addCommandTag("gender.male");
                        hasMale = true;
                     } else if (roll < chances.male() + chances.female()) {
                        entity.addCommandTag("gender.female");
                        hasFemale = true;
                     } else {
                        entity.addCommandTag("gender.male");
                        entity.addCommandTag("gender.female");
                        hasMale = true;
                        hasFemale = true;
                     }
                  }

                  if (entity instanceof GenderHolder holder) {
                     int mask = 0;
                     if (hasMale) {
                        mask |= 1;
                     }

                     if (hasFemale) {
                        mask |= 2;
                     }

                     holder.setGenderMask(mask);
                  }

                  if (entity instanceof EnergyHolder holder && world instanceof ServerWorld serverWorld) {
                     holder.ensureEnergyInitialized(serverWorld);
                  }
               }
            }
         }
      });
   }

   static void registerCowMilkingGenderGate() {
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
         NonConfig config = NeedsOfNature.getConfig();
         if (config == null || !config.femaleCowOnlyMilking()) {
            return ActionResult.PASS;
         }

         if (entity instanceof CowEntity cow) {
            if (cow.getType() != EntityType.COW) {
               return ActionResult.PASS;
            } else {
               ItemStack stack = player.getStackInHand(hand);
               if (!stack.isEmpty() && stack.isOf(Items.BUCKET)) {
                  return (ActionResult)(canCowBeMilked(cow) ? ActionResult.PASS : ActionResult.FAIL);
               } else {
                  return ActionResult.PASS;
               }
            }
         } else {
            return ActionResult.PASS;
         }
      });
   }

   static void registerPlayerGenderRequests() {
      ServerPlayNetworking.registerGlobalReceiver(SetPlayerGenderC2SPayload.ID, (payload, context) -> context.server().execute(() -> {
         ServerPlayerEntity player = context.player();
         if (player != null) {
            handleClientGenderRequest(player, payload.mask(), payload.initialSelection());
         }
      }));
   }

   static void ensurePlayerGenderInitialized(ServerPlayerEntity player) {
      if (player != null) {
         int mask = 0;
         if (player instanceof GenderHolder holder) {
            mask = holder.getGenderMask() & 3;
         }

         if (mask == 0) {
            mask = resolveGenderMaskFromTags(player);
         }

         if (mask != 0) {
            markPlayerGenderSelected(player);
         }

         if (mask == 0) {
            NonConfig config = NeedsOfNature.getConfig();
            if (config.requirePlayerGenderSelectionOnJoin()) {
               sendGenderSelectionPrompt(player);
            } else {
               int var3 = 2;
               applyPlayerGenderMask(player, var3, true, NonChangeSource.SYSTEM);
            }
         } else {
            applyGenderMask(player, mask);
            if (NeedsOfNature.getConfig().requirePlayerGenderSelectionOnJoin() && !hasPlayerGenderSelected(player)) {
               sendGenderSelectionPrompt(player);
            }
         }
      }
   }

   static int setPlayerGenderFromCommand(ServerPlayerEntity player, int mask) {
      return setPlayerGenderFromCommand(player, mask, NonChangeSource.COMMAND);
   }

   static int setPlayerGenderFromCommand(ServerPlayerEntity player, int mask, NonChangeSource source) {
      int normalized = normalizeGenderMask(mask);
      applyPlayerGenderMask(player, normalized, true, source == null ? NonChangeSource.COMMAND : source);
      return normalized;
   }

   static void resetPlayerGenderSelection(ServerPlayerEntity player) {
      if (player != null) {
         clearPlayerGenderSelectionState(player);
         sendGenderSelectionPrompt(player);
      }
   }

   static boolean canClientChangeGenderFreely() {
      return NeedsOfNature.getConfig().allowPlayerGenderChangeAnytime();
   }

   static boolean isStartingGenderAllowed(int mask) {
      int normalized = normalizeGenderMask(mask);
      int allowed = NeedsOfNature.getConfig().getAllowedStartingGenderMask();
      return (allowed & startingGenderChoiceBit(normalized)) != 0;
   }

   private static void handleClientGenderRequest(ServerPlayerEntity player, int requestedMask, boolean initialSelection) {
      NonConfig config = NeedsOfNature.getConfig();
      int normalized = normalizeGenderMask(requestedMask);
      boolean selected = hasPlayerGenderSelected(player);
      if (initialSelection) {
         if (selected && !config.allowPlayerGenderChangeAnytime()) {
            sendCurrentGenderFeedback(player, "Gender has already been selected on this server.");
         } else if (!isStartingGenderAllowed(normalized)) {
            sendGenderSelectionPrompt(player);
         } else {
            applyPlayerGenderMask(player, normalized, true, NonChangeSource.JOIN_SELECTION);
         }
      } else {
         if (!config.allowPlayerGenderChangeAnytime()) {
            if (!selected && config.requirePlayerGenderSelectionOnJoin()) {
               sendGenderSelectionPrompt(player);
               return;
            }

            if (selected) {
               sendCurrentGenderFeedback(player, "Gender changes are disabled by this server.");
               return;
            }

            if (!isStartingGenderAllowed(normalized)) {
               sendGenderSelectionPrompt(player);
               return;
            }
         }

         applyPlayerGenderMask(player, normalized, true, NonChangeSource.CLIENT_PREFERENCE);
      }
   }

   private static void applyPlayerGenderMask(ServerPlayerEntity player, int mask, boolean markSelected) {
      applyPlayerGenderMask(player, mask, markSelected, NonChangeSource.SYSTEM);
   }

   private static void applyPlayerGenderMask(ServerPlayerEntity player, int mask, boolean markSelected, NonChangeSource source) {
      applyGenderMask(player, mask, source);
      if (markSelected) {
         markPlayerGenderSelected(player);
      }
   }

   private static void markPlayerGenderSelected(ServerPlayerEntity player) {
      if (player != null) {
         player.addCommandTag("gender.selected");
      }
   }

   private static void clearPlayerGenderSelectionState(ServerPlayerEntity player) {
      if (player != null) {
         player.removeCommandTag("gender.selected");
         player.removeCommandTag("gender.male");
         player.removeCommandTag("gender.female");
         if (player instanceof GenderHolder holder) {
            holder.setGenderMask(0);
         }
      }
   }

   private static boolean hasPlayerGenderSelected(ServerPlayerEntity player) {
      return player != null && player.getCommandTags().contains("gender.selected");
   }

   private static void sendGenderSelectionPrompt(ServerPlayerEntity player) {
      if (player != null) {
         NonConfig config = NeedsOfNature.getConfig();
         ServerPlayNetworking.send(
            player,
            new GenderSelectionPromptS2CPayload(
               config.getAllowedStartingGenderMask(), currentOrDefaultPlayerMask(player), !config.allowPlayerGenderChangeAnytime()
            )
         );
      }
   }

   private static void sendCurrentGenderFeedback(ServerPlayerEntity player, String message) {
      if (player != null) {
         player.sendMessage(Text.literal("[NoN] " + message), false);
      }
   }

   private static int currentOrDefaultPlayerMask(ServerPlayerEntity player) {
      if (player instanceof GenderHolder holder) {
         int mask = holder.getGenderMask() & 3;
         if (mask != 0) {
            return mask;
         }
      }

      return firstAllowedStartingMask(NeedsOfNature.getConfig().getAllowedStartingGenderMask());
   }

   private static int firstAllowedStartingMask(int allowedMask) {
      int allowed = allowedMask & 3;
      if ((allowedMask & 2) != 0) {
         return 2;
      } else if ((allowedMask & 1) != 0) {
         return 1;
      } else {
         return (allowedMask & 4) != 0 ? 3 : 3;
      }
   }

   private static int startingGenderChoiceBit(int genderMask) {
      return switch (genderMask & 3) {
         case 1 -> 1;
         case 2 -> 2;
         case 3 -> 4;
         default -> 2;
      };
   }

   private static int normalizeGenderMask(int mask) {
      int normalized = mask & 3;
      return normalized == 0 ? 2 : normalized;
   }

   static NonConfig.GenderSpawnChances resolveGenderSpawnChances(Entity entity) {
      return NonEntityProfiles.resolve(entity).genderSpawn();
   }

   static Map<String, NonConfig.GenderSpawnChances> resolveEffectiveGenderSpawnMap() {
      return NonEntityProfiles.resolveEffectiveGenderSpawnMap();
   }

   static boolean hasMaleTag(Entity entity) {
      return entity != null && entity.getCommandTags().contains("gender.male");
   }

   static boolean hasFemaleTag(Entity entity) {
      return entity != null && entity.getCommandTags().contains("gender.female");
   }

   static boolean isOnlyMale(Entity entity) {
      return entity instanceof GenderHolder holder ? (holder.getGenderMask() & 3) == 1 : false;
   }

   static boolean hasMaleGender(Entity entity) {
      if (entity instanceof GenderHolder holder) {
         int mask = holder.getGenderMask() & 3;
         if (mask != 0) {
            return (mask & 1) != 0;
         }
      }

      return hasMaleTag(entity);
   }

   static boolean hasFemaleGender(Entity entity) {
      if (entity instanceof GenderHolder holder) {
         int mask = holder.getGenderMask() & 3;
         if (mask != 0) {
            return (mask & 2) != 0;
         }
      }

      return hasFemaleTag(entity);
   }

   static NonLiquidTankType getLiquidTankType(Entity entity) {
      return isOnlyMale(entity) ? NonLiquidTankType.A : NonLiquidTankType.V;
   }

   static void applyGenderMask(Entity entity, int mask) {
      applyGenderMask(entity, mask, NonChangeSource.SYSTEM);
   }

   static boolean isOnlyFemale(Entity entity) {
      return entity == null ? false : hasFemaleGender(entity) && !hasMaleGender(entity);
   }

   static void applyGenderMask(Entity entity, int mask, NonChangeSource source) {
      if (entity != null) {
         int beforeMask = entity instanceof GenderHolder existingHolder ? existingHolder.getGenderMask() & 3 : 0;
         int normalizedMask = mask & 3;
         if (normalizedMask == 0) {
            normalizedMask = 2;
         }

         entity.removeCommandTag("gender.male");
         entity.removeCommandTag("gender.female");
         if ((normalizedMask & 1) != 0) {
            entity.addCommandTag("gender.male");
         }

         if ((normalizedMask & 2) != 0) {
            entity.addCommandTag("gender.female");
         }

         if (entity instanceof GenderHolder holder) {
            holder.setGenderMask(normalizedMask);
         }

         if (normalizedMask == 1 && entity instanceof ServerPlayerEntity player) {
            if (NonMessSystem.normalizeMaleOnlyMess(player, false)) {
               NonMessSystem.broadcastMessState(player);
            }

            NeedsOfNature.clearPregnancyState(player, true);
         }

         if (entity instanceof ServerPlayerEntity player && (beforeMask & 3) != (normalizedMask & 3)) {
            NonApiInternals.fireGenderChanged(player, normalizedMask, source == null ? NonChangeSource.SYSTEM : source);
            NonPregnancyConceptionSystem.syncState(player);
         }
      }
   }

   private static boolean canCowBeMilked(CowEntity cow) {
      if (cow == null) {
         return true;
      } else {
         return cow instanceof GenderHolder holder ? holder.hasGender(2) : cow.getCommandTags().contains("gender.female");
      }
   }

   private static int resolveGenderMaskFromTags(Entity entity) {
      if (entity == null) {
         return 0;
      }

      Set<String> tags = entity.getCommandTags();
      int mask = 0;
      if (tags.contains("gender.male")) {
         mask |= 1;
      }

      if (tags.contains("gender.female")) {
         mask |= 2;
      }

      return mask;
   }
}
