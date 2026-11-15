package dev.piolunson.v2.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.piolunson.v2.feature.module.Module;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;

public class RegistryDataSetting<T, D extends SettingGroup> extends RegistrySetting<T> {
   private final Function<T, D> factory;
   private final Map<T, D> data = new HashMap<>();

   public RegistryDataSetting(String name, RegistryKind<T> kind, Supplier<D> factory, Collection<T> defaults) {
      this(name, kind, ignored -> factory.get(), defaults);
   }

   public RegistryDataSetting(String name, RegistryKind<T> kind, Function<T, D> factory, Collection<T> defaults) {
      super(name, kind, defaults);
      this.factory = factory;
   }

   public D get(T entry) {
      return this.data.computeIfAbsent(entry, key -> {
         D group = this.factory.apply((T)key);
         group.setOwner(this.getOwner());
         return group;
      });
   }

   @Override
   public void setOwner(Module owner) {
      super.setOwner(owner);
      this.data.values().forEach(group -> group.setOwner(owner));
   }

   @Override
   public void setValue(Set<T> value) {
      super.setValue(value);
      this.data.keySet().retainAll(this.getValue());
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();

      for (T entry : this.getValue()) {
         object.add(this.getKey(entry), this.get(entry).toJson());
      }

      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      if (element != null) {
         if (element.isJsonArray()) {
            super.fromJson(element);
         } else if (element.isJsonObject()) {
            Set<T> restored = new LinkedHashSet<>();
            Map<T, D> restoredData = new HashMap<>();

            for (Entry<String, JsonElement> stored : element.getAsJsonObject().entrySet()) {
               this.getKind().byKey(stored.getKey()).ifPresent(entry -> {
                  D group = this.factory.apply((T)entry);
                  group.setOwner(this.getOwner());
                  group.fromJson(stored.getValue());
                  restored.add((T)entry);
                  restoredData.put((T)entry, group);
               });
            }

            this.setValue(restored);
            this.data.putAll(restoredData);
         }
      }
   }
}
