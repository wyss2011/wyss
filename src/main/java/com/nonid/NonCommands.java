package com.nonid;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.nonid.network.SkinRippedInfoRequestS2CPayload;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

final class NonCommands {
   private NonCommands() {
   }

   static void register() {
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                    "needsofnature"
                                 )
                                 .requires(NonCommands::isOpCommandSource))
                              .then(
                                 ((LiteralArgumentBuilder)CommandManager.literal("getplayerinfo").executes(ctx -> {
                                       ServerPlayerEntity self = ((ServerCommandSource)ctx.getSource()).getPlayer();
                                       if (self == null) {
                                          ((ServerCommandSource)ctx.getSource())
                                             .sendError(Text.literal("控制台用法: /needsofnature getplayerinfo <player>"));
                                          return 0;
                                       } else {
                                          return sendPlayerInfo((ServerCommandSource)ctx.getSource(), self);
                                       }
                                    }))
                                    .then(
                                       CommandManager.argument("player", EntityArgumentType.player())
                                          .executes(ctx -> sendPlayerInfo((ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                                    )
                              ))
                           .then(CommandManager.literal("resetmobcooldowns").executes(ctx -> resetMobCooldowns((ServerCommandSource)ctx.getSource()))))
                        .then(
                           ((LiteralArgumentBuilder)CommandManager.literal("skin").then(messCommand()))
                              .then(
                                 ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("ripped")
                                          .then(CommandManager.literal("info").executes(ctx -> requestRippedSkinInfo((ServerCommandSource)ctx.getSource()))))
                                       .then(
                                          ((LiteralArgumentBuilder)CommandManager.literal("reset").executes(ctx -> {
                                                ServerPlayerEntity self = ((ServerCommandSource)ctx.getSource()).getPlayer();
                                                if (self == null) {
                                                   ((ServerCommandSource)ctx.getSource())
                                                      .sendError(Text.literal("控制台用法: /needsofnature skin ripped reset <player>"));
                                                   return 0;
                                                } else {
                                                   return resetDestroyedSkinCommand((ServerCommandSource)ctx.getSource(), self);
                                                }
                                             }))
                                             .then(
                                                CommandManager.argument("player", EntityArgumentType.player())
                                                   .executes(
                                                      ctx -> resetDestroyedSkinCommand(
                                                         (ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")
                                                      )
                                                   )
                                             )
                                       ))
                                    .then(
                                       CommandManager.literal("set")
                                          .then(
                                             ((RequiredArgumentBuilder)CommandManager.argument("stage", IntegerArgumentType.integer(0, 4)).executes(ctx -> {
                                                   ServerPlayerEntity self = ((ServerCommandSource)ctx.getSource()).getPlayer();
                                                   if (self == null) {
                                                      ((ServerCommandSource)ctx.getSource())
                                                         .sendError(Text.literal("控制台用法: /needsofnature skin ripped set <stage> <player>"));
                                                      return 0;
                                                   } else {
                                                      return setDestroyedSkinStageCommand(
                                                         (ServerCommandSource)ctx.getSource(), self, IntegerArgumentType.getInteger(ctx, "stage")
                                                      );
                                                   }
                                                }))
                                                .then(
                                                   CommandManager.argument("player", EntityArgumentType.player())
                                                      .executes(
                                                         ctx -> setDestroyedSkinStageCommand(
                                                            (ServerCommandSource)ctx.getSource(),
                                                            EntityArgumentType.getPlayer(ctx, "player"),
                                                            IntegerArgumentType.getInteger(ctx, "stage")
                                                         )
                                                      )
                                                )
                                          )
                                    )
                              )
                        ))
                     .then(
                        ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("gender")
                                    .requires(NonCommands::isOpCommandSource))
                                 .then(
                                    CommandManager.literal("get")
                                       .then(
                                          CommandManager.argument("player", EntityArgumentType.player())
                                             .executes(
                                                ctx -> getGenderCommand((ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"))
                                             )
                                       )
                                 ))
                              .then(
                                 CommandManager.literal("set")
                                    .then(
                                       CommandManager.argument("player", EntityArgumentType.player())
                                          .then(
                                             CommandManager.argument("gender", StringArgumentType.word())
                                                .executes(
                                                   ctx -> setGenderCommand(
                                                      (ServerCommandSource)ctx.getSource(),
                                                      EntityArgumentType.getPlayer(ctx, "player"),
                                                      StringArgumentType.getString(ctx, "gender")
                                                   )
                                                )
                                          )
                                    )
                              ))
                           .then(
                              CommandManager.literal("reset")
                                 .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                       .executes(ctx -> resetGenderCommand((ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                                 )
                           )
                     ))
                  .then(
                     ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("pregnancy")
                                    .then(
                                       CommandManager.literal("cycle")
                                          .then(
                                             CommandManager.literal("set")
                                                .then(
                                                   ((RequiredArgumentBuilder)CommandManager.argument("day", IntegerArgumentType.integer(1, 30))
                                                         .executes(
                                                            ctx -> {
                                                               ServerPlayerEntity self = ((ServerCommandSource)ctx.getSource()).getPlayer();
                                                               if (self == null) {
                                                                  ((ServerCommandSource)ctx.getSource())
                                                                     .sendError(
                                                                        Text.literal("控制台用法: /needsofnature pregnancy cycle set <day> <player>")
                                                                     );
                                                                  return 0;
                                                               } else {
                                                                  return setPregnancyCycleDayCommand(
                                                                     (ServerCommandSource)ctx.getSource(), self, IntegerArgumentType.getInteger(ctx, "day")
                                                                  );
                                                               }
                                                            }
                                                         ))
                                                      .then(
                                                         CommandManager.argument("player", EntityArgumentType.player())
                                                            .executes(
                                                               ctx -> setPregnancyCycleDayCommand(
                                                                  (ServerCommandSource)ctx.getSource(),
                                                                  EntityArgumentType.getPlayer(ctx, "player"),
                                                                  IntegerArgumentType.getInteger(ctx, "day")
                                                               )
                                                            )
                                                      )
                                                )
                                          )
                                    ))
                                 .then(
                                    CommandManager.literal("set")
                                       .then(
                                          CommandManager.argument("player", EntityArgumentType.player())
                                             .then(
                                                ((RequiredArgumentBuilder)CommandManager.argument("entity", StringArgumentType.string())
                                                      .executes(
                                                         ctx -> setPregnancyCommand(
                                                            (ServerCommandSource)ctx.getSource(),
                                                            EntityArgumentType.getPlayer(ctx, "player"),
                                                            StringArgumentType.getString(ctx, "entity"),
                                                            NeedsOfNature.getConfig().getPregnancyDurationMinutes(),
                                                            0
                                                         )
                                                      ))
                                                   .then(
                                                      ((RequiredArgumentBuilder)CommandManager.argument("minutes", IntegerArgumentType.integer(1, 5256000))
                                                            .executes(
                                                               ctx -> setPregnancyCommand(
                                                                  (ServerCommandSource)ctx.getSource(),
                                                                  EntityArgumentType.getPlayer(ctx, "player"),
                                                                  StringArgumentType.getString(ctx, "entity"),
                                                                  IntegerArgumentType.getInteger(ctx, "minutes"),
                                                                  0
                                                               )
                                                            ))
                                                         .then(
                                                            CommandManager.argument("spawns", IntegerArgumentType.integer(1, 16))
                                                               .executes(
                                                                  ctx -> setPregnancyCommand(
                                                                     (ServerCommandSource)ctx.getSource(),
                                                                     EntityArgumentType.getPlayer(ctx, "player"),
                                                                     StringArgumentType.getString(ctx, "entity"),
                                                                     IntegerArgumentType.getInteger(ctx, "minutes"),
                                                                     IntegerArgumentType.getInteger(ctx, "spawns")
                                                                  )
                                                               )
                                                         )
                                                   )
                                             )
                                       )
                                 ))
                              .then(
                                 ((LiteralArgumentBuilder)CommandManager.literal("clear").executes(ctx -> {
                                       ServerPlayerEntity self = ((ServerCommandSource)ctx.getSource()).getPlayer();
                                       if (self == null) {
                                          ((ServerCommandSource)ctx.getSource())
                                             .sendError(Text.literal("控制台用法: /needsofnature pregnancy clear <player>"));
                                          return 0;
                                       } else {
                                          return clearPregnancyCommand((ServerCommandSource)ctx.getSource(), self);
                                       }
                                    }))
                                    .then(
                                       CommandManager.argument("player", EntityArgumentType.player())
                                          .executes(
                                             ctx -> clearPregnancyCommand((ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"))
                                          )
                                    )
                              ))
                           .then(
                              ((LiteralArgumentBuilder)CommandManager.literal("complete").executes(ctx -> {
                                    ServerPlayerEntity self = ((ServerCommandSource)ctx.getSource()).getPlayer();
                                    if (self == null) {
                                       ((ServerCommandSource)ctx.getSource())
                                          .sendError(Text.literal("控制台用法: /needsofnature pregnancy complete <player>"));
                                       return 0;
                                    } else {
                                       return completePregnancyCommand((ServerCommandSource)ctx.getSource(), self);
                                    }
                                 }))
                                 .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                       .executes(
                                          ctx -> completePregnancyCommand((ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"))
                                       )
                                 )
                           ))
                        .then(
                           ((LiteralArgumentBuilder)CommandManager.literal("get").executes(ctx -> {
                                 ServerPlayerEntity self = ((ServerCommandSource)ctx.getSource()).getPlayer();
                                 if (self == null) {
                                    ((ServerCommandSource)ctx.getSource()).sendError(Text.literal("控制台用法: /needsofnature pregnancy get <player>"));
                                    return 0;
                                 } else {
                                    return sendPregnantEntity((ServerCommandSource)ctx.getSource(), self);
                                 }
                              }))
                              .then(
                                 CommandManager.argument("player", EntityArgumentType.player())
                                    .executes(ctx -> sendPregnantEntity((ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                              )
                        )
                  )
            )
         );
   }

   private static LiteralArgumentBuilder<ServerCommandSource> messCommand() {
      return (LiteralArgumentBuilder<ServerCommandSource>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("mess")
               .requires(NonCommands::isOpCommandSource))
            .then(
               ((LiteralArgumentBuilder)CommandManager.literal("clean").executes(ctx -> {
                     ServerPlayerEntity self = ((ServerCommandSource)ctx.getSource()).getPlayer();
                     if (self == null) {
                        ((ServerCommandSource)ctx.getSource()).sendError(Text.literal("控制台用法: /needsofnature skin mess clean <player>"));
                        return 0;
                     } else {
                        return cleanMessCommand((ServerCommandSource)ctx.getSource(), self);
                     }
                  }))
                  .then(
                     CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> cleanMessCommand((ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                  )
            ))
         .then(
            ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("set")
                        .then(
                           CommandManager.argument("v_value", IntegerArgumentType.integer(0, 10))
                              .then(
                                 CommandManager.argument("a_value", IntegerArgumentType.integer(0, 10))
                                    .then(
                                       ((RequiredArgumentBuilder)CommandManager.argument("m_value", IntegerArgumentType.integer(0, 10))
                                             .executes(
                                                ctx -> {
                                                   ServerPlayerEntity self = ((ServerCommandSource)ctx.getSource()).getPlayer();
                                                   if (self == null) {
                                                      ((ServerCommandSource)ctx.getSource())
                                                         .sendError(Text.literal("控制台用法: /needsofnature skin mess set <v> <a> <m> <player>"));
                                                      return 0;
                                                   } else {
                                                      return setMessCommand(
                                                         (ServerCommandSource)ctx.getSource(),
                                                         self,
                                                         IntegerArgumentType.getInteger(ctx, "v_value"),
                                                         IntegerArgumentType.getInteger(ctx, "a_value"),
                                                         IntegerArgumentType.getInteger(ctx, "m_value")
                                                      );
                                                   }
                                                }
                                             ))
                                          .then(
                                             CommandManager.argument("player", EntityArgumentType.player())
                                                .executes(
                                                   ctx -> setMessCommand(
                                                      (ServerCommandSource)ctx.getSource(),
                                                      EntityArgumentType.getPlayer(ctx, "player"),
                                                      IntegerArgumentType.getInteger(ctx, "v_value"),
                                                      IntegerArgumentType.getInteger(ctx, "a_value"),
                                                      IntegerArgumentType.getInteger(ctx, "m_value")
                                                   )
                                                )
                                          )
                                    )
                              )
                        ))
                     .then(messSlotSetCommand("v")))
                  .then(messSlotSetCommand("a")))
               .then(messSlotSetCommand("m"))
         );
   }

   private static LiteralArgumentBuilder<ServerCommandSource> messSlotSetCommand(String slot) {
      return (LiteralArgumentBuilder<ServerCommandSource>)CommandManager.literal(slot)
         .then(
            ((RequiredArgumentBuilder)CommandManager.argument("value", IntegerArgumentType.integer(0, 10)).executes(ctx -> {
                  ServerPlayerEntity self = ((ServerCommandSource)ctx.getSource()).getPlayer();
                  if (self == null) {
                     ((ServerCommandSource)ctx.getSource())
                        .sendError(Text.literal("控制台用法: /needsofnature skin mess set " + slot + " <value> <player>"));
                     return 0;
                  } else {
                     return setMessSlotCommand((ServerCommandSource)ctx.getSource(), self, slot, IntegerArgumentType.getInteger(ctx, "value"));
                  }
               }))
               .then(
                  CommandManager.argument("player", EntityArgumentType.player())
                     .executes(
                        ctx -> setMessSlotCommand(
                           (ServerCommandSource)ctx.getSource(),
                           EntityArgumentType.getPlayer(ctx, "player"),
                           slot,
                           IntegerArgumentType.getInteger(ctx, "value")
                        )
                     )
               )
         );
   }

   private static int sendPlayerInfo(ServerCommandSource source, ServerPlayerEntity target) {
      int result = sendPlayerEnergy(source, target);
      result += sendPlayerCooldowns(source, target);
      result += sendPlayerContestInfo(source, target);
      source.sendFeedback(() -> Text.literal(NonPregnancyConceptionSystem.describeCycle(target)), false);
      return result + 1;
   }

   private static int sendPlayerContestInfo(ServerCommandSource source, ServerPlayerEntity target) {
      List<String> lines = NonPlayerAttackSystem.getContestDebugLines(target);

      for (String line : lines) {
         source.sendFeedback(() -> Text.literal(line), false);
      }

      return lines.size();
   }

   private static int sendPlayerEnergy(ServerCommandSource source, ServerPlayerEntity target) {
      if (target instanceof EnergyHolder holder) {
         int current = holder.getEnergy();
         int max = holder.getMaxEnergy();
         source.sendFeedback(() -> Text.literal(target.getName().getString() + " energy: " + current + "/" + max), false);
         return current;
      } else {
         source.sendError(Text.literal("目标没有能量数据。"));
         return 0;
      }
   }

   private static int sendPlayerCooldowns(ServerCommandSource source, ServerPlayerEntity target) {
      if (!(target.getEntityWorld() instanceof ServerWorld world)) {
         source.sendError(Text.literal("目标不在服务器世界中。"));
         return 0;
      } else {
         List<String> cooldowns = NeedsOfNature.getActivePlayerCooldownDebugLines(world, target);
         if (cooldowns.isEmpty()) {
            source.sendFeedback(() -> Text.literal(target.getName().getString() + " cooldowns: none"), false);
            return 0;
         }

         source.sendFeedback(() -> Text.literal(target.getName().getString() + " cooldowns:"), false);

         for (String line : cooldowns) {
            source.sendFeedback(() -> Text.literal("- " + line), false);
         }

         return cooldowns.size();
      }
   }

   private static int requestRippedSkinInfo(ServerCommandSource source) {
      ServerPlayerEntity player = source.getPlayer();
      if (player == null) {
         source.sendError(Text.literal("控制台用法: /needsofnature skin ripped info as a player"));
         return 0;
      } else {
         ServerPlayNetworking.send(player, new SkinRippedInfoRequestS2CPayload());
         source.sendFeedback(() -> Text.literal("已请求输出本地撕裂皮肤信息。"), false);
         return 1;
      }
   }

   private static boolean isOpCommandSource(ServerCommandSource source) {
      if (source == null) {
         return false;
      }

      ServerPlayerEntity player = source.getPlayer();
      return player == null ? true : source.getServer().getPlayerManager().isOperator(new PlayerConfigEntry(player.getGameProfile()));
   }

   private static int resetMobCooldowns(ServerCommandSource source) {
      int failsafeEntries = NeedsOfNature.clearAttackOutcomeFailsafeEntries();
      int mobsReset = 0;

      for (ServerWorld world : source.getServer().getWorlds()) {
         for (Entity entity : world.iterateEntities()) {
            if (entity instanceof MobEntity mob && mob instanceof EnergyHolder holder) {
               holder.onNonAnimationStarted();
               mobsReset++;
            }
         }
      }

      int total = failsafeEntries + mobsReset;
      String feedback = "Reset mob cooldowns. Failsafe entries: " + failsafeEntries + ", mob partner cooldown sets: " + mobsReset + ".";
      source.sendFeedback(() -> Text.literal(feedback), true);
      return total;
   }

   private static int cleanMessCommand(ServerCommandSource source, ServerPlayerEntity target) {
      NonMessSystem.clearMess(target);
      source.sendFeedback(() -> Text.literal(target.getName().getString() + " mess cleared."), true);
      return 1;
   }

   private static int setMessCommand(ServerCommandSource source, ServerPlayerEntity target, int v, int a, int m) {
      if (!NeedsOfNature.isMessSystemEnabled()) {
         source.sendError(Text.literal("污渍系统已禁用。"));
         return 0;
      } else if (target instanceof MessHolder holder) {
         holder.setVMess(NonMessSystem.clampMess(v));
         holder.setAMess(NonMessSystem.clampMess(a));
         holder.setMMess(NonMessSystem.clampMess(m));
         NonMessSystem.broadcastMessState(target);
         maybeGrantMessedUp(target, holder);
         sendMessSetFeedback(source, target, holder);
         return 1;
      } else {
         source.sendError(Text.literal("目标没有污渍数据。"));
         return 0;
      }
   }

   private static int setMessSlotCommand(ServerCommandSource source, ServerPlayerEntity target, String slot, int value) {
      if (!NeedsOfNature.isMessSystemEnabled()) {
         source.sendError(Text.literal("污渍系统已禁用。"));
         return 0;
      }

      if (target instanceof MessHolder holder) {
         int clamped = NonMessSystem.clampMess(value);
         switch (slot) {
            case "v":
               holder.setVMess(clamped);
               break;
            case "a":
               holder.setAMess(clamped);
               break;
            case "m":
               holder.setMMess(clamped);
               break;
            default:
               source.sendError(Text.literal("未知污渍槽位 '" + slot + "'. Use v, a, or m."));
               return 0;
         }

         NonMessSystem.broadcastMessState(target);
         maybeGrantMessedUp(target, holder);
         sendMessSetFeedback(source, target, holder);
         return 1;
      } else {
         source.sendError(Text.literal("目标没有污渍数据。"));
         return 0;
      }
   }

   private static void sendMessSetFeedback(ServerCommandSource source, ServerPlayerEntity target, MessHolder holder) {
      source.sendFeedback(
         () -> Text.literal(
            target.getName().getString()
               + " mess set to V="
               + NonMessSystem.clampMess(holder.getVMess())
               + ", A="
               + NonMessSystem.clampMess(holder.getAMess())
               + ", M="
               + NonMessSystem.clampMess(holder.getMMess())
               + "."
         ),
         true
      );
   }

   private static void maybeGrantMessedUp(ServerPlayerEntity target, MessHolder holder) {
      if (NonMessSystem.clampMess(holder.getVMess()) >= 10
         && NonMessSystem.clampMess(holder.getAMess()) >= 10
         && NonMessSystem.clampMess(holder.getMMess()) >= 10) {
         NonAdvancementHooks.grantMessedUp(target);
      }
   }

   private static int getGenderCommand(ServerCommandSource source, ServerPlayerEntity target) {
      source.sendFeedback(() -> Text.literal(target.getName().getString() + " gender: " + genderName(currentGenderMask(target))), false);
      return 1;
   }

   private static int setGenderCommand(ServerCommandSource source, ServerPlayerEntity target, String genderRaw) {
      int mask = parseGenderMask(genderRaw);
      if (mask == 0) {
         source.sendError(Text.literal("未知性别 '" + genderRaw + "'. Use male, female, or both."));
         return 0;
      } else {
         int applied = NonGenderSystem.setPlayerGenderFromCommand(target, mask);
         source.sendFeedback(() -> Text.literal(target.getName().getString() + " gender set to " + genderName(applied) + "."), true);
         return 1;
      }
   }

   private static int resetGenderCommand(ServerCommandSource source, ServerPlayerEntity target) {
      NonGenderSystem.resetPlayerGenderSelection(target);
      source.sendFeedback(() -> Text.literal(target.getName().getString() + " gender selection reset."), true);
      return 1;
   }

   private static int currentGenderMask(ServerPlayerEntity target) {
      return target instanceof GenderHolder holder ? holder.getGenderMask() & 3 : 0;
   }

   private static int parseGenderMask(String raw) {
      if (raw == null) {
         return 0;
      }

      return switch (raw.trim().toLowerCase(Locale.ROOT)) {
         case "m", "male" -> 1;
         case "f", "female" -> 2;
         case "b", "both", "male+female", "female+male" -> 3;
         default -> 0;
      };
   }

   private static String genderName(int mask) {
      return switch (mask & 3) {
         case 1 -> "male";
         case 2 -> "female";
         case 3 -> "male+female";
         default -> "none";
      };
   }

   private static int setDestroyedSkinStageCommand(ServerCommandSource source, ServerPlayerEntity target, int stage) {
      if (!NeedsOfNature.isDestroyedSkinSystemEnabled()) {
         source.sendError(Text.literal("撕裂皮肤系统已禁用。"));
         return 0;
      }

      int clamped = Math.max(0, Math.min(4, stage));
      if (target instanceof DestroyedSkinHolder holder) {
         holder.setDestroyedSkinDamage(NonDestroyedSkinSystem.destroyedSkinDamageForStage(clamped));
      }

      NonDestroyedSkinSystem.setDestroyedSkinStage(target, clamped);
      source.sendFeedback(
         () -> Text.literal(
            target.getName().getString()
               + " ripped skin stage set to "
               + clamped
               + " (damage "
               + NonDestroyedSkinSystem.destroyedSkinDamageForStage(clamped)
               + "/10)."
         ),
         true
      );
      return clamped;
   }

   private static int resetDestroyedSkinCommand(ServerCommandSource source, ServerPlayerEntity target) {
      if (!NeedsOfNature.isDestroyedSkinSystemEnabled()) {
         source.sendError(Text.literal("撕裂皮肤系统已禁用。"));
         return 0;
      } else {
         NonDestroyedSkinSystem.resetDestroyedSkinDamage(target);
         source.sendFeedback(() -> Text.literal(target.getName().getString() + " ripped skin damage reset."), true);
         return 1;
      }
   }

   private static int sendPregnantEntity(ServerCommandSource source, ServerPlayerEntity target) {
      Identifier pregnantEntity = NeedsOfNature.getPregnantEntityTypeId(target);
      if (pregnantEntity == null) {
         source.sendFeedback(() -> Text.literal(target.getName().getString() + " is not pregnant."), false);
         return 0;
      } else {
         source.sendFeedback(() -> Text.literal(target.getName().getString() + " pregnant entity: " + pregnantEntity), false);
         return 1;
      }
   }

   private static int setPregnancyCycleDayCommand(ServerCommandSource source, ServerPlayerEntity target, int day) {
      if (!NonPregnancyConceptionSystem.setCycleDay(target, day)) {
         source.sendError(Text.literal("目标没有孕期周期数据。"));
         return 0;
      } else {
         source.sendFeedback(() -> Text.literal(target.getName().getString() + " pregnancy cycle set to day " + day + "/30."), true);
         return 1;
      }
   }

   private static int setPregnancyCommand(ServerCommandSource source, ServerPlayerEntity target, String entityIdRaw, int minutes, int offspringCountOverride) {
      if (NonGenderSystem.isOnlyMale(target)) {
         source.sendError(Text.literal("无法为纯男性玩家分娩。"));
         return 0;
      }

      Identifier entityId = Identifier.tryParse(entityIdRaw);
      if (entityId != null && Registries.ENTITY_TYPE.containsId(entityId)) {
         if (target.getEntityWorld() instanceof ServerWorld world) {
            long var13 = NeedsOfNature.getGlobalTick(world);
            long durationTicks = Math.max(1L, Math.min(5256000L, minutes)) * 1200L;
            NeedsOfNature.beginPregnancy(target, entityId, var13 + durationTicks, null, offspringCountOverride);
            int resolvedDisplaySpawnCount = ((PregnancyHolder)target).getPregnancyOffspringCount();
            if (resolvedDisplaySpawnCount <= 0) {
               resolvedDisplaySpawnCount = offspringCountOverride > 0 ? Math.max(1, Math.min(16, offspringCountOverride)) : 1;
            }

            int displaySpawnCount = resolvedDisplaySpawnCount;
            source.sendFeedback(
               () -> Text.literal(
                  "Set pregnancy for "
                     + target.getName().getString()
                     + " to "
                     + entityId
                     + " ("
                     + minutes
                     + " minutes, "
                     + displaySpawnCount
                     + " spawn"
                     + (displaySpawnCount == 1 ? "" : "s")
                     + ")."
               ),
               true
            );
            return 1;
         } else {
            source.sendError(Text.literal("目标不在服务器世界中。"));
            return 0;
         }
      } else {
         source.sendError(Text.literal("未知实体 ID: " + entityIdRaw));
         return 0;
      }
   }

   private static int clearPregnancyCommand(ServerCommandSource source, ServerPlayerEntity target) {
      NeedsOfNature.clearPregnancyState(target, true);
      source.sendFeedback(() -> Text.literal("已清除 " + target.getName().getString() + "."), true);
      return 1;
   }

   private static int completePregnancyCommand(ServerCommandSource source, ServerPlayerEntity target) {
      Identifier pregnantEntity = NeedsOfNature.getPregnantEntityTypeId(target);
      if (pregnantEntity == null) {
         source.sendError(Text.literal(target.getName().getString() + " is not pregnant."));
         return 0;
      } else if (target.getEntityWorld() instanceof ServerWorld world) {
         NeedsOfNature.markPregnancyPending(target, NeedsOfNature.getGlobalTick(world));
         source.sendFeedback(() -> Text.literal("已标记 " + target.getName().getString() + "."), true);
         return 1;
      } else {
         source.sendError(Text.literal("目标不在服务器世界中。"));
         return 0;
      }
   }
}
