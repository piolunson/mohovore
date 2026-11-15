package dev.piolunson.features.modules.hud;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.render.Render2DEvent;
import dev.piolunson.features.modules.client.HudClientModule;
import dev.piolunson.features.modules.client.HudModule;
import dev.piolunson.features.modules.client.HudPosition;
import dev.piolunson.mixin.client.PlayerTabOverlayAccessor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

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
      int totalWidth = mc.textRenderer.getWidth(label) + mc.textRenderer.getWidth(value);
      HudClientModule hud = Homovore.moduleManager.getModuleByClass(HudClientModule.class);
      int linesBelow = hud != null ? hud.linesBelow(this) : 0;
      int rx = this.lineX(HudPosition.BOTTOM_RIGHT, totalWidth);
      int ry = this.blockTop(HudPosition.BOTTOM_RIGHT, 1, linesBelow, 0);
      event.getContext().drawTextWithShadow(mc.textRenderer, label, rx, ry, -1);
      event.getContext().drawTextWithShadow(mc.textRenderer, value, rx + mc.textRenderer.getWidth(label), ry, -5592406);
   }

   private int getPing() {
      ClientPlayNetworkHandler conn = mc.getNetworkHandler();
      if (conn != null && mc.player != null) {
         PlayerListEntry info = conn.getPlayerListEntry(mc.player.getUuid());
         int latency = info != null ? info.getLatency() : 0;
         if (latency > 0) {
            return latency;
         }
      }

      Integer parsed = this.parseFromTablist();
      return parsed != null ? Math.max(parsed, 0) : 0;
   }

   private Integer parseFromTablist() {
      if (mc.inGameHud == null) {
         return null;
      } else if (mc.inGameHud.getPlayerListHud() instanceof PlayerTabOverlayAccessor accessor) {
         Integer fromHeader = this.parse(accessor.homovore$getHeader());
         return fromHeader != null ? fromHeader : this.parse(accessor.homovore$getFooter());
      } else {
         return null;
      }
   }

   private Integer parse(Text component) {
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
