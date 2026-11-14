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
import net.minecraft.class_1268;
import net.minecraft.class_1792;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_3532;
import net.minecraft.class_3965;
import net.minecraft.class_2350.class_2351;

public class AutoPortalModule extends Module {
   private final Setting<Boolean> render = this.bool("Render", true);
   private final Setting<Float> fadeTime = this.num("FadeTime", 1.0F, 0.05F, 2.0F);
   private final Setting<Color> fillColor = this.color("FillColor", 85, 0, 255, 44);
   private final Setting<Color> outlineColor = this.color("OutlineColor", 85, 0, 255, 44);
   private final List<class_2338> portalBlocks = new ArrayList<>();
   private final Map<class_2338, Long> renderMap = new HashMap<>();

   public AutoPortalModule() {
      super("AutoPortal", "For the base hunter who has places to be.", Module.Category.WORLD);
   }

   @Override
   public void onEnable() {
      this.portalBlocks.clear();
      int obsidianCount = 0;

      for (int i = 0; i < 36; i++) {
         if (mc.field_1724.method_31548().method_5438(i).method_7909() == class_1802.field_8281) {
            obsidianCount += mc.field_1724.method_31548().method_5438(i).method_7947();
         }
      }

      if (obsidianCount < 10) {
         ChatUtil.sendMessage(class_2561.method_43470("[AutoPortal] Not enough obsidian (need at least 10)!"));
         this.disable();
      } else {
         class_2350 forward = mc.field_1724.method_5735();
         class_2350 right = forward.method_10170();
         class_2338 standingPos = mc.field_1724.method_24515();
         class_2338 blockBelow = standingPos.method_10074();

         double blockHeight;
         try {
            blockHeight = mc.field_1687.method_8320(blockBelow).method_26220(mc.field_1687, blockBelow).method_1105(class_2351.field_11052);
         } catch (Exception e) {
            blockHeight = 1.0;
         }

         if (blockHeight < 1.0) {
            standingPos = standingPos.method_10084();
         }

         class_2338 base = standingPos.method_10079(forward, 2).method_10079(right, -1);
         List<class_2338> frame = new ArrayList<>();
         frame.add(base.method_10079(right, 1));
         frame.add(base.method_10079(right, 2));

         for (int i = 1; i <= 3; i++) {
            frame.add(base.method_10079(right, 0).method_10086(i));
         }

         for (int i = 1; i <= 3; i++) {
            frame.add(base.method_10079(right, 3).method_10086(i));
         }

         frame.add(base.method_10079(right, 1).method_10086(4));
         frame.add(base.method_10079(right, 2).method_10086(4));
         long obsidianMatches = frame.stream().filter(pos -> mc.field_1687.method_8320(pos).method_26204().method_8389() == class_1802.field_8281).count();
         if (obsidianMatches >= frame.size()) {
            ChatUtil.sendMessage(class_2561.method_43470("[AutoPortal] A portal already exists here!"));
            this.disable();
         } else {
            boolean obstructed = frame.stream()
               .anyMatch(
                  pos -> !mc.field_1687.method_8320(pos).method_45474() && mc.field_1687.method_8320(pos).method_26204().method_8389() != class_1802.field_8281
               );
            if (obstructed) {
               ChatUtil.sendMessage(class_2561.method_43470("[AutoPortal] Portal area obstructed. Move and try again."));
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

         for (class_2338 pos : this.portalBlocks) {
            if (mc.field_1687.method_8320(pos).method_26204().method_8389() != class_1802.field_8281) {
               allDone = false;
               break;
            }
         }

         if (allDone) {
            this.ignite();
            ChatUtil.sendMessage(class_2561.method_43470("[AutoPortal] Portal complete!"));
            this.disable();
         } else {
            Result obsidian = this.findHotbar(class_1802.field_8281);
            if (obsidian != null) {
               class_243 eye = mc.field_1724.method_33571();
               long now = System.currentTimeMillis();

               for (class_2338 pos : this.portalBlocks) {
                  class_2680 state = mc.field_1687.method_8320(pos);
                  if (state.method_26204().method_8389() != class_1802.field_8281) {
                     if (class_243.method_24953(pos).method_1022(eye) > 5.154) {
                        ChatUtil.sendMessage(class_2561.method_43470("[AutoPortal] Out of range — disabling."));
                        this.disable();
                        return;
                     }

                     if (state.method_45474()) {
                        if (Homovore.placementManager.enqueue(pos, class_1802.field_8281)) {
                           this.renderMap.put(pos, now);
                        }
                     } else {
                        mc.field_1761.method_2910(pos, class_2350.field_11036);
                        mc.field_1724.method_6104(class_1268.field_5808);
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
      Result fas = this.findHotbar(class_1802.field_8884);
      if (fas != null) {
         class_2338 firePos = this.portalBlocks.get(0).method_10084();
         class_3965 hit = new class_3965(class_243.method_24953(firePos), class_2350.field_11036, firePos, false);
         Homovore.swapManager.withSwap(fas, SwapMode.ALTSILENT, 150, () -> {
            mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, hit);
            mc.field_1724.method_6104(class_1268.field_5808);
         });
      }
   }

   private Result findHotbar(class_1792 item) {
      Result r = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
      return r.found() ? r : null;
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue()) {
         long now = System.currentTimeMillis();
         double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;

         for (Entry<class_2338, Long> entry : this.renderMap.entrySet()) {
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
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), class_3532.method_15340(a, 0, 255));
   }
}
