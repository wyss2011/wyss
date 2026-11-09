package com.nonid.client;

import com.nonid.NeedsOfNature;
import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundInstance.AttenuationType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class NonDebugSpinMusicClient {
   private static final Identifier SOUND_ID = NeedsOfNature.id("funkytown");
   private static final float VOLUME = 0.025F;
   private static final float PITCH = 1.0F;
   private static final Map<UUID, NonDebugSpinMusicClient.SpinMusicSound> ACTIVE = new HashMap<>();

   private NonDebugSpinMusicClient() {
   }

   public static void clientTick(MinecraftClient client) {
      if (client != null) {
         if (NonDebugSpinMode.isEnabled() && client.world != null) {
            pruneStoppedSounds(client);
            Map<UUID, UUID> anchors = findActiveInstanceAnchors(client.world);
            stopInactiveSounds(client, anchors.keySet());

            for (Entry<UUID, UUID> entry : anchors.entrySet()) {
               playForInstance(client, entry.getKey(), entry.getValue());
            }
         } else {
            stopAll(client);
         }
      }
   }

   public static void stopAll(MinecraftClient client) {
      if (!ACTIVE.isEmpty()) {
         for (NonDebugSpinMusicClient.SpinMusicSound sound : ACTIVE.values()) {
            sound.finish();
            if (client != null) {
               client.getSoundManager().stop(sound);
            }
         }

         ACTIVE.clear();
      }
   }

   private static Map<UUID, UUID> findActiveInstanceAnchors(ClientWorld world) {
      if (world == null) {
         return Map.of();
      }

      Map<UUID, UUID> anchors = new HashMap<>();

      for (Entity entity : world.getEntities()) {
         if (entity instanceof LivingEntity living) {
            UUID actorUuid = living.getUuid();
            UUID instanceId = ClientAnimationRuntime.findLatestActiveInstanceContaining(actorUuid);
            if (instanceId != null && !anchors.containsKey(instanceId)) {
               UUID anchorUuid = ClientAnimationRuntime.findAnchorUuidForActor(actorUuid);
               anchors.put(instanceId, anchorUuid != null ? anchorUuid : actorUuid);
            }
         }
      }

      return anchors.isEmpty() ? Map.of() : Map.copyOf(anchors);
   }

   private static void stopInactiveSounds(MinecraftClient client, Set<UUID> activeInstances) {
      Iterator<Entry<UUID, NonDebugSpinMusicClient.SpinMusicSound>> iterator = ACTIVE.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, NonDebugSpinMusicClient.SpinMusicSound> entry = iterator.next();
         if (!activeInstances.contains(entry.getKey())) {
            NonDebugSpinMusicClient.SpinMusicSound sound = entry.getValue();
            if (sound != null) {
               sound.finish();
               client.getSoundManager().stop(sound);
            }

            iterator.remove();
         }
      }
   }

   private static void pruneStoppedSounds(MinecraftClient client) {
      Iterator<Entry<UUID, NonDebugSpinMusicClient.SpinMusicSound>> iterator = ACTIVE.entrySet().iterator();

      while (iterator.hasNext()) {
         NonDebugSpinMusicClient.SpinMusicSound sound = iterator.next().getValue();
         if (sound == null || sound.isDone() || client != null && !client.getSoundManager().isPlaying(sound)) {
            iterator.remove();
         }
      }
   }

   private static void playForInstance(MinecraftClient client, UUID instanceId, UUID anchorUuid) {
      if (client != null && client.world != null && instanceId != null && anchorUuid != null) {
         NonDebugSpinMusicClient.SpinMusicSound existing = ACTIVE.get(instanceId);
         if (existing == null || existing.isDone()) {
            Entity anchor = client.world.getEntity(anchorUuid);
            if (anchor != null) {
               NonDebugSpinMusicClient.SpinMusicSound sound = new NonDebugSpinMusicClient.SpinMusicSound(
                  instanceId, anchorUuid, anchor instanceof PlayerEntity ? SoundCategory.PLAYERS : SoundCategory.NEUTRAL, 0.025F, 1.0F
               );
               ACTIVE.put(instanceId, sound);
               client.getSoundManager().play(sound);
            }
         }
      }
   }

   private static final class SpinMusicSound extends MovingSoundInstance {
      private final UUID instanceId;
      private final UUID anchorUuid;

      private SpinMusicSound(UUID instanceId, UUID anchorUuid, SoundCategory category, float volume, float pitch) {
         super((SoundEvent)Registries.SOUND_EVENT.get(NonDebugSpinMusicClient.SOUND_ID), category, SoundInstance.createRandom());
         this.instanceId = instanceId;
         this.anchorUuid = anchorUuid;
         this.volume = volume;
         this.pitch = pitch;
         this.repeat = true;
         this.repeatDelay = 0;
         this.relative = false;
         this.attenuationType = AttenuationType.LINEAR;
         this.updatePosition();
      }

      public void tick() {
         if (!NonDebugSpinMode.isEnabled() || ClientAnimationRuntime.findCurrentStage(this.instanceId) == null || !this.updatePosition()) {
            this.setDone();
         }
      }

      private boolean updatePosition() {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.world != null) {
            Entity anchor = client.world.getEntity(this.anchorUuid);
            if (anchor == null) {
               return false;
            }

            this.x = anchor.getX();
            this.y = anchor.getY();
            this.z = anchor.getZ();
            return true;
         } else {
            return false;
         }
      }

      private void finish() {
         this.setDone();
      }
   }
}
