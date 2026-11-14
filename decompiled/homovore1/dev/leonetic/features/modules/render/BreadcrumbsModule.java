package dev.leonetic.features.modules.render;

import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.RenderUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_1297;
import net.minecraft.class_1684;
import net.minecraft.class_243;
import net.minecraft.class_3532;

public class BreadcrumbsModule extends Module {
   private static final int PEARL_DELAY = 2;
   private static final double Y_OFFSET = 0.1;
   private static final double MIN_SEGMENT_SQ = 1.0E-4;
   public Setting<Boolean> self = this.bool("Self", true);
   public Setting<Boolean> pearls = this.bool("Pearls", true);
   public Setting<Integer> length = this.num("Length", 40, 5, 200);
   public Setting<Float> lineWidth = this.num("LineWidth", 2.0F, 0.5F, 5.0F);
   public Setting<Color> color = this.color("Color", 255, 255, 255, 200);
   public Setting<Boolean> fade = this.bool("Fade", true);
   public Setting<Float> fadeStart = this.num("FadeStart", 0.5F, 0.0F, 1.0F);
   private final List<BreadcrumbsModule.Breadcrumb> selfCrumbs = new ArrayList<>();
   private final List<BreadcrumbsModule.Breadcrumb> pearlCrumbs = new ArrayList<>();
   private final Map<Integer, class_243> lastPearlSample = new HashMap<>();
   private class_243 lastPlayerSample;

   public BreadcrumbsModule() {
      super("Breadcrumbs", "Shows movement trails for self and pearls.", Module.Category.RENDER);
      this.fadeStart.setVisibility(v -> this.fade.getValue());
   }

   @Override
   public void onEnable() {
      this.reset();
   }

   @Override
   public void onDisable() {
      this.reset();
   }

   @Override
   public void reset() {
      this.selfCrumbs.clear();
      this.pearlCrumbs.clear();
      this.lastPearlSample.clear();
      this.lastPlayerSample = null;
   }

   @Override
   public void onTick() {
      if (!nullCheck()) {
         this.fade(this.selfCrumbs);
         this.fade(this.pearlCrumbs);
         if (this.self.getValue()) {
            class_243 pos = new class_243(mc.field_1724.field_6038, mc.field_1724.field_5971 + 0.1, mc.field_1724.field_5989);
            if (this.lastPlayerSample != null && this.lastPlayerSample.method_1025(pos) > 1.0E-4) {
               this.selfCrumbs.add(new BreadcrumbsModule.Breadcrumb(this.lastPlayerSample, pos, this.length.getValue()));
            }

            this.lastPlayerSample = pos;
         } else {
            this.lastPlayerSample = null;
         }

         if (this.pearls.getValue()) {
            Set<Integer> alive = new HashSet<>();

            for (class_1297 entity : mc.field_1687.method_18112()) {
               if (entity instanceof class_1684 pearl) {
                  int id = pearl.method_5628();
                  alive.add(id);
                  class_243 pos = new class_243(pearl.field_6038, pearl.field_5971, pearl.field_5989);
                  class_243 last = this.lastPearlSample.get(id);
                  if (last != null && pearl.field_6012 > 2 && last.method_1025(pos) > 1.0E-4) {
                     this.pearlCrumbs.add(new BreadcrumbsModule.Breadcrumb(last, pos, this.length.getValue()));
                  }

                  this.lastPearlSample.put(id, pos);
               }
            }

            this.lastPearlSample.keySet().retainAll(alive);
         } else {
            this.lastPearlSample.clear();
         }
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (!nullCheck()) {
         Color base = this.color.getValue();
         float partial = event.getDelta();
         float width = this.lineWidth.getValue();

         for (BreadcrumbsModule.Breadcrumb crumb : this.selfCrumbs) {
            RenderUtil.drawLine(crumb.from, crumb.to, this.fade(base, crumb, partial), width);
         }

         if (this.self.getValue() && this.lastPlayerSample != null) {
            class_243 head = interpolate(mc.field_1724, partial).method_1031(0.0, 0.1, 0.0);
            RenderUtil.drawLine(this.lastPlayerSample, head, base, width);
         }

         for (BreadcrumbsModule.Breadcrumb crumb : this.pearlCrumbs) {
            RenderUtil.drawLine(crumb.from, crumb.to, this.fade(base, crumb, partial), width);
         }

         if (this.pearls.getValue()) {
            for (class_1297 entity : mc.field_1687.method_18112()) {
               if (entity instanceof class_1684 pearl && pearl.field_6012 > 2) {
                  class_243 last = this.lastPearlSample.get(pearl.method_5628());
                  if (last != null) {
                     RenderUtil.drawLine(last, interpolate(pearl, partial), base, width);
                  }
               }
            }
         }
      }
   }

   private static class_243 interpolate(class_1297 e, float partial) {
      return new class_243(
         class_3532.method_16436(partial, e.field_6038, e.method_23317()),
         class_3532.method_16436(partial, e.field_5971, e.method_23318()),
         class_3532.method_16436(partial, e.field_5989, e.method_23321())
      );
   }

   private Color fade(Color base, BreadcrumbsModule.Breadcrumb b, float partial) {
      if (!this.fade.getValue()) {
         return base;
      }

      float life = (b.age + partial) / b.lifeTime;
      float start = this.fadeStart.getValue();
      float progress = (life - start) / (1.0F - start);
      if (progress < 0.0F) {
         progress = 0.0F;
      }

      if (progress > 1.0F) {
         progress = 1.0F;
      }

      int a = Math.round(base.getAlpha() * (1.0F - progress));
      return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
   }

   private void fade(List<BreadcrumbsModule.Breadcrumb> list) {
      Iterator<BreadcrumbsModule.Breadcrumb> it = list.iterator();

      while (it.hasNext()) {
         BreadcrumbsModule.Breadcrumb b = it.next();
         if (++b.age >= b.lifeTime) {
            it.remove();
         }
      }
   }

   private static class Breadcrumb {
      final class_243 from;
      final class_243 to;
      final int lifeTime;
      int age;

      Breadcrumb(class_243 from, class_243 to, int lifeTime) {
         this.from = from;
         this.to = to;
         this.lifeTime = lifeTime;
      }
   }
}
