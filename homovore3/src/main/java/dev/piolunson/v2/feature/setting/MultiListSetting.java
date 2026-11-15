package dev.piolunson.v2.feature.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.piolunson.v2.util.Jsonable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class MultiListSetting<T> extends Setting<Set<T>> implements Jsonable {
   private final Supplier<List<T>> entries;
   private final Function<T, String> naming;
   private final Function<T, String> keys;
   private final Function<String, Optional<T>> lookup;
   private final boolean defaultAll;
   private boolean useDefaultAll;

   public MultiListSetting(
      String name,
      Collection<T> defaults,
      Supplier<List<T>> entries,
      Function<T, String> naming,
      Function<T, String> keys,
      Function<String, Optional<T>> lookup
   ) {
      this(name, defaults, entries, naming, keys, lookup, false);
   }

   public MultiListSetting(
      String name,
      Collection<T> defaults,
      Supplier<List<T>> entries,
      Function<T, String> naming,
      Function<T, String> keys,
      Function<String, Optional<T>> lookup,
      boolean defaultAll
   ) {
      super(name, new LinkedHashSet<>(defaults));
      this.entries = entries;
      this.naming = naming;
      this.keys = keys;
      this.lookup = lookup;
      this.defaultAll = defaultAll;
      this.useDefaultAll = defaultAll;
   }

   public Set<T> getValue() {
      return this.useDefaultAll
         ? Collections.unmodifiableSet(new LinkedHashSet<>(this.getEntries()))
         : Collections.unmodifiableSet((Set<? extends T>)super.getValue());
   }

   public void setValue(Set<T> value) {
      Set<T> next = new LinkedHashSet<>(value);
      super.setValue((Set<T>)(Object)next);
      if (super.getValue() == next) {
         this.useDefaultAll = false;
      }
   }

   public void setValueNoEvent(Set<T> value) {
      super.setValueNoEvent((Set<T>)(Object)(new LinkedHashSet<T>(value)));
      this.useDefaultAll = false;
   }

   @Override
   public void reset() {
      this.useDefaultAll = this.defaultAll;
      super.reset();
   }

   public boolean contains(T entry) {
      return this.useDefaultAll ? this.getEntries().contains(entry) : ((Set)super.getValue()).contains(entry);
   }

   public boolean isEmpty() {
      return !this.useDefaultAll && ((Set)super.getValue()).isEmpty();
   }

   public int size() {
      return this.useDefaultAll ? this.getEntries().size() : ((Set)super.getValue()).size();
   }

   public void add(T entry) {
      if (!this.contains(entry)) {
         Set<T> next = new LinkedHashSet<>(this.getValue());
         next.add(entry);
         this.setValue(next);
      }
   }

   public void remove(T entry) {
      if (this.contains(entry)) {
         Set<T> next = new LinkedHashSet<>(this.getValue());
         next.remove(entry);
         this.setValue(next);
      }
   }

   public void toggle(T entry) {
      if (this.contains(entry)) {
         this.remove(entry);
      } else {
         this.add(entry);
      }
   }

   public void clear() {
      if (!this.isEmpty()) {
         this.setValue(Set.of());
      }
   }

   public List<T> getEntries() {
      List<T> current = this.entries.get();
      return current == null ? List.of() : current;
   }

   public String displayName(T entry) {
      return this.naming.apply(entry);
   }

   public String getKey(T entry) {
      return this.keys.apply(entry);
   }

   @Override
   public String displayValue() {
      return this.isEmpty() ? "None" : String.valueOf(this.size());
   }

   @Override
   public String getType() {
      return "MultiList";
   }

   @Override
   public JsonElement toJson() {
      JsonArray array = new JsonArray();

      for (T entry : this.getValue()) {
         array.add(this.getKey(entry));
      }

      return array;
   }

   @Override
   public void fromJson(JsonElement element) {
      if (element != null && element.isJsonArray()) {
         Set<T> restored = new LinkedHashSet<>();

         for (JsonElement stored : element.getAsJsonArray()) {
            this.lookup.apply(stored.getAsString()).ifPresent(restored::add);
         }

         this.setValue(restored);
      }
   }
}
