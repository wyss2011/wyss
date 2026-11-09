package com.nonid;

import com.nonid.data.NonEntityProfiles;
import com.nonid.internal.animation.data.AnimationDefinitions;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class NonMobEnergySystem {
   public static final int DEFAULT_TICKS_PER_ENERGY = 120;
   public static final int PARTNER_SCAN_INTERVAL_TICKS = 40;
   public static final int POST_ATTEMPT_COOLDOWN_TICKS = 100;
   public static final int ROLL_FAIL_COOLDOWN_TICKS = 1200;
   public static final int ENERGY_JOIN_MIN = 70;
   public static final int ENERGY_MOB_MATCH_MIN = 120;
   public static final int ENERGY_ATTACK = 200;
   public static final double PARTNER_SCAN_RADIUS = 20.0;
   public static final double PLAYER_COOLDOWN_BYPASS_DIST_SQ = 64.0;
   public static final double PLAYER_ANIMATION_LINGER_RADIUS = 3.0;
   public static final double PLAYER_ANIMATION_LINGER_RADIUS_SQ = 9.0;
   public static final double PLAYER_ANIMATION_LINGER_MIN_DISTANCE = 1.5;
   public static final double PLAYER_ANIMATION_LINGER_MIN_DISTANCE_SQ = 2.25;
   public static final double PLAYER_ANIMATION_LINGER_MOVE_SPEED = 1.1;
   private static final int GENDER_MIXED = 0;
   private static final int GENDER_MALE = 1;
   private static final int GENDER_FEMALE = 2;

   private NonMobEnergySystem() {
   }

   public static double passiveGainScale(MobEntity mob, float multiplier, float drift, boolean nearAnimation, double nearAnimationMultiplier) {
      double rate = NeedsOfNature.getConfig().getEnergyGainRate();
      double profileMultiplier = NonEntityProfiles.resolveEnergyGainMultiplier(mob);
      double scale = multiplier * drift * rate * profileMultiplier;
      if (nearAnimation) {
         scale *= Math.max(1.0, nearAnimationMultiplier);
      }

      return Double.isFinite(scale) && scale > 0.0 ? scale : 0.0;
   }

   public static int ticksPerGain(MobEntity mob, float multiplier, float drift, boolean nearAnimation, double nearAnimationMultiplier) {
      double scale = passiveGainScale(mob, multiplier, drift, nearAnimation, nearAnimationMultiplier);
      return scale <= 0.0 ? 120 : Math.max(1, (int)Math.round(120.0 / scale));
   }

   public static float randomGainMultiplier(ServerWorld world) {
      float a = world.getRandom().nextFloat();
      float b = world.getRandom().nextFloat();
      float tri = (a + b) * 0.5F;
      return 0.4F + tri * 0.6F;
   }

   public static float randomGainDrift(ServerWorld world) {
      return 0.9F + world.getRandom().nextFloat() * 0.2F;
   }

   public static boolean hasGenericAnimationSupport(EntityType<?> type, boolean baby) {
      Identifier entityTypeId = Registries.ENTITY_TYPE.getId(type);
      return AnimationDefinitions.hasEnabledGenericAnimationForEntityType(entityTypeId, baby);
   }

   public static boolean isEnergyAttackStabilized(Entity entity) {
      return entity != null && entity.getCommandTags().contains("non.energy_stabilized");
   }

   public static boolean bypassesEntityEntityRoll(Entity self, Entity candidate) {
      return self != null && candidate != null
         ? self instanceof HorseEntity && candidate.getType() == NeedsOfNature.HORSE_LIQUID_COLLECTOR_ENTITY_TYPE
            || self.getType() == NeedsOfNature.HORSE_LIQUID_COLLECTOR_ENTITY_TYPE && candidate instanceof HorseEntity
         : false;
   }

   public static boolean passesSameGenderRoll(ServerWorld world, Entity debugSource, List<? extends Entity> actors) {
      if (world == null) {
         return true;
      }

      int group = resolveGenderGroup(actors);
      int percent;
      String label;
      if (group == 1) {
         percent = NeedsOfNature.getConfig().getMaleMaleChancePercent();
         label = "male+male";
      } else if (group == 2) {
         percent = NeedsOfNature.getConfig().getFemaleFemaleChancePercent();
         label = "female+female";
      } else {
         percent = 100;
         label = "mixed";
      }

      return rollPercent(world, debugSource, percent, label);
   }

   public static int resolveMultiActorJoinChancePercent(Entity joiningEntity) {
      Integer profileChance = NonEntityProfiles.resolve(joiningEntity).multiActorJoinChancePercent();
      return profileChance == null ? NeedsOfNature.getConfig().getMultiActorJoinChancePercent() : Math.max(0, Math.min(100, profileChance));
   }

   public static boolean passesMultiActorJoinRoll(ServerWorld world, Entity debugSource, int totalActors, int chancePercent) {
      return world != null && totalActors > 2 ? rollPercent(world, debugSource, chancePercent, "join 3+ actors (total=" + totalActors + ")") : true;
   }

   public static boolean passesEntityEntityRoll(ServerWorld world, Entity debugSource) {
      if (world == null) {
         return true;
      }

      int percent = NeedsOfNature.getConfig().getEntityEntityChancePercent();
      return rollPercent(world, debugSource, percent, "entity+entity");
   }

   public static void appendCooldownMapDebug(
      List<String> lines,
      ServerWorld world,
      long nowTick,
      String label,
      Map<UUID, Long> cooldowns,
      Map<UUID, Long> bypassableCooldowns,
      boolean includeBypassState
   ) {
      if (lines != null && world != null && cooldowns != null && !cooldowns.isEmpty()) {
         int shown = 0;

         for (Entry<UUID, Long> entry : cooldowns.entrySet()) {
            UUID partnerId = entry.getKey();
            long remaining = entry.getValue() - nowTick;
            if (partnerId != null && remaining > 0L) {
               String suffix = "";
               if (includeBypassState && bypassableCooldowns != null && bypassableCooldowns.containsKey(partnerId)) {
                  suffix = " (bypass: close+LOS)";
               }

               lines.add(label + ": " + debugEntityName(world, partnerId) + " " + NeedsOfNature.formatTicksForDebug(remaining) + suffix);
               if (++shown >= 5) {
                  int extra = cooldowns.size() - shown;
                  if (extra > 0) {
                     lines.add(label + ": +" + extra + " more");
                  }
                  break;
               }
            }
         }
      }
   }

   public static String debugEntityName(ServerWorld world, UUID uuid) {
      if (uuid == null) {
         return "unknown";
      }

      Entity entity = world.getEntity(uuid);
      if (entity != null) {
         return entity.getDisplayName().getString();
      }

      String raw = uuid.toString();
      return raw.length() <= 8 ? raw : raw.substring(0, 8);
   }

   private static int resolveGenderGroup(List<? extends Entity> actors) {
      int group = 0;
      if (actors != null && !actors.isEmpty()) {
         for (Entity actor : actors) {
            if (actor == null) {
               return 0;
            }

            boolean hasMale = actor.getCommandTags().contains("gender.male");
            boolean hasFemale = actor.getCommandTags().contains("gender.female");
            int actorGroup;
            if (hasMale && !hasFemale) {
               actorGroup = 1;
            } else {
               if (!hasFemale || hasMale) {
                  return 0;
               }

               actorGroup = 2;
            }

            if (group == 0) {
               group = actorGroup;
            } else if (group != actorGroup) {
               return 0;
            }
         }

         return group;
      } else {
         return 0;
      }
   }

   private static boolean rollPercent(ServerWorld world, Entity debugSource, int percent, String label) {
      if (percent >= 100) {
         return true;
      }

      if (percent <= 0) {
         return false;
      }

      boolean success = world.getRandom().nextInt(100) < percent;
      sendRollDebug(world, debugSource, label, percent, success);
      return success;
   }

   private static void sendRollDebug(ServerWorld world, Entity debugSource, String label, int percent, boolean success) {
      if (world != null && debugSource != null) {
         String result = success ? "success" : "fail";
         NeedsOfNature.sendDebugChatToNearby(
            world, debugSource, 20.0, NonDebugChatCategory.INFO, Text.translatable("debug.needsofnature.roll_result", new Object[]{label, percent, result})
         );
      }
   }
}
