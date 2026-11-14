package dev.leonetic.gui;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.impl.HudModule;
import dev.leonetic.feature.module.impl.hud.HudPosition;
import dev.leonetic.feature.module.impl.hud.StackedElement;
import dev.leonetic.render.Anim;
import dev.leonetic.render.Render2D;
import dev.leonetic.render.font.FontEngine;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_437;

public final class HudEditorScreen extends class_437 {
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
      super(class_2561.method_43470("HUD Editor"));
      this.hud = hud;
   }

   protected void method_25426() {
      this.openTime = System.currentTimeMillis();
      this.closing = false;
      this.alpha = 0.0F;
      this.dragging = null;
      this.hud.setEditing(true);
   }

   public void method_25419() {
      if (!this.closing) {
         this.closing = true;
         this.closeTime = System.currentTimeMillis();
         this.dragging = null;
         Homovore2Client.config().save();
      }
   }

   public void method_25432() {
      this.hud.setEditing(false);
   }

   public boolean method_25421() {
      return false;
   }

   public void method_25420(class_332 graphics, int mouseX, int mouseY, float delta) {
   }

   public void method_25394(class_332 graphics, int mouseX, int mouseY, float delta) {
      Anim.beginFrame();
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

      int width = graphics.method_51421();
      int height = graphics.method_51443();
      Render2D.Surface previous = Render2D.surface(Render2D.Surface.HUD);

      try {
         this.placements = this.hud.layout(graphics);
         Render2D.alpha = this.alpha;
         Render2D.fill(graphics, 0.0F, 0.0F, width, height, DIM);
         this.drawElements(graphics, mouseX, mouseY);
         if (this.dragging != null) {
            this.drawDrag(graphics, mouseX, mouseY, width, height, HudPosition.at(mouseX, mouseY, width, height));
         }
      } finally {
         Render2D.alpha = 1.0F;
         Render2D.surface(previous);
      }

      FontEngine.flush();
   }

   private void drawElements(class_332 graphics, int mouseX, int mouseY) {
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

   private void drawLabel(class_332 graphics, HudModule.Placement placement, StackedElement element) {
      String name = Render2D.trim(element.getDisplayName(), Math.max(8, placement.width() - 2));
      Render2D.text(
         graphics,
         name,
         placement.x() + (placement.width() - Render2D.width(name)) / 2.0F,
         Render2D.centeredTextY(placement.y(), placement.height()),
         element.isEnabled() ? -1 : DISABLED_TEXT
      );
   }

   private void drawDrag(class_332 graphics, int mouseX, int mouseY, int width, int height, HudPosition target) {
      this.drawInsertLine(graphics, width, height, target, this.insertIndex(target, mouseY));
      float left = mouseX - this.dragOffsetX;
      float top = mouseY - this.dragOffsetY;
      Color accent = Theme.categoryColor(this.hud.getCategory(), top);
      Render2D.fill(graphics, left, top, left + this.dragWidth, top + this.dragHeight, Theme.withAlpha(accent, 170).getRGB());
      Render2D.outline(graphics, left, top, left + this.dragWidth, top + this.dragHeight, -1, 1.0F);
      String name = Render2D.trim(this.dragging.getDisplayName(), Math.max(8, this.dragWidth - 2));
      Render2D.text(graphics, name, left + (this.dragWidth - Render2D.width(name)) / 2.0F, Render2D.centeredTextY(top, this.dragHeight), -1);
   }

   private void drawInsertLine(class_332 graphics, int width, int height, HudPosition target, int index) {
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

   public boolean method_25402(class_11909 event, boolean doubled) {
      if (this.closing) {
         return true;
      }

      int mouseX = (int)event.comp_4798();
      int mouseY = (int)event.comp_4799();
      HudModule.Placement hit = this.placementAt(mouseX, mouseY);
      if (hit == null) {
         return true;
      }

      if (event.method_74245() == 0) {
         this.dragging = hit.element();
         this.dragOffsetX = mouseX - hit.x();
         this.dragOffsetY = mouseY - hit.y();
         this.dragWidth = hit.width();
         this.dragHeight = hit.height();
      } else if (event.method_74245() == 1) {
         hit.element().toggle();
      }

      return true;
   }

   public boolean method_25406(class_11909 event) {
      if (!this.closing && this.dragging != null && event.method_74245() == 0) {
         int mouseX = (int)event.comp_4798();
         int mouseY = (int)event.comp_4799();
         HudPosition target = HudPosition.at(mouseX, mouseY, this.field_22789, this.field_22790);
         this.hud.move(this.dragging, target, this.insertIndex(target, mouseY));
         this.dragging = null;
         return true;
      } else {
         return true;
      }
   }

   public boolean method_25404(class_11908 event) {
      return this.closing ? true : super.method_25404(event);
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
