package dev.leonetic.features.modules.funny;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.class_10799;
import net.minecraft.class_1109;
import net.minecraft.class_2960;
import net.minecraft.class_332;
import net.minecraft.class_3417;

public class AutoHorseModule extends Module {
   private static final class_2960 TEXTURE = class_2960.method_60655("homovore", "textures/autohorse.png");
   private static final int TEXTURE_WIDTH = 1024;
   private static final int TEXTURE_HEIGHT = 1536;
   private static final long MIN_TRIGGER_INTERVAL_MS = 200L;
   private final Setting<Integer> duration = this.num("Duration", 25, 5, 100);
   private final Setting<Float> scale = this.num("Scale", 0.16F, 0.05F, 0.35F);
   private final Setting<Float> volume = this.num("Volume", 1.0F, 0.1F, 1.0F);
   private final List<AutoHorseModule.HorseSprite> horses = new ArrayList<>();
   private int activeTicks;
   private boolean wasHurtLastTick;
   private long lastTriggerAt;

   public AutoHorseModule() {
      super("AutoHorse", "Shows horses and plays a sound when you take damage.", Module.Category.FUNNY);
   }

   @Override
   public void onEnable() {
      this.resetState();
   }

   @Override
   public void onDisable() {
      this.resetState();
   }

   @Override
   public void onTick() {
      if (nullCheck()) {
         this.resetState();
      } else {
         if (this.activeTicks > 0) {
            this.activeTicks--;
            if (this.activeTicks == 0) {
               this.horses.clear();
            }
         }

         boolean hurt = mc.field_1724.field_6235 > 0;
         if (hurt && !this.wasHurtLastTick) {
            this.triggerHorseRain();
         }

         this.wasHurtLastTick = hurt;
      }
   }

   @Override
   public void onRender2D(Render2DEvent event) {
      if (!this.horses.isEmpty()) {
         int screenWidth = mc.method_22683().method_4486();
         int screenHeight = mc.method_22683().method_4502();
         if (screenWidth > 0 && screenHeight > 0) {
            class_332 context = event.getContext();

            for (AutoHorseModule.HorseSprite horse : this.horses) {
               int width = Math.min(horse.width(), screenWidth);
               int height = Math.min(horse.height(), screenHeight);
               if (width > 0 && height > 0) {
                  context.method_25302(
                     class_10799.field_56883,
                     TEXTURE,
                     Math.max(0, Math.min(horse.x(), screenWidth - width)),
                     Math.max(0, Math.min(horse.y(), screenHeight - height)),
                     0.0F,
                     0.0F,
                     width,
                     height,
                     1024,
                     1536,
                     1024,
                     1536
                  );
               }
            }
         }
      }
   }

   private void triggerHorseRain() {
      if (mc.method_22683() != null) {
         long now = System.currentTimeMillis();
         if (now - this.lastTriggerAt >= 200L) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int screenWidth = mc.method_22683().method_4486();
            int screenHeight = mc.method_22683().method_4502();
            if (screenWidth > 0 && screenHeight > 0) {
               int imageWidth = Math.max(72, Math.round(screenWidth * this.scale.getValue()));
               int imageHeight = Math.max(108, Math.round(imageWidth * 1.5F));
               if (imageHeight > screenHeight) {
                  float shrink = (float)screenHeight / imageHeight;
                  imageHeight = screenHeight;
                  imageWidth = Math.max(1, Math.round(imageWidth * shrink));
               }

               if (imageWidth > screenWidth) {
                  imageWidth = screenWidth;
               }

               if (imageWidth > 0 && imageHeight > 0) {
                  this.horses.clear();
                  int count = random.nextInt(3, 6);
                  int maxX = Math.max(0, screenWidth - imageWidth);
                  int maxY = Math.max(0, screenHeight - imageHeight);

                  for (int i = 0; i < count; i++) {
                     this.horses.add(new AutoHorseModule.HorseSprite(random.nextInt(maxX + 1), random.nextInt(maxY + 1), imageWidth, imageHeight));
                  }

                  this.activeTicks = this.duration.getValue();
                  this.lastTriggerAt = now;
                  this.playHorseSound();
               }
            }
         }
      }
   }

   private void resetState() {
      this.horses.clear();
      this.activeTicks = 0;
      this.wasHurtLastTick = false;
      this.lastTriggerAt = 0L;
   }

   private void playHorseSound() {
      try {
         mc.method_1483().method_4873(class_1109.method_4758(Homovore.AUTOHORSE_SOUND, this.volume.getValue()));
      } catch (RuntimeException e) {
         Homovore.LOGGER.warn("Failed to play AutoHorse sound, falling back to UI click", e);
         mc.method_1483().method_4873(class_1109.method_47978(class_3417.field_15015, Math.min(this.volume.getValue(), 1.0F)));
      }
   }

   private record HorseSprite(int x, int y, int width, int height) {
   }
}
