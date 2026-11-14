package dev.leonetic.features.modules.render;

import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import net.minecraft.class_10017;
import net.minecraft.class_1299;
import net.minecraft.class_1311;
import net.minecraft.class_1676;

public class SeeThroughModule extends Module {
   public Setting<Boolean> players = this.bool("Players", true).setPage("Entities");
   public Setting<Float> playersRange = this.num("Players Range", 64.0F, 4.0F, 256.0F).setPage("Entities").setVisibility(v -> this.players.getValue());
   public Setting<Boolean> monsters = this.bool("Monsters", false).setPage("Entities");
   public Setting<Float> monstersRange = this.num("Monsters Range", 64.0F, 4.0F, 256.0F).setPage("Entities").setVisibility(v -> this.monsters.getValue());
   public Setting<Boolean> animals = this.bool("Animals", false).setPage("Entities");
   public Setting<Float> animalsRange = this.num("Animals Range", 64.0F, 4.0F, 256.0F).setPage("Entities").setVisibility(v -> this.animals.getValue());
   public Setting<Boolean> items = this.bool("Items", true).setPage("Entities");
   public Setting<Float> itemsRange = this.num("Items Range", 64.0F, 4.0F, 256.0F).setPage("Entities").setVisibility(v -> this.items.getValue());
   public Setting<Boolean> crystals = this.bool("Crystals", false).setPage("Entities");
   public Setting<Float> crystalsRange = this.num("Crystals Range", 64.0F, 4.0F, 256.0F).setPage("Entities").setVisibility(v -> this.crystals.getValue());
   public Setting<Boolean> projectiles = this.bool("Projectiles", false).setPage("Entities");
   public Setting<Float> projectilesRange = this.num("Projectiles Range", 64.0F, 4.0F, 256.0F)
      .setPage("Entities")
      .setVisibility(v -> this.projectiles.getValue());

   public SeeThroughModule() {
      super("SeeThrough", "Render selected entities through walls", Module.Category.RENDER);
   }

   public boolean shouldSeeThrough(class_10017 state) {
      if (state == null) {
         return false;
      } else {
         class_1299<?> type = state.field_58171;
         if (type == null) {
            return false;
         } else if (type == class_1299.field_6097) {
            return this.inRange(state, this.players, this.playersRange);
         } else if (type == class_1299.field_6052) {
            return this.inRange(state, this.items, this.itemsRange);
         } else if (type == class_1299.field_6110) {
            return this.inRange(state, this.crystals, this.crystalsRange);
         } else if (class_1676.class.isAssignableFrom(type.method_31794())) {
            return this.inRange(state, this.projectiles, this.projectilesRange);
         } else if (type.method_5891() == class_1311.field_6302) {
            return this.inRange(state, this.monsters, this.monstersRange);
         } else {
            return type.method_5891() != class_1311.field_17715 ? this.inRange(state, this.animals, this.animalsRange) : false;
         }
      }
   }

   private boolean inRange(class_10017 state, Setting<Boolean> enabled, Setting<Float> range) {
      if (!enabled.getValue()) {
         return false;
      }

      float r = range.getValue();
      return state.field_53332 <= (double)r * r;
   }
}
