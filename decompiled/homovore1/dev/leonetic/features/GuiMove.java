package dev.leonetic.features;

import dev.leonetic.event.impl.input.KeyInputEvent;
import dev.leonetic.event.impl.input.MouseInputEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.gui.HomovoreGui;
import dev.leonetic.util.traits.Util;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_11910;
import net.minecraft.class_408;
import net.minecraft.class_463;
import net.minecraft.class_471;
import net.minecraft.class_497;
import net.minecraft.class_7743;

public class GuiMove implements Util {
   private boolean up;
   private boolean down;
   private boolean left;
   private boolean right;
   private boolean jump;
   private boolean shift;
   private boolean sprint;

   public void init() {
      EVENT_BUS.register(this);
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (this.shouldRun()) {
            mc.field_1690.field_1894.method_23481(this.up);
            mc.field_1690.field_1881.method_23481(this.down);
            mc.field_1690.field_1913.method_23481(this.left);
            mc.field_1690.field_1849.method_23481(this.right);
            mc.field_1690.field_1903.method_23481(this.jump);
            mc.field_1690.field_1832.method_23481(this.shift);
            mc.field_1690.field_1867.method_23481(this.sprint);
         }
      });
   }

   @Subscribe
   private void onKey(KeyInputEvent event) {
      if (event.getAction() == 1 || event.getAction() == 0) {
         if (!(mc.field_1755 instanceof HomovoreGui) || !isArrowKey(event.getKey())) {
            boolean pressed = event.getAction() == 1;
            class_11908 ke = new class_11908(event.getKey(), 0, 0);
            if (mc.field_1690.field_1894.method_1417(ke)) {
               this.up = pressed;
            } else if (mc.field_1690.field_1881.method_1417(ke)) {
               this.down = pressed;
            } else if (mc.field_1690.field_1913.method_1417(ke)) {
               this.left = pressed;
            } else if (mc.field_1690.field_1849.method_1417(ke)) {
               this.right = pressed;
            } else if (mc.field_1690.field_1903.method_1417(ke)) {
               this.jump = pressed;
            } else if (mc.field_1690.field_1832.method_1417(ke)) {
               this.shift = pressed;
            } else if (mc.field_1690.field_1867.method_1417(ke)) {
               this.sprint = pressed;
            }
         }
      }
   }

   @Subscribe
   private void onMouse(MouseInputEvent event) {
      if (event.getAction() == 1 || event.getAction() == 0) {
         boolean pressed = event.getAction() == 1;
         class_11909 me = new class_11909(0.0, 0.0, new class_11910(event.getButton(), 0));
         if (mc.field_1690.field_1894.method_1433(me)) {
            this.up = pressed;
         } else if (mc.field_1690.field_1881.method_1433(me)) {
            this.down = pressed;
         } else if (mc.field_1690.field_1913.method_1433(me)) {
            this.left = pressed;
         } else if (mc.field_1690.field_1849.method_1433(me)) {
            this.right = pressed;
         } else if (mc.field_1690.field_1903.method_1433(me)) {
            this.jump = pressed;
         } else if (mc.field_1690.field_1832.method_1433(me)) {
            this.shift = pressed;
         } else if (mc.field_1690.field_1867.method_1433(me)) {
            this.sprint = pressed;
         }
      }
   }

   private static boolean isArrowKey(int key) {
      return key == 263 || key == 262 || key == 265 || key == 264;
   }

   private boolean shouldRun() {
      return mc.field_1755 != null
         && !(mc.field_1755 instanceof class_408)
         && !(mc.field_1755 instanceof class_7743)
         && !(mc.field_1755 instanceof class_471)
         && !(mc.field_1755 instanceof class_463)
         && !(mc.field_1755 instanceof class_497);
   }
}
