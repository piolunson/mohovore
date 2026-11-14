package dev.leonetic.features.modules.hud;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.client.HudClientModule;
import dev.leonetic.features.modules.client.HudModule;
import dev.leonetic.features.modules.client.HudPosition;
import dev.leonetic.mixin.client.PlayerTabOverlayAccessor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.class_2561;
import net.minecraft.class_634;
import net.minecraft.class_640;

public class PingHudModule extends HudModule {
   private static final int WHITE = -1;
   private static final int GRAY = -5592406;
   private static final Pattern PING_SUFFIX = Pattern.compile("(?i)(\\d+)\\s*(?:ms)?\\s*ping");
   private static final Pattern PING_PREFIX = Pattern.compile("(?i)ping\\s*(?:[:=-])?\\s*(\\d+)");

   public PingHudModule() {
      super("Ping");
   }

   @Override
   public void render(Render2DEvent event) {
      String label = "Ping ";
      String value = String.valueOf(this.getPing());
      int totalWidth = mc.field_1772.method_1727(label) + mc.field_1772.method_1727(value);
      HudClientModule hud = Homovore.moduleManager.getModuleByClass(HudClientModule.class);
      int linesBelow = hud != null ? hud.linesBelow(this) : 0;
      int rx = this.lineX(HudPosition.BOTTOM_RIGHT, totalWidth);
      int ry = this.blockTop(HudPosition.BOTTOM_RIGHT, 1, linesBelow, 0);
      event.getContext().method_25303(mc.field_1772, label, rx, ry, -1);
      event.getContext().method_25303(mc.field_1772, value, rx + mc.field_1772.method_1727(label), ry, -5592406);
   }

   private int getPing() {
      class_634 conn = mc.method_1562();
      if (conn != null && mc.field_1724 != null) {
         class_640 info = conn.method_2871(mc.field_1724.method_5667());
         int latency = info != null ? info.method_2959() : 0;
         if (latency > 0) {
            return latency;
         }
      }

      Integer parsed = this.parseFromTablist();
      return parsed != null ? Math.max(parsed, 0) : 0;
   }

   private Integer parseFromTablist() {
      if (mc.field_1705 == null) {
         return null;
      } else if (mc.field_1705.method_1750() instanceof PlayerTabOverlayAccessor accessor) {
         Integer fromHeader = this.parse(accessor.homovore$getHeader());
         return fromHeader != null ? fromHeader : this.parse(accessor.homovore$getFooter());
      } else {
         return null;
      }
   }

   private Integer parse(class_2561 component) {
      if (component == null) {
         return null;
      }

      String text = component.getString();
      if (text.isEmpty()) {
         return null;
      }

      Matcher m = PING_SUFFIX.matcher(text);
      if (m.find()) {
         return this.tryInt(m.group(1));
      }

      m = PING_PREFIX.matcher(text);
      return m.find() ? this.tryInt(m.group(1)) : null;
   }

   private Integer tryInt(String s) {
      try {
         return Integer.parseInt(s);
      } catch (NumberFormatException e) {
         return null;
      }
   }
}
