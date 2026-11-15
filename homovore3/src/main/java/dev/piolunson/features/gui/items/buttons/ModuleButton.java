package dev.piolunson.features.gui.items.buttons;

import dev.piolunson.features.gui.GuiNavigator;
import dev.piolunson.features.gui.GuiTheme;
import dev.piolunson.features.gui.Widget;
import dev.piolunson.features.gui.items.Item;
import dev.piolunson.features.gui.items.SearchBar;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.modules.client.ClickGuiModule;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.util.render.RenderUtil;
import dev.piolunson.util.render.font.Fonts;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public class ModuleButton extends Button {
   public static final int SWITCHER_HEIGHT = 10;
   private static final int ARROW_HITBOX = 10;
   private static final String DEFAULT_PAGE = "General";
   private final Module module;
   private final List<Item> items = new ArrayList<>();
   private String currentPageName = "General";
   private boolean subOpen;
   private float animatedSubHeight = 0.0F;
   private boolean hoveringPagePrev;
   private boolean hoveringPageNext;

   public ModuleButton(Module module) {
      super(module.getName());
      this.module = module;
      this.buildItems();
   }

   private void buildItems() {
      this.items.clear();
      List<Setting<?>> tail = new ArrayList<>();

      for (Setting<?> setting : this.module.getSettings()) {
         if (setting != this.module.enabled && setting != this.module.displayName) {
            if (setting != this.module.bind && setting != this.module.bindMode) {
               SettingButton<?> btn = SettingButton.create(setting);
               if (btn != null) {
                  this.items.add(btn);
               }
            } else {
               tail.add(setting);
            }
         }
      }

      for (Setting<?> setting : tail) {
         SettingButton<?> btn = SettingButton.create(setting);
         if (btn != null) {
            this.items.add(btn);
         }
      }
   }

   private List<String> visiblePages() {
      LinkedHashSet<String> seen = new LinkedHashSet<>();

      for (Item item : this.items) {
         if (!item.isHidden()) {
            seen.add(item.getPage());
         }
      }

      boolean hadDefault = seen.remove("General");
      List<String> out = new ArrayList<>(seen);
      if (hadDefault) {
         out.add("General");
      }

      if (out.isEmpty()) {
         out.add("General");
      }

      return out;
   }

   private String resolveCurrentPage(List<String> pages) {
      for (String p : pages) {
         if (p.equalsIgnoreCase(this.currentPageName)) {
            return p;
         }
      }

      return pages.get(0);
   }

   private boolean onPage(Item item, String page) {
      return item.getPage().equalsIgnoreCase(page);
   }

   private void forEachActive(String page, Consumer<Item> consumer) {
      for (Item item : this.items) {
         if (!item.isHidden() && this.onPage(item, page)) {
            consumer.accept(item);
         }
      }
   }

   private float getTotalSubHeight(List<String> pages, String page) {
      float h = 0.0F;
      if (pages.size() > 1) {
         h += 10.0F;
      }

      for (Item item : this.items) {
         if (!item.isHidden() && this.onPage(item, page)) {
            h += item.getHeight();
         }
      }

      return h;
   }

   @Override
   public void drawScreen(DrawContext context, int mouseX, int mouseY, float partialTicks) {
      for (Item item : this.items) {
         item.update();
      }

      List<String> pages = this.visiblePages();
      String page = this.resolveCurrentPage(pages);
      this.currentPageName = page;
      boolean hovering = this.isHovering(mouseX, mouseY);
      boolean searching = SearchBar.hasQuery();
      boolean match = searching && SearchBar.matches(this.getName());
      boolean navSelected = GuiNavigator.isSelected(this);
      drawRow(context, this.x, this.y, this.x + this.width, this.y + 13.0F, this.getState(), hovering);
      if (match || navSelected) {
         Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(110, 120, 130);
         float rx2 = this.x + this.width;
         float ry2 = this.y + 13.0F - 1.0F;
         if (!this.getState()) {
            RenderUtil.rect(context, this.x, this.y, rx2, ry2, GuiTheme.withAlpha(accent, 70).getRGB());
         }

         drawSelectionRing(context, this.x, this.y, rx2, ry2, accent);
      }

      int textColor;
      if (searching && !match) {
         textColor = 1442840575;
      } else if (match) {
         textColor = -1;
      } else {
         textColor = this.getState() ? -1 : -3880238;
      }

      this.drawScrollableString(this.getName(), this.x + 4.0F, this.y + 2.5F, textColor, this.width - 16, hovering);
      if (!this.items.isEmpty()) {
         String arrow = this.subOpen ? "-" : "+";
         this.drawString(arrow, this.x + this.width - 8.0F, this.y + 2.5F, textColor);
      }

      float targetSubHeight = this.subOpen ? this.getTotalSubHeight(pages, page) : 0.0F;
      this.animatedSubHeight = this.animatedSubHeight + (targetSubHeight - this.animatedSubHeight) * ClickGuiModule.getInstance().getExpandSpeed();
      if (Math.abs(this.animatedSubHeight - targetSubHeight) < 0.5F) {
         this.animatedSubHeight = targetSubHeight;
      }

      if (this.animatedSubHeight > 0.5F) {
         float trayTop = this.y + 13.0F;
         float trayBottom = trayTop + this.animatedSubHeight;
         boolean animating = Math.abs(this.animatedSubHeight - targetSubHeight) >= 0.5F;
         if (animating) {
            context.enableScissor((int)this.x, (int)trayTop, (int)(this.x + this.width), (int)trayBottom);
         }

         RenderUtil.rect(context, this.x, trayTop, this.x + this.width, trayBottom, GuiTheme.SETTINGS_TRAY_BG);
         Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(110, 120, 130);
         int stripe = GuiTheme.withAlpha(accent, 200).getRGB();
         RenderUtil.rect(context, this.x, trayTop, this.x + 2.0F, trayBottom, stripe);
         float settingX = this.x + 6.0F;
         int settingWidth = this.width - 6 - 2;
         float h = 0.0F;
         if (pages.size() > 1) {
            this.drawPageSwitcher(context, trayTop + h, mouseX, mouseY, page);
            h += 10.0F;
         } else {
            this.hoveringPagePrev = false;
            this.hoveringPageNext = false;
         }

         Color savedAccent = Widget.currentAccent;

         for (Item item : this.items) {
            if (!item.isHidden() && this.onPage(item, page)) {
               item.setLocation(settingX, trayTop + h);
               item.setWidth(settingWidth);
               Widget.currentAccent = GuiTheme.moduleColor(Widget.currentCategory, trayTop + h);
               item.drawScreen(context, mouseX, mouseY, partialTicks);
               if (GuiNavigator.isSelected(item)) {
                  drawSelectionRing(
                     context, item.getX(), item.getY(), item.getX() + settingWidth, item.getY() + item.getRowHeight() - 1.0F, Widget.currentAccent
                  );
               }

               h += item.getHeight();
            }
         }

         Widget.currentAccent = savedAccent;
         if (animating) {
            context.disableScissor();
         }
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      if (!this.items.isEmpty()) {
         if (mouseButton == 1 && this.isHovering(mouseX, mouseY)) {
            this.subOpen = !this.subOpen;
            mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         }

         if (this.subOpen) {
            List<String> pages = this.visiblePages();
            String page = this.resolveCurrentPage(pages);
            if (mouseButton == 0 && pages.size() > 1) {
               int idx = pages.indexOf(page);
               if (this.hoveringPagePrev) {
                  this.currentPageName = pages.get((idx - 1 + pages.size()) % pages.size());
                  mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                  return;
               }

               if (this.hoveringPageNext) {
                  this.currentPageName = pages.get((idx + 1) % pages.size());
                  mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                  return;
               }
            }

            this.forEachActive(page, item -> item.mouseClicked(mouseX, mouseY, mouseButton));
         }
      }
   }

   private void drawPageSwitcher(DrawContext context, float top, int mouseX, int mouseY, String page) {
      float left = this.x + 6.0F;
      float right = this.x + this.width - 2.0F;
      float bottom = top + 10.0F;
      this.hoveringPagePrev = mouseX >= left && mouseX < left + 10.0F && mouseY >= top && mouseY < bottom;
      this.hoveringPageNext = mouseX >= right - 10.0F && mouseX < right && mouseY >= top && mouseY < bottom;
      float textY = top + 1.0F;
      int prevColor = this.hoveringPagePrev ? -1644050 : -3880238;
      int nextColor = this.hoveringPageNext ? -1644050 : -3880238;
      this.drawString("<", left + 2.0F, textY, prevColor);
      this.drawString(">", right - 6.0F, textY, nextColor);
      int tw = Fonts.width(page);
      this.drawString(page, this.x + (this.width - tw) / 2.0F, textY, -3880238);
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
      super.mouseReleased(mouseX, mouseY, releaseButton);
      if (this.subOpen && !this.items.isEmpty()) {
         String page = this.resolveCurrentPage(this.visiblePages());
         this.forEachActive(page, item -> item.mouseReleased(mouseX, mouseY, releaseButton));
      }
   }

   @Override
   public void onKeyTyped(String typedChar, int keyCode) {
      super.onKeyTyped(typedChar, keyCode);
      if (this.subOpen && !this.items.isEmpty()) {
         String page = this.resolveCurrentPage(this.visiblePages());
         this.forEachActive(page, item -> item.onKeyTyped(typedChar, keyCode));
      }
   }

   @Override
   public void onKeyPressed(int key) {
      super.onKeyPressed(key);
      if (this.subOpen && !this.items.isEmpty()) {
         String page = this.resolveCurrentPage(this.visiblePages());
         this.forEachActive(page, item -> item.onKeyPressed(key));
      }
   }

   @Override
   public int getHeight() {
      return this.animatedSubHeight <= 0.0F ? 13 : 13 + (int)Math.ceil(this.animatedSubHeight);
   }

   public boolean isAnimating() {
      float target = this.subOpen ? this.getTotalSubHeight(this.visiblePages(), this.resolveCurrentPage(this.visiblePages())) : 0.0F;
      return Math.abs(this.animatedSubHeight - target) >= 0.5F;
   }

   public Module getModule() {
      return this.module;
   }

   @Override
   public int getRowHeight() {
      return 13;
   }

   public boolean isSubOpen() {
      return this.subOpen;
   }

   public void toggleSubOpen() {
      if (!this.items.isEmpty()) {
         this.subOpen = !this.subOpen;
         mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
      }
   }

   public boolean hasMultiplePages() {
      return this.visiblePages().size() > 1;
   }

   public void cyclePage(int dir) {
      List<String> pages = this.visiblePages();
      if (pages.size() > 1) {
         int idx = pages.indexOf(this.resolveCurrentPage(pages));
         this.currentPageName = pages.get(((idx + dir) % pages.size() + pages.size()) % pages.size());
         mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
      }
   }

   public List<Item> currentPageVisibleSettings() {
      List<Item> out = new ArrayList<>();
      if (!this.subOpen) {
         return out;
      }

      String page = this.resolveCurrentPage(this.visiblePages());

      for (Item item : this.items) {
         if (!item.isHidden() && this.onPage(item, page)) {
            out.add(item);
         }
      }

      return out;
   }

   @Override
   public void toggle() {
      this.module.toggle();
   }

   @Override
   public boolean getState() {
      return this.module.isEnabled();
   }
}
