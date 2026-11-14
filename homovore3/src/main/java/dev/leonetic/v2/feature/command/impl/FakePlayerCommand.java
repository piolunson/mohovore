package dev.leonetic.v2.feature.command.impl;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.leonetic.v2.feature.command.Command;
import dev.leonetic.v2.feature.command.CommandManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class FakePlayerCommand extends Command {
   private static final byte TOTEM_POP_EVENT_ID = 35;
   private static final EquipmentSlot[] COPIED_SLOTS = new EquipmentSlot[]{
      EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   private final List<OtherClientPlayerEntity> spawned = new ArrayList<>();
   private int nextId = -13337;

   public FakePlayerCommand() {
      super("fakeplayer", "fp");
      this.setDescription("Spawns a client-side fake player to test on");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)builder.executes(ctx -> this.spawn("FakePlayer")))
                  .then(literal("remove").executes(ctx -> this.removeAll())))
               .then(literal("pop").executes(ctx -> this.popAll())))
            .then(literal("swim").executes(ctx -> this.toggleSwim())))
         .then(argument("name", StringArgumentType.word()).executes(ctx -> this.spawn(StringArgumentType.getString(ctx, "name"))));
   }

   private int spawn(String name) {
      if (!inGame()) {
         return this.fail("You need to be in a world to do that.");
      }

      int despawned = this.despawnAll();
      GameProfile profile = new GameProfile(UUID.randomUUID(), name);
      OtherClientPlayerEntity fake = new OtherClientPlayerEntity(mc.world, profile);
      fake.setId(this.nextId--);
      fake.copyPositionAndRotation(mc.player);
      fake.setHeadYaw(mc.player.getHeadYaw());
      fake.setHealth(20.0F);

      for (EquipmentSlot slot : COPIED_SLOTS) {
         fake.equipStack(slot, mc.player.getEquippedStack(slot).copy());
      }

      mc.world.addEntity(fake);
      this.spawned.add(fake);
      return despawned > 0 ? this.success("Replaced fake player with {green}%s", name) : this.success("Spawned fake player {green}%s", name);
   }

   private int popAll() {
      if (inGame() && mc.getNetworkHandler() != null) {
         int popped = 0;

         for (OtherClientPlayerEntity fake : this.spawned) {
            if (fake.getEntityWorld() == mc.world) {
               mc.getNetworkHandler().onEntityStatus(new EntityStatusS2CPacket(fake, (byte)35));
               popped++;
            }
         }

         return popped == 0 ? this.fail("No fake players to pop. Spawn one first.") : this.success("Popped {green}%d{reset} fake player(s)", popped);
      } else {
         return this.fail("You need to be in a world to do that.");
      }
   }

   private int toggleSwim() {
      if (!inGame()) {
         return this.fail("You need to be in a world to do that.");
      }

      boolean anyStanding = false;
      int found = 0;

      for (OtherClientPlayerEntity fake : this.spawned) {
         if (fake.getEntityWorld() == mc.world) {
            found++;
            if (fake.getPose() != EntityPose.SWIMMING) {
               anyStanding = true;
            }
         }
      }

      if (found == 0) {
         return this.fail("No fake players. Spawn one first.");
      }

      EntityPose pose = anyStanding ? EntityPose.SWIMMING : EntityPose.STANDING;

      for (OtherClientPlayerEntity fake : this.spawned) {
         if (fake.getEntityWorld() == mc.world) {
            fake.setPose(pose);
            fake.calculateDimensions();
         }
      }

      return this.success(pose == EntityPose.SWIMMING ? "Fake player(s) now in swim pose." : "Fake player(s) now standing.");
   }

   private int removeAll() {
      if (!inGame()) {
         this.spawned.clear();
         return this.fail("You need to be in a world to do that.");
      } else {
         int removed = this.despawnAll();
         return removed == 0 ? this.success("No fake players to remove.") : this.success("Removed {green}%d{reset} fake player(s)", removed);
      }
   }

   private int despawnAll() {
      int removed = 0;
      Iterator<OtherClientPlayerEntity> it = this.spawned.iterator();

      while (it.hasNext()) {
         OtherClientPlayerEntity fake = it.next();
         it.remove();
         if (fake.getEntityWorld() == mc.world) {
            mc.world.removeEntity(fake.getId(), RemovalReason.DISCARDED);
            removed++;
         }
      }

      return removed;
   }
}
