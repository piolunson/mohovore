package dev.leonetic.features.commands.impl;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.leonetic.features.commands.Command;
import dev.leonetic.manager.CommandManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.class_1304;
import net.minecraft.class_2663;
import net.minecraft.class_4050;
import net.minecraft.class_745;
import net.minecraft.class_1297.class_5529;

public class FakePlayerCommand extends Command {
   private static final byte TOTEM_POP_EVENT_ID = 35;
   private static final class_1304[] COPIED_SLOTS = new class_1304[]{
      class_1304.field_6173, class_1304.field_6171, class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166
   };
   private final List<class_745> spawned = new ArrayList<>();
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

   private int popAll() {
      if (!nullCheck() && mc.method_1562() != null) {
         int popped = 0;

         for (class_745 fake : this.spawned) {
            if (fake.method_73183() == mc.field_1687) {
               mc.method_1562().method_11148(new class_2663(fake, (byte)35));
               popped++;
            }
         }

         return popped == 0 ? this.fail("No fake players to pop. Spawn one first.") : this.success("Popped %s fake player(s)", popped);
      } else {
         return this.fail("You need to be in a world to do that.");
      }
   }

   private int toggleSwim() {
      if (nullCheck()) {
         return this.fail("You need to be in a world to do that.");
      }

      boolean anyStanding = false;
      int found = 0;

      for (class_745 fake : this.spawned) {
         if (fake.method_73183() == mc.field_1687) {
            found++;
            if (fake.method_18376() != class_4050.field_18079) {
               anyStanding = true;
            }
         }
      }

      if (found == 0) {
         return this.fail("No fake players. Spawn one first.");
      }

      class_4050 pose = anyStanding ? class_4050.field_18079 : class_4050.field_18076;

      for (class_745 fake : this.spawned) {
         if (fake.method_73183() == mc.field_1687) {
            fake.method_18380(pose);
            fake.method_18382();
         }
      }

      return this.success(pose == class_4050.field_18079 ? "Fake player(s) now in swim pose." : "Fake player(s) now standing.");
   }

   private int spawn(String name) {
      if (nullCheck()) {
         return this.fail("You need to be in a world to do that.");
      }

      int despawned = this.despawnAll();
      GameProfile profile = new GameProfile(UUID.randomUUID(), name);
      class_745 fake = new class_745(mc.field_1687, profile);
      fake.method_5838(this.nextId--);
      fake.method_5719(mc.field_1724);
      fake.method_5847(mc.field_1724.method_5791());
      fake.method_6033(20.0F);

      for (class_1304 slot : COPIED_SLOTS) {
         fake.method_5673(slot, mc.field_1724.method_6118(slot).method_7972());
      }

      mc.field_1687.method_53875(fake);
      this.spawned.add(fake);
      return despawned > 0 ? this.success("Replaced fake player with {green} %s", name) : this.success("Spawned fake player {green} %s", name);
   }

   private int removeAll() {
      if (nullCheck()) {
         this.spawned.clear();
         return this.fail("You need to be in a world to do that.");
      } else {
         int removed = this.despawnAll();
         return removed == 0 ? this.success("No fake players to remove.") : this.success("Removed %s fake player(s)", removed);
      }
   }

   private int despawnAll() {
      int removed = 0;
      Iterator<class_745> it = this.spawned.iterator();

      while (it.hasNext()) {
         class_745 fake = it.next();
         it.remove();
         if (fake.method_73183() == mc.field_1687) {
            mc.field_1687.method_2945(fake.method_5628(), class_5529.field_26999);
            removed++;
         }
      }

      return removed;
   }
}
