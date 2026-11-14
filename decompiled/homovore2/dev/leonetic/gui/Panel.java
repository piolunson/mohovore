package dev.leonetic.gui;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.module.impl.ClickGuiModule;
import dev.leonetic.gui.component.BindComponent;
import dev.leonetic.gui.component.Component;
import dev.leonetic.gui.component.ModuleComponent;
import dev.leonetic.gui.component.TextBox;
import dev.leonetic.render.Render2D;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_310;
import net.minecraft.class_332;

public class Panel {
   private static Color currentAccent = new Color(70, 75, 82);
   private static Category currentCategory;
   private final Category category;
   private final List<Component> components = new ArrayList<>();
   private int x;
   private int y;
   private int dragX;
   private int dragY;
   private boolean open = true;
   private boolean dragging;
   private float animatedHeight;
   private float scrollOffset;

   public Panel(Category category, List<Module> modules, int x, int y) {
      this.category = category;
      this.x = x;
      this.y = y;
      modules.stream().map(ModuleComponent::new).forEach(this.components::add);
   }

   public void render(class_332 graphics, int mouseX, int mouseY, float delta) {
      this.components.forEach(Component::update);
      if (this.dragging) {
         this.x = this.dragX + mouseX;
         this.y = this.dragY + mouseY;
      }

      int headerBottom = this.y + 13;
      int screenHeight = (int)(class_310.method_1551().method_22683().method_4502() / ClickGuiScreen.getScale());
      int available = Math.max(0, screenHeight - headerBottom - 6);
      float contentHeight = this.contentHeight();
      float target = this.open ? Math.min(contentHeight, available) : 0.0F;
      float speed = Homovore2Client.modules().get(ClickGuiModule.class).getExpandSpeed();
      this.animatedHeight = this.animatedHeight + (target - this.animatedHeight) * speed;
      if (Math.abs(this.animatedHeight - target) < 0.5F) {
         this.animatedHeight = target;
      }

      Color categoryAccent = Theme.categoryColor(this.category, this.y);
      Render2D.fill(graphics, this.x, this.y, this.x + 110, headerBottom, categoryAccent.getRGB());
      String title = Render2D.trim(this.category.getDisplayName(), 89);
      Render2D.text(graphics, title, this.x + 4, Render2D.centeredTextY(this.y, 13.0F), -1);
      CategoryIcon.draw(graphics, this.category, this.x + 110 - 4 - 10, this.y + 1.5F, -1);
      if (this.animatedHeight <= 0.5F) {
         this.components.forEach(Component::decayHover);
      } else {
         int bodyBottom = (int)(headerBottom + this.animatedHeight);
         float maxScroll = Math.max(0.0F, contentHeight - this.animatedHeight);
         this.scrollOffset = Math.max(0.0F, Math.min(this.scrollOffset, maxScroll));
         this.scrollSelectionIntoView(this.animatedHeight, maxScroll);
         Render2D.fill(graphics, this.x, headerBottom, this.x + 110, bodyBottom, Theme.BODY_BG);
         Render2D.scissor(graphics, this.x, headerBottom, this.x + 110, bodyBottom);
         currentCategory = this.category;
         float cursor = headerBottom + 2 - this.scrollOffset;

         for (Component component : this.components) {
            if (component.isHidden()) {
               component.decayHover();
            } else {
               component.setBounds(this.x, cursor, 110);
               currentAccent = Theme.moduleColor(this.category, cursor);
               component.render(graphics, mouseX, mouseY, delta);
               if (GuiNavigator.isSelected(component) && !(component instanceof ModuleComponent)) {
                  ModuleComponent.drawSelectionRing(
                     graphics, this.x, component.getY(), this.x + 110, component.getY() + component.rowHeight() - 1.0F, currentAccent
                  );
               }

               cursor += component.height();
            }
         }

         currentCategory = null;
         Render2D.unscissor(graphics);
         Render2D.outline(graphics, this.x, this.y, this.x + 110, bodyBottom, Theme.withAlpha(categoryAccent, categoryAccent.getAlpha() / 2).getRGB(), 1.0F);
      }
   }

   public void mouseClicked(int mouseX, int mouseY, int button) {
      if (this.isOverHeader(mouseX, mouseY)) {
         TextBox.blurActive();
         if (button == 0) {
            this.dragX = this.x - mouseX;
            this.dragY = this.y - mouseY;
            ClickGuiScreen.get().getPanels().forEach(panel -> panel.dragging = false);
            this.dragging = true;
         } else if (button == 1) {
            this.open = !this.open;
            Panel.ComponentClick.play();
         }
      } else if (this.open) {
         int bodyTop = this.y + 13;
         int bodyBottom = (int)(bodyTop + this.animatedHeight);

         for (Component component : this.components) {
            if (!component.isHidden() && component.getY() + component.height() > bodyTop && component.getY() < bodyBottom) {
               component.mouseClicked(mouseX, mouseY, button);
            }
         }
      }
   }

   public void mouseReleased(int mouseX, int mouseY, int button) {
      if (button == 0) {
         this.dragging = false;
      }

      if (this.open) {
         int bodyTop = this.y + 13;
         int bodyBottom = (int)(bodyTop + this.animatedHeight);

         for (Component component : this.components) {
            if (!component.isHidden() && component.getY() + component.height() > bodyTop && component.getY() < bodyBottom) {
               component.mouseReleased(mouseX, mouseY, button);
            }
         }
      }
   }

   public void keyTyped(String typed, int modifiers) {
      if (this.open) {
         this.components.forEach(c -> c.keyTyped(typed, modifiers));
      }
   }

   public void keyPressed(int key) {
      if (this.open) {
         this.components.forEach(c -> c.keyPressed(key));
      }
   }

   public boolean isCapturingKey() {
      for (Component component : this.components) {
         if (component instanceof ModuleComponent module) {
            for (Component setting : module.visibleSettings()) {
               if (setting instanceof BindComponent bind && bind.isListening()) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public boolean isOverBody(int mouseX, int mouseY) {
      int bodyTop = this.y + 13;
      return this.open
         && this.animatedHeight > 0.0F
         && mouseX >= this.x
         && mouseX <= this.x + 110
         && mouseY >= bodyTop
         && mouseY <= bodyTop + this.animatedHeight;
   }

   public void scroll(int mouseX, int mouseY, double amount) {
      for (Component component : this.components) {
         if (!component.isHidden() && component.mouseScrolled(mouseX, mouseY, amount)) {
            return;
         }
      }

      float max = Math.max(0.0F, this.contentHeight() - this.animatedHeight);
      this.scrollOffset = Math.max(0.0F, Math.min(this.scrollOffset - (float)amount * 12.0F, max));
   }

   private void scrollSelectionIntoView(float visibleHeight, float maxScroll) {
      if (GuiNavigator.isFocusedPanel(this)) {
         Component selected = GuiNavigator.getSelected();
         if (selected != null) {
            Float selectedTop = null;
            int selectedHeight = 0;
            float top = 2.0F;

            for (Component component : this.components) {
               if (!component.isHidden()) {
                  if (component == selected) {
                     selectedTop = top;
                     selectedHeight = component.rowHeight();
                  }

                  if (component instanceof ModuleComponent module && module.isExpanded()) {
                     float settingTop = top + 13.0F + (module.hasMultiplePages() ? 10 : 0);

                     for (Component setting : module.visibleSettings()) {
                        if (setting == selected) {
                           selectedTop = settingTop;
                           selectedHeight = setting.rowHeight();
                        }

                        settingTop += setting.height();
                     }
                  }

                  top += component.height();
               }
            }

            if (selectedTop != null) {
               if (selectedTop < this.scrollOffset) {
                  this.scrollOffset = selectedTop;
               } else if (selectedTop + selectedHeight > this.scrollOffset + visibleHeight) {
                  this.scrollOffset = selectedTop + selectedHeight - visibleHeight;
               }

               this.scrollOffset = Math.max(0.0F, Math.min(this.scrollOffset, maxScroll));
            }
         }
      }
   }

   private float contentHeight() {
      float total = 0.0F;

      for (Component component : this.components) {
         if (!component.isHidden()) {
            total += component.height();
         }
      }

      return total <= 0.0F ? 0.0F : total + 2.0F;
   }

   public boolean isOverHeader(int mouseX, int mouseY) {
      return mouseX >= this.x && mouseX <= this.x + 110 && mouseY >= this.y && mouseY <= this.y + 13;
   }

   public Category getCategory() {
      return this.category;
   }

   public int getX() {
      return this.x;
   }

   public int getY() {
      return this.y;
   }

   public boolean isOpen() {
      return this.open;
   }

   public void setOpen(boolean open) {
      this.open = open;
   }

   public void setPosition(int x, int y) {
      this.x = x;
      this.y = y;
   }

   public List<Component> getComponents() {
      return this.components;
   }

   public boolean isDragging() {
      return this.dragging;
   }

   public boolean isHidden() {
      return false;
   }

   public static Color currentAccent() {
      return currentAccent;
   }

   public static void setCurrentAccent(Color color) {
      currentAccent = color;
   }

   public static Category currentCategory() {
      return currentCategory;
   }

   private static final class ComponentClick extends Component {
      private static void play() {
         click();
      }
   }
}
