package dev.piolunson.v2.gui.component;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.module.impl.ClickGuiModule;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.gui.GuiNavigator;
import dev.piolunson.v2.gui.Panel;
import dev.piolunson.v2.gui.SearchOverlay;
import dev.piolunson.v2.gui.Theme;
import dev.piolunson.v2.render.Anim;
import dev.piolunson.v2.render.Hover;
import dev.piolunson.v2.render.Render2D;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;

public class ModuleComponent extends Component {
   public static final int SWITCHER_HEIGHT = 10;
   private static final float COLLAPSE_HALF_LIFE = 0.045F;
   private static final int ARROW_HITBOX = 10;
   private static final String DEFAULT_PAGE = "General";
   private final Module module;
   private final List<SettingComponent<?>> settings = new ArrayList<>();
   private String currentPage = "General";
   private boolean expanded;
   private float trayHeight;
   private float arrowProgress;
   private float matchAnim = 1.0F;
   private boolean hoveringPrevious;
   private boolean hoveringNext;
   private final Hover previousHover = new Hover();
   private final Hover nextHover = new Hover();

   public ModuleComponent(Module module) {
      this.module = module;
      this.height = 13;

      for (Setting<?> setting : module.getGuiSettings()) {
         SettingComponent<?> component = SettingComponent.of(setting);
         if (component != null) {
            this.settings.add(component);
         }
      }
   }

   @Override
   public void update() {
      boolean visible = SearchOverlay.matches(this.module.getDisplayName());
      this.matchAnim = Anim.towards(this.matchAnim, visible ? 1.0F : 0.0F, 0.045F);
      if (!visible && this.matchAnim <= 0.0F) {
         this.expanded = false;
      }
   }

   @Override
   public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
      this.settings.forEach(Component::update);
      List<String> pages = this.visiblePages();
      this.currentPage = this.resolvePage(pages);
      boolean hovering = this.isHovered(mouseX, mouseY);
      boolean selected = GuiNavigator.isSelected(this);
      float rowHeight = this.rowHeight();
      boolean collapsing = this.matchAnim < 1.0F;
      if (collapsing) {
         Render2D.scissor(graphics, this.x, this.y, this.x + this.width, this.y + rowHeight);
      }

      this.hoverWeight(hovering, mouseX);
      drawRow(graphics, this.x, this.y, this.x + this.width, this.y + rowHeight, this.module.isEnabled(), this.hover);
      Color accent = Panel.currentAccent();
      if (selected) {
         drawSelectionRing(graphics, this.x, this.y, this.x + this.width, this.y + rowHeight - 1.0F, accent);
      }

      this.drawScrollableString(graphics, this.module.getDisplayName(), this.x + 4.0F, this.y + (rowHeight - 8.0F) / 2.0F, -1, this.width - 4 - 12, hovering);
      float target = this.expanded ? this.totalTrayHeight(pages, this.currentPage) : 0.0F;
      float speed = Homovore2Client.modules().get(ClickGuiModule.class).getExpandSpeed();
      this.trayHeight = this.trayHeight + (target - this.trayHeight) * speed;
      if (Math.abs(this.trayHeight - target) < 0.5F) {
         this.trayHeight = target;
      }

      this.arrowProgress = this.arrowProgress + ((this.expanded ? 1.0F : 0.0F) - this.arrowProgress) * speed;
      if (!this.settings.isEmpty()) {
         Render2D.chevron(graphics, this.x + this.width - 8.0F, this.y + rowHeight / 2.0F, 3.0F, this.arrowProgress, -1);
      }

      if (collapsing) {
         Render2D.unscissor(graphics);
      }

      if (!(this.trayHeight <= 0.5F) && !collapsing) {
         float trayTop = this.y + rowHeight;
         float trayBottom = trayTop + this.trayHeight;
         boolean animating = this.trayHeight != target;
         if (animating) {
            Render2D.scissor(graphics, this.x, trayTop, this.x + this.width, trayBottom);
         }

         float settingX = this.x + 8.0F;
         int settingWidth = this.width - 8 - 2;
         float offset = 0.0F;
         if (pages.size() > 1) {
            this.drawPageSwitcher(graphics, trayTop, mouseX, mouseY, this.currentPage);
            offset += 10.0F;
         } else {
            this.hoveringPrevious = this.hoveringNext = false;
            this.previousHover.decay();
            this.nextHover.decay();
         }

         for (SettingComponent<?> setting : this.settings) {
            if (!setting.isHidden() && this.onPage(setting, this.currentPage)) {
               setting.setBounds(settingX, trayTop + offset, settingWidth);
               Panel.setCurrentAccent(Theme.moduleColor(Panel.currentCategory(), trayTop + offset));
               setting.render(graphics, mouseX, mouseY, delta);
               if (GuiNavigator.isSelected(setting)) {
                  drawSelectionRing(
                     graphics,
                     setting.getX(),
                     setting.getY(),
                     setting.getX() + settingWidth,
                     setting.getY() + setting.rowHeight() - 1.0F,
                     Panel.currentAccent()
                  );
               }

               offset += setting.height();
            } else {
               setting.decayHover();
            }
         }

         Panel.setCurrentAccent(accent);
         if (animating) {
            Render2D.unscissor(graphics);
         }
      } else {
         this.settings.forEach(Component::decayHover);
      }
   }

   public static void drawRow(DrawContext graphics, float x1, float y1, float x2, float y2, boolean enabled, Hover hover) {
      Color accent = Panel.currentAccent();
      if (enabled) {
         Render2D.fill(graphics, x1, y1, x2, y2 - 1.0F, accent.getRGB());
      }

      hover.fill(graphics, x1, y1, x2, y2 - 1.0F, enabled ? Theme.HOVER : Theme.withAlpha(accent, 60).getRGB());
   }

   public static void drawSelectionRing(DrawContext graphics, float x1, float y1, float x2, float y2, Color accent) {
      Render2D.outline(graphics, x1, y1, x2, y2, Theme.withAlpha(Theme.brighten(accent, 0.6F), 230).getRGB(), 1.0F);
   }

   private List<String> visiblePages() {
      LinkedHashSet<String> seen = new LinkedHashSet<>();

      for (SettingComponent<?> setting : this.settings) {
         if (!setting.isHidden()) {
            seen.add(setting.getPage());
         }
      }

      boolean hadDefault = seen.remove("General");
      List<String> pages = new ArrayList<>(seen);
      if (hadDefault) {
         pages.add("General");
      }

      if (pages.isEmpty()) {
         pages.add("General");
      }

      return pages;
   }

   private String resolvePage(List<String> pages) {
      return pages.stream().filter(page -> page.equalsIgnoreCase(this.currentPage)).findFirst().orElse(pages.getFirst());
   }

   private boolean onPage(Component component, String page) {
      return component.getPage().equalsIgnoreCase(page);
   }

   private void forEachActive(String page, Consumer<SettingComponent<?>> action) {
      for (SettingComponent<?> setting : this.settings) {
         if (!setting.isHidden() && this.onPage(setting, page)) {
            action.accept(setting);
         }
      }
   }

   private float totalTrayHeight(List<String> pages, String page) {
      float result = pages.size() > 1 ? 10.0F : 0.0F;

      for (SettingComponent<?> setting : this.settings) {
         if (!setting.isHidden() && this.onPage(setting, page)) {
            result += setting.height();
         }
      }

      return result;
   }

   private void drawPageSwitcher(DrawContext graphics, float top, int mouseX, int mouseY, String page) {
      float left = this.x + 8.0F;
      float right = this.x + this.width - 2.0F;
      float bottom = top + 10.0F;
      this.hoveringPrevious = mouseX >= left && mouseX < left + 10.0F && mouseY >= top && mouseY < bottom;
      this.hoveringNext = mouseX >= right - 10.0F && mouseX < right && mouseY >= top && mouseY < bottom;
      this.previousHover.update(this.hoveringPrevious, mouseX, left, left + 10.0F);
      this.nextHover.update(this.hoveringNext, mouseX, right - 10.0F, right);
      float textY = top + 1.0F;
      this.previousHover.fill(graphics, left, top, left + 10.0F, bottom, Theme.HOVER);
      this.nextHover.fill(graphics, right - 10.0F, top, right, bottom, Theme.HOVER);
      this.drawString(graphics, "<", left + 2.0F, textY, this.previousHover.mix(-3880238, -1644050));
      this.drawString(graphics, ">", right - 6.0F, textY, this.nextHover.mix(-3880238, -1644050));
      this.drawString(graphics, page, this.x + (this.width - Render2D.width(page)) / 2.0F, textY, -3880238);
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int button) {
      if (this.isHovered(mouseX, mouseY)) {
         if (button == 0) {
            this.module.toggle();
            click();
         }

         if (button == 1 && !this.settings.isEmpty()) {
            this.expanded = !this.expanded;
            click();
         }
      }

      if (this.expanded) {
         List<String> pages = this.visiblePages();
         String page = this.resolvePage(pages);
         if (button == 0 && pages.size() > 1) {
            int index = pages.indexOf(page);
            if (this.hoveringPrevious) {
               this.currentPage = pages.get(Math.floorMod(index - 1, pages.size()));
               click();
               return;
            }

            if (this.hoveringNext) {
               this.currentPage = pages.get((index + 1) % pages.size());
               click();
               return;
            }
         }

         this.forEachActive(page, setting -> setting.mouseClicked(mouseX, mouseY, button));
      }
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int button) {
      if (this.expanded) {
         this.forEachActive(this.resolvePage(this.visiblePages()), setting -> setting.mouseReleased(mouseX, mouseY, button));
      }
   }

   @Override
   public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
      if (!this.expanded) {
         return false;
      }

      for (Component setting : this.visibleSettings()) {
         if (setting.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void keyTyped(String typed, int modifiers) {
      if (this.expanded) {
         this.forEachActive(this.resolvePage(this.visiblePages()), setting -> setting.keyTyped(typed, modifiers));
      }
   }

   @Override
   public void keyPressed(int key) {
      if (this.expanded) {
         this.forEachActive(this.resolvePage(this.visiblePages()), setting -> setting.keyPressed(key));
      }
   }

   @Override
   public boolean isHidden() {
      return super.isHidden() || this.matchAnim <= 0.0F;
   }

   @Override
   public float height() {
      return this.rowHeight() + (!(this.matchAnim < 1.0F) && !(this.trayHeight <= 0.0F) ? (int)Math.ceil(this.trayHeight) : 0);
   }

   @Override
   public int rowHeight() {
      return (int)Math.ceil(13.0F * this.matchAnim);
   }

   public boolean isAnimating() {
      return Math.abs(this.trayHeight - (this.expanded ? this.totalTrayHeight(this.visiblePages(), this.resolvePage(this.visiblePages())) : 0.0F)) >= 0.5F;
   }

   public boolean isExpanded() {
      return this.expanded;
   }

   public void toggleExpanded() {
      if (!this.settings.isEmpty()) {
         this.expanded = !this.expanded;
         click();
      }
   }

   public boolean hasMultiplePages() {
      return this.visiblePages().size() > 1;
   }

   public void cyclePage(int direction) {
      List<String> pages = this.visiblePages();
      if (pages.size() > 1) {
         int index = pages.indexOf(this.resolvePage(pages));
         this.currentPage = pages.get(Math.floorMod(index + direction, pages.size()));
         click();
      }
   }

   public List<Component> visibleSettings() {
      List<Component> result = new ArrayList<>();
      if (!this.expanded) {
         return result;
      }

      String page = this.resolvePage(this.visiblePages());

      for (SettingComponent<?> setting : this.settings) {
         if (!setting.isHidden() && this.onPage(setting, page)) {
            result.add(setting);
         }
      }

      return result;
   }

   public Module getModule() {
      return this.module;
   }

   @Override
   public void keyActivate() {
      this.module.toggle();
      click();
   }
}
