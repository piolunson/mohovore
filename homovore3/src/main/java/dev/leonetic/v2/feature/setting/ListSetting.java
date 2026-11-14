package dev.leonetic.v2.feature.setting;

import java.util.List;
import java.util.function.Supplier;

public class ListSetting extends Setting<String> {
   private final Supplier<List<String>> options;
   private Runnable refresh = () -> {};

   public ListSetting(String name, String defaultValue, Supplier<List<String>> options) {
      super(name, defaultValue);
      this.options = options;
   }

   public ListSetting refreshWith(Runnable refresh) {
      this.refresh = refresh == null ? () -> {} : refresh;
      return this;
   }

   public void refresh() {
      this.refresh.run();
   }

   public List<String> getOptions() {
      List<String> current = this.options.get();
      return current == null ? List.of() : current;
   }

   @Override
   public void cycle(int direction) {
      List<String> current = this.getOptions();
      if (!current.isEmpty()) {
         int index = current.indexOf(this.getValue());
         this.setValue(current.get(Math.floorMod((index < 0 ? 0 : index) + direction, current.size())));
      }
   }
}
