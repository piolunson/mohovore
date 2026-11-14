package dev.leonetic.feature.module.impl.hud;

import dev.leonetic.feature.module.impl.HudModule;
import net.minecraft.client.gui.DrawContext;

public abstract class AnchoredElement extends HudElement {
   protected AnchoredElement(String name, boolean defaultEnabled) {
      super(name, defaultEnabled);
   }

   public abstract void render(DrawContext var1, HudModule var2);
}
