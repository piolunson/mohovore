package dev.piolunson.v2.gui;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.module.impl.HudModule;
import dev.piolunson.v2.feature.module.impl.hud.HudPosition;
import dev.piolunson.v2.feature.module.impl.hud.StackedElement;
import dev.piolunson.v2.render.Anim;
import dev.piolunson.v2.render.Render2D;
import dev.piolunson.v2.render.font.FontEngine;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public final class HudEditorScreen extends Screen {
   private static final long FADE_DURATION = 150L;
   private static final int DIM = new Color(0, 0, 0, 90).getRGB();
   private static final int BOX_OUTLINE = new Color(255, 255, 255, 70).getRGB();
   private static final int DISABLED_FILL = new Color(0, 0, 0, 120).getRGB();
   private static final int DISABLED_TEXT = new Color(255, 255, 255, 120).getRGB();
   private static final int INSERT_LINE = new Color(120, 200, 255, 230).getRGB();
   private final HudModule hud;
   private List<HudModule.Placement> placements = List.of();
   private StackedElement dragging;
   private int dragOffsetX;
   private int dragOffsetY;
   private int dragWidth;
   private int dragHeight;
   private float alpha;
   private long openTime;
   private boolean closing;
   private long closeTime;

   public HudEditorScreen(HudModule hud) {
      super(Text.literal("HUD Editor"));
      this.hud = hud;
   }

   protected void init() {
      this.openTime = System.currentTimeMillis();
      this.closing = false;
      this.alpha = 0.0F;
      this.dragging = null;
      this.hud.setEditing(true);
   }

   public void close() {
      if (!this.closing) {
         this.closing = true;
         this.closeTime = System.currentTimeMillis();
         this.dragging = null;
         Homovore2Client.config().save();
      }
   }

   public void removed() {
      this.hud.setEditing(false);
   }

   public boolean shouldPause() {
      return false;
   }

   public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
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

      int width = context.getScaledWindowWidth();
      int height = context.getScaledWindowHeight();
      Render2D.Surface previous = Render2D.surface(Render2D.Surface.HUD);

      try {
         this.placements = this.hud.layout(context);
         Render2D.alpha = this.alpha;
         Render2D.fill(context, 0.0F, 0.0F, width, height, DIM);
         this.drawElements(context, mouseX, mouseY);
         if (this.dragging != null) {
            this.drawDrag(context, mouseX, mouseY, width, height, HudPosition.at(mouseX, mouseY, width, height));
         }
      } finally {
         Render2D.alpha = 1.0F;
         Render2D.surface(previous);
      }

      FontEngine.flush();
   }

   private void drawElements(DrawContext graphics, int mouseX, int mouseY) {
      for (HudModule.Placement placement : this.placements) {
         StackedElement element = placement.element();
         if (element != this.dragging) {
            boolean hovered = placement.contains(mouseX, mouseY);
            if (!element.isEnabled()) {
               Render2D.fill(graphics, placement.x(), placement.y(), placement.x() + placement.width(), placement.y() + placement.height(), DISABLED_FILL);
            }

            Render2D.outline(
               graphics, placement.x(), placement.y(), placement.x() + placement.width(), placement.y() + placement.height(), hovered ? -1 : BOX_OUTLINE, 1.0F
            );
            if (!element.isEnabled() || hovered) {
               this.drawLabel(graphics, placement, element);
            }
         }
      }
   }

   private void drawLabel(DrawContext graphics, HudModule.Placement placement, StackedElement element) {
      String name = Render2D.trim(element.getDisplayName(), Math.max(8, placement.width() - 2));
      Render2D.text(
         graphics,
         name,
         placement.x() + (placement.width() - Render2D.width(name)) / 2.0F,
         Render2D.centeredTextY(placement.y(), placement.height()),
         element.isEnabled() ? -1 : DISABLED_TEXT
      );
   }

   private void drawDrag(DrawContext graphics, int mouseX, int mouseY, int width, int height, HudPosition target) {
      this.drawInsertLine(graphics, width, height, target, this.insertIndex(target, mouseY));
      float left = mouseX - this.dragOffsetX;
      float top = mouseY - this.dragOffsetY;
      Color accent = Theme.categoryColor(this.hud.getCategory(), top);
      Render2D.fill(graphics, left, top, left + this.dragWidth, top + this.dragHeight, Theme.withAlpha(accent, 170).getRGB());
      Render2D.outline(graphics, left, top, left + this.dragWidth, top + this.dragHeight, -1, 1.0F);
      String name = Render2D.trim(this.dragging.getDisplayName(), Math.max(8, this.dragWidth - 2));
      Render2D.text(graphics, name, left + (this.dragWidth - Render2D.width(name)) / 2.0F, Render2D.centeredTextY(top, this.dragHeight), -1);
   }

   private void drawInsertLine(DrawContext graphics, int width, int height, HudPosition target, int index) {
      List<HudModule.Placement> members = this.membersOf(target);
      int[] zone = target.zone(width, height);
      float left = target.isLeft() ? zone[0] + 3 : zone[2] - 3 - this.lineWidth(members);
      float y;
      if (members.isEmpty()) {
         y = (zone[1] + zone[3]) / 2.0F;
      } else if (index <= 0) {
         HudModule.Placement first = members.getFirst();
         y = first.y() - 1.0F;
         left = target.isLeft() ? first.x() : first.x() + first.width() - this.lineWidth(members);
      } else {
         HudModule.Placement above = members.get(Math.min(index, members.size()) - 1);
         y = above.y() + above.height() + 1.0F;
         left = target.isLeft() ? above.x() : above.x() + above.width() - this.lineWidth(members);
      }

      Render2D.fill(graphics, left, y - 1.0F, left + this.lineWidth(members), y + 1.0F, INSERT_LINE);
   }

   private int lineWidth(List<HudModule.Placement> members) {
      int widest = 0;

      for (HudModule.Placement placement : members) {
         widest = Math.max(widest, placement.width());
      }

      return Math.max(24, widest);
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      if (this.closing) {
         return true;
      }

      int mouseX = (int)click.x();
      int mouseY = (int)click.y();
      HudModule.Placement hit = this.placementAt(mouseX, mouseY);
      if (hit == null) {
         return true;
      }

      if (click.button() == 0) {
         this.dragging = hit.element();
         this.dragOffsetX = mouseX - hit.x();
         this.dragOffsetY = mouseY - hit.y();
         this.dragWidth = hit.width();
         this.dragHeight = hit.height();
      } else if (click.button() == 1) {
         hit.element().toggle();
      }

      return true;
   }

   public boolean mouseReleased(Click click) {
      if (!this.closing && this.dragging != null && click.button() == 0) {
         int mouseX = (int)click.x();
         int mouseY = (int)click.y();
         HudPosition target = HudPosition.at(mouseX, mouseY, this.width, this.height);
         this.hud.move(this.dragging, target, this.insertIndex(target, mouseY));
         this.dragging = null;
         return true;
      } else {
         return true;
      }
   }

   public boolean keyPressed(KeyInput input) {
      return this.closing ? true : super.keyPressed(input);
   }

   private HudModule.Placement placementAt(int mouseX, int mouseY) {
      for (int i = this.placements.size() - 1; i >= 0; i--) {
         HudModule.Placement placement = this.placements.get(i);
         if (placement.contains(mouseX, mouseY)) {
            return placement;
         }
      }

      return null;
   }

   private List<HudModule.Placement> membersOf(HudPosition group) {
      List<HudModule.Placement> members = new ArrayList<>();

      for (HudModule.Placement placement : this.placements) {
         if (placement.element() != this.dragging && placement.element().position() == group) {
            members.add(placement);
         }
      }

      return members;
   }

   private int insertIndex(HudPosition group, int mouseY) {
      int index = 0;

      for (HudModule.Placement placement : this.membersOf(group)) {
         if (mouseY >= placement.centerY()) {
            index++;
         }
      }

      return index;
   }
}
