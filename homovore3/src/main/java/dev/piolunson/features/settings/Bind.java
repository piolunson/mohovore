package dev.piolunson.features.settings;

import com.google.common.base.Converter;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.piolunson.util.traits.Util;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class Bind implements Util {
   public static final int MOUSE_BUTTON_OFFSET = 1000;
   private int key;

   public Bind(int key) {
      this.key = key;
   }

   public static Bind none() {
      return new Bind(-1);
   }

   public static Bind fromMouseButton(int button) {
      return new Bind(1000 + button);
   }

   public static boolean isMouseButton(int key) {
      return key >= 1000;
   }

   public int getKey() {
      return this.key;
   }

   public void setKey(int key) {
      this.key = key;
   }

   public boolean isEmpty() {
      return this.key < 0;
   }

   @Override
   public String toString() {
      if (this.isEmpty()) {
         return "None";
      }

      if (isMouseButton(this.key)) {
         return "M" + (this.key - 1000 + 1);
      }

      String raw = InputUtil.fromKeyCode(new KeyInput(this.key, 0, 0)).getTranslationKey();
      String cleaned = raw.replace("key.keyboard.", "").replace("key.mouse.", "").replace('.', ' ').trim();
      StringBuilder out = new StringBuilder(cleaned.length());

      for (String part : cleaned.split(" ")) {
         if (!part.isEmpty()) {
            if (out.length() > 0) {
               out.append(' ');
            }

            out.append(this.capitalise(part));
         }
      }

      return out.toString();
   }

   public boolean isDown() {
      if (this.isEmpty()) {
         return false;
      } else {
         return isMouseButton(this.key)
            ? GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), this.key - 1000) == 1
            : GLFW.glfwGetKey(mc.getWindow().getHandle(), this.getKey()) == 1;
      }
   }

   private String capitalise(String str) {
      return str.isEmpty() ? "" : Character.toUpperCase(str.charAt(0)) + (str.length() != 1 ? str.substring(1).toLowerCase() : "");
   }

   public static class BindConverter extends Converter<Bind, JsonElement> {
      public JsonElement doForward(Bind bind) {
         return new JsonPrimitive(bind.toString());
      }

      public Bind doBackward(JsonElement jsonElement) {
         String s = jsonElement.getAsString();
         if (s.equalsIgnoreCase("None")) {
            return Bind.none();
         }

         if (s.toUpperCase().startsWith("M") && s.length() >= 2) {
            try {
               int button = Integer.parseInt(s.substring(1)) - 1;
               if (button >= 0) {
                  return Bind.fromMouseButton(button);
               }
            } catch (NumberFormatException var6) {
            }
         }

         int key = -1;

         try {
            key = InputUtil.fromTranslationKey(s.toUpperCase()).getCode();
         } catch (Exception var5) {
         }

         return key == 0 ? Bind.none() : new Bind(key);
      }
   }
}
