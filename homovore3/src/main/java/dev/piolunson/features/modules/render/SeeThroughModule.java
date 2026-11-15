package dev.piolunson.features.modules.render;

import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Setting;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.projectile.ProjectileEntity;

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

   public boolean shouldSeeThrough(EntityRenderState state) {
      if (state == null) {
         return false;
      } else {
         EntityType<?> type = state.entityType;
         if (type == null) {
            return false;
         } else if (type == EntityType.PLAYER) {
            return this.inRange(state, this.players, this.playersRange);
         } else if (type == EntityType.ITEM) {
            return this.inRange(state, this.items, this.itemsRange);
         } else if (type == EntityType.END_CRYSTAL) {
            return this.inRange(state, this.crystals, this.crystalsRange);
         } else if (ProjectileEntity.class.isAssignableFrom(type.getBaseClass())) {
            return this.inRange(state, this.projectiles, this.projectilesRange);
         } else if (type.getSpawnGroup() == SpawnGroup.MONSTER) {
            return this.inRange(state, this.monsters, this.monstersRange);
         } else {
            return type.getSpawnGroup() != SpawnGroup.MISC ? this.inRange(state, this.animals, this.animalsRange) : false;
         }
      }
   }

   private boolean inRange(EntityRenderState state, Setting<Boolean> enabled, Setting<Float> range) {
      if (!enabled.getValue()) {
         return false;
      }

      float r = range.getValue();
      return state.squaredDistanceToCamera <= (double)r * r;
   }
}
