package dev.leonetic.feature.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.leonetic.util.Jsonable;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_1144;
import net.minecraft.class_1146;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3414;
import net.minecraft.class_7923;

public class SoundSetting extends Setting<Set<class_3414>> implements Jsonable {
   private static List<class_3414> entries;
   private static final Map<class_3414, String> haystacks = new HashMap<>();
   private Runnable onChange = () -> {};

   public SoundSetting(String name) {
      this(name, List.of());
   }

   public SoundSetting(String name, Collection<class_3414> defaults) {
      super(name, new LinkedHashSet<>(defaults));
   }

   public SoundSetting onChange(Runnable onChange) {
      this.onChange = onChange == null ? () -> {} : onChange;
      return this;
   }

   public Set<class_3414> getValue() {
      return Collections.unmodifiableSet((Set<? extends class_3414>)super.getValue());
   }

   public void setValue(Set<class_3414> value) {
      Set<class_3414> before = (Set<class_3414>)super.getValue();
      super.setValue(new LinkedHashSet<>(value));
      if (!before.equals(super.getValue())) {
         this.onChange.run();
      }
   }

   public boolean contains(class_3414 sound) {
      return ((Set)super.getValue()).contains(sound);
   }

   public boolean isEmpty() {
      return ((Set)super.getValue()).isEmpty();
   }

   public int size() {
      return ((Set)super.getValue()).size();
   }

   public void add(class_3414 sound) {
      if (!this.contains(sound)) {
         Set<class_3414> next = new LinkedHashSet<>((Collection<? extends class_3414>)super.getValue());
         next.add(sound);
         this.setValue(next);
      }
   }

   public void remove(class_3414 sound) {
      if (this.contains(sound)) {
         Set<class_3414> next = new LinkedHashSet<>((Collection<? extends class_3414>)super.getValue());
         next.remove(sound);
         this.setValue(next);
      }
   }

   public void toggle(class_3414 sound) {
      if (this.contains(sound)) {
         this.remove(sound);
      } else {
         this.add(sound);
      }
   }

   public void clear() {
      if (!this.isEmpty()) {
         this.setValue(Set.of());
      }
   }

   public static List<class_3414> getEntries() {
      if (entries == null) {
         entries = class_7923.field_41172.method_10220().sorted(Comparator.comparing(SoundSetting::displayName)).toList();
      }

      return entries;
   }

   public static class_3414 byKey(String key) {
      class_2960 identifier = class_2960.method_12829(key);
      return identifier == null ? null : (class_3414)class_7923.field_41172.method_63535(identifier);
   }

   public static String key(class_3414 sound) {
      class_2960 identifier = class_7923.field_41172.method_10221(sound);
      return identifier == null ? "" : identifier.toString();
   }

   public static String displayName(class_3414 sound) {
      class_2960 identifier = class_7923.field_41172.method_10221(sound);
      if (identifier == null) {
         return "";
      }

      StringBuilder name = new StringBuilder(identifier.method_12832().length());

      for (String word : identifier.method_12832().split("[._]")) {
         if (!word.isEmpty()) {
            if (!name.isEmpty()) {
               name.append(' ');
            }

            name.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
         }
      }

      return name.toString();
   }

   public static String subtitle(class_3414 sound) {
      class_1144 soundManager = class_310.method_1551().method_1483();
      if (soundManager == null) {
         return "";
      }

      class_1146 entry = soundManager.method_4869(sound.comp_3319());
      return entry != null && entry.method_4886() != null ? entry.method_4886().getString() : "";
   }

   public static boolean matches(class_3414 sound, String needle) {
      return needle.isEmpty() || haystack(sound).contains(needle);
   }

   private static String haystack(class_3414 sound) {
      String cached = haystacks.get(sound);
      if (cached != null) {
         return cached;
      }

      String subtitle = subtitle(sound);
      String haystack = (displayName(sound) + "\u0000" + key(sound) + "\u0000" + subtitle).toLowerCase();
      if (!subtitle.isEmpty()) {
         haystacks.put(sound, haystack);
      }

      return haystack;
   }

   @Override
   public String getType() {
      return "Sounds";
   }

   @Override
   public String displayValue() {
      return this.isEmpty() ? "None" : String.valueOf(this.size());
   }

   @Override
   public JsonElement toJson() {
      JsonArray array = new JsonArray();

      for (class_3414 sound : (Set)super.getValue()) {
         array.add(key(sound));
      }

      return array;
   }

   @Override
   public void fromJson(JsonElement element) {
      if (element != null && element.isJsonArray()) {
         Set<class_3414> restored = new LinkedHashSet<>();

         for (JsonElement stored : element.getAsJsonArray()) {
            class_3414 sound = byKey(stored.getAsString());
            if (sound != null) {
               restored.add(sound);
            }
         }

         this.setValue(restored);
      }
   }
}
