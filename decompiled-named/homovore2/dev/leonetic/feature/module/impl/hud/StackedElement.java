package dev.leonetic.feature.module.impl.hud;

import dev.leonetic.feature.module.impl.HudModule;
import dev.leonetic.feature.setting.Setting;
import net.minecraft.client.gui.DrawContext;

public abstract class StackedElement extends HudElement {
   public final Setting<HudPosition> position;
   private final Setting<Integer> order;

   protected StackedElement(String name, boolean defaultEnabled, HudPosition defaultPosition, int defaultOrder) {
      super(name, defaultEnabled);
      this.position = this.register(new Setting<>("Position", defaultPosition)).describe("Which of the six groups this element is stacked into.");
      this.order = this.register(new Setting<>("Order", defaultOrder));
   }

   public HudPosition position() {
      return this.position.getValue();
   }

   public void setPosition(HudPosition value) {
      this.position.setValue(value);
   }

   public int order() {
      return this.order.getValue();
   }

   public void setOrder(int value) {
      this.order.setValue(value);
   }

   public abstract int width();

   public abstract int height();

   public abstract void render(DrawContext var1, HudModule var2, int var3, int var4, int var5);

   protected int rowLeft(int left, int width, int rowWidth) {
      return this.position().isLeft() ? left : left + width - rowWidth;
   }
}
