package dev.leonetic.features.modules.render;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.InteractionUtil;
import dev.leonetic.util.render.MatrixCapture;
import dev.leonetic.util.render.RenderUtil;
import dev.leonetic.util.traits.Util;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2620;
import net.minecraft.class_265;
import net.minecraft.class_2680;
import net.minecraft.class_332;

public class BreakIndicatorsModule extends Module {
   private final Setting<Boolean> useDoubleminePrediction = this.bool("UseDoubleminePrediction", false).setPage("General");
   private final Setting<Float> rebreakCompletionAmount = this.num("RebreakCompletionAmount", 0.7F, 0.0F, 1.5F).setPage("General");
   private final Setting<Float> completionAmount = this.num("FullCompletionAmount", 1.0F, 0.0F, 1.5F).setPage("General");
   private final Setting<Float> removeCompletionAmount = this.num("ForceRemoveCompletionAmount", 1.3F, 0.0F, 1.5F).setPage("General");
   private final Setting<Boolean> ignoreFriends = this.bool("IgnoreFriends", false).setPage("General");
   private final Setting<Boolean> holdRebreak = this.bool("HoldRebreak", true).setPage("General");
   private final Setting<Boolean> render = this.bool("DoRender", true).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.5F, 5.0F).setPage("Render");
   private final Setting<Color> sideColor = this.color("SideColor", 255, 0, 80, 10).setPage("Render");
   private final Setting<Color> lineColor = this.color("LineColor", 255, 255, 255, 40).setPage("Render");
   private final Setting<Boolean> chinese = this.bool("Chinese", false).setPage("Chinese");
   private final Setting<BreakIndicatorsModule.Easing> chineseEase = this.mode("Ease", BreakIndicatorsModule.Easing.CubicInOut)
      .setPage("Chinese")
      .setVisibility(v -> this.chinese.getValue());
   private final Setting<Boolean> chineseProgress = this.bool("Progress", true).setPage("Chinese").setVisibility(v -> this.chinese.getValue());
   private final Setting<Float> chineseTextScale = this.num("TextScale", 1.0F, 0.1F, 3.0F)
      .setPage("Chinese")
      .setVisibility(v -> this.chinese.getValue() && this.chineseProgress.getValue());
   private final Setting<Float> chineseMinTextScale = this.num("MinTextScale", 0.3F, 0.1F, 1.0F)
      .setPage("Chinese")
      .setVisibility(v -> this.chinese.getValue() && this.chineseProgress.getValue());
   private final Setting<Boolean> chineseSecond = this.bool("Second", true).setPage("Chinese").setVisibility(v -> this.chinese.getValue());
   private final Setting<Color> chineseFill = this.color("Fill", 198, 176, 12, 78).setPage("Chinese").setVisibility(v -> this.chinese.getValue());
   private final Setting<Color> chineseBox = this.color("Box", 198, 176, 12, 255).setPage("Chinese").setVisibility(v -> this.chinese.getValue());
   private final Setting<Color> chineseFriendFill = this.color("FriendFill", 30, 45, 169, 78).setPage("Chinese").setVisibility(v -> this.chinese.getValue());
   private final Setting<Color> chineseFriendBox = this.color("FriendBox", 30, 45, 169, 255).setPage("Chinese").setVisibility(v -> this.chinese.getValue());
   private final Setting<Color> chineseSecondFill = this.color("SecondFill", 255, 255, 255, 100)
      .setPage("Chinese")
      .setVisibility(v -> this.chinese.getValue() && this.chineseSecond.getValue());
   private final Setting<Color> chineseSecondBox = this.color("SecondBox", 255, 255, 255, 255)
      .setPage("Chinese")
      .setVisibility(v -> this.chinese.getValue() && this.chineseSecond.getValue());
   private static final Color PROGRESS_START = new Color(255, 6, 6);
   private static final Color PROGRESS_END = new Color(0, 255, 12);
   private static final Color DOUBLE_COLOR = new Color(255, 179, 96);
   private static final long BREAK_TIME_MS = 2500L;
   private final Map<class_2338, BreakIndicatorsModule.BreakEntry> breaks = new ConcurrentHashMap<>();

   public BreakIndicatorsModule() {
      super("BreakIndicators", "Renders the progress of a block being broken.", Module.Category.RENDER);
   }

   @Override
   public void onEnable() {
      this.breaks.clear();
   }

   @Override
   public void onDisable() {
      this.breaks.clear();
   }

   @Subscribe
   private void onPacket(PacketEvent.Receive event) {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         if (event.getPacket() instanceof class_2620 packet) {
            class_2338 var12 = packet.method_11277().method_10062();
            int id = packet.method_11280();
            int progress = packet.method_11278();
            if (progress >= 0 && progress <= 9) {
               class_2680 state = mc.field_1687.method_8320(var12);
               if (InteractionUtil.canBreak(var12, state)) {
                  class_1297 entity = mc.field_1687.method_8469(id);
                  long now = System.currentTimeMillis();
                  if (this.holdRebreak.getValue()) {
                     this.breaks.values().removeIf(b -> b.held && b.entityId == id && !b.pos.equals(var12));
                  }

                  BreakIndicatorsModule.BreakEntry existing = this.breaks.get(var12);
                  if (existing != null) {
                     if (existing.held) {
                        existing.held = false;
                        existing.startMs = now;
                     }
                  } else {
                     if (this.useDoubleminePrediction.getValue() && entity instanceof class_1657) {
                        List<BreakIndicatorsModule.BreakEntry> playerBreaks = this.breaks
                           .values()
                           .stream()
                           .filter(b -> b.entity == entity && !b.pos.equals(var12))
                           .sorted(Comparator.comparingLong(b -> b.startMs))
                           .toList();
                        if (playerBreaks.size() >= 2) {
                           this.breaks.remove(playerBreaks.getLast().pos);
                        }
                     }

                     this.breaks.put(var12, new BreakIndicatorsModule.BreakEntry(var12, id, entity, now));
                  }
               }
            } else {
               BreakIndicatorsModule.BreakEntry existing = this.breaks.get(var12);
               if (existing != null && !existing.held && existing.entityId == id && !mc.field_1687.method_8320(var12).method_26215()) {
                  this.breaks.remove(var12);
               }
            }
         }
      }
   }

   public boolean isBlockBeingBroken(class_2338 pos) {
      return this.breaks.containsKey(pos);
   }

   public boolean isBlockBeingBrokenByFriend(class_2338 pos) {
      BreakIndicatorsModule.BreakEntry entry = this.breaks.get(pos);
      return entry != null && entry.entity instanceof class_1657 player && Homovore.friendManager.isFriend(player);
   }

   public boolean isBlockBeingBrokenBySelf(class_2338 pos) {
      if (mc.field_1724 == null) {
         return false;
      }

      BreakIndicatorsModule.BreakEntry entry = this.breaks.get(pos);
      return entry != null && entry.entityId == mc.field_1724.method_5628();
   }

   public double getBlockBreakProgress(class_2338 pos) {
      BreakIndicatorsModule.BreakEntry entry = this.breaks.get(pos);
      if (entry == null) {
         return 0.0;
      } else {
         return entry.held ? 1.0 : Math.clamp(entry.progress(System.currentTimeMillis()), 0.0, 1.0);
      }
   }

   public Map<class_2338, BreakIndicatorsModule.BreakInfo> getActiveBreaksSnapshot() {
      Map<class_2338, BreakIndicatorsModule.BreakInfo> snapshot = new HashMap<>();

      for (BreakIndicatorsModule.BreakEntry entry : this.breaks.values()) {
         if (!entry.held) {
            snapshot.put(entry.pos, new BreakIndicatorsModule.BreakInfo(entry.pos, entry.entity instanceof class_1657 player ? player : null));
         }
      }

      return snapshot;
   }

   @Subscribe
   private void onRender(Render3DEvent event) {
      if (!nullCheck()) {
         long now = System.currentTimeMillis();
         Iterator<Entry<class_2338, BreakIndicatorsModule.BreakEntry>> iterator = this.breaks.entrySet().iterator();

         while (iterator.hasNext()) {
            BreakIndicatorsModule.BreakEntry entry = iterator.next().getValue();
            if (!entry.held) {
               class_2680 state = mc.field_1687.method_8320(entry.pos);
               boolean finished = state.method_26215()
                  || entry.progress(now) > this.removeCompletionAmount.getValue().floatValue()
                  || !InteractionUtil.canBreak(entry.pos, state);
               if (finished) {
                  if (this.holdRebreak.getValue()) {
                     entry.held = true;
                  } else {
                     iterator.remove();
                  }
               }
            }
         }

         if (this.useDoubleminePrediction.getValue()) {
            Map<class_1657, List<BreakIndicatorsModule.BreakEntry>> playerBreakingBlocks = this.breaks
               .values()
               .stream()
               .filter(entryx -> entryx.entity instanceof class_1657)
               .sorted(Comparator.comparingLong(entryx -> entryx.startMs))
               .collect(Collectors.groupingBy(entryx -> (class_1657)entryx.entity, Collectors.toList()));

            for (List<BreakIndicatorsModule.BreakEntry> list : playerBreakingBlocks.values()) {
               list.forEach(entryx -> entryx.isRebreak = false);
               if (list.size() >= 2) {
                  list.getLast().isRebreak = true;
               }
            }
         }

         if (this.render.getValue()) {
            for (BreakIndicatorsModule.BreakEntry entry : this.breaks.values()) {
               if (!this.ignoreFriends.getValue() || !(entry.entity instanceof class_1657 player && Homovore.friendManager.isFriend(player))) {
                  entry.renderBlock(event, now);
               }
            }
         }
      }
   }

   @Override
   public void onRender2D(Render2DEvent event) {
      if (!nullCheck()) {
         if (this.render.getValue() && this.chinese.getValue() && this.chineseProgress.getValue()) {
            if (MatrixCapture.projection != null) {
               class_332 graphics = event.getContext();
               long now = System.currentTimeMillis();

               record TextJob(double distSq, Runnable draw) {
               }

               List<TextJob> jobs = new ArrayList<>();

               for (BreakIndicatorsModule.BreakEntry entry : this.breaks.values()) {
                  if ((!this.ignoreFriends.getValue() || !(entry.entity instanceof class_1657 player && Homovore.friendManager.isFriend(player)))
                     && (!entry.isRebreak || this.chineseSecond.getValue())) {
                     class_243 center = entry.pos.method_46558();
                     double distSq = mc.field_1724.method_73189().method_1025(center);
                     jobs.add(new TextJob(distSq, () -> entry.renderText(graphics, center, Math.sqrt(distSq), now)));
                  }
               }

               jobs.sort(Comparator.comparingDouble(TextJob::distSq).reversed());

               for (TextJob job : jobs) {
                  job.draw.run();
               }
            }
         }
      }
   }

   private static Color fade(Color from, Color to, double t) {
      double p = Math.clamp(t, 0.0, 1.0);
      return new Color(
         (int)(from.getRed() + (to.getRed() - from.getRed()) * p),
         (int)(from.getGreen() + (to.getGreen() - from.getGreen()) * p),
         (int)(from.getBlue() + (to.getBlue() - from.getBlue()) * p)
      );
   }

   private final class BreakEntry {
      private final class_2338 pos;
      private final int entityId;
      private final class_1297 entity;
      private long startMs;
      private boolean isRebreak;
      private boolean held;

      private BreakEntry(class_2338 pos, int entityId, class_1297 entity, long startMs) {
         this.pos = pos;
         this.entityId = entityId;
         this.entity = entity;
         this.startMs = startMs;
      }

      private boolean complete() {
         return this.held || Util.mc.field_1687.method_8320(this.pos).method_26215();
      }

      private double easedScale(long now) {
         double completion = (this.isRebreak
               ? BreakIndicatorsModule.this.rebreakCompletionAmount.getValue()
               : BreakIndicatorsModule.this.completionAmount.getValue())
            .floatValue();
         double raw = this.held ? 1.0 : (completion <= 0.0 ? 1.0 : Math.clamp(this.progress(now) / completion, 0.0, 1.0));
         return Math.clamp(BreakIndicatorsModule.this.chineseEase.getValue().ease(raw), 0.0, 1.0);
      }

      private Color fillColorFor() {
         if (this.isRebreak && BreakIndicatorsModule.this.chineseSecond.getValue()) {
            return BreakIndicatorsModule.this.chineseSecondFill.getValue();
         } else {
            return this.entity instanceof class_1657 player && Homovore.friendManager.isFriend(player)
               ? BreakIndicatorsModule.this.chineseFriendFill.getValue()
               : BreakIndicatorsModule.this.chineseFill.getValue();
         }
      }

      private Color boxColorFor() {
         if (this.isRebreak && BreakIndicatorsModule.this.chineseSecond.getValue()) {
            return BreakIndicatorsModule.this.chineseSecondBox.getValue();
         } else {
            return this.entity instanceof class_1657 player && Homovore.friendManager.isFriend(player)
               ? BreakIndicatorsModule.this.chineseFriendBox.getValue()
               : BreakIndicatorsModule.this.chineseBox.getValue();
         }
      }

      private void renderChinese(Render3DEvent event, long now) {
         if (!this.isRebreak || BreakIndicatorsModule.this.chineseSecond.getValue()) {
            double size = 0.5 * (1.0 - this.easedScale(now));
            class_238 box = new class_238(this.pos).method_1011(size);
            RenderUtil.drawBoxFilled(event.getMatrix(), box, this.fillColorFor());
            RenderUtil.drawBox(event.getMatrix(), box, this.boxColorFor(), BreakIndicatorsModule.this.lineWidth.getValue());
         }
      }

      private void renderText(class_332 graphics, class_243 center, double dist, long now) {
         float[] screen = MatrixCapture.worldToScreen(center.field_1352, center.field_1351, center.field_1350);
         if (screen != null) {
            String name = this.entity != null ? this.entity.method_5477().getString() : "?";
            String status;
            Color statusColor;
            if (this.complete()) {
               status = "Broke";
               statusColor = Util.mc.field_1687.method_8320(this.pos).method_26215()
                  ? BreakIndicatorsModule.PROGRESS_END
                  : BreakIndicatorsModule.PROGRESS_START;
            } else {
               double p = Math.clamp(this.progress(now), 0.0, 1.0);
               status = String.format("%.1f", p * 100.0);
               statusColor = BreakIndicatorsModule.fade(BreakIndicatorsModule.PROGRESS_START, BreakIndicatorsModule.PROGRESS_END, p);
            }

            if (this.isRebreak && BreakIndicatorsModule.this.chineseSecond.getValue()) {
               status = "Double";
               statusColor = BreakIndicatorsModule.DOUBLE_COLOR;
            }

            float s = (float)Math.max(
               BreakIndicatorsModule.this.chineseMinTextScale.getValue().floatValue(),
               BreakIndicatorsModule.this.chineseTextScale.getValue().floatValue() * 8.0 / (dist + 8.0)
            );
            int lineH = 9;
            graphics.method_51448().pushMatrix();
            graphics.method_51448().translate(screen[0], screen[1]);
            graphics.method_51448().scale(s, s);
            graphics.method_25303(Util.mc.field_1772, name, -Util.mc.field_1772.method_1727(name) / 2, -lineH - 1, -1);
            graphics.method_25303(Util.mc.field_1772, status, -Util.mc.field_1772.method_1727(status) / 2, 1, statusColor.getRGB() | 0xFF000000);
            graphics.method_51448().popMatrix();
         }
      }

      private void renderBlock(Render3DEvent event, long now) {
         if (BreakIndicatorsModule.this.chinese.getValue()) {
            this.renderChinese(event, now);
         } else {
            class_2680 state = Util.mc.field_1687.method_8320(this.pos);
            class_265 shape = state.method_26218(Util.mc.field_1687, this.pos);
            if (shape.method_1110()) {
               RenderUtil.drawBoxFilled(event.getMatrix(), this.pos, BreakIndicatorsModule.this.sideColor.getValue());
               RenderUtil.drawBox(event.getMatrix(), this.pos, BreakIndicatorsModule.this.lineColor.getValue(), BreakIndicatorsModule.this.lineWidth.getValue());
            } else {
               class_238 orig = shape.method_1107();
               double completion = (this.isRebreak
                     ? BreakIndicatorsModule.this.rebreakCompletionAmount.getValue()
                     : BreakIndicatorsModule.this.completionAmount.getValue())
                  .floatValue();
               double scale = this.held ? 1.0 : (completion <= 0.0 ? 1.0 : Math.clamp(this.progress(now) / completion, 0.0, 1.0));
               double centerX = (orig.field_1323 + orig.field_1320) * 0.5;
               double centerY = (orig.field_1322 + orig.field_1325) * 0.5;
               double centerZ = (orig.field_1321 + orig.field_1324) * 0.5;
               double halfX = orig.method_17939() * scale * 0.5;
               double halfY = orig.method_17940() * scale * 0.5;
               double halfZ = orig.method_17941() * scale * 0.5;
               double x1 = this.pos.method_10263() + centerX - halfX;
               double y1 = this.pos.method_10264() + centerY - halfY;
               double z1 = this.pos.method_10260() + centerZ - halfZ;
               double x2 = this.pos.method_10263() + centerX + halfX;
               double y2 = this.pos.method_10264() + centerY + halfY;
               double z2 = this.pos.method_10260() + centerZ + halfZ;
               class_238 renderBox = new class_238(x1, y1, z1, x2, y2, z2);
               RenderUtil.drawBoxFilled(event.getMatrix(), renderBox, BreakIndicatorsModule.this.sideColor.getValue());
               RenderUtil.drawBox(
                  event.getMatrix(), renderBox, BreakIndicatorsModule.this.lineColor.getValue(), BreakIndicatorsModule.this.lineWidth.getValue()
               );
            }
         }
      }

      private double progress(long now) {
         return (now - this.startMs) / 2500.0;
      }
   }

   public record BreakInfo(class_2338 pos, class_1657 player) {
   }

   public enum Easing {
      Linear,
      SineInOut,
      QuadInOut,
      CubicInOut,
      ExpoOut,
      BackOut;

      public double ease(double t) {
         return switch (this) {
            case Linear -> t;
            case SineInOut -> -(Math.cos(Math.PI * t) - 1.0) / 2.0;
            case QuadInOut -> t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2.0) / 2.0;
            case CubicInOut -> t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
            case ExpoOut -> t >= 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t);
            case BackOut -> {
               double c1 = 1.70158;
               double c3 = c1 + 1.0;
               yield 1.0 + c3 * Math.pow(t - 1.0, 3.0) + c1 * Math.pow(t - 1.0, 2.0);
            }
         };
      }
   }
}
