package dev.leonetic.feature.module.impl.hud;

import dev.leonetic.feature.module.impl.HudModule;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.render.Render2D;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.network.PacketLimiter;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

public final class PacketLimitsElement extends StackedElement {
   private static final String SEPARATOR = " ";
   public final Setting<Color> labelColor = this.register(new Setting<>("Label", new Color(255, 255, 255, 255))).describe("The name of each budget.");
   public final Setting<Color> exhaustedColor = this.register(new Setting<>("Exhausted", new Color(255, 85, 85, 255)))
      .describe("A budget that has nothing left to spend.");

   public PacketLimitsElement() {
      super("PacketLimits", true, HudPosition.BOTTOM_RIGHT, 1);
   }

   @Override
   public int width() {
      int widest = 0;

      for (PacketLimitsElement.Row row : rows()) {
         widest = Math.max(widest, Render2D.width(row.label() + " ") + Render2D.width(row.used() + "/" + row.limit()));
      }

      return widest;
   }

   @Override
   public int height() {
      return rows().size() * Render2D.lineHeight();
   }

   @Override
   public void render(DrawContext graphics, HudModule hud, int left, int top, int width) {
      int y = top;

      for (PacketLimitsElement.Row row : rows()) {
         String label = row.label() + " ";
         String value = row.used() + "/" + row.limit();
         int x = this.rowLeft(left, width, Render2D.width(label) + Render2D.width(value));
         Render2D.text(graphics, label, x, y, this.labelColor.getValue().getRGB());
         Render2D.text(graphics, value, x + Render2D.width(label), y, row.exhausted() ? this.exhaustedColor.getValue().getRGB() : -5592406);
         y += Render2D.lineHeight();
      }
   }

   private static List<PacketLimitsElement.Row> rows() {
      PacketLimiter limiter = NetworkThreadExecutor.limiter();
      if (limiter == null) {
         return List.of();
      }

      PacketLimiter.Snapshot snapshot = limiter.snapshot();
      List<PacketLimitsElement.Row> rows = new ArrayList<>(5);
      rows.add(new PacketLimitsElement.Row("General", snapshot.globalUsed(), snapshot.globalLimit()));
      rows.add(new PacketLimitsElement.Row("Use +" + snapshot.useItemGuardMillis() + "ms", snapshot.useItemUsed(), snapshot.useItemLimit()));
      rows.add(new PacketLimitsElement.Row("Click", snapshot.clickSlotUsed(), snapshot.clickSlotLimit()));
      rows.add(new PacketLimitsElement.Row("Swap", snapshot.offhandUsed(), snapshot.offhandLimit()));
      rows.add(new PacketLimitsElement.Row("Recipe", snapshot.placeRecipeUsed(), snapshot.placeRecipeLimit()));
      return rows;
   }

   private record Row(String label, long used, long limit) {
      boolean exhausted() {
         return this.limit > 0L && this.used >= this.limit;
      }
   }
}
