package dev.piolunson.features.modules.combat;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.entity.player.PreTickEvent;
import dev.piolunson.event.impl.render.Render3DEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.modules.client.TargetsModule;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.manager.RotationRequest;
import dev.piolunson.util.EnchantmentUtil;
import dev.piolunson.util.MathUtil;
import dev.piolunson.util.inventory.Result;
import dev.piolunson.util.inventory.ResultType;
import dev.piolunson.util.inventory.SwapMode;
import dev.piolunson.util.player.EatUtil;
import dev.piolunson.util.render.RenderUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.AttributeModifiersComponent.Entry;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public class AutoSwordModule extends Module {
   private static final double RANGE = 3.0;
   private final Setting<Double> delay = this.num("Delay", 0.92, 0.0, 1.0);
   private final Setting<Boolean> swing = this.bool("Swing", true);
   private final Setting<Boolean> render = this.bool("Render", true);
   private final Setting<AutoSwordModule.TpsMode> tpsMode = this.mode("TPS", AutoSwordModule.TpsMode.LATEST);
   private final Setting<Boolean> criticals = this.bool("Criticals", false);
   private final Setting<Boolean> critsWithSword = this.bool("CritsWithSword", true).setVisibility(v -> this.criticals.getValue());
   private final Setting<Boolean> strict = this.bool("Strict", true).setVisibility(v -> this.criticals.getValue());
   private Entity currentTarget = null;
   private float attackCooldownTicks = 0.0F;

   public AutoSwordModule() {
      super("AutoSword", "Automatically attacks nearby players with the best weapon.", Module.Category.COMBAT);
   }

   @Override
   public void onDisable() {
      this.currentTarget = null;
      this.attackCooldownTicks = 0.0F;
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck() && !mc.player.isDead()) {
         float tps = switch ((AutoSwordModule.TpsMode)this.tpsMode.getValue()) {
            case LATEST -> Homovore.tpsCounterService.getLatestTPS();
            case AVERAGE -> Homovore.tpsCounterService.getAverageTPS();
            default -> 20.0F;
         };
         if (!Float.isFinite(tps) || tps < 1.0F) {
            tps = 1.0F;
         }

         this.attackCooldownTicks -= tps / 20.0F;
         if (this.attackCooldownTicks < 0.0F) {
            this.attackCooldownTicks = 0.0F;
         }

         if (!this.isMaceAttackReady()) {
            AutoCrystalModule ac = Homovore.moduleManager.getModuleByClass(AutoCrystalModule.class);
            if (ac != null && ac.isEnabled() && ac.getLastBestDamage() > 0.0F) {
               this.currentTarget = null;
               return;
            }
         }

         this.currentTarget = this.findTarget();
         if (this.currentTarget != null) {
            if (!(this.attackCooldownTicks > 0.0F)) {
               int weaponSlot = this.getWeapon();
               if (weaponSlot != -1) {
                  Vec3d eyePos = mc.player.getCameraPosVec(1.0F);
                  Vec3d closestPoint = this.getClosestPointToEye(eyePos, this.currentTarget.getBoundingBox());
                  float[] angles = MathUtil.calcAngle(eyePos, closestPoint);
                  Homovore.rotationManager.submit(new RotationRequest("AutoSword", 6, angles[0], angles[1], RotationRequest.Mode.SILENT));
                  float serverYaw = Homovore.rotationManager.getServerYaw();
                  float serverPitch = Homovore.rotationManager.getServerPitch();
                  if (!this.currentTarget.getBoundingBox().contains(eyePos)) {
                     Vec3d lookVec = this.getLookVector(serverYaw, serverPitch);
                     Vec3d reachEnd = eyePos.add(lookVec.multiply(3.0));
                     if (this.currentTarget.getBoundingBox().raycast(eyePos, reachEnd).isEmpty()) {
                        return;
                     }
                  }

                  ItemStack weaponStack = mc.player.getInventory().getStack(weaponSlot);
                  boolean doCrit = this.shouldCrit(weaponStack);
                  boolean needSwap = weaponSlot != Homovore.swapManager.serverSlot();
                  if (!needSwap || !mc.player.isUsingItem() || mc.player.getActiveHand() != Hand.MAIN_HAND) {
                     if (!needSwap || !EatUtil.shouldDefer()) {
                        Result weapon = new Result(weaponSlot, weaponStack, ResultType.HOTBAR);
                        boolean swung = Homovore.swapManager.withSwap(weapon, SwapMode.SILENT, 200, () -> {
                           if (doCrit) {
                              double x = mc.player.getX();
                              double y = mc.player.getY();
                              double z = mc.player.getZ();
                              boolean hc = mc.player.horizontalCollision;
                              mc.getNetworkHandler().sendPacket(new Full(x, y, z, serverYaw, serverPitch, mc.player.isOnGround(), hc));
                              mc.getNetworkHandler().sendPacket(new Full(x, y + 0.0625, z, serverYaw, serverPitch, false, hc));
                              mc.getNetworkHandler().sendPacket(new Full(x, y + 0.045, z, serverYaw, serverPitch, false, hc));
                           }

                           mc.interactionManager.attackEntity(mc.player, this.currentTarget);
                           if (this.swing.getValue()) {
                              mc.player.swingHand(Hand.MAIN_HAND);
                           }
                        });
                        if (swung) {
                           this.attackCooldownTicks = this.getBaseCooldownTicks(weaponStack, tps) * this.delay.getValue().floatValue();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && this.currentTarget != null && !nullCheck() && !mc.player.isDead()) {
         AutoCrystalModule ac = Homovore.moduleManager.getModuleByClass(AutoCrystalModule.class);
         if (ac == null || !ac.isEnabled() || !(ac.getLastBestDamage() >= ac.getMinDamage())) {
            float partialTicks = event.getDelta();
            double interpX = this.currentTarget.lastRenderX + (this.currentTarget.getX() - this.currentTarget.lastRenderX) * partialTicks;
            double interpY = this.currentTarget.lastRenderY + (this.currentTarget.getY() - this.currentTarget.lastRenderY) * partialTicks;
            double interpZ = this.currentTarget.lastRenderZ + (this.currentTarget.getZ() - this.currentTarget.lastRenderZ) * partialTicks;
            Box box = this.currentTarget
               .getBoundingBox()
               .offset(interpX - this.currentTarget.getX(), interpY - this.currentTarget.getY(), interpZ - this.currentTarget.getZ());
            RenderUtil.drawBox(event.getMatrix(), box, Homovore.colorManager.get("ui"), 1.5F);
         }
      }
   }

   public boolean isMaceAttackReady() {
      if (mc.player == null) {
         return false;
      }

      if (!MaceItem.shouldDealAdditionalDamage(mc.player)) {
         return false;
      }

      for (int slot = 0; slot < 9; slot++) {
         if (mc.player.getInventory().getStack(slot).getItem() instanceof MaceItem) {
            return true;
         }
      }

      return false;
   }

   private boolean shouldCrit(ItemStack weaponStack) {
      if (!this.criticals.getValue()) {
         return false;
      } else if (!mc.player.isOnGround()) {
         return false;
      } else if (mc.player.isTouchingWater() || mc.player.isInLava()) {
         return false;
      } else if (mc.player.isClimbing() || mc.player.hasVehicle()) {
         return false;
      } else if (mc.player.hasStatusEffect(StatusEffects.LEVITATION)) {
         return false;
      } else {
         return this.critsWithSword.getValue() && !weaponStack.isIn(ItemTags.SWORDS) ? false : !this.strict.getValue() || this.isPlayerPhasedIntoBlock();
      }
   }

   private boolean isPlayerPhasedIntoBlock() {
      Box bb = mc.player.getBoundingBox();
      double eyeY = mc.player.getEyeY();
      double headMinY = MathHelper.clamp(eyeY - 0.1, bb.minY, bb.maxY);
      Box headBox = new Box(bb.minX, headMinY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
      int minX = MathHelper.floor(headBox.minX);
      int maxX = MathHelper.floor(headBox.maxX - 1.0E-7);
      int minY = MathHelper.floor(headBox.minY);
      int maxY = MathHelper.floor(headBox.maxY - 1.0E-7);
      int minZ = MathHelper.floor(headBox.minZ);
      int maxZ = MathHelper.floor(headBox.maxZ - 1.0E-7);

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               if (this.blockIntersectsBox(new BlockPos(x, y, z), headBox)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean blockIntersectsBox(BlockPos pos, Box box) {
      VoxelShape shape = mc.world.getBlockState(pos).getCollisionShape(mc.world, pos);
      return shape.isEmpty() ? false : shape.getBoundingBox().offset(pos).intersects(box);
   }

   private Entity findTarget() {
      TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
      if (targets == null) {
         return null;
      }

      Vec3d eyePos = mc.player.getCameraPosVec(1.0F);
      Entity best = null;
      double bestDist = Double.MAX_VALUE;

      for (Entity entity : mc.world.getOtherEntities(null, mc.player.getBoundingBox().expand(4.0))) {
         if (targets.isValidTarget(entity)) {
            double dist = eyePos.distanceTo(this.clampToBox(eyePos, entity.getBoundingBox()));
            if (!(dist > 3.0) && dist < bestDist) {
               bestDist = dist;
               best = entity;
            }
         }
      }

      return best;
   }

   private float getBaseCooldownTicks(ItemStack stack, float tps) {
      float baseTicks;
      if (stack.isIn(ItemTags.SWORDS)) {
         baseTicks = 13.0F;
      } else if (stack.isIn(ItemTags.AXES)) {
         baseTicks = 21.0F;
      } else if (stack.getItem() instanceof TridentItem) {
         baseTicks = 19.0F;
      } else if (stack.getItem() instanceof MaceItem) {
         baseTicks = 34.0F;
      } else {
         baseTicks = 5.0F;
      }

      return baseTicks * (20.0F / tps);
   }

   private int getWeapon() {
      int bestSlot = -1;
      float bestDamage = -1.0F;

      for (int slot = 0; slot < 9; slot++) {
         ItemStack held = mc.player.getInventory().getStack(slot);
         if (!held.isEmpty()) {
            boolean isSword = held.isIn(ItemTags.SWORDS);
            boolean isAxe = held.isIn(ItemTags.AXES);
            boolean isTrident = held.getItem() instanceof TridentItem;
            boolean isMace = held.getItem() instanceof MaceItem;
            if (isSword || isAxe || isTrident || isMace) {
               float attackDamage = 0.0F;
               if (held.contains(DataComponentTypes.ATTRIBUTE_MODIFIERS)) {
                  AttributeModifiersComponent modifiers = (AttributeModifiersComponent)held.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);

                  for (Entry entry : modifiers.modifiers()) {
                     if (entry.attribute().matches(EntityAttributes.ATTACK_DAMAGE)) {
                        attackDamage += (float)entry.modifier().value();
                     }
                  }
               }

               if (isSword) {
                  attackDamage += 5.0F;
               }

               attackDamage += EnchantmentUtil.getLevel(Enchantments.SHARPNESS, held) * 1.25F;
               attackDamage += EnchantmentUtil.getLevel(Enchantments.SMITE, held) * 2.5F;
               attackDamage += EnchantmentUtil.getLevel(Enchantments.BANE_OF_ARTHROPODS, held) * 2.5F;
               if (attackDamage > bestDamage) {
                  bestDamage = attackDamage;
                  bestSlot = slot;
               }
            }
         }
      }

      return bestSlot;
   }

   private Vec3d getClosestPointToEye(Vec3d eye, Box box) {
      double x = MathHelper.clamp(eye.x, box.minX, box.maxX);
      double y = MathHelper.clamp(eye.y, box.minY, box.maxY);
      double z = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
      double VEC = 0.0625;
      double EPS = 1.0E-9;
      if (Math.abs(x - box.minX) < 1.0E-9) {
         x = Math.min(box.minX + 0.0625, box.maxX - 1.0E-9);
      } else if (Math.abs(x - box.maxX) < 1.0E-9) {
         x = Math.max(box.maxX - 0.0625, box.minX + 1.0E-9);
      }

      if (Math.abs(z - box.minZ) < 1.0E-9) {
         z = Math.min(box.minZ + 0.0625, box.maxZ - 1.0E-9);
      } else if (Math.abs(z - box.maxZ) < 1.0E-9) {
         z = Math.max(box.maxZ - 0.0625, box.minZ + 1.0E-9);
      }

      return new Vec3d(x, y, z);
   }

   private Vec3d clampToBox(Vec3d point, Box box) {
      return new Vec3d(
         MathHelper.clamp(point.x, box.minX, box.maxX), MathHelper.clamp(point.y, box.minY, box.maxY), MathHelper.clamp(point.z, box.minZ, box.maxZ)
      );
   }

   private Vec3d getLookVector(float yaw, float pitch) {
      float f = (float)Math.cos(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float g = (float)Math.sin(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float h = -((float)Math.cos(-pitch * (float) (Math.PI / 180.0)));
      float i = (float)Math.sin(-pitch * (float) (Math.PI / 180.0));
      return new Vec3d(g * h, i, f * h);
   }

   public enum TpsMode {
      NONE,
      LATEST,
      AVERAGE;
   }
}
