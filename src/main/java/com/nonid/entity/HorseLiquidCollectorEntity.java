package com.nonid.entity;

import com.nonid.EnergyHolder;
import com.nonid.NeedsOfNature;
import com.nonid.particle.NonParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.DefaultAttributeContainer.Builder;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class HorseLiquidCollectorEntity extends PathAwareEntity implements GeoEntity {
   private static final TrackedData<Boolean> COLLECTOR_FULL = DataTracker.registerData(HorseLiquidCollectorEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final String LIQUID_TYPE_NBT = "NeedsOfNatureCollectorLiquidType";
   private static final int FIXED_ENERGY_VALUE = 70;
   private static final int FULL_DRIP_INTERVAL_TICKS = 60;
   private static final int NATURAL_REGEN_INTERVAL_TICKS = 80;
   private static final float NATURAL_REGEN_AMOUNT = 1.0F;
   private static final Vec3d FULL_DRIP_LOCAL_OFFSET = new Vec3d(0.0, 0.6, -0.55);
   private AnimatableInstanceCache animatableCache;
   @Nullable
   private Identifier storedLiquidEntityTypeId;
   private boolean storedMixedLiquid;
   private boolean orientationLocked;
   private float lockedYaw;
   private float lockedPitch;
   private long nextFullDripTick = Long.MIN_VALUE;

   public HorseLiquidCollectorEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
      super(entityType, world);
   }

   public static Builder createAttributes() {
      return MobEntity.createMobAttributes().add(EntityAttributes.MAX_HEALTH, 2.0).add(EntityAttributes.MOVEMENT_SPEED, 0.0);
   }

   protected void initGoals() {
   }

   protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
      super.initDataTracker(builder);
      builder.add(COLLECTOR_FULL, false);
   }

   public void tickMovement() {
      super.tickMovement();
      this.freezeStationaryState();
   }

   public void tick() {
      super.tick();
      this.enforceFixedEnergy();
      if (!this.getEntityWorld().isClient() && this.deathTime > 0) {
         this.discard();
      } else {
         this.tickNaturalRegen();
         this.freezeStationaryState();
         this.tickFullDripParticles();
      }
   }

   public boolean damage(ServerWorld world, DamageSource source, float amount) {
      if (!this.isAlive() || this.isRemoved()) {
         return false;
      }

      if (!this.isInvulnerableTo(world, source) && !(amount <= 0.0F)) {
         if (!(this.getHealth() - amount <= 0.0F)) {
            return super.damage(world, source, amount);
         }

         if (!(source.getAttacker() instanceof PlayerEntity player && player.isCreative())) {
            this.dropStack(world, new ItemStack(NeedsOfNature.HORSE_LIQUID_COLLECTOR));
         }

         this.discard();
         return true;
      } else {
         return false;
      }
   }

   public boolean isPushable() {
      return false;
   }

   public void pushAwayFrom(Entity entity) {
   }

   public void addVelocity(double deltaX, double deltaY, double deltaZ) {
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
      if (this.isCollectorFull()) {
         if (this.storedMixedLiquid) {
            view.putString("NeedsOfNatureCollectorLiquidType", "mixed");
         } else {
            if (this.storedLiquidEntityTypeId != null) {
               view.putString("NeedsOfNatureCollectorLiquidType", this.storedLiquidEntityTypeId.toString());
            }
         }
      }
   }

   public void readData(ReadView view) {
      super.readData(view);
      String raw = view.getOptionalString("NeedsOfNatureCollectorLiquidType").orElse("");
      if (raw.isBlank()) {
         this.setStoredEmpty();
      } else if ("mixed".equalsIgnoreCase(raw)) {
         this.setStoredMixedLiquid();
      } else {
         Identifier parsed = Identifier.tryParse(raw);
         if (parsed == null) {
            this.setStoredEmpty();
         } else {
            this.setStoredEntityLiquid(parsed);
            this.ensureOrientationLock();
         }
      }
   }

   public boolean isCollectorFull() {
      return (Boolean)this.dataTracker.get(COLLECTOR_FULL);
   }

   @Nullable
   public Identifier getBottleLiquidEntityTypeId() {
      return this.isCollectorFull() && !this.storedMixedLiquid ? this.storedLiquidEntityTypeId : null;
   }

   public void clearStoredLiquid() {
      this.setStoredEmpty();
   }

   public void setLockedOrientation(float yaw, float pitch) {
      this.orientationLocked = true;
      this.lockedYaw = yaw;
      this.lockedPitch = pitch;
      this.applyLockedOrientation();
   }

   public boolean absorbMixedLiquid() {
      if (this.isCollectorFull() && this.storedMixedLiquid) {
         return false;
      }

      this.setStoredMixedLiquid();
      return true;
   }

   public boolean absorbEntityLiquid(@Nullable Identifier donorTypeId) {
      if (donorTypeId == null) {
         return this.absorbMixedLiquid();
      }

      if (!this.isCollectorFull()) {
         this.setStoredEntityLiquid(donorTypeId);
         return true;
      }

      if (this.storedMixedLiquid) {
         return false;
      }

      if (donorTypeId.equals(this.storedLiquidEntityTypeId)) {
         return false;
      }

      this.setStoredMixedLiquid();
      return true;
   }

   private void setStoredEntityLiquid(Identifier entityTypeId) {
      this.storedLiquidEntityTypeId = entityTypeId;
      this.storedMixedLiquid = false;
      this.dataTracker.set(COLLECTOR_FULL, true);
      this.resetFullDripTimer();
   }

   private void setStoredMixedLiquid() {
      this.storedLiquidEntityTypeId = null;
      this.storedMixedLiquid = true;
      this.dataTracker.set(COLLECTOR_FULL, true);
      this.resetFullDripTimer();
   }

   private void setStoredEmpty() {
      this.storedLiquidEntityTypeId = null;
      this.storedMixedLiquid = false;
      this.dataTracker.set(COLLECTOR_FULL, false);
      this.nextFullDripTick = Long.MIN_VALUE;
   }

   private void freezeStationaryState() {
      this.setSilent(true);
      this.setNoGravity(true);
      this.setAiDisabled(true);
      this.getNavigation().stop();
      this.setVelocity(Vec3d.ZERO);
      this.fallDistance = 0.0;
      this.applyLockedOrientation();
   }

   private void ensureOrientationLock() {
      if (!this.orientationLocked) {
         this.orientationLocked = true;
         this.lockedYaw = this.getYaw();
         this.lockedPitch = this.getPitch();
      }
   }

   private void applyLockedOrientation() {
      this.ensureOrientationLock();
      this.setYaw(this.lockedYaw);
      this.setPitch(this.lockedPitch);
      this.setHeadYaw(this.lockedYaw);
      this.setBodyYaw(this.lockedYaw);
   }

   private void tickFullDripParticles() {
      if (this.getEntityWorld() instanceof ServerWorld world) {
         if (this.isCollectorFull() && this.isAlive() && !this.isRemoved()) {
            long now = world.getTime();
            if (this.nextFullDripTick == Long.MIN_VALUE) {
               this.nextFullDripTick = now + 60L;
            }

            if (now >= this.nextFullDripTick) {
               Vec3d spawnPos = this.resolveFullDripSpawnPos();
               world.spawnParticles(
                  NonParticles.liquidFalling(NeedsOfNature.resolveBottleLiquidTintRgb(this.getBottleLiquidEntityTypeId())),
                  spawnPos.x,
                  spawnPos.y,
                  spawnPos.z,
                  1,
                  0.0,
                  0.0,
                  0.0,
                  0.0
               );
               this.nextFullDripTick = now + 60L;
            }
         }
      }
   }

   private void resetFullDripTimer() {
      if (this.getEntityWorld() instanceof ServerWorld world) {
         this.nextFullDripTick = world.getTime() + 60L;
      } else {
         this.nextFullDripTick = Long.MIN_VALUE;
      }
   }

   private void tickNaturalRegen() {
      if (this.getEntityWorld() instanceof ServerWorld world) {
         if (this.isAlive() && !this.isRemoved()) {
            if (this.deathTime <= 0 && this.hurtTime <= 0) {
               float maxHealth = this.getMaxHealth();
               if (!(this.getHealth() >= maxHealth)) {
                  if (world.getTime() % 80L == 0L) {
                     this.heal(1.0F);
                  }
               }
            }
         }
      }
   }

   private void enforceFixedEnergy() {
      if (this.getEntityWorld() instanceof ServerWorld) {
         if (this instanceof EnergyHolder holder) {
            if (holder.getEnergy() != 70) {
               holder.setEnergy(70);
            }
         }
      }
   }

   private Vec3d resolveFullDripSpawnPos() {
      double yawRad = Math.toRadians(this.getYaw());
      double sin = Math.sin(yawRad);
      double cos = Math.cos(yawRad);
      Vec3d forward = new Vec3d(-sin, 0.0, cos);
      Vec3d right = new Vec3d(forward.z, 0.0, -forward.x);
      return new Vec3d(this.getX(), this.getY(), this.getZ())
         .add(right.multiply(FULL_DRIP_LOCAL_OFFSET.x))
         .add(0.0, FULL_DRIP_LOCAL_OFFSET.y, 0.0)
         .add(forward.multiply(FULL_DRIP_LOCAL_OFFSET.z));
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
