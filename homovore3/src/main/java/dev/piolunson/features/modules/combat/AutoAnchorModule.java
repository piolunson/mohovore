package dev.piolunson.features.modules.combat;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.entity.player.PreTickEvent;
import dev.piolunson.event.impl.render.Render3DEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.modules.client.TargetsModule;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.manager.RotationRequest;
import dev.piolunson.mixin.client.ClientLevelAccessor;
import dev.piolunson.util.AnchorDamageUtil;
import dev.piolunson.util.DamageSyncTracker;
import dev.piolunson.util.MathUtil;
import dev.piolunson.util.PlaceUtil;
import dev.piolunson.util.inventory.InventoryUtil;
import dev.piolunson.util.inventory.Result;
import dev.piolunson.util.inventory.ResultType;
import dev.piolunson.util.player.EatUtil;
import dev.piolunson.util.render.RenderUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.World;

public class AutoAnchorModule extends Module {
   private final Setting<Double> minDamage = this.num("MinDamage", 6.0, 0.0, 36.0).setPage("General");
   private final Setting<Double> maxSelfDamage = this.num("MaxSelfDamage", 4.0, 0.0, 36.0).setPage("General");
   private static final double PLACE_RANGE = 6.0;
   private final Setting<Integer> delay = this.num("Delay", 2, 0, 20).setPage("General");
   private final Setting<Boolean> damageSync = this.bool("DamageSync", false).setPage("General");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 1.0F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 130, 80, 255, 45).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 130, 80, 255, 255).setPage("Render");
   private int lastBurstTick = -1;
   private float lastDamage = 0.0F;
   private BlockPos renderPos = null;
   private long renderStart = 0L;

   public AutoAnchorModule() {
      super("AutoAnchor", "Places, charges and detonates respawn anchors in a single burst.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      this.lastBurstTick = -1;
      this.lastDamage = 0.0F;
      this.renderPos = null;
   }

   @Override
   public void onDisable() {
      this.lastDamage = 0.0F;
      this.renderPos = null;
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck() && !mc.player.isDead()) {
         if (!mc.world.getRegistryKey().equals(World.NETHER)) {
            if (mc.player.currentScreenHandler.syncId == 0) {
               int sinceLast = mc.player.age - this.lastBurstTick;
               if (sinceLast < 0 || sinceLast >= this.delay.getValue()) {
                  if (!EatUtil.shouldDefer()) {
                     int anchorSlot = this.hotbarSlotOf(Items.RESPAWN_ANCHOR);
                     int glowSlot = this.hotbarSlotOf(Items.GLOWSTONE);
                     if (anchorSlot >= 0 && glowSlot >= 0) {
                        List<AnchorDamageUtil.Target> targets = this.collectTargets();
                        if (targets.isEmpty()) {
                           this.lastDamage = 0.0F;
                        } else {
                           AutoAnchorModule.Candidate best = this.findBest(targets);
                           if (best == null) {
                              this.lastDamage = 0.0F;
                           } else {
                              if (this.burst(best.pos, best.needPlace, anchorSlot, glowSlot)) {
                                 this.lastBurstTick = mc.player.age;
                                 this.lastDamage = best.damage;
                                 if (this.render.getValue()) {
                                    this.renderPos = best.pos;
                                    this.renderStart = System.currentTimeMillis();
                                 }
                              }
                           }
                        }
                     } else {
                        this.lastDamage = 0.0F;
                     }
                  }
               }
            }
         }
      }
   }

   private boolean burst(BlockPos pos, boolean needPlace, int anchorSlot, int glowSlot) {
      ClientPlayNetworkHandler conn = mc.getNetworkHandler();
      if (conn == null) {
         return false;
      }

      BlockHitResult placeHit = null;
      if (needPlace) {
         placeHit = Homovore.placementManager.prepareAirPlaceHit(pos);
         if (placeHit == null) {
            return false;
         }
      }

      Vec3d eye = mc.player.getCameraPosVec(1.0F);
      BlockHitResult hit = this.anchorHit(pos, eye);
      float[] angles = MathUtil.calcAngle(eye, hit.getPos());
      Homovore.rotationManager.submit(new RotationRequest("AutoAnchor", 62, angles[0], angles[1], RotationRequest.Mode.SILENT));
      int originalSlot = Homovore.swapManager.serverSlot();
      int returnSlot = this.isGlowstone(originalSlot) ? anchorSlot : originalSlot;
      int currentSlot = originalSlot;
      if (needPlace) {
         if (anchorSlot != currentSlot) {
            conn.sendPacket(new UpdateSelectedSlotC2SPacket(anchorSlot));
            currentSlot = anchorSlot;
         }

         this.sendUseItemOn(conn, placeHit);
         Homovore.placementManager.notePlacement(pos);
      }

      if (glowSlot != currentSlot) {
         conn.sendPacket(new UpdateSelectedSlotC2SPacket(glowSlot));
         currentSlot = glowSlot;
      }

      this.sendUseItemOn(conn, hit);
      if (returnSlot != currentSlot) {
         conn.sendPacket(new UpdateSelectedSlotC2SPacket(returnSlot));
         currentSlot = returnSlot;
      }

      this.sendUseItemOn(conn, hit);
      conn.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
      if (currentSlot != originalSlot) {
         conn.sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
      }

      return true;
   }

   private void sendUseItemOn(ClientPlayNetworkHandler conn, BlockHitResult hit) {
      this.sendUseItemOn(conn, hit, Hand.MAIN_HAND);
   }

   private void sendUseItemOn(ClientPlayNetworkHandler conn, BlockHitResult hit, Hand hand) {
      PendingUpdateManager handler = ((ClientLevelAccessor)mc.world).homovore$getBlockStatePredictionHandler().incrementSequence();

      try {
         conn.sendPacket(new PlayerInteractBlockC2SPacket(hand, hit, handler.getSequence()));
      } catch (Throwable var8) {
         if (handler != null) {
            try {
               handler.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (handler != null) {
         handler.close();
      }
   }

   private boolean isGlowstone(int slot) {
      if (slot >= 0 && slot <= 8) {
         ItemStack stack = mc.player.getInventory().getStack(slot);
         return stack.isOf(Items.GLOWSTONE);
      } else {
         return false;
      }
   }

   private BlockHitResult anchorHit(BlockPos pos, Vec3d eye) {
      Vec3d center = Vec3d.ofCenter(pos);
      double dx = eye.x - center.x;
      double dy = eye.y - center.y;
      double dz = eye.z - center.z;
      double ax = Math.abs(dx);
      double ay = Math.abs(dy);
      double az = Math.abs(dz);
      Direction face;
      if (ay >= ax && ay >= az) {
         face = dy > 0.0 ? Direction.UP : Direction.DOWN;
      } else if (ax >= az) {
         face = dx > 0.0 ? Direction.EAST : Direction.WEST;
      } else {
         face = dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
      }

      Vec3d hitVec = center.add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
      return new BlockHitResult(hitVec, face, pos, false);
   }

   private AutoAnchorModule.Candidate findBest(List<AnchorDamageUtil.Target> targets) {
      Vec3d eye = mc.player.getCameraPosVec(1.0F);
      BlockPos playerPos = mc.player.getBlockPos();
      double maxSelf = this.maxSelfDamage.getValue();
      double min = this.minDamage.getValue();
      double rangeSq = 36.0;
      float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      int r = (int)Math.ceil(6.0);
      int rr = r * r;
      AutoAnchorModule.Candidate best = null;
      Mutable cursor = new Mutable();

      for (int x = -r; x <= r; x++) {
         int xx = x * x;

         for (int y = -r; y <= r; y++) {
            int xxyy = xx + y * y;

            for (int z = -r; z <= r; z++) {
               if (xxyy + z * z <= rr) {
                  cursor.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                  Vec3d center = Vec3d.ofCenter(cursor);
                  if (!(eye.squaredDistanceTo(center) > rangeSq)) {
                     BlockState state = mc.world.getBlockState(cursor);
                     boolean existing = state.isOf(Blocks.RESPAWN_ANCHOR);
                     BlockPos pos = null;
                     boolean bounded = false;
                     int i = 0;

                     for (int n = targets.size(); i < n; i++) {
                        AnchorDamageUtil.Target target = targets.get(i);
                        if (!(target.pos().distanceTo(center) > 10.0) && AnchorDamageUtil.maxDamage(target, center) >= min) {
                           bounded = true;
                           break;
                        }
                     }

                     if (bounded) {
                        if (existing) {
                           pos = cursor.toImmutable();
                        } else {
                           if (!state.isReplaceable() || !PlaceUtil.canPlace(cursor)) {
                              continue;
                           }

                           pos = cursor.toImmutable();
                        }

                        float total = 0.0F;
                        boolean any = false;
                        int ix = 0;

                        for (int n = targets.size(); ix < n; ix++) {
                           AnchorDamageUtil.Target target = targets.get(ix);
                           if (!(target.pos().distanceTo(center) > 10.0) && (!this.damageSync.getValue() || !DamageSyncTracker.shouldHold(target.entity()))) {
                              float dmg = AnchorDamageUtil.damage(target, center, pos);
                              if (!(dmg <= 0.0F)) {
                                 total += dmg;
                                 any = true;
                              }
                           }
                        }

                        if (any && !(total < min)) {
                           float self = AnchorDamageUtil.selfDamage(center, pos);
                           if ((best == null || !(total <= best.damage)) && !(self > maxSelf) && (!(self > 0.0F) || !(self + 1.5F >= playerHealth))) {
                              best = new AutoAnchorModule.Candidate(pos, total, !existing);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private List<AnchorDamageUtil.Target> collectTargets() {
      TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
      List<AnchorDamageUtil.Target> out = new ArrayList<>();
      Box area = mc.player.getBoundingBox().expand(16.0);

      for (Entity e : mc.world.getOtherEntities(mc.player, area)) {
         if (e instanceof LivingEntity living && !living.isDead() && (targets == null || targets.isValidPlayerTarget(e))) {
            out.add(AnchorDamageUtil.Target.of(living));
         }
      }

      return out;
   }

   private int hotbarSlotOf(Item item) {
      Result r = InventoryUtil.find(item, InventoryUtil.HOTBAR_SCOPE);
      return r.found() && r.type() != ResultType.OFFHAND ? r.slot() : -1;
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && this.renderPos != null) {
         long age = System.currentTimeMillis() - this.renderStart;
         double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;
         if (age > fadeMs) {
            this.renderPos = null;
         } else {
            double t = age / fadeMs;
            Color fc = this.fillColor.getValue();
            Color oc = this.outlineColor.getValue();
            RenderUtil.drawBoxFilled(event.getMatrix(), this.renderPos, withAlpha(fc, (int)(fc.getAlpha() * (1.0 - t))));
            RenderUtil.drawBox(event.getMatrix(), this.renderPos, withAlpha(oc, (int)(oc.getAlpha() * (1.0 - t))), 1.0F);
         }
      }
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathHelper.clamp(a, 0, 255));
   }

   @Override
   public String getDisplayInfo() {
      return this.lastDamage > 0.0F ? String.format("%.1f", this.lastDamage) : null;
   }

   private record Candidate(BlockPos pos, float damage, boolean needPlace) {
   }
}
