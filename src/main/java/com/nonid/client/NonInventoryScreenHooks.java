package com.nonid.client;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.nonid.DestroyedSkinHolder;
import com.nonid.NeedsOfNature;
import com.nonid.NonSkinRepairBlocks;
import com.nonid.PregnancyCyclePhase;
import com.nonid.mixin.client.HandledScreenAccessor;
import com.nonid.network.ManualDamageDestroyedSkinC2SPayload;
import com.nonid.network.RepairDestroyedSkinC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BeforeRender;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.AllowMouseClick;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class NonInventoryScreenHooks {
   private static final int SURVIVAL_ICON_SIZE = 14;
   private static final int SURVIVAL_BUTTON_SIZE = 18;
   private static final int CREATIVE_ICON_SIZE = 12;
   private static final int CREATIVE_BUTTON_SIZE = 16;
   private static final int CYCLE_ICON_OFFSET_X = -2;
   private static final int CYCLE_ICON_OFFSET_Y = -1;
   private static final int SURVIVAL_CYCLE_X = 27;
   private static final int SURVIVAL_REPAIR_X = 58;
   private static final int SURVIVAL_BUTTON_Y = 61;
   private static final int SURVIVAL_PREVIEW_MIN_X = 20;
   private static final int SURVIVAL_PREVIEW_MAX_X = 82;
   private static final int SURVIVAL_PREVIEW_MIN_Y = 4;
   private static final int SURVIVAL_PREVIEW_MAX_Y = 82;
   private static final int CREATIVE_CYCLE_X = 72;
   private static final int CREATIVE_REPAIR_X = 90;
   private static final int CREATIVE_BUTTON_Y = 34;
   private static final int CREATIVE_PREVIEW_MIN_X = 73;
   private static final int CREATIVE_PREVIEW_MAX_X = 105;
   private static final int CREATIVE_PREVIEW_MIN_Y = 6;
   private static final int CREATIVE_PREVIEW_MAX_Y = 49;
   private static boolean registered;

   private NonInventoryScreenHooks() {
   }

   public static void register() {
      if (!registered) {
         registered = true;
         ScreenEvents.AFTER_INIT.register((AfterInit)(client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen<?> handledScreen && isPlayerInventoryScreen(screen)) {
               NonInventoryScreenHooks.InventoryControls controls = new NonInventoryScreenHooks.InventoryControls(screen, handledScreen);
               Screens.getButtons(screen).add(controls.repairButton);
               Screens.getButtons(screen).add(controls.cycleButton);
               controls.update();
               ScreenEvents.beforeRender(screen).register((BeforeRender)(current, context, mouseX, mouseY, delta) -> controls.update());
               ScreenMouseEvents.allowMouseClick(screen).register((AllowMouseClick)(current, click) -> click.button() != 0 || !controls.tryManualDamage(click));
            }
         });
      }
   }

   private static boolean isPlayerInventoryScreen(Screen screen) {
      return screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen;
   }

   private static boolean isInventoryViewVisible(Screen screen) {
      return !(screen instanceof CreativeInventoryScreen creative && !creative.isInventoryTabSelected());
   }

   private static final class InventoryControls {
      private final Screen screen;
      private final HandledScreen<?> handledScreen;
      private final ButtonWidget repairButton;
      private final NonInventoryScreenHooks.PregnancyCycleButton cycleButton;

      private InventoryControls(Screen screen, HandledScreen<?> handledScreen) {
         this.screen = screen;
         this.handledScreen = handledScreen;
         boolean creative = screen instanceof CreativeInventoryScreen;
         int buttonSize = creative ? 16 : 18;
         int iconSize = creative ? 12 : 14;
         this.repairButton = new NonInventoryScreenHooks.SkinRepairButton(buttonSize, iconSize);
         this.cycleButton = new NonInventoryScreenHooks.PregnancyCycleButton(buttonSize, iconSize);
      }

      private void update() {
         boolean inventoryVisible = NonInventoryScreenHooks.isInventoryViewVisible(this.screen);
         this.positionButtons();
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         int damage = player instanceof DestroyedSkinHolder holder ? holder.getDestroyedSkinDamage() : 0;
         boolean repairVisible = inventoryVisible && damage > 0;
         boolean nearRepairBlock = repairVisible
            && player != null
            && NonSkinRepairBlocks.isNearRepairBlock(player.getEntityWorld(), player.getBlockPos(), NeedsOfNature.getConfig());
         boolean allowCraftingTable = NeedsOfNature.getConfig().isCraftingTableSkinRepairAllowed();
         this.repairButton.visible = repairVisible;
         this.repairButton.active = nearRepairBlock;
         this.repairButton
            .setTooltip(
               Tooltip.of(
                  Text.translatable(
                     nearRepairBlock
                        ? "gui.needsofnature.repair_skin"
                        : (allowCraftingTable ? "gui.needsofnature.repair_skin.needs_loom_or_crafting_table" : "gui.needsofnature.repair_skin.needs_loom")
                  )
               )
            );
         boolean cycleVisible = inventoryVisible && NonPregnancyClientState.isCycleEnabled();
         this.cycleButton.visible = cycleVisible;
         if (cycleVisible) {
            PregnancyCyclePhase phase = NonPregnancyClientState.getCyclePhase();

            Text phaseText = Text.translatable(switch (phase) {
               case SAFE -> "config.needsofnature.pregnancy_cycle.safe";
               case NORMAL -> "config.needsofnature.pregnancy_cycle.normal";
               case RISKY -> "config.needsofnature.pregnancy_cycle.risky";
               case OVULATION -> "config.needsofnature.pregnancy_cycle.ovulation";
            });
            this.cycleButton.setPhase(phase);
            this.cycleButton
               .setTooltip(
                  Tooltip.of(Text.translatable("gui.needsofnature.pregnancy_cycle.tooltip", new Object[]{NonPregnancyClientState.getCycleDay(), 30, phaseText}))
               );
         }
      }

      private void positionButtons() {
         HandledScreenAccessor accessor = (HandledScreenAccessor)this.handledScreen;
         int left = accessor.non$getLeft();
         int top = accessor.non$getTop();
         if (this.screen instanceof CreativeInventoryScreen) {
            this.cycleButton.setPosition(left + 72, top + 34);
            this.repairButton.setPosition(left + 90, top + 34);
         } else {
            this.cycleButton.setPosition(left + 27, top + 61);
            this.repairButton.setPosition(left + 58, top + 61);
         }
      }

      private boolean tryManualDamage(Click click) {
         if (NonInventoryScreenHooks.isInventoryViewVisible(this.screen) && this.isInsidePlayerPreview(click.x(), click.y())) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player == null || !this.hasShearsForManualDamage(player)) {
               return false;
            }

            if (!ClientPlayNetworking.canSend(ManualDamageDestroyedSkinC2SPayload.ID)) {
               return false;
            }

            ClientPlayNetworking.send(new ManualDamageDestroyedSkinC2SPayload());
            return true;
         } else {
            return false;
         }
      }

      private boolean hasShearsForManualDamage(ClientPlayerEntity player) {
         ItemStack cursorStack = this.handledScreen.getScreenHandler().getCursorStack();
         return cursorStack.isOf(Items.SHEARS) || player.getMainHandStack().isOf(Items.SHEARS) || player.getOffHandStack().isOf(Items.SHEARS);
      }

      private boolean isInsidePlayerPreview(double mouseX, double mouseY) {
         HandledScreenAccessor accessor = (HandledScreenAccessor)this.handledScreen;
         int left = accessor.non$getLeft();
         int top = accessor.non$getTop();
         return this.screen instanceof CreativeInventoryScreen
            ? mouseX >= left + 73 && mouseX <= left + 105 && mouseY >= top + 6 && mouseY <= top + 49
            : mouseX >= left + 20 && mouseX <= left + 82 && mouseY >= top + 4 && mouseY <= top + 82;
      }
   }

   private static final class PregnancyCycleButton extends ButtonWidget {
      private final int iconSize;
      private PregnancyCyclePhase phase = PregnancyCyclePhase.NORMAL;

      private PregnancyCycleButton(int buttonSize, int iconSize) {
         super(0, 0, buttonSize, buttonSize, net.minecraft.text.Text.translatable(""), button -> {}, DEFAULT_NARRATION_SUPPLIER);
         this.iconSize = iconSize;
      }

      private void setPhase(PregnancyCyclePhase phase) {
         this.phase = phase == null ? PregnancyCyclePhase.NORMAL : phase;
      }

      protected void drawIcon(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         Identifier texture = switch (this.phase) {
            case SAFE -> NonIconButtonTextures.PREGNANCY_CYCLE_SAFE;
            case NORMAL -> NonIconButtonTextures.PREGNANCY_CYCLE_NORMAL;
            case RISKY -> NonIconButtonTextures.PREGNANCY_CYCLE_RISKY;
            case OVULATION -> NonIconButtonTextures.PREGNANCY_CYCLE_OVULATION;
         };
         GpuTextureView textureView = MinecraftClient.getInstance().getTextureManager().getTexture(texture).getGlTextureView();
         int textureWidth = Math.max(1, textureView.getWidth(0));
         int textureHeight = Math.max(1, textureView.getHeight(0));
         float displayScale = this.iconSize / 14.0F;
         int drawWidth = Math.max(1, Math.round(textureWidth * displayScale));
         int drawHeight = Math.max(1, Math.round(textureHeight * displayScale));
         int anchorX = this.getX() + (this.getWidth() - this.iconSize) / 2 + -2;
         int anchorBottom = this.getY() + (this.getHeight() - this.iconSize) / 2 + this.iconSize + -1;
         context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            texture,
            anchorX,
            anchorBottom - drawHeight,
            0.0F,
            0.0F,
            drawWidth,
            drawHeight,
            textureWidth,
            textureHeight,
            textureWidth,
            textureHeight
         );
      }

      public void playDownSound(SoundManager soundManager) {
      }
   }

   private static final class SkinRepairButton extends ButtonWidget {
      private final int iconSize;

      private SkinRepairButton(int buttonSize, int iconSize) {
         super(0, 0, buttonSize, buttonSize, net.minecraft.text.Text.translatable(""), button -> {
            if (ClientPlayNetworking.canSend(RepairDestroyedSkinC2SPayload.ID)) {
               ClientPlayNetworking.send(new RepairDestroyedSkinC2SPayload());
            }
         }, DEFAULT_NARRATION_SUPPLIER);
         this.iconSize = iconSize;
      }

      protected void drawIcon(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         int iconX = this.getX() + (this.getWidth() - this.iconSize) / 2;
         int iconY = this.getY() + (this.getHeight() - this.iconSize) / 2;
         int color = this.active ? -1 : -2130706433;
         context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            NonIconButtonTextures.SKIN_REPAIR,
            iconX,
            iconY,
            0.0F,
            0.0F,
            this.iconSize,
            this.iconSize,
            this.iconSize,
            this.iconSize,
            color
         );
      }

      public void playDownSound(SoundManager soundManager) {
      }
   }
}
