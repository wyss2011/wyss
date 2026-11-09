package com.nonid.entity;

import com.mojang.serialization.Codec;
import com.nonid.NeedsOfNature;
import com.nonid.NonConfig;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.DefaultAttributeContainer.Builder;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class PregnancyEggEntity extends PathAwareEntity implements GeoEntity {
   private static final TrackedData<String> SOURCE_ENTITY_TYPE_ID = DataTracker.registerData(PregnancyEggEntity.class, TrackedDataHandlerRegistry.STRING);
   private static final TrackedData<String> TARGET_ENTITY_TYPE_ID = DataTracker.registerData(PregnancyEggEntity.class, TrackedDataHandlerRegistry.STRING);
   private static final TrackedData<Long> HATCH_START_GLOBAL_TICK = DataTracker.registerData(PregnancyEggEntity.class, TrackedDataHandlerRegistry.LONG);
   private static final TrackedData<Long> HATCH_AT_GLOBAL_TICK = DataTracker.registerData(PregnancyEggEntity.class, TrackedDataHandlerRegistry.LONG);
   private static final TrackedData<Float> EGG_START_SIZE = DataTracker.registerData(PregnancyEggEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Float> EGG_END_SIZE = DataTracker.registerData(PregnancyEggEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<String> EGG_TEXTURE_ID = DataTracker.registerData(PregnancyEggEntity.class, TrackedDataHandlerRegistry.STRING);
   private static final TrackedData<Float> EGG_HEALTH = DataTracker.registerData(PregnancyEggEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final String NBT_SOURCE_ENTITY_TYPE = "NeedsOfNatureEggSourceType";
   private static final String NBT_TARGET_ENTITY_TYPE = "NeedsOfNatureEggTargetType";
   private static final String NBT_HATCH_GLOBAL_TICK = "NeedsOfNatureEggHatchGlobalTick";
   private static final String NBT_HATCH_START_GLOBAL_TICK = "NeedsOfNatureEggHatchStartGlobalTick";
   private static final String NBT_OWNER_UUID = "NeedsOfNatureEggOwnerUuid";
   private static final String NBT_VARIANT_DATA = "NeedsOfNatureEggVariantData";
   private static final String NBT_EGG_START_SIZE = "NeedsOfNatureEggStartSize";
   private static final String NBT_EGG_END_SIZE = "NeedsOfNatureEggEndSize";
   private static final String NBT_EGG_TEXTURE_ID = "NeedsOfNatureEggTexture";
   private static final String NBT_EGG_HEALTH = "NeedsOfNatureEggHealth";
   private static final float BASE_DIMENSION = 0.3F;
   private AnimatableInstanceCache animatableCache;
   private float lastDimensionsScale = -1.0F;
   @Nullable
   private UUID ownerUuid;
   @Nullable
   private NeedsOfNature.PregnancyVariantData variantData;

   public PregnancyEggEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
      super(entityType, world);
   }

   public static Builder createAttributes() {
      return MobEntity.createMobAttributes().add(EntityAttributes.MAX_HEALTH, 2.0).add(EntityAttributes.MOVEMENT_SPEED, 0.0);
   }

   protected void initGoals() {
   }

   protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
      super.initDataTracker(builder);
      builder.add(SOURCE_ENTITY_TYPE_ID, "");
      builder.add(TARGET_ENTITY_TYPE_ID, "");
      builder.add(HATCH_START_GLOBAL_TICK, 0L);
      builder.add(HATCH_AT_GLOBAL_TICK, 0L);
      builder.add(EGG_START_SIZE, 0.5F);
      builder.add(EGG_END_SIZE, 1.0F);
      builder.add(EGG_TEXTURE_ID, "");
      builder.add(EGG_HEALTH, 2.0F);
   }

   public void configure(
      @Nullable UUID ownerPlayerUuid,
      @Nullable Identifier sourceEntityTypeId,
      @Nullable Identifier targetEntityTypeId,
      long hatchAtTick,
      @Nullable NeedsOfNature.PregnancyVariantData variantData,
      @Nullable NonConfig.EggProfile eggProfile
   ) {
      this.ownerUuid = ownerPlayerUuid;
      this.variantData = NeedsOfNature.normalizePregnancyVariantData(variantData);
      this.setSourceEntityTypeId(sourceEntityTypeId);
      this.setTargetEntityTypeId(targetEntityTypeId);
      this.applyEggProfile(eggProfile);
      long startTick = resolveCurrentTick(this.getEntityWorld());
      long endTick = Math.max(0L, hatchAtTick);
      if (endTick <= startTick) {
         endTick = startTick + 1L;
      }

      this.setHatchStartGlobalTick(startTick);
      this.setHatchAtGlobalTick(endTick);
      this.refreshEggDimensionsIfNeeded(true);
   }

   @Nullable
   public Identifier getSourceEntityTypeId() {
      String raw = (String)this.dataTracker.get(SOURCE_ENTITY_TYPE_ID);
      return raw != null && !raw.isBlank() ? Identifier.tryParse(raw) : null;
   }

   public void setSourceEntityTypeId(@Nullable Identifier entityTypeId) {
      this.dataTracker.set(SOURCE_ENTITY_TYPE_ID, entityTypeId == null ? "" : entityTypeId.toString());
   }

   @Nullable
   public Identifier getTargetEntityTypeId() {
      String raw = (String)this.dataTracker.get(TARGET_ENTITY_TYPE_ID);
      return raw != null && !raw.isBlank() ? Identifier.tryParse(raw) : null;
   }

   public void setTargetEntityTypeId(@Nullable Identifier entityTypeId) {
      this.dataTracker.set(TARGET_ENTITY_TYPE_ID, entityTypeId == null ? "" : entityTypeId.toString());
   }

   public long getHatchStartGlobalTick() {
      return Math.max(0L, (Long)this.dataTracker.get(HATCH_START_GLOBAL_TICK));
   }

   public void setHatchStartGlobalTick(long tick) {
      this.dataTracker.set(HATCH_START_GLOBAL_TICK, Math.max(0L, tick));
   }

   public long getHatchAtGlobalTick() {
      return Math.max(0L, (Long)this.dataTracker.get(HATCH_AT_GLOBAL_TICK));
   }

   public void setHatchAtGlobalTick(long tick) {
      this.dataTracker.set(HATCH_AT_GLOBAL_TICK, Math.max(0L, tick));
   }

   public float getEggStartSize() {
      return clampEggSize((Float)this.dataTracker.get(EGG_START_SIZE));
   }

   public void setEggStartSize(float size) {
      this.dataTracker.set(EGG_START_SIZE, clampEggSize(size));
      this.refreshEggDimensionsIfNeeded(true);
   }

   public float getEggEndSize() {
      return clampEggSize((Float)this.dataTracker.get(EGG_END_SIZE));
   }

   public void setEggEndSize(float size) {
      this.dataTracker.set(EGG_END_SIZE, clampEggSize(size));
      this.refreshEggDimensionsIfNeeded(true);
   }

   @Nullable
   public Identifier getEggTextureId() {
      String raw = (String)this.dataTracker.get(EGG_TEXTURE_ID);
      if (raw != null && !raw.isBlank()) {
         Identifier parsed = Identifier.tryParse(raw);
         return parsed != null && !parsed.getPath().isBlank() ? parsed : null;
      } else {
         return null;
      }
   }

   public void setEggTextureId(@Nullable Identifier textureId) {
      this.dataTracker.set(EGG_TEXTURE_ID, textureId != null && !textureId.getPath().isBlank() ? textureId.toString() : "");
   }

   public float getEggHealth() {
      return clampEggHealth((Float)this.dataTracker.get(EGG_HEALTH));
   }

   public void setEggHealth(float health) {
      this.setEggHealth(health, false);
   }

   private void setEggHealth(float health, boolean healToMax) {
      float clamped = clampEggHealth(health);
      this.dataTracker.set(EGG_HEALTH, clamped);
      this.applyEggHealth(clamped, healToMax);
   }

   public void tickMovement() {
      super.tickMovement();
      this.freezeStationaryState();
      this.refreshEggDimensionsIfNeeded(false);
   }

   public void tick() {
      super.tick();
      this.freezeStationaryState();
      this.refreshEggDimensionsIfNeeded(false);
      this.pushAwayFromNearbyEggs();
      if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
         Identifier targetTypeId = this.getTargetEntityTypeId();
         if (targetTypeId == null) {
            this.discard();
         } else if (resolveGlobalTick(serverWorld) >= this.getHatchAtGlobalTick()) {
            this.hatch(serverWorld, targetTypeId);
         }
      }
   }

   public boolean isPushable() {
      return true;
   }

   public void pushAwayFrom(Entity entity) {
      if (entity instanceof PregnancyEggEntity) {
         super.pushAwayFrom(entity);
      }
   }

   public void addVelocity(double deltaX, double deltaY, double deltaZ) {
      super.addVelocity(deltaX, deltaY, deltaZ);
   }

   public void takeKnockback(double strength, double x, double z) {
   }

   public boolean isPushedByFluids() {
      return false;
   }

   public boolean canImmediatelyDespawn(double distanceSquared) {
      return false;
   }

   public void writeData(WriteView view) {
      super.writeData(view);
      Identifier source = this.getSourceEntityTypeId();
      if (source != null) {
         view.putString("NeedsOfNatureEggSourceType", source.toString());
      }

      Identifier target = this.getTargetEntityTypeId();
      if (target != null) {
         view.putString("NeedsOfNatureEggTargetType", target.toString());
      }

      view.put("NeedsOfNatureEggHatchStartGlobalTick", Codec.LONG, this.getHatchStartGlobalTick());
      view.put("NeedsOfNatureEggHatchGlobalTick", Codec.LONG, this.getHatchAtGlobalTick());
      if (this.ownerUuid != null) {
         view.putString("NeedsOfNatureEggOwnerUuid", this.ownerUuid.toString());
      }

      String variantJson = NeedsOfNature.encodePregnancyVariantData(this.variantData);
      if (variantJson != null) {
         view.putString("NeedsOfNatureEggVariantData", variantJson);
      }

      view.putFloat("NeedsOfNatureEggStartSize", this.getEggStartSize());
      view.putFloat("NeedsOfNatureEggEndSize", this.getEggEndSize());
      Identifier textureId = this.getEggTextureId();
      if (textureId != null) {
         view.putString("NeedsOfNatureEggTexture", textureId.toString());
      }

      view.putFloat("NeedsOfNatureEggHealth", this.getEggHealth());
   }

   public void readData(ReadView view) {
      super.readData(view);
      String sourceRaw = view.getOptionalString("NeedsOfNatureEggSourceType").orElse("");
      this.setSourceEntityTypeId(Identifier.tryParse(sourceRaw));
      String targetRaw = view.getOptionalString("NeedsOfNatureEggTargetType").orElse("");
      this.setTargetEntityTypeId(Identifier.tryParse(targetRaw));
      long start = Math.max(0L, view.read("NeedsOfNatureEggHatchStartGlobalTick", Codec.LONG).orElse(0L));
      long end = Math.max(0L, view.read("NeedsOfNatureEggHatchGlobalTick", Codec.LONG).orElse(0L));
      if (end > 0L && start >= end) {
         start = Math.max(0L, end - 1L);
      }

      this.setHatchStartGlobalTick(start);
      this.setHatchAtGlobalTick(end);
      String ownerRaw = view.getOptionalString("NeedsOfNatureEggOwnerUuid").orElse("");
      this.ownerUuid = parseUuid(ownerRaw);
      this.variantData = NeedsOfNature.decodePregnancyVariantData(view.getOptionalString("NeedsOfNatureEggVariantData").orElse(""));
      this.setEggStartSize(view.read("NeedsOfNatureEggStartSize", Codec.FLOAT).orElse(0.5F));
      this.setEggEndSize(view.read("NeedsOfNatureEggEndSize", Codec.FLOAT).orElse(1.0F));
      this.setEggTextureId(Identifier.tryParse(view.getOptionalString("NeedsOfNatureEggTexture").orElse("")));
      this.setEggHealth(view.read("NeedsOfNatureEggHealth", Codec.FLOAT).orElse(2.0F));
      this.refreshEggDimensionsIfNeeded(true);
   }

   @Nullable
   private static UUID parseUuid(@Nullable String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            return UUID.fromString(raw);
         } catch (IllegalArgumentException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   private void hatch(ServerWorld world, Identifier targetTypeId) {
      if (!Registries.ENTITY_TYPE.containsId(targetTypeId)) {
         this.discard();
      } else {
         EntityType<?> entityType = (EntityType<?>)Registries.ENTITY_TYPE.get(targetTypeId);
         Entity entity = entityType.create(world, SpawnReason.BREEDING);
         if (entity == null) {
            this.discard();
         } else {
            entity.refreshPositionAndAngles(this.getX(), this.getY(), this.getZ(), this.getYaw(), this.getPitch());
            if (entity instanceof MobEntity mob) {
               mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), SpawnReason.BREEDING, null);
               NeedsOfNature.applyPregnancyVariantDataForEgg(world, mob, this.variantData);
               NeedsOfNature.applyPregnancyMobFlagsForEgg(mob, this.resolveOwnerPlayer(world));
            }

            if (world.spawnEntity(entity)) {
               this.playHatchEffects(world);
            }

            this.discard();
         }
      }
   }

   public float getGrowthScale(float partialTick) {
      long startTick = this.getHatchStartGlobalTick();
      long endTick = this.getHatchAtGlobalTick();
      float startSize = this.getEggStartSize();
      float endSize = this.getEggEndSize();
      if (endTick <= startTick) {
         return endSize;
      }

      double now = (float)resolveCurrentTick(this.getEntityWorld()) + Math.max(0.0F, partialTick);
      double progress = (now - startTick) / (endTick - startTick);
      progress = MathHelper.clamp(progress, 0.0, 1.0);
      return (float)(startSize + (endSize - startSize) * progress);
   }

   protected EntityDimensions getBaseDimensions(EntityPose pose) {
      float scale = this.getGrowthScale(0.0F);
      return EntityDimensions.fixed(0.3F * scale, 0.3F * scale);
   }

   public void onTrackedDataSet(TrackedData<?> data) {
      super.onTrackedDataSet(data);
      if (EGG_START_SIZE.equals(data) || EGG_END_SIZE.equals(data)) {
         this.refreshEggDimensionsIfNeeded(true);
      }

      if (EGG_HEALTH.equals(data)) {
         this.applyEggHealth(this.getEggHealth(), false);
      }
   }

   private void applyEggProfile(@Nullable NonConfig.EggProfile eggProfile) {
      NonConfig.EggProfile safe = eggProfile == null ? NonConfig.EggProfile.defaults() : eggProfile.resolvedOver(NonConfig.EggProfile.defaults());
      Float startSize = safe.startSize();
      Float endSize = safe.endSize();
      Float health = safe.health();
      this.setEggStartSize(startSize == null ? 0.5F : startSize);
      this.setEggEndSize(endSize == null ? 1.0F : endSize);
      String texture = safe.texture();
      this.setEggTextureId(texture == null ? null : Identifier.tryParse(texture));
      this.setEggHealth(health == null ? 2.0F : health, true);
   }

   private void applyEggHealth(float health, boolean healToMax) {
      EntityAttributeInstance attribute = this.getAttributeInstance(EntityAttributes.MAX_HEALTH);
      if (attribute != null && Math.abs(attribute.getBaseValue() - health) > 0.001) {
         attribute.setBaseValue(health);
      }

      if (healToMax || this.getHealth() > health || this.getHealth() <= 0.0F) {
         this.setHealth(health);
      }
   }

   private void refreshEggDimensionsIfNeeded(boolean force) {
      float scale = this.getGrowthScale(0.0F);
      if (force || !(Math.abs(scale - this.lastDimensionsScale) < 0.01F)) {
         this.lastDimensionsScale = scale;
         this.calculateDimensions();
      }
   }

   private static float clampEggSize(float value) {
      return !Float.isFinite(value) ? 1.0F : MathHelper.clamp(value, 0.05F, 4.0F);
   }

   private static float clampEggHealth(float value) {
      return !Float.isFinite(value) ? 2.0F : MathHelper.clamp(value, 0.5F, 100.0F);
   }

   private void playHatchEffects(ServerWorld world) {
      if (world != null) {
         world.playSound(
            null,
            this.getX(),
            this.getY(),
            this.getZ(),
            SoundEvents.ENTITY_TURTLE_EGG_HATCH,
            SoundCategory.NEUTRAL,
            0.9F,
            0.95F + world.getRandom().nextFloat() * 0.1F
         );
         world.spawnParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.25, this.getZ(), 12, 0.25, 0.2, 0.25, 0.02);
         world.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.2, this.getZ(), 8, 0.18, 0.12, 0.18, 0.01);
      }
   }

   @Nullable
   private ServerPlayerEntity resolveOwnerPlayer(ServerWorld world) {
      return this.ownerUuid != null && world.getServer() != null ? world.getServer().getPlayerManager().getPlayer(this.ownerUuid) : null;
   }

   private static long resolveGlobalTick(ServerWorld world) {
      if (world != null && world.getServer() != null) {
         ServerWorld overworld = world.getServer().getOverworld();
         return overworld == null ? world.getTime() : overworld.getTime();
      } else {
         return 0L;
      }
   }

   private static long resolveCurrentTick(@Nullable World world) {
      if (world == null) {
         return 0L;
      } else {
         return world instanceof ServerWorld serverWorld ? resolveGlobalTick(serverWorld) : world.getTime();
      }
   }

   private void freezeStationaryState() {
      this.setSilent(true);
      this.setNoGravity(true);
      this.setAiDisabled(true);
      this.getNavigation().stop();
      Vec3d velocity = this.getVelocity();
      double x = MathHelper.clamp(velocity.x * 0.65, -0.08, 0.08);
      double z = MathHelper.clamp(velocity.z * 0.65, -0.08, 0.08);
      if (Math.abs(x) < 0.001) {
         x = 0.0;
      }

      if (Math.abs(z) < 0.001) {
         z = 0.0;
      }

      this.setVelocity(x, 0.0, z);
      this.fallDistance = 0.0;
   }

   private void pushAwayFromNearbyEggs() {
      if (!this.isRemoved()) {
         Box searchBox = this.getBoundingBox().expand(0.05, 0.0, 0.05);
         List<PregnancyEggEntity> nearbyEggs = this.getEntityWorld()
            .getEntitiesByClass(PregnancyEggEntity.class, searchBox, egg -> egg != this && !egg.isRemoved());
         if (!nearbyEggs.isEmpty()) {
            double pushX = 0.0;
            double pushZ = 0.0;
            double ownRadius = Math.max(0.1, this.getBoundingBox().getLengthX() * 0.5);

            for (PregnancyEggEntity other : nearbyEggs) {
               double otherRadius = Math.max(0.1, other.getBoundingBox().getLengthX() * 0.5);
               double minDistance = ownRadius + otherRadius + 0.04;
               double dx = this.getX() - other.getX();
               double dz = this.getZ() - other.getZ();
               double distanceSq = dx * dx + dz * dz;
               if (!(distanceSq >= minDistance * minDistance)) {
                  if (distanceSq < 1.0E-6) {
                     double angle = this.deterministicSeparationAngle();
                     dx = Math.cos(angle);
                     dz = Math.sin(angle);
                     distanceSq = 1.0;
                  }

                  double distance = Math.sqrt(distanceSq);
                  if (!(distance <= 0.0)) {
                     double strength = Math.min(0.08, (minDistance - distance) * 0.15);
                     pushX += dx / distance * strength;
                     pushZ += dz / distance * strength;
                  }
               }
            }

            if (!(Math.abs(pushX) < 1.0E-5) || !(Math.abs(pushZ) < 1.0E-5)) {
               pushX = MathHelper.clamp(pushX, -0.08, 0.08);
               pushZ = MathHelper.clamp(pushZ, -0.08, 0.08);
               this.updatePosition(this.getX() + pushX, this.getY(), this.getZ() + pushZ);
               this.setVelocity(pushX * 0.25, 0.0, pushZ * 0.25);
               this.applySettledPushRotation(pushX, pushZ);
            }
         }
      }
   }

   private double deterministicSeparationAngle() {
      long bits = this.getUuid().getMostSignificantBits() ^ this.getUuid().getLeastSignificantBits();
      double unit = (bits & 65535L) / 65536.0;
      return unit * Math.PI * 2.0;
   }

   private void applySettledPushRotation(double pushX, double pushZ) {
      if (!(Math.abs(pushX) < 1.0E-5) || !(Math.abs(pushZ) < 1.0E-5)) {
         float yaw = (float)(Math.atan2(pushZ, pushX) * (180.0 / Math.PI)) - 90.0F;
         this.setYaw(yaw);
         this.setHeadYaw(yaw);
         this.setBodyYaw(yaw);
         this.lastYaw = yaw;
      }
   }

   public void registerControllers(@NotNull ControllerRegistrar controllers) {
   }

   @NotNull
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      if (this.animatableCache == null) {
         this.animatableCache = GeckoLibUtil.createInstanceCache(this);
      }

      return this.animatableCache;
   }
}
