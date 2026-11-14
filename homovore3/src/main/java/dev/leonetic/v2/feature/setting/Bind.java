package dev.leonetic.v2.feature.setting;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class Bind {
   public static final int MOUSE_OFFSET = 1000;
   public static final int NONE = -1;
   private final int key;

   public Bind(int key) {
      this.key = key;
   }

   public static Bind none() {
      return new Bind(-1);
   }

   public static Bind mouse(int button) {
      return new Bind(1000 + button);
   }

   public int getKey() {
      return this.key;
   }

   public boolean isEmpty() {
      return this.key < 0;
   }

   public boolean isMouse() {
      return this.key >= 1000;
   }

   public boolean isDown() {
      if (this.isEmpty()) {
         return false;
      }

      long window = MinecraftClient.getInstance().getWindow().getHandle();
      return this.isMouse() ? GLFW.glfwGetMouseButton(window, this.key - 1000) == 1 : GLFW.glfwGetKey(window, this.key) == 1;
   }

   @Override
   public String toString() {
      if (this.isEmpty()) {
         return "None";
      }

      if (this.isMouse()) {
         return "M" + (this.key - 1000 + 1);
      }

      String name = InputUtil.fromKeyCode(new KeyInput(this.key, 0, 0))
         .getTranslationKey()
         .replace("key.keyboard.", "")
         .replace("key.mouse.", "")
         .replace('.', ' ')
         .trim();
      return name.isEmpty() ? "None" : name.toUpperCase();
   }
}
