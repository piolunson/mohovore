package dev.piolunson.v2.feature.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.piolunson.v2.util.Jsonable;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class SoundSetting extends Setting<Set<SoundEvent>> implements Jsonable {
   private static List<SoundEvent> entries;
   private static final Map<SoundEvent, String> haystacks = new HashMap<>();
   private Runnable onChange = () -> {};

   public SoundSetting(String name) {
      this(name, List.of());
   }

   public SoundSetting(String name, Collection<SoundEvent> defaults) {
      super(name, new LinkedHashSet<>(defaults));
   }

   public SoundSetting onChange(Runnable onChange) {
      this.onChange = onChange == null ? () -> {} : onChange;
      return this;
   }

   public Set<SoundEvent> getValue() {
      return Collections.unmodifiableSet((Set<? extends SoundEvent>)super.getValue());
   }

   public void setValue(Set<SoundEvent> value) {
      Set<SoundEvent> before = (Set<SoundEvent>)super.getValue();
      super.setValue(new LinkedHashSet<>(value));
      if (!before.equals(super.getValue())) {
         this.onChange.run();
      }
   }

   public boolean contains(SoundEvent sound) {
      return ((Set<SoundEvent>)(Set)super.getValue()).contains(sound);
   }

   public boolean isEmpty() {
      return ((Set<SoundEvent>)(Set)super.getValue()).isEmpty();
   }

   public int size() {
      return ((Set<SoundEvent>)(Set)super.getValue()).size();
   }

   public void add(SoundEvent sound) {
      if (!this.contains(sound)) {
         Set<SoundEvent> next = new LinkedHashSet<>((Collection<? extends SoundEvent>)super.getValue());
         next.add(sound);
         this.setValue(next);
      }
   }

   public void remove(SoundEvent sound) {
      if (this.contains(sound)) {
         Set<SoundEvent> next = new LinkedHashSet<>((Collection<? extends SoundEvent>)super.getValue());
         next.remove(sound);
         this.setValue(next);
      }
   }

   public void toggle(SoundEvent sound) {
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

   public static List<SoundEvent> getEntries() {
      if (entries == null) {
         entries = Registries.SOUND_EVENT.stream().sorted(Comparator.comparing(SoundSetting::displayName)).toList();
      }

      return entries;
   }

   public static SoundEvent byKey(String key) {
      Identifier identifier = Identifier.tryParse(key);
      return identifier == null ? null : (SoundEvent)Registries.SOUND_EVENT.get(identifier);
   }

   public static String key(SoundEvent sound) {
      Identifier identifier = Registries.SOUND_EVENT.getId(sound);
      return identifier == null ? "" : identifier.toString();
   }

   public static String displayName(SoundEvent sound) {
      Identifier identifier = Registries.SOUND_EVENT.getId(sound);
      if (identifier == null) {
         return "";
      }

      StringBuilder name = new StringBuilder(identifier.getPath().length());

      for (String word : identifier.getPath().split("[._]")) {
         if (!word.isEmpty()) {
            if (!name.isEmpty()) {
               name.append(' ');
            }

            name.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
         }
      }

      return name.toString();
   }

   public static String subtitle(SoundEvent sound) {
      SoundManager soundManager = MinecraftClient.getInstance().getSoundManager();
      if (soundManager == null) {
         return "";
      }

      WeightedSoundSet entry = soundManager.get(sound.id());
      return entry != null && entry.getSubtitle() != null ? entry.getSubtitle().getString() : "";
   }

   public static boolean matches(SoundEvent sound, String needle) {
      return needle.isEmpty() || haystack(sound).contains(needle);
   }

   private static String haystack(SoundEvent sound) {
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

      for (SoundEvent sound : (Set<SoundEvent>)(Set)super.getValue()) {
         array.add(key(sound));
      }

      return array;
   }

   @Override
   public void fromJson(JsonElement element) {
      if (element != null && element.isJsonArray()) {
         Set<SoundEvent> restored = new LinkedHashSet<>();

         for (JsonElement stored : element.getAsJsonArray()) {
            SoundEvent sound = byKey(stored.getAsString());
            if (sound != null) {
               restored.add(sound);
            }
         }

         this.setValue(restored);
      }
   }
}
