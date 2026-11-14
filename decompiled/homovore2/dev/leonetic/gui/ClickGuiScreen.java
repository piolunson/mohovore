package dev.leonetic.gui;

import dev.leonetic.Homovore2Client;
import dev.leonetic.config.ConfigCode;
import dev.leonetic.feature.command.impl.ConfigCommand;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.module.impl.ClickGuiModule;
import dev.leonetic.gui.component.TextBox;
import dev.leonetic.render.Anim;
import dev.leonetic.render.Render2D;
import dev.leonetic.render.font.FontEngine;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.class_11905;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_437;

public class ClickGuiScreen extends class_437 {
   private static final long FADE_DURATION = 150L;
   private static ClickGuiScreen instance;
   private static Color colorClipboard;
   private final List<Panel> panels = new ArrayList<>();
   private float alpha;
   private long openTime;
   private boolean closing;
   private long closeTime;
   private long captureTime;

   private ClickGuiScreen() {
      super(class_2561.method_43470("Homovore"));
      this.buildPanels();
   }

   public static ClickGuiScreen get() {
      if (instance == null) {
         instance = new ClickGuiScreen();
      }

      return instance;
   }

   private void buildPanels() {
      this.panels.clear();
      int spacing = 115;
      int x = 6 - spacing;

      for (Category category : Category.values()) {
         List<Module> modules = new ArrayList<>(Homovore2Client.modules().byCategory(category));
         if (!modules.isEmpty()) {
            modules.sort(Comparator.comparing(Module::getName));
            Panel panel = new Panel(category, modules, x += spacing, 6);
            ClickGuiConfig.apply(panel);
            this.panels.add(panel);
         }
      }
   }

   protected void method_25426() {
      this.openTime = System.currentTimeMillis();
      this.closing = false;
      this.alpha = 0.0F;
      SearchOverlay.reset();
      GuiNavigator.reset();
   }

   public void method_25419() {
      if (!this.closing) {
         this.closing = true;
         this.closeTime = System.currentTimeMillis();
         SearchOverlay.close();
         GuiNavigator.reset();
         Homovore2Client.config().save();
      }
   }

   public void method_25394(class_332 graphics, int mouseX, int mouseY, float delta) {
      Anim.beginFrame();
      long now = System.currentTimeMillis();
      if (!this.closing) {
         this.alpha = Math.min(1.0F, (float)(now - this.openTime) / 150.0F);
      } else {
         this.alpha = Math.max(0.0F, 1.0F - (float)(now - this.closeTime) / 150.0F);
         if (this.alpha <= 0.0F) {
            this.field_22787.method_1507(null);
            return;
         }
      }

      Render2D.alpha = this.alpha;
      Render2D.fill(graphics, 0.0F, 0.0F, graphics.method_51421(), graphics.method_51443(), Theme.SCREEN_DIM);
      float scale = getScale();
      int scaledMouseX = (int)(mouseX / scale);
      int scaledMouseY = (int)(mouseY / scale);
      TextBox.routeMouseMoved(scaledMouseX);
      graphics.method_51448().pushMatrix();
      graphics.method_51448().scale(scale, scale);
      this.panels.forEach(panel -> panel.render(graphics, scaledMouseX, scaledMouseY, delta));
      graphics.method_51448().popMatrix();
      SearchOverlay.render(graphics, graphics.method_51421(), graphics.method_51443());
      Render2D.alpha = 1.0F;
      FontEngine.flush();
   }

   public void method_25420(class_332 graphics, int mouseX, int mouseY, float delta) {
   }

   public static float getScale() {
      return 1.0F;
   }

   public boolean method_25402(class_11909 event, boolean doubled) {
      if (this.closing) {
         return true;
      }

      int mouseX = (int)(event.comp_4798() / getScale());
      int mouseY = (int)(event.comp_4799() / getScale());
      if (TextBox.routeMouseClicked(mouseX, mouseY, event.method_74245(), doubled)) {
         return true;
      }

      if (this.panels.stream().noneMatch(panel -> panel.isOverHeader(mouseX, mouseY) || panel.isOverBody(mouseX, mouseY))) {
         TextBox.blurActive();
      }

      this.panels.forEach(panel -> panel.mouseClicked(mouseX, mouseY, event.method_74245()));
      return true;
   }

   public boolean method_25406(class_11909 event) {
      if (this.closing) {
         return true;
      }

      int mouseX = (int)(event.comp_4798() / getScale());
      int mouseY = (int)(event.comp_4799() / getScale());
      TextBox.routeMouseReleased();
      this.panels.forEach(panel -> panel.mouseReleased(mouseX, mouseY, event.method_74245()));
      return true;
   }

   public boolean method_25401(double mouseX, double mouseY, double horizontal, double vertical) {
      if (this.closing) {
         return true;
      }

      int x = (int)(mouseX / getScale());
      int y = (int)(mouseY / getScale());

      for (Panel panel : this.panels) {
         if (panel.isOverBody(x, y)) {
            panel.scroll(x, y, vertical);
            return true;
         }
      }

      return true;
   }

   public boolean method_25404(class_11908 event) {
      if (this.closing) {
         return true;
      }

      int key = event.method_74228();
      if (this.panels.stream().anyMatch(Panel::isCapturingKey)) {
         this.captureTime = System.currentTimeMillis();
         this.panels.forEach(panel -> panel.keyPressed(key));
         return true;
      }

      if (key != 70 || !event.method_74240() && !event.method_75377()) {
         if (TextBox.hasActiveFocus()) {
            TextBox.routeKeyPressed(event);
            return true;
         }

         if (GuiNavigator.handleKey(key)) {
            return true;
         }

         if (event.method_74243() && this.panels.stream().noneMatch(Panel::isCapturingKey)) {
            String clipboard = this.field_22787.field_1774.method_1460();
            if (ConfigCode.looksLikeCode(clipboard)) {
               ConfigCommand.importCode(clipboard);
               return true;
            }
         }

         ClickGuiModule module = Homovore2Client.modules().get(ClickGuiModule.class);
         if (key == module.keybind.getValue().getKey() && System.currentTimeMillis() - this.openTime > 50L) {
            this.method_25419();
            return true;
         } else {
            this.panels.forEach(panel -> panel.keyPressed(key));
            return super.method_25404(event);
         }
      } else {
         SearchOverlay.toggle();
         return true;
      }
   }

   public boolean method_25400(class_11905 event) {
      if (this.closing) {
         return true;
      } else if (System.currentTimeMillis() - this.captureTime < 80L) {
         return true;
      } else if (SearchOverlay.swallowToggleChar(event.method_74226())) {
         return true;
      } else if (TextBox.hasActiveFocus()) {
         TextBox.routeKeyTyped(event.method_74226());
         return true;
      } else {
         this.panels.forEach(panel -> panel.keyTyped(event.method_74226(), 0));
         return super.method_25400(event);
      }
   }

   public boolean method_25421() {
      return false;
   }

   public void method_25432() {
      TextBox.unfocusAll();
      SearchOverlay.reset();
   }

   public List<Panel> getPanels() {
      return this.panels;
   }

   public static List<Panel> livePanels() {
      return instance == null ? List.of() : instance.panels;
   }

   public static boolean isDraggingPanel() {
      return instance != null && instance.panels.stream().anyMatch(Panel::isDragging);
   }

   public static Color getColorClipboard() {
      return colorClipboard;
   }

   public static void setColorClipboard(Color color) {
      colorClipboard = color;
   }
}
