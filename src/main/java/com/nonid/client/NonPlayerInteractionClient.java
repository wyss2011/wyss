package com.nonid.client;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.nonid.NeedsOfNature;
import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import com.nonid.network.PlayerAnimationCancelC2SPayload;
import com.nonid.network.PlayerAnimationChoiceC2SPayload;
import com.nonid.network.PlayerAnimationConsentC2SPayload;
import com.nonid.network.PlayerAnimationConsentS2CPayload;
import com.nonid.network.PlayerAnimationRequestStatusS2CPayload;
import com.nonid.network.PlayerAnimationSelectionS2CPayload;
import com.nonid.network.PlayerProfileActionC2SPayload;
import com.nonid.network.PlayerProfileSnapshotS2CPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.ElementListWidget.Entry;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public final class NonPlayerInteractionClient {
   private static final Gson GSON = new Gson();
   private static final Identifier DEFAULT_PLAYER_SKIN = Identifier.ofVanilla("textures/entity/player/wide/steve.png");
   private static int profileRevision;
   private static NonPlayerInteractionClient.ProfileSnapshot profileSnapshot = new NonPlayerInteractionClient.ProfileSnapshot(
      "requests_and_attacks", 15, List.of(), List.of()
   );

   private NonPlayerInteractionClient() {
   }

   public static void register() {
      UseEntityCallback.EVENT
         .register(
            (UseEntityCallback)(player, world, hand, entity, hitResult) -> {
               if (world.isClient()
                  && hand == Hand.MAIN_HAND
                  && player.getMainHandStack().isEmpty()
                  && !player.isSpectator()
                  && entity != null
                  && !entity.isRemoved()
                  && !entity.getUuid().equals(player.getUuid())) {
                  boolean directRequest = player.isSneaking() && entity instanceof PlayerEntity;
                  boolean joinRequest = ClientAnimationRuntime.findLatestActiveInstanceContaining(entity.getUuid()) != null;
                  return (ActionResult)(!directRequest && !joinRequest ? ActionResult.PASS : ActionResult.SUCCESS);
               } else {
                  return ActionResult.PASS;
               }
            }
         );
      ClientPlayNetworking.registerGlobalReceiver(
         PlayerProfileSnapshotS2CPayload.ID, (payload, context) -> context.client().execute(() -> applyProfileSnapshot(payload))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         PlayerAnimationSelectionS2CPayload.ID, (payload, context) -> context.client().execute(() -> openSelection(payload))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         PlayerAnimationConsentS2CPayload.ID, (payload, context) -> context.client().execute(() -> openConsent(payload))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         PlayerAnimationRequestStatusS2CPayload.ID, (payload, context) -> context.client().execute(() -> applyRequestStatus(payload))
      );
   }

   public static void clear() {
      profileRevision++;
      profileSnapshot = new NonPlayerInteractionClient.ProfileSnapshot("requests_and_attacks", 15, List.of(), List.of());
   }

   public static int profileRevision() {
      return profileRevision;
   }

   public static NonPlayerInteractionClient.ProfileSnapshot profileSnapshot() {
      return profileSnapshot;
   }

   public static void requestProfiles() {
      sendProfileAction("request", "", "");
   }

   public static void setDefaultProfile(String mode) {
      sendProfileAction("default", "", mode);
   }

   public static void setPlayerProfile(String playerNameOrUuid, String mode) {
      sendProfileAction("set", playerNameOrUuid, mode);
   }

   public static void removePlayerProfile(String playerUuid) {
      sendProfileAction("remove", playerUuid, "");
   }

   private static void sendProfileAction(String action, String target, String mode) {
      if (ClientPlayNetworking.canSend(PlayerProfileActionC2SPayload.ID)) {
         ClientPlayNetworking.send(new PlayerProfileActionC2SPayload(action, target, mode));
      }
   }

   private static void applyProfileSnapshot(PlayerProfileSnapshotS2CPayload payload) {
      try {
         NonPlayerInteractionClient.ProfileSnapshot parsed = (NonPlayerInteractionClient.ProfileSnapshot)GSON.fromJson(
            payload.json(), NonPlayerInteractionClient.ProfileSnapshot.class
         );
         if (parsed != null) {
            profileSnapshot = parsed;
            profileRevision++;
         }
      } catch (JsonParseException e) {
         NeedsOfNature.LOGGER.warn("[NoN] Failed to parse player interaction profile snapshot.", e);
      }

      if (payload.errorKey() != null && !payload.errorKey().isBlank()) {
         sendLocalMessage(payload.errorKey());
      }
   }

   private static void openSelection(PlayerAnimationSelectionS2CPayload payload) {
      NonPlayerInteractionClient.Candidate[] parsed;
      try {
         parsed = (NonPlayerInteractionClient.Candidate[])GSON.fromJson(payload.candidatesJson(), NonPlayerInteractionClient.Candidate[].class);
      } catch (JsonParseException e) {
         NeedsOfNature.LOGGER.warn("[NoN] Failed to parse player animation candidates.", e);
         sendLocalMessage("gui.needsofnature.player_request.invalid");
         return;
      }

      List<NonPlayerInteractionClient.Candidate> candidates = parsed == null ? List.of() : List.of(parsed);
      if (candidates.isEmpty()) {
         sendLocalMessage("gui.needsofnature.player_request.no_match");
      } else {
         MinecraftClient client = MinecraftClient.getInstance();
         client.setScreen(new NonPlayerInteractionClient.AnimationSelectionScreen(payload.requestId(), payload.targetName(), candidates, payload.joinRequest()));
      }
   }

   private static void openConsent(PlayerAnimationConsentS2CPayload payload) {
      List<String> tags;
      try {
         String[] parsed = (String[])GSON.fromJson(payload.contentTagsJson(), String[].class);
         tags = parsed == null ? List.of() : List.of(parsed);
      } catch (JsonParseException e) {
         tags = List.of();
      }

      MinecraftClient.getInstance()
         .setScreen(
            new NonPlayerInteractionClient.ConsentScreen(
               payload.requestId(), payload.requesterUuid(), payload.requesterName(), payload.animationId(), payload.displayName(), tags, payload.joinRequest()
            )
         );
   }

   private static void applyRequestStatus(PlayerAnimationRequestStatusS2CPayload payload) {
      MinecraftClient client = MinecraftClient.getInstance();
      Screen current = client.currentScreen;
      if ("waiting".equals(payload.state())) {
         client.setScreen(new NonPlayerInteractionClient.WaitingScreen(payload.requestId()));
      } else {
         if (current instanceof NonPlayerInteractionClient.RequestBoundScreen requestScreen && requestScreen.requestId().equals(payload.requestId())) {
            requestScreen.markResolved();
            client.setScreen(null);
         }

         if (payload.messageKey() != null && !payload.messageKey().isBlank() && !"started".equals(payload.state())) {
            if (usesRequestActionBar(payload.messageKey())) {
               showRequestActionBar(payload.messageKey());
            } else {
               sendLocalMessage(payload.messageKey());
            }
         }
      }
   }

   private static boolean usesRequestActionBar(String translationKey) {
      return "gui.needsofnature.player_request.rejected".equals(translationKey) || "gui.needsofnature.player_request.energy_low".equals(translationKey);
   }

   private static void showRequestActionBar(String translationKey) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (translationKey != null && !translationKey.isBlank()) {
         Text message = "gui.needsofnature.player_request.energy_low".equals(translationKey)
            ? Text.translatable(translationKey, new Object[]{NeedsOfNature.getConfig().getPlayerRequestMinEnergy()})
            : Text.translatable(translationKey);
         client.inGameHud.setOverlayMessage(message, false);
      }
   }

   private static void sendLocalMessage(String translationKey) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null && translationKey != null && !translationKey.isBlank()) {
         client.player.sendMessage(Text.translatable(translationKey), false);
      }
   }

   public static void drawPlayerFace(DrawContext context, UUID playerUuid, int x, int y, int size) {
      MinecraftClient client = MinecraftClient.getInstance();
      Identifier texture = DEFAULT_PLAYER_SKIN;
      if (client.getNetworkHandler() != null && playerUuid != null) {
         PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(playerUuid);
         if (entry != null && entry.getSkinTextures() != null && entry.getSkinTextures().body() != null) {
            PlayerSkinDrawer.draw(context, entry.getSkinTextures(), x, y, size);
            return;
         }
      }

      drawTextureRegion(context, texture, x, y, 8, 8, size, size, 8, 8);
      drawTextureRegion(context, texture, x, y, 40, 8, size, size, 8, 8);
   }

   private static void drawTextureRegion(
      DrawContext context, Identifier texture, int x, int y, int u, int v, int width, int height, int regionWidth, int regionHeight
   ) {
      context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, 64, 64, regionWidth, regionHeight);
   }

   private static Tooltip blockRequirementTooltip(String requirement) {
      String summary = requirement != null && !requirement.isBlank() ? requirement : "block requirement";
      return Tooltip.of(Text.translatable("gui.needsofnature.player_request.block_requirement_tooltip", new Object[]{summary}));
   }

   private static Tooltip animationCardTooltip(NonPlayerInteractionClient.CandidateGroup group) {
      MutableText text = Text.literal(shortAnimationId(group.animationId()));
      if (!group.selectable()) {
         String summary = group.blockRequirement() != null && !group.blockRequirement().isBlank() ? group.blockRequirement() : "block requirement";
         text.append(Text.literal("\n")).append(Text.translatable("gui.needsofnature.player_request.block_requirement_tooltip", new Object[]{summary}));
      }

      return Tooltip.of(text);
   }

   private static String shortAnimationId(String raw) {
      Identifier id = Identifier.tryParse(raw);
      return id == null ? raw : id.getPath();
   }

   private static final class AnimationSelectionScreen extends Screen implements NonPlayerInteractionClient.RequestBoundScreen {
      private final UUID requestId;
      private final String targetName;
      private final List<NonPlayerInteractionClient.CandidateGroup> groups;
      private final boolean joinRequest;
      private boolean resolved;

      private AnimationSelectionScreen(UUID requestId, String targetName, List<NonPlayerInteractionClient.Candidate> candidates, boolean joinRequest) {
         super(Text.translatable(joinRequest ? "gui.needsofnature.player_request.select_join_title" : "gui.needsofnature.player_request.select_title"));
         this.requestId = requestId;
         this.targetName = targetName == null ? "Player" : targetName;
         this.joinRequest = joinRequest;
         Map<String, List<NonPlayerInteractionClient.Candidate>> grouped = new LinkedHashMap<>();

         for (NonPlayerInteractionClient.Candidate candidate : candidates) {
            grouped.computeIfAbsent(candidate.animation_id(), ignored -> new ArrayList<>()).add(candidate);
         }

         this.groups = grouped.entrySet()
            .stream()
            .map(entry -> new NonPlayerInteractionClient.CandidateGroup(entry.getKey(), List.copyOf(entry.getValue())))
            .toList();
      }

      protected void init() {
         int top = 48;
         int bottom = 34;
         NonPlayerInteractionClient.CandidateGridList list = new NonPlayerInteractionClient.CandidateGridList(
            this.client, this.width, Math.max(40, this.height - top - bottom), top, this.textRenderer, this::chooseGroup
         );
         list.setGroups(this.groups);
         this.addDrawableChild(list);
         this.addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.cancel"), ignored -> this.cancel()).dimensions(this.width / 2 - 75, this.height - 28, 150, 20).build()
         );
      }

      private void chooseGroup(NonPlayerInteractionClient.CandidateGroup group) {
         if (group.candidates().size() == 1) {
            if (group.first().selectable()) {
               this.choose(group.first());
            }
         } else {
            MinecraftClient.getInstance().setScreen(new NonPlayerInteractionClient.RoleSelectionScreen(this, this.requestId, group));
         }
      }

      private void choose(NonPlayerInteractionClient.Candidate candidate) {
         if (ClientPlayNetworking.canSend(PlayerAnimationChoiceC2SPayload.ID)) {
            ClientPlayNetworking.send(new PlayerAnimationChoiceC2SPayload(this.requestId, candidate.index()));
         }
      }

      private void cancel() {
         if (!this.resolved && ClientPlayNetworking.canSend(PlayerAnimationCancelC2SPayload.ID)) {
            ClientPlayNetworking.send(new PlayerAnimationCancelC2SPayload(this.requestId));
         }

         this.resolved = true;
         MinecraftClient.getInstance().setScreen(null);
      }

      public void close() {
         this.cancel();
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         super.render(context, mouseX, mouseY, deltaTicks);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, -1);
         context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable(
               this.joinRequest ? "gui.needsofnature.player_request.select_join_for" : "gui.needsofnature.player_request.select_for",
               new Object[]{this.targetName}
            ),
            this.width / 2,
            30,
            -5592406
         );
      }

      @Override
      public UUID requestId() {
         return this.requestId;
      }

      @Override
      public void markResolved() {
         this.resolved = true;
      }
   }

   private record Candidate(
      int index,
      String animation_id,
      String display_name,
      String requester_role,
      String requester_activity,
      boolean selectable,
      String block_requirement,
      List<String> actor_keys,
      List<String> content_tags,
      String pack_name,
      String pack_author
   ) {
      private Candidate {
         animation_id = animation_id == null ? "unknown" : animation_id;
         display_name = display_name == null ? "" : display_name.trim();
         requester_role = requester_role == null ? "player" : requester_role;
         requester_activity = "passive".equalsIgnoreCase(requester_activity) ? "passive" : "active";
         block_requirement = block_requirement == null ? "" : block_requirement;
         actor_keys = actor_keys == null ? List.of() : List.copyOf(actor_keys);
         content_tags = content_tags == null ? List.of() : List.copyOf(content_tags);
         pack_name = pack_name == null ? "" : pack_name;
         pack_author = pack_author == null ? "" : pack_author;
      }
   }

   private static final class CandidateGridList extends ElementListWidget<NonPlayerInteractionClient.CandidateGridList.RowEntry> {
      private final TextRenderer textRenderer;
      private final Consumer<NonPlayerInteractionClient.CandidateGroup> onChoose;

      private CandidateGridList(
         MinecraftClient client, int width, int height, int top, TextRenderer textRenderer, Consumer<NonPlayerInteractionClient.CandidateGroup> onChoose
      ) {
         super(client, width, height, top, 124);
         this.textRenderer = textRenderer;
         this.onChoose = onChoose;
         this.centerListVertically = false;
      }

      private void setGroups(List<NonPlayerInteractionClient.CandidateGroup> groups) {
         this.clearEntries();
         List<NonPlayerInteractionClient.CandidateGroup> available = groups.stream().filter(NonPlayerInteractionClient.CandidateGroup::selectable).toList();
         List<NonPlayerInteractionClient.CandidateGroup> unavailable = groups.stream().filter(group -> !group.selectable()).toList();
         this.addSection(Text.translatable("gui.needsofnature.player_request.section.available"), available);
         this.addSection(Text.translatable("gui.needsofnature.player_request.section.unavailable"), unavailable);
      }

      private void addSection(Text title, List<NonPlayerInteractionClient.CandidateGroup> groups) {
         if (!groups.isEmpty()) {
            this.addEntry(new NonPlayerInteractionClient.CandidateGridList.RowEntry(title, this.textRenderer), 22);

            for (int index = 0; index < groups.size(); index += 3) {
               this.addEntry(
                  new NonPlayerInteractionClient.CandidateGridList.RowEntry(
                     groups.subList(index, Math.min(groups.size(), index + 3)), this.textRenderer, this.onChoose
                  )
               );
            }
         }
      }

      public int getRowWidth() {
         return Math.min(510, Math.max(240, this.getWidth() - 28));
      }

      public int getRowLeft() {
         return (this.getWidth() - this.getRowWidth()) / 2;
      }

      protected int getScrollbarX() {
         return this.getWidth() - 8;
      }

      private static final class RowEntry extends Entry<NonPlayerInteractionClient.CandidateGridList.RowEntry> {
         private final Text sectionTitle;
         private final TextRenderer textRenderer;
         private final List<NonPlayerInteractionClient.PreviewCardWidget> cards;

         private RowEntry(Text sectionTitle, TextRenderer textRenderer) {
            this.sectionTitle = sectionTitle;
            this.textRenderer = textRenderer;
            this.cards = List.of();
         }

         private RowEntry(
            List<NonPlayerInteractionClient.CandidateGroup> groups, TextRenderer textRenderer, Consumer<NonPlayerInteractionClient.CandidateGroup> onChoose
         ) {
            this.sectionTitle = null;
            this.textRenderer = textRenderer;
            List<NonPlayerInteractionClient.PreviewCardWidget> widgets = new ArrayList<>();

            for (NonPlayerInteractionClient.CandidateGroup group : groups) {
               widgets.add(new NonPlayerInteractionClient.PreviewCardWidget(group, textRenderer, onChoose));
            }

            this.cards = List.copyOf(widgets);
         }

         public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            if (this.sectionTitle != null) {
               int centerX = this.getContentMiddleX();
               int textWidth = this.textRenderer.getWidth(this.sectionTitle);
               int lineY = this.getContentMiddleY();
               context.fill(this.getContentX(), lineY, centerX - textWidth / 2 - 8, lineY + 1, -11908534);
               context.fill(centerX + textWidth / 2 + 8, lineY, this.getContentRightEnd(), lineY + 1, -11908534);
               context.drawCenteredTextWithShadow(this.textRenderer, this.sectionTitle, centerX, lineY - 9 / 2, -5197648);
            } else {
               int gap = 6;
               int cardWidth = Math.max(70, (this.getContentWidth() - gap * 2) / 3);
               int x = this.getContentX();
               int y = this.getContentY() + 2;

               for (NonPlayerInteractionClient.PreviewCardWidget card : this.cards) {
                  card.setX(x);
                  card.setY(y);
                  card.setWidth(cardWidth);
                  card.render(context, mouseX, mouseY, deltaTicks);
                  x += cardWidth + gap;
               }
            }
         }

         public List<? extends Selectable> selectableChildren() {
            return this.cards;
         }

         public List<? extends Element> children() {
            return this.cards;
         }

         public void forEachChild(Consumer<ClickableWidget> consumer) {
            this.cards.forEach(consumer);
         }
      }
   }

   private record CandidateGroup(String animationId, List<NonPlayerInteractionClient.Candidate> candidates) {
      private NonPlayerInteractionClient.Candidate first() {
         return this.candidates.getFirst();
      }

      private boolean selectable() {
         return this.candidates.stream().anyMatch(NonPlayerInteractionClient.Candidate::selectable);
      }

      private boolean hasDisplayName() {
         return !this.first().display_name().isBlank();
      }

      private String displayName() {
         return this.hasDisplayName() ? this.first().display_name() : NonPlayerInteractionClient.shortAnimationId(this.animationId);
      }

      private String blockRequirement() {
         return this.candidates
            .stream()
            .map(NonPlayerInteractionClient.Candidate::block_requirement)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse("block requirement");
      }
   }

   private static final class ConsentScreen extends Screen implements NonPlayerInteractionClient.RequestBoundScreen {
      private final UUID requestId;
      private final UUID requesterUuid;
      private final String requesterName;
      private final String animationId;
      private final String displayName;
      private final List<String> contentTags;
      private final boolean joinRequest;
      private boolean resolved;

      private ConsentScreen(
         UUID requestId, UUID requesterUuid, String requesterName, String animationId, String displayName, List<String> contentTags, boolean joinRequest
      ) {
         super(Text.translatable("gui.needsofnature.player_request.consent_title"));
         this.requestId = requestId;
         this.requesterUuid = requesterUuid;
         this.requesterName = requesterName;
         this.animationId = animationId;
         this.displayName = displayName != null && !displayName.isBlank() ? displayName.trim() : NonPlayerInteractionClient.shortAnimationId(animationId);
         this.contentTags = contentTags;
         this.joinRequest = joinRequest;
      }

      protected void init() {
         int top = Math.max(24, this.height / 2 - 88);
         int y = Math.min(this.height - 28, top + 112);
         this.addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.needsofnature.player_request.accept"), ignored -> this.respond(true))
               .dimensions(this.width / 2 - 104, y, 100, 20)
               .build()
         );
         this.addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.needsofnature.player_request.reject"), ignored -> this.respond(false))
               .dimensions(this.width / 2 + 4, y, 100, 20)
               .build()
         );
      }

      private void respond(boolean accepted) {
         if (!this.resolved) {
            this.resolved = true;
            if (ClientPlayNetworking.canSend(PlayerAnimationConsentC2SPayload.ID)) {
               ClientPlayNetworking.send(new PlayerAnimationConsentC2SPayload(this.requestId, accepted));
            }

            MinecraftClient.getInstance().setScreen(null);
         }
      }

      public void close() {
         this.respond(false);
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         int centerX = this.width / 2;
         int top = Math.max(24, this.height / 2 - 88);
         context.fill(centerX - 180, top - 10, centerX + 180, top + 138, -585623528);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, top, -1);
         NonPlayerInteractionClient.drawPlayerFace(context, this.requesterUuid, centerX - 96, top + 22, 32);
         context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.requesterName), centerX - 80, top + 58, -1);
         NonAnimationPreviewRenderer.draw(context, this.textRenderer, this.animationId, centerX + 24, top + 17, 64);
         context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable(
               this.joinRequest ? "gui.needsofnature.player_request.join_prompt" : "gui.needsofnature.player_request.start_prompt",
               new Object[]{this.displayName}
            ),
            centerX,
            top + 84,
            -3092272
         );
         if (!this.contentTags.isEmpty()) {
            context.drawCenteredTextWithShadow(
               this.textRenderer,
               Text.literal(String.join(" | ", this.contentTags.stream().map(NonTagLocalizer::localize).toList())),
               centerX,
               top + 97,
               -6645094
            );
         }

         super.render(context, mouseX, mouseY, deltaTicks);
      }

      @Override
      public UUID requestId() {
         return this.requestId;
      }

      @Override
      public void markResolved() {
         this.resolved = true;
      }
   }

   public record KnownPlayerEntry(String uuid, String name) {
   }

   private static final class PreviewCardWidget extends ClickableWidget {
      private final NonPlayerInteractionClient.CandidateGroup group;
      private final TextRenderer textRenderer;
      private final Consumer<NonPlayerInteractionClient.CandidateGroup> onChoose;

      private PreviewCardWidget(
         NonPlayerInteractionClient.CandidateGroup group, TextRenderer textRenderer, Consumer<NonPlayerInteractionClient.CandidateGroup> onChoose
      ) {
         super(0, 0, 100, 116, Text.literal(group.displayName()));
         this.group = group;
         this.textRenderer = textRenderer;
         this.onChoose = onChoose;
         this.active = group.selectable();
         if (!this.active || group.hasDisplayName()) {
            this.setTooltip(NonPlayerInteractionClient.animationCardTooltip(group));
         }
      }

      protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         int border = !this.active ? -12632257 : (this.isSelected() ? -1002406 : -11184811);
         int background = !this.active ? -300542442 : (this.isSelected() ? -298636510 : -585426149);
         context.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), border);
         context.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.getWidth() - 1, this.getY() + this.getHeight() - 1, background);
         int previewSize = Math.min(72, Math.max(42, this.getWidth() - 18));
         int previewX = this.getX() + (this.getWidth() - previewSize) / 2;
         int previewY = this.getY() + 6;
         NonAnimationPreviewRenderer.draw(context, this.textRenderer, this.group.animationId(), previewX, previewY, previewSize);
         if (!this.active) {
            context.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, -1728053248);
         }

         NonPlayerInteractionClient.Candidate first = this.group.first();
         String id = this.group.displayName();
         context.drawCenteredTextWithShadow(
            this.textRenderer,
            this.textRenderer.trimToWidth(id, Math.max(20, this.getWidth() - 8)),
            this.getX() + this.getWidth() / 2,
            previewY + previewSize + 5,
            this.active ? -1 : -8947849
         );
         if (!this.active) {
            context.drawCenteredTextWithShadow(
               this.textRenderer,
               this.textRenderer
                  .trimToWidth(
                     Text.translatable("gui.needsofnature.player_request.block_requirement_unavailable").getString(), Math.max(20, this.getWidth() - 8)
                  ),
               this.getX() + this.getWidth() / 2,
               previewY + previewSize + 16,
               -6710887
            );
            context.drawCenteredTextWithShadow(
               this.textRenderer,
               this.textRenderer
                  .trimToWidth(
                     Text.translatable("gui.needsofnature.player_request.block_requirement_summary", new Object[]{this.group.blockRequirement()}).getString(),
                     Math.max(20, this.getWidth() - 8)
                  ),
               this.getX() + this.getWidth() / 2,
               this.getY() + this.getHeight() - 11,
               -8947849
            );
         } else {
            String tags = first.content_tags().isEmpty()
               ? ""
               : String.join(" | ", first.content_tags().stream().map(NonTagLocalizer::localize).toList());
            if (!tags.isBlank()) {
               context.drawCenteredTextWithShadow(
                  this.textRenderer,
                  this.textRenderer.trimToWidth(tags, Math.max(20, this.getWidth() - 8)),
                  this.getX() + this.getWidth() / 2,
                  previewY + previewSize + 16,
                  -6645094
               );
            }

            if (this.group.candidates().size() > 1) {
               context.drawCenteredTextWithShadow(
                  this.textRenderer,
                  Text.translatable("gui.needsofnature.player_request.roles", new Object[]{this.group.candidates().size()}),
                  this.getX() + this.getWidth() / 2,
                  this.getY() + this.getHeight() - 11,
                  -1853325
               );
            }
         }
      }

      public void onClick(Click click, boolean doubled) {
         super.onClick(click, doubled);
         if (this.active && this.onChoose != null) {
            this.onChoose.accept(this.group);
         }
      }

      protected void appendClickableNarrations(NarrationMessageBuilder builder) {
         this.appendDefaultNarrations(builder);
      }
   }

   public record ProfileEntry(String uuid, String name, String mode) {
   }

   public record ProfileSnapshot(
      String default_mode,
      int allowed_mask,
      List<NonPlayerInteractionClient.ProfileEntry> overrides,
      List<NonPlayerInteractionClient.KnownPlayerEntry> known_players
   ) {
      public ProfileSnapshot {
         default_mode = default_mode == null ? "requests_and_attacks" : default_mode;
         overrides = overrides == null ? List.of() : List.copyOf(overrides);
         known_players = known_players == null ? List.of() : List.copyOf(known_players);
      }
   }

   private interface RequestBoundScreen {
      UUID requestId();

      void markResolved();
   }

   private static final class RoleSelectionScreen extends Screen implements NonPlayerInteractionClient.RequestBoundScreen {
      private final NonPlayerInteractionClient.AnimationSelectionScreen parent;
      private final UUID requestId;
      private final NonPlayerInteractionClient.CandidateGroup group;
      private boolean resolved;

      private RoleSelectionScreen(NonPlayerInteractionClient.AnimationSelectionScreen parent, UUID requestId, NonPlayerInteractionClient.CandidateGroup group) {
         super(Text.translatable("gui.needsofnature.player_request.role_title"));
         this.parent = parent;
         this.requestId = requestId;
         this.group = group;
      }

      protected void init() {
         int buttonWidth = Math.min(360, this.width - 40);
         int startY = Math.max(42, (this.height - this.group.candidates().size() * 24) / 2);

         for (int index = 0; index < this.group.candidates().size(); index++) {
            NonPlayerInteractionClient.Candidate candidate = this.group.candidates().get(index);
            Text label = Text.translatable(
               "gui.needsofnature.player_request.role_option",
               new Object[]{candidate.requester_role(), Text.translatable("gui.needsofnature.player_request.role_activity." + candidate.requester_activity())}
            );
            ButtonWidget button = ButtonWidget.builder(label, ignored -> this.parent.choose(candidate))
               .dimensions(this.width / 2 - buttonWidth / 2, startY + index * 24, buttonWidth, 20)
               .build();
            button.active = candidate.selectable();
            if (!candidate.selectable()) {
               button.setTooltip(NonPlayerInteractionClient.blockRequirementTooltip(candidate.block_requirement()));
            }

            this.addDrawableChild(button);
         }

         this.addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.back"), ignored -> this.close()).dimensions(this.width / 2 - 75, this.height - 28, 150, 20).build()
         );
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         super.render(context, mouseX, mouseY, deltaTicks);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, -1);
         context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.group.displayName()), this.width / 2, 30, -5592406);
      }

      public void close() {
         if (!this.resolved) {
            MinecraftClient.getInstance().setScreen(this.parent);
         }
      }

      @Override
      public UUID requestId() {
         return this.requestId;
      }

      @Override
      public void markResolved() {
         this.resolved = true;
         this.parent.markResolved();
      }
   }

   private static final class WaitingScreen extends Screen implements NonPlayerInteractionClient.RequestBoundScreen {
      private final UUID requestId;
      private boolean resolved;

      private WaitingScreen(UUID requestId) {
         super(Text.translatable("gui.needsofnature.player_request.waiting_title"));
         this.requestId = requestId;
      }

      protected void init() {
         this.addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.cancel"), ignored -> this.cancel())
               .dimensions(this.width / 2 - 75, this.height / 2 + 24, 150, 20)
               .build()
         );
      }

      private void cancel() {
         if (!this.resolved && ClientPlayNetworking.canSend(PlayerAnimationCancelC2SPayload.ID)) {
            ClientPlayNetworking.send(new PlayerAnimationCancelC2SPayload(this.requestId));
         }

         this.resolved = true;
         MinecraftClient.getInstance().setScreen(null);
      }

      public void close() {
         this.cancel();
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         super.render(context, mouseX, mouseY, deltaTicks);
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 18, -1);
         context.drawCenteredTextWithShadow(
            this.textRenderer, Text.translatable("gui.needsofnature.player_request.waiting_description"), this.width / 2, this.height / 2, -5592406
         );
      }

      @Override
      public UUID requestId() {
         return this.requestId;
      }

      @Override
      public void markResolved() {
         this.resolved = true;
      }
   }
}
