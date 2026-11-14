package dev.leonetic.gui.component;

import dev.leonetic.feature.setting.ListSetting;
import dev.leonetic.gui.ClickGuiScreen;
import dev.leonetic.gui.Panel;
import dev.leonetic.gui.Theme;
import dev.leonetic.render.Anim;
import dev.leonetic.render.Hover;
import dev.leonetic.render.Marquee;
import dev.leonetic.render.Render2D;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_332;

public class ListComponent extends SettingComponent<String> {
   private static final int ROW_HEIGHT = 11;
   private static final int SEARCH_HEIGHT = 11;
   private static final int VISIBLE_ROWS = 6;
   private static final int GAP = 2;
   private static final int SCROLLBAR_WIDTH = 2;
   private static final float OPEN_HALF_LIFE = 0.05F;
   private final ListSetting list;
   private final TextBox search;
   private final List<String> shown = new ArrayList<>();
   private final List<Hover> rowHovers = new ArrayList<>();
   private final List<Marquee> rowMarquees = new ArrayList<>();
   private final Marquee valueMarquee = new Marquee();
   private String query = "";
   private boolean open;
   private float openAnim;
   private float scroll;
   private float listTop;
   private float listBottom;
   private float searchTop;

   public ListComponent(ListSetting setting) {
      super(setting);
      this.list = setting;
      this.height = 12;
      this.search = new TextBox().placeholder("Search").maxLength(48).onChange(value -> {
         this.query = value;
         this.scroll = 0.0F;
      });
      this.search.onCancel(this::collapse);
   }

   @Override
   public void update() {
      super.update();
      if (this.open && this.isHidden()) {
         this.collapse();
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
      if (this.open || this.openAnim > 0.0F) {
         this.refreshOptions();
      }

      this.openAnim = Anim.towards(this.openAnim, this.open ? 1.0F : 0.0F, 0.05F);
      boolean hovering = this.isHovered(mouseX, mouseY);
      this.hoverWeight(hovering, mouseX);
      this.hover.fill(graphics, this.x, this.y, this.x + this.width, this.y + 12.0F, Theme.SLIDER_TRACK);
      float textY = this.y + 2.0F;
      String value = this.setting.getValue();
      int slot = Math.max(0, this.width / 2);
      int valueWidth = Math.min(Render2D.width(value), slot);
      this.valueMarquee.draw(graphics, value, this.x + this.width - valueWidth - 9.0F, textY, -3880238, valueWidth, hovering);
      this.drawScrollableString(graphics, this.list.getName(), this.x + 2.0F, textY, -1644050, Math.max(0, this.width - valueWidth - 14), hovering);
      Render2D.chevron(graphics, this.x + this.width - 5.0F, this.y + 6.0F, 2.0F, this.openAnim, -1644050);
      float trayHeight = this.openAnim * this.trayHeight();
      if (trayHeight <= 0.5F) {
         this.listTop = this.listBottom = this.searchTop = 0.0F;
         this.rowHovers.forEach(Hover::decay);
         this.rowMarquees.forEach(Marquee::reset);
      } else {
         float trayTop = this.y + 12.0F;
         float trayBottom = trayTop + trayHeight;
         boolean animating = this.openAnim != (this.open ? 1.0F : 0.0F);
         if (animating) {
            Render2D.scissor(graphics, this.x, trayTop, this.x + this.width, trayBottom);
         }

         Color accent = Panel.currentAccent();
         this.searchTop = trayTop + 2.0F;
         boolean hoveringSearch = this.inArea(mouseX, mouseY, this.x + 2.0F, this.searchTop, this.x + this.width - 2.0F, this.searchTop + 11.0F);
         int searchColor = this.search.isFocused() ? -1 : (hoveringSearch ? -1644050 : -3880238);
         this.search.renderBare(graphics, this.x + 2.0F, this.searchTop, this.x + this.width - 2.0F, this.searchTop + 11.0F, 3.0F, searchColor);
         this.listTop = this.searchTop + 11.0F + 2.0F;
         this.listBottom = this.listTop + this.visibleRows() * 11;
         this.scroll = this.clampScroll(this.scroll);
         Render2D.scissor(graphics, this.x, this.listTop, this.x + this.width, this.listBottom);
         this.renderRows(graphics, mouseX, mouseY, accent);
         Render2D.unscissor(graphics);
         if (animating) {
            Render2D.unscissor(graphics);
         }
      }
   }

   private void renderRows(class_332 graphics, int mouseX, int mouseY, Color accent) {
      if (this.shown.isEmpty()) {
         this.decayRowsOutside(0, -1);
         this.drawString(graphics, "No matches", this.x + 4.0F, this.listTop + 2.0F, -3880238);
      } else {
         int first = (int)(this.scroll / 11.0F);
         int last = first - 1;
         int textWidth = this.width - 6 - (this.hasOverflow() ? 4 : 0);

         for (int index = first; index < this.shown.size(); last = index++) {
            float top = this.listTop + index * 11 - this.scroll;
            if (top >= this.listBottom) {
               break;
            }

            String option = this.shown.get(index);
            boolean selected = option.equals(this.setting.getValue());
            boolean hovering = this.inArea(mouseX, mouseY, this.x, top, this.x + this.width, top + 11.0F);
            Hover rowHover = this.rowHover(index);
            rowHover.update(hovering, mouseX, this.x, this.x + this.width);
            if (selected) {
               Render2D.fill(graphics, this.x, top, this.x + this.width, top + 11.0F, Theme.withAlpha(accent, 150).getRGB());
            } else {
               rowHover.fill(graphics, this.x, top, this.x + this.width, top + 11.0F, Theme.HOVER);
            }

            this.rowMarquee(index).draw(graphics, option, this.x + 4.0F, top + 1.5F, selected ? -1 : rowHover.mix(-1644050, -1), textWidth, hovering);
         }

         this.decayRowsOutside(first, last);
         if (this.hasOverflow()) {
            this.renderScrollbar(graphics, accent);
         }
      }
   }

   private Hover rowHover(int index) {
      while (this.rowHovers.size() <= index) {
         this.rowHovers.add(new Hover());
      }

      return this.rowHovers.get(index);
   }

   private Marquee rowMarquee(int index) {
      while (this.rowMarquees.size() <= index) {
         this.rowMarquees.add(new Marquee());
      }

      return this.rowMarquees.get(index);
   }

   private void decayRowsOutside(int first, int last) {
      for (int index = 0; index < this.rowHovers.size(); index++) {
         if (index < first || index > last) {
            this.rowHovers.get(index).decay();
            this.rowMarquee(index).reset();
         }
      }
   }

   private void renderScrollbar(class_332 graphics, Color accent) {
      float track = this.listBottom - this.listTop;
      float content = this.shown.size() * 11.0F;
      float thumb = Math.max(6.0F, track * (track / content));
      float top = this.listTop + (track - thumb) * (this.scroll / Math.max(1.0F, content - track));
      float left = this.x + this.width - 2.0F - 1.0F;
      Render2D.fill(graphics, left, this.listTop, left + 2.0F, this.listBottom, Theme.SLIDER_TRACK);
      Render2D.fill(graphics, left, top, left + 2.0F, top + thumb, Theme.brighten(accent, 0.3F).getRGB());
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int button) {
      if (this.isHovered(mouseX, mouseY)) {
         if (button == 0) {
            this.toggle();
         } else if (button == 1) {
            this.list.cycle(1);
            click();
         }
      } else if (this.open) {
         if (this.inArea(mouseX, mouseY, this.x + 2.0F, this.searchTop, this.x + this.width - 2.0F, this.searchTop + 11.0F)) {
            if (!this.search.isFocused()) {
               this.search.focus();
            }
         } else if (!this.inArea(mouseX, mouseY, this.x, this.listTop, this.x + this.width, this.listBottom)) {
            this.search.unfocus();
         } else if (button == 0) {
            int index = (int)((mouseY - this.listTop + this.scroll) / 11.0F);
            if (index >= 0 && index < this.shown.size()) {
               this.setting.setValue(this.shown.get(index));
               this.collapse();
               click();
            }
         }
      }
   }

   @Override
   public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
      if (this.open && this.inArea(mouseX, mouseY, this.x, this.listTop, this.x + this.width, this.listBottom)) {
         this.scroll = this.clampScroll(this.scroll - (float)amount * 11.0F);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void keyActivate() {
      this.toggle();
   }

   private void toggle() {
      if (this.open) {
         this.collapse();
      } else {
         this.list.refresh();
         this.refreshOptions();
         this.open = true;
         this.scrollSelectionIntoView();
         this.search.setText("");
         this.search.focus();
      }

      click();
   }

   private void collapse() {
      this.open = false;
      this.query = "";
      this.search.setText("");
      this.search.unfocus();
   }

   private void refreshOptions() {
      this.shown.clear();
      String needle = this.query.toLowerCase();

      for (String option : this.list.getOptions()) {
         if (needle.isEmpty() || option.toLowerCase().contains(needle)) {
            this.shown.add(option);
         }
      }
   }

   private void scrollSelectionIntoView() {
      int index = this.shown.indexOf(this.setting.getValue());
      if (index < 0) {
         this.scroll = 0.0F;
      } else {
         this.scroll = this.clampScroll(index * 11 - (this.visibleRows() - 1) * 11 / 2.0F);
      }
   }

   private float clampScroll(float value) {
      float max = Math.max(0.0F, this.shown.size() * 11.0F - this.visibleRows() * 11);
      return Math.max(0.0F, Math.min(value, max));
   }

   private boolean hasOverflow() {
      return this.shown.size() > this.visibleRows();
   }

   private int visibleRows() {
      return Math.max(1, Math.min(6, this.shown.size()));
   }

   private float trayHeight() {
      return 15 + this.visibleRows() * 11 + 2;
   }

   private boolean inArea(int mouseX, int mouseY, float left, float top, float right, float bottom) {
      return !ClickGuiScreen.isDraggingPanel() && left <= mouseX && mouseX < right && top <= mouseY && mouseY < bottom;
   }
}
