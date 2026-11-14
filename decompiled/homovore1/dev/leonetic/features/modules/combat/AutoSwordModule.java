package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.client.TargetsModule;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.util.EnchantmentUtil;
import dev.leonetic.util.MathUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.inventory.SwapMode;
import dev.leonetic.util.player.EatUtil;
import dev.leonetic.util.render.RenderUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1294;
import net.minecraft.class_1297;
import net.minecraft.class_1799;
import net.minecraft.class_1835;
import net.minecraft.class_1893;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_265;
import net.minecraft.class_3489;
import net.minecraft.class_3532;
import net.minecraft.class_5134;
import net.minecraft.class_9285;
import net.minecraft.class_9334;
import net.minecraft.class_9362;
import net.minecraft.class_2828.class_2830;
import net.minecraft.class_9285.class_9287;

public class AutoSwordModule extends Module {
   private static final double RANGE = 3.0;
   private final Setting<Double> delay = this.num("Delay", 0.92, 0.0, 1.0);
   private final Setting<Boolean> swing = this.bool("Swing", true);
   private final Setting<Boolean> render = this.bool("Render", true);
   private final Setting<AutoSwordModule.TpsMode> tpsMode = this.mode("TPS", AutoSwordModule.TpsMode.LATEST);
   private final Setting<Boolean> criticals = this.bool("Criticals", false);
   private final Setting<Boolean> critsWithSword = this.bool("CritsWithSword", true).setVisibility(v -> this.criticals.getValue());
   private final Setting<Boolean> strict = this.bool("Strict", true).setVisibility(v -> this.criticals.getValue());
   private class_1297 currentTarget = null;
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
      if (!nullCheck() && !mc.field_1724.method_29504()) {
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
                  class_243 eyePos = mc.field_1724.method_5836(1.0F);
                  class_243 closestPoint = this.getClosestPointToEye(eyePos, this.currentTarget.method_5829());
                  float[] angles = MathUtil.calcAngle(eyePos, closestPoint);
                  Homovore.rotationManager.submit(new RotationRequest("AutoSword", 6, angles[0], angles[1], RotationRequest.Mode.SILENT));
                  float serverYaw = Homovore.rotationManager.getServerYaw();
                  float serverPitch = Homovore.rotationManager.getServerPitch();
                  if (!this.currentTarget.method_5829().method_1006(eyePos)) {
                     class_243 lookVec = this.getLookVector(serverYaw, serverPitch);
                     class_243 reachEnd = eyePos.method_1019(lookVec.method_1021(3.0));
                     if (this.currentTarget.method_5829().method_992(eyePos, reachEnd).isEmpty()) {
                        return;
                     }
                  }

                  class_1799 weaponStack = mc.field_1724.method_31548().method_5438(weaponSlot);
                  boolean doCrit = this.shouldCrit(weaponStack);
                  boolean needSwap = weaponSlot != Homovore.swapManager.serverSlot();
                  if (!needSwap || !mc.field_1724.method_6115() || mc.field_1724.method_6058() != class_1268.field_5808) {
                     if (!needSwap || !EatUtil.shouldDefer()) {
                        Result weapon = new Result(weaponSlot, weaponStack, ResultType.HOTBAR);
                        boolean swung = Homovore.swapManager.withSwap(weapon, SwapMode.SILENT, 200, () -> {
                           if (doCrit) {
                              double x = mc.field_1724.method_23317();
                              double y = mc.field_1724.method_23318();
                              double z = mc.field_1724.method_23321();
                              boolean hc = mc.field_1724.field_5976;
                              mc.method_1562().method_52787(new class_2830(x, y, z, serverYaw, serverPitch, mc.field_1724.method_24828(), hc));
                              mc.method_1562().method_52787(new class_2830(x, y + 0.0625, z, serverYaw, serverPitch, false, hc));
                              mc.method_1562().method_52787(new class_2830(x, y + 0.045, z, serverYaw, serverPitch, false, hc));
                           }

                           mc.field_1761.method_2918(mc.field_1724, this.currentTarget);
                           if (this.swing.getValue()) {
                              mc.field_1724.method_6104(class_1268.field_5808);
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
      if (this.render.getValue() && this.currentTarget != null && !nullCheck() && !mc.field_1724.method_29504()) {
         AutoCrystalModule ac = Homovore.moduleManager.getModuleByClass(AutoCrystalModule.class);
         if (ac == null || !ac.isEnabled() || !(ac.getLastBestDamage() >= ac.getMinDamage())) {
            float partialTicks = event.getDelta();
            double interpX = this.currentTarget.field_6038 + (this.currentTarget.method_23317() - this.currentTarget.field_6038) * partialTicks;
            double interpY = this.currentTarget.field_5971 + (this.currentTarget.method_23318() - this.currentTarget.field_5971) * partialTicks;
            double interpZ = this.currentTarget.field_5989 + (this.currentTarget.method_23321() - this.currentTarget.field_5989) * partialTicks;
            class_238 box = this.currentTarget
               .method_5829()
               .method_989(
                  interpX - this.currentTarget.method_23317(), interpY - this.currentTarget.method_23318(), interpZ - this.currentTarget.method_23321()
               );
            RenderUtil.drawBox(event.getMatrix(), box, Homovore.colorManager.get("ui"), 1.5F);
         }
      }
   }

   public boolean isMaceAttackReady() {
      if (mc.field_1724 == null) {
         return false;
      }

      if (!class_9362.method_58659(mc.field_1724)) {
         return false;
      }

      for (int slot = 0; slot < 9; slot++) {
         if (mc.field_1724.method_31548().method_5438(slot).method_7909() instanceof class_9362) {
            return true;
         }
      }

      return false;
   }

   private boolean shouldCrit(class_1799 weaponStack) {
      if (!this.criticals.getValue()) {
         return false;
      } else if (!mc.field_1724.method_24828()) {
         return false;
      } else if (mc.field_1724.method_5799() || mc.field_1724.method_5771()) {
         return false;
      } else if (mc.field_1724.method_6101() || mc.field_1724.method_5765()) {
         return false;
      } else if (mc.field_1724.method_6059(class_1294.field_5902)) {
         return false;
      } else {
         return this.critsWithSword.getValue() && !weaponStack.method_31573(class_3489.field_42611)
            ? false
            : !this.strict.getValue() || this.isPlayerPhasedIntoBlock();
      }
   }

   private boolean isPlayerPhasedIntoBlock() {
      class_238 bb = mc.field_1724.method_5829();
      double eyeY = mc.field_1724.method_23320();
      double headMinY = class_3532.method_15350(eyeY - 0.1, bb.field_1322, bb.field_1325);
      class_238 headBox = new class_238(bb.field_1323, headMinY, bb.field_1321, bb.field_1320, bb.field_1325, bb.field_1324);
      int minX = class_3532.method_15357(headBox.field_1323);
      int maxX = class_3532.method_15357(headBox.field_1320 - 1.0E-7);
      int minY = class_3532.method_15357(headBox.field_1322);
      int maxY = class_3532.method_15357(headBox.field_1325 - 1.0E-7);
      int minZ = class_3532.method_15357(headBox.field_1321);
      int maxZ = class_3532.method_15357(headBox.field_1324 - 1.0E-7);

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               if (this.blockIntersectsBox(new class_2338(x, y, z), headBox)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean blockIntersectsBox(class_2338 pos, class_238 box) {
      class_265 shape = mc.field_1687.method_8320(pos).method_26220(mc.field_1687, pos);
      return shape.method_1110() ? false : shape.method_1107().method_996(pos).method_994(box);
   }

   private class_1297 findTarget() {
      TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
      if (targets == null) {
         return null;
      }

      class_243 eyePos = mc.field_1724.method_5836(1.0F);
      class_1297 best = null;
      double bestDist = Double.MAX_VALUE;

      for (class_1297 entity : mc.field_1687.method_8335(null, mc.field_1724.method_5829().method_1014(4.0))) {
         if (targets.isValidTarget(entity)) {
            double dist = eyePos.method_1022(this.clampToBox(eyePos, entity.method_5829()));
            if (!(dist > 3.0) && dist < bestDist) {
               bestDist = dist;
               best = entity;
            }
         }
      }

      return best;
   }

   private float getBaseCooldownTicks(class_1799 stack, float tps) {
      float baseTicks;
      if (stack.method_31573(class_3489.field_42611)) {
         baseTicks = 13.0F;
      } else if (stack.method_31573(class_3489.field_42612)) {
         baseTicks = 21.0F;
      } else if (stack.method_7909() instanceof class_1835) {
         baseTicks = 19.0F;
      } else if (stack.method_7909() instanceof class_9362) {
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
         class_1799 held = mc.field_1724.method_31548().method_5438(slot);
         if (!held.method_7960()) {
            boolean isSword = held.method_31573(class_3489.field_42611);
            boolean isAxe = held.method_31573(class_3489.field_42612);
            boolean isTrident = held.method_7909() instanceof class_1835;
            boolean isMace = held.method_7909() instanceof class_9362;
            if (isSword || isAxe || isTrident || isMace) {
               float attackDamage = 0.0F;
               if (held.method_57826(class_9334.field_49636)) {
                  class_9285 modifiers = (class_9285)held.method_58694(class_9334.field_49636);

                  for (class_9287 entry : modifiers.comp_2393()) {
                     if (entry.comp_2395().method_55838(class_5134.field_23721)) {
                        attackDamage += (float)entry.comp_2396().comp_2449();
                     }
                  }
               }

               if (isSword) {
                  attackDamage += 5.0F;
               }

               attackDamage += EnchantmentUtil.getLevel(class_1893.field_9118, held) * 1.25F;
               attackDamage += EnchantmentUtil.getLevel(class_1893.field_9123, held) * 2.5F;
               attackDamage += EnchantmentUtil.getLevel(class_1893.field_9112, held) * 2.5F;
               if (attackDamage > bestDamage) {
                  bestDamage = attackDamage;
                  bestSlot = slot;
               }
            }
         }
      }

      return bestSlot;
   }

   private class_243 getClosestPointToEye(class_243 eye, class_238 box) {
      double x = class_3532.method_15350(eye.field_1352, box.field_1323, box.field_1320);
      double y = class_3532.method_15350(eye.field_1351, box.field_1322, box.field_1325);
      double z = class_3532.method_15350(eye.field_1350, box.field_1321, box.field_1324);
      double VEC = 0.0625;
      double EPS = 1.0E-9;
      if (Math.abs(x - box.field_1323) < 1.0E-9) {
         x = Math.min(box.field_1323 + 0.0625, box.field_1320 - 1.0E-9);
      } else if (Math.abs(x - box.field_1320) < 1.0E-9) {
         x = Math.max(box.field_1320 - 0.0625, box.field_1323 + 1.0E-9);
      }

      if (Math.abs(z - box.field_1321) < 1.0E-9) {
         z = Math.min(box.field_1321 + 0.0625, box.field_1324 - 1.0E-9);
      } else if (Math.abs(z - box.field_1324) < 1.0E-9) {
         z = Math.max(box.field_1324 - 0.0625, box.field_1321 + 1.0E-9);
      }

      return new class_243(x, y, z);
   }

   private class_243 clampToBox(class_243 point, class_238 box) {
      return new class_243(
         class_3532.method_15350(point.field_1352, box.field_1323, box.field_1320),
         class_3532.method_15350(point.field_1351, box.field_1322, box.field_1325),
         class_3532.method_15350(point.field_1350, box.field_1321, box.field_1324)
      );
   }

   private class_243 getLookVector(float yaw, float pitch) {
      float f = (float)Math.cos(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float g = (float)Math.sin(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float h = -((float)Math.cos(-pitch * (float) (Math.PI / 180.0)));
      float i = (float)Math.sin(-pitch * (float) (Math.PI / 180.0));
      return new class_243(g * h, i, f * h);
   }

   public enum TpsMode {
      NONE,
      LATEST,
      AVERAGE;
   }
}
