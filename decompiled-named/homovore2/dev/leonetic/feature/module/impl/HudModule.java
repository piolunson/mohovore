package dev.leonetic.feature.module.impl;

import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.module.impl.hud.ActiveModulesElement;
import dev.leonetic.feature.module.impl.hud.AnchoredElement;
import dev.leonetic.feature.module.impl.hud.ArmorElement;
import dev.leonetic.feature.module.impl.hud.CoordinatesElement;
import dev.leonetic.feature.module.impl.hud.CountsElement;
import dev.leonetic.feature.module.impl.hud.HudElement;
import dev.leonetic.feature.module.impl.hud.HudElementSetting;
import dev.leonetic.feature.module.impl.hud.HudPosition;
import dev.leonetic.feature.module.impl.hud.PacketLimitsElement;
import dev.leonetic.feature.module.impl.hud.PingElement;
import dev.leonetic.feature.module.impl.hud.PotIndicatorElement;
import dev.leonetic.feature.module.impl.hud.RadarElement;
import dev.leonetic.feature.module.impl.hud.SpeedElement;
import dev.leonetic.feature.module.impl.hud.StackedElement;
import dev.leonetic.feature.module.impl.hud.TotemsElement;
import dev.leonetic.gui.HudEditorScreen;
import dev.leonetic.render.Render2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

public final class HudModule extends Module {
   public static final int GROUP_GAP = 2;
   private final List<HudElement> elements = new ArrayList<>();
   private final List<StackedElement> stacked = new ArrayList<>();
   private final List<AnchoredElement> anchored = new ArrayList<>();
   private boolean editing;

   public HudModule() {
      super("Hud", Category.RENDER, "Static-position HUD elements.");
      this.button("Edit Layout", () -> mc.setScreen(new HudEditorScreen(this))).page("Elements").describe("Drag the elements between the six position groups.");
      this.add(new ActiveModulesElement());
      this.add(new ArmorElement());
      this.add(new CoordinatesElement());
      this.add(new CountsElement());
      this.add(new PacketLimitsElement());
      this.add(new PingElement());
      this.add(new PotIndicatorElement());
      this.add(new RadarElement());
      this.add(new SpeedElement());
      this.add(new TotemsElement());
   }

   private void add(HudElement element) {
      this.elements.add(element);
      if (element instanceof StackedElement element0) {
         this.stacked.add(element0);
      }

      if (element instanceof AnchoredElement element0) {
         this.anchored.add(element0);
      }

      this.register(new HudElementSetting(element));
      element.attachTo(this);
   }

   public List<HudElement> getElements() {
      return this.elements;
   }

   public List<StackedElement> getStackedElements() {
      return this.stacked;
   }

   @Override
   public void onRender2D(DrawContext graphics, float partialTick) {
      if (mc.player != null && mc.world != null) {
         Render2D.Surface previous = Render2D.surface(Render2D.Surface.HUD);

         try {
            for (AnchoredElement element : this.anchored) {
               if (element.isEnabled()) {
                  element.render(graphics, this);
               }
            }

            for (HudModule.Placement placement : this.layout(graphics)) {
               if (placement.element().isEnabled()) {
                  placement.element().render(graphics, this, placement.x(), placement.y(), placement.width());
               }
            }
         } finally {
            Render2D.surface(previous);
         }
      }
   }

   public boolean isEditing() {
      return this.editing;
   }

   public void setEditing(boolean editing) {
      this.editing = editing;
   }

   public List<HudModule.Placement> layout(DrawContext graphics) {
      return this.layout(graphics.getScaledWindowWidth(), graphics.getScaledWindowHeight(), HudElement.bottomAnchor(graphics));
   }

   public List<HudModule.Placement> layout(int screenWidth, int screenHeight, int bottom) {
      List<HudModule.Placement> placements = new ArrayList<>();
      int groupGap = Render2D.usesCustomFont() ? 0 : 2;

      for (HudPosition group : HudPosition.values()) {
         List<HudModule.Placement> column = new ArrayList<>();
         int total = 0;

         for (StackedElement element : this.membersOf(group)) {
            int width = element.width();
            int height = element.height();
            if (this.editing) {
               width = Math.max(width, element.placeholderWidth());
               height = Math.max(height, element.placeholderHeight());
            }

            if (width > 0 && height > 0) {
               column.add(new HudModule.Placement(element, 0, 0, width, height));
               total += height + groupGap;
            }
         }

         if (!column.isEmpty()) {
            total -= groupGap;

            int top = switch (group.row()) {
               case TOP -> 2;
               case CENTER -> Math.max(2, (screenHeight - total) / 2);
               case BOTTOM -> Math.max(2, bottom - 2 - total);
            };

            for (HudModule.Placement sized : column) {
               int left = group.isLeft() ? 2 : screenWidth - 2 - sized.width();
               placements.add(new HudModule.Placement(sized.element(), left, top, sized.width(), sized.height()));
               top += sized.height() + groupGap;
            }
         }
      }

      return placements;
   }

   public List<StackedElement> membersOf(HudPosition group) {
      List<StackedElement> members = new ArrayList<>();

      for (StackedElement element : this.stacked) {
         if (element.position() == group && (this.editing || element.isEnabled())) {
            members.add(element);
         }
      }

      members.sort(Comparator.comparingInt(StackedElement::order));
      return members;
   }

   public void move(StackedElement element, HudPosition group, int index) {
      HudPosition from = element.position();
      List<StackedElement> target = this.membersOf(group);
      target.remove(element);
      index = Math.max(0, Math.min(index, target.size()));
      target.add(index, element);
      if (element.position() != group) {
         element.setPosition(group);
      }

      renumber(target);
      if (from != group) {
         renumber(this.membersOf(from));
      }
   }

   private static void renumber(List<StackedElement> members) {
      for (int i = 0; i < members.size(); i++) {
         members.get(i).setOrder(i);
      }
   }

   public record Placement(StackedElement element, int x, int y, int width, int height) {
      public boolean contains(int mouseX, int mouseY) {
         return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
      }

      public int centerY() {
         return this.y + this.height / 2;
      }
   }
}
