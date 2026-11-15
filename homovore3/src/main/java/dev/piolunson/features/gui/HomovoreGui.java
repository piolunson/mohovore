package dev.piolunson.features.gui;

import dev.piolunson.Homovore;
import dev.piolunson.features.Feature;
import dev.piolunson.features.gui.items.Item;
import dev.piolunson.features.gui.items.SearchBar;
import dev.piolunson.features.gui.items.TextBox;
import dev.piolunson.features.gui.items.buttons.ModuleButton;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.modules.client.ClickGuiModule;
import dev.piolunson.util.render.GuiFade;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public class HomovoreGui extends Screen {
   private static HomovoreGui INSTANCE = new HomovoreGui();
   private static Color colorClipboard = null;
   private float alpha = 0.0F;
   private long openTime = 0L;
   private boolean closing = false;
   private long closeTime = 0L;
   private static final long FADE_DURATION = 150L;
   private final ArrayList<Widget> widgets = new ArrayList<>();
   private SearchBar searchBar;

   public HomovoreGui() {
      super(Text.literal("Homovore"));
      this.setInstance();
      this.load();
   }

   public static HomovoreGui getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new HomovoreGui();
      }

      return INSTANCE;
   }

   public static HomovoreGui getClickGui() {
      return getInstance();
   }

   private void setInstance() {
      INSTANCE = this;
   }

   private void load() {
      int spacing = 115;
      int x = 6 - spacing;

      for (Module.Category category : Homovore.moduleManager.getCategories()) {
         if (category != Module.Category.HUD && (category != Module.Category.FUNNY || Homovore.commandManager.isFunnyVisible())) {
            Widget panel = new Widget(category.getName(), category, x += spacing, 6, true);
            Homovore.moduleManager.stream().filter(m -> m.getCategory() == category && !m.hidden).map(ModuleButton::new).forEach(panel::addButton);
            this.widgets.add(panel);
         }
      }

      this.widgets.forEach(components -> components.getItems().sort(Comparator.comparing(Feature::getName)));

      for (Widget panel : this.widgets) {
         if (panel.getCategory() == Module.Category.CLIENT) {
            this.searchBar = new SearchBar();
            panel.getItems().add(0, this.searchBar);
            break;
         }
      }
   }

   public void init() {
      super.init();
      this.openTime = System.currentTimeMillis();
      this.closing = false;
      this.alpha = 0.0F;
      GuiNavigator.reset();
   }

   public void close() {
      if (!this.closing) {
         this.closing = true;
         this.closeTime = System.currentTimeMillis();
         if (this.searchBar != null) {
            this.searchBar.clear();
         }

         GuiNavigator.reset();
      }
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
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

      GuiFade.alpha = this.alpha;
      Item.context = context;
      context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), GuiFade.apply(GuiTheme.SCREEN_DIM));
      float s = getScale();
      int sx = (int)(mouseX / s);
      int sy = (int)(mouseY / s);
      context.getMatrices().pushMatrix();
      context.getMatrices().scale(s, s);
      this.widgets.forEach(components -> components.drawScreen(context, sx, sy, deltaTicks));
      context.getMatrices().popMatrix();
      GuiFade.alpha = 1.0F;
   }

   public static float getScale() {
      return 1.0F;
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      if (this.closing) {
         return true;
      }

      float s = getScale();
      int mx = (int)(click.x() / s);
      int my = (int)(click.y() / s);
      this.widgets.forEach(components -> components.mouseClicked(mx, my, click.button()));
      return super.mouseClicked(click, doubled);
   }

   public boolean mouseReleased(Click click) {
      if (this.closing) {
         return true;
      }

      float s = getScale();
      int mx = (int)(click.x() / s);
      int my = (int)(click.y() / s);
      this.widgets.forEach(components -> components.mouseReleased(mx, my, click.button()));
      return super.mouseReleased(click);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (this.closing) {
         return true;
      }

      float s = getScale();
      int mx = (int)(mouseX / s);
      int my = (int)(mouseY / s);

      for (Widget widget : this.widgets) {
         if (widget.isHoveringBody(mx, my)) {
            widget.scroll(verticalAmount);
            return true;
         }
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   public boolean keyPressed(KeyInput input) {
      if (this.closing) {
         return true;
      }

      if (TextBox.hasActiveFocus()) {
         TextBox.routeKeyPressed(input.getKeycode());
         return true;
      }

      if (GuiNavigator.handleKey(input.getKeycode())) {
         return true;
      }

      if (input.getKeycode() == ClickGuiModule.getInstance().bind.getValue().getKey()) {
         if (System.currentTimeMillis() - this.openTime > 50L) {
            this.close();
         }

         return true;
      } else {
         this.widgets.forEach(component -> component.onKeyPressed(input.getKeycode()));
         return super.keyPressed(input);
      }
   }

   public boolean charTyped(CharInput input) {
      if (this.closing) {
         return true;
      } else if (TextBox.hasActiveFocus()) {
         TextBox.routeKeyTyped(input.asString());
         return true;
      } else {
         this.widgets.forEach(component -> component.onKeyTyped(input.asString(), input.modifiers()));
         return super.charTyped(input);
      }
   }

   public boolean shouldPause() {
      return false;
   }

   public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
   }

   public void reload() {
      this.widgets.clear();
      this.load();
   }

   public final ArrayList<Widget> getComponents() {
      return this.widgets;
   }

   public int getTextOffset() {
      return -6;
   }

   public static Color getColorClipboard() {
      return colorClipboard;
   }

   public static void setColorClipboard(Color color) {
      colorClipboard = color;
   }
}
