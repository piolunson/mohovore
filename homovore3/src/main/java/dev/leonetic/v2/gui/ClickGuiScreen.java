package dev.leonetic.v2.gui;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.config.ConfigCode;
import dev.leonetic.v2.feature.command.impl.ConfigCommand;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.module.impl.ClickGuiModule;
import dev.leonetic.v2.gui.component.TextBox;
import dev.leonetic.v2.render.Anim;
import dev.leonetic.v2.render.Render2D;
import dev.leonetic.v2.render.font.FontEngine;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public class ClickGuiScreen extends Screen {
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
      super(Text.literal("Homovore"));
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

   protected void init() {
      this.openTime = System.currentTimeMillis();
      this.closing = false;
      this.alpha = 0.0F;
      SearchOverlay.reset();
      GuiNavigator.reset();
   }

   public void close() {
      if (!this.closing) {
         this.closing = true;
         this.closeTime = System.currentTimeMillis();
         SearchOverlay.close();
         GuiNavigator.reset();
         Homovore2Client.config().save();
      }
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      Anim.beginFrame();
      long now = System.currentTimeMillis();
      if (!this.closing) {
         this.alpha = Math.min(1.0F, (float)(now - this.openTime) / 150.0F);
      } else {
         this.alpha = Math.max(0.0F, 1.0F - (float)(now - this.closeTime) / 150.0F);
         if (this.alpha <= 0.0F) {
            this.client.setScreen(null);
            return;
         }
      }

      Render2D.alpha = this.alpha;
      Render2D.fill(context, 0.0F, 0.0F, context.getScaledWindowWidth(), context.getScaledWindowHeight(), Theme.SCREEN_DIM);
      float scale = getScale();
      int scaledMouseX = (int)(mouseX / scale);
      int scaledMouseY = (int)(mouseY / scale);
      TextBox.routeMouseMoved(scaledMouseX);
      context.getMatrices().pushMatrix();
      context.getMatrices().scale(scale, scale);
      this.panels.forEach(panel -> panel.render(context, scaledMouseX, scaledMouseY, deltaTicks));
      context.getMatrices().popMatrix();
      SearchOverlay.render(context, context.getScaledWindowWidth(), context.getScaledWindowHeight());
      Render2D.alpha = 1.0F;
      FontEngine.flush();
   }

   public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
   }

   public static float getScale() {
      return 1.0F;
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      if (this.closing) {
         return true;
      }

      int mouseX = (int)(click.x() / getScale());
      int mouseY = (int)(click.y() / getScale());
      if (TextBox.routeMouseClicked(mouseX, mouseY, click.button(), doubled)) {
         return true;
      }

      if (this.panels.stream().noneMatch(panel -> panel.isOverHeader(mouseX, mouseY) || panel.isOverBody(mouseX, mouseY))) {
         TextBox.blurActive();
      }

      this.panels.forEach(panel -> panel.mouseClicked(mouseX, mouseY, click.button()));
      return true;
   }

   public boolean mouseReleased(Click click) {
      if (this.closing) {
         return true;
      }

      int mouseX = (int)(click.x() / getScale());
      int mouseY = (int)(click.y() / getScale());
      TextBox.routeMouseReleased();
      this.panels.forEach(panel -> panel.mouseReleased(mouseX, mouseY, click.button()));
      return true;
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (this.closing) {
         return true;
      }

      int x = (int)(mouseX / getScale());
      int y = (int)(mouseY / getScale());

      for (Panel panel : this.panels) {
         if (panel.isOverBody(x, y)) {
            panel.scroll(x, y, verticalAmount);
            return true;
         }
      }

      return true;
   }

   public boolean keyPressed(KeyInput input) {
      if (this.closing) {
         return true;
      }

      int key = input.getKeycode();
      if (this.panels.stream().anyMatch(Panel::isCapturingKey)) {
         this.captureTime = System.currentTimeMillis();
         this.panels.forEach(panel -> panel.keyPressed(key));
         return true;
      }

      if (key != 70 || !input.hasCtrl() && !input.hasCtrlOrCmd()) {
         if (TextBox.hasActiveFocus()) {
            TextBox.routeKeyPressed(input);
            return true;
         }

         if (GuiNavigator.handleKey(key)) {
            return true;
         }

         if (input.isPaste() && this.panels.stream().noneMatch(Panel::isCapturingKey)) {
            String clipboard = this.client.keyboard.getClipboard();
            if (ConfigCode.looksLikeCode(clipboard)) {
               ConfigCommand.importCode(clipboard);
               return true;
            }
         }

         ClickGuiModule module = Homovore2Client.modules().get(ClickGuiModule.class);
         if (key == module.keybind.getValue().getKey() && System.currentTimeMillis() - this.openTime > 50L) {
            this.close();
            return true;
         } else {
            this.panels.forEach(panel -> panel.keyPressed(key));
            return super.keyPressed(input);
         }
      } else {
         SearchOverlay.toggle();
         return true;
      }
   }

   public boolean charTyped(CharInput input) {
      if (this.closing) {
         return true;
      } else if (System.currentTimeMillis() - this.captureTime < 80L) {
         return true;
      } else if (SearchOverlay.swallowToggleChar(input.asString())) {
         return true;
      } else if (TextBox.hasActiveFocus()) {
         TextBox.routeKeyTyped(input.asString());
         return true;
      } else {
         this.panels.forEach(panel -> panel.keyTyped(input.asString(), 0));
         return super.charTyped(input);
      }
   }

   public boolean shouldPause() {
      return false;
   }

   public void removed() {
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
