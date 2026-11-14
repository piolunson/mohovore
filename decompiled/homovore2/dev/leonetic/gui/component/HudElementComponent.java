package dev.leonetic.gui.component;

import dev.leonetic.feature.module.impl.hud.HudElement;
import dev.leonetic.feature.module.impl.hud.HudElementSetting;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.gui.Panel;
import dev.leonetic.gui.Theme;
import dev.leonetic.render.Anim;
import dev.leonetic.render.Render2D;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.class_332;

public class HudElementComponent extends SettingComponent<Boolean> {
   private static final float OPEN_HALF_LIFE = 0.05F;
   private static final int CHILD_INDENT = 5;
   private final HudElement element;
   private final List<SettingComponent<?>> children = new ArrayList<>();
   private boolean expanded;
   private float openAnim;

   public HudElementComponent(HudElementSetting setting) {
      super(setting);
      this.element = setting.getElement();
      this.height = 12;

      for (Setting<?> child : this.element.getGuiSettings()) {
         SettingComponent<?> component = SettingComponent.of(child);
         if (component != null) {
            this.children.add(component);
         }
      }
   }

   public HudElement getElement() {
      return this.element;
   }

   @Override
   public void update() {
      super.update();
      this.children.forEach(Component::update);
      if (this.children.isEmpty()) {
         this.expanded = false;
      }
   }

   @Override
   public float height() {
      return 12 + (int)Math.ceil(this.openAnim * this.trayHeight());
   }

   @Override
   public int rowHeight() {
      return 12;
   }

   @Override
   public void render(class_332 graphics, int mouseX, int mouseY, float delta) {
      this.openAnim = Anim.towards(this.openAnim, this.expanded ? 1.0F : 0.0F, 0.05F, 0.002F);
      boolean hovering = this.isHovered(mouseX, mouseY);
      boolean on = this.setting.getValue();
      this.hoverWeight(hovering, mouseX);
      Color accent = Panel.currentAccent();
      if (on) {
         Render2D.fill(graphics, this.x, this.y, this.x + this.width, this.y + 12.0F, accent.getRGB());
      }

      this.hover.fill(graphics, this.x, this.y, this.x + this.width, this.y + 12.0F, on ? Theme.HOVER : Theme.withAlpha(accent, 60).getRGB());
      float textY = Render2D.centeredTextY(this.y, 12.0F);
      this.drawScrollableString(graphics, this.element.getDisplayName(), this.x + 2.0F, textY, on ? -1 : -1644050, Math.max(0, this.width - 12), hovering);
      if (!this.children.isEmpty()) {
         Render2D.chevron(graphics, this.x + this.width - 5.0F, this.y + 6.0F, 2.0F, this.openAnim, on ? -1 : -1644050);
      }

      float revealed = this.openAnim * this.trayHeight();
      if (revealed <= 0.5F) {
         this.children.forEach(Component::decayHover);
      } else {
         float trayTop = this.y + 12.0F;
         boolean animating = this.openAnim != (this.expanded ? 1.0F : 0.0F);
         if (animating) {
            Render2D.scissor(graphics, this.x, trayTop, this.x + this.width, trayTop + revealed);
         }

         float childX = this.x + 5.0F;
         int childWidth = Math.max(0, this.width - 5);
         float offset = 0.0F;

         for (SettingComponent<?> child : this.children) {
            if (child.isHidden()) {
               child.decayHover();
            } else {
               child.setBounds(childX, trayTop + offset, childWidth);
               Panel.setCurrentAccent(Theme.moduleColor(Panel.currentCategory(), trayTop + offset));
               child.render(graphics, mouseX, mouseY, delta);
               offset += child.height();
            }
         }

         Panel.setCurrentAccent(accent);
         if (animating) {
            Render2D.unscissor(graphics);
         }
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int button) {
      if (this.isHovered(mouseX, mouseY)) {
         if (button == 0) {
            this.setting.setValue(!this.setting.getValue());
            click();
         } else if (button == 1 && !this.children.isEmpty()) {
            this.expanded = !this.expanded;
            click();
         }
      } else if (this.expanded) {
         this.forEachChild(child -> child.mouseClicked(mouseX, mouseY, button));
      }
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int button) {
      if (this.expanded) {
         this.forEachChild(child -> child.mouseReleased(mouseX, mouseY, button));
      }
   }

   @Override
   public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
      if (!this.expanded) {
         return false;
      }

      for (SettingComponent<?> child : this.children) {
         if (!child.isHidden() && child.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void keyTyped(String typed, int modifiers) {
      if (this.expanded) {
         this.forEachChild(child -> child.keyTyped(typed, modifiers));
      }
   }

   @Override
   public void keyPressed(int key) {
      if (this.expanded) {
         this.forEachChild(child -> child.keyPressed(key));
      }
   }

   @Override
   public void keyActivate() {
      this.setting.setValue(!this.setting.getValue());
      click();
   }

   @Override
   public void decayHover() {
      super.decayHover();
      this.children.forEach(Component::decayHover);
   }

   private void forEachChild(Consumer<SettingComponent<?>> action) {
      for (SettingComponent<?> child : this.children) {
         if (!child.isHidden()) {
            action.accept(child);
         }
      }
   }

   private float trayHeight() {
      float total = 0.0F;

      for (SettingComponent<?> child : this.children) {
         if (!child.isHidden()) {
            total += child.height();
         }
      }

      return total;
   }
}
