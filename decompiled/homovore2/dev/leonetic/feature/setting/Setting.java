package dev.leonetic.feature.setting;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.ClientEvent;
import dev.leonetic.feature.module.Module;
import java.awt.Color;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.joml.Vector2f;

public class Setting<T> {
   private final String name;
   private final T defaultValue;
   private T value;
   private Number min;
   private Number max;
   private String description = "";
   private String page = "General";
   private BooleanSupplier visibility = () -> true;
   private Runnable onChange = () -> {};
   private Module owner;

   public Setting(String name, T defaultValue) {
      this.name = name;
      this.defaultValue = defaultValue;
      this.value = defaultValue;
   }

   public Setting(String name, T defaultValue, Number min, Number max) {
      this(name, defaultValue);
      this.min = min;
      this.max = max;
   }

   public String getName() {
      return this.name;
   }

   public T getValue() {
      return this.value;
   }

   public void setValue(T value) {
      ClientEvent event = new ClientEvent(this);
      if (!Homovore2Client.EVENT_BUS.post(event)) {
         T before = this.value;
         this.value = value;
         if (!Objects.equals(before, value)) {
            this.onChange.run();
         }
      }
   }

   public void setValueNoEvent(T value) {
      this.value = value;
   }

   public Module getOwner() {
      return this.owner;
   }

   public void setOwner(Module owner) {
      this.owner = owner;
   }

   public String getType() {
      if (this.isEnumSetting()) {
         return "Enum";
      } else if (this.isColorSetting()) {
         return "Color";
      } else {
         return this.value instanceof Vector2f ? "Pos" : this.defaultValue.getClass().getSimpleName();
      }
   }

   public String getValueAsString() {
      return String.valueOf(this.value);
   }

   public T getDefaultValue() {
      return this.defaultValue;
   }

   public void reset() {
      this.value = this.defaultValue;
   }

   public Number getMin() {
      return this.min;
   }

   public Number getMax() {
      return this.max;
   }

   public boolean isRanged() {
      return this.min != null && this.max != null;
   }

   public boolean isNumberSetting() {
      return this.value instanceof Number;
   }

   public boolean isEnumSetting() {
      return this.value instanceof Enum;
   }

   public boolean isColorSetting() {
      return this.value instanceof Color;
   }

   public boolean isIntegral() {
      return this.value instanceof Integer;
   }

   public Setting<T> visibleWhen(BooleanSupplier visibility) {
      this.visibility = visibility;
      return this;
   }

   public boolean isVisible() {
      return this.visibility.getAsBoolean();
   }

   public Setting<T> onChange(Runnable onChange) {
      this.onChange = onChange == null ? () -> {} : onChange;
      return this;
   }

   public Setting<T> describe(String description) {
      this.description = description;
      return this;
   }

   public String getPage() {
      return this.page;
   }

   public Setting<T> page(String page) {
      String value = page == null ? "" : page.trim();
      this.page = value.isEmpty() ? "General" : value;
      return this;
   }

   public String getDescription() {
      return this.description;
   }

   public void cycle(int direction) {
      if (this.value instanceof Enum<?> current) {
         Object[] var5 = current.getDeclaringClass().getEnumConstants();
         int next = Math.floorMod(current.ordinal() + direction, var5.length);
         this.value = (T)var5[next];
      }
   }

   public String displayValue() {
      if (this.value instanceof Enum<?> constant) {
         return prettify(constant.name());
      } else {
         return this.value instanceof Double d ? String.valueOf(Math.round(d * 100.0) / 100.0) : String.valueOf(this.value);
      }
   }

   private static String prettify(String constant) {
      StringBuilder out = new StringBuilder(constant.length());

      for (String word : constant.split("_")) {
         if (!word.isEmpty()) {
            if (!out.isEmpty()) {
               out.append(' ');
            }

            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
         }
      }

      return out.toString();
   }
}
