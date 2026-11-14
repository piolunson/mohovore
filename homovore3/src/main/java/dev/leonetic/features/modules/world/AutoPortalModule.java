package dev.leonetic.features.modules.world;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.SwapMode;
import dev.leonetic.util.player.ChatUtil;
import dev.leonetic.util.render.RenderUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;

public class AutoPortalModule extends Module {
   private final Setting<Boolean> render = this.bool("Render", true);
   private final Setting<Float> fadeTime = this.num("FadeTime", 1.0F, 0.05F, 2.0F);
   private final Setting<Color> fillColor = this.color("FillColor", 85, 0, 255, 44);
   private final Setting<Color> outlineColor = this.color("OutlineColor", 85, 0, 255, 44);
   private final List<BlockPos> portalBlocks = new ArrayList<>();
   private final Map<BlockPos, Long> renderMap = new HashMap<>();

   public AutoPortalModule() {
      super("AutoPortal", "For the base hunter who has places to be.", Module.Category.WORLD);
   }

   @Override
   public void onEnable() {
      this.portalBlocks.clear();
      int obsidianCount = 0;

      for (int i = 0; i < 36; i++) {
         if (mc.player.getInventory().getStack(i).getItem() == Items.OBSIDIAN) {
            obsidianCount += mc.player.getInventory().getStack(i).getCount();
         }
      }

      if (obsidianCount < 10) {
         ChatUtil.sendMessage(Text.literal("[AutoPortal] Not enough obsidian (need at least 10)!"));
         this.disable();
      } else {
         Direction forward = mc.player.getHorizontalFacing();
         Direction right = forward.rotateYClockwise();
         BlockPos standingPos = mc.player.getBlockPos();
         BlockPos blockBelow = standingPos.down();

         double blockHeight;
         try {
            blockHeight = mc.world.getBlockState(blockBelow).getCollisionShape(mc.world, blockBelow).getMax(Axis.Y);
         } catch (Exception e) {
            blockHeight = 1.0;
         }

         if (blockHeight < 1.0) {
            standingPos = standingPos.up();
         }

         BlockPos base = standingPos.offset(forward, 2).offset(right, -1);
         List<BlockPos> frame = new ArrayList<>();
         frame.add(base.offset(right, 1));
         frame.add(base.offset(right, 2));

         for (int i = 1; i <= 3; i++) {
            frame.add(base.offset(right, 0).up(i));
         }

         for (int i = 1; i <= 3; i++) {
            frame.add(base.offset(right, 3).up(i));
         }

         frame.add(base.offset(right, 1).up(4));
         frame.add(base.offset(right, 2).up(4));
         long obsidianMatches = frame.stream().filter(pos -> mc.world.getBlockState(pos).getBlock().asItem() == Items.OBSIDIAN).count();
         if (obsidianMatches >= frame.size()) {
            ChatUtil.sendMessage(Text.literal("[AutoPortal] A portal already exists here!"));
            this.disable();
         } else {
            boolean obstructed = frame.stream()
               .anyMatch(pos -> !mc.world.getBlockState(pos).isReplaceable() && mc.world.getBlockState(pos).getBlock().asItem() != Items.OBSIDIAN);
            if (obstructed) {
               ChatUtil.sendMessage(Text.literal("[AutoPortal] Portal area obstructed. Move and try again."));
               this.disable();
            } else {
               this.portalBlocks.addAll(frame);
            }
         }
      }
   }

   @Override
   public void onDisable() {
      Homovore.placementManager.removeQueuedFor(this.portalBlocks::contains);
      this.portalBlocks.clear();
      this.renderMap.clear();
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck() && !this.portalBlocks.isEmpty()) {
         boolean allDone = true;

         for (BlockPos pos : this.portalBlocks) {
            if (mc.world.getBlockState(pos).getBlock().asItem() != Items.OBSIDIAN) {
               allDone = false;
               break;
            }
         }

         if (allDone) {
            this.ignite();
            ChatUtil.sendMessage(Text.literal("[AutoPortal] Portal complete!"));
            this.disable();
         } else {
            Result obsidian = this.findHotbar(Items.OBSIDIAN);
            if (obsidian != null) {
               Vec3d eye = mc.player.getEyePos();
               long now = System.currentTimeMillis();

               for (BlockPos pos : this.portalBlocks) {
                  BlockState state = mc.world.getBlockState(pos);
                  if (state.getBlock().asItem() != Items.OBSIDIAN) {
                     if (Vec3d.ofCenter(pos).distanceTo(eye) > 5.154) {
                        ChatUtil.sendMessage(Text.literal("[AutoPortal] Out of range — disabling."));
                        this.disable();
                        return;
                     }

                     if (state.isReplaceable()) {
                        if (Homovore.placementManager.enqueue(pos, Items.OBSIDIAN)) {
                           this.renderMap.put(pos, now);
                        }
                     } else {
                        mc.interactionManager.attackBlock(pos, Direction.UP);
                        mc.player.swingHand(Hand.MAIN_HAND);
                     }
                  }
               }

               long fadeMs = (long)(this.fadeTime.getValue() * 1000.0F);
               this.renderMap.entrySet().removeIf(e -> now - e.getValue() > fadeMs);
            }
         }
      }
   }

   private void ignite() {
      Result fas = this.findHotbar(Items.FLINT_AND_STEEL);
      if (fas != null) {
         BlockPos firePos = this.portalBlocks.get(0).up();
         BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(firePos), Direction.UP, firePos, false);
         Homovore.swapManager.withSwap(fas, SwapMode.ALTSILENT, 150, () -> {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
         });
      }
   }

   private Result findHotbar(Item item) {
      Result r = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
      return r.found() ? r : null;
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue()) {
         long now = System.currentTimeMillis();
         double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;

         for (Entry<BlockPos, Long> entry : this.renderMap.entrySet()) {
            long age = now - entry.getValue();
            if (!(age > fadeMs)) {
               double t = age / fadeMs;
               Color fc = this.fillColor.getValue();
               Color oc = this.outlineColor.getValue();
               RenderUtil.drawBoxFilled(event.getMatrix(), entry.getKey(), withAlpha(fc, (int)(fc.getAlpha() * (1.0 - t))));
               RenderUtil.drawBox(event.getMatrix(), entry.getKey(), withAlpha(oc, (int)(oc.getAlpha() * (1.0 - t))), 1.0F);
            }
         }
      }
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathHelper.clamp(a, 0, 255));
   }
}
