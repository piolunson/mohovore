package dev.piolunson.v2.gui;

import dev.piolunson.v2.gui.component.BindComponent;
import dev.piolunson.v2.gui.component.Component;
import dev.piolunson.v2.gui.component.ModuleComponent;
import java.util.ArrayList;
import java.util.List;

public final class GuiNavigator {
   private static boolean active;
   private static int panelIndex;
   private static Component selected;
   private static BindComponent pendingBind;

   private GuiNavigator() {
   }

   public static void reset() {
      active = false;
      panelIndex = 0;
      selected = null;
      pendingBind = null;
   }

   public static boolean isSelected(Component component) {
      return active && component != null && component == selected;
   }

   public static boolean isFocusedPanel(Panel panel) {
      return active && panel != null && panel == focusedPanel();
   }

   public static Component getSelected() {
      return active ? selected : null;
   }

   public static boolean handleKey(int key) {
      if (pendingBind != null) {
         if (pendingBind.isListening()) {
            pendingBind.keyPressed(key);
            if (!pendingBind.isListening()) {
               pendingBind = null;
            }

            return true;
         }

         pendingBind = null;
      }

      boolean arrow = key == 263 || key == 262 || key == 265 || key == 264;
      if (!active) {
         if (!arrow) {
            return false;
         }

         active = true;
         selectFirst();
         return true;
      } else {
         return switch (key) {
            case 32 -> {
               expand();
               yield true;
            }
            case 256 -> {
               active = false;
               selected = null;
               yield true;
            }
            case 257, 335 -> {
               activate();
               yield true;
            }
            case 262 -> {
               horizontal(1);
               yield true;
            }
            case 263 -> {
               horizontal(-1);
               yield true;
            }
            case 264 -> {
               move(1);
               yield true;
            }
            case 265 -> {
               move(-1);
               yield true;
            }
            default -> false;
         };
      }
   }

   private static void selectFirst() {
      List<Component> rows = flatList();
      selected = rows.isEmpty() ? null : rows.getFirst();
   }

   private static void move(int direction) {
      List<Component> rows = flatList();
      if (rows.isEmpty()) {
         selected = null;
      } else {
         int index = rows.indexOf(selected);
         selected = rows.get(index < 0 ? 0 : Math.max(0, Math.min(rows.size() - 1, index + direction)));
      }
   }

   private static void horizontal(int direction) {
      ModuleComponent owner = owningMultiPageModule();
      if (owner != null) {
         owner.cyclePage(direction);
         List<Component> settings = owner.visibleSettings();
         selected = settings.isEmpty() ? owner : settings.getFirst();
      } else {
         List<Panel> panels = visiblePanels();
         if (!panels.isEmpty()) {
            float targetY = selected == null ? Float.NEGATIVE_INFINITY : selected.getY();
            panelIndex = Math.max(0, Math.min(panels.size() - 1, clampPanelIndex() + direction));
            List<Component> rows = flatList();
            if (rows.isEmpty()) {
               selected = null;
            } else {
               Component best = rows.getFirst();
               float bestDistance = Float.MAX_VALUE;

               for (Component row : rows) {
                  float distance = Math.abs(row.getY() - targetY);
                  if (distance < bestDistance) {
                     bestDistance = distance;
                     best = row;
                  }
               }

               selected = best;
            }
         }
      }
   }

   private static void activate() {
      if (selected != null) {
         selected.keyActivate();
         if (selected instanceof BindComponent bind && bind.isListening()) {
            pendingBind = bind;
         }
      }
   }

   private static void expand() {
      if (selected instanceof ModuleComponent module) {
         module.toggleExpanded();
      }
   }

   private static List<Panel> visiblePanels() {
      return new ArrayList<>(ClickGuiScreen.get().getPanels());
   }

   private static int clampPanelIndex() {
      return Math.max(0, Math.min(visiblePanels().size() - 1, panelIndex));
   }

   private static Panel focusedPanel() {
      List<Panel> panels = visiblePanels();
      if (panels.isEmpty()) {
         return null;
      }

      panelIndex = clampPanelIndex();
      return panels.get(panelIndex);
   }

   private static List<Component> flatList() {
      List<Component> rows = new ArrayList<>();
      Panel panel = focusedPanel();
      if (panel == null) {
         return rows;
      }

      for (Component component : panel.getComponents()) {
         if (!component.isHidden()) {
            rows.add(component);
            if (component instanceof ModuleComponent module && module.isExpanded()) {
               rows.addAll(module.visibleSettings());
            }
         }
      }

      return rows;
   }

   private static ModuleComponent owningMultiPageModule() {
      Panel panel = focusedPanel();
      if (panel != null && selected != null) {
         for (Component component : panel.getComponents()) {
            if (component instanceof ModuleComponent module && module.isExpanded() && module.hasMultiplePages() && module.visibleSettings().contains(selected)) {
               return module;
            }
         }

         return null;
      } else {
         return null;
      }
   }
}
