package dev.piolunson.manager;

import dev.piolunson.Homovore;
import dev.piolunson.event.Stage;
import dev.piolunson.event.impl.entity.DeathEvent;
import dev.piolunson.event.impl.entity.player.TickEvent;
import dev.piolunson.event.impl.entity.player.UpdateWalkingPlayerEvent;
import dev.piolunson.event.impl.input.KeyInputEvent;
import dev.piolunson.event.impl.input.MouseInputEvent;
import dev.piolunson.event.impl.network.ChatEvent;
import dev.piolunson.event.impl.render.Render2DEvent;
import dev.piolunson.event.impl.render.Render3DEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.Feature;
import net.minecraft.entity.player.PlayerEntity;

public class EventManager extends Feature {
   public void init() {
      EVENT_BUS.register(this);
   }

   public void onUnload() {
      EVENT_BUS.unregister(this);
   }

   @Subscribe
   public void onTick(TickEvent event) {
      if (!nullCheck()) {
         Homovore.moduleManager.onTick();

         for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != null && !(player.getHealth() > 0.0F)) {
               EVENT_BUS.post(new DeathEvent(player));
            }
         }
      }
   }

   @Subscribe(priority = 100)
   public void onUpdateWalkingPlayer(UpdateWalkingPlayerEvent event) {
      if (!nullCheck()) {
         if (event.getStage() == Stage.PRE) {
            Homovore.positionManager.updatePosition();
         }

         if (event.getStage() == Stage.POST) {
            Homovore.positionManager.restorePosition();
         }
      }
   }

   @Subscribe
   public void onWorldRender(Render3DEvent event) {
      Homovore.moduleManager.onRender3D(event);
   }

   @Subscribe
   public void onRenderGameOverlayEvent(Render2DEvent event) {
      Homovore.moduleManager.onRender2D(event);
   }

   @Subscribe
   public void onKeyInput(KeyInputEvent event) {
      if (event.getAction() == 1) {
         Homovore.moduleManager.onKeyPressed(event.getKey());
      } else if (event.getAction() == 0) {
         Homovore.moduleManager.onKeyReleased(event.getKey());
      }
   }

   @Subscribe
   public void onMouseInput(MouseInputEvent event) {
      if (event.getAction() == 1) {
         Homovore.moduleManager.onMousePressed(event.getButton());
      } else if (event.getAction() == 0) {
         Homovore.moduleManager.onMouseReleased(event.getButton());
      }
   }

   @Subscribe
   public void onChatSent(ChatEvent event) {
      String message = event.getMessage();
      if (message.startsWith(Homovore.commandManager.getCommandPrefix())) {
         event.cancel();
         Homovore.commandManager.onChatSent(message);
      }
   }
}
