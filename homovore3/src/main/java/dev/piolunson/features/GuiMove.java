package dev.piolunson.features;

import dev.piolunson.event.impl.input.KeyInputEvent;
import dev.piolunson.event.impl.input.MouseInputEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.gui.HomovoreGui;
import dev.piolunson.util.traits.Util;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.AbstractCommandBlockScreen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.StructureBlockScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;

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
            mc.options.forwardKey.setPressed(this.up);
            mc.options.backKey.setPressed(this.down);
            mc.options.leftKey.setPressed(this.left);
            mc.options.rightKey.setPressed(this.right);
            mc.options.jumpKey.setPressed(this.jump);
            mc.options.sneakKey.setPressed(this.shift);
            mc.options.sprintKey.setPressed(this.sprint);
         }
      });
   }

   @Subscribe
   private void onKey(KeyInputEvent event) {
      if (event.getAction() == 1 || event.getAction() == 0) {
         if (!(mc.currentScreen instanceof HomovoreGui) || !isArrowKey(event.getKey())) {
            boolean pressed = event.getAction() == 1;
            KeyInput ke = new KeyInput(event.getKey(), 0, 0);
            if (mc.options.forwardKey.matchesKey(ke)) {
               this.up = pressed;
            } else if (mc.options.backKey.matchesKey(ke)) {
               this.down = pressed;
            } else if (mc.options.leftKey.matchesKey(ke)) {
               this.left = pressed;
            } else if (mc.options.rightKey.matchesKey(ke)) {
               this.right = pressed;
            } else if (mc.options.jumpKey.matchesKey(ke)) {
               this.jump = pressed;
            } else if (mc.options.sneakKey.matchesKey(ke)) {
               this.shift = pressed;
            } else if (mc.options.sprintKey.matchesKey(ke)) {
               this.sprint = pressed;
            }
         }
      }
   }

   @Subscribe
   private void onMouse(MouseInputEvent event) {
      if (event.getAction() == 1 || event.getAction() == 0) {
         boolean pressed = event.getAction() == 1;
         Click me = new Click(0.0, 0.0, new MouseInput(event.getButton(), 0));
         if (mc.options.forwardKey.matchesMouse(me)) {
            this.up = pressed;
         } else if (mc.options.backKey.matchesMouse(me)) {
            this.down = pressed;
         } else if (mc.options.leftKey.matchesMouse(me)) {
            this.left = pressed;
         } else if (mc.options.rightKey.matchesMouse(me)) {
            this.right = pressed;
         } else if (mc.options.jumpKey.matchesMouse(me)) {
            this.jump = pressed;
         } else if (mc.options.sneakKey.matchesMouse(me)) {
            this.shift = pressed;
         } else if (mc.options.sprintKey.matchesMouse(me)) {
            this.sprint = pressed;
         }
      }
   }

   private static boolean isArrowKey(int key) {
      return key == 263 || key == 262 || key == 265 || key == 264;
   }

   private boolean shouldRun() {
      return mc.currentScreen != null
         && !(mc.currentScreen instanceof ChatScreen)
         && !(mc.currentScreen instanceof AbstractSignEditScreen)
         && !(mc.currentScreen instanceof AnvilScreen)
         && !(mc.currentScreen instanceof AbstractCommandBlockScreen)
         && !(mc.currentScreen instanceof StructureBlockScreen);
   }
}
