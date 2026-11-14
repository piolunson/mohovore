package dev.leonetic.gui.component;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.ClickGuiSoundEvent;
import dev.leonetic.gui.ClickGuiScreen;
import dev.leonetic.render.Hover;
import dev.leonetic.render.Marquee;
import dev.leonetic.render.Render2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public class Component {
   protected static final MinecraftClient mc = MinecraftClient.getInstance();
   protected final Hover hover = new Hover();
   protected float x;
   protected float y;
   protected int width;
   protected int height;
   private boolean hidden;
   private final Marquee marquee = new Marquee();
   private boolean pointerInside;

   public void setBounds(float x, float y, int width) {
      this.x = x;
      this.y = y;
      this.width = width;
   }

   public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
   }

   public void mouseClicked(int mouseX, int mouseY, int button) {
   }

   public void mouseReleased(int mouseX, int mouseY, int button) {
   }

   public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
      return false;
   }

   public void keyTyped(String typed, int modifiers) {
   }

   public void keyPressed(int key) {
   }

   public void keyActivate() {
   }

   public void update() {
   }

   public float height() {
      return this.height;
   }

   public int rowHeight() {
      return this.height;
   }

   public float getX() {
      return this.x;
   }

   public float getY() {
      return this.y;
   }

   public boolean isHidden() {
      return this.hidden;
   }

   public void setHidden(boolean hidden) {
      this.hidden = hidden;
   }

   public String getPage() {
      return "General";
   }

   public void decayHover() {
      this.pointerInside = false;
      this.hover.decay();
   }

   protected float hoverWeight(boolean hovering, int mouseX) {
      if (hovering && !this.pointerInside) {
         Homovore2Client.EVENT_BUS.post(new ClickGuiSoundEvent(ClickGuiSoundEvent.Type.HOVER));
      }

      this.pointerInside = hovering;
      return this.hover.update(hovering, mouseX, this.x, this.x + this.width);
   }

   public boolean isHovered(int mouseX, int mouseY) {
      return ClickGuiScreen.isDraggingPanel()
         ? false
         : mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY < this.y + this.rowHeight();
   }

   protected void drawString(DrawContext graphics, String text, double x, double y, int color) {
      Render2D.text(graphics, text, (float)x, (float)y, color);
   }

   protected void drawScrollableString(DrawContext graphics, String text, double x, double y, int color, int maxWidth, boolean hovering) {
      this.marquee.draw(graphics, text, (float)x, (float)y, color, maxWidth, hovering);
   }

   protected static void click() {
      ClickGuiSoundEvent event = new ClickGuiSoundEvent(ClickGuiSoundEvent.Type.PRESS);
      if (!Homovore2Client.EVENT_BUS.post(event)) {
         mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
      }
   }
}
