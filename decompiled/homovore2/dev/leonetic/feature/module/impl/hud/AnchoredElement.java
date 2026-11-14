package dev.leonetic.feature.module.impl.hud;

import dev.leonetic.feature.module.impl.HudModule;
import net.minecraft.class_332;

public abstract class AnchoredElement extends HudElement {
   protected AnchoredElement(String name, boolean defaultEnabled) {
      super(name, defaultEnabled);
   }

   public abstract void render(class_332 var1, HudModule var2);
}
