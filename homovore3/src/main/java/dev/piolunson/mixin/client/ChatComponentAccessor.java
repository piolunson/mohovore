package dev.piolunson.mixin.client;

import java.util.List;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.ChatHudLine.Visible;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatHud.class)
public interface ChatComponentAccessor {
   @Accessor("messages")
   List<ChatHudLine> homovore$getAllMessages();

   @Accessor("visibleMessages")
   List<Visible> homovore$getTrimmedMessages();

   @Invoker("refresh")
   void homovore$refreshTrimmedMessages();
}
