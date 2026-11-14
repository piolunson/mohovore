package dev.leonetic.feature.module.impl.hud;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.module.impl.ClickGuiModule;
import dev.leonetic.feature.module.impl.CrystalPlacerModule;
import dev.leonetic.feature.module.impl.HudModule;
import dev.leonetic.feature.setting.Bind;
import dev.leonetic.feature.setting.MultiListSetting;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.render.Render2D;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.class_332;

public final class ActiveModulesElement extends StackedElement {
   public final Setting<Color> color = this.register(new Setting<>("Color", new Color(175, 0, 0, 255)))
      .describe("Enabled module text outside the Colors theme.");
   public final Setting<Boolean> binds = this.register(new Setting<>("Binds", true)).describe("Writes each module's key after its name.");
   public final MultiListSetting<Module> modules = this.register(
      new MultiListSetting<>("Modules", List.of(), this::availableModules, Module::getDisplayName, Module::getName, this::moduleByName, true)
   );

   public ActiveModulesElement() {
      super("ActiveModules", true, HudPosition.CENTER_RIGHT, 0);
      this.modules.describe("The modules kept in this list, including while they are disabled.");
   }

   @Override
   public int width() {
      int widest = 0;

      for (Module module : this.shown()) {
         widest = Math.max(widest, Render2D.width(module.getDisplayName()) + Render2D.width(this.suffix(module)));
      }

      return widest;
   }

   @Override
   public int height() {
      return this.shown().size() * Render2D.lineHeight();
   }

   @Override
   public void render(class_332 graphics, HudModule hud, int left, int top, int width) {
      int y = top;

      for (Module module : this.shown()) {
         String name = module.getDisplayName();
         String suffix = this.suffix(module);
         int rowColor = this.rowColor(module);
         int x = this.rowLeft(left, width, Render2D.width(name) + Render2D.width(suffix));
         Render2D.text(graphics, name, x, y, rowColor);
         if (!suffix.isEmpty()) {
            Render2D.text(graphics, suffix, x + Render2D.width(name), y, rowColor);
         }

         y += Render2D.lineHeight();
      }
   }

   private String suffix(Module module) {
      StringBuilder suffix = new StringBuilder();
      if (module instanceof CrystalPlacerModule crystalPlacer) {
         suffix.append(" [").append(crystalPlacer.crystalsPerSecond()).append(" CPS]");
      }

      if (this.binds.getValue()) {
         Bind bind = module.keybind.getValue();
         if (!bind.isEmpty()) {
            suffix.append(" [").append(bind).append(']');
         }
      }

      return suffix.toString();
   }

   private List<Module> shown() {
      List<Module> modules = new ArrayList<>();

      for (Module module : Homovore2Client.modules().getModules()) {
         if (this.modules.contains(module)) {
            modules.add(module);
         }
      }

      modules.sort(Comparator.<Module>comparingInt(modulex -> Render2D.width(modulex.getDisplayName()) + Render2D.width(this.suffix(modulex))).reversed());
      return modules;
   }

   private List<Module> availableModules() {
      return Homovore2Client.modules() == null
         ? List.of()
         : Homovore2Client.modules().getModules().stream().filter(module -> !(module instanceof HudModule)).toList();
   }

   private Optional<Module> moduleByName(String name) {
      if (Homovore2Client.modules() == null) {
         return Optional.empty();
      }

      String key = name.toLowerCase(Locale.ROOT);
      return this.availableModules().stream().filter(module -> module.getName().toLowerCase(Locale.ROOT).equals(key)).findFirst();
   }

   private int rowColor(Module module) {
      if (!module.isEnabled()) {
         return -5592406;
      }

      ClickGuiModule clickGui = Homovore2Client.modules().get(ClickGuiModule.class);
      return clickGui != null && clickGui.theme.getValue() == ClickGuiModule.Theme.COLORS
         ? module.getCategory().getColor().getRGB()
         : this.color.getValue().getRGB();
   }
}
