package dev.leonetic.mixin;

import java.util.List;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatHud.class)
public interface ChatComponentAccessor {
   @Accessor("messages")
   List<ChatHudLine> homovore$getAllMessages();

   @Invoker("refresh")
   void homovore$refreshTrimmedMessages();

   @Invoker("addMessage")
   void homovore$addMessage(Text var1, MessageSignatureData var2, MessageIndicator var3);
}
