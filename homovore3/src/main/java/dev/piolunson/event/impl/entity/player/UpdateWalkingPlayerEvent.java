package dev.piolunson.event.impl.entity.player;

import dev.piolunson.event.Event;
import dev.piolunson.event.Stage;

public class UpdateWalkingPlayerEvent extends Event {
   private final Stage stage;

   public UpdateWalkingPlayerEvent(Stage stage) {
      this.stage = stage;
   }

   public Stage getStage() {
      return this.stage;
   }
}
