package dev.leonetic.features.gui;

import dev.leonetic.Homovore;
import dev.leonetic.features.Feature;
import dev.leonetic.features.gui.items.Item;
import dev.leonetic.features.gui.items.SearchBar;
import dev.leonetic.features.gui.items.TextBox;
import dev.leonetic.features.gui.items.buttons.ModuleButton;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.client.ClickGuiModule;
import dev.leonetic.util.render.GuiFade;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.class_11905;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_437;

public class HomovoreGui extends class_437 {
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
      super(class_2561.method_43470("Homovore"));
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

   public void method_25426() {
      super.method_25426();
      this.openTime = System.currentTimeMillis();
      this.closing = false;
      this.alpha = 0.0F;
      GuiNavigator.reset();
   }

   public void method_25419() {
      if (!this.closing) {
         this.closing = true;
         this.closeTime = System.currentTimeMillis();
         if (this.searchBar != null) {
            this.searchBar.clear();
         }

         GuiNavigator.reset();
      }
   }

   public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
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

      GuiFade.alpha = this.alpha;
      Item.context = context;
      context.method_25294(0, 0, context.method_51421(), context.method_51443(), GuiFade.apply(GuiTheme.SCREEN_DIM));
      float s = getScale();
      int sx = (int)(mouseX / s);
      int sy = (int)(mouseY / s);
      context.method_51448().pushMatrix();
      context.method_51448().scale(s, s);
      this.widgets.forEach(components -> components.drawScreen(context, sx, sy, delta));
      context.method_51448().popMatrix();
      GuiFade.alpha = 1.0F;
   }

   public static float getScale() {
      return 1.0F;
   }

   public boolean method_25402(class_11909 click, boolean doubled) {
      if (this.closing) {
         return true;
      }

      float s = getScale();
      int mx = (int)(click.comp_4798() / s);
      int my = (int)(click.comp_4799() / s);
      this.widgets.forEach(components -> components.mouseClicked(mx, my, click.method_74245()));
      return super.method_25402(click, doubled);
   }

   public boolean method_25406(class_11909 click) {
      if (this.closing) {
         return true;
      }

      float s = getScale();
      int mx = (int)(click.comp_4798() / s);
      int my = (int)(click.comp_4799() / s);
      this.widgets.forEach(components -> components.mouseReleased(mx, my, click.method_74245()));
      return super.method_25406(click);
   }

   public boolean method_25401(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
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

      return super.method_25401(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   public boolean method_25404(class_11908 input) {
      if (this.closing) {
         return true;
      }

      if (TextBox.hasActiveFocus()) {
         TextBox.routeKeyPressed(input.method_74228());
         return true;
      }

      if (GuiNavigator.handleKey(input.method_74228())) {
         return true;
      }

      if (input.method_74228() == ClickGuiModule.getInstance().bind.getValue().getKey()) {
         if (System.currentTimeMillis() - this.openTime > 50L) {
            this.method_25419();
         }

         return true;
      } else {
         this.widgets.forEach(component -> component.onKeyPressed(input.method_74228()));
         return super.method_25404(input);
      }
   }

   public boolean method_25400(class_11905 input) {
      if (this.closing) {
         return true;
      } else if (TextBox.hasActiveFocus()) {
         TextBox.routeKeyTyped(input.method_74226());
         return true;
      } else {
         this.widgets.forEach(component -> component.onKeyTyped(input.method_74226(), input.comp_4794()));
         return super.method_25400(input);
      }
   }

   public boolean method_25421() {
      return false;
   }

   public void method_25420(class_332 context, int mouseX, int mouseY, float delta) {
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
