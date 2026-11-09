package com.nonid.client;

import com.nonid.EnergyHolder;
import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import com.nonid.effect.NonStatusEffects;
import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.network.AttackStateS2CPayload;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Atlases;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;

@Environment(EnvType.CLIENT)
public final class NonHudOverlay {
   private static final Identifier ENERGY_BAR_BG = Identifier.of("needsofnature", "hud/progress_background");
   private static final Identifier ENERGY_BAR_FILL = Identifier.of("needsofnature", "hud/progress_full");
   private static final Identifier ATTACK_BAR_BG = Identifier.of("needsofnature", "hud/attack_background");
   private static final Identifier ATTACK_BAR_FILL = Identifier.of("needsofnature", "hud/attack_full");
   private static final Identifier ATTACK_PROMPT = Identifier.of("needsofnature", "hud/ad_mash");
   private static final Identifier LIQUID_TANK_BG = Identifier.of("needsofnature", "hud/liquid_background");
   private static final Identifier LIQUID_TANK_FILL = Identifier.of("needsofnature", "hud/liquid_full");
   private static final Identifier ENERGY_HEART = Identifier.of("needsofnature", "textures/particle/smallheart.png");
   private static final Identifier HUD_TEXTURE_SIZE_RELOADER_ID = NeedsOfNature.id("hud_texture_sizes");
   private static final int ENERGY_BAR_DEFAULT_W = 182;
   private static final int ENERGY_BAR_DEFAULT_H = 5;
   private static final int ENERGY_BAR_BOTTOM_MARGIN = 27;
   private static final int ATTACK_BAR_DEFAULT_W = 81;
   private static final int ATTACK_BAR_DEFAULT_H = 5;
   private static final int ATTACK_BAR_DEFAULT_Y_OFFSET = -4;
   private static final int ATTACK_PROMPT_DEFAULT_TEX_W = 68;
   private static final int ATTACK_PROMPT_DEFAULT_TEX_H = 128;
   private static final int ENERGY_HEART_DEFAULT_W = 7;
   private static final int ENERGY_HEART_DEFAULT_H = 7;
   private static final int LIQUID_DEFAULT_W = 8;
   private static final float LOOP_PROGRESS_HIDDEN = -1.0F;
   private static final int LIQUID_DEFAULT_H = 31;
   private static final float ATTACK_ESCAPE_MIN_HEALTH_FACTOR = 0.3F;
   private static final float ATTACK_PROGRESS_EASE_RATE = 18.0F;
   private static final float ATTACK_PROGRESS_EASE_SNAP_EPSILON = 5.0E-4F;
   private static final float ATTACK_PROGRESS_EASE_MAX_FRAME_SECONDS = 0.1F;
   private static final long DEFEATED_EASE_DURATION_TICKS = 300L;
   private static final float DEFEATED_MAX_STEP_MULTIPLIER = 4.0F;
   private static boolean hideVanillaHud = false;
   private static float loopProgress = 0.0F;
   private static long lastUpdateTick = -1L;
   private static UUID attackInstance = null;
   private static float attackProgress = 0.0F;
   private static float displayedAttackProgress = 0.0F;
   private static NonHudOverlay.AttackKey lastAttackKey = NonHudOverlay.AttackKey.NONE;
   private static long lastAttackDecayTick = -1L;
   private static boolean attackInputClearPending = false;
   private static boolean attackEscapable = true;
   private static AttackStateS2CPayload.EscapeProfile attackEscapeProfile = AttackStateS2CPayload.EscapeProfile.NONE;
   private static AttackStateS2CPayload.PvpRole attackPvpRole = AttackStateS2CPayload.PvpRole.NONE;
   private static int liquidStored = 0;
   private static int liquidCapacity = 200;
   private static int liquidTintRgb = 15920063;
   private static int runtimeAttackEscapeHits = -1;
   private static boolean uiPreviewEnabled = false;
   private static boolean uiPreviewOverrideActive = false;
   private static int previewEnergyX = 0;
   private static int previewEnergyY = 0;
   private static int previewEnergyHeartX = 0;
   private static int previewEnergyHeartY = 0;
   private static int previewAttackX = 0;
   private static int previewAttackY = 0;
   private static int previewPromptX = 0;
   private static int previewPromptY = 0;
   private static int previewLiquidX = 0;
   private static int previewLiquidY = 0;
   private static boolean previewEnergyVisible = true;
   private static boolean previewEnergyHeartVisible = true;
   private static boolean previewAttackVisible = true;
   private static boolean previewPromptVisible = true;
   private static boolean previewLiquidVisible = true;
   private static volatile NonHudOverlay.SpriteSize energyHeartSize = new NonHudOverlay.SpriteSize(7, 7);

   private NonHudOverlay() {
   }

   public static void registerReloadListener() {
      ResourceLoader.get(ResourceType.CLIENT_RESOURCES)
         .registerReloader(HUD_TEXTURE_SIZE_RELOADER_ID, (SynchronousResourceReloader)NonHudOverlay::reloadDirectTextureSizes);
   }

   public static void clientTick(MinecraftClient client) {
      if (client != null) {
         ClientPlayerEntity player = client.player;
         if (player == null || client.world == null) {
            clearAnimationHudState();
            liquidStored = 0;
            liquidTintRgb = 15920063;
         } else if (player.isSpectator()) {
            clearAnimationHudState();
         } else {
            boolean animActive = ClientAnimationRuntime.isActorPendingOrActive(player.getUuid());
            UUID activeInstance = ClientAnimationRuntime.findLatestActiveInstanceContaining(player.getUuid());
            if (!animActive) {
               clearAnimationHudState();
            } else {
               hideVanillaHud = true;
               if (attackInstance != null && activeInstance != null && !activeInstance.equals(attackInstance) && !attackInputClearPending) {
                  resetAttackState();
               }

               long now = client.world.getTime();
               if (lastUpdateTick != now) {
                  lastUpdateTick = now;
                  loopProgress = computeLoopProgress(player.getUuid(), now);
                  tickAttackDecay(now);
               }
            }
         }
      }
   }

   public static void onDisconnect(MinecraftClient client) {
      clearAnimationHudState();
      liquidStored = 0;
      liquidTintRgb = 15920063;
      clearPlayerRuntimeSettings();
   }

   private static void clearAnimationHudState() {
      hideVanillaHud = false;
      loopProgress = 0.0F;
      resetAttackState();
   }

   public static boolean shouldHideVanillaHud() {
      return hideVanillaHud;
   }

   public static boolean shouldRenderGameplayHud() {
      MinecraftClient client = MinecraftClient.getInstance();
      return client != null && client.player != null && client.options != null && !client.options.hudHidden && !client.player.isSpectator();
   }

   public static void renderOverlay(DrawContext ctx) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && ctx != null) {
         if (shouldRenderGameplayHud()) {
            updateDisplayedAttackProgress(client);
            NonHudOverlay.UiOffsets offsets = resolveUiOffsets(false);
            NonHudOverlay.UiVisibility visibility = resolveUiVisibility(false);
            NonHudOverlay.LayeredSprites energySprites = resolveLayeredSprites(ENERGY_BAR_BG, ENERGY_BAR_FILL, 182, 5);
            NonHudOverlay.Rect baseEnergy = energyBarRect(ctx, energySprites, 0, 0);
            NonHudOverlay.Rect energy = offsetRect(baseEnergy, offsets.energyX(), offsets.energyY());
            NonHudOverlay.LayeredSprites attackSprites = resolveLayeredSprites(ATTACK_BAR_BG, ATTACK_BAR_FILL, 81, 5);
            NonHudOverlay.Rect baseAttack = attackBarRect(baseEnergy, attackSprites, 0, 0);
            float displayedLoopProgress = loopProgress;
            if (client.player != null && client.world != null) {
               float tickProgress = client.getRenderTickCounter().getTickProgress(false);
               displayedLoopProgress = computeLoopProgress(client.player.getUuid(), (float)client.world.getTime() + Math.max(0.0F, tickProgress));
            }

            float progress = displayedLoopProgress < 0.0F ? -1.0F : Math.max(0.0F, Math.min(1.0F, displayedLoopProgress));
            if (visibility.energy() && progress >= 0.0F) {
               drawHorizontalBar(ctx, ENERGY_BAR_BG, ENERGY_BAR_FILL, energySprites, energy, progress);
            }

            if (client.player != null && attackInstance != null && ClientAnimationRuntime.isActorPendingOrActive(client.player.getUuid())) {
               UUID inst = ClientAnimationRuntime.findLatestActiveInstanceContaining(client.player.getUuid());
               if ((inst == null || inst.equals(attackInstance)) && attackEscapable) {
                  NonHudOverlay.Rect attack = attackBarDisplayRect(baseAttack, offsets);
                  if (visibility.attack()) {
                     drawHorizontalBar(ctx, ATTACK_BAR_BG, ATTACK_BAR_FILL, attackSprites, attack, displayedAttackProgress);
                  }

                  if (visibility.prompt()) {
                     NonHudOverlay.AttackPromptInfo promptInfo = resolveAttackPromptInfo();
                     NonHudOverlay.Rect prompt = attackPromptRect(ctx, baseAttack, promptInfo, offsets.promptX(), offsets.promptY());
                     int v = lastAttackKey == NonHudOverlay.AttackKey.LEFT ? promptInfo.frameHeight() : 0;
                     drawAttackPrompt(ctx, promptInfo, prompt, v);
                  }
               }
            }

            renderLiquidOverlay(ctx);
         }
      }
   }

   public static void renderLiquidOverlay(DrawContext ctx) {
      if (shouldRenderGameplayHud()) {
         renderLiquidOverlayInternal(ctx, false);
         renderEnergyHeartOverlay(ctx, false);
      }
   }

   public static void renderUiPreview(DrawContext ctx) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && ctx != null) {
         NonHudOverlay.UiOffsets offsets = resolveUiOffsets(true);
         NonHudOverlay.UiVisibility visibility = resolveUiVisibility(true);
         NonHudOverlay.LayeredSprites energySprites = resolveLayeredSprites(ENERGY_BAR_BG, ENERGY_BAR_FILL, 182, 5);
         NonHudOverlay.Rect baseEnergy = energyBarRect(ctx, energySprites, 0, 0);
         NonHudOverlay.Rect energy = offsetRect(baseEnergy, offsets.energyX(), offsets.energyY());
         NonHudOverlay.LayeredSprites attackSprites = resolveLayeredSprites(ATTACK_BAR_BG, ATTACK_BAR_FILL, 81, 5);
         NonHudOverlay.Rect baseAttack = attackBarRect(baseEnergy, attackSprites, 0, 0);
         if (visibility.energy()) {
            drawHorizontalBar(ctx, ENERGY_BAR_BG, ENERGY_BAR_FILL, energySprites, energy, 0.6F);
         }

         NonHudOverlay.Rect attack = attackBarDisplayRect(baseAttack, offsets);
         if (visibility.attack()) {
            drawHorizontalBar(ctx, ATTACK_BAR_BG, ATTACK_BAR_FILL, attackSprites, attack, 0.4F);
         }

         if (visibility.prompt()) {
            NonHudOverlay.AttackPromptInfo promptInfo = resolveAttackPromptInfo();
            NonHudOverlay.Rect prompt = attackPromptRect(ctx, baseAttack, promptInfo, offsets.promptX(), offsets.promptY());
            drawAttackPrompt(ctx, promptInfo, prompt, 0);
         }

         renderLiquidOverlayInternal(ctx, true);
         renderEnergyHeartOverlay(ctx, true);
      }
   }

   public static NonHudOverlay.UiPreviewBounds getUiPreviewBounds(DrawContext ctx) {
      if (ctx == null) {
         return NonHudOverlay.UiPreviewBounds.empty();
      }

      NonHudOverlay.UiOffsets offsets = resolveUiOffsets(true);
      NonHudOverlay.LayeredSprites energySprites = resolveLayeredSprites(ENERGY_BAR_BG, ENERGY_BAR_FILL, 182, 5);
      NonHudOverlay.Rect baseEnergy = energyBarRect(ctx, energySprites, 0, 0);
      NonHudOverlay.Rect energy = offsetRect(baseEnergy, offsets.energyX(), offsets.energyY());
      NonHudOverlay.LayeredSprites attackSprites = resolveLayeredSprites(ATTACK_BAR_BG, ATTACK_BAR_FILL, 81, 5);
      NonHudOverlay.Rect baseAttack = attackBarRect(baseEnergy, attackSprites, 0, 0);
      NonHudOverlay.Rect attack = attackBarDisplayRect(baseAttack, offsets);
      NonHudOverlay.AttackPromptInfo promptInfo = resolveAttackPromptInfo();
      NonHudOverlay.Rect prompt = attackPromptRect(ctx, baseAttack, promptInfo, offsets.promptX(), offsets.promptY());
      NonHudOverlay.LayeredSprites liquidSprites = resolveLiquidSprites();
      NonHudOverlay.Rect liquid = liquidTankRect(ctx, liquidSprites, offsets.liquidX(), offsets.liquidY());
      NonHudOverlay.Rect heart = energyHeartRect(ctx, true);
      return new NonHudOverlay.UiPreviewBounds(energy, heart, attack, prompt, liquid);
   }

   private static void renderLiquidOverlayInternal(DrawContext ctx, boolean preview) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && ctx != null) {
         if (resolveUiVisibility(preview).liquid()) {
            int capacity = liquidCapacity;
            int stored = liquidStored;
            if (preview) {
               capacity = Math.max(1, capacity);
               stored = Math.max(1, capacity / 2);
            }

            NonHudOverlay.UiOffsets offsets = resolveUiOffsets(preview);
            NonHudOverlay.LayeredSprites sprites = resolveLiquidSprites();
            NonHudOverlay.Rect tank = liquidTankRect(ctx, sprites, offsets.liquidX(), offsets.liquidY());
            if (stored > 0 && capacity > 0) {
               float progress = Math.max(0.0F, Math.min(1.0F, (float)stored / capacity));
               NonHudOverlay.Rect background = bottomLeftAlignedRect(tank, sprites.background());
               NonHudOverlay.Rect fill = bottomLeftAlignedRect(tank, sprites.fill());
               int filled = Math.max(0, Math.min(fill.height(), Math.round(progress * fill.height())));
               drawGuiSprite(ctx, LIQUID_TANK_BG, background);
               if (filled > 0) {
                  int fillY = fill.y() + fill.height() - filled;
                  ctx.enableScissor(fill.x(), fillY, fill.x() + fill.width(), fill.y() + fill.height());
                  int fillColor = preview ? -1 : NonLiquidColors.toOpaqueArgb(liquidTintRgb);
                  drawGuiSprite(ctx, LIQUID_TANK_FILL, fill, fillColor);
                  ctx.disableScissor();
               }
            }
         }
      }
   }

   private static void renderEnergyHeartOverlay(DrawContext ctx, boolean preview) {
      if (resolveUiVisibility(preview).energyHeart()) {
         NonHudOverlay.Rect rect = energyHeartRect(ctx, preview);
         renderEnergyHeart(ctx, rect, preview);
      }
   }

   private static NonHudOverlay.Rect energyHeartRect(DrawContext ctx, boolean preview) {
      NonHudOverlay.LayeredSprites liquidSprites = resolveLiquidSprites();
      NonHudOverlay.Rect tank = liquidTankRect(ctx, liquidSprites, 0, 0);
      NonHudOverlay.UiOffsets offsets = resolveUiOffsets(preview);
      NonHudOverlay.SpriteSize heart = energyHeartSize;
      int centerX = tank.x() + tank.width() / 2 + offsets.energyHeartX();
      int x = centerX - heart.width() / 2;
      int y = tank.y() - 1 - heart.height() + offsets.energyHeartY();
      return new NonHudOverlay.Rect(x, y, heart.width(), heart.height());
   }

   private static NonHudOverlay.Rect liquidTankRect(DrawContext ctx, NonHudOverlay.LayeredSprites sprites, int offsetX, int offsetY) {
      int heartsX = ctx.getScaledWindowWidth() / 2 - 91;
      int heartsY = ctx.getScaledWindowHeight() - 39;
      int x = heartsX + 192 - 8 + offsetX;
      int bottom = heartsY + 10 + 29 + offsetY;
      return new NonHudOverlay.Rect(x, bottom - sprites.height(), sprites.width(), sprites.height());
   }

   private static void renderEnergyHeart(DrawContext ctx, NonHudOverlay.Rect rect, boolean preview) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && ctx != null) {
         int energizedLevel = preview ? 2 : getEnergizedLevel(client.player);
         if (energizedLevel > 0) {
            double pulse = computeHeartPulse(energizedLevel);

            float scale = switch (energizedLevel) {
               case 1 -> 1.0F + (float)pulse * 0.35F;
               case 2 -> 1.0F + (float)pulse * 0.45F;
               default -> 1.0F + (float)pulse * 0.85F;
            };
            int centerX = rect.x() + rect.width() / 2;
            int centerY = rect.y() + rect.height() / 2;

            int alpha = switch (energizedLevel) {
               case 1 -> 150 + (int)Math.round(pulse * 70.0);
               case 2 -> 185 + (int)Math.round(pulse * 65.0);
               default -> 200 + (int)Math.round(pulse * 55.0);
            };
            int color = Math.max(0, Math.min(255, alpha)) << 24 | 16777215;
            Matrix3x2fStack matrices = ctx.getMatrices();
            matrices.pushMatrix();
            matrices.translate(centerX, centerY);
            matrices.scale(scale, scale);
            matrices.translate(-centerX, -centerY);
            ctx.drawTexture(
               RenderPipelines.GUI_TEXTURED, ENERGY_HEART, rect.x(), rect.y(), 0.0F, 0.0F, rect.width(), rect.height(), rect.width(), rect.height(), color
            );
            matrices.popMatrix();
         }
      }
   }

   public static int getEnergizedLevel(ClientPlayerEntity player) {
      if (player == null) {
         return 0;
      }

      for (StatusEffectInstance effect : player.getStatusEffects()) {
         if (effect.getEffectType().value() == NonStatusEffects.ENERGIZED) {
            return Math.max(1, Math.min(3, effect.getAmplifier() + 1));
         }
      }

      return 0;
   }

   public static double computeHeartPulse(int energizedLevel) {
      return computeHeartPulse(energizedLevel, 0L);
   }

   public static double computeHeartPulse(int energizedLevel, long timeOffsetMs) {
      double periodMs = switch (energizedLevel) {
         case 1 -> 900.0;
         case 2 -> 450.0;
         default -> 225.0;
      };

      double attackMs = switch (energizedLevel) {
         case 1 -> 120.0;
         case 2 -> 55.0;
         default -> 30.0;
      };

      double decayMs = switch (energizedLevel) {
         case 1 -> 200.0;
         case 2 -> 150.0;
         default -> 100.0;
      };
      double elapsedMs = Math.floorMod(System.currentTimeMillis() + Math.max(0L, timeOffsetMs), (long)periodMs);
      if (elapsedMs >= attackMs + decayMs) {
         return 0.0;
      } else if (elapsedMs < attackMs) {
         double attack = elapsedMs / attackMs;
         return attack * attack;
      } else {
         double decay = (elapsedMs - attackMs) / decayMs;
         double remaining = Math.max(0.0, 1.0 - decay);
         return remaining * remaining;
      }
   }

   private static void drawHorizontalBar(
      DrawContext ctx, Identifier backgroundId, Identifier fillId, NonHudOverlay.LayeredSprites sprites, NonHudOverlay.Rect bounds, float progress
   ) {
      float clamped = Math.max(0.0F, Math.min(1.0F, progress));
      NonHudOverlay.Rect background = bottomLeftAlignedRect(bounds, sprites.background());
      NonHudOverlay.Rect fill = bottomLeftAlignedRect(bounds, sprites.fill());
      int filled = Math.max(0, Math.min(fill.width(), Math.round(clamped * fill.width())));
      drawGuiSprite(ctx, backgroundId, background);
      if (filled > 0) {
         ctx.enableScissor(fill.x(), fill.y(), fill.x() + filled, fill.y() + fill.height());
         drawGuiSprite(ctx, fillId, fill);
         ctx.disableScissor();
      }
   }

   private static void drawAttackPrompt(DrawContext ctx, NonHudOverlay.AttackPromptInfo promptInfo, NonHudOverlay.Rect bounds, int v) {
      ctx.drawGuiTexture(
         RenderPipelines.GUI_TEXTURED,
         ATTACK_PROMPT,
         promptInfo.textureWidth(),
         promptInfo.textureHeight(),
         0,
         v,
         bounds.x(),
         bounds.y(),
         bounds.width(),
         bounds.height()
      );
   }

   private static void drawGuiSprite(DrawContext ctx, Identifier id, NonHudOverlay.Rect bounds) {
      ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, id, bounds.x(), bounds.y(), bounds.width(), bounds.height());
   }

   private static void drawGuiSprite(DrawContext ctx, Identifier id, NonHudOverlay.Rect bounds, int color) {
      ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, id, bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
   }

   private static NonHudOverlay.Rect energyBarRect(DrawContext ctx, NonHudOverlay.LayeredSprites sprites, int offsetX, int offsetY) {
      int x = (ctx.getScaledWindowWidth() - sprites.width()) / 2 + offsetX;
      int bottom = ctx.getScaledWindowHeight() - 27 + offsetY;
      return new NonHudOverlay.Rect(x, bottom - sprites.height(), sprites.width(), sprites.height());
   }

   private static NonHudOverlay.Rect attackBarRect(NonHudOverlay.Rect baseEnergy, NonHudOverlay.LayeredSprites sprites, int offsetX, int offsetY) {
      int x = baseEnergy.x() + (baseEnergy.width() - sprites.width()) / 2 + offsetX;
      int y = baseEnergy.y() - 8 - sprites.height() + offsetY;
      return new NonHudOverlay.Rect(x, y, sprites.width(), sprites.height());
   }

   private static NonHudOverlay.Rect attackBarDisplayRect(NonHudOverlay.Rect baseAttack, NonHudOverlay.UiOffsets offsets) {
      return offsetRect(baseAttack, offsets.attackX(), offsets.attackY() + -4);
   }

   private static NonHudOverlay.Rect attackPromptRect(
      DrawContext ctx, NonHudOverlay.Rect baseAttack, NonHudOverlay.AttackPromptInfo promptInfo, int offsetX, int offsetY
   ) {
      int frameW = Math.max(1, promptInfo.frameWidth());
      int frameH = Math.max(1, promptInfo.frameHeight());
      int x = (ctx.getScaledWindowWidth() - frameW) / 2 + offsetX;
      int y = Math.max(4, baseAttack.y() - frameH - 6 + offsetY);
      return new NonHudOverlay.Rect(x, y, frameW, frameH);
   }

   private static NonHudOverlay.Rect bottomLeftAlignedRect(NonHudOverlay.Rect bounds, NonHudOverlay.SpriteSize size) {
      return new NonHudOverlay.Rect(bounds.x(), bounds.y() + bounds.height() - size.height(), size.width(), size.height());
   }

   private static NonHudOverlay.Rect offsetRect(NonHudOverlay.Rect rect, int offsetX, int offsetY) {
      return new NonHudOverlay.Rect(rect.x() + offsetX, rect.y() + offsetY, rect.width(), rect.height());
   }

   public static double getCurrentAnimationStageProgress(UUID playerUuid) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (playerUuid != null && client != null && client.world != null) {
         float progress = computeLoopProgress(playerUuid, client.world.getTime());
         return progress == -1.0F ? Double.NaN : clampProgress(progress);
      } else {
         return Double.NaN;
      }
   }

   private static float computeLoopProgress(UUID playerUuid, double nowTick) {
      UUID inst = ClientAnimationRuntime.findLatestActiveInstanceContaining(playerUuid);
      if (inst == null) {
         return 0.0F;
      } else {
         long stageStartTick = ClientAnimationRuntime.findStageStartTick(inst);
         long stageEndTick = ClientAnimationRuntime.findStageEndTick(inst);
         AnimationStageInfo currentStage = ClientAnimationRuntime.findCurrentStage(inst);
         if (stageStartTick < 0L || currentStage == null) {
            return 0.0F;
         } else {
            return stageEndTick <= stageStartTick ? -1.0F : clampProgress((nowTick - stageStartTick) / (stageEndTick - stageStartTick));
         }
      }
   }

   private static float clampProgress(double progress) {
      return (float)Math.max(0.0, Math.min(1.0, progress));
   }

   private static NonHudOverlay.AttackPromptInfo resolveAttackPromptInfo() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return new NonHudOverlay.AttackPromptInfo(68, 128);
      }

      SpriteAtlasTexture atlas = client.getAtlasManager().getAtlasTexture(Atlases.GUI);
      Sprite sprite = atlas.getSprite(ATTACK_PROMPT);
      if (sprite == null) {
         return new NonHudOverlay.AttackPromptInfo(68, 128);
      }

      int texW = sprite.getContents().getWidth();
      int texH = sprite.getContents().getHeight();
      return texW > 0 && texH > 0 ? new NonHudOverlay.AttackPromptInfo(texW, texH) : new NonHudOverlay.AttackPromptInfo(68, 128);
   }

   private static NonHudOverlay.LayeredSprites resolveLiquidSprites() {
      return resolveLayeredSprites(LIQUID_TANK_BG, LIQUID_TANK_FILL, 8, 31);
   }

   private static NonHudOverlay.LayeredSprites resolveLayeredSprites(Identifier background, Identifier fill, int fallbackW, int fallbackH) {
      return new NonHudOverlay.LayeredSprites(resolveSpriteSize(background, fallbackW, fallbackH), resolveSpriteSize(fill, fallbackW, fallbackH));
   }

   private static NonHudOverlay.SpriteSize resolveSpriteSize(Identifier id, int fallbackW, int fallbackH) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return new NonHudOverlay.SpriteSize(fallbackW, fallbackH);
      }

      SpriteAtlasTexture atlas = client.getAtlasManager().getAtlasTexture(Atlases.GUI);
      Sprite sprite = atlas.getSprite(id);
      if (sprite == null) {
         return new NonHudOverlay.SpriteSize(fallbackW, fallbackH);
      }

      int texW = sprite.getContents().getWidth();
      int texH = sprite.getContents().getHeight();
      return texW > 0 && texH > 0 ? new NonHudOverlay.SpriteSize(texW, texH) : new NonHudOverlay.SpriteSize(fallbackW, fallbackH);
   }

   private static void reloadDirectTextureSizes(ResourceManager manager) {
      energyHeartSize = readDirectTextureSize(manager, ENERGY_HEART, 7, 7);
   }

   private static NonHudOverlay.SpriteSize readDirectTextureSize(ResourceManager manager, Identifier id, int fallbackW, int fallbackH) {
      if (manager == null) {
         return new NonHudOverlay.SpriteSize(fallbackW, fallbackH);
      }

      try {
         Resource resource = (Resource)manager.getResource(id).orElse(null);
         if (resource == null) {
            return new NonHudOverlay.SpriteSize(fallbackW, fallbackH);
         }

         try (InputStream input = resource.getInputStream()) {
            NativeImage image = NativeImage.read(input);

            label106: {
               NonHudOverlay.SpriteSize var9;
               try {
                  int width = image.getWidth();
                  int height = image.getHeight();
                  if (width <= 0 || height <= 0) {
                     break label106;
                  }

                  var9 = new NonHudOverlay.SpriteSize(width, height);
               } catch (Throwable var12) {
                  if (image != null) {
                     try {
                        image.close();
                     } catch (Throwable var11) {
                        var12.addSuppressed(var11);
                     }
                  }

                  throw var12;
               }

               if (image != null) {
                  image.close();
               }

               return var9;
            }

            if (image != null) {
               image.close();
            }

            return new NonHudOverlay.SpriteSize(fallbackW, fallbackH);
         }
      } catch (IOException | RuntimeException var14) {
         return new NonHudOverlay.SpriteSize(fallbackW, fallbackH);
      }
   }

   public static void setRuntimeAttackEscapeHits(int attackEscapeHits) {
      runtimeAttackEscapeHits = Math.max(1, Math.min(50, attackEscapeHits));
   }

   public static int getRuntimeLoopSeconds() {
      return resolveRuntimeLoopSeconds();
   }

   public static int getRuntimePeakLoopSeconds() {
      return resolveRuntimePeakLoopSeconds();
   }

   public static int getRuntimeAttackEscapeHits() {
      return resolveRuntimeAttackEscapeHits();
   }

   public static double getRuntimeAttackDecayPerSecond() {
      return resolveRuntimeAttackDecayPerSecond();
   }

   public static int getRuntimeAttackEscapeDamageDifficultyPercent() {
      return resolveRuntimeAttackEscapeDamageDifficultyPercent();
   }

   public static boolean getRuntimeAttackCreativePlayers() {
      NonConfig config = NeedsOfNature.getConfig();
      return config != null && config.canAttackCreativePlayers();
   }

   private static int resolveRuntimeLoopSeconds() {
      NonConfig config = NeedsOfNature.getConfig();
      return config == null ? 15 : config.getLoopProgressSeconds();
   }

   private static int resolveRuntimePeakLoopSeconds() {
      NonConfig config = NeedsOfNature.getConfig();
      return config == null ? 7 : config.getPeakLoopProgressSeconds();
   }

   private static int resolveRuntimeAttackEscapeHits() {
      if (runtimeAttackEscapeHits >= 0) {
         return Math.max(1, Math.min(50, runtimeAttackEscapeHits));
      }

      NonConfig config = NeedsOfNature.getConfig();
      return config == null ? 12 : config.getAttackEscapeHits();
   }

   private static double resolveRuntimeAttackDecayPerSecond() {
      NonConfig config = NeedsOfNature.getConfig();
      return config == null ? 0.2 : config.getAttackDecayPerSecond();
   }

   private static int resolveRuntimeAttackEscapeDamageDifficultyPercent() {
      NonConfig config = NeedsOfNature.getConfig();
      return config == null ? 100 : config.getAttackEscapeDamageDifficultyPercent();
   }

   private static void clearPlayerRuntimeSettings() {
      runtimeAttackEscapeHits = -1;
   }

   public static void setLiquidState(int stored, int capacity, int tintRgb) {
      liquidStored = Math.max(0, stored);
      liquidCapacity = Math.max(1, capacity);
      liquidTintRgb = tintRgb & 16777215;
      if (liquidStored > liquidCapacity) {
         liquidStored = liquidCapacity;
      }
   }

   public static float getLiquidFillRatio() {
      if (liquidCapacity <= 0) {
         return 0.0F;
      }

      float ratio = (float)liquidStored / liquidCapacity;
      return Math.max(0.0F, Math.min(1.0F, ratio));
   }

   public static int getLiquidTintRgb() {
      return liquidTintRgb;
   }

   public static void setEscapeControlState(
      UUID instanceId,
      boolean attack,
      boolean escapable,
      AttackStateS2CPayload.EscapeProfile escapeProfile,
      AttackStateS2CPayload.PvpRole pvpRole,
      float progress
   ) {
      if (instanceId != null) {
         if (attack) {
            if (!instanceId.equals(attackInstance)) {
               resetAttackState();
               attackInstance = instanceId;
               attackInputClearPending = true;
            }

            attackEscapable = escapable;
            attackEscapeProfile = sanitizeEscapeProfile(escapeProfile, attack);
            attackPvpRole = pvpRole == null ? AttackStateS2CPayload.PvpRole.NONE : pvpRole;
            if (attackPvpRole != AttackStateS2CPayload.PvpRole.NONE) {
               attackProgress = MathHelper.clamp(progress, 0.0F, 1.0F);
            }
         } else if (instanceId.equals(attackInstance)) {
            resetAttackState();
         }
      }
   }

   private static AttackStateS2CPayload.EscapeProfile sanitizeEscapeProfile(AttackStateS2CPayload.EscapeProfile profile, boolean attack) {
      if (!attack) {
         return AttackStateS2CPayload.EscapeProfile.NONE;
      } else {
         return profile != null && profile != AttackStateS2CPayload.EscapeProfile.NONE ? profile : AttackStateS2CPayload.EscapeProfile.NORMAL;
      }
   }

   public static boolean isAttackInstance(UUID instanceId) {
      return instanceId != null && instanceId.equals(attackInstance);
   }

   public static void recordAttackInput(UUID instanceId, NonHudOverlay.AttackKey key) {
      if (isAttackInstance(instanceId)) {
         if (key != NonHudOverlay.AttackKey.NONE) {
            if (attackEscapable) {
               if (lastAttackKey != key) {
                  if (attackPvpRole == AttackStateS2CPayload.PvpRole.NONE) {
                     float step = resolveAttackProgressStep();
                     attackProgress = Math.min(1.0F, attackProgress + step);
                  }

                  lastAttackKey = key;
               }
            }
         }
      }
   }

   public static boolean isServerControlledAttack() {
      return attackPvpRole != AttackStateS2CPayload.PvpRole.NONE;
   }

   public static boolean isAttackProgressComplete() {
      return !isServerControlledAttack() && attackProgress >= 1.0F;
   }

   public static void resetAttackProgress() {
      attackProgress = 0.0F;
      displayedAttackProgress = 0.0F;
      lastAttackKey = NonHudOverlay.AttackKey.NONE;
      lastAttackDecayTick = -1L;
   }

   private static void updateDisplayedAttackProgress(MinecraftClient client) {
      if (attackInstance == null) {
         displayedAttackProgress = attackProgress;
      } else {
         float difference = attackProgress - displayedAttackProgress;
         if (Math.abs(difference) <= 5.0E-4F) {
            displayedAttackProgress = attackProgress;
         } else {
            float frameSeconds = MathHelper.clamp(client.getRenderTickCounter().getDynamicDeltaTicks() / 20.0F, 0.0F, 0.1F);
            float alpha = 1.0F - (float)Math.exp(-18.0F * frameSeconds);
            displayedAttackProgress = MathHelper.clamp(MathHelper.lerp(alpha, displayedAttackProgress, attackProgress), 0.0F, 1.0F);
         }
      }
   }

   private static void resetAttackState() {
      attackInstance = null;
      resetAttackProgress();
      attackInputClearPending = false;
      attackEscapable = true;
      attackEscapeProfile = AttackStateS2CPayload.EscapeProfile.NONE;
      attackPvpRole = AttackStateS2CPayload.PvpRole.NONE;
   }

   public static void setUiPreviewEnabled(boolean enabled) {
      uiPreviewEnabled = enabled;
      if (!enabled) {
         uiPreviewOverrideActive = false;
      }
   }

   public static boolean isUiPreviewEnabled() {
      return uiPreviewEnabled;
   }

   public static void setUiPreviewOffsets(
      int energyX,
      int energyY,
      int attackX,
      int attackY,
      int promptX,
      int promptY,
      int liquidX,
      int liquidY,
      int energyHeartX,
      int energyHeartY,
      boolean energyVisible,
      boolean energyHeartVisible,
      boolean attackVisible,
      boolean promptVisible,
      boolean liquidVisible
   ) {
      uiPreviewOverrideActive = true;
      previewEnergyX = energyX;
      previewEnergyY = energyY;
      previewAttackX = attackX;
      previewAttackY = attackY;
      previewPromptX = promptX;
      previewPromptY = promptY;
      previewLiquidX = liquidX;
      previewLiquidY = liquidY;
      previewEnergyHeartX = energyHeartX;
      previewEnergyHeartY = energyHeartY;
      previewEnergyVisible = energyVisible;
      previewEnergyHeartVisible = energyHeartVisible;
      previewAttackVisible = attackVisible;
      previewPromptVisible = promptVisible;
      previewLiquidVisible = liquidVisible;
   }

   private static NonHudOverlay.UiOffsets resolveUiOffsets(boolean preview) {
      NonConfig config = NeedsOfNature.getConfig();
      int energyX = config == null ? 0 : config.getUiEnergyOffsetX();
      int energyY = config == null ? 0 : config.getUiEnergyOffsetY();
      int energyHeartX = config == null ? 0 : config.getUiEnergyHeartOffsetX();
      int energyHeartY = config == null ? 0 : config.getUiEnergyHeartOffsetY();
      int attackX = config == null ? 0 : config.getUiAttackOffsetX();
      int attackY = config == null ? 0 : config.getUiAttackOffsetY();
      int promptX = config == null ? 0 : config.getUiPromptOffsetX();
      int promptY = config == null ? 0 : config.getUiPromptOffsetY();
      int liquidX = config == null ? 0 : config.getUiLiquidOffsetX();
      int liquidY = config == null ? 0 : config.getUiLiquidOffsetY();
      return preview && uiPreviewOverrideActive
         ? new NonHudOverlay.UiOffsets(
            previewEnergyX,
            previewEnergyY,
            previewEnergyHeartX,
            previewEnergyHeartY,
            previewAttackX,
            previewAttackY,
            previewPromptX,
            previewPromptY,
            previewLiquidX,
            previewLiquidY
         )
         : new NonHudOverlay.UiOffsets(energyX, energyY, energyHeartX, energyHeartY, attackX, attackY, promptX, promptY, liquidX, liquidY);
   }

   private static NonHudOverlay.UiVisibility resolveUiVisibility(boolean preview) {
      NonConfig config = NeedsOfNature.getConfig();
      boolean energy = config == null || config.isUiEnergyVisible();
      boolean energyHeart = config == null || config.isUiEnergyHeartVisible();
      boolean attack = config == null || config.isUiAttackVisible();
      boolean prompt = config == null || config.isUiPromptVisible();
      boolean liquid = config == null || config.isUiLiquidVisible();
      return preview && uiPreviewOverrideActive
         ? new NonHudOverlay.UiVisibility(previewEnergyVisible, previewEnergyHeartVisible, previewAttackVisible, previewPromptVisible, previewLiquidVisible)
         : new NonHudOverlay.UiVisibility(energy, energyHeart, attack, prompt, liquid);
   }

   public static boolean consumeAttackInputClear() {
      if (!attackInputClearPending) {
         return false;
      }

      attackInputClearPending = false;
      return true;
   }

   private static float resolveAttackProgressStep() {
      int hits = resolveRuntimeAttackEscapeHits();
      if (hits <= 0) {
         return 1.0F;
      }

      float base = 1.0F / hits;
      float healthFactor = resolveAttackHealthFactor();
      if (healthFactor <= 0.0F) {
         return 0.0F;
      }

      float energyFactor = resolveAttackEnergyFactor();
      if (energyFactor <= 0.0F) {
         return 0.0F;
      }

      float rngScale = healthFactor >= 0.999F ? 1.0F : randomAttackScale();
      float defeatedEase = resolveDefeatedEaseProgress();
      if (defeatedEase <= 0.0F) {
         return base * healthFactor * rngScale * energyFactor;
      }

      float adjustedHealth = MathHelper.lerp(defeatedEase, healthFactor, 1.0F);
      float adjustedEnergy = MathHelper.lerp(defeatedEase, energyFactor, 1.0F);
      float adjustedRng = MathHelper.lerp(defeatedEase, rngScale, 1.0F);
      float multiplier = MathHelper.lerp(defeatedEase, 1.0F, 4.0F);
      return base * adjustedHealth * adjustedRng * adjustedEnergy * multiplier;
   }

   private static float resolveDefeatedEaseProgress() {
      if (attackInstance == null) {
         return 0.0F;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.player == null || client.world == null) {
         return 0.0F;
      }

      if (attackEscapeProfile != AttackStateS2CPayload.EscapeProfile.DEFEATED) {
         return 0.0F;
      }

      Long startTick = ClientAnimationRuntime.findStartTickForActor(client.player.getUuid());
      if (startTick == null) {
         return 0.0F;
      }

      long elapsed = Math.max(0L, client.world.getTime() - startTick);
      return MathHelper.clamp((float)elapsed / 300.0F, 0.0F, 1.0F);
   }

   private static float resolveAttackHealthFactor() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null) {
         float health = client.player.getHealth();
         float maxHealth = client.player.getMaxHealth();
         float minHealth = 0.5F;
         if (maxHealth <= minHealth) {
            return 0.3F;
         }

         float rawFactor = (health - minHealth) / (maxHealth - minHealth);
         rawFactor = MathHelper.clamp(rawFactor, 0.0F, 1.0F);
         float impact = resolveRuntimeAttackEscapeDamageDifficultyPercent() / 100.0F;
         float factor = 1.0F - (1.0F - rawFactor) * impact;
         return MathHelper.clamp(factor, 0.3F, 1.0F);
      } else {
         return 1.0F;
      }
   }

   private static float resolveAttackEnergyFactor() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player instanceof EnergyHolder holder) {
         int var6 = Math.max(1, holder.getMaxEnergy());
         float energy = Math.max(0.0F, Math.min(var6, holder.getEnergy()));
         if (energy <= 100.0F) {
            float t = energy / 100.0F;
            return 1.0F - 0.1F * t;
         } else {
            float t = Math.min(1.0F, (energy - 100.0F) / 100.0F);
            float factor = 0.9F - 0.82F * t * t;
            return Math.max(0.08F, factor);
         }
      } else {
         return 1.0F;
      }
   }

   private static float randomAttackScale() {
      return 0.5F + ThreadLocalRandom.current().nextFloat();
   }

   private static void tickAttackDecay(long nowTick) {
      if (attackInstance == null) {
         lastAttackDecayTick = nowTick;
      } else if (isServerControlledAttack()) {
         lastAttackDecayTick = nowTick;
      } else if (lastAttackDecayTick < 0L) {
         lastAttackDecayTick = nowTick;
      } else {
         long elapsedTicks = nowTick - lastAttackDecayTick;
         if (elapsedTicks > 0L) {
            lastAttackDecayTick = nowTick;
            double decayPerSecond = resolveRuntimeAttackDecayPerSecond();
            if (!(decayPerSecond <= 0.0)) {
               float decayPerTick = (float)(decayPerSecond / 20.0);
               attackProgress = Math.max(0.0F, attackProgress - decayPerTick * (float)elapsedTicks);
            }
         }
      }
   }

   public enum AttackKey {
      NONE,
      LEFT,
      RIGHT;
   }

   private record AttackPromptInfo(int textureWidth, int textureHeight) {
      int frameWidth() {
         return this.textureWidth;
      }

      int frameHeight() {
         return this.textureHeight / 2;
      }
   }

   private record LayeredSprites(NonHudOverlay.SpriteSize background, NonHudOverlay.SpriteSize fill) {
      int width() {
         return Math.max(this.background.width(), this.fill.width());
      }

      int height() {
         return Math.max(this.background.height(), this.fill.height());
      }
   }

   public record Rect(int x, int y, int width, int height) {
      public boolean contains(double mouseX, double mouseY) {
         return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
      }

      public NonHudOverlay.Rect padded(int padding) {
         int safe = Math.max(0, padding);
         return new NonHudOverlay.Rect(this.x - safe, this.y - safe, this.width + safe * 2, this.height + safe * 2);
      }
   }

   private record SpriteSize(int width, int height) {
   }

   private record UiOffsets(
      int energyX, int energyY, int energyHeartX, int energyHeartY, int attackX, int attackY, int promptX, int promptY, int liquidX, int liquidY
   ) {
   }

   public record UiPreviewBounds(
      NonHudOverlay.Rect energy, NonHudOverlay.Rect energyHeart, NonHudOverlay.Rect attack, NonHudOverlay.Rect prompt, NonHudOverlay.Rect liquid
   ) {
      public static NonHudOverlay.UiPreviewBounds empty() {
         NonHudOverlay.Rect empty = new NonHudOverlay.Rect(0, 0, 0, 0);
         return new NonHudOverlay.UiPreviewBounds(empty, empty, empty, empty, empty);
      }
   }

   private record UiVisibility(boolean energy, boolean energyHeart, boolean attack, boolean prompt, boolean liquid) {
   }
}
