package com.nonid.integration;

import com.nonid.NonConfig;
import com.nonid.client.NonPlayerInteractionClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ElementListWidget.Entry;
import net.minecraft.text.Text;

final class NonPlayerProfilesScreen extends Screen {
   private static final String[] MODES = new String[]{"deny_all", "requests_only", "requests_and_attacks", "auto_accept"};
   private final Screen parent;
   private ButtonWidget defaultModeButton;
   private ButtonWidget typedModeButton;
   private TextFieldWidget playerNameField;
   private NonPlayerProfilesScreen.ProfileList profileList;
   private String typedMode = "requests_and_attacks";
   private int renderedRevision = -1;

   NonPlayerProfilesScreen(Screen parent) {
      super(Text.translatable("gui.needsofnature.player_profiles.title"));
      this.parent = parent;
   }

   protected void init() {
      int centerX = this.width / 2;
      int top = 34;
      this.defaultModeButton = ButtonWidget.builder(Text.literal(""), ignored -> {
         NonPlayerInteractionClient.ProfileSnapshot snapshot = NonPlayerInteractionClient.profileSnapshot();
         String next = nextAllowedMode(snapshot.default_mode(), snapshot.allowed_mask());
         NonPlayerInteractionClient.setDefaultProfile(next);
      }).dimensions(centerX + 20, top, 180, 20).build();
      this.addDrawableChild(this.defaultModeButton);
      this.playerNameField = new TextFieldWidget(
         this.textRenderer, centerX - 220, top + 30, 185, 20, Text.translatable("gui.needsofnature.player_profiles.player_name")
      );
      this.playerNameField.setMaxLength(64);
      this.playerNameField.setPlaceholder(Text.translatable("gui.needsofnature.player_profiles.player_name_hint"));
      this.addDrawableChild(this.playerNameField);
      this.typedModeButton = ButtonWidget.builder(modeText(this.typedMode), ignored -> {
         this.typedMode = nextAllowedMode(this.typedMode, NonPlayerInteractionClient.profileSnapshot().allowed_mask());
         this.typedModeButton.setMessage(modeText(this.typedMode));
      }).dimensions(centerX - 29, top + 30, 174, 20).build();
      this.addDrawableChild(this.typedModeButton);
      this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.needsofnature.player_profiles.apply"), ignored -> {
         String name = this.playerNameField.getText().trim();
         if (!name.isEmpty()) {
            NonPlayerInteractionClient.setPlayerProfile(name, this.typedMode);
         }
      }).dimensions(centerX + 151, top + 30, 69, 20).build());
      int listTop = top + 60;
      int bottom = 38;
      this.profileList = new NonPlayerProfilesScreen.ProfileList(
         this.client,
         this.width,
         Math.max(40, this.height - listTop - bottom),
         listTop,
         this.textRenderer,
         this::setProfile,
         NonPlayerInteractionClient::removePlayerProfile
      );
      this.addDrawableChild(this.profileList);
      if (NonModMenuScreens.canEditServerGameplaySettings()) {
         this.addDrawableChild(
            ButtonWidget.builder(
                  Text.translatable("gui.needsofnature.player_profiles.allowed_profiles"),
                  ignored -> MinecraftClient.getInstance()
                     .setScreen(new NonPlayerProfilesScreen.AllowedProfilesScreen(this, NonModMenuScreens.serverConfigForScreen()))
               )
               .dimensions(centerX - 220, this.height - 28, 135, 20)
               .build()
         );
      }

      this.addDrawableChild(
         ButtonWidget.builder(Text.translatable("gui.done"), ignored -> this.close()).dimensions(centerX - 75, this.height - 28, 150, 20).build()
      );
      NonPlayerInteractionClient.requestProfiles();
      this.refreshFromSnapshot(true);
   }

   public void tick() {
      super.tick();
      this.refreshFromSnapshot(false);
   }

   private void refreshFromSnapshot(boolean force) {
      int revision = NonPlayerInteractionClient.profileRevision();
      if (force || revision != this.renderedRevision) {
         this.renderedRevision = revision;
         NonPlayerInteractionClient.ProfileSnapshot snapshot = NonPlayerInteractionClient.profileSnapshot();
         if (this.defaultModeButton != null) {
            this.defaultModeButton.setMessage(modeText(snapshot.default_mode()));
         }

         if (!isAllowed(this.typedMode, snapshot.allowed_mask())) {
            this.typedMode = firstAllowedMode(snapshot.allowed_mask());
            if (this.typedModeButton != null) {
               this.typedModeButton.setMessage(modeText(this.typedMode));
            }
         }

         if (this.profileList != null) {
            this.profileList.setSnapshot(snapshot);
         }
      }
   }

   private void setProfile(NonPlayerProfilesScreen.ProfileRow row) {
      String next = nextAllowedMode(row.mode(), NonPlayerInteractionClient.profileSnapshot().allowed_mask());
      NonPlayerInteractionClient.setPlayerProfile(row.uuid().toString(), next);
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      super.render(context, mouseX, mouseY, deltaTicks);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, -1);
      context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.needsofnature.player_profiles.default"), this.width / 2 - 220, 40, -3092272);
   }

   public void close() {
      MinecraftClient.getInstance().setScreen(this.parent);
   }

   private static Text modeText(String mode) {
      return Text.translatable("gui.needsofnature.player_profiles.mode." + normalizeMode(mode));
   }

   private static String normalizeMode(String mode) {
      for (String candidate : MODES) {
         if (candidate.equals(mode)) {
            return candidate;
         }
      }

      return "requests_and_attacks";
   }

   private static String nextAllowedMode(String current, int allowedMask) {
      String normalized = normalizeMode(current);
      int currentIndex = 0;

      for (int index = 0; index < MODES.length; index++) {
         if (MODES[index].equals(normalized)) {
            currentIndex = index;
         }
      }

      for (int offset = 1; offset <= MODES.length; offset++) {
         String candidate = MODES[(currentIndex + offset) % MODES.length];
         if (isAllowed(candidate, allowedMask)) {
            return candidate;
         }
      }

      return "requests_and_attacks";
   }

   private static String firstAllowedMode(int allowedMask) {
      for (String mode : MODES) {
         if (isAllowed(mode, allowedMask)) {
            return mode;
         }
      }

      return "requests_and_attacks";
   }

   private static boolean isAllowed(String mode, int allowedMask) {
      int index = List.of(MODES).indexOf(normalizeMode(mode));
      return index >= 0 && (allowedMask & 1 << index) != 0;
   }

   private static final class AllowedProfilesScreen extends Screen {
      private final Screen parent;
      private final NonConfig config;
      private final List<ButtonWidget> modeButtons = new ArrayList<>();
      private int mask;

      private AllowedProfilesScreen(Screen parent, NonConfig config) {
         super(Text.translatable("gui.needsofnature.player_profiles.allowed_profiles"));
         this.parent = parent;
         this.config = config;
         this.mask = config.getAllowedPlayerInteractionProfilesMask();
      }

      protected void init() {
         this.modeButtons.clear();
         int width = Math.min(340, this.width - 40);
         int startY = Math.max(42, this.height / 2 - 62);

         for (int index = 0; index < NonPlayerProfilesScreen.MODES.length; index++) {
            int bit = 1 << index;
            String mode = NonPlayerProfilesScreen.MODES[index];
            ButtonWidget button = ButtonWidget.builder(Text.literal(""), ignored -> this.toggle(bit))
               .dimensions(this.width / 2 - width / 2, startY + index * 24, width, 20)
               .build();
            this.modeButtons.add(button);
            this.addDrawableChild(button);
         }

         this.addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.done"), ignored -> this.close()).dimensions(this.width / 2 - 75, this.height - 28, 150, 20).build()
         );
         this.updateButtons();
      }

      private void toggle(int bit) {
         int next = this.mask ^ bit;
         if ((next & 15) != 0) {
            this.mask = next & 15;
            this.updateButtons();
         }
      }

      private void updateButtons() {
         for (int index = 0; index < this.modeButtons.size(); index++) {
            boolean enabled = (this.mask & 1 << index) != 0;
            this.modeButtons
               .get(index)
               .setMessage(
                  Text.translatable(
                     enabled ? "gui.needsofnature.player_profiles.allowed" : "gui.needsofnature.player_profiles.disallowed",
                     new Object[]{NonPlayerProfilesScreen.modeText(NonPlayerProfilesScreen.MODES[index])}
                  )
               );
         }
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         super.render(context, mouseX, mouseY, deltaTicks);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, -1);
         context.drawCenteredTextWithShadow(
            this.textRenderer, Text.translatable("gui.needsofnature.player_profiles.allowed_profiles_hint"), this.width / 2, 29, -5592406
         );
      }

      public void close() {
         this.config.setAllowedPlayerInteractionProfilesMask(this.mask);
         NonModMenuScreens.saveServerConfig(this.config);
         MinecraftClient.getInstance().setScreen(this.parent);
      }
   }

   private static final class ProfileList extends ElementListWidget<NonPlayerProfilesScreen.ProfileList.RowEntry> {
      private final TextRenderer textRenderer;
      private final Consumer<NonPlayerProfilesScreen.ProfileRow> onCycle;
      private final Consumer<String> onRemove;

      private ProfileList(
         MinecraftClient client,
         int width,
         int height,
         int top,
         TextRenderer textRenderer,
         Consumer<NonPlayerProfilesScreen.ProfileRow> onCycle,
         Consumer<String> onRemove
      ) {
         super(client, width, height, top, 34);
         this.textRenderer = textRenderer;
         this.onCycle = onCycle;
         this.onRemove = onRemove;
         this.centerListVertically = false;
      }

      private void setSnapshot(NonPlayerInteractionClient.ProfileSnapshot snapshot) {
         this.clearEntries();
         Map<String, NonPlayerInteractionClient.ProfileEntry> overrides = new HashMap<>();

         for (NonPlayerInteractionClient.ProfileEntry override : snapshot.overrides()) {
            overrides.put(override.uuid(), override);
         }

         for (NonPlayerInteractionClient.KnownPlayerEntry known : snapshot.known_players()) {
            try {
               UUID uuid = UUID.fromString(known.uuid());
               NonPlayerInteractionClient.ProfileEntry override = overrides.get(known.uuid());
               String mode = override == null ? snapshot.default_mode() : override.mode();
               this.addEntry(
                  new NonPlayerProfilesScreen.ProfileList.RowEntry(
                     new NonPlayerProfilesScreen.ProfileRow(uuid, known.name(), mode, override != null), this.textRenderer, this.onCycle, this.onRemove
                  )
               );
            } catch (IllegalArgumentException var8) {
            }
         }
      }

      public int getRowWidth() {
         return Math.min(440, Math.max(260, this.getWidth() - 30));
      }

      public int getRowLeft() {
         return (this.getWidth() - this.getRowWidth()) / 2;
      }

      protected int getScrollbarX() {
         return this.getWidth() - 8;
      }

      private static final class RowEntry extends Entry<NonPlayerProfilesScreen.ProfileList.RowEntry> {
         private final NonPlayerProfilesScreen.ProfileRow row;
         private final TextRenderer textRenderer;
         private final ButtonWidget modeButton;
         private final ButtonWidget removeButton;
         private final List<ClickableWidget> widgets;

         private RowEntry(
            NonPlayerProfilesScreen.ProfileRow row, TextRenderer textRenderer, Consumer<NonPlayerProfilesScreen.ProfileRow> onCycle, Consumer<String> onRemove
         ) {
            this.row = row;
            this.textRenderer = textRenderer;
            this.modeButton = ButtonWidget.builder(NonPlayerProfilesScreen.modeText(row.mode()), ignored -> onCycle.accept(row))
               .dimensions(0, 0, 170, 20)
               .build();
            this.removeButton = ButtonWidget.builder(Text.literal("x"), ignored -> onRemove.accept(row.uuid().toString())).dimensions(0, 0, 20, 20).build();
            this.removeButton.active = row.overridden();
            this.widgets = List.of(this.modeButton, this.removeButton);
         }

         public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            int x = this.getContentX() + 3;
            int y = this.getContentY() + 4;
            NonPlayerInteractionClient.drawPlayerFace(context, this.row.uuid(), x, y, 24);
            int right = this.getContentX() + this.getContentWidth() - 3;
            this.removeButton.setX(right - 20);
            this.removeButton.setY(y + 2);
            this.modeButton.setX(right - 196);
            this.modeButton.setY(y + 2);
            this.modeButton.render(context, mouseX, mouseY, deltaTicks);
            this.removeButton.render(context, mouseX, mouseY, deltaTicks);
            int textWidth = Math.max(30, this.modeButton.getX() - (x + 31) - 6);
            context.drawTextWithShadow(
               this.textRenderer, this.textRenderer.trimToWidth(this.row.name(), textWidth), x + 31, y + 8, this.row.overridden() ? -1 : -5592406
            );
         }

         public List<? extends Selectable> selectableChildren() {
            return this.widgets;
         }

         public List<? extends Element> children() {
            return this.widgets;
         }

         public void forEachChild(Consumer<ClickableWidget> consumer) {
            this.widgets.forEach(consumer);
         }
      }
   }

   private record ProfileRow(UUID uuid, String name, String mode, boolean overridden) {
   }
}
