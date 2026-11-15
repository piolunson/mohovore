package dev.piolunson.v2.gui.component;

import dev.piolunson.v2.feature.setting.MultiListSetting;
import dev.piolunson.v2.feature.setting.RegistryDataSetting;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.gui.ClickGuiScreen;
import dev.piolunson.v2.gui.Panel;
import dev.piolunson.v2.gui.Theme;
import dev.piolunson.v2.render.Anim;
import dev.piolunson.v2.render.Hover;
import dev.piolunson.v2.render.Marquee;
import dev.piolunson.v2.render.Render2D;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.DrawContext;

public class RegistryComponent<T> extends SettingComponent<Set<T>> {
   private static final int ROW_HEIGHT = 11;
   private static final int SEARCH_HEIGHT = 11;
   private static final int VISIBLE_ROWS = 6;
   private static final int GAP = 2;
   private static final int SCROLLBAR_WIDTH = 2;
   private static final int CHILD_INDENT = 5;
   private static final float OPEN_HALF_LIFE = 0.05F;
   private final MultiListSetting<T> registry;
   private final RegistryDataSetting<T, ?> data;
   private final TextBox search;
   private final List<T> shown = new ArrayList<>();
   private final List<Hover> rowHovers = new ArrayList<>();
   private final List<Marquee> rowMarquees = new ArrayList<>();
   private final Marquee valueMarquee = new Marquee();
   private final List<SettingComponent<?>> children = new ArrayList<>();
   private T configured;
   private String query = "";
   private boolean open;
   private float openAnim;
   private int scrollIndex;
   private float searchTop;
   private float listTop;
   private float listBottom;

   public RegistryComponent(MultiListSetting<T> setting) {
      super(setting);
      this.registry = setting;
      this.data = (RegistryDataSetting<T, ?>)(setting instanceof RegistryDataSetting<?, ?> nested ? nested : null);
      this.height = 12;
      this.search = new TextBox().placeholder("Search").maxLength(48).onChange(value -> {
         this.query = value;
         this.scrollIndex = 0;
         this.refreshOptions();
         this.collapseEntry();
      });
      this.search.onCancel(this::collapse);

      for (int index = 0; index < 6; index++) {
         this.rowHovers.add(new Hover());
         this.rowMarquees.add(new Marquee());
      }
   }

   @Override
   public void update() {
      super.update();
      this.children.forEach(Component::update);
      if (this.open && this.isHidden()) {
         this.collapse();
      }

      if (this.configured != null && this.configuredSlot() < 0) {
         this.collapseEntry();
      }

      this.layoutChildren();
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
   public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
      this.openAnim = Anim.towards(this.openAnim, this.open ? 1.0F : 0.0F, 0.05F);
      boolean hovering = this.isHovered(mouseX, mouseY);
      this.hoverWeight(hovering, mouseX);
      this.hover.fill(graphics, this.x, this.y, this.x + this.width, this.y + 12.0F, Theme.SLIDER_TRACK);
      float textY = Render2D.centeredTextY(this.y, 12.0F);
      String value = this.registry.displayValue();
      int valueSlot = Math.max(0, this.width / 2);
      int valueWidth = Math.min(Render2D.width(value), valueSlot);
      this.valueMarquee.draw(graphics, value, this.x + this.width - valueWidth - 9.0F, textY, -3880238, valueWidth, hovering);
      this.drawScrollableString(graphics, this.registry.getName(), this.x + 2.0F, textY, -1644050, Math.max(0, this.width - valueWidth - 14), hovering);
      Render2D.chevron(graphics, this.x + this.width - 5.0F, this.y + 6.0F, 2.0F, this.openAnim, -1644050);
      float revealed = this.openAnim * this.trayHeight();
      if (revealed <= 0.5F) {
         this.decayRows();
         this.children.forEach(Component::decayHover);
      } else {
         float trayTop = this.y + 12.0F;
         boolean animating = this.openAnim != (this.open ? 1.0F : 0.0F);
         if (animating) {
            Render2D.scissor(graphics, this.x, trayTop, this.x + this.width, trayTop + revealed);
         }

         this.searchTop = trayTop + 2.0F;
         boolean hoveringSearch = this.inArea(mouseX, mouseY, this.x + 2.0F, this.searchTop, this.x + this.width - 2.0F, this.searchTop + 11.0F);
         int searchColor = this.search.isFocused() ? -1 : (hoveringSearch ? -1644050 : -3880238);
         this.search.renderBare(graphics, this.x + 2.0F, this.searchTop, this.x + this.width - 2.0F, this.searchTop + 11.0F, 3.0F, searchColor);
         this.listTop = this.searchTop + 11.0F + 2.0F;
         this.layoutChildren();
         this.listBottom = this.listTop + this.visibleRows() * 11 + this.childrenHeight();
         Render2D.scissor(graphics, this.x, this.listTop, this.x + this.width, this.listBottom);
         this.renderRows(graphics, mouseX, mouseY, Panel.currentAccent());
         this.renderChildren(graphics, mouseX, mouseY, delta);
         Render2D.unscissor(graphics);
         if (animating) {
            Render2D.unscissor(graphics);
         }
      }
   }

   private void renderRows(DrawContext graphics, int mouseX, int mouseY, Color accent) {
      if (this.shown.isEmpty()) {
         this.decayRows();
         this.drawString(graphics, "No matches", this.x + 4.0F, this.listTop + 2.0F, -3880238);
      } else {
         int used = 0;

         for (int slot = 0; slot < this.visibleRows(); slot++) {
            int index = this.scrollIndex + slot;
            if (index >= this.shown.size()) {
               break;
            }

            T entry = this.shown.get(index);
            float top = this.rowTop(slot);
            boolean picked = this.registry.contains(entry);
            boolean hovering = this.inArea(mouseX, mouseY, this.x, top, this.x + this.width, top + 11.0F);
            Hover rowHover = this.rowHovers.get(slot);
            rowHover.update(hovering, mouseX, this.x, this.x + this.width);
            if (picked) {
               Render2D.fill(graphics, this.x, top, this.x + this.width, top + 11.0F, Theme.withAlpha(accent, 150).getRGB());
            } else {
               rowHover.fill(graphics, this.x, top, this.x + this.width, top + 11.0F, Theme.HOVER);
            }

            boolean configurable = this.data != null && picked;
            int reserved = (configurable ? 8 : 0) + (this.hasOverflow() ? 4 : 0);
            this.rowMarquees
               .get(slot)
               .draw(
                  graphics,
                  this.registry.displayName(entry),
                  this.x + 4.0F,
                  top + 1.5F,
                  picked ? -1 : rowHover.mix(-1644050, -1),
                  Math.max(0, this.width - 8 - reserved),
                  hovering
               );
            if (configurable) {
               String mark = entry.equals(this.configured) ? "v" : ">";
               this.drawString(graphics, mark, this.x + this.width - reserved - 3.0F, top + 1.5F, -3880238);
            }

            used++;
         }

         for (int slot = used; slot < 6; slot++) {
            this.rowHovers.get(slot).decay();
            this.rowMarquees.get(slot).reset();
         }

         if (this.hasOverflow()) {
            this.renderScrollbar(graphics, accent);
         }
      }
   }

   private void renderChildren(DrawContext graphics, int mouseX, int mouseY, float delta) {
      if (!this.children.isEmpty()) {
         Color accent = Panel.currentAccent();

         for (SettingComponent<?> child : this.children) {
            if (child.isHidden()) {
               child.decayHover();
            } else {
               child.render(graphics, mouseX, mouseY, delta);
            }
         }

         Panel.setCurrentAccent(accent);
      }
   }

   private void renderScrollbar(DrawContext graphics, Color accent) {
      float track = this.listBottom - this.listTop;
      float thumb = Math.max(6.0F, track * ((float)this.visibleRows() / this.shown.size()));
      float top = this.listTop + (track - thumb) * ((float)this.scrollIndex / this.maxScroll());
      float left = this.x + this.width - 2.0F - 1.0F;
      Render2D.fill(graphics, left, this.listTop, left + 2.0F, this.listBottom, Theme.SLIDER_TRACK);
      Render2D.fill(graphics, left, top, left + 2.0F, Math.min(this.listBottom, top + thumb), Theme.brighten(accent, 0.3F).getRGB());
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int button) {
      if (!this.isHovered(mouseX, mouseY)) {
         if (this.open) {
            for (SettingComponent<?> child : this.children) {
               if (!child.isHidden()) {
                  child.mouseClicked(mouseX, mouseY, button);
               }
            }

            if (this.inArea(mouseX, mouseY, this.x + 2.0F, this.searchTop, this.x + this.width - 2.0F, this.searchTop + 11.0F)) {
               if (!this.search.isFocused()) {
                  this.search.focus();
               }
            } else if (!this.inArea(mouseX, mouseY, this.x, this.listTop, this.x + this.width, this.listBottom)) {
               this.search.unfocus();
            } else {
               int slot = this.slotAt(mouseY);
               if (slot >= 0) {
                  int index = this.scrollIndex + slot;
                  if (index < this.shown.size()) {
                     T entry = this.shown.get(index);
                     if (button == 0) {
                        this.registry.toggle(entry);
                        if (!this.registry.contains(entry) && entry.equals(this.configured)) {
                           this.collapseEntry();
                        }

                        click();
                     } else if (button == 1 && this.data != null) {
                        if (entry.equals(this.configured)) {
                           this.collapseEntry();
                        } else {
                           if (!this.registry.contains(entry)) {
                              this.registry.add(entry);
                           }

                           if (this.registry.contains(entry)) {
                              this.configure(entry);
                           }
                        }

                        click();
                     }
                  }
               }
            }
         }
      } else if (button == 0 || button == 1) {
         this.toggleOpen();
      }
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int button) {
      this.children.forEach(child -> child.mouseReleased(mouseX, mouseY, button));
   }

   @Override
   public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
      if (this.open && this.inArea(mouseX, mouseY, this.x, this.listTop, this.x + this.width, this.listBottom)) {
         for (SettingComponent<?> child : this.children) {
            if (!child.isHidden() && child.mouseScrolled(mouseX, mouseY, amount)) {
               return true;
            }
         }

         if (amount == 0.0) {
            return true;
         }

         this.scrollIndex = Math.max(0, Math.min(this.maxScroll(), this.scrollIndex - (int)Math.signum(amount)));
         if (this.configured != null && this.configuredSlot() < 0) {
            this.collapseEntry();
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public void keyTyped(String typed, int modifiers) {
      this.children.forEach(child -> child.keyTyped(typed, modifiers));
   }

   @Override
   public void keyPressed(int key) {
      this.children.forEach(child -> child.keyPressed(key));
   }

   @Override
   public void keyActivate() {
      this.toggleOpen();
   }

   @Override
   public void decayHover() {
      super.decayHover();
      this.decayRows();
      this.children.forEach(Component::decayHover);
   }

   private void toggleOpen() {
      if (this.open) {
         this.collapse();
      } else {
         this.query = "";
         this.search.setText("");
         this.refreshOptions();
         this.scrollIndex = 0;
         this.open = true;
         this.search.focus();
      }

      click();
   }

   private void collapse() {
      this.open = false;
      this.query = "";
      this.search.setText("");
      this.search.unfocus();
      this.collapseEntry();
   }

   private void refreshOptions() {
      List<T> rest = new ArrayList<>();
      this.shown.clear();
      String needle = this.query.toLowerCase(Locale.ROOT);

      for (T entry : this.registry.getEntries()) {
         if (needle.isEmpty()
            || this.registry.displayName(entry).toLowerCase(Locale.ROOT).contains(needle)
            || this.registry.getKey(entry).toLowerCase(Locale.ROOT).contains(needle)) {
            if (this.registry.contains(entry)) {
               this.shown.add(entry);
            } else {
               rest.add(entry);
            }
         }
      }

      this.shown.addAll(rest);
   }

   private void configure(T entry) {
      this.collapseEntry();
      this.configured = entry;

      for (Setting<?> setting : this.data.get(entry).getSettings()) {
         SettingComponent<?> component = SettingComponent.of(setting);
         if (component != null) {
            this.children.add(component);
         }
      }

      this.children.forEach(Component::update);
      this.layoutChildren();
   }

   private void collapseEntry() {
      this.children.clear();
      this.configured = null;
   }

   private void layoutChildren() {
      int slot = this.configuredSlot();
      if (slot >= 0) {
         float top = this.rowTop(slot) + 11.0F;

         for (SettingComponent<?> child : this.children) {
            if (!child.isHidden()) {
               child.setBounds(this.x + 5.0F, top, Math.max(0, this.width - 5));
               top += child.height();
            }
         }
      }
   }

   private int childrenHeight() {
      int total = 0;

      for (SettingComponent<?> child : this.children) {
         if (!child.isHidden()) {
            total += (int)Math.ceil(child.height());
         }
      }

      return total;
   }

   private float trayHeight() {
      return 15 + this.visibleRows() * 11 + this.childrenHeight() + 2;
   }

   private int visibleRows() {
      return Math.max(1, Math.min(6, this.shown.size()));
   }

   private boolean hasOverflow() {
      return this.shown.size() > this.visibleRows();
   }

   private int maxScroll() {
      return Math.max(0, this.shown.size() - 6);
   }

   private int configuredSlot() {
      if (this.configured == null) {
         return -1;
      }

      int slot = this.shown.indexOf(this.configured) - this.scrollIndex;
      return slot >= 0 && slot < this.visibleRows() ? slot : -1;
   }

   private float rowTop(int slot) {
      int configuredSlot = this.configuredSlot();
      return this.listTop + slot * 11 + (configuredSlot >= 0 && slot > configuredSlot ? this.childrenHeight() : 0);
   }

   private int slotAt(int mouseY) {
      for (int slot = 0; slot < this.visibleRows(); slot++) {
         float top = this.rowTop(slot);
         if (mouseY >= top && mouseY < top + 11.0F) {
            return slot;
         }
      }

      return -1;
   }

   private void decayRows() {
      this.rowHovers.forEach(Hover::decay);
      this.rowMarquees.forEach(Marquee::reset);
   }

   private boolean inArea(int mouseX, int mouseY, float left, float top, float right, float bottom) {
      return !ClickGuiScreen.isDraggingPanel() && left <= mouseX && mouseX < right && top <= mouseY && mouseY < bottom;
   }
}
