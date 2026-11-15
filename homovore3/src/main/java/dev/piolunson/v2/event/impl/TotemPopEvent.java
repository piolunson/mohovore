package dev.piolunson.v2.event.impl;

import dev.piolunson.v2.event.Event;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

public class TotemPopEvent extends Event {
   private final PlayerEntity player;
   private final int pops;

   public TotemPopEvent(PlayerEntity player, int pops) {
      this.player = player;
      this.pops = pops;
   }

   public PlayerEntity getPlayer() {
      return this.player;
   }

   public int getPops() {
      return this.pops;
   }

   public boolean isSelf() {
      return this.player == MinecraftClient.getInstance().player;
   }
}
