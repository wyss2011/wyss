package com.nonid.client;

import com.nonid.NeedsOfNature;
import com.nonid.NonAccessoryShedSystem;
import com.nonid.NonTrinketsIntegration;
import com.nonid.internal.animation.client.runtime.ClientAnimationRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemStackEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.shape.VoxelShape;

public final class NonArmorShedClient {
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final int FLYING_TICKS = 24;
   private static final int GROUND_TICKS = 40;
   private static final int GROUND_FADE_TICKS = 30;
   private static final double GRAVITY = 0.035;
   private static final double AIR_DRAG = 0.96;
   private static final double GROUND_DRAG = 0.78;
   private static final float ITEM_SCALE = 1.0F;
   private static final long FRESH_START_MAX_AGE_TICKS = 5L;
   private static final long QUEUED_HANDOFF_SKIP_TICKS = 12L;
   private static final Identifier PLAYER_DEFEATED = Identifier.of("needsofnature", "player_defeated-belly");
   private static final Identifier PLAYER_BIRTH = Identifier.of("needsofnature", "player_birth");
   private static final List<NonArmorShedClient.ArmorProp> PROPS = new ArrayList<>();
   private static final Map<UUID, Long> LAST_SPAWNED_START_TICK = new HashMap<>();
   private static final Map<UUID, Long> LAST_ACTIVE_TICK = new HashMap<>();

   private NonArmorShedClient() {
   }

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(NonArmorShedClient::render);
   }

   public static void clientTick(MinecraftClient client) {
      if (!NeedsOfNature.getConfig().isArmorShedEffectEnabled()) {
         clear();
      } else {
         tickProps(client);
         if (client != null && client.world != null) {
            long now = client.world.getTime();

            for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
               if (player != null && !player.isRemoved()) {
                  UUID uuid = player.getUuid();
                  if (!ClientAnimationRuntime.isActorActive(uuid)) {
                     LAST_SPAWNED_START_TICK.remove(uuid);
                  } else {
                     Long previousActiveTick = LAST_ACTIVE_TICK.get(uuid);
                     Long startTick = ClientAnimationRuntime.findStartTickForActor(uuid);
                     if (startTick == null) {
                        LAST_ACTIVE_TICK.put(uuid, now);
                     } else {
                        Long lastSpawned = LAST_SPAWNED_START_TICK.get(uuid);
                        if (lastSpawned != null && lastSpawned.equals(startTick)) {
                           LAST_ACTIVE_TICK.put(uuid, now);
                        } else {
                           if (shouldSpawnForStart(uuid, now, startTick, previousActiveTick)) {
                              spawnForPlayer(player, startTick);
                           }

                           LAST_SPAWNED_START_TICK.put(uuid, startTick);
                           LAST_ACTIVE_TICK.put(uuid, now);
                        }
                     }
                  }
               }
            }
         } else {
            clear();
         }
      }
   }

   public static void clear() {
      PROPS.clear();
      LAST_SPAWNED_START_TICK.clear();
      LAST_ACTIVE_TICK.clear();
   }

   private static boolean shouldSpawnForStart(UUID playerUuid, long now, long startTick, Long previousActiveTick) {
      if (now - startTick > 5L) {
         return false;
      }

      if (previousActiveTick != null && now - previousActiveTick <= 12L) {
         return false;
      }

      Identifier activeAnimation = ClientAnimationRuntime.findLatestActiveAnimationIdContaining(playerUuid);
      return !isQueuedFollowUpAnimation(activeAnimation);
   }

   private static boolean isQueuedFollowUpAnimation(Identifier animationId) {
      return animationId == null ? false : PLAYER_DEFEATED.equals(animationId) || PLAYER_BIRTH.equals(animationId);
   }

   private static void spawnForPlayer(AbstractClientPlayerEntity player, long startTick) {
      Vec3d origin = new Vec3d(player.getX(), player.getY() + player.getHeight() * 0.72, player.getZ());
      long seedBase = player.getUuid().getMostSignificantBits() ^ player.getUuid().getLeastSignificantBits() ^ startTick;
      Random random = new Random(seedBase);
      float yawRad = player.getYaw() * (float) (Math.PI / 180.0);
      Vec3d forward = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
      Vec3d right = new Vec3d(forward.z, 0.0, -forward.x);

      for (int i = 0; i < ARMOR_SLOTS.length; i++) {
         EquipmentSlot slot = ARMOR_SLOTS[i];
         ItemStack stack = player.getEquippedStack(slot);
         if (stack != null && !stack.isEmpty()) {
            double side = (i - 1.5) * 0.18;
            Vec3d spawnPos = origin.add(right.multiply(side)).add(0.0, -0.08 * i, 0.0);
            addProp(stack, spawnPos, forward, right, random, seedBase, i, ARMOR_SLOTS.length);
         }
      }

      spawnShedAccessories(player, origin, forward, right, random, seedBase);
   }

   private static void spawnShedAccessories(AbstractClientPlayerEntity player, Vec3d origin, Vec3d forward, Vec3d right, Random random, long seedBase) {
      NonAccessoryShedSystem.ShedState shedState = NonAccessoryShedSystem.getShedState(player.getUuid());
      if (!shedState.isEmpty()) {
         List<ItemStack> stacks = NonTrinketsIntegration.getVisuallyShedAccessoryStacks(player);

         for (int index = 0; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            if (stack != null && !stack.isEmpty()) {
               double side = (index - 0.5) * 0.24;
               Vec3d spawnPos = origin.add(right.multiply(side)).add(0.0, -0.34, 0.0);
               addProp(stack, spawnPos, forward, right, random, seedBase, 20 + index, 2);
            }
         }
      }
   }

   private static void addProp(ItemStack stack, Vec3d spawnPos, Vec3d forward, Vec3d right, Random random, long seedBase, int index, int count) {
      ItemStack visualStack = stack.copy();
      visualStack.setCount(1);
      double angle = (Math.PI * 2) * index / Math.max(1, count) + (random.nextDouble() - 0.5) * 0.55;
      Vec3d outward = right.multiply(Math.cos(angle)).add(forward.multiply(Math.sin(angle))).normalize();
      double speed = 0.105 + random.nextDouble() * 0.035;
      Vec3d velocity = outward.multiply(speed).add(0.0, 0.15 + random.nextDouble() * 0.13, 0.0);
      PROPS.add(
         new NonArmorShedClient.ArmorProp(
            visualStack,
            spawnPos,
            velocity,
            random.nextFloat() * 360.0F,
            random.nextFloat() * 360.0F,
            7.0F + random.nextFloat() * 7.0F,
            10.0F + random.nextFloat() * 12.0F,
            (int)(seedBase + index * 31L)
         )
      );
   }

   private static void tickProps(MinecraftClient client) {
      if (!PROPS.isEmpty()) {
         if (client != null && client.world != null) {
            Iterator<NonArmorShedClient.ArmorProp> iterator = PROPS.iterator();

            while (iterator.hasNext()) {
               NonArmorShedClient.ArmorProp prop = iterator.next();
               prop.age++;
               prop.previousPosition = prop.position;
               if (prop.landed) {
                  prop.groundAge++;
                  prop.velocity = prop.velocity.multiply(0.78, 0.0, 0.78);
                  if (prop.groundAge > 40) {
                     iterator.remove();
                  }
               } else {
                  prop.velocity = prop.velocity.add(0.0, -0.035, 0.0).multiply(0.96, 0.98, 0.96);
                  Vec3d next = prop.position.add(prop.velocity);
                  NonArmorShedClient.GroundHit hit = findGround(client, next);
                  if (hit.hit() && prop.velocity.y <= 0.0 && prop.age > 3) {
                     prop.position = new Vec3d(next.x, hit.y(), next.z);
                     prop.velocity = Vec3d.ZERO;
                     prop.landed = true;
                  } else {
                     prop.position = next;
                     if (prop.age > 64) {
                        iterator.remove();
                     }
                  }
               }
            }
         } else {
            clear();
         }
      }
   }

   private static NonArmorShedClient.GroundHit findGround(MinecraftClient client, Vec3d pos) {
      if (client != null && client.world != null) {
         BlockPos blockPos = BlockPos.ofFloored(pos.x, pos.y - 0.04, pos.z);
         BlockState state = client.world.getBlockState(blockPos);
         VoxelShape shape = state.getCollisionShape(client.world, blockPos);
         if (shape.isEmpty()) {
            return NonArmorShedClient.GroundHit.MISS;
         }

         double top = blockPos.getY() + shape.getMax(Axis.Y);
         return pos.y < top + 0.12 ? new NonArmorShedClient.GroundHit(true, top + 0.03) : NonArmorShedClient.GroundHit.MISS;
      } else {
         return NonArmorShedClient.GroundHit.MISS;
      }
   }

   private static void render(WorldRenderContext context) {
      if (NeedsOfNature.getConfig().isArmorShedEffectEnabled()) {
         if (!PROPS.isEmpty()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
               Vec3d cameraPos = context.worldState().cameraRenderState.pos;
               if (cameraPos != null) {
                  float tickProgress = client.getRenderTickCounter().getTickProgress(false);
                  MatrixStack matrices = context.matrices();

                  for (NonArmorShedClient.ArmorProp prop : PROPS) {
                     Vec3d renderPos = prop.previousPosition.lerp(prop.position, tickProgress);
                     matrices.push();
                     matrices.translate(renderPos.x - cameraPos.x, renderPos.y - cameraPos.y, renderPos.z - cameraPos.z);
                     float smoothAge = prop.age + tickProgress;
                     float yaw = prop.landed ? prop.groundYaw : prop.baseYaw + smoothAge * prop.yawSpeed;
                     float pitch = prop.landed ? 90.0F : prop.basePitch + smoothAge * prop.pitchSpeed;
                     matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
                     matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
                     float fadeScale = prop.landed ? prop.groundFadeScale(tickProgress) : 1.0F;
                     matrices.scale(1.0F * fadeScale, 1.0F * fadeScale, 1.0F * fadeScale);
                     ItemStackEntityRenderState state = new ItemStackEntityRenderState();
                     ItemEntity fake = new ItemEntity(client.world, prop.position.x, prop.position.y, prop.position.z, prop.stack);
                     state.update(fake, prop.stack, client.getItemModelManager());
                     ItemEntityRenderer.renderStack(matrices, context.commandQueue(), 15728880, state, net.minecraft.util.math.random.Random.create(prop.seed));
                     matrices.pop();
                  }
               }
            }
         }
      }
   }

   private static final class ArmorProp {
      final ItemStack stack;
      Vec3d position;
      Vec3d previousPosition;
      Vec3d velocity;
      final float baseYaw;
      final float basePitch;
      final float yawSpeed;
      final float pitchSpeed;
      final float groundYaw;
      final int seed;
      int age;
      int groundAge;
      boolean landed;

      ArmorProp(ItemStack stack, Vec3d position, Vec3d velocity, float baseYaw, float basePitch, float yawSpeed, float pitchSpeed, int seed) {
         this.stack = stack;
         this.position = position;
         this.previousPosition = position;
         this.velocity = velocity;
         this.baseYaw = baseYaw;
         this.basePitch = basePitch;
         this.yawSpeed = yawSpeed;
         this.pitchSpeed = pitchSpeed;
         this.groundYaw = baseYaw;
         this.seed = seed;
      }

      float groundFadeScale(float tickProgress) {
         int fadeStart = Math.max(0, 10);
         float smoothGroundAge = this.groundAge + tickProgress;
         if (smoothGroundAge <= fadeStart) {
            return 1.0F;
         }

         float remaining = (40.0F - smoothGroundAge) / 30.0F;
         return MathHelper.clamp(remaining, 0.0F, 1.0F);
      }
   }

   private record GroundHit(boolean hit, double y) {
      private static final NonArmorShedClient.GroundHit MISS = new NonArmorShedClient.GroundHit(false, 0.0);
   }
}
