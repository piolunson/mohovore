package dev.leonetic.feature.module.impl.hud;

import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.render.Render2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.registry.tag.FluidTags;

public abstract class HudElement {
   protected static final MinecraftClient mc = MinecraftClient.getInstance();
   public static final int MARGIN = 2;
   protected static final int WHITE = -1;
   protected static final int GRAY = -5592406;
   protected static final int RED = -43691;
   private static final int CHAT_INPUT_HEIGHT = 14;
   private final String name;
   private final String displayName;
   private final List<Setting<?>> settings = new ArrayList<>();
   public final Setting<Boolean> enabled;

   protected HudElement(String name, boolean defaultEnabled) {
      this.name = name;
      this.displayName = spaceCamelCase(name);
      this.enabled = this.register(new Setting<>("Enabled", defaultEnabled));
   }

   public String getName() {
      return this.name;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public boolean isEnabled() {
      return this.enabled.getValue();
   }

   public void setEnabled(boolean state) {
      this.enabled.setValue(state);
   }

   public void toggle() {
      this.setEnabled(!this.isEnabled());
   }

   public List<Setting<?>> getSettings() {
      return Collections.unmodifiableList(this.settings);
   }

   public List<Setting<?>> getGuiSettings() {
      List<Setting<?>> out = new ArrayList<>(this.settings);
      out.remove(this.enabled);
      return out;
   }

   protected <S extends Setting<?>> S register(S setting) {
      this.settings.add(setting);
      return setting;
   }

   public void attachTo(Module owner) {
      this.settings.forEach(setting -> setting.setOwner(owner));
   }

   public int placeholderWidth() {
      return Render2D.width(this.getDisplayName()) + 4;
   }

   public int placeholderHeight() {
      return Render2D.lineHeight();
   }

   public static int bottomAnchor(DrawContext graphics) {
      return graphics.getScaledWindowHeight() - (mc.currentScreen instanceof ChatScreen ? 14 : 0);
   }

   protected static boolean showingBubbles() {
      return mc.player.isSubmergedIn(FluidTags.WATER) || mc.player.getAir() < mc.player.getMaxAir();
   }

   private static String spaceCamelCase(String name) {
      return name.replaceAll("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", " ");
   }
}
