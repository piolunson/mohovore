package dev.leonetic.features.modules.client;

import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.hud.ActiveModulesHudModule;
import dev.leonetic.features.modules.hud.ArmorHudModule;
import dev.leonetic.features.modules.hud.CoordinatesHudModule;
import dev.leonetic.features.modules.hud.CountsHudModule;
import dev.leonetic.features.modules.hud.NotifierHudModule;
import dev.leonetic.features.modules.hud.PingHudModule;
import dev.leonetic.features.modules.hud.RadarHudModule;
import dev.leonetic.features.modules.hud.SpeedHudModule;
import dev.leonetic.features.modules.hud.TotemsHudModule;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.font.Fonts;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class HudClientModule extends Module {
   private static final List<Class<? extends HudModule>> BOTTOM_STACK = List.of(
      CoordinatesHudModule.class, PingHudModule.class, RadarHudModule.class, ActiveModulesHudModule.class
   );
   private final Map<HudModule, Setting<Boolean>> elements = new LinkedHashMap<>();
   public final Setting<Color> radarEnemyColor = this.color("Radar Enemy Color", 255, 255, 255, 255).setPage("Colors");
   public final Setting<Color> radarFriendColor = this.color("Radar Friend Color", 0, 255, 100, 255).setPage("Colors");
   public final Setting<Color> radarSelfColor = this.color("Radar Self Color", 255, 255, 255, 255).setPage("Colors");
   public final Setting<Color> activeModuleColor = this.color("Active Module Color", 175, 0, 0, 255).setPage("Colors");
   public Setting<HudPosition> coordinatesPosition;
   public Setting<HudPosition> activeModulesPosition;

   public HudClientModule() {
      super("Hud", "Static-position HUD elements", Module.Category.CLIENT);
      this.register(new TotemsHudModule(), true);
      this.register(new ArmorHudModule(), true);
      this.register(new CountsHudModule(), true);
      this.register(new CoordinatesHudModule(), true);
      this.coordinatesPosition = this.mode("Coordinates Position", HudPosition.BOTTOM_RIGHT).setPage("Elements");
      this.register(new PingHudModule(), true);
      this.register(new RadarHudModule(), true);
      this.register(new ActiveModulesHudModule(), true);
      this.activeModulesPosition = this.mode("ActiveModules Position", HudPosition.CENTER_RIGHT).setPage("Elements");
      this.register(new NotifierHudModule(), true);
      this.register(new SpeedHudModule(), false);
   }

   private void register(HudModule element, boolean defaultOn) {
      this.elements.put(element, this.bool(element.getName(), defaultOn).setPage("Elements"));
   }

   public HudPosition positionOf(HudModule element) {
      if (element instanceof CoordinatesHudModule) {
         return this.coordinatesPosition.getValue();
      } else {
         return element instanceof ActiveModulesHudModule ? this.activeModulesPosition.getValue() : HudPosition.BOTTOM_RIGHT;
      }
   }

   private int lineCount(HudModule element) {
      if (element instanceof RadarHudModule radar) {
         return radar.renderedLineCount();
      } else {
         return element instanceof ActiveModulesHudModule active ? active.getEntries().size() : 1;
      }
   }

   public int linesBelow(HudModule element) {
      HudPosition anchor = this.positionOf(element);
      if (!anchor.isBottom()) {
         return 0;
      }

      int lines = 0;

      for (Class<? extends HudModule> type : BOTTOM_STACK) {
         if (type.isInstance(element)) {
            break;
         }

         HudModule other = this.getElement(type);
         if (other != null && this.isElementEnabled(type) && this.positionOf(other) == anchor) {
            lines += this.lineCount(other);
         }
      }

      return lines;
   }

   public <T extends HudModule> T getElement(Class<T> type) {
      for (HudModule element : this.elements.keySet()) {
         if (type.isInstance(element)) {
            return (T)element;
         }
      }

      return null;
   }

   public boolean isElementEnabled(Class<? extends HudModule> type) {
      for (Entry<HudModule, Setting<Boolean>> entry : this.elements.entrySet()) {
         if (type.isInstance(entry.getKey())) {
            return entry.getValue().getValue();
         }
      }

      return false;
   }

   @Override
   public void onRender2D(Render2DEvent event) {
      if (!nullCheck()) {
         if (!mc.options.hudHidden) {
            Fonts.beginHudPass();

            try {
               for (Entry<HudModule, Setting<Boolean>> entry : this.elements.entrySet()) {
                  if (entry.getValue().getValue()) {
                     entry.getKey().render(event);
                  }
               }
            } finally {
               Fonts.endHudPass();
            }
         }
      }
   }
}
